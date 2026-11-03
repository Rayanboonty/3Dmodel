# Vortex Client

A Minecraft: Java Edition launcher written in Rust, with a pixel-art
**"Blockworks × Dawn"** interface. It installs and runs **every version Mojang has
published** (releases, snapshots, betas and alphas, from `rd-132211` in 2009 to the
latest snapshot) plus **Fabric** and **Forge**. Windows is the main target, and Linux and
macOS work too.

## The app

- **Living pixel sky.** A pixel sunrise is the default. You can also follow your clock
  (dawn → day → dusk → night) or lock any phase. It has a sun and moon on an arc,
  drifting clouds, three parallax hill layers with pine trees, birds, cherry petals,
  fireflies and shooting stars.
- **Chunky pixel UI.** Notched pixel borders, 3D buttons that press down, an XP-bar
  progress meter, pixel switches and sliders, and staggered page transitions. Panels
  change colour with the sky.
- **Feedback.** Synthesised 8-bit click and launch sounds (no audio files), pixel
  particle bursts on buttons, block confetti on success, toasts and a dirt-block boot
  screen that wipes away into the sunrise.
- **Pages.**

  | Page | What it does |
  |---|---|
  | Home | Hero with an instance switcher and a big Play button, recent instances, Mojang's patch notes, tips |
  | Instances | Search, sort, play, edit, open folder, delete |
  | New | Three-step wizard: pick any of 900+ versions, pick Vanilla, Fabric or Forge (and a build), then name and memory |
  | Mods | Installed mods with enable/disable switches and delete. Drag `.jar` files onto the window to install them |
  | Accounts | Offline accounts and Microsoft sign-in, each with a generated pixel avatar |
  | Settings | Memory, Java, JVM arguments, Azure client ID, parallel downloads, sky, sounds, animations |

- **Game log drawer** (`Ctrl+L`): live, colour-coded, filterable and searchable.
- **Keyboard:** `Ctrl+Enter` plays, `Alt+1`…`6` switch pages, `Esc` closes things.

## Engine features (`vortex-core`)

| | |
|---|---|
| All versions | Mojang's live manifest, legacy and modern argument formats, `inheritsFrom` profiles |
| Fabric | Any loader build, from meta.fabricmc.net |
| Forge | Runs Forge's official installers. Legacy installers (1.6–1.12) have their version info and universal jar unpacked. Modern installers (1.13+) run the installer's own patch steps (binary patching, mappings, jar splitting) with the right Java |
| Java | Downloads the exact Mojang runtime each version needs (8, 17, 21, 25), so you don't need to install Java |
| Assets and natives | Modern, "virtual" and pre-1.6 assets; LWJGL 2 and 3 natives |
| Accounts | Microsoft device-code login with token refresh, plus offline accounts |
| Safety | SHA-1-verified parallel downloads, Log4Shell-patched log configs, and checks so files from manifests or zips can't be written outside their folder |

## Layout

```
vortex-client/
├── crates/vortex-core   # the engine (library)
├── crates/vortex-app    # desktop app (Tauri 2): src/main.rs + ui/ (HTML/CSS/JS, no build step)
└── crates/vortex-cli    # command line tool
```

Data is stored in `%APPDATA%\VortexClient` on Windows, `~/.local/share/VortexClient` on
Linux and `~/Library/Application Support/VortexClient` on macOS. Set `VORTEX_HOME` to use a
different folder.

## Building

### On Windows (recommended)

1. Install Rust from <https://rustup.rs> with the default MSVC toolchain.
2. Run `cargo build --release` in this folder.
3. Run `target\release\vortex.exe`. It's a single file: with MSVC, WebView2's loader is
   linked in statically.

Windows 10 and 11 already include the WebView2 runtime that the app uses.

### Cross-compiling from Linux

```bash
sudo apt install gcc-mingw-w64-x86-64
rustup target add x86_64-pc-windows-gnu
cargo build --release --target x86_64-pc-windows-gnu -p vortex-app -p vortex-cli
```

The MinGW build needs `WebView2Loader.dll` beside `vortex.exe`. Cargo copies it into the
same output folder, so ship both files together.

### Linux

```bash
sudo apt install libwebkit2gtk-4.1-dev libgtk-3-dev librsvg2-dev
cargo run --release -p vortex-app
```

### Previewing the UI in a browser

The UI falls back to a demo backend outside the app, so you can preview the design with
`python3 -m http.server -d crates/vortex-app/ui` and open <http://localhost:8000>.

## CLI

```bash
vortex-cli versions --kind all
vortex-cli forge-versions 1.20.1
vortex-cli offline Steve
vortex-cli launch 1.20.1 --forge          # creates the instance, installs Forge, plays
vortex-cli launch 1.21.4 --fabric
vortex-cli launch "PvP" --server mc.example.net
vortex-cli launch b1.7.3 --dry-run        # print the java command instead of running it
```

## Microsoft sign-in setup

Microsoft sign-in needs an **Azure application (client) ID** that Mojang has approved for
the Minecraft API. This launcher doesn't include one.

1. Go to the [Azure portal](https://portal.azure.com) → *App registrations* → *New registration*.
   Choose **Personal Microsoft accounts only**.
2. Under *Authentication*, turn on **Allow public client flows**.
3. Ask Mojang to approve the app for the Minecraft API at <https://aka.ms/mce-reviewappid>.
4. Paste the client ID in **Settings → Microsoft sign-in**, or set `VORTEX_MS_CLIENT_ID`.

Offline accounts work without any of this. They're for singleplayer and LAN, and online-mode
servers reject them.

## Tested

Everything below was installed from the real Mojang, Fabric and Forge servers and launched
on a headless Linux box with software OpenGL, no GPU and no sound card.

| Test | Result |
|---|---|
| **Desktop app, end to end** | PLAY → progress bar → Fabric 1.21.4 reached Minecraft's welcome screen. STOP closed it, and the log drawer streamed the game's output |
| Forge 1.20.1 (47.4.28) | The installer's 6 patch steps ran, then Forge mod loading and textures |
| Forge 1.12.2 and 1.8.9 | Loaded textures (legacy installers) |
| Fabric 1.21.4 | Loaded textures |
| 1.7.10 (earlier version of the UI) | Title screen |
| 1.8.9, 1.12.2 | Title screen |
| 1.16.5, 1.6.4, b1.7.3, a1.0.4, rd-132211 | Ran without crashing |
| 26.3 and 26.4 snapshots | Java 25 was downloaded and the game started, but stopped at window creation. 26.x needs a real GPU, so **check this on your PC** |
| Windows build | Cross-compiled; **not yet run on real Windows** |

Run the unit tests with `cargo test --workspace`.

## Credits

The UI fonts are [Silkscreen](https://github.com/googlefonts/silkscreen) and
[Outfit](https://github.com/Outfitio/Outfit-Fonts), both under the SIL Open Font License 1.1.
The licence texts are in `crates/vortex-app/ui/fonts/`. Vortex isn't affiliated with Mojang
or Microsoft.
