//! Launch extras behind the launcher's options: game defaults written to
//! options.txt, garbage collector choice, commands around a launch, world
//! backups, old-log clean-up and crash reasons.

use std::path::{Path, PathBuf};
use std::time::{Duration, SystemTime};

use crate::settings::Settings;

/// Whether this Minecraft version quotes string values in options.txt
/// (`renderClouds:"fast"`, 1.19 and later).
fn quotes_strings(minecraft: &str) -> bool {
    let mut it = minecraft.split(['.', '-', ' ']);
    let major: u32 = it.next().and_then(|s| s.parse().ok()).unwrap_or(0);
    let minor: u32 = it.next().and_then(|s| s.parse().ok()).unwrap_or(0);
    major >= 26 || (major == 1 && minor >= 19)
}

/// Windows: run this Java on the fast graphics card (the same switch as
/// Settings > System > Display > Graphics in Windows).
pub fn prefer_fast_gpu(java: &Path) {
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        // javaw.exe draws the window; set both.
        let mut paths = vec![java.to_path_buf()];
        if let Some(name) = java.file_name().and_then(|n| n.to_str()) {
            let other = if name.eq_ignore_ascii_case("java.exe") { "javaw.exe" } else { "java.exe" };
            paths.push(java.with_file_name(other));
        }
        for p in paths {
            let _ = std::process::Command::new("reg")
                .args(["add", r"HKCU\Software\Microsoft\DirectX\UserGpuPreferences", "/v", &p.display().to_string(), "/t", "REG_SZ", "/d", "GpuPreference=2;", "/f"])
                .creation_flags(0x0800_0000)
                .status();
        }
    }
    #[cfg(not(windows))]
    let _ = java;
}

/// Render resolution: the RenderScale mod's config in an instance. Written
/// when the setting is below 100%, and reset to full when it's back at 100%
/// and the mod already has a config (so it stops scaling).
pub fn write_render_scale(game_dir: &Path, percent: u32) {
    let path = game_dir.join("config").join("renderscale.json5");
    let percent = percent.clamp(25, 100);
    if percent >= 100 && !path.exists() {
        return;
    }
    if let Some(parent) = path.parent() {
        let _ = std::fs::create_dir_all(parent);
    }
    let text = format!("{{\n\t\"scale\": {:.2},\n\t\"forceLinear\": true\n}}\n", percent as f64 / 100.0);
    let _ = std::fs::write(path, text);
}

/// What Max FPS mode writes to options.txt (`graphicsMode` from 1.16 on,
/// `fancyGraphics` before; the game ignores the one it doesn't know).
const MAX_FPS_MODE: &[(&str, &str)] = &[
    ("graphicsMode", "0"),
    ("fancyGraphics", "false"),
    ("entityShadows", "false"),
    ("biomeBlendRadius", "0"),
    ("mipmapLevels", "0"),
    ("particles", "2"),
    ("renderDistance", "8"),
    ("simulationDistance", "8"),
    ("entityDistanceScaling", "0.5"),
    ("enableVsync", "false"),
    ("maxFps", "260"),
];

/// Write the launcher's game defaults into an instance's options.txt.
/// Only options the player set are touched; "Auto" leaves the game's own.
pub fn apply_game_defaults(game_dir: &Path, minecraft: &str, s: &Settings) {
    let set = |k: &str, v: String| crate::launcher::set_game_option(game_dir, k, &v);
    let q = |v: &str| if quotes_strings(minecraft) { format!("\"{v}\"") } else { v.to_string() };
    if s.max_fps_mode {
        // Draw less. With the Performance pack this took a test world from
        // 11.6 to 38 FPS. Options set below still win over it.
        for (k, v) in MAX_FPS_MODE {
            set(k, v.to_string());
        }
        set("renderClouds", q("false"));
        set("ao", if quotes_strings(minecraft) { "false" } else { "0" }.into());
    }
    if let Some(v) = s.max_fps {
        set("maxFps", v.clamp(10, 260).to_string());
    }
    if let Some(v) = s.render_distance {
        set("renderDistance", v.clamp(2, 32).to_string());
    }
    if let Some(v) = s.simulation_distance {
        set("simulationDistance", v.clamp(5, 32).to_string());
    }
    if let Some(v) = s.gui_scale {
        set("guiScale", v.min(6).to_string());
    }
    if let Some(v) = s.vsync {
        set("enableVsync", v.to_string());
    }
    if let Some(deg) = s.fov {
        // Stored as (degrees - 70) / 40.
        set("fov", format!("{:.4}", (deg.clamp(30, 110) as f64 - 70.0) / 40.0));
    }
    if let Some(v) = s.raw_mouse_input {
        set("rawMouseInput", v.to_string());
    }
    if s.narrator_off {
        set("narrator", "0".into());
    }
    if s.skip_multiplayer_warning {
        set("skipMultiplayerWarning", "true".into());
        set("joinedFirstServer", "true".into());
    }
    if let Some(v) = s.auto_jump {
        set("autoJump", v.to_string());
    }
    if let Some(v) = s.entity_shadows {
        set("entityShadows", v.to_string());
    }
    if let Some(p) = s.particles.as_deref() {
        let n = match p { "decreased" => 1, "minimal" => 2, _ => 0 };
        set("particles", n.to_string());
    }
    if let Some(c) = s.clouds.as_deref() {
        let v = match c { "off" => "false", "fast" => "fast", _ => "true" };
        set("renderClouds", q(v));
    }
    if let Some(v) = s.pause_on_lost_focus {
        set("pauseOnLostFocus", v.to_string());
    }
    // More options: written as the game stores them (only sane keys).
    for (k, v) in &s.game_options {
        let key_ok = !k.is_empty() && k.len() < 64 && k.chars().all(|c| c.is_ascii_alphanumeric() || c == '_' || c == '.');
        if key_ok && !v.contains(['\n', '\r']) && v.len() < 256 {
            set(k, v.clone());
        }
    }
    if s.hide_tutorial {
        set("tutorialStep", "none".into());
    }
}

/// JVM flags for the chosen garbage collector (`auto` = the launcher's tuned
/// G1 flags, which apply anyway).
pub fn gc_args(gc: &str, java_major: u32) -> Vec<String> {
    let v: &[&str] = match gc {
        "zgc" if java_major >= 21 => &["-XX:+UseZGC", "-XX:+ZGenerational"],
        "zgc" if java_major >= 15 => &["-XX:+UseZGC"],
        "shenandoah" if java_major >= 17 => &["-XX:+UseShenandoahGC"],
        "parallel" => &["-XX:+UseParallelGC"],
        "g1" => &["-XX:+UseG1GC"],
        _ => &[],
    };
    v.iter().map(|s| s.to_string()).collect()
}

/// Split a command line into words (double quotes group words).
pub fn split_command(s: &str) -> Vec<String> {
    let mut out = Vec::new();
    let mut cur = String::new();
    let mut quoted = false;
    let mut any = false;
    for c in s.chars() {
        match c {
            '"' => {
                quoted = !quoted;
                any = true;
            }
            c if c.is_whitespace() && !quoted => {
                if any {
                    out.push(std::mem::take(&mut cur));
                    any = false;
                }
            }
            c => {
                cur.push(c);
                any = true;
            }
        }
    }
    if any {
        out.push(cur);
    }
    out
}

/// `KEY=value` pairs, one per line or separated by `;`.
pub fn parse_env(s: &str) -> Vec<(String, String)> {
    s.split(['\n', ';'])
        .filter_map(|p| {
            let (k, v) = p.trim().split_once('=')?;
            let k = k.trim();
            (!k.is_empty() && k.chars().all(|c| c.is_ascii_alphanumeric() || c == '_')).then(|| (k.to_string(), v.trim().to_string()))
        })
        .collect()
}

/// Run a command through the system shell in the game folder and wait for
/// it (at most `timeout`). Returns its exit code, or an error message.
pub async fn run_hook(command: &str, game_dir: &Path, timeout: Duration) -> Result<i32, String> {
    let command = command.trim();
    if command.is_empty() {
        return Ok(0);
    }
    #[cfg(windows)]
    let mut cmd = {
        let mut c = tokio::process::Command::new("cmd");
        c.arg("/C").arg(command);
        // No console window flashing up (tokio's Command has this built in).
        c.creation_flags(0x0800_0000);
        c
    };
    #[cfg(not(windows))]
    let mut cmd = {
        let mut c = tokio::process::Command::new("sh");
        c.arg("-c").arg(command);
        c
    };
    cmd.current_dir(game_dir).stdin(std::process::Stdio::null());
    match tokio::time::timeout(timeout, cmd.status()).await {
        Ok(Ok(st)) => Ok(st.code().unwrap_or(-1)),
        Ok(Err(e)) => Err(format!("could not run it: {e}")),
        Err(_) => Err(format!("still running after {}s, left in the background", timeout.as_secs())),
    }
}

/// Zip the instance's worlds into `backups/<date>.zip`, keeping the newest
/// `keep`. Returns the new file (None when there are no worlds).
pub fn backup_worlds(game_dir: &Path, keep: u32) -> std::io::Result<Option<PathBuf>> {
    use std::io::Write;
    let saves = game_dir.join("saves");
    if !saves.is_dir() || std::fs::read_dir(&saves)?.next().is_none() {
        return Ok(None);
    }
    let dir = game_dir.join("backups");
    std::fs::create_dir_all(&dir)?;
    let stamp = chrono_like_stamp();
    let path = dir.join(format!("worlds-{stamp}.zip"));
    let file = std::fs::File::create(&path)?;
    let mut zip = zip::ZipWriter::new(file);
    let opts = zip::write::SimpleFileOptions::default().compression_method(zip::CompressionMethod::Deflated);
    let mut stack = vec![saves.clone()];
    while let Some(d) = stack.pop() {
        for e in std::fs::read_dir(&d)?.flatten() {
            let p = e.path();
            let rel = p.strip_prefix(&saves).unwrap_or(&p).to_string_lossy().replace('\\', "/");
            if p.is_dir() {
                stack.push(p);
            } else if !rel.ends_with("session.lock") {
                if let Ok(bytes) = std::fs::read(&p) {
                    zip.start_file(rel, opts).map_err(std::io::Error::other)?;
                    zip.write_all(&bytes)?;
                }
            }
        }
    }
    zip.finish().map_err(std::io::Error::other)?;
    // Keep the newest `keep` backups.
    let mut all: Vec<PathBuf> = std::fs::read_dir(&dir)?
        .flatten()
        .map(|e| e.path())
        .filter(|p| p.file_name().and_then(|n| n.to_str()).is_some_and(|n| n.starts_with("worlds-") && n.ends_with(".zip")))
        .collect();
    all.sort();
    let keep = keep.max(1) as usize;
    if all.len() > keep {
        for old in &all[..all.len() - keep] {
            let _ = std::fs::remove_file(old);
        }
    }
    Ok(Some(path))
}

fn chrono_like_stamp() -> String {
    let secs = SystemTime::now().duration_since(SystemTime::UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0);
    // Days since 1970 to a calendar date (UTC), without a date library.
    let days = secs / 86_400;
    let (y, m, d) = civil_from_days(days as i64);
    let rem = secs % 86_400;
    format!("{y:04}-{m:02}-{d:02}_{:02}-{:02}-{:02}", rem / 3600, rem / 60 % 60, rem % 60)
}

fn civil_from_days(z: i64) -> (i64, u32, u32) {
    let z = z + 719_468;
    let era = z.div_euclid(146_097);
    let doe = z.rem_euclid(146_097);
    let yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365;
    let y = yoe + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = (doy - (153 * mp + 2) / 5 + 1) as u32;
    let m = if mp < 10 { mp + 3 } else { mp - 9 } as u32;
    (if m <= 2 { y + 1 } else { y }, m, d)
}

/// Delete game logs older than `days` (0 = keep everything). Returns how
/// many were removed.
pub fn prune_logs(game_dir: &Path, days: u32) -> usize {
    if days == 0 {
        return 0;
    }
    let limit = Duration::from_secs(days as u64 * 86_400);
    let mut n = 0;
    for sub in ["logs", "crash-reports"] {
        let Ok(rd) = std::fs::read_dir(game_dir.join(sub)) else { continue };
        for e in rd.flatten() {
            let p = e.path();
            let name = p.file_name().and_then(|n| n.to_str()).unwrap_or("");
            if name == "latest.log" || !p.is_file() {
                continue;
            }
            let old = e.metadata().ok().and_then(|m| m.modified().ok()).and_then(|t| t.elapsed().ok()).is_some_and(|age| age > limit);
            if old && std::fs::remove_file(&p).is_ok() {
                n += 1;
            }
        }
    }
    n
}

/// Why the game crashed, from the newest crash report written after
/// `since` (or the end of latest.log): a one-line reason and the file.
pub fn crash_reason(game_dir: &Path, since: SystemTime) -> Option<(String, PathBuf)> {
    let newest = std::fs::read_dir(game_dir.join("crash-reports"))
        .ok()?
        .flatten()
        .filter_map(|e| {
            let t = e.metadata().ok()?.modified().ok()?;
            (t >= since).then(|| (t, e.path()))
        })
        .max_by_key(|(t, _)| *t);
    if let Some((_, path)) = newest {
        let text = std::fs::read_to_string(&path).ok()?;
        let desc = text.lines().find_map(|l| l.strip_prefix("Description: ")).unwrap_or("").trim();
        let cause = text.lines().skip_while(|l| !l.starts_with("Description:")).nth(2).unwrap_or("").trim();
        let mut reason = explain(cause).unwrap_or_else(|| if cause.is_empty() { desc.to_string() } else { format!("{desc}: {cause}") });
        reason.truncate(300);
        return Some((reason, path));
    }
    // No crash report: the last error in the log.
    let log = game_dir.join("logs").join("latest.log");
    let text = std::fs::read_to_string(&log).ok()?;
    let line = text.lines().rev().find(|l| l.contains("Exception") || l.contains("Error:") || l.contains("/ERROR]"))?;
    let mut reason = explain(line).unwrap_or_else(|| line.trim().to_string());
    reason.truncate(300);
    Some((reason, log))
}

/// Plain-language reasons for the most common crashes.
fn explain(line: &str) -> Option<String> {
    let l = line.to_lowercase();
    let r = if l.contains("outofmemoryerror") {
        "The game ran out of memory. Give it more in the instance's settings (Memory)."
    } else if l.contains("mixin") && (l.contains("apply") || l.contains("injection")) {
        "A mod doesn't fit this Minecraft version (a mixin failed). Update or remove the newest mod you added."
    } else if l.contains("incompatible mod") || l.contains("requires") && l.contains("mod") {
        "A mod is missing something it needs, or two mods don't go together. The log says which."
    } else if l.contains("unsupportedclassversionerror") {
        "A mod needs a newer Java than this version runs on."
    } else if l.contains("glfw error") || l.contains("pixel format") || l.contains("opengl") {
        "Your graphics driver couldn't start the game. Update the graphics driver."
    } else {
        return None;
    };
    Some(r.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn splits_commands_and_env() {
        assert_eq!(split_command(r#"prime-run  "my wrapper" -x"#), vec!["prime-run", "my wrapper", "-x"]);
        assert!(split_command("   ").is_empty());
        assert_eq!(parse_env("A=1; B_2 = two\nbad key=3"), vec![("A".into(), "1".into()), ("B_2".into(), "two".into())]);
    }

    #[test]
    fn gc_flags_follow_java() {
        assert_eq!(gc_args("zgc", 21), vec!["-XX:+UseZGC", "-XX:+ZGenerational"]);
        assert!(gc_args("zgc", 8).is_empty());
        assert!(gc_args("auto", 21).is_empty());
    }

    #[test]
    fn writes_game_defaults() {
        let dir = std::env::temp_dir().join(format!("vortex-defaults-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let s = Settings { max_fps: Some(144), fov: Some(90), clouds: Some("fast".into()), hide_tutorial: true, ..Settings::default() };
        apply_game_defaults(&dir, "1.21.4", &s);
        let t = std::fs::read_to_string(dir.join("options.txt")).unwrap();
        assert!(t.contains("maxFps:144") && t.contains("fov:0.5000") && t.contains("renderClouds:\"fast\"") && t.contains("tutorialStep:none"));
        apply_game_defaults(&dir, "1.8.9", &Settings { clouds: Some("off".into()), ..Settings::default() });
        assert!(std::fs::read_to_string(dir.join("options.txt")).unwrap().contains("renderClouds:false"));
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn max_fps_mode_draws_less_but_choices_win() {
        let dir = std::env::temp_dir().join(format!("vortex-maxfps-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let s = Settings { max_fps_mode: true, render_distance: Some(12), ..Settings::default() };
        apply_game_defaults(&dir, "1.21.4", &s);
        let t = std::fs::read_to_string(dir.join("options.txt")).unwrap();
        assert!(t.contains("graphicsMode:0") && t.contains("renderClouds:\"false\"") && t.contains("ao:false") && t.contains("particles:2"));
        // A render distance the player picked beats the mode's 8.
        assert!(t.contains("renderDistance:12") && !t.contains("renderDistance:8"));
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn dates() {
        assert_eq!(civil_from_days(0), (1970, 1, 1));
        assert_eq!(civil_from_days(20_399), (2025, 11, 7));
    }

    #[test]
    fn explains_common_crashes() {
        assert!(explain("java.lang.OutOfMemoryError: Java heap space").unwrap().contains("memory"));
        assert!(explain("nothing to see").is_none());
    }
}
