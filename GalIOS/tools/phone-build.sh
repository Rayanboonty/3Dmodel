#!/system/bin/sh
# Build the iOS 27 image set on the phone itself, as root, without a PC. GalIOS runs this; it can also be run by hand.
#
#   sh phone-build.sh start     set up Ubuntu 24.04 (arm64) in $GALIOS_HOME and run build-ios-image-wsl.sh in it,
#                               detached; returns at once
#   sh phone-build.sh status    state=..., step=..., exit=..., then the log's last lines as "| ..."
#   sh phone-build.sh stop      end a running build (finished steps are kept: start again resumes)
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
  [ -f "$H/build.log" ] && tail -n 40 "$H/build.log" | sed -e 's/\x1b\[[0-9;]*m//g' -e 's/^/| /'
  echo "@@OK status"
}

do_stop() {
  if pid_alive; then
    p=$(cat "$H/pid")
    # The runner is a session leader (setsid): end its whole group, then anything left.
    kill -TERM -"$p" 2>/dev/null || kill -TERM "$p" 2>/dev/null
    i=0; while [ -d "/proc/$p" ] && [ $i -lt 20 ]; do sleep 1; i=$((i + 1)); done
    kill -KILL -"$p" 2>/dev/null; kill -KILL "$p" 2>/dev/null
    echo stopped > "$H/exit"
    echo "stopped" > "$R/galios/progress" 2>/dev/null
  fi
  rm -f "$H/pid"
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
  for v in http_proxy https_proxy HTTP_PROXY HTTPS_PROXY no_proxy NO_PROXY; do
    eval "val=\${$v:-}"
    [ -n "$val" ] && passenv="$passenv $v=$val"
  done

  # 2. The runner: private mounts, a small /dev, then the build in Ubuntu.
  cat > "$H/run.sh" <<EOF
set -e
R='$R'
mount -t proc proc "\$R/proc"
mount -t sysfs sysfs "\$R/sys"
mount -t tmpfs -o mode=0755,size=64m tmpfs "\$R/dev"
for n in null zero full random urandom tty fuse; do
  [ -e "/dev/\$n" ] || continue
  touch "\$R/dev/\$n" && mount -o bind "/dev/\$n" "\$R/dev/\$n"
done
mkdir -p "\$R/dev/pts" "\$R/dev/shm"
mount -t devpts -o newinstance,ptmxmode=0666 devpts "\$R/dev/pts" 2>/dev/null || true
[ -e "\$R/dev/pts/ptmx" ] && ln -sf pts/ptmx "\$R/dev/ptmx"
mount -t tmpfs -o mode=1777 tmpfs "\$R/dev/shm"
ln -sf /proc/self/fd "\$R/dev/fd"; ln -sf /proc/self/fd/0 "\$R/dev/stdin"
ln -sf /proc/self/fd/1 "\$R/dev/stdout"; ln -sf /proc/self/fd/2 "\$R/dev/stderr"
mount -o bind '$STORAGE' "\$R/sdcard"
exec chroot "\$R" /usr/bin/env -i HOME=/root TERM=dumb LANG=C.UTF-8 \\
  PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \\
  GALIOS_WORK=/root/oneios-build GALIOS_OUT_DIR=/sdcard/Download GALIOS_PROGRESS=/galios/progress $passenv \\
  /bin/bash /galios/build.sh $args
EOF
  rm -f "$H/exit" "$H/pid"
  echo "starting" > "$R/galios/progress"
  date +%s > "$H/started"
  # Detached: its own session, so GalIOS (and a closed screen) can go away; the exit code is kept for status.
  if [ -n "$BB" ]; then US="$BB unshare"; SS="$BB setsid"; else US="unshare"; SS="setsid"; fi
  # The detached shell writes its own pid (setsid may fork, so $! would not always be the session leader).
  $SS sh -c "echo \$\$ > '$H/pid'; $US -m --propagation private sh '$H/run.sh' > '$H/build.log' 2>&1; echo \$? > '$H/exit'" </dev/null >/dev/null 2>&1 &
  i=0; while [ ! -s "$H/pid" ] && [ $i -lt 50 ]; do sleep 0.1; i=$((i + 1)); done
  [ -s "$H/pid" ] || die "the build did not start"
  echo "@@OK start"
}

case "${1:-}" in
  start) do_start ;;
  stop) do_stop ;;
  status) do_status ;;
  *) sed -n '2,20p' "$0"; exit 2 ;;
esac
