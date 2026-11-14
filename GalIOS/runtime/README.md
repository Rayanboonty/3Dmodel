# GalIOS runtime patches

Small fixes to OneiOS's runtime (xnuhost), applied by `.github/workflows/galios-runtime.yml` on top of OneiOS's
main before `runtime/package.sh`. Each one is skipped when OneiOS already has the fix.

- `0001-fence-table.patch`: xnuhost never made virglrenderer's fence table (virglrenderer.c, which makes it, is not
  built into xnuhost). The first GPU submit that exports a fence (`drm_timeline_submit_fence` → `virgl_fence_set_fd`)
  then read a NULL table, and xnuhost died with `fault on a host thread: signal, address 0xb` right after backboardd
  opened `AGXDeviceUserClient`. The patch makes the table once, when the KGSL renderer opens.
