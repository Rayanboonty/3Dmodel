package net.vortexrealm.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.Material;

public enum Setting {
    DUEL_REQUESTS(
        Setting.Category.GAMEPLAY,
        "Duel Requests",
        Material.DIAMOND_SWORD,
        "Who is allowed to send you duel requests.",
        0,
        "everyone:Everyone",
        "nobody:Nobody"
    ),
    DUEL_POPUP(
        Setting.Category.GAMEPLAY,
        "Duel Request Style",
        Material.BELL,
        "Show incoming duel requests as a pop-up or in chat.",
        0,
        "popup:Pop-up",
        "chat:Chat only"
    ),
    PARTY_INVITES(
        Setting.Category.GAMEPLAY, "Party Invites", Material.CAKE, "Who is allowed to invite you to parties.", 0, "everyone:Everyone", "nobody:Nobody"
    ),
    ALLOW_SPECTATORS(
        Setting.Category.GAMEPLAY, "Spectators", Material.ENDER_EYE, "Allow other players to spectate your matches.", 0, "on:Allowed", "off:Blocked"
    ),
    PING_RANGE(
        Setting.Category.GAMEPLAY,
        "Ping Range",
        Material.REPEATER,
        "Only match opponents whose ping is close to yours (relaxes the longer you wait).",
        0,
        "any:Any ping",
        "50:±50 ms",
        "100:±100 ms",
        "150:±150 ms",
        "250:±250 ms"
    ),
    MATCHMAKING(
        Setting.Category.GAMEPLAY,
        "Matchmaking",
        Material.SPYGLASS,
        "Fast finds games quickly, Strict waits for opponents close to your rank.",
        1,
        "fast:Fast",
        "balanced:Balanced",
        "strict:Strict"
    ),
    AUTO_REQUEUE(Setting.Category.GAMEPLAY, "Auto Re-queue", Material.HOPPER, "Automatically join the same queue again after a match.", 0, "off:Off", "on:On"),
    RANKUP_POPUPS(
        Setting.Category.GAMEPLAY,
        "Rank-up Pop-ups",
        Material.TOTEM_OF_UNDYING,
        "Show the promotion series pop-up when you can rank up.",
        0,
        "on:On",
        "off:Off"
    ),
    NAMETAG_KIT(
        Setting.Category.GAMEPLAY, "Lobby Nametag Rank", Material.NAME_TAG, "Which kit's tier is shown on your nametag in the lobby.", 0, "auto:Best tier"
    ),
    SCOREBOARD(Setting.Category.VISUAL, "Scoreboard", Material.OAK_SIGN, "Show the sidebar scoreboard.", 0, "on:Shown", "off:Hidden"),
    LOBBY_TIME(
        Setting.Category.VISUAL,
        "Lobby Time",
        Material.CLOCK,
        "Your personal time of day while in PvP worlds.",
        0,
        "server:Server time",
        "day:Day",
        "sunset:Sunset",
        "night:Night",
        "midnight:Midnight"
    ),
    LOBBY_PLAYERS(
        Setting.Category.VISUAL,
        "Lobby Players",
        Material.PLAYER_HEAD,
        "Show or hide other players in the lobby.",
        0,
        "show:Shown",
        "party:Party only",
        "hide:Hidden"
    ),
    KILL_EFFECT(
        Setting.Category.VISUAL,
        "Kill Effect",
        Material.BLAZE_POWDER,
        "Effect played where your opponent dies.",
        0,
        "none:None",
        "lightning:Lightning",
        "blood:Blood",
        "firework:Firework",
        "explosion:Explosion",
        "flames:Flames",
        "hearts:Hearts",
        "totem:Totem",
        "souls:Souls",
        "smoke:Smoke"
    ),
    TRAIL(
        Setting.Category.VISUAL,
        "Projectile Trail",
        Material.ARROW,
        "Particles behind your arrows, pearls, potions and tridents.",
        0,
        "none:None",
        "hearts:Hearts",
        "flames:Flames",
        "notes:Notes",
        "crit:Crit",
        "magic:Magic",
        "snow:Snow",
        "end_rod:End Rod",
        "cherry:Cherry",
        "soul:Soul Fire"
    ),
    TITLES(Setting.Category.VISUAL, "Big Titles", Material.PAINTING, "Countdown, round and victory titles.", 0, "on:On", "off:Off"),
    SCORE_BAR(Setting.Category.VISUAL, "Score Bar", Material.ITEM_FRAME, "Heads + live score above your hotbar during duels.", 0, "on:On", "off:Off"),
    HEALTH_TAGS(
        Setting.Category.VISUAL, "Health Under Names", Material.GLISTERING_MELON_SLICE, "Show health under players' names in matches.", 0, "on:On", "off:Off"
    ),
    DEATH_MESSAGES(
        Setting.Category.VISUAL, "Death Messages", Material.SKELETON_SKULL, "Which death messages you see in FFA.", 0, "all:All", "mine:Only mine", "none:None"
    ),
    FIREWORKS(Setting.Category.VISUAL, "Victory Fireworks", Material.FIREWORK_ROCKET, "Fireworks when you win a duel.", 0, "on:On", "off:Off"),
    MATCH_SOUNDS(Setting.Category.SOUND, "Match Sounds", Material.NOTE_BLOCK, "Countdown, round and victory sounds.", 0, "on:On", "off:Off"),
    HIT_SOUND(Setting.Category.SOUND, "Hit Sound", Material.TARGET, "Play a 'ding' when you land a melee hit.", 0, "vanilla:Vanilla", "ding:Ding"),
    QUEUE_SOUNDS(Setting.Category.SOUND, "Queue Sounds", Material.BELL, "Sounds when joining queues and finding matches.", 0, "on:On", "off:Off"),
    CHAT_SOUNDS(Setting.Category.SOUND, "Request Sounds", Material.JUKEBOX, "Sounds for duel requests, party invites and party chat.", 0, "on:On", "off:Off"),
    DOUBLE_JUMP(Setting.Category.LOBBY, "Double Jump", Material.FEATHER, "Double jump in the lobby.", 0, "on:On", "off:Off"),
    LOBBY_FLY(Setting.Category.LOBBY, "Lobby Flight", Material.ELYTRA, "Fly in the lobby (requires vortex.lobbyfly).", 0, "off:Off", "on:On"),
    LOBBY_SPEED(Setting.Category.LOBBY, "Lobby Speed", Material.SUGAR, "Speed effect while walking around the lobby.", 0, "off:Off", "on:On"),
    STATUS(Setting.Category.SOCIAL, "Status", Material.LIME_DYE, "Online, Invisible (appear offline) or Do Not Disturb.", 0, "online:Online", "invisible:Invisible", "dnd:Do Not Disturb"),
    REQ_ONLINE(Setting.Category.SOCIAL, "Requests (Online)", Material.PAPER, "Who can send you requests while Online.", 0, "fr.fg.fl.fm.nb|-1|0:Everyone"),
    REQ_DND(Setting.Category.SOCIAL, "Requests (Do Not Disturb)", Material.PAPER, "Who can send you requests while in Do Not Disturb.", 0, "fr|-1|0:Friends"),
    TELEPORTS(Setting.Category.SOCIAL, "Teleport Requests", Material.ENDER_PEARL, "Who can send you /tpa requests.", 0, "everyone:Everyone", "friends:Friends only", "nobody:Nobody"),
    VISIBILITY(Setting.Category.SOCIAL, "Online Visibility", Material.SPYGLASS, "Who can see that you are online (friends list, tab, /near).", 0, "everyone:Everyone", "friends:Friends only", "nobody:Nobody"),
    MSG_FROM(Setting.Category.SOCIAL, "Private Messages", Material.WRITABLE_BOOK, "Who can send you private messages.", 0, "everyone:Everyone", "friends:Friends only", "nobody:Nobody"),
    SHOW_FLAG(Setting.Category.SOCIAL, "Show My Flag", Material.WHITE_BANNER, "Show your country flag next to your name.", 0, "on:Shown", "off:Hidden"),
    SHOW_REGION(Setting.Category.SOCIAL, "Show My Region", Material.COMPASS, "Show your region on your profile.", 0, "on:Shown", "off:Hidden"),
    SPEC_CHAT(Setting.Category.SOCIAL, "Spectator Chat", Material.ENDER_EYE, "See what spectators say in your matches.", 0, "on:Shown", "off:Hidden"),
    SPEC_NOTIFY(Setting.Category.SOCIAL, "Spectator Alerts", Material.OBSERVER, "'X started spectating' lines in your matches.", 0, "on:Shown", "off:Hidden"),
    FRIEND_ALERTS(Setting.Category.SOCIAL, "Friend Alerts", Material.BELL, "A message when a friend joins or leaves.", 0, "on:On", "off:Off"),
    NAMETAG_RANK(Setting.Category.SOCIAL, "Ranking On Nametag", Material.NAME_TAG, "Show your overall position (#123) on your nametag.", 0, "on:Shown", "off:Hidden"),
    NAMETAG_FLAG(Setting.Category.SOCIAL, "Flag On Nametag", Material.WHITE_BANNER, "Show your flag on your nametag.", 0, "on:Shown", "off:Hidden"),
    XP_RANK(Setting.Category.SOCIAL, "Ranking As XP Level", Material.EXPERIENCE_BOTTLE, "Show your overall position as your XP level in the lobby.", 0, "on:On", "off:Off"),
    PING_EQ(Setting.Category.GAMEPLAY, "Ping Equalizer", Material.REPEATER, "Tick the Ping Equalizer by default when you send a duel.", 0, "off:Off", "on:On"),
    REGION(Setting.Category.SOCIAL, "Region", Material.RECOVERY_COMPASS, "Your preferred server region.", 0, "auto:Automatic");

    public final Setting.Category category;
    public final String title;
    public final Material icon;
    public final String description;
    private final int defIndex;
    private final List<Setting.Option> options;

    private Setting(Setting.Category category, String title, Material icon, String description, int defIndex, String... opts) {
        this.category = category;
        this.title = title;
        this.icon = icon;
        this.description = description;
        this.defIndex = defIndex;
        List<Setting.Option> list = new ArrayList<>();

        for (String o : opts) {
            int i = o.indexOf(58);
            list.add(new Setting.Option(o.substring(0, i), o.substring(i + 1)));
        }

        this.options = Collections.unmodifiableList(list);
    }

    public List<Setting.Option> staticOptions() {
        return this.options;
    }

    public String defaultValue() {
        return this.options.get(Math.min(this.defIndex, this.options.size() - 1)).id();
    }

    public static enum Category {
        GAMEPLAY("Gameplay", Material.DIAMOND_SWORD),
        VISUAL("Visuals", Material.PAINTING),
        SOUND("Sounds", Material.NOTE_BLOCK),
        LOBBY("Lobby", Material.BEACON),
        SOCIAL("Social", Material.PLAYER_HEAD, true);

        public final String title;
        public final Material icon;
        /** Hidden categories are edited from /settings (Privacy, Chat, Name Tag), not from the setting tabs. */
        public final boolean hidden;

        private Category(String title, Material icon) {
            this(title, icon, false);
        }

        private Category(String title, Material icon, boolean hidden) {
            this.title = title;
            this.icon = icon;
            this.hidden = hidden;
        }
    }

    public record Option(String id, String label) {
    }
}
