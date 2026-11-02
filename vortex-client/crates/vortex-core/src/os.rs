//! Host platform detection, expressed in Mojang's vocabulary.

/// OS name as used by version JSON rules: `windows`, `osx` or `linux`.
pub fn name() -> &'static str {
    if cfg!(target_os = "windows") {
        "windows"
    } else if cfg!(target_os = "macos") {
        "osx"
    } else {
        "linux"
    }
}

/// CPU architecture: `x86`, `x86_64`, `arm64` or `arm32`.
pub fn arch() -> &'static str {
    if cfg!(target_arch = "x86") {
        "x86"
    } else if cfg!(target_arch = "aarch64") {
        "arm64"
    } else if cfg!(target_arch = "arm") {
        "arm32"
    } else {
        "x86_64"
    }
}

/// Value for `${arch}` in legacy native classifiers (`natives-windows-${arch}`).
pub fn arch_bits() -> &'static str {
    if cfg!(target_pointer_width = "64") {
        "64"
    } else {
        "32"
    }
}

/// OS version string matched against `os.version` regexes in rules.
///
/// Windows 10 and 11 both report `10.0`, and every Java runtime Mojang ships
/// for current versions requires Windows 10+, so that is what we report.
#[cfg(target_os = "windows")]
pub fn version() -> String {
    "10.0".to_string()
}

#[cfg(target_os = "macos")]
pub fn version() -> String {
    std::process::Command::new("sw_vers")
        .arg("-productVersion")
        .output()
        .ok()
        .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
        .unwrap_or_default()
}

#[cfg(not(any(target_os = "windows", target_os = "macos")))]
pub fn version() -> String {
    String::new()
}

/// Platform key in Mojang's Java runtime index.
pub fn java_runtime_platform() -> Option<&'static str> {
    match (name(), arch()) {
        ("windows", "x86_64") => Some("windows-x64"),
        ("windows", "x86") => Some("windows-x86"),
        ("windows", "arm64") => Some("windows-arm64"),
        ("osx", "arm64") => Some("mac-os-arm64"),
        ("osx", _) => Some("mac-os"),
        ("linux", "x86_64") => Some("linux"),
        ("linux", "x86") => Some("linux-i386"),
        _ => None,
    }
}

pub fn classpath_separator() -> &'static str {
    if cfg!(windows) {
        ";"
    } else {
        ":"
    }
}
