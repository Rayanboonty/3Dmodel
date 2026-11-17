package net.vortexpvp.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.vortexpvp.rank.Tier;
import org.bukkit.Difficulty;
import org.bukkit.Material;

public final class Opt<T> {
    private static final List<Opt<?>> ALL = new ArrayList<>();
    public final String path;
    public final Opt.Type type;
    public final T def;
    public final Opt.Section section;
    public final String name;
    public final String desc;
    public final Material icon;
    public final double min;
    public final double max;
    public final double step;
    public final Class<? extends Enum<?>> enumClass;
    public final boolean gui;
    public static final Opt<String> SERVER_NAME = str(
        "general.server-name", "Vortex Realm", Opt.Section.GENERAL, "Server Name", "Scoreboard title text (gold, bold).", Material.NAME_TAG
    );
    public static final Opt<String> SERVER_IP = str(
        "general.server-ip", "play.yourserver.net", Opt.Section.GENERAL, "Server IP", "Shown at the bottom of the scoreboard.", Material.PAPER
    );
    public static final Opt<String> CHAT_PREFIX = str(
        "general.chat-prefix",
        "<gold><b>PvP</b></gold> <dark_gray>»</dark_gray> ",
        Opt.Section.GENERAL,
        "Chat Prefix",
        "MiniMessage prefix in front of plugin messages.",
        Material.WRITABLE_BOOK
    );
    public static final Opt<Boolean> LOBBY_ON_JOIN = bool(
        "general.teleport-to-lobby-on-join",
        true,
        Opt.Section.GENERAL,
        "Lobby On Join",
        "Send players to the PvP lobby when they join in a PvP world.",
        Material.ENDER_PEARL
    );
    public static final Opt<Boolean> MAINTENANCE = bool(
        "general.maintenance", false, Opt.Section.GENERAL, "Maintenance Mode", "Blocks queues/duels/FFA for everyone without vortexpvp.admin.", Material.BARRIER
    );
    public static final Opt<Boolean> SPRITE_ICONS = bool(
        "general.sprite-icons",
        true,
        Opt.Section.GENERAL,
        "Item Sprite Icons",
        "Show real item icons in nametags, chat and titles (1.21.9+ clients).",
        Material.ITEM_FRAME
    );
    public static final Opt<Boolean> SMP_UTILS = bool(
        "extras.smp-commands",
        true,
        Opt.Section.EXTRAS,
        "SMP Utility Commands",
        "/craft /ec /trash /hat /nv /near in SMP worlds (never in PvP).",
        Material.CRAFTING_TABLE
    );
    public static final Opt<String> SMP_MAP_URL = str(
        "extras.web-map-url",
        "",
        Opt.Section.EXTRAS,
        "Web Map URL",
        "Link of your BlueMap / squaremap page, shown in /smpadmin (e.g. http://play.example.com:8100).",
        Material.FILLED_MAP
    );
    public static final Opt<String> LINK_DISCORD = str(
        "extras.links.discord", "https://discord.gg/knHtEeHyk", Opt.Section.EXTRAS, "Discord Link", "Shown in /links.", Material.BELL
    );
    public static final Opt<String> LINK_STORE = str("extras.links.store", "", Opt.Section.EXTRAS, "Store Link", "Shown in /links.", Material.EMERALD);
    public static final Opt<String> LINK_WEBSITE = str("extras.links.website", "", Opt.Section.EXTRAS, "Website Link", "Shown in /links.", Material.PAPER);
    public static final Opt<Boolean> WELCOME_SCREEN = bool(
        "extras.welcome.enabled",
        true,
        Opt.Section.EXTRAS,
        "Welcome Screen",
        "First join: the welcome screen (about the server, Discord, country, guide).",
        Material.CAKE
    );
    public static final Opt<Boolean> VERIFY_ENABLED = bool(
        "extras.welcome.verification",
        true,
        Opt.Section.EXTRAS,
        "Human Verification",
        "New players answer a simple maths question in the welcome screen (stops join bots) and get the green verified badge next to their name.",
        Material.LIME_DYE
    );
    public static final Opt<Boolean> VERIFY_BEFORE_CHAT = bool(
        "extras.welcome.verify-before-chat",
        true,
        Opt.Section.EXTRAS,
        "Verify Before Chatting",
        "Players who haven't verified can't chat or send private messages until they do (/verify).",
        Material.BOOK
    );
    public static final Opt<Boolean> VERIFIED_BADGE = bool(
        "extras.welcome.verified-badge",
        true,
        Opt.Section.EXTRAS,
        "Verified Badge",
        "The green check next to verified players' names (nametag, tab, chat).",
        Material.EMERALD
    );
    public static final Opt<List<String>> WELCOME_COUNTRIES = new Opt<>(
        "extras.welcome.quick-countries",
        Opt.Type.STRING_LIST,
        List.of("MA", "DZ", "TN", "EG", "FR", "ES", "US", "GB", "DE"),
        Opt.Section.EXTRAS,
        "Welcome Quick Countries",
        "Country codes offered as one-click buttons in the welcome screen.",
        Material.WHITE_BANNER,
        0.0,
        0.0,
        1.0,
        null,
        true
    );
    public static final Opt<List<String>> WELCOME_BRIEF = new Opt<>(
        "extras.welcome.brief",
        Opt.Type.STRING_LIST,
        List.of(
            "<gray>Vortex Realm is a <white>PvP practice + SMP network</white>: duels in <gold>20+ kits</gold>, a <gold>tier ladder</gold> from <#9E9E9E>LT5</#9E9E9E> to <#FFAA00><b>HT1</b></#FFAA00>, FFA, parties and tournaments.",
            "<gray>Off the battlefield: <green>Survival</green>, <red>Lifesteal</red>, <light_purple>Strength</light_purple>, <yellow>Ranked SMP</yellow> and <aqua>Eco</aqua> worlds - all one click away in the hub.",
            "<gray>Play fair: cheaters end up in the <dark_red>hacker pool</dark_red>. <white>/report</white> anyone suspicious."
        ),
        Opt.Section.EXTRAS,
        "Welcome Text",
        "Lines of the welcome screen (MiniMessage).",
        Material.WRITABLE_BOOK,
        0.0,
        0.0,
        1.0,
        null,
        true
    );
    public static final Opt<Boolean> SMP_SIDEBAR = bool(
        "extras.smp-sidebar",
        true,
        Opt.Section.EXTRAS,
        "SMP & Hub Sidebars",
        "A sidebar in the hub and every SMP mode (balance in Eco, hearts in Lifesteal...). Lines: scoreboards.yml.",
        Material.OAK_SIGN
    );
    public static final Opt<Boolean> TAB_ENABLED = bool(
        "extras.tab.enabled",
        true,
        Opt.Section.EXTRAS,
        "Custom Tab List",
        "Rank icons and gradients, countries, a header and footer per mode (tab.yml).",
        Material.PAINTING
    );
    public static final Opt<String> TAB_DEFAULT_RANK = str(
        "extras.tab.default-rank-label",
        "MEMBER",
        Opt.Section.EXTRAS,
        "Tab Label Without Rank",
        "What players without a server rank show in the tab list (empty = nothing).",
        Material.PAPER
    );
    public static final Opt<Boolean> TAB_SHOW_TIER = bool(
        "extras.tab.show-tier",
        true,
        Opt.Section.EXTRAS,
        "Tab Shows Tier",
        "Each player's best PvP tier (HT3, LT1...) after their name in the tab list, next to their server rank.",
        Material.NAME_TAG
    );
    public static final Opt<Boolean> TAB_PER_WORLD = bool(
        "extras.tab.per-world",
        true,
        Opt.Section.EXTRAS,
        "Tab Per Mode",
        "The tab list only shows players of your own mode (Practice, Hub, each SMP) - like separate servers. Staff always see everyone.",
        Material.ITEM_FRAME
    );
    public static final Opt<Boolean> COUNTRY_ON_NAMETAG = bool(
        "extras.country-on-nametag",
        true,
        Opt.Section.EXTRAS,
        "Country On Nametag",
        "Show the player's country code (flag colours) after their name.",
        Material.WHITE_BANNER
    );
    public static final Opt<Boolean> RANK_ON_NAMETAG = bool(
        "extras.rank-on-nametag", true, Opt.Section.EXTRAS, "Rank On Nametag", "Show the server rank (icon + gradient) before names in PvP.", Material.NAME_TAG
    );
    public static final Opt<Boolean> JOIN_AT_HUB = bool(
        "extras.join-at-hub",
        true,
        Opt.Section.EXTRAS,
        "Join at Hub",
        "Players join the server at the hub (when a world has the Hub mode); the selector brings them back to their last spot in each mode.",
        Material.NETHER_STAR
    );
    public static final Opt<Boolean> ANNOUNCER = bool(
        "extras.announcer.enabled", true, Opt.Section.EXTRAS, "Auto Announcer", "Broadcast the tips from announcements.yml.", Material.GOAT_HORN
    );
    public static final Opt<Integer> ANNOUNCER_INTERVAL = integer(
        "extras.announcer.interval-seconds", 300, Opt.Section.EXTRAS, "Announcer Interval", "Seconds between tips.", Material.CLOCK, 30, 3600, 30
    );
    public static final Opt<Integer> QUEUE_AFK_MINUTES = integer(
        "extras.queue-afk-minutes",
        3,
        Opt.Section.EXTRAS,
        "Queue AFK Kick",
        "Remove players from queues after this many minutes without moving (0 = off).",
        Material.COBWEB,
        0,
        30,
        1
    );
    public static final Opt<Boolean> SIDEBAR = bool(
        "general.sidebar", false, Opt.Section.GENERAL, "Sidebar Scoreboard", "Show the VortexPvP scoreboard on the right of the screen.", Material.OAK_SIGN
    );
    public static final Opt<Boolean> NAMETAG_RANKS = bool(
        "general.nametag-ranks",
        true,
        Opt.Section.GENERAL,
        "Nametag Ranks",
        "Show kit icon + tier (e.g. HT3) before player names and in the tab list.",
        Material.NAME_TAG
    );
    public static final Opt<Boolean> PLAYER_COLLISION = bool(
        "general.player-collision", false, Opt.Section.GENERAL, "Player Collision", "Whether players push each other.", Material.SLIME_BALL
    );
    public static final Opt<Boolean> AUTO_GENERATE = bool(
        "general.auto-generate-arenas",
        true,
        Opt.Section.GENERAL,
        "First-Run Arenas",
        "Generate a starter set of biome arenas + FFA arenas when none exist.",
        Material.GRASS_BLOCK
    );
    public static final Opt<Boolean> LOBBY_DOUBLE_JUMP = bool(
        "lobby.double-jump", true, Opt.Section.LOBBY, "Double Jump", "Players can double jump in the lobby (per-player toggle in settings).", Material.FEATHER
    );
    public static final Opt<Double> LOBBY_JUMP_POWER = dbl(
        "lobby.double-jump-power",
        1.15,
        Opt.Section.LOBBY,
        "Double Jump Power",
        "Launch strength of the lobby double jump.",
        Material.RABBIT_FOOT,
        0.2,
        4.0,
        0.05
    );
    public static final Opt<Integer> LOBBY_VOID_DEPTH = integer(
        "lobby.void-depth",
        40,
        Opt.Section.LOBBY,
        "Void Depth",
        "Blocks below the lobby spawn before players are teleported back.",
        Material.BLACK_CONCRETE,
        5,
        400,
        5
    );
    public static final Opt<Boolean> LOBBY_BUILD_BLOCK = bool(
        "lobby.protect",
        true,
        Opt.Section.LOBBY,
        "Protect Lobby",
        "Block breaking/placing/interaction in the lobby (admins use /pvpadmin build).",
        Material.BEDROCK
    );
    public static final Opt<Boolean> LOBBY_SPEED = bool(
        "lobby.speed-effect", false, Opt.Section.LOBBY, "Lobby Speed", "Give everyone Speed I in the lobby.", Material.SUGAR
    );
    public static final Opt<Boolean> RANKED_ENABLED = bool(
        "queue.ranked-enabled", true, Opt.Section.QUEUE, "Ranked Queues", "Allow players to join ranked queues.", Material.NETHERITE_SWORD
    );
    public static final Opt<Boolean> UNRANKED_ENABLED = bool(
        "queue.unranked-enabled", true, Opt.Section.QUEUE, "Unranked Queues", "Allow players to join unranked queues.", Material.DIAMOND_SWORD
    );
    public static final Opt<Integer> RANKED_MIN_WINS = integer(
        "queue.ranked-min-unranked-wins",
        0,
        Opt.Section.QUEUE,
        "Ranked Unlock",
        "Unranked wins (any kit) needed before ranked unlocks.",
        Material.IRON_DOOR,
        0,
        100,
        1
    );
    public static final Opt<Integer> RANKED_INVITE_EVERY = integer(
        "queue.ranked-invite-every",
        3,
        Opt.Section.QUEUE,
        "Tier Test Invite",
        "Unranked duels in a kit before the 'ranked tier test' pop-up (repeats every N).",
        Material.BELL,
        1,
        50,
        1
    );
    public static final Opt<Integer> ELO_RANGE_START = integer(
        "queue.elo-range-start",
        250,
        Opt.Section.QUEUE,
        "Rank Range Start",
        "Ranked: the first +- points search window (unranked matches anyone waiting).",
        Material.SPYGLASS,
        0,
        5000,
        25
    );
    public static final Opt<Integer> ELO_RANGE_EXPAND = integer(
        "queue.elo-range-expand-per-second",
        60,
        Opt.Section.QUEUE,
        "Rank Range Growth",
        "Ranked: how much the points window grows every second in queue (three times as fast when only two players are queueing).",
        Material.PISTON,
        0,
        1000,
        5
    );
    public static final Opt<Integer> ELO_RANGE_MAX = integer(
        "queue.elo-range-max", 2500, Opt.Section.QUEUE, "Rank Range Max", "Largest Rank window the matchmaker will ever use.", Material.OBSERVER, 0, 20000, 100
    );
    public static final Opt<Integer> PING_RANGE_EXPAND = integer(
        "queue.ping-range-expand-per-second",
        10,
        Opt.Section.QUEUE,
        "Ping Range Growth",
        "How fast a player's ping limit relaxes per second waiting.",
        Material.REPEATER,
        0,
        500,
        5
    );
    public static final Opt<Integer> AVOID_REMATCH = integer(
        "queue.avoid-last-opponent-seconds",
        8,
        Opt.Section.QUEUE,
        "Avoid Rematch",
        "Seconds the matchmaker avoids pairing you with your last opponent.",
        Material.STRUCTURE_VOID,
        0,
        120,
        1
    );
    public static final Opt<Boolean> QUEUE_REOPEN = bool(
        "queue.reopen-menu-after-click",
        true,
        Opt.Section.QUEUE,
        "Keep Menu Open",
        "Re-open the queue menu after clicking a kit (lets players pick several).",
        Material.CHEST
    );
    public static final Opt<String> MENU_STYLE = enumOpt(
        "queue.menu-style",
        Opt.MenuStyle.AUTO,
        Opt.Section.QUEUE,
        "Queue Menu Style",
        "AUTO = MCPVP dialog menu on 1.21.6+ clients, chest menu for older clients. DIALOG or CHEST force one style.",
        Material.ITEM_FRAME,
        Opt.MenuStyle.class
    );
    public static final Opt<Integer> ROUNDS_TO_WIN = integer(
        "match.default-rounds-to-win",
        2,
        Opt.Section.MATCH,
        "Rounds To Win",
        "Default 'first to' rounds when a kit does not override it.",
        Material.TARGET,
        1,
        10,
        1
    );
    public static final Opt<String> START_MODE = enumOpt(
        "match.start-mode",
        Opt.StartMode.ELEVATOR,
        Opt.Section.MATCH,
        "Start Animation",
        "ELEVATOR rises players up, GATES opens fences/cages, FREEZE just counts down.",
        Material.SCAFFOLDING,
        Opt.StartMode.class
    );
    public static final Opt<Boolean> ELEVATOR_EVERY_ROUND = bool(
        "match.elevator-every-round",
        false,
        Opt.Section.MATCH,
        "Elevator Every Round",
        "Use the elevator for every round instead of only the first one.",
        Material.LADDER
    );
    public static final Opt<Integer> ELEVATOR_DEPTH = integer(
        "match.elevator-depth", 6, Opt.Section.MATCH, "Elevator Depth", "How many blocks below the spawn the elevator starts.", Material.IRON_CHAIN, 2, 20, 1
    );
    public static final Opt<Integer> ELEVATOR_SECONDS = integer(
        "match.elevator-seconds", 5, Opt.Section.MATCH, "Elevator Countdown", "Seconds the elevator ride lasts (counts down 5..1).", Material.CLOCK, 2, 10, 1
    );
    public static final Opt<List<String>> CAGE_KITS = new Opt<>(
        "match.cage-kits",
        Opt.Type.STRING_LIST,
        List.of("sword", "nodebuff", "pot", "nethpot", "combo", "gapple"),
        Opt.Section.MATCH,
        "Cage Start Kits",
        "1v1 duels in these kits start in a stone cage (MCPVP style): arrange your kit and save the layout at the chest, hit the gate to ready up, the gate opens when both are ready or the time runs out.",
        Material.DARK_OAK_FENCE,
        0.0,
        0.0,
        1.0,
        null,
        true
    );
    public static final Opt<Integer> CAGE_SECONDS = integer(
        "match.cage-seconds",
        15,
        Opt.Section.MATCH,
        "Cage Time",
        "Longest wait in the cage before the gate opens (both ready = 3 seconds). Later rounds use the normal countdown.",
        Material.CLOCK,
        5,
        60,
        1
    );
    public static final Opt<Integer> COUNTDOWN = integer(
        "match.countdown-seconds", 3, Opt.Section.MATCH, "Countdown", "Freeze countdown before each round (3..1 FIGHT).", Material.REPEATER, 1, 10, 1
    );
    public static final Opt<Integer> ROUND_END_DELAY = integer(
        "match.round-end-delay-ticks",
        50,
        Opt.Section.MATCH,
        "Round Break",
        "Ticks between a round ending and the next one starting.",
        Material.HOPPER,
        10,
        200,
        5
    );
    public static final Opt<String> ROUND_THROW_MODE = new Opt<>(
        "match.round-throw-mode",
        Opt.Type.ENUM,
        "SMART",
        Opt.Section.MATCH,
        "Throw Back Between Rounds",
        "After a round players fly through the air back to their spawn (MCPVP style). SMART = only kits that don't build or break blocks (no crystal / anchor / build maps, where the ground is gone), ALL = every kit, OFF = plain teleport. Kits can override it in the kit editor.",
        Material.WIND_CHARGE,
        0.0,
        0.0,
        1.0,
        Opt.ThrowMode.class,
        true
    );
    public static final Opt<String> ARENA_DIFFICULTY = new Opt<>(
        "match.arena-difficulty",
        Opt.Type.ENUM,
        "HARD",
        Opt.Section.MATCH,
        "Arena Difficulty",
        "Difficulty of the arenas world. Explosions (crystals, anchors) hit 1.5x harder on HARD - what crystal PvP servers use. NORMAL = vanilla singleplayer damage.",
        Material.END_CRYSTAL,
        0.0,
        0.0,
        1.0,
        Difficulty.class,
        true
    );
    public static final Opt<Double> EXPLOSION_DAMAGE = dbl(
        "match.explosion-damage-multiplier",
        1.0,
        Opt.Section.MATCH,
        "Explosion Damage",
        "Extra multiplier for crystal / anchor / TNT damage to players in duels and FFA (on top of the difficulty).",
        Material.RESPAWN_ANCHOR,
        0.25,
        3.0,
        0.05
    );
    public static final Opt<Integer> END_DELAY = integer(
        "match.end-delay-ticks",
        100,
        Opt.Section.MATCH,
        "Victory Screen Time",
        "Ticks the victory screen shows before returning to the lobby.",
        Material.FIREWORK_STAR,
        0,
        300,
        5
    );
    public static final Opt<Integer> MAX_DURATION = integer(
        "match.max-duration-minutes", 15, Opt.Section.MATCH, "Max Match Length", "Minutes before a match ends as a draw.", Material.CLOCK, 1, 120, 1
    );
    public static final Opt<Integer> MATCH_FOUND_DELAY = integer(
        "match.found-cooldown", 3, Opt.Section.MATCH, "Match Found Cooldown", "Seconds between 'match found' and the teleport.", Material.CLOCK, 1, 10, 1
    );
    public static final Opt<Boolean> MAP_BAN = bool(
        "match.map-ban-enabled", true, Opt.Section.MATCH, "Map Ban Phase", "Players ban maps from floating blocks before the match.", Material.GRASS_BLOCK
    );
    public static final Opt<Integer> MAP_BAN_SECONDS = integer(
        "match.map-ban-time", 15, Opt.Section.MATCH, "Map Ban Time", "Seconds for the map ban phase.", Material.CLOCK, 5, 45, 1
    );
    public static final Opt<String> MAP_PREVIEW_3D = new Opt<>(
        "match.map-ban-3d-preview",
        Opt.Type.ENUM,
        "FLAT_KITS",
        Opt.Section.MATCH,
        "3D Map Preview",
        "Map ban shows a detailed 3D miniature of each arena instead of a block. FLAT_KITS = designed arenas (sword, UHC, pot kits) get the miniature, biome maps show their biome block; ALL, OFF.",
        Material.ARMOR_STAND,
        0.0,
        0.0,
        1.0,
        Opt.MapPreview.class,
        true
    );
    public static final Opt<String> MAP_PREVIEW_DETAIL = new Opt<>(
        "match.map-preview-detail",
        Opt.Type.ENUM,
        "NORMAL",
        Opt.Section.MATCH,
        "3D Preview Detail",
        "How detailed the 3D map miniatures are. LOW = 140 parts per map, NORMAL = 240, HIGH = 400 (more client entities).",
        Material.SPYGLASS,
        0.0,
        0.0,
        1.0,
        Opt.PreviewDetail.class,
        true
    );
    public static final Opt<Integer> BANS_PER_PLAYER = integer(
        "match.max-bans-per-player", 2, Opt.Section.MATCH, "Bans Per Player", "How many maps each player may ban.", Material.BARRIER, 0, 5, 1
    );
    public static final Opt<Boolean> VOTES = bool(
        "match.preference-votes",
        true,
        Opt.Section.MATCH,
        "Preference Votes",
        "Kit rule votes (e.g. 'Limit to 8 Totems'); applies only if everyone votes yes.",
        Material.EMERALD_BLOCK
    );
    public static final Opt<Integer> VOTE_SECONDS = integer(
        "match.vote-time", 8, Opt.Section.MATCH, "Vote Time", "Seconds per preference vote.", Material.CLOCK, 3, 30, 1
    );
    public static final Opt<Boolean> ALLOW_SPECTATORS = bool(
        "match.allow-spectators", true, Opt.Section.MATCH, "Spectators", "Allow /spectate on matches (players can still opt out).", Material.ENDER_EYE
    );
    public static final Opt<Boolean> REMATCH = bool(
        "match.rematch-button", true, Opt.Section.MATCH, "Rematch Button", "Show a clickable [Rematch] after duels.", Material.REPEATER
    );
    public static final Opt<Integer> PEARL_COOLDOWN = integer(
        "match.pearl-cooldown-ticks",
        10,
        Opt.Section.MATCH,
        "Pearl Cooldown",
        "Ender pearl cooldown in duels/FFA, in ticks (vanilla 20 = 1s). Lower = faster pearl catches. Kits can override it (pearl-cooldown in kits.yml).",
        Material.ENDER_PEARL,
        0,
        40,
        1
    );
    public static final Opt<Integer> WIND_COOLDOWN = integer(
        "match.wind-charge-cooldown-ticks",
        10,
        Opt.Section.MATCH,
        "Wind Charge Cooldown",
        "Wind charge cooldown in duels/FFA, in ticks (vanilla 10). Kits can override it.",
        Material.WIND_CHARGE,
        0,
        40,
        1
    );
    public static final Opt<Integer> HIT_DELAY = integer(
        "match.hit-delay-ticks",
        20,
        Opt.Section.MATCH,
        "Hit Delay",
        "Invulnerability ticks after a hit (vanilla = 20). Lower = faster kills.",
        Material.IRON_CHESTPLATE,
        1,
        40,
        1
    );
    public static final Opt<Boolean> REMOVE_BOTTLES = bool(
        "match.remove-empty-bottles", true, Opt.Section.MATCH, "Remove Bottles", "Delete empty glass bottles after drinking potions.", Material.GLASS_BOTTLE
    );
    public static final Opt<Boolean> ALLOW_DROPS = bool(
        "match.allow-item-drop", false, Opt.Section.MATCH, "Item Dropping", "Allow players to drop items during matches.", Material.DROPPER
    );
    public static final Opt<List<Integer>> TIER_THRESHOLDS = new Opt<>(
        "ranking.tier-thresholds",
        Opt.Type.INT_LIST,
        List.of(0, 150, 300, 700, 950, 1200, 1800, 2150, 2500, 3300, 3750, 4200, 5200, 5850, 6500),
        Opt.Section.RANKING,
        "Tier Thresholds",
        "Minimum rank points for LT5,MT5,HT5,LT4,MT4,HT4,LT3,MT3,HT3,LT2,MT2,HT2,LT1,MT1,HT1.",
        Material.LADDER,
        0.0,
        100000.0,
        50.0,
        null,
        true
    );
    public static final Opt<List<String>> LB_MAIN_KITS = new Opt<>(
        "ranking.leaderboard-main-kits",
        Opt.Type.STRING_LIST,
        List.of("sword", "crystal", "mace", "nethpot", "smp", "uhc", "axe", "pot"),
        Opt.Section.RANKING,
        "Leaderboard Main Kits",
        "Kits with their own button on the leaderboard and profiles (in this order). Every other ranked kit is under 'More Gamemodes'.",
        Material.ITEM_FRAME,
        0.0,
        0.0,
        1.0,
        null,
        true
    );
    public static final Opt<Integer> PLACEMENT_MATCHES = integer(
        "ranking.placement-matches",
        10,
        Opt.Section.RANKING,
        "Placement Matches",
        "Ranked matches a new player plays (per kit) before getting a tier. Until then they are Unranked: no tier tag, no rank icon. 0 = ranked straight away.",
        Material.COMPASS,
        0,
        30,
        1
    );
    public static final Opt<Boolean> TIER_TEST_ABOVE = bool(
        "ranking.tier-test-vs-higher-tier",
        true,
        Opt.Section.RANKING,
        "Tier Test vs Higher Tier",
        "A Tier Test Challenge is played against a player who already HAS the tier you want (one rank above you) - beat them to unlock it.",
        Material.GOLDEN_SWORD
    );
    public static final Opt<Integer> TIER_TEST_FALLBACK = integer(
        "ranking.tier-test-fallback-seconds",
        45,
        Opt.Section.RANKING,
        "Tier Test Fallback",
        "Seconds a Tier Test waits for a higher-tier opponent before it also accepts players of your own tier (0 = wait for a higher tier forever).",
        Material.CLOCK,
        0,
        1800,
        15
    );
    public static final Opt<Boolean> TIER_TEST_POPUP_ONCE = bool(
        "ranking.tier-test-popup-once",
        true,
        Opt.Section.RANKING,
        "Tier Test Pop-up Once",
        "The 'Accept Challenge' pop-up shows one time per tier; after that the challenge waits in the Duels menu (right-click the kit) and /rankup.",
        Material.PAPER
    );
    public static final Opt<Integer> SKILL_CHECK_GAMES = integer(
        "ranking.skill-check-duels",
        3,
        Opt.Section.RANKING,
        "Skill Check Duels",
        "A player's first unranked duels in a kit (while they have no tier there) are scored - win, rounds, damage, hits, combos, opponent strength - and then place them at the tier they play at. 0 = off (placement only through ranked matches).",
        Material.SPYGLASS,
        0,
        10,
        1
    );
    public static final Opt<String> SKILL_CHECK_MAX = enumOpt(
        "ranking.skill-check-max-tier",
        Tier.HT3,
        Opt.Section.RANKING,
        "Skill Check Highest Tier",
        "The highest tier the skill check can give; above it players climb (and take Tier Tests) normally.",
        Material.GOLDEN_HELMET,
        Tier.class
    );
    public static final Opt<Integer> SAME_TIER_SECONDS = integer(
        "ranking.same-tier-seconds",
        15,
        Opt.Section.RANKING,
        "Same Tier Search",
        "Ranked: for this many seconds players are only matched with their own tier; then the search widens one tier at a time (from LT2 up never more than one tier). 0 = off.",
        Material.TARGET,
        0,
        600,
        5
    );
    public static final Opt<Integer> MAX_TIER_GAP = integer(
        "ranking.max-tier-gap",
        2,
        Opt.Section.RANKING,
        "Max Tier Gap",
        "Ranked: players can only meet others at most this many tiers away (LT5 never meets HT1). Starts at 1 and widens every 20s of waiting up to this.",
        Material.LADDER,
        1,
        9,
        1
    );
    public static final Opt<Integer> ELO_SCALE = integer(
        "ranking.scale",
        1000,
        Opt.Section.RANKING,
        "Rank Scale",
        "Rank difference that makes a 10x favourite. Higher = upsets matter less.",
        Material.COMPARATOR,
        100,
        5000,
        50
    );
    public static final Opt<Integer> K_PROVISIONAL = integer(
        "ranking.k-provisional",
        400,
        Opt.Section.RANKING,
        "K (new players)",
        "Rank swing for a player's first matches (fast placement).",
        Material.EXPERIENCE_BOTTLE,
        1,
        2000,
        10
    );
    public static final Opt<Integer> PROVISIONAL_GAMES = integer(
        "ranking.provisional-games", 5, Opt.Section.RANKING, "Placement Games", "Ranked games that use the new-player K value.", Material.BOOK, 0, 50, 1
    );
    public static final Opt<Integer> K_NORMAL = integer(
        "ranking.k-normal", 240, Opt.Section.RANKING, "K (normal)", "Rank swing for established players.", Material.GOLD_INGOT, 1, 2000, 10
    );
    public static final Opt<Integer> K_HIGH = integer(
        "ranking.k-high", 160, Opt.Section.RANKING, "K (high Rank)", "Rank swing above the high-rank threshold.", Material.DIAMOND, 1, 2000, 10
    );
    public static final Opt<Integer> K_HIGH_FROM = integer(
        "ranking.k-high-from-elo",
        3300,
        Opt.Section.RANKING,
        "High Rank From",
        "Rank where the high-rank K value starts.",
        Material.NETHERITE_INGOT,
        0,
        100000,
        100
    );
    public static final Opt<Double> LOSS_FACTOR = dbl(
        "ranking.loss-factor",
        0.7,
        Opt.Section.RANKING,
        "Loss Factor",
        "Losses are multiplied by this (below 1 = climbing is easier than falling).",
        Material.REDSTONE,
        0.1,
        2.0,
        0.05
    );
    public static final Opt<Integer> MIN_GAIN = integer(
        "ranking.min-gain", 25, Opt.Section.RANKING, "Min Gain", "Smallest Rank gain for a win.", Material.LIME_DYE, 0, 1000, 5
    );
    public static final Opt<Integer> MAX_GAIN = integer(
        "ranking.max-gain", 600, Opt.Section.RANKING, "Max Gain", "Largest Rank gain for a win.", Material.EMERALD, 1, 5000, 25
    );
    public static final Opt<Integer> MIN_LOSS = integer(
        "ranking.min-loss", 10, Opt.Section.RANKING, "Min Loss", "Smallest Rank loss for a loss.", Material.PINK_DYE, 0, 1000, 5
    );
    public static final Opt<Integer> MAX_LOSS = integer(
        "ranking.max-loss", 400, Opt.Section.RANKING, "Max Loss", "Largest Rank loss for a loss.", Material.REDSTONE_BLOCK, 1, 5000, 25
    );
    public static final Opt<Double> STREAK_BONUS = dbl(
        "ranking.streak-bonus-per-win",
        0.05,
        Opt.Section.RANKING,
        "Streak Bonus",
        "Extra gain per consecutive win (hidden potential).",
        Material.BLAZE_POWDER,
        0.0,
        1.0,
        0.01
    );
    public static final Opt<Double> STREAK_BONUS_MAX = dbl(
        "ranking.streak-bonus-max", 0.25, Opt.Section.RANKING, "Streak Bonus Cap", "Maximum streak bonus multiplier.", Material.BLAZE_ROD, 0.0, 3.0, 0.05
    );
    public static final Opt<Double> MARGIN_BONUS = dbl(
        "ranking.margin-bonus",
        0.15,
        Opt.Section.RANKING,
        "Clean Win Bonus",
        "Extra gain/loss for a flawless score like 2-0.",
        Material.GOLDEN_SWORD,
        0.0,
        1.0,
        0.05
    );
    public static final Opt<Boolean> PROMOTION = bool(
        "ranking.promotion-series",
        true,
        Opt.Section.RANKING,
        "Tier Test Challenges",
        "Ranking up needs a Tier Test Challenge: when your points are high enough, queueing pops up 'Accept Challenge' - one match, first to 5, against a player of your tier. Off = tiers follow your points automatically.",
        Material.TOTEM_OF_UNDYING
    );
    public static final Opt<String> PROMOTION_FROM = new Opt<>(
        "ranking.tier-test-from-tier",
        Opt.Type.ENUM,
        "HT5",
        Opt.Section.RANKING,
        "Tier Tests From",
        "First tier that needs a Tier Test Challenge (lower tiers rank up by points alone).",
        Material.GOLD_BLOCK,
        0.0,
        0.0,
        1.0,
        Tier.class,
        true
    );
    public static final Opt<Integer> TIER_TEST_FIRST_TO = integer(
        "ranking.tier-test-first-to", 5, Opt.Section.RANKING, "Tier Test First To", "Rounds needed to win a Tier Test Challenge.", Material.TARGET, 1, 10, 1
    );
    public static final Opt<Integer> DEMOTION_BUFFER = integer(
        "ranking.demotion-buffer",
        120,
        Opt.Section.RANKING,
        "Demotion Buffer",
        "Rank below a tier's threshold before you drop a tier.",
        Material.IRON_CHAIN,
        0,
        2000,
        10
    );
    public static final Opt<Boolean> TIER_DEFENSE = bool(
        "ranking.tier-defense.enabled",
        true,
        Opt.Section.RANKING,
        "Tier Defense",
        "Instead of dropping a tier straight away, players must win (or lose) a Tier Defense Challenge.",
        Material.SHIELD
    );
    public static final Opt<String> TIER_DEFENSE_FROM = enumOpt(
        "ranking.tier-defense.from", Tier.LT4, Opt.Section.RANKING, "Tier Defense From", "Tiers from this one up must defend before dropping.", Material.IRON_CHESTPLATE, Tier.class
    );
    public static final Opt<Integer> TIER_DEFENSE_FIRST_TO = integer(
        "ranking.tier-defense.first-to", 5, Opt.Section.RANKING, "Tier Defense First To", "Rounds needed to win a Tier Defense Challenge.", Material.TARGET, 1, 10, 1
    );
    public static final Opt<Integer> TIER_DEFENSE_GRACE = integer(
        "ranking.tier-defense.grace-matches",
        5,
        Opt.Section.RANKING,
        "Tier Defense Grace",
        "Ranked matches you may play normally while a defense is due; after that you drop a tier (0 = no limit: only losing the defense drops you).",
        Material.CLOCK,
        0,
        50,
        1
    );
    public static final Opt<Boolean> TIER_CHALLENGE = bool(
        "ranking.tier-challenge.enabled", true, Opt.Section.RANKING, "Tier Challenges", "/challenge - ranked first-to matches with a big Tier Score impact.", Material.GOLDEN_SWORD
    );
    public static final Opt<Integer> TIER_CHALLENGE_FIRST_TO = integer(
        "ranking.tier-challenge.first-to", 3, Opt.Section.RANKING, "Tier Challenge First To", "Rounds needed to win a Tier Challenge.", Material.TARGET, 1, 10, 1
    );
    public static final Opt<Double> TIER_CHALLENGE_MULTIPLIER = dbl(
        "ranking.tier-challenge.multiplier",
        2.0,
        Opt.Section.RANKING,
        "Tier Challenge Impact",
        "Rank points of a Tier Challenge are multiplied by this.",
        Material.BLAZE_POWDER,
        1.0,
        5.0,
        0.25
    );
    public static final Opt<Integer> TIER_CHALLENGE_COOLDOWN = integer(
        "ranking.tier-challenge.cooldown-minutes",
        30,
        Opt.Section.RANKING,
        "Tier Challenge Cooldown",
        "Minutes before the same two players can play another Tier Challenge.",
        Material.CLOCK,
        0,
        1440,
        5
    );
    public static final Opt<Integer> DUEL_MAX_FIRST_TO = integer(
        "duels.max-first-to", 5, Opt.Section.MATCH, "Duel Max First To", "Highest 'First To' a duel can be set to.", Material.REPEATER, 1, 15, 1
    );
    public static final Opt<Boolean> PING_EQUALIZER = bool(
        "duels.ping-equalizer.enabled",
        true,
        Opt.Section.MATCH,
        "Ping Equalizer",
        "Offer the experimental Ping Equalizer in duels and Tier Challenges.",
        Material.REPEATER
    );
    public static final Opt<Integer> PING_EQUALIZER_MAX = integer(
        "duels.ping-equalizer.max-delay-ms", 120, Opt.Section.MATCH, "Ping Equalizer Max", "Most delay (ms) added to the lower-ping player.", Material.CLOCK, 0, 300, 10
    );
    public static final Opt<Boolean> SPECTATOR_FEED = bool(
        "spectators.join-messages", true, Opt.Section.MATCH, "Spectator Join Messages", "'X started spectating' lines in matches.", Material.OBSERVER
    );
    public static final Opt<Boolean> SPECTATOR_CHAT = bool(
        "spectators.chat", true, Opt.Section.MATCH, "Spectator Chat", "Spectators talk to the match they watch (<👁 name>).", Material.ENDER_EYE
    );
    public static final Opt<Boolean> SPECTATOR_HIDE_STAFF = bool(
        "spectators.hide-staff", true, Opt.Section.MATCH, "Silent Staff Spectators", "Staff watch matches without join messages.", Material.IRON_HELMET
    );
    public static final Opt<Boolean> MAP_BAN_TURNS = bool(
        "match.map-ban-turns",
        true,
        Opt.Section.MATCH,
        "Turn-Based Map Bans",
        "Players ban maps one after another (YOUR TURN / OPPONENT'S TURN) instead of at the same time.",
        Material.FILLED_MAP
    );
    public static final Opt<Integer> MAP_BAN_TURN_SECONDS = integer(
        "match.map-ban-turn-seconds", 15, Opt.Section.MATCH, "Map Ban Turn Time", "Seconds for each ban turn (the very first ban gets 5 more).", Material.CLOCK, 3, 30, 1
    );
    public static final Opt<Boolean> SOCIAL_ENABLED = bool(
        "social.enabled", true, Opt.Section.CHAT, "Friends & Status", "Friends, follows, Online / Invisible / Do Not Disturb and request privacy.", Material.PLAYER_HEAD
    );
    public static final Opt<Integer> SOCIAL_MAX_FRIENDS = integer(
        "social.max-friends", 200, Opt.Section.CHAT, "Friend Limit", "Most friends a player can have.", Material.TOTEM_OF_UNDYING, 5, 2000, 5
    );
    public static final Opt<String> SOCIAL_FAMOUS_RANKS = str(
        "social.famous-ranks",
        "media,partner,youtube,creator,famous",
        Opt.Section.CHAT,
        "Famous Ranks",
        "Server ranks counted as Famous (Creators tab column and request filters), comma separated.",
        Material.GOLDEN_HELMET
    );
    public static final Opt<Boolean> TAB_COLUMNS = bool(
        "tab.columns.enabled",
        true,
        Opt.Section.VISUALS,
        "MCPVP Tab Columns",
        "4-column tab list: Staff, Friends, Creators, Top Players and kit leaderboards.",
        Material.PAINTING
    );
    public static final Opt<String> TAB_COLUMNS_WHERE = str(
        "tab.columns.where", "pvp", Opt.Section.VISUALS, "Tab Columns Where", "pvp = only in PvP worlds, everywhere = all worlds.", Material.COMPASS
    );
    public static final Opt<String> TAB_COLUMNS_SECTIONS = str(
        "tab.columns.sections",
        "staff,friends,creators,online,top,endgame,sword,spear,mace",
        Opt.Section.VISUALS,
        "Tab Columns Sections",
        "Sections in order: staff, friends, creators, online, top, or a kit id.",
        Material.BOOK
    );
    public static final Opt<Integer> TAB_COLUMNS_SECTION_SIZE = integer(
        "tab.columns.section-size", 8, Opt.Section.VISUALS, "Tab Section Size", "Players listed per leaderboard section.", Material.LADDER, 3, 18, 1
    );
    public static final Opt<Integer> TAB_ANIMATION_TICKS = integer(
        "tab.animation-ticks", 4, Opt.Section.VISUALS, "Tab Animation Speed", "Ticks between frames of the animated tab header (2 = smoothest, higher = lighter on the server).", Material.CLOCK, 2, 40, 1
    );
    public static final Opt<Integer> TAB_LOGO_OFFSET = integer(
        "tab.logo-offset", 0, Opt.Section.VISUALS, "Tab Logo Offset", "Moves the server logo in the tab header left (negative) or right (positive), in pixels.", Material.COMPASS, -20, 20, 1
    );
    public static final Opt<String> TAB_DESIGN = new Opt<>(
        "tab.design",
        Opt.Type.STRING,
        "CLASSIC",
        Opt.Section.VISUALS,
        "Tab Design",
        "Look of the tab list: CLASSIC, COMPETITIVE, NETWORK, MINIMAL, NEON, STATS_WALL, ROYAL, OCEAN, RETRO_ARCADE, MONO_PRO or AUTUMN. Change it in /pvpadmin -> Tab Design.",
        Material.PAINTING,
        0.0,
        256.0,
        1.0,
        null,
        false
    );
    public static final Opt<String> BROADCAST_FROM = new Opt<>(
        "ranking.broadcast-rankups-from-tier",
        Opt.Type.ENUM,
        "HT5",
        Opt.Section.RANKING,
        "Broadcast Rank-ups",
        "Rank-ups to this tier or higher are announced to everyone.",
        Material.BELL,
        0.0,
        0.0,
        1.0,
        Tier.class,
        true
    );
    public static final Opt<Boolean> FFA_ENABLED = bool(
        "ffa.enabled", true, Opt.Section.FFA, "FFA Enabled", "Allow players to join FFA arenas.", Material.NETHERITE_AXE
    );
    public static final Opt<Integer> FFA_SPAWN_PROT = integer(
        "ffa.spawn-protection-seconds", 3, Opt.Section.FFA, "Spawn Protection", "Invulnerable seconds after joining an FFA arena.", Material.SHIELD, 0, 30, 1
    );
    public static final Opt<Boolean> FFA_SPAWN_ANIMATION = bool(
        "ffa.spawn-animation",
        true,
        Opt.Section.FFA,
        "Spawn Animation",
        "Glide down into the arena in a column of light and land with a shockwave (open sky only), with the kit name on screen.",
        Material.FEATHER
    );
    public static final Opt<Integer> FFA_COMBAT_TAG = integer(
        "ffa.combat-tag-seconds",
        10,
        Opt.Section.FFA,
        "Combat Tag",
        "Seconds after combat before you can leave. Logging out while tagged counts as a death.",
        Material.IRON_SWORD,
        0,
        120,
        1
    );
    public static final Opt<Boolean> FFA_KILLER_HEAL = bool(
        "ffa.killer-heal", true, Opt.Section.FFA, "Heal On Kill", "Fully heal the killer after a kill.", Material.GOLDEN_APPLE
    );
    public static final Opt<Boolean> FFA_KILLER_REFILL = bool(
        "ffa.killer-refill", false, Opt.Section.FFA, "Refill On Kill", "Give the killer a fresh kit after a kill.", Material.CHEST
    );
    public static final Opt<Boolean> FFA_RESPAWN_IN_ARENA = bool(
        "ffa.respawn-in-arena", false, Opt.Section.FFA, "Respawn In Arena", "Respawn in the arena instead of returning to the lobby.", Material.RED_BED
    );
    public static final Opt<Integer> FFA_REGEN_DELAY = integer(
        "ffa.block-regen-seconds",
        30,
        Opt.Section.FFA,
        "Block Regen",
        "Seconds before broken/placed blocks in FFA regenerate.",
        Material.MOSSY_COBBLESTONE,
        3,
        600,
        1
    );
    public static final Opt<Integer> FFA_FULL_REGEN = integer(
        "ffa.full-regen-minutes", 10, Opt.Section.FFA, "Full Regen", "Minutes between full FFA arena regenerations (0 = off).", Material.GRASS_BLOCK, 0, 240, 1
    );
    public static final Opt<Integer> MACE_ARENA_SIZE = integer(
        "arena.mace-arena-size",
        400,
        Opt.Section.ARENA,
        "Mace Arena Size",
        "Size of the grand colosseum (mace / spear mace duels and FFA). Bigger = more room for wind charges and pearl catches. Existing ones grow by themselves (same layout).",
        Material.MACE,
        120,
        448,
        10
    );
    public static final Opt<Integer> BLOCKS_PER_TICK = integer(
        "arena.blocks-per-tick",
        20000,
        Opt.Section.ARENA,
        "Blocks Per Tick",
        "Budget for arena copying/resetting. Lower if you see lag spikes.",
        Material.HOPPER,
        500,
        200000,
        500
    );
    public static final Opt<String> ARENA_WALLS = enumOpt(
        "arena.walls",
        Opt.WallStyle.NONE,
        Opt.Section.ARENA,
        "Arena Walls",
        "Decorative wall around generated arenas (pillars and lanterns). NONE = no wall. Applies to new / rebuilt arenas.",
        Material.STONE_BRICK_WALL,
        Opt.WallStyle.class
    );
    public static final Opt<Boolean> ARENA_PLANTS = bool(
        "arena.plants",
        false,
        Opt.Section.ARENA,
        "Arena Grass & Flowers",
        "Grass, flowers and bushes on generated arenas. Off = clean ground. Applies to new / rebuilt arenas.",
        Material.SHORT_GRASS
    );
    public static final Opt<String> RESET_MODE = enumOpt(
        "arena.default-reset-mode",
        Opt.ResetMode.BOTH,
        Opt.Section.ARENA,
        "Reset Mode",
        "TRACKED = undo recorded changes, SNAPSHOT = full block compare, BOTH = safest.",
        Material.STRUCTURE_BLOCK,
        Opt.ResetMode.class
    );
    public static final Opt<Integer> COPY_SPACING = integer(
        "arena.copy-spacing", 64, Opt.Section.ARENA, "Copy Spacing", "Empty blocks between arena copies.", Material.SCAFFOLDING, 16, 512, 16
    );
    public static final Opt<Integer> BUILD_HEIGHT = integer(
        "arena.default-build-height",
        25,
        Opt.Section.ARENA,
        "Build Height",
        "Blocks above the spawn you may build in build kits.",
        Material.SCAFFOLDING,
        1,
        200,
        1
    );
    public static final Opt<Integer> DUEL_ARENA_SIZE = integer(
        "arena.duel-size", 192, Opt.Section.ARENA, "Duel Arena Size", "Side length of generated duel arenas (designed arenas and colosseums).", Material.MAP, 32, 400, 4
    );
    public static final Opt<Integer> FFA_ARENA_SIZE = integer(
        "arena.ffa-size", 256, Opt.Section.ARENA, "FFA Arena Size", "Side length of generated FFA arenas.", Material.FILLED_MAP, 40, 448, 4
    );
    public static final Opt<Integer> DEFAULT_COPIES = integer(
        "arena.generated-copies",
        3,
        Opt.Section.ARENA,
        "Copies Per Map",
        "Instances generated per duel map (more = more simultaneous duels).",
        Material.CHEST_MINECART,
        1,
        20,
        1
    );
    public static final Opt<Integer> DUEL_DEPTH = integer(
        "arena.duel-depth",
        48,
        Opt.Section.ARENA,
        "Duel Arena Depth",
        "Blocks of ground under duel arenas (uneven bedrock at the bottom). Deeper = more room to dig and blast.",
        Material.DEEPSLATE,
        8,
        110,
        2
    );
    public static final Opt<Integer> FFA_DEPTH = integer(
        "arena.ffa-depth",
        56,
        Opt.Section.ARENA,
        "FFA Arena Depth",
        "Blocks of ground under FFA arenas (uneven bedrock at the bottom).",
        Material.COBBLED_DEEPSLATE,
        8,
        110,
        2
    );
    public static final Opt<Integer> CRYSTAL_DEPTH = integer(
        "arena.crystal-depth",
        100,
        Opt.Section.ARENA,
        "Crystal Arena Depth",
        "Blocks of ground under crystal arenas: players can blast this deep before they reach the uneven bedrock floor (never the void). Applies to crystal duel and FFA arenas.",
        Material.END_CRYSTAL,
        8,
        180,
        2
    );
    public static final Opt<Boolean> ARENA_ORES = bool(
        "arena.ores",
        true,
        Opt.Section.ARENA,
        "Underground Ores",
        "Natural ore veins (coal, iron, gold, redstone, lapis, diamonds deep down) in the arena underground.",
        Material.DIAMOND_ORE
    );
    public static final Opt<Boolean> ARENA_AUTO_UPDATE = bool(
        "arena.auto-update",
        true,
        Opt.Section.ARENA,
        "Auto-Update Arenas",
        "After a restart, generated arenas built with older settings (depth, walls, grass, ores) are rebuilt with the same layout. Arenas you edited by hand are never touched. Run it now: /pvpadmin rebuild outdated",
        Material.ANVIL
    );
    public static final Opt<Boolean> VANILLA_TERRAIN = bool(
        "arena.vanilla.enabled",
        true,
        Opt.Section.ARENA,
        "Vanilla Terrain Maps",
        "Natural maps (Plains, Snowy Plains, Desert, Badlands, The End...) are real vanilla terrain cut from a generated world and optimised for PvP (caves filled, no lava, fair flat spawns) - like MCPVP.",
        Material.GRASS_BLOCK
    );
    public static final Opt<Integer> VANILLA_SIZE = integer(
        "arena.vanilla.size", 256, Opt.Section.ARENA, "Vanilla Map Size", "Side length of vanilla terrain maps.", Material.FILLED_MAP, 96, 448, 16
    );
    public static final Opt<String> VANILLA_SEED = str(
        "arena.vanilla.seed",
        "VortexRealm",
        Opt.Section.ARENA,
        "Vanilla Map Seed",
        "World seed the vanilla maps are cut from. Same seed = the same maps on every server. Change it and rebuild to get new maps.",
        Material.WHEAT_SEEDS
    );
    public static final Opt<Boolean> VANILLA_STRUCTURES = bool(
        "arena.vanilla.structures", false, Opt.Section.ARENA, "Vanilla Structures", "Allow villages, ruins and other structures on vanilla maps.", Material.BELL
    );
    public static final Opt<Boolean> VANILLA_FILL_CAVES = bool(
        "arena.vanilla.fill-caves",
        true,
        Opt.Section.ARENA,
        "Fill Caves",
        "Fill caves, underground water and lava pockets below the surface so digging and blasting stays fair.",
        Material.STONE
    );
    public static final Opt<Boolean> VANILLA_KEEP_SOURCE = bool(
        "arena.vanilla.keep-source-world",
        false,
        Opt.Section.ARENA,
        "Keep Terrain World",
        "Keep the temporary vortex_terrain world after cutting maps (for admins who want to look around). Off = deleted automatically.",
        Material.CHEST
    );
    public static final Opt<Integer> VANILLA_COPIES = integer(
        "arena.vanilla.copies",
        1,
        Opt.Section.ARENA,
        "Vanilla Map Copies",
        "Extra copies made of each vanilla map up front (more are added automatically when the map is busy).",
        Material.CHEST_MINECART,
        0,
        20,
        1
    );
    public static final Opt<Boolean> AUTO_COPIES = bool(
        "arena.auto-copies",
        true,
        Opt.Section.ARENA,
        "Auto Copies",
        "When every copy of a map is busy for a while, another copy is built automatically (up to Max Copies).",
        Material.HOPPER_MINECART
    );
    public static final Opt<Integer> MAX_COPIES = integer(
        "arena.max-copies", 4, Opt.Section.ARENA, "Max Copies", "Most copies a map may get from Auto Copies.", Material.CHEST, 1, 30, 1
    );
    public static final Opt<Boolean> COLOSSEUM_BARRIERS = bool(
        "arena.colosseum-barriers",
        false,
        Opt.Section.ARENA,
        "Colosseum Barriers",
        "Invisible barrier walls around the colosseum floors. Off = the world border is the only limit.",
        Material.BARRIER
    );
    public static final Opt<String> TOURNAMENT_START_MODE = enumOpt(
        "events.tournament-start-mode",
        Opt.StartMode.ELEVATOR,
        Opt.Section.MATCH,
        "Tournament Start",
        "How tournament duels start: ELEVATOR rises players up, CAGE uses the stone cage with a fence gate, FREEZE just counts down, GATES uses arena gates.",
        Material.SCAFFOLDING,
        Opt.StartMode.class
    );
    public static final Opt<Boolean> TOURNAMENT_GATES = bool(
        "events.tournament-gates",
        false,
        Opt.Section.MATCH,
        "Tournament Gates & Cages",
        "Off = no fences, gates or cages at all in tournament duels (the start mode above is used instead).",
        Material.OAK_FENCE_GATE
    );
    public static final Opt<Boolean> PARTY_ENABLED = bool("party.enabled", true, Opt.Section.PARTY, "Parties", "Enable the party system.", Material.CAKE);
    public static final Opt<Integer> PARTY_MAX = integer(
        "party.max-size", 10, Opt.Section.PARTY, "Max Party Size", "Maximum members in a party.", Material.PLAYER_HEAD, 2, 50, 1
    );
    public static final Opt<Integer> PARTY_INVITE_EXPIRE = integer(
        "party.invite-expire-seconds", 60, Opt.Section.PARTY, "Invite Expiry", "Seconds before a party invite expires.", Material.CLOCK, 10, 600, 5
    );
    public static final Opt<Boolean> PACK_ENABLED = bool(
        "pack.enabled",
        true,
        Opt.Section.WEB,
        "Resource Pack",
        "Offer the server pack on join: country flags, the verified seal and the full-screen rank-up animation. Players without it still see everything, in plain text.",
        Material.PAINTING
    );
    public static final Opt<Boolean> PACK_REQUIRED = bool(
        "pack.required",
        false,
        Opt.Section.WEB,
        "Pack Required",
        "Players must accept the pack to play (the game disconnects them if they decline).",
        Material.BARRIER
    );
    public static final Opt<String> PACK_URL = str(
        "pack.external-url",
        "",
        Opt.Section.WEB,
        "External Pack URL",
        "Leave empty to serve the pack from the built-in web server. Or upload plugins/VortexPvP/web/vortex-pack.zip somewhere and paste its direct download link here.",
        Material.LEAD
    );
    public static final Opt<Boolean> WEB_ENABLED = bool(
        "web.enabled",
        true,
        Opt.Section.WEB,
        "Website",
        "Run the built-in website: leaderboards, tier list, player profiles, the admin panel (/webadmin) and the pack download.",
        Material.FILLED_MAP
    );
    public static final Opt<String> WEB_TAGLINE = str(
        "web.tagline",
        "The competitive PvP network. Duels, ranked tiers, tournaments and SMP worlds - all on one server.",
        Opt.Section.WEB,
        "Website Tagline",
        "The line under the server name on the website's home page.",
        Material.OAK_SIGN
    );
    public static final Opt<String> WEB_ACCENT = str(
        "web.accent-color",
        "#FFB020",
        Opt.Section.WEB,
        "Website Accent",
        "The website's main colour (hex), e.g. #FFB020 gold, #7C5CFF violet, #2E9BFF blue.",
        Material.ORANGE_DYE
    );
    public static final Opt<List<String>> WEB_PAGES = new Opt<>(
        "web.pages",
        Opt.Type.STRING_LIST,
        List.of("home", "modes", "leaderboards", "tiers", "ranks", "staff", "rules", "player"),
        Opt.Section.WEB,
        "Website Pages",
        "Pages shown in the website menu (remove a line to hide a page): home, modes, leaderboards, tiers, ranks, staff, rules, player.",
        Material.BOOKSHELF,
        0.0,
        0.0,
        1.0,
        null,
        true
    );
    public static final Opt<List<String>> WEB_NEWS = new Opt<>(
        "web.news",
        Opt.Type.STRING_LIST,
        List.of(
            "2026-10-17|The website is live|Leaderboards, the tier list, ranks and player profiles are all here.",
            "2026-10-18|New rank-up animation|Rank up and watch your new tier crash down in flames, MCPVP style."
        ),
        Opt.Section.WEB,
        "Website News",
        "News on the home page, newest first: date|title|text.",
        Material.PAPER,
        0.0,
        0.0,
        1.0,
        null,
        true
    );
    public static final Opt<List<String>> WEB_RULES = new Opt<>(
        "web.rules",
        Opt.Type.STRING_LIST,
        List.of(
            "No cheating|No hacked clients, macros, auto-clickers or x-ray. Cheaters go to the hacker pool.",
            "Be respectful|No harassment, slurs or spam. Keep chat friendly.",
            "No teaming in FFA|Fight fair; teaming and cross-teaming are punished.",
            "No exploiting|Report bugs to staff instead of abusing them.",
            "Fair names and skins|Nothing offensive in names, skins or capes.",
            "Listen to staff|If a staff member asks you to stop, stop. Appeal on Discord."
        ),
        Opt.Section.WEB,
        "Website Rules",
        "The rules page: title|text per rule.",
        Material.WRITABLE_BOOK,
        0.0,
        0.0,
        1.0,
        null,
        true
    );
    public static final Opt<List<String>> WEB_LINKS = new Opt<>(
        "web.links",
        Opt.Type.STRING_LIST,
        List.of(),
        Opt.Section.WEB,
        "Website Extra Links",
        "Extra buttons in the website footer: name|url (vote sites, YouTube, TikTok...).",
        Material.LEAD,
        0.0,
        0.0,
        1.0,
        null,
        true
    );
    public static final Opt<String> STORE_LINK = str(
        "web.store-link",
        "",
        Opt.Section.WEB,
        "Store Link",
        "Where the 'Get it' buttons on the website's ranks page go (your Tebex store...). Empty = the Discord.",
        Material.EMERALD
    );
    public static final Opt<Integer> WEB_PORT = integer(
        "web.port",
        8123,
        Opt.Section.WEB,
        "Website Port",
        "Port of the website and pack download (TCP). On a game host, use the extra port they give you.",
        Material.TRIPWIRE_HOOK,
        1,
        65535,
        1
    );
    public static final Opt<Boolean> AI_ENABLED = bool(
        "ai.enabled",
        true,
        Opt.Section.WEB,
        "AI Assistant",
        "Admins can ask the AI to change settings, kits and ranks: /ai <what you want> or the web panel. Works with any provider's API key (/ai key <key> in the console) or a local model (Ollama, LM Studio).",
        Material.AMETHYST_CLUSTER
    );
    public static final Opt<String> AI_MODEL = str(
        "ai.model",
        "",
        Opt.Section.WEB,
        "AI Model",
        "The model to use; empty = the provider's default (Claude: claude-opus-5-5, OpenAI: gpt-4.1-mini, Gemini: gemini-2.5-flash, Ollama: qwen2.5:3b...).",
        Material.KNOWLEDGE_BOOK
    );
    public static final Opt<String> AI_PROVIDER = str(
        "ai.provider",
        "auto",
        Opt.Section.WEB,
        "AI Provider",
        "auto = every provider that has a key, in turn if one fails, then a local model. Or one of: anthropic, openai, gemini, openrouter, groq, mistral, deepseek, xai, together, cerebras, ollama, lmstudio, custom.",
        Material.COMPARATOR
    );
    public static final Opt<String> AI_BASE_URL = str(
        "ai.base-url",
        "",
        Opt.Section.WEB,
        "AI Local Address",
        "For a local or self-hosted model: its address, e.g. http://localhost:11434/v1 (Ollama) or http://192.168.1.5:1234/v1 (LM Studio). Also used as a proxy address for the provider picked in AI Provider. Empty = the usual one.",
        Material.REPEATER
    );
    public static final Opt<Boolean> AI_LOCAL_FALLBACK = bool(
        "ai.local-fallback",
        true,
        Opt.Section.WEB,
        "AI Local Fallback",
        "When every provider with a key fails (or there is no key), try a local model (Ollama, LM Studio) on this machine.",
        Material.HOPPER
    );
    public static final Opt<Boolean> WEB_SAME_PORT = bool(
        "web.same-port",
        true,
        Opt.Section.WEB,
        "Use Minecraft Port",
        "Also serve the website and the pack on the Minecraft port (works on hosts with only one port). Players get links on the address they joined with. Behind a proxy (Velocity/Bungee), set Website Address to a full URL instead.",
        Material.ENDER_EYE
    );
    public static final Opt<String> WEB_HOST = str(
        "web.public-address",
        "",
        Opt.Section.WEB,
        "Public Address",
        "The address people reach the website at, e.g. play.vortexrealm.net or 1.2.3.4. Empty = the address each player typed to join.",
        Material.NAME_TAG
    );
    public static final Opt<Boolean> POOL_CHAT = bool(
        "anticheat.hacker-pool-chat-isolated",
        true,
        Opt.Section.ANTICHEAT,
        "Hacker Pool Chat",
        "Players in the hacker pool only chat with each other (staff still read it).",
        Material.REDSTONE_TORCH
    );
    public static final Opt<String> FOUND_EFFECT = new Opt<>(
        "visuals.duel-found-effect",
        Opt.Type.ENUM,
        "BOTH",
        Opt.Section.VISUALS,
        "Duel Found Effect",
        "BOTH = the first version's pop: the totem animation with the kit's item, then the kit's weapon pops up in front of you (default). TOTEM = only the totem animation, SWORD = only the weapon pop, ICON = the kit's icon at a smaller size, NONE = off.",
        Material.TOTEM_OF_UNDYING,
        0.0,
        0.0,
        1.0,
        Opt.FoundEffect.class,
        true
    );
    public static final Opt<String> RANKUP_STYLE = new Opt<>(
        "visuals.rank-up-style",
        Opt.Type.ENUM,
        "DROP",
        Opt.Section.VISUALS,
        "Rank-Up Animation",
        "DROP = MCPVP: the old tier sinks into the ground line by line, the new one falls from the sky and bounces down in flames. SCREEN = on your screen (server pack). SMOOTH = glowing letters in front of you, BLOCKS = giant block letters.",
        Material.NETHER_STAR,
        0.0,
        0.0,
        1.0,
        Opt.RankUpStyle.class,
        true
    );
    public static final Opt<Boolean> STATS_3D = bool(
        "visuals.stats-3d",
        true,
        Opt.Section.VISUALS,
        "3D Player Stats",
        "/stats and shift + right-click on a player in the lobby show the stats in 3D in front of you (panels, the player on a pedestal, kits to click). Off = the profile window.",
        Material.ARMOR_STAND
    );
    public static final Opt<Boolean> TOASTS = bool(
        "visuals.advancement-toasts",
        true,
        Opt.Section.VISUALS,
        "Advancement Toasts",
        "'Challenge Complete' style pop-ups for wins, ranked wins, placements and rank-ups.",
        Material.KNOWLEDGE_BOOK
    );
    public static final Opt<List<String>> AD_TOASTS = new Opt<>(
        "visuals.ad-toasts",
        Opt.Type.STRING_LIST,
        List.of(
            "Join our Discord!|discord.gg/knHtEeHyk|minecraft:amethyst_shard",
            "Tournaments every week|Watch #events on Discord|minecraft:golden_helmet",
            "Bring your friends!|Climb the tiers together|minecraft:cake"
        ),
        Opt.Section.VISUALS,
        "Ad Toasts",
        "Pop-up ads (title|text|icon) shown after wins and every few minutes. Changes apply at the next restart (with nobody online).",
        Material.OAK_HANGING_SIGN,
        0.0,
        0.0,
        1.0,
        null,
        true
    );
    public static final Opt<Integer> AD_TOAST_WINS = integer(
        "visuals.ad-toast-every-wins",
        3,
        Opt.Section.VISUALS,
        "Ad Toast Every N Wins",
        "Show an ad toast after every N wins (0 = never after wins).",
        Material.GOLD_NUGGET,
        0,
        20,
        1
    );
    public static final Opt<Integer> AD_TOAST_MINUTES = integer(
        "visuals.ad-toast-minutes",
        12,
        Opt.Section.VISUALS,
        "Ad Toast Every N Minutes",
        "Show an ad toast to lobby/hub players every N minutes (0 = never).",
        Material.CLOCK,
        0,
        120,
        1
    );
    public static final Opt<Boolean> TOTEM_ON_WIN = bool(
        "visuals.totem-pop-on-win",
        true,
        Opt.Section.VISUALS,
        "Totem Pop On Win",
        "The winner gets the totem-pop animation with a golden trophy.",
        Material.GOLD_INGOT
    );
    public static final Opt<Boolean> TITLES = bool(
        "visuals.titles", true, Opt.Section.VISUALS, "Big Titles", "Match found / countdown / round / victory titles.", Material.OAK_SIGN
    );
    public static final Opt<Boolean> VICTORY_FIREWORKS = bool(
        "visuals.victory-fireworks", true, Opt.Section.VISUALS, "Victory Fireworks", "Launch fireworks around the winner.", Material.FIREWORK_ROCKET
    );
    public static final Opt<String> TROPHY = str(
        "visuals.trophy-glyph", "\ud83c\udfc6", Opt.Section.VISUALS, "Trophy Glyph", "Symbol on both sides of VICTORY.", Material.GOLD_BLOCK
    );
    public static final Opt<Boolean> CENTER_CHAT = bool(
        "visuals.center-chat", true, Opt.Section.VISUALS, "Centered Chat", "Center match result chat summaries.", Material.LECTERN
    );
    public static final Opt<Boolean> KILL_EFFECTS = bool(
        "visuals.kill-effects", true, Opt.Section.VISUALS, "Kill Effects", "Allow players' chosen kill effects.", Material.BLAZE_POWDER
    );
    public static final Opt<Boolean> TRAILS = bool(
        "visuals.projectile-trails", true, Opt.Section.VISUALS, "Projectile Trails", "Allow players' chosen projectile trails.", Material.ARROW
    );
    public static final Opt<Boolean> SENTINEL_ENABLED = bool(
        "anticheat.sentinel.enabled",
        true,
        Opt.Section.ANTICHEAT,
        "Sentinel Anti-Cheat",
        "Combat checks: reach, hitbox/aim, multi-aura, no-swing, hitting while using an item, anti-knockback, timer and fast-place. Alerts staff with levels (/sentinel).",
        Material.ENDER_EYE
    );
    public static final Opt<Double> SENTINEL_REACH_TOLERANCE = dbl(
        "anticheat.sentinel.reach-tolerance",
        0.25,
        Opt.Section.ANTICHEAT,
        "Reach Tolerance",
        "Blocks over the normal reach (3) allowed before a Reach flag. Lower = stricter.",
        Material.STICK,
        0.05,
        1.5,
        0.05
    );
    public static final Opt<Integer> SENTINEL_MAX_PING = integer(
        "anticheat.sentinel.max-ping",
        350,
        Opt.Section.ANTICHEAT,
        "Skip Above Ping",
        "Players with a higher ping aren't checked (too laggy to judge).",
        Material.REPEATER,
        100,
        1000,
        25
    );
    public static final Opt<Integer> SENTINEL_TIMER_MS = integer(
        "anticheat.sentinel.timer-ms",
        250,
        Opt.Section.ANTICHEAT,
        "Timer Allowance",
        "Milliseconds of movement ahead of real time allowed before a Timer flag.",
        Material.CLOCK,
        100,
        2000,
        50
    );
    public static final Opt<Integer> SENTINEL_MAX_PLACE = integer(
        "anticheat.sentinel.max-place-per-second",
        20,
        Opt.Section.ANTICHEAT,
        "Max Blocks / s",
        "Blocks placed in one second before a FastPlace flag.",
        Material.SCAFFOLDING,
        8,
        60,
        1
    );
    public static final Opt<Integer> SENTINEL_POOL_VL = integer(
        "anticheat.sentinel.auto-pool-vl",
        0,
        Opt.Section.ANTICHEAT,
        "Auto Hacker Pool",
        "Total violation level that sends a player to the hacker pool for 7 days automatically. 0 = never (staff decide).",
        Material.IRON_BARS,
        0,
        200,
        5
    );
    public static final Opt<Integer> SS_MINUTES = integer(
        "anticheat.screenshare.minutes",
        5,
        Opt.Section.ANTICHEAT,
        "Screenshare Time",
        "Minutes a frozen player has to join the Discord (shown as a countdown).",
        Material.CLOCK,
        1,
        30,
        1
    );
    public static final Opt<String> SS_TOOL_LINK = str(
        "anticheat.screenshare.tool-link",
        "",
        Opt.Section.ANTICHEAT,
        "Screenshare Tool Link",
        "Optional download link of the checking tool your staff use (shown to the frozen player). Empty = not shown.",
        Material.SPYGLASS
    );
    public static final Opt<String> SS_CHEAT_ACTION = str(
        "anticheat.screenshare.cheating-command",
        "hackerpool {player} 30d Cheating (screenshare)",
        Opt.Section.ANTICHEAT,
        "Caught Cheating",
        "Console command when staff end a screenshare as cheating. {player}, {staff}.",
        Material.IRON_BARS
    );
    public static final Opt<String> SS_ADMIT_ACTION = str(
        "anticheat.screenshare.admit-command",
        "hackerpool {player} 14d Admitted cheating (screenshare)",
        Opt.Section.ANTICHEAT,
        "Admitted",
        "Console command when the player admits (/ss admit). Usually shorter.",
        Material.PAPER
    );
    public static final Opt<String> SS_LOGOUT_ACTION = str(
        "anticheat.screenshare.logout-command",
        "hackerpool {player} 30d Logged out during a screenshare",
        Opt.Section.ANTICHEAT,
        "Logged Out",
        "Console command when a frozen player logs out (counts as admitting).",
        Material.BARRIER
    );
    public static final Opt<Boolean> AC_EXEMPT = bool(
        "anticheat.exempt-enabled",
        true,
        Opt.Section.ANTICHEAT,
        "Velocity Exemptions",
        "Suppress Vulcan/Grim(legacy API) movement flags right after wind bursts, mace smashes and explosions.",
        Material.WIND_CHARGE
    );
    public static final Opt<Integer> AC_EXEMPT_MS = integer(
        "anticheat.exempt-millis",
        1500,
        Opt.Section.ANTICHEAT,
        "Exemption Time",
        "Milliseconds of exemption after a high-velocity event.",
        Material.CLOCK,
        100,
        10000,
        100
    );
    public static final Opt<Boolean> CLIENT_CHECK = bool(
        "anticheat.client-check",
        true,
        Opt.Section.ANTICHEAT,
        "Hacked Client Check",
        "Checks the client brand and mod channels of every player against the list below.",
        Material.SPYGLASS
    );
    public static final Opt<String> BLOCKED_CLIENTS = str(
        "anticheat.blocked-clients",
        "wurst,meteor,aristois,impact,liquidbounce,future,rusherhack,inertia,wolfram,kami,salhack,boze,thunderhack,konas,phobos,3arthh4ck,forgehax,sigma,vape,raven,novoline,tenacity,baritone,freecam,xray",
        Opt.Section.ANTICHEAT,
        "Blocked Clients / Mods",
        "Words in a client brand or mod channel that are not allowed (comma list).",
        Material.BARRIER
    );
    public static final Opt<String> CLIENT_ACTION = enumOpt(
        "anticheat.client-action",
        Opt.ClientAction.KICK,
        Opt.Section.ANTICHEAT,
        "Blocked Client Action",
        "KICK = kick and alert staff, ALERT = only alert staff.",
        Material.IRON_DOOR,
        Opt.ClientAction.class
    );
    public static final Opt<Boolean> CLICK_CHECK = bool(
        "anticheat.click-check",
        true,
        Opt.Section.ANTICHEAT,
        "Autoclicker / Macro Check",
        "Alerts staff about very high CPS or robot-perfect click timing in fights.",
        Material.STONE_BUTTON
    );
    public static final Opt<Integer> MAX_CPS = integer(
        "anticheat.max-cps", 20, Opt.Section.ANTICHEAT, "CPS Alert", "Clicks per second (held for 3 s) that alert staff.", Material.REDSTONE, 8, 50, 1
    );
    public static final Opt<Boolean> ESSENTIALS = bool(
        "smp.essentials",
        true,
        Opt.Section.ESSENTIALS,
        "SMP Essentials",
        "Built-in /home /spawn /back /rtp /tpa /warp for the SMP worlds (each mode keeps its own homes and warps). Turn off if another plugin should handle them.",
        Material.RED_BED
    );
    public static final Opt<Boolean> ESS_HOMES = bool(
        "smp.homes.enabled", true, Opt.Section.ESSENTIALS, "Homes", "/sethome /home /delhome /homes.", Material.WHITE_BED
    );
    public static final Opt<Integer> HOMES_MAX = integer(
        "smp.homes.max",
        3,
        Opt.Section.ESSENTIALS,
        "Max Homes",
        "Homes per player per mode. More with the permission vortexpvp.homes.<number> or vortexpvp.homes.unlimited.",
        Material.BLUE_BED,
        1,
        100,
        1
    );
    public static final Opt<Integer> HOMES_HELPER = integer(
        "smp.homes.helper", 4, Opt.Section.ESSENTIALS, "Homes: Helpers", "Homes per mode for staff with the Helper role.", Material.CYAN_BED, 1, 100, 1
    );
    public static final Opt<Integer> HOMES_MOD = integer(
        "smp.homes.mod", 5, Opt.Section.ESSENTIALS, "Homes: Mods", "Homes per mode for staff with the Mod role.", Material.BLUE_BED, 1, 100, 1
    );
    public static final Opt<Integer> HOMES_ADMIN = integer(
        "smp.homes.admin", 10, Opt.Section.ESSENTIALS, "Homes: Admins", "Homes per mode for staff with the Admin role (and ops).", Material.RED_BED, 1, 100, 1
    );
    public static final Opt<Boolean> ESS_TPA = bool(
        "smp.tpa.enabled", true, Opt.Section.ESSENTIALS, "Teleport Requests", "/tpa /tpahere /tpaccept /tpdeny /tptoggle (same mode only).", Material.ENDER_EYE
    );
    public static final Opt<Integer> TPA_EXPIRE = integer(
        "smp.tpa.expire-seconds", 60, Opt.Section.ESSENTIALS, "Request Expiry", "Seconds before a teleport request expires.", Material.CLOCK, 10, 600, 5
    );
    public static final Opt<Boolean> ESS_BACK = bool(
        "smp.back.enabled", true, Opt.Section.ESSENTIALS, "/back", "Return to where you were before your last teleport.", Material.COMPASS
    );
    public static final Opt<String> BACK_DEATH_MODES = str(
        "smp.back.on-death-modes",
        "SURVIVAL,ECO",
        Opt.Section.ESSENTIALS,
        "/back After Death",
        "Modes where /back also returns you to where you died (comma list of SURVIVAL, LIFESTEAL, STRENGTH, RANKED, ECO; empty = none).",
        Material.SKELETON_SKULL
    );
    public static final Opt<Boolean> ESS_RTP = bool(
        "smp.rtp.enabled",
        true,
        Opt.Section.ESSENTIALS,
        "Random Teleport",
        "/rtp - a random safe spot in the overworld or nether of your mode.",
        Material.GRASS_BLOCK
    );
    public static final Opt<Integer> RTP_MAX = integer(
        "smp.rtp.max-radius",
        5000,
        Opt.Section.ESSENTIALS,
        "RTP Max Distance",
        "Farthest /rtp distance from the world spawn.",
        Material.FILLED_MAP,
        200,
        50000,
        100
    );
    public static final Opt<Integer> RTP_MIN = integer(
        "smp.rtp.min-radius", 500, Opt.Section.ESSENTIALS, "RTP Min Distance", "Closest /rtp distance from the world spawn.", Material.MAP, 0, 20000, 100
    );
    public static final Opt<Integer> RTP_COOLDOWN = integer(
        "smp.rtp.cooldown-seconds", 120, Opt.Section.ESSENTIALS, "RTP Cooldown", "Seconds between two /rtp of the same player.", Material.CLOCK, 0, 3600, 10
    );
    public static final Opt<Boolean> ESS_WARPS = bool(
        "smp.warps.enabled",
        true,
        Opt.Section.ESSENTIALS,
        "Warps",
        "/warp /warps (admins: /setwarp /delwarp, icon = the item in your hand).",
        Material.END_PORTAL_FRAME
    );
    public static final Opt<Integer> TP_WARMUP = integer(
        "smp.teleport.warmup-seconds",
        3,
        Opt.Section.ESSENTIALS,
        "Teleport Warmup",
        "Seconds to stand still before /home /spawn /back /rtp /tpa /warp (0 = instant).",
        Material.HOPPER,
        0,
        15,
        1
    );
    public static final Opt<Integer> TP_COOLDOWN = integer(
        "smp.teleport.cooldown-seconds",
        5,
        Opt.Section.ESSENTIALS,
        "Teleport Cooldown",
        "Seconds between two teleports of the same player.",
        Material.REPEATER,
        0,
        600,
        5
    );
    public static final Opt<Boolean> TP_CANCEL_MOVE = bool(
        "smp.teleport.cancel-on-move", true, Opt.Section.ESSENTIALS, "Cancel On Move", "Moving during the warmup cancels the teleport.", Material.LEATHER_BOOTS
    );
    public static final Opt<Boolean> TP_CANCEL_DAMAGE = bool(
        "smp.teleport.cancel-on-damage",
        true,
        Opt.Section.ESSENTIALS,
        "Cancel On Damage",
        "Taking damage during the warmup cancels the teleport.",
        Material.IRON_SWORD
    );
    public static final Opt<Integer> COMBAT_TAG = integer(
        "smp.combat-tag-seconds",
        15,
        Opt.Section.ESSENTIALS,
        "SMP Combat Tag",
        "Seconds a PvP hit blocks commands/teleports; logging out meanwhile = death (0 = off).",
        Material.NETHERITE_SWORD,
        0,
        120,
        1
    );
    public static final Opt<Boolean> CLAIMS = bool(
        "claims.enabled",
        true,
        Opt.Section.CLAIMS,
        "Land Claims",
        "Players protect their builds with a golden shovel (or /claim). Nobody else can build, open chests or use doors there unless trusted.",
        Material.GOLDEN_SHOVEL
    );
    public static final Opt<String> CLAIM_MODES = str(
        "claims.modes",
        "SURVIVAL,ECO",
        Opt.Section.CLAIMS,
        "Claim Modes",
        "Modes where land can be claimed (comma list of SURVIVAL, LIFESTEAL, STRENGTH, RANKED, ECO).",
        Material.MAP
    );
    public static final Opt<Integer> CLAIM_START_BLOCKS = integer(
        "claims.start-blocks",
        1000,
        Opt.Section.CLAIMS,
        "Starting Claim Blocks",
        "Claim blocks (area) every player starts with.",
        Material.GRASS_BLOCK,
        0,
        100000,
        100
    );
    public static final Opt<Integer> CLAIM_BLOCKS_HOUR = integer(
        "claims.blocks-per-hour",
        250,
        Opt.Section.CLAIMS,
        "Claim Blocks Per Hour",
        "Claim blocks earned per hour played in a claim mode (not while AFK).",
        Material.CLOCK,
        0,
        10000,
        25
    );
    public static final Opt<Integer> CLAIM_MAX_EARNED = integer(
        "claims.max-earned-blocks",
        40000,
        Opt.Section.CLAIMS,
        "Max Earned Blocks",
        "Playtime stops adding claim blocks after this many.",
        Material.GOLD_BLOCK,
        0,
        1000000,
        1000
    );
    public static final Opt<Integer> CLAIM_MIN_WIDTH = integer(
        "claims.min-width", 5, Opt.Section.CLAIMS, "Min Claim Width", "Smallest side length of a claim.", Material.STICK, 1, 100, 1
    );
    public static final Opt<Integer> CLAIM_MAX_CLAIMS = integer(
        "claims.max-claims", 10, Opt.Section.CLAIMS, "Max Claims", "Claims per player per mode.", Material.CHEST, 1, 200, 1
    );
    public static final Opt<Integer> CLAIM_SPAWN_DISTANCE = integer(
        "claims.spawn-protection", 64, Opt.Section.CLAIMS, "Spawn Distance", "No claims this close to the world spawn.", Material.BEACON, 0, 2000, 8
    );
    public static final Opt<Boolean> CLAIM_EXPLOSIONS = bool(
        "claims.block-explosions", true, Opt.Section.CLAIMS, "Explosion Protection", "TNT, creepers, beds... don't break blocks inside claims.", Material.TNT
    );
    public static final Opt<Boolean> CLAIM_PVP = bool(
        "claims.block-pvp", false, Opt.Section.CLAIMS, "No PvP In Claims", "Players can't hurt each other inside claims.", Material.SHIELD
    );
    public static final Opt<Boolean> CLAIM_ENTER_MESSAGE = bool(
        "claims.enter-message",
        true,
        Opt.Section.CLAIMS,
        "Enter / Leave Messages",
        "Action bar message when walking into or out of a claim.",
        Material.OAK_SIGN
    );
    public static final Opt<Boolean> CHAT_FORMAT_ON = bool(
        "chat.format.enabled",
        true,
        Opt.Section.CHAT,
        "Chat Format",
        "Format every chat message with the layout below (turn off if another chat plugin does it).",
        Material.WRITABLE_BOOK
    );
    public static final Opt<String> CHAT_FORMAT = str(
        "chat.format.layout",
        "<mode><tier><prefix><name><suffix><dark_gray> » </dark_gray><white><message>",
        Opt.Section.CHAT,
        "Chat Layout",
        "MiniMessage. Tags: <mode> (mode tag, shown when the reader is elsewhere), <tier> (best PvP tier), <prefix> <suffix> (LuckPerms through PlaceholderAPI), <hearts>, <name>, <message>.",
        Material.NAME_TAG
    );
    public static final Opt<Boolean> CHAT_PER_MODE = bool(
        "chat.per-mode",
        true,
        Opt.Section.CHAT,
        "Chat Per Mode",
        "Players only see chat from their own mode (PvP, Hub, each SMP) like separate servers. Start a message with ! to talk to everyone. Staff see everything.",
        Material.ENDER_CHEST
    );
    public static final Opt<Boolean> CHAT_TIERS = bool(
        "chat.tier-tags", true, Opt.Section.CHAT, "Tier In Chat", "Best PvP tier (e.g. HT3) in front of names.", Material.NETHER_STAR
    );
    public static final Opt<Boolean> CHAT_MENTIONS = bool(
        "chat.mentions", true, Opt.Section.CHAT, "@Mentions", "Writing a player's name highlights it and pings them.", Material.BELL
    );
    public static final Opt<Boolean> CHAT_ITEM = bool(
        "chat.show-item", true, Opt.Section.CHAT, "[item] In Chat", "Writing [item] shows the item in your hand (hover to see it).", Material.ITEM_FRAME
    );
    public static final Opt<Integer> CHAT_COOLDOWN = integer(
        "chat.cooldown-millis", 1000, Opt.Section.CHAT, "Chat Cooldown", "Milliseconds between two messages (anti-spam).", Material.CLOCK, 0, 10000, 250
    );
    public static final Opt<Boolean> CHAT_ANTI_REPEAT = bool(
        "chat.anti-repeat", true, Opt.Section.CHAT, "Anti-Repeat", "Block the same message twice in a row.", Material.REPEATER
    );
    public static final Opt<Boolean> CHAT_ANTI_CAPS = bool(
        "chat.anti-caps", true, Opt.Section.CHAT, "Anti-Caps", "Messages that are mostly CAPITALS are turned to lowercase.", Material.PAPER
    );
    public static final Opt<Boolean> CHAT_FILTER = bool(
        "chat.filter.enabled",
        true,
        Opt.Section.CHAT,
        "Swear Filter",
        "Blocks swearing in chat, /msg, party chat, signs and anvil names (catches f.u.c.k, fuuuck, f4ck). Edit the words in /pvpadmin -> Chat Moderation.",
        Material.BARRIER
    );
    public static final Opt<String> CHAT_FILTER_ACTION = new Opt<>(
        "chat.filter.action",
        Opt.Type.ENUM,
        "BLOCK",
        Opt.Section.CHAT,
        "Swear Filter Action",
        "BLOCK = the message is not sent. CENSOR = it is sent with the word replaced by ****.",
        Material.IRON_DOOR,
        0.0,
        0.0,
        1.0,
        Opt.FilterAction.class,
        true
    );
    public static final Opt<Boolean> CHAT_FILTER_MUTE = bool(
        "chat.filter.auto-mute", true, Opt.Section.CHAT, "Auto-Mute Swearing", "Swearing mutes the player (see Mute Steps).", Material.CLOCK
    );
    public static final Opt<String> CHAT_FILTER_STEPS = str(
        "chat.filter.mute-steps",
        "2,10,30",
        Opt.Section.CHAT,
        "Mute Steps (minutes)",
        "Mute length for the 1st, 2nd, 3rd... offence inside the reset window. The last step repeats.",
        Material.REPEATER
    );
    public static final Opt<Integer> CHAT_FILTER_RESET = integer(
        "chat.filter.reset-hours", 24, Opt.Section.CHAT, "Offence Reset (hours)", "Offences older than this are forgotten.", Material.SUNFLOWER, 1, 720, 1
    );
    public static final Opt<Boolean> CHAT_FILTER_NOTIFY = bool(
        "chat.filter.notify-staff", true, Opt.Section.CHAT, "Alert Staff", "Staff see what was blocked and who was muted.", Material.BELL
    );
    public static final Opt<Boolean> PRIVATE_MESSAGES = bool(
        "chat.private-messages", true, Opt.Section.CHAT, "Private Messages", "/msg /r /ignore /msgtoggle /socialspy.", Material.FEATHER
    );
    public static final Opt<String> JOIN_MESSAGE = str(
        "chat.join-message",
        "<dark_gray>[<green>+</green>]</dark_gray> <gray><name>",
        Opt.Section.CHAT,
        "Join Message",
        "Shown when a player joins (<name>). Empty = no message.",
        Material.LIME_DYE
    );
    public static final Opt<String> QUIT_MESSAGE = str(
        "chat.quit-message",
        "<dark_gray>[<red>-</red>]</dark_gray> <gray><name>",
        Opt.Section.CHAT,
        "Quit Message",
        "Shown when a player leaves (<name>). Empty = no message.",
        Material.RED_DYE
    );
    public static final Opt<String> FIRST_JOIN_MESSAGE = str(
        "chat.first-join-message",
        "<gold>✦</gold> <yellow>Welcome <white><name></white> to the server! <gray>(#<count>)",
        Opt.Section.CHAT,
        "First Join Message",
        "Shown to everyone when someone joins for the very first time (<name>, <count>).",
        Material.CAKE
    );
    public static final Opt<Boolean> EVENTS_ENABLED = bool(
        "events.enabled",
        true,
        Opt.Section.EVENTS,
        "Tournaments",
        "Bracket tournaments: /tournament host <kit> (staff), players join with one click.",
        Material.GOLDEN_HELMET
    );
    public static final Opt<Integer> EVENT_JOIN_SECONDS = integer(
        "events.join-seconds", 60, Opt.Section.EVENTS, "Join Time", "Seconds players have to join before the bracket starts.", Material.CLOCK, 15, 600, 5
    );
    public static final Opt<Integer> EVENT_MIN_PLAYERS = integer(
        "events.min-players", 4, Opt.Section.EVENTS, "Min Players", "A tournament with fewer players is cancelled.", Material.PLAYER_HEAD, 2, 64, 1
    );
    public static final Opt<Integer> EVENT_MAX_PLAYERS = integer(
        "events.max-players", 64, Opt.Section.EVENTS, "Max Players", "Tournament size limit.", Material.TOTEM_OF_UNDYING, 2, 256, 2
    );
    public static final Opt<Integer> EVENT_ROUNDS = integer(
        "events.first-to",
        1,
        Opt.Section.EVENTS,
        "First To",
        "Rounds needed to win a tournament match (the final always needs one more).",
        Material.TARGET,
        1,
        5,
        1
    );
    public static final Opt<Integer> EVENT_AUTO_MINUTES = integer(
        "events.auto-every-minutes",
        0,
        Opt.Section.EVENTS,
        "Auto Tournaments",
        "Start a tournament with a random kit every N minutes when enough players are in the PvP lobby (0 = off).",
        Material.REPEATER,
        0,
        720,
        5
    );
    public static final Opt<String> EVENT_REWARD = str(
        "events.reward-command",
        "",
        Opt.Section.EVENTS,
        "Winner Reward",
        "Console command(s) for the winner, separated by ; - <player> is the winner's name (e.g. balance give <player> 500).",
        Material.CHEST
    );
    public static final Opt<Boolean> EVENT_BROADCAST_ALL = bool(
        "events.announce-everywhere",
        true,
        Opt.Section.EVENTS,
        "Announce Everywhere",
        "Announce tournaments in the SMP and hub too (clicking Join travels to PvP).",
        Material.GOAT_HORN
    );
    public static final Opt<String> DB_TYPE = new Opt<>(
        "database.type", Opt.Type.STRING, "SQLITE", Opt.Section.GENERAL, "Database", "SQLITE or MYSQL", Material.CHEST, 0.0, 0.0, 1.0, null, false
    );
    public static final Opt<String> DB_HOST = new Opt<>(
        "database.host", Opt.Type.STRING, "localhost", Opt.Section.GENERAL, "", "", Material.CHEST, 0.0, 0.0, 1.0, null, false
    );
    public static final Opt<Integer> DB_PORT = new Opt<>(
        "database.port", Opt.Type.INT, 3306, Opt.Section.GENERAL, "", "", Material.CHEST, 1.0, 65535.0, 1.0, null, false
    );
    public static final Opt<String> DB_NAME = new Opt<>(
        "database.name", Opt.Type.STRING, "vortexpvp", Opt.Section.GENERAL, "", "", Material.CHEST, 0.0, 0.0, 1.0, null, false
    );
    public static final Opt<String> DB_USER = new Opt<>(
        "database.user", Opt.Type.STRING, "root", Opt.Section.GENERAL, "", "", Material.CHEST, 0.0, 0.0, 1.0, null, false
    );
    public static final Opt<String> DB_PASSWORD = new Opt<>(
        "database.password", Opt.Type.STRING, "", Opt.Section.GENERAL, "", "", Material.CHEST, 0.0, 0.0, 1.0, null, false
    );
    public static final Opt<List<String>> EXCLUDED_WORLDS = new Opt<>(
        "worlds.excluded",
        Opt.Type.STRING_LIST,
        List.of(),
        Opt.Section.GENERAL,
        "",
        "Worlds the plugin ignores (e.g. your SMP worlds).",
        Material.CHEST,
        0.0,
        0.0,
        1.0,
        null,
        false
    );
    public static final Opt<Boolean> SMP_DIMENSIONS = bool(
        "smp.own-dimensions",
        true,
        Opt.Section.EXTRAS,
        "Own Nether & End",
        "Every SMP world gets its own <world>_nether and <world>_the_end; portals lead there.",
        Material.OBSIDIAN
    );
    public static final Opt<Boolean> SMP_RESPAWN_BED = bool(
        "smp.respawn-at-bed",
        true,
        Opt.Section.EXTRAS,
        "Respawn At Bed",
        "SMP deaths respawn at the player's bed or anchor; without one (or when off) at the hub.",
        Material.RED_BED
    );
    public static final Opt<Boolean> SEPARATE_INVENTORIES = new Opt<>(
        "worlds.separate-inventories",
        Opt.Type.BOOL,
        true,
        Opt.Section.GENERAL,
        "",
        "Keep inventories of excluded worlds separate from PvP.",
        Material.CHEST,
        0.0,
        1.0,
        1.0,
        null,
        false
    );

    private Opt(
        String path,
        Opt.Type type,
        T def,
        Opt.Section section,
        String name,
        String desc,
        Material icon,
        double min,
        double max,
        double step,
        Class<? extends Enum<?>> enumClass,
        boolean gui
    ) {
        this.path = path;
        this.type = type;
        this.def = def;
        this.section = section;
        this.name = name;
        this.desc = desc;
        this.icon = icon;
        this.min = min;
        this.max = max;
        this.step = step;
        this.enumClass = enumClass;
        this.gui = gui;
        ALL.add(this);
    }

    public static List<Opt<?>> all() {
        return Collections.unmodifiableList(ALL);
    }

    private static Opt<Boolean> bool(String path, boolean def, Opt.Section s, String name, String desc, Material icon) {
        return new Opt<>(path, Opt.Type.BOOL, def, s, name, desc, icon, 0.0, 1.0, 1.0, null, true);
    }

    private static Opt<Integer> integer(String path, int def, Opt.Section s, String name, String desc, Material icon, int min, int max, int step) {
        return new Opt<>(path, Opt.Type.INT, def, s, name, desc, icon, min, max, step, null, true);
    }

    private static Opt<Double> dbl(String path, double def, Opt.Section s, String name, String desc, Material icon, double min, double max, double step) {
        return new Opt<>(path, Opt.Type.DOUBLE, def, s, name, desc, icon, min, max, step, null, true);
    }

    private static Opt<String> str(String path, String def, Opt.Section s, String name, String desc, Material icon) {
        return new Opt<>(path, Opt.Type.STRING, def, s, name, desc, icon, 0.0, 256.0, 1.0, null, true);
    }

    private static <E extends Enum<E>> Opt<String> enumOpt(String path, E def, Opt.Section s, String name, String desc, Material icon, Class<E> cls) {
        return new Opt<>(path, Opt.Type.ENUM, def.name(), s, name, desc, icon, 0.0, 0.0, 1.0, cls, true);
    }

    public static enum ClientAction {
        KICK,
        ALERT;
    }

    public static enum FoundEffect {
        ICON,
        TOTEM,
        SWORD,
        BOTH,
        NONE;
    }

    public static enum FilterAction {
        BLOCK,
        CENSOR;
    }

    public static enum MapPreview {
        OFF,
        FLAT_KITS,
        ALL;
    }

    public static enum PreviewDetail {
        LOW,
        NORMAL,
        HIGH;
    }

    public static enum MenuStyle {
        AUTO,
        DIALOG,
        CHEST;
    }

    public static enum RankUpStyle {
        DROP,
        SCREEN,
        SMOOTH,
        BLOCKS;
    }

    public static enum ResetMode {
        TRACKED,
        SNAPSHOT,
        BOTH;
    }

    public static enum Section {
        GENERAL("General", Material.COMPASS),
        LOBBY("Lobby", Material.BEACON),
        QUEUE("Queues & Matchmaking", Material.CLOCK),
        MATCH("Matches", Material.DIAMOND_SWORD),
        RANKING("Ranking", Material.NETHER_STAR),
        FFA("Free For All", Material.NETHERITE_AXE),
        ARENA("Arenas & Reset", Material.GRASS_BLOCK),
        PARTY("Parties", Material.CAKE),
        VISUALS("Visuals & Effects", Material.FIREWORK_ROCKET),
        ANTICHEAT("Anti-Cheat Compatibility", Material.SHIELD),
        EVENTS("Tournaments", Material.GOLDEN_HELMET),
        EXTRAS("SMP & Extras", Material.OAK_SAPLING),
        ESSENTIALS("SMP Homes & Teleports", Material.RED_BED),
        CLAIMS("SMP Land Claims", Material.GOLDEN_SHOVEL),
        CHAT("Chat & Messages", Material.WRITABLE_BOOK),
        WEB("Website & Resource Pack", Material.FILLED_MAP);

        public final String title;
        public final Material icon;

        private Section(String title, Material icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    public static enum StartMode {
        ELEVATOR,
        GATES,
        FREEZE,
        CAGE;
    }

    public static enum ThrowMode {
        OFF,
        SMART,
        ALL;
    }

    public static enum Type {
        BOOL,
        INT,
        DOUBLE,
        STRING,
        ENUM,
        INT_LIST,
        STRING_LIST;
    }

    public static enum WallStyle {
        NONE,
        STONE_BRICK,
        BLACKSTONE,
        QUARTZ,
        DEEPSLATE,
        NETHER;
    }
}
