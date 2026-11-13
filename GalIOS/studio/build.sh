#!/bin/sh
# Build GalIOS Studio: bundles the image build script (../tools), the GalIOS APK (../app release build) and the art
# (../app res), then compiles Studio for Windows (GalIOS-Studio.exe) and for this machine (galios-studio, for tests).
# The exe's icon, version and manifest (DPI-aware, no elevation) come from winres/ (go-winres make).
set -eu
cd "$(dirname "$0")"
mkdir -p assets dist
cp ../tools/build-ios-image-wsl.sh assets/build-ios-image-wsl.sh
cp ../tools/galios-rootd.sh assets/galios-rootd.sh
cp ../app/build/outputs/apk/release/app-release.apk assets/GalIOS.apk
cp ../app/src/main/res/drawable-nodpi/bg_nebula.jpg ui/bg.jpg
cp ../app/src/main/res/drawable-nodpi/glow_orb.png ui/orb.png
# The GalIOS inside must be the one Studio expects (its root bridge).
want=$(sed -n 's/.*bundledGalIOSVersion = "\(.*\)"/\1/p' adb.go)
have=$(sed -n 's/.*versionName = "\(.*\)"/\1/p' ../app/build.gradle.kts)
[ "$want" = "$have" ] || { echo "adb.go expects GalIOS $want, the app is $have" >&2; exit 1; }
if command -v go-winres >/dev/null 2>&1; then go-winres make --in winres/winres.json --arch amd64; fi
CGO_ENABLED=0 GOOS=windows GOARCH=amd64 go build -trimpath -ldflags "-s -w -H windowsgui" -o dist/GalIOS-Studio.exe .
CGO_ENABLED=0 go build -trimpath -o dist/galios-studio .
ls -la dist
