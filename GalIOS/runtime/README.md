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
- `0009-android-keeps-2-gib.patch`: `ram-session-reserve` 2 GiB (OneiOS: 1 GiB). With OneiOS's fit and 1 GiB the
  galios.10 session started at 8958 MiB available, gave iOS 6912 MiB, and Android fell to 740-900 MiB and swapped
  320 MiB of iOS's RAM (swap-ins up to 5 MB/s). With 2 GiB iOS gets about 6144 MiB and Android keeps about 1.5 GiB.

History: galios.4 had a 4 GiB RAM floor and `oneios-actuator=0` and felt worse than galios.3; its log showed the cause
was the P vCPUs' lost cores (0005). galios.6-8 had a RAM floor of their own (3-3.5 GiB) and the touch IC kept awake at
level 0; OneiOS main now fits `ram=auto` to the session's needs (#30) and sleeps the touch IC only at a real display
off (#29, hosted 0069), so galios.9 (OneiOS 3233fed) takes theirs and drops both.
