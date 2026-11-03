//! One listing API for every mod loader, for UIs.

use anyhow::Result;
use serde::{Deserialize, Serialize};

use crate::{fabric, forge, Launcher};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum LoaderKind {
    Vanilla,
    Fabric,
    Forge,
}

#[derive(Debug, Clone, Serialize)]
pub struct LoaderVersion {
    /// Pass this back when creating an instance.
    pub id: String,
    pub display: String,
    pub stable: bool,
    pub recommended: bool,
    pub latest: bool,
}

/// Loader builds available for a Minecraft version, newest first.
/// Empty when the loader doesn't support that version.
pub async fn versions(launcher: &Launcher, kind: LoaderKind, game: &str) -> Result<Vec<LoaderVersion>> {
    Ok(match kind {
        LoaderKind::Vanilla => Vec::new(),
        LoaderKind::Fabric => {
            let list = fabric::loader_versions(launcher.http(), game).await?;
            let first_stable = list.iter().position(|l| l.stable);
            list.into_iter()
                .enumerate()
                .map(|(i, l)| LoaderVersion {
                    recommended: Some(i) == first_stable,
                    latest: i == 0,
                    stable: l.stable,
                    display: l.version.clone(),
                    id: l.version,
                })
                .collect()
        }
        LoaderKind::Forge => forge::versions(launcher, game)
            .await?
            .into_iter()
            .map(|f| LoaderVersion {
                id: f.display.clone(),
                display: f.display,
                stable: true,
                recommended: f.recommended,
                latest: f.latest,
            })
            .collect(),
    })
}
