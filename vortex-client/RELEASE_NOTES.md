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
