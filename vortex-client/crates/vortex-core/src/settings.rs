//! Global launcher settings (`settings.json`).

use std::path::{Path, PathBuf};

use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct Settings {
    pub min_memory_mb: u32,
    pub max_memory_mb: u32,
    /// Force a specific Java for every instance (normally automatic).
    pub java_path: Option<PathBuf>,
    pub extra_jvm_args: Vec<String>,
    /// Azure application (client) id used for Microsoft sign-in.
    pub microsoft_client_id: Option<String>,
    pub download_concurrency: usize,
    pub show_snapshots: bool,
    pub show_old_versions: bool,
    /// UI: time of day — `cycle` (a running day/night cycle, default),
    /// `clock` (follows the computer's clock), or a fixed `dawn`, `day`,
    /// `dusk` or `night`.
    pub sky: String,
    /// UI: real minutes per in-game day in `cycle` mode.
    pub day_length_min: u32,
    /// UI: click and launch sounds.
    pub ui_sounds: bool,
    /// UI: background and transition animations.
    pub animations: bool,
}

impl Default for Settings {
    fn default() -> Self {
        Self {
            min_memory_mb: 512,
            max_memory_mb: 4096,
            java_path: None,
            extra_jvm_args: Vec::new(),
            microsoft_client_id: None,
            download_concurrency: 32,
            show_snapshots: false,
            show_old_versions: false,
            sky: "cycle".to_string(),
            day_length_min: 10,
            ui_sounds: true,
            animations: true,
        }
    }
}

impl Settings {
    pub fn load(path: &Path) -> Result<Self> {
        match std::fs::read(path) {
            Ok(bytes) => serde_json::from_slice(&bytes)
                .with_context(|| format!("invalid {}", path.display())),
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(Self::default()),
            Err(e) => Err(e.into()),
        }
    }

    pub fn save(&self, path: &Path) -> Result<()> {
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        std::fs::write(path, serde_json::to_vec_pretty(self)?)?;
        Ok(())
    }

    /// Client id from `VORTEX_MS_CLIENT_ID`, falling back to settings.
    pub fn client_id(&self) -> Option<String> {
        std::env::var("VORTEX_MS_CLIENT_ID")
            .ok()
            .filter(|s| !s.trim().is_empty())
            .or_else(|| self.microsoft_client_id.clone())
            .map(|s| s.trim().to_string())
            .filter(|s| !s.is_empty())
    }
}
