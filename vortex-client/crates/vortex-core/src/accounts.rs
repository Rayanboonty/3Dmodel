//! Saved accounts (`accounts.json`).
//!
//! Note: like most launchers, refresh tokens are stored in a plain JSON file in
//! the user's profile directory (on Unix it is created with mode 0600).

use std::path::{Path, PathBuf};

use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct MicrosoftTokens {
    pub refresh_token: String,
    pub minecraft_token: String,
    /// Unix seconds.
    pub minecraft_expires_at: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(tag = "type", rename_all = "lowercase")]
pub enum AccountKind {
    Offline,
    Microsoft(MicrosoftTokens),
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct Account {
    /// Profile UUID without dashes.
    pub uuid: String,
    pub username: String,
    pub kind: AccountKind,
}

impl Account {
    pub fn is_microsoft(&self) -> bool {
        matches!(self.kind, AccountKind::Microsoft(_))
    }

    pub fn kind_label(&self) -> &'static str {
        match self.kind {
            AccountKind::Offline => "Offline",
            AccountKind::Microsoft(_) => "Microsoft",
        }
    }

    /// True when the Minecraft token is missing or expires within 5 minutes.
    pub fn needs_refresh(&self) -> bool {
        match &self.kind {
            AccountKind::Offline => false,
            AccountKind::Microsoft(t) => t.minecraft_expires_at <= crate::auth::now_secs() + 300,
        }
    }
}

#[derive(Debug, Default, Serialize, Deserialize)]
pub struct AccountStore {
    #[serde(default)]
    pub accounts: Vec<Account>,
    /// UUID of the selected account.
    #[serde(default)]
    pub selected: Option<String>,
    #[serde(skip)]
    path: PathBuf,
}

impl AccountStore {
    pub fn load(path: &Path) -> Result<Self> {
        let mut store: AccountStore = match std::fs::read(path) {
            Ok(bytes) => serde_json::from_slice(&bytes)
                .with_context(|| format!("invalid {}", path.display()))?,
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => AccountStore::default(),
            Err(e) => return Err(e.into()),
        };
        store.path = path.to_path_buf();
        Ok(store)
    }

    pub fn save(&self) -> Result<()> {
        if let Some(parent) = self.path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let data = serde_json::to_vec_pretty(self)?;
        let tmp = self.path.with_extension("json.tmp");
        std::fs::write(&tmp, data)?;
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            std::fs::set_permissions(&tmp, std::fs::Permissions::from_mode(0o600))?;
        }
        std::fs::rename(&tmp, &self.path)?;
        Ok(())
    }

    /// Add or replace (by UUID) an account and select it.
    pub fn upsert(&mut self, account: Account) {
        self.selected = Some(account.uuid.clone());
        match self.accounts.iter_mut().find(|a| a.uuid == account.uuid) {
            Some(existing) => *existing = account,
            None => self.accounts.push(account),
        }
    }

    pub fn remove(&mut self, uuid: &str) {
        self.accounts.retain(|a| a.uuid != uuid);
        if self.selected.as_deref() == Some(uuid) {
            self.selected = self.accounts.first().map(|a| a.uuid.clone());
        }
    }

    pub fn select(&mut self, uuid: &str) {
        if self.accounts.iter().any(|a| a.uuid == uuid) {
            self.selected = Some(uuid.to_string());
        }
    }

    pub fn selected(&self) -> Option<&Account> {
        let uuid = self.selected.as_deref()?;
        self.accounts.iter().find(|a| a.uuid == uuid)
    }

    pub fn find_by_name(&self, name: &str) -> Option<&Account> {
        self.accounts
            .iter()
            .find(|a| a.username.eq_ignore_ascii_case(name))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn round_trip() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("accounts.json");
        let mut store = AccountStore::load(&path).unwrap();
        store.upsert(crate::auth::offline_account("Steve").unwrap());
        store.upsert(Account {
            uuid: "abc".into(),
            username: "Alex".into(),
            kind: AccountKind::Microsoft(MicrosoftTokens {
                refresh_token: "r".into(),
                minecraft_token: "m".into(),
                minecraft_expires_at: 0,
            }),
        });
        store.save().unwrap();

        let mut loaded = AccountStore::load(&path).unwrap();
        assert_eq!(loaded.accounts.len(), 2);
        assert_eq!(loaded.selected().unwrap().username, "Alex");
        assert!(loaded.selected().unwrap().needs_refresh());
        loaded.remove("abc");
        assert_eq!(loaded.selected().unwrap().username, "Steve");
        assert!(loaded.find_by_name("steve").is_some());
    }
}
