//! Vortex — desktop launcher (Tauri). The UI lives in `ui/`; this file
//! exposes `vortex-core` to it as Tauri commands and streams launch progress,
//! game logs and game state back as events.

#![cfg_attr(all(windows, not(debug_assertions)), windows_subsystem = "windows")]

use std::collections::HashMap;
use std::path::Path;
use std::sync::{Arc, Mutex as StdMutex};
use std::time::{Duration, Instant};

use serde::Serialize;
use tauri::{AppHandle, Emitter, Manager, State};
use tokio::sync::{oneshot, Mutex};
use vortex_core::accounts::{Account, AccountKind, AccountStore};
use vortex_core::instance::{Instance, Loader};
use vortex_core::loaders::{self, LoaderKind, LoaderVersion};
use vortex_core::logs::{LogLevel, LogLine};
use vortex_core::manifest::VersionType;
use vortex_core::mods::{self, InstalledMod};
use vortex_core::news::{self, NewsEntry};
use vortex_core::paths::Paths;
use vortex_core::progress::{Progress, ProgressFn};
use vortex_core::settings::Settings;
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
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct ProgressEvent {
    instance: String,
    stage: String,
    done: u64,
    total: u64,
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
) -> CmdResult<InstanceView> {
    let loader = match loader {
        LoaderKind::Vanilla => Loader::Vanilla,
        LoaderKind::Fabric => Loader::Fabric { version: loader_version },
        LoaderKind::Forge => Loader::Forge { version: loader_version },
    };
    let mut inst = Instance::new(name.trim(), &version, loader);
    inst.memory_mb = memory_mb;
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
async fn launch_instance(app: AppHandle, state: State<'_, AppState>, name: String) -> CmdResult<()> {
    let mut instance = find_instance(&state, &name)?;
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
    let launcher = state.launcher.clone();
    tauri::async_runtime::spawn(async move {
        let result = run_game(&app, launcher, instance, account, settings, kill_rx).await;
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
    s.save(&state.launcher.paths().settings_file()).map_err(fail)?;
    *state.settings.lock().await = s.clone();
    Ok(s)
}

#[tauri::command]
async fn news(state: State<'_, AppState>) -> CmdResult<Vec<NewsEntry>> {
    news::patch_notes(&state.launcher, 12).await.map_err(fail)
}

#[tauri::command]
async fn list_mods(state: State<'_, AppState>, name: String) -> CmdResult<Vec<InstalledMod>> {
    let inst = find_instance(&state, &name)?;
    let dir = mods::mods_dir(&inst);
    tauri::async_runtime::spawn_blocking(move || mods::list(&dir))
        .await
        .map_err(|e| e.to_string())?
        .map_err(fail)
}

#[tauri::command]
async fn toggle_mod(state: State<'_, AppState>, name: String, file: String, enabled: bool) -> CmdResult<Vec<InstalledMod>> {
    let inst = find_instance(&state, &name)?;
    let dir = mods::mods_dir(&inst);
    mods::set_enabled(&dir, &file, enabled).map_err(fail)?;
    mods::list(&dir).map_err(fail)
}

#[tauri::command]
async fn delete_mod(state: State<'_, AppState>, name: String, file: String) -> CmdResult<Vec<InstalledMod>> {
    let inst = find_instance(&state, &name)?;
    let dir = mods::mods_dir(&inst);
    mods::delete(&dir, &file).map_err(fail)?;
    mods::list(&dir).map_err(fail)
}

/// Copy dropped `.jar` files into an instance's mods folder.
#[tauri::command]
async fn import_mods(state: State<'_, AppState>, name: String, paths: Vec<String>) -> CmdResult<Vec<InstalledMod>> {
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
    mods::list(&dir).map_err(fail)
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

async fn run_game(
    app: &AppHandle,
    launcher: Arc<Launcher>,
    instance: Instance,
    mut account: Account,
    settings: Settings,
    mut kill: oneshot::Receiver<()>,
) -> anyhow::Result<Option<i32>> {
    let name = instance.name.clone();
    let state = |s: &'static str| StateEvent { instance: name.clone(), state: s, code: None, error: None };
    let _ = app.emit("game-state", state("preparing"));

    let before = account.clone();
    let session = launcher.session_for(&mut account, &settings).await?;
    if account != before {
        let st = app.state::<AppState>();
        let mut store = st.accounts.lock().await;
        store.upsert(account.clone());
        let _ = store.save();
    }
    if matches!(account.kind, AccountKind::Offline) {
        emit_lines(app, &name, vec![LogLineView { level: "warn", text: "Offline account: singleplayer and LAN only.".into() }]);
    }

    let cmd = launcher
        .prepare_launch(&instance, &session, &settings, &progress_emitter(app, &name))
        .await?;
    emit_lines(app, &name, vec![LogLineView { level: "debug", text: format!("> {}", cmd.display_redacted()) }]);

    let mut game = launch::spawn(&cmd)?;
    let _ = app.emit("game-state", state("running"));

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
    Ok(status.code())
}

fn emit_lines(app: &AppHandle, instance: &str, lines: Vec<LogLineView>) {
    let _ = app.emit("game-log", LogEvent { instance: instance.to_string(), lines });
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
    let paths = Paths::from_default();
    let settings = Settings::load(&paths.settings_file()).unwrap_or_default();
    let accounts = load_accounts(&paths.accounts_file());
    let launcher = Launcher::with_concurrency(paths, settings.download_concurrency)
        .expect("cannot create the Vortex data folder");

    tauri::Builder::default()
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
            open_path,
            open_url,
        ])
        .run(tauri::generate_context!())
        .expect("error while running Vortex");
}
