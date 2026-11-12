//! Screen recording: films your screen (the game in fullscreen) to an MP4
//! in Videos\Vortex. Uses ffmpeg, downloaded once on first use, with the
//! graphics card's video encoder when there is one (NVIDIA, AMD or Intel)
//! so the game keeps its FPS.

use std::io::Read;
use std::path::{Path, PathBuf};
use std::process::Stdio;
use std::sync::{LazyLock, Mutex as StdMutex};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use anyhow::{anyhow, bail, Context, Result};
use serde::Serialize;
use tokio::io::AsyncWriteExt;
use tokio::process::{Child, Command};
use tokio::sync::Mutex;
use vortex_core::progress::{Progress, ProgressFn};
use vortex_core::reqwest::Client;

const FFMPEG_ZIP: &str = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip";

struct Rec {
    child: Child,
    file: PathBuf,
    started: u64,
}

static REC: LazyLock<Mutex<Option<Rec>>> = LazyLock::new(Default::default);
static ENCODER: LazyLock<StdMutex<Option<String>>> = LazyLock::new(Default::default);

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Status {
    pub recording: bool,
    pub started: u64,
    pub file: String,
    pub folder: String,
    pub ready: bool,
}

fn now() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0)
}

pub fn folder() -> PathBuf {
    dirs::video_dir().or_else(dirs::home_dir).unwrap_or_else(std::env::temp_dir).join("Vortex")
}

fn tool_path(data_root: &Path) -> PathBuf {
    data_root.join("tools").join(if cfg!(windows) { "ffmpeg.exe" } else { "ffmpeg" })
}

/// The ffmpeg to use: ours, or one already installed (outside Windows).
fn ffmpeg(data_root: &Path) -> Option<PathBuf> {
    let ours = tool_path(data_root);
    if ours.is_file() {
        return Some(ours);
    }
    if !cfg!(windows) {
        for dir in std::env::var_os("PATH").map(|p| std::env::split_paths(&p).collect::<Vec<_>>()).unwrap_or_default() {
            let p = dir.join("ffmpeg");
            if p.is_file() {
                return Some(p);
            }
        }
    }
    None
}

fn command(exe: &Path) -> Command {
    let mut c = Command::new(exe);
    c.stdin(Stdio::null()).stdout(Stdio::null()).stderr(Stdio::null()).kill_on_drop(true);
    #[cfg(windows)]
    c.creation_flags(0x0800_0000);
    c
}

/// Download ffmpeg (Windows), keeping only ffmpeg.exe.
pub async fn ensure(http: &Client, data_root: &Path, progress: &ProgressFn) -> Result<PathBuf> {
    if let Some(p) = ffmpeg(data_root) {
        return Ok(p);
    }
    if !cfg!(windows) {
        bail!("install ffmpeg to record (it's in every Linux package manager)");
    }
    let mut res = http.get(FFMPEG_ZIP).send().await?.error_for_status().context("couldn't download the recorder")?;
    let total = res.content_length().unwrap_or(0);
    let mut buf = Vec::with_capacity(total as usize);
    while let Some(chunk) = res.chunk().await? {
        buf.extend_from_slice(&chunk);
        progress(Progress { stage: "Downloading the recorder".into(), done: buf.len() as u64, total });
    }
    let target = tool_path(data_root);
    let t2 = target.clone();
    tokio::task::spawn_blocking(move || -> Result<()> {
        let mut zip = vortex_core::zip::ZipArchive::new(std::io::Cursor::new(buf))?;
        for i in 0..zip.len() {
            let mut e = zip.by_index(i)?;
            if e.name()?.ends_with("/bin/ffmpeg.exe") {
                let mut out = Vec::new();
                e.read_to_end(&mut out)?;
                std::fs::create_dir_all(t2.parent().expect("has parent"))?;
                let part = t2.with_extension("part");
                std::fs::write(&part, out)?;
                std::fs::rename(part, &t2)?;
                return Ok(());
            }
        }
        bail!("the recorder download had no ffmpeg.exe")
    })
    .await
    .map_err(|e| anyhow!("{e}"))??;
    Ok(target)
}

/// The fastest H.264 encoder this PC has: the graphics card's if it works.
async fn encoder(exe: &Path) -> String {
    if let Some(e) = ENCODER.lock().unwrap_or_else(|e| e.into_inner()).clone() {
        return e;
    }
    let mut pick = "libx264".to_string();
    for enc in ["h264_nvenc", "h264_amf", "h264_qsv"] {
        let ok = command(exe)
            .args(["-hide_banner", "-loglevel", "error", "-f", "lavfi", "-i", "color=c=black:s=320x240:d=0.2", "-c:v", enc, "-f", "null", "-"])
            .status()
            .await
            .map(|s| s.success())
            .unwrap_or(false);
        if ok {
            pick = enc.to_string();
            break;
        }
    }
    *ENCODER.lock().unwrap_or_else(|e| e.into_inner()) = Some(pick.clone());
    pick
}

fn stamp() -> String {
    let secs = now();
    let days = (secs / 86400) as i64;
    let rem = secs % 86400;
    let z = days + 719_468;
    let era = z.div_euclid(146_097);
    let doe = z - era * 146_097;
    let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146_096) / 365;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = doy - (153 * mp + 2) / 5 + 1;
    let m = if mp < 10 { mp + 3 } else { mp - 9 };
    let y = yoe + era * 400 + i64::from(m <= 2);
    format!("{y:04}-{m:02}-{d:02} {:02}.{:02}.{:02}", rem / 3600, (rem % 3600) / 60, rem % 60)
}

/// The in-game REC badge reads this file.
fn marker(data_root: &Path) -> PathBuf {
    crate::music::dir(data_root).join("recording")
}

pub async fn start(http: &Client, data_root: &Path, fps: u32, quality: &str, progress: &ProgressFn) -> Result<Status> {
    let mut rec = REC.lock().await;
    if rec.is_some() {
        drop(rec);
        return status(data_root).await;
    }
    let exe = ensure(http, data_root, progress).await?;
    let enc = encoder(&exe).await;
    let fps = fps.clamp(15, 120).to_string();
    let out_dir = folder();
    std::fs::create_dir_all(&out_dir)?;
    let file = out_dir.join(format!("Vortex {}.mp4", stamp()));
    let mut args: Vec<String> = vec!["-hide_banner".into(), "-loglevel".into(), "error".into(), "-y".into()];
    if cfg!(windows) {
        args.extend(["-f", "gdigrab", "-framerate", &fps, "-draw_mouse", "0", "-i", "desktop"].map(String::from));
    } else {
        let display = std::env::var("DISPLAY").unwrap_or_else(|_| ":0".into());
        args.extend(["-f", "x11grab", "-framerate", &fps, "-draw_mouse", "0", "-i", &display].map(String::from));
    }
    // Quality: bitrate for the graphics card encoders, CRF for x264.
    let (crf, rate) = match quality {
        "high" => ("18", "20M"),
        "low" => ("28", "6M"),
        _ => ("23", "12M"),
    };
    args.extend(["-c:v".into(), enc.clone()]);
    if enc == "libx264" {
        args.extend(["-preset", "ultrafast", "-crf", crf].map(String::from));
    } else {
        args.extend(["-b:v", rate].map(String::from));
    }
    args.extend(["-pix_fmt", "yuv420p", "-movflags", "+faststart"].map(String::from));
    args.push(file.to_string_lossy().into_owned());
    let mut cmd = command(&exe);
    cmd.args(&args).stdin(Stdio::piped());
    let mut child = cmd.spawn().context("couldn't start the recorder")?;
    // It fails fast when it can't capture.
    tokio::time::sleep(Duration::from_millis(600)).await;
    if let Ok(Some(st)) = child.try_wait() {
        bail!("the recorder stopped straight away ({st})");
    }
    let _ = std::fs::create_dir_all(crate::music::dir(data_root));
    let _ = std::fs::write(marker(data_root), now().to_string());
    *rec = Some(Rec { child, file, started: now() });
    drop(rec);
    status(data_root).await
}

pub async fn stop(data_root: &Path) -> Result<Option<String>> {
    let taken = REC.lock().await.take();
    let _ = std::fs::remove_file(marker(data_root));
    let Some(mut r) = taken else { return Ok(None) };
    // "q" lets ffmpeg finish the MP4 properly.
    if let Some(mut stdin) = r.child.stdin.take() {
        let _ = stdin.write_all(b"q").await;
        let _ = stdin.flush().await;
    }
    if tokio::time::timeout(Duration::from_secs(15), r.child.wait()).await.is_err() {
        let _ = r.child.kill().await;
    }
    Ok(Some(r.file.to_string_lossy().into_owned()))
}

/// True while a recording runs (for closing the launcher).
pub fn busy() -> bool {
    REC.try_lock().map(|r| r.is_some()).unwrap_or(true)
}

pub async fn status(data_root: &Path) -> Result<Status> {
    let rec = REC.lock().await;
    Ok(Status {
        recording: rec.is_some(),
        started: rec.as_ref().map(|r| r.started).unwrap_or(0),
        file: rec.as_ref().map(|r| r.file.to_string_lossy().into_owned()).unwrap_or_default(),
        folder: folder().to_string_lossy().into_owned(),
        ready: ffmpeg(data_root).is_some(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Records the X display for two seconds; run with
    /// `DISPLAY=:79 cargo test -p vortex-app records_screen -- --ignored`.
    #[tokio::test]
    #[ignore]
    async fn records_screen() {
        let root = std::env::temp_dir().join("vortex-rec-test");
        let launcher = vortex_core::Launcher::new(vortex_core::paths::Paths::new(root.join("data"))).unwrap();
        let http = launcher.http().clone();
        let st = start(&http, &root, 30, "normal", &vortex_core::progress::silent()).await.unwrap();
        assert!(st.recording);
        assert!(marker(&root).is_file());
        tokio::time::sleep(Duration::from_secs(2)).await;
        let file = stop(&root).await.unwrap().unwrap();
        let size = std::fs::metadata(&file).unwrap().len();
        println!("{file}: {size} bytes, encoder {:?}", ENCODER.lock().unwrap());
        assert!(size > 1000);
        assert!(!marker(&root).exists());
        let _ = std::fs::remove_file(file);
    }
}
