# GalIOS runtime patches

Small fixes to OneiOS's runtime (xnuhost), applied by `.github/workflows/galios-runtime.yml` on top of OneiOS's
main before `runtime/package.sh`. Each one is skipped when OneiOS already has the fix.

- `0001-fence-table.patch`: xnuhost never made virglrenderer's fence table (virglrenderer.c, which makes it, is not
  built into xnuhost). The first GPU submit that exports a fence (`drm_timeline_submit_fence` → `virgl_fence_set_fd`)
  then read a NULL table, and xnuhost died with `fault on a host thread: signal, address 0xb` right after backboardd
  opened `AGXDeviceUserClient`. The patch makes the table once, when the KGSL renderer opens.
- `0004-touch-stays-awake.patch`: `touch-sleep` defaults to 0, so the touch IC is not put to sleep when iOS's
  brightness reaches 0. On the S25 Ultra the lock screen dimmed and then took no touches.

- `0003-side-key-without-actuator.patch`: the default boot arguments get OneiOS docs/hardware.md 5's boot C
  (`oneios-actuator=0`: on the S25 Ultra the side key then locked iOS in 0.85 s) and boot E
  (`oneios-hid-buttons-builtin=1`: the side buttons published Built-In, Consumer Control; backboardd took them for an
  external keyboard, the likely reason the volume keys did nothing). E needs a kernel from OneiOS a623515 on.
  galios.12 adds `oneios-hid-queue-kib=256` (the touch screen's event queue; OneiOS newtodo 3, the keyboard that
  stops after 3-4 keys: a finger's lift lost in a full 16 KiB queue turns the keyboard into its trackpad mode).
- `0005-vcpus-keep-their-cores.patch`: every 2 s xnuhost reads each vCPU's affinity and sets it again when it is not
  its plan's (the iOS threads follow by the epoch). On the S25 Ultra, fast-charging, the two P vCPUs could not take
  the prime cores at the start (`vCPU 6 on host CPUs 0x40: Invalid argument`) and ran on the E cores beside the
  pinned vCPUs for the whole session: each vCPU got about a third of a core, single page faults took up to 16 s.
- `0006-brightness-key-fallback-and-dimmest.patch`: (1) the dimmest light level iOS asks for in a session goes on
  the panel's floor and the levels up to 10 % of the light are spread from there (iOS's bottom lit the panel well above
  One UI's dimmest); (2) for a kernel without hosted 0069 (no display power from iOS's framebuffer), a side-key press
  while iOS's display counts as on and its level is 0 brings iOS's last level back after 600 ms (after a lock the
  panel stayed at its floor). With 0069 the display power does that and the fallback stays out. (1) ends for the
  session at the new driver's first slider position or display power (a kernel from OneiOS 2e24760 on): its slider's
  bottom is the floor already, and remapping only the light levels made slider and light disagree at the bottom
  (galios.10). Brightness tests: 95 checks, 0 failures (OneiOS's 82 and 13 new, both drivers' cases).
- `0008-animation-boost-finds-the-vcpus.patch`: adds a drm2kgsl patch. drm2kgsl raises the vCPU threads' uclamp.min
  to 1024 while the compositor animates (Droid-VM measured page swipes held back by schedutil at 2.2-2.7 GHz), but it
  looked for crosvm's thread names only (`crosvm_vcpu*`); xnuhost's are `xnu-cpuN`, so the phone logged
  `vCPU uclamp.min 1024: 0 of 0 threads` and the boost never happened. galios.11: the E vCPUs only (xnu-cpu0..5).
  With all eight (`8 of 8 threads`, galios.8-10) the prime cores reached 96-104 C within 5 s of the boot, iOS was told
  its thermal state was heavy (it slows itself and dims) and the session flapped between heavy and nominal.
- `0010-ios-not-told-hot-by-core-bursts.patch`: the thermal bridge tells iOS HEAVY from the SoC at 107 C (OneiOS:
  95 C) and TRAPPING at 110 C (105 C). The S25 Ultra's cores burst to 100-104 C within seconds of any load: in one
  session 33 of 39 HEAVY came from the SoC with the case at 34-40 C, and each one made iOS slow itself and cap the
  brightness. The case and the battery limits are unchanged. Thermal tests: 73 checks, 0 failed.
- `0011-maximum-performance-switch.patch`: the owner's switch (GalIOS 1.17, Setup step 6): with the file
  `galios-max` in the stage, xnuhost gives iOS 10 GiB (no fit to what the host has free) and sets GALIOS_BOOST_ALL,
  which 0008's drm2kgsl patch reads to boost every vCPU. Off by default: the balanced settings are what the logs
  support (more RAM made the host swap; the boost on the prime cores drove them to 104 C).
- galios.13 changes 0006's remap: the old driver's light levels run 25 %..100 % (16387 of 65535 at the slider's bottom
  in every drag on the phone), so iOS's dimmest (25 %, or lower if iOS asks later) is the panel's floor and the rest is
  spread evenly to the ceiling. Brightness tests 97 checks, 0 failures.
- `0009-android-keeps-2-gib.patch`: `ram-session-reserve` 2 GiB (OneiOS: 1 GiB). With OneiOS's fit and 1 GiB the
  galios.10 session started at 8958 MiB available, gave iOS 6912 MiB, and Android fell to 740-900 MiB and swapped
  320 MiB of iOS's RAM (swap-ins up to 5 MB/s). With 2 GiB iOS gets about 6144 MiB and Android keeps about 1.5 GiB.
- `0012-widgets-read-wait-and-spin-traces.patch` (on top of 0003): from the 2026-11-14 20:51 session (old kernel of
  2026-11-13). (1) `oneios-gpu-read-wait=1`: Lock Screen widgets flashed blank, and the kernel's counters showed the
  render server reading the widget renderer's IOSurfaces while they were still being written (170 such reads; 4 of 14
  of its surfaces all zero). This is OneiOS docs/gpu.md 5.1's third guess, and the switch was already in that kernel
  (`oneios-gpu-read-wait: off`). Check in the next log: `oneios-gpu-read-wait: on`, `read while written` 0 for
  WidgetRenderer_, no `zero` surfaces. (2) Diagnostics, no behaviour change: `oneios-ubt=` (user backtraces every 30 s
  from 120 s, 60 samples), `oneios-iouc=` and `oneios-sysfail=` for kbd, Spotlight, Preferences, InputUI,
  MobileTimer and backboardd. In that session kbd ran at about 25 % of a vCPU for minutes, Preferences at 100 % for a
  minute, and keys stayed pressed; these lines say where they spin (read them with OneiOS's `oneios-tool sym`). Not
  done on purpose: Spotlight's indexer off (`oneios-disable-jobs=...searchd...`). Spotlight sat at 100 % for 100 s,
  but that was the Spotlight process itself, not searchd, so turning the indexer off is OneiOS's A/B and not a fix.
- `0013-iphone-identity-switch.patch`: the owner's switch in GalIOS (Setup, "iPhone screen features"). With the file
  `galios-identity.adt` in the stage (`/data` in the session), xnuhost takes it as the identity tree: the iPhone18,2
  device tree that the image build read from the owner's own IPSW (`OneiOS_iPhone18-2_identity.adt`, beside the image;
  never in this repository or its releases). OneiOS's loader then copies the iPhone's model, board and `/product` into
  the guest's tree instead of three properties (`docs/userland.md` 5: the guest had no `oled-display`,
  `island-notch-location`, `display-corner-radius`, `supports-burnin-mitigation`, ...), and MobileGestalt answers
  the Dynamic Island and the always-on display from those. OneiOS's own A/B of the same thing is
  `--set identity=out/kernel-inputs/24A446/devicetree.bin`; it is not their default because it changes many answers at
  once (the OLED and P3 flags among them), hence a switch. A file that does not parse is logged and the boot goes on
  without it. Log lines: `GalIOS iPhone screen features: the identity ...`, `GalIOS identity: N /product properties
  ...`. What iOS then asks the panel for always-on (display power, brightness keys) shows in the `ONEIOS BACKLIGHT`
  lines: the next step of the always-on driver follows those.
- `0014-the-case-never-locks-ios.patch`: the thermal bridge's case rule (Samsung's `lrp` sensor) tops at HEAVY. It used
  to send TRAPPING at 46 C and SLEEPING at 50 C, thresholds OneiOS's README calls a guess on Samsung's scale (Samsung's
  charger only limits its current there). TRAPPING is Apple's "critical": iOS locked itself behind "iPhone needs to cool
  down" (the owner's video, 2026-11-14 21:48; the first session's log had the case at 45 C). At HEAVY iOS still slows
  itself down, which is what cools the phone. The battery (TRAPPING at 50 C) and the SoC (110 C, 0010) are unchanged.
  The two thermal tests that drove TRAPPING through the case now drive it through the battery; CI now runs the thermal
  and brightness host tests (`make -C thermal test`, `make -C brightness test`) on every build.
- `0015-tuning-switches-from-the-stage.patch`: the owner's tuning switches (GalIOS 1.22, Setup "Tuning"). xnuhost reads
  `galios-bootargs` from the stage (`/data` in the session): words `key=value` whose key is on a short list
  (`sheng-vsync`, `oneios-disable-jobs`, `oneios-enable-jobs`, `oneios-gpu-read-wait`, `oneios-hid-queue-kib`, the
  `oneios-ubt*`/`iouc`/`sysfail` traces, `oneios-log-persist`, `oneios-bl-mode`), letters, digits and `._,=-` only, at
  most 300 bytes. They go in front of the defaults: XNU's `PE_parse_boot_argn` takes the first match, so a switch
  overrides a default, and `sheng-vsync=60` also stops `xh_display_boot_args` adding the panel's 120. Anything else is
  dropped and logged (`GalIOS tuning: "..." dropped`); the boot goes on. Log line: `GalIOS tuning: <the words used>`.
  It is how new switches reach iOS without a new runtime: each upgrade in `GalIOS/ROADMAP.md` that is a boot setting
  becomes a toggle the owner can turn off again.
- `0016-audio-target-up-to-200-ms.patch`: the AAudio helper's target (the sound it keeps queued ahead of Android's
  mixer) may rise to 200 ms after underruns, not 80 (`ONEIOS_AUDIO_TARGET_MAX_MS`, 40-400). The 2026-11-14 20:51
  session: "target 80, peak 80" and still 167 underruns and 2 overflows in 520 s of sound, heard as crackles and odd
  noises; the guest's periods came late by more than 80 ms while the phone was hot (1444 of 45622 late). Each underrun
  still raises the target by 5 ms and 20 s without one still lowers it, so the latency only grows while underruns keep
  coming. CI now also runs the helper's host test. Not the volume slider: iOS's speaker device (UniXNU-droidvm's
  `DroidVMAudio.cpp`, built into the kernel) publishes no volume control, so iOS pins the slider at the top; that is a
  kernel driver change (ROADMAP).
- `0017-volume-keys-set-the-speaker-level.patch`: since iOS's slider cannot move (above: `DroidVMAudio.cpp` at
  UniXNU-droidvm f9121cd publishes `"controls"` as an empty array and answers SetControlValue, selector 2, with
  kIOReturnBadArgument), the phone's volume keys set the level on the host. xnuhost's input passes each press (value 1,
  not the repeats) of KEY_VOLUMEUP/KEY_VOLUMEDOWN to `br_audio_volume_key` (still sending it to iOS as before);
  `audio/backend.c` keeps 16 steps and scales the speaker's samples on their way to the helper by the square of the
  step's fraction (Q15), starting at 16, which passes them unchanged. Log line: `audio: GalIOS volume N of 16 (the volume
  keys)`. The weak reference keeps a build without the back end linking. The real fix, a control iOS's HAL reads, needs
  IOAudio2's control dictionary as 24A446's HAL parses it (read in the image's shared cache): ROADMAP 29b.

History: galios.4 had a 4 GiB RAM floor and `oneios-actuator=0` and felt worse than galios.3; its log showed the cause
was the P vCPUs' lost cores (0005). galios.6-8 had a RAM floor of their own (3-3.5 GiB) and the touch IC kept awake at
level 0; OneiOS main now fits `ram=auto` to the session's needs (#30) and sleeps the touch IC only at a real display
off (#29, hosted 0069), so galios.9 (OneiOS 3233fed) takes theirs and drops both.

- **0018 iPhone screen features: the screen only.** With the identity from `galios-identity.adt`, xnuhost now takes only the screen properties of the iPhone's `/product` (`oled-display`, `display-corner-radius`, `supports-burnin-mitigation` and those naming the island, notch, always-on or AOD). The guest keeps its own board name, model, target-type and GPU feature sets. Taking the whole iPhone (0013 alone) made iOS choose the iPhone's heaviest rendering paths and its own touch tables: on the S25 Ultra iOS crawled and touch stopped. `galios-identity-full` in the stage brings the whole iPhone back for experiments. OneiOS is pinned (`GalIOS/gpu/ONEIOS_REF`) to the commit the patches were made on, since OneiOS's main moved on 2026-11-15 and six patches no longer apply there.

- **0019 the prime cores, and RAM that fits.** A session log showed vCPUs 6 and 7 refused their prime cores with EINVAL for the whole session (outside the cpuset Android put the session in), so both ran on CPU 0; xnuhost now moves itself once to the root cpuset (`/dev/cpuset`, every core) and places them again. Maximum performance no longer fixes the guest at 10 GiB (only 4.9 GiB fitted: the host swapped iOS's RAM); the RAM stays `ram=auto` and every vCPU keeps the animation boost.
