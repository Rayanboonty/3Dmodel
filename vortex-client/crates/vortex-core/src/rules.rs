//! Evaluation of the `rules` arrays found on libraries and arguments.
//!
//! Semantics (same as Mojang's launcher): with no rules, the item is allowed.
//! Otherwise start at "disallowed" and walk the rules in order; every rule
//! that *applies* to the current environment sets the outcome to its action.

use std::collections::{BTreeMap, HashSet};

use serde::{Deserialize, Serialize};

use crate::os;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum RuleAction {
    Allow,
    Disallow,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct OsRule {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub name: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub version: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub arch: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Rule {
    pub action: RuleAction,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub os: Option<OsRule>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub features: Option<BTreeMap<String, bool>>,
}

/// Everything a rule can test.
#[derive(Debug, Clone)]
pub struct Environment {
    pub os_name: String,
    pub os_version: String,
    pub os_arch: String,
    /// Enabled launcher features, e.g. `has_custom_resolution`.
    pub features: HashSet<String>,
}

impl Environment {
    pub fn current() -> Self {
        Self {
            os_name: os::name().to_string(),
            os_version: os::version(),
            os_arch: os::arch().to_string(),
            features: HashSet::new(),
        }
    }

    pub fn enable(&mut self, feature: &str) {
        self.features.insert(feature.to_string());
    }
}

fn normalize_arch(a: &str) -> &str {
    match a {
        "amd64" | "x64" => "x86_64",
        "aarch64" => "arm64",
        "i386" | "i686" => "x86",
        other => other,
    }
}

impl Rule {
    /// Does this rule's condition match the environment?
    pub fn applies(&self, env: &Environment) -> bool {
        if let Some(os) = &self.os {
            if let Some(name) = &os.name {
                if name != &env.os_name {
                    return false;
                }
            }
            if let Some(arch) = &os.arch {
                if normalize_arch(arch) != normalize_arch(&env.os_arch) {
                    return false;
                }
            }
            if let Some(version) = &os.version {
                match regex::Regex::new(version) {
                    Ok(re) if re.is_match(&env.os_version) => {}
                    _ => return false,
                }
            }
        }
        if let Some(features) = &self.features {
            for (feature, wanted) in features {
                if env.features.contains(feature) != *wanted {
                    return false;
                }
            }
        }
        true
    }
}

pub fn allowed(rules: &[Rule], env: &Environment) -> bool {
    if rules.is_empty() {
        return true;
    }
    let mut result = false;
    for rule in rules {
        if rule.applies(env) {
            result = rule.action == RuleAction::Allow;
        }
    }
    result
}

#[cfg(test)]
mod tests {
    use super::*;

    fn env(os: &str, arch: &str, version: &str) -> Environment {
        Environment {
            os_name: os.into(),
            os_version: version.into(),
            os_arch: arch.into(),
            features: HashSet::new(),
        }
    }

    fn rules(json: &str) -> Vec<Rule> {
        serde_json::from_str(json).unwrap()
    }

    #[test]
    fn empty_rules_allow() {
        assert!(allowed(&[], &env("windows", "x86_64", "10.0")));
    }

    #[test]
    fn allow_all_but_osx() {
        let r = rules(r#"[{"action":"allow"},{"action":"disallow","os":{"name":"osx"}}]"#);
        assert!(allowed(&r, &env("windows", "x86_64", "10.0")));
        assert!(allowed(&r, &env("linux", "x86_64", "")));
        assert!(!allowed(&r, &env("osx", "arm64", "14.0")));
    }

    #[test]
    fn only_windows() {
        let r = rules(r#"[{"action":"allow","os":{"name":"windows"}}]"#);
        assert!(allowed(&r, &env("windows", "x86_64", "10.0")));
        assert!(!allowed(&r, &env("linux", "x86_64", "")));
    }

    #[test]
    fn version_regex() {
        let r = rules(r#"[{"action":"allow","os":{"name":"windows","version":"^10\\."}}]"#);
        assert!(allowed(&r, &env("windows", "x86_64", "10.0")));
        assert!(!allowed(&r, &env("windows", "x86_64", "6.1")));
        // Old LWJGL on OS X 10.5 only.
        let r = rules(
            r#"[{"action":"allow"},{"action":"disallow","os":{"name":"osx","version":"^10\\.5\\.\\d$"}}]"#,
        );
        assert!(allowed(&r, &env("osx", "x86_64", "14.2")));
        assert!(!allowed(&r, &env("osx", "x86_64", "10.5.8")));
    }

    #[test]
    fn arch_x86_is_32_bit_only() {
        let r = rules(r#"[{"action":"allow","os":{"arch":"x86"}}]"#);
        assert!(!allowed(&r, &env("windows", "x86_64", "10.0")));
        assert!(allowed(&r, &env("windows", "x86", "10.0")));
    }

    #[test]
    fn features() {
        let r = rules(r#"[{"action":"allow","features":{"has_custom_resolution":true}}]"#);
        let mut e = env("windows", "x86_64", "10.0");
        assert!(!allowed(&r, &e));
        e.enable("has_custom_resolution");
        assert!(allowed(&r, &e));
    }
}
