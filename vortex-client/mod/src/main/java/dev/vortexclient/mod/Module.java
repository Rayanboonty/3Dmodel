package dev.vortexclient.mod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

/** A feature that can be turned on and off from the mod menu. */
public final class Module {
    public enum Category {
        HUD("HUD"), PVP("PvP"), UTILITY("Utility");

        public final String label;

        Category(String label) {
            this.label = label;
        }
    }

    public enum Type { BOOL, SLIDER, CHOICE }

    public record Option(String key, String label, Type type, Object def, double min, double max, double step, List<String> choices) {
        static Option bool(String key, String label, boolean def) {
            return new Option(key, label, Type.BOOL, def, 0, 1, 1, List.of());
        }

        static Option slider(String key, String label, double def, double min, double max, double step) {
            return new Option(key, label, Type.SLIDER, def, min, max, step, List.of());
        }

        static Option choice(String key, String label, String def, String... choices) {
            return new Option(key, label, Type.CHOICE, def, 0, 0, 1, List.of(choices));
        }
    }

    /** Options every HUD element has. */
    static final List<Option> HUD_OPTIONS = List.of(
            Option.slider("scale", "Scale", 1.0, 0.5, 2.0, 0.1),
            Option.bool("background", "Background", true),
            Option.bool("shadow", "Text shadow", true),
            Option.choice("color", "Text color", "White", "White", "Accent", "Aqua", "Gold", "Green", "Pink", "Rainbow", "Custom"),
            Option.slider("hue", "Custom colour (hue)", 200, 0, 360, 5),
            Option.slider("opacity", "Background opacity", 0.45, 0.0, 1.0, 0.05),
            Option.choice("brackets", "Brackets", "None", "None", "[ ]", "( )"),
            Option.bool("labels", "Labels", true),
            Option.bool("border", "Accent border", false));


    public final String id;
    public final String name;
    public final String description;
    public final Category category;
    public final Item icon;
    public final boolean hud;
    public final boolean defaultOn;
    public final List<Option> options;

    private Module(String id, String name, String description, Category category, Item icon, boolean hud, boolean defaultOn, List<Option> extra) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.icon = icon;
        this.hud = hud;
        this.defaultOn = defaultOn;
        List<Option> all = new ArrayList<>(extra);
        if (hud) all.addAll(HUD_OPTIONS);
        this.options = List.copyOf(all);
    }

    public Option option(String key) {
        for (Option o : options) if (o.key().equals(key)) return o;
        return null;
    }

    public static final List<Module> ALL = List.of(
            new Module("boost", "FPS Boost", "More FPS in one switch: fast graphics, shorter view and fewer effects. Your own settings come back when you turn it off.",
                    Category.UTILITY, Items.BLAZE_ROD, false, false, List.of(
                            Option.slider("view", "View distance (chunks)", 8, 4, 16, 1),
                            Option.bool("graphics", "Fast graphics", true),
                            Option.bool("particles", "Fewest particles", true),
                            Option.bool("clouds", "No clouds", true),
                            Option.bool("shadows", "No entity shadows", true),
                            Option.bool("lighting", "No smooth lighting", false),
                            Option.bool("entities", "Hide far-away entities sooner", true))),
            new Module("keystrokes", "Keystrokes", "Shows your movement keys and mouse buttons as you press them.",
                    Category.HUD, Items.PISTON, true, true, List.of(
                            Option.bool("mouse", "Mouse buttons", true),
                            Option.bool("cps", "Clicks per second on buttons", true),
                            Option.bool("space", "Space bar", true))),
            new Module("cps", "CPS", "Your clicks per second.",
                    Category.HUD, Items.TARGET, true, true, List.of(Option.bool("right", "Show right clicks", true), Option.bool("peak", "Best CPS this session", false))),
            new Module("fps", "FPS", "Frames per second.",
                    Category.HUD, Items.REPEATER, true, true, List.of(Option.bool("frametime", "Frame time (ms)", false), Option.bool("minmax", "Lowest and highest", false), Option.bool("fpscolor", "Colour by FPS", false))),
            new Module("ping", "Ping", "Your connection's round trip time to the server.",
                    Category.HUD, Items.SCULK_SENSOR, true, false, List.of(Option.bool("quality", "Colour by quality", true))),
            new Module("coords", "Coordinates", "Where you are and which way you're facing.",
                    Category.HUD, Items.COMPASS, true, true, List.of(
                            Option.bool("facing", "Facing", true),
                            Option.bool("biome", "Biome", false),
                            Option.bool("nether", "Nether / Overworld position", false),
                            Option.bool("dimension", "Dimension", false),
                            Option.choice("decimals", "Decimals", "0", "0", "1", "2"))),
            new Module("armor", "Armor Status", "Your armor and held item with their durability.",
                    Category.HUD, Items.DIAMOND_CHESTPLATE, true, true, List.of(
                            Option.bool("held", "Held item", true),
                            Option.choice("durability", "Durability", "Value", "Value", "Percent", "Hidden"),
                            Option.choice("layout", "Layout", "Vertical", "Vertical", "Horizontal"))),
            new Module("potions", "Potion Effects", "Active effects with time left.",
                    Category.HUD, Items.BREWING_STAND, true, true, List.of(Option.bool("icons", "Icons", true), Option.bool("kind", "Green for good, red for bad", false), Option.bool("blink", "Blink when ending", true))),
            new Module("reach", "Reach Display", "How far away your last hit landed, in blocks.",
                    Category.PVP, Items.DIAMOND_SWORD, true, false, List.of(Option.choice("decimals", "Decimals", "2", "1", "2", "3"), Option.bool("rangecolor", "Colour by distance", false))),
            new Module("combo", "Combo Counter", "Hits in a row without being hit back.",
                    Category.PVP, Items.FIRE_CHARGE, true, false, List.of(Option.bool("hidezero", "Hide at zero", false), Option.bool("best", "Best combo", false))),
            new Module("sprint", "Toggle Sprint", "Sprint stays on after you press it once.",
                    Category.PVP, Items.RABBIT_FOOT, true, true, List.of(
                            Option.bool("sneak", "Toggle sneak too", false))),
            new Module("nohurtcam", "No Hurt Cam", "Stops the screen shaking when you take damage.",
                    Category.PVP, Items.SHIELD, false, false, List.of()),
            new Module("zoom", "Zoom", "Hold C to zoom. Scroll while zooming to change how far.",
                    Category.UTILITY, Items.SPYGLASS, false, true, List.of(
                            Option.slider("level", "Zoom", 4.0, 2.0, 10.0, 0.5),
                            Option.bool("smooth", "Smooth camera", true),
                            Option.bool("scroll", "Scroll to adjust", true),
                            Option.bool("toggle", "Press to toggle", false),
                            Option.bool("animate", "Animated zoom", true),
                            Option.bool("hidehud", "Hide HUD while zooming", false))),
            new Module("fullbright", "Fullbright", "See in the dark without night vision.",
                    Category.UTILITY, Items.GLOWSTONE, false, false, List.of()),
            new Module("speed", "Speedometer", "How fast you're moving.",
                    Category.UTILITY, Items.SUGAR, true, false, List.of(
                            Option.choice("unit", "Unit", "m/s", "m/s", "km/h", "blocks/s"),
                            Option.bool("vertical", "Include up and down", false))),
            new Module("clock", "Clock", "Your computer's time.",
                    Category.UTILITY, Items.CLOCK, true, false, List.of(
                            Option.bool("h24", "24-hour", true),
                            Option.bool("seconds", "Seconds", false),
                            Option.bool("date", "Date", false),
                            Option.bool("weekday", "Day of the week", false))),
            new Module("direction", "Direction", "A compass strip at the top of the screen.",
                    Category.HUD, Items.RECOVERY_COMPASS, true, false, List.of(Option.bool("degrees", "Degrees", false), Option.slider("width", "Width", 150, 100, 260, 10))),
            new Module("target", "Target HUD", "Health and distance of what you're fighting.",
                    Category.PVP, Items.GOLDEN_APPLE, true, true, List.of(Option.bool("armor", "Their armor", true), Option.bool("hearts", "Health as hearts", false))),
            new Module("items", "Item Counter", "How many arrows, pearls, golden apples and totems you carry.",
                    Category.PVP, Items.ENDER_PEARL, true, false, List.of(Option.bool("hidezero", "Hide items you have none of", false))),
            new Module("memory", "Memory", "How much memory the game is using.",
                    Category.UTILITY, Items.REDSTONE, true, false, List.of(Option.bool("percent", "Percent only", false), Option.bool("usagecolor", "Colour by usage", true))),
            new Module("server", "Server Address", "The server you're on.",
                    Category.UTILITY, Items.OAK_SIGN, true, false, List.of()),
            new Module("day", "Day Counter", "Days passed in this world.",
                    Category.UTILITY, Items.SUNFLOWER, true, false, List.of(Option.bool("time", "Time of day", false))),
            new Module("saturation", "Saturation", "Hidden hunger saturation.",
                    Category.HUD, Items.COOKED_BEEF, true, false, List.of()),
            new Module("crosshair", "Crosshair", "Your own crosshair shape, size and colour.",
                    Category.PVP, Items.BOW, false, false, List.of(
                            Option.choice("style", "Style", "Cross", "Cross", "Dot", "Plus", "Circle", "T", "X", "Square", "Arrow"),
                            Option.slider("size", "Size", 5, 2, 12, 1),
                            Option.slider("gap", "Gap", 2, 0, 6, 1),
                            Option.slider("thick", "Thickness", 1, 1, 3, 1),
                            Option.choice("color", "Colour", "White", "White", "Accent", "Green", "Red", "Aqua", "Yellow", "Pink", "Rainbow"),
                            Option.bool("outline", "Outline", true),
                            Option.bool("dynamic", "Spread when moving", false),
                            Option.bool("hitcolor", "Red flash on hit", true),
                            Option.bool("center", "Centre dot", false))),
            new Module("freelook", "Freelook", "Hold Left Alt to look around without turning.",
                    Category.UTILITY, Items.ENDER_EYE, false, true, List.of(Option.bool("toggle", "Press to toggle", false))),
            new Module("lowfire", "Low Fire", "Moves the fire overlay down so you can see.",
                    Category.PVP, Items.FLINT_AND_STEEL, false, true, List.of()),
            new Module("timestamps", "Chat Timestamps", "Adds the time to every chat message.",
                    Category.UTILITY, Items.WRITABLE_BOOK, false, false, List.of(
                            Option.bool("h24", "24-hour", true),
                            Option.bool("seconds", "Seconds", false))),
            new Module("timechanger", "Time Changer", "Pick the time of day. Only your screen changes.",
                    Category.UTILITY, Items.DAYLIGHT_DETECTOR, false, false, List.of(
                            Option.choice("time", "Time", "Day", "Sunrise", "Day", "Sunset", "Night", "Midnight", "Custom"),
                            Option.slider("hour", "Custom hour", 12, 0, 23, 1))),
            new Module("weather", "Clear Weather", "Hides rain, snow and thunder. Only your screen changes.",
                    Category.UTILITY, Items.WATER_BUCKET, false, false, List.of()),
            new Module("blockoutline", "Block Outline", "Your own colour for the box around the block you look at.",
                    Category.UTILITY, Items.TINTED_GLASS, false, false, List.of(
                            Option.choice("color", "Colour", "Accent", "Accent", "White", "Black", "Red", "Aqua", "Green", "Rainbow"),
                            Option.slider("opacity", "Opacity", 0.8, 0.2, 1.0, 0.05),
                            Option.bool("pulse", "Pulse", false))),
            new Module("particles", "Hit Particles", "Crit and sharpness sparks on every hit, so you see each one land.",
                    Category.PVP, Items.NETHER_STAR, false, false, List.of(
                            Option.bool("crits", "Always crits", true),
                            Option.bool("sharp", "Always sharpness", true),
                            Option.slider("amount", "Amount", 1, 1, 5, 1))),
            new Module("nametag", "Own Nametag", "See your own name above your head in third person.",
                    Category.UTILITY, Items.NAME_TAG, false, false, List.of()),
            new Module("bossbar", "Hide Boss Bars", "Removes boss health bars from the top of the screen.",
                    Category.HUD, Items.DRAGON_HEAD, false, false, List.of()),
            new Module("scoreboard", "Hide Scoreboard", "Removes the scoreboard from the side of the screen.",
                    Category.HUD, Items.ITEM_FRAME, false, false, List.of()),
            new Module("session", "Session Timer", "How long you've been playing since you joined.",
                    Category.HUD, Items.CAKE, true, false, List.of()),
            new Module("pack", "Pack Display", "The resource pack you're using.",
                    Category.HUD, Items.PAINTING, true, false, List.of()),
            new Module("badge", "Vortex Badge", "The VC badge next to Vortex players in the Tab list (singleplayer, LAN and servers with Vortex).",
                    Category.UTILITY, Items.BEACON, false, true, List.of(
                            Option.bool("tab", "In the player list", true),
                            Option.bool("self", "On your own name", true))),
            new Module("light", "Light Level", "Block light where you stand (mobs spawn at 0).",
                    Category.HUD, Items.TORCH, true, false, List.of(Option.bool("sky", "Sky light too", false))),
            new Module("players", "Player Count", "How many players are online.",
                    Category.HUD, Items.PLAYER_HEAD, true, false, List.of()),
            new Module("biome", "Biome", "The biome you're in.",
                    Category.UTILITY, Items.GRASS_BLOCK, true, false, List.of()),
            new Module("rotation", "Yaw and Pitch", "Exactly where you're looking.",
                    Category.UTILITY, Items.ARROW, true, false, List.of()),
            new Module("chunk", "Chunk Position", "The chunk you're in and where inside it.",
                    Category.UTILITY, Items.MAP, true, false, List.of()),
            new Module("entities", "Entity Count", "How many entities are loaded around you.",
                    Category.UTILITY, Items.EGG, true, false, List.of()),
            new Module("durability", "Durability Warning", "Warns you before your tool or armor breaks.",
                    Category.PVP, Items.ANVIL, true, true, List.of(Option.slider("threshold", "Warn below (%)", 10, 5, 50, 5), Option.bool("sound", "Sound when it gets low", true))),
            new Module("deathpos", "Death Coordinates", "Where you last died, so you can get your items back.",
                    Category.UTILITY, Items.SKELETON_SKULL, true, true, List.of(Option.bool("chat", "Say it in chat", true), Option.bool("distance", "Distance to it", true))),
            new Module("autogg", "Auto GG", "Says gg when a game ends on minigame servers.",
                    Category.PVP, Items.EMERALD, false, false, List.of(Option.choice("msg", "Message", "gg", "gg", "GG", "gg wp", "Good game"), Option.slider("delay", "Delay (seconds)", 1, 0.5, 5, 0.5))),
            new Module("snaplook", "Snaplook", "Hold V to see yourself from the front.",
                    Category.UTILITY, Items.GLASS_PANE, false, true, List.of()),
            new Module("bow", "Bow Charge", "How far your bow is drawn.",
                    Category.PVP, Items.CROSSBOW, true, false, List.of(Option.bool("arrows", "Arrows left", true))),
            new Module("fall", "Fall Distance", "How far you're falling, red when it hurts.",
                    Category.UTILITY, Items.FEATHER, true, false, List.of(Option.slider("warn", "Red from (blocks)", 4, 2, 20, 1))),
            new Module("health", "Health", "Your health as a number, with absorption.",
                    Category.HUD, Items.APPLE, true, false, List.of(Option.bool("food", "Hunger too", true))),
            new Module("xp", "Experience", "Your level and how far to the next one.",
                    Category.HUD, Items.EXPERIENCE_BOTTLE, true, false, List.of(Option.bool("points", "Total points", false))),
            new Module("forecast", "Weather", "Clear, rain or thunder.",
                    Category.HUD, Items.LIGHTNING_ROD, true, false, List.of()),
            new Module("moon", "Moon Phase", "Tonight's moon (slimes spawn more at full moon).",
                    Category.HUD, Items.END_ROD, true, false, List.of()),
            // ---- 0.9
            new Module("hudstyle", "HUD Style", "The look of every HUD element: theme, corners, size, spacing and text.",
                    Category.HUD, Items.MAGENTA_GLAZED_TERRACOTTA, false, true, List.of(
                            Option.choice("theme", "Theme", "Glass", "Glass", "Solid", "Minimal", "Outline", "Neon", "Clear"),
                            Option.slider("radius", "Corner radius", 4, 0, 8, 1),
                            Option.slider("globalscale", "Size of everything", 1.0, 0.5, 1.5, 0.05),
                            Option.slider("spacing", "Space between", 3, 0, 10, 1),
                            Option.slider("margin", "Distance from the edge", 4, 0, 20, 1),
                            Option.bool("accentbar", "Accent stripe", false),
                            Option.choice("labelcolor", "Label colour", "Accent", "Accent", "White", "Gray", "Same as text"),
                            Option.choice("textcase", "Text", "Normal", "Normal", "UPPER", "lower"),
                            Option.bool("bold", "Bold text", false),
                            Option.slider("rainbow", "Rainbow speed", 4, 1, 10, 1),
                            Option.bool("hidechat", "Hide while chat is open", false))),
            new Module("armorpts", "Armor Points", "Your armor value, with a bar.",
                    Category.PVP, Items.IRON_CHESTPLATE, true, false, List.of(Option.bool("bar", "Bar", true))),
            new Module("air", "Air Meter", "Breath left under water, in seconds.",
                    Category.UTILITY, Items.HEART_OF_THE_SEA, true, true, List.of()),
            new Module("fire", "Burn Timer", "How long you'll keep burning.",
                    Category.PVP, Items.BLAZE_POWDER, true, false, List.of()),
            new Module("elytra", "Elytra Status", "Durability of the elytra you're wearing.",
                    Category.UTILITY, Items.ELYTRA, true, false, List.of(Option.bool("speed", "Speed while flying", true))),
            new Module("mount", "Mount Health", "Health of the horse (or anything) you're riding.",
                    Category.UTILITY, Items.SADDLE, true, false, List.of()),
            new Module("lookat", "Looking At", "The block or creature you're looking at.",
                    Category.UTILITY, Items.OBSERVER, true, false, List.of(Option.bool("pos", "Its position", false), Option.bool("dist", "Distance", true))),
            new Module("kills", "Kill Counter", "Kills this session.",
                    Category.PVP, Items.IRON_SWORD, true, false, List.of(Option.bool("kdr", "Kill/death ratio", true))),
            new Module("distance", "Distance Travelled", "How far you've gone this session.",
                    Category.UTILITY, Items.LEATHER_BOOTS, true, false, List.of()),
            new Module("fpsgraph", "FPS Graph", "Your frame rate over the last 20 seconds.",
                    Category.HUD, Items.COMPARATOR, true, false, List.of(Option.bool("stats", "Lowest, average, highest", true))),
            new Module("pinggraph", "Ping Graph", "Your ping over the last minute.",
                    Category.HUD, Items.ECHO_SHARD, true, false, List.of()),
            new Module("nearby", "Nearby Players", "Players around you and how far away they are.",
                    Category.PVP, Items.ZOMBIE_HEAD, true, false, List.of(Option.slider("count", "Show up to", 5, 1, 10, 1), Option.slider("range", "Within (blocks)", 64, 16, 256, 16))),
            new Module("invhud", "Inventory View", "Your whole inventory on screen.",
                    Category.HUD, Items.CHEST, true, false, List.of(Option.bool("hotbar", "Hotbar too", false))),
            new Module("helditem", "Held Item", "Name, count and durability of what's in your hand.",
                    Category.HUD, Items.STICK, true, false, List.of(Option.bool("total", "How many you carry", true))),
            new Module("hunger", "Hunger Warning", "Tells you to eat before you stop sprinting.",
                    Category.UTILITY, Items.BREAD, true, true, List.of(Option.slider("below", "Warn at (hunger)", 8, 2, 18, 1))),
            new Module("attack", "Attack Cooldown", "When your next hit does full damage.",
                    Category.PVP, Items.NETHERITE_SWORD, true, false, List.of(Option.bool("hidefull", "Hide when ready", true))),
            new Module("tabping", "Tab Ping", "Ping in milliseconds next to every name in the Tab list.",
                    Category.UTILITY, Items.REDSTONE_TORCH, false, true, List.of(Option.bool("color", "Colour by quality", true))),
            new Module("mentions", "Chat Mentions", "Highlights messages with your name and plays a sound.",
                    Category.UTILITY, Items.BELL, false, true, List.of(Option.bool("sound", "Sound", true), Option.bool("highlight", "Highlight", true))),
            new Module("copycoords", "Copy Coordinates", "F7 copies where you are, to paste anywhere.",
                    Category.UTILITY, Items.PAPER, false, true, List.of(Option.bool("chat", "Confirm in chat", true))),
            new Module("lowhp", "Low Health Warning", "The screen edges glow red when your health is low.",
                    Category.PVP, Items.REDSTONE_BLOCK, false, true, List.of(Option.slider("below", "Below (hearts)", 4, 1, 10, 0.5), Option.bool("pulse", "Pulse", true))),
            new Module("hitmarker", "Hit Marker", "A quick X on the crosshair when your hit lands.",
                    Category.PVP, Items.FLINT, false, false, List.of(Option.bool("killred", "Red on a kill", true), Option.slider("size", "Size", 4, 2, 8, 1))),
            new Module("killsound", "Kill Sound", "A sound when you get a kill.",
                    Category.PVP, Items.NOTE_BLOCK, false, false, List.of(Option.choice("sound", "Sound", "Ding", "Ding", "Level up", "Orb"))),
            new Module("autorespawn", "Auto Respawn", "Respawns you straight away when you die.",
                    Category.UTILITY, Items.RESPAWN_ANCHOR, false, false, List.of()),
            // ---- 0.10
            new Module("music", "Music", "What's playing in the launcher's music player, with live lyrics. F8 plays or pauses, F9 skips.",
                    Category.HUD, Items.MUSIC_DISC_CAT, true, true, List.of(
                            Option.bool("lyrics", "Live lyrics", true),
                            Option.bool("nextline", "Next line too", true),
                            Option.bool("bar", "Progress bar", true),
                            Option.bool("artist", "Artist", true))),
            new Module("nickhider", "Nick Hider", "Shows another name instead of yours in chat and the Tab list. Only on your screen.",
                    Category.UTILITY, Items.LEATHER_HELMET, false, false, List.of(
                            Option.choice("name", "Show as", "You", "You", "Me", "Player", "Hidden"))),
            new Module("hitboxes", "Hitboxes", "Boxes around players and mobs, so you see exactly where to hit (F3+B).",
                    Category.PVP, Items.ARMOR_STAND, false, false, List.of()));

    /** The module with this id, or null. */
    public static Module find(String id) {
        for (Module m : ALL) if (m.id.equals(id)) return m;
        return null;
    }

    public static Module get(String id) {
        for (Module m : ALL) if (m.id.equals(id)) return m;
        throw new IllegalArgumentException("unknown module " + id);
    }
}
