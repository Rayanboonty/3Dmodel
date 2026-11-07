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

/// Physical memory in MB, if the system says.
pub fn total_ram_mb() -> Option<u64> {
    #[cfg(windows)]
    {
        #[repr(C)]
        struct MemoryStatusEx {
            length: u32,
            memory_load: u32,
            total_phys: u64,
            avail_phys: u64,
            total_page_file: u64,
            avail_page_file: u64,
            total_virtual: u64,
            avail_virtual: u64,
            avail_extended_virtual: u64,
        }
        #[link(name = "kernel32")]
        unsafe extern "system" {
            fn GlobalMemoryStatusEx(buffer: *mut MemoryStatusEx) -> i32;
        }
        let mut st = MemoryStatusEx {
            length: std::mem::size_of::<MemoryStatusEx>() as u32,
            memory_load: 0,
            total_phys: 0,
            avail_phys: 0,
            total_page_file: 0,
            avail_page_file: 0,
            total_virtual: 0,
            avail_virtual: 0,
            avail_extended_virtual: 0,
        };
        // SAFETY: the struct is laid out as MEMORYSTATUSEX with its length set.
        let ok = unsafe { GlobalMemoryStatusEx(&mut st) };
        (ok != 0 && st.total_phys > 0).then_some(st.total_phys / (1024 * 1024))
    }
    #[cfg(target_os = "macos")]
    {
        let out = std::process::Command::new("sysctl").args(["-n", "hw.memsize"]).output().ok()?;
        String::from_utf8_lossy(&out.stdout).trim().parse::<u64>().ok().map(|b| b / (1024 * 1024))
    }
    #[cfg(not(any(windows, target_os = "macos")))]
    {
        let text = std::fs::read_to_string("/proc/meminfo").ok()?;
        let kb: u64 = text.lines().find_map(|l| l.strip_prefix("MemTotal:"))?.trim().trim_end_matches("kB").trim().parse().ok()?;
        Some(kb / 1024)
    }
}

/// Logical CPU cores.
pub fn cpu_count() -> usize {
    std::thread::available_parallelism().map(|n| n.get()).unwrap_or(2)
}

/// A PC that needs the light settings: 4 cores or fewer, or 8 GB or less.
pub fn is_low_end() -> bool {
    cpu_count() <= 4 || total_ram_mb().is_some_and(|r| r <= 8 * 1024)
}

/// The most memory Minecraft should get on a PC with `total_mb` of RAM:
/// enough for the game, with room left for Windows and the browser.
/// Modded instances get a bit more.
pub fn recommended_memory_mb(total_mb: Option<u64>, modded: bool) -> u32 {
    let Some(total) = total_mb else {
        return if modded { 4096 } else { 3072 };
    };
    let vanilla = match total {
        0..=3200 => 1024,
        3201..=4400 => 1536,
        4401..=6400 => 2048,
        6401..=8400 => 2560,
        8401..=12400 => 3072,
        _ => 4096,
    };
    let more = match total {
        0..=3200 => 256,
        3201..=8400 => 512,
        8401..=12400 => 1024,
        12401..=16800 => 1024,
        _ => 2048,
    };
    if modded {
        vanilla + more
    } else {
        vanilla
    }
}

#[cfg(test)]
mod ram_tests {
    use super::*;

    #[test]
    fn memory_fits_the_pc() {
        // A 4 GB laptop: well under half for Java, so Windows doesn't swap.
        assert_eq!(recommended_memory_mb(Some(4096), false), 1536);
        assert_eq!(recommended_memory_mb(Some(4096), true), 2048);
        assert_eq!(recommended_memory_mb(Some(2048), true), 1280);
        assert_eq!(recommended_memory_mb(Some(8192), true), 3072);
        assert_eq!(recommended_memory_mb(Some(16384), true), 5120);
        assert_eq!(recommended_memory_mb(Some(32768), true), 6144);
        assert_eq!(recommended_memory_mb(None, true), 4096);
        assert!(total_ram_mb().is_some_and(|r| r > 256));
        assert!(cpu_count() >= 1);
    }
}
