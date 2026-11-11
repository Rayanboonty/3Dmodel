//! Skin library: saved skins (`<data>/skins/library`), the skin chosen for
//! each account, and uploading a skin to a Microsoft account.
//!
//! Offline accounts can't have a skin on Mojang's servers, so their chosen
//! skin is applied in-game by the Vortex mod (only you see it).

use std::path::{Path, PathBuf};

use anyhow::{bail, Context, Result};
use serde::{Deserialize, Serialize};
use sha1::{Digest, Sha1};

use crate::Launcher;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SavedSkin {
    pub id: String,
    pub name: String,
    pub slim: bool,
    pub added: u64,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Library {
    #[serde(default)]
    pub skins: Vec<SavedSkin>,
    /// Account UUID → chosen skin id.
    #[serde(default)]
    pub applied: std::collections::BTreeMap<String, String>,
    /// Account UUID → cape id (`none`, a Vortex cape or a saved cape).
    #[serde(default)]
    pub capes: std::collections::BTreeMap<String, String>,
    /// Capes added to the collection (from capes.dev, a file or a link).
    #[serde(default)]
    pub saved_capes: Vec<SavedCape>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SavedCape {
    pub id: String,
    pub name: String,
    /// Where it came from, e.g. "OptiFine" or "File".
    pub source: String,
    pub added: u64,
}

/// A cape someone has on a free cape service, found through capes.dev.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct FoundCape {
    pub service: String,
    pub image_url: String,
}

pub struct Skins {
    dir: PathBuf,
}

/// Capes drawn by the Vortex mod. Only you see them in-game.
pub const CAPES: [&str; 6] = ["none", "vortex", "diamond", "ember", "emerald", "amethyst"];

impl Skins {
    pub fn new(launcher: &Launcher) -> Self {
        Self { dir: launcher.paths().root.join("skins") }
    }

    pub fn at(dir: impl Into<PathBuf>) -> Self {
        Self { dir: dir.into() }
    }

    fn library_file(&self) -> PathBuf {
        self.dir.join("library.json")
    }

    pub fn png_path(&self, id: &str) -> Result<PathBuf> {
        if id.is_empty() || !id.chars().all(|c| c.is_ascii_hexdigit()) {
            bail!("invalid skin id");
        }
        Ok(self.dir.join("library").join(format!("{id}.png")))
    }

    /// Saved cape ids are "c" and 16 hex digits, so they never clash with Vortex capes.
    pub fn cape_path(&self, id: &str) -> Result<PathBuf> {
        let hex = id.strip_prefix('c').unwrap_or("");
        if hex.len() != 16 || !hex.chars().all(|c| c.is_ascii_hexdigit()) {
            bail!("invalid cape id");
        }
        Ok(self.dir.join("capes").join(format!("{id}.png")))
    }

    /// Add a cape PNG (already laid out like a 64×32 cape, or a multiple of it).
    pub fn add_cape(&self, png: &[u8], name: &str, source: &str) -> Result<SavedCape> {
        let (w, h) = png_size(png)?;
        if w < 64 || w % 64 != 0 || h * 2 != w || w > 1024 {
            bail!("a cape must be 64×32 (or a bigger multiple of it); this one is {w}×{h}");
        }
        let id = format!("c{}", &hex::encode(Sha1::digest(png))[..16]);
        let path = self.cape_path(&id)?;
        std::fs::create_dir_all(path.parent().expect("has parent"))?;
        std::fs::write(&path, png)?;
        let mut lib = self.load();
        let cape = match lib.saved_capes.iter().find(|c| c.id == id) {
            Some(c) => c.clone(),
            None => {
                let c = SavedCape { id, name: clean_name(name), source: clean_name(source), added: crate::auth::now_secs() };
                lib.saved_capes.push(c.clone());
                c
            }
        };
        self.save(&lib)?;
        Ok(cape)
    }

    pub fn remove_cape(&self, id: &str) -> Result<Library> {
        let path = self.cape_path(id)?;
        let mut lib = self.load();
        lib.saved_capes.retain(|c| c.id != id);
        for v in lib.capes.values_mut() {
            if v == id {
                *v = "none".into();
            }
        }
        let _ = std::fs::remove_file(path);
        self.save(&lib)?;
        Ok(lib)
    }

    /// The PNG of the saved cape an account wears, if it wears one.
    pub fn cape_file_for(&self, account_uuid: &str) -> Option<PathBuf> {
        let id = self.cape_for(account_uuid);
        self.cape_path(&id).ok().filter(|p| p.is_file())
    }

    pub fn load(&self) -> Library {
        let mut lib: Library = std::fs::read(self.library_file())
            .ok()
            .and_then(|b| serde_json::from_slice(&b).ok())
            .unwrap_or_default();
        lib.skins.retain(|s| self.png_path(&s.id).map(|p| p.is_file()).unwrap_or(false));
        lib.saved_capes.retain(|c| self.cape_path(&c.id).map(|p| p.is_file()).unwrap_or(false));
        lib
    }

    fn save(&self, lib: &Library) -> Result<()> {
        std::fs::create_dir_all(&self.dir)?;
        std::fs::write(self.library_file(), serde_json::to_vec_pretty(lib)?)?;
        Ok(())
    }

    /// Add a PNG to the library (or rename it if it's already there).
    pub fn add(&self, png: &[u8], name: &str, slim: bool) -> Result<SavedSkin> {
        let (w, h) = png_size(png)?;
        if !(w == 64 && (h == 64 || h == 32)) && !(w == h && w % 64 == 0 && w <= 512) {
            bail!("a skin must be a 64×64 or 64×32 PNG (this one is {w}×{h})");
        }
        let id = hex::encode(Sha1::digest(png))[..16].to_string();
        let path = self.png_path(&id)?;
        std::fs::create_dir_all(path.parent().expect("has parent"))?;
        std::fs::write(&path, png)?;
        let mut lib = self.load();
        let name = clean_name(name);
        let skin = match lib.skins.iter_mut().find(|s| s.id == id) {
            Some(s) => {
                s.name = name;
                s.slim = slim;
                s.clone()
            }
            None => {
                let s = SavedSkin { id, name, slim, added: crate::auth::now_secs() };
                lib.skins.push(s.clone());
                s
            }
        };
        self.save(&lib)?;
        Ok(skin)
    }

    pub fn update(&self, id: &str, name: Option<&str>, slim: Option<bool>) -> Result<Library> {
        let mut lib = self.load();
        let s = lib.skins.iter_mut().find(|s| s.id == id).context("no such skin")?;
        if let Some(n) = name {
            s.name = clean_name(n);
        }
        if let Some(sl) = slim {
            s.slim = sl;
        }
        self.save(&lib)?;
        Ok(lib)
    }

    pub fn remove(&self, id: &str) -> Result<Library> {
        let path = self.png_path(id)?;
        let mut lib = self.load();
        lib.skins.retain(|s| s.id != id);
        lib.applied.retain(|_, v| v != id);
        let _ = std::fs::remove_file(path);
        self.save(&lib)?;
        Ok(lib)
    }

    /// Remember which skin an account uses.
    pub fn set_applied(&self, account_uuid: &str, id: Option<&str>) -> Result<Library> {
        let mut lib = self.load();
        match id {
            Some(id) => {
                if !lib.skins.iter().any(|s| s.id == id) {
                    bail!("no such skin");
                }
                lib.applied.insert(account_uuid.to_string(), id.to_string());
            }
            None => {
                lib.applied.remove(account_uuid);
            }
        }
        self.save(&lib)?;
        Ok(lib)
    }

    pub fn set_cape(&self, account_uuid: &str, cape: &str) -> Result<Library> {
        let mut lib = self.load();
        if !CAPES.contains(&cape) && !lib.saved_capes.iter().any(|c| c.id == cape) {
            bail!("unknown cape '{cape}'");
        }
        lib.capes.insert(account_uuid.to_string(), cape.to_string());
        self.save(&lib)?;
        Ok(lib)
    }

    /// The skin chosen for an account, as (png path, slim).
    pub fn applied_for(&self, account_uuid: &str) -> Option<(PathBuf, bool)> {
        let lib = self.load();
        let id = lib.applied.get(account_uuid)?;
        let skin = lib.skins.iter().find(|s| &s.id == id)?;
        Some((self.png_path(&skin.id).ok()?, skin.slim))
    }

    pub fn cape_for(&self, account_uuid: &str) -> String {
        self.load().capes.get(account_uuid).cloned().unwrap_or_else(|| "none".into())
    }
}

/// Every cape a player has on the free cape services capes.dev knows
/// (OptiFine, LabyMod, MinecraftCapes, 5zig, TLauncher, Cloaks+ and more).
pub async fn find_capes(http: &reqwest::Client, username: &str) -> Result<Vec<FoundCape>> {
    let name: String = username.trim().chars().filter(|c| c.is_ascii_alphanumeric() || *c == '_').take(16).collect();
    if name.len() < 3 {
        bail!("type a Minecraft username");
    }
    let v: serde_json::Value = http
        .get(format!("https://api.capes.dev/load/{name}"))
        .send()
        .await?
        .error_for_status()
        .context("capes.dev didn't answer")?
        .json()
        .await?;
    let label = |k: &str| match k {
        "minecraft" => "Minecraft".to_string(),
        "optifine" => "OptiFine".to_string(),
        "labymod" => "LabyMod".to_string(),
        "minecraftcapes" => "MinecraftCapes".to_string(),
        "5zig" => "5zig".to_string(),
        "tlauncher" => "TLauncher".to_string(),
        "cloaksplus" => "Cloaks+".to_string(),
        "mantle" => "Mantle".to_string(),
        "lunar" => "Lunar".to_string(),
        k => {
            let mut c = k.chars();
            c.next().map(|f| f.to_uppercase().collect::<String>() + c.as_str()).unwrap_or_default()
        }
    };
    let mut out = Vec::new();
    if let Some(map) = v.as_object() {
        for (k, c) in map {
            if c.get("exists").and_then(|e| e.as_bool()) != Some(true) {
                continue;
            }
            let url = c.get("stillImageUrl").or_else(|| c.get("imageUrl")).and_then(|u| u.as_str()).unwrap_or("");
            if url.starts_with("https://") {
                out.push(FoundCape { service: label(k), image_url: url.to_string() });
            }
        }
    }
    Ok(out)
}

fn clean_name(name: &str) -> String {
    let n: String = name.trim().chars().filter(|c| !c.is_control()).take(40).collect();
    if n.is_empty() {
        "Skin".into()
    } else {
        n
    }
}

/// Width and height from a PNG header.
pub fn png_size(png: &[u8]) -> Result<(u32, u32)> {
    const SIG: [u8; 8] = [0x89, b'P', b'N', b'G', 0x0D, 0x0A, 0x1A, 0x0A];
    if png.len() < 24 || png[..8] != SIG || &png[12..16] != b"IHDR" {
        bail!("that file isn't a PNG image");
    }
    let w = u32::from_be_bytes(png[16..20].try_into().expect("4 bytes"));
    let h = u32::from_be_bytes(png[20..24].try_into().expect("4 bytes"));
    Ok((w, h))
}

#[derive(Deserialize)]
struct NameLookup {
    id: String,
    name: String,
}

#[derive(Deserialize)]
struct SessionProfile {
    properties: Vec<SessionProperty>,
}

#[derive(Deserialize)]
struct SessionProperty {
    name: String,
    value: String,
}

/// A player's current skin from Mojang, as (png, slim). `who` is a
/// username or a UUID.
pub async fn fetch_profile_skin(http: &reqwest::Client, uuid: &str) -> Result<(Vec<u8>, bool)> {
    use base64::Engine;
    let url = format!("https://sessionserver.mojang.com/session/minecraft/profile/{uuid}");
    let profile: SessionProfile = http.get(url).send().await?.error_for_status()?.json().await?;
    let textures = profile
        .properties
        .iter()
        .find(|p| p.name == "textures")
        .context("profile has no textures")?;
    let decoded = base64::engine::general_purpose::STANDARD.decode(&textures.value)?;
    let v: serde_json::Value = serde_json::from_slice(&decoded)?;
    let skin = &v["textures"]["SKIN"];
    let url = skin["url"].as_str().context("no skin set")?.replacen("http://", "https://", 1);
    let slim = skin["metadata"]["model"].as_str() == Some("slim");
    let bytes = http.get(url).send().await?.error_for_status()?.bytes().await?;
    Ok((bytes.to_vec(), slim))
}

/// Look up a Java Edition player by name and download their skin.
pub async fn skin_of_player(http: &reqwest::Client, username: &str) -> Result<(String, Vec<u8>, bool)> {
    let name = username.trim();
    if name.is_empty() || name.len() > 16 || !name.chars().all(|c| c.is_ascii_alphanumeric() || c == '_') {
        bail!("'{name}' isn't a valid Minecraft username");
    }
    let resp = http
        .get(format!("https://api.mojang.com/users/profiles/minecraft/{name}"))
        .send()
        .await
        .context("could not reach Mojang")?;
    if resp.status() == reqwest::StatusCode::NOT_FOUND || resp.status() == reqwest::StatusCode::NO_CONTENT {
        bail!("no Java Edition player is called '{name}'");
    }
    let found: NameLookup = resp.error_for_status()?.json().await?;
    let (png, slim) = fetch_profile_skin(http, &found.id).await?;
    Ok((found.name, png, slim))
}

/// Upload a skin to a Microsoft account (changes it everywhere).
pub async fn upload_to_account(http: &reqwest::Client, access_token: &str, png: Vec<u8>, slim: bool) -> Result<()> {
    let part = reqwest::multipart::Part::bytes(png)
        .file_name("skin.png")
        .mime_str("image/png")?;
    let form = reqwest::multipart::Form::new()
        .text("variant", if slim { "slim" } else { "classic" })
        .part("file", part);
    let resp = http
        .post("https://api.minecraftservices.com/minecraft/profile/skins")
        .bearer_auth(access_token)
        .multipart(form)
        .send()
        .await
        .context("could not reach Minecraft services")?;
    if !resp.status().is_success() {
        let status = resp.status();
        let body = resp.text().await.unwrap_or_default();
        bail!("Mojang refused the skin ({status}): {}", body.chars().take(200).collect::<String>());
    }
    Ok(())
}

pub fn read_png(path: &Path) -> Result<Vec<u8>> {
    let bytes = std::fs::read(path).with_context(|| format!("cannot read {}", path.display()))?;
    png_size(&bytes)?;
    Ok(bytes)
}

/// Minecraft's own default skins (Steve, Alex, Ari, Efe, Kai, Makena, Noor,
/// Sunny, Zuri), read from the newest game jar already downloaded.
pub fn default_skins(versions_dir: &Path) -> Vec<(String, bool, Vec<u8>)> {
    let mut jars: Vec<(std::time::SystemTime, PathBuf)> = std::fs::read_dir(versions_dir)
        .into_iter()
        .flatten()
        .flatten()
        .filter_map(|d| {
            let id = d.file_name().to_string_lossy().to_string();
            let jar = d.path().join(format!("{id}.jar"));
            let t = jar.metadata().ok()?.modified().ok()?;
            Some((t, jar))
        })
        .collect();
    jars.sort_by_key(|j| std::cmp::Reverse(j.0));
    for (_, jar) in jars {
        let Ok(f) = std::fs::File::open(&jar) else { continue };
        let Ok(mut zip) = zip::ZipArchive::new(f) else { continue };
        let mut out = Vec::new();
        for slim in [false, true] {
            for name in ["steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri"] {
                let path = format!("assets/minecraft/textures/entity/player/{}/{name}.png", if slim { "slim" } else { "wide" });
                if let Ok(mut e) = zip.by_name(&path) {
                    let mut buf = Vec::new();
                    if std::io::Read::read_to_end(&mut e, &mut buf).is_ok() {
                        let mut title = name.to_string();
                        title[..1].make_ascii_uppercase();
                        out.push((format!("{title} ({})", if slim { "slim" } else { "classic" }), slim, buf));
                    }
                }
            }
        }
        if !out.is_empty() {
            return out;
        }
    }
    Vec::new()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn png(w: u32, h: u32) -> Vec<u8> {
        let mut v = vec![0x89, b'P', b'N', b'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13];
        v.extend_from_slice(b"IHDR");
        v.extend_from_slice(&w.to_be_bytes());
        v.extend_from_slice(&h.to_be_bytes());
        v.extend_from_slice(&[8, 6, 0, 0, 0]);
        v
    }

    #[test]
    fn library_round_trip() {
        let dir = tempfile::tempdir().unwrap();
        let skins = Skins::at(dir.path());
        assert!(skins.add(&png(100, 50), "bad", false).is_err());
        let s = skins.add(&png(64, 64), "  My skin  ", true).unwrap();
        assert_eq!(s.name, "My skin");
        // Same bytes again: no duplicate.
        skins.add(&png(64, 64), "Renamed", false).unwrap();
        assert_eq!(skins.load().skins.len(), 1);
        skins.set_applied("acc", Some(&s.id)).unwrap();
        let (path, slim) = skins.applied_for("acc").unwrap();
        assert!(path.is_file());
        assert!(!slim);
        skins.set_cape("acc", "diamond").unwrap();
        assert_eq!(skins.cape_for("acc"), "diamond");
        assert!(skins.set_cape("acc", "nope").is_err());
        skins.remove(&s.id).unwrap();
        assert!(skins.applied_for("acc").is_none());
        assert!(skins.png_path("../x").is_err());
    }
}
