GalIOS 1.18 and GalIOS Studio 1.9

**Both now update themselves.**
- GalIOS checks this repository's releases at every refresh. With root, it downloads a newer GalIOS, checks it against its SHA-256 and installs it with no prompt. GalIOS closes when the update is in: open it again. Without root, the Update banner hands the APK to Android's installer as before.
- Studio checks a few seconds after it starts and every 6 hours. It downloads a newer Studio, checks its SHA-256, waits until no task is running, swaps itself and restarts.

**From now on every GalIOS is signed with one release key**, so each update installs over the last. GalIOS 1.17 and older were signed on another machine, so Android refuses 1.18 as an update to them: install 1.18 once with this Studio (Phone, Install GalIOS). It replaces the old copy. iOS's image and runtime on the phone stay where they are.

**The next runtime** (a separate release, made from GalIOS/runtime/patches; get it in Setup, step 3): Lock Screen widgets no longer flash blank (OneiOS's GPU read wait is on). The session log now also records where the keyboard, Settings and Clock get stuck, so the next fix can follow the log.

Still needs the kernel and image rebuilt with Studio's Do everything: brightness slider, volume keys, dimming after lock, Wi-Fi joining, the Camera.
