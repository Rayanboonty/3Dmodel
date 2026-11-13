# Vortex Realm – MCPVP-style practice plugins

This folder has **two plugins with full source code** for a Paper **1.21.11** server (Java 21):

| Jar | What it is | Use it when |
|---|---|---|
| `dist/PracticePvP-1.8.0.jar` | Your existing PracticePvP plugin, upgraded. Everything it already did is still there, plus all the new MCPVP features below. Same plugin name and the same `plugins/PracticePvP` data folder, so arenas, kits, ranks, the database and settings keep working. | You want to update your live server and keep everything as it is. |
| `dist/VortexRealm-2.7.0.jar` | The new **VortexRealm** plugin: the same engine and features under its own name and package (`net.vortexrealm`), `plugins/VortexRealm` data folder, `vortex.*` permissions, `vr_*` database tables and `%vortex_…%` placeholders. On its first start it copies your PracticePvP data over automatically. | You want a clean Vortex Realm identity. |

> Install **one** of them, not both. VortexRealm switches itself off if PracticePvP is also installed.

**Also here:** `dist/VortexPvP-1.0.2.jar` (frozen, no longer updated) is a separate **test build** with about 50 new systems on top of the same engine. These include:
- rank ladder, seasons, clans and ranked 2v2;
- replays, drills and arcade games;
- battle pass, missions and achievements;
- Discord, Eco SMP extras and more.

It is meant for a test server. See **[README-VortexPvP.md](README-VortexPvP.md)**.

## Install / upgrade

1. Stop the server and back up `plugins/PracticePvP`.
2. **Upgrade in place:** replace the old `PracticePvP-*.jar` with `PracticePvP-1.8.0.jar` (or the old `VortexRealm-*.jar` with `VortexRealm-2.7.0.jar`). That's all: everything is carried over by itself (see below).
   **Or switch to VortexRealm:** remove the PracticePvP jar and add `VortexRealm-2.7.0.jar`. On the first start, configs, arenas, kits, ranks and the SQLite database are copied from `plugins/PracticePvP` to `plugins/VortexRealm`. Tables are renamed `pp_*` → `vr_*` and permission nodes in the copied yml files `practice.*` → `vortex.*`. Your old folder is left untouched. With MySQL, run `/vortex migrate` once, then restart.
3. Start the server. The new arena pack (vanilla maps, deep crystal maps and the new designed arenas) generates in the background, one map after another; players can keep playing meanwhile. Your old generated arenas are converted afterwards (bigger, no walls, vanilla terrain where it applies). Hand-edited arenas are left alone.
4. Build the new **Royal Castle** hub if you want it:
   - `/pvpadmin` → **Hub Setup**, which builds it in the hub world;
   - or stand where it should go: `/pvpadmin` → **Builds** → *Royal Castle* → **Preview (3D)**, then **Build here**.

   Nothing is rebuilt automatically, so your current hub stays until you choose.
5. Optional: put a resource pack-capable client on the server to see flags and icons; set `online-mode=true` so tab heads show skins.

The jars bundle everything they need. PlaceholderAPI, LuckPerms, GrimAC, Vulcan and **Vault** are optional (soft dependencies).

## New in 2.4.0 / 3.4.0: castle-town lobby, auto-updates, nearest-region servers

### The Town Square is now a castle town
- A curtain wall around the whole town with twelve towers (round towers with tile cones, square towers with
  hipped roofs), a walkable wall-walk, a **gatehouse** with twin drum towers, portcullis, banners and a drawbridge over
  a dry ditch, and two side gates.
- **The keep** at the end of the market street: a grand stair, a buttressed great hall with tall windows, rose windows
  and a slate roof, four corner turrets and a tall donjon. Inside: throne dais, long tables, chandeliers, hearth. The
  bailey has a well, a garden and a stable; a **castle park** (lawns, hedges, gravel walk, pond, benches) replaces the
  houses that stood next to it.
- A two-tier **fountain** in a compass rose in the plaza, a tavern and a guild hall on the market street, a smithy, a
  chapel with a bell tower and churchyard, market stalls, a windmill, and three rings of timber-framed houses (jetties,
  shutters, flower boxes, chimneys, dormers), bracket street lamps, carts and crates.
- The KITS/SHOP stands, the NPC pedestals, the spawn, the bots yard and the event hill stay exactly where they were.

**Upgrade your lobby in one click:** `/pvpadmin` → Builds → Town Square → **⬆ Upgrade lobby** (or `/pvpadmin hub upgrade`).
It finds your lobby (also ones built with older versions), saves a full backup, rebuilds it in the same place and puts
the NPCs and the bots yard back - no duplicates. Anything you built by hand inside the lobby area is replaced.
`/pvpadmin hub build <style> [radius]` builds a lobby where you stand.

### Auto-updates (no more uploading jars)
Every server checks for a new version every 30 minutes (`updates.*` in config.yml, Settings → Network):
1. It reads `update.json` from GitHub (`updates.sources`; add your own website after it, comma separated).
2. It downloads the new jar, **checks its SHA-256**, and puts it in `plugins/update/` - the server swaps it in on the
   next start.
3. It restarts at a good moment (`updates.restart`): **as soon as nobody is online, or at the restart hour** (05:00 by
   default) with a 5-minute countdown, after running matches finish. A **full backup** is saved first.
4. On start the new version carries everything over (ranks, stats, kits, NPCs, settings) and prints the usual report.

**Turn on auto-restart in your host panel** (Bao Host: Startup / "Auto restart"), so the server comes back after the
update restart. Commands: `/pvpadmin update` (status), `check`, `now`, `on`, `off`.

### Nearest region: one network, servers in the USA, Europe and Asia
Run the same plugin on one server per region and list them in `regions.yml`:
```yaml
current: na          # this server's region
auto: AUTO           # OFF, SUGGEST (clickable message) or AUTO (moved on join)
ip-lookup: true      # continent from the player's IP (ipwho.is) when they haven't picked a country
regions:
  na: {name: North America, location: "Miami, Florida", host: '', port: 25565, flag: us, continents: [NA, SA]}
  eu: {name: Europe, location: Frankfurt, host: eu.yourdomain.net, port: 25565, flag: de, continents: [EU, AF]}
  as: {name: Asia, location: Singapore, host: as.yourdomain.net, port: 25565, flag: sg, continents: [AS, OC]}
sync:
  role: primary      # on the main server; 'replica' on the others
  primary-url: ''    # on replicas: the main server's website, e.g. http://us.yourdomain.net:8123
  key: ...           # made for you on the primary - copy the same key to every replica
```
- A player joining a region that isn't nearest to them is moved (AUTO) or told (SUGGEST). A region picked in
  `/region` always wins. Every server needs `accepts-transfers=true` in server.properties.
- **Same server everywhere:** replicas copy server ranks, kits and countries from the primary every minute, and ranks
  or countries given on a replica are sent to the primary. Point every region at **one MySQL database**
  (`database.type: MYSQL`) so stats, ELO and match history are shared too.
- **Best for players:** use **GeoDNS** so `play.yourdomain.net` already answers with the nearest region's IP (Cloudflare
  Load Balancing with geo steering, or AWS Route 53 geolocation records). Then players connect straight to their
  nearest server and the plugin's auto region only fixes the rest.

## New in 2.3.0 / 3.3.0: arena cleanup, faster match found, votes, kit editor, AI full access
- Only vanilla, show-arena and colosseum maps are kept (the open and small themed duel maps are removed on start).
  Vanilla maps 200, mace maps big (320), sword kits back to the classic size.
- Match found, map vote and countdown are faster; the map vote text is centred and wraps by real width.
- New pre-match votes: **play first to N rounds**, **no natural regen**, **knockback N%**; `match.max-votes`.
- Kit editor: knockback %, hit delay, pearl / wind charge cooldowns, which maps a kit plays on, the rounds vote and a
  votes editor. **Copy A Kit** (or `/kits copy <from> <id> [name]`) to make your own kits.
- AI assistant: operators' AI can run any server command, edit every part of a kit, manage arenas, read the log and
  edit any plugin's config. `/ai mode online|local|both`, `/ai setup local` (free local model with Ollama).

## New in 2.2.0 / 3.2.0: bot training overhaul, new Bot Arena screen, bots in the Town Square

- **Flat maps for bots.** Every bot fight and training now happens on the new flat **Training Grounds** map, never on the biome maps:
  - a 72-block grid floor so distances are easy to read;
  - distance marks every 4 blocks;
  - an obsidian pad where the dummies stand, so crystals and anchors go right on it;
  - room above for elytra and mid-air training.
  - The map is made automatically (4 copies), even with `auto-generate-arenas: false`.
  - Set `bots.flat-maps: false` to use the kit maps instead.
- **Training dummies really stand still.** Hits, crystals and wind charges don't move them. On the test server they moved 0.00 blocks after 6 hits each.
- **D-tap fixed.** The dummy has no hit cooldown after a pop and its new totem is back in one tick, so the second crystal really pops. Two crystals 0.5 s apart counted as a d-tap (fastest 485 ms). The d-tap training sword has Knockback I, and you get extra obsidian and crystals.
- **Stun slam fixed.** The axe and the mace are put in your hotbar for the stun slam trainings.
- **8 new training forms** (19 in total):
  - Mid-Air Stun Slam: the dummy hovers 7 blocks up with a shield;
  - Elytra Shield Flyer: a gliding elytra dummy with a shield;
  - Spear Stun Slam: break the shield with a spear lunge, then slam;
  - Spear + Mace Combo;
  - Lunge Target: counts lunges and their distance;
  - W-Tap Target: counts sprint-hit streaks;
  - Crit Dummy;
  - Archery Target: 22 blocks away, counts the longest shot.
- **New Bot Arena screen**, inspired by the big practice servers:
  - a header with your record (W/L, Expert wins, best streak) and the **featured training of the day**;
  - tabs **⚔ Fight · 🗡 Sword · 🪂 Air & Elytra · 🔱 Spear · 💎 Crystal · 📜 Records**;
  - **▶ Quick Play** (your last bot), Search and Academy;
  - a kit grid with what each bot does;
  - difficulty cards with your W/L per difficulty, plus "★ Surprise me";
  - training cards with your best result;
  - Records shows your best and the server's top 3 for every training (saved in `bot-records.yml`). A new personal best is announced.
- **Bots in the Town Square hub.** The Town Square hub (the one with the giant chestplate and trophy) now has a **Training Grounds wing**:
  - a walled yard with a "BOTS" gate and a target sign;
  - six bot-map alcoves with their mobs;
  - three NPCs: find a gamemode, training, academy;
  - a dummy yard with hay dummies and targets.
  - The mobs and NPCs are placed when the hub is built (`bots.hall-in-hub`).
  - `/pvpadmin bothall` still builds the separate hall where you stand.
- **On/off switches** in `/pvpadmin` → Settings → Practice Bots & Training: `bots.enabled` (fights), `bots.training` (dummies), `bots.flat-maps`, `bots.hall-in-hub`.
- **Bug fix:** on maps with an invisible roof, bots, dummies and Academy lessons could be placed on top of the roof. They now always stand on the real floor.
- With auto-generate off, the start no longer checks every arena's blocks at once, which loaded many chunks and caused a lag spike on small servers.

## New in 2.1.0 / 3.1.0: compact detailed duel maps, a new Mole PvP map

**12 new duel arenas, built to be played, not just looked at.** These are original designs in the style of the big practice servers.

**Sword maps (72 blocks):** Temple Ruins, Sakura Garden, Nether Bastion, Frozen Harbor, Desert Bazaar, Castle Courtyard, Mushroom Hollow, End Citadel.
- The players spawn **28 blocks apart** on raised pads under arches with banners and a hanging lantern.
- The floor is flat and clear of obstacles, with real detail:
  - a mix of 3-5 blocks;
  - a medallion in the centre and a walk to each spawn;
  - a border band;
  - flower beds in the corners.
- Around the floor:
  - a trimmed low wall with posts and lights, and corner pillars;
  - plants and props along the wall;
  - a water, lava or ice channel.
- Scenery outside an invisible barrier keeps the view full: trees, cliffs, towers, temples, torii gates, giant mushrooms, end islands, a lighthouse.

**UHC maps (90 blocks):** Meadow Hills, Red Canyon, Taiga Valley, Overgrown Ruins.
- Low hills, trees, rocks and ruined walls to build around.
- Diggable ground 22 blocks deep, and the players spawn 44 blocks apart.

**Which kits play where:**
- sword, pot, NoDebuff, combo, gapple, shield and NethPot play only on the new sword maps;
- UHC and Build UHC play only on the UHC maps;
- the other kits (axe, bow, SMP...) get the new maps on top of their old ones;
- the bigger designed maps are now 110 blocks instead of 192 (only if you never changed `arena.duel-size`).

**Sizes and generation:**
- The sizes are in `arena.duel-compact-size` (72) and `arena.duel-build-size` (90).
- The new maps are made automatically when the first player joins, **even with `general.auto-generate-arenas: false`**, because they are small (about 10-20 s each).

**Mole PvP: "Mole Hollow".** The old 401-block mine with a surface camp is gone.
- The map is solid rock, 81×81 and 46 deep, with **no surface**: real stone, tuff and deepslate layers, ores, gravel and granite pockets, and a few small dripstone caves.
- Each player starts in a small furnished base (bed, chest, furnace, crafting table, lanterns, banner) sealed in the rock, **26 blocks from the enemy**. Dig to find them.
- A bedrock roof and floor keep the fight underground, and the sonar pings every 20 s.
- FFA has 12 bases in a 101-block map.

**Old maps are refreshed.** Every generated arena is rebuilt in the background with the new settings, one at a time; hand-edited ones are left alone. The old Mole map is rebuilt into Mole Hollow.

## New in 2.0.0 / 3.0.0: the SMP is its own server

- The Practice plugin keeps the hub, duels, FFA and bots. Everything SMP moved to the new **VortexSMP** plugin (see `README-VortexSMP.md`): economy, homes, tpa, claims, combat tag and the separate inventories.
- **Server selector `SERVER` entries.** Players are sent to another host with the built-in transfer (set `accepts-transfers=true` on both servers) or through Velocity/BungeeCord (`network.mode: PROXY`).
  - Set the SMP address in `/pvpadmin` → Server Selector → SURVIVAL SMP → Host & Port. Use Test Connection to check it.
  - `/smp`, the selector and SMP NPCs then send players there.
  - Live player counts come from a server-list ping.
- **Safe upgrade:**
  - worlds with an old SMP mode are left alone;
  - a player still carrying old SMP items gets them saved to `inventories/groups/<uuid>/` before the hub gives its items;
  - VortexSMP can import those files.

## New in 1.8.0 / 2.7.0: practice bots for every mode

Practice bots and training dummies are switched on. `/bot` opens the menu, `/bot <kit> <easy|medium|hard|expert>` starts a fight, `/train` opens the dummies.

**The bot plays each kit with the kit's own items:**
- **Crystal / End Game:** obsidian and crystals, the **d-tap** (a second crystal straight after your totem pops, at Hard and Expert), and anchors charged with glowstone.
- **Mace / Spear Mace:** wind-charge launches into a mace smash, the **stun slam** (axe breaks your shield, the mace lands in the same fall), and the elytra dive when the kit has an elytra.
- **Spear:** the spear charge from a few blocks away. **Trident:** trident throws from range.
- **UHC / Build UHC:** fishing rod pulls, lava at your feet (picked up again), water when it is on fire.
- **SMP kits / Late Game:** cobwebs at your feet. **Cart:** TNT minecarts on a rail next to you. **Creeper:** creeper eggs.
- **Pot kits:** only real healing potions are thrown; strength, speed and fire-resistance potions are drunk at the start.
- **Bow:** keeps its distance and shoots. **Combo:** hits at the kit's fast hit delay.
- **Everywhere:** crits, strafing, w-taps, shield blocking, axe shield-breaks, golden apples, pearls (in from far away, out when low), a totem swapped into the off hand when low.
- The bot waits during the 3-2-1 countdown. Mole PvP has no bot yet (bots can't dig).

**Training dummies (`/train <name>`), each with live stats over its head:**
- `dtap`: counts d-taps and the fastest time between two pops.
- `anchor`: anchor damage (you get anchors and glowstone).
- `stunslam`: counts a shield break followed by a mace smash in the same fall.
- `mace`: best smash and fall height. `elytra`: a flying target.
- `pearl`: a running target; counts pearls landed within 3 blocks.
- `pot`: chips your health so you practise healing.
- `spear`: best spear hit. `combo`: combos and sprint hits. `shield`, `crystal`: as before.

**Also:** the bot menu lists exactly what the bot does with each kit, the Bot Practice hall is open, and the Academy has a new d-tap lesson.

## New in 1.7.4 / 2.6.4: bring an old plugin folder back - everything, in one go

Switching plugins (PracticePvP → VortexRealm, or to VortexPvP) or updating now keeps everything as it was, and an old plugin folder can be brought back at any time.

**Import an old plugin folder:**
1. Put the old folder in `plugins/VortexRealm/import/`, or a `.zip` / `.tar.gz` of it. A hosting panel archive works as it is. If the old folder is still in `plugins/`, `/pvpadmin import from PracticePvP` does this for you.
2. Restart. A full backup of the current data is saved first (`before-import`). Then:
   - **Server setup comes back as it was:** worlds and hub, PvP lobby and spawns, NPCs, holograms, decorations, image walls, arenas, kits, ranks and rank kits, menus, tab, scoreboards, the shop and every setting.
   - **Player data is merged, and nothing current is overwritten:**
     - Missing homes, claims (renumbered if needed), last locations, players, stats, kit layouts, friends, cosmetics and matches are added.
     - A player whose stats or money were reset by the switch gets the old values **plus** what they earned since.
     - Lost ender chests and inventories are handed back the next time the player is in that world.
   - **The database is read with its newest changes.** The `data.db-wal` file next to `data.db` holds them, and switching plugins used to leave it behind.
3. What it did is in `import/LAST-IMPORT.txt` and in `/pvpadmin import`. The imported files stay in `import/done-…`.

**Also:**
- Entities, blocks and items the old plugin tagged keep working after a switch: bot hall, NPCs, image walls, spawner stacks, hub items and shop spawners.
- Backups now always include the database's newest changes.
- Switching from PracticePvP copies `data.db-wal` too.

## New in 1.7.3 / 2.6.3: ender chests kept when switching plugins, and a restore tool

**The bug.** Switching from PracticePvP to VortexRealm could empty players' SMP ender chests and inventories.
- Each plugin marks which saved inventory a player carries, under its own name. VortexRealm didn't recognise PracticePvP's marker.
- So a player who joined in the hub had their saved survival inventory and ender chest overwritten by the hub's (empty) one.
- The backup didn't help either. PracticePvP's backups sit in `plugins/VortexRealm-Vault` and VortexRealm only looked in its own folder. It also took no backup on its very first start.

**Fixed:**
- The new plugin takes the old plugin's markers over, so nothing gets mixed up. This works for PracticePvP ↔ VortexRealm ↔ VortexPvP.
- A saved inventory is never written over by items of unknown origin. Those go to a separate `…-unsorted-…yml` file instead.
- Every save keeps the previous copy as `<group>.prev.yml`.
- The first start on existing data always takes a `first-start` backup.

**Getting lost items back (`/smpadmin restore`, also works from the console):**
- `/smpadmin restore all-from-old`: shows every player whose ender chest was lost in the switch. The items come from the untouched `plugins/PracticePvP` folder.
- `/smpadmin restore all-from-old confirm`: gives them back. They are added to the free slots, and nothing a player has now is replaced. Online players get them at once; the others when they next enter that world.
- `/smpadmin restore <player>`: lists everything saved for one player. Sources are the old plugin folder, previous saves, and the backups of every plugin's vault, each with item counts.
  - `/smpadmin restore <player> <#> preview` shows the contents.
  - `/smpadmin restore <player> <#> enderchest|inventory|all` restores them. What the player had before is saved first, as `…-before-restore-…yml`.

## New in 1.7.2 / 2.6.2

- **Country flags as images in the tab list**: players with the server pack now see the real flag image next to every name in the normal tab list too (it showed the letters, e.g. "MA", unless the pack was set to required). Players without the pack still see the letters. The stats hologram shows the image as well.
- **/rtp in the End** lands on a random outer End island (1100+ blocks out) away from portals and gateways, instead of the exit portal that sent players back to the hub. If no island is found it uses the obsidian entry platform.
- **FFA first**: every FFA arena (also the vanilla-terrain ones: Crystal, BuildUHC, SpearMace) is made before the duel maps.

## New in 1.7.1 / 2.6.1

**Arenas switch on, FFA never stuck at "Generating..."**
- A duel map switches on as soon as the map itself is built; its copies join one by one as each is ready (before, the whole map stayed off until every copy was done).
- The arena pack makes the quick ones first: FFA arenas on designed maps (seconds each), then designed duel maps, then the vanilla-terrain maps (those take minutes each, because Minecraft has to generate the terrain).
- The generated terrain is kept for the whole pack instead of being deleted and generated again for every vanilla map, which made the pack many times slower.
- An FFA arena always leaves "Generating..." - if building it fails (even before it starts), it is cleaned up and made again on the next start instead of staying stuck and off.
- If a map takes more than 25 minutes, the pack logs what it is doing and moves on to the next one (that map is made again on the next start).
- The console shows each map of the pack as it starts and finishes; the FFA and duel lists show "Generating now", "Waiting its turn in the arena pack 3/12" or "Off".

## New in 1.7.0 / 2.6.0

**Lightweight**
- The jar went from 33 MB to 3 MB: the AI no longer bundles a provider SDK (Kotlin, Jackson, OkHttp...), it talks to the APIs itself.
- Start-up about twice as fast: arena snapshots load in parallel in the background (start-up checks wait for them).
- The animated tab header renders a quarter as often by default (`tab.animation-ticks`, 4) and measures its width at most every 2 seconds per player.
- Holograms and animated decorations are not animated when nobody is within 48 blocks.

**AI assistant: every provider, or a local model**
- Works with Anthropic (Claude), OpenAI, Google Gemini, OpenRouter, Groq, Mistral, DeepSeek, xAI (Grok), Together, Cerebras, and local models through Ollama, LM Studio or any OpenAI-compatible server.
- `/ai key <key>` tells the provider from the key (sk-ant-, AIza, gsk_, xai-, sk-or-...); `/ai key <provider> <key>` sets one by name; `/ai providers` lists them. Keys can also come from environment variables (OPENAI_API_KEY, GEMINI_API_KEY...).
- `ai.provider: auto` (default) tries every provider that has a key in turn if one fails, then a local model on the same machine. `ai.model` empty = each provider's default; `ai.base-url` points to a local model or a proxy.

**Match found pop**: back to the first version's animation: the totem pop with the kit's item, then the kit's weapon pops up in front of you (setting *Duel Found Effect* = `BOTH`; servers on TOTEM/ICON move back by themselves).

**Builds rebuilt like a pro builder**
- **Eco Spawn** is now a floating island: rolling hills with stone cliffs, a deep eroded underside with rock spikes and hanging roots, a tiered quartz fountain, a stream that runs off the edge as a waterfall, timber-framed market houses with pitched roofs, chimneys, flower boxes and striped awnings (the NPCs stand at their counters), a sunken colosseum Warzone with tiered seats, buttresses and an arched gate, an amethyst geode AFK zone with a crystal spire, big branching trees, winding lantern-lit paths and small floating islets.
- New hub style **Vortex Isles** (`/pvpadmin` → Builds → Hubs & Spawns): the Vortex Spire (an obsidian core wound by amethyst and purpur ribbons, floating rings, a beacon crown) on a stepped plaza with a twisting sun-ray floor, eight domed gamemode shrines with columns and coloured bands for the NPCs, and the same island, waterfall, trees and islets.

**Fixes**: builds load their chunks before placing blocks (no more "block change off the main thread" errors when building into new chunks).

## New in 1.6.0 / 2.5.0

**Eco Spawn** (`/ecospawn` where you stand, or `/pvpadmin` → Builds → *Eco Spawn* with a 3D preview; build it in the Eco SMP world)
- A 121 x 121 plaza with a fountain and market stalls: **Shop**, **Sell**, **Shard Shop**, **Auction House** and **Orders** NPCs (click them). `/spawn` lands on it.
- **Warzone** to the north: a walled pit with cover where PvP is always on. Everywhere else on the spawn PvP is off, and nobody can break or build (admins in creative can).
- **AFK zone** in the south-east: a sunken amethyst garden. Standing in it gives 1 shard every 60 seconds (`shards.afk-seconds` / `shards.afk-amount` in eco.yml), with a countdown in the action bar. `/afk` takes you there.

**Shards and Shard Shop**
- Shards from playtime, the AFK zone and kills (3 per kill, not again for the same player within 5 minutes).
- The shard shop has spawners (pig, cow, zombie, skeleton, spider, creeper, blaze, iron golem; they keep their mob when placed) and enchanted books (mending, unbreaking, protection, sharpness, efficiency, fortune), plus a button to the AFK zone.

**Sell fixed**
- 80+ more items can be sold: blocks of diamond/iron/gold/emerald/etc. (9 x the item), raw ores, every log, crops, mob drops, stone types.
- Shulker boxes and bundles with items inside are never sold (their contents came back as nothing before), damaged tools sell for what is left of them.
- A sell price at or above the shop's price is lowered by itself, so nothing can be bought and sold for profit.

**Shop**: many more blocks (every plank, stone family, sandstone, prismarine, end stone, blackstone, copper, quartz...) and three new categories: **Colours** (all concretes, wools, terracottas, stained glass, powders), **Decoration** and **Redstone**. Existing shops keep their prices; only missing items are added.

**Also**
- Match found: the MCPVP pop, the totem animation with the kit's item (setting *Duel Found Effect* = `TOTEM`).
- Map vote: the name under each biome block no longer overlaps the block when it turns or is hovered.
- Tab: the server logo is centred exactly; `tab.logo-offset` (pixels) nudges it if your pack's logo needs it.

## New in 1.5.3 / 2.4.3

**Enable All Arenas really switches every arena on**
- It checks the map itself (the floor under the spawns) instead of guessing. Every arena whose map is there is switched on, even if its snapshot was missing (it is taken again) or a copy was left half-built (it is reset).
- A void map is built again on the spot and switches on when done. Only maps that are really being generated are listed as "still being generated", with the pack progress.
- Anything that can't be switched on says why (no spawns set, world not loaded...).
- A copy that fails to build is removed instead of blocking its map.

## New in 1.5.2 / 2.4.2

**Void arenas are made again** (Mole Mines and any other map)
- A map whose build was cut short by a restart had its spawns saved but no blocks, so players were sent into the void. On every start the plugin now finds these unfinished maps (duel and FFA), removes them and builds them again. The console lists them.
- 1.5.1 could switch such a map on; that can't happen any more (a map only counts as finished once its blocks are all placed).

**Every arena is made and switched on, on start-up and on the first join**
- Every map of the arena pack and an FFA arena for every FFA kit are checked on every start and again when the first player joins: missing or void ones are generated, finished ones are switched on (FFA too).
- FFA arenas are now made right after the first few duel maps instead of after the whole pack.
- Admins who join while maps are still being made see how many are ready.

**Match found pop is back to the original**
- The totem pop with the kit's item, then the kit's weapon pops up at its original size (setting *Duel Found Effect* = `BOTH`). Servers on the 1.4.0 default (`ICON`) move back to it by themselves. The other styles are still in `/pvpadmin` → Settings → Visuals.

## New in 1.5.1 / 2.4.1

**Arenas left "off" are switched on**
- A restart while the arena pack was still generating maps left those arenas saved as off. On the first start of 1.5.1 every finished arena (duel and FFA) that was left off like this is switched on, and the console tells you how many.
- From now on, a map whose generation is cut short is either switched on (if it finished) or made again on the next start. It is never left off.
- `/pvpadmin arenas enableall` (console too), or the **Enable All Arenas** button in `/pvpadmin` → Duel Arenas / FFA Arenas, switches on every finished arena at once. It also lists any that are still generating; those turn on by themselves when done.

## New in 1.5.0 / 2.4.0

**Mole PvP** (kit `mole`, duels, queue and FFA)
- An underground digging mode. You spawn in a sealed chamber deep under the ground, dig your way through, and hunt the other player.
- **The kit:** netherite pickaxe and diamond shovel (Efficiency V), iron armor, diamond sword, bow, golden apples, cobblestone, torches and a water bucket.
- **Sonar:** every 30 s, the action bar tells you roughly where the enemy is ("~40 blocks north-east, 12 below").
- **Sudden death** after 8 minutes: everyone glows and the sonar pings every 5 s.
- **Its own map, Mole Mines,** 401 × 401 blocks and 82 deep, the biggest map on the server:
  - real rock layers with ores, granite, diorite and gravel pockets;
  - three levels of old mine shafts (oak frames, rails, lanterns, ladders between the levels);
  - two amethyst crystal caverns lit by glowstone and froglights;
  - an underground lake, and lava falls behind tinted glass;
  - a mining camp on top: the mine entrance, cranes, tents and a viewing deck.
  - Bedrock under every column. Barriers keep the fight underground.

**Practice bots and training dummies: coming soon.** `/bot` and `/train` already answer "Coming soon", and so do the map mobs in the Bot Practice hall. The bots will arrive in a later update.

**The Academy** (`/learn`): a course for each gamemode:
- Sword, Axe & Shield, Mace, Elytra Mace, Crystal PvP, Potions, UHC, Mole PvP.
- 21 lessons in all, one tip or skill each, for example crits, w-tapping, shield breaks, the stun slam, density vs breach, the elytra dive, obsidian placement, anchors, totem swaps, pot timing and tunnelling.
- **Every lesson:**
  1. **Film:** a smooth camera circles a coach who shows the move. The tips appear one by one as titles and on a floating panel.
  2. **Slow-motion replay** of the key moment, from close up.
  3. **Your turn:** the kit plus a training dummy, and a goal to reach in time (e.g. "break the shield 3 times", "smash for 14+ damage", "pop 2 totems", "dig 30 blocks"). Passing saves the lesson; your progress shows as bars.
- `/learn skip` jumps to the practice part. The course list has a **search** box.

**Bot Practice hall** (`/pvpadmin` → Builds → *Bot Practice Hall*, or `/pvpadmin bothall`)
- A hall cut into the rock, with an alcove per map. Each alcove has a wall in the biome's blocks (badlands stripes, desert sandstone, snow and ice, cherry, crimson, end…), a carved pedestal with that biome's mob, and the map's name floating above.
- **Clicking a map mob** shows "Bots coming soon". The **Academy** NPC in the middle already works.
- It has a 3D preview. `/pvpadmin bothall remove` takes the mobs and NPCs away.

**/alts** (staff: mods and admins)
- `/alts <player>` lists every account that joined from the same IP. For each: online/offline, banned/muted, how many IPs are shared, last seen, and **[Check]** / **[Punish]** buttons.
- Staff get an alert when an alt of a banned player joins.
- IPs are stored only as salted hashes (the salt is in `alts-salt.yml`).

## Earlier: 1.4.0 / 2.3.0

**Every arena and every FFA, generated and switched on**
- The arena pack runs again on servers that already have one (pack 6). It adds every map that was missing:
  - Cherry Grove, Crimson Forest, Mushroom Fields and the Champions colosseum;
  - the three new show arenas below.
  - A vanilla biome the server's seed simply doesn't have near spawn (Pale Garden on the test seed) is skipped once, with a warning. It is not retried on every start.
- **Every FFA kit gets a decorated FFA arena:**

  | Kit | FFA arena |
  |---|---|
  | combo | Oasis |
  | axe | Gladiator |
  | trident | Frost Palace |
  | gapple | Volcano |
  | nodebuff | Deep Dark |
  | builduhc | Plains |

  Any FFA kit you add later gets one too. On the update, every finished FFA arena is switched on.
- `/pvpadmin generate ffa … <size>` now goes up to 448. **Generate All Missing** also makes the missing FFA arenas.

**Three new show arenas** (designed pool: sword, UHC, pot kits; they have 3D previews in the map vote)

| Arena | Look |
|---|---|
| **Desert Palace** | Sand floor, red / yellow / cream walls, blue pillars, orange onion-dome towers, a palace with golden gates, a balcony and minarets |
| **Olympus** | Sand floor, eight rows of sandstone seats, a ring of tall quartz arches with red banners, and a giant stone warrior with a spear and a round shield |
| **Timber Fort** | Packed-earth floor, a stone wall with a lantern walkway, timber-frame houses with red roofs and balconies, a red-and-white striped tent gatehouse, a rock outcrop with pines |

All three stand on bedrock in every column.

**Vanilla biome maps are now truly vanilla, just without caves**
- The site picker no longer looks for flat, treeless ground. It used to mark spots down for trees and hills, so maps came out bare.
- Now a map is the biome as it really is: forests, hills, cherry trees, snowy peaks.
- Only the caves are filled:
  - underground air, cave water and lava, moss and dripleaves become the rock around them;
  - ravines and cave mouths are closed.
- Ores, gravel and dirt pockets, geodes, frozen lakes and rivers stay exactly as vanilla has them.
- Every biome map is re-cut by itself after the update, on a new site. The designed arenas are not touched.

**Hub: Royal Castle** (the new one-click default in **Hub Setup**; the old hubs are still there)
- **A walled castle on a hilly floating island:**
  - curtain walls with battlements and banners;
  - four round corner towers with slate cones;
  - a gatehouse with a raised portcullis;
  - a keep with turrets and a tall great tower.
- **The courtyard:** lawns with hedges and flowers, a tiered fountain, the NPCs along the main path and the leaderboard holograms.
- **Around the castle:**
  - woods and hills;
  - a lake with a waterfall;
  - a fort on the east hill, joined to the castle by an arched stone bridge, with a hall for FFA and events;
  - a watchtower on the west hill;
  - just three market stalls.

**Builds panel with 3D previews** (`/pvpadmin` → **Builds**)
- Every hub / spawn style and every small build is a tile.
- Click one, then **Preview (3D)**: a miniature of the design floats in front of you for 30 seconds and turns slowly. Only you see it.
  - About 800 pieces for the castle, built in the background in about 2 seconds.
- Then **Build here**. `/pvpadmin preview <style>` and `/pvpadmin stoppreview` work from chat too.

**Armor trims and shield designs** (`/cosmetics`, the Settings menu, or the kit editor)
- **Armor trim:** any pattern in any material, on all the armor of your kits. Each kit can have its own trim, or none.
- **Shield:** a base colour plus up to 6 banner layers, or a ready-made design: Vortex, Flame, Royal, Skull, Creeper, Frost, Crusader, Nether.
- Applied whenever a kit is given (duels, FFA, refills). Saved per player.
- Also from chat: `/cosmetics trim <pattern> <material> [kit]`, `/cosmetics shield <design>`.
- **Trims right in the kit editor:**
  - while arranging a kit (`/kiteditor`, or `/kiteditor <kit>` to go straight to one), click any armor piece;
  - pick a pattern, then a material, and you're back on your layout;
  - each kit keeps its own trim, or "No Trim", or "Same as Every Kit";
  - the editor shows the armor with its trim.

**Homes by role:** players 3, helpers 4, mods 5, admins 10 (`smp.homes.*`). Rank permissions `practice.homes.<n>` still raise the limit.

**Match-found pop:** the kit's icon pops up at a **medium size** (the crystal was huge). It is the new default; the old totem and sword pops are still options.

**SMP commands fixed**
- `/tpa` and `/tpahere` take the exact name: `/tpa Bob` never reaches Bobby.
- **A request is only used up when the teleport really happens.** If the teleport is cancelled (moving, damage, combat, cooldown), both players are told. The request stays open with an **[ACCEPT AGAIN]** button.
- `/tpaccept` works while you are in combat, as long as you are not the one who teleports.
- When a request expires, both players are told. An expired request can't be accepted any more.
- Tab completion lists the players in the same mode's overworld, nether and end, and hides players with invisible status.
- `/rtp overworld|nether|end`.
- A teleport into someone else's claim is blocked.

**Tab list: everything lines up, and 11 designs**
- **Columns:** every column starts with a section title on the top row. A section never breaks across two columns.
- **Rows:** names line up inside each section, after the widest dot / flag / rank. Elo and stat values line up too.
- **Dividers:** sized to the widest line of the header and footer.
- **5 new designs:**

  | Design | Look |
  |---|---|
  | **Royal** | Purple and gold, ♛ crowned title, Court / Nobles / Guards / Allies columns |
  | **Ocean** | Aqua gradients and wave dividers, On Board / Captains / Fleet |
  | **Retro Arcade** | ▓▒░ 8-bit banner, 1UP / HI-SCORE columns, INSERT COIN footer |
  | **Monochrome Pro** | White and grey, thin rules, your Elo / tier / rank first |
  | **Autumn** | Orange-to-red ✿ gradients, Harvest / Golden Leaves / Hearth |

## Earlier: 1.3.1 / 2.2.1

**Map vote: room between the biome blocks** (cpvp, cart, mace, spear, creeper and every other biome-map kit)
- The biome blocks are spread wider. Neighbours are now **0.95 blocks apart instead of 0.70**: at least 21° on the arc, and more when the names are long.
- **Long names wrap onto two lines** at the space nearest the middle (*Badlands / Red Desert*, *Mushroom / Fields*), so the labels never run into each other.
  - A two-line label sits one line lower, so it never touches its block.
- The two rows are **1.0 block apart instead of 0.66**, so a hovered top block and its label stay clear of the row below.
- The final-two pick is spread out the same way.
- Boards with 3D arena previews (sword, UHC, pot kits) already had their own wider spacing and are unchanged.

**Tab list: 6 designs, picked by the admin** (`/pvpadmin` → **Tab Design**, or `/pvpadmin tabdesign <id>` from the console)

| Design | Inspired by | Look |
|---|---|---|
| **Classic** (default) | MCPVP | Your current tab, unchanged: animated logo, Staff · Friends · Online · Top · kit columns with heads, flags, status dots and ranks |
| **Competitive** | Practice servers | Gold server name over a rule, slate `▌` section bars, every leaderboard row shows **tier and Elo**. The footer shows *your* Elo, tier and leaderboard rank, plus live matches and players in queue |
| **Network** | Big minigame networks | A bold yellow server name and "You are playing on *ip*". **No fake columns**: the normal player list, sorted by rank colour. Footer with Discord, store and players online |
| **Minimal** | Clean SMP servers | One line of header and one of footer. **One column** of names with ping only: no skins, flags, dots or ranks. Works in every world |
| **Neon** | Arcade / cyber servers | A fast magenta→cyan animated logo, glowing `· · ·` dividers between sections, and ★ ⚡ ♥ ✚ gradient section titles |
| **Stats Wall** | Clans and leagues | **All four columns are leaderboards** (Elo, most wins, best streak, FFA kills) with the value next to every name. Footer with live matches, queue, players online and TPS. Works in every world |

- Each design is a whole look:
  - header and footer;
  - logo colours and animation speed, and the gradient of each mode;
  - which list is shown (the 4-column grid, one column, or the normal player list);
  - the section titles;
  - how a row looks (heads, flags, status dots, ranks, tier and Elo, ping bars).
- Choosing a design writes its header, footer and colours into `tab.yml`; the old file is kept as `tab-backup.yml`.
  - It also sets the column options (`tab.columns.*`) and `tab.design`.
  - Your own logo text is kept. Edits you make on top of a design stay until you pick another one.
- The hover on each tile in the picker shows what the design looks like.
- New footer placeholders for your own `tab.yml`:

  | Placeholder | Shows |
  |---|---|
  | `<my_elo>` | the viewer's Elo in their best kit |
  | `<my_tier>` | their tier in that kit |
  | `<my_rank>` | their leaderboard place in that kit |
  | `<matches>` | matches being played |
  | `<queued>` | players in a queue |

- New column sections: `top_wins`, `top_streak` and `top_ffa`. They are read from the database once a minute in the background.
- Updating keeps your current tab: `tab.design` starts as `CLASSIC` and nothing in `tab.yml` changes until you pick a design.

## Earlier: 1.3.0 / 2.2.0

**Updating keeps everything: just replace the jar**
- **Before anything loads, a full backup is taken** in `plugins/VortexRealm-Vault/`. It includes every setting, kit, arena, world, NPC, hologram, rank and the database. It now also includes every player's **inventory, ender chest, XP, advancements and statistics** from every world (`playerdata`).
  - The same backup runs every 6 hours and at shutdown, too.
- **Settings:** all your values are kept, and new options are added with their defaults. An old value this version can't read is kept under `old-settings:` in `config.yml` instead of being dropped.
- **Kits:** your kits are never overwritten.
  - New kits from an update are added.
  - Default kits you never edited get the new default items, and keep your queue switches, order and arenas.
  - If you edited a default kit, yours stays. The new default is added next to it as `<kit>_new`, switched off.
  - A kit you deleted stays deleted.
- **Players:** stats, ranks, kit layouts, friends, economy and the PvP/SMP inventories are updated in place. Nothing is reset.
- **NPCs, holograms, messages, worlds, the hub and regions** are kept. New message lines are added without touching edited ones.
- **Switching to VortexRealm:** if both plugin folders exist, anything VortexRealm is missing is copied over from PracticePvP: files that don't exist yet and database rows it lacks. Nothing on the VortexRealm side is overwritten.
- **After the update** the console and every admin who joins get a short report of what was carried over. `/pvpadmin` → Data Vault has *Last Update Report*, *Snapshot Now* and *Restore* (restores plugin data and player inventories at the next start).

**Arenas: deeper, no caves, bedrock under every one**
- Every arena ends on uneven bedrock, so digging down never reaches the void. That covers biome maps, deep crystal maps, End islands, designed arenas, colosseums and the floating plazas, whose whole underside is now bedrock.
- Biome maps are deeper (duel ground 34 → 48 blocks, FFA 44 → 56; crystal stays 100) and still have no caves, ores or geodes.
- Existing arenas are rebuilt automatically in the background after the update. Hand-edited arenas are left alone.

**Swear filter and mutes** (`/pvpadmin` → Chat Moderation, or `/chatfilter`)
- **What it catches:** swearing in chat, `/msg`, `/r`, party chat, signs and anvil names, including tricks like `f.u.c.k`, `f u c k`, `fuuuck`, `sh1t` and accents.
  - English, French and Spanish words are included.
  - Allowed words like *class*, *assassin* and *computer* are never hit.
- **What happens:** the message is blocked (or sent as `****` if you prefer), the player is told, and staff get an alert.
  - **Auto-mute:** 2 minutes, then 10, then 30 for repeat offences within 24 hours.
  - Muted players can't chat or `/msg` and see the time left. Mutes survive restarts.
- **Commands:** `/mute <player> [10m|2h|1d|perm] [reason]`, `/unmute`, `/mutes`.
- **The Chat Moderation screen** lets you edit everything without a file:
  - filter on/off, block or censor, mute steps, reset window, staff alerts;
  - blocked words and allowed words;
  - muted players (unmute or extend);
  - recent offences;
  - a message tester.

**NPCs: many more options** (`/npc` → an NPC → *Look…*, *Name Tag…*, *Behaviour…*)
- **Look:** armor (copies yours), off-hand item, size 0.5–2×, pose (standing, sneaking, lying, sleeping, gliding), and an idle animation (waves, looks around, turns slowly, cheers).
- **Skin:** a player name, or *Skin = My Skin*, which saves your textures in the file so no online lookup is needed.
- **Name tag:** style (classic, glass, neon, ember, hologram, none), background colour and opacity, text size, height, and an animated gradient title.
- **Behaviour:** click sound, click cooldown, a permission needed to use it, your own greeting text and radius, and the floating item (on/off, any item).
- **Copy NPC Here** duplicates an NPC with every setting.

**Leaderboard holograms: fully customisable** (`/pvpadmin` → Leaderboard Hologram → New / Edit nearest / Remove nearest)
- **What it shows:** any kit or overall, or **several kits taking turns** with a page-flip animation. Ranked by Elo, wins, win %, best streak, FFA kills or FFA K/D, with 3–20 rows.
- **How it looks:** 9 designs, your own title (`<kit>` and `<stat>` work), colours for #1, #2, #3 and the rest, accent, background opacity, size and line width. Tier and wins/losses can be shown or hidden.
- **Placement:** turns to the viewer or faces one way (*Face Me*), move, up/down, copy, and the refresh time.

**New hologram designs** (leaderboards and text holograms)
- **Podium:** the top 3 stand as players in their own skins on gold, silver and bronze pedestals, with name plates and the rest of the list above.
- **Framed Board:** a dark oak frame with gold corners and lanterns.
- **Sci-Fi Hologram:** a cyan projection with a scanning line, a flicker and a projector beam.
- **Royal:** gold trim, a purple card and a spinning crown with sparkles.
- **Marquee:** a title that scrolls like a stadium screen, with striped rows.
- **Orbit:** the kits' items circle the card.

**New hub: Practice Town** (`/pvpadmin` → Hub Setup, or *Build Spawn Here* → *Practice Town*)
- A chill, MCPVP-style town, not flat and not Vortex-themed:
  - a cobbled plaza with a tiered fountain and a sword statue;
  - four lamp-lit streets with benches and trees;
  - a market street with striped stalls and bunting;
  - soft hills, groves and flower meadows around it.
- **A building for every mode, each with its NPC at the door:**
  - Duels Hall (sparring rings, banners);
  - Ranked Arena (a small colosseum with gold trim);
  - FFA Pit (sunken sand ring);
  - Leaderboard Hall (podium and framed boards placed automatically);
  - Kit Workshop (anvils, smithing);
  - Events Stage (amphitheatre);
  - Shop (awning, counter);
  - Spectate Tower.
- **Extras:** a pond with a bridge, a parkour course and a training yard. A welcome board, a tiers hologram, a Discord marquee and a floating crown are placed for you.
- It is the new default for the hub and the PvP lobby. **Nothing is rebuilt by itself**: your current hub stays until you click Hub Setup.

**Fixes**
- Holograms and NPCs that appeared in the same moment a player arrived could stay invisible until the player walked away. They now wait until the player's game has loaded that spot.
- The colosseums' surrounding hills and outer wall now end on bedrock too (they used to stop in stone over the void).

## Earlier: 1.2.1 / 2.1.1

**Duel maps: vanilla terrain, no caves**
- The biome maps keep the vanilla **surface**: hills, trees, grass and flowers. Everything under it is now solid:
  - no caves, cave openings, ravines or holes, at any depth;
  - no ores, gravel or clay pockets, dripstone or sculk; the rock below is plain stone, deepslate or netherrack, with dirt and sand bands under the surface as in vanilla;
  - deep trenches and cave mouths at the surface are filled up to the ground around them, topped with the same block (grass, sand, snow, terracotta).
- Existing biome and crystal maps are re-cut automatically on the first start, in the background: about 1½ minutes per map, so roughly 20 minutes for all 12. Each map stays out of the rotation until it is done, so for a while after the update fewer maps are offered.

**Map vote: biome selection or 3D arena selection**
- **Crystal, cart, mace, spear and creeper** pick between the **biome maps**: each map is its biome block (grass, snow, sand, red sand, terracotta, end stone) with the biome as its name ("Snowy Plains", not "Snowy Plains (Crystal)").
- **Sword, UHC, Build UHC, pot, nethpot, nodebuff, combo, gapple and shield** pick between the **designed arenas**. Each one shows as a **detailed 3D miniature** of the real arena:
  - built from the arena itself: walls, towers, trees, water and floor patterns, with up to 56×56 detail;
  - turned toward you and tilted so you see the top and two sides;
  - looking at one grows it, leans it toward you and turns it so you can see every side; your opponent's hover grows too;
  - a banned arena folds into a small block that flies into the circle;
  - the picked arena glides to the middle and turns slowly.
- Mace kits now play the biome maps. To give them the Grand Colosseum back, set `arena-type: VANILLA,MACE` for the kit in `kits.yml`.
- `match.map-ban-3d-preview`: `FLAT_KITS` (default) shows the 3D arena selection for the designed arenas, `ALL` for every map, `OFF` for none.
- `match.map-preview-detail`: `LOW` / `NORMAL` / `HIGH` = at most 140 / 240 / 400 display parts per miniature. Each player sees only their own board.

**Smoother animations**
- Map vote elements (cubes, labels, title, header, vote items) update every tick instead of every second tick, so eased curves keep their shape.
- Hovered cubes pop with a small overshoot. Maps slide into the free places with an ease-in-out after a ban.
- The 3D miniatures move, grow and turn as one piece: every few ticks each part gets one new target, and the client glides between them.
- The spinning item over hub NPCs turns and bobs in small steps, so it never pauses.

**Fixes**
- **The map vote's text never showed in 1.2.0:** map names, the "A VS B" title, the header and the map cubes did not appear. There were two causes:
  - the board was spawned in the same tick as the teleport, before the client had those chunks, so the server never sent it;
  - texts without a rotation never grew from their spawn size.
  Both are fixed and confirmed on a live client.
- With `map-ban-3d-preview: ALL`, valleys on biome maps no longer leave holes in the miniature, and deep maps are no longer mostly underground rock.

## Earlier: 1.2.0 / 2.1.0

**Arenas**
- **100% vanilla maps, made for PvP.** Crystal, spear, creeper, cart and spear-mace fight on real vanilla terrain, cut from the world generator with a fixed seed. Every server gets the same maps.
  - The maps are MCPVP's set: **Plains, Snowy Plains, Desert, Badlands Red Desert, Badlands Mountains, The End Island** (obsidian pillars) and **Outer End Islands** (chorus).
  - Each site is searched for: pure biome, no lava, little water, fair flat spawns, few trees.
  - Optimised for PvP: caves under the surface are filled, frozen lakes and rivers become snowy ground (no slippery ice, no flooding), and leaves never decay.
  - Two different maps never use the same place.
- **Crystal maps are all deep** (Plains, Snowy Plains, Desert, Red Desert and Crystal Flats), with room to dig and bomb.
- **No walls, twice as big:**
  - duels 192 (was 96), FFA 256 (was 128), mace 400 (was 230), vanilla maps 256;
  - the world border is the edge;
  - colosseums are bigger and have no invisible barrier columns.
- **8 new designed arenas** for the sword kits: Sakura Dojo, Ancient Ruins, Desert Oasis, Frozen Palace, Volcanic Crater, Deep Dark Plaza, Mushroom Isle and Sky Castle Courtyard. All are open floating plazas.
- **Kits pick their pool:**
  - sword, pot, nethpot, nodebuff, combo, shield and gapple play the designed arenas;
  - crystal plays the deep maps;
  - mace plays the grand colosseum plus a mix of the others;
  - the other kits play a mix of vanilla and designed maps;
  - `arena-type` in `kits.yml` takes a list (`VANILLA,DESIGNED`, `CRYSTAL`, `MACE`).
- **Auto copies:** a map that has been full for 20 s gets another copy, up to `arena.max-copies`.
- **Tournaments:** the host picks the start (**Elevator** / Cage / Freeze) and can switch off gates and fences completely.

**Animations (MCPVP screen recording)**
- **Map ban rebuilt:**
  - tilted map cubes in two arcs, eased on every tick (nothing jumps or snaps);
  - hovering grows the cube with a green "CLICK TO BAN" fade, and you see your opponent's hover live;
  - banned maps fly into a slowly turning circle while the others slide into the free places;
  - the last player who didn't ban picks the final map, which tumbles in the centre;
  - preference votes have Yes and No sprites;
  - at the end the circle widens, recedes and shrinks away.
- **"A VS B" title:** a light band with a trailing shadow sweeps through each name in the name's own colour. The same sweep plays on the match intro, where the names glide in pixel by pixel and VS pops.
- **XP score and points text** is anchored in one place. It no longer follows you, it keeps clear of the other player, and it re-appears in front of you if you are sent to the lobby mid-animation.
- **Match end:** the winner can walk around and the loser becomes a spectator whose camera turns smoothly to the winner. The same happens between rounds. The victory screen lasts 5 s.
- **DEATHMATCH – Next kill wins!** at double match point, an MCPVP **ROUND REPORT** card after each round, and a lobby broadcast for Tier Tests, Tier Defenses and Tier Challenges with a **[Spectate]** button.
- Smooth camera turns in the rank-up animations.

**Tab and logo**
- The tab adapts to the server: one short column with few players (leaderboards grow with the player count; kit boards appear from 4 players), up to 4 columns of 20 with sections lined up.
- There is a new **Online** section, and the Friends section only shows when friends are online.
- During the map vote the tab has no rows, like MCPVP.
- The **VR logo** is in the resource pack and shows at the top of the tab (header line `<logo>` in `tab.yml`).

**SMP, hub and economy**
- **Every SMP world has its own nether and end** (`<world>_nether`, `<world>_the_end`). Nether and end portals lead there and back, for items and mobs too, and the dragon's exit brings you back to that SMP world.
- **Dying in SMP respawns you at your bed** (same mode) **or at the hub**, never in the PvP lobby.
- **Castle Market hub:**
  - a fountain plaza and four lamp-lit avenues;
  - a market street of striped-awning stalls under bunting;
  - four small castles with gates, towers and a keep;
  - stone arches and gardens;
  - no houses.
  It is the new default for the hub and the PvP lobby.
- **Economy (DonutSMP style):**
  - money in K/M/B;
  - `/pay` asks to confirm large amounts;
  - `/bal`, `/baltop`, `/sell`, `/worth`, `/shop`, `/ah`, `/orders`;
  - **Shards** for playtime and kills, spent in the `/shards` shop;
  - choose which SMP modes use the economy in `eco.yml` → `worlds`.
- **Vault:** the built-in economy is offered to other plugins through Vault. With `provider: auto` (the default), an economy plugin you already have (EssentialsX, CMI…) is used instead, so all plugins share one balance. `builtin` always uses ours; `external` only uses the other plugin's.

**Fixes**
- Cage starts (sword) were cut 4 s short by the stuck-countdown guard.
- Stale-match cleanup could fire before the victory screen ended, so tournaments replayed pairs.
- Arena resets waited behind generation.
- FFA arenas were never generated when the first start was interrupted.
- Arena updates could start while the pack was still generating.
- Entering `world_nether` from an SMP main world sent players to the PvP lobby.
- "No key layers" errors on flat worlds.

## Earlier: 1.1.0 / 2.0.0 (from the MCPVP screenshots)

**Friends, status and privacy**
- `/friend add|accept|deny|remove|list|requests <player>`, `/follow`, `/unfollow`, plus a Friends screen with online status, where each friend is, and buttons to duel, message, spectate, invite to party or remove.
- **Status**: ● Online / ● Invisible / ● Do Not Disturb (`/status`, or Settings → Privacy → Requests).
  - *Invisible*: you appear offline in friends lists and the tab, and nobody can send you duel requests.
  - *Do Not Disturb*: only the groups you tick can reach you.
- **Requests – <status>** screen: who may send you party invites, duels, messages and friend requests.
  - Groups: Friends / Following / Followers / Famous / Noobs (everyone else).
  - A **Tier** minimum and an **Overall Ranking** slider, both applied to everyone else (the Noobs group).
  - Separate rules for Online and for Do Not Disturb.
- **Settings hub** (`/settings` or the lobby item): Privacy (Requests, Teleports, Location, Visibility), Chat, Name Tag, Gameplay (all the old settings), Regions and Friends.

**Duels like MCPVP**
- `/duel <player>` opens **"Select a kit to challenge … in!"** with the **Weapons / Vanilla / Skills / Most Played** tabs. The duel screen then shows `#rank | You 🏳 VS Them`, a **First To** slider, an **Experimental Ping Equalizer** checkbox, **View Advanced Options** (map, spectators) and **Send Duel**.
- **Tier Challenge**: `/challenge <player>` starts a *ranked* first-to-3 with a bigger Tier Score swing.
  - The screen shows "HT5 | A VS B", First To and Location, the Tier Score warning, and **Ping Equalizer** and **I understand** checkboxes. The challenged player must tick "I understand" too.
  - Leaving counts as a loss, and there is a per-pair cooldown.
- **Ping Equalizer** (experimental): the player with the lower ping gets a small, capped delay so both play with a similar round trip. It is removed when the match ends.

**Tiers**
- **Tier Defense Challenge**: when your points fall below your tier, the next ranked queue asks:
  - The prompt reads "*You are queuing for a Tier Defense Challenge in Sword. First to 5 … Win to defend your tier. If you lose, you rank down to HT4*", with Accept Challenge / Queue Normally.
  - Winning keeps the tier and losing drops it. After a few normal matches without defending, you drop automatically (configurable).
- The existing Tier Test Challenge, placements and LT5–HT1 ladder are unchanged.

**Matches**
- **Map vote with turns**:
  - A floating green banner shows "**A VS B – MAP VOTE – YOUR TURN: BAN 1/2 8s**" (or "OPPONENT'S TURN"), and chat shows "Dream banned Snowy Plains."
  - A player who runs out of time skips their bans.
- **Match intro**: the two names slide in and meet at a pulsing **VS**.
- **Spectators**: "👁 X started spectating." / "stopped spectating." for everyone in the match. Spectators talk to the match as `<👁 name> message`. Players can block spectators per duel. Staff spectate silently (configurable).
- **End map**: end stone with obsidian pillars, iron-bar cages and chorus plants.

**Tab, nametags and lobby**
- **MCPVP tab**:
  - 4 columns × 20 rows: Staff, Friends (with status dots), Creators, Top Players and the top players of chosen kits (End Game, Sword, Spear, Mace by default), with heads and flags.
  - Footer: "**N Online · Lowest Ping Host: Miami, Florida**".
  - It falls back to the normal tab automatically if the server version doesn't support it.
- **Nametag**: `⚔ LT4 #13428 | Name 🏳` (overall position and flag can each be hidden). Optionally, your overall position is shown as your **XP level** in the lobby.
- **Regions** (`/region`, Settings → Regions): set up in `regions.yml`. Picking another region moves the player there with the vanilla transfer packet; the target server needs `accepts-transfers=true`.
- Vortex Realm branding: the purple-to-cyan animated tab logo (your custom colors are kept).

## Commands (new)

| Command | Use |
|---|---|
| `/friend …` (`/friends`) | add, accept, deny, remove, list, requests |
| `/follow <player>`, `/unfollow <player>` | follow / stop following |
| `/status [online\|invisible\|dnd]` | change status (no argument opens Settings) |
| `/challenge <player> [kit]` | send a ranked Tier Challenge |
| `/region [id]` | pick a server region |
| `/rankup <kit> defend\|normal` | answer a Tier Defense Challenge from chat |
| `/vortex [migrate]` | *(VortexRealm only)* version / move PracticePvP data |
| `/shards` | your shards and the shard shop (economy worlds) |
| `/pay <player> <amount> [confirm]` | amounts like `250k`, `1.5m`; large amounts ask to confirm |
| `/mute <player> [10m\|2h\|1d\|perm] [reason]`, `/unmute <player>`, `/mutes` | staff: mute, unmute, list muted players |
| `/chatfilter` (`/chatmod`) | staff: the Chat Moderation screen (swear filter, words, mutes) |
| `/bot [kit] [difficulty]`, `/bot leave` | fight a practice bot |
| `/train [shield\|crystal\|combo\|mace\|elytra]` | training dummies |
| `/learn [course\|lesson]`, `/learn skip`, `/learn stop` | the Academy |
| `/alts <player>` | staff: accounts on the same IP |
| `/afk` | go to the AFK zone of the Eco Spawn (shards while you stay inside) |
| `/ecospawn [remove]` | admin: build the Eco Spawn where you stand (or `/ecospawn <world> <x> <y> <z>` from the console) |
| `/pvpadmin arenas enableall` | admin: switch on every finished duel and FFA arena |
| `/pvpadmin bothall [remove]` | admin: build the Bot Practice hall where you stand |
| `/pvpadmin tabdesign <classic\|competitive\|network\|minimal\|neon\|stats_wall>` | admin: switch the tab design (console too) |

All the old commands still work (`/duel`, `/queue`, `/ffa`, `/party`, `/spectate`, `/settings`, `/pvpadmin`, SMP, economy, claims, `/ai`, `/ss`…).

## Permissions (new)

PracticePvP uses `practice.*` and VortexRealm uses `vortex.*`. The suffixes are the same:

- `….famous`: counts as Famous (the Creators column and request filters). Server ranks listed in `social.famous-ranks` count too.
- `….friends.unlimited`: no friend limit.
- `….staff`: bypasses request privacy and sees Invisible players. Can also `/mute`, `/unmute`, `/mutes`, `/chatfilter`, and gets swear-filter alerts.
- `….chat.filter.bypass`: not checked by the swear filter (off for everyone by default, ops included).

## New config options (`config.yml`, also in `/pvpadmin` → settings)

| Path | Default | |
|---|---|---|
| `social.enabled` | true | friends, follows, status, privacy |
| `social.max-friends` | 200 | |
| `social.famous-ranks` | media,partner,youtube,creator,famous | |
| `tab.columns.enabled` | true | MCPVP 4-column tab |
| `tab.columns.where` | pvp | `pvp` or `everywhere` |
| `tab.columns.sections` | staff,friends,creators,online,top,endgame,sword,spear,mace | section order; any kit id works |
| `tab.columns.section-size` | 8 | |
| `tab.design` | CLASSIC | set by the Tab Design picker |
| `duels.max-first-to` | 5 | |
| `duels.ping-equalizer.enabled` / `.max-delay-ms` | true / 120 | |
| `spectators.join-messages` / `.chat` / `.hide-staff` | true / true / true | |
| `match.map-ban-turns` / `match.map-ban-turn-seconds` | true / 15 | |
| `match.map-ban-3d-preview` | FLAT_KITS | 3D arena miniatures for the designed arenas (`ALL`, `OFF`) |
| `match.map-preview-detail` | NORMAL | 140 / 240 / 400 parts per miniature (`LOW`, `NORMAL`, `HIGH`) |
| `chat.filter.enabled` / `.action` / `.auto-mute` | true / BLOCK / true | swear filter; `CENSOR` sends the message as **** |
| `chat.filter.mute-steps` / `.reset-hours` / `.notify-staff` | 2,10,30 / 24 / true | mute minutes per offence, when offences are forgotten |
| `arena.duel-depth` / `arena.ffa-depth` / `arena.crystal-depth` | 48 / 56 / 100 | ground under arenas; every arena ends on bedrock |
| `match.max-bans-per-player` | 2 | |
| `ranking.tier-defense.enabled` / `.from` / `.first-to` / `.grace-matches` | true / LT4 / 5 / 5 | |
| `ranking.tier-challenge.enabled` / `.first-to` / `.multiplier` / `.cooldown-minutes` | true / 3 / 2.0 / 30 | |

| `arena.vanilla.enabled` / `.size` / `.seed` | true / 256 / VortexRealm | vanilla maps; the seed decides which terrain you get |
| `arena.vanilla.fill-caves` / `.structures` / `.copies` | true / false / 1 | filled caves, no villages, copies to start with |
| `arena.auto-copies` / `arena.max-copies` | true / 4 | extra copies of busy maps |
| `arena.colosseum-barriers` | false | the old invisible barrier columns |
| `arena.duel-size` / `arena.ffa-size` / `arena.mace-arena-size` | 192 / 256 / 400 | twice as big; values still on the old defaults are raised on update |
| `events.tournament-start-mode` / `events.tournament-gates` | ELEVATOR / false | |
| `match.end-delay-ticks` | 100 | victory screen (winner moves, loser spectates) |
| `smp.own-dimensions` | true | `<world>_nether` / `<world>_the_end` per SMP world |
| `smp.respawn-at-bed` | true | otherwise SMP deaths go to the hub |
| `eco.yml` → `provider` / `worlds` / `pay-confirm-above` / `shards.*` | auto / [ECO] / 100000 | Vault provider, economy worlds, shard rates and shop |

`regions.yml` lists the regions (name, city label, host, port, flag). `current` is this server's region.

## Building from source

```
cd vortex-realm
mvn package            # builds both plugins
# practicepvp/target/PracticePvP-1.3.0.jar
# vortexrealm/target/VortexRealm-2.2.0.jar
```

Requires JDK 21 and Maven. The source of 1.0.0 was recovered from the jar with Vineflower. Every class was checked against the original bytecode, and the few places where the decompiler had changed behaviour were fixed.

## How it was tested

1.4.0 / 2.3.0, on Paper 1.21.11:
- **Bot tests (mineflayer):**
  - homes cap at 3 / 4 / 5 / 10 for player / helper / mod / admin;
  - the crystal pop peaks at 0.62 and the sword at 0.89 (it was 1.55);
  - after `/cosmetics trim silence netherite` and `/cosmetics shield skull`, the duel kit's netherite armor carries the trim and its shield the skull banner;
  - `/pvpadmin preview royal_castle` sends 797 pieces to the admin and none to other players.
  - **tpa:**
    - `/tpa Bob` doesn't reach Bobby;
    - a cancelled teleport keeps the request open, and accepting again teleports;
    - tpahere moves the receiver;
    - tab completion finds a player in the nether;
    - `/tpaccept` works while the receiver is in combat.
  - All 11 tab designs apply and render their header and footer, with no errors (52/52 checks).
- **Arena pack 6 on an upgraded server:**
  - the 3 show arenas, Champions, Crimson, Mushroom Fields, Cherry Grove and the FFA arenas were generated;
  - Pale Garden isn't near spawn on the test seed, so it was skipped once.
- **Biome maps re-cut with the new rules:**
  - on bedrock in 100% of columns, with no caves;
  - ores, gravel pockets and geodes kept;
  - Cherry Grove keeps its forest and its snowy peak.
- **Not checkable headless:** how it looks on a real client screen. The renders in `previews/` stand in for that.


1.3.1 / 2.2.1, on Paper 1.21.11:
- **Self test:** 155 passed, 0 failed (VortexRealm: 154 / 0). The new checks:
  - every tab design renders its header, footer and section titles with no raw tags left;
  - every biome label fits between its neighbours at the spacing its board uses (11 maps).
- **Bot test** (two bots on the server), 30/30 passed:

  | Design | Fake tab entries | Checked |
  |---|---|---|
  | Classic | 10 | applies, no errors |
  | Competitive | 7 | header, the "Your Elo" footer |
  | Network | 0 (normal list) | header, store footer |
  | Minimal | 3 (one column) | header, no skins |
  | Neon | 10 | header |
  | Stats Wall | 14 | header, the live-matches footer |

  - Creeper map vote: 7 biome blocks on two rows. The closest neighbours are **0.95 blocks** apart (0.70 in 1.3.0), the rows are 1.0 apart, and the long names come as two lines.
- **Not checked headless:** how the designs and the spacing look on a real client screen. Skins in the tab can't be checked on an offline-mode test server.

1.3.0 / 2.2.0, on Paper 1.21.11:
- **Upgrade from 1.2.1 with real data** (edited kits, layouts, PvP/SMP inventories, ranks, economy):
  - all 277 old settings kept (only the two arena depths moved on purpose, 6 options added);
  - an edited mace kit kept, with `mace_new` added beside it;
  - a deleted kit stayed deleted;
  - database rows and every inventory file unchanged;
  - the backup before the update held 11 player inventory files.
  VortexRealm 2.1.1 → 2.2.0 did the same (15 player files) and passes its self test.
- **Bedrock:** every rebuilt arena was checked column by column for what it ends on.

  | Arena | Columns on bedrock before | After |
  |---|---|---|
  | Biome maps (Plains, Snowy) | — | 100% |
  | Deep crystal map | 80% | 100% |
  | Floating designed plaza (Sakura) | 31% | 100% |
  | Colosseum | — | 100% |
  | Grand Colosseum | 65% | 100% |
  | End island | 0% | 100% |

  Still no holes and no ores under the surface.
- **Self test** 148–162 checks pass; the count changes with how many 3D previews are built at that moment. That includes 10 swear variants caught and 11 normal sentences allowed ("nice class", "assassin build", "je vais prendre une douche", "il faut communiquer"…).
- **Bot test** (mineflayer):
  - a clean message goes through; `f.u.c.k` is blocked;
  - the first offence mutes for 2 minutes, the second for 10;
  - a muted player can't chat or `/msg`, and can again after `/unmute`;
  - an NPC with armor, a shield and 1.5× size reaches the client with its equipment and scale;
  - Podium and Framed Board leaderboards arrive complete (12 block displays, 6 text displays, 3 item displays, the podium players as mannequins).
- **Practice Town hub:** built offline from the same code and checked from top-down and isometric renders. All 168 block types and states it uses are valid in 1.21.11. Its 8 NPC spots and 7 hologram/decoration spots are where intended.
- **Not judged:** how the hub, the new hologram designs and the NPC animations look to a player. A headless bot can't see them, and they weren't built in a live world.

1.2.1 / 2.1.1, on Paper 1.21.11:
- **Biome maps:** every block of the re-cut snapshots was counted. Plains, for example:
  - holes under the surface: 270 → 0;
  - ore blocks: 76,826 → 0;
  - gravel, clay, geode and dripstone pockets: about 92,000 → 181 (surface gravel patches);
  - trees unchanged: 1,126 leaf and 91 log blocks.
  A cross-section render shows plain stone with the vanilla granite, diorite and andesite bands.
- **3D previews:** built from all 28 real arenas and rendered offline from the same parts.
  - Designed arenas use 130–240 parts at NORMAL detail, on a 24×24 to 56×56 grid.
  - A build takes about 0.1 s off the main thread (about 1 s for a 256-wide biome map in `ALL` mode).
- **Bot test** (mineflayer, live server):
  - A sword duel's map vote sends each player their own 7 miniatures (1,517 parts, 3,034 on the server), and every part grows to full size.
  - The title, header and 7 labels arrive at full size.
  - Looking at a miniature and clicking bans it.
  - A creeper duel shows 7 biome cubes and no miniatures. Biome names have no "(Crystal)".
  - No display entities are left after either match.
- **Upgrade:** a VortexRealm 2.1.0 server updated to 2.1.1 gets `match.map-preview-detail` added to its config and passes `/pvpadmin selftest` 134/134. That includes the kit pools and nine 3D previews within budget.
- How smooth the motion looks can't be judged by a headless bot; it was checked for errors and leftovers, not by eye.

1.2.0 / 2.1.0, on Paper 1.21.11:
- **Fresh server:**
  - all 28 duel maps and 5 FFA arenas generate;
  - the vanilla maps were checked from top-down renders of their snapshots: real terrain, no lava, deep crystal floors, no walls;
  - `/pvpadmin selftest` passes 122/122, including the logo, pixel spaces, Vault provider, SMP dimensions and a map for every kit.
- **Upgrade:** a VortexRealm 2.0.0 server with old arenas updates its config (v7 → v8), starts the new arena pack and passes the self test.
- **Bot test** (mineflayer, 20 checks). Confirmed working:
  - the tab with 1 and 2 players, and no rows during the map vote;
  - duel, map ban and round report;
  - the loser spectating and the winner moving;
  - the return to the lobby;
  - the nether portal in an SMP world leading to `smp_nether`, and an SMP death respawning in `hub`;
  - `/bal`, the `/pay` confirmation and `/shards`.

  Two checks could not be verified: whether the FIGHT title appears, because the bot library doesn't report titles.
- The animations (smoothness, light sweep, orbit) can't be judged by a headless bot. They were checked for errors and leftover entities, not by eye.

1.1.0 / 2.0.0:

- Booted on Paper 1.21.11 (build 132):
  - The plugin enables with no errors and generates all starter arenas.
  - `/pvpadmin selftest` passes, including the new checks: privacy filters, Tier Defense pairing, friends tables, tab packets, Ping Equalizer and Regions.
- Three headless bot players (mineflayer) checked friends, Invisible blocking duels, DND filters, duel requests, match start through the turn-based map ban, spectator lines and chat, and the 80-entry tab.

Dialog screens (settings, duel, challenge) need a real 1.21.6+ client to click through. Older clients get the classic chest menus.
