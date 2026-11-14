# GalIOS runtime patches

Small fixes to OneiOS's runtime (xnuhost), applied by `.github/workflows/galios-runtime.yml` on top of OneiOS's
main before `runtime/package.sh`. Each one is skipped when OneiOS already has the fix.

- `0001-fence-table.patch`: xnuhost never made virglrenderer's fence table (virglrenderer.c, which makes it, is not
  built into xnuhost). The first GPU submit that exports a fence (`drm_timeline_submit_fence` → `virgl_fence_set_fd`)
  then read a NULL table, and xnuhost died with `fault on a host thread: signal, address 0xb` right after backboardd
  opened `AGXDeviceUserClient`. The patch makes the table once, when the KGSL renderer opens.
- `0004-touch-stays-awake.patch`: `touch-sleep` defaults to 0, so the touch IC is not put to sleep when iOS's
  brightness reaches 0. On the S25 Ultra the lock screen dimmed and then took no touches.

Tried and taken out again (galios.4, 2026-11-14): a 4 GiB floor for Android (iOS 6912 MiB) and `oneios-actuator=0`.
With them every tap took up to 20 s on the phone, worse than galios.3, so galios.5 is back to OneiOS's RAM split and
boot arguments.
