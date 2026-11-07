//! Launcher updates from GitHub Releases.
//!
//! Each release of the launcher is a GitHub release in the project's repo
//! tagged `vortex-v<version>` (e.g. `vortex-v0.3.0`) with the launcher as a
//! single `Vortex.exe` (and, for older launchers, a Windows zip) attached.
//! The launcher compares the newest one with its own version, downloads
//! the exe (or the zip), swaps it in place (a running .exe can be renamed
//! on Windows), starts the new version and quits.

use std::path::{Path, PathBuf};

use anyhow::{anyhow, bail, Context, Result};
use serde::{Deserialize, Serialize};
use vortex_core::{reqwest, zip};

/// Where releases are published. Override with `VORTEX_UPDATE_REPO=owner/name`.
pub const DEFAULT_REPO: &str = "Rayanboonty/3Dmodel";
pub const TAG_PREFIX: &str = "vortex-v";
const ASSET_NAME: &str = "Vortex-windows-x64.zip";
/// The whole launcher in one file (everything it needs is built in).
const EXE_ASSET: &str = "Vortex.exe";

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateInfo {
    pub available: bool,
    pub current: String,
    pub version: String,
    pub notes: String,
    pub published: String,
    pub url: Option<String>,
    pub size: u64,
    pub page: Option<String>,
}

#[derive(Deserialize)]
struct Release {
    tag_name: String,
    #[serde(default)]
    body: Option<String>,
    #[serde(default)]
    draft: bool,
    #[serde(default)]
    prerelease: bool,
    #[serde(default)]
    published_at: Option<String>,
    #[serde(default)]
    html_url: Option<String>,
    #[serde(default)]
    assets: Vec<Asset>,
}

#[derive(Deserialize)]
struct Asset {
    name: String,
    browser_download_url: String,
    #[serde(default)]
    size: u64,
}

pub fn repo() -> String {
    std::env::var("VORTEX_UPDATE_REPO").ok().filter(|s| s.contains('/')).unwrap_or_else(|| DEFAULT_REPO.to_string())
}

/// `0.10.2` -> [0, 10, 2]; anything after `-` or `+` is ignored.
pub fn parse_version(v: &str) -> Vec<u64> {
    v.trim()
        .trim_start_matches('v')
        .split(['-', '+'])
        .next()
        .unwrap_or("")
        .split('.')
        .map(|p| p.parse().unwrap_or(0))
        .collect()
}

pub fn is_newer(candidate: &str, current: &str) -> bool {
    let (mut a, mut b) = (parse_version(candidate), parse_version(current));
    let n = a.len().max(b.len());
    a.resize(n, 0);
    b.resize(n, 0);
    a > b
}

/// The newest published launcher release, compared with `current`.
pub async fn check(http: &reqwest::Client, current: &str) -> Result<UpdateInfo> {
    let url = format!("https://api.github.com/repos/{}/releases?per_page=30", repo());
    let resp = http
        .get(&url)
        .header("Accept", "application/vnd.github+json")
        .timeout(std::time::Duration::from_secs(15))
        .send()
        .await
        .context("couldn't reach GitHub")?;
    if resp.status().as_u16() == 404 {
        bail!("the update repository {} isn't public", repo());
    }
    let releases: Vec<Release> = resp.error_for_status()?.json().await.context("unexpected reply from GitHub")?;
    let best = releases
        .into_iter()
        .filter(|r| !r.draft && !r.prerelease && r.tag_name.starts_with(TAG_PREFIX))
        .max_by(|a, b| parse_version(&a.tag_name[TAG_PREFIX.len()..]).cmp(&parse_version(&b.tag_name[TAG_PREFIX.len()..])));
    let none = UpdateInfo {
        available: false,
        current: current.to_string(),
        version: current.to_string(),
        notes: String::new(),
        published: String::new(),
        url: None,
        size: 0,
        page: None,
    };
    let Some(r) = best else { return Ok(none) };
    let version = r.tag_name[TAG_PREFIX.len()..].to_string();
    let asset = r.assets.iter().find(|a| a.name.eq_ignore_ascii_case(EXE_ASSET))
        .or_else(|| r.assets.iter().find(|a| a.name.eq_ignore_ascii_case(ASSET_NAME)))
        .or_else(|| r.assets.iter().find(|a| a.name.to_ascii_lowercase().ends_with(".zip")));
    Ok(UpdateInfo {
        available: is_newer(&version, current) && asset.is_some(),
        current: current.to_string(),
        notes: r.body.unwrap_or_default().trim().to_string(),
        published: r.published_at.unwrap_or_default().get(..10).unwrap_or("").to_string(),
        url: asset.map(|a| a.browser_download_url.clone()),
        size: asset.map(|a| a.size).unwrap_or(0),
        page: r.html_url,
        version,
    })
}

/// Download `url` to `dest`, reporting `(done, total)` bytes.
pub async fn download(http: &reqwest::Client, url: &str, dest: &Path, mut progress: impl FnMut(u64, u64)) -> Result<()> {
    use tokio::io::AsyncWriteExt;
    let mut resp = http.get(url).send().await?.error_for_status()?;
    let total = resp.content_length().unwrap_or(0);
    if let Some(p) = dest.parent() {
        tokio::fs::create_dir_all(p).await?;
    }
    let mut f = tokio::fs::File::create(dest).await?;
    let mut done = 0u64;
    while let Some(chunk) = resp.chunk().await? {
        f.write_all(&chunk).await?;
        done += chunk.len() as u64;
        progress(done, total);
    }
    f.flush().await?;
    if total > 0 && done != total {
        bail!("the download was cut short");
    }
    Ok(())
}

/// Files of the release that are swapped in next to the running launcher.
fn wanted(name: &str) -> bool {
    let n = name.to_ascii_lowercase();
    n.ends_with(".exe") || n.ends_with(".dll") || n.ends_with(".jar") || n.ends_with(".md") || n.ends_with(".txt")
}

/// Unpack the release zip over the launcher's folder. Files in use are
/// renamed to `*.old` first (allowed on Windows for running programs) and
/// removed on the next start. Returns the new launcher executable.
pub fn apply(zip_path: &Path, install_dir: &Path, exe_name: &str) -> Result<PathBuf> {
    // A single-file update: the new launcher itself.
    let mut head = [0u8; 2];
    {
        use std::io::Read;
        let _ = std::fs::File::open(zip_path)?.read(&mut head)?;
    }
    if &head == b"MZ" {
        let target = install_dir.join(exe_name);
        let tmp = target.with_extension("exe.new");
        std::fs::copy(zip_path, &tmp).with_context(|| format!("can't write {}", tmp.display()))?;
        if target.exists() {
            let old = target.with_extension("exe.old");
            let _ = std::fs::remove_file(&old);
            std::fs::rename(&target, &old).with_context(|| format!("can't replace {}", target.display()))?;
        }
        std::fs::rename(&tmp, &target)?;
        return Ok(target);
    }
    let file = std::fs::File::open(zip_path)?;
    let mut zip = zip::ZipArchive::new(file).context("the update isn't a valid zip")?;
    let mut staged: Vec<(PathBuf, PathBuf)> = Vec::new();
    for i in 0..zip.len() {
        let mut entry = zip.by_index(i)?;
        if entry.is_dir() {
            continue;
        }
        let Some(rel) = entry.enclosed_name() else { continue };
        let Some(name) = rel.file_name().and_then(|n| n.to_str()).map(String::from) else { continue };
        if !wanted(&name) {
            continue;
        }
        // The zip holds a `Vortex/` folder; its files go straight in.
        let target = install_dir.join(if name.eq_ignore_ascii_case("vortex.exe") { exe_name } else { name.as_str() });
        let tmp = target.with_extension(format!("{}.new", target.extension().and_then(|e| e.to_str()).unwrap_or("")));
        let mut out = std::fs::File::create(&tmp).with_context(|| format!("can't write {}", tmp.display()))?;
        std::io::copy(&mut entry, &mut out)?;
        staged.push((tmp, target));
    }
    if !staged.iter().any(|(_, t)| t.file_name().and_then(|n| n.to_str()).is_some_and(|n| n.eq_ignore_ascii_case(exe_name))) {
        for (tmp, _) in &staged {
            let _ = std::fs::remove_file(tmp);
        }
        bail!("the update doesn't contain the launcher");
    }
    for (tmp, target) in &staged {
        if target.exists() {
            let old = target.with_extension(format!("{}.old", target.extension().and_then(|e| e.to_str()).unwrap_or("")));
            let _ = std::fs::remove_file(&old);
            std::fs::rename(target, &old).with_context(|| format!("can't replace {}", target.display()))?;
        }
        std::fs::rename(tmp, target)?;
    }
    Ok(install_dir.join(exe_name))
}

/// Remove `*.old` files left by the last update.
pub fn cleanup(install_dir: &Path) {
    if let Ok(rd) = std::fs::read_dir(install_dir) {
        for e in rd.flatten() {
            if e.file_name().to_string_lossy().ends_with(".old") {
                let _ = std::fs::remove_file(e.path());
            }
        }
    }
}

pub fn current_exe() -> Result<(PathBuf, String)> {
    let exe = std::env::current_exe()?;
    let dir = exe.parent().ok_or_else(|| anyhow!("no launcher folder"))?.to_path_buf();
    let name = exe.file_name().and_then(|n| n.to_str()).unwrap_or("Vortex.exe").to_string();
    Ok((dir, name))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    #[test]
    fn applies_a_single_exe() {
        let dir = std::env::temp_dir().join(format!("vortex-upd-exe-{}", std::process::id()));
        let install = dir.join("install");
        std::fs::create_dir_all(&install).unwrap();
        std::fs::write(install.join("Vortex.exe"), b"MZold").unwrap();
        let new = dir.join("Vortex-0.9.0.bin");
        std::fs::write(&new, b"MZnew launcher").unwrap();
        let exe = apply(&new, &install, "Vortex.exe").unwrap();
        assert_eq!(std::fs::read(&exe).unwrap(), b"MZnew launcher");
        assert_eq!(std::fs::read(install.join("Vortex.exe.old")).unwrap(), b"MZold");
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn versions() {
        assert!(is_newer("0.3.0", "0.2.0"));
        assert!(is_newer("0.10.0", "0.9.9"));
        assert!(is_newer("1.0", "0.99.1"));
        assert!(!is_newer("0.2.0", "0.2.0"));
        assert!(!is_newer("0.2.0-beta", "0.2.0"));
        assert_eq!(parse_version("v1.2.3+build"), vec![1, 2, 3]);
    }

    #[test]
    fn applies_a_release_zip() {
        let dir = tempfile_dir();
        let install = dir.join("app");
        std::fs::create_dir_all(&install).unwrap();
        std::fs::write(install.join("Vortex.exe"), b"old").unwrap();
        let zip_path = dir.join("u.zip");
        {
            let mut w = zip::ZipWriter::new(std::fs::File::create(&zip_path).unwrap());
            let o = zip::write::SimpleFileOptions::default();
            w.start_file("Vortex/Vortex.exe", o).unwrap();
            w.write_all(b"new").unwrap();
            w.start_file("Vortex/README.md", o).unwrap();
            w.write_all(b"readme").unwrap();
            w.start_file("Vortex/notes.bin", o).unwrap();
            w.write_all(b"skip").unwrap();
            w.finish().unwrap();
        }
        let exe = apply(&zip_path, &install, "Vortex.exe").unwrap();
        assert_eq!(std::fs::read(&exe).unwrap(), b"new");
        assert_eq!(std::fs::read(install.join("Vortex.exe.old")).unwrap(), b"old");
        assert!(install.join("README.md").is_file());
        assert!(!install.join("notes.bin").exists());
        cleanup(&install);
        assert!(!install.join("Vortex.exe.old").exists());
        let _ = std::fs::remove_dir_all(&dir);
    }

    fn tempfile_dir() -> PathBuf {
        let d = std::env::temp_dir().join(format!("vortex-upd-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&d);
        std::fs::create_dir_all(&d).unwrap();
        d
    }
}
