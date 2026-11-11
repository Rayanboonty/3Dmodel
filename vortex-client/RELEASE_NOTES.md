**Vortex 0.10.0: music, every version, and more mods.**

**Music.** A new Music page plays your own songs (MP3, FLAC, Ogg, Opus, M4A, WAV) or free internet radio, searchable by name or genre.
- Lyrics follow the song line by line. They come from a .lrc file next to the song, or from LRCLIB (free, no account). Click a line to jump there.
- In-game, the Music overlay shows the song and the current lyric line. F8 plays or pauses, F9 skips (change them in Controls).

**Vortex in-game mods on every Minecraft 1.20 and 1.21 version.** The Right Shift menu and all its mods used to work on six versions. Now they work on all 19, from 1.20 to 1.21.11. Hitboxes doesn't work on 1.21.9 and newer.

**Fullscreen sticks.** With Start fullscreen on in the launcher, every version starts fullscreen. With it off, the game keeps what you chose in-game (F11 or Video Settings); before, every launch put it back in a window.

**10 new in-game mods:**
- Waypoints: B saves a spot; the list shows how far and which way.
- Cooldowns: ender pearl, chorus fruit, shield and wind charge.
- TNT Timer: seconds until the nearest lit TNT goes off.
- Stopwatch: Y starts and stops it, hold Y to reset.
- Quick Chat: F10 sends gg, gl hf, /l, /hub and more.
- Server TPS, Resource Counter (Bed Wars), Block Counter, and FOV Changer (same FOV while sprinting).
- Nick Hider and Hitboxes.

**A better in-game menu.** Every mod has its picture, and every options page has a live preview: the crosshair as you shape it, the hit marker, the low-health glow, the block outline, chat features, the Tab list, time and weather, and more.

**More PvP mods** in the PvP pack: Not Enough Animations, Combat Hitboxes, TotemCounter, Totem Tweaks and AppleSkin.

**The look.** The old tab dock and liquid glass look stay. Pages, dialogs and the start screen no longer fade (particles still play). Liquid glass is in Settings > Appearance; the standard is the plain, fastest look.

That makes 88 in-game mods with 141 options.

**From 0.9.0:**

**Vortex 0.9.0: more FPS, a cleaner game, fewer settings.**

**FPS Boost (in-game).** One switch at the top of the mod menu: fast graphics, a shorter view (8 chunks, you can change it), fewest particles, no clouds or shadows, and far-away entities hidden sooner. Turn it off and every setting it changed comes back exactly as you had it, even after a restart.

**Render resolution (launcher, Settings > Game > Performance).** Draws the world with fewer pixels (85% down to 50%) while menus and text stay sharp. On a weak graphics card this is the biggest single gain. Fabric 1.21.4 and newer.

**Measured.** On our test machine, with the Performance pack and Max FPS mode: 102 FPS average. We also tried four more popular "optimisation" mods (Enhanced Block Entities, Particle Core, Ixeris and a loading-screen fix). Together they lowered it to 85 FPS, so they are not in the pack.

**A cleaner in-game menu.** The big cards are gone: it's now a plain two-column list with one small switch per mod. Hover a row to see what it does, and to show its heart and options. The tabs are plain words. Presets moved to the Edit HUD screen.

**A lighter HUD.** Each element's text is worked out once per game tick instead of every frame.

**Fewer settings.** The Game tab went from 16 cards to 4: Performance, Memory and window, Game defaults and Advanced. The long "More options" list is gone (anything it set is cleared). General has 4 cards.

**Fewer, better in-game mods.** Removed the ones nobody needs: Difficulty, Game Mode, Game Version, Vertical Speed, AFK Timer, Dimension (still an option on Coordinates), Death Counter (Kill Counter shows K/D), World Time (Day Counter shows it) and the HUD key switch (F6 always works now).

**Fixes.**
- The game no longer crashes when a removed module is still in your settings.
- FPS Boost only re-applies when you change it, so toggling other mods never makes the world reload.

**From 0.8.1:**

**Vortex 0.8.1: a quicker start.** The particle animation when the launcher opens is gone (it lagged on many computers). The launcher now appears straight away with a short fade.

The Android app is no longer made.

**From 0.8.0:**

**Vortex 0.8.0: the big one.** Over 130 new options and mods, a real installer, texture packs, a badge for Vortex players and faster everything.

**Starts on low-end PCs.** Minecraft could be very slow to start on low-end PCs, or not start at all. Fixed:
- **Automatic memory** (on by default, Settings > Game > Memory): Vortex gives Minecraft the right amount for your PC's RAM, such as 2 GB with mods on a 4 GB laptop. Before, every PC gave it 4 GB, so PCs with 4-6 GB ran out and swapped to disk, or the game failed to start.
- Java's garbage collector is now set up for small amounts of memory too.
- If Modrinth can't be reached, the game starts straight away instead of waiting on every mod; the missing mods are added next time. Retrying them never holds up a launch for more than 45 seconds.
- With no internet, a Microsoft account still starts the game with its saved sign-in, instead of failing.
- **The big one:** the first launch after each update (and the next launch whenever the game was closed with Stop or Task Manager) recorded a Java class archive, which made it many times slower. We measured a 71-mod pack on a 2-core PC: 170 s to the title screen while recording, 21 s once recorded. That archive is gone, and every launch now takes about 23 s on the same PC. Old archives (100+ MB each) are deleted.
- Java's own one-time speed-up job now runs at idle priority, so it never slows the game starting.

**Zero instances.** Vortex starts with no instances, and yours appear as soon as you make them. On Android, the profiles that Amethyst and the Forge and OptiFine installers add on their own are no longer shown.

**Vortex installs itself.** The first time you open Vortex.exe, it offers to install as a real app:
- It goes in your user's Programs folder, with Start menu and Desktop shortcuts.
- It shows in Settings > Apps, where you can uninstall it.
- It can start with Windows (minimized), from Settings > General > Vortex app.
- Closing it while Minecraft runs asks first.

**A badge for Vortex players.** A VC badge appears next to the name of everyone playing with Vortex in the Tab list (hold Tab):
- It works in singleplayer, LAN worlds and on servers running the Vortex mod.
- On normal servers like Hypixel, only you see your own badge, because the server doesn't pass the signal on.
- Turn it off in the in-game menu (Right Shift > Vortex Badge).

**Texture packs in Customize.** A new Texture packs tab next to Skins:
- Browse and install packs from Modrinth for the instance's Minecraft version, by style.
- Turn them on and off with one switch, and remove them.

**Faster.**
- Packs install about 3.6x faster: every mod downloads at once. We timed a 61-mod pack at 6.4 s, down from 23.1 s.
- If a mod has no build for your version, a similar mod stands in for it.
- New System options: use the fast graphics card for Minecraft on laptops with two, higher game priority, and a fix for servers that fail over IPv6.

**Updates reach everyone.** If GitHub's update check is busy (it limits how often each network can ask), Vortex reads the release page instead. It also checks every 30 minutes and when you come back to the window, and installs a waiting update after you close the game.

**A new loading screen.** The launcher now builds itself out of particles: the logo, then each panel, land grain by grain on their exact pixels, with a real progress bar.

**18 new in-game mods** in Right Shift: Vortex Badge, Light Level, Player Count, World Time, Biome, Yaw and Pitch, Chunk Position, Entity Count, Durability Warning, Death Coordinates, Auto GG, Snaplook (hold V), Bow Charge, Fall Distance, Health, Experience, Weather and Moon Phase.

**24 new in-game options**, including:
- Rainbow text, background opacity and brackets for the HUD.
- Frame time on the FPS counter, and coloured ping.
- Nether position and decimals for coordinates, and decimals for reach.
- Press-to-toggle zoom and freelook, and seconds and date for the clock.

The HUD's left column now wraps instead of running off the screen.

**80 Minecraft options in the launcher** (Settings > Game > More options), set before the game starts. They cover graphics, camera, controls, chat, every sound channel, skin layers, language and accessibility.

**New launcher options:**
- Streamer mode (hides names and addresses) and high contrast.
- Interface sound volume and keyboard shortcuts.
- Confirm before closing, and the default pack for new instances.
- Time played on each instance, and sorting by it.
- A What's new card after each update.

**Fixes.**
- Mods that had no build for the chosen version no longer crash the game (Cull Leaves was removed from the packs).
- The in-game HUD no longer runs off the bottom of small screens.
- Death coordinates clear once you get back to them.

**From 0.7.8:**

**One file for Windows.** Vortex now comes as a single **Vortex.exe**: no zip, nothing to unpack. Put it anywhere and open it. Updates replace that one file too.

**From 0.7.7:**

**A much faster launcher.** It was slow because of the glass effects. Measured on the Home screen, on a computer with no graphics card:
- **Before:** liquid glass and blur everywhere, 83 ms a frame (about 12 FPS).
- **Now, with the new Default:** 16.7 ms a frame (a smooth 60 FPS).

The animated wallpaper also costs about half as much: the photo is resized once instead of every frame, and its slow drifting lights run at 30 FPS.

**Choose your glass** in Settings > Appearance > Glass:
- **Default** (the new standard): clean panels with no blur. The fastest.
- **Blur:** frosted panels.
- **Liquid glass:** frosted panels that also bend what's behind them, like iOS 26. Best on a strong graphics card.

Reduce transparency is now the Default glass.

**From 0.7.6:**

**Max FPS mode.** A new switch in Settings > Game: fast graphics, no clouds or shadows, minimal particles, render distance 8. Anything you set yourself still wins.

We measured it in the same world and spot, on a test machine with no graphics card:
- Normal Minecraft: 11.6 FPS.
- With the Performance pack: 14.6 FPS.
- Performance pack plus Max FPS mode: 38.0 FPS, about 3.3x normal Minecraft.

On a real graphics card the Performance pack alone usually gives about 2-4x.

**In-game fixes.** The Vortex look now applies only to Minecraft's own text buttons. Other mods' buttons, item list arrows and tabs, the recipe book and lock icons, and checkboxes look the way they were made again.

**Play button.** The button and its arrow are one piece of liquid glass, with no curve or line where they meet.

**From 0.7.5:**

**The Play button, remade.**
- **Liquid glass everywhere:** LAUNCH GAME is orange liquid glass, PLAYING is green liquid glass, and the button turns into dark glass while the game gets ready.
- **Particle loading:** the progress is a river of glowing particles inside the button, thickest at its moving front. Steps without a percentage show a sweeping swarm.
- **Explosion when it's ready:** the particles rush in, the button blows apart into gold sparks with a flash and a shockwave, and PLAYING forms out of the dust.
- **Cleaner:** no more stray outlines, double lines next to the arrow, or dots floating around the button.

**Packs matched to every version.**
- **Client mods only:** Radium, VMP and C2ME are gone (they're for servers).
- **The right mod for each version:** when a mod has no build for your version, its counterpart is used. REI or EMI if JEI is missing, Starlight on versions before ScalableLux, Rubidium on Forge versions before Embeddium, and Sodium Dynamic Lights if LambDynamicLights is missing. The install sheet shows which one was used.
- **More mods:** Not Enough Animations, Sound Physics Remastered, Presence Footsteps, AmbientSounds, Enchantment Descriptions, Visuality, Cubes Without Borders, Particle Rain, Traveler's Titles, Better Clouds, Model Fix, Capes, Better Mount HUD, Language Reload and more. Forge gets Oculus (shaders).
- Tested in game: Normal on 1.21.4 and 1.20.1 (title screen and a world), and PvP on 1.21.4 (title screen).

**From 0.7.4:**

**A liquid glass Play button while the game gets ready.** After you press Play, the button turns into liquid glass:
- A glowing liquid fill shows the download, with a bright wobbling edge and dust streaming along it.
- Sparks rise off the edge as it moves. Steps without a percentage show a sweep of light instead.
- Pressing Play charges the button with dust, and it bursts with a ring of light when the game starts.
- It works on phones too.

**From 0.7.3:**

**A new look for pack installs.** The first launch of a PvP, Normal or Performance instance now opens a liquid glass sheet that shows every mod arriving:
- Each mod lights up while it downloads, with dust streaming into it and a fill showing the download.
- It bursts into sparks and gets a check mark when it lands. Mods with no build for your version are crossed out.
- A ring and a bar fill up as the pack goes, then the sheet says how many mods were added and breaks into dust as the game starts.
- **Hide** closes it; the Play button keeps showing the progress.

**From 0.7.2:**

**VC cape.** The Vortex cape now shows the VC logo instead of the old V, in-game and in the launcher.

**From 0.7.1:**

**Your VC logo everywhere.** The old "VORTEX CLIENT" logo is gone:
- **In-game:** the Vortex main menu and the mod menu now show the VC logo on every supported version (1.20.1 to 1.21.11).
- **App icon:** Vortex.exe and the Mod Menu entry use the VC icon.

**From 0.7.0:**

**Choose how you play.** When you create an instance, Vortex asks what it's for:
- **PvP:** the Vortex PvP mods, every performance mod, 3D skin layers, wavey capes, better ping and more.
- **Normal:** shaders (Iris), Xaero's minimap and world map, JEI, Jade, AppleSkin, connected textures, dynamic lights and more, plus every performance mod.
- **Performance:** every optimisation mod that works together: Sodium, Lithium, FerriteCore, ModernFix, ImmediatelyFast, EntityCulling, More Culling, C2ME, ScalableLux, Krypton, Dynamic FPS, BadOptimizations and more.
- **Vanilla:** no mods.

The mods are added on the first launch, for your exact Minecraft version.

**New in-game look.** The mod menu, the HUD and the buttons match the launcher: rounded glass, real on/off switches, and a dock at the bottom. This applies on every supported version.

**30+ new options:**
- **Game defaults:** max FPS, render and simulation distance, FOV, GUI scale, VSync, raw mouse input, shadows, particles, clouds, auto-jump, pause when you tab out, no tutorial pop-ups, narrator off, and skip the multiplayer warning.
- **Launching:** pick the garbage collector, rejoin your last server, run commands before or after playing, and use a wrapper command and environment variables.
- **Mods and safety:** update mods before playing, back up worlds automatically, delete old logs, and a crash helper that tells you why the game crashed.
- **Launcher:** start page, size, reduced transparency, particle effects, lite mode, and flash when the game closes.

**From 0.6.1:**
- Vortex mods on 1.20.1, 1.20.4, 1.21.1, 1.21.4, 1.21.8 and 1.21.11.

**From 0.6.0:**
- Vortex mods on 1.20.1 to 1.21.4.
- Performance mods (Sodium and friends) for much higher FPS.
- Crash checks for every version.

**From 0.5.8:**
- Steve moves naturally.

**From 0.5.7:**
- The birch forest wallpaper.

**From 0.5.6:**
- Closing things breaks them into particles that keep their own look.

**From 0.5.5:**
- Much smoother on phones.
- The tabs sit in a dock at the bottom of the wallpaper.
- Pages blur and fade softly at their edges as you scroll.

**From 0.5.4:**
- Liquid glass panels.
- MagicOS-style particles.

**From 0.5.0:**
- Updates install by themselves.
- Particles stream in the loading bar, then burst and build the launcher.
- A new light and dark switch.
- Dust falls from top to bottom.
- Fullscreen works.
- Launches start faster.
- First open starts with no instances.
