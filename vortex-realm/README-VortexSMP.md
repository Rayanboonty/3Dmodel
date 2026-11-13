# VortexSMP 1.1.0 – the survival server of Vortex Realm

`dist/VortexSMP-1.1.0.jar` goes on the **SMP server** only (Paper 1.21.11, Java 21). The Practice server keeps PracticePvP/VortexRealm. Don't put both plugins on the same server: both have `/home`, `/tpa`, `/claim`...

## What it does
- **Homes:** `/sethome`, `/home`, `/delhome`, `/homes`. 3 by default; more with `vortexsmp.homes.<n>` (old `practice.homes.<n>` permissions still count).
- **Teleports:** `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/tptoggle`, `/back`, `/spawn`, `/rtp`, `/warp` and `/warps`.
  - `/rtp end` only lands on the outer End islands (1000+ blocks out, solid ground, never near a portal or gateway, never on the obsidian platform). If the End world border is smaller than 1150 blocks, admins are told.
  - Admins use `/setwarp`, `/delwarp` and `/setspawn`.
  - Teleports have a warmup and a cooldown.
- **Combat tag:** logging out in a fight means death.
- **Admin tools:** `/smpadmin` (players, positions, invsee, ender chest, freeze, logout spots); `/craft`, `/ec`, `/trash`, `/hat`, `/nv`, `/near`, `/playtime`.
- A sidebar and tab with the SMP and lobby player counts.
- **`/hub` (`/lobby`)** sends the player back to the Practice server.
- **No separate inventories:** the server's own player data is the only inventory, so nothing is ever swapped or cleared.

## Setup (two servers)
1. On **both** servers, set `accepts-transfers=true` in `server.properties`. With Velocity, use `network.mode: PROXY` instead.
2. SMP server: in `plugins/VortexSMP/config.yml`, set `network.hub.host` and `network.hub.port` to the Practice server.
3. Practice server: `/pvpadmin` → Server Selector → SURVIVAL SMP → Host & Port = the SMP server. Use Test Connection to check it.

## New in 1.4.0: items missing after moving the SMP to another host
If players arrive with **empty inventories and ender chests** after copying the old world and plugin folder to a new
host, their items are still in the copied files - they just weren't matched. 1.4.0 fixes the usual causes by itself:
- **The old folder can stay where you copied it**: `plugins/VortexRealm` (or PracticePvP / VortexPvP) next to
  VortexSMP is found too, not only `plugins/VortexSMP/import/`.
- **Changed player IDs**: when the old and the new host use a different `online-mode` (cracked vs premium), every
  player gets a new ID. VortexSMP finds their old ID from their name (the old plugin's player list, usercache.json,
  offline/premium IDs) and moves their old player file (inventory, ender chest, XP, position), saved inventory,
  /oldstuff items and homes to the new ID the first time they join.
- **Other old modes**: items saved in other old SMP worlds (eco, lifesteal...) wait in `/oldstuff` instead of being
  left behind.
- Each player gets their items **once** - a second import never duplicates them.

Commands (admins):
- `/vortexsmp import check [player]` - where the old data is, what was found, this server's online-mode, and for a
  player what is saved under their current and old IDs, with the next step to take.
- `/vortexsmp import relink <player>` - for players who already joined empty: their old data is put back (they
  rejoin once); what they gathered since the move goes to `/oldstuff`.
- `/vortexsmp import give <player>` - give an online player what is saved for them right away.

## New in 1.3.0: auto-updates
VortexSMP updates itself like the Practice plugin: it checks `update.json` (GitHub, `updates.sources`) every 30
minutes, downloads a new version, checks its SHA-256 and installs it on a restart - as soon as nobody is online, or
at the restart hour (05:00) with a countdown. A zip of the VortexSMP folder (homes, warps, drills, market, Discord
links) is saved in `plugins/VortexSMP/backups/` first; player inventories live in the world and are never touched.
Turn on auto-restart in your host panel. `/vortexsmp update` (status), `check`, `now`, `on`, `off`.

## New in 1.2.0: Discord invite drills, /trade, /market
- `/link` links your Discord; the bot counts your invites (needs `discord.bot-token`, the Message Content intent and
  Manage Server). 10 invites = 2x2 drill, 20 = 3x3, 30+ = 4x4 (`drills.tiers`), `/drill` to claim.
- `/trade <player>`: a safe trade window. `/market`: post an offer (what you give, what you want); others fill it.

## New in 1.1.0
- Land claims are removed completely.
- `/rtp` in the End is fixed (see above).
- **Moving the old SMP keeps everything** (below).

## Move the old SMP (old world + old plugin folder)
Your members keep their inventory, ender chest and XP exactly as they were. Nothing is cleared.
1. Stop the old server.
2. Copy the old **SMP world** folders (e.g. `smp`, `smp_nether`, `smp_the_end`) to the new server and set `level-name=smp`. Or keep the new world name: the old one is mapped to it automatically.
3. Copy the old server's main world `playerdata/`, `stats/` and `advancements/` folders into the new main world folder.
4. Copy the old `plugins/VortexRealm` folder into `plugins/VortexSMP/import/`.
5. Start the server and run `/vortexsmp migrate`. It checks each step and tells you what is missing.

**What happens when each player joins:**
- If their copied player data says they were in the SMP when they logged off, their inventory is already the newest one and is kept.
- Otherwise (they were in the hub or PvP), the hub items are removed and their saved **SMP inventory, ender chest, XP, health and food come back in the exact same slots**.
- Anything else they carried is kept in `/oldstuff`. Nothing is deleted.
- Homes, warps, tpa toggles and the old SMP spawn come over too.

## Optional: bring the old SMP over
Put the old `plugins/VortexRealm` (or `PracticePvP`) folder, or a .zip/.tar.gz of it, into `plugins/VortexSMP/import/` and restart. You can also run `/vortexsmp import preview`, then `/vortexsmp import run`.
- `import.mode` chooses the old mode to bring over (SURVIVAL by default).
- **What comes over:** homes, warps, the SMP spawn, where each player last was, and old survival inventories and ender chests.
- **Nothing is overwritten:**
  - a backup is made first;
  - existing homes and warps win;
  - the saved SMP inventory goes back slot by slot (see above); copies of unknown origin wait in `/oldstuff`.
- **World names:** when the old SMP world had another name (like `smp`), it is mapped to this server's main world automatically. You can also set `import.world-map` yourself.
