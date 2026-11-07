//! Global launcher settings (`settings.json`).

use std::path::{Path, PathBuf};

use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct Settings {
    pub min_memory_mb: u32,
    pub max_memory_mb: u32,
    /// Force a specific Java for every instance (normally automatic).
    pub java_path: Option<PathBuf>,
    pub extra_jvm_args: Vec<String>,
    /// Azure application (client) id used for Microsoft sign-in.
    pub microsoft_client_id: Option<String>,
    pub download_concurrency: usize,
    pub show_snapshots: bool,
    pub show_old_versions: bool,
    /// UI: time of day — `cycle` (a running day/night cycle, default),
    /// `clock` (follows the computer's clock), or a fixed `dawn`, `day`,
    /// `dusk` or `night`.
    pub sky: String,
    /// UI: real minutes per in-game day in `cycle` mode.
    pub day_length_min: u32,
    /// UI: click and launch sounds.
    pub ui_sounds: bool,
    /// UI: background and transition animations.
    pub animations: bool,

    /// Game window size for instances that don't set their own.
    pub game_width: Option<u32>,
    pub game_height: Option<u32>,
    /// Start the game fullscreen.
    pub fullscreen: bool,
    /// What the launcher does once the game is running: `keep`,
    /// `minimize`, or `hide` (it comes back when the game closes).
    pub after_launch: String,
    /// Add the Vortex in-game mods (HUD, PvP tools, your skin and cape) to
    /// Fabric instances they support.
    pub vortex_mods: bool,
    /// Add performance mods (Sodium, Lithium, FerriteCore, EntityCulling,
    /// ImmediatelyFast; ModernFix on Forge) to Fabric and Forge instances,
    /// once each, so the game runs at a higher frame rate.
    pub performance_mods: bool,
    /// Open the game log when a game starts.
    pub open_log_on_launch: bool,

    /// UI: accent colour name (`amber`, `diamond`, `emerald`, `amethyst`,
    /// `redstone`, `copper`).
    pub accent: String,
    /// UI: frosted glass strength, 0 (clear) to 100 (heavy).
    pub glass: u32,
    /// UI: which of the logo designs to show (0-9).
    pub logo_style: u32,
    /// UI: background scene (`meadow`, `cherry`, `peaks`, `desert`, `ocean`,
    /// `jungle`, `village`, `lush`, `nether`, `end`).
    pub background: String,
    /// In-game: Vortex main menu and button style (needs Vortex mods).
    pub vortex_menu: bool,
    /// UI: show the 3D player on the home screen.
    pub show_player: bool,
    /// UI: animals, particles and other moving details in the world.
    pub world_life: bool,
    /// UI: 12-hour clock in the top bar.
    pub clock_12h: bool,

    /// Mods: install beta and alpha versions when they are the newest.
    pub mods_allow_unstable: bool,
    /// Mods: also install the mods a mod requires.
    pub mods_with_dependencies: bool,

    /// Reuse the last launch's resolved files so the game starts at once
    /// (a full check still runs every few days and after a failed start).
    pub fast_launch: bool,
    /// Add tuned garbage-collector and class-sharing flags on Java 17+.
    pub tuned_jvm: bool,

    // ---- Game defaults, written into each instance's options.txt before
    // it starts. None = leave the game's own setting alone.
    pub max_fps: Option<u32>,
    pub render_distance: Option<u32>,
    pub simulation_distance: Option<u32>,
    /// 0 = Auto.
    pub gui_scale: Option<u32>,
    pub vsync: Option<bool>,
    /// Field of view in degrees (30-110).
    pub fov: Option<u32>,
    pub raw_mouse_input: Option<bool>,
    pub narrator_off: bool,
    pub skip_multiplayer_warning: bool,
    pub auto_jump: Option<bool>,
    pub entity_shadows: Option<bool>,
    /// `all`, `decreased` or `minimal`.
    pub particles: Option<String>,
    /// `fancy`, `fast` or `off`.
    pub clouds: Option<String>,
    pub pause_on_lost_focus: Option<bool>,
    /// Skip the "Move with WASD" tutorial pop-ups.
    pub hide_tutorial: bool,
    /// Max FPS mode: draw less for a much higher frame rate (fast graphics,
    /// no clouds or shadows, minimal particles, render distance 8...).
    pub max_fps_mode: bool,

    // ---- Launching.
    /// Garbage collector: `auto`, `g1`, `zgc`, `shenandoah` or `parallel`.
    pub gc: String,
    /// Run before the game starts (in the game folder); the launch waits.
    pub pre_launch_command: String,
    /// Run after the game closes.
    pub post_exit_command: String,
    /// Put in front of the Java command (e.g. `gamemoderun` or `prime-run`).
    pub wrapper_command: String,
    /// Extra environment for the game: `KEY=value`, one per line.
    pub game_env: String,
    /// Zip each instance's worlds before it starts.
    pub backup_worlds: bool,
    /// How many world backups to keep per instance.
    pub backups_keep: u32,
    /// After a crash, say why in plain words.
    pub crash_helper: bool,
    /// Delete game logs and crash reports older than this many days (0 = never).
    pub log_retention_days: u32,
    /// Update mods installed from Modrinth before each launch.
    pub auto_update_mods: bool,
    /// Pressing Play rejoins the server you last joined from the launcher.
    pub rejoin_last_server: bool,

    /// Interface preferences the launcher UI keeps (layout, wallpaper,
    /// glass style…); stored as-is.
    #[serde(flatten)]
    pub ui: serde_json::Map<String, serde_json::Value>,
}

impl Default for Settings {
    fn default() -> Self {
        Self {
            min_memory_mb: 512,
            max_memory_mb: 4096,
            java_path: None,
            extra_jvm_args: Vec::new(),
            microsoft_client_id: None,
            download_concurrency: 32,
            show_snapshots: false,
            show_old_versions: false,
            sky: "cycle".to_string(),
            day_length_min: 10,
            ui_sounds: true,
            animations: true,
            game_width: None,
            game_height: None,
            fullscreen: false,
            after_launch: "keep".to_string(),
            vortex_mods: true,
            performance_mods: true,
            open_log_on_launch: false,
            accent: "amber".to_string(),
            glass: 60,
            logo_style: 0,
            background: "golden".to_string(),
            vortex_menu: true,
            show_player: true,
            world_life: true,
            clock_12h: false,
            mods_allow_unstable: false,
            mods_with_dependencies: true,
            fast_launch: true,
            tuned_jvm: true,
            max_fps: None,
            render_distance: None,
            simulation_distance: None,
            gui_scale: None,
            vsync: None,
            fov: None,
            raw_mouse_input: None,
            narrator_off: true,
            skip_multiplayer_warning: true,
            auto_jump: None,
            entity_shadows: None,
            particles: None,
            clouds: None,
            pause_on_lost_focus: None,
            hide_tutorial: true,
            max_fps_mode: false,
            gc: "auto".to_string(),
            pre_launch_command: String::new(),
            post_exit_command: String::new(),
            wrapper_command: String::new(),
            game_env: String::new(),
            backup_worlds: false,
            backups_keep: 5,
            crash_helper: true,
            log_retention_days: 30,
            auto_update_mods: false,
            rejoin_last_server: false,
            ui: serde_json::Map::new(),
        }
    }
}

impl Settings {
    pub fn load(path: &Path) -> Result<Self> {
        match std::fs::read(path) {
            Ok(bytes) => serde_json::from_slice(&bytes)
                .with_context(|| format!("invalid {}", path.display())),
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => Ok(Self::default()),
            Err(e) => Err(e.into()),
        }
    }

    pub fn save(&self, path: &Path) -> Result<()> {
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        std::fs::write(path, serde_json::to_vec_pretty(self)?)?;
        Ok(())
    }

    /// Default game window size, when both width and height are set.
    pub fn resolution(&self) -> Option<(u32, u32)> {
        match (self.game_width, self.game_height) {
            (Some(w), Some(h)) if w >= 320 && h >= 240 => Some((w, h)),
            _ => None,
        }
    }

    /// Client id from `VORTEX_MS_CLIENT_ID`, falling back to settings.
    pub fn client_id(&self) -> Option<String> {
        std::env::var("VORTEX_MS_CLIENT_ID")
            .ok()
            .filter(|s| !s.trim().is_empty())
            .or_else(|| self.microsoft_client_id.clone())
            .map(|s| s.trim().to_string())
            .filter(|s| !s.is_empty())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn keeps_ui_preferences() {
        let s: Settings = serde_json::from_str(r#"{"accent":"diamond","glass_style":"smoke","wall_blur":24,"home_news":false}"#).unwrap();
        assert_eq!(s.accent, "diamond");
        assert!(s.fast_launch, "new settings default to fast launch");
        assert_eq!(s.ui.get("glass_style").and_then(|v| v.as_str()), Some("smoke"));
        let back: serde_json::Value = serde_json::to_value(&s).unwrap();
        assert_eq!(back["wall_blur"], 24);
        assert_eq!(back["home_news"], false);
        assert_eq!(back["accent"], "diamond");
    }
}
