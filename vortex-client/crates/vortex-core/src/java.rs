//! Java runtimes.
//!
//! Every version JSON names a Mojang runtime component (`jre-legacy` = Java 8,
//! `java-runtime-gamma` = 17, `java-runtime-delta` = 21,
//! `java-runtime-epsilon` = 25, …). We download exactly that runtime from
//! Mojang, so the user never has to install Java for *any* version.

use std::collections::{BTreeMap, HashMap};
use std::path::{Path, PathBuf};

use anyhow::{anyhow, Context, Result};
use serde::Deserialize;

use crate::download::{DownloadTask, Downloader, Verify};
use crate::os;
use crate::paths::{safe_relative, Paths};
use crate::progress::{report, ProgressFn};

pub const RUNTIME_INDEX_URL: &str = "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

const MARKER: &str = ".vortex-runtime";

#[derive(Debug, Deserialize)]
struct RuntimeEntry {
    manifest: ManifestRef,
    #[serde(default)]
    version: Option<RuntimeVersion>,
}

#[derive(Debug, Deserialize)]
struct ManifestRef {
    sha1: String,
    #[serde(default)]
    size: Option<u64>,
    url: String,
}

#[derive(Debug, Deserialize)]
struct RuntimeVersion {
    name: String,
}

type RuntimeIndex = HashMap<String, HashMap<String, Vec<RuntimeEntry>>>;

#[derive(Debug, Deserialize)]
struct RuntimeManifest {
    files: BTreeMap<String, RuntimeFile>,
}

#[derive(Debug, Deserialize)]
#[serde(tag = "type", rename_all = "lowercase")]
enum RuntimeFile {
    File {
        #[serde(default)]
        executable: bool,
        downloads: RuntimeDownloads,
    },
    Directory,
    Link {
        #[allow(dead_code)]
        target: String,
    },
}

#[derive(Debug, Deserialize)]
struct RuntimeDownloads {
    raw: RawDownload,
}

#[derive(Debug, Deserialize)]
struct RawDownload {
    sha1: String,
    size: u64,
    url: String,
}

/// Where the `java` binary lives inside a Mojang runtime directory.
/// On Windows this is `javaw.exe`, so no console window pops up.
pub fn java_executable(runtime_dir: &Path) -> PathBuf {
    if cfg!(windows) {
        runtime_dir.join("bin").join("javaw.exe")
    } else if cfg!(target_os = "macos") {
        runtime_dir
            .join("jre.bundle")
            .join("Contents")
            .join("Home")
            .join("bin")
            .join("java")
    } else {
        runtime_dir.join("bin").join("java")
    }
}

pub fn runtime_dir(paths: &Paths, component: &str, platform: &str) -> PathBuf {
    paths.runtimes().join(component).join(platform)
}

/// Make sure the Mojang runtime `component` is installed and return the path
/// to its java executable.
pub async fn ensure_runtime(
    dl: &Downloader,
    paths: &Paths,
    component: &str,
    progress: &ProgressFn,
) -> Result<PathBuf> {
    let platform = os::java_runtime_platform()
        .ok_or_else(|| anyhow!("Mojang does not publish Java runtimes for this platform"))?;
    let dir = runtime_dir(paths, component, platform);
    let exe = java_executable(&dir);
    let marker = dir.join(MARKER);

    report(progress, "Checking Java runtime", 0, 0);
    let index_bytes = match dl
        .get_cached(RUNTIME_INDEX_URL, &paths.cache().join("java-runtimes.json"))
        .await
    {
        Ok(b) => b,
        // Offline but already installed: just use it.
        Err(_) if exe.is_file() => return Ok(exe),
        Err(e) => return Err(e),
    };
    let index: RuntimeIndex =
        serde_json::from_slice(&index_bytes).context("invalid Java runtime index")?;
    let entry = index
        .get(platform)
        .and_then(|p| p.get(component))
        .and_then(|v| v.first())
        .ok_or_else(|| {
            anyhow!("Mojang has no '{component}' Java runtime for {platform}")
        })?;

    if exe.is_file()
        && std::fs::read_to_string(&marker)
            .map(|s| s.trim() == entry.manifest.sha1)
            .unwrap_or(false)
    {
        return Ok(exe);
    }

    let label = entry
        .version
        .as_ref()
        .map(|v| format!("Java {}", v.name))
        .unwrap_or_else(|| format!("Java ({component})"));
    report(progress, &format!("Fetching {label}"), 0, 0);
    let mut manifest_task = DownloadTask::new(&entry.manifest.url, paths.cache().join(format!("runtime-{component}-{platform}.json")))
        .sha1(Some(entry.manifest.sha1.clone()));
    manifest_task.size = entry.manifest.size;
    let manifest_path = manifest_task.path.clone();
    dl.run(vec![manifest_task], "Java manifest", Verify::Hash, progress).await?;
    let manifest: RuntimeManifest = serde_json::from_slice(&tokio::fs::read(&manifest_path).await?)
        .context("invalid Java runtime manifest")?;

    let mut tasks = Vec::new();
    #[cfg(unix)]
    let mut links: Vec<(PathBuf, String)> = Vec::new();
    for (name, file) in &manifest.files {
        let Some(rel) = safe_relative(name) else { continue };
        let path = dir.join(rel);
        match file {
            RuntimeFile::Directory => {
                tokio::fs::create_dir_all(&path).await?;
            }
            RuntimeFile::File { executable, downloads } => {
                let mut t = DownloadTask::new(&downloads.raw.url, path)
                    .sha1(Some(downloads.raw.sha1.clone()))
                    .size(Some(downloads.raw.size));
                t.executable = *executable;
                tasks.push(t);
            }
            #[cfg(unix)]
            RuntimeFile::Link { target } => links.push((path, target.clone())),
            #[cfg(not(unix))]
            RuntimeFile::Link { .. } => {}
        }
    }
    dl.run(tasks, &format!("Downloading {label}"), Verify::Size, progress)
        .await?;

    #[cfg(unix)]
    for (path, target) in links {
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let _ = std::fs::remove_file(&path);
        std::os::unix::fs::symlink(&target, &path)
            .with_context(|| format!("creating symlink {}", path.display()))?;
    }

    if !exe.is_file() {
        return Err(anyhow!(
            "Java runtime installed but {} is missing",
            exe.display()
        ));
    }
    tokio::fs::write(&marker, &entry.manifest.sha1).await?;
    Ok(exe)
}

/// Best-effort lookup of a system Java (`JAVA_HOME`, then `PATH`).
pub fn find_system_java() -> Option<PathBuf> {
    let exe = if cfg!(windows) { "javaw.exe" } else { "java" };
    if let Some(home) = std::env::var_os("JAVA_HOME") {
        let p = PathBuf::from(home).join("bin").join(exe);
        if p.is_file() {
            return Some(p);
        }
    }
    let path = std::env::var_os("PATH")?;
    std::env::split_paths(&path)
        .map(|d| d.join(exe))
        .find(|p| p.is_file())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_runtime_manifest() {
        let m: RuntimeManifest = serde_json::from_str(
            r#"{"files": {
                "bin": {"type": "directory"},
                "bin/java": {"type": "file", "executable": true,
                    "downloads": {"raw": {"sha1": "a", "size": 1, "url": "u"}, "lzma": {"sha1": "b", "size": 1, "url": "v"}}},
                "lib/x.so": {"type": "link", "target": "../y.so"}
            }}"#,
        )
        .unwrap();
        assert_eq!(m.files.len(), 3);
        assert!(matches!(m.files["bin/java"], RuntimeFile::File { executable: true, .. }));
    }
}
