//! Installing the launcher as a Windows app.
//!
//! Vortex ships as one `Vortex.exe`. The first time it's opened from
//! anywhere else (Downloads, the Desktop), it offers to install itself: it
//! copies itself to `%LOCALAPPDATA%\Programs\Vortex Client` (no admin
//! rights needed, and updates can replace it there), adds Desktop and Start
//! menu shortcuts and an entry in Settings > Apps (with Uninstall), then
//! restarts from there. Instances, accounts and settings live elsewhere
//! (`%APPDATA%\VortexClient`) and are kept when it's uninstalled.

#![cfg_attr(not(windows), allow(dead_code))]

use std::path::{Path, PathBuf};

use anyhow::{anyhow, Result};
use serde::Serialize;

/// The uninstall entry under HKCU\...\Uninstall and the Run value name.
pub const APP_KEY: &str = "VortexClient";
pub const APP_NAME: &str = "Vortex Client";
const EXE: &str = "Vortex.exe";

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct InstallStatus {
    /// This platform can install (Windows).
    pub supported: bool,
    /// The running launcher is the installed copy.
    pub installed: bool,
    /// Another copy is installed (this one runs from somewhere else).
    pub elsewhere: bool,
    pub install_dir: String,
    pub running_from: String,
    pub autostart: bool,
}

/// Where the launcher installs: %LOCALAPPDATA%\Programs\Vortex Client.
pub fn install_dir() -> Option<PathBuf> {
    dirs::data_local_dir().map(|d| d.join("Programs").join(APP_NAME))
}

pub fn status() -> InstallStatus {
    let exe = std::env::current_exe().unwrap_or_default();
    let dir = install_dir().unwrap_or_default();
    let installed = same_dir(&exe, &dir);
    InstallStatus {
        supported: cfg!(windows),
        installed,
        elsewhere: !installed && dir.join(EXE).is_file(),
        install_dir: dir.display().to_string(),
        running_from: exe.display().to_string(),
        autostart: autostart_enabled(),
    }
}

/// The folder part of a Windows path (works on any OS, for the tests).
fn parent_of(path: &str) -> String {
    path.replace('/', "\\").rsplit_once('\\').map(|(p, _)| p.to_string()).unwrap_or_default()
}

fn same_dir(exe: &Path, dir: &Path) -> bool {
    let parent = parent_of(&exe.to_string_lossy()).to_lowercase();
    let dir = dir.to_string_lossy().replace('/', "\\").to_lowercase().trim_end_matches('\\').to_string();
    !parent.is_empty() && parent == dir
}

/// PowerShell's single-quoted string: ' doubles.
fn ps_quote(s: &str) -> String {
    format!("'{}'", s.replace('\'', "''"))
}

/// The PowerShell that makes a shortcut in a known folder (Desktop,
/// Programs = the Start menu), wherever Windows keeps it (OneDrive too).
fn shortcut_script(folder: &str, target: &Path) -> String {
    let target = target.display().to_string();
    let dir = parent_of(&target);
    format!(
        "$d=[Environment]::GetFolderPath({folder}); $s=(New-Object -ComObject WScript.Shell).CreateShortcut((Join-Path $d {name})); \
         $s.TargetPath={t}; $s.WorkingDirectory={w}; $s.IconLocation={i}; $s.Description={desc}; $s.Save()",
        folder = ps_quote(folder),
        name = ps_quote(&format!("{APP_NAME}.lnk")),
        t = ps_quote(&target),
        w = ps_quote(&dir),
        i = ps_quote(&format!("{target},0")),
        desc = ps_quote("Minecraft launcher"),
    )
}

fn remove_shortcut_script(folder: &str) -> String {
    format!(
        "$p=Join-Path ([Environment]::GetFolderPath({})) {}; if (Test-Path $p) {{ Remove-Item -Force $p }}",
        ps_quote(folder),
        ps_quote(&format!("{APP_NAME}.lnk"))
    )
}

/// The registry values of the Settings > Apps entry.
fn uninstall_values(exe: &Path, version: &str, size_kb: u64) -> Vec<(&'static str, &'static str, String)> {
    let exe_s = exe.display().to_string();
    let dir = parent_of(&exe_s);
    vec![
        ("DisplayName", "REG_SZ", APP_NAME.to_string()),
        ("DisplayVersion", "REG_SZ", version.to_string()),
        ("Publisher", "REG_SZ", APP_NAME.to_string()),
        ("DisplayIcon", "REG_SZ", format!("{exe_s},0")),
        ("InstallLocation", "REG_SZ", dir),
        ("UninstallString", "REG_SZ", format!("\"{exe_s}\" --uninstall")),
        ("NoModify", "REG_DWORD", "1".into()),
        ("NoRepair", "REG_DWORD", "1".into()),
        ("EstimatedSize", "REG_DWORD", size_kb.to_string()),
    ]
}

#[cfg(windows)]
mod sys {
    use std::os::windows::process::CommandExt;
    use std::process::Command;

    const NO_WINDOW: u32 = 0x0800_0000;

    pub fn powershell(script: &str) -> anyhow::Result<()> {
        let out = Command::new("powershell")
            .args(["-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", script])
            .creation_flags(NO_WINDOW)
            .output()?;
        if !out.status.success() {
            anyhow::bail!("{}", String::from_utf8_lossy(&out.stderr).trim());
        }
        Ok(())
    }

    pub fn reg(args: &[&str]) -> anyhow::Result<bool> {
        let out = Command::new("reg").args(args).creation_flags(NO_WINDOW).output()?;
        Ok(out.status.success())
    }

    pub fn detached(program: &std::path::Path, args: &[&str]) -> anyhow::Result<()> {
        Command::new(program).args(args).creation_flags(NO_WINDOW | 0x0000_0008).spawn()?;
        Ok(())
    }

    pub fn cmd_later(line: &str) -> anyhow::Result<()> {
        Command::new("cmd").args(["/C", line]).creation_flags(NO_WINDOW | 0x0000_0008).spawn()?;
        Ok(())
    }
}

const UNINSTALL_KEY: &str = r"HKCU\Software\Microsoft\Windows\CurrentVersion\Uninstall\VortexClient";
const RUN_KEY: &str = r"HKCU\Software\Microsoft\Windows\CurrentVersion\Run";

/// Copy the running launcher into place, add shortcuts and the Apps entry.
/// Returns the installed exe (start it, then quit this one).
pub fn install(version: &str, desktop: bool, start_menu: bool) -> Result<PathBuf> {
    if !cfg!(windows) {
        return Err(anyhow!("installing is for Windows"));
    }
    let dir = install_dir().ok_or_else(|| anyhow!("no local app data folder"))?;
    std::fs::create_dir_all(&dir)?;
    let exe = std::env::current_exe()?;
    let target = dir.join(EXE);
    if !same_dir(&exe, &dir) {
        // A running older copy there: move it aside (allowed on Windows).
        if target.exists() {
            let old = target.with_extension("exe.old");
            let _ = std::fs::remove_file(&old);
            let _ = std::fs::rename(&target, &old);
        }
        std::fs::copy(&exe, &target)?;
    }
    #[cfg(windows)]
    {
        if desktop {
            sys::powershell(&shortcut_script("Desktop", &target))?;
        }
        if start_menu {
            sys::powershell(&shortcut_script("Programs", &target))?;
        }
        let size_kb = std::fs::metadata(&target).map(|m| m.len() / 1024).unwrap_or(0);
        for (name, kind, data) in uninstall_values(&target, version, size_kb) {
            sys::reg(&["add", UNINSTALL_KEY, "/v", name, "/t", kind, "/d", &data, "/f"])?;
        }
        // Start-with-Windows points at the installed copy from now on.
        if autostart_enabled() {
            set_autostart(true)?;
        }
    }
    let _ = (desktop, start_menu, version);
    Ok(target)
}

/// Undo `install`: shortcuts, the Apps entry, start-with-Windows, and the
/// installed files (once this copy has quit). Game data is kept.
pub fn uninstall() -> Result<()> {
    if !cfg!(windows) {
        return Err(anyhow!("uninstalling is for Windows"));
    }
    #[cfg(windows)]
    {
        let _ = sys::powershell(&format!("{}; {}", remove_shortcut_script("Desktop"), remove_shortcut_script("Programs")));
        let _ = sys::reg(&["delete", UNINSTALL_KEY, "/f"]);
        let _ = sys::reg(&["delete", RUN_KEY, "/v", APP_KEY, "/f"]);
        if let Some(dir) = install_dir() {
            // Give this process a moment to exit, then remove the folder.
            sys::cmd_later(&format!("ping -n 3 127.0.0.1 >nul & rmdir /s /q \"{}\"", dir.display()))?;
        }
    }
    Ok(())
}

pub fn autostart_enabled() -> bool {
    #[cfg(windows)]
    {
        sys::reg(&["query", RUN_KEY, "/v", APP_KEY]).unwrap_or(false)
    }
    #[cfg(not(windows))]
    {
        false
    }
}

/// Start the launcher when Windows starts (the installed copy when there is
/// one), or stop doing so.
pub fn set_autostart(on: bool) -> Result<()> {
    #[cfg(windows)]
    {
        if on {
            let dir = install_dir().map(|d| d.join(EXE)).filter(|p| p.is_file());
            let exe = match dir {
                Some(p) => p,
                None => std::env::current_exe()?,
            };
            sys::reg(&["add", RUN_KEY, "/v", APP_KEY, "/t", "REG_SZ", "/d", &format!("\"{}\" --minimized", exe.display()), "/f"])?;
        } else {
            let _ = sys::reg(&["delete", RUN_KEY, "/v", APP_KEY, "/f"]);
        }
        Ok(())
    }
    #[cfg(not(windows))]
    {
        let _ = on;
        Err(anyhow!("starting with the computer is for Windows"))
    }
}

/// Start `exe` on its own (the installed copy after installing).
pub fn start(exe: &Path) -> Result<()> {
    #[cfg(windows)]
    {
        sys::detached(exe, &[])
    }
    #[cfg(not(windows))]
    {
        std::process::Command::new(exe).spawn()?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn quotes_for_powershell() {
        assert_eq!(ps_quote("C:\\Users\\O'Neil\\x"), "'C:\\Users\\O''Neil\\x'");
        let s = shortcut_script("Desktop", Path::new("C:\\Users\\Me\\AppData\\Local\\Programs\\Vortex Client\\Vortex.exe"));
        assert!(s.contains("GetFolderPath('Desktop')"));
        assert!(s.contains("'Vortex Client.lnk'"));
        assert!(s.contains("$s.TargetPath='C:\\Users\\Me\\AppData\\Local\\Programs\\Vortex Client\\Vortex.exe'"));
        assert!(s.contains("$s.WorkingDirectory='C:\\Users\\Me\\AppData\\Local\\Programs\\Vortex Client'"));
    }

    #[test]
    fn apps_entry_uninstalls_with_the_exe() {
        let v = uninstall_values(Path::new("C:\\P\\Vortex Client\\Vortex.exe"), "0.8.0", 20000);
        let get = |k: &str| v.iter().find(|(n, _, _)| *n == k).map(|(_, _, d)| d.clone()).unwrap();
        assert_eq!(get("UninstallString"), "\"C:\\P\\Vortex Client\\Vortex.exe\" --uninstall");
        assert_eq!(get("DisplayVersion"), "0.8.0");
        assert_eq!(get("InstallLocation"), "C:\\P\\Vortex Client");
    }

    #[test]
    fn knows_when_it_runs_installed() {
        assert!(same_dir(Path::new("C:\\A\\Vortex Client\\Vortex.exe"), Path::new("c:\\a\\vortex client\\")));
        assert!(!same_dir(Path::new("C:\\Users\\Me\\Downloads\\Vortex.exe"), Path::new("C:\\A\\Vortex Client")));
    }
}
