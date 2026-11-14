# GalIOS: hand-off (2026-11-14)

Everything another engineer or AI needs to carry this project on. Read this file first, then `README.md`,
`runtime/README.md`, `gpu/README.md` and `tools/WINDOWS.md`.

## 1. What this is

GalIOS runs **iOS 27.0.1 (24A446, as iPhone18,2)** on a **Samsung Galaxy S25 Ultra** (SM-S938B, pa3q, Snapdragon
8 Elite SM8750, Adreno 830, 12 GB) with temporary root, using two upstream projects it does not own:

- **OneiOS** (github.com/akshaynexus/OneiOS): the whole system: `xnuhost` (iOS's XNU kernel run as a Linux
  process on Android's kernel, "takeover" mode: One UI's framework is stopped, Android's kernel and drivers stay),
  the hosted kernel patches and drivers (`kernel/`, `drivers/`), the image builder (`scripts/`), the Metal plug-in
  (`userland/metal`, Mesa Turnip for Adreno + metal2vulkan), `docs/` (read `docs/newtodo.md` first: their live
  status, what is proven on a phone and what is only built).
- **OneOS** (the "iOS 27 edition" Android app, package `com.oneos...`): ships the boot engine: `oneios_root.sh`
  (root verbs: status, begin/receive/finish staging, switch, return, logs, logfile, remove...), `oneios_switch.sh`,
  `oneios_return.sh`. GalIOS reads them from the installed OneOS APK; they are **not** in this repository.

Only one iOS build is supported by OneiOS (`profiles/ios-27.0.1-iPhone18,2.toml`). The image is built by the user
from Apple's own IPSW; **no Apple file is ever in this repository or its releases** (keep it that way).

## 2. What is in this package

| Path | What |
|---|---|
| `app/` | **GalIOS** Android app (Kotlin, Jetpack Compose), `dev.galios`, version 1.16 (code 16). Root client, OneOS engine reader + switch-script adapter (S25 Ultra phone check, read-only bind fallback), staging importer, runtime downloader, logs (saved to Downloads/GalIOS, also automatically after each session), "Start iOS fresh" (erases iOS's Data store), on-phone image build, self-update. |
| `studio/` | **GalIOS Studio** (Go 1.24, Windows exe with a WebView2 window; `ui/` is its HTML/JS/CSS). Version 1.8. "Do everything": WSL + Ubuntu setup, the image build (runs `tools/build-ios-image-wsl.sh`), installs GalIOS (bundled APK) and the runtime, imports the image over adb, root through su or GalIOS's root bridge. |
| `tools/build-ios-image-wsl.sh` | The image build in WSL/Ubuntu: packages, clang 23, Rust, OneiOS + references, IPSW fetch/decrypt, **kernel build (rebuilt when OneiOS's kernel/drivers change)**, prelink, GPU pack, image set, verify, copy to Windows. |
| `tools/phone-build.sh` | The same build inside an Ubuntu chroot on the phone (root). |
| `tools/galios-adb.sh`, `tools/galios-rootd.sh` | Studio's adb helper and the queue-based root helper that lends root to GalIOS. |
| `runtime/patches/` | GalIOS's patches to OneiOS's runtime (xnuhost and bridges), applied by `.github/workflows/galios-runtime.yml` on top of OneiOS `main`. Each is explained in `runtime/README.md`. |
| `gpu/` | Notes for the GPU pack workflow (`ONEIOS_REF` optional pin). |
| `.github/workflows/galios-gpu.yml` | macOS runner: builds OneiOS's Metal plug-in, guest helpers and capture plug-in against iOS 27 stubs made on the runner from Apple's SystemOS image (never published); publishes release `galios-gpu-YYYYMMDD-N` with `galios-gpu-pack.tar.gz`. |
| `.github/workflows/galios-runtime.yml` | Linux runner: OneiOS `main` + `runtime/patches` -> `runtime/package.sh` -> `oneios-runtime-0.1.8-galios.N-arm64.tar.zst`, checks, attached to the newest GPU pack release (older runtimes removed). |
| `binaries/` | The current builds: `GalIOS-1.16.apk`, `GalIOS-Studio-1.8.exe` (convenience; rebuild from source). |

Build: app `./gradlew :app:testDebugUnitTest :app:assembleRelease` (Android SDK in `local.properties`);
Studio `cd studio && ./build.sh` (copies the build script, the APK and art, runs go-winres, cross-builds the exe;
`go test ./...`). The workflows run on push to their paths.

## 3. Where things stand (proven on the user's phone)

- iOS boots, GPU acceleration works (Liquid Glass, blur, wallpaper; frames ~4 ms on the GPU), Wi-Fi scans, touch.
- Runtime history (each from a phone log; details in `runtime/README.md`):
  - galios.2: xnuhost crashed on the first GPU fence: virglrenderer's fence table was never made (0001).
  - galios.3: the display helper did not load (OneiOS f7bb1c3 fixed it upstream).
  - galios.6: P vCPUs lost their prime cores while fast-charging (`Invalid argument`), 16 s stalls: 0005 re-pins.
  - galios.8: drm2kgsl's animation boost never found xnuhost's vCPUs (0008). galios.11: boost only the E vCPUs
    (all eight drove the prime cores to 96-104 C in 5 s; iOS was told "heavy", slowed and dimmed).
  - galios.9: rebased on OneiOS 3233fed (their RAM fit, display power, touch IC fixes); galios.11: Android keeps
    2 GiB (0009) after the host swapped 320 MiB of iOS's RAM; galios.12: `oneios-hid-queue-kib=256` (keyboard).
- **The user's kernel is still the one built 2026-11-13** (log: `Darwin Kernel Version 25.5.0: Fri Nov 13 21:23:27
  GMT 2026`). The fixes that need the new kernel (brightness slider position, display power, side buttons as
  Built-In, WebKit icache fault, Wi-Fi auto-join) arrive only when Studio 1.8's "Do everything" rebuilds the kernel
  and image (that also starts iOS's Data store over, bringing the capture plug-in).

## 4. Open problems, with what is known

| Problem | Known | Next step |
|---|---|---|
| Not smooth enough | Heat (prime cores 96-104 C), host memory pressure, ~100k traps/s host<->guest; GPU frames are fast | Next log after galios.12: `thermal:` lines, `memory: host MemAvailable`, `the host swaps iOS's RAM`, `oneios-cpu:`, `oneios-gpu-report:`, `perf:` |
| Brightness | Old kernel: slider arrives only as brightness-nits (works 0..100% in the last log); DisplayBrightness float truncated to 0 | New kernel (hosted 0069 + driver 2e24760); GalIOS 0006 keeps a fallback for the old one |
| Dim after lock | Old kernel sends level 0 and no wake level; 0006's side-key fallback; new kernel sends display power | Verify in a log after the kernel rebuild |
| Volume keys | Presses reach backboardd; it took the buttons for an external keyboard (OneiOS PC boot) | Boot E `oneios-hid-buttons-builtin=1` (in 0003) needs the new kernel |
| Keyboard stops / crashes | OneiOS hypothesis: lost finger lift in a 16 KiB queue -> trackpad mode | galios.12 turns on the 256 KiB queue; check `oneios-hid: touch screen: event queue 256 KiB`, `0 dropped` |
| Camera | Host helper finds 3 cameras; the old Data store has no capture plug-in; OneiOS has never had a frame on a phone | "Start iOS fresh" or a new image, then `oneios-camera-addon ... stage 5`, cameracaptured lines (`runtime/bridges/camera/README.md`) |
| Clock not showing | Not identified (lock screen clock vs status bar vs app) | Ask the user which; OneiOS "blank widgets" (docs/gpu.md 5.1) may be related |
| Passcode, wallpaper setting | No log yet | Reproduce, read the log |
| Face ID | Impossible: no Secure Enclave (`No SEP present`), no TrueDepth | - |

Things decided and why (do not undo without new evidence): RAM is not "max" (more RAM for iOS makes Android swap:
Android's own part is 3.0-3.5 GB); the boost is not on the prime cores; device-identity spoofing for Apple ID /
activation is out of scope (it would defeat Apple's own device checks).

## 5. How to work on it

1. Get the user's newest session log: GalIOS saves it to `Downloads/GalIOS/GalIOS-session-<time>.txt` after each
   session. Fix only what a log shows; blind changes made galios.4 worse.
2. Runtime change: write it against OneiOS `main` (clone it, apply `runtime/patches` in order), test with OneiOS's
   host tests (`runtime/bridges/brightness: make test`, `runtime/xnuhost: make build/host/test_xnuhost`; one ramfix
   check fails when run as root, also on unpatched main), write the patch into `runtime/patches/`, add a check
   to `galios-runtime.yml`, push: CI publishes galios.N+1. Users get it in GalIOS: Setup > step 3.
3. Kernel or driver change: it is OneiOS's tree; a patch there needs a new kernel build (Studio's Do everything).
4. App/Studio: bump `versionCode`/`versionName`, `studio/adb.go` `bundledGalIOS*`, `studio/main.go` `version`.

Upstream to watch: OneiOS `docs/newtodo.md` (their next-session settings and what each fix needs).
