//! Music: your own music files, free internet radio and synced lyrics.
//!
//! The launcher plays the audio (its web view decodes MP3, AAC, FLAC, Ogg,
//! Opus and WAV). Lyrics come from a `.lrc` file next to the song or from
//! LRCLIB (free, no account). Radio stations come from radio-browser.info.
//! The game reads `music/now-playing.json` for its Now Playing overlay and
//! writes `music/commands.txt` (play/pause, next, previous) for the launcher.

use std::borrow::Cow;
use std::io::{Read, Seek, SeekFrom};
use std::path::{Path, PathBuf};
use std::sync::Mutex;

use serde::{Deserialize, Serialize};

pub const EXTS: &[&str] = &["mp3", "ogg", "oga", "opus", "flac", "m4a", "aac", "wav", "webm"];
const MAX_TRACKS: usize = 5000;

/// The folder the audio server may read from (set when the library is scanned).
static ROOT: Mutex<Option<PathBuf>> = Mutex::new(None);

#[derive(Serialize, Clone, Debug, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct Track {
    pub path: String,
    pub title: String,
    pub artist: String,
    pub album: String,
}

pub fn default_dir() -> PathBuf {
    dirs::audio_dir().or_else(|| dirs::home_dir().map(|h| h.join("Music"))).unwrap_or_else(|| PathBuf::from("."))
}

fn is_audio(p: &Path) -> bool {
    p.extension().and_then(|e| e.to_str()).is_some_and(|e| EXTS.contains(&e.to_ascii_lowercase().as_str()))
}

/// Every song in a folder (three levels deep), sorted by artist and title.
pub fn scan(dir: &Path) -> Vec<Track> {
    *ROOT.lock().unwrap_or_else(|e| e.into_inner()) = Some(dir.to_path_buf());
    let mut out = Vec::new();
    let mut stack = vec![(dir.to_path_buf(), 0)];
    while let Some((d, depth)) = stack.pop() {
        let Ok(rd) = std::fs::read_dir(&d) else { continue };
        for e in rd.flatten() {
            let p = e.path();
            if p.is_dir() {
                if depth < 3 {
                    stack.push((p, depth + 1));
                }
            } else if is_audio(&p) {
                out.push(track(&p));
                if out.len() >= MAX_TRACKS {
                    break;
                }
            }
        }
    }
    out.sort_by_key(|t| (t.artist.to_lowercase(), t.title.to_lowercase()));
    out
}

fn track(p: &Path) -> Track {
    let stem = p.file_stem().and_then(|s| s.to_str()).unwrap_or("").to_string();
    // "Artist - Title" in the file name, unless the tags say better.
    let (mut artist, mut title) = match stem.split_once(" - ") {
        Some((a, t)) => (a.trim().to_string(), t.trim().to_string()),
        None => (String::new(), stem.clone()),
    };
    let mut album = String::new();
    if let Some(t) = id3(p) {
        if !t.0.is_empty() {
            title = t.0;
        }
        if !t.1.is_empty() {
            artist = t.1;
        }
        album = t.2;
    }
    Track { path: p.to_string_lossy().into_owned(), title, artist, album }
}

/// Title, artist and album from an MP3's ID3v2 tag.
fn id3(p: &Path) -> Option<(String, String, String)> {
    let mut f = std::fs::File::open(p).ok()?;
    let mut head = [0u8; 10];
    f.read_exact(&mut head).ok()?;
    if &head[..3] != b"ID3" {
        return None;
    }
    let ver = head[3];
    let size = synchsafe(&head[6..10]) as usize;
    let mut buf = vec![0u8; size.min(1 << 20)];
    f.read_exact(&mut buf).ok()?;
    let (mut title, mut artist, mut album) = (String::new(), String::new(), String::new());
    let mut i = 0;
    let (id_len, hdr) = if ver == 2 { (3, 6) } else { (4, 10) };
    while i + hdr <= buf.len() {
        let id = &buf[i..i + id_len];
        if id[0] == 0 {
            break;
        }
        let len = match ver {
            2 => u32::from_be_bytes([0, buf[i + 3], buf[i + 4], buf[i + 5]]) as usize,
            4 => synchsafe(&buf[i + 4..i + 8]) as usize,
            _ => u32::from_be_bytes([buf[i + 4], buf[i + 5], buf[i + 6], buf[i + 7]]) as usize,
        };
        let start = i + hdr;
        let end = (start + len).min(buf.len());
        if start >= end {
            break;
        }
        let text = || text_frame(&buf[start..end]);
        match id {
            b"TIT2" | b"TT2" => title = text(),
            b"TPE1" | b"TP1" => artist = text(),
            b"TALB" | b"TAL" => album = text(),
            _ => {}
        }
        i = end;
    }
    Some((title, artist, album))
}

fn synchsafe(b: &[u8]) -> u32 {
    b.iter().fold(0u32, |acc, &x| (acc << 7) | (x as u32 & 0x7f))
}

fn text_frame(b: &[u8]) -> String {
    let (enc, body) = match b.split_first() {
        Some((e, rest)) => (*e, rest),
        None => return String::new(),
    };
    let s = match enc {
        1 | 2 => {
            let mut units: Vec<u16> = body.chunks_exact(2).map(|c| u16::from_be_bytes([c[0], c[1]])).collect();
            // A little-endian byte order mark: swap.
            if units.first() == Some(&0xFFFE) {
                units = units.iter().map(|u| u.swap_bytes()).collect();
            }
            if units.first() == Some(&0xFEFF) {
                units.remove(0);
            }
            String::from_utf16_lossy(&units)
        }
        3 => String::from_utf8_lossy(body).into_owned(),
        _ => body.iter().map(|&c| c as char).collect(),
    };
    s.trim_matches(char::from(0)).split('\0').next().unwrap_or("").trim().to_string()
}

// ------------------------------------------------------------- lyrics --

#[derive(Serialize, Default, Debug)]
#[serde(rename_all = "camelCase")]
pub struct Lyrics {
    /// (milliseconds, line), in order.
    pub synced: Vec<(u64, String)>,
    pub plain: Option<String>,
    pub source: String,
}

/// Lines of an LRC file: `[mm:ss.xx] text` (a line can have several times).
pub fn parse_lrc(text: &str) -> Vec<(u64, String)> {
    let mut out = Vec::new();
    for line in text.lines() {
        let mut rest = line.trim();
        let mut times = Vec::new();
        while let Some(stripped) = rest.strip_prefix('[') {
            let Some(close) = stripped.find(']') else { break };
            let tag = &stripped[..close];
            rest = stripped[close + 1..].trim_start();
            if let Some((m, s)) = tag.split_once(':') {
                if let (Ok(m), Ok(s)) = (m.trim().parse::<u64>(), s.trim().parse::<f64>()) {
                    times.push(m * 60_000 + (s * 1000.0).round() as u64);
                }
            }
        }
        for t in times {
            out.push((t, rest.to_string()));
        }
    }
    out.sort_by_key(|l| l.0);
    out
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct LrcLib {
    synced_lyrics: Option<String>,
    plain_lyrics: Option<String>,
}

/// Synced lyrics for a song: a `.lrc` next to it first, then LRCLIB.
pub async fn lyrics(http: &vortex_core::reqwest::Client, path: Option<&str>, artist: &str, title: &str, duration: Option<f64>) -> anyhow::Result<Lyrics> {
    if let Some(p) = path {
        let lrc = Path::new(p).with_extension("lrc");
        if let Ok(text) = std::fs::read_to_string(&lrc) {
            let synced = parse_lrc(&text);
            if !synced.is_empty() {
                return Ok(Lyrics { synced, plain: None, source: "file".into() });
            }
        }
    }
    const API: &str = "https://lrclib.net/api";
    let mut q = vec![("artist_name", artist.to_string()), ("track_name", title.to_string())];
    if let Some(d) = duration.filter(|d| *d > 0.0) {
        q.push(("duration", format!("{}", d.round() as u64)));
    }
    let take = |l: LrcLib| Lyrics {
        synced: l.synced_lyrics.as_deref().map(parse_lrc).unwrap_or_default(),
        plain: l.plain_lyrics,
        source: "LRCLIB".into(),
    };
    if !artist.is_empty() {
        let r = http.get(format!("{API}/get")).query(&q).send().await?;
        if r.status().is_success() {
            let l: LrcLib = r.json().await?;
            if l.synced_lyrics.is_some() || l.plain_lyrics.is_some() {
                return Ok(take(l));
            }
        }
    }
    // No exact match: search, and prefer a result with timings.
    let r = http.get(format!("{API}/search")).query(&[("q", format!("{artist} {title}").trim().to_string())]).send().await?;
    let list: Vec<LrcLib> = if r.status().is_success() { r.json().await? } else { Vec::new() };
    let mut list = list.into_iter();
    let first_synced = list.by_ref().find(|l| l.synced_lyrics.is_some());
    Ok(first_synced.map(take).unwrap_or_default())
}

// -------------------------------------------------------------- radio --

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct Station {
    pub name: String,
    #[serde(alias = "url_resolved")]
    pub url: String,
    #[serde(default)]
    pub favicon: String,
    #[serde(default)]
    pub tags: String,
    #[serde(default)]
    pub country: String,
    #[serde(default)]
    pub codec: String,
    #[serde(default)]
    pub bitrate: u32,
}

#[derive(Deserialize)]
struct RawStation {
    name: String,
    url_resolved: String,
    #[serde(default)]
    favicon: String,
    #[serde(default)]
    tags: String,
    #[serde(default)]
    country: String,
    #[serde(default)]
    codec: String,
    #[serde(default)]
    bitrate: u32,
}

/// The most popular working stations matching a name or genre.
pub async fn radio(http: &vortex_core::reqwest::Client, query: &str, tag: &str) -> anyhow::Result<Vec<Station>> {
    let r = http
        .get("https://de1.api.radio-browser.info/json/stations/search")
        .query(&[("name", query), ("tag", tag), ("limit", "40"), ("hidebroken", "true"), ("order", "clickcount"), ("reverse", "true")])
        .send()
        .await?;
    let raw: Vec<RawStation> = r.error_for_status()?.json().await?;
    Ok(raw
        .into_iter()
        .filter(|s| s.url_resolved.starts_with("http") && !s.name.trim().is_empty())
        .map(|s| Station { name: s.name.trim().to_string(), url: s.url_resolved, favicon: s.favicon, tags: s.tags, country: s.country, codec: s.codec, bitrate: s.bitrate })
        .collect())
}

// ------------------------------------------------- the game's overlay --

pub fn dir(data_root: &Path) -> PathBuf {
    data_root.join("music")
}

/// What the game shows: written whenever the song, play state or position changes.
pub fn write_now_playing(data_root: &Path, state: &serde_json::Value) -> std::io::Result<()> {
    let d = dir(data_root);
    std::fs::create_dir_all(&d)?;
    let tmp = d.join("now-playing.json.part");
    std::fs::write(&tmp, serde_json::to_vec(state).unwrap_or_default())?;
    std::fs::rename(tmp, d.join("now-playing.json"))
}

/// Buttons pressed in the game since the last call (play/pause, next, previous).
pub fn take_commands(data_root: &Path) -> Vec<String> {
    let p = dir(data_root).join("commands.txt");
    let Ok(text) = std::fs::read_to_string(&p) else { return Vec::new() };
    let _ = std::fs::remove_file(&p);
    text.lines().map(|l| l.trim().to_string()).filter(|l| ["toggle", "next", "prev"].contains(&l.as_str())).collect()
}

// --------------------------------------------------------- audio server --

fn percent_decode(s: &str) -> String {
    let b = s.as_bytes();
    let mut out = Vec::with_capacity(b.len());
    let mut i = 0;
    while i < b.len() {
        if b[i] == b'%' && i + 2 < b.len() {
            if let Ok(v) = u8::from_str_radix(&s[i + 1..i + 3], 16) {
                out.push(v);
                i += 3;
                continue;
            }
        }
        out.push(b[i]);
        i += 1;
    }
    String::from_utf8_lossy(&out).into_owned()
}

fn mime(p: &Path) -> &'static str {
    match p.extension().and_then(|e| e.to_str()).map(|e| e.to_ascii_lowercase()).as_deref() {
        Some("mp3") => "audio/mpeg",
        Some("ogg" | "oga") => "audio/ogg",
        Some("opus") => "audio/ogg; codecs=opus",
        Some("flac") => "audio/flac",
        Some("m4a" | "aac") => "audio/mp4",
        Some("wav") => "audio/wav",
        Some("webm") => "audio/webm",
        _ => "application/octet-stream",
    }
}

/// `vmusic://localhost/<path>`: an audio file from the music folder, with
/// byte ranges so the player can seek.
pub fn serve(request: &tauri::http::Request<Vec<u8>>) -> tauri::http::Response<Cow<'static, [u8]>> {
    let fail = |code: u16| tauri::http::Response::builder().status(code).body(Cow::Borrowed(&[][..])).unwrap();
    let raw = percent_decode(request.uri().path().trim_start_matches('/'));
    let path = PathBuf::from(&raw);
    let allowed = ROOT.lock().unwrap_or_else(|e| e.into_inner()).clone();
    let inside = allowed.is_some_and(|root| path.starts_with(&root));
    if !inside || !is_audio(&path) {
        return fail(403);
    }
    let Ok(mut f) = std::fs::File::open(&path) else { return fail(404) };
    let len = f.metadata().map(|m| m.len()).unwrap_or(0);
    let range = request.headers().get("range").and_then(|v| v.to_str().ok()).and_then(|v| v.strip_prefix("bytes=")).and_then(|v| {
        let (a, b) = v.split_once('-')?;
        let start: u64 = a.parse().ok()?;
        let end: u64 = if b.is_empty() { len.saturating_sub(1) } else { b.parse().ok()? };
        Some((start, end.min(len.saturating_sub(1))))
    });
    // At most 4 MB a request: the player asks for the rest as it goes.
    const CHUNK: u64 = 4 << 20;
    let (start, end, partial) = match range {
        Some((s, e)) if s < len => (s, e.min(s + CHUNK - 1), true),
        Some(_) => return fail(416),
        None => (0, len.saturating_sub(1).min(CHUNK - 1), len > CHUNK),
    };
    let mut buf = vec![0u8; (end + 1 - start) as usize];
    if f.seek(SeekFrom::Start(start)).is_err() || f.read_exact(&mut buf).is_err() {
        return fail(500);
    }
    let mut b = tauri::http::Response::builder()
        .header("Content-Type", mime(&path))
        .header("Accept-Ranges", "bytes")
        .header("Content-Length", buf.len().to_string())
        .header("Access-Control-Allow-Origin", "*");
    if partial {
        b = b.status(206).header("Content-Range", format!("bytes {start}-{end}/{len}"));
    }
    b.body(Cow::Owned(buf)).unwrap_or_else(|_| fail(500))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_lrc_with_several_times() {
        let l = parse_lrc("[ar:Someone]\n[00:12.50]Hello\n[01:02.00][00:05.00]Again\nno time\n");
        assert_eq!(l, vec![(5000, "Again".into()), (12500, "Hello".into()), (62000, "Again".into())]);
    }

    #[test]
    fn names_from_files_and_tags() {
        let dir = std::env::temp_dir().join(format!("vortex-music-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        std::fs::write(dir.join("Daft Punk - One More Time.mp3"), b"not really").unwrap();
        // An ID3v2.3 tag with a title frame (UTF-8 text is encoding 3 in v2.4; use latin-1 here).
        let mut tag = b"ID3\x03\x00\x00".to_vec();
        let frame_body = b"\x00Tagged Title";
        let mut frame = b"TIT2".to_vec();
        frame.extend_from_slice(&(frame_body.len() as u32).to_be_bytes());
        frame.extend_from_slice(&[0, 0]);
        frame.extend_from_slice(frame_body);
        let n = frame.len() as u32;
        tag.extend_from_slice(&[((n >> 21) & 0x7f) as u8, ((n >> 14) & 0x7f) as u8, ((n >> 7) & 0x7f) as u8, (n & 0x7f) as u8]);
        tag.extend_from_slice(&frame);
        std::fs::write(dir.join("x.mp3"), &tag).unwrap();
        std::fs::write(dir.join("cover.jpg"), b"x").unwrap();
        let list = scan(&dir);
        let _ = std::fs::remove_dir_all(&dir);
        assert_eq!(list.len(), 2);
        assert!(list.iter().any(|t| t.artist == "Daft Punk" && t.title == "One More Time"));
        assert!(list.iter().any(|t| t.title == "Tagged Title"));
    }

    #[test]
    fn decodes_paths() {
        assert_eq!(percent_decode("C%3A/Music/A%20B.mp3"), "C:/Music/A B.mp3");
    }
}
