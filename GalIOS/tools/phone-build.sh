#!/system/bin/sh
# Build the iOS 27 image set on the phone itself, as root, without a PC. GalIOS runs this; it can also be run by hand.
#
#   sh phone-build.sh start     set up Ubuntu 24.04 (arm64) in $GALIOS_HOME and run build-ios-image-wsl.sh in it,
#                               detached; returns at once
#   sh phone-build.sh status    state=..., step=..., exit=..., then the log's last lines as "| ..."
#   sh phone-build.sh stop      end a running build (finished steps are kept: start again resumes)
#   sh phone-build.sh log       the whole log (last 3000 lines) as "| ..."
#
# Env for start:
#   ROOTFS_TAR    ubuntu-base-24.04.x-base-arm64.tar.gz (GalIOS downloads it and checks its SHA-256); only needed
#                 the first time, while the Ubuntu folder is not set up yet
#   BUILD_SCRIPT  build-ios-image-wsl.sh (the same script a PC runs)
#   IPSW          optional: the .ipsw on the phone (/sdcard/...); without it the parts are downloaded from Apple
#   GALIOS_HOME   default /data/local/galios-build (on /data: a real Linux file system, needs about 70 GB)
#   STORAGE       default /data/media/0: the phone's internal storage, seen as /sdcard inside Ubuntu
#
# Nothing here changes Android: every mount is made in a private mount namespace (unshare -m) and disappears
# with the build's process; /dev inside Ubuntu is its own small tmpfs with a few device nodes bound in.
# The finished archive is copied to the Download folder. Keep it to yourself: it is built from Apple's firmware.

H="${GALIOS_HOME:-/data/local/galios-build}"
R="$H/rootfs"
STORAGE="${STORAGE:-/data/media/0}"

say() { echo "$*"; }
die() { echo "@@ERROR $*"; exit 1; }

# A busybox for unshare/mount/setsid: the root manager's (as OneOS uses), else the system's own tools.
BB=""
for c in /data/adb/ksu/bin/busybox /data/adb/ap/bin/busybox /data/adb/magisk/busybox; do
  [ -x "$c" ] && { BB="$c"; break; }
done
run() { if [ -n "$BB" ]; then "$BB" "$@"; else "$@"; fi; }

pid_alive() {
  p=$(cat "$H/pid" 2>/dev/null) || return 1
  [ -n "$p" ] && [ -d "/proc/$p" ]
}

state() {
  if pid_alive; then echo running
  elif [ -f "$H/exit" ]; then
    case "$(cat "$H/exit")" in 0) echo done ;; stopped) echo stopped ;; *) echo failed ;; esac
  else echo idle
  fi
}

do_status() {
  echo "state=$(state)"
  echo "step=$(head -n 1 "$R/galios/progress" 2>/dev/null)"
  echo "exit=$(cat "$H/exit" 2>/dev/null)"
  echo "started=$(cat "$H/started" 2>/dev/null)"
  [ -f "$H/build.log" ] && tail -n 40 "$H/build.log" | clean
  echo "@@OK status"
}

# The log without terminal colours, each line as "| ...". Only tr and a plain sed: Android's toybox has both,
# but its sed does not know \x escapes (1.5 lost the whole log to that).
clean() { tr -d '\033\r' | sed -e 's/\[[0-9;]*m//g' -e 's/^/| /'; }

do_log() {
  [ -f "$H/build.log" ] && tail -n 3000 "$H/build.log" | clean
  echo "@@OK log"
}

# Every process running inside Ubuntu: its root directory is the Ubuntu folder.
# Mounts under the Ubuntu folder left by an earlier run that ended without its cleanup (deepest first).
stale_mounts() { awk -v r="$R" '$2 == r || index($2, r "/") == 1 { print $2 }' /proc/mounts 2>/dev/null | sort -r; }
unmount_stale() {
  for t in $(stale_mounts); do umount -l "$t" 2>/dev/null || umount "$t" 2>/dev/null; done
}

inside_pids() {
  for d in /proc/[0-9]*; do
    [ "$(readlink "$d/root" 2>/dev/null)" = "$R" ] && echo "${d#/proc/}"
  done
}

do_stop() {
  if pid_alive || [ -n "$(inside_pids)" ]; then
    p=$(cat "$H/pid" 2>/dev/null)
    r=$(cat "$H/runner.pid" 2>/dev/null)
    # The build inside Ubuntu, the runner (its trap undoes the mounts) and the detached shell; not only a process
    # group, which depends on how setsid behaved.
    for q in $(inside_pids) $r $p; do kill -TERM "$q" 2>/dev/null; done
    [ -n "$p" ] && kill -TERM -"$p" 2>/dev/null
    i=0; while { [ -n "$(inside_pids)" ] || { [ -n "$r" ] && [ -d "/proc/$r" ]; }; } && [ $i -lt 20 ]; do sleep 1; i=$((i + 1)); done
    for q in $(inside_pids) $r $p; do kill -KILL "$q" 2>/dev/null; done
    sleep 1
    echo stopped > "$H/exit"
    echo "stopped" > "$R/galios/progress" 2>/dev/null
  fi
  unmount_stale
  rm -f "$H/pid" "$H/runner.pid"
  echo "@@OK stop"
}

# The path of a file in the phone's storage, as Ubuntu sees it (/sdcard is the storage there).
inside_path() {
  case "$1" in
    /sdcard/*) echo "/sdcard/${1#/sdcard/}" ;;
    /storage/emulated/0/*) echo "/sdcard/${1#/storage/emulated/0/}" ;;
    /storage/self/primary/*) echo "/sdcard/${1#/storage/self/primary/}" ;;
    "$STORAGE"/*) echo "/sdcard/${1#"$STORAGE"/}" ;;
    *) return 1 ;;
  esac
}

do_start() {
  [ "$(id -u)" = 0 ] || die "needs root"
  pid_alive && die "a build is already running"
  [ -n "$(inside_pids)" ] && die "something is still running inside Ubuntu: tap Stop first"
  [ -f "${BUILD_SCRIPT:-}" ] || die "no build script (BUILD_SCRIPT)"
  mkdir -p "$H" || die "cannot make $H"

  # 1. Ubuntu, once.
  if [ ! -f "$R/.galios-ok" ]; then
    [ -f "${ROOTFS_TAR:-}" ] || die "Ubuntu is not set up and no ROOTFS_TAR was given"
    say "Setting up Ubuntu in $R"
    rm -rf "$R" && mkdir -p "$R" || die "cannot make $R"
    run tar -xzf "$ROOTFS_TAR" -C "$R" || die "could not unpack $ROOTFS_TAR"
    [ -x "$R/bin/bash" ] || [ -x "$R/usr/bin/bash" ] || die "the archive is not an Ubuntu base system"
    touch "$R/.galios-ok"
  fi
  # Every start: network names, apt as root (Android lets only some users open sockets), the build script.
  rm -f "$R/etc/resolv.conf"
  printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' > "$R/etc/resolv.conf"
  grep -q localhost "$R/etc/hosts" 2>/dev/null || printf '127.0.0.1 localhost\n::1 localhost\n' > "$R/etc/hosts"
  mkdir -p "$R/etc/apt/apt.conf.d" "$R/galios" "$R/sdcard" "$R/root" "$R/tmp"
  chmod 1777 "$R/tmp"
  echo 'APT::Sandbox::User "root";' > "$R/etc/apt/apt.conf.d/99galios"
  cp "$BUILD_SCRIPT" "$R/galios/build.sh" || die "cannot copy the build script"
  # Test hosts only: an extra CA for an HTTPS proxy (ca-certificates picks it up when it is installed).
  if [ -n "${GALIOS_EXTRA_CA:-}" ] && [ -f "$GALIOS_EXTRA_CA" ]; then
    mkdir -p "$R/usr/local/share/ca-certificates" && cp "$GALIOS_EXTRA_CA" "$R/usr/local/share/ca-certificates/galios-extra.crt"
  fi

  args="--download"
  if [ -n "${IPSW:-}" ]; then
    [ -f "$IPSW" ] || die "no file at $IPSW"
    args=$(inside_path "$IPSW") || die "the .ipsw must be in the phone's storage (Download, Documents, ...): $IPSW"
  fi
  # BUILD_ARGS replaces the arguments (tests: --setup-only).
  args="${BUILD_ARGS:-$args}"
  # Proxy settings to pass into Ubuntu (test hosts); empty on the phone.
  passenv=""
  for v in http_proxy https_proxy HTTP_PROXY HTTPS_PROXY no_proxy NO_PROXY CARGO_HTTP_CAINFO SSL_CERT_FILE GITHUB_TOKEN GALIOS_MIN_FREE_GB; do
    eval "val=\${$v:-}"
    [ -n "$val" ] && passenv="$passenv $v=$val"
  done

  # 2. How to get a private mount namespace on this phone. Best: unshare with --propagation private (busybox,
  # util-linux). Android's own toybox unshare has no --propagation: then run.sh makes / private itself. Either way
  # run.sh records each mount and unmounts it when the build ends, so nothing is left in Android's view.
  if [ -n "${GALIOS_FORCE_UNSHARE:-}" ]; then umode="$GALIOS_FORCE_UNSHARE"
  elif [ -n "$BB" ] && "$BB" unshare --help 2>&1 | grep -q -- '--propagation'; then umode=busybox
  elif command -v unshare >/dev/null 2>&1 && unshare --help 2>&1 | grep -q -- '--propagation'; then umode=system
  elif [ -n "$BB" ] && "$BB" unshare --help >/dev/null 2>&1; then umode=busybox-plain
  elif command -v unshare >/dev/null 2>&1; then umode=system-plain
  else umode=none
  fi
  case "$umode" in
    busybox) US="$BB unshare -m --propagation private" ;;
    system) US="unshare -m --propagation private" ;;
    busybox-plain) US="$BB unshare -m" ;;
    system-plain) US="unshare -m" ;;
    unchanged) US="unshare -m --propagation unchanged" ;;   # tests: like a toybox unshare
    *) US="" ;;
  esac
  if [ -n "$BB" ]; then SS="$BB setsid"; elif command -v setsid >/dev/null 2>&1; then SS="setsid"; else SS=""; fi

  # 3. The runner: traced, every mount recorded and undone at the end; a small /dev; then the build in Ubuntu.
  cat > "$H/run.sh" <<EOF
R='$R'
MOUNTED=""
mnt() { t="\$1"; shift; if mount "\$@" "\$t"; then MOUNTED="\$t \$MOUNTED"; echo "mounted \${t#\$R}"; else echo "FAILED: mount \$* \${t#\$R}"; return 1; fi; }
cleanup() { set +e; for t in \$MOUNTED; do umount -l "\$t" 2>/dev/null || umount "\$t" 2>/dev/null; done; MOUNTED=""; }
echo \$\$ > '$H/runner.pid'
echo "== setting up Ubuntu's mounts (private to this build)"
trap cleanup EXIT
trap 'cleanup; exit 143' TERM INT HUP
set -e
mount --make-rprivate / 2>/dev/null && echo "mount namespace made private" || echo "note: could not make / private; mounts are undone at the end instead"
# Ubuntu's / must be a mount point: bwrap (the kernel build's sandbox) makes / a slave mount first and fails with
# "Failed to make / slave" on a plain chroot directory (1.7 on an S25 Ultra). So the folder is bound onto itself,
# before anything is mounted inside it.
mnt "\$R" -o bind "\$R" || mnt "\$R" --bind "\$R"
# Fresh /proc, /sys and a small /dev; where the kernel refuses one, the phone's own is bound in instead.
mnt "\$R/proc" -t proc proc || mnt "\$R/proc" -o bind /proc
mnt "\$R/sys" -t sysfs sysfs || mnt "\$R/sys" -o bind /sys
if mnt "\$R/dev" -t tmpfs -o mode=0755,size=64m tmpfs; then
  # The device nodes Ubuntu needs, made as nodes (root may mknod on this tmpfs). Not bind mounts of single files:
  # Android's toybox mount takes "-o bind /dev/null" for a disk image and fails at losetup (1.6 on an S25 Ultra).
  for spec in "null 1 3" "zero 1 5" "full 1 7" "random 1 8" "urandom 1 9" "tty 5 0" "fuse 10 229"; do
    set -- \$spec
    [ -e "/dev/\$1" ] || continue
    if mknod -m 666 "\$R/dev/\$1" c "\$2" "\$3" 2>/dev/null; then echo "made /dev/\$1"
    else touch "\$R/dev/\$1"; mnt "\$R/dev/\$1" --bind "/dev/\$1" || mnt "\$R/dev/\$1" -o bind "/dev/\$1"
    fi
  done
else
  mnt "\$R/dev" -o bind /dev
fi
mkdir -p "\$R/dev/pts" "\$R/dev/shm"
mnt "\$R/dev/pts" -t devpts -o newinstance,ptmxmode=0666 devpts || true
[ -e "\$R/dev/pts/ptmx" ] && ln -sf pts/ptmx "\$R/dev/ptmx"
mnt "\$R/dev/shm" -t tmpfs -o mode=1777 tmpfs
ln -sf /proc/self/fd "\$R/dev/fd"; ln -sf /proc/self/fd/0 "\$R/dev/stdin"
ln -sf /proc/self/fd/1 "\$R/dev/stdout"; ln -sf /proc/self/fd/2 "\$R/dev/stderr"
mnt "\$R/sdcard" -o bind '$STORAGE'
echo "== Ubuntu is ready; starting the build"
chroot "\$R" /usr/bin/env -i HOME=/root TERM=dumb LANG=C.UTF-8 \\
  PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \\
  GALIOS_CHROOT=1 GALIOS_WORK=/root/oneios-build GALIOS_OUT_DIR=/sdcard/Download GALIOS_PROGRESS=/galios/progress $passenv \\
  /bin/bash /galios/build.sh $args
EOF
  unmount_stale
  rm -f "$H/exit" "$H/pid"
  echo "starting" > "$R/galios/progress"
  date +%s > "$H/started"
  # What this phone's root looks like, first in the log: most start problems are explained by these lines.
  {
    echo "== GalIOS phone build, $(date)"
    echo "root: uid $(id -u), SELinux $(getenforce 2>/dev/null || echo '?'), context $(tr -d '\0' < /proc/self/attr/current 2>/dev/null)"
    echo "kernel: $(uname -r) ($(uname -m))"
    echo "busybox: ${BB:-none}; unshare: $umode; setsid: ${SS:-none}"
    echo "tools: chroot=$(command -v chroot) mount=$(command -v mount) unshare=$(command -v unshare)"
    echo "free on /data: $(df -h /data 2>/dev/null | tail -n 1)"
    echo "ubuntu: $(cat "$R/etc/lsb-release" 2>/dev/null | tr '\n' ' ')"
  } > "$H/build.log" 2>&1
  # Detached: its own session, so GalIOS (and a closed screen) can go away; the exit code is kept for status.
  # The detached shell writes its own pid (setsid may fork, so $! would not always be the session leader).
  $SS sh -c "echo \$\$ > '$H/pid'; $US sh '$H/run.sh' >> '$H/build.log' 2>&1; echo \$? > '$H/exit'" </dev/null >/dev/null 2>&1 &
  i=0; while [ ! -s "$H/pid" ] && [ $i -lt 50 ]; do sleep 0.1; i=$((i + 1)); done
  [ -s "$H/pid" ] || die "the build did not start"
  echo "@@OK start"
}

case "${1:-}" in
  start) do_start ;;
  stop) do_stop ;;
  status) do_status ;;
  log) do_log ;;
  *) sed -n '2,20p' "$0"; exit 2 ;;
esac
