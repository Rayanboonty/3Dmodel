#!/bin/sh
# Build GalIOS Studio: bundles the image build script (../tools), the GalIOS APK (../app release build) and the art
# (../app res), then compiles Studio for Windows (GalIOS-Studio.exe) and for this machine (galios-studio, for tests).
set -eu
cd "$(dirname "$0")"
cp ../tools/build-ios-image-wsl.sh assets/build-ios-image-wsl.sh
cp ../app/build/outputs/apk/release/app-release.apk assets/GalIOS.apk
cp ../app/src/main/res/drawable-nodpi/bg_nebula.jpg ui/bg.jpg
cp ../app/src/main/res/drawable-nodpi/glow_orb.png ui/orb.png
mkdir -p dist
CGO_ENABLED=0 GOOS=windows GOARCH=amd64 go build -trimpath -ldflags "-s -w -H windowsgui" -o dist/GalIOS-Studio.exe .
CGO_ENABLED=0 go build -trimpath -o dist/galios-studio .
ls -la dist
