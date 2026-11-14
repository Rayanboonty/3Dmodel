# GalIOS runtime patches

Small fixes to OneiOS's runtime (xnuhost), applied by `.github/workflows/galios-runtime.yml` on top of OneiOS's
main before `runtime/package.sh`. Each one is skipped when OneiOS already has the fix.

- `0001-fence-table.patch`: xnuhost never made virglrenderer's fence table (virglrenderer.c, which makes it, is not
  built into xnuhost). The first GPU submit that exports a fence (`drm_timeline_submit_fence` → `virgl_fence_set_fd`)
  then read a NULL table, and xnuhost died with `fault on a host thread: signal, address 0xb` right after backboardd
  opened `AGXDeviceUserClient`. The patch makes the table once, when the KGSL renderer opens.
- `0002-ram-host-floor.patch`: `ram=auto` leaves Android at least 4 GiB (OneiOS: 1.5 GiB). On the S25 Ultra (11 GiB
  seen by Android) iOS got 9472 MiB and, with the GPU on, Android fell to 670 MiB available and swapped 1.8 GB,
  which made everything lag; iOS now gets 6912 MiB.
- `0003-side-key-without-actuator.patch`: adds `oneios-actuator=0` to the default boot arguments, the test OneiOS's
  `docs/newtodo.md` (2) names for the side key doing nothing or acting late since the Actuator (button haptics) was
  added. Haptics did nothing on the phone anyway (`0 requests to the motor`).
