# Vortex Client for Android

Play Minecraft: Java Edition on Android with the Vortex look.

This is built on [Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android)
(the successor of PojavLauncher), which does the hard part: a Java runtime and
LWJGL ported to Android, OpenGL translation layers, touch and gamepad controls,
Microsoft sign-in, Fabric, Forge and Modrinth. Vortex adds its own design and
some performance defaults on top. Amethyst is licensed under the GNU LGPL 3.0,
and so is this Android app; its licence ships inside the APK and the full
source is here and upstream.

## What Vortex changes

- **Look:** the Golden Meadow wallpaper behind the home screen, the VC logo,
  frosted tiles, a frosted bottom sheet with the instance picker, and an amber
  **Launch game** button in the Outfit typeface. Presses dip in and spring
  back. Separate layouts for portrait and landscape.
- **Name and icon:** "Vortex Client", package `dev.vortexclient.android`
  (it installs alongside Amethyst), the VC app icon (adaptive on Android 8+).
- **Performance:**
  - A low-pause G1 garbage collector by default (fewer stutters), unless you
    set your own collector in the JVM arguments.
  - The game runs on the big CPU cores by default on capable phones.
  - Amethyst's own tuning stays on: render scale and sustained performance
    chosen by how powerful the phone is.

The first launch downloads the Java runtime for your phone's CPU from
Amethyst's servers, then the game files from Mojang.

## Build

```bash
export ANDROID_HOME=/path/to/android-sdk   # with NDK 27.3.13750724
./build.sh            # release APKs -> out/Vortex-Client-android-{arm64,armv7,x86_64,universal}.apk
./build.sh debug      # debug APK
```

You need git, Python 3, JDK 21 (to run Gradle) and JDK 8 (one helper module
targets it). Release builds are signed with the project's debug key so anyone
can build and install them. To sign with your own key (needed to publish
updates that install over each other), set `VORTEX_KEYSTORE`,
`VORTEX_KEYSTORE_PASSWORD` and `VORTEX_KEY_ALIAS`.

## Files

- `apply.py`: applies the Vortex changes to an Amethyst checkout.
- `overlay/res/`: Vortex layouts, drawables, colours, fonts (Outfit, OFL; see
  `OFL-Outfit.txt`),
  icons and the wallpaper.
- `build.sh`: fetches Amethyst at the pinned commit, applies the overlay and
  builds.
