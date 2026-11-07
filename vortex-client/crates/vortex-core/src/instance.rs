//! Instances: isolated game directories, each pinned to a version + loader.

use std::path::{Path, PathBuf};

use anyhow::{bail, Context, Result};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "lowercase")]
pub enum Loader {
    #[default]
    Vanilla,
    Fabric {
        /// Fabric loader version; `None` = latest stable at install time.
        #[serde(default)]
        version: Option<String>,
    },
    Forge {
        /// Forge build (e.g. `47.4.28`); `None` = recommended at install time.
        #[serde(default)]
        version: Option<String>,
    },
}

impl Loader {
    pub fn label(&self) -> String {
        match self {
            Loader::Vanilla => "Vanilla".into(),
            Loader::Fabric { version: Some(v) } => format!("Fabric {v}"),
            Loader::Fabric { version: None } => "Fabric".into(),
            Loader::Forge { version: Some(v) } => format!("Forge {v}"),
            Loader::Forge { version: None } => "Forge".into(),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Instance {
    pub name: String,
    pub minecraft_version: String,
    #[serde(default)]
    pub loader: Loader,
    /// Overrides the global max memory (MB).
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub memory_mb: Option<u32>,
    /// Overrides automatic Java selection.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub java_path: Option<PathBuf>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub jvm_args: Vec<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub resolution: Option<(u32, u32)>,
    /// Server to join on start (`host[:port]`).
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub join_server: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub last_played: Option<u64>,
    /// What the instance was made for: `pvp`, `normal`, `performance` or
    /// `vanilla`; its mods are added on the first launch.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub pack: Option<String>,
    /// The server last joined from the launcher (for "rejoin last server").
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub last_server: Option<String>,
    /// Directory containing `instance.json`. Set when loading.
    #[serde(skip)]
    pub dir: PathBuf,
}

impl Instance {
    pub fn new(name: &str, minecraft_version: &str, loader: Loader) -> Self {
        Self {
            name: name.to_string(),
            minecraft_version: minecraft_version.to_string(),
            loader,
            memory_mb: None,
            java_path: None,
            jvm_args: Vec::new(),
            resolution: None,
            join_server: None,
            last_played: None,
            pack: None,
            last_server: None,
            dir: PathBuf::new(),
        }
    }

    /// The `.minecraft`-equivalent folder (saves, mods, resourcepacks…).
    pub fn game_dir(&self) -> PathBuf {
        self.dir.join("minecraft")
    }

    pub fn save(&self) -> Result<()> {
        std::fs::create_dir_all(&self.dir)?;
        let data = serde_json::to_vec_pretty(self)?;
        std::fs::write(self.dir.join("instance.json"), data)?;
        Ok(())
    }
}

/// Make a name safe to use as a folder name on every OS (incl. Windows).
pub fn sanitize_dir_name(name: &str) -> String {
    const RESERVED: [&str; 22] = [
        "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8",
        "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
    ];
    let mut s: String = name
        .chars()
        .map(|c| match c {
            '<' | '>' | ':' | '"' | '/' | '\\' | '|' | '?' | '*' => '_',
            c if c.is_control() => '_',
            c => c,
        })
        .collect();
    s = s.trim().trim_end_matches('.').to_string();
    if s.is_empty() {
        s = "instance".into();
    }
    if RESERVED.iter().any(|r| r.eq_ignore_ascii_case(&s)) {
        s.push('_');
    }
    s
}

pub struct InstanceStore {
    root: PathBuf,
}

impl InstanceStore {
    pub fn new(root: impl Into<PathBuf>) -> Self {
        Self { root: root.into() }
    }

    pub fn root(&self) -> &Path {
        &self.root
    }

    /// All instances, most recently played first.
    pub fn list(&self) -> Result<Vec<Instance>> {
        let mut out = Vec::new();
        let entries = match std::fs::read_dir(&self.root) {
            Ok(e) => e,
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(out),
            Err(e) => return Err(e.into()),
        };
        for entry in entries.flatten() {
            let dir = entry.path();
            let file = dir.join("instance.json");
            let Ok(bytes) = std::fs::read(&file) else { continue };
            match serde_json::from_slice::<Instance>(&bytes) {
                Ok(mut inst) => {
                    inst.dir = dir;
                    out.push(inst);
                }
                Err(e) => eprintln!("skipping {}: {e}", file.display()),
            }
        }
        out.sort_by(|a, b| {
            b.last_played
                .cmp(&a.last_played)
                .then_with(|| a.name.to_lowercase().cmp(&b.name.to_lowercase()))
        });
        Ok(out)
    }

    pub fn find(&self, name: &str) -> Result<Option<Instance>> {
        Ok(self
            .list()?
            .into_iter()
            .find(|i| i.name.eq_ignore_ascii_case(name)))
    }

    pub fn create(&self, mut instance: Instance) -> Result<Instance> {
        if instance.name.trim().is_empty() {
            bail!("instance name cannot be empty");
        }
        if self.find(&instance.name)?.is_some() {
            bail!("an instance called '{}' already exists", instance.name);
        }
        let base = sanitize_dir_name(&instance.name);
        let mut dir = self.root.join(&base);
        let mut n = 2;
        while dir.exists() {
            dir = self.root.join(format!("{base} ({n})"));
            n += 1;
        }
        instance.dir = dir;
        instance.save()?;
        std::fs::create_dir_all(instance.game_dir())?;
        Ok(instance)
    }

    pub fn delete(&self, instance: &Instance) -> Result<()> {
        // Only ever delete inside our own instances folder.
        let dir = instance
            .dir
            .canonicalize()
            .with_context(|| format!("instance folder {} not found", instance.dir.display()))?;
        let root = self.root.canonicalize()?;
        if !dir.starts_with(&root) || dir == root {
            bail!("refusing to delete {}", dir.display());
        }
        std::fs::remove_dir_all(dir)?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sanitizes_names() {
        assert_eq!(sanitize_dir_name("My: PvP / Pack?"), "My_ PvP _ Pack_");
        assert_eq!(sanitize_dir_name("con"), "con_");
        assert_eq!(sanitize_dir_name("  ...  "), "instance");
        assert_eq!(sanitize_dir_name("1.8.9 PvP."), "1.8.9 PvP");
    }

    #[test]
    fn create_list_delete() {
        let dir = tempfile::tempdir().unwrap();
        let store = InstanceStore::new(dir.path().join("instances"));
        let a = store
            .create(Instance::new("PvP 1.8.9", "1.8.9", Loader::Vanilla))
            .unwrap();
        assert!(a.game_dir().is_dir());
        store
            .create(Instance::new("Fabric", "1.21.4", Loader::Fabric { version: None }))
            .unwrap();
        assert!(store.create(Instance::new("pvp 1.8.9", "1.8.9", Loader::Vanilla)).is_err());
        let list = store.list().unwrap();
        assert_eq!(list.len(), 2);
        assert_eq!(
            store.find("fabric").unwrap().unwrap().loader,
            Loader::Fabric { version: None }
        );
        store.delete(&a).unwrap();
        assert_eq!(store.list().unwrap().len(), 1);
    }
}
