# GalIOS

A personal Android app that boots iOS 27 next to One UI on a Galaxy S25 Ultra (SM-S938, Snapdragon) or S24 Ultra,
with temporary root, and brings you back to One UI.

GalIOS is the front end. The boot itself is done by:

- **OneiOS** ([akshaynexus/OneiOS](https://github.com/akshaynexus/OneiOS)): `xnuhost`, a source-built XNU that runs as
  a process on One UI's kernel. GalIOS downloads its runtime package from OneiOS's releases.
- **OneOS's iOS 27 engine** ([akshaynexus/OneOS](https://github.com/akshaynexus/OneOS), branch `oneios`): its root
  script and session scripts, which stop One UI, start the session and bring One UI back. GalIOS reads them from the
  **installed OneOS app** and ships none of them. OneOS's switch script accepts the S24 Ultra only; GalIOS adapts that
  one phone check on the phone so it also accepts the S25 Ultra (pa3q, SM8750), and refuses if the check is not the
  one it knows.

GalIOS ships no part of OneOS, OneiOS or iOS. The iOS image is built by its owner from Apple's firmware with
OneiOS's scripts (`docs/building.md` there) and imported from a file.

## What you need on the phone

1. Temporary root, with busybox and resetprop at a root manager's paths (`/data/adb/ksu/bin`, `/data/adb/ap/bin` or
   `/data/adb/magisk`). GalIOS's Setup step 1 checks this.
2. The OneOS app, iOS 27 edition (OneOS releases, `oneios-<version>`). It is the boot engine; you do not need to open it.
3. Your iOS 27 image set (`OneiOS_iOS27.0.1_24A446_iPhone18-2.tar.zst`, about 11 GB) copied to the phone, and
   about 22 GB free while it is imported.

Then in GalIOS: **Setup** → check root → install engine → download the runtime → import your image. GalIOS lists the
OneiOS archives it finds in Download, Documents or the top of the storage (`OneiOS_iOS…tar.zst`,
`oneios-runtime-…-arm64.tar.zst`) with a one-tap Import; any other place works with Choose image. **Boot** → Boot
iOS 27. To come back: hold Volume Up + Volume Down for 3 seconds.

## Updates

GalIOS updates itself. On each refresh it checks the GitHub Releases of its repository
(`dev/galios/core/Updater.kt`, `REPO`) for a build whose versionCode is higher than the installed one, shown by an
asset named `GalIOS-<versionCode>.apk`. If one exists it shows an "Update available" banner; tapping Update downloads
the APK and hands it to Android's package installer (the user confirms; GalIOS asks to be allowed to install apps the
first time). Nothing installs on its own.

**To publish an update** (so installed copies see it), create a GitHub release on the repo with the APK attached,
named for its versionCode:

    # bump versionCode/versionName in app/build.gradle.kts, then:
    ./gradlew :app:assembleRelease
    cp app/build/outputs/apk/release/app-release.apk GalIOS-<versionCode>.apk
    gh release create v<versionName> GalIOS-<versionCode>.apk -t "GalIOS <versionName>" -n "<notes>"

The asset name must be exactly `GalIOS-<versionCode>.apk` (e.g. `GalIOS-4.apk`); that number is how GalIOS compares
builds. Releases hold only GalIOS's own APK — no Apple or OneOS files.

## Build

JDK 17+ and the Android SDK (platform 35):

    ./gradlew :app:assembleRelease      # app/build/outputs/apk/release/app-release.apk
    ./gradlew :app:testDebugUnitTest    # unit tests

The staging test runs against OneOS's real scripts on a stand-in phone only when given their paths
(`-Dgalios.rootScript=... -Dgalios.switchScript=... -Dgalios.returnScript=... -Dgalios.fakePath=...`); see
`app/src/test/java/dev/galios/core/StageProtocolTest.kt`. Screenshots: `-Proborazzi.test.record=true`, images in
`app/build/screenshots/`.

## GalIOS Studio (Windows)

`studio/` is a Windows program that does the PC side: it builds the image in WSL Ubuntu (`tools/build-ios-image-wsl.sh`,
which it carries), then over USB installs GalIOS, installs the boot engine (read from the phone's OneOS app, adapted
for the S25 Ultra), adds the OneiOS runtime and the image, runs the boot check, and boots and returns. It opens as a
local page at `http://127.0.0.1:47827/` and talks only to this PC, the phone and GitHub/Google downloads.

    cd studio && ./build.sh       # dist/GalIOS-Studio.exe (Go 1.24; copies the build script and the release APK in)
    go test ./...                 # unit tests; the boot path runs against OneOS's real scripts when given their
                                  # paths (STUDIO_FAKE_ADB, STUDIO_ONEOS_APK_DIR, STUDIO_REAL_ROOT/_SWITCH/_RETURN)

## Status

Built and tested off the phone: the core against OneOS's real root script (install, the S25 adaptation, runtime and
image import with root's SHA-256 checks, a damaged file refused, a cancel, status), and every screen rendered. Not yet
run on a phone. Nobody has booted iOS on an S25 Ultra yet; after each try, the Logs tab shows how far it got.

Not affiliated with Apple or Samsung. iOS is a trademark of Apple Inc.; Galaxy is a trademark of Samsung Electronics.
