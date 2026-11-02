//! Mojang's global version manifest — the list of *every* Java Edition
//! version: releases, snapshots, old betas and old alphas.

use serde::{Deserialize, Serialize};

pub const VERSION_MANIFEST_URL: &str =
    "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum VersionType {
    Release,
    Snapshot,
    OldBeta,
    OldAlpha,
    #[serde(other)]
    Other,
}

impl VersionType {
    pub fn label(self) -> &'static str {
        match self {
            VersionType::Release => "Release",
            VersionType::Snapshot => "Snapshot",
            VersionType::OldBeta => "Beta",
            VersionType::OldAlpha => "Alpha",
            VersionType::Other => "Other",
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Latest {
    pub release: String,
    pub snapshot: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ManifestVersion {
    pub id: String,
    #[serde(rename = "type")]
    pub kind: VersionType,
    pub url: String,
    #[serde(default)]
    pub time: String,
    #[serde(default)]
    pub release_time: String,
    #[serde(default)]
    pub sha1: Option<String>,
    #[serde(default)]
    pub compliance_level: Option<u32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VersionManifest {
    pub latest: Latest,
    /// Newest first, exactly as Mojang orders them.
    pub versions: Vec<ManifestVersion>,
}

impl VersionManifest {
    pub fn find(&self, id: &str) -> Option<&ManifestVersion> {
        self.versions.iter().find(|v| v.id == id)
    }

    pub fn of_type(&self, kind: VersionType) -> impl Iterator<Item = &ManifestVersion> {
        self.versions.iter().filter(move |v| v.kind == kind)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_manifest() {
        let json = r#"{
            "latest": {"release": "1.21.4", "snapshot": "25w02a"},
            "versions": [
                {"id": "25w02a", "type": "snapshot", "url": "https://x/25w02a.json",
                 "time": "t", "releaseTime": "r", "sha1": "abc", "complianceLevel": 1},
                {"id": "b1.7.3", "type": "old_beta", "url": "https://x/b.json", "time": "t", "releaseTime": "r"},
                {"id": "rd-132211", "type": "old_alpha", "url": "https://x/rd.json", "time": "t", "releaseTime": "r"},
                {"id": "weird", "type": "experiment", "url": "https://x/w.json", "time": "t", "releaseTime": "r"}
            ]
        }"#;
        let m: VersionManifest = serde_json::from_str(json).unwrap();
        assert_eq!(m.versions.len(), 4);
        assert_eq!(m.find("b1.7.3").unwrap().kind, VersionType::OldBeta);
        assert_eq!(m.find("weird").unwrap().kind, VersionType::Other);
        assert_eq!(m.of_type(VersionType::OldAlpha).count(), 1);
    }
}
