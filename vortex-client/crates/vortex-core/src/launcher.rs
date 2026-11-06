use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::Duration;

use anyhow::{anyhow, bail, Context, Result};
use tokio::sync::Mutex;

use crate::accounts::{Account, AccountKind};
use crate::assets;
use crate::auth;
use crate::download::{sha1_file, write_atomic, Downloader};
use crate::fabric;
use crate::fastlaunch::{self, LaunchPlan};
use crate::forge;
use crate::install::{self, InstalledVersion};
use crate::instance::{Instance, InstanceStore, Loader};
use crate::java;
use crate::launch::{self, LaunchCommand, LaunchOptions, LaunchSession};
use crate::manifest::{VersionManifest, VERSION_MANIFEST_URL};
use crate::paths::Paths;
use crate::progress::{report, ProgressFn};
use crate::settings::Settings;
use crate::version::VersionJson;
use crate::USER_AGENT;

/// Entry point for UIs: owns the HTTP client, download pool and caches.
/// Cheap to share behind an `Arc`; every method takes `&self`.
pub struct Launcher {
    paths: Paths,
    downloader: Downloader,
    manifest: Mutex<Option<Arc<VersionManifest>>>,
}

impl Launcher {
    pub fn new(paths: Paths) -> Result<Self> {
        Self::with_concurrency(paths, 32)
    }

    pub fn with_concurrency(paths: Paths, concurrency: usize) -> Result<Self> {
        // reqwest is built without a bundled crypto provider; use ring.
        let _ = rustls::crypto::ring::default_provider().install_default();
        let http = reqwest::Client::builder()
            .user_agent(USER_AGENT)
            .connect_timeout(Duration::from_secs(15))
            .read_timeout(Duration::from_secs(60))
            .build()
            .context("failed to create HTTP client")?;
        std::fs::create_dir_all(&paths.root)
            .with_context(|| format!("cannot create {}", paths.root.display()))?;
        Ok(Self {
            paths,
            downloader: Downloader::new(http, concurrency),
            manifest: Mutex::new(None),
        })
    }

    pub fn paths(&self) -> &Paths {
        &self.paths
    }

    pub fn http(&self) -> &reqwest::Client {
        self.downloader.client()
    }

    pub fn downloader(&self) -> &Downloader {
        &self.downloader
    }

    pub fn instances(&self) -> InstanceStore {
        InstanceStore::new(self.paths.instances())
    }

    /// Mojang's list of every version. Cached in memory, and on disk for
    /// offline use.
    pub async fn manifest(&self, refresh: bool) -> Result<Arc<VersionManifest>> {
        let mut guard = self.manifest.lock().await;
        if !refresh {
            if let Some(m) = guard.as_ref() {
                return Ok(m.clone());
            }
        }
        let bytes = self
            .downloader
            .get_cached(
                VERSION_MANIFEST_URL,
                &self.paths.cache().join("version_manifest_v2.json"),
            )
            .await?;
        let manifest: VersionManifest =
            serde_json::from_slice(&bytes).context("invalid version manifest")?;
        let manifest = Arc::new(manifest);
        *guard = Some(manifest.clone());
        Ok(manifest)
    }

    /// Ids of versions with a JSON on disk (vanilla, Fabric, or copied in).
    pub fn installed_versions(&self) -> Vec<String> {
        let Ok(entries) = std::fs::read_dir(self.paths.versions()) else {
            return Vec::new();
        };
        let mut ids: Vec<String> = entries
            .flatten()
            .filter_map(|e| e.file_name().into_string().ok())
            .filter(|id| self.paths.version_json(id).is_file())
            .collect();
        ids.sort();
        ids
    }

    async fn load_version_json(&self, id: &str) -> Result<VersionJson> {
        let path = self.paths.version_json(id);
        let manifest = self.manifest(false).await.ok();
        let entry = manifest.as_ref().and_then(|m| m.find(id)).cloned();

        if path.is_file() {
            let fresh = match entry.as_ref().and_then(|e| e.sha1.clone()) {
                Some(expected) => {
                    let p = path.clone();
                    tokio::task::spawn_blocking(move || sha1_file(&p))
                        .await?
                        .map(|actual| actual.eq_ignore_ascii_case(&expected))
                        .unwrap_or(false)
                }
                None => true,
            };
            if fresh {
                let bytes = tokio::fs::read(&path).await?;
                return serde_json::from_slice(&bytes)
                    .with_context(|| format!("invalid version JSON {}", path.display()));
            }
        }

        let entry = entry.ok_or_else(|| {
            anyhow!("unknown version '{id}': not installed and not in Mojang's version manifest")
        })?;
        let resp = self
            .http()
            .get(&entry.url)
            .send()
            .await?
            .error_for_status()?;
        let bytes = resp.bytes().await?;
        if let Some(expected) = &entry.sha1 {
            use sha1::{Digest, Sha1};
            let actual = hex::encode(Sha1::digest(&bytes));
            if !actual.eq_ignore_ascii_case(expected) {
                bail!("version JSON for {id} failed its checksum");
            }
        }
        let version: VersionJson =
            serde_json::from_slice(&bytes).with_context(|| format!("invalid version JSON for {id}"))?;
        write_atomic(&path, &bytes).await?;
        Ok(version)
    }

    /// Load a version JSON and merge its whole `inheritsFrom` chain.
    pub async fn resolve_version(&self, id: &str) -> Result<VersionJson> {
        let mut version = self.load_version_json(id).await?;
        let mut depth = 0;
        while let Some(parent_id) = version.inherits_from.clone() {
            depth += 1;
            if depth > 8 {
                bail!("inheritsFrom chain too deep for {id}");
            }
            let parent = self.load_version_json(&parent_id).await?;
            version = version.merge_onto(parent);
        }
        Ok(version)
    }

    /// Download everything a version needs (except Java).
    pub async fn install_version(&self, id: &str, progress: &ProgressFn) -> Result<InstalledVersion> {
        report(progress, &format!("Resolving {id}"), 0, 0);
        let version = self.resolve_version(id).await?;
        install::install(&self.downloader, &self.paths, version, progress).await
    }

    /// Version id to launch for an instance (installs the loader if needed).
    pub async fn version_id_for(&self, instance: &Instance, progress: &ProgressFn) -> Result<String> {
        match &instance.loader {
            Loader::Vanilla => Ok(instance.minecraft_version.clone()),
            Loader::Fabric { version } => {
                fabric::install_profile(
                    self.http(),
                    &self.paths,
                    &instance.minecraft_version,
                    version.as_deref(),
                )
                .await
            }
            Loader::Forge { version } => {
                forge::install(self, &instance.minecraft_version, version.as_deref(), progress).await
            }
        }
    }

    /// The Java executable to use: explicit override, else the Mojang runtime
    /// the version asks for, else a system Java as a last resort.
    pub async fn java_for(
        &self,
        installed: &InstalledVersion,
        override_path: Option<&Path>,
        progress: &ProgressFn,
    ) -> Result<PathBuf> {
        if let Some(p) = override_path {
            if p.is_file() {
                return Ok(p.to_path_buf());
            }
            bail!("Java not found at {}", p.display());
        }
        let (component, major) = installed.version.java_requirement();
        match java::ensure_runtime(&self.downloader, &self.paths, &component, progress).await {
            Ok(p) => Ok(p),
            Err(e) => match java::find_system_java() {
                Some(p) => {
                    report(progress, &format!("Mojang Java {major} unavailable, using system Java"), 0, 0);
                    Ok(p)
                }
                None => Err(e.context(format!("no Java {major} runtime available"))),
            },
        }
    }

    /// Build launch credentials, refreshing Microsoft tokens if needed.
    /// The account is updated in place; save the account store afterwards.
    pub async fn session_for(&self, account: &mut Account, settings: &Settings) -> Result<LaunchSession> {
        if account.needs_refresh() {
            let client_id = settings.client_id().ok_or_else(|| {
                anyhow!("set a Microsoft client id in Settings to refresh this account")
            })?;
            auth::refresh_microsoft(self.http(), &client_id, account).await?;
        }
        Ok(match &account.kind {
            AccountKind::Offline => LaunchSession::offline(&account.username),
            AccountKind::Microsoft(t) => LaunchSession {
                username: account.username.clone(),
                uuid: account.uuid.clone(),
                access_token: t.minecraft_token.clone(),
                user_type: "msa".to_string(),
                xuid: "0".to_string(),
            },
        })
    }

    /// Install (if needed) and build the launch command for an instance.
    ///
    /// With fast launch on, a launch plan saved by the last successful
    /// prepare is reused when it still matches, so nothing is fetched or
    /// hashed and the game starts straight away.
    pub async fn prepare_launch(
        &self,
        instance: &Instance,
        session: &LaunchSession,
        settings: &Settings,
        progress: &ProgressFn,
    ) -> Result<LaunchCommand> {
        let key = fastlaunch::key(instance, settings);
        let plan_path = fastlaunch::plan_path(&self.paths, instance);
        let cached = if settings.fast_launch { LaunchPlan::load(&plan_path, &key) } else { None };
        let game_dir = instance.game_dir();
        std::fs::create_dir_all(&game_dir)?;

        let (installed, java) = match cached {
            Some(plan) => {
                report(progress, "Fast launch", 1, 1);
                (plan.installed(), plan.java().to_path_buf())
            }
            None => {
                let id = self.version_id_for(instance, progress).await?;
                let installed = self.install_version(&id, progress).await?;
                let java_override = instance.java_path.as_deref().or(settings.java_path.as_deref());
                let java = self.java_for(&installed, java_override, progress).await?;
                if installed.assets.map_to_resources {
                    report(progress, "Copying legacy resources", 0, 0);
                    let paths = self.paths.clone();
                    let index = installed.assets.clone();
                    let dest = game_dir.join("resources");
                    tokio::task::spawn_blocking(move || assets::copy_by_name(&paths, &index, &dest)).await??;
                }
                LaunchPlan::new(&installed, &java, key).save(&plan_path);
                (installed, java)
            }
        };

        let mut extra_jvm_args = settings.extra_jvm_args.clone();
        extra_jvm_args.extend(instance.jvm_args.iter().cloned());
        if settings.tuned_jvm {
            let (_, major) = installed.version.java_requirement();
            let cds = self.paths.cache().join("cds").join(format!("{}-{}.jsa", installed.id.replace(['/', '\\', ':'], "_"), crate::LAUNCHER_VERSION));
            // The game's own archive needs the JDK's base one under it.
            let base = settings.fast_launch && {
                let j = java.clone();
                tokio::task::spawn_blocking(move || fastlaunch::ensure_base_cds(&j, major)).await.unwrap_or(false)
            };
            let tuned = fastlaunch::tuned_jvm_args(major, &extra_jvm_args, base.then_some(cds.as_path()));
            extra_jvm_args.splice(0..0, tuned);
        }
        let opts = LaunchOptions {
            java,
            game_dir,
            min_memory_mb: settings.min_memory_mb,
            max_memory_mb: instance.memory_mb.unwrap_or(settings.max_memory_mb),
            extra_jvm_args,
            resolution: instance.resolution.or_else(|| settings.resolution()),
            fullscreen: settings.fullscreen,
            join_server: instance.join_server.clone(),
        };
        // Newer versions take fullscreen from the game's own options.txt over
        // the --fullscreen flag, so the launcher's choice is written there too.
        set_game_option(&opts.game_dir, "fullscreen", if settings.fullscreen { "true" } else { "false" });
        report(progress, "Ready", 1, 1);
        launch::build_command(&installed, &self.paths, session, &opts)
    }

    /// Read the files an instance's last launch used, so the next start
    /// loads them from memory. Returns the bytes read (0 if no plan yet).
    pub async fn warm_up(&self, instance: &Instance, settings: &Settings) -> u64 {
        let key = fastlaunch::key(instance, settings);
        let path = fastlaunch::plan_path(&self.paths, instance);
        tokio::task::spawn_blocking(move || {
            LaunchPlan::load(&path, &key)
                .map(|p| {
                    let (_, major) = p.installed().version.java_requirement();
                    fastlaunch::ensure_base_cds(p.java(), major);
                    fastlaunch::warm(&p)
                })
                .unwrap_or(0)
        })
            .await
            .unwrap_or(0)
    }

    /// Make the next launch of an instance check and repair every file.
    pub fn forget_launch_plan(&self, instance: &Instance) {
        fastlaunch::forget(&self.paths, instance);
    }
}

/// Set one `key:value` line in a game folder's options.txt, adding it if
/// missing and leaving every other option as the player left it.
pub fn set_game_option(game_dir: &std::path::Path, key: &str, value: &str) {
    let path = game_dir.join("options.txt");
    let text = std::fs::read_to_string(&path).unwrap_or_default();
    let prefix = format!("{key}:");
    let mut found = false;
    let mut lines: Vec<String> = text
        .lines()
        .map(|l| {
            if l.starts_with(&prefix) {
                found = true;
                format!("{key}:{value}")
            } else {
                l.to_string()
            }
        })
        .collect();
    if !found {
        lines.push(format!("{key}:{value}"));
    }
    let _ = std::fs::write(&path, lines.join("\n") + "\n");
}

#[cfg(test)]
mod option_tests {
    use super::set_game_option;

    #[test]
    fn writes_and_replaces_an_option() {
        let dir = std::env::temp_dir().join(format!("vortex-opt-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        std::fs::write(dir.join("options.txt"), "version:3955\nfullscreen:false\nfov:0.5\n").unwrap();
        set_game_option(&dir, "fullscreen", "true");
        let t = std::fs::read_to_string(dir.join("options.txt")).unwrap();
        assert_eq!(t, "version:3955\nfullscreen:true\nfov:0.5\n");
        std::fs::remove_file(dir.join("options.txt")).unwrap();
        set_game_option(&dir, "fullscreen", "true");
        assert_eq!(std::fs::read_to_string(dir.join("options.txt")).unwrap(), "fullscreen:true\n");
        let _ = std::fs::remove_dir_all(&dir);
    }
}
