//! Fast launch: remember everything a launch resolved (version JSON chain,
//! classpath, natives, Java) so the next launch of the same instance skips
//! the version manifest, loader lookups, Java manifest and file hashing.
//!
//! A plan is only reused while every file it names is still on disk with
//! the same size, the instance's version/loader/Java choice is unchanged,
//! and it is younger than [`MAX_AGE_SECS`]; otherwise the launcher does a
//! full check and writes a fresh plan.

use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};
use sha1::{Digest, Sha1};

use crate::assets::AssetIndex;
use crate::install::{InstalledVersion, LoggingSetup};
use crate::instance::Instance;
use crate::paths::Paths;
use crate::settings::Settings;
use crate::version::VersionJson;

/// Plans older than this get a full check (picks up loader updates).
pub const MAX_AGE_SECS: u64 = 3 * 24 * 3600;
const FORMAT: u32 = 1;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LaunchPlan {
    format: u32,
    key: String,
    created: u64,
    id: String,
    version: VersionJson,
    client_jar: PathBuf,
    classpath: Vec<PathBuf>,
    natives_dir: PathBuf,
    asset_index_id: String,
    map_to_resources: bool,
    is_virtual: bool,
    logging: Option<(String, PathBuf)>,
    java: PathBuf,
    /// Every file the launch needs, with its size, for a quick stat check.
    files: Vec<(PathBuf, u64)>,
}

fn now() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0)
}

/// What decides whether a plan still applies to an instance.
pub fn key(instance: &Instance, settings: &Settings) -> String {
    format!(
        "{}|{:?}|{:?}|{:?}|{}",
        instance.minecraft_version,
        instance.loader,
        instance.java_path,
        settings.java_path,
        crate::LAUNCHER_VERSION
    )
}

/// Where an instance's plan lives.
pub fn plan_path(paths: &Paths, instance: &Instance) -> PathBuf {
    let id = hex::encode(Sha1::digest(instance.dir.to_string_lossy().as_bytes()));
    paths.cache().join("launch").join(format!("{}.json", &id[..16]))
}

/// Forget an instance's plan so the next launch checks everything.
pub fn forget(paths: &Paths, instance: &Instance) {
    let _ = std::fs::remove_file(plan_path(paths, instance));
}

impl LaunchPlan {
    pub fn new(installed: &InstalledVersion, java: &Path, key: String) -> Self {
        let mut files: Vec<(PathBuf, u64)> = Vec::new();
        for p in installed.classpath.iter().chain(std::iter::once(&installed.client_jar)).chain(std::iter::once(&java.to_path_buf())) {
            if let Ok(m) = std::fs::metadata(p) {
                files.push((p.clone(), m.len()));
            }
        }
        Self {
            format: FORMAT,
            key,
            created: now(),
            id: installed.id.clone(),
            version: installed.version.clone(),
            client_jar: installed.client_jar.clone(),
            classpath: installed.classpath.clone(),
            natives_dir: installed.natives_dir.clone(),
            asset_index_id: installed.asset_index_id.clone(),
            map_to_resources: installed.assets.map_to_resources,
            is_virtual: installed.assets.is_virtual,
            logging: installed.logging.as_ref().map(|l| (l.argument.clone(), l.path.clone())),
            java: java.to_path_buf(),
            files,
        }
    }

    pub fn save(&self, path: &Path) {
        if let Some(parent) = path.parent() {
            let _ = std::fs::create_dir_all(parent);
        }
        if let Ok(bytes) = serde_json::to_vec(self) {
            let tmp = path.with_extension("json.part");
            if std::fs::write(&tmp, bytes).is_ok() {
                let _ = std::fs::rename(&tmp, path);
            }
        }
    }

    /// Load a plan if it is still valid for `key`.
    pub fn load(path: &Path, key: &str) -> Option<Self> {
        let plan: Self = serde_json::from_slice(&std::fs::read(path).ok()?).ok()?;
        if plan.format != FORMAT || plan.key != key || now().saturating_sub(plan.created) > MAX_AGE_SECS {
            return None;
        }
        if !plan.natives_dir.is_dir() || plan.files.len() < plan.classpath.len() + 2 {
            return None;
        }
        let intact = plan
            .files
            .iter()
            .all(|(p, size)| std::fs::metadata(p).is_ok_and(|m| m.is_file() && m.len() == *size));
        intact.then_some(plan)
    }

    pub fn java(&self) -> &Path {
        &self.java
    }

    /// Every file the game reads at start-up (for warming the disk cache).
    pub fn files(&self) -> impl Iterator<Item = &Path> {
        self.files.iter().map(|(p, _)| p.as_path())
    }

    pub fn installed(&self) -> InstalledVersion {
        InstalledVersion {
            id: self.id.clone(),
            version: self.version.clone(),
            client_jar: self.client_jar.clone(),
            classpath: self.classpath.clone(),
            natives_dir: self.natives_dir.clone(),
            asset_index_id: self.asset_index_id.clone(),
            assets: AssetIndex { objects: Default::default(), is_virtual: self.is_virtual, map_to_resources: self.map_to_resources },
            logging: self.logging.clone().map(|(argument, path)| LoggingSetup { argument, path }),
        }
    }
}

/// Read every file once so the OS has them cached when Java starts; the
/// game then loads its jars from memory instead of the disk.
pub fn warm(plan: &LaunchPlan) -> u64 {
    use std::io::Read;
    let mut total = 0u64;
    let mut buf = vec![0u8; 1 << 20];
    for p in plan.files() {
        if let Ok(mut f) = std::fs::File::open(p) {
            while let Ok(n) = f.read(&mut buf) {
                if n == 0 {
                    break;
                }
                total += n as u64;
            }
        }
    }
    total
}

/// Make sure a Java runtime has its base class-data-sharing archive (the
/// JDK's own classes, pre-parsed). Mojang's runtimes ship without one; the
/// JDK can write it in well under a second, once. Returns true when the
/// archive is there.
pub fn ensure_base_cds(java: &Path, java_major: u32) -> bool {
    if java_major < 17 {
        return false;
    }
    // <home>/bin/java(.exe) -> <home>
    let Some(home) = java.parent().and_then(|b| b.parent()) else { return false };
    let dirs = [home.join("lib").join("server"), home.join("bin").join("server")];
    let Some(server) = dirs.iter().find(|d| d.join("libjvm.so").is_file() || d.join("jvm.dll").is_file() || d.join("libjvm.dylib").is_file()) else {
        return false;
    };
    let archive = server.join("classes.jsa");
    if archive.is_file() {
        return true;
    }
    let mut cmd = std::process::Command::new(java);
    cmd.arg("-Xshare:dump").stdout(std::process::Stdio::null()).stderr(std::process::Stdio::null());
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        cmd.creation_flags(0x0800_0000); // CREATE_NO_WINDOW
    }
    cmd.status().map(|s| s.success()).unwrap_or(false) && archive.is_file()
}

/// JVM flags that make start-up and play smoother, for Java 17 and newer.
/// Skipped when the user already chose a garbage collector.
pub fn tuned_jvm_args(java_major: u32, user_args: &[String], cds_archive: Option<&Path>) -> Vec<String> {
    let mut out = Vec::new();
    if java_major < 17 {
        return out;
    }
    let chose_gc = user_args.iter().any(|a| a.starts_with("-XX:+Use") && a.ends_with("GC"));
    if !chose_gc {
        out.extend(
            [
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+UseG1GC",
                "-XX:G1NewSizePercent=20",
                "-XX:G1ReservePercent=20",
                "-XX:MaxGCPauseMillis=50",
                "-XX:G1HeapRegionSize=32M",
                "-XX:+ParallelRefProcEnabled",
                "-XX:+DisableExplicitGC",
            ]
            .map(String::from),
        );
    }
    // Class data sharing: Java 19+ writes an archive of the game's loaded
    // classes on the first run and maps it on later runs (faster start-up).
    if java_major >= 19 && !user_args.iter().any(|a| a.contains("SharedArchiveFile")) {
        if let Some(path) = cds_archive {
            if let Some(parent) = path.parent() {
                let _ = std::fs::create_dir_all(parent);
            }
            out.push("-XX:+AutoCreateSharedArchive".to_string());
            out.push(format!("-XX:SharedArchiveFile={}", path.display()));
            out.push("-Xlog:cds=off".to_string());
            out.push("-Xlog:cds+dynamic=off".to_string());
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn tuned_args_respect_user_gc() {
        assert!(tuned_jvm_args(8, &[], None).is_empty());
        let a = tuned_jvm_args(21, &[], None);
        assert!(a.iter().any(|x| x == "-XX:+UseG1GC"));
        let b = tuned_jvm_args(21, &["-XX:+UseZGC".to_string()], None);
        assert!(!b.iter().any(|x| x == "-XX:+UseG1GC"));
        let c = tuned_jvm_args(21, &[], Some(Path::new("/tmp/x.jsa")));
        assert!(c.iter().any(|x| x.starts_with("-XX:SharedArchiveFile=")));
        assert!(!tuned_jvm_args(17, &[], Some(Path::new("/tmp/x.jsa"))).iter().any(|x| x.contains("Shared")));
    }

    #[test]
    fn plan_round_trip_and_invalidation() {
        let dir = tempfile::tempdir().unwrap();
        let jar = dir.path().join("a.jar");
        let client = dir.path().join("client.jar");
        let java = dir.path().join("java");
        let natives = dir.path().join("natives");
        std::fs::create_dir_all(&natives).unwrap();
        for p in [&jar, &client, &java] {
            std::fs::write(p, b"data").unwrap();
        }
        let version: VersionJson = serde_json::from_str(r#"{"id":"1.21.4","mainClass":"net.minecraft.client.main.Main"}"#).unwrap();
        let installed = InstalledVersion {
            id: "1.21.4".into(),
            version,
            client_jar: client.clone(),
            classpath: vec![jar.clone()],
            natives_dir: natives,
            asset_index_id: "19".into(),
            assets: AssetIndex::default(),
            logging: None,
        };
        let plan = LaunchPlan::new(&installed, &java, "k".into());
        let path = dir.path().join("plan.json");
        plan.save(&path);
        let back = LaunchPlan::load(&path, "k").expect("valid plan");
        assert_eq!(back.installed().classpath, vec![jar.clone()]);
        assert!(LaunchPlan::load(&path, "other").is_none());
        std::fs::write(&jar, b"changed size").unwrap();
        assert!(LaunchPlan::load(&path, "k").is_none());
    }
}
