//! Vortex — desktop launcher (Tauri). The UI lives in `ui/`; this file
//! exposes `vortex-core` to it as Tauri commands and streams launch progress,
//! game logs and game state back as events.

#![cfg_attr(all(windows, not(debug_assertions)), windows_subsystem = "windows")]

use std::collections::HashMap;
use std::path::Path;
use std::sync::{Arc, Mutex as StdMutex};
use std::time::{Duration, Instant};

use serde::Serialize;

mod updater;
mod installer;
mod music;
use tauri::{AppHandle, Emitter, Manager, State};
use tokio::sync::{oneshot, Mutex};
use vortex_core::accounts::{Account, AccountKind, AccountStore};
use vortex_core::instance::{Instance, Loader};
use vortex_core::loaders::{self, LoaderKind, LoaderVersion};
use vortex_core::logs::{LogLevel, LogLine};
use vortex_core::manifest::VersionType;
use vortex_core::modrinth::{self, ProjectKind};
use vortex_core::mods::{self, InstalledMod};
use vortex_core::news::{self, NewsEntry};
use vortex_core::paths::Paths;
use vortex_core::progress::{Progress, ProgressFn};
use vortex_core::settings::Settings;
use vortex_core::skins::{self, Skins};
use vortex_core::servers;
use vortex_core::{auth, launch, Launcher, LAUNCHER_VERSION};

type CmdResult<T> = Result<T, String>;

fn fail(e: anyhow::Error) -> String {
    format!("{e:#}")
}

struct AppState {
    launcher: Arc<Launcher>,
    settings: Mutex<Settings>,
    accounts: Mutex<AccountStore>,
    running: Mutex<HashMap<String, oneshot::Sender<()>>>,
}

// ------------------------------------------------------------------ views --

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct AccountView {
    uuid: String,
    username: String,
    kind: &'static str,
    selected: bool,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct InstanceView {
    name: String,
    minecraft_version: String,
    loader: &'static str,
    loader_version: Option<String>,
    loader_label: String,
    memory_mb: Option<u32>,
    join_server: Option<String>,
    jvm_args: String,
    last_played: Option<u64>,
    game_dir: String,
    running: bool,
    mod_count: usize,
    pack: Option<String>,
    playtime_secs: u64,
}

#[derive(Serialize)]
struct VersionView {
    id: String,
    kind: &'static str,
    date: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct VersionList {
    latest_release: String,
    latest_snapshot: String,
    versions: Vec<VersionView>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct Bootstrap {
    app_version: &'static str,
    data_dir: String,
    platform: &'static str,
    settings: Settings,
    accounts: Vec<AccountView>,
    instances: Vec<InstanceView>,
    /// The PC's memory and cores, for automatic memory.
    system_ram_mb: Option<u64>,
    cpu_count: usize,
    auto_memory_mb: u32,
    auto_memory_modded_mb: u32,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct ProgressEvent {
    instance: String,
    stage: String,
    done: u64,
    total: u64,
}

/// What the pack install sheet shows: the list when it starts, each mod as
/// it goes (active, added, skipped, failed), then the totals.
#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct PackEvent {
    instance: String,
    phase: &'static str,
    #[serde(skip_serializing_if = "Option::is_none")]
    pack: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    version: Option<String>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    mods: Vec<PackModView>,
    #[serde(skip_serializing_if = "Option::is_none")]
    id: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    state: Option<&'static str>,
    /// The mod actually added, when an alternative stood in.
    #[serde(skip_serializing_if = "Option::is_none")]
    name: Option<String>,
    added: usize,
    skipped: usize,
    failed: usize,
}

#[derive(Clone, Serialize)]
struct PackModView {
    id: String,
    name: String,
}

impl PackEvent {
    fn new(instance: &str, phase: &'static str) -> Self {
        PackEvent { instance: instance.to_string(), phase, pack: None, version: None, mods: Vec::new(), id: None, state: None, name: None, added: 0, skipped: 0, failed: 0 }
    }
}

#[derive(Clone, Serialize)]
struct LogEvent {
    instance: String,
    lines: Vec<LogLineView>,
}

#[derive(Clone, Serialize)]
struct LogLineView {
    level: &'static str,
    text: String,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct StateEvent {
    instance: String,
    state: &'static str,
    code: Option<i32>,
    error: Option<String>,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct DeviceCodeEvent {
    user_code: String,
    verification_uri: String,
}

fn account_views(store: &AccountStore) -> Vec<AccountView> {
    store
        .accounts
        .iter()
        .map(|a| AccountView {
            uuid: a.uuid.clone(),
            username: a.username.clone(),
            kind: if a.is_microsoft() { "microsoft" } else { "offline" },
            selected: store.selected.as_deref() == Some(a.uuid.as_str()),
        })
        .collect()
}

fn count_mods(inst: &Instance) -> usize {
    std::fs::read_dir(mods::mods_dir(inst))
        .map(|rd| {
            rd.flatten()
                .filter(|e| {
                    let n = e.file_name().to_string_lossy().to_ascii_lowercase();
                    n.ends_with(".jar") || n.ends_with(".jar.disabled")
                })
                .count()
        })
        .unwrap_or(0)
}

fn instance_view(inst: &Instance, running: &HashMap<String, oneshot::Sender<()>>) -> InstanceView {
    let (loader, loader_version) = match &inst.loader {
        Loader::Vanilla => ("vanilla", None),
        Loader::Fabric { version } => ("fabric", version.clone()),
        Loader::Forge { version } => ("forge", version.clone()),
    };
    InstanceView {
        name: inst.name.clone(),
        minecraft_version: inst.minecraft_version.clone(),
        loader,
        loader_version,
        loader_label: inst.loader.label(),
        memory_mb: inst.memory_mb,
        join_server: inst.join_server.clone(),
        jvm_args: inst.jvm_args.join(" "),
        last_played: inst.last_played,
        game_dir: inst.game_dir().to_string_lossy().into_owned(),
        running: running.contains_key(&inst.name),
        mod_count: count_mods(inst),
        pack: inst.pack.clone(),
        playtime_secs: inst.playtime_secs,
    }
}

async fn instance_views(state: &AppState) -> CmdResult<Vec<InstanceView>> {
    let list = state.launcher.instances().list().map_err(fail)?;
    let running = state.running.lock().await;
    Ok(list.iter().map(|i| instance_view(i, &running)).collect())
}

fn find_instance(state: &AppState, name: &str) -> CmdResult<Instance> {
    state
        .launcher
        .instances()
        .find(name)
        .map_err(fail)?
        .ok_or_else(|| format!("no instance named '{name}'"))
}

fn level_name(l: LogLevel) -> &'static str {
    match l {
        LogLevel::Trace | LogLevel::Debug => "debug",
        LogLevel::Info => "info",
        LogLevel::Warn => "warn",
        LogLevel::Error | LogLevel::Fatal => "error",
    }
}

// ---------------------------------------------------------------- commands --

#[tauri::command]
async fn bootstrap(state: State<'_, AppState>) -> CmdResult<Bootstrap> {
    let settings = state.settings.lock().await.clone();
    let accounts = account_views(&*state.accounts.lock().await);
    Ok(Bootstrap {
        app_version: LAUNCHER_VERSION,
        data_dir: state.launcher.paths().root.to_string_lossy().into_owned(),
        platform: vortex_core::os::name(),
        settings,
        accounts,
        instances: instance_views(&state).await?,
        system_ram_mb: vortex_core::os::total_ram_mb(),
        cpu_count: vortex_core::os::cpu_count(),
        auto_memory_mb: vortex_core::os::recommended_memory_mb(vortex_core::os::total_ram_mb(), false),
        auto_memory_modded_mb: vortex_core::os::recommended_memory_mb(vortex_core::os::total_ram_mb(), true),
    })
}

#[tauri::command]
async fn list_versions(state: State<'_, AppState>, refresh: bool) -> CmdResult<VersionList> {
    let m = state.launcher.manifest(refresh).await.map_err(fail)?;
    Ok(VersionList {
        latest_release: m.latest.release.clone(),
        latest_snapshot: m.latest.snapshot.clone(),
        versions: m
            .versions
            .iter()
            .map(|v| VersionView {
                id: v.id.clone(),
                kind: match v.kind {
                    VersionType::Release => "release",
                    VersionType::Snapshot => "snapshot",
                    VersionType::OldBeta => "beta",
                    VersionType::OldAlpha => "alpha",
                    VersionType::Other => "other",
                },
                date: v.release_time.get(..10).unwrap_or_default().to_string(),
            })
            .collect(),
    })
}

#[tauri::command]
async fn loader_versions(state: State<'_, AppState>, loader: LoaderKind, game: String) -> CmdResult<Vec<LoaderVersion>> {
    loaders::versions(&state.launcher, loader, &game).await.map_err(fail)
}

#[tauri::command]
async fn list_instances(state: State<'_, AppState>) -> CmdResult<Vec<InstanceView>> {
    instance_views(&state).await
}

#[tauri::command]
async fn create_instance(
    state: State<'_, AppState>,
    name: String,
    version: String,
    loader: LoaderKind,
    loader_version: Option<String>,
    memory_mb: Option<u32>,
    pack: Option<String>,
) -> CmdResult<InstanceView> {
    let loader = match loader {
        LoaderKind::Vanilla => Loader::Vanilla,
        LoaderKind::Fabric => Loader::Fabric { version: loader_version },
        LoaderKind::Forge => Loader::Forge { version: loader_version },
    };
    let mut inst = Instance::new(name.trim(), &version, loader);
    inst.memory_mb = memory_mb;
    inst.pack = pack.filter(|p| ["pvp", "normal", "performance", "vanilla"].contains(&p.as_str()));
    let inst = state.launcher.instances().create(inst).map_err(fail)?;
    let running = state.running.lock().await;
    Ok(instance_view(&inst, &running))
}

#[tauri::command]
async fn update_instance(
    state: State<'_, AppState>,
    name: String,
    memory_mb: Option<u32>,
    join_server: Option<String>,
    jvm_args: Option<String>,
) -> CmdResult<InstanceView> {
    let mut inst = find_instance(&state, &name)?;
    inst.memory_mb = memory_mb;
    inst.join_server = join_server.map(|s| s.trim().to_string()).filter(|s| !s.is_empty());
    if let Some(args) = jvm_args {
        inst.jvm_args = args.split_whitespace().map(String::from).collect();
    }
    inst.save().map_err(fail)?;
    let running = state.running.lock().await;
    Ok(instance_view(&inst, &running))
}

#[tauri::command]
async fn delete_instance(state: State<'_, AppState>, name: String) -> CmdResult<Vec<InstanceView>> {
    if state.running.lock().await.contains_key(&name) {
        return Err("stop the game before deleting this instance".into());
    }
    let inst = find_instance(&state, &name)?;
    state.launcher.instances().delete(&inst).map_err(fail)?;
    instance_views(&state).await
}

#[tauri::command]
async fn launch_instance(app: AppHandle, state: State<'_, AppState>, name: String, server: Option<String>) -> CmdResult<()> {
    let mut instance = find_instance(&state, &name)?;
    let join_once = server.map(|s| s.trim().to_string()).filter(|s| !s.is_empty());
    let account = state
        .accounts
        .lock()
        .await
        .selected()
        .cloned()
        .ok_or("add an account first (Accounts page)")?;
    let (kill_tx, kill_rx) = oneshot::channel();
    {
        let mut running = state.running.lock().await;
        if running.contains_key(&name) {
            return Err(format!("{name} is already running"));
        }
        running.insert(name.clone(), kill_tx);
    }
    instance.last_played = Some(auth::now_secs());
    let _ = instance.save();
    let settings = state.settings.lock().await.clone();
    // A server picked on Home: join it this time (remembered as the last
    // server, which "rejoin last server" uses for a plain Play).
    if join_once.is_some() {
        instance.last_server = join_once.clone();
        let _ = instance.save();
        instance.join_server = join_once;
    } else if settings.rejoin_last_server && instance.join_server.is_none() {
        instance.join_server = instance.last_server.clone();
    }
    let launcher = state.launcher.clone();
    tauri::async_runtime::spawn(async move {
        let started = std::time::Instant::now();
        let plain = instance.clone();
        let track = settings.track_playtime;
        let result = run_game(&app, launcher.clone(), instance, account, settings, kill_rx).await;
        // Time played, added to the instance (it may have been renamed or
        // changed meanwhile: reload it first).
        if track && started.elapsed() > Duration::from_secs(20) {
            if let Ok(Some(mut inst)) = launcher.instances().find(&plain.name) {
                inst.playtime_secs += started.elapsed().as_secs();
                let _ = inst.save();
            }
        }
        // A start-up failure: check every file on the next launch.
        let quick_crash = matches!(&result, Ok(Some(c)) if *c != 0) && started.elapsed() < Duration::from_secs(25);
        if result.is_err() || quick_crash {
            launcher.forget_launch_plan(&plain);
        }
        let st = app.state::<AppState>();
        st.running.lock().await.remove(&name);
        let ev = match result {
            Ok(code) => StateEvent { instance: name, state: "exited", code, error: None },
            Err(e) => StateEvent { instance: name, state: "failed", code: None, error: Some(fail(e)) },
        };
        let _ = app.emit("game-state", ev);
    });
    Ok(())
}

#[tauri::command]
async fn stop_instance(state: State<'_, AppState>, name: String) -> CmdResult<()> {
    if let Some(kill) = state.running.lock().await.remove(&name) {
        let _ = kill.send(());
    }
    Ok(())
}

/// Pre-load the files an instance's game starts from (fast launch).
#[tauri::command]
async fn warm_up(state: State<'_, AppState>, name: String) -> CmdResult<u64> {
    let inst = find_instance(&state, &name)?;
    let settings = state.settings.lock().await.clone();
    if !settings.fast_launch {
        return Ok(0);
    }
    Ok(state.launcher.warm_up(&inst, &settings).await)
}

/// Check and repair every file on the next launch of an instance.
#[tauri::command]
async fn verify_instance(state: State<'_, AppState>, name: String) -> CmdResult<()> {
    let inst = find_instance(&state, &name)?;
    state.launcher.forget_launch_plan(&inst);
    Ok(())
}

/// Players online, message of the day, icon and ping for a server.
#[tauri::command]
async fn server_status(address: String) -> CmdResult<servers::ServerStatus> {
    servers::ping(&address).await.map_err(fail)
}

/// Is there a newer launcher on GitHub Releases?
#[tauri::command]
async fn check_update(state: State<'_, AppState>) -> CmdResult<updater::UpdateInfo> {
    updater::check(state.launcher.http(), LAUNCHER_VERSION).await.map_err(fail)
}

#[derive(Clone, Serialize)]
struct UpdateProgress {
    stage: &'static str,
    done: u64,
    total: u64,
}

/// Whether the launcher is installed as an app, and where.
#[tauri::command]
fn install_status() -> installer::InstallStatus {
    installer::status()
}

/// Install the launcher as an app, start the installed copy, quit this one.
#[tauri::command]
async fn install_app(app: AppHandle, desktop: bool, start_menu: bool) -> CmdResult<()> {
    let exe = tokio::task::spawn_blocking(move || installer::install(LAUNCHER_VERSION, desktop, start_menu))
        .await
        .map_err(|e| e.to_string())?
        .map_err(fail)?;
    installer::start(&exe).map_err(|e| format!("installed, but couldn't start it: {e}"))?;
    let quit = app.clone();
    tauri::async_runtime::spawn(async move {
        tokio::time::sleep(Duration::from_millis(300)).await;
        quit.exit(0);
    });
    Ok(())
}

/// Remove the installed app (shortcuts, Apps entry, files) and quit.
#[tauri::command]
async fn uninstall_app(app: AppHandle) -> CmdResult<()> {
    tokio::task::spawn_blocking(installer::uninstall).await.map_err(|e| e.to_string())?.map_err(fail)?;
    let quit = app.clone();
    tauri::async_runtime::spawn(async move {
        tokio::time::sleep(Duration::from_millis(200)).await;
        quit.exit(0);
    });
    Ok(())
}

/// Quit the launcher (after the player confirmed closing while playing).
#[tauri::command]
fn quit_app(app: AppHandle) {
    app.exit(0);
}

/// Start the launcher with Windows, or stop doing so.
#[tauri::command]
fn set_autostart(on: bool) -> CmdResult<bool> {
    installer::set_autostart(on).map_err(fail)?;
    Ok(installer::autostart_enabled())
}

#[tauri::command]
async fn install_update(app: AppHandle, state: State<'_, AppState>, url: String, version: String) -> CmdResult<()> {
    if !state.running.lock().await.is_empty() {
        return Err("close the game before updating".into());
    }
    if !url.starts_with("https://github.com/") && !url.starts_with("https://objects.githubusercontent.com/") {
        return Err("updates only come from GitHub".into());
    }
    let (dir, exe) = updater::current_exe().map_err(fail)?;
    let safe: String = version.chars().filter(|c| c.is_ascii_alphanumeric() || *c == '.' || *c == '-').collect();
    let dest = state.launcher.paths().cache().join("update").join(format!("Vortex-{safe}.download"));
    let emitter = app.clone();
    let mut last = Instant::now() - Duration::from_secs(1);
    updater::download(state.launcher.http(), &url, &dest, |done, total| {
        if last.elapsed() > Duration::from_millis(80) || done == total {
            last = Instant::now();
            let _ = emitter.emit("update-progress", UpdateProgress { stage: "download", done, total });
        }
    })
    .await
    .map_err(fail)?;
    let _ = app.emit("update-progress", UpdateProgress { stage: "install", done: 1, total: 1 });
    let zip = dest.clone();
    let new_exe = tokio::task::spawn_blocking(move || updater::apply(&zip, &dir, &exe))
        .await
        .map_err(|e| e.to_string())?
        .map_err(fail)?;
    let _ = std::fs::remove_file(&dest);
    let _ = app.emit("update-progress", UpdateProgress { stage: "restart", done: 1, total: 1 });
    std::process::Command::new(&new_exe).spawn().map_err(|e| format!("couldn't start the new version: {e}"))?;
    let quit = app.clone();
    tauri::async_runtime::spawn(async move {
        tokio::time::sleep(Duration::from_millis(400)).await;
        quit.exit(0);
    });
    Ok(())
}

#[tauri::command]
async fn add_offline_account(state: State<'_, AppState>, username: String) -> CmdResult<Vec<AccountView>> {
    let acc = auth::offline_account(username.trim()).map_err(fail)?;
    let mut store = state.accounts.lock().await;
    store.upsert(acc);
    store.save().map_err(fail)?;
    Ok(account_views(&store))
}

#[tauri::command]
async fn remove_account(state: State<'_, AppState>, uuid: String) -> CmdResult<Vec<AccountView>> {
    let mut store = state.accounts.lock().await;
    store.remove(&uuid);
    store.save().map_err(fail)?;
    Ok(account_views(&store))
}

#[tauri::command]
async fn select_account(state: State<'_, AppState>, uuid: String) -> CmdResult<Vec<AccountView>> {
    let mut store = state.accounts.lock().await;
    store.select(&uuid);
    store.save().map_err(fail)?;
    Ok(account_views(&store))
}

#[tauri::command]
async fn microsoft_login(app: AppHandle, state: State<'_, AppState>) -> CmdResult<Vec<AccountView>> {
    let client_id = state
        .settings
        .lock()
        .await
        .client_id()
        .ok_or("set your Microsoft (Azure) client ID in Settings first — see the README")?;
    let http = state.launcher.http().clone();
    let code = auth::start_device_login(&http, &client_id).await.map_err(fail)?;
    let _ = app.emit(
        "device-code",
        DeviceCodeEvent { user_code: code.user_code.clone(), verification_uri: code.verification_uri.clone() },
    );
    let account = auth::finish_device_login(&http, &client_id, &code).await.map_err(fail)?;
    let mut store = state.accounts.lock().await;
    store.upsert(account);
    store.save().map_err(fail)?;
    Ok(account_views(&store))
}

#[tauri::command]
async fn save_settings(state: State<'_, AppState>, settings: Settings) -> CmdResult<Settings> {
    let mut s = settings;
    s.max_memory_mb = s.max_memory_mb.clamp(512, 65536);
    s.min_memory_mb = s.min_memory_mb.clamp(256, s.max_memory_mb);
    s.download_concurrency = s.download_concurrency.clamp(1, 64);
    s.day_length_min = s.day_length_min.clamp(1, 120);
    s.glass = s.glass.min(100);
    s.logo_style = s.logo_style.min(10);
    s.game_width = s.game_width.map(|w| w.clamp(320, 7680));
    s.game_height = s.game_height.map(|h| h.clamp(240, 4320));
    if !["keep", "minimize", "hide"].contains(&s.after_launch.as_str()) {
        s.after_launch = "keep".into();
    }
    s.save(&state.launcher.paths().settings_file()).map_err(fail)?;
    *state.settings.lock().await = s.clone();
    Ok(s)
}

#[tauri::command]
async fn news(state: State<'_, AppState>) -> CmdResult<Vec<NewsEntry>> {
    news::patch_notes(&state.launcher, 12).await.map_err(fail)
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ContentView {
    #[serde(flatten)]
    item: InstalledMod,
    icon_url: Option<String>,
    project_id: Option<String>,
}

fn content_dir(inst: &Instance, kind: Option<&str>) -> CmdResult<(ProjectKind, std::path::PathBuf)> {
    let kind = ProjectKind::parse(kind.unwrap_or("mod")).map_err(fail)?;
    Ok((kind, kind.dir(inst)))
}

fn content_views(inst: &Instance, kind: ProjectKind, dir: &Path) -> CmdResult<Vec<ContentView>> {
    let index = modrinth::installed(inst);
    let list = mods::list(dir).map_err(fail)?;
    Ok(list
        .into_iter()
        .map(|item| {
            let base = item.file.trim_end_matches(".disabled");
            let entry = index.files.get(&format!("{}/{}", kind.folder(), base));
            ContentView {
                icon_url: entry.and_then(|e| e.icon_url.clone()),
                project_id: entry.map(|e| e.project_id.clone()),
                item: InstalledMod { name: entry.map(|e| e.title.clone()).filter(|_| item.loader == "unknown").unwrap_or(item.name.clone()), ..item },
            }
        })
        .collect())
}

#[tauri::command]
async fn list_mods(state: State<'_, AppState>, name: String, kind: Option<String>) -> CmdResult<Vec<ContentView>> {
    let inst = find_instance(&state, &name)?;
    let (kind, dir) = content_dir(&inst, kind.as_deref())?;
    tauri::async_runtime::spawn_blocking(move || content_views(&inst, kind, &dir))
        .await
        .map_err(|e| e.to_string())?
}

#[tauri::command]
async fn toggle_mod(state: State<'_, AppState>, name: String, file: String, enabled: bool, kind: Option<String>) -> CmdResult<Vec<ContentView>> {
    let inst = find_instance(&state, &name)?;
    let (kind, dir) = content_dir(&inst, kind.as_deref())?;
    mods::set_enabled(&dir, &file, enabled).map_err(fail)?;
    content_views(&inst, kind, &dir)
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct MusicLibrary {
    dir: String,
    tracks: Vec<music::Track>,
}

/// The songs in a folder (the system Music folder unless one is given).
#[tauri::command]
async fn music_library(dir: Option<String>) -> CmdResult<MusicLibrary> {
    let dir = dir.filter(|d| !d.trim().is_empty()).map(std::path::PathBuf::from).unwrap_or_else(music::default_dir);
    let d = dir.clone();
    let tracks = tokio::task::spawn_blocking(move || music::scan(&d)).await.map_err(|e| e.to_string())?;
    Ok(MusicLibrary { dir: dir.to_string_lossy().into_owned(), tracks })
}

/// Synced lyrics for a song (an .lrc beside it, or LRCLIB).
#[tauri::command]
async fn music_lyrics(state: State<'_, AppState>, path: Option<String>, artist: String, title: String, duration: Option<f64>) -> CmdResult<music::Lyrics> {
    music::lyrics(state.launcher.http(), path.as_deref(), &artist, &title, duration).await.map_err(fail)
}

/// Free internet radio stations (radio-browser.info).
#[tauri::command]
async fn music_radio(state: State<'_, AppState>, query: String, tag: String) -> CmdResult<Vec<music::Station>> {
    music::radio(state.launcher.http(), &query, &tag).await.map_err(fail)
}

/// What's playing, for the game's Now Playing overlay.
#[tauri::command]
async fn music_state(state: State<'_, AppState>, now: serde_json::Value) -> CmdResult<()> {
    music::write_now_playing(&state.launcher.paths().root, &now).map_err(|e| e.to_string())
}

/// Play/pause, next and previous pressed in the game.
#[tauri::command]
async fn music_commands(state: State<'_, AppState>) -> CmdResult<Vec<String>> {
    Ok(music::take_commands(&state.launcher.paths().root))
}

/// Texture packs Minecraft will use in an instance (file names, lowest
/// priority first).
#[tauri::command]
async fn active_packs(state: State<'_, AppState>, name: String) -> CmdResult<Vec<String>> {
    let inst = find_instance(&state, &name)?;
    Ok(vortex_core::active_resource_packs(&inst.game_dir()))
}

/// Turn on exactly these texture packs for the next launch.
#[tauri::command]
async fn set_active_packs(state: State<'_, AppState>, name: String, files: Vec<String>) -> CmdResult<Vec<String>> {
    let inst = find_instance(&state, &name)?;
    let dir = inst.game_dir();
    let present: Vec<String> = files.into_iter().filter(|f| !f.contains(['/', '\\']) && dir.join("resourcepacks").join(f).exists()).collect();
    vortex_core::set_active_resource_packs(&dir, &present, matches!(inst.loader, Loader::Fabric { .. }));
    Ok(vortex_core::active_resource_packs(&dir))
}

#[tauri::command]
async fn delete_mod(state: State<'_, AppState>, name: String, file: String, kind: Option<String>) -> CmdResult<Vec<ContentView>> {
    let inst = find_instance(&state, &name)?;
    let (kind, dir) = content_dir(&inst, kind.as_deref())?;
    mods::delete(&dir, &file).map_err(fail)?;
    content_views(&inst, kind, &dir)
}

// --------------------------------------------------------------- modrinth --

#[tauri::command]
async fn modrinth_search(
    state: State<'_, AppState>,
    query: String,
    kind: String,
    instance: Option<String>,
    sort: Option<String>,
    category: Option<String>,
    offset: Option<u32>,
) -> CmdResult<modrinth::SearchPage> {
    let kind = ProjectKind::parse(&kind).map_err(fail)?;
    let inst = match instance {
        Some(n) => Some(find_instance(&state, &n)?),
        None => None,
    };
    let q = modrinth::SearchQuery {
        text: query.chars().take(100).collect(),
        kind,
        game_version: inst.as_ref().map(|i| i.minecraft_version.clone()),
        loader: inst.as_ref().and_then(modrinth::loader_name).map(String::from),
        category: category.filter(|c| !c.is_empty() && c.chars().all(|ch| ch.is_ascii_alphanumeric() || ch == '-')),
        sort: modrinth::Sort::parse(sort.as_deref().unwrap_or("relevance")),
        offset: offset.unwrap_or(0),
        limit: 24,
    };
    modrinth::search(&state.launcher, &q).await.map_err(fail)
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ProjectView {
    project: modrinth::Project,
    versions: Vec<modrinth::VersionInfo>,
}

#[tauri::command]
async fn modrinth_project(state: State<'_, AppState>, id: String, kind: String, instance: Option<String>) -> CmdResult<ProjectView> {
    let kind = ProjectKind::parse(&kind).map_err(fail)?;
    let project = modrinth::project(&state.launcher, &id).await.map_err(fail)?;
    let versions = match instance {
        Some(n) => {
            let inst = find_instance(&state, &n)?;
            let target = modrinth::Target::for_instance(&inst, kind);
            modrinth::versions(&state.launcher, &project.id, &target).await.map_err(fail)?
        }
        None => Vec::new(),
    };
    Ok(ProjectView { project, versions: versions.into_iter().take(30).collect() })
}

#[tauri::command]
async fn modrinth_install(
    app: AppHandle,
    state: State<'_, AppState>,
    instance: String,
    project_id: String,
    version_id: Option<String>,
    kind: String,
) -> CmdResult<modrinth::InstallResult> {
    let kind = ProjectKind::parse(&kind).map_err(fail)?;
    let inst = find_instance(&state, &instance)?;
    let settings = state.settings.lock().await.clone();
    let progress = progress_emitter(&app, &format!("modrinth:{project_id}"));
    modrinth::install(
        &state.launcher,
        &inst,
        kind,
        &project_id,
        version_id.as_deref(),
        settings.mods_allow_unstable,
        settings.mods_with_dependencies,
        &progress,
    )
    .await
    .map_err(fail)
}

/// Project ids already installed in an instance, by kind.
#[tauri::command]
async fn modrinth_installed(state: State<'_, AppState>, instance: String) -> CmdResult<HashMap<String, Vec<String>>> {
    let inst = find_instance(&state, &instance)?;
    let index = modrinth::installed(&inst);
    let mut out: HashMap<String, Vec<String>> = HashMap::new();
    for e in index.files.values() {
        let k = match e.kind {
            ProjectKind::Mod => "mod",
            ProjectKind::Resourcepack => "resourcepack",
            ProjectKind::Shader => "shader",
        };
        out.entry(k.to_string()).or_default().push(e.project_id.clone());
    }
    Ok(out)
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct VersionArt {
    version: String,
    image: String,
}

#[tauri::command]
async fn version_art(state: State<'_, AppState>) -> CmdResult<Vec<VersionArt>> {
    let list = news::version_images(&state.launcher).await.map_err(fail)?;
    Ok(list.into_iter().map(|(version, image)| VersionArt { version, image }).collect())
}

// ------------------------------------------------------------------ skins --

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct SkinItem {
    id: String,
    name: String,
    slim: bool,
    added: u64,
    data_url: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct SkinLibraryView {
    skins: Vec<SkinItem>,
    /// Skin id chosen for the selected account.
    applied: Option<String>,
    cape: String,
    capes: Vec<&'static str>,
    /// Whether the selected account is a Microsoft account.
    microsoft: bool,
}

async fn skin_library_view(state: &AppState) -> CmdResult<SkinLibraryView> {
    let account = state.accounts.lock().await.selected().cloned();
    let library = Skins::new(&state.launcher);
    let lib = library.load();
    let skins = lib
        .skins
        .iter()
        .filter_map(|s| {
            let bytes = std::fs::read(library.png_path(&s.id).ok()?).ok()?;
            Some(SkinItem { id: s.id.clone(), name: s.name.clone(), slim: s.slim, added: s.added, data_url: png_data_url(&bytes) })
        })
        .collect();
    Ok(SkinLibraryView {
        skins,
        applied: account.as_ref().and_then(|a| lib.applied.get(&a.uuid).cloned()),
        cape: account.as_ref().map(|a| library.cape_for(&a.uuid)).unwrap_or_else(|| "none".into()),
        capes: skins::CAPES.to_vec(),
        microsoft: account.as_ref().is_some_and(|a| a.is_microsoft()),
    })
}

#[tauri::command]
async fn skin_library(state: State<'_, AppState>) -> CmdResult<SkinLibraryView> {
    skin_library_view(&state).await
}

#[tauri::command]
async fn skin_add(state: State<'_, AppState>, data_url: String, name: String, slim: bool) -> CmdResult<SkinLibraryView> {
    use base64::Engine;
    let b64 = data_url.split_once("base64,").map(|(_, b)| b).ok_or("expected a PNG image")?;
    let bytes = base64::engine::general_purpose::STANDARD.decode(b64.trim()).map_err(|e| e.to_string())?;
    Skins::new(&state.launcher).add(&bytes, &name, slim).map_err(fail)?;
    skin_library_view(&state).await
}

#[tauri::command]
async fn skin_import_files(state: State<'_, AppState>, paths: Vec<String>) -> CmdResult<SkinLibraryView> {
    let library = Skins::new(&state.launcher);
    for p in paths {
        let path = std::path::PathBuf::from(&p);
        let name = path.file_stem().and_then(|s| s.to_str()).unwrap_or("Skin").to_string();
        let bytes = skins::read_png(&path).map_err(fail)?;
        library.add(&bytes, &name, false).map_err(fail)?;
    }
    skin_library_view(&state).await
}

#[tauri::command]
async fn skin_from_player(state: State<'_, AppState>, username: String) -> CmdResult<SkinLibraryView> {
    let (name, png, slim) = skins::skin_of_player(state.launcher.http(), &username).await.map_err(fail)?;
    Skins::new(&state.launcher).add(&png, &name, slim).map_err(fail)?;
    skin_library_view(&state).await
}

#[tauri::command]
async fn skin_update(state: State<'_, AppState>, id: String, name: Option<String>, slim: Option<bool>) -> CmdResult<SkinLibraryView> {
    Skins::new(&state.launcher).update(&id, name.as_deref(), slim).map_err(fail)?;
    skin_library_view(&state).await
}

#[tauri::command]
async fn skin_remove(state: State<'_, AppState>, id: String) -> CmdResult<SkinLibraryView> {
    Skins::new(&state.launcher).remove(&id).map_err(fail)?;
    skin_library_view(&state).await
}

/// Use a saved skin on the selected account. Microsoft accounts get it
/// uploaded to Mojang; offline accounts get it in-game through the Vortex
/// mod.
#[tauri::command]
async fn skin_apply(state: State<'_, AppState>, id: Option<String>) -> CmdResult<SkinLibraryView> {
    let mut account = state.accounts.lock().await.selected().cloned().ok_or("add an account first")?;
    let library = Skins::new(&state.launcher);
    if let (Some(id), true) = (&id, account.is_microsoft()) {
        let skin = library.load().skins.into_iter().find(|s| &s.id == id).ok_or("no such skin")?;
        let png = std::fs::read(library.png_path(id).map_err(fail)?).map_err(|e| e.to_string())?;
        let settings = state.settings.lock().await.clone();
        let before = account.clone();
        let session = state.launcher.session_for(&mut account, &settings).await.map_err(fail)?;
        if account != before {
            let mut store = state.accounts.lock().await;
            store.upsert(account.clone());
            let _ = store.save();
        }
        skins::upload_to_account(state.launcher.http(), &session.access_token, png.clone(), skin.slim)
            .await
            .map_err(fail)?;
        let cache = state.launcher.paths().cache().join("skins");
        let _ = std::fs::create_dir_all(&cache);
        let _ = std::fs::write(cache.join(format!("{}.png", account.uuid)), &png);
    }
    library.set_applied(&account.uuid, id.as_deref()).map_err(fail)?;
    skin_library_view(&state).await
}

#[tauri::command]
async fn set_cape(state: State<'_, AppState>, cape: String) -> CmdResult<SkinLibraryView> {
    let account = state.accounts.lock().await.selected().cloned().ok_or("add an account first")?;
    Skins::new(&state.launcher).set_cape(&account.uuid, &cape).map_err(fail)?;
    skin_library_view(&state).await
}

/// Folder holding the in-game main menu art the UI exports before a launch.
fn menu_dir(launcher: &Launcher) -> std::path::PathBuf {
    launcher.paths().cache().join("menu")
}

/// Save the launcher's background and logo (PNG data URLs) for the Vortex
/// main menu in-game.
#[tauri::command]
async fn save_menu_art(state: State<'_, AppState>, background: String, logo: String) -> CmdResult<()> {
    use base64::Engine;
    let dir = menu_dir(&state.launcher);
    std::fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    for (name, url) in [("background.png", background), ("logo.png", logo)] {
        let b64 = url.split_once("base64,").map(|(_, b)| b).ok_or("expected a PNG image")?;
        let bytes = base64::engine::general_purpose::STANDARD.decode(b64.trim()).map_err(|e| e.to_string())?;
        skins::png_size(&bytes).map_err(fail)?;
        std::fs::write(dir.join(name), bytes).map_err(|e| e.to_string())?;
    }
    Ok(())
}

/// Copy dropped `.jar` files into an instance's mods folder.
#[tauri::command]
async fn import_mods(state: State<'_, AppState>, name: String, paths: Vec<String>) -> CmdResult<Vec<ContentView>> {
    let inst = find_instance(&state, &name)?;
    let dir = mods::mods_dir(&inst);
    std::fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    let mut copied = 0;
    for p in paths {
        let src = std::path::PathBuf::from(&p);
        let is_jar = src
            .extension()
            .and_then(|e| e.to_str())
            .is_some_and(|e| e.eq_ignore_ascii_case("jar"));
        let Some(file) = src.file_name() else { continue };
        if !is_jar || !src.is_file() {
            continue;
        }
        std::fs::copy(&src, dir.join(file)).map_err(|e| e.to_string())?;
        copied += 1;
    }
    if copied == 0 {
        return Err("only .jar files can be installed as mods".into());
    }
    content_views(&inst, ProjectKind::Mod, &dir)
}

fn open_with_os(target: &str) -> std::io::Result<()> {
    let mut cmd = if cfg!(windows) {
        let mut c = std::process::Command::new("explorer");
        c.arg(target);
        c
    } else if cfg!(target_os = "macos") {
        let mut c = std::process::Command::new("open");
        c.arg(target);
        c
    } else {
        let mut c = std::process::Command::new("xdg-open");
        c.arg(target);
        c
    };
    cmd.spawn().map(|_| ())
}

#[tauri::command]
async fn open_path(state: State<'_, AppState>, target: String, name: Option<String>) -> CmdResult<()> {
    let path = match (target.as_str(), name) {
        ("data", _) => state.launcher.paths().root.clone(),
        ("skins", _) => state.launcher.paths().root.join("skins"),
        ("runtimes", _) => state.launcher.paths().runtimes(),
        ("resourcepacks", Some(n)) => find_instance(&state, &n)?.game_dir().join("resourcepacks"),
        ("shaderpacks", Some(n)) => find_instance(&state, &n)?.game_dir().join("shaderpacks"),
        ("instance", Some(n)) => find_instance(&state, &n)?.game_dir(),
        ("mods", Some(n)) => mods::mods_dir(&find_instance(&state, &n)?),
        ("screenshots", Some(n)) => find_instance(&state, &n)?.game_dir().join("screenshots"),
        ("logs", Some(n)) => find_instance(&state, &n)?.game_dir().join("logs"),
        _ => return Err("unknown folder".into()),
    };
    std::fs::create_dir_all(&path).map_err(|e| e.to_string())?;
    open_with_os(&path.to_string_lossy()).map_err(|e| e.to_string())
}

#[tauri::command]
async fn open_url(url: String) -> CmdResult<()> {
    if !url.starts_with("https://") || url.contains(['"', '\n', ' ']) {
        return Err("only https links can be opened".into());
    }
    #[cfg(windows)]
    {
        std::process::Command::new("rundll32")
            .args(["url.dll,FileProtocolHandler", &url])
            .spawn()
            .map(|_| ())
            .map_err(|e| e.to_string())
    }
    #[cfg(not(windows))]
    {
        open_with_os(&url).map_err(|e| e.to_string())
    }
}

// -------------------------------------------------------------- launching --

/// Progress callback that forwards to the UI at most ~20 times a second
/// (always forwarding stage changes and completion).
fn progress_emitter(app: &AppHandle, instance: &str) -> ProgressFn {
    let app = app.clone();
    let instance = instance.to_string();
    let last = StdMutex::new((Instant::now() - Duration::from_secs(1), String::new()));
    Arc::new(move |p: Progress| {
        let mut guard = last.lock().unwrap();
        let stage_changed = guard.1 != p.stage;
        let finished = p.total > 0 && p.done >= p.total;
        if stage_changed || finished || guard.0.elapsed() >= Duration::from_millis(50) {
            *guard = (Instant::now(), p.stage.clone());
            let _ = app.emit(
                "launch-progress",
                ProgressEvent { instance: instance.clone(), stage: p.stage, done: p.done, total: p.total },
            );
        }
    })
}

/// The Vortex in-game mod, built from `mod/` for each Minecraft version it
/// supports (`bash mod/build-all.sh`).
const VORTEX_MODS: &[(&str, &[u8])] = &[
    ("1.20.1", include_bytes!("../resources/vortex-mods-1.20.1.jar")),
    ("1.20.4", include_bytes!("../resources/vortex-mods-1.20.4.jar")),
    ("1.21.1", include_bytes!("../resources/vortex-mods-1.21.1.jar")),
    ("1.21.4", include_bytes!("../resources/vortex-mods-1.21.4.jar")),
    ("1.21.8", include_bytes!("../resources/vortex-mods-1.21.8.jar")),
    ("1.21.11", include_bytes!("../resources/vortex-mods-1.21.11.jar")),
];
const VORTEX_MOD_FILE: &str = "vortex-mods.jar";

/// The Vortex mod built for this Minecraft version, if there is one.
fn vortex_mod_for(minecraft: &str) -> Option<&'static [u8]> {
    VORTEX_MODS.iter().find(|(v, _)| *v == minecraft).map(|(_, jar)| *jar)
}
/// Modrinth project id of Fabric API, which the mod needs.
const FABRIC_API: &str = "P7dR8mSH";

/// Put the Vortex mod (and Fabric API) in a supported Fabric instance, or
/// take it out when it's turned off. Adds the JVM properties the mod reads.
async fn setup_vortex_mod(
    launcher: &Launcher,
    instance: &mut Instance,
    account: &Account,
    settings: &Settings,
    progress: &ProgressFn,
) -> anyhow::Result<bool> {
    use anyhow::Context;
    if !matches!(instance.loader, Loader::Fabric { .. }) {
        return Ok(false);
    }
    let Some(vortex_mod) = vortex_mod_for(&instance.minecraft_version) else { return Ok(false) };
    let dir = mods::mods_dir(instance);
    let jar = dir.join(VORTEX_MOD_FILE);
    if !settings.vortex_mods || vortex_mod.is_empty() {
        let _ = std::fs::remove_file(&jar);
        return Ok(false);
    }
    // Turned off by hand on the Mods page: leave it off.
    if dir.join(format!("{VORTEX_MOD_FILE}.disabled")).is_file() {
        return Ok(false);
    }
    std::fs::create_dir_all(&dir)?;
    if std::fs::read(&jar).map(|b| b != vortex_mod).unwrap_or(true) {
        std::fs::write(&jar, vortex_mod)?;
    }
    let has_api = modrinth::installed(instance).files.values().any(|e| e.project_id == FABRIC_API)
        || mods::list(&dir)?.iter().any(|m| m.name == "Fabric API");
    if !has_api {
        modrinth::install(launcher, instance, ProjectKind::Mod, FABRIC_API, None, false, true, progress)
            .await
            .context("could not install Fabric API, which the Vortex mod needs")?;
    }
    let library = Skins::new(launcher);
    if !account.is_microsoft() {
        if let Some((path, slim)) = library.applied_for(&account.uuid) {
            instance.jvm_args.push(format!("-Dvortex.skin={}", path.display()));
            instance.jvm_args.push(format!("-Dvortex.skinSlim={slim}"));
        }
    }
    instance.jvm_args.push(format!("-Dvortex.cape={}", library.cape_for(&account.uuid)));
    instance.jvm_args.push(format!("-Dvortex.accent={}", settings.accent));
    instance.jvm_args.push(format!("-Dvortex.music={}", music::dir(&launcher.paths().root).display()));
    instance.jvm_args.push(format!("-Dvortex.menu={}", settings.vortex_menu));
    let menu = menu_dir(launcher);
    for (prop, file) in [("menuBg", "background.png"), ("menuLogo", "logo.png")] {
        let p = menu.join(file);
        if p.is_file() {
            instance.jvm_args.push(format!("-Dvortex.{prop}={}", p.display()));
        }
    }
    Ok(true)
}

/// Mod sets by Modrinth project id. Each mod is added only when it has a
/// build for the instance's Minecraft and loader; the rest are skipped.
/// `a|b` entries are alternatives in order: the first with a build for the
/// version wins (newer mods first, their older-version counterparts after).
/// Only mods that run on the client: server-only ones (Radium, VMP, C2ME)
/// are left out.
/// Performance: every mainstream optimisation mod that works alongside the
/// others (the Fabulously Optimized idea, and a bit further).
const PERF_FABRIC: &[(&str, &str)] = &[
    ("AANobbMI", "Sodium"),
    ("gvQqBUqZ", "Lithium"),
    ("uXXizFIs", "FerriteCore"),
    ("NNAgCjsB", "EntityCulling"),
    ("5ZwdcRci", "ImmediatelyFast"),
    ("nmDcB62a", "ModernFix"),
    ("51shyZVL", "More Culling"),
    ("LQ3K71Q1", "Dynamic FPS"),
    ("g96Z4WVZ", "BadOptimizations"),
    ("fQEb0iXm", "Krypton"),
    ("Ps1zyz6x|H8CaAYZC", "ScalableLux|Starlight"),
    ("NRjRiSSD", "Memory Leak Fix"),
    ("9mtu0sUO", "Fast IP Ping"),
    ("QwxR6Gcd", "Debugify"),
    ("PtjYWJkn", "Sodium Extra"),
    ("Bh37bMuy", "Reese's Sodium Options"),
    ("iG6ZHsUV", "Cull Less Leaves"),
    ("x1hIzbuY", "FastQuit"),
    ("Wnxd13zP", "Clumps"),
    ("vSEH1ERy", "ThreadTweak"),
    ("uLbm7CG6", "Language Reload"),
];

/// The same idea on Forge (Embeddium, or Rubidium on older versions).
const PERF_FORGE: &[(&str, &str)] = &[
    ("sk9rgfiA|4ZqxOvjD", "Embeddium|Rubidium"),
    ("uXXizFIs", "FerriteCore"),
    ("NNAgCjsB", "EntityCulling"),
    ("5ZwdcRci", "ImmediatelyFast"),
    ("nmDcB62a", "ModernFix"),
    ("LQ3K71Q1", "Dynamic FPS"),
    ("g96Z4WVZ", "BadOptimizations"),
    ("NRjRiSSD", "Memory Leak Fix"),
    ("9mtu0sUO", "Fast IP Ping"),
    ("QwxR6Gcd", "Debugify"),
    ("Wnxd13zP", "Clumps"),
    ("qa2H4BS9", "Canary"),
    ("2eT495vq", "Saturn"),
];

/// Normal: the everyday extras on top of the performance set.
const NORMAL_FABRIC: &[(&str, &str)] = &[
    ("mOgUt4GM", "Mod Menu"),
    ("YL57xq9U", "Iris Shaders"),
    ("1IjD5062", "Continuity"),
    ("yBW8D80W|PxQSWIcD", "LambDynamicLights|Sodium Dynamic Lights"),
    ("EsAfCjCV", "AppleSkin"),
    ("1bokaNcj", "Xaero's Minimap"),
    ("NcUtCpym", "Xaero's World Map"),
    ("u6dRKJwZ|nfn13YXA|fRiHVvU7", "JEI|REI|EMI"),
    ("nvQzSEkH|6AQIaxuO", "Jade|WTHIT"),
    ("aC3cM3Vq", "Mouse Tweaks"),
    ("2M01OLQq", "Shulker Box Tooltip"),
    ("Wb5oqrBJ", "Chat Heads"),
    ("zV5r3pPn", "3D Skin Layers"),
    ("4I1XuqiY", "Entity Model Features"),
    ("BVzZfTc1", "Entity Texture Features"),
    ("8shC1gFX", "BetterF3"),
    ("xv94TkTM", "Controlling"),
    ("qQyHxfxd", "No Chat Reports"),
    ("O7RBXm3n", "Inventory Profiles Next"),
    ("smUP7V3r", "Durability Tooltip"),
    ("WhbRG4iK", "Falling Leaves"),
    ("kYuIpRLv", "Wavey Capes"),
    ("cVNW5lr6", "Item Highlighter"),
    ("M08ruV16", "Bobby"),
    ("MPCX6s5C", "Not Enough Animations"),
    ("qyVF9oeo", "Sound Physics Remastered"),
    ("rcTfTZr3", "Presence Footsteps"),
    ("fM515JnW", "AmbientSounds"),
    ("UVtY3ZAC", "Enchantment Descriptions"),
    ("rI0hvYcd", "Visuality"),
    ("ETlrkaYF", "Cubes Without Borders"),
    ("nrikgvxm", "Particle Rain"),
    ("pJogNFap", "Paginated Advancements"),
    ("gPCdW0Wr", "Make Bubbles Pop"),
    ("JtifUr64", "Traveler's Titles"),
    ("5srFLIaK", "Better Clouds"),
    ("QdG47OkI", "Model Fix"),
    ("89Wsn8GD", "Capes"),
    ("kqJFAPU9", "Better Mount HUD"),
    ("1KiJRrTg", "Screenshot to Clipboard"),
];

const NORMAL_FORGE: &[(&str, &str)] = &[
    ("GchcoXML", "Oculus"),
    ("u6dRKJwZ", "JEI"),
    ("nvQzSEkH", "Jade"),
    ("EsAfCjCV", "AppleSkin"),
    ("1bokaNcj", "Xaero's Minimap"),
    ("NcUtCpym", "Xaero's World Map"),
    ("aC3cM3Vq", "Mouse Tweaks"),
    ("xv94TkTM", "Controlling"),
    ("Wb5oqrBJ", "Chat Heads"),
    ("zV5r3pPn", "3D Skin Layers"),
    ("4I1XuqiY", "Entity Model Features"),
    ("BVzZfTc1", "Entity Texture Features"),
    ("qQyHxfxd", "No Chat Reports"),
    ("2M01OLQq", "Shulker Box Tooltip"),
    ("O7RBXm3n", "Inventory Profiles Next"),
    ("kYuIpRLv", "Wavey Capes"),
    ("1IjD5062", "Continuity"),
    ("PxQSWIcD", "Sodium Dynamic Lights"),
    ("8shC1gFX", "BetterF3"),
    ("smUP7V3r", "Durability Tooltip"),
    ("cVNW5lr6", "Item Highlighter"),
    ("MPCX6s5C", "Not Enough Animations"),
    ("qyVF9oeo", "Sound Physics Remastered"),
    ("fM515JnW", "AmbientSounds"),
    ("UVtY3ZAC", "Enchantment Descriptions"),
    ("ETlrkaYF", "Cubes Without Borders"),
    ("nrikgvxm", "Particle Rain"),
    ("gPCdW0Wr", "Make Bubbles Pop"),
    ("JtifUr64", "Traveler's Titles"),
    ("5srFLIaK", "Better Clouds"),
    ("QdG47OkI", "Model Fix"),
    ("89Wsn8GD", "Capes"),
];

/// PvP: the client-side extras that help in a fight, on top of performance
/// (the Vortex mod itself brings the PvP HUD).
const PVP_FABRIC: &[(&str, &str)] = &[
    ("mOgUt4GM", "Mod Menu"),
    ("zV5r3pPn", "3D Skin Layers"),
    ("kYuIpRLv", "Wavey Capes"),
    ("Wb5oqrBJ", "Chat Heads"),
    ("qQyHxfxd", "No Chat Reports"),
    ("MS1ZMyR7", "Better Ping Display"),
    ("GfoMZ9Ly", "Shield Statuses"),
    ("x02cBj9Y", "Status Effect Bars"),
    ("smUP7V3r", "Durability Tooltip"),
    ("cVNW5lr6", "Item Highlighter"),
    ("yjgIrBjZ", "Auth Me"),
    ("M08ruV16", "Bobby"),
    ("89Wsn8GD", "Capes"),
    ("xv94TkTM", "Controlling"),
    ("aC3cM3Vq", "Mouse Tweaks"),
    ("rI0hvYcd", "Visuality"),
    // 0.10: more PvP, none of them clash with Vortex's own crosshair, zoom or HUD.
    ("MPCX6s5C", "Not Enough Animations"),
    ("4Hfmgaef", "OverflowAnimations"),
    ("geGZinIB", "Combat Hitboxes"),
    ("T9R7YTnA", "TotemCounter"),
    ("1cfO6J6t", "Totem Tweaks"),
    ("EsAfCjCV", "AppleSkin"),
];

const PVP_FORGE: &[(&str, &str)] = &[
    ("zV5r3pPn", "3D Skin Layers"),
    ("kYuIpRLv", "Wavey Capes"),
    ("Wb5oqrBJ", "Chat Heads"),
    ("qQyHxfxd", "No Chat Reports"),
    ("smUP7V3r", "Durability Tooltip"),
    ("89Wsn8GD", "Capes"),
    ("xv94TkTM", "Controlling"),
    ("aC3cM3Vq", "Mouse Tweaks"),
    ("cVNW5lr6", "Item Highlighter"),
];

/// A pack entry's alternatives, in order: (id, name).
fn alternatives(entry: (&'static str, &'static str)) -> Vec<(&'static str, &'static str)> {
    let names: Vec<&'static str> = entry.1.split('|').collect();
    entry.0.split('|').enumerate().map(|(i, id)| (id, names.get(i).copied().unwrap_or(names[0]))).collect()
}

/// Remembers which pack mods were already added to an instance, so one you
/// remove stays removed.
const PERF_MARK: &str = ".vortex-performance.json";

type ModList = &'static [(&'static str, &'static str)];

/// The mods an instance's pack adds, for its loader.
fn pack_mods(pack: &str, loader: &Loader) -> Vec<(&'static str, &'static str)> {
    let fabric = matches!(loader, Loader::Fabric { .. });
    let (perf, extra): (ModList, ModList) = match (pack, fabric) {
        ("performance", true) => (PERF_FABRIC, &[]),
        ("performance", false) => (PERF_FORGE, &[]),
        ("normal", true) => (PERF_FABRIC, NORMAL_FABRIC),
        ("normal", false) => (PERF_FORGE, NORMAL_FORGE),
        ("pvp", true) => (PERF_FABRIC, PVP_FABRIC),
        ("pvp", false) => (PERF_FORGE, PVP_FORGE),
        _ => return Vec::new(),
    };
    perf.iter().chain(extra.iter()).copied().collect()
}

/// Add the instance's pack (PvP, Normal or Performance) on a Fabric or Forge
/// instance: each mod once, when a version exists for this Minecraft.
/// Instances made before packs existed get the performance pack when that
/// setting is on. Returns the names added.
async fn setup_pack(
    app: &AppHandle,
    launcher: &Launcher,
    instance: &Instance,
    settings: &Settings,
    progress: &ProgressFn,
) -> Vec<String> {
    if matches!(instance.loader, Loader::Vanilla) {
        return Vec::new();
    }
    let pack = match instance.pack.as_deref() {
        Some(p) => p,
        None if settings.performance_mods => "performance",
        None => return Vec::new(),
    };
    let mut list = pack_mods(pack, &instance.loader);
    if list.is_empty() {
        return Vec::new();
    }
    // Render resolution below 100% needs the RenderScale mod (Fabric).
    if settings.render_scale < 100 && matches!(instance.loader, Loader::Fabric { .. }) {
        list.push(("Va8PJBFX", "RenderScale"));
    }
    let mark = instance.dir.join(PERF_MARK);
    // Already set up once: anything left is a retry of mods that failed.
    let retry = mark.exists();
    let mut done: Vec<String> = std::fs::read(&mark).ok().and_then(|b| serde_json::from_slice(&b).ok()).unwrap_or_default();
    let dir = mods::mods_dir(instance);
    let present = mods::list(&dir).unwrap_or_default();
    // OptiFine (through OptiFabric) doesn't go with Sodium, Iris or Embeddium.
    let optifine = present.iter().any(|m| m.name.to_lowercase().contains("optif"));
    let index = modrinth::installed(instance);
    let mut todo = Vec::new();
    for entry in list {
        let options = alternatives(entry);
        let key = options[0].0;
        if options.iter().any(|(id, _)| done.iter().any(|d| d == id)) {
            continue;
        }
        let have = options.iter().any(|(id, name)| {
            index.files.values().any(|e| e.project_id == *id) || present.iter().any(|m| m.name.eq_ignore_ascii_case(name))
        });
        let clash = optifine && options.iter().any(|(id, _)| ["AANobbMI", "YL57xq9U", "sk9rgfiA", "4ZqxOvjD", "GchcoXML", "PtjYWJkn", "Bh37bMuy"].contains(id));
        if have || clash {
            done.push(key.to_string());
        } else {
            todo.push((key, options));
        }
    }
    let _ = std::fs::write(&mark, serde_json::to_vec(&done).unwrap_or_default());
    if todo.is_empty() {
        return Vec::new();
    }
    report(progress, "Checking Modrinth", 0, 0);
    if !modrinth::reachable(launcher).await {
        emit_lines(app, &instance.name, vec![LogLineView { level: "warn", text: "Modrinth can't be reached, so the pack's mods are added next time. Starting the game now.".into() }]);
        return Vec::new();
    }

    let name = &instance.name;
    let send = |ev: PackEvent| {
        let _ = app.emit("pack-install", ev);
    };
    let mut start = PackEvent::new(name, "start");
    start.pack = Some(pack.to_string());
    start.version = Some(format!("{} {}", instance.minecraft_version, instance.loader.label().split(' ').next().unwrap_or_default()));
    start.mods = todo.iter().map(|(id, o)| PackModView { id: id.to_string(), name: o[0].1.to_string() }).collect();
    send(start);

    // Every mod at once (8 at a time), shared dependencies fetched once.
    let entries: Vec<Vec<String>> = todo.iter().map(|(_, o)| o.iter().map(|(id, _)| id.to_string()).collect()).collect();
    let total = todo.len() as u64;
    let finished = std::sync::atomic::AtomicU64::new(0);
    let tally = StdMutex::new((Vec::<String>::new(), 0usize, 0usize, done));
    report(progress, &format!("Adding mods (0/{total})"), 0, total);
    let install = modrinth::install_pack(
        launcher,
        instance,
        &entries,
        |i| {
            let mut ev = PackEvent::new(name, "mod");
            ev.id = Some(todo[i].0.to_string());
            ev.state = Some("active");
            send(ev);
        },
        |i, state, winner| {
            let (key, options) = &todo[i];
            let n = finished.fetch_add(1, std::sync::atomic::Ordering::Relaxed) + 1;
            report(progress, &format!("Adding mods ({n}/{total})"), n, total);
            let mut ev = PackEvent::new(name, "mod");
            ev.id = Some(key.to_string());
            let mut t = tally.lock().unwrap();
            ev.state = Some(match state {
                modrinth::PackState::Added => {
                    let mod_name = options[winner.unwrap_or(0).min(options.len() - 1)].1;
                    t.0.push(mod_name.to_string());
                    ev.name = Some(mod_name.to_string());
                    t.3.push(key.to_string());
                    "added"
                }
                // None for this version: don't keep asking.
                modrinth::PackState::NoBuild => {
                    t.1 += 1;
                    t.3.push(key.to_string());
                    "skipped"
                }
                // Offline or Modrinth down: try again next launch.
                modrinth::PackState::Failed => {
                    t.2 += 1;
                    "failed"
                }
            });
            drop(t);
            send(ev);
        },
    );
    // A first install shows its progress and may take a while; retrying a
    // few failed mods never holds the game up for more than 45 seconds.
    if retry {
        if tokio::time::timeout(Duration::from_secs(45), install).await.is_err() {
            emit_lines(app, &instance.name, vec![LogLineView { level: "warn", text: "Adding the pack's missing mods took too long; they're tried again next time.".into() }]);
        }
    } else {
        let _ = install.await;
    }
    let (added, skipped, failed, done) = tally.into_inner().unwrap_or_else(|e| e.into_inner());
    let _ = std::fs::write(&mark, serde_json::to_vec(&done).unwrap_or_default());
    let mut end = PackEvent::new(name, "done");
    end.added = added.len();
    end.skipped = skipped;
    end.failed = failed;
    send(end);
    added
}

fn window_after_launch(app: &AppHandle, how: &str, game_running: bool) {
    let Some(w) = app.get_webview_window("main") else { return };
    match (how, game_running) {
        ("minimize", true) => {
            let _ = w.minimize();
        }
        ("hide", true) => {
            let _ = w.hide();
        }
        ("minimize" | "hide", false) => {
            let _ = w.show();
            let _ = w.unminimize();
            let _ = w.set_focus();
        }
        _ => {}
    }
}

async fn run_game(
    app: &AppHandle,
    launcher: Arc<Launcher>,
    mut instance: Instance,
    mut account: Account,
    settings: Settings,
    mut kill: oneshot::Receiver<()>,
) -> anyhow::Result<Option<i32>> {
    let name = instance.name.clone();
    let state = |s: &'static str| StateEvent { instance: name.clone(), state: s, code: None, error: None };
    let _ = app.emit("game-state", state("preparing"));

    let before = account.clone();
    let session = match launcher.session_for(&mut account, &settings).await {
        Ok(s) => s,
        // Couldn't refresh the sign-in (offline, Microsoft down): play with
        // the saved one. Singleplayer works; servers may ask to sign in again.
        Err(e) => match &account.kind {
            AccountKind::Microsoft(t) if !t.minecraft_token.is_empty() => {
                emit_lines(app, &name, vec![LogLineView { level: "warn", text: format!("Couldn't refresh the Microsoft sign-in ({e:#}); starting with the saved one.") }]);
                vortex_core::launch::LaunchSession {
                    username: account.username.clone(),
                    uuid: account.uuid.clone(),
                    access_token: t.minecraft_token.clone(),
                    user_type: "msa".to_string(),
                    xuid: "0".to_string(),
                }
            }
            _ => return Err(e),
        },
    };
    if account != before {
        let st = app.state::<AppState>();
        let mut store = st.accounts.lock().await;
        store.upsert(account.clone());
        let _ = store.save();
    }
    if matches!(account.kind, AccountKind::Offline) {
        emit_lines(app, &name, vec![LogLineView { level: "warn", text: "Offline account: singleplayer and LAN only.".into() }]);
    }

    let progress = progress_emitter(app, &name);
    let game_dir = instance.game_dir();
    let info = |text: String| emit_lines(app, &name, vec![LogLineView { level: "info", text }]);
    let warn = |text: String| emit_lines(app, &name, vec![LogLineView { level: "warn", text }]);
    // Housekeeping first: old logs, a world backup, mod updates.
    let pruned = vortex_core::extras::prune_logs(&game_dir, settings.log_retention_days);
    if pruned > 0 {
        info(format!("Removed {pruned} old log file(s)."));
    }
    if settings.backup_worlds {
        report(&progress, "Backing up worlds", 0, 0);
        let dir = game_dir.clone();
        let keep = settings.backups_keep;
        match tokio::task::spawn_blocking(move || vortex_core::extras::backup_worlds(&dir, keep)).await {
            Ok(Ok(Some(p))) => info(format!("Worlds backed up to {}.", p.display())),
            Ok(Ok(None)) => {}
            Ok(Err(e)) => warn(format!("World backup failed: {e}")),
            Err(e) => warn(format!("World backup failed: {e}")),
        }
    }
    let added = setup_pack(app, &launcher, &instance, &settings, &progress).await;
    vortex_core::extras::write_render_scale(&game_dir, settings.render_scale);
    if !added.is_empty() {
        info(format!("Mods added: {}.", added.join(", ")));
    }
    if settings.auto_update_mods {
        report(&progress, "Checking for mod updates", 0, 0);
        if let Ok(up) = modrinth::update_all(&launcher, &instance, settings.mods_allow_unstable, &progress).await {
            if !up.is_empty() {
                info(format!("Updated: {}.", up.join(", ")));
            }
        }
    }
    if !settings.pre_launch_command.trim().is_empty() {
        report(&progress, "Running your pre-launch command", 0, 0);
        match vortex_core::extras::run_hook(&settings.pre_launch_command, &game_dir, Duration::from_secs(120)).await {
            Ok(0) => {}
            Ok(code) => warn(format!("Pre-launch command exited with {code}.")),
            Err(e) => warn(format!("Pre-launch command: {e}")),
        }
    }
    match setup_vortex_mod(&launcher, &mut instance, &account, &settings, &progress).await {
        Ok(true) => emit_lines(app, &name, vec![LogLineView { level: "info", text: "Vortex mods added (Right Shift in-game opens the mod menu).".into() }]),
        Ok(false) => {}
        Err(e) => emit_lines(app, &name, vec![LogLineView { level: "warn", text: format!("Vortex mods skipped: {e:#}") }]),
    }
    let cmd = launcher.prepare_launch(&instance, &session, &settings, &progress).await?;
    emit_lines(app, &name, vec![LogLineView { level: "debug", text: format!("> {}", cmd.display_redacted()) }]);

    let started_at = std::time::SystemTime::now();
    let mut game = launch::spawn(&cmd)?;
    let _ = app.emit("game-state", state("running"));
    window_after_launch(app, &settings.after_launch, true);

    let mut buffer: Vec<LogLineView> = Vec::new();
    let mut tick = tokio::time::interval(Duration::from_millis(120));
    let mut kill_armed = true;
    let status = loop {
        tokio::select! {
            line = game.logs.recv() => match line {
                Some(LogLine { level, text }) => {
                    buffer.push(LogLineView { level: level_name(level), text });
                    if buffer.len() >= 400 {
                        emit_lines(app, &name, std::mem::take(&mut buffer));
                    }
                }
                None => break game.wait().await?,
            },
            _ = tick.tick() => {
                if !buffer.is_empty() {
                    emit_lines(app, &name, std::mem::take(&mut buffer));
                }
            }
            res = &mut kill, if kill_armed => {
                if res.is_ok() {
                    let _ = game.child.kill().await;
                    break game.wait().await?;
                }
                kill_armed = false;
            }
        }
    };
    while let Ok(LogLine { level, text }) = game.logs.try_recv() {
        buffer.push(LogLineView { level: level_name(level), text });
    }
    if !buffer.is_empty() {
        emit_lines(app, &name, buffer);
    }
    window_after_launch(app, &settings.after_launch, false);
    // Crash helper: why it crashed, in plain words.
    let code = status.code();
    if settings.crash_helper && code.is_some_and(|c| c != 0) {
        if let Some((reason, file)) = vortex_core::extras::crash_reason(&game_dir, started_at) {
            let _ = app.emit("game-crash", CrashEvent { instance: name.clone(), reason, file: file.to_string_lossy().into_owned() });
        }
    }
    if !settings.post_exit_command.trim().is_empty() {
        let cmdline = settings.post_exit_command.clone();
        let dir = game_dir.clone();
        tokio::spawn(async move {
            let _ = vortex_core::extras::run_hook(&cmdline, &dir, Duration::from_secs(600)).await;
        });
    }
    // Flash the taskbar so you notice the game closed.
    if settings.ui.get("flash_on_exit").and_then(|v| v.as_bool()).unwrap_or(false) {
        if let Some(w) = app.get_webview_window("main") {
            let _ = w.request_user_attention(Some(tauri::UserAttentionType::Informational));
        }
    }
    Ok(code)
}

fn report(progress: &ProgressFn, stage: &str, done: u64, total: u64) {
    progress(Progress { stage: stage.to_string(), done, total });
}

#[derive(Serialize, Clone)]
struct CrashEvent {
    instance: String,
    reason: String,
    file: String,
}

fn emit_lines(app: &AppHandle, instance: &str, lines: Vec<LogLineView>) {
    let _ = app.emit("game-log", LogEvent { instance: instance.to_string(), lines });
}

// ------------------------------------------------------------------ skins --

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct SkinView {
    /// `data:image/png;base64,…`, or `None` when no skin could be found.
    data_url: Option<String>,
    slim: bool,
    /// `chosen` (from the skin library), `account`, `default` or `none`.
    source: &'static str,
    /// Vortex cape id, or `none`.
    cape: String,
}

fn png_data_url(bytes: &[u8]) -> String {
    use base64::Engine;
    format!("data:image/png;base64,{}", base64::engine::general_purpose::STANDARD.encode(bytes))
}

/// The default skin from a Minecraft client jar the player already installed.
fn default_skin_from_jars(paths: &Paths) -> Option<Vec<u8>> {
    use std::io::Read;
    const NAMES: [&str; 2] = [
        "assets/minecraft/textures/entity/player/wide/steve.png",
        "assets/minecraft/textures/entity/steve.png",
    ];
    let mut jars: Vec<std::path::PathBuf> = std::fs::read_dir(paths.versions())
        .ok()?
        .flatten()
        .filter_map(|e| {
            let id = e.file_name().into_string().ok()?;
            let jar = paths.version_jar(&id);
            jar.is_file().then_some(jar)
        })
        .collect();
    jars.sort();
    jars.reverse();
    for jar in jars {
        let Ok(file) = std::fs::File::open(&jar) else { continue };
        let Ok(mut zip) = vortex_core::zip::ZipArchive::new(file) else { continue };
        for name in NAMES {
            if let Ok(mut entry) = zip.by_name(name) {
                let mut buf = Vec::new();
                if entry.read_to_end(&mut buf).is_ok() && buf.starts_with(b"\x89PNG") {
                    return Some(buf);
                }
            }
        }
    }
    None
}

#[tauri::command]
async fn player_skin(state: State<'_, AppState>) -> CmdResult<SkinView> {
    let account = state.accounts.lock().await.selected().cloned();
    let library = Skins::new(&state.launcher);
    let cape = account.as_ref().map(|a| library.cape_for(&a.uuid)).unwrap_or_else(|| "none".into());
    if let Some((path, slim)) = account.as_ref().and_then(|a| library.applied_for(&a.uuid)) {
        if let Ok(bytes) = std::fs::read(&path) {
            return Ok(SkinView { data_url: Some(png_data_url(&bytes)), slim, source: "chosen", cape });
        }
    }
    let skins = state.launcher.paths().cache().join("skins");
    let _ = std::fs::create_dir_all(&skins);

    if let Some(acc) = account.filter(|a| a.is_microsoft()) {
        let file = skins.join(format!("{}.png", acc.uuid));
        let slim_file = skins.join(format!("{}.slim", acc.uuid));
        match skins::fetch_profile_skin(state.launcher.http(), &acc.uuid).await {
            Ok((bytes, slim)) => {
                let _ = std::fs::write(&file, &bytes);
                if slim {
                    let _ = std::fs::write(&slim_file, b"1");
                } else {
                    let _ = std::fs::remove_file(&slim_file);
                }
                return Ok(SkinView { data_url: Some(png_data_url(&bytes)), slim, source: "account", cape: cape.clone() });
            }
            Err(_) => {
                if let Ok(bytes) = std::fs::read(&file) {
                    return Ok(SkinView { data_url: Some(png_data_url(&bytes)), slim: slim_file.is_file(), source: "account", cape: cape.clone() });
                }
            }
        }
    }

    let cached = skins.join("default.png");
    if let Ok(bytes) = std::fs::read(&cached) {
        return Ok(SkinView { data_url: Some(png_data_url(&bytes)), slim: false, source: "default", cape: cape.clone() });
    }
    let paths = state.launcher.paths().clone();
    let found = tauri::async_runtime::spawn_blocking(move || default_skin_from_jars(&paths))
        .await
        .map_err(|e| e.to_string())?;
    Ok(match found {
        Some(bytes) => {
            let _ = std::fs::write(&cached, &bytes);
            SkinView { data_url: Some(png_data_url(&bytes)), slim: false, source: "default", cape: cape.clone() }
        }
        None => SkinView { data_url: None, slim: false, source: "none", cape },
    })
}

// ------------------------------------------------------------------- main --

fn load_accounts(path: &Path) -> AccountStore {
    match AccountStore::load(path) {
        Ok(s) => s,
        Err(_) => {
            let _ = std::fs::rename(path, path.with_extension("json.broken"));
            AccountStore::load(path).unwrap_or_default()
        }
    }
}

fn main() {
    // Settings > Apps > Uninstall runs `Vortex.exe --uninstall`.
    if std::env::args().any(|a| a == "--uninstall") {
        let _ = installer::uninstall();
        return;
    }
    let start_minimized = std::env::args().any(|a| a == "--minimized");
    if let Ok((dir, _)) = updater::current_exe() {
        updater::cleanup(&dir);
    }
    let paths = Paths::from_default();
    let settings = Settings::load(&paths.settings_file()).unwrap_or_default();
    let accounts = load_accounts(&paths.accounts_file());
    let launcher = Launcher::with_concurrency(paths, settings.download_concurrency)
        .expect("cannot create the Vortex data folder");

    tauri::Builder::default()
        .register_uri_scheme_protocol("vmusic", |_ctx, request| music::serve(&request))
        .manage(AppState {
            launcher: Arc::new(launcher),
            settings: Mutex::new(settings),
            accounts: Mutex::new(accounts),
            running: Mutex::new(HashMap::new()),
        })
        .invoke_handler(tauri::generate_handler![
            bootstrap,
            list_versions,
            loader_versions,
            list_instances,
            create_instance,
            update_instance,
            delete_instance,
            launch_instance,
            stop_instance,
            warm_up,
            verify_instance,
            server_status,
            check_update,
            install_update,
            install_status,
            quit_app,
            active_packs,
            music_library,
            music_lyrics,
            music_radio,
            music_state,
            music_commands,
            set_active_packs,
            install_app,
            uninstall_app,
            set_autostart,
            add_offline_account,
            remove_account,
            select_account,
            microsoft_login,
            save_settings,
            news,
            list_mods,
            toggle_mod,
            delete_mod,
            import_mods,
            modrinth_search,
            modrinth_project,
            modrinth_install,
            modrinth_installed,
            version_art,
            skin_library,
            skin_add,
            skin_import_files,
            skin_from_player,
            skin_update,
            skin_remove,
            skin_apply,
            set_cape,
            save_menu_art,
            player_skin,
            open_path,
            open_url,
        ])
        .on_window_event(|window, event| {
            // Closing while a game runs: ask first (Settings > General).
            if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                let state = window.state::<AppState>();
                let busy = state.running.try_lock().map(|r| !r.is_empty()).unwrap_or(false);
                let ask = state.settings.try_lock().map(|s| s.ui.get("confirm_close").and_then(|v| v.as_bool()).unwrap_or(true)).unwrap_or(true);
                if busy && ask {
                    api.prevent_close();
                    let _ = window.emit("confirm-close", ());
                }
            }
        })
        .setup(move |app| {
            // Started with Windows: open quietly in the taskbar.
            if start_minimized {
                if let Some(w) = app.get_webview_window("main") {
                    let _ = w.minimize();
                }
            }
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running Vortex");
}

#[cfg(test)]
mod pack_tests {
    use super::*;

    #[test]
    fn alternatives_split_in_order() {
        assert_eq!(alternatives(("a|b|c", "A|B|C")), vec![("a", "A"), ("b", "B"), ("c", "C")]);
        assert_eq!(alternatives(("x", "X")), vec![("x", "X")]);
    }

    #[test]
    fn packs_are_client_side_and_unique() {
        // Server-only mods stay out: Radium, VMP, C2ME.
        for pack in ["performance", "normal", "pvp"] {
            for loader in [Loader::Fabric { version: None }, Loader::Forge { version: None }] {
                let list = pack_mods(pack, &loader);
                assert!(!list.is_empty());
                let ids: Vec<&str> = list.iter().flat_map(|e| e.0.split('|')).collect();
                for bad in ["2gvRmQXx", "wnEe9KBa", "VSNURh3q"] {
                    assert!(!ids.contains(&bad), "{pack}: {bad}");
                }
                let mut seen = std::collections::HashSet::new();
                for e in &list {
                    assert!(seen.insert(e.0), "{pack}: {} twice", e.1);
                    assert_eq!(e.0.split('|').count(), e.1.split('|').count(), "{}", e.1);
                    assert!(e.0.split('|').all(|id| id.len() == 8 && id.chars().all(|c| c.is_ascii_alphanumeric())));
                }
            }
        }
    }
}
