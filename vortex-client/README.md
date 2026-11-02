# Vortex Client

A Minecraft: Java Edition launcher written in Rust. It runs **every version Mojang has
ever published**: releases, snapshots, betas and alphas, from `rd-132211` (2009) up to
the newest snapshot. It also supports the **Fabric** mod loader. Windows is the main
target, and Linux and macOS work too.

> The UI in `crates/vortex-app` is a **test UI**. It's plain and functional so the engine
> can be exercised. All of the logic lives in `vortex-core`, so the UI can be replaced
> without touching anything else.

## What it does

| Feature | Details |
|---|---|
| All versions | Reads Mojang's live version manifest (900+ versions) and handles every version JSON format: legacy `minecraftArguments`, modern rule-based `arguments`, and `inheritsFrom` profiles |
| Automatic Java | Downloads the exact Java runtime Mojang ships for each version (Java 8 for old versions, 17/21/25 for newer ones), so you don't need to install Java |
| Assets | Supports modern hashed assets, 1.6–1.7 "virtual" assets and pre-1.6 `resources/` assets |
| Natives | Supports LWJGL 2 extracted natives and LWJGL 3 classpath natives, including Windows ARM64 |
| Fabric | Choose a loader version, or use the latest stable one |
| Accounts | Microsoft sign-in (device code) with token refresh, plus offline accounts for singleplayer/LAN testing |
| Instances | Each instance has its own game folder (saves, mods, resource packs), memory setting and an optional server to join on start |
| Downloads | Parallel downloads, SHA-1 checks, retries and atomic writes. Files that are already present are skipped |
| Safety | Log4Shell-patched log configs are used for every version that needs them. Paths from manifests and zip files are checked so nothing can be written outside its folder |
| Windows | `Vortex.exe` is a GUI app with no console window and a static C runtime. The game is started with `javaw.exe` and no console |

## Layout

```
vortex-client/
├── crates/vortex-core   # the engine (library): manifest, install, java, auth, launch …
├── crates/vortex-app    # test GUI (egui)  → Vortex.exe
└── crates/vortex-cli    # command line     → vortex-cli.exe
```

Data is stored in `%APPDATA%\VortexClient` on Windows, `~/.local/share/VortexClient` on
Linux and `~/Library/Application Support/VortexClient` on macOS. Set `VORTEX_HOME` to use a
different folder.

## Building on Windows

1. Install Rust from <https://rustup.rs> (use the default MSVC toolchain).
2. From this folder:

   ```powershell
   cargo build --release
   ```

3. Run `target\release\vortex.exe` for the GUI or `target\release\vortex-cli.exe` for the CLI.

Every push also builds `Vortex.exe` on GitHub Actions (`.github/workflows/vortex-client.yml`).
Download it from the run's **Artifacts** section as `Vortex-windows-x64`.

### Cross-compiling from Linux

```bash
sudo apt install gcc-mingw-w64-x86-64
rustup target add x86_64-pc-windows-gnu
cargo build --release --target x86_64-pc-windows-gnu
# → target/x86_64-pc-windows-gnu/release/vortex.exe
```

## Using the CLI

```bash
vortex-cli versions --kind all          # every version Mojang has
vortex-cli offline Steve                # add an offline account
vortex-cli create "PvP" 1.8.9           # make an instance
vortex-cli launch "PvP"                 # install everything and play
vortex-cli launch 1.21.4 --fabric       # one-liner: auto-creates the instance
vortex-cli launch b1.7.3 --dry-run      # print the java command instead of running it
vortex-cli launch "PvP" --server mc.example.net   # join a server on start
```

Run `vortex-cli --help` to see every command.

## Microsoft sign-in setup

Microsoft sign-in needs an **Azure application (client) ID**, and Mojang has to approve it
for the Minecraft API. This launcher doesn't include one.

1. Go to the [Azure portal](https://portal.azure.com) → *App registrations* → *New registration*.
   Choose **Personal Microsoft accounts only**.
2. Under *Authentication*, turn on **Allow public client flows** (needed for the device-code flow).
3. Ask Mojang to approve the app for the Minecraft API with the form linked from
   <https://aka.ms/mce-reviewappid>. Until they approve it, the Minecraft login step returns
   `403`, and Vortex tells you so.
4. Paste the client ID in **Settings → Microsoft client ID**, or set
   `VORTEX_MS_CLIENT_ID`.

Offline accounts work without any of this. They're for singleplayer and LAN, and online-mode
servers reject them.

Tokens are stored in `accounts.json` in the data folder, which is what most launchers do.
On Unix the file is only readable by your user.

## Tested

These versions were installed and launched for real on Linux under Xvfb, and each one
reached game start-up:

| Version | Notes |
|---|---|
| 26.3 | Downloads Java 25. The game starts, but Xvfb's software GLX can't create the window for the new renderer. This is a limit of the headless test setup |
| 1.21.4 + Fabric | Java 21 and Fabric Loader 0.20.1. Reaches resource loading |
| 1.16.5 | LWJGL 3 with extracted natives |
| 1.12.2, 1.8.9, 1.7.10 | LWJGL 2 with Log4Shell-patched XML logging. 1.7.10 reached the title screen when launched from the GUI |
| 1.6.4 | "virtual" legacy assets |
| b1.7.3, a1.0.4, rd-132211 | launchwrapper and pre-1.6 resources |

Run the unit tests with `cargo test --workspace`.

## For the real UI later

The test GUI only calls into `vortex_core::Launcher`:

```rust
let launcher = Launcher::new(Paths::from_default())?;
let manifest = launcher.manifest(false).await?;            // every version
let instance = launcher.instances().create(Instance::new("PvP", "1.8.9", Loader::Vanilla))?;
let session  = launcher.session_for(&mut account, &settings).await?;
let cmd      = launcher.prepare_launch(&instance, &session, &settings, &progress).await?;
let mut game = vortex_core::launch::spawn(&cmd)?;           // game.logs = parsed log lines
```

Any front-end can sit on top of this, whether it's egui, Tauri, Slint or something else.
