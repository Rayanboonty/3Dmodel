//! Vortex Client — test GUI.
//!
//! This is intentionally a plain, functional UI for exercising the launcher
//! core. All real work happens in `vortex-core`; this crate only renders state
//! and forwards button presses, so it can be replaced wholesale later.

// No console window behind the GUI on Windows release builds.
#![cfg_attr(all(windows, not(debug_assertions)), windows_subsystem = "windows")]

mod app;

use std::sync::Arc;

use eframe::egui;

fn main() -> eframe::Result {
    let options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default()
            .with_title("Vortex Client")
            .with_app_id("vortex-client")
            .with_inner_size([1100.0, 720.0])
            .with_min_inner_size([820.0, 520.0])
            .with_icon(Arc::new(icon())),
        ..Default::default()
    };
    eframe::run_native(
        "Vortex Client",
        options,
        Box::new(|cc| Ok(Box::new(app::VortexApp::new(cc)))),
    )
}

/// Procedural 64×64 "vortex" icon so we don't need binary assets yet.
fn icon() -> egui::IconData {
    const N: u32 = 64;
    let mut rgba = Vec::with_capacity((N * N * 4) as usize);
    let c = (N as f32 - 1.0) / 2.0;
    for y in 0..N {
        for x in 0..N {
            let (dx, dy) = (x as f32 - c, y as f32 - c);
            let r = (dx * dx + dy * dy).sqrt() / c;
            let theta = dy.atan2(dx);
            // Spiral arms: brightness depends on angle + radius.
            let arm = ((theta * 3.0 + r * 9.0).sin() * 0.5 + 0.5).powf(2.0);
            let (red, green, blue, alpha) = if r > 1.0 {
                (0, 0, 0, 0)
            } else {
                let glow = (1.0 - r).powf(0.6);
                let v = (arm * 0.75 + glow * 0.45).min(1.0);
                (
                    (120.0 + 120.0 * v) as u8,
                    (40.0 + 90.0 * v * glow) as u8,
                    (200.0 + 55.0 * v) as u8,
                    (255.0 * (1.0 - r).min(0.08) / 0.08) as u8,
                )
            };
            rgba.extend_from_slice(&[red, green, blue, alpha]);
        }
    }
    egui::IconData {
        rgba,
        width: N,
        height: N,
    }
}
