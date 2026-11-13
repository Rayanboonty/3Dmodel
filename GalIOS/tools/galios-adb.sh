#!/usr/bin/env bash
# GalIOS from the Ubuntu window (WSL) on Windows: puts iOS 27 on a Galaxy S25 Ultra over USB and boots it, with no
# app on the PC and no GalIOS app needed on the phone. It drives the OneOS app's own boot engine (read from the
# OneOS app installed on the phone, never shipped here), adapts its one S24-only phone check for the S25 Ultra, and
# stages the OneiOS runtime and your image through OneOS's root script, which checks every file's size and SHA-256.
#
#   bash galios-adb.sh check            phone, root, busybox, resetprop, volume keys, free space
#   bash galios-adb.sh install          the boot engine (from the phone's OneOS app), S25 check adapted
#   bash galios-adb.sh runtime [FILE]   the OneiOS runtime: FILE, or the newest from OneiOS's GitHub releases
#   bash galios-adb.sh image [FILE]     your image set (default: OneiOS_iOS27.0.1_24A446_iPhone18-2.tar.zst in
#                                       your Windows Downloads folder)
#   bash galios-adb.sh status           what is staged and whether the boot check passes
#   bash galios-adb.sh boot             start iOS 27 (One UI stops in about 40 s)
#   bash galios-adb.sh return           back to One UI (also: hold Volume Up + Volume Down for 3 s)
#   bash galios-adb.sh logs             copy the session logs to Downloads\galios-logs
#   bash galios-adb.sh all [IMAGE]      check, install, runtime, image, status in one go
#   bash galios-adb.sh remove           delete everything this put on the phone (frees about 11 GB)
#
# Uses Windows' adb.exe (Google's platform-tools, unzipped to C:\platform-tools, or set ADB=/path/to/adb.exe).
# Root on the phone: `su -c` (set GALIOS_SU for another). Free space on the phone: about 25 GB while the image is
# copied (each file is pushed, then copied into the stage, then the pushed copy is deleted).
set -euo pipefail

STAGE=/data/local/tmp/oneios
TMP=/data/local/tmp/galios-push
PKG=com.fahadali.oneos
SU=${GALIOS_SU:-su -c 'sh -s'}
IMAGE_NAME=OneiOS_iOS27.0.1_24A446_iPhone18-2.tar.zst

die() { printf '\n\033[1;31mSTOPPED: %s\033[0m\n' "$*" >&2; exit 1; }
say() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
ok()  { printf '\033[1;32m    %s\033[0m\n' "$*"; }

# ---- the PC side ----------------------------------------------------------------------------------------------
winuser() { cmd.exe /c 'echo %USERNAME%' 2>/dev/null | tr -d '\r' || true; }
DOWNLOADS=${GALIOS_DOWNLOADS:-}
if [ -z "$DOWNLOADS" ]; then
	u=$(winuser)
	if [ -n "$u" ] && [ -d "/mnt/c/Users/$u/Downloads" ]; then DOWNLOADS="/mnt/c/Users/$u/Downloads"; else DOWNLOADS="$HOME"; fi
fi
find_adb() {
	[ -n "${ADB:-}" ] && { echo "$ADB"; return; }
	for c in /mnt/c/platform-tools/adb.exe /mnt/c/Android/platform-tools/adb.exe "$DOWNLOADS/platform-tools/adb.exe" \
		"/mnt/c/Users/$(winuser)/AppData/Local/Android/Sdk/platform-tools/adb.exe"; do
		[ -f "$c" ] && { echo "$c"; return; }
	done
	command -v adb.exe 2>/dev/null || command -v adb 2>/dev/null || true
}
ADB=$(find_adb)
[ -n "$ADB" ] || die "adb not found. Unzip Google's platform-tools so that C:\\platform-tools\\adb.exe exists (guide, part 3)."
adb() { "$ADB" "$@"; }
# A path adb can open: Windows' adb.exe needs a Windows path.
hostpath() { case "$ADB" in *.exe) wslpath -w "$1" ;; *) echo "$1" ;; esac; }
need() { command -v "$1" >/dev/null 2>&1 || die "$1 is missing in Ubuntu: sudo apt-get install -y $2"; }
need python3 python3
sha()  { sha256sum "$1" | cut -d' ' -f1; }
size() { stat -c %s "$1"; }

# ---- the phone side -------------------------------------------------------------------------------------------
phone() {
	local st; st=$(adb get-state 2>&1 | tr -d '\r' || true)
	case "$st" in
	device) ;;
	*unauthorized*) die "on the phone, tap Allow on the USB debugging prompt (tick Always allow), then run this again" ;;
	*more\ than\ one*) die "more than one phone or emulator is connected: unplug the others" ;;
	*) die "no phone found by adb ($st). Plug in the S25 Ultra with USB debugging on (guide, part 4)" ;;
	esac
}
# A script on stdin, run as root on the phone. Its exit code is not used: the @@ line says what happened.
root() { { adb shell -T "$SU" || true; } | tr -d '\r'; }
R() { printf '%s\n' "sh $STAGE/bin/oneios_root.sh $*" | root; }
last() { grep '^@@' | tail -n 1 || true; }
okreply() { printf '%s\n' "$1" | grep -q '^@@OK' || die "the phone said: $(printf '%s\n' "$1" | last | sed 's/^@@[A-Z]* //')"; }
rootcheck() {
	[ "$(printf 'id -u\n' | root)" = 0 ] || die "root is not active: start your temporary root, and allow root for 'Shell' (com.android.shell) in your root app"
	printf 'test -f %s/bin/oneios_root.sh && echo yes\n' "$STAGE" | root | grep -q yes || die "the boot engine is not installed yet: bash $0 install"
}
# Push one local file, then hand it to oneios_root.sh on stdin: send FILE <verb args...>
send() {
	local f=$1; shift
	printf 'mkdir -p %s && chmod 0777 %s\n' "$TMP" "$TMP" | root >/dev/null
	adb push "$(hostpath "$f")" "$TMP/part" </dev/null >/dev/null || die "adb push of $f failed (is the phone still connected?)"
	local out; out=$(printf '%s\n' "sh $STAGE/bin/oneios_root.sh $* < $TMP/part; rm -f $TMP/part" | root)
	printf '%s\n' "$out" | last
	okreply "$out"
}

# ---- the boot engine, from the OneOS app on the phone ----------------------------------------------------------
ENGINE="$DOWNLOADS/galios-engine"
pull_engine() {
	say "Reading the boot engine from the OneOS app on the phone"
	local apk; apk=$(adb shell pm path "$PKG" </dev/null | tr -d '\r' | sed -n 's/^package://p' | grep 'base.apk$' | head -n 1 || true)
	[ -n "$apk" ] || die "the OneOS app (iOS 27 edition, package $PKG) is not installed on the phone: install it first (guide, part 4)"
	rm -rf "$ENGINE" && mkdir -p "$ENGINE"
	adb pull "$apk" "$(hostpath "$ENGINE/oneos.apk")" </dev/null >/dev/null || die "could not copy the OneOS app from the phone"
	python3 - "$ENGINE" <<'PY' || die "this OneOS build has no iOS 27 boot engine: install OneOS's iOS 27 edition"
import os, re, sys, zipfile
d = sys.argv[1]
want = ["root/oneios_root.sh", "oneios/oneios_switch.sh", "oneios/oneios_return.sh",
        "oneios/boot_status.sh", "oneios/OneOSBootStatus.dex", "oneios/oneos_boot_icon.png"]
z = zipfile.ZipFile(os.path.join(d, "oneos.apk"))
names = set(z.namelist())
for w in want:
    if "assets/" + w not in names:
        sys.exit("missing " + w)
    open(os.path.join(d, os.path.basename(w)), "wb").write(z.read("assets/" + w))
# OneOS's switch accepts the S24 Ultra only: replace exactly that check (and refuse if it is not the one known).
p = os.path.join(d, "oneios_switch.sh")
lines = open(p, encoding="utf-8").read().split("\n")
if any(re.match(r"\s*SM-S938\*\)", l) for l in lines):
    print("already accepts the S25 Ultra"); sys.exit(0)
m = next((i for i, l in enumerate(lines) if l.strip().startswith('case "$P_MODEL" in SM-S928*)')
          and l.strip().endswith("esac") and "refuse" in l), -1)
if m < 0 or m + 2 >= len(lines):
    sys.exit("unknown phone check (no SM-S928 line)")
soc, ref = lines[m + 1].strip(), lines[m + 2].strip()
if not (soc.startswith('[ "$P_DEVICE" = e3q ]') and "SM8650" in soc and soc.endswith("||") and ref.startswith("refuse ")):
    sys.exit("unknown codename/SoC check")
for i, l in enumerate(lines):
    if m <= i <= m + 2 or l.strip().startswith("#"):
        continue
    if any(t in l for t in ("SM-S928", '"e3q"', "= e3q", "SM8650")):
        sys.exit("OneOS checks for the S24 Ultra again at its line %d" % (i + 1))
ind = lines[m][:len(lines[m]) - len(lines[m].lstrip())]
new = ["# GalIOS: this phone check accepts the Galaxy S25 Ultra beside the S24 Ultra.",
       'case "$P_MODEL" in',
       '    SM-S928*) [ "$P_DEVICE" = e3q ] && [ "$P_SOC" = SM8650 ] ||',
       '        refuse "iOS on the Galaxy S24 Ultra needs e3q, SM8650; this phone is ${P_DEVICE:-?}, ${P_SOC:-?}" ;;',
       '    SM-S938*) [ "$P_DEVICE" = pa3q ] && [ "$P_SOC" = SM8750 ] ||',
       '        refuse "iOS on the Galaxy S25 Ultra needs pa3q, SM8750; this phone is ${P_DEVICE:-?}, ${P_SOC:-?}" ;;',
       '    *) refuse "iOS runs on the Galaxy S24 Ultra and S25 Ultra only; this phone is ${P_MODEL:-unknown}" ;;',
       "esac"]
lines[m:m + 3] = [ind + x for x in new]
open(p, "w", encoding="utf-8").write("\n".join(lines))
print("S25 Ultra check adapted")
PY
}
stage_scripts() {
	local f
	for f in oneios_switch.sh oneios_return.sh boot_status.sh OneOSBootStatus.dex oneos_boot_icon.png; do
		printf '    %-22s' "$f"
		send "$ENGINE/$f" "script $f $(size "$ENGINE/$f") $(sha "$ENGINE/$f")"
	done
}

# ---- a runtime package or an image set -------------------------------------------------------------------------
unpack() {   # a .tar.zst -> a folder next to it (kept, so a second run skips this); a folder as is
	case "$1" in
	*.tar.zst)
		need zstd zstd
		local d="${1%.tar.zst}.unpacked"
		if [ ! -f "$d/.done" ]; then
			echo "    unpacking $(basename "$1") (the image takes 10-20 minutes) ..." >&2
			rm -rf "$d" && mkdir -p "$d" && zstd -dc "$1" | tar -xf - -C "$d" && touch "$d/.done" ||
				die "could not unpack $1 (damaged, or not enough free space next to it)"
		fi
		echo "$d" ;;
	*) [ -d "$1" ] || die "$1 is neither a .tar.zst nor a folder"; echo "$1" ;;
	esac
}
top_of() {   # the folder holding $2: the top or one level down
	[ -f "$1/$2" ] && { echo "$1"; return; }
	local d; for d in "$1"/*/; do [ -f "$d$2" ] && { echo "${d%/}"; return; }; done
	die "no $2 in $1: this is not a OneiOS archive"
}
stage_kind() {   # kind folder manifest-name -> number of files received, in $COUNT
	local kind=$1 dir=$2 man=$3 out p s h m n=0
	out=$(R "begin $kind"); okreply "$out"
	python3 - "$dir" "$man" > "$dir/.plan" <<'PY' || die "$man is damaged"
import json, os, re, sys
d, man = sys.argv[1:3]
for f in json.load(open(os.path.join(d, man)))["files"]:
    p = f["path"]
    if not re.fullmatch(r"[A-Za-z0-9._+,-]+(/[A-Za-z0-9._+,-]+)*", p) or ".." in p.split("/"):
        sys.exit("bad path " + p)
    print(f'{p}\t{f["size"]}\t{f["sha256"]}\t{f.get("mode", "0644")}')
PY
	local total; total=$(wc -l < "$dir/.plan")
	while IFS=$'\t' read -r p s h m; do
		[ -f "$dir/$p" ] || die "$p is listed in $man but missing from the archive"
		[ "$(size "$dir/$p")" = "$s" ] || die "$p is not the size $man says: the archive is damaged"
		n=$((n + 1))
		printf '    [%d/%d] %s (%s MB) ' "$n" "$total" "$p" "$((s / 1048576))"
		send "$dir/$p" "receive $kind $p $s $h $m"
	done < "$dir/.plan"
	printf '    %s ' "$man"
	send "$dir/$man" "receive $kind $man $(size "$dir/$man") $(sha "$dir/$man") 0644"
	COUNT=$((n + 1))
}
latest_runtime() {
	need curl curl
	say "Downloading the newest OneiOS runtime from GitHub" >&2
	local url name
	url=$(curl -fsSL https://api.github.com/repos/akshaynexus/OneiOS/releases/latest |
		python3 -c 'import json,sys
for a in json.load(sys.stdin)["assets"]:
    n = a["name"]
    if n.startswith("oneios-runtime-") and n.endswith("-arm64.tar.zst"): print(a["browser_download_url"]); break') ||
		die "could not reach GitHub's release list (internet? try again in a minute)"
	[ -n "$url" ] || die "OneiOS's latest release has no runtime package"
	name=$(basename "$url")
	curl -fL --retry 3 -o "$DOWNLOADS/$name" "$url" >&2 && curl -fsSL -o "$DOWNLOADS/$name.sha256" "$url.sha256" ||
		die "the runtime download failed"
	[ "$(cut -d' ' -f1 < "$DOWNLOADS/$name.sha256")" = "$(sha "$DOWNLOADS/$name")" ] || die "the runtime download is damaged: run again"
	ok "$name (checked)" >&2
	echo "$DOWNLOADS/$name"
}

cmd_check() {
	phone
	say "The phone"
	printf '%s\n' '
echo "model    $(getprop ro.product.model) / $(getprop ro.product.device) / $(getprop ro.soc.model)   (want SM-S938x / pa3q / SM8750)"
echo "android  $(getprop ro.build.version.release), $(getprop ro.build.display.id)"
echo "root     uid=$(id -u)   (want 0)"
bb=MISSING; for c in /data/adb/ksu/bin/busybox /data/adb/ap/bin/busybox /data/adb/magisk/busybox; do [ -x "$c" ] && bb=$c && break; done
rp=MISSING; for c in /data/adb/ksu/bin/resetprop /data/adb/ap/bin/resetprop /debug_ramdisk/resetprop /sbin/resetprop; do [ -x "$c" ] && rp=$c && break; done
echo "busybox  $bb"
echo "resetprop $rp"
k=$(getevent -pl 2>/dev/null)
v=MISSING; case "$k" in *KEY_VOLUMEUP*) case "$k" in *KEY_VOLUMEDOWN*) v=ok ;; esac ;; esac
echo "volume keys $v   (the way back uses them)"
echo "free     $(df -h /data | tail -n 1 | awk "{print \$4}") on /data   (want 25G or more)"
' | root
	adb shell "dumpsys package $PKG | grep -m1 versionName" </dev/null | tr -d '\r' | sed 's/^ */OneOS    /' | grep . ||
		echo "OneOS    NOT INSTALLED (install OneOS's iOS 27 edition)"
}
cmd_install() {
	phone
	[ "$(printf 'id -u\n' | root)" = 0 ] || die "root is not active: start your temporary root, and allow root for 'Shell' (com.android.shell) in your root app"
	pull_engine
	say "Installing the boot engine on the phone"
	local rs="$ENGINE/oneios_root.sh"
	printf 'mkdir -p %s && chmod 0777 %s\n' "$TMP" "$TMP" | root >/dev/null
	adb push "$(hostpath "$rs")" "$TMP/oneios_root.sh" </dev/null >/dev/null || die "adb push failed"
	local out; out=$(printf '%s\n' "umask 077
S=$STAGE; F=\$S/bin/oneios_root.sh
if awk -v s=\"\$S\" '\$5 == s || index(\$5, s \"/\") == 1 { f = 1 } END { exit !f }' /proc/1/mountinfo /proc/self/mountinfo; then
  echo '@@REFUSED something is mounted in the stage: restart the phone, start root again, run this again'; exit 2
fi
[ -d \"\$S\" ] || mkdir \"\$S\" || { echo '@@FAILED could not make the stage'; exit 4; }
chown 0:0 \"\$S\" && chmod 0700 \"\$S\" && { [ -d \"\$S/bin\" ] || mkdir \"\$S/bin\"; } && chown 0:0 \"\$S/bin\" && chmod 0700 \"\$S/bin\" || { echo '@@FAILED stage'; exit 4; }
cp $TMP/oneios_root.sh \"\$F.new\" && rm -f $TMP/oneios_root.sh
[ \"\$(sha256sum \"\$F.new\" | cut -d' ' -f1)\" = $(sha "$rs") ] || { rm -f \"\$F.new\"; echo '@@REFUSED the root script did not arrive intact'; exit 2; }
chown 0:0 \"\$F.new\" && chmod 0700 \"\$F.new\" && mv -f \"\$F.new\" \"\$F\" && echo '@@OK root script installed'" | root)
	printf '%s\n' "$out" | last; okreply "$out"
	stage_scripts
	ok "Boot engine installed. Next: bash $0 runtime"
}
cmd_runtime() {
	phone; rootcheck
	local f=${1:-}
	[ -n "$f" ] || f=$(latest_runtime)
	say "Adding the OneiOS runtime: $(basename "$f")"
	local d; d=$(top_of "$(unpack "$f")" runtime.json)
	stage_kind runtime "$d" runtime.json
	local args; args=$(python3 -c 'import json,sys; j=json.load(open(sys.argv[1]))
print(j["version"], j["session_abi"], j["xnuhost"], ",".join(str(x) for x in j["image_formats"]), 1 if j.get("placeholder") else 0)' "$d/runtime.json")
	local out; out=$(R "finish runtime $COUNT $args"); printf '%s\n' "$out" | last; okreply "$out"
	ok "Runtime on the phone. Next: bash $0 image"
}
cmd_image() {
	phone; rootcheck
	local f=${1:-$DOWNLOADS/$IMAGE_NAME}
	[ -e "$f" ] || die "no image at $f: build it first (guide, part 2), or give its path"
	say "Adding your iOS 27 image: $(basename "$f") (about 11 GB over USB: 10-30 minutes)"
	local d; d=$(top_of "$(unpack "$f")" manifest.json)
	stage_kind image "$d" manifest.json
	local args; args=$(python3 -c 'import json,sys; j=json.load(open(sys.argv[1])); print(j["format"], j["version"], j["build"])' "$d/manifest.json")
	local out; out=$(R "finish image $COUNT $args"); printf '%s\n' "$out" | last; okreply "$out"
	ok "Image on the phone. You can delete the folder $(hostpath "$d") now to free space on the PC."
	ok "Next: bash $0 status"
}
cmd_status() {
	phone; rootcheck
	say "What is on the phone"
	local out; out=$(R status)
	printf '%s\n' "$out" | sed -n 's/^@@RESULT /    /p'
	if printf '%s\n' "$out" | grep -q '^@@RESULT ready 1'; then ok "Boot check passed. Next: bash $0 boot"
	else printf '\033[1;33m    Not ready: see the "ready 0" line above.\033[0m\n'; fi
}
cmd_boot() {
	phone; rootcheck
	[ -f "$ENGINE/oneios_switch.sh" ] || pull_engine
	say "Staging the session scripts again (the S25 Ultra check adapted)"
	stage_scripts
	say "Booting iOS 27"
	echo "    One UI stops in about 40 seconds; iOS's first picture comes about 80 seconds after that."
	echo "    To come back: hold Volume Up + Volume Down together for 3 seconds (or: bash $0 return)."
	local out; out=$(R "switch 1 1"); printf '%s\n' "$out" | last; okreply "$out"
	ok "Started. If it does not reach iOS, come back with the volume keys, then: bash $0 logs"
}
cmd_return() {
	phone
	printf '%s\n' "sh $STAGE/bin/oneios_root.sh return" | root | last
	ok "Asked. One UI is back in about 45 seconds."
}
cmd_logs() {
	phone; rootcheck
	local d; d="$DOWNLOADS/galios-logs/$(date +%Y%m%d-%H%M%S)"; mkdir -p "$d"
	say "Copying the session logs to $(hostpath "$d")"
	R logs > "$d/summary.txt" || true
	printf '%s\n' "cat $STAGE/session/xnuhost.log 2>/dev/null || cat \$(ls -t $STAGE/logs/*xnuhost* 2>/dev/null | head -n 1) 2>/dev/null" | root > "$d/xnuhost.log" || true
	printf '%s\n' "for f in $STAGE/*.log $STAGE/logs/*.log; do [ -f \"\$f\" ] && { echo \"===== \$f\"; tail -n 400 \"\$f\"; }; done" | root > "$d/switch-and-return.log" || true
	ls -l "$d"
	ok "Send me summary.txt and the last 50 lines of switch-and-return.log."
}

cmd_remove() {
	phone; rootcheck
	local out; out=$(R remove); printf '%s\n' "$out" | last; okreply "$out"
	printf 'rm -rf %s\n' "$TMP" | root >/dev/null
	ok "Removed from the phone."
}

case "${1:-}" in
check) cmd_check ;;
remove) cmd_remove ;;
install) cmd_install ;;
runtime) cmd_runtime "${2:-}" ;;
image) cmd_image "${2:-}" ;;
status) cmd_status ;;
boot | switch) cmd_boot ;;
return) cmd_return ;;
logs | log) cmd_logs ;;
all) cmd_check; cmd_install; cmd_runtime ""; cmd_image "${2:-}"; cmd_status ;;
*) sed -n '2,21p' "$0"; exit 2 ;;
esac
