//! Maven coordinates (`group:artifact:version[:classifier][@ext]`).

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct MavenCoord {
    pub group: String,
    pub artifact: String,
    pub version: String,
    pub classifier: Option<String>,
    pub extension: String,
}

impl MavenCoord {
    pub fn parse(s: &str) -> Option<Self> {
        let (coords, extension) = match s.split_once('@') {
            Some((c, e)) => (c, e.to_string()),
            None => (s, "jar".to_string()),
        };
        let parts: Vec<&str> = coords.split(':').collect();
        let (group, artifact, version, classifier) = match parts.as_slice() {
            [g, a, v] => (g, a, v, None),
            [g, a, v, c] => (g, a, v, Some(c.to_string())),
            _ => return None,
        };
        if group.is_empty() || artifact.is_empty() || version.is_empty() {
            return None;
        }
        Some(Self {
            group: group.to_string(),
            artifact: artifact.to_string(),
            version: version.to_string(),
            classifier,
            extension,
        })
    }

    pub fn with_classifier(&self, classifier: &str) -> Self {
        Self {
            classifier: Some(classifier.to_string()),
            ..self.clone()
        }
    }

    /// Relative path inside a maven repository, always with `/` separators.
    pub fn path(&self) -> String {
        let classifier = self
            .classifier
            .as_ref()
            .map(|c| format!("-{c}"))
            .unwrap_or_default();
        format!(
            "{}/{}/{}/{}-{}{}.{}",
            self.group.replace('.', "/"),
            self.artifact,
            self.version,
            self.artifact,
            self.version,
            classifier,
            self.extension
        )
    }

    /// Identity used to de-duplicate libraries (version intentionally left
    /// out so a child profile can override a parent's library version).
    pub fn dedup_key(&self) -> String {
        format!(
            "{}:{}:{}",
            self.group,
            self.artifact,
            self.classifier.as_deref().unwrap_or("")
        )
    }
}

/// Join a repository base URL and a relative path.
pub fn join_url(base: &str, path: &str) -> String {
    format!("{}/{}", base.trim_end_matches('/'), path.trim_start_matches('/'))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_and_path() {
        let c = MavenCoord::parse("org.lwjgl:lwjgl:3.3.3:natives-windows").unwrap();
        assert_eq!(
            c.path(),
            "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar"
        );
        let c = MavenCoord::parse("net.fabricmc:fabric-loader:0.16.9").unwrap();
        assert_eq!(
            c.path(),
            "net/fabricmc/fabric-loader/0.16.9/fabric-loader-0.16.9.jar"
        );
        let c = MavenCoord::parse("com.example:thing:1.0@zip").unwrap();
        assert_eq!(c.path(), "com/example/thing/1.0/thing-1.0.zip");
        assert!(MavenCoord::parse("nope").is_none());
    }

    #[test]
    fn dedup_key_ignores_version() {
        let a = MavenCoord::parse("org.ow2.asm:asm:9.6").unwrap();
        let b = MavenCoord::parse("org.ow2.asm:asm:9.7").unwrap();
        assert_eq!(a.dedup_key(), b.dedup_key());
        let n = MavenCoord::parse("org.lwjgl:lwjgl:3.3.3:natives-linux").unwrap();
        assert_ne!(n.dedup_key(), MavenCoord::parse("org.lwjgl:lwjgl:3.3.3").unwrap().dedup_key());
    }

    #[test]
    fn join() {
        assert_eq!(join_url("https://a.b/", "/c/d.jar"), "https://a.b/c/d.jar");
        assert_eq!(join_url("https://a.b", "c/d.jar"), "https://a.b/c/d.jar");
    }
}
