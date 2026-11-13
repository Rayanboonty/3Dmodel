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

Then in GalIOS: **Setup** → check root → install engine → download the runtime → choose your image. **Boot** → Boot
iOS 27. To come back: hold Volume Up + Volume Down for 3 seconds.

## Build

JDK 17+ and the Android SDK (platform 35):

    ./gradlew :app:assembleRelease      # app/build/outputs/apk/release/app-release.apk
    ./gradlew :app:testDebugUnitTest    # unit tests

The staging test runs against OneOS's real scripts on a stand-in phone only when given their paths
(`-Dgalios.rootScript=... -Dgalios.switchScript=... -Dgalios.returnScript=... -Dgalios.fakePath=...`); see
`app/src/test/java/dev/galios/core/StageProtocolTest.kt`. Screenshots: `-Proborazzi.test.record=true`, images in
`app/build/screenshots/`.

## Status

Built and tested off the phone: the core against OneOS's real root script (install, the S25 adaptation, runtime and
image import with root's SHA-256 checks, a damaged file refused, a cancel, status), and every screen rendered. Not yet
run on a phone. Nobody has booted iOS on an S25 Ultra yet; after each try, the Logs tab shows how far it got.

Not affiliated with Apple or Samsung. iOS is a trademark of Apple Inc.; Galaxy is a trademark of Samsung Electronics.
