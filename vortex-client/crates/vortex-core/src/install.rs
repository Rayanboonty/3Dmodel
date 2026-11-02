//! Turn a resolved version JSON into files on disk.

use std::collections::HashSet;
use std::path::{Path, PathBuf};

use anyhow::{anyhow, bail, Context, Result};

use crate::assets::{self, AssetIndex};
use crate::download::{DownloadTask, Downloader, Verify};
use crate::paths::{safe_relative, Paths};
use crate::progress::{report, ProgressFn};
use crate::rules::Environment;
use crate::version::{ResolvedArtifact, VersionJson};

/// Everything needed to build a launch command for a version.
#[derive(Debug, Clone)]
pub struct InstalledVersion {
    /// The id that was installed (e.g. `1.21.4` or `fabric-loader-…`).
    pub id: String,
    /// Fully merged version JSON (no `inheritsFrom` left).
    pub version: VersionJson,
    pub client_jar: PathBuf,
    /// Library jars for the classpath, in order (client jar not included).
    pub classpath: Vec<PathBuf>,
    pub natives_dir: PathBuf,
    pub asset_index_id: String,
    pub assets: AssetIndex,
    pub logging: Option<LoggingSetup>,
}

#[derive(Debug, Clone)]
pub struct LoggingSetup {
    /// e.g. `-Dlog4j.configurationFile=${path}`
    pub argument: String,
    pub path: PathBuf,
}

struct Native {
    jar: PathBuf,
    exclude: Vec<String>,
}

fn library_path(paths: &Paths, artifact: &ResolvedArtifact) -> Result<PathBuf> {
    let rel = safe_relative(&artifact.path)
        .ok_or_else(|| anyhow!("refusing unsafe library path '{}'", artifact.path))?;
    Ok(paths.libraries().join(rel))
}

fn task_for(paths: &Paths, artifact: &ResolvedArtifact, tasks: &mut Vec<DownloadTask>) -> Result<PathBuf> {
    let path = library_path(paths, artifact)?;
    match &artifact.url {
        Some(url) => tasks.push(
            DownloadTask::new(url, &path)
                .sha1(artifact.sha1.clone())
                .size(artifact.size),
        ),
        None if !path.is_file() => bail!(
            "library {} has no download URL and is not installed",
            artifact.path
        ),
        None => {}
    }
    Ok(path)
}

/// Download libraries, natives, the client jar, the log config and assets.
pub async fn install(
    dl: &Downloader,
    paths: &Paths,
    version: VersionJson,
    progress: &ProgressFn,
) -> Result<InstalledVersion> {
    let env = Environment::current();
    let mut tasks = Vec::new();
    let mut classpath = Vec::new();
    let mut natives = Vec::new();
    let mut seen_artifacts = HashSet::new();
    let mut seen_natives = HashSet::new();

    for lib in version.libraries.iter().filter(|l| l.applies(&env)) {
        if let Some(native) = lib.native(&env) {
            if seen_natives.insert(native.path.clone()) {
                let jar = task_for(paths, &native, &mut tasks)?;
                natives.push(Native {
                    jar,
                    exclude: lib.extract_excludes(),
                });
            }
        }
        if let Some(artifact) = lib.artifact() {
            if seen_artifacts.insert(lib.dedup_key()) {
                classpath.push(task_for(paths, &artifact, &mut tasks)?);
            }
        }
    }

    let jar_id = version.client_jar_id().to_string();
    let client_jar = paths.version_jar(&jar_id);
    match version.downloads.as_ref().and_then(|d| d.client.as_ref()) {
        Some(c) => tasks.push(
            DownloadTask::new(&c.url, &client_jar)
                .sha1(c.sha1.clone())
                .size(c.size),
        ),
        None if !client_jar.is_file() => bail!("version {} has no client jar", version.id),
        None => {}
    }

    let mut logging = None;
    if let Some(client) = version.logging.as_ref().and_then(|l| l.client.as_ref()) {
        if let Some(rel) = safe_relative(&client.file.id) {
            let path = paths.log_configs().join(rel);
            tasks.push(
                DownloadTask::new(&client.file.url, &path)
                    .sha1(Some(client.file.sha1.clone()))
                    .size(client.file.size),
            );
            logging = Some(LoggingSetup {
                argument: client.argument.clone(),
                path,
            });
        }
    }

    dl.run(tasks, "Libraries", Verify::Hash, progress).await?;

    let natives_dir = paths.natives(&version.id);
    if !natives.is_empty() {
        report(progress, "Extracting natives", 0, natives.len() as u64);
        let dir = natives_dir.clone();
        tokio::task::spawn_blocking(move || {
            for n in &natives {
                extract_natives(&n.jar, &dir, &n.exclude)?;
            }
            anyhow::Ok(())
        })
        .await??;
    }
    std::fs::create_dir_all(&natives_dir)?;

    let asset_index_id = version.asset_index_id();
    let assets = match &version.asset_index {
        Some(index_ref) => assets::install(dl, paths, index_ref, progress).await?,
        None => {
            let local = paths.asset_indexes().join(format!("{asset_index_id}.json"));
            if local.is_file() {
                assets::load_index(&local).await?
            } else {
                AssetIndex::default()
            }
        }
    };

    report(progress, "Installed", 1, 1);
    Ok(InstalledVersion {
        id: version.id.clone(),
        client_jar,
        classpath,
        natives_dir,
        asset_index_id,
        assets,
        logging,
        version,
    })
}

/// Extract a natives jar into `dest`, honouring the `extract.exclude` list.
pub fn extract_natives(jar: &Path, dest: &Path, exclude: &[String]) -> Result<()> {
    let file = std::fs::File::open(jar).with_context(|| format!("opening {}", jar.display()))?;
    let mut zip = zip::ZipArchive::new(file).with_context(|| format!("reading {}", jar.display()))?;
    for i in 0..zip.len() {
        let mut entry = zip.by_index(i)?;
        if entry.is_dir() {
            continue;
        }
        let name = entry.name()?.to_string();
        if name.starts_with("META-INF/") || exclude.iter().any(|e| name.starts_with(e.as_str())) {
            continue;
        }
        let Some(rel) = entry.enclosed_name() else {
            continue;
        };
        let out = dest.join(rel);
        // Already extracted (files may be locked by a running game on Windows).
        if std::fs::metadata(&out).is_ok_and(|m| m.len() == entry.size()) {
            continue;
        }
        if let Some(parent) = out.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let mut f = std::fs::File::create(&out)?;
        std::io::copy(&mut entry, &mut f)?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    #[test]
    fn extracts_natives_with_excludes() {
        let dir = tempfile::tempdir().unwrap();
        let jar = dir.path().join("n.jar");
        {
            let mut w = zip::ZipWriter::new(std::fs::File::create(&jar).unwrap());
            let opts = zip::write::SimpleFileOptions::default();
            w.start_file("lwjgl64.dll", opts).unwrap();
            w.write_all(b"dll").unwrap();
            w.start_file("META-INF/MANIFEST.MF", opts).unwrap();
            w.write_all(b"m").unwrap();
            w.start_file("skip/me.txt", opts).unwrap();
            w.write_all(b"x").unwrap();
            w.finish().unwrap();
        }
        let out = dir.path().join("out");
        extract_natives(&jar, &out, &["skip/".to_string()]).unwrap();
        assert_eq!(std::fs::read(out.join("lwjgl64.dll")).unwrap(), b"dll");
        assert!(!out.join("META-INF").exists());
        assert!(!out.join("skip").exists());
        // Idempotent.
        extract_natives(&jar, &out, &[]).unwrap();
    }
}
