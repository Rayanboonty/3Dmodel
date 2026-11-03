//! # vortex-core
//!
//! Everything the Vortex Client needs to install and launch **any** Minecraft:
//! Java Edition version published by Mojang — from `rd-132211` (2009) through
//! the latest snapshot — plus the Fabric and Forge mod loaders.
//!
//! This crate is deliberately UI-agnostic: the test GUI (`vortex-app`) and the
//! command line tool (`vortex-cli`) are thin layers on top of [`Launcher`].
//! A future UI (egui, Tauri, …) only needs to talk to this crate.
//!
//! Rough flow:
//!
//! 1. [`Launcher::manifest`] — list every version Mojang has ever shipped.
//! 2. [`Launcher::install_version`] — download the version JSON, client jar,
//!    libraries, natives, assets and log config.
//! 3. [`Launcher::java_for`] — download the exact Java runtime Mojang ships
//!    for that version (Java 8 for old versions, 17/21/25 for new ones).
//! 4. [`launch::build_command`] + [`launch::spawn`] — start the game.
//!
//! [`Launcher::prepare_launch`] does 1–4 for an [`instance::Instance`].

pub mod accounts;
pub mod assets;
pub mod auth;
pub mod download;
pub mod fabric;
pub mod forge;
pub mod install;
pub mod instance;
pub mod java;
pub mod launch;
pub mod loaders;
pub mod logs;
pub mod manifest;
pub mod maven;
pub mod mods;
pub mod news;
pub mod os;
pub mod paths;
pub mod progress;
pub mod rules;
pub mod settings;
pub mod version;

mod launcher;

pub use launcher::Launcher;
// Re-exported so front-ends use the same versions as the core.
pub use reqwest;
pub use zip;

/// Value of `${launcher_name}` / `-Dminecraft.launcher.brand`.
pub const LAUNCHER_NAME: &str = "vortex";
/// Value of `${launcher_version}`.
pub const LAUNCHER_VERSION: &str = env!("CARGO_PKG_VERSION");
/// User agent for every HTTP request we make.
pub const USER_AGENT: &str = concat!("VortexClient/", env!("CARGO_PKG_VERSION"));
