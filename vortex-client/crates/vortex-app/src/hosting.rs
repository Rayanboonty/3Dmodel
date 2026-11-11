//! Server hosting: make a Minecraft server on this PC (Vanilla, Paper or
//! Fabric), run it, and manage everything about it from the launcher —
//! console, players, settings, plugins and mods, backups and a tunnel so
//! friends outside your network can join.
//!
//! Each server lives in `<data>/servers/<name>/` with its config in
//! `vortex-server.json`.

use std::collections::{BTreeSet, HashMap, VecDeque};
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use std::process::Stdio;
use std::sync::{Arc, LazyLock, Mutex as StdMutex};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use anyhow::{anyhow, bail, Context, Result};
use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::process::{ChildStdin, Command};
use tokio::sync::Mutex;
use vortex_core::progress::{Progress, ProgressFn};
use vortex_core::reqwest::Client;
use vortex_core::Launcher;

const CONFIG: &str = "vortex-server.json";
const LOG_LINES: usize = 2000;

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct ServerConfig {
    pub name: String,
    /// `vanilla`, `paper` or `fabric`.
    pub kind: String,
    pub version: String,
    pub memory_mb: u32,
    #[serde(default)]
    pub created: u64,
    /// Mojang Java runtime component the version needs.
    #[serde(default)]
    pub java: String,
    /// Back the world up every time the server stops.
    #[serde(default)]
    pub backup_on_stop: bool,
    /// Start it again if it crashes.
    #[serde(default)]
    pub restart_on_crash: bool,
    /// Keep this many backups (older ones are removed). 0 = keep all.
    #[serde(default = "default_keep")]
    pub keep_backups: u32,
    /// The public address from the tunnel, saved to share.
    #[serde(default)]
    pub public_address: String,
    /// Extra Java arguments.
    #[serde(default)]
    pub jvm_args: String,
}

fn default_keep() -> u32 {
    10
}

#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct ServerView {
    #[serde(flatten)]
    pub config: ServerConfig,
    pub running: bool,
    pub starting: bool,
    pub players: Vec<String>,
    pub started_at: u64,
    pub port: u16,
    pub max_players: u32,
    pub motd: String,
    pub lan_address: String,
}

struct Running {
    stdin: Arc<Mutex<ChildStdin>>,
    started: u64,
    ready: bool,
    stopping: bool,
    players: BTreeSet<String>,
}

static RUN: LazyLock<StdMutex<HashMap<String, Running>>> = LazyLock::new(Default::default);
static LOGS: LazyLock<StdMutex<HashMap<String, VecDeque<String>>>> = LazyLock::new(Default::default);

fn now() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0)
}

pub fn root(launcher: &Launcher) -> PathBuf {
    launcher.paths().root.join("servers")
}

fn clean_name(name: &str) -> Result<String> {
    let n: String = name.trim().chars().filter(|c| c.is_ascii_alphanumeric() || " -_.".contains(*c)).take(40).collect();
    let n = n.trim().trim_matches('.').to_string();
    if n.is_empty() {
        bail!("give the server a name (letters, numbers, spaces, - and _)");
    }
    Ok(n)
}

fn dir_of(root: &Path, name: &str) -> Result<PathBuf> {
    let n = clean_name(name)?;
    if n != name.trim() {
        bail!("no server called '{name}'");
    }
    Ok(root.join(n))
}

fn load_config(dir: &Path) -> Result<ServerConfig> {
    let b = std::fs::read(dir.join(CONFIG)).context("this folder isn't a Vortex server")?;
    Ok(serde_json::from_slice(&b)?)
}

fn save_config(dir: &Path, c: &ServerConfig) -> Result<()> {
    std::fs::write(dir.join(CONFIG), serde_json::to_vec_pretty(c)?)?;
    Ok(())
}

fn push_log(app: &AppHandle, name: &str, line: String) {
    {
        let mut logs = LOGS.lock().unwrap_or_else(|e| e.into_inner());
        let q = logs.entry(name.to_string()).or_default();
        q.push_back(line.clone());
        while q.len() > LOG_LINES {
            q.pop_front();
        }
    }
    let _ = app.emit("server-log", serde_json::json!({ "name": name, "line": line }));
}

fn emit_state(app: &AppHandle, name: &str) {
    let _ = app.emit("server-state", serde_json::json!({ "name": name }));
}

// ----------------------------------------------------------- properties --

/// `server.properties` as key → value (comments dropped).
pub fn read_properties(dir: &Path) -> Vec<(String, String)> {
    std::fs::read_to_string(dir.join("server.properties"))
        .unwrap_or_default()
        .lines()
        .filter(|l| !l.trim_start().starts_with('#'))
        .filter_map(|l| l.split_once('=').map(|(k, v)| (k.trim().to_string(), unescape(v.trim()))))
        .collect()
}

fn unescape(v: &str) -> String {
    v.replace("\\:", ":").replace("\\=", "=").replace("\\\\", "\\")
}

fn escape(v: &str) -> String {
    v.replace('\\', "\\\\").replace(':', "\\:").replace('=', "\\=").replace(['\n', '\r'], " ")
}

/// Change some keys and keep the rest of the file as it was.
pub fn write_properties(dir: &Path, changes: &HashMap<String, String>) -> Result<()> {
    let path = dir.join("server.properties");
    let text = std::fs::read_to_string(&path).unwrap_or_default();
    let mut seen = BTreeSet::new();
    let mut out = String::new();
    for line in text.lines() {
        if let Some((k, _)) = line.split_once('=').filter(|_| !line.trim_start().starts_with('#')) {
            let k = k.trim();
            if let Some(v) = changes.get(k) {
                out.push_str(&format!("{k}={}\n", escape(v)));
                seen.insert(k.to_string());
                continue;
            }
        }
        out.push_str(line);
        out.push('\n');
    }
    for (k, v) in changes {
        let ok = !k.is_empty() && k.chars().all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '.' || c == '_');
        if ok && !seen.contains(k) {
            out.push_str(&format!("{k}={}\n", escape(v)));
        }
    }
    std::fs::write(path, out)?;
    Ok(())
}

fn prop<'a>(props: &'a [(String, String)], key: &str) -> Option<&'a str> {
    props.iter().find(|(k, _)| k == key).map(|(_, v)| v.as_str())
}

fn lan_ip() -> Option<String> {
    // Asks the OS which interface reaches the internet; nothing is sent.
    let s = std::net::UdpSocket::bind("0.0.0.0:0").ok()?;
    s.connect("8.8.8.8:80").ok()?;
    Some(s.local_addr().ok()?.ip().to_string())
}

// ------------------------------------------------------------------ list --

pub fn list(root: &Path) -> Vec<ServerView> {
    let mut out: Vec<ServerView> = std::fs::read_dir(root)
        .into_iter()
        .flatten()
        .flatten()
        .filter_map(|d| view(&d.path()).ok())
        .collect();
    out.sort_by_key(|v| std::cmp::Reverse(v.config.created));
    out
}

pub fn view(dir: &Path) -> Result<ServerView> {
    let config = load_config(dir)?;
    let props = read_properties(dir);
    let port = prop(&props, "server-port").and_then(|p| p.parse().ok()).unwrap_or(25565);
    let run = RUN.lock().unwrap_or_else(|e| e.into_inner());
    let r = run.get(&config.name);
    Ok(ServerView {
        running: r.is_some(),
        starting: r.is_some_and(|r| !r.ready),
        players: r.map(|r| r.players.iter().cloned().collect()).unwrap_or_default(),
        started_at: r.map(|r| r.started).unwrap_or(0),
        port,
        max_players: prop(&props, "max-players").and_then(|p| p.parse().ok()).unwrap_or(20),
        motd: prop(&props, "motd").unwrap_or("A Minecraft Server").to_string(),
        lan_address: lan_ip().map(|ip| if port == 25565 { ip } else { format!("{ip}:{port}") }).unwrap_or_default(),
        config,
    })
}

pub fn log(name: &str) -> Vec<String> {
    LOGS.lock().unwrap_or_else(|e| e.into_inner()).get(name).map(|q| q.iter().cloned().collect()).unwrap_or_default()
}

// -------------------------------------------------------------- versions --

/// Minecraft versions a server type can run, newest first.
pub async fn versions(launcher: &Launcher, kind: &str) -> Result<Vec<String>> {
    let http = launcher.http();
    match kind {
        "paper" => {
            let v: serde_json::Value = http.get("https://fill.papermc.io/v3/projects/paper").send().await?.error_for_status()?.json().await?;
            let mut out = Vec::new();
            if let Some(groups) = v.get("versions").and_then(|g| g.as_object()) {
                for list in groups.values() {
                    for id in list.as_array().into_iter().flatten().filter_map(|x| x.as_str()) {
                        if !id.contains('-') {
                            out.push(id.to_string());
                        }
                    }
                }
            }
            sort_versions(&mut out);
            Ok(out)
        }
        "fabric" => {
            let v: Vec<serde_json::Value> = http.get("https://meta.fabricmc.net/v2/versions/game").send().await?.error_for_status()?.json().await?;
            Ok(v.iter().filter(|g| g.get("stable").and_then(|s| s.as_bool()) == Some(true)).filter_map(|g| g.get("version")?.as_str().map(String::from)).collect())
        }
        _ => {
            let m = launcher.manifest(false).await?;
            Ok(m.of_type(vortex_core::manifest::VersionType::Release).map(|v| v.id.clone()).collect())
        }
    }
}

fn sort_versions(v: &mut [String]) {
    let key = |s: &String| s.split('.').map(|p| p.parse::<u32>().unwrap_or(0)).collect::<Vec<_>>();
    v.sort_by_key(|s| std::cmp::Reverse(key(s)));
}

// ---------------------------------------------------------------- create --

async fn download(http: &Client, url: &str, to: &Path, progress: &ProgressFn, stage: &str) -> Result<()> {
    let mut res = http.get(url).send().await?.error_for_status().with_context(|| format!("couldn't download {url}"))?;
    let total = res.content_length().unwrap_or(0);
    let mut buf = Vec::with_capacity(total as usize);
    while let Some(chunk) = res.chunk().await? {
        buf.extend_from_slice(&chunk);
        progress(Progress { stage: stage.into(), done: buf.len() as u64, total });
    }
    let part = to.with_extension("part");
    std::fs::write(&part, &buf)?;
    std::fs::rename(&part, to)?;
    Ok(())
}

/// Vanilla's version JSON (server jar and the Java it needs).
async fn version_json(launcher: &Launcher, version: &str) -> Result<serde_json::Value> {
    let m = launcher.manifest(false).await?;
    let mv = m.find(version).ok_or_else(|| anyhow!("Minecraft {version} isn't a known version"))?;
    let bytes = launcher.downloader().get_cached(&mv.url, &launcher.paths().cache().join("server-versions").join(format!("{version}.json"))).await?;
    Ok(serde_json::from_slice(&bytes)?)
}

pub async fn create(launcher: &Launcher, root: &Path, mut config: ServerConfig, progress: &ProgressFn) -> Result<ServerView> {
    config.name = clean_name(&config.name)?;
    let dir = root.join(&config.name);
    if dir.exists() {
        bail!("there's already a server called {}", config.name);
    }
    let http = launcher.http();
    let vj = version_json(launcher, &config.version).await?;
    config.java = vj.pointer("/javaVersion/component").and_then(|c| c.as_str()).unwrap_or("jre-legacy").to_string();
    let url = match config.kind.as_str() {
        "paper" => {
            let builds: Vec<serde_json::Value> = http
                .get(format!("https://fill.papermc.io/v3/projects/paper/versions/{}/builds", config.version))
                .send()
                .await?
                .error_for_status()
                .context("Paper doesn't have that version")?
                .json()
                .await?;
            let b = builds.iter().find(|b| b.get("channel").and_then(|c| c.as_str()) == Some("STABLE")).or(builds.first()).ok_or_else(|| anyhow!("Paper has no build for {}", config.version))?;
            b.pointer("/downloads/server:default/url").and_then(|u| u.as_str()).ok_or_else(|| anyhow!("Paper's build has no download"))?.to_string()
        }
        "fabric" => {
            let loaders: Vec<serde_json::Value> = http.get(format!("https://meta.fabricmc.net/v2/versions/loader/{}", config.version)).send().await?.error_for_status()?.json().await?;
            let loader = loaders.iter().filter_map(|l| l.pointer("/loader/version")?.as_str()).next().ok_or_else(|| anyhow!("Fabric doesn't support {} yet", config.version))?.to_string();
            let installers: Vec<serde_json::Value> = http.get("https://meta.fabricmc.net/v2/versions/installer").send().await?.error_for_status()?.json().await?;
            let inst = installers.iter().find(|i| i.get("stable").and_then(|s| s.as_bool()) == Some(true)).and_then(|i| i.get("version")?.as_str()).unwrap_or("1.1.0").to_string();
            format!("https://meta.fabricmc.net/v2/versions/loader/{}/{loader}/{inst}/server/jar", config.version)
        }
        _ => {
            config.kind = "vanilla".into();
            vj.pointer("/downloads/server/url").and_then(|u| u.as_str()).ok_or_else(|| anyhow!("Minecraft {} has no server download", config.version))?.to_string()
        }
    };
    std::fs::create_dir_all(&dir)?;
    let result = async {
        download(http, &url, &dir.join("server.jar"), progress, "Downloading the server").await?;
        // Accepted in the create dialog.
        std::fs::write(dir.join("eula.txt"), "# Accepted in Vortex Client (https://aka.ms/MinecraftEULA)\neula=true\n")?;
        let mut props = HashMap::new();
        for (k, v) in [
            ("motd", format!("{} \u{00a7}7· hosted with Vortex", config.name)),
            ("server-port", "25565".into()),
            ("max-players", "20".into()),
            ("view-distance", "10".into()),
            ("simulation-distance", "8".into()),
            ("difficulty", "normal".into()),
            ("gamemode", "survival".into()),
            ("online-mode", "true".into()),
            ("enable-command-block", "false".into()),
            ("spawn-protection", "16".into()),
        ] {
            props.insert(k.to_string(), v);
        }
        write_properties(&dir, &props)?;
        std::fs::create_dir_all(dir.join(if config.kind == "fabric" { "mods" } else { "plugins" }))?;
        if config.kind == "vanilla" {
            let _ = std::fs::remove_dir(dir.join("plugins"));
        }
        config.created = now();
        save_config(&dir, &config)
    }
    .await;
    if let Err(e) = result {
        let _ = std::fs::remove_dir_all(&dir);
        return Err(e);
    }
    view(&dir)
}

pub fn update_config(root: &Path, name: &str, patch: serde_json::Value) -> Result<ServerView> {
    let dir = dir_of(root, name)?;
    let c = load_config(&dir)?;
    let mut v = serde_json::to_value(&c)?;
    if let (Some(obj), Some(p)) = (v.as_object_mut(), patch.as_object()) {
        for (k, val) in p {
            if !["name", "kind", "version", "created", "java"].contains(&k.as_str()) {
                obj.insert(k.clone(), val.clone());
            }
        }
    }
    let mut next: ServerConfig = serde_json::from_value(v)?;
    next.memory_mb = next.memory_mb.clamp(512, 65536);
    save_config(&dir, &next)?;
    view(&dir)
}

pub fn delete(root: &Path, name: &str) -> Result<()> {
    if is_running(name) {
        bail!("stop the server first");
    }
    let dir = dir_of(root, name)?;
    load_config(&dir)?;
    std::fs::remove_dir_all(dir)?;
    Ok(())
}

pub fn any_running() -> bool {
    !RUN.lock().unwrap_or_else(|e| e.into_inner()).is_empty()
}

pub fn is_running(name: &str) -> bool {
    RUN.lock().unwrap_or_else(|e| e.into_inner()).contains_key(name)
}

// ----------------------------------------------------------- start / stop --

pub fn start(app: AppHandle, launcher: Arc<Launcher>, root: PathBuf, name: String) -> std::pin::Pin<Box<dyn std::future::Future<Output = Result<()>> + Send>> {
    Box::pin(start_inner(app, launcher, root, name))
}

async fn start_inner(app: AppHandle, launcher: Arc<Launcher>, root: PathBuf, name: String) -> Result<()> {
    if is_running(&name) {
        return Ok(());
    }
    let dir = dir_of(&root, &name)?;
    let config = load_config(&dir)?;
    let progress: ProgressFn = {
        let app = app.clone();
        let name = name.clone();
        Arc::new(move |p: Progress| {
            let _ = app.emit("server-progress", serde_json::json!({ "name": name, "stage": p.stage, "done": p.done, "total": p.total }));
        })
    };
    let component = if config.java.is_empty() { "java-runtime-delta".to_string() } else { config.java.clone() };
    let java = match vortex_core::java::ensure_runtime(launcher.downloader(), launcher.paths(), &component, &progress).await {
        Ok(p) => p,
        Err(e) => vortex_core::java::find_system_java().ok_or_else(|| e.context("no Java for this server"))?,
    };
    let mem = config.memory_mb.clamp(512, 65536);
    let mut args: Vec<String> = vec![format!("-Xms{}M", (mem / 2).max(512)), format!("-Xmx{mem}M")];
    // Aikar's flags: the usual G1 tuning for Minecraft servers.
    for a in [
        "-XX:+UseG1GC", "-XX:+ParallelRefProcEnabled", "-XX:MaxGCPauseMillis=200", "-XX:+UnlockExperimentalVMOptions",
        "-XX:+DisableExplicitGC", "-XX:+AlwaysPreTouch", "-XX:G1NewSizePercent=30", "-XX:G1MaxNewSizePercent=40",
        "-XX:G1HeapRegionSize=8M", "-XX:G1ReservePercent=20", "-XX:G1HeapWastePercent=5", "-XX:G1MixedGCCountTarget=4",
        "-XX:InitiatingHeapOccupancyPercent=15", "-XX:G1MixedGCLiveThresholdPercent=90", "-XX:G1RSetUpdatingPauseTimePercent=5",
        "-XX:SurvivorRatio=32", "-XX:+PerfDisableSharedMem", "-XX:MaxTenuringThreshold=1",
    ] {
        args.push(a.into());
    }
    args.extend(config.jvm_args.split_whitespace().map(String::from));
    args.extend(["-jar".into(), "server.jar".into(), "nogui".into()]);
    let mut cmd = Command::new(&java);
    cmd.args(&args).current_dir(&dir).stdin(Stdio::piped()).stdout(Stdio::piped()).stderr(Stdio::piped()).kill_on_drop(true);
    #[cfg(windows)]
    cmd.creation_flags(0x0800_0000);
    let mut child = cmd.spawn().with_context(|| format!("couldn't start Java ({})", java.display()))?;
    let stdin = child.stdin.take().ok_or_else(|| anyhow!("no console"))?;
    let stdout = child.stdout.take().ok_or_else(|| anyhow!("no output"))?;
    let stderr = child.stderr.take().ok_or_else(|| anyhow!("no output"))?;
    LOGS.lock().unwrap_or_else(|e| e.into_inner()).insert(name.clone(), VecDeque::new());
    RUN.lock().unwrap_or_else(|e| e.into_inner()).insert(
        name.clone(),
        Running { stdin: Arc::new(Mutex::new(stdin)), started: now(), ready: false, stopping: false, players: BTreeSet::new() },
    );
    push_log(&app, &name, format!("[Vortex] Starting {} {} with {} MB of memory", config.kind, config.version, mem));
    emit_state(&app, &name);

    for (stream, is_err) in [(Box::new(stdout) as Box<dyn tokio::io::AsyncRead + Unpin + Send>, false), (Box::new(stderr), true)] {
        let app = app.clone();
        let name = name.clone();
        tokio::spawn(async move {
            let mut lines = BufReader::new(stream).lines();
            while let Ok(Some(line)) = lines.next_line().await {
                track(&app, &name, &line);
                push_log(&app, &name, if is_err && !line.contains("WARN") && !line.contains("INFO") { format!("[stderr] {line}") } else { line });
            }
        });
    }
    tokio::spawn(async move {
        let status = child.wait().await;
        let was_stopping = RUN.lock().unwrap_or_else(|e| e.into_inner()).remove(&name).is_some_and(|r| r.stopping);
        let code = status.as_ref().ok().and_then(|s| s.code());
        push_log(&app, &name, format!("[Vortex] The server stopped{}", code.map(|c| format!(" (exit code {c})")).unwrap_or_default()));
        emit_state(&app, &name);
        let config = load_config(&dir).ok();
        if config.as_ref().is_some_and(|c| c.backup_on_stop) {
            match backup(&app, &root, &name).await {
                Ok(b) => push_log(&app, &name, format!("[Vortex] Backed up the world: {}", b.file)),
                Err(e) => push_log(&app, &name, format!("[Vortex] Backup failed: {e:#}")),
            }
        }
        let crashed = !was_stopping && code != Some(0);
        if crashed && config.is_some_and(|c| c.restart_on_crash) {
            push_log(&app, &name, "[Vortex] It crashed; starting it again in 5 seconds".into());
            tokio::time::sleep(Duration::from_secs(5)).await;
            if let Err(e) = start(app.clone(), launcher, root, name.clone()).await {
                push_log(&app, &name, format!("[Vortex] Couldn't restart it: {e:#}"));
            }
        }
    });
    Ok(())
}

/// Follow joins, leaves and "Done" in the log.
fn track(app: &AppHandle, name: &str, line: &str) {
    let mut changed = false;
    {
        let mut run = RUN.lock().unwrap_or_else(|e| e.into_inner());
        let Some(r) = run.get_mut(name) else { return };
        let msg = line.rsplit_once("]: ").map(|(_, m)| m).unwrap_or(line);
        if !r.ready && msg.starts_with("Done (") {
            r.ready = true;
            changed = true;
        }
        for (suffix, join) in [(" joined the game", true), (" left the game", false)] {
            if let Some(who) = msg.strip_suffix(suffix) {
                let who = who.trim();
                if !who.is_empty() && who.len() <= 16 && who.chars().all(|c| c.is_ascii_alphanumeric() || c == '_') {
                    if join {
                        r.players.insert(who.to_string());
                    } else {
                        r.players.remove(who);
                    }
                    changed = true;
                }
            }
        }
    }
    if changed {
        emit_state(app, name);
    }
}

pub async fn command(name: &str, line: &str) -> Result<()> {
    let stdin = {
        let run = RUN.lock().unwrap_or_else(|e| e.into_inner());
        run.get(name).map(|r| r.stdin.clone()).ok_or_else(|| anyhow!("the server isn't running"))?
    };
    let clean: String = line.trim().trim_start_matches('/').chars().filter(|c| !c.is_control()).collect();
    if clean.is_empty() {
        return Ok(());
    }
    let mut s = stdin.lock().await;
    s.write_all(format!("{clean}\n").as_bytes()).await?;
    s.flush().await?;
    Ok(())
}

pub async fn stop(name: &str) -> Result<()> {
    {
        let mut run = RUN.lock().unwrap_or_else(|e| e.into_inner());
        match run.get_mut(name) {
            Some(r) => r.stopping = true,
            None => return Ok(()),
        }
    }
    command(name, "stop").await
}

/// Stop every server (the launcher is closing).
pub async fn stop_all() {
    let names: Vec<String> = RUN.lock().unwrap_or_else(|e| e.into_inner()).keys().cloned().collect();
    for n in &names {
        let _ = stop(n).await;
    }
    for _ in 0..60 {
        if RUN.lock().unwrap_or_else(|e| e.into_inner()).is_empty() {
            break;
        }
        tokio::time::sleep(Duration::from_millis(500)).await;
    }
}

// --------------------------------------------------------------- players --

#[derive(Serialize, Deserialize, Clone, Default)]
#[serde(rename_all = "camelCase")]
pub struct PlayerEntry {
    #[serde(default)]
    pub name: String,
    #[serde(default)]
    pub uuid: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub level: Option<u32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub reason: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub ip: Option<String>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Players {
    pub online: Vec<String>,
    pub ops: Vec<PlayerEntry>,
    pub whitelist: Vec<PlayerEntry>,
    pub banned: Vec<PlayerEntry>,
    pub banned_ips: Vec<PlayerEntry>,
    pub whitelist_on: bool,
}

fn read_list(dir: &Path, file: &str) -> Vec<PlayerEntry> {
    std::fs::read(dir.join(file)).ok().and_then(|b| serde_json::from_slice(&b).ok()).unwrap_or_default()
}

pub fn players(root: &Path, name: &str) -> Result<Players> {
    let dir = dir_of(root, name)?;
    let v = view(&dir)?;
    let props = read_properties(&dir);
    Ok(Players {
        online: v.players,
        ops: read_list(&dir, "ops.json"),
        whitelist: read_list(&dir, "whitelist.json"),
        banned: read_list(&dir, "banned-players.json"),
        banned_ips: read_list(&dir, "banned-ips.json"),
        whitelist_on: prop(&props, "white-list") == Some("true"),
    })
}

/// Op, whitelist, ban… a player. Running servers get the console command
/// (so it takes effect at once); stopped ones get their lists edited.
pub async fn player_action(http: &Client, root: &Path, name: &str, action: &str, player: &str, reason: &str) -> Result<()> {
    let p: String = player.trim().chars().filter(|c| c.is_ascii_alphanumeric() || *c == '_' || *c == '.' || *c == ':').take(45).collect();
    if p.is_empty() {
        bail!("type a player name");
    }
    let reason: String = reason.chars().filter(|c| !c.is_control()).take(100).collect();
    let line = match action {
        "op" => format!("op {p}"),
        "deop" => format!("deop {p}"),
        "whitelist-add" => format!("whitelist add {p}"),
        "whitelist-remove" => format!("whitelist remove {p}"),
        "ban" => format!("ban {p} {reason}"),
        "pardon" => format!("pardon {p}"),
        "ban-ip" => format!("ban-ip {p} {reason}"),
        "pardon-ip" => format!("pardon-ip {p}"),
        "kick" => format!("kick {p} {reason}"),
        "whitelist-on" => "whitelist on".into(),
        "whitelist-off" => "whitelist off".into(),
        _ => bail!("unknown action"),
    };
    if is_running(name) {
        return command(name, &line).await;
    }
    let dir = dir_of(root, name)?;
    match action {
        "kick" => bail!("start the server to kick players"),
        "whitelist-on" | "whitelist-off" => {
            let mut m = HashMap::new();
            m.insert("white-list".to_string(), (action == "whitelist-on").to_string());
            return write_properties(&dir, &m);
        }
        _ => {}
    }
    let (file, add) = match action {
        "op" => ("ops.json", true),
        "deop" => ("ops.json", false),
        "whitelist-add" => ("whitelist.json", true),
        "whitelist-remove" => ("whitelist.json", false),
        "ban" => ("banned-players.json", true),
        "pardon" => ("banned-players.json", false),
        "ban-ip" => ("banned-ips.json", true),
        _ => ("banned-ips.json", false),
    };
    let mut list: Vec<serde_json::Value> = std::fs::read(dir.join(file)).ok().and_then(|b| serde_json::from_slice(&b).ok()).unwrap_or_default();
    let key = if file == "banned-ips.json" { "ip" } else { "name" };
    list.retain(|e| !e.get(key).and_then(|n| n.as_str()).is_some_and(|n| n.eq_ignore_ascii_case(&p)));
    if add {
        let mut e = serde_json::Map::new();
        if file == "banned-ips.json" {
            e.insert("ip".into(), p.clone().into());
        } else {
            let online = prop(&read_properties(&dir), "online-mode") != Some("false");
            let (uuid, real) = if online { lookup_uuid(http, &p).await? } else { (vortex_core::auth::offline_uuid(&p), p.clone()) };
            e.insert("uuid".into(), dashed(&uuid).into());
            e.insert("name".into(), real.into());
        }
        if file == "ops.json" {
            e.insert("level".into(), 4.into());
            e.insert("bypassesPlayerLimit".into(), false.into());
        }
        if file.starts_with("banned") {
            e.insert("created".into(), "2000-01-01 00:00:00 +0000".into());
            e.insert("source".into(), "Vortex".into());
            e.insert("expires".into(), "forever".into());
            e.insert("reason".into(), if reason.is_empty() { "Banned by an operator.".into() } else { reason.clone().into() });
        }
        list.push(serde_json::Value::Object(e));
    }
    std::fs::write(dir.join(file), serde_json::to_vec_pretty(&list)?)?;
    Ok(())
}

async fn lookup_uuid(http: &Client, name: &str) -> Result<(String, String)> {
    #[derive(Deserialize)]
    struct P {
        id: String,
        name: String,
    }
    let r = http.get(format!("https://api.mojang.com/users/profiles/minecraft/{name}")).send().await?;
    if !r.status().is_success() {
        bail!("there's no Minecraft player called {name}");
    }
    let p: P = r.json().await?;
    Ok((p.id, p.name))
}

fn dashed(u: &str) -> String {
    let u = u.replace('-', "");
    if u.len() != 32 {
        return u;
    }
    format!("{}-{}-{}-{}-{}", &u[..8], &u[8..12], &u[12..16], &u[16..20], &u[20..])
}

// --------------------------------------------------------------- backups --

#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct Backup {
    pub file: String,
    pub size: u64,
    pub time: u64,
}

fn world_dirs(dir: &Path) -> Vec<PathBuf> {
    let level = prop(&read_properties(dir), "level-name").unwrap_or("world").to_string();
    [level.clone(), format!("{level}_nether"), format!("{level}_the_end")].into_iter().map(|w| dir.join(w)).filter(|p| p.is_dir()).collect()
}

pub fn backups(root: &Path, name: &str) -> Result<Vec<Backup>> {
    let dir = dir_of(root, name)?.join("backups");
    let mut out: Vec<Backup> = std::fs::read_dir(&dir)
        .into_iter()
        .flatten()
        .flatten()
        .filter(|e| e.path().extension().is_some_and(|x| x == "zip"))
        .filter_map(|e| {
            let m = e.metadata().ok()?;
            Some(Backup {
                file: e.file_name().to_string_lossy().to_string(),
                size: m.len(),
                time: m.modified().ok()?.duration_since(UNIX_EPOCH).ok()?.as_secs(),
            })
        })
        .collect();
    out.sort_by_key(|b| std::cmp::Reverse(b.time));
    Ok(out)
}

pub async fn backup(app: &AppHandle, root: &Path, name: &str) -> Result<Backup> {
    let dir = dir_of(root, name)?;
    let running = is_running(name);
    if running {
        command(name, "save-off").await?;
        command(name, "save-all flush").await?;
        tokio::time::sleep(Duration::from_secs(3)).await;
    }
    let worlds = world_dirs(&dir);
    let out_dir = dir.join("backups");
    let stamp = chrono_like(now());
    let file = format!("{name}-{stamp}.zip");
    let target = out_dir.join(&file);
    let base = dir.clone();
    let res = tokio::task::spawn_blocking(move || -> Result<u64> {
        if worlds.is_empty() {
            bail!("there's no world yet; start the server once first");
        }
        std::fs::create_dir_all(target.parent().expect("has parent"))?;
        let f = std::fs::File::create(&target)?;
        let mut zip = vortex_core::zip::ZipWriter::new(f);
        let opts = vortex_core::zip::write::SimpleFileOptions::default().compression_method(vortex_core::zip::CompressionMethod::Deflated);
        for w in worlds {
            let mut stack = vec![w];
            while let Some(d) = stack.pop() {
                for e in std::fs::read_dir(&d)?.flatten() {
                    let p = e.path();
                    let rel = p.strip_prefix(&base)?.to_string_lossy().replace('\\', "/");
                    if p.is_dir() {
                        stack.push(p);
                    } else if !rel.ends_with("session.lock") {
                        zip.start_file(rel, opts)?;
                        let mut buf = Vec::new();
                        std::fs::File::open(&p)?.read_to_end(&mut buf)?;
                        zip.write_all(&buf)?;
                    }
                }
            }
        }
        zip.finish()?;
        Ok(std::fs::metadata(&target)?.len())
    })
    .await
    .map_err(|e| anyhow!("{e}"))?;
    if running {
        let _ = command(name, "save-on").await;
    }
    let size = res?;
    // Keep only the newest few.
    if let Ok(c) = load_config(&dir) {
        if c.keep_backups > 0 {
            for old in backups(root, name)?.into_iter().skip(c.keep_backups as usize) {
                let _ = std::fs::remove_file(out_dir.join(old.file));
            }
        }
    }
    emit_state(app, name);
    Ok(Backup { file, size, time: now() })
}

/// YYYYMMDD-HHMMSS in UTC, without a date crate.
fn chrono_like(secs: u64) -> String {
    let days = (secs / 86400) as i64;
    let rem = secs % 86400;
    // Civil-from-days (Howard Hinnant).
    let z = days + 719_468;
    let era = z.div_euclid(146_097);
    let doe = z - era * 146_097;
    let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146_096) / 365;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = doy - (153 * mp + 2) / 5 + 1;
    let m = if mp < 10 { mp + 3 } else { mp - 9 };
    let y = yoe + era * 400 + i64::from(m <= 2);
    format!("{y:04}{m:02}{d:02}-{:02}{:02}{:02}", rem / 3600, (rem % 3600) / 60, rem % 60)
}

fn backup_path(root: &Path, name: &str, file: &str) -> Result<PathBuf> {
    if file.contains(['/', '\\']) || file.starts_with('.') || !file.ends_with(".zip") {
        bail!("invalid backup");
    }
    let p = dir_of(root, name)?.join("backups").join(file);
    if !p.is_file() {
        bail!("that backup is gone");
    }
    Ok(p)
}

pub fn delete_backup(root: &Path, name: &str, file: &str) -> Result<()> {
    std::fs::remove_file(backup_path(root, name, file)?)?;
    Ok(())
}

pub async fn restore_backup(root: &Path, name: &str, file: &str) -> Result<()> {
    if is_running(name) {
        bail!("stop the server before restoring a backup");
    }
    let zip_path = backup_path(root, name, file)?;
    let dir = dir_of(root, name)?;
    tokio::task::spawn_blocking(move || -> Result<()> {
        for w in world_dirs(&dir) {
            std::fs::remove_dir_all(w)?;
        }
        let mut zip = vortex_core::zip::ZipArchive::new(std::fs::File::open(zip_path)?)?;
        for i in 0..zip.len() {
            let mut e = zip.by_index(i)?;
            let Some(rel) = e.enclosed_name() else { continue };
            let out = dir.join(rel);
            if e.is_dir() {
                std::fs::create_dir_all(&out)?;
                continue;
            }
            std::fs::create_dir_all(out.parent().expect("has parent"))?;
            let mut buf = Vec::new();
            e.read_to_end(&mut buf)?;
            std::fs::write(out, buf)?;
        }
        Ok(())
    })
    .await
    .map_err(|e| anyhow!("{e}"))?
}

// ------------------------------------------------------ plugins and mods --

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AddonHit {
    pub id: String,
    pub title: String,
    pub description: String,
    pub icon: String,
    pub downloads: u64,
    pub author: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AddonFile {
    pub file: String,
    pub size: u64,
    pub enabled: bool,
}

fn addon_dir(dir: &Path, kind: &str) -> PathBuf {
    dir.join(if kind == "fabric" { "mods" } else { "plugins" })
}

pub fn addons(root: &Path, name: &str) -> Result<Vec<AddonFile>> {
    let dir = dir_of(root, name)?;
    let c = load_config(&dir)?;
    let mut out: Vec<AddonFile> = std::fs::read_dir(addon_dir(&dir, &c.kind))
        .into_iter()
        .flatten()
        .flatten()
        .filter_map(|e| {
            let f = e.file_name().to_string_lossy().to_string();
            let enabled = f.ends_with(".jar");
            (enabled || f.ends_with(".jar.disabled")).then(|| AddonFile { size: e.metadata().map(|m| m.len()).unwrap_or(0), file: f, enabled })
        })
        .collect();
    out.sort_by_key(|a| a.file.to_lowercase());
    Ok(out)
}

fn loaders_for(kind: &str) -> &'static [&'static str] {
    if kind == "fabric" {
        &["fabric"]
    } else {
        &["paper", "spigot", "bukkit", "purpur", "folia"]
    }
}

pub async fn addon_search(http: &Client, root: &Path, name: &str, query: &str) -> Result<Vec<AddonHit>> {
    let c = load_config(&dir_of(root, name)?)?;
    if c.kind == "vanilla" {
        bail!("Vanilla servers can't use plugins or mods");
    }
    let (ptype, cats): (&str, Vec<String>) = if c.kind == "fabric" {
        ("mod", vec!["categories:fabric".into()])
    } else {
        ("plugin", loaders_for("paper").iter().map(|l| format!("categories:{l}")).collect())
    };
    let mut facets = vec![vec![format!("project_type:{ptype}")], cats, vec![format!("versions:{}", c.version)]];
    if c.kind == "fabric" {
        facets.push(vec!["server_side:required".into(), "server_side:optional".into()]);
    }
    let facets = serde_json::to_string(&facets)?;
    let v: serde_json::Value = http
        .get("https://api.modrinth.com/v2/search")
        .query(&[("query", query), ("facets", &facets), ("limit", "30"), ("index", if query.is_empty() { "downloads" } else { "relevance" })])
        .send()
        .await?
        .error_for_status()?
        .json()
        .await?;
    Ok(v.get("hits")
        .and_then(|h| h.as_array())
        .into_iter()
        .flatten()
        .filter_map(|h| {
            Some(AddonHit {
                id: h.get("project_id")?.as_str()?.to_string(),
                title: h.get("title")?.as_str()?.to_string(),
                description: h.get("description").and_then(|d| d.as_str()).unwrap_or("").to_string(),
                icon: h.get("icon_url").and_then(|d| d.as_str()).unwrap_or("").to_string(),
                downloads: h.get("downloads").and_then(|d| d.as_u64()).unwrap_or(0),
                author: h.get("author").and_then(|d| d.as_str()).unwrap_or("").to_string(),
            })
        })
        .collect())
}

/// Install a plugin or mod (and the mods it needs) from Modrinth.
pub async fn addon_install(http: &Client, root: &Path, name: &str, project: &str) -> Result<Vec<String>> {
    let dir = dir_of(root, name)?;
    let c = load_config(&dir)?;
    let target = addon_dir(&dir, &c.kind);
    std::fs::create_dir_all(&target)?;
    let mut todo = vec![project.to_string()];
    let mut done = BTreeSet::new();
    let mut installed = Vec::new();
    while let Some(id) = todo.pop() {
        if !done.insert(id.clone()) || done.len() > 12 {
            continue;
        }
        let id: String = id.chars().filter(|c| c.is_ascii_alphanumeric()).collect();
        let loaders = serde_json::to_string(loaders_for(&c.kind))?;
        let gv = serde_json::to_string(&[&c.version])?;
        let vers: Vec<serde_json::Value> = http
            .get(format!("https://api.modrinth.com/v2/project/{id}/version"))
            .query(&[("loaders", loaders.as_str()), ("game_versions", gv.as_str())])
            .send()
            .await?
            .error_for_status()?
            .json()
            .await?;
        let Some(v) = vers.first() else {
            if installed.is_empty() {
                bail!("there's no version of it for {} {}", c.kind, c.version);
            }
            continue;
        };
        let files = v.get("files").and_then(|f| f.as_array()).cloned().unwrap_or_default();
        let f = files.iter().find(|f| f.get("primary").and_then(|p| p.as_bool()) == Some(true)).or(files.first()).ok_or_else(|| anyhow!("no file to download"))?;
        let url = f.get("url").and_then(|u| u.as_str()).unwrap_or("");
        let fname: String = f.get("filename").and_then(|u| u.as_str()).unwrap_or("addon.jar").chars().filter(|c| !"/\\:".contains(*c)).collect();
        if !url.starts_with("https://") || !fname.ends_with(".jar") {
            continue;
        }
        if !target.join(&fname).exists() {
            let bytes = http.get(url).send().await?.error_for_status()?.bytes().await?;
            std::fs::write(target.join(&fname), &bytes)?;
            installed.push(fname);
        }
        for d in v.get("dependencies").and_then(|d| d.as_array()).into_iter().flatten() {
            if d.get("dependency_type").and_then(|t| t.as_str()) == Some("required") {
                if let Some(p) = d.get("project_id").and_then(|p| p.as_str()) {
                    todo.push(p.to_string());
                }
            }
        }
    }
    Ok(installed)
}

pub fn addon_toggle(root: &Path, name: &str, file: &str, enabled: Option<bool>) -> Result<()> {
    let dir = dir_of(root, name)?;
    let c = load_config(&dir)?;
    if file.contains(['/', '\\']) || file.starts_with('.') {
        bail!("invalid file");
    }
    let p = addon_dir(&dir, &c.kind).join(file);
    if !p.is_file() {
        bail!("that file is gone");
    }
    match enabled {
        None => std::fs::remove_file(p)?,
        Some(true) if file.ends_with(".disabled") => std::fs::rename(&p, p.with_extension(""))?,
        Some(false) if file.ends_with(".jar") => std::fs::rename(&p, p.with_extension("jar.disabled"))?,
        _ => {}
    }
    Ok(())
}

// ---------------------------------------------------------------- tunnel --

/// playit.gg's agent: a free tunnel, so friends can join without port
/// forwarding. It opens in its own window and walks you through linking it
/// to a free playit.gg account; then make a Minecraft Java tunnel there.
pub async fn open_tunnel(http: &Client, root: &Path) -> Result<()> {
    let tools = root.join(".tools");
    std::fs::create_dir_all(&tools)?;
    let (asset, exe) = if cfg!(windows) {
        ("playit-windows-x86_64-signed.exe", "playit.exe")
    } else if cfg!(target_os = "macos") {
        bail!("playit.gg has no agent for macOS; use your LAN address or port forwarding")
    } else {
        ("playit-linux-amd64", "playit")
    };
    let path = tools.join(exe);
    if !path.is_file() {
        let url = format!("https://github.com/playit-cloud/playit-agent/releases/latest/download/{asset}");
        let bytes = http.get(&url).send().await?.error_for_status().context("couldn't download playit.gg's agent")?.bytes().await?;
        std::fs::write(&path, &bytes)?;
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            std::fs::set_permissions(&path, std::fs::Permissions::from_mode(0o755))?;
        }
    }
    let mut cmd = std::process::Command::new(&path);
    cmd.current_dir(&tools);
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        cmd.creation_flags(0x0000_0010); // CREATE_NEW_CONSOLE: playit shows its setup link there
    }
    cmd.spawn().context("couldn't start playit.gg's agent")?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn properties_round_trip() {
        let dir = std::env::temp_dir().join(format!("vortex-host-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        std::fs::write(dir.join("server.properties"), "#Minecraft server properties\nmotd=Hello\\: world\nmax-players=20\n").unwrap();
        let mut m = HashMap::new();
        m.insert("max-players".to_string(), "8".to_string());
        m.insert("pvp".to_string(), "false".to_string());
        write_properties(&dir, &m).unwrap();
        let p = read_properties(&dir);
        let text = std::fs::read_to_string(dir.join("server.properties")).unwrap();
        let _ = std::fs::remove_dir_all(&dir);
        assert!(text.starts_with("#Minecraft"));
        assert_eq!(prop(&p, "motd"), Some("Hello: world"));
        assert_eq!(prop(&p, "max-players"), Some("8"));
        assert_eq!(prop(&p, "pvp"), Some("false"));
    }

    /// Downloads real servers; run with `cargo test -p vortex-app real_servers -- --ignored`.
    #[tokio::test]
    #[ignore]
    async fn real_servers() {
        let root = std::env::var("VORTEX_HOST_TEST").map(PathBuf::from).unwrap_or_else(|_| std::env::temp_dir().join("vortex-host-real"));
        let launcher = Launcher::new(vortex_core::paths::Paths::new(root.join("data"))).unwrap();
        let servers = root.join("servers");
        let quiet = vortex_core::progress::silent();
        for kind in ["paper", "fabric", "vanilla"] {
            let vs = versions(&launcher, kind).await.unwrap();
            assert!(vs.iter().any(|v| v == "1.21.4"), "{kind}: {:?}", &vs[..vs.len().min(5)]);
            let name = format!("test-{kind}");
            let _ = std::fs::remove_dir_all(servers.join(&name));
            let cfg = ServerConfig { name: name.clone(), kind: kind.into(), version: "1.21.4".into(), memory_mb: 2048, created: 0, java: String::new(), backup_on_stop: false, restart_on_crash: false, keep_backups: 10, public_address: String::new(), jvm_args: String::new() };
            let v = create(&launcher, &servers, cfg, &quiet).await.unwrap();
            assert_eq!(v.port, 25565);
            let jar = std::fs::metadata(servers.join(&name).join("server.jar")).unwrap().len();
            println!("{kind}: server.jar {jar} bytes, java {}", v.config.java);
            assert!(jar > 100_000 || kind == "fabric");
        }
        let http = launcher.http().clone();
        let hits = addon_search(&http, &servers, "test-paper", "").await.unwrap();
        println!("paper plugins: {:?}", hits.iter().take(5).map(|h| &h.title).collect::<Vec<_>>());
        let got = addon_install(&http, &servers, "test-paper", &hits[0].id).await.unwrap();
        println!("installed {got:?}");
        let mods = addon_search(&http, &servers, "test-fabric", "lithium").await.unwrap();
        let got = addon_install(&http, &servers, "test-fabric", &mods[0].id).await.unwrap();
        println!("fabric installed {got:?}");
        assert!(!addons(&servers, "test-fabric").unwrap().is_empty());
        player_action(&http, &servers, "test-vanilla", "op", "Notch", "").await.unwrap();
        assert_eq!(players(&servers, "test-vanilla").unwrap().ops[0].name, "Notch");
    }

    #[test]
    fn names_and_dates() {
        assert!(clean_name("  ").is_err());
        assert_eq!(clean_name("My Server!").unwrap(), "My Server");
        assert_eq!(chrono_like(0), "19700101-000000");
        assert_eq!(chrono_like(1_700_000_000), "20231114-221320");
        assert_eq!(dashed("069a79f444e94726a5befca90e38aaf5"), "069a79f4-44e9-4726-a5be-fca90e38aaf5");
    }
}
