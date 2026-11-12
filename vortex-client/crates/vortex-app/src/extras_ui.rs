//! Launcher customisation helpers: the wallpaper gallery (shader showcase
//! screenshots from Modrinth) and your own plugins.

use std::borrow::Cow;
use std::path::{Path, PathBuf};

use anyhow::{bail, Result};
use serde::Serialize;
use vortex_core::reqwest::Client;

/// Shader packs whose showcase screenshots make the wallpaper gallery.
const SHADERS: &[&str] = &[
    "complementary-reimagined",
    "complementary-unbound",
    "photon-shader",
    "bsl-shaders",
    "solas-shader",
    "bliss-shader",
    "rethinking-voxels",
    "sildurs-vibrant-shaders",
    "nostalgia-shader",
    "miniature-shader",
];

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Wallpaper {
    /// Small picture for the picker.
    pub thumb: String,
    /// The full-size picture.
    pub url: String,
    pub title: String,
    /// The shader pack it shows off, and its page.
    pub source: String,
    pub page: String,
}

pub async fn gallery(http: &Client) -> Result<Vec<Wallpaper>> {
    let ids = serde_json::to_string(SHADERS)?;
    let projects: Vec<serde_json::Value> = http
        .get("https://api.modrinth.com/v2/projects")
        .query(&[("ids", ids.as_str())])
        .send()
        .await?
        .error_for_status()?
        .json()
        .await?;
    let mut out = Vec::new();
    for p in &projects {
        let name = p["title"].as_str().unwrap_or("Shader").to_string();
        let slug = p["slug"].as_str().unwrap_or("");
        for g in p["gallery"].as_array().into_iter().flatten() {
            let (Some(url), Some(raw)) = (g["url"].as_str(), g["raw_url"].as_str()) else { continue };
            if !raw.starts_with("https://cdn.modrinth.com/") {
                continue;
            }
            out.push(Wallpaper {
                thumb: url.to_string(),
                url: raw.to_string(),
                title: g["title"].as_str().filter(|t| !t.is_empty()).unwrap_or(&name).to_string(),
                source: name.clone(),
                page: format!("https://modrinth.com/shader/{slug}"),
            });
        }
    }
    Ok(out)
}

/// A gallery picture's bytes (only from Modrinth's CDN), as a data URL.
pub async fn fetch(http: &Client, url: &str) -> Result<String> {
    if !url.starts_with("https://cdn.modrinth.com/") {
        bail!("only gallery pictures can be fetched");
    }
    let res = http.get(url).send().await?.error_for_status()?;
    let kind = res.headers().get("content-type").and_then(|v| v.to_str().ok()).unwrap_or("image/jpeg").to_string();
    let bytes = res.bytes().await?;
    if bytes.len() > 40 << 20 {
        bail!("that picture is too big");
    }
    use base64::Engine;
    Ok(format!("data:{kind};base64,{}", base64::engine::general_purpose::STANDARD.encode(&bytes)))
}

// ------------------------------------------------------------------ plugins --

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Plugin {
    pub file: String,
    pub name: String,
    pub enabled: bool,
}

pub fn plugin_dir(data_root: &Path) -> PathBuf {
    data_root.join("plugins")
}

fn valid(file: &str) -> bool {
    !file.is_empty() && !file.starts_with('.') && file.chars().all(|c| c.is_ascii_alphanumeric() || "-_. ".contains(c)) && (file.ends_with(".js") || file.ends_with(".js.disabled"))
}

pub fn plugins(data_root: &Path) -> Vec<Plugin> {
    let mut out: Vec<Plugin> = std::fs::read_dir(plugin_dir(data_root))
        .into_iter()
        .flatten()
        .flatten()
        .filter_map(|e| {
            let file = e.file_name().to_string_lossy().to_string();
            if !valid(&file) {
                return None;
            }
            let enabled = file.ends_with(".js");
            let name = file.trim_end_matches(".disabled").trim_end_matches(".js").replace(['-', '_'], " ");
            Some(Plugin { file, name, enabled })
        })
        .collect();
    out.sort_by_key(|p| p.name.to_lowercase());
    out
}

pub fn toggle(data_root: &Path, file: &str, enabled: Option<bool>) -> Result<()> {
    if !valid(file) {
        bail!("invalid plugin");
    }
    let p = plugin_dir(data_root).join(file);
    if !p.is_file() {
        bail!("that plugin is gone");
    }
    match enabled {
        None => std::fs::remove_file(p)?,
        Some(true) if file.ends_with(".disabled") => std::fs::rename(&p, p.with_extension(""))?,
        Some(false) if file.ends_with(".js") => std::fs::rename(&p, p.with_extension("js.disabled"))?,
        _ => {}
    }
    Ok(())
}

pub const EXAMPLE: &str = r#"// A Vortex plugin. Files in this folder ending in .js load when Vortex
// starts (turn them on and off in Settings > Account & Data > Plugins).
//
// The default export gets the `vortex` API:
//   vortex.addWidget({ id, title, html(), every })  a Home widget; html() returns HTML,
//                                                  re-drawn every `every` seconds
//   vortex.toast(title, text)                      a notification
//   vortex.onLaunch(fn), vortex.onExit(fn)         fn(instanceName) when a game starts/stops
//   vortex.instances(), vortex.settings()          read-only copies
//   vortex.call(command, args)                     the launcher's own commands

export default function (vortex) {
  vortex.addWidget({
    id: "hello",
    title: "Session clock",
    every: 1,
    html() {
      const s = Math.floor((Date.now() - started) / 1000);
      return `<div style="font-size:22px;font-weight:700">${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}</div>
              <div class="muted small">since Vortex opened</div>`;
    },
  });
  vortex.onLaunch((name) => vortex.toast("Have fun", `${name} is starting.`));
}

const started = Date.now();
"#;

pub fn make_example(data_root: &Path) -> Result<()> {
    let dir = plugin_dir(data_root);
    std::fs::create_dir_all(&dir)?;
    let p = dir.join("example-session-clock.js");
    if !p.exists() {
        std::fs::write(p, EXAMPLE)?;
    }
    Ok(())
}

/// `vplugin://localhost/<file>`: an enabled plugin's code, for import().
pub fn serve(data_root: &Path, request: &tauri::http::Request<Vec<u8>>) -> tauri::http::Response<Cow<'static, [u8]>> {
    let fail = |code: u16| tauri::http::Response::builder().status(code).body(Cow::Borrowed(&[][..])).unwrap();
    let file = request.uri().path().trim_start_matches('/').replace("%20", " ");
    if !valid(&file) || !file.ends_with(".js") {
        return fail(404);
    }
    match std::fs::read(plugin_dir(data_root).join(&file)) {
        Ok(code) => tauri::http::Response::builder()
            .header("Content-Type", "text/javascript")
            .header("Access-Control-Allow-Origin", "*")
            .body(Cow::Owned(code))
            .unwrap_or_else(|_| fail(500)),
        Err(_) => fail(404),
    }
}
