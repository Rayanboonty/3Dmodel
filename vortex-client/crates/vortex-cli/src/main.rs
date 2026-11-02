//! `vortex-cli` — scriptable front-end for the Vortex Client.
//!
//! ```text
//! vortex-cli versions --kind all
//! vortex-cli install 1.8.9
//! vortex-cli offline Steve
//! vortex-cli create "PvP" 1.8.9
//! vortex-cli launch "PvP"
//! vortex-cli launch 1.21.4 --fabric      # auto-creates an instance
//! ```

use std::io::{IsTerminal, Write};
use std::path::PathBuf;
use std::sync::{Arc, Mutex};

use anyhow::{anyhow, bail, Result};
use clap::{Parser, Subcommand, ValueEnum};
use vortex_core::accounts::AccountStore;
use vortex_core::instance::{Instance, Loader};
use vortex_core::manifest::VersionType;
use vortex_core::paths::Paths;
use vortex_core::progress::{Progress, ProgressFn};
use vortex_core::settings::Settings;
use vortex_core::{auth, fabric, launch, Launcher};

#[derive(Parser)]
#[command(name = "vortex-cli", version, about = "Vortex Client — install and launch any Minecraft version")]
struct Cli {
    /// Data directory (default: %APPDATA%\VortexClient or $VORTEX_HOME).
    #[arg(long, global = true)]
    home: Option<PathBuf>,
    #[command(subcommand)]
    command: Cmd,
}

#[derive(Clone, Copy, ValueEnum)]
enum Kind {
    Release,
    Snapshot,
    Beta,
    Alpha,
    All,
}

#[derive(Subcommand)]
enum Cmd {
    /// List Minecraft versions from Mojang.
    Versions {
        #[arg(long, value_enum, default_value = "release")]
        kind: Kind,
        /// Show at most N versions (0 = all).
        #[arg(long, default_value_t = 0)]
        limit: usize,
    },
    /// List Fabric loader versions for a Minecraft version.
    FabricVersions { minecraft: String },
    /// Download a version (and its Java runtime) without launching.
    Install {
        version: String,
        /// Install with the Fabric loader (latest stable unless --loader).
        #[arg(long)]
        fabric: bool,
        #[arg(long)]
        loader: Option<String>,
    },
    /// List instances.
    Instances,
    /// Create an instance.
    Create {
        name: String,
        version: String,
        #[arg(long)]
        fabric: bool,
        #[arg(long)]
        loader: Option<String>,
        #[arg(long)]
        memory: Option<u32>,
        /// Server to join on launch (host[:port]).
        #[arg(long)]
        server: Option<String>,
    },
    /// Delete an instance and its game folder.
    Delete { name: String },
    /// Launch an instance (or a version: an instance is created for it).
    Launch {
        target: String,
        #[arg(long)]
        fabric: bool,
        /// Play as this offline username instead of the selected account.
        #[arg(long)]
        offline: Option<String>,
        /// Use the saved account with this username.
        #[arg(long)]
        account: Option<String>,
        /// Override max memory (MB).
        #[arg(long)]
        memory: Option<u32>,
        /// Join a server on start (host[:port]).
        #[arg(long)]
        server: Option<String>,
        /// Print the command instead of running it.
        #[arg(long)]
        dry_run: bool,
    },
    /// List saved accounts.
    Accounts,
    /// Add an offline account (singleplayer / LAN testing).
    Offline { username: String },
    /// Sign in with a Microsoft account (device code flow).
    Login {
        /// Azure client id (else settings / VORTEX_MS_CLIENT_ID).
        #[arg(long)]
        client_id: Option<String>,
    },
    /// Select the default account.
    Select { username: String },
    /// Remove a saved account.
    Logout { username: String },
    /// Show where Vortex keeps its files.
    Paths,
}

fn progress_printer() -> ProgressFn {
    let tty = std::io::stderr().is_terminal();
    let last_stage = Arc::new(Mutex::new(String::new()));
    Arc::new(move |p: Progress| {
        let line = if p.total > 0 {
            format!("{} {}/{} ({:.0}%)", p.stage, p.done, p.total, p.fraction() * 100.0)
        } else {
            p.stage.clone()
        };
        let finished = p.total == 0 || p.done == p.total;
        let mut last_stage = last_stage.lock().unwrap();
        let new_stage = *last_stage != p.stage;
        if tty {
            // Redraw one status line; keep it when a stage completes.
            eprint!("\r\x1b[2K{line}");
            if finished {
                eprintln!();
            }
            let _ = std::io::stderr().flush();
        } else if new_stage || (finished && p.total > 0) {
            // Logs/pipes: one line per stage start and per stage end.
            eprintln!("{line}");
        }
        *last_stage = p.stage.clone();
    })
}

fn loader_from(fabric: bool, loader: Option<String>) -> Loader {
    if fabric || loader.is_some() {
        Loader::Fabric { version: loader }
    } else {
        Loader::Vanilla
    }
}

#[tokio::main]
async fn main() -> Result<()> {
    let cli = Cli::parse();
    let paths = Paths::new(cli.home.clone().unwrap_or_else(Paths::default_root));
    let settings = Settings::load(&paths.settings_file())?;
    let launcher = Launcher::with_concurrency(paths.clone(), settings.download_concurrency)?;
    let progress = progress_printer();

    match cli.command {
        Cmd::Versions { kind, limit } => {
            let manifest = launcher.manifest(true).await?;
            println!("latest release: {}   latest snapshot: {}", manifest.latest.release, manifest.latest.snapshot);
            let mut shown = 0;
            for v in &manifest.versions {
                let keep = match kind {
                    Kind::All => true,
                    Kind::Release => v.kind == VersionType::Release,
                    Kind::Snapshot => v.kind == VersionType::Snapshot,
                    Kind::Beta => v.kind == VersionType::OldBeta,
                    Kind::Alpha => v.kind == VersionType::OldAlpha,
                };
                if keep {
                    println!("{:<28} {:<9} {}", v.id, v.kind.label(), v.release_time.get(..10).unwrap_or(""));
                    shown += 1;
                    if limit > 0 && shown >= limit {
                        break;
                    }
                }
            }
            eprintln!("{shown} version(s) shown, {} known in total", manifest.versions.len());
        }
        Cmd::FabricVersions { minecraft } => {
            let versions = fabric::loader_versions(launcher.http(), &minecraft).await?;
            if versions.is_empty() {
                bail!("Fabric does not support {minecraft}");
            }
            for l in versions.iter().take(20) {
                println!("{}{}", l.version, if l.stable { "" } else { "  (beta)" });
            }
        }
        Cmd::Install { version, fabric, loader } => {
            let mut instance = Instance::new(&version, &version, loader_from(fabric, loader));
            instance.dir = paths.instances().join("_unused");
            let id = launcher.version_id_for(&instance).await?;
            let installed = launcher.install_version(&id, &progress).await?;
            let java = launcher.java_for(&installed, settings.java_path.as_deref(), &progress).await?;
            println!("installed {id}");
            println!("  libraries: {}", installed.classpath.len());
            println!("  assets:    {} ({} objects)", installed.asset_index_id, installed.assets.objects.len());
            println!("  java:      {}", java.display());
        }
        Cmd::Instances => {
            let list = launcher.instances().list()?;
            if list.is_empty() {
                println!("no instances yet — try: vortex-cli create MyWorld 1.21.4");
            }
            for i in list {
                println!("{:<24} {:<14} {:<16} {}", i.name, i.minecraft_version, i.loader.label(), i.dir.display());
            }
        }
        Cmd::Create { name, version, fabric, loader, memory, server } => {
            let manifest = launcher.manifest(false).await?;
            if manifest.find(&version).is_none() && !paths.version_json(&version).is_file() {
                bail!("unknown Minecraft version '{version}' (see `vortex-cli versions --kind all`)");
            }
            let mut inst = Instance::new(&name, &version, loader_from(fabric, loader));
            inst.memory_mb = memory;
            inst.join_server = server;
            let inst = launcher.instances().create(inst)?;
            println!("created '{}' at {}", inst.name, inst.dir.display());
        }
        Cmd::Delete { name } => {
            let store = launcher.instances();
            let inst = store.find(&name)?.ok_or_else(|| anyhow!("no instance named '{name}'"))?;
            store.delete(&inst)?;
            println!("deleted '{}'", inst.name);
        }
        Cmd::Launch { target, fabric, offline, account, memory, server, dry_run } => {
            let store = launcher.instances();
            let mut instance = match store.find(&target)? {
                Some(i) => i,
                None => {
                    let manifest = launcher.manifest(false).await?;
                    if manifest.find(&target).is_none() && !paths.version_json(&target).is_file() {
                        bail!("'{target}' is neither an instance nor a known version");
                    }
                    let loader = loader_from(fabric, None);
                    let name = if fabric { format!("{target} (Fabric)") } else { target.clone() };
                    match store.find(&name)? {
                        Some(i) => i,
                        None => store.create(Instance::new(&name, &target, loader))?,
                    }
                }
            };
            // CLI overrides apply to this launch only; they are not saved.
            let mut saved = instance.clone();
            if memory.is_some() {
                instance.memory_mb = memory;
            }
            if server.is_some() {
                instance.join_server = server;
            }

            let mut accounts = AccountStore::load(&paths.accounts_file())?;
            let session = if let Some(name) = offline {
                auth::validate_username(&name)?;
                launch::LaunchSession::offline(&name)
            } else {
                let uuid = match account {
                    Some(name) => accounts
                        .find_by_name(&name)
                        .ok_or_else(|| anyhow!("no saved account '{name}'"))?
                        .uuid
                        .clone(),
                    None => accounts
                        .selected()
                        .ok_or_else(|| anyhow!("no account — run `vortex-cli offline <name>` or `vortex-cli login`, or pass --offline <name>"))?
                        .uuid
                        .clone(),
                };
                let acc = accounts.accounts.iter_mut().find(|a| a.uuid == uuid).unwrap();
                let before = acc.clone();
                let session = launcher.session_for(acc, &settings).await?;
                if *acc != before {
                    accounts.save()?;
                }
                session
            };

            let cmd = launcher.prepare_launch(&instance, &session, &settings, &progress).await?;
            if dry_run {
                println!("{}", cmd.display_redacted());
                return Ok(());
            }
            saved.last_played = Some(auth::now_secs());
            saved.save()?;
            eprintln!("launching {} as {} …", instance.name, session.username);
            let mut game = launch::spawn(&cmd)?;
            while let Some(line) = game.logs.recv().await {
                println!("{}", line.text);
            }
            let status = game.wait().await?;
            eprintln!("game exited with {status}");
            if !status.success() {
                std::process::exit(status.code().unwrap_or(1));
            }
        }
        Cmd::Accounts => {
            let accounts = AccountStore::load(&paths.accounts_file())?;
            if accounts.accounts.is_empty() {
                println!("no accounts — `vortex-cli offline <name>` or `vortex-cli login`");
            }
            for a in &accounts.accounts {
                let mark = if accounts.selected.as_deref() == Some(&a.uuid) { "*" } else { " " };
                println!("{mark} {:<16} {:<10} {}", a.username, a.kind_label(), auth::dashed_uuid(&a.uuid));
            }
        }
        Cmd::Offline { username } => {
            let mut accounts = AccountStore::load(&paths.accounts_file())?;
            accounts.upsert(auth::offline_account(&username)?);
            accounts.save()?;
            println!("added offline account {username} (selected)");
        }
        Cmd::Login { client_id } => {
            let client_id = client_id
                .or_else(|| settings.client_id())
                .ok_or_else(|| anyhow!("no Microsoft client id: pass --client-id or set VORTEX_MS_CLIENT_ID (see README)"))?;
            let code = auth::start_device_login(launcher.http(), &client_id).await?;
            println!("To sign in, open {} and enter the code: {}", code.verification_uri, code.user_code);
            let account = auth::finish_device_login(launcher.http(), &client_id, &code).await?;
            let mut accounts = AccountStore::load(&paths.accounts_file())?;
            println!("signed in as {}", account.username);
            accounts.upsert(account);
            accounts.save()?;
        }
        Cmd::Select { username } => {
            let mut accounts = AccountStore::load(&paths.accounts_file())?;
            let uuid = accounts
                .find_by_name(&username)
                .ok_or_else(|| anyhow!("no saved account '{username}'"))?
                .uuid
                .clone();
            accounts.select(&uuid);
            accounts.save()?;
            println!("selected {username}");
        }
        Cmd::Logout { username } => {
            let mut accounts = AccountStore::load(&paths.accounts_file())?;
            let uuid = accounts
                .find_by_name(&username)
                .ok_or_else(|| anyhow!("no saved account '{username}'"))?
                .uuid
                .clone();
            accounts.remove(&uuid);
            accounts.save()?;
            println!("removed {username}");
        }
        Cmd::Paths => {
            println!("data:      {}", paths.root.display());
            println!("instances: {}", paths.instances().display());
            println!("versions:  {}", paths.versions().display());
            println!("runtimes:  {}", paths.runtimes().display());
        }
    }
    Ok(())
}
