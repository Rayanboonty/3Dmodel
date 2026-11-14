# GalIOS roadmap: 37 upgrades

Every item says why (the evidence: the 2026-11-14 20:51 session log, the owner's videos of 21:48, OneiOS's own notes),
what it needs, and where it stands. The project's rule holds: change runtime behaviour only where a log shows the
cause; anything else ships as a switch the owner can turn off (Setup, Tuning: `galios-bootargs`, runtime patch 0015).

Status: **done** (shipped), **this release**, **needs a log** (the code waits for a session that shows what to do),
**needs the rebuild** (Studio's Do everything: new kernel and image), **big** (weeks, mostly OneiOS's tree or the Mac build).

## A. Heat and power

The log: SoC 95-105 C within seconds of load, 37 thermal steps, host CPU up to 695 % of 800, case 45 C. The video: iOS
locked behind "iPhone needs to cool down".

| # | Upgrade | Why | Needs | Status |
|---|---|---|---|---|
| 1 | The case never locks iOS (0014) | the case rule sent "critical" at a guessed 46 C | - | done (galios.18) |
| 2 | 60 Hz cool mode (Tuning) | backboardd, the compositor, ran 100-157 % CPU at 120 Hz | a log with it on: `sheng-vsync=60`, flush rate, SoC | this release |
| 3 | Pause search indexing (Tuning) | Spotlight at 100 % of a core for 100 s | a log: Spotlight's CPU, kbd's | this release |
| 4 | Charge bypass while hot | charging heats the same board; gaming phones run from the cable without charging | the S25 Ultra's charging-control node (read on the phone first) | needs a log |
| 5 | 120 Hz only while touching, 60 when still | iOS draws every frame at 120 Hz even when nothing moves | the guest display driver's vsync at run time (OneiOS) | big |
| 6 | Boost the fast cores only while touching | the animation boost already exists (0008); touch is the better trigger | drm2kgsl's boost hook + touch events in xnuhost | needs a log |
| 7 | Profiles: Cool, Balanced, Maximum | one tap for the right switch set | items 2, 3, Maximum performance | after 2 and 3 are proven |
| 8 | Heat in the session report | see at once which rule fired and when | item 30 | with 30 |

## B. CPU

| # | Upgrade | Why | Needs | Status |
|---|---|---|---|---|
| 9 | Fix where kbd, Settings and Clock spin | kbd 25 % for minutes, Preferences 100 % for a minute, keys stuck | the 0012 traces in a log, read with `oneios-tool sym` | needs a log |
| 10 | Background services off (A/B) | Mail 171 %, BackgroundShortcutRunner 195 %, generativeexperience busy, with no network or Neural Engine | `oneios-disable-jobs` per service, one at a time | needs a log |
| 11 | Cheaper system calls | a BSD call costs about 28 us here (about 1 us on an iPhone): every call traps to the host | OneiOS's deferred unmaps and trap work | big (upstream) |
| 12 | Fixed RAM (no host swap of iOS) | OneiOS's `ram-giveback=0`, built, never run on a phone | an `xnuhost.conf` key (not a boot setting): a second switch file | needs a log |
| 13 | vCPU layout A/B | 6 E + 2 P pinned; heat is on the P cores | xnuhost's plan as a switch | needs a log |

## C. GPU and display path

GPU frames take about 2.6 ms of 8.3: the GPU is not the limit. The CPU around it is.

| # | Upgrade | Why | Needs | Status |
|---|---|---|---|---|
| 14 | Zero-copy scanout | every frame is copied by the CPU to the panel: about 66 MB/s, 1.6 ms a frame | the display helper taking iOS's buffer as a dmabuf | big |
| 15 | Widgets read wait (0012) | 170 widget reads while written, 4 blank surfaces | a log: `read while written` 0 | done, to verify |
| 16 | Wide colour path (half-float) | OneiOS's opt-in `display-format=rgba16f` | an `xnuhost.conf` switch, the Mac's plug-in | big |
| 17 | Measure Liquid Glass's cost | blur passes on backboardd's CPU and GPU time | OneiOS's `oneios-display` swap lines | needs a log |

## D. Screen features

| # | Upgrade | Why | Needs | Status |
|---|---|---|---|---|
| 18 | iPhone identity (OLED, island, corners) | iOS had 3 `/product` values of about 200 | the rebuild (the identity file) | done (1.19), to verify |
| 19 | Always-on, step 2: what iOS asks | with the identity on, backboardd's requests show in `ONEIOS BACKLIGHT` | a log with item 18 on | needs a log |
| 20 | Always-on, step 3: the panel's low-power mode | a dim, slow image while iOS sleeps | Samsung's panel node from xnuhost | after 19 |
| 21 | Dynamic Island's size and place | the screen is 360x780 points, the Pro Max's tables are for 440x956 | the UI scale (2 or 3) and One UI's resolution (QHD gives 480x1040) | needs a log and screenshots |
| 22 | The status bar clock | missing in the video; likely the same geometry | item 21 | needs a log |
| 23 | Brightness, full range | the slider stopped reaching the panel after a minute | the rebuild (slider position), 0006 | needs the rebuild |

## E. Hardware

| # | Upgrade | Why | Needs | Status |
|---|---|---|---|---|
| 24 | Volume keys | presses reach iOS as an external keyboard's | the rebuild (Built-In buttons) | needs the rebuild |
| 25 | Wi-Fi joins | 11 scans, no join | the rebuild (auto-join fix) | needs the rebuild |
| 26 | Camera | no capture plug-in in the old Data store | the rebuild, or Start iOS fresh | needs the rebuild |
| 27 | Flashlight | no bridge at all | a host LED bridge + the capture plug-in's torch (Mac) | big |
| 28 | Bluetooth | OneiOS's UART path never tried in iOS | OneiOS | big (upstream) |
| 29 | Haptics and key clicks | counters in every layer, never proven | a log | needs a log |
| 29a | Sound without crackles (0016) | the helper's target sat at its 80 ms ceiling with 167 underruns | a log: underruns, the target's peak | this release |
| 29c | Volume keys set the level (0017) | the slider cannot move; the keys reach xnuhost anyway | a log: `audio: GalIOS volume` lines | this release |
| 29b | The volume slider | iOS's speaker device publishes no volume control, so iOS pins the slider at the top | a volume control in UniXNU-droidvm's DroidVMAudio.cpp (kernel), gain applied in the helper | big (kernel driver) |

## F. Tools

| # | Upgrade | Why | Needs | Status |
|---|---|---|---|---|
| 30 | Session report in GalIOS | the numbers in this file took a PC and an hour to pull out of 18,000 lines | parse the saved log on the phone | next |
| 31 | iOS crash reports on the PC | the log never names the app that crashed (4 ReportCrash runs) | OneiOS's `pull-data` and `ioslog` in Studio (Rust, built on GitHub for Windows) | big |
| 32 | Studio: "Collect everything" | one zip with every log for whoever fixes it | item 31 | after 31 |
| 33 | Live log while iOS runs | see a problem as it happens | adb in a session (OneiOS's USB work) | needs a log |
| 34 | A/B mode | alternate one switch across boots and compare the logs | items 7 and 30 | after 30 |
| 35 | CI runs every patched part's tests | 0014's one wrong test was only found because CI ran them | done for thermal and brightness | done (partly) |

## G. Design

| # | Upgrade | Why | Needs | Status |
|---|---|---|---|---|
| 36 | Studio's look matches the app | the exe still has the old gradients | Studio's `ui/` (HTML and CSS) | next |
| 37 | Light mode | One UI and iOS both follow the system | the theme's colours as a scheme | later |

## Not on this list, and why

- Phone calls and mobile data: no bridge from iOS to Samsung's modem exists; it would be the biggest bridge of all.
- Face ID: no Secure Enclave on this phone; a camera stand-in would unlock for anyone's face.
- iOS 18 or 26: OneiOS boots one build (27.0.1); each version is its own port.
- iOS images from a server: they are built from Apple's firmware; each owner builds their own (Studio, or the phone).
- Device-identity spoofing for Apple's services: it would defeat Apple's own checks.
