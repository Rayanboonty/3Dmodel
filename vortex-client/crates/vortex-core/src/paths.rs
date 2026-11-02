//! On-disk layout.
//!
//! ```text
//! <root>/                      %APPDATA%\VortexClient on Windows
//!   settings.json
//!   accounts.json
//!   versions/<id>/<id>.json    version JSON (vanilla or Fabric profile)
//!   versions/<id>/<id>.jar     client jar
//!   libraries/                 maven-style library tree (shared)
//!   assets/indexes|objects|virtual|log_configs
//!   natives/<id>/              extracted LWJGL natives per version
//!   runtimes/<component>/<platform>/   Mojang Java runtimes
//!   instances/<name>/instance.json + minecraft/ (game directory)
//!   cache/                     cached manifests for offline use
//! ```

use std::path::{Component, Path, PathBuf};

#[derive(Debug, Clone)]
pub struct Paths {
    pub root: PathBuf,
}

impl Paths {
    pub fn new(root: impl Into<PathBuf>) -> Self {
        Self { root: root.into() }
    }

    /// `$VORTEX_HOME` if set, otherwise the platform data directory
    /// (`%APPDATA%\VortexClient` on Windows, `~/.local/share/VortexClient` on
    /// Linux, `~/Library/Application Support/VortexClient` on macOS).
    pub fn default_root() -> PathBuf {
        if let Some(home) = std::env::var_os("VORTEX_HOME") {
            return PathBuf::from(home);
        }
        dirs::data_dir()
            .unwrap_or_else(|| PathBuf::from("."))
            .join("VortexClient")
    }

    pub fn from_default() -> Self {
        Self::new(Self::default_root())
    }

    pub fn versions(&self) -> PathBuf {
        self.root.join("versions")
    }
    pub fn version_dir(&self, id: &str) -> PathBuf {
        self.versions().join(id)
    }
    pub fn version_json(&self, id: &str) -> PathBuf {
        self.version_dir(id).join(format!("{id}.json"))
    }
    pub fn version_jar(&self, id: &str) -> PathBuf {
        self.version_dir(id).join(format!("{id}.jar"))
    }
    pub fn libraries(&self) -> PathBuf {
        self.root.join("libraries")
    }
    pub fn assets(&self) -> PathBuf {
        self.root.join("assets")
    }
    pub fn asset_indexes(&self) -> PathBuf {
        self.assets().join("indexes")
    }
    pub fn asset_objects(&self) -> PathBuf {
        self.assets().join("objects")
    }
    pub fn asset_virtual(&self, index_id: &str) -> PathBuf {
        self.assets().join("virtual").join(index_id)
    }
    pub fn log_configs(&self) -> PathBuf {
        self.assets().join("log_configs")
    }
    pub fn natives(&self, id: &str) -> PathBuf {
        self.root.join("natives").join(id)
    }
    pub fn runtimes(&self) -> PathBuf {
        self.root.join("runtimes")
    }
    pub fn instances(&self) -> PathBuf {
        self.root.join("instances")
    }
    pub fn cache(&self) -> PathBuf {
        self.root.join("cache")
    }
    pub fn settings_file(&self) -> PathBuf {
        self.root.join("settings.json")
    }
    pub fn accounts_file(&self) -> PathBuf {
        self.root.join("accounts.json")
    }
}

/// Turn an untrusted relative path (from a manifest, zip entry, asset index…)
/// into a safe relative path. Rejects absolute paths and `..` components so a
/// malicious file can never write outside its destination directory.
pub fn safe_relative(path: &str) -> Option<PathBuf> {
    let p = Path::new(path);
    let mut out = PathBuf::new();
    for c in p.components() {
        match c {
            Component::Normal(part) => out.push(part),
            Component::CurDir => {}
            _ => return None,
        }
    }
    if out.as_os_str().is_empty() {
        None
    } else {
        Some(out)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn safe_relative_rejects_traversal() {
        assert!(safe_relative("../evil").is_none());
        assert!(safe_relative("a/../../evil").is_none());
        assert!(safe_relative("/etc/passwd").is_none());
        assert!(safe_relative("").is_none());
        assert_eq!(
            safe_relative("minecraft/sounds/a.ogg").unwrap(),
            PathBuf::from("minecraft").join("sounds").join("a.ogg")
        );
        assert_eq!(safe_relative("./x").unwrap(), PathBuf::from("x"));
    }
}
