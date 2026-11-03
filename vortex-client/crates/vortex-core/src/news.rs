//! Mojang's Java Edition patch notes feed (the official launcher's "News").

use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};

use crate::Launcher;

pub const PATCH_NOTES_URL: &str = "https://launchercontent.mojang.com/v2/javaPatchNotes.json";
const CONTENT_BASE: &str = "https://launchercontent.mojang.com";

#[derive(Debug, Deserialize)]
struct Feed {
    entries: Vec<RawEntry>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct RawEntry {
    title: String,
    version: String,
    #[serde(rename = "type", default)]
    kind: String,
    #[serde(default)]
    image: Option<RawImage>,
    #[serde(default)]
    date: String,
    #[serde(default)]
    short_text: String,
}

#[derive(Debug, Deserialize)]
struct RawImage {
    url: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct NewsEntry {
    pub title: String,
    pub version: String,
    /// `release` or `snapshot`.
    pub kind: String,
    pub image_url: Option<String>,
    /// `YYYY-MM-DD`.
    pub date: String,
    pub summary: String,
}

fn convert(raw: RawEntry) -> NewsEntry {
    NewsEntry {
        image_url: raw.image.map(|i| {
            if i.url.starts_with("http") {
                i.url
            } else {
                format!("{CONTENT_BASE}{}", i.url)
            }
        }),
        date: raw.date.get(..10).unwrap_or(&raw.date).to_string(),
        title: raw.title,
        version: raw.version,
        kind: raw.kind,
        summary: raw.short_text,
    }
}

/// Newest patch notes first (cached for offline use).
pub async fn patch_notes(launcher: &Launcher, limit: usize) -> Result<Vec<NewsEntry>> {
    let bytes = launcher
        .downloader()
        .get_cached(PATCH_NOTES_URL, &launcher.paths().cache().join("java-patch-notes.json"))
        .await?;
    let feed: Feed = serde_json::from_slice(&bytes).context("invalid patch notes feed")?;
    Ok(feed.entries.into_iter().take(limit).map(convert).collect())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn converts_entries() {
        let feed: Feed = serde_json::from_str(
            r#"{"version":1,"entries":[{"title":"Minecraft 26.3","version":"26.3","type":"release",
              "image":{"title":"x","url":"/v2/images/a.jpg"},"contentPath":"p","id":"26-3",
              "date":"2026-09-15T10:00:00.000Z","shortText":"A cat."}]}"#,
        )
        .unwrap();
        let e = convert(feed.entries.into_iter().next().unwrap());
        assert_eq!(e.image_url.as_deref(), Some("https://launchercontent.mojang.com/v2/images/a.jpg"));
        assert_eq!(e.date, "2026-09-15");
        assert_eq!(e.kind, "release");
    }
}
