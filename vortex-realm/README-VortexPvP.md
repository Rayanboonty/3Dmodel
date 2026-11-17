# VortexPvP 1.0.2 – the test build with every new system

`dist/VortexPvP-1.0.2.jar` is a **separate test plugin**: the full Vortex Realm engine (everything VortexRealm 2.6.4 does) plus about 50 new systems on top. Use it to try the new features before any of them go into your live plugin.

It never touches your live data:
- it runs as its own plugin, **VortexPvP**, with data in `plugins/VortexPvP`;
- it uses its own `vp_*` database tables, `vortexpvp.*` permissions and `%vortexpvp_…%` placeholders;
- it keeps its backups in `plugins/VortexPvP-Test-Vault`.

> Run it on a **test server**, not next to PracticePvP or VortexRealm. They use the same commands (`/duel`, `/ffa`…), so only one of them can own each command.

## Install

1. Put `VortexPvP-1.0.2.jar` in `plugins/` on a Paper 1.21.11 test server (Java 21). Vault is optional.
2. Start the server. The arenas generate in the background, the same as VortexRealm.
3. Settings for the new systems are in `plugins/VortexPvP/plus/config.yml`.
   - Every system has an `enabled: true/false` switch.
   - Apply changes with `/plus reload`.
4. Check that everything works:
   - `/pvpadmin selftest` (or `/plus selftest`) runs the self-test, which includes one check for every new system;
   - `/plus status` shows what is running.

## What's new

### Ranked and competitive
- **Rank ladder** (`/ladder [player]`):
  - Coal → Copper → Iron → Gold → Lapis → Emerald → Diamond → Netherite, with 3 divisions each, per kit;
  - plus an **overall rank** (the average of every ranked kit; kits you haven't played count as Coal);
  - shown in tab, in chat and on a rank card GUI;
  - country leaderboards: `/ladder country [code]`.
- **Promotion series and demotion shield:**
  - moving up to a new rank takes a best-of-3 series;
  - a fresh promotion is protected for 3 matches.
- **Fair ranked:**
  - wins against the same opponent are damped: half points from the 3rd win in an hour, none from the 4th;
  - ranked matches between alt accounts don't count;
  - a minimum playtime is needed for ranked;
  - the ping equaliser switches on when the players' pings differ by 80 ms or more.
- **Seasons** (`/season`):
  - 30 days each, ended automatically;
  - when a season ends:
    - the Hall of Fame keeps the top 10 of every kit (`/season hof`);
    - everyone gets a title and shards for their best rank of the season;
    - rank points are halved, not wiped.
  - Admin: `/season end confirm`, `/season board` (a floating Hall of Fame).
- **Clans** (`/clan`):
  - create, invite, roles, colour, info, top;
  - clan chat (`/clanchat`);
  - the clan tag shows in tab and chat;
  - clan Elo rises with members' ranked wins;
  - **clan wars**: first to 3 wins within 30 minutes moves clan Elo.
- **Ranked 2v2** (`/duo invite`, `/duo queue <kit>`, `/duo top`): a team rating per pair, matchmaking that widens while you wait, and a top list.
- **Party vs party** (`/partywar <leader> [kit]`).
- **Weekly Cup:**
  - an automatic tournament every Saturday at 18:00 (you can change this), with a rotating kit;
  - the champion gets 500 shards and the *Weekly Champion* title;
  - see past winners with `/weeklycup`.
- **Streaks and big moments:**
  - a ✷ flair in tab at 5+ ranked wins in a row;
  - "X ended Y's 8-win streak!" announcements;
  - a server-wide announcement the first time someone reaches Diamond or higher, or enters a top 10.
- **Head-to-head and rivals:**
  - "You 3 – 5 Steve" when a match starts;
  - beating your most-played opponent gives bonus XP.

### Getting better
- **Practice bots and training dummies** are switched on (`/bot`, `/train`).
- **Post-match analysis** after every match:
  - covers hits, accuracy, combo, crits, damage, potions, gapples, pearls, W-taps, blocks and fight length;
  - each number is compared with your last 20 matches;
  - hover the chat card for the details; `/lastmatch` shows your history.
- **Replays:**
  - ranked matches are recorded tick by tick (set `replays.record: all` to record every match) and kept for 7 days;
  - `/replay <id>` plays one back **for you only**, with Mannequins wearing the players' skins in the real arena;
  - hotbar controls: play/pause, -5s/+5s, 0.5×/1×/2× speed, a follow camera and exit;
  - replays linked to a report or an anti-cheat flag are kept.
- **Training drills** (`/drill`), each with a top 10 (`/drill top <name>`):
  - CPS test;
  - aim trainer;
  - W-tap trainer;
  - pearl landing;
  - crystal speed;
  - mace timing.
- **Coach mode** (`/coach <friend>`):
  - spectate with a live CPS/combo/hits/HP overlay;
  - `/coach note <text>` saves a timestamped note into the replay.
- **Match HUD** (`/hud`): your CPS, combo, the opponent's heals left and the fight timer on the action bar.

### Things to do
- **Arcade** (`/arcade`), on its own small arenas:
  - Sumo, Boxing (first to 100 hits), Spleef, One in the Chamber, King of the Hill, a Parkour race and Bridge;
  - on weekends one mode is **featured** with double XP.
- **FFA bounties:**
  - a 5+ kill streak puts a growing shard bounty on you and makes you glow;
  - whoever ends the streak collects it (`/bounty`).
- **FFA kill-streak rewards:**
  - 3 kills: golden apple;
  - 5 kills: Speed;
  - 7 kills: Strength;
  - 10 kills: Regeneration and Absorption.
- **Missions** (`/missions`): 3 daily, 3 weekly and 4 season missions, paying XP and shards.
- **Battle pass** (`/pass`):
  - 50 levels a season, with a free and a premium track (permission `vortexpvp.pass.premium`);
  - rewards are shards, titles, win effects, pets, death messages and gadgets.
- **Achievements** (`/achievements`): almost 300, generated for every kit plus special ones; rare ones are announced.
- **Titles** (`/titles`): pick the one shown next to your name in chat.
- **Daily reward calendar** (`/daily`) and a **login streak**.
- **Kit of the Week** (`/kitvote`):
  - players submit their own kit (`/kitvote submit <name>`) and vote;
  - the winner becomes a featured unranked queue the next week.
- **Match of the Week** (`/motw`): the best replay of the week (big combos, long close fights).

### Looks
- **Win effects** (fireworks, lightning, totem, dragon, hearts, souls, flames, snow) and **death messages** (dramatic, pirate, knight, robot, chef). Choose yours in `/vfx`.
- **Hub pets** (`/pet`): they follow you around the hub, with no AI, and nobody can hurt them.
- **Hub gadgets** (`/gadget`): leap, grapple, fireworks, and a paint gun. The paint is only shown to players; no blocks change.
- **Hub parkour:**
  - `/parkour build` (admin) builds a 24-jump course where you stand;
  - it has checkpoints, a timer and a top 10.
- **Punching dummy** while you wait in a queue, with a combo counter.
- **Seasonal hub decorations**, switched on by the date: Halloween, winter and Valentine's.
- **Cinematic spectator camera** (`/cinematic <player>`), for streams.

### Community
- **Website:**
  - `/p/<name>` profile pages: ladder rank of every kit, recent matches, replays, achievements, clan;
  - `/plus/weekly`;
  - JSON under `/api/plus/…`;
  - an **admin dashboard** (`/plus web` gives the private link) with live TPS, players, queues, matches, reports and shards, and one-click arena rebuilds.
- **Discord** (no extra plugin):
  - set `discord.webhook` for ranked results, rank-ups, streaks, seasons, tournaments and clan wars;
  - add `discord.bot-token`, `discord.guild` and `discord.link-channel` for **account linking** (`/link`);
  - add `discord.roles` entries such as `Diamond:123456789` and players get the Discord role of their overall rank.
- **Creator program:**
  - `/creator add <player> <code>` (admin);
  - players support a creator with `/code <code>`, and the creator earns 5% of the shards their supporters earn.
- **LIVE badge** (`/live <twitch/youtube/kick link>`) and **streamer mode** (`/streamer`): IPs and coordinates are hidden in the chat you see.
- **Mentors** (`/mentor <player>`): Diamond-overall players can mentor new ones, and both earn XP when the mentee wins.
- **Friend spectate** (`/fspec <friend>`).

### Fairness (staff)
- **Fair+ checks** on top of the built-in anti-cheat:
  - reach, with ping compensation;
  - autoclicker (click timing too regular, or too fast);
  - no knockback taken;
  - crystal aura.
- Each Fair+ alert has a confidence score and a link to the replay (`/alerts` turns them on or off).
- **Report evidence:** every `/report` gets the reported player's replay attached. `/evidence [player]` opens a GUI where staff click to watch it.
- **Screenshare log:** every `/ss` is logged. View it with `/sslog [player]`; add notes with `/sslog note <player> <text>`.

### Eco SMP extras
- **Spawner stacking:** right-click a spawner with the same spawner, up to 10.
- **Crates and keys** (`/crate`): Vote, Rare and Legendary, with a spinning opening animation; staff give keys with `/crate give`.
- **Chest shops:** put a `[Shop]` sign on a chest, with the amount on line 2 and the price on line 3.
- **Bid house** (`/bid`, `/bid sell <price> [minutes]`):
  - timed auctions;
  - outbid players are refunded;
  - a last-minute bid adds a minute.
- **Daily deal** (`/deal`): one discounted item a day in the shard shop.
- **World events:**
  - airdrops, meteors full of ores, and King of the Hill;
  - they start automatically every 45 minutes when players are online, or on demand with `/ecoevent`.
- **Season wipe** (`/ecowipe`):
  - a backup is saved first;
  - the 3 richest players get a title and a Legendary key;
  - then balances and shards reset.

### Admin
- `/plus status` shows everything at a glance.
- `/plus test match [kit]` runs a fake match result through XP, missions, achievements, head-to-head, analysis and the webhook.
- `/plus test season-end`, `/plus test webhook <text>` and `/plus test replay` test those systems.
- `/plus rollback list | last | <file>` restores the full backup from before the last update.
- `/plus xp <player> <n>` gives XP; `/plus unlock <player> <group:value>` unlocks a cosmetic.
- **Shared data across servers:** with MySQL configured, the new systems keep player progress and shared data (seasons, clans…) in the same database. Several servers can share them.

## Permissions

| Permission | Default | Gives |
|---|---|---|
| `vortexpvp.pass.premium` | nobody | the premium battle-pass track |
| `vortexpvp.cosmetics.all` | op | every cosmetic unlocked |
| `vortexpvp.alerts` | op | Fair+ alerts |
| `vortexpvp.live` | op | the `/live` badge |
| `vortexpvp.coach` | op | coach anyone, not only friends |
| `vortexpvp.staff` / `vortexpvp.admin` | op | staff tools / every admin command |

## For developers

- The add-on lives in `vortexpvp-plus/` and is never overwritten.
- `vortexpvp/` is generated from `practicepvp/` (renamed to `net.vortexpvp`) and the add-on is laid over it, so it always has every fix of the main plugin.
- The core fires three Bukkit events that the add-on uses: `MatchStartEvent`, `MatchEndEvent` and `FfaKillEvent`.
- PracticePvP and VortexRealm fire them too; nothing listens to them there, so nothing changes.
