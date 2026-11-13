#!/usr/bin/env bash
# Build your own iOS 27 image set for GalIOS on a Windows PC, inside WSL2 (Ubuntu 24.04), from the .ipsw you
# downloaded. It runs OneiOS's own scripts (github.com/akshaynexus/OneiOS, docs/building.md sections 1-3):
#
#   your .ipsw  ->  fetch (decrypts the OS images)  ->  kernel inputs  ->  XNU kernel  ->  kernel.kc  ->  image set
#   -> OneiOS_iOS27.0.1_24A446_iPhone18-2.tar.zst (about 11 GB), copied to your Windows Downloads folder.
#
# Usage, in the Ubuntu window:
#   bash build-ios-image-wsl.sh "C:\Users\You\Downloads\iPhone18,2_27.0.1_24A446_Restore.ipsw"
#   bash build-ios-image-wsl.sh /mnt/c/Users/You/Downloads/iPhone18,2_27.0.1_24A446_Restore.ipsw
#   bash build-ios-image-wsl.sh --setup-only        # install and build the tools, then stop
#   bash build-ios-image-wsl.sh --download          # no .ipsw: read the needed parts from Apple's servers (10.6 GB)
#
# GalIOS runs this same script on the phone itself (inside an Ubuntu chroot, see phone-build.sh) with
#   GALIOS_PROGRESS=<file>   the current step is written there, and "done" or "failed: <step>" at the end
#   GALIOS_OUT_DIR=<dir>     the finished archive is copied there (the phone's Download folder)
#
# The IPSW must be the one OneiOS pins: iPhone 17 Pro Max (iPhone18,2), iOS 27.0.1, build 24A446. Any other is
# refused by OneiOS's checks. Run it again after any stop: finished steps are skipped (markers in ~/oneios-build).
# Needs: about 60 GB free inside WSL, internet (Apple's key service decrypts the OS images; sources from GitHub).
# The result stays on your PC. Never upload or share it: it is built from Apple's firmware.
set -euo pipefail

WORK="${GALIOS_WORK:-$HOME/oneios-build}"
ONEIOS="$WORK/OneiOS"
PROFILE="profiles/ios-27.0.1-iPhone18,2.toml"
ARCHIVE="OneiOS_iOS27.0.1_24A446_iPhone18-2.tar.zst"
LIBDISPATCH_TAG="swift-6.0.3-RELEASE"
APFS_FUSE_COMMIT="66b86bd525e8cb90f9012543be89b1f092b75cf3"
BWRAP_TAG="v0.11.2"

STEP="starting"
progress() { [ -z "${GALIOS_PROGRESS:-}" ] || printf '%s\n' "$*" > "$GALIOS_PROGRESS" 2>/dev/null || true; }
say()  { STEP="$*"; progress "$*"; printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
ok()   { printf '\033[1;32m    %s\033[0m\n' "$*"; }
die()  { progress "failed: $*"; printf '\n\033[1;31mSTOPPED: %s\033[0m\n' "$*" >&2; exit 1; }
done_mark() { touch "$WORK/.done-$1"; }
is_done()   { [ -f "$WORK/.done-$1" ]; }
SUDO=""; [ "$(id -u)" = 0 ] || SUDO="sudo"
# Any failing command ends the run with the step it was in; finished steps are kept, so running again resumes.
trap 'rc=$?; progress "failed: $STEP (exit $rc)"; printf "\n\033[1;31mSTOPPED during: %s (exit %s).\033[0m\nRun the same command again to continue from here. If it stops at the same place, send the last 20 lines above.\n" "$STEP" "$rc" >&2' ERR

setup_only=0
download=0
ipsw_arg=""
for a in "$@"; do
  case "$a" in
    --setup-only) setup_only=1 ;;
    --download) download=1 ;;
    -h|--help) sed -n '2,19p' "$0"; exit 0 ;;
    *) ipsw_arg="$a" ;;
  esac
done
[ "$setup_only" = 1 ] || [ "$download" = 1 ] || [ -n "$ipsw_arg" ] || { sed -n '2,19p' "$0"; die "give the path of your .ipsw, or --download"; }

mkdir -p "$WORK"

# ---------------------------------------------------------------------------------------------------------------
# 0. Checks: Linux (WSL2), space, and the IPSW
# ---------------------------------------------------------------------------------------------------------------
[ "$(uname -s)" = Linux ] || die "run this inside Ubuntu (WSL2), not in Windows PowerShell"
if grep -qi microsoft /proc/version 2>/dev/null; then
  ok "WSL detected"
  [ -e /dev/fuse ] || die "/dev/fuse is missing: update WSL from PowerShell with 'wsl --update', then 'wsl --shutdown' and reopen Ubuntu"
fi
free_gb=$(df -Pk "$WORK" | awk 'NR==2 {print int($4/1048576)}')
[ "$free_gb" -ge "${GALIOS_MIN_FREE_GB:-60}" ] || [ "$setup_only" = 1 ] || die "only ${free_gb} GB free; about 60 GB are needed (on a PC: free space on drive C:; on the phone: internal storage)"
ok "${free_gb} GB free"

IPSW=""
if [ "$setup_only" = 0 ] && [ "$download" = 0 ]; then
  case "$ipsw_arg" in
    [A-Za-z]:\\*|[A-Za-z]:/*) command -v wslpath >/dev/null || die "wslpath missing: give the path as /mnt/c/..."
                              IPSW=$(wslpath -u "$ipsw_arg") ;;
    *) IPSW="$ipsw_arg" ;;
  esac
  [ -f "$IPSW" ] || die "no file at $IPSW"
  case "$(basename "$IPSW")" in
    iPhone18,2_27.0.1_24A446_Restore.ipsw) ok "IPSW: $(basename "$IPSW") ($(du -h "$IPSW" | cut -f1))" ;;
    *) printf '\033[1;33m    The file is named %s, not iPhone18,2_27.0.1_24A446_Restore.ipsw.\n    OneiOS checks the contents and will stop if it is not iOS 27.0.1 (24A446) for iPhone18,2.\033[0m\n' "$(basename "$IPSW")" ;;
  esac
  # Reading 10 GB through /mnt/c is slow; a copy inside WSL is much faster for the many range reads.
  if [[ "$IPSW" == /mnt/* ]]; then
    local_copy="$WORK/$(basename "$IPSW")"
    if [ ! -f "$local_copy" ] || [ "$(stat -c %s "$local_copy")" != "$(stat -c %s "$IPSW")" ]; then
      say "Copying the IPSW into WSL (faster to read there; a few minutes)"
      cp "$IPSW" "$local_copy.part" && mv "$local_copy.part" "$local_copy"
    fi
    IPSW="$local_copy"
  fi
fi

# ---------------------------------------------------------------------------------------------------------------
# 1. Packages
# ---------------------------------------------------------------------------------------------------------------
PACKAGES=(build-essential clang lld llvm git make cmake ninja-build python3 python3-venv flex bison m4 autoconf
  automake pkg-config bubblewrap libblocksruntime-dev zstd tmux curl patch unzip xz-utils ca-certificates
  fuse3 libfuse3-dev zlib1g-dev libbz2-dev libattr1-dev libicu-dev lzfse liblzfse-dev meson libcap-dev)
# Checked on every run (not a done-marker): a newer version of this script may need packages an older run did not.
missing=()
for p in "${PACKAGES[@]}"; do dpkg-query -W -f='${Status}' "$p" 2>/dev/null | grep -q "install ok installed" || missing+=("$p"); done
if [ ${#missing[@]} -gt 0 ]; then
  say "Installing build packages: ${missing[*]}${SUDO:+ (asks for your Ubuntu password)}"
  $SUDO apt-get update -q
  $SUDO env DEBIAN_FRONTEND=noninteractive apt-get install -y -q "${missing[@]}"
fi
ok "build packages installed"
python3 -c 'import sys; sys.exit(0 if sys.version_info >= (3, 11) else 1)' || die "python3 3.11 or newer is needed"

# ---------------------------------------------------------------------------------------------------------------
# 2. libdispatch (the kernel's Apple-style linker needs it; Ubuntu 24.04 has no package)
# ---------------------------------------------------------------------------------------------------------------
if [ ! -e /usr/include/dispatch/dispatch.h ] || ! ldconfig -p | grep -q 'libdispatch\.so '; then
  say "Building libdispatch ($LIBDISPATCH_TAG)"
  rm -rf "$WORK/src/libdispatch"
  git clone -q --depth 1 -b "$LIBDISPATCH_TAG" https://github.com/swiftlang/swift-corelibs-libdispatch.git "$WORK/src/libdispatch"
  cmake -S "$WORK/src/libdispatch" -B "$WORK/src/libdispatch/build" -G Ninja -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_C_COMPILER=clang -DCMAKE_CXX_COMPILER=clang++ -DCMAKE_INSTALL_PREFIX=/usr -DBUILD_TESTING=OFF >/dev/null
  ninja -C "$WORK/src/libdispatch/build"
  $SUDO ninja -C "$WORK/src/libdispatch/build" install >/dev/null
  $SUDO ldconfig
fi
ok "libdispatch: $(ldconfig -p | awk '/libdispatch\.so /{print $NF; exit}')"

# ---------------------------------------------------------------------------------------------------------------
# 3. apfs-fuse (reads the iOS disk images without root; not packaged for Ubuntu)
# ---------------------------------------------------------------------------------------------------------------
if ! command -v apfs-fuse >/dev/null; then
  say "Building apfs-fuse"
  rm -rf "$WORK/src/apfs-fuse"
  git clone -q https://github.com/sgan81/apfs-fuse.git "$WORK/src/apfs-fuse"
  git -C "$WORK/src/apfs-fuse" checkout -q "$APFS_FUSE_COMMIT"
  git -C "$WORK/src/apfs-fuse" submodule update -q --init --recursive
  cmake -S "$WORK/src/apfs-fuse" -B "$WORK/src/apfs-fuse/build" -DCMAKE_BUILD_TYPE=Release -DUSE_FUSE3=ON >/dev/null
  make -C "$WORK/src/apfs-fuse/build" -j"$(nproc)" >/dev/null
  $SUDO install -m 0755 "$WORK/src/apfs-fuse/build/apfs-fuse" /usr/local/bin/apfs-fuse
fi
ok "apfs-fuse: $(command -v apfs-fuse)"

# ---------------------------------------------------------------------------------------------------------------
# 3b. bubblewrap 0.10 or newer: the kernel build overlays /usr/bin with --overlay-src/--tmp-overlay, which Ubuntu
#     24.04's bubblewrap 0.9.0 does not have. Built into /usr/local/bin, which comes before /usr/bin on PATH.
# ---------------------------------------------------------------------------------------------------------------
bwrap_ok() { command -v bwrap >/dev/null && bwrap --help 2>&1 | grep -q -- '--overlay-src'; }
if ! bwrap_ok; then
  say "Building bubblewrap ${BWRAP_TAG#v} (the kernel build needs 0.10 or newer)"
  rm -rf "$WORK/src/bubblewrap"
  git clone -q --depth 1 -b "$BWRAP_TAG" https://github.com/containers/bubblewrap.git "$WORK/src/bubblewrap"
  meson setup "$WORK/src/bubblewrap/build" "$WORK/src/bubblewrap" --prefix=/usr/local \
    -Dman=disabled -Dselinux=disabled -Dtests=false >/dev/null
  ninja -C "$WORK/src/bubblewrap/build" >/dev/null
  $SUDO ninja -C "$WORK/src/bubblewrap/build" install >/dev/null
  hash -r
fi
bwrap_ok || die "bubblewrap with --overlay-src is still not first on PATH ($(command -v bwrap))"
ok "bubblewrap: $(bwrap --version) at $(command -v bwrap)"

# ---------------------------------------------------------------------------------------------------------------
# 4. Rust (OneiOS's oneios-tool builds the kernel collection)
# ---------------------------------------------------------------------------------------------------------------
[ -f "$HOME/.cargo/env" ] && . "$HOME/.cargo/env"
if ! command -v cargo >/dev/null; then
  say "Installing Rust"
  curl -fsSL https://sh.rustup.rs | sh -s -- -y --profile minimal
  . "$HOME/.cargo/env"
fi
ok "rust: $(rustc --version)"

# ---------------------------------------------------------------------------------------------------------------
# 5. OneiOS and its references
# ---------------------------------------------------------------------------------------------------------------
if [ ! -d "$ONEIOS/.git" ]; then
  say "Getting OneiOS"
  git clone -q https://github.com/akshaynexus/OneiOS.git "$ONEIOS"
else
  git -C "$ONEIOS" pull -q --ff-only || echo "    (could not update OneiOS; using the copy already here)"
fi
cd "$ONEIOS"
if ! is_done references; then
  say "Getting OneiOS's source references (UniXNU and the drivers it builds)"
  scripts/setup/get-references.sh
  done_mark references
fi
# The kernel's own sources at OneiOS's pinned commits (kernel/pins.sh). kernel/build-hosted.sh does not fetch them
# itself (only build-linux.sh does), so on a fresh checkout its base, Droid-VM-UniXNU, would be missing.
if ! is_done kernel-sources || [ ! -d external/Droid-VM-UniXNU ]; then
  say "Getting the kernel's pinned sources"
  kernel/fetch.sh
  done_mark kernel-sources
fi
if [ ! -x out/bin/ipsw ]; then
  say "Getting the ipsw tool (decrypts Apple's OS images)"
  scripts/fetch/get-ipsw-cli.sh
fi
ok "OneiOS ready: $(git log -1 --format='%h %cs')"

if [ "$setup_only" = 1 ]; then
  say "Setup done. Run again with the path of your .ipsw to build the image."
  exit 0
fi

# ---------------------------------------------------------------------------------------------------------------
# 6. The build (OneiOS docs/building.md sections 1-3)
# ---------------------------------------------------------------------------------------------------------------
if ! is_done fetch; then
  if [ -n "$IPSW" ]; then
    say "Reading the IPSW and decrypting the OS images (10.6 GB; asks Apple's key service for the keys)"
  else
    say "Downloading iOS 27.0.1 from Apple and decrypting the OS images (10.6 GB)"
  fi
  scripts/fetch/fetch.sh ${IPSW:+--source "$IPSW"} "$PROFILE"
  done_mark fetch
fi
if ! is_done kernel-inputs; then
  say "Kernel inputs (Apple's kernelcache and device tree from the IPSW)"
  scripts/image/kernel-inputs.sh "$PROFILE"
  done_mark kernel-inputs
fi
if ! is_done kernel; then
  say "Building the XNU kernel (10-20 minutes the first time)"
  if [ -n "${GALIOS_CHROOT:-}" ]; then
    # On the phone (GalIOS's own Ubuntu): the kernel build puts four helpers (xcrun, plutil, codesign, csh) in
    # /usr/bin through a bwrap overlay mount, which Android's /data refuses ("Can't make overlay mount", GalIOS 1.8
    # on an S25 Ultra). This Ubuntu is GalIOS's alone, so they are installed there for good instead; build-hosted.sh
    # then finds them in place and skips bwrap. The same toolchain steps build-hosted.sh runs first come first here.
    kernel/linux/setup-toolchain.sh
    TC="$ONEIOS/out/toolchain"
    [ -x "$TC/unixnu/CommandLineTools/usr/bin/xnu-host-cc" ] && [ "$TC/unixnu" -nt kernel/linux/clang-wrapper ] || kernel/linux/unixnu-layout.sh
    for t in xcrun plutil codesign csh; do install -m 0755 "$TC/unixnu/usrbin/$t" "/usr/bin/$t"; done
    ok "kernel helpers installed in this Ubuntu's /usr/bin (no overlay needed)"
  fi
  env HOSTED_OUT=galios XNU_FILESET_REFERENCE=1 kernel/build-hosted.sh
  done_mark kernel
fi
if ! is_done prelink; then
  say "Building the kernel collection (kernel.kc)"
  env KERNEL=out/kernel/current-hosted-galios/xnu.macho REFERENCE=out/kernel/current-hosted-galios/reference.macho \
    kernel/prelink/build.sh
  done_mark prelink
fi
if ! is_done image; then
  say "Building the image set (about 11 GB)"
  scripts/image/build.sh "$PROFILE" --kernel out/kernel/24A446/kernel.kc
  done_mark image
fi
say "Checking the archive"
scripts/image/verify.sh "out/image/$ARCHIVE"

# ---------------------------------------------------------------------------------------------------------------
# 7. Hand it to Windows
# ---------------------------------------------------------------------------------------------------------------
dest="${GALIOS_OUT_DIR:-}"
if [ -z "$dest" ] && command -v cmd.exe >/dev/null 2>&1; then
  winuser=$(cmd.exe /c "echo %USERNAME%" 2>/dev/null | tr -d '\r')
  [ -n "$winuser" ] && [ -d "/mnt/c/Users/$winuser/Downloads" ] && dest="/mnt/c/Users/$winuser/Downloads"
fi
if [ -n "$dest" ]; then
  say "Copying the image to $dest"
  mkdir -p "$dest"
  cp "out/image/$ARCHIVE.sha256" "$dest/"
  cp "out/image/$ARCHIVE" "$dest/$ARCHIVE.part" && mv -f "$dest/$ARCHIVE.part" "$dest/$ARCHIVE"
  ok "$dest/$ARCHIVE"
  [ -z "${winuser:-}" ] || ok "Windows path: C:\\Users\\$winuser\\Downloads\\$ARCHIVE"
else
  ok "The image is at $ONEIOS/out/image/$ARCHIVE"
fi
cat <<EOF

Done. Next, on the phone:
  1. Copy $ARCHIVE to the phone's Download folder (USB cable, File Explorer: Phone > Internal storage > Download).
  2. Open GalIOS > Setup > iOS 27 image: it appears under "Found on this phone". Tap Import.
Keep this file to yourself: it is built from Apple's firmware.
EOF
progress "done"
