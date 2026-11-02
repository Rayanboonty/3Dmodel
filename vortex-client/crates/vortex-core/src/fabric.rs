//! Fabric mod loader support via meta.fabricmc.net.
//!
//! A Fabric "profile" is just a version JSON that `inheritsFrom` the vanilla
//! version, so once it is saved the normal install path handles it.

use anyhow::{anyhow, Context, Result};
use serde::Deserialize;

use crate::download::write_atomic;
use crate::paths::Paths;
use crate::version::VersionJson;

pub const META: &str = "https://meta.fabricmc.net/v2";

#[derive(Debug, Clone, Deserialize)]
pub struct LoaderInfo {
    pub version: String,
    #[serde(default)]
    pub stable: bool,
}

#[derive(Debug, Deserialize)]
struct LoaderEntry {
    loader: LoaderInfo,
}

#[derive(Debug, Clone, Deserialize)]
pub struct GameVersion {
    pub version: String,
    #[serde(default)]
    pub stable: bool,
}

fn url(segments: &[&str]) -> Result<reqwest::Url> {
    let mut u = reqwest::Url::parse(META)?;
    u.path_segments_mut()
        .map_err(|_| anyhow!("bad base url"))?
        .extend(segments);
    Ok(u)
}

/// Minecraft versions Fabric supports.
pub async fn game_versions(http: &reqwest::Client) -> Result<Vec<GameVersion>> {
    Ok(http
        .get(url(&["versions", "game"])?)
        .send()
        .await?
        .error_for_status()?
        .json()
        .await?)
}

/// Loader versions available for a Minecraft version (newest first).
/// Empty if Fabric does not support that version.
pub async fn loader_versions(http: &reqwest::Client, game_version: &str) -> Result<Vec<LoaderInfo>> {
    let entries: Vec<LoaderEntry> = http
        .get(url(&["versions", "loader", game_version])?)
        .send()
        .await?
        .error_for_status()?
        .json()
        .await?;
    Ok(entries.into_iter().map(|e| e.loader).collect())
}

/// Version id Fabric profiles use.
pub fn profile_id(game_version: &str, loader_version: &str) -> String {
    format!("fabric-loader-{loader_version}-{game_version}")
}

/// Save the Fabric profile for `game_version` and return its version id.
/// With `loader_version = None` the latest stable loader is used.
pub async fn install_profile(
    http: &reqwest::Client,
    paths: &Paths,
    game_version: &str,
    loader_version: Option<&str>,
) -> Result<String> {
    if let Some(lv) = loader_version {
        let id = profile_id(game_version, lv);
        if paths.version_json(&id).is_file() {
            return Ok(id);
        }
    }
    let loader = match loader_version {
        Some(v) => v.to_string(),
        None => {
            let versions = loader_versions(http, game_version).await?;
            versions
                .iter()
                .find(|l| l.stable)
                .or(versions.first())
                .map(|l| l.version.clone())
                .ok_or_else(|| anyhow!("Fabric does not support Minecraft {game_version}"))?
        }
    };
    let id = profile_id(game_version, &loader);
    let path = paths.version_json(&id);
    if path.is_file() {
        return Ok(id);
    }
    let body = http
        .get(url(&["versions", "loader", game_version, &loader, "profile", "json"])?)
        .send()
        .await?
        .error_for_status()
        .with_context(|| format!("Fabric {loader} is not available for {game_version}"))?
        .bytes()
        .await?;
    let profile: VersionJson = serde_json::from_slice(&body).context("invalid Fabric profile")?;
    if profile.id != id {
        return Err(anyhow!("unexpected Fabric profile id {}", profile.id));
    }
    write_atomic(&path, &body).await?;
    Ok(id)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn url_encodes_segments() {
        let u = url(&["versions", "loader", "1.14 Pre-Release 1"]).unwrap();
        assert_eq!(
            u.as_str(),
            "https://meta.fabricmc.net/v2/versions/loader/1.14%20Pre-Release%201"
        );
    }
}
