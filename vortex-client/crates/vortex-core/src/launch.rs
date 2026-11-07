//! Building the `java …` command line and starting the game.

use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::process::{ExitStatus, Stdio};

use anyhow::{anyhow, Context, Result};
use tokio::io::{AsyncBufReadExt, AsyncRead, BufReader};
use tokio::process::{Child, Command};
use tokio::sync::mpsc;

use crate::install::InstalledVersion;
use crate::logs::{LogLine, LogParser};
use crate::os;
use crate::paths::Paths;
use crate::rules::Environment;
use crate::{LAUNCHER_NAME, LAUNCHER_VERSION};

/// Who is playing.
#[derive(Debug, Clone)]
pub struct LaunchSession {
    pub username: String,
    /// Profile UUID without dashes.
    pub uuid: String,
    pub access_token: String,
    /// `msa` for Microsoft accounts, `legacy` for offline.
    pub user_type: String,
    pub xuid: String,
}

impl LaunchSession {
    pub fn offline(username: &str) -> Self {
        Self {
            username: username.to_string(),
            uuid: crate::auth::offline_uuid(username),
            access_token: "0".to_string(),
            user_type: "legacy".to_string(),
            xuid: "0".to_string(),
        }
    }
}

#[derive(Debug, Clone)]
pub struct LaunchOptions {
    pub java: PathBuf,
    pub game_dir: PathBuf,
    pub min_memory_mb: u32,
    pub max_memory_mb: u32,
    pub extra_jvm_args: Vec<String>,
    pub resolution: Option<(u32, u32)>,
    pub fullscreen: bool,
    /// `host[:port]` to join straight after start-up.
    pub join_server: Option<String>,
}

#[derive(Debug, Clone)]
pub struct LaunchCommand {
    pub java: PathBuf,
    pub args: Vec<String>,
    pub working_dir: PathBuf,
    secrets: Vec<String>,
    /// Program (and its arguments) the Java command runs under, like
    /// `gamemoderun` or `prime-run`. Empty = run Java directly.
    pub wrapper: Vec<String>,
    /// Extra environment variables for the game.
    pub env: Vec<(String, String)>,
    /// Windows process priority flag (0 = normal).
    pub priority: u32,
}

impl LaunchCommand {
    /// The command line with access tokens redacted — safe to log or show.
    pub fn display_redacted(&self) -> String {
        let mut out = format!("\"{}\"", self.java.display());
        for arg in &self.args {
            let mut a = arg.clone();
            for s in &self.secrets {
                if s.len() > 4 {
                    a = a.replace(s.as_str(), "<redacted>");
                }
            }
            if a.contains(' ') {
                out.push_str(&format!(" \"{a}\""));
            } else {
                out.push(' ');
                out.push_str(&a);
            }
        }
        out
    }
}

/// Default JVM arguments for versions that predate `arguments.jvm` (≤ 1.12.2).
fn legacy_jvm_args() -> Vec<String> {
    let mut v = Vec::new();
    if cfg!(windows) {
        v.push("-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump".to_string());
    }
    if cfg!(target_arch = "x86") {
        v.push("-Xss1M".to_string());
    }
    v.extend(
        [
            "-Djava.library.path=${natives_directory}",
            "-Dminecraft.launcher.brand=${launcher_name}",
            "-Dminecraft.launcher.version=${launcher_version}",
            "-cp",
            "${classpath}",
        ]
        .map(String::from),
    );
    v
}

/// Replace every `${key}` that has a value; unknown keys are left alone.
pub fn substitute(s: &str, vars: &HashMap<&str, String>) -> String {
    let mut out = String::with_capacity(s.len());
    let mut rest = s;
    while let Some(start) = rest.find("${") {
        out.push_str(&rest[..start]);
        let after = &rest[start + 2..];
        match after.find('}') {
            Some(end) => {
                let key = &after[..end];
                match vars.get(key) {
                    Some(v) => out.push_str(v),
                    None => out.push_str(&rest[start..start + 2 + end + 1]),
                }
                rest = &after[end + 1..];
            }
            None => {
                out.push_str(&rest[start..]);
                rest = "";
            }
        }
    }
    out.push_str(rest);
    out
}

fn split_server(s: &str) -> (String, String) {
    match s.rsplit_once(':') {
        Some((host, port)) if !host.is_empty() && port.parse::<u16>().is_ok() => {
            (host.to_string(), port.to_string())
        }
        _ => (s.to_string(), "25565".to_string()),
    }
}

fn path_str(p: &Path) -> String {
    p.to_string_lossy().into_owned()
}

/// Build the full command line for an installed version.
pub fn build_command(
    installed: &InstalledVersion,
    paths: &Paths,
    session: &LaunchSession,
    opts: &LaunchOptions,
) -> Result<LaunchCommand> {
    let v = &installed.version;
    let main_class = v
        .main_class
        .as_deref()
        .ok_or_else(|| anyhow!("version {} has no mainClass", installed.id))?;

    let mut classpath: Vec<String> = installed.classpath.iter().map(|p| path_str(p)).collect();
    classpath.push(path_str(&installed.client_jar));
    let classpath = classpath.join(os::classpath_separator());

    let game_assets = if installed.assets.map_to_resources {
        opts.game_dir.join("resources")
    } else if installed.assets.is_virtual {
        paths.asset_virtual(&installed.asset_index_id)
    } else {
        paths.assets()
    };

    let mut env = Environment::current();
    if opts.resolution.is_some() {
        env.enable("has_custom_resolution");
    }
    let modern_quick_play = v
        .arguments
        .as_ref()
        .is_some_and(|a| a.game.iter().any(|g| g.mentions("${quickPlayMultiplayer}")));
    let server = opts.join_server.as_deref().map(split_server);
    if server.is_some() && modern_quick_play {
        env.enable("is_quick_play_multiplayer");
    }

    let (width, height) = opts.resolution.unwrap_or((854, 480));
    let mut vars: HashMap<&str, String> = HashMap::new();
    vars.insert("auth_player_name", session.username.clone());
    vars.insert("auth_uuid", session.uuid.clone());
    vars.insert("auth_access_token", session.access_token.clone());
    vars.insert(
        "auth_session",
        format!("token:{}:{}", session.access_token, session.uuid),
    );
    vars.insert("auth_xuid", session.xuid.clone());
    vars.insert("clientid", "vortex".to_string());
    vars.insert("user_type", session.user_type.clone());
    vars.insert("user_properties", "{}".to_string());
    vars.insert("version_name", installed.id.clone());
    vars.insert(
        "version_type",
        v.kind.clone().unwrap_or_else(|| "release".to_string()),
    );
    vars.insert("game_directory", path_str(&opts.game_dir));
    vars.insert("assets_root", path_str(&paths.assets()));
    vars.insert("game_assets", path_str(&game_assets));
    vars.insert("assets_index_name", installed.asset_index_id.clone());
    vars.insert("natives_directory", path_str(&installed.natives_dir));
    vars.insert("library_directory", path_str(&paths.libraries()));
    vars.insert("classpath", classpath);
    vars.insert("classpath_separator", os::classpath_separator().to_string());
    vars.insert("launcher_name", LAUNCHER_NAME.to_string());
    vars.insert("launcher_version", LAUNCHER_VERSION.to_string());
    vars.insert("resolution_width", width.to_string());
    vars.insert("resolution_height", height.to_string());
    if let Some((host, port)) = &server {
        vars.insert("quickPlayMultiplayer", format!("{host}:{port}"));
    }

    let mut jvm: Vec<String> = vec![
        format!("-Xms{}M", opts.min_memory_mb.min(opts.max_memory_mb)),
        format!("-Xmx{}M", opts.max_memory_mb),
    ];
    let mut game: Vec<String> = Vec::new();
    let mut legacy_server_args = false;

    match (&v.minecraft_arguments, &v.arguments) {
        (Some(legacy), extra) => {
            jvm.extend(legacy_jvm_args());
            game.extend(legacy.split_whitespace().map(String::from));
            if let Some(extra) = extra {
                jvm.extend(extra.jvm.iter().flat_map(|a| a.values(&env)));
                game.extend(extra.game.iter().flat_map(|a| a.values(&env)));
            }
            // 1.6+ understand --width/--height/--server; betas and alphas don't.
            let modern_cli = legacy.contains("--version");
            if modern_cli {
                if opts.resolution.is_some() {
                    game.extend(["--width", "${resolution_width}", "--height", "${resolution_height}"].map(String::from));
                }
                if opts.fullscreen {
                    game.push("--fullscreen".into());
                }
                legacy_server_args = true;
            }
        }
        (None, Some(args)) => {
            jvm.extend(args.jvm.iter().flat_map(|a| a.values(&env)));
            game.extend(args.game.iter().flat_map(|a| a.values(&env)));
            if opts.fullscreen {
                game.push("--fullscreen".into());
            }
            legacy_server_args = !modern_quick_play;
        }
        (None, None) => return Err(anyhow!("version {} has no launch arguments", installed.id)),
    }

    if legacy_server_args {
        if let Some((host, port)) = &server {
            game.extend(["--server".to_string(), host.clone(), "--port".to_string(), port.clone()]);
        }
    }

    jvm.extend(opts.extra_jvm_args.iter().cloned());
    if let Some(logging) = &installed.logging {
        let mut lv = HashMap::new();
        lv.insert("path", path_str(&logging.path));
        jvm.push(substitute(&logging.argument, &lv));
    }

    let mut args: Vec<String> = jvm.iter().map(|a| substitute(a, &vars)).collect();
    args.push(main_class.to_string());
    args.extend(game.iter().map(|a| substitute(a, &vars)));

    Ok(LaunchCommand {
        java: opts.java.clone(),
        args,
        working_dir: opts.game_dir.clone(),
        secrets: vec![session.access_token.clone()],
        wrapper: Vec::new(),
        env: Vec::new(),
        priority: 0,
    })
}

/// A running game.
pub struct GameProcess {
    pub child: Child,
    /// Parsed stdout/stderr lines. Closed once the game's pipes close.
    pub logs: mpsc::UnboundedReceiver<LogLine>,
}

impl GameProcess {
    pub fn id(&self) -> Option<u32> {
        self.child.id()
    }

    pub async fn wait(&mut self) -> Result<ExitStatus> {
        Ok(self.child.wait().await?)
    }
}

/// Start the game. Its output is parsed (Mojang's XML log4j events become
/// plain lines) and delivered through [`GameProcess::logs`].
pub fn spawn(cmd: &LaunchCommand) -> Result<GameProcess> {
    std::fs::create_dir_all(&cmd.working_dir)?;
    let mut command = match cmd.wrapper.split_first() {
        Some((prog, rest)) => {
            let mut c = Command::new(prog);
            c.args(rest).arg(&cmd.java);
            c
        }
        None => Command::new(&cmd.java),
    };
    command.envs(cmd.env.iter().map(|(k, v)| (k.as_str(), v.as_str())));
    command
        .args(&cmd.args)
        .current_dir(&cmd.working_dir)
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    #[cfg(windows)]
    {
        const CREATE_NO_WINDOW: u32 = 0x0800_0000;
        command.creation_flags(CREATE_NO_WINDOW | cmd.priority);
    }
    let mut child = command
        .spawn()
        .with_context(|| format!("failed to start {}", cmd.java.display()))?;

    let (tx, rx) = mpsc::unbounded_channel();
    if let Some(out) = child.stdout.take() {
        tokio::spawn(pump(out, tx.clone(), false));
    }
    if let Some(err) = child.stderr.take() {
        tokio::spawn(pump(err, tx, true));
    }
    Ok(GameProcess { child, logs: rx })
}

async fn pump<R: AsyncRead + Unpin>(reader: R, tx: mpsc::UnboundedSender<LogLine>, stderr: bool) {
    let mut reader = BufReader::new(reader);
    let mut parser = LogParser::default();
    let mut buf = Vec::new();
    loop {
        buf.clear();
        match reader.read_until(b'\n', &mut buf).await {
            Ok(0) | Err(_) => break,
            Ok(_) => {
                let line = String::from_utf8_lossy(&buf);
                let line = line.trim_end_matches(['\r', '\n']);
                for l in parser.feed(line, stderr) {
                    // Keep draining even if nobody listens, so the game never
                    // blocks on a full pipe.
                    let _ = tx.send(l);
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::assets::AssetIndex;
    use crate::version::VersionJson;

    #[test]
    fn substitution() {
        let mut vars = HashMap::new();
        vars.insert("a", "1".to_string());
        vars.insert("natives_directory", "/n".to_string());
        assert_eq!(substitute("x${a}y${a}", &vars), "x1y1");
        assert_eq!(substitute("${missing}", &vars), "${missing}");
        assert_eq!(substitute("${natives_directory}/java", &vars), "/n/java");
        assert_eq!(substitute("broken ${a", &vars), "broken ${a");
        assert_eq!(substitute("plain", &vars), "plain");
    }

    #[test]
    fn server_split() {
        assert_eq!(split_server("mc.example.com"), ("mc.example.com".into(), "25565".into()));
        assert_eq!(split_server("1.2.3.4:25570"), ("1.2.3.4".into(), "25570".into()));
    }

    fn installed(json: &str) -> InstalledVersion {
        let version: VersionJson = serde_json::from_str(json).unwrap();
        InstalledVersion {
            id: version.id.clone(),
            client_jar: PathBuf::from("/v/client.jar"),
            classpath: vec![PathBuf::from("/l/a.jar"), PathBuf::from("/l/b.jar")],
            natives_dir: PathBuf::from("/natives"),
            asset_index_id: version.asset_index_id(),
            assets: AssetIndex::default(),
            logging: None,
            version,
        }
    }

    fn opts() -> LaunchOptions {
        LaunchOptions {
            java: PathBuf::from("java"),
            game_dir: PathBuf::from("/game"),
            min_memory_mb: 512,
            max_memory_mb: 2048,
            extra_jvm_args: vec!["-XX:+UseG1GC".into()],
            resolution: None,
            fullscreen: false,
            join_server: Some("play.example.net".into()),
        }
    }

    #[test]
    fn legacy_command() {
        let iv = installed(
            r#"{"id": "1.8.9", "type": "release", "mainClass": "net.minecraft.client.main.Main",
                "assets": "1.8",
                "minecraftArguments": "--username ${auth_player_name} --version ${version_name} --gameDir ${game_directory} --assetsDir ${assets_root} --assetIndex ${assets_index_name} --uuid ${auth_uuid} --accessToken ${auth_access_token} --userProperties ${user_properties} --userType ${user_type}"}"#,
        );
        let session = LaunchSession::offline("Steve");
        let cmd = build_command(&iv, &Paths::new("/root"), &session, &opts()).unwrap();
        let a = &cmd.args;
        assert_eq!(a[0], "-Xms512M");
        assert_eq!(a[1], "-Xmx2048M");
        let cp = a.iter().position(|x| x == "-cp").unwrap();
        assert!(a[cp + 1].ends_with("client.jar"));
        assert!(a.contains(&"-Djava.library.path=/natives".to_string()));
        assert!(a.contains(&"-XX:+UseG1GC".to_string()));
        let main = a.iter().position(|x| x == "net.minecraft.client.main.Main").unwrap();
        assert!(main > cp);
        assert_eq!(a[main + 1..main + 3], ["--username".to_string(), "Steve".to_string()]);
        assert!(a.contains(&"1.8".to_string()));
        assert!(a.windows(2).any(|w| w[0] == "--server" && w[1] == "play.example.net"));
        assert!(!a.iter().any(|x| x.contains("${")), "unsubstituted: {a:?}");
    }

    #[test]
    fn modern_command_uses_quick_play() {
        let iv = installed(
            r#"{"id": "1.21.4", "type": "release", "mainClass": "net.minecraft.client.main.Main",
                "assetIndex": {"id": "19", "sha1": "s", "size": 1, "url": "u"},
                "arguments": {
                  "game": ["--username", "${auth_player_name}", "--accessToken", "${auth_access_token}",
                    {"rules": [{"action": "allow", "features": {"has_custom_resolution": true}}], "value": ["--width", "${resolution_width}"]},
                    {"rules": [{"action": "allow", "features": {"is_quick_play_multiplayer": true}}], "value": ["--quickPlayMultiplayer", "${quickPlayMultiplayer}"]}],
                  "jvm": ["-Djava.library.path=${natives_directory}", "-cp", "${classpath}"]}}"#,
        );
        let session = LaunchSession {
            username: "Alex".into(),
            uuid: "abc".into(),
            access_token: "secret-token-123".into(),
            user_type: "msa".into(),
            xuid: "0".into(),
        };
        let cmd = build_command(&iv, &Paths::new("/root"), &session, &opts()).unwrap();
        let a = &cmd.args;
        assert!(a.windows(2).any(|w| w[0] == "--quickPlayMultiplayer" && w[1] == "play.example.net:25565"));
        assert!(!a.contains(&"--server".to_string()));
        assert!(!a.contains(&"--width".to_string()));
        assert!(cmd.display_redacted().contains("<redacted>"));
        assert!(!cmd.display_redacted().contains("secret-token-123"));
    }

    #[test]
    fn alpha_command() {
        let mut iv = installed(
            r#"{"id": "b1.7.3", "type": "old_beta", "mainClass": "net.minecraft.launchwrapper.Launch",
                "assets": "pre-1.6",
                "minecraftArguments": "${auth_player_name} ${auth_session} --gameDir ${game_directory} --assetsDir ${game_assets} --tweakClass net.minecraft.launchwrapper.AlphaVanillaTweaker"}"#,
        );
        iv.assets.map_to_resources = true;
        let mut o = opts();
        o.join_server = None;
        o.resolution = Some((1280, 720));
        let cmd = build_command(&iv, &Paths::new("/root"), &LaunchSession::offline("Notch"), &o).unwrap();
        let a = &cmd.args;
        let res = PathBuf::from("/game").join("resources").to_string_lossy().into_owned();
        assert!(a.contains(&res), "{a:?}");
        assert!(!a.contains(&"--width".to_string()), "betas don't take --width");
        assert!(a.iter().any(|x| x.starts_with("token:0:")));
    }
}
