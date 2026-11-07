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
            Option.choice("color", "Text color", "White", "White", "Accent", "Aqua", "Gold", "Green", "Pink", "Rainbow"),
            Option.slider("opacity", "Background opacity", 0.45, 0.0, 1.0, 0.05),
            Option.choice("brackets", "Brackets", "None", "None", "[ ]", "( )"));

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
            new Module("keystrokes", "Keystrokes", "Shows your movement keys and mouse buttons as you press them.",
                    Category.HUD, Items.PISTON, true, true, List.of(
                            Option.bool("mouse", "Mouse buttons", true),
                            Option.bool("cps", "Clicks per second on buttons", true),
                            Option.bool("space", "Space bar", true))),
            new Module("cps", "CPS", "Your clicks per second.",
                    Category.HUD, Items.TARGET, true, true, List.of(Option.bool("right", "Show right clicks", true))),
            new Module("fps", "FPS", "Frames per second.",
                    Category.HUD, Items.REPEATER, true, true, List.of(Option.bool("frametime", "Frame time (ms)", false))),
            new Module("ping", "Ping", "Your connection's round trip time to the server.",
                    Category.HUD, Items.SCULK_SENSOR, true, false, List.of(Option.bool("quality", "Colour by quality", true))),
            new Module("coords", "Coordinates", "Where you are and which way you're facing.",
                    Category.HUD, Items.COMPASS, true, true, List.of(
                            Option.bool("facing", "Facing", true),
                            Option.bool("biome", "Biome", false),
                            Option.bool("nether", "Nether / Overworld position", false),
                            Option.choice("decimals", "Decimals", "0", "0", "1", "2"))),
            new Module("armor", "Armor Status", "Your armor and held item with their durability.",
                    Category.HUD, Items.DIAMOND_CHESTPLATE, true, true, List.of(
                            Option.bool("held", "Held item", true),
                            Option.choice("durability", "Durability", "Value", "Value", "Percent", "Hidden"))),
            new Module("potions", "Potion Effects", "Active effects with time left.",
                    Category.HUD, Items.BREWING_STAND, true, true, List.of(Option.bool("icons", "Icons", true))),
            new Module("reach", "Reach Display", "How far away your last hit landed, in blocks.",
                    Category.PVP, Items.DIAMOND_SWORD, true, false, List.of(Option.choice("decimals", "Decimals", "2", "1", "2", "3"))),
            new Module("combo", "Combo Counter", "Hits in a row without being hit back.",
                    Category.PVP, Items.FIRE_CHARGE, true, false, List.of(Option.bool("hidezero", "Hide at zero", false))),
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
                            Option.bool("toggle", "Press to toggle", false))),
            new Module("fullbright", "Fullbright", "See in the dark without night vision.",
                    Category.UTILITY, Items.GLOWSTONE, false, false, List.of()),
            new Module("speed", "Speedometer", "How fast you're moving.",
                    Category.UTILITY, Items.SUGAR, true, false, List.of(
                            Option.choice("unit", "Unit", "m/s", "m/s", "km/h"))),
            new Module("clock", "Clock", "Your computer's time.",
                    Category.UTILITY, Items.CLOCK, true, false, List.of(
                            Option.bool("h24", "24-hour", true),
                            Option.bool("seconds", "Seconds", false),
                            Option.bool("date", "Date", false))),
            new Module("direction", "Direction", "A compass strip at the top of the screen.",
                    Category.HUD, Items.RECOVERY_COMPASS, true, false, List.of(Option.bool("degrees", "Degrees", false))),
            new Module("target", "Target HUD", "Health and distance of what you're fighting.",
                    Category.PVP, Items.GOLDEN_APPLE, true, true, List.of()),
            new Module("items", "Item Counter", "How many arrows, pearls, golden apples and totems you carry.",
                    Category.PVP, Items.ENDER_PEARL, true, false, List.of()),
            new Module("memory", "Memory", "How much memory the game is using.",
                    Category.UTILITY, Items.REDSTONE, true, false, List.of(Option.bool("percent", "Percent only", false))),
            new Module("server", "Server Address", "The server you're on.",
                    Category.UTILITY, Items.OAK_SIGN, true, false, List.of()),
            new Module("day", "Day Counter", "Days passed in this world.",
                    Category.UTILITY, Items.SUNFLOWER, true, false, List.of(Option.bool("time", "Time of day", false))),
            new Module("saturation", "Saturation", "Hidden hunger saturation.",
                    Category.HUD, Items.COOKED_BEEF, true, false, List.of()),
            new Module("crosshair", "Crosshair", "Your own crosshair shape, size and colour.",
                    Category.PVP, Items.BOW, false, false, List.of(
                            Option.choice("style", "Style", "Cross", "Cross", "Dot", "Plus", "Circle"),
                            Option.slider("size", "Size", 5, 2, 12, 1),
                            Option.slider("gap", "Gap", 2, 0, 6, 1),
                            Option.slider("thick", "Thickness", 1, 1, 3, 1),
                            Option.choice("color", "Colour", "White", "White", "Accent", "Green", "Red", "Aqua"),
                            Option.bool("outline", "Outline", true))),
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
                            Option.choice("time", "Time", "Day", "Sunrise", "Day", "Sunset", "Night"))),
            new Module("weather", "Clear Weather", "Hides rain, snow and thunder. Only your screen changes.",
                    Category.UTILITY, Items.WATER_BUCKET, false, false, List.of()),
            new Module("blockoutline", "Block Outline", "Your own colour for the box around the block you look at.",
                    Category.UTILITY, Items.TINTED_GLASS, false, false, List.of(
                            Option.choice("color", "Colour", "Accent", "Accent", "White", "Black", "Red", "Aqua", "Green", "Rainbow"),
                            Option.slider("opacity", "Opacity", 0.8, 0.2, 1.0, 0.05))),
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
            new Module("worldtime", "World Time", "The time of day in the world, as a clock.",
                    Category.UTILITY, Items.LANTERN, true, false, List.of(Option.bool("h24", "24-hour", true))),
            new Module("biome", "Biome", "The biome you're in.",
                    Category.UTILITY, Items.GRASS_BLOCK, true, false, List.of()),
            new Module("rotation", "Yaw and Pitch", "Exactly where you're looking.",
                    Category.UTILITY, Items.ARROW, true, false, List.of()),
            new Module("chunk", "Chunk Position", "The chunk you're in and where inside it.",
                    Category.UTILITY, Items.MAP, true, false, List.of()),
            new Module("entities", "Entity Count", "How many entities are loaded around you.",
                    Category.UTILITY, Items.EGG, true, false, List.of()),
            new Module("durability", "Durability Warning", "Warns you before your tool or armor breaks.",
                    Category.PVP, Items.ANVIL, true, true, List.of(Option.slider("threshold", "Warn below (%)", 10, 5, 50, 5))),
            new Module("deathpos", "Death Coordinates", "Where you last died, so you can get your items back.",
                    Category.UTILITY, Items.SKELETON_SKULL, true, true, List.of(Option.bool("chat", "Say it in chat", true))),
            new Module("autogg", "Auto GG", "Says gg when a game ends on minigame servers.",
                    Category.PVP, Items.EMERALD, false, false, List.of(Option.choice("msg", "Message", "gg", "gg", "GG", "gg wp", "Good game"))),
            new Module("snaplook", "Snaplook", "Hold V to see yourself from the front.",
                    Category.UTILITY, Items.GLASS_PANE, false, true, List.of()),
            new Module("bow", "Bow Charge", "How far your bow is drawn.",
                    Category.PVP, Items.CROSSBOW, true, false, List.of()),
            new Module("fall", "Fall Distance", "How far you're falling, red when it hurts.",
                    Category.UTILITY, Items.FEATHER, true, false, List.of()),
            new Module("health", "Health", "Your health as a number, with absorption.",
                    Category.HUD, Items.APPLE, true, false, List.of(Option.bool("food", "Hunger too", true))),
            new Module("xp", "Experience", "Your level and how far to the next one.",
                    Category.HUD, Items.EXPERIENCE_BOTTLE, true, false, List.of()),
            new Module("forecast", "Weather", "Clear, rain or thunder.",
                    Category.HUD, Items.LIGHTNING_ROD, true, false, List.of()),
            new Module("moon", "Moon Phase", "Tonight's moon (slimes spawn more at full moon).",
                    Category.HUD, Items.END_ROD, true, false, List.of()));

    public static Module get(String id) {
        for (Module m : ALL) if (m.id.equals(id)) return m;
        throw new IllegalArgumentException("unknown module " + id);
    }
}
