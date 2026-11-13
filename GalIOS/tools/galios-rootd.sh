#!/system/bin/sh
# GalIOS's root helper, for temporary roots that give root to adb's shell but not to apps.
#
# GalIOS Studio starts it as root over USB (su through adb). It runs until the phone restarts (as temporary root
# does) or until Studio stops it, and lends its root to the GalIOS app: GalIOS writes a script to its own folder
# on the shared storage, Android/data/dev.galios/files/rootq/<id>.sh, which only GalIOS (and adb, and root) can
# write to on Android 11 and newer; this runs it as root and leaves <id>.out (its output) and <id>.rc (its exit
# code) beside it. It touches <id>/alive every second, so GalIOS knows it is there.
#
#   galios-rootd.sh            run (Studio starts it detached: setsid, no terminal)
#   GALIOS_ROOTQ=<dir>         the queue folder (tests); default the GalIOS app's folder
#   GALIOS_ROOTD_STATE=<dir>   where the pid and the stop file live (tests); default /data/local/tmp/galios-bridge
Q=${GALIOS_ROOTQ:-/storage/emulated/0/Android/data/dev.galios/files/rootq}
S=${GALIOS_ROOTD_STATE:-/data/local/tmp/galios-bridge}
mkdir -p "$S" 2>/dev/null
echo $$ > "$S/rootd.pid"
rm -f "$S/rootd.stop"
trap '' HUP
trap 'rm -f "$S/rootd.pid"; exit 0' TERM INT
while :; do
	[ -f "$S/rootd.stop" ] && { rm -f "$S/rootd.stop" "$S/rootd.pid"; exit 0; }
	if [ -d "$Q" ]; then
		: > "$Q/alive" 2>/dev/null && chmod 0666 "$Q/alive" 2>/dev/null
		for f in "$Q"/*.sh; do
			[ -f "$f" ] || break
			id=${f%.sh}
			mv -f "$f" "$id.run" 2>/dev/null || continue
			(
				sh "$id.run" > "$id.out" 2>&1 < /dev/null
				echo $? > "$id.rc.tmp"
				chmod 0666 "$id.out" "$id.rc.tmp" 2>/dev/null
				mv -f "$id.rc.tmp" "$id.rc"
				rm -f "$id.run"
			) &
		done
	fi
	sleep 0.3 2>/dev/null || sleep 1
done
