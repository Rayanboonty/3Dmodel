package net.vortexsmp.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Every setting of VortexSMP (config.yml). Paths match the old SMP settings of VortexRealm/PracticePvP. */
public final class Opt<T> {
    private static final List<Opt<?>> ALL = new ArrayList<>();
    public final String path;
    public final T def;
    public final String desc;

    // network: where /hub and /lobby send players (the Practice server)
    public static final Opt<String> HUB_HOST = new Opt<>("network.hub.host", "", "Address of the Practice/lobby server, e.g. play.example.net (empty = /hub is off).");
    public static final Opt<Integer> HUB_PORT = new Opt<>("network.hub.port", 25565, "Port of the Practice/lobby server.");
    public static final Opt<String> NETWORK_MODE = new Opt<>("network.mode", "TRANSFER", "TRANSFER = built-in transfer (accepts-transfers=true on the lobby server). PROXY = Velocity/BungeeCord Connect.");
    public static final Opt<String> HUB_PROXY = new Opt<>("network.hub.proxy-name", "lobby", "The lobby's server name in velocity.toml (PROXY mode only).");
    public static final Opt<String> SERVER_NAME = new Opt<>("general.server-name", "Vortex Realm", "Name in the sidebar and tab.");
    public static final Opt<String> SERVER_IP = new Opt<>("general.server-ip", "play.yourserver.net", "IP shown at the bottom of the sidebar.");
    public static final Opt<String> LINK_DISCORD = new Opt<>("extras.links.discord", "", "Discord link shown in the tab footer.");
    public static final Opt<Boolean> SMP_SIDEBAR = new Opt<>("extras.smp-sidebar", true, "The SMP sidebar.");
    public static final Opt<Boolean> TAB = new Opt<>("extras.tab", true, "Header and footer of the tab list.");
    public static final Opt<Boolean> JOIN_MESSAGES = new Opt<>("extras.join-messages", true, "Styled join and leave messages.");
    public static final Opt<Boolean> SMP_UTILS = new Opt<>("extras.smp-commands", true, "/craft /ec /trash /hat /nv /near.");
    public static final Opt<String> SMP_MAP_URL = new Opt<>("extras.web-map-url", "", "Link of your BlueMap / squaremap page, shown in /smpadmin.");
    // essentials
    public static final Opt<Boolean> ESSENTIALS = new Opt<>("smp.essentials", true, "Built-in /home /spawn /back /rtp /tpa /warp.");
    public static final Opt<Boolean> ESS_HOMES = new Opt<>("smp.homes.enabled", true, "/sethome /home /delhome /homes.");
    public static final Opt<Integer> HOMES_MAX = new Opt<>("smp.homes.max", 3, "Homes per player (more with vortexsmp.homes.<n>).");
    public static final Opt<Integer> HOMES_HELPER = new Opt<>("smp.homes.helper", 4, "Homes with vortexsmp.homes.role.helper.");
    public static final Opt<Integer> HOMES_MOD = new Opt<>("smp.homes.mod", 5, "Homes with vortexsmp.homes.role.mod.");
    public static final Opt<Integer> HOMES_ADMIN = new Opt<>("smp.homes.admin", 10, "Homes with vortexsmp.homes.role.admin (and ops).");
    public static final Opt<Boolean> ESS_TPA = new Opt<>("smp.tpa.enabled", true, "/tpa /tpahere /tpaccept /tpdeny /tptoggle.");
    public static final Opt<Integer> TPA_EXPIRE = new Opt<>("smp.tpa.expire-seconds", 60, "Seconds before a teleport request expires.");
    public static final Opt<Boolean> ESS_BACK = new Opt<>("smp.back.enabled", true, "/back returns to where you were before your last teleport.");
    public static final Opt<String> BACK_DEATH_MODES = new Opt<>("smp.back.on-death-modes", "SURVIVAL", "SURVIVAL = /back also returns you to where you died; empty = never.");
    public static final Opt<Boolean> ESS_RTP = new Opt<>("smp.rtp.enabled", true, "/rtp random teleport.");
    public static final Opt<Integer> RTP_MAX = new Opt<>("smp.rtp.max-radius", 5000, "Farthest /rtp distance from spawn.");
    public static final Opt<Integer> RTP_MIN = new Opt<>("smp.rtp.min-radius", 500, "Closest /rtp distance from spawn.");
    public static final Opt<Integer> RTP_COOLDOWN = new Opt<>("smp.rtp.cooldown-seconds", 120, "Seconds between two /rtp.");
    public static final Opt<Boolean> ESS_WARPS = new Opt<>("smp.warps.enabled", true, "/warp /warps (admins: /setwarp /delwarp).");
    public static final Opt<Integer> TP_WARMUP = new Opt<>("smp.teleport.warmup-seconds", 3, "Seconds to stand still before a teleport (0 = instant).");
    public static final Opt<Integer> TP_COOLDOWN = new Opt<>("smp.teleport.cooldown-seconds", 5, "Seconds between two teleports.");
    public static final Opt<Boolean> TP_CANCEL_MOVE = new Opt<>("smp.teleport.cancel-on-move", true, "Moving during the warmup cancels the teleport.");
    public static final Opt<Boolean> TP_CANCEL_DAMAGE = new Opt<>("smp.teleport.cancel-on-damage", true, "Taking damage during the warmup cancels the teleport.");
    public static final Opt<Integer> COMBAT_TAG = new Opt<>("smp.combat-tag-seconds", 15, "Seconds a PvP hit blocks commands/teleports; logging out meanwhile = death (0 = off).");
    // discord, invites and drills
    public static final Opt<String> DISCORD_TOKEN = new Opt<>("discord.bot-token", "", "Token of your Discord bot (Developer Portal -> Bot). Needs the Message Content intent and Manage Server permission.");
    public static final Opt<String> DISCORD_GUILD = new Opt<>("discord.guild-id", "", "ID of your Discord server (right-click the server -> Copy ID).");
    public static final Opt<String> DISCORD_LINK_CHANNEL = new Opt<>("discord.link-channel-id", "", "Channel where players post their /link code.");
    public static final Opt<String> DISCORD_INVITES_CHANNEL = new Opt<>("discord.invites-channel-id", "", "Channel where the bot keeps an invite leaderboard post up to date (empty = no post).");
    public static final Opt<Boolean> DRILLS = new Opt<>("drills.enabled", true, "Drill pickaxes that mine 2x2, 3x3 or 4x4, earned with Discord invites (/drill).");
    public static final Opt<String> DRILL_TIERS = new Opt<>("drills.tiers", "10:2,20:3,30:4", "invites:size pairs - 10 invites = 2x2 drill, 20 = 3x3, 30+ = 4x4.");
    // trading
    public static final Opt<Boolean> TRADE = new Opt<>("trade.enabled", true, "/trade <player>: a safe trade window between two players.");
    public static final Opt<Boolean> MARKET = new Opt<>("market.enabled", true, "/market: post what you give and what you want; others fill your offer.");
    public static final Opt<Integer> MARKET_MAX = new Opt<>("market.max-offers", 5, "Most open offers per player.");
    // auto-update
    public static final Opt<Boolean> UPDATES = new Opt<>("updates.enabled", true, "Download new versions of VortexSMP by itself and install them on a restart (a backup is taken first; nothing is lost).");
    public static final Opt<String> UPDATE_SOURCES = new Opt<>("updates.sources", "https://raw.githubusercontent.com/Rayanboonty/3Dmodel/refs/heads/claude/vortex-realm-pvp-plugin-2x1tq1/vortex-realm/dist/update.json", "Update manifest links (update.json), comma separated, tried in order.");
    public static final Opt<Integer> UPDATE_CHECK = new Opt<>("updates.check-minutes", 30, "Minutes between two checks for a new version.");
    public static final Opt<String> UPDATE_RESTART = new Opt<>("updates.restart", "EMPTY_OR_NIGHT", "EMPTY_OR_NIGHT (when nobody is online, or at the restart hour with a countdown), NOW, or MANUAL. Turn on auto-restart in your host panel.");
    public static final Opt<Integer> UPDATE_HOUR = new Opt<>("updates.restart-hour", 5, "Hour of the day (server time) for a night-time update restart.");
    // import
    public static final Opt<String> IMPORT_MODE = new Opt<>("import.mode", "SURVIVAL", "Which old SMP mode the import brings over: SURVIVAL, ECO, LIFESTEAL, STRENGTH, RANKED or ALL.");
    public static final Opt<Boolean> IMPORT_INVENTORIES = new Opt<>("import.inventories", true, "Give players their old survival inventory and ender chest (once).");
    public static final Opt<Boolean> IMPORT_LOCATIONS = new Opt<>("import.last-locations", true, "Players start where they last were in the old SMP (once).");

    private Opt(String path, T def, String desc) {
        this.path = path;
        this.def = def;
        this.desc = desc;
        ALL.add(this);
    }

    public static List<Opt<?>> all() {
        return Collections.unmodifiableList(ALL);
    }
}
