//! Mods installed in an instance (`<game dir>/mods`): list, enable/disable,
//! delete. Names and versions are read from the jar's own metadata
//! (`fabric.mod.json`, `quilt.mod.json`, `META-INF/mods.toml`,
//! `META-INF/neoforge.mods.toml`, legacy `mcmod.info`).

use std::io::Read;
use std::path::{Path, PathBuf};

use anyhow::{bail, Result};
use serde::Serialize;
use serde_json::Value;

use crate::instance::Instance;

const DISABLED: &str = ".disabled";

#[derive(Debug, Clone, Serialize, PartialEq, Eq)]
pub struct InstalledMod {
    /// File name inside the mods folder.
    pub file: String,
    pub enabled: bool,
    pub size: u64,
    pub name: String,
    pub version: Option<String>,
    pub description: Option<String>,
    /// `fabric`, `quilt`, `forge` or `unknown`.
    pub loader: String,
}

pub fn mods_dir(instance: &Instance) -> PathBuf {
    instance.game_dir().join("mods")
}

#[derive(Default)]
struct Meta {
    name: Option<String>,
    version: Option<String>,
    description: Option<String>,
    loader: Option<&'static str>,
}

fn read_zip_text(zip: &mut zip::ZipArchive<std::fs::File>, name: &str) -> Option<String> {
    let mut f = zip.by_name(name).ok()?;
    let mut s = String::new();
    f.read_to_string(&mut s).ok()?;
    Some(s)
}

fn toml_value(text: &str, key: &str) -> Option<String> {
    for line in text.lines() {
        let line = line.trim();
        let Some(rest) = line.strip_prefix(key) else { continue };
        let rest = rest.trim_start();
        let Some(rest) = rest.strip_prefix('=') else { continue };
        let rest = rest.trim();
        let value = rest
            .strip_prefix("'''")
            .and_then(|r| r.split("'''").next())
            .or_else(|| rest.strip_prefix('"').and_then(|r| r.split('"').next()))
            .or_else(|| rest.strip_prefix('\'').and_then(|r| r.split('\'').next()))?;
        if value.contains("${") || value.is_empty() {
            return None;
        }
        return Some(value.trim().to_string());
    }
    None
}

fn read_meta(path: &Path) -> Meta {
    let Ok(file) = std::fs::File::open(path) else { return Meta::default() };
    let Ok(mut zip) = zip::ZipArchive::new(file) else { return Meta::default() };
    let str_of = |v: &Value, k: &str| v.get(k).and_then(|x| x.as_str()).map(String::from);

    if let Some(text) = read_zip_text(&mut zip, "fabric.mod.json") {
        if let Ok(v) = serde_json::from_str::<Value>(&text) {
            return Meta {
                name: str_of(&v, "name").or_else(|| str_of(&v, "id")),
                version: str_of(&v, "version"),
                description: str_of(&v, "description"),
                loader: Some("fabric"),
            };
        }
    }
    if let Some(text) = read_zip_text(&mut zip, "quilt.mod.json") {
        if let Ok(v) = serde_json::from_str::<Value>(&text) {
            let l = &v["quilt_loader"];
            return Meta {
                name: l["metadata"]["name"].as_str().map(String::from).or_else(|| str_of(l, "id")),
                version: str_of(l, "version"),
                description: l["metadata"]["description"].as_str().map(String::from),
                loader: Some("quilt"),
            };
        }
    }
    for toml in ["META-INF/mods.toml", "META-INF/neoforge.mods.toml"] {
        if let Some(text) = read_zip_text(&mut zip, toml) {
            let version = toml_value(&text, "version").or_else(|| {
                read_zip_text(&mut zip, "META-INF/MANIFEST.MF").and_then(|m| {
                    m.lines()
                        .find_map(|l| l.strip_prefix("Implementation-Version:"))
                        .map(|v| v.trim().to_string())
                })
            });
            return Meta {
                name: toml_value(&text, "displayName"),
                version,
                description: toml_value(&text, "description"),
                loader: Some("forge"),
            };
        }
    }
    if let Some(text) = read_zip_text(&mut zip, "mcmod.info") {
        if let Ok(v) = serde_json::from_str::<Value>(&text) {
            let first = v
                .as_array()
                .and_then(|a| a.first().cloned())
                .or_else(|| v["modList"].as_array().and_then(|a| a.first().cloned()));
            if let Some(m) = first {
                return Meta {
                    name: str_of(&m, "name"),
                    version: str_of(&m, "version").filter(|v| !v.contains("${")),
                    description: str_of(&m, "description"),
                    loader: Some("forge"),
                };
            }
        }
    }
    Meta::default()
}

/// Everything in the mods folder that looks like a mod, sorted by name.
pub fn list(dir: &Path) -> Result<Vec<InstalledMod>> {
    let entries = match std::fs::read_dir(dir) {
        Ok(e) => e,
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(Vec::new()),
        Err(e) => return Err(e.into()),
    };
    let mut out = Vec::new();
    for entry in entries.flatten() {
        let Ok(file) = entry.file_name().into_string() else { continue };
        let (base, enabled) = match file.strip_suffix(DISABLED) {
            Some(b) => (b.to_string(), false),
            None => (file.clone(), true),
        };
        let lower = base.to_ascii_lowercase();
        if !(lower.ends_with(".jar") || lower.ends_with(".zip")) {
            continue;
        }
        let path = entry.path();
        let size = entry.metadata().map(|m| m.len()).unwrap_or(0);
        let meta = read_meta(&path);
        out.push(InstalledMod {
            name: meta.name.unwrap_or_else(|| base.trim_end_matches(".jar").to_string()),
            version: meta.version,
            description: meta.description,
            loader: meta.loader.unwrap_or("unknown").to_string(),
            file,
            enabled,
            size,
        });
    }
    out.sort_by_key(|m| m.name.to_lowercase());
    Ok(out)
}

fn checked(dir: &Path, file: &str) -> Result<PathBuf> {
    let simple = !file.is_empty()
        && !file.contains(['/', '\\'])
        && file != "."
        && file != "..";
    if !simple {
        bail!("invalid mod file name");
    }
    let p = dir.join(file);
    if !p.is_file() {
        bail!("{file} is not in the mods folder");
    }
    Ok(p)
}

/// Enable or disable a mod by renaming it (`x.jar` ⇄ `x.jar.disabled`).
/// Returns the new file name.
pub fn set_enabled(dir: &Path, file: &str, enabled: bool) -> Result<String> {
    let path = checked(dir, file)?;
    let new_name = match (enabled, file.strip_suffix(DISABLED)) {
        (true, Some(base)) => base.to_string(),
        (false, None) => format!("{file}{DISABLED}"),
        _ => return Ok(file.to_string()),
    };
    std::fs::rename(path, dir.join(&new_name))?;
    Ok(new_name)
}

pub fn delete(dir: &Path, file: &str) -> Result<()> {
    std::fs::remove_file(checked(dir, file)?)?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    fn jar(path: &Path, entries: &[(&str, &str)]) {
        let mut w = zip::ZipWriter::new(std::fs::File::create(path).unwrap());
        for (name, body) in entries {
            w.start_file(*name, zip::write::SimpleFileOptions::default()).unwrap();
            w.write_all(body.as_bytes()).unwrap();
        }
        w.finish().unwrap();
    }

    #[test]
    fn lists_toggles_and_deletes() {
        let dir = tempfile::tempdir().unwrap();
        let d = dir.path();
        jar(&d.join("sodium.jar"), &[("fabric.mod.json", r#"{"id":"sodium","name":"Sodium","version":"0.6.0","description":"Fast"}"#)]);
        jar(
            &d.join("jei.jar"),
            &[("META-INF/mods.toml", "modLoader=\"javafml\"\n[[mods]]\nmodId=\"jei\"\nversion=\"15.2.0\"\ndisplayName=\"Just Enough Items\"\ndescription='''Item viewer'''\n")],
        );
        jar(&d.join("old.jar"), &[("mcmod.info", r#"[{"modid":"x","name":"Old Mod","version":"${version}"}]"#)]);
        std::fs::write(d.join("notes.txt"), "not a mod").unwrap();

        let mods = list(d).unwrap();
        let names: Vec<&str> = mods.iter().map(|m| m.name.as_str()).collect();
        assert_eq!(names, vec!["Just Enough Items", "Old Mod", "Sodium"]);
        assert_eq!(mods[0].loader, "forge");
        assert_eq!(mods[0].version.as_deref(), Some("15.2.0"));
        assert_eq!(mods[0].description.as_deref(), Some("Item viewer"));
        assert_eq!(mods[1].version, None);
        assert_eq!(mods[2].loader, "fabric");

        let off = set_enabled(d, "sodium.jar", false).unwrap();
        assert_eq!(off, "sodium.jar.disabled");
        assert!(!list(d).unwrap().iter().find(|m| m.name == "Sodium").unwrap().enabled);
        assert_eq!(set_enabled(d, &off, true).unwrap(), "sodium.jar");

        assert!(delete(d, "../escape.jar").is_err());
        delete(d, "old.jar").unwrap();
        assert_eq!(list(d).unwrap().len(), 2);
    }
}
