# GalIOS runtime patches

Small fixes to OneiOS's runtime (xnuhost), applied by `.github/workflows/galios-runtime.yml` on top of OneiOS's
main before `runtime/package.sh`. Each one is skipped when OneiOS already has the fix.

- `0001-fence-table.patch`: xnuhost never made virglrenderer's fence table (virglrenderer.c, which makes it, is not
  built into xnuhost). The first GPU submit that exports a fence (`drm_timeline_submit_fence` → `virgl_fence_set_fd`)
  then read a NULL table, and xnuhost died with `fault on a host thread: signal, address 0xb` right after backboardd
  opened `AGXDeviceUserClient`. The patch makes the table once, when the KGSL renderer opens.
- `0004-touch-stays-awake.patch`: `touch-sleep` defaults to 0, so the touch IC is not put to sleep when iOS's
  brightness reaches 0. On the S25 Ultra the lock screen dimmed and then took no touches.

- `0002-ram-host-floor.patch`: `ram=auto` leaves Android at least 3.5 GiB (OneiOS: 1.5 GiB): iOS gets 7424 MiB on
  the S25 Ultra. iOS fills its RAM with file cache down to about 600 MiB free, and Android's own part (kernel, KGSL,
  display, xnuhost's page tables) took 3.0-3.5 GiB, so Android keeps about (floor - 2.9 GiB): with 1.5 GiB it fell
  to 670 MiB and swapped 1.8 GB; 4 GiB (galios.4) left it 1.2 GiB and iOS never used its compressor; 3 GiB
  (galios.6, galios.7) would leave about 200 MiB once iOS's cache is full.
- `0003-side-key-without-actuator.patch`: `oneios-actuator=0` in the default boot arguments. With it (galios.4) the
  side key locked iOS 0.85 s after the press (`request_power_change ... powerstate 0`); without it the presses
  reached backboardd and nothing followed (OneiOS docs/newtodo.md 2). Haptics did nothing either way.
- `0005-vcpus-keep-their-cores.patch`: every 2 s xnuhost reads each vCPU's affinity and sets it again when it is not
  its plan's (the iOS threads follow by the epoch). On the S25 Ultra, fast-charging, the two P vCPUs could not take
  the prime cores at the start (`vCPU 6 on host CPUs 0x40: Invalid argument`) and ran on the E cores beside the
  pinned vCPUs for the whole session: each vCPU got about a third of a core, single page faults took up to 16 s.

- `0006-side-key-brings-brightness-back.patch`: iOS 27's CoreBrightness sends the slider as `DisplayBrightness`, a
  float that IOKit's serialization hands the driver as the integer 0 (`Brightness number 0x0 ... nothing sent`), and
  after the side key wakes the display it sends no other level, so the panel stayed at its floor (dim after every
  lock). A side-key press while iOS's level is 0 now brings the last lit level back 600 ms later, unless iOS sets one.

- `0007-brightness-dimmest-is-the-panels.patch`: the dimmest light level iOS asks for in a session goes on the
  panel's floor, and the levels up to 10 % of the light are spread from there (above that nothing changes): iOS's
  slider bottom lit the panel well above One UI's dimmest. OneiOS's brightness tests and new ones: 80 checks, 0 failures.
- `0008-animation-boost-finds-the-vcpus.patch`: adds a drm2kgsl patch. drm2kgsl raises the vCPU threads' uclamp.min
  to 1024 while the compositor animates (Droid-VM measured page swipes held back by schedutil at 2.2-2.7 GHz), but it
  looked for crosvm's thread names only (`crosvm_vcpu*`); xnuhost's are `xnu-cpuN`, so the phone logged
  `vCPU uclamp.min 1024: 0 of 0 threads` and the boost never happened.

History: galios.4 had 0002 at 4 GiB and 0003 and felt worse than galios.3; its log showed the cause was the
P vCPUs' lost cores (0005), not those two. galios.5 had only 0001 and 0004.
