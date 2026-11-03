//! Minecraft Forge, installed straight from Forge's official installer jars.
//!
//! Two installer generations are handled:
//! * **legacy** (1.6 – 1.12): `install_profile.json` has `install` +
//!   `versionInfo`; we save `versionInfo` as the version JSON and unpack the
//!   universal jar into the library tree.
//! * **modern** (1.12.2 late builds, 1.13+): `install_profile.json` lists
//!   `data` and `processors`. We unpack the embedded maven files, download the
//!   processor libraries, then run every client-side processor with the Java
//!   runtime of that Minecraft version — exactly what Forge's installer does
//!   (binary patching, mappings, jar splitting).
//!
//! The resulting version JSON `inheritsFrom` vanilla, so the normal install
//! and launch code takes it from there.

use std::collections::HashMap;
use std::io::Read;
use std::path::{Path, PathBuf};

use anyhow::{anyhow, bail, Context, Result};
use serde::Deserialize;
use serde_json::Value;

use crate::download::{sha1_file, write_atomic, DownloadTask, Verify};
use crate::install;
use crate::maven::MavenCoord;
use crate::os;
use crate::paths::{safe_relative, Paths};
use crate::progress::{report, ProgressFn};
use crate::version::Library;
use crate::Launcher;

pub const MAVEN: &str = "https://maven.minecraftforge.net";
pub const METADATA_URL: &str = "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml";
pub const PROMOTIONS_URL: &str = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";

/// One Forge build for a Minecraft version.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ForgeVersion {
    /// Full maven version, e.g. `1.20.1-47.4.28` or `1.7.10-10.13.4.1614-1.7.10`.
    pub id: String,
    /// Forge's own number, e.g. `47.4.28`.
    pub display: String,
    pub recommended: bool,
    pub latest: bool,
}

#[derive(Debug, Deserialize)]
struct Promotions {
    promos: HashMap<String, String>,
}

fn numeric_key(s: &str) -> Vec<u64> {
    s.split(['.', '-', '_'])
        .map(|p| p.trim_start_matches(|c: char| !c.is_ascii_digit()))
        .map(|p| p.chars().take_while(|c| c.is_ascii_digit()).collect::<String>())
        .map(|p| p.parse().unwrap_or(0))
        .collect()
}

/// Parse `maven-metadata.xml` + promotions into the builds for `game`,
/// newest first.
pub fn parse_versions(metadata_xml: &str, promos: &HashMap<String, String>, game: &str) -> Vec<ForgeVersion> {
    let recommended = promos.get(&format!("{game}-recommended"));
    let latest = promos.get(&format!("{game}-latest"));
    let mut out: Vec<ForgeVersion> = metadata_xml
        .split("<version>")
        .skip(1)
        .filter_map(|chunk| chunk.split("</version>").next())
        .filter_map(|full| {
            let (mc, rest) = full.split_once('-')?;
            if mc != game {
                return None;
            }
            let display = rest
                .strip_suffix(&format!("-{game}"))
                .unwrap_or(rest)
                .to_string();
            Some(ForgeVersion {
                id: full.to_string(),
                recommended: recommended == Some(&display),
                latest: latest == Some(&display),
                display,
            })
        })
        .collect();
    out.sort_by_key(|v| std::cmp::Reverse(numeric_key(&v.display)));
    out.dedup_by(|a, b| a.id == b.id);
    out
}

/// Forge builds available for a Minecraft version (empty if none).
pub async fn versions(launcher: &Launcher, game: &str) -> Result<Vec<ForgeVersion>> {
    let dl = launcher.downloader();
    let cache = launcher.paths().cache();
    let xml = dl.get_cached(METADATA_URL, &cache.join("forge-maven-metadata.xml")).await?;
    let promos: HashMap<String, String> = match dl
        .get_cached(PROMOTIONS_URL, &cache.join("forge-promotions.json"))
        .await
        .ok()
        .and_then(|b| serde_json::from_slice::<Promotions>(&b).ok())
    {
        Some(p) => p.promos,
        None => HashMap::new(),
    };
    Ok(parse_versions(&String::from_utf8_lossy(&xml), &promos, game))
}

fn lib_path(paths: &Paths, coord: &str) -> Result<PathBuf> {
    let c = MavenCoord::parse(coord).ok_or_else(|| anyhow!("bad maven coordinate '{coord}'"))?;
    let rel = safe_relative(&c.path()).ok_or_else(|| anyhow!("unsafe library path for '{coord}'"))?;
    Ok(paths.libraries().join(rel))
}

/// Replace `{KEY}` tokens; a whole `[coord]` argument becomes a library path.
fn resolve_arg(arg: &str, data: &HashMap<String, String>, paths: &Paths) -> Result<String> {
    if let Some(coord) = arg.strip_prefix('[').and_then(|a| a.strip_suffix(']')) {
        return Ok(lib_path(paths, coord)?.to_string_lossy().into_owned());
    }
    let mut out = String::new();
    let mut rest = arg;
    while let Some(start) = rest.find('{') {
        out.push_str(&rest[..start]);
        let after = &rest[start + 1..];
        match after.find('}') {
            Some(end) => {
                let key = &after[..end];
                match data.get(key) {
                    Some(v) => out.push_str(v),
                    None => out.push_str(&rest[start..start + end + 2]),
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
    Ok(out)
}

#[derive(Debug, Deserialize)]
struct Processor {
    jar: String,
    #[serde(default)]
    classpath: Vec<String>,
    #[serde(default)]
    args: Vec<String>,
    #[serde(default)]
    outputs: HashMap<String, String>,
    #[serde(default)]
    sides: Option<Vec<String>>,
}

fn read_entry(zip: &mut zip::ZipArchive<std::fs::File>, name: &str) -> Result<Vec<u8>> {
    let mut f = zip
        .by_name(name)
        .with_context(|| format!("installer has no {name}"))?;
    let mut buf = Vec::new();
    f.read_to_end(&mut buf)?;
    Ok(buf)
}

fn extract_entry(zip: &mut zip::ZipArchive<std::fs::File>, name: &str, dest: &Path) -> Result<()> {
    let data = read_entry(zip, name)?;
    if let Some(parent) = dest.parent() {
        std::fs::create_dir_all(parent)?;
    }
    std::fs::write(dest, data)?;
    Ok(())
}

/// Unpack `maven/**` from the installer into the library tree.
fn extract_maven(zip: &mut zip::ZipArchive<std::fs::File>, libraries: &Path) -> Result<()> {
    for i in 0..zip.len() {
        let mut entry = zip.by_index(i)?;
        if entry.is_dir() {
            continue;
        }
        let name = entry.name()?.to_string();
        let Some(rel) = name.strip_prefix("maven/").and_then(safe_relative) else {
            continue;
        };
        let out = libraries.join(rel);
        if std::fs::metadata(&out).is_ok_and(|m| m.len() == entry.size()) {
            continue;
        }
        if let Some(parent) = out.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let mut f = std::fs::File::create(&out)?;
        std::io::copy(&mut entry, &mut f)?;
    }
    Ok(())
}

fn main_class(jar: &Path) -> Result<String> {
    let mut zip = zip::ZipArchive::new(std::fs::File::open(jar)?)?;
    let manifest = String::from_utf8_lossy(&read_entry(&mut zip, "META-INF/MANIFEST.MF")?).into_owned();
    manifest
        .lines()
        .find_map(|l| l.strip_prefix("Main-Class:"))
        .map(|s| s.trim().to_string())
        .ok_or_else(|| anyhow!("{} has no Main-Class", jar.display()))
}

enum Profile {
    Legacy {
        version_json: Vec<u8>,
        id: String,
        universal_entry: String,
        universal_coord: String,
    },
    Modern {
        version_json: Vec<u8>,
        id: String,
        minecraft: String,
        libraries: Vec<Library>,
        data: HashMap<String, String>,
        processors: Vec<Processor>,
    },
}

fn read_profile(installer: &Path) -> Result<Profile> {
    let mut zip = zip::ZipArchive::new(std::fs::File::open(installer)?)
        .context("the Forge installer is not a valid jar")?;
    let profile: Value = serde_json::from_slice(&read_entry(&mut zip, "install_profile.json")?)?;

    if let (Some(install), Some(info)) = (profile.get("install"), profile.get("versionInfo")) {
        let id = info["id"].as_str().ok_or_else(|| anyhow!("versionInfo has no id"))?.to_string();
        return Ok(Profile::Legacy {
            version_json: serde_json::to_vec_pretty(info)?,
            id,
            universal_entry: install["filePath"].as_str().unwrap_or_default().to_string(),
            universal_coord: install["path"].as_str().unwrap_or_default().to_string(),
        });
    }

    let json_name = profile["json"].as_str().unwrap_or("/version.json").trim_start_matches('/');
    let version_json = read_entry(&mut zip, json_name)?;
    let parsed: Value = serde_json::from_slice(&version_json)?;
    let id = parsed["id"].as_str().ok_or_else(|| anyhow!("version.json has no id"))?.to_string();
    let minecraft = profile["minecraft"].as_str().unwrap_or_default().to_string();
    let libraries: Vec<Library> = serde_json::from_value(profile.get("libraries").cloned().unwrap_or(Value::Array(vec![])))?;
    let processors: Vec<Processor> = serde_json::from_value(profile.get("processors").cloned().unwrap_or(Value::Array(vec![])))?;
    let mut data = HashMap::new();
    if let Some(map) = profile.get("data").and_then(|d| d.as_object()) {
        for (k, v) in map {
            if let Some(client) = v.get("client").and_then(|c| c.as_str()) {
                data.insert(k.clone(), client.to_string());
            }
        }
    }
    Ok(Profile::Modern { version_json, id, minecraft, libraries, data, processors })
}

/// Install Forge for `game` and return the version id to launch.
/// `version` may be the full maven version or Forge's own number;
/// `None` picks the recommended build (else the newest).
pub async fn install(
    launcher: &Launcher,
    game: &str,
    version: Option<&str>,
    progress: &ProgressFn,
) -> Result<String> {
    let paths = launcher.paths().clone();
    let cache = paths.cache().join("forge");

    // Fast path: already installed (works offline).
    if let Some(v) = version {
        for candidate in [v.to_string(), format!("{game}-{v}")] {
            if let Ok(id) = std::fs::read_to_string(cache.join(format!("{candidate}.id"))) {
                if paths.version_json(id.trim()).is_file() {
                    return Ok(id.trim().to_string());
                }
            }
        }
    }

    report(progress, &format!("Finding Forge for {game}"), 0, 0);
    let builds = versions(launcher, game).await?;
    let build = match version {
        Some(v) => builds.iter().find(|b| b.id == v || b.display == v).cloned(),
        None => builds
            .iter()
            .find(|b| b.recommended)
            .or_else(|| builds.iter().find(|b| b.latest))
            .or(builds.first())
            .cloned(),
    }
    .ok_or_else(|| match version {
        Some(v) => anyhow!("Forge {v} does not exist for Minecraft {game}"),
        None => anyhow!("Forge is not available for Minecraft {game}"),
    })?;

    let marker = cache.join(format!("{}.id", build.id));
    if let Ok(id) = std::fs::read_to_string(&marker) {
        if paths.version_json(id.trim()).is_file() {
            return Ok(id.trim().to_string());
        }
    }

    let installer = cache.join(format!("forge-{}-installer.jar", build.id));
    let url = format!("{MAVEN}/net/minecraftforge/forge/{0}/forge-{0}-installer.jar", build.id);
    let sha1 = launcher
        .http()
        .get(format!("{url}.sha1"))
        .send()
        .await
        .ok()
        .filter(|r| r.status().is_success());
    let sha1 = match sha1 {
        Some(r) => r.text().await.ok().map(|t| t.trim().to_string()).filter(|t| t.len() == 40),
        None => None,
    };
    launcher
        .downloader()
        .run(vec![DownloadTask::new(&url, &installer).sha1(sha1)], "Forge installer", Verify::Hash, progress)
        .await
        .map_err(|e| e.context(format!("Forge {} has no installer (Forge before 1.6 is not supported)", build.display)))?;

    let inst = installer.clone();
    let profile = tokio::task::spawn_blocking(move || read_profile(&inst)).await??;

    let id = match profile {
        Profile::Legacy { version_json, id, universal_entry, universal_coord } => {
            report(progress, "Unpacking Forge", 0, 0);
            let dest = lib_path(&paths, &universal_coord)?;
            let inst = installer.clone();
            tokio::task::spawn_blocking(move || {
                let mut zip = zip::ZipArchive::new(std::fs::File::open(&inst)?)?;
                extract_entry(&mut zip, &universal_entry, &dest)
            })
            .await??;
            write_atomic(&paths.version_json(&id), &version_json).await?;
            id
        }
        Profile::Modern { version_json, id, minecraft, libraries, mut data, processors } => {
            report(progress, "Unpacking Forge", 0, 0);
            let (inst, libs_dir) = (installer.clone(), paths.libraries());
            tokio::task::spawn_blocking(move || {
                let mut zip = zip::ZipArchive::new(std::fs::File::open(&inst)?)?;
                extract_maven(&mut zip, &libs_dir)
            })
            .await??;

            let client: Vec<Processor> = processors
                .into_iter()
                .filter(|p| p.sides.as_ref().is_none_or(|s| s.iter().any(|x| x == "client")))
                .collect();
            if !client.is_empty() {
                run_processors(launcher, &installer, &build.id, &minecraft, &libraries, &mut data, client, progress).await?;
            }
            write_atomic(&paths.version_json(&id), &version_json).await?;
            id
        }
    };
    write_atomic(&marker, id.as_bytes()).await?;
    Ok(id)
}

#[allow(clippy::too_many_arguments)]
async fn run_processors(
    launcher: &Launcher,
    installer: &Path,
    build: &str,
    minecraft: &str,
    libraries: &[Library],
    data: &mut HashMap<String, String>,
    processors: Vec<Processor>,
    progress: &ProgressFn,
) -> Result<()> {
    let paths = launcher.paths().clone();

    // Vanilla first: processors patch its client jar.
    let vanilla = launcher.install_version(minecraft, progress).await?;
    let java = launcher.java_for(&vanilla, None, progress).await?;

    let mut tasks = Vec::new();
    for lib in libraries {
        if let Some(a) = lib.artifact() {
            install::task_for(&paths, &a, &mut tasks)?;
        }
    }
    launcher
        .downloader()
        .run(tasks, "Forge tools", Verify::Hash, progress)
        .await?;

    // Resolve the installer's data table.
    let scratch = paths.cache().join("forge").join(format!("{build}-data"));
    for value in data.values_mut() {
        if let Some(coord) = value.strip_prefix('[').and_then(|v| v.strip_suffix(']')) {
            *value = lib_path(&paths, coord)?.to_string_lossy().into_owned();
        } else if let Some(lit) = value.strip_prefix('\'').and_then(|v| v.strip_suffix('\'')) {
            *value = lit.to_string();
        } else if let Some(entry) = value.strip_prefix('/') {
            let rel = safe_relative(entry).ok_or_else(|| anyhow!("unsafe installer path {entry}"))?;
            let dest = scratch.join(rel);
            let (inst, name, d) = (installer.to_path_buf(), entry.to_string(), dest.clone());
            tokio::task::spawn_blocking(move || {
                let mut zip = zip::ZipArchive::new(std::fs::File::open(&inst)?)?;
                extract_entry(&mut zip, &name, &d)
            })
            .await??;
            *value = dest.to_string_lossy().into_owned();
        }
    }
    let s = |p: &Path| p.to_string_lossy().into_owned();
    data.insert("SIDE".into(), "client".into());
    data.insert("MINECRAFT_JAR".into(), s(&vanilla.client_jar));
    data.insert("MINECRAFT_VERSION".into(), minecraft.to_string());
    data.insert("ROOT".into(), s(&paths.root));
    data.insert("INSTALLER".into(), s(installer));
    data.insert("LIBRARY_DIR".into(), s(&paths.libraries()));

    let total = processors.len() as u64;
    for (i, proc) in processors.iter().enumerate() {
        report(progress, "Patching Minecraft for Forge", i as u64, total);
        let args: Vec<String> = proc
            .args
            .iter()
            .map(|a| resolve_arg(a, data, &paths))
            .collect::<Result<_>>()?;

        // Fetch Mojang's mappings ourselves rather than relying on Java's
        // network settings.
        if args.iter().any(|a| a == "DOWNLOAD_MOJMAPS") {
            let output = args
                .iter()
                .position(|a| a == "--output")
                .and_then(|i| args.get(i + 1))
                .ok_or_else(|| anyhow!("DOWNLOAD_MOJMAPS without --output"))?;
            let mappings = vanilla
                .version
                .downloads
                .as_ref()
                .and_then(|d| d.client_mappings.clone())
                .ok_or_else(|| anyhow!("Minecraft {minecraft} has no client mappings"))?;
            launcher
                .downloader()
                .run(
                    vec![DownloadTask::new(&mappings.url, output).sha1(mappings.sha1).size(mappings.size)],
                    "Mappings",
                    Verify::Hash,
                    progress,
                )
                .await?;
            continue;
        }

        let outputs: Vec<(PathBuf, String)> = proc
            .outputs
            .iter()
            .map(|(k, v)| Ok((PathBuf::from(resolve_arg(k, data, &paths)?), resolve_arg(v, data, &paths)?)))
            .collect::<Result<_>>()?;
        let outputs_ok = |outs: &[(PathBuf, String)]| {
            !outs.is_empty()
                && outs.iter().all(|(p, sha)| {
                    sha1_file(p).map(|a| a.eq_ignore_ascii_case(sha.trim_matches('\''))).unwrap_or(false)
                })
        };
        if outputs_ok(&outputs) {
            continue;
        }

        let jar = lib_path(&paths, &proc.jar)?;
        let jar_for_main = jar.clone();
        let main = tokio::task::spawn_blocking(move || main_class(&jar_for_main)).await??;
        let mut cp = vec![s(&jar)];
        for c in &proc.classpath {
            cp.push(s(&lib_path(&paths, c)?));
        }

        let mut cmd = tokio::process::Command::new(&java);
        cmd.arg("-cp")
            .arg(cp.join(os::classpath_separator()))
            .arg(&main)
            .args(&args)
            .current_dir(&paths.root)
            .stdin(std::process::Stdio::null());
        #[cfg(windows)]
        cmd.creation_flags(0x0800_0000);
        let out = cmd
            .output()
            .await
            .with_context(|| format!("could not run Forge processor {}", proc.jar))?;
        if !out.status.success() {
            let text = format!(
                "{}{}",
                String::from_utf8_lossy(&out.stdout),
                String::from_utf8_lossy(&out.stderr)
            );
            let tail: Vec<&str> = text.lines().rev().take(12).collect();
            let tail: Vec<&str> = tail.into_iter().rev().collect();
            bail!("Forge processor {} failed ({}):\n{}", proc.jar, out.status, tail.join("\n"));
        }
        if !outputs.is_empty() && !outputs_ok(&outputs) {
            bail!("Forge processor {} produced unexpected output", proc.jar);
        }
    }
    report(progress, "Patching Minecraft for Forge", total, total);
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    const XML: &str = r#"<metadata><versioning><versions>
        <version>1.20.1-47.4.5</version>
        <version>1.20.1-47.4.28</version>
        <version>1.20.1-47.4.10</version>
        <version>1.7.10-10.13.4.1614-1.7.10</version>
        <version>1.20.4-49.0.1</version>
    </versions></versioning></metadata>"#;

    #[test]
    fn parses_and_sorts_versions() {
        let mut promos = HashMap::new();
        promos.insert("1.20.1-recommended".to_string(), "47.4.10".to_string());
        promos.insert("1.20.1-latest".to_string(), "47.4.28".to_string());
        let v = parse_versions(XML, &promos, "1.20.1");
        let displays: Vec<&str> = v.iter().map(|x| x.display.as_str()).collect();
        assert_eq!(displays, vec!["47.4.28", "47.4.10", "47.4.5"]);
        assert!(v[0].latest && !v[0].recommended);
        assert!(v[1].recommended);
        assert_eq!(v[0].id, "1.20.1-47.4.28");

        let old = parse_versions(XML, &HashMap::new(), "1.7.10");
        assert_eq!(old[0].id, "1.7.10-10.13.4.1614-1.7.10");
        assert_eq!(old[0].display, "10.13.4.1614");
        assert!(parse_versions(XML, &HashMap::new(), "1.20").is_empty());
    }

    #[test]
    fn resolves_processor_args() {
        let paths = Paths::new("/root");
        let mut data = HashMap::new();
        data.insert("SIDE".to_string(), "client".to_string());
        data.insert("MC_SLIM".to_string(), "/x/slim.jar".to_string());
        assert_eq!(resolve_arg("{SIDE}", &data, &paths).unwrap(), "client");
        assert_eq!(resolve_arg("--out={MC_SLIM}", &data, &paths).unwrap(), "--out=/x/slim.jar");
        assert_eq!(resolve_arg("{UNKNOWN}", &data, &paths).unwrap(), "{UNKNOWN}");
        let p = resolve_arg("[de.oceanlabs.mcp:mcp_config:1.20.1-2023@zip]", &data, &paths).unwrap();
        assert!(p.replace('\\', "/").ends_with("de/oceanlabs/mcp/mcp_config/1.20.1-2023/mcp_config-1.20.1-2023.zip"), "{p}");
    }
}
