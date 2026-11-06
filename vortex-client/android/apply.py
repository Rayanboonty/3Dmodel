#!/usr/bin/env python3
"""Turn an Amethyst-Android checkout into Vortex Client for Android.

Usage: python3 apply.py /path/to/amethyst-android

Copies overlay/ over the app module and makes a few small source edits:
the Vortex name, package id, icon, colours, fonts, the Launch button, and
performance defaults. Safe to run more than once.
"""
import pathlib
import re
import shutil
import sys

HERE = pathlib.Path(__file__).resolve().parent
ROOT = pathlib.Path(sys.argv[1]).resolve()
APP = ROOT / "app_pojavlauncher"
MAIN = APP / "src" / "main"
RES = MAIN / "res"
JAVA = MAIN / "java"
PACKAGE = "dev.vortexclient.android"


def edit(path, pairs, regex=False):
    p = pathlib.Path(path)
    s = p.read_text(encoding="utf-8")
    for a, b in pairs:
        if b in s:  # already applied
            continue
        if regex:
            s2 = re.sub(a, b, s, count=1, flags=re.S)
        else:
            s2 = s.replace(a, b, 1) if a in s else s
        if s2 == s and b not in s:
            raise SystemExit(f"{p.name}: could not find {a[:70]!r}")
        s = s2
    p.write_text(s, encoding="utf-8")


# 1. Resources: copy the overlay; drop the old icons it replaces.
for icon in RES.glob("mipmap-*/ic_launcher*.webp"):
    icon.unlink()
shutil.copytree(HERE / "overlay" / "res", RES, dirs_exist_ok=True)
for d in RES.glob("mipmap-*dpi"):
    fg = d / "ic_launcher.png"
    if fg.exists():
        shutil.copyfile(fg, d / "ic_launcher_round.png")

# 2. Name, package id and the Launch label.
edit(RES / "values" / "strings.xml", [
    ('<string name="app_name" translatable="false">Amethyst</string>', '<string name="app_name" translatable="false">Vortex Client</string>'),
    ('<string name="app_short_name" translatable="false">Amethyst</string>', '<string name="app_short_name" translatable="false">Vortex</string>'),
    ('<string name="main_play">Play</string>', '<string name="main_play">Launch game</string>'),
])
gradle = APP / "build.gradle"
s = gradle.read_text(encoding="utf-8").replace("org.angelauramc.amethyst", PACKAGE)
# The build types set the visible name too.
s = s.replace('"Amethyst (Debug)"', '"Vortex (Debug)"').replace('resValue "string", "app_name", "Amethyst"', 'resValue "string", "app_name", "Vortex Client"')
s = s.replace('resValue "string", "app_short_name", "Amethyst"', 'resValue "string", "app_short_name", "Vortex"')
# Release builds: sign with your own key when VORTEX_KEYSTORE is set,
# otherwise with the project's debug key so anyone can build and install.
if "VORTEX_KEYSTORE" not in s:
    s = s.replace('''    signingConfigs {
        customDebug {''', '''    signingConfigs {
        vortexRelease {
            storeFile System.getenv("VORTEX_KEYSTORE") ? file(System.getenv("VORTEX_KEYSTORE")) : file("debug.keystore")
            storePassword System.getenv("VORTEX_KEYSTORE") ? System.getenv("VORTEX_KEYSTORE_PASSWORD") : "android"
            keyAlias System.getenv("VORTEX_KEYSTORE") ? System.getenv("VORTEX_KEY_ALIAS") : "androiddebugkey"
            keyPassword System.getenv("VORTEX_KEYSTORE") ? System.getenv("VORTEX_KEYSTORE_PASSWORD") : "android"
        }
        customDebug {''', 1)
    s = s.replace("            signingConfig signingConfigs.customRelease", "            signingConfig signingConfigs.vortexRelease", 1)
# Smaller downloads: one APK per CPU type (arm64 covers nearly every phone)
# plus a universal one.
if "splits {" not in s:
    s = s.replace("    namespace 'net.kdt.pojavlaunch'", """    namespace 'net.kdt.pojavlaunch'
    splits {
        abi {
            enable true
            reset()
            include 'arm64-v8a', 'armeabi-v7a', 'x86_64'
            universalApk true
        }
    }""", 1)
gradle.write_text(s, encoding="utf-8")

# 3. Colours: the Vortex ground and amber accent.
edit(RES / "values" / "colors.xml", [
    ('<color name="minebutton_color">#9649b8</color>', '<color name="minebutton_color">#FFB547</color>'),
    ('<color name="background_app">#181818</color>', '<color name="background_app">#0D0A12</color>'),
    ('<color name="background_overlay">#464646</color>', '<color name="background_overlay">#2E2638</color>'),
    ('<color name="background_status_bar">#242424</color>', '<color name="background_status_bar">#E6140E1C</color>'),
    ('<color name="background_bottom_bar">#232323</color>', '<color name="background_bottom_bar">#D9120C18</color>'),
])

# 4. The Launch button: amber pill, heavy Outfit, dark amber text.
edit(JAVA / "com" / "kdt" / "mcgui" / "MineButton.java", [
    ('setTypeface(ResourcesCompat.getFont(getContext(), R.font.noto_sans_bold));',
     'setTypeface(ResourcesCompat.getFont(getContext(), R.font.vortex_outfit_black));\n\t\tsetTextColor(0xFF2B1600);\n\t\tsetLetterSpacing(0.06f);'),
    ('R.drawable.mine_button_background', 'R.drawable.vortex_play'),
])

# 5. Performance defaults.
#    a) A low-pause G1 collector unless the player picked a collector.
edit(JAVA / "net" / "kdt" / "pojavlaunch" / "utils" / "JREUtils.java", [
    ('        //Add automatically generated args\n',
     '''        // Vortex: smoother frame times with a low-pause G1 collector, unless
        // the player chose a garbage collector in their own JVM arguments.
        boolean vortexChoseGc = false;
        for (String a : userArgs) if (a.startsWith("-XX:+Use") && a.endsWith("GC")) vortexChoseGc = true;
        if (!vortexChoseGc) {
            userArgs.add("-XX:+UnlockExperimentalVMOptions");
            userArgs.add("-XX:+UseG1GC");
            userArgs.add("-XX:MaxGCPauseMillis=40");
            userArgs.add("-XX:G1NewSizePercent=20");
            userArgs.add("-XX:G1ReservePercent=20");
            userArgs.add("-XX:+ParallelRefProcEnabled");
            userArgs.add("-XX:+DisableExplicitGC");
        }

        //Add automatically generated args
'''),
])
#    b) Pin the game to the big CPU cores on capable phones by default.
edit(JAVA / "net" / "kdt" / "pojavlaunch" / "prefs" / "LauncherPreferences.java", [
    ('PREF_BIG_CORE_AFFINITY = DEFAULT_PREF.getBoolean("bigCoreAffinity", false);',
     'PREF_BIG_CORE_AFFINITY = DEFAULT_PREF.getBoolean("bigCoreAffinity", isDevicePowerful);'),
])

# 6. The Vortex interface. The launcher screen becomes the same interface as
#    the Windows app (served from the APK's assets into a WebView), talking to
#    Amethyst through a native bridge. Amethyst's own screens stay for
#    Microsoft sign-in, advanced settings, touch controls and installers.
UI = HERE.parent / "crates" / "vortex-app" / "ui"
assets = MAIN / "assets" / "vortex"
if assets.exists():
    shutil.rmtree(assets)
shutil.copytree(UI, assets)
shutil.copytree(HERE / "overlay" / "java", JAVA, dirs_exist_ok=True)
launcher = JAVA / "net" / "kdt" / "pojavlaunch" / "LauncherActivity.java"
V = "net.kdt.pojavlaunch.vortex.VortexWeb"
if V not in launcher.read_text(encoding="utf-8"):
    edit(launcher, [
        ("        bindViews();\n", f"        bindViews();\n        {V}.attach(this);\n"),
        ("        mInstallTracker.attach();\n", f"        mInstallTracker.attach();\n        {V}.onResume(this);\n"),
        ("        mInstallTracker.detach();\n", f"        mInstallTracker.detach();\n        {V}.onPause(this);\n"),
        ("        super.onDestroy();\n        mProgressLayout.cleanUpObservers();",
         f"        super.onDestroy();\n        {V}.onDestroy(this);\n        mProgressLayout.cleanUpObservers();"),
        ("    public void onBackPressed() {\n",
         f"    public void onBackPressed() {{\n        if ({V}.onBack(this, this::finish)) return;\n"),
    ])
# Turning the phone shouldn't restart the launcher (and reload the interface).
manifest = MAIN / "AndroidManifest.xml"
m = manifest.read_text(encoding="utf-8")
LA = '            android:name=".LauncherActivity"\n'
if LA + "            android:configChanges" not in m:
    m = m.replace(LA, LA + '            android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|keyboard|navigation|uiMode"\n', 1)
    manifest.write_text(m, encoding="utf-8")

# Updates: the app downloads the new APK from the release and hands it to
# Android's installer, which needs a permission and a file provider.
m = manifest.read_text(encoding="utf-8")
if "REQUEST_INSTALL_PACKAGES" not in m:
    m = m.replace("<application", '<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />\n\n    <application', 1)
if ".vortexupdate" not in m:
    m = m.replace("    </application>", '''        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.vortexupdate"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/vortex_update_paths" />
        </provider>
    </application>''', 1)
manifest.write_text(m, encoding="utf-8")

# The app's version is the launcher's version (vortex-client/Cargo.toml), so
# the Android app and Windows releases line up and updates compare right.
cargo = (HERE.parent / "Cargo.toml").read_text(encoding="utf-8")
ver = re.search(r'^version = "([^"]+)"', cargo, re.M).group(1)
parts = [int(x) for x in (re.findall(r"\d+", ver) + ["0", "0", "0"])[:3]]
code = 10000000 + parts[0] * 10000 + parts[1] * 100 + parts[2]
g = gradle.read_text(encoding="utf-8")
g = re.sub(r"versionCode \d+", f"versionCode {code}", g, count=1)
g = re.sub(r"versionName (getVersionName\(\)|\"[^\"]*\")", f'versionName "{ver}"', g, count=1)
gradle.write_text(g, encoding="utf-8")

# 7. No "Amethyst" left in the app's own text (crash screen, notifications);
#    the credit stays in the About text below.
for strings in RES.glob("values*/strings.xml"):
    t = strings.read_text(encoding="utf-8")
    if "Amethyst" in t:
        strings.write_text(t.replace("Amethyst", "Vortex Client"), encoding="utf-8")

# 8. Credit where it's due (LGPL-3.0): keep Amethyst's licence and say so.
about = MAIN / "assets" / "about_en.txt"
if about.exists():
    t = about.read_text(encoding="utf-8")
    note = "Vortex Client for Android is built on Amethyst (LGPL-3.0) by AngelAuraMC and the PojavLauncher team.\n\n"
    if not t.startswith(note):
        about.write_text(note + t, encoding="utf-8")

print(f"Vortex applied to {ROOT}")
