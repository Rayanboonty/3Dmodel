//! Modrinth (<https://modrinth.com>): search for mods, resource packs and
//! shader packs, and install them into an instance together with the mods
//! they require.
//!
//! What was installed from Modrinth is remembered in the instance folder
//! (`modrinth.json`) so the UI can show "Installed" and so a newer version
//! replaces the old file instead of sitting next to it.

use std::collections::{BTreeMap, HashSet, VecDeque};
use std::path::{Path, PathBuf};

use anyhow::{bail, Context, Result};
use serde::{Deserialize, Serialize};

use crate::download::{DownloadTask, Verify};
use crate::instance::{Instance, Loader};
use crate::progress::{report, ProgressFn};
use crate::Launcher;

pub const API: &str = "https://api.modrinth.com/v2";
const INDEX_FILE: &str = "modrinth.json";

/// What kind of project, and so which folder it goes in.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum ProjectKind {
    Mod,
    Resourcepack,
    Shader,
}

impl ProjectKind {
    pub fn parse(s: &str) -> Result<Self> {
        Ok(match s {
            "mod" | "mods" => Self::Mod,
            "resourcepack" | "resourcepacks" => Self::Resourcepack,
            "shader" | "shaders" | "shaderpack" | "shaderpacks" => Self::Shader,
            other => bail!("unknown project type '{other}'"),
        })
    }

    fn api_name(self) -> &'static str {
        match self {
            Self::Mod => "mod",
            Self::Resourcepack => "resourcepack",
            Self::Shader => "shader",
        }
    }

    /// Folder inside the game directory.
    pub fn folder(self) -> &'static str {
        match self {
            Self::Mod => "mods",
            Self::Resourcepack => "resourcepacks",
            Self::Shader => "shaderpacks",
        }
    }

    pub fn dir(self, instance: &Instance) -> PathBuf {
        instance.game_dir().join(self.folder())
    }
}

/// The Modrinth loader name for an instance, if it can run mods.
pub fn loader_name(instance: &Instance) -> Option<&'static str> {
    match instance.loader {
        Loader::Vanilla => None,
        Loader::Fabric { .. } => Some("fabric"),
        Loader::Forge { .. } => Some("forge"),
    }
}

// ------------------------------------------------------------------ search --

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "lowercase")]
pub enum Sort {
    #[default]
    Relevance,
    Downloads,
    Follows,
    Newest,
    Updated,
}

impl Sort {
    pub fn parse(s: &str) -> Self {
        match s {
            "downloads" => Self::Downloads,
            "follows" => Self::Follows,
            "newest" => Self::Newest,
            "updated" => Self::Updated,
            _ => Self::Relevance,
        }
    }

    fn api_name(self) -> &'static str {
        match self {
            Self::Relevance => "relevance",
            Self::Downloads => "downloads",
            Self::Follows => "follows",
            Self::Newest => "newest",
            Self::Updated => "updated",
        }
    }
}

#[derive(Debug, Clone)]
pub struct SearchQuery {
    pub text: String,
    pub kind: ProjectKind,
    /// Only projects with a version for this Minecraft version.
    pub game_version: Option<String>,
    /// Only mods for this loader (`fabric`, `forge`). Ignored for packs.
    pub loader: Option<String>,
    /// Optional category, e.g. `optimization`.
    pub category: Option<String>,
    pub sort: Sort,
    pub offset: u32,
    pub limit: u32,
}

#[derive(Debug, Deserialize)]
struct RawSearch {
    hits: Vec<RawHit>,
    offset: u32,
    total_hits: u32,
}

#[derive(Debug, Deserialize)]
struct RawHit {
    project_id: String,
    slug: String,
    title: String,
    #[serde(default)]
    description: String,
    #[serde(default)]
    author: String,
    #[serde(default)]
    downloads: u64,
    #[serde(default)]
    follows: u64,
    #[serde(default)]
    icon_url: Option<String>,
    #[serde(default)]
    display_categories: Vec<String>,
    #[serde(default)]
    date_modified: String,
    #[serde(default)]
    featured_gallery: Option<String>,
    #[serde(default)]
    color: Option<u32>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SearchHit {
    pub id: String,
    pub slug: String,
    pub title: String,
    pub description: String,
    pub author: String,
    pub downloads: u64,
    pub follows: u64,
    pub icon_url: Option<String>,
    pub categories: Vec<String>,
    /// `YYYY-MM-DD`.
    pub updated: String,
    pub gallery: Option<String>,
    /// Brand colour as `#rrggbb`.
    pub color: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SearchPage {
    pub hits: Vec<SearchHit>,
    pub offset: u32,
    pub total: u32,
}

const LOADER_CATEGORIES: [&str; 29] = [
    "fabric", "forge", "neoforge", "quilt", "liteloader", "modloader", "babric", "rift", "legacy-fabric", "ornithe",
    "bta-babric", "java", "minecraft", "iris", "optifine", "canvas", "vanilla", "bukkit", "paper", "spigot",
    "purpur", "folia", "sponge", "velocity", "bungeecord", "waterfall", "datapack", "nilloader", "risugami",
];

fn facets(q: &SearchQuery) -> String {
    let mut groups: Vec<Vec<String>> = vec![vec![format!("project_type:{}", q.kind.api_name())]];
    if let Some(v) = &q.game_version {
        groups.push(vec![format!("versions:{v}")]);
    }
    if q.kind == ProjectKind::Mod {
        if let Some(l) = &q.loader {
            groups.push(vec![format!("categories:{l}")]);
        }
    }
    if let Some(c) = &q.category {
        groups.push(vec![format!("categories:{c}")]);
    }
    serde_json::to_string(&groups).expect("facets serialise")
}

pub async fn search(launcher: &Launcher, q: &SearchQuery) -> Result<SearchPage> {
    let limit = q.limit.clamp(1, 100).to_string();
    let offset = q.offset.to_string();
    let facets = facets(q);
    let resp = launcher
        .http()
        .get(format!("{API}/search"))
        .query(&[
            ("query", q.text.as_str()),
            ("facets", facets.as_str()),
            ("index", q.sort.api_name()),
            ("offset", offset.as_str()),
            ("limit", limit.as_str()),
        ])
        .send()
        .await
        .context("could not reach Modrinth")?
        .error_for_status()?;
    let raw: RawSearch = resp.json().await.context("unexpected reply from Modrinth")?;
    Ok(SearchPage {
        offset: raw.offset,
        total: raw.total_hits,
        hits: raw
            .hits
            .into_iter()
            .map(|h| SearchHit {
                id: h.project_id,
                slug: h.slug,
                title: h.title,
                description: h.description,
                author: h.author,
                downloads: h.downloads,
                follows: h.follows,
                icon_url: h.icon_url.filter(|u| !u.is_empty()),
                categories: h
                    .display_categories
                    .into_iter()
                    .filter(|c| !LOADER_CATEGORIES.contains(&c.as_str()))
                    .collect(),
                updated: h.date_modified.chars().take(10).collect(),
                gallery: h.featured_gallery.filter(|u| !u.is_empty()),
                color: h.color.map(|c| format!("#{c:06x}")),
            })
            .collect(),
    })
}

// ---------------------------------------------------------------- projects --

#[derive(Debug, Clone, Deserialize)]
struct RawProject {
    id: String,
    slug: String,
    title: String,
    #[serde(default)]
    description: String,
    #[serde(default)]
    project_type: String,
    #[serde(default)]
    downloads: u64,
    #[serde(default)]
    followers: u64,
    #[serde(default)]
    icon_url: Option<String>,
    #[serde(default)]
    categories: Vec<String>,
    #[serde(default)]
    updated: String,
    #[serde(default)]
    source_url: Option<String>,
    #[serde(default)]
    wiki_url: Option<String>,
    #[serde(default)]
    discord_url: Option<String>,
    #[serde(default)]
    gallery: Vec<RawImage>,
    #[serde(default)]
    license: Option<RawLicense>,
}

#[derive(Debug, Clone, Deserialize)]
struct RawImage {
    url: String,
    #[serde(default)]
    title: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
struct RawLicense {
    id: String,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Project {
    pub id: String,
    pub slug: String,
    pub title: String,
    pub description: String,
    pub kind: String,
    pub downloads: u64,
    pub follows: u64,
    pub icon_url: Option<String>,
    pub categories: Vec<String>,
    pub updated: String,
    pub license: Option<String>,
    pub page_url: String,
    pub source_url: Option<String>,
    pub wiki_url: Option<String>,
    pub discord_url: Option<String>,
    pub gallery: Vec<GalleryImage>,
}

#[derive(Debug, Clone, Serialize)]
pub struct GalleryImage {
    pub url: String,
    pub title: Option<String>,
}

fn convert_project(p: RawProject) -> Project {
    let kind = if p.project_type.is_empty() { "mod".to_string() } else { p.project_type };
    Project {
        page_url: format!("https://modrinth.com/{}/{}", kind, p.slug),
        id: p.id,
        slug: p.slug,
        title: p.title,
        description: p.description,
        kind,
        downloads: p.downloads,
        follows: p.followers,
        icon_url: p.icon_url.filter(|u| !u.is_empty()),
        categories: p.categories,
        updated: p.updated.chars().take(10).collect(),
        license: p.license.map(|l| l.id).filter(|l| !l.is_empty()),
        source_url: p.source_url,
        wiki_url: p.wiki_url,
        discord_url: p.discord_url,
        gallery: p.gallery.into_iter().take(8).map(|g| GalleryImage { url: g.url, title: g.title }).collect(),
    }
}

/// Validates a Modrinth id or slug before it goes into a URL path.
fn checked_id(id: &str) -> Result<&str> {
    let ok = !id.is_empty()
        && id.len() <= 64
        && id.chars().all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_');
    if !ok {
        bail!("invalid Modrinth id '{id}'");
    }
    Ok(id)
}

pub async fn project(launcher: &Launcher, id: &str) -> Result<Project> {
    let url = format!("{API}/project/{}", checked_id(id)?);
    let raw: RawProject = launcher.downloader().get_json(&url).await?;
    Ok(convert_project(raw))
}

// ---------------------------------------------------------------- versions --

#[derive(Debug, Clone, Deserialize)]
struct RawVersion {
    id: String,
    project_id: String,
    #[serde(default)]
    name: String,
    #[serde(default)]
    version_number: String,
    #[serde(default)]
    version_type: String,
    #[serde(default)]
    game_versions: Vec<String>,
    #[serde(default)]
    loaders: Vec<String>,
    #[serde(default)]
    date_published: String,
    #[serde(default)]
    downloads: u64,
    #[serde(default)]
    files: Vec<RawFile>,
    #[serde(default)]
    dependencies: Vec<RawDependency>,
}

#[derive(Debug, Clone, Deserialize)]
struct RawFile {
    url: String,
    filename: String,
    #[serde(default)]
    primary: bool,
    #[serde(default)]
    size: Option<u64>,
    #[serde(default)]
    hashes: BTreeMap<String, String>,
}

#[derive(Debug, Clone, Deserialize)]
struct RawDependency {
    #[serde(default)]
    version_id: Option<String>,
    #[serde(default)]
    project_id: Option<String>,
    #[serde(default)]
    dependency_type: String,
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct VersionInfo {
    pub id: String,
    pub name: String,
    pub number: String,
    /// `release`, `beta` or `alpha`.
    pub kind: String,
    pub game_versions: Vec<String>,
    pub loaders: Vec<String>,
    pub date: String,
    pub downloads: u64,
    pub file: Option<String>,
    pub size: Option<u64>,
}

impl RawVersion {
    fn primary_file(&self) -> Option<&RawFile> {
        self.files.iter().find(|f| f.primary).or_else(|| self.files.first())
    }

    fn info(&self) -> VersionInfo {
        let f = self.primary_file();
        VersionInfo {
            id: self.id.clone(),
            name: self.name.clone(),
            number: self.version_number.clone(),
            kind: self.version_type.clone(),
            game_versions: self.game_versions.clone(),
            loaders: self.loaders.clone(),
            date: self.date_published.chars().take(10).collect(),
            downloads: self.downloads,
            file: f.map(|f| f.filename.clone()),
            size: f.and_then(|f| f.size),
        }
    }
}

/// Filters used to find versions that work in an instance.
#[derive(Debug, Clone)]
pub struct Target {
    pub kind: ProjectKind,
    pub game_version: String,
    pub loader: Option<String>,
}

impl Target {
    pub fn for_instance(instance: &Instance, kind: ProjectKind) -> Self {
        Self {
            kind,
            game_version: instance.minecraft_version.clone(),
            loader: loader_name(instance).map(String::from),
        }
    }
}

async fn raw_versions(launcher: &Launcher, project_id: &str, target: &Target) -> Result<Vec<RawVersion>> {
    let game = serde_json::to_string(&[&target.game_version])?;
    let mut req = launcher
        .http()
        .get(format!("{API}/project/{}/version", checked_id(project_id)?))
        .query(&[("game_versions", game.as_str())]);
    let loaders;
    if target.kind == ProjectKind::Mod {
        if let Some(l) = &target.loader {
            loaders = serde_json::to_string(&[l])?;
            req = req.query(&[("loaders", loaders.as_str())]);
        }
    }
    let resp = req.send().await.context("could not reach Modrinth")?.error_for_status()?;
    let mut list: Vec<RawVersion> = resp.json().await.context("unexpected reply from Modrinth")?;
    list.sort_by(|a, b| b.date_published.cmp(&a.date_published));
    Ok(list)
}

/// Versions of a project that work in the target, newest first.
pub async fn versions(launcher: &Launcher, project_id: &str, target: &Target) -> Result<Vec<VersionInfo>> {
    Ok(raw_versions(launcher, project_id, target).await?.iter().map(RawVersion::info).collect())
}

/// Newest release, or the newest version of any kind when there are no
/// releases (or when `allow_unstable` is set).
fn pick(list: &[RawVersion], allow_unstable: bool) -> Option<&RawVersion> {
    let usable = |v: &&RawVersion| v.primary_file().is_some();
    if allow_unstable {
        return list.iter().find(usable);
    }
    list.iter()
        .filter(usable)
        .find(|v| v.version_type == "release")
        .or_else(|| list.iter().find(usable))
}

// ------------------------------------------------------------------- index --

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct Index {
    /// Keyed by `<folder>/<file name>` (without `.disabled`).
    #[serde(default)]
    pub files: BTreeMap<String, IndexEntry>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct IndexEntry {
    pub project_id: String,
    pub version_id: String,
    pub title: String,
    #[serde(default)]
    pub icon_url: Option<String>,
    pub kind: ProjectKind,
}

fn index_path(instance: &Instance) -> PathBuf {
    instance.dir.join(INDEX_FILE)
}

fn key(kind: ProjectKind, file: &str) -> String {
    format!("{}/{}", kind.folder(), file)
}

fn present(dir: &Path, file: &str) -> bool {
    dir.join(file).is_file() || dir.join(format!("{file}.disabled")).is_file()
}

impl Index {
    pub fn load(instance: &Instance) -> Self {
        std::fs::read(index_path(instance))
            .ok()
            .and_then(|b| serde_json::from_slice(&b).ok())
            .unwrap_or_default()
    }

    fn save(&self, instance: &Instance) -> Result<()> {
        std::fs::create_dir_all(&instance.dir)?;
        std::fs::write(index_path(instance), serde_json::to_vec_pretty(self)?)?;
        Ok(())
    }

    /// Drop entries whose file was deleted outside the launcher.
    fn prune(&mut self, instance: &Instance) {
        self.files.retain(|k, e| {
            let file = k.split_once('/').map(|(_, f)| f).unwrap_or(k);
            present(&e.kind.dir(instance), file)
        });
    }

    fn file_of(&self, kind: ProjectKind, project_id: &str) -> Option<String> {
        self.files
            .iter()
            .find(|(_, e)| e.kind == kind && e.project_id == project_id)
            .map(|(k, _)| k.split_once('/').map(|(_, f)| f).unwrap_or(k).to_string())
    }
}

/// Modrinth entries that are still on disk, keyed by `<folder>/<file>`.
pub fn installed(instance: &Instance) -> Index {
    let mut index = Index::load(instance);
    index.prune(instance);
    index
}

// ----------------------------------------------------------------- install --

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct InstallResult {
    /// Titles of everything that was added or updated, the requested
    /// project first.
    pub installed: Vec<String>,
    /// Titles of required dependencies that were already installed.
    pub already: Vec<String>,
}

struct Planned {
    kind: ProjectKind,
    version: RawVersion,
}

/// Install a project (a specific version, or the best match) and, for
/// mods, every required dependency.
#[allow(clippy::too_many_arguments)]
pub async fn install(
    launcher: &Launcher,
    instance: &Instance,
    kind: ProjectKind,
    project_id: &str,
    version_id: Option<&str>,
    allow_unstable: bool,
    with_dependencies: bool,
    progress: &ProgressFn,
) -> Result<InstallResult> {
    if kind == ProjectKind::Mod && loader_name(instance).is_none() {
        bail!("'{}' is a vanilla instance. Mods need Fabric or Forge", instance.name);
    }
    let mut index = installed(instance);
    let mut plan: Vec<Planned> = Vec::new();
    let mut seen: HashSet<String> = HashSet::new();
    let mut already_ids: Vec<String> = Vec::new();
    // (project id, exact version id, is the requested project)
    let mut queue: VecDeque<(String, Option<String>, bool)> = VecDeque::new();
    queue.push_back((project_id.to_string(), version_id.map(String::from), true));

    report(progress, "Finding versions", 0, 0);
    while let Some((pid, vid, requested)) = queue.pop_front() {
        if !seen.insert(pid.clone()) {
            continue;
        }
        let this_kind = if requested { kind } else { ProjectKind::Mod };
        if !requested && index.file_of(this_kind, &pid).is_some() {
            already_ids.push(pid);
            continue;
        }
        let target = Target::for_instance(instance, this_kind);
        let version = match vid {
            Some(v) => {
                let url = format!("{API}/version/{}", checked_id(&v)?);
                launcher.downloader().get_json::<RawVersion>(&url).await?
            }
            None => {
                let list = raw_versions(launcher, &pid, &target).await?;
                match pick(&list, allow_unstable) {
                    Some(v) => v.clone(),
                    None if requested => bail!(
                        "no version of this project works with Minecraft {}{}",
                        target.game_version,
                        target.loader.as_deref().map(|l| format!(" ({l})")).unwrap_or_default()
                    ),
                    // An optional-looking failure for a dependency shouldn't
                    // block the main install; Minecraft will say what's missing.
                    None => continue,
                }
            }
        };
        if version.project_id != pid && requested {
            bail!("that version belongs to a different project");
        }
        if with_dependencies && this_kind == ProjectKind::Mod {
            for dep in version.dependencies.iter().filter(|d| d.dependency_type == "required") {
                match (&dep.project_id, &dep.version_id) {
                    (Some(p), v) => queue.push_back((p.clone(), v.clone(), false)),
                    (None, Some(v)) => {
                        let url = format!("{API}/version/{}", checked_id(v)?);
                        if let Ok(dv) = launcher.downloader().get_json::<RawVersion>(&url).await {
                            queue.push_back((dv.project_id.clone(), Some(dv.id.clone()), false));
                        }
                    }
                    (None, None) => {}
                }
            }
        }
        plan.push(Planned { kind: this_kind, version });
    }

    // Titles and icons for everything involved, in one request.
    let mut ids: Vec<String> = plan.iter().map(|p| p.version.project_id.clone()).collect();
    ids.extend(already_ids.iter().cloned());
    let projects: Vec<RawProject> = if ids.is_empty() {
        Vec::new()
    } else {
        let list = serde_json::to_string(&ids)?;
        launcher
            .http()
            .get(format!("{API}/projects"))
            .query(&[("ids", list.as_str())])
            .send()
            .await?
            .error_for_status()?
            .json()
            .await
            .unwrap_or_default()
    };
    let title_of = |id: &str| {
        projects
            .iter()
            .find(|p| p.id == id)
            .map(|p| p.title.clone())
            .unwrap_or_else(|| id.to_string())
    };
    let icon_of = |id: &str| projects.iter().find(|p| p.id == id).and_then(|p| p.icon_url.clone());

    let mut tasks = Vec::new();
    for p in &plan {
        let file = p.version.primary_file().context("this version has no files")?;
        if file.filename.contains(['/', '\\']) || file.filename.starts_with('.') {
            bail!("refusing odd file name '{}'", file.filename);
        }
        let dir = p.kind.dir(instance);
        tasks.push(
            DownloadTask::new(file.url.clone(), dir.join(&file.filename))
                .sha1(file.hashes.get("sha1").cloned())
                .size(file.size),
        );
    }
    launcher
        .downloader()
        .run(tasks, "Downloading", Verify::Hash, progress)
        .await?;

    // Record what's installed and remove files this replaced.
    let mut installed_titles = Vec::new();
    for p in &plan {
        let file = p.version.primary_file().expect("checked above");
        let dir = p.kind.dir(instance);
        if let Some(old) = index.file_of(p.kind, &p.version.project_id) {
            if old != file.filename {
                let _ = std::fs::remove_file(dir.join(&old));
                let _ = std::fs::remove_file(dir.join(format!("{old}.disabled")));
                index.files.remove(&key(p.kind, &old));
            }
        }
        // A disabled copy of the same file would otherwise load twice.
        let _ = std::fs::remove_file(dir.join(format!("{}.disabled", file.filename)));
        index.files.insert(
            key(p.kind, &file.filename),
            IndexEntry {
                project_id: p.version.project_id.clone(),
                version_id: p.version.id.clone(),
                title: title_of(&p.version.project_id),
                icon_url: icon_of(&p.version.project_id),
                kind: p.kind,
            },
        );
        installed_titles.push(title_of(&p.version.project_id));
    }
    index.save(instance)?;
    report(progress, "Installed", 1, 1);
    Ok(InstallResult {
        installed: installed_titles,
        already: already_ids.iter().map(|id| title_of(id)).collect(),
    })
}

/// Update everything installed from Modrinth to its newest version for the
/// instance (mods you turned off are left as they are). Returns the titles
/// that were updated.
pub async fn update_all(launcher: &Launcher, instance: &Instance, allow_unstable: bool, progress: &ProgressFn) -> Result<Vec<String>> {
    let index = installed(instance);
    let mut updated = Vec::new();
    for (k, e) in &index.files {
        let file = k.split_once('/').map(|(_, f)| f).unwrap_or(k);
        // Turned off on the Mods page: leave it.
        if !e.kind.dir(instance).join(file).is_file() {
            continue;
        }
        let target = Target::for_instance(instance, e.kind);
        let Ok(list) = raw_versions(launcher, &e.project_id, &target).await else { continue };
        let Some(best) = pick(&list, allow_unstable) else { continue };
        if best.id == e.version_id {
            continue;
        }
        if install(launcher, instance, e.kind, &e.project_id, Some(&best.id), allow_unstable, false, progress).await.is_ok() {
            updated.push(e.title.clone());
        }
    }
    Ok(updated)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn version(id: &str, kind: &str, date: &str, files: bool) -> RawVersion {
        RawVersion {
            id: id.into(),
            project_id: "p".into(),
            name: id.into(),
            version_number: id.into(),
            version_type: kind.into(),
            game_versions: vec![],
            loaders: vec![],
            date_published: date.into(),
            downloads: 0,
            files: if files {
                vec![RawFile { url: "u".into(), filename: format!("{id}.jar"), primary: true, size: None, hashes: BTreeMap::new() }]
            } else {
                vec![]
            },
            dependencies: vec![],
        }
    }

    #[test]
    fn picks_newest_release_then_falls_back() {
        let list = vec![
            version("beta2", "beta", "2026-03", true),
            version("rel1", "release", "2026-02", true),
            version("rel0", "release", "2026-01", true),
        ];
        assert_eq!(pick(&list, false).unwrap().id, "rel1");
        assert_eq!(pick(&list, true).unwrap().id, "beta2");
        let betas = vec![version("b", "beta", "2026", true), version("nofile", "release", "2025", false)];
        assert_eq!(pick(&betas, false).unwrap().id, "b");
        assert!(pick(&[], false).is_none());
    }

    #[test]
    fn builds_facets() {
        let q = SearchQuery {
            text: String::new(),
            kind: ProjectKind::Mod,
            game_version: Some("1.21.4".into()),
            loader: Some("fabric".into()),
            category: None,
            sort: Sort::Downloads,
            offset: 0,
            limit: 20,
        };
        assert_eq!(facets(&q), r#"[["project_type:mod"],["versions:1.21.4"],["categories:fabric"]]"#);
        let packs = SearchQuery { kind: ProjectKind::Resourcepack, ..q };
        assert_eq!(facets(&packs), r#"[["project_type:resourcepack"],["versions:1.21.4"]]"#);
    }

    #[test]
    fn rejects_bad_ids() {
        assert!(checked_id("AANobbMI").is_ok());
        assert!(checked_id("sodium-extra").is_ok());
        assert!(checked_id("../x").is_err());
        assert!(checked_id("").is_err());
    }

    #[test]
    fn index_tracks_files_on_disk() {
        let dir = tempfile::tempdir().unwrap();
        let mut inst = Instance::new("Test", "1.21.4", Loader::Fabric { version: None });
        inst.dir = dir.path().to_path_buf();
        let mods = ProjectKind::Mod.dir(&inst);
        std::fs::create_dir_all(&mods).unwrap();
        std::fs::write(mods.join("a.jar.disabled"), b"x").unwrap();
        let mut index = Index::default();
        for f in ["a.jar", "gone.jar"] {
            index.files.insert(
                key(ProjectKind::Mod, f),
                IndexEntry { project_id: f.into(), version_id: "v".into(), title: f.into(), icon_url: None, kind: ProjectKind::Mod },
            );
        }
        index.save(&inst).unwrap();
        let live = installed(&inst);
        assert_eq!(live.files.len(), 1);
        assert_eq!(live.file_of(ProjectKind::Mod, "a.jar").as_deref(), Some("a.jar"));
    }
}
