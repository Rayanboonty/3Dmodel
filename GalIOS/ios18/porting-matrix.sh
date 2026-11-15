#!/usr/bin/env bash
# GalaxyIOS iOS 18, stage 0: the kernel porting matrix.
#
#   porting-matrix.sh XNU_DIR ONEIOS_DIR [Y2Q_DIR] > report.md
#
# XNU_DIR is Apple's published xnu-11417 source (iPadOS/iOS 18). On it, in order:
#   1. UniXNU-y2q's iPadOS 18 series (patches/xnu-ipados18): the board layer that boots iPadOS 18.7.10 (22H374) on a
#      Snapdragon phone, bare metal. It is the iOS 18 counterpart of the Droid-VM board layer iOS 27 uses here.
#   2. OneiOS's own kernel patches (kernel/device/patches/xnu-12377: the three oneios-*.patch).
#   3. OneiOS's hosted machine layer (kernel/patches/oneios-hosted/series): what makes XNU run as a process beside
#      Android under xnuhost, which is how GalaxyIOS boots iOS on a locked S25 Ultra.
# Each patch is applied if it applies (cleanly or by a 3-way merge) and committed, so the next one sees it; one that
# does not is reported and left out. The report is the list of what carries over to xnu-11417 and what must be ported.
# Nothing is built, nothing from Apple's firmware is used: source code only.
set -u
X=$(cd "$1" && pwd)
O=$(cd "$2" && pwd)
Y=""
[ $# -ge 3 ] && [ -d "$3" ] && Y=$(cd "$3" && pwd)

cd "$X"
git config user.email ci@galaxyios.invalid
git config user.name "GalaxyIOS CI"
git config core.autocrlf false

applied=0 merged=0 port=0
try() {   # $1: the patch file. Prints one table row; returns 0 when it went in.
  local p=$1 name files
  name=$(basename "$p")
  files=$(grep -c '^diff --git' "$p" 2>/dev/null || echo 0)
  if git apply --check --whitespace=nowarn "$p" 2>/dev/null && git apply --whitespace=nowarn "$p"; then
    git add -A && git commit -qm "$name" --allow-empty
    echo "| \`$name\` | applies | $files |"; applied=$((applied + 1)); return 0
  fi
  if git apply --3way --whitespace=nowarn "$p" >/dev/null 2>&1 && ! git diff --name-only --diff-filter=U | grep -q .; then
    git add -A && git commit -qm "$name (3-way)" --allow-empty
    echo "| \`$name\` | applies with a 3-way merge | $files |"; merged=$((merged + 1)); return 0
  fi
  git reset -q --hard; git clean -qfd
  local why
  why=$(git apply --check --whitespace=nowarn "$p" 2>&1 | sed -n 's/^error: //p' | sed 's/|/\\|/g' | head -n 3 | tr '\n' ';')
  echo "| \`$name\` | **to port**: ${why:-does not apply} | $files |"; port=$((port + 1)); return 1
}
series() { grep -v '^[[:space:]]*#' "$1" | sed '/^[[:space:]]*$/d'; }

echo "# GalaxyIOS iOS 18: kernel porting matrix"
echo
echo "Base: \`$(git describe --tags --always 2>/dev/null)\` (Apple's published XNU source). OneiOS at \`$(git -C "$O" rev-parse --short HEAD)\`."
echo

echo "## 1. The iPadOS 18 board layer (UniXNU-y2q)"
echo
if [ -n "$Y" ]; then
  dir="$Y/patches/xnu-ipados18"
  echo "UniXNU-y2q at \`$(git -C "$Y" rev-parse --short HEAD 2>/dev/null)\`, \`patches/xnu-ipados18\`."
  echo
  echo "| Patch | Result | Files |"
  echo "| --- | --- | --- |"
  if [ -f "$dir/series" ]; then list=$(series "$dir/series" | sed "s|^|$dir/|"); else list=$(ls "$dir"/*.patch 2>/dev/null | sort); fi
  [ -n "$list" ] || echo "| (none found in $dir) | - | - |"
  for p in $list; do [ -f "$p" ] && try "$p"; done
else
  echo "Not fetched: the iPadOS 18 board layer is missing from this run."
fi
y_applied=$applied y_merged=$merged y_port=$port
applied=0 merged=0 port=0
echo

echo "## 2. OneiOS's own kernel patches"
echo
echo "| Patch | Result | Files |"
echo "| --- | --- | --- |"
for p in "$O"/kernel/device/patches/xnu-12377/oneios-*.patch; do [ -f "$p" ] && try "$p"; done
d_applied=$applied d_merged=$merged d_port=$port
applied=0 merged=0 port=0
echo

echo "## 3. The hosted machine layer (XNU as a process beside Android)"
echo
echo "| Patch | Result | Files |"
echo "| --- | --- | --- |"
h=$O/kernel/patches/oneios-hosted
for n in $(series "$h/series"); do [ -f "$h/$n" ] && try "$h/$n"; done
echo
echo "## Summary"
echo
echo "| Layer | Applies | 3-way | To port |"
echo "| --- | --- | --- | --- |"
echo "| iPadOS 18 board (y2q) | $y_applied | $y_merged | $y_port |"
echo "| OneiOS own | $d_applied | $d_merged | $d_port |"
echo "| Hosted machine layer | $applied | $merged | $port |"
echo
echo "A patch \"to port\" is not lost work: it says which xnu-12377 change has to be rewritten against xnu-11417's code."
