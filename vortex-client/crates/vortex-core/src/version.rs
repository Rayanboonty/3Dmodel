//! The per-version JSON (`versions/<id>/<id>.json`) and its pieces.
//!
//! Supports every format Mojang has used:
//! * legacy `minecraftArguments` strings (≤ 1.12.2, betas, alphas),
//! * modern `arguments.game` / `arguments.jvm` lists with rules (1.13+),
//! * `inheritsFrom` profiles (Fabric, and anything else built the same way).

use std::collections::BTreeMap;

use serde::{Deserialize, Serialize};

use crate::maven::{join_url, MavenCoord};
use crate::os;
use crate::rules::{self, Environment, Rule};

pub const MOJANG_LIBRARIES: &str = "https://libraries.minecraft.net/";

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VersionJson {
    pub id: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub inherits_from: Option<String>,
    #[serde(default, rename = "type", skip_serializing_if = "Option::is_none")]
    pub kind: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub main_class: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub minecraft_arguments: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub arguments: Option<Arguments>,
    #[serde(default)]
    pub libraries: Vec<Library>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub asset_index: Option<AssetIndexRef>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub assets: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub downloads: Option<VersionDownloads>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub java_version: Option<JavaVersion>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub logging: Option<Logging>,
    /// Which version's jar to use as the client jar (set on child profiles).
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub jar: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub release_time: Option<String>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Arguments {
    #[serde(default)]
    pub game: Vec<Argument>,
    #[serde(default)]
    pub jvm: Vec<Argument>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(untagged)]
pub enum Argument {
    Plain(String),
    Conditional { rules: Vec<Rule>, value: ArgValue },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(untagged)]
pub enum ArgValue {
    One(String),
    Many(Vec<String>),
}

impl Argument {
    /// The raw (unsubstituted) values this argument contributes in `env`.
    pub fn values(&self, env: &Environment) -> Vec<String> {
        match self {
            Argument::Plain(s) => vec![s.clone()],
            Argument::Conditional { rules: r, value } => {
                if !rules::allowed(r, env) {
                    return Vec::new();
                }
                match value {
                    ArgValue::One(s) => vec![s.clone()],
                    ArgValue::Many(v) => v.clone(),
                }
            }
        }
    }

    /// Mentions `needle` anywhere (regardless of rules).
    pub fn mentions(&self, needle: &str) -> bool {
        match self {
            Argument::Plain(s) => s.contains(needle),
            Argument::Conditional { value, .. } => match value {
                ArgValue::One(s) => s.contains(needle),
                ArgValue::Many(v) => v.iter().any(|s| s.contains(needle)),
            },
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Download {
    #[serde(default)]
    pub sha1: Option<String>,
    #[serde(default)]
    pub size: Option<u64>,
    pub url: String,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct VersionDownloads {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub client: Option<Download>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub server: Option<Download>,
    /// ProGuard mappings for the client (Forge's installer needs them).
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub client_mappings: Option<Download>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AssetIndexRef {
    pub id: String,
    pub sha1: String,
    pub size: u64,
    #[serde(default)]
    pub total_size: Option<u64>,
    pub url: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JavaVersion {
    pub component: String,
    pub major_version: u32,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Logging {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub client: Option<LoggingClient>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LoggingClient {
    /// e.g. `-Dlog4j.configurationFile=${path}`
    pub argument: String,
    pub file: LoggingFile,
    #[serde(default, rename = "type")]
    pub kind: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LoggingFile {
    pub id: String,
    pub sha1: String,
    #[serde(default)]
    pub size: Option<u64>,
    pub url: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Library {
    pub name: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub downloads: Option<LibraryDownloads>,
    /// Maven repository base (Fabric and other third-party profiles).
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub url: Option<String>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub rules: Vec<Rule>,
    /// Legacy natives map: OS name → classifier (may contain `${arch}`).
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub natives: Option<BTreeMap<String, String>>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub extract: Option<Extract>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub sha1: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub size: Option<u64>,
    /// Legacy Forge: `false` marks a server-only library.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub clientreq: Option<bool>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct LibraryDownloads {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub artifact: Option<Artifact>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub classifiers: Option<BTreeMap<String, Artifact>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Artifact {
    #[serde(default)]
    pub path: Option<String>,
    #[serde(default)]
    pub sha1: Option<String>,
    #[serde(default)]
    pub size: Option<u64>,
    #[serde(default)]
    pub url: String,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Extract {
    #[serde(default)]
    pub exclude: Vec<String>,
}

/// A library file resolved to a concrete location.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ResolvedArtifact {
    /// Path relative to the libraries directory, `/`-separated.
    pub path: String,
    /// `None` when the file must already exist locally.
    pub url: Option<String>,
    pub sha1: Option<String>,
    pub size: Option<u64>,
}

impl Library {
    pub fn coord(&self) -> Option<MavenCoord> {
        MavenCoord::parse(&self.name)
    }

    pub fn applies(&self, env: &Environment) -> bool {
        self.clientreq != Some(false) && rules::allowed(&self.rules, env)
    }

    /// Key used to de-duplicate libraries across inherited profiles.
    pub fn dedup_key(&self) -> String {
        self.coord()
            .map(|c| c.dedup_key())
            .unwrap_or_else(|| self.name.clone())
    }

    fn base_url(&self) -> &str {
        self.url
            .as_deref()
            .filter(|u| !u.is_empty())
            .unwrap_or(MOJANG_LIBRARIES)
    }

    /// The jar that goes on the classpath, if any. Legacy natives-only
    /// libraries (LWJGL 2 `lwjgl-platform` etc.) have none.
    pub fn artifact(&self) -> Option<ResolvedArtifact> {
        if let Some(a) = self.downloads.as_ref().and_then(|d| d.artifact.as_ref()) {
            let path = a.path.clone().or_else(|| self.coord().map(|c| c.path()))?;
            return Some(ResolvedArtifact {
                path,
                url: Some(a.url.clone()).filter(|u| !u.is_empty()),
                sha1: a.sha1.clone(),
                size: a.size,
            });
        }
        if self.natives.is_some() {
            return None;
        }
        if self
            .downloads
            .as_ref()
            .is_some_and(|d| d.classifiers.is_some())
        {
            return None;
        }
        let path = self.coord()?.path();
        Some(ResolvedArtifact {
            url: Some(join_url(self.base_url(), &path)),
            path,
            sha1: self.sha1.clone(),
            size: self.size,
        })
    }

    /// The legacy natives jar to extract for the current OS, if any.
    pub fn native(&self, env: &Environment) -> Option<ResolvedArtifact> {
        let natives = self.natives.as_ref()?;
        let classifier = natives
            .get(&env.os_name)?
            .replace("${arch}", os::arch_bits());
        if let Some(a) = self
            .downloads
            .as_ref()
            .and_then(|d| d.classifiers.as_ref())
            .and_then(|c| c.get(&classifier))
        {
            let path = a
                .path
                .clone()
                .or_else(|| self.coord().map(|c| c.with_classifier(&classifier).path()))?;
            return Some(ResolvedArtifact {
                path,
                url: Some(a.url.clone()).filter(|u| !u.is_empty()),
                sha1: a.sha1.clone(),
                size: a.size,
            });
        }
        let path = self.coord()?.with_classifier(&classifier).path();
        Some(ResolvedArtifact {
            url: Some(join_url(self.base_url(), &path)),
            path,
            sha1: None,
            size: None,
        })
    }

    pub fn extract_excludes(&self) -> Vec<String> {
        self.extract
            .as_ref()
            .map(|e| e.exclude.clone())
            .unwrap_or_default()
    }
}

impl VersionJson {
    /// Merge this (child) profile on top of its `inheritsFrom` parent.
    ///
    /// Child values win; libraries are child-first; argument lists are
    /// concatenated parent-then-child. Like the official launcher, a child
    /// without an explicit `jar` gets its own copy of the parent's client jar
    /// at `versions/<child>/<child>.jar` (Forge's module system expects the
    /// jar to be named after the version it launches).
    pub fn merge_onto(self, parent: VersionJson) -> VersionJson {
        let child_has_jar = self
            .downloads
            .as_ref()
            .is_some_and(|d| d.client.is_some());
        let jar = self.jar;

        let mut libraries = self.libraries;
        libraries.extend(parent.libraries);

        let arguments = match (parent.arguments, self.arguments) {
            (Some(mut p), Some(c)) => {
                p.game.extend(c.game);
                p.jvm.extend(c.jvm);
                Some(p)
            }
            (p, c) => c.or(p),
        };

        VersionJson {
            id: self.id,
            inherits_from: parent.inherits_from,
            kind: self.kind.or(parent.kind),
            main_class: self.main_class.or(parent.main_class),
            minecraft_arguments: self.minecraft_arguments.or(parent.minecraft_arguments),
            arguments,
            libraries,
            asset_index: self.asset_index.or(parent.asset_index),
            assets: self.assets.or(parent.assets),
            downloads: if child_has_jar { self.downloads } else { parent.downloads },
            java_version: self.java_version.or(parent.java_version),
            logging: self.logging.or(parent.logging),
            jar,
            release_time: self.release_time.or(parent.release_time),
        }
    }

    /// The version whose `<id>.jar` is the client jar.
    pub fn client_jar_id(&self) -> &str {
        self.jar.as_deref().unwrap_or(&self.id)
    }

    /// Asset index id, falling back to the legacy `assets` field, then the
    /// historical default.
    pub fn asset_index_id(&self) -> String {
        self.asset_index
            .as_ref()
            .map(|a| a.id.clone())
            .or_else(|| self.assets.clone())
            .unwrap_or_else(|| "legacy".to_string())
    }

    /// `(component, major)` of the Java runtime this version wants.
    /// Versions predating the `javaVersion` field all run on Java 8.
    pub fn java_requirement(&self) -> (String, u32) {
        match &self.java_version {
            Some(j) => (j.component.clone(), j.major_version),
            None => ("jre-legacy".to_string(), 8),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashSet;

    fn win() -> Environment {
        Environment {
            os_name: "windows".into(),
            os_version: "10.0".into(),
            os_arch: "x86_64".into(),
            features: HashSet::new(),
        }
    }

    #[test]
    fn legacy_natives_library() {
        let lib: Library = serde_json::from_str(
            r#"{
            "downloads": {"classifiers": {
                "natives-windows": {"path": "org/lwjgl/lwjgl/lwjgl-platform/2.9.0/lwjgl-platform-2.9.0-natives-windows.jar",
                    "sha1": "aaa", "size": 1, "url": "https://libraries.minecraft.net/x.jar"},
                "natives-linux": {"path": "l.jar", "sha1": "bbb", "size": 2, "url": "https://libraries.minecraft.net/l.jar"}
            }},
            "extract": {"exclude": ["META-INF/"]},
            "name": "org.lwjgl.lwjgl:lwjgl-platform:2.9.0",
            "natives": {"linux": "natives-linux", "osx": "natives-osx", "windows": "natives-windows"}
        }"#,
        )
        .unwrap();
        assert!(lib.artifact().is_none(), "natives-only libs are not on the classpath");
        let n = lib.native(&win()).unwrap();
        assert_eq!(n.sha1.as_deref(), Some("aaa"));
        assert_eq!(lib.extract_excludes(), vec!["META-INF/".to_string()]);
    }

    #[test]
    fn arch_placeholder_in_classifier() {
        let lib: Library = serde_json::from_str(
            r#"{"name": "tv.twitch:twitch-platform:5.16", "natives": {"windows": "natives-windows-${arch}"}}"#,
        )
        .unwrap();
        let n = lib.native(&win()).unwrap();
        assert!(n.path.ends_with(&format!("twitch-platform-5.16-natives-windows-{}.jar", os::arch_bits())));
    }

    #[test]
    fn maven_style_library() {
        let lib: Library = serde_json::from_str(
            r#"{"name": "net.fabricmc:fabric-loader:0.16.9", "url": "https://maven.fabricmc.net/", "sha1": "abc", "size": 5}"#,
        )
        .unwrap();
        let a = lib.artifact().unwrap();
        assert_eq!(
            a.url.as_deref(),
            Some("https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.16.9/fabric-loader-0.16.9.jar")
        );
        assert_eq!(a.sha1.as_deref(), Some("abc"));
    }

    #[test]
    fn conditional_arguments() {
        let args: Arguments = serde_json::from_str(
            r#"{"game": ["--username", "${auth_player_name}",
                {"rules": [{"action": "allow", "features": {"has_custom_resolution": true}}],
                 "value": ["--width", "${resolution_width}"]}],
               "jvm": [{"rules": [{"action": "allow", "os": {"name": "windows"}}], "value": "-XX:HeapDumpPath=x"}]}"#,
        )
        .unwrap();
        let mut env = win();
        let game: Vec<String> = args.game.iter().flat_map(|a| a.values(&env)).collect();
        assert_eq!(game, vec!["--username", "${auth_player_name}"]);
        env.enable("has_custom_resolution");
        let game: Vec<String> = args.game.iter().flat_map(|a| a.values(&env)).collect();
        assert_eq!(game.len(), 4);
        let jvm: Vec<String> = args.jvm.iter().flat_map(|a| a.values(&env)).collect();
        assert_eq!(jvm, vec!["-XX:HeapDumpPath=x"]);
    }

    #[test]
    fn merge_fabric_profile() {
        let parent: VersionJson = serde_json::from_str(
            r#"{"id": "1.21.4", "type": "release", "mainClass": "net.minecraft.client.main.Main",
                "arguments": {"game": ["--version", "${version_name}"], "jvm": ["-cp", "${classpath}"]},
                "libraries": [{"name": "org.ow2.asm:asm:9.6"}, {"name": "com.mojang:brigadier:1.0"}],
                "assetIndex": {"id": "19", "sha1": "s", "size": 1, "url": "u"},
                "downloads": {"client": {"sha1": "c", "size": 1, "url": "https://x/client.jar"}},
                "javaVersion": {"component": "java-runtime-delta", "majorVersion": 21}}"#,
        )
        .unwrap();
        let child: VersionJson = serde_json::from_str(
            r#"{"id": "fabric-loader-0.16.9-1.21.4", "inheritsFrom": "1.21.4",
                "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
                "arguments": {"game": [], "jvm": ["-DFabricMcEmu= net.minecraft.client.main.Main "]},
                "libraries": [{"name": "org.ow2.asm:asm:9.7", "url": "https://maven.fabricmc.net/"}]}"#,
        )
        .unwrap();
        let merged = child.merge_onto(parent);
        assert_eq!(merged.id, "fabric-loader-0.16.9-1.21.4");
        assert_eq!(merged.inherits_from, None);
        assert_eq!(merged.main_class.as_deref(), Some("net.fabricmc.loader.impl.launch.knot.KnotClient"));
        assert_eq!(merged.client_jar_id(), "fabric-loader-0.16.9-1.21.4");
        assert_eq!(merged.asset_index_id(), "19");
        assert_eq!(merged.java_requirement(), ("java-runtime-delta".to_string(), 21));
        assert_eq!(merged.libraries[0].name, "org.ow2.asm:asm:9.7", "child libraries first");
        let jvm = &merged.arguments.as_ref().unwrap().jvm;
        assert_eq!(jvm.len(), 3);
        assert!(merged.downloads.unwrap().client.is_some());
    }

    #[test]
    fn java_defaults_to_legacy() {
        let v: VersionJson = serde_json::from_str(r#"{"id": "1.2.5"}"#).unwrap();
        assert_eq!(v.java_requirement(), ("jre-legacy".to_string(), 8));
        assert_eq!(v.asset_index_id(), "legacy");
    }
}
