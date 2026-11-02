//! Asset indexes and objects (textures, sounds, languages…).
//!
//! Three historical layouts are supported:
//! * modern: objects addressed by hash under `assets/objects`,
//! * `virtual` (1.6–1.7.2, index `legacy`): copied by name into
//!   `assets/virtual/<index>`,
//! * `map_to_resources` (pre-1.6 / betas / alphas): copied by name into the
//!   instance's `resources/` folder at launch.

use std::collections::BTreeMap;
use std::path::{Path, PathBuf};

use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};

use crate::download::{DownloadTask, Downloader, Verify};
use crate::paths::{safe_relative, Paths};
use crate::progress::{report, ProgressFn};
use crate::version::AssetIndexRef;

pub const RESOURCES_URL: &str = "https://resources.download.minecraft.net";

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct AssetIndex {
    #[serde(default)]
    pub objects: BTreeMap<String, AssetObject>,
    #[serde(default, rename = "virtual")]
    pub is_virtual: bool,
    #[serde(default)]
    pub map_to_resources: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AssetObject {
    pub hash: String,
    pub size: u64,
}

impl AssetObject {
    fn prefix(&self) -> Option<&str> {
        self.hash.get(..2)
    }

    pub fn path(&self, paths: &Paths) -> Option<PathBuf> {
        Some(paths.asset_objects().join(self.prefix()?).join(&self.hash))
    }

    pub fn url(&self) -> Option<String> {
        Some(format!("{RESOURCES_URL}/{}/{}", self.prefix()?, self.hash))
    }
}

/// Download the index and every object it references.
pub async fn install(
    dl: &Downloader,
    paths: &Paths,
    index_ref: &AssetIndexRef,
    progress: &ProgressFn,
) -> Result<AssetIndex> {
    let index_path = paths
        .asset_indexes()
        .join(format!("{}.json", index_ref.id));
    dl.run(
        vec![DownloadTask::new(&index_ref.url, &index_path)
            .sha1(Some(index_ref.sha1.clone()))
            .size(Some(index_ref.size))],
        "Asset index",
        Verify::Hash,
        progress,
    )
    .await?;

    let index = load_index(&index_path).await?;
    let tasks: Vec<DownloadTask> = index
        .objects
        .values()
        .filter_map(|obj| {
            Some(
                DownloadTask::new(obj.url()?, obj.path(paths)?)
                    .sha1(Some(obj.hash.clone()))
                    .size(Some(obj.size)),
            )
        })
        .collect();
    dl.run(tasks, "Assets", Verify::Size, progress).await?;

    if index.is_virtual {
        report(progress, "Preparing legacy assets", 0, 0);
        let dest = paths.asset_virtual(&index_ref.id);
        let (paths, index) = (paths.clone(), index.clone());
        tokio::task::spawn_blocking(move || copy_by_name(&paths, &index, &dest)).await??;
    }
    Ok(index)
}

/// Load an index that was already downloaded (offline launches).
pub async fn load_index(path: &Path) -> Result<AssetIndex> {
    let bytes = tokio::fs::read(path)
        .await
        .with_context(|| format!("reading asset index {}", path.display()))?;
    serde_json::from_slice(&bytes).context("invalid asset index")
}

/// Copy every object to `dest/<name>` (skipping ones already there).
pub fn copy_by_name(paths: &Paths, index: &AssetIndex, dest: &Path) -> Result<()> {
    for (name, obj) in &index.objects {
        let (Some(rel), Some(src)) = (safe_relative(name), obj.path(paths)) else {
            continue;
        };
        let target = dest.join(rel);
        if std::fs::metadata(&target).is_ok_and(|m| m.len() == obj.size) {
            continue;
        }
        if let Some(parent) = target.parent() {
            std::fs::create_dir_all(parent)?;
        }
        std::fs::copy(&src, &target)
            .with_context(|| format!("copying asset {name} to {}", target.display()))?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_legacy_flags() {
        let idx: AssetIndex = serde_json::from_str(
            r#"{"map_to_resources": true, "objects": {"sound/a.ogg": {"hash": "abcdef", "size": 3}}}"#,
        )
        .unwrap();
        assert!(idx.map_to_resources);
        assert!(!idx.is_virtual);
        let obj = &idx.objects["sound/a.ogg"];
        assert_eq!(obj.url().unwrap(), "https://resources.download.minecraft.net/ab/abcdef");

        let idx: AssetIndex = serde_json::from_str(r#"{"virtual": true, "objects": {}}"#).unwrap();
        assert!(idx.is_virtual);
    }

    #[test]
    fn copy_by_name_skips_traversal() {
        let dir = tempfile::tempdir().unwrap();
        let paths = Paths::new(dir.path());
        let obj = AssetObject { hash: "abcdef".into(), size: 2 };
        let src = obj.path(&paths).unwrap();
        std::fs::create_dir_all(src.parent().unwrap()).unwrap();
        std::fs::write(&src, b"hi").unwrap();
        let mut objects = BTreeMap::new();
        objects.insert("ok/file.txt".to_string(), obj.clone());
        objects.insert("../escape.txt".to_string(), obj);
        let index = AssetIndex { objects, is_virtual: true, map_to_resources: false };
        let dest = dir.path().join("out");
        copy_by_name(&paths, &index, &dest).unwrap();
        assert_eq!(std::fs::read(dest.join("ok").join("file.txt")).unwrap(), b"hi");
        assert!(!dir.path().join("escape.txt").exists());
    }
}
