//! Parallel, verified, resumable-by-skipping file downloads.

use std::collections::HashSet;
use std::io::Read;
use std::path::{Path, PathBuf};
use std::time::Duration;

use anyhow::{anyhow, bail, Context, Result};
use futures::StreamExt;
use sha1::{Digest, Sha1};
use tokio::io::AsyncWriteExt;

use crate::progress::{report, ProgressFn};

#[derive(Debug, Clone)]
pub struct DownloadTask {
    pub url: String,
    pub path: PathBuf,
    pub sha1: Option<String>,
    pub size: Option<u64>,
    /// Mark the file executable after download (Unix only).
    pub executable: bool,
}

impl DownloadTask {
    pub fn new(url: impl Into<String>, path: impl Into<PathBuf>) -> Self {
        Self {
            url: url.into(),
            path: path.into(),
            sha1: None,
            size: None,
            executable: false,
        }
    }

    pub fn sha1(mut self, sha1: Option<String>) -> Self {
        self.sha1 = sha1;
        self
    }

    pub fn size(mut self, size: Option<u64>) -> Self {
        self.size = size;
        self
    }
}

/// How hard to look at files that already exist on disk.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Verify {
    /// Trust a matching size (fast; used for thousands of asset objects).
    Size,
    /// Re-hash existing files when a SHA-1 is known (jars, configs).
    Hash,
}

#[derive(Clone)]
pub struct Downloader {
    client: reqwest::Client,
    concurrency: usize,
    retries: u32,
}

impl Downloader {
    pub fn new(client: reqwest::Client, concurrency: usize) -> Self {
        Self {
            client,
            concurrency: concurrency.max(1),
            retries: 3,
        }
    }

    pub fn client(&self) -> &reqwest::Client {
        &self.client
    }

    /// Download every task that is missing or out of date.
    pub async fn run(
        &self,
        tasks: Vec<DownloadTask>,
        stage: &str,
        verify: Verify,
        progress: &ProgressFn,
    ) -> Result<()> {
        // De-duplicate by destination (several asset names can share a hash).
        let mut seen = HashSet::new();
        let tasks: Vec<DownloadTask> = tasks
            .into_iter()
            .filter(|t| seen.insert(t.path.clone()))
            .collect();

        report(progress, &format!("Checking {}", stage.to_lowercase()), 0, 0);
        let pending = tokio::task::spawn_blocking(move || {
            tasks
                .into_iter()
                .filter(|t| !is_up_to_date(t, verify))
                .collect::<Vec<_>>()
        })
        .await?;

        let total = pending.len() as u64;
        if total == 0 {
            return Ok(());
        }
        report(progress, stage, 0, total);

        let mut stream = futures::stream::iter(pending.into_iter().map(|task| {
            let this = self.clone();
            async move {
                this.fetch_with_retry(&task)
                    .await
                    .with_context(|| format!("failed to download {}", task.url))
            }
        }))
        .buffer_unordered(self.concurrency);

        let mut done = 0u64;
        let mut errors = Vec::new();
        while let Some(result) = stream.next().await {
            done += 1;
            if let Err(e) = result {
                errors.push(e);
            }
            report(progress, stage, done, total);
        }
        if let Some(first) = errors.into_iter().next() {
            return Err(first.context(format!("{stage}: some downloads failed")));
        }
        Ok(())
    }

    async fn fetch_with_retry(&self, task: &DownloadTask) -> Result<()> {
        let mut last_err = None;
        for attempt in 0..self.retries {
            if attempt > 0 {
                tokio::time::sleep(Duration::from_millis(500 * (1 << attempt))).await;
            }
            match self.fetch(task).await {
                Ok(()) => return Ok(()),
                Err(e) => last_err = Some(e),
            }
        }
        Err(last_err.unwrap_or_else(|| anyhow!("download failed")))
    }

    async fn fetch(&self, task: &DownloadTask) -> Result<()> {
        if let Some(parent) = task.path.parent() {
            tokio::fs::create_dir_all(parent).await?;
        }
        let tmp = tmp_path(&task.path);
        let mut resp = self
            .client
            .get(&task.url)
            .send()
            .await?
            .error_for_status()?;
        let mut file = tokio::fs::File::create(&tmp).await?;
        let mut hasher = Sha1::new();
        let mut len = 0u64;
        while let Some(chunk) = resp.chunk().await? {
            hasher.update(&chunk);
            len += chunk.len() as u64;
            file.write_all(&chunk).await?;
        }
        file.flush().await?;
        drop(file);

        if let Some(expected) = task.size {
            if len != expected {
                let _ = tokio::fs::remove_file(&tmp).await;
                bail!("size mismatch: expected {expected} bytes, got {len}");
            }
        }
        if let Some(expected) = &task.sha1 {
            let actual = hex::encode(hasher.finalize());
            if !actual.eq_ignore_ascii_case(expected) {
                let _ = tokio::fs::remove_file(&tmp).await;
                bail!("sha1 mismatch: expected {expected}, got {actual}");
            }
        }
        tokio::fs::rename(&tmp, &task.path).await?;
        #[cfg(unix)]
        if task.executable {
            use std::os::unix::fs::PermissionsExt;
            tokio::fs::set_permissions(&task.path, std::fs::Permissions::from_mode(0o755)).await?;
        }
        Ok(())
    }

    /// GET a URL and decode JSON.
    pub async fn get_json<T: serde::de::DeserializeOwned>(&self, url: &str) -> Result<T> {
        let resp = self.client.get(url).send().await?.error_for_status()?;
        let bytes = resp.bytes().await?;
        serde_json::from_slice(&bytes).with_context(|| format!("invalid JSON from {url}"))
    }

    /// GET a URL, cache the body at `cache`, and fall back to the cached copy
    /// when offline.
    pub async fn get_cached(&self, url: &str, cache: &Path) -> Result<Vec<u8>> {
        let fetched = async {
            let resp = self
                .client
                .get(url)
                .timeout(Duration::from_secs(20))
                .send()
                .await?
                .error_for_status()?;
            anyhow::Ok(resp.bytes().await?.to_vec())
        }
        .await;
        match fetched {
            Ok(body) => {
                if let Some(parent) = cache.parent() {
                    let _ = tokio::fs::create_dir_all(parent).await;
                }
                let _ = write_atomic(cache, &body).await;
                Ok(body)
            }
            Err(e) => tokio::fs::read(cache)
                .await
                .map_err(|_| e.context(format!("could not fetch {url} and no cached copy exists"))),
        }
    }
}

fn tmp_path(path: &Path) -> PathBuf {
    let mut name = path
        .file_name()
        .map(|n| n.to_os_string())
        .unwrap_or_default();
    name.push(".part");
    path.with_file_name(name)
}

/// Write a file via a temporary sibling so readers never see half a file.
pub async fn write_atomic(path: &Path, data: &[u8]) -> Result<()> {
    if let Some(parent) = path.parent() {
        tokio::fs::create_dir_all(parent).await?;
    }
    let tmp = tmp_path(path);
    tokio::fs::write(&tmp, data).await?;
    tokio::fs::rename(&tmp, path).await?;
    Ok(())
}

/// Blocking SHA-1 of a file, lowercase hex.
pub fn sha1_file(path: &Path) -> std::io::Result<String> {
    let mut file = std::fs::File::open(path)?;
    let mut hasher = Sha1::new();
    let mut buf = vec![0u8; 64 * 1024];
    loop {
        let n = file.read(&mut buf)?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
    }
    Ok(hex::encode(hasher.finalize()))
}

fn is_up_to_date(task: &DownloadTask, verify: Verify) -> bool {
    let Ok(meta) = std::fs::metadata(&task.path) else {
        return false;
    };
    if !meta.is_file() {
        return false;
    }
    if let Some(size) = task.size {
        if meta.len() != size {
            return false;
        }
    }
    match (&task.sha1, verify) {
        (Some(expected), Verify::Hash) => sha1_file(&task.path)
            .map(|actual| actual.eq_ignore_ascii_case(expected))
            .unwrap_or(false),
        _ => true,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sha1_and_up_to_date() {
        let dir = tempfile::tempdir().unwrap();
        let p = dir.path().join("f.txt");
        std::fs::write(&p, b"hello").unwrap();
        let sha = sha1_file(&p).unwrap();
        assert_eq!(sha, "aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d");

        let t = DownloadTask::new("http://x", &p).sha1(Some(sha.clone())).size(Some(5));
        assert!(is_up_to_date(&t, Verify::Hash));
        let bad = DownloadTask::new("http://x", &p).sha1(Some("00".into())).size(Some(5));
        assert!(!is_up_to_date(&bad, Verify::Hash));
        assert!(is_up_to_date(&bad, Verify::Size), "size mode trusts matching size");
        let wrong_size = DownloadTask::new("http://x", &p).size(Some(6));
        assert!(!is_up_to_date(&wrong_size, Verify::Size));
        assert!(!is_up_to_date(&DownloadTask::new("http://x", dir.path().join("missing")), Verify::Size));
    }

    #[test]
    fn tmp_path_is_sibling() {
        let p = Path::new("a").join("b.jar");
        assert_eq!(tmp_path(&p), Path::new("a").join("b.jar.part"));
    }
}
