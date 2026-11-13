package net.vortexpvp.npc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.TextDisplay;

public final class Npc {
    public static final List<String> MENUS = List.of(
        "duels", "ranked", "ffa", "leaderboard", "settings", "spectate", "kiteditor", "party", "worlds", "events", "stats", "welcome"
    );
    public static final List<String> PARTICLES = List.of("none", "ring", "hearts", "flames", "portal", "enchant", "end_rod", "notes", "soul");
    public static final List<String> GLOWS = List.of("none", "white", "gold", "yellow", "aqua", "green", "red", "light_purple", "blue");
    public final String id;
    public Location loc;
    public String skin = "";
    public final List<String> lines = new ArrayList<>();
    public Npc.Action action = Npc.Action.NONE;
    public String value = "";
    public boolean lookAt = true;
    public String glow = "none";
    public Material hand;
    public String particle = "none";
    public boolean sneak;
    public boolean floatItem = true;
    public boolean bar = true;
    // ---- look
    /** Armor and off-hand (null = none). */
    public org.bukkit.inventory.ItemStack helmet;
    public org.bukkit.inventory.ItemStack chest;
    public org.bukkit.inventory.ItemStack legs;
    public org.bukkit.inventory.ItemStack boots;
    public org.bukkit.inventory.ItemStack offhand;
    /** Size, 0.5 - 2.0. */
    public double scale = 1.0;
    public Npc.Pose pose = Npc.Pose.STAND;
    /** A skin copied from a player (textures value + signature): works without looking the name up online. */
    public String skinValue = "";
    public String skinSignature = "";
    public Npc.Idle idle = Npc.Idle.NONE;
    // ---- label
    public Npc.LabelStyle labelStyle = Npc.LabelStyle.CLASSIC;
    /** Label background, #RRGGBB (empty = the style's own). */
    public String labelColor = "";
    /** Background opacity 0-255 (-1 = the style's own). */
    public int labelAlpha = -1;
    public double labelScale = 1.0;
    /** Extra height above the head, in blocks. */
    public double labelHeight = 0.0;
    public boolean hideLabel;
    /** The first line gets a moving gradient. */
    public boolean animatedTitle;
    public String titleColors = "#FFB547:#FF4A1C";
    // ---- behaviour
    /** Floating item over the label (null = the held item). */
    public Material floatMaterial;
    public String clickSound = "ui.button.click";
    public int cooldownMs = 700;
    /** Permission needed to use the NPC (empty = everyone). */
    public String permission = "";
    /** Action bar shown to players walking up (empty = "Click to ..."; "none" = nothing). */
    public String greetText = "";
    public int greetRadius = 4;
    String rawLocation;
    transient Mannequin body;
    transient TextDisplay label;
    transient ItemDisplay floater;
    transient double barShown = -1.0;
    final transient Map<UUID, Long> greeted = new HashMap<>();

    public Npc(String id, Location loc) {
        this.id = id;
        this.loc = loc;
    }

    public String describe() {
        return switch (this.action) {
            case WORLD -> "Travel to " + this.value;
            default -> this.action.label;
            case MENU -> "Open " + this.value;
            case COMMAND -> "/" + this.value;
            case CONSOLE -> "Console: " + this.value;
            case MESSAGE -> "Message";
        };
    }

    public static enum Pose {
        STAND("Standing", org.bukkit.entity.Pose.STANDING),
        SNEAK("Sneaking", org.bukkit.entity.Pose.SNEAKING),
        SWIM("Swimming / lying", org.bukkit.entity.Pose.SWIMMING),
        SLEEP("Sleeping", org.bukkit.entity.Pose.SLEEPING),
        GLIDE("Gliding (elytra)", org.bukkit.entity.Pose.FALL_FLYING);

        public final String label;
        public final org.bukkit.entity.Pose bukkit;

        private Pose(String label, org.bukkit.entity.Pose bukkit) {
            this.label = label;
            this.bukkit = bukkit;
        }

        /** Label height for this pose at size 1. */
        public double head() {
            return switch (this) {
                case STAND -> 2.2;
                case SNEAK -> 1.9;
                default -> 1.1;
            };
        }
    }

    public static enum Idle {
        NONE("Still"),
        WAVE("Waves now and then"),
        LOOK_AROUND("Looks around"),
        SPIN("Turns slowly"),
        CHEER("Cheers (jumps and swings)");

        public final String label;

        private Idle(String label) {
            this.label = label;
        }
    }

    /** Name tag looks, shared with the hologram designs. */
    public static enum LabelStyle {
        CLASSIC("Classic", 90, 0x000000),
        GLASS("Glass card", 170, 0x090C18),
        NEON("Neon", 120, 0x00E5FF),
        EMBER("Ember", 150, 0x2A0F05),
        HOLO("Hologram", 70, 0x0A3A4A),
        NONE("No background", 0, 0x000000);

        public final String label;
        public final int alpha;
        public final int rgb;

        private LabelStyle(String label, int alpha, int rgb) {
            this.label = label;
            this.alpha = alpha;
            this.rgb = rgb;
        }
    }

    public static enum Action {
        NONE("Nothing"),
        WORLD("Travel to a world"),
        LOBBY("PvP practice lobby"),
        HUB("Hub"),
        SELECTOR("World selector"),
        MENU("Open a menu"),
        COMMAND("Player runs a command"),
        CONSOLE("Console runs a command"),
        MESSAGE("Send a message");

        public final String label;

        private Action(String label) {
            this.label = label;
        }
    }
}
