package net.vortexrealm.survival.trade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;

/**
 * 3.13.13: turns what a player types into an item, even with typos and nicknames: "dimond" -> diamond,
 * "netherit sword" -> netherite sword, "god apple" -> enchanted golden apple, "pearls" -> ender pearl.
 */
public final class ItemMatcher {
    private static final Map<String, Material> ALIASES = new HashMap<>();
    private static final List<Material> ITEMS = new ArrayList<>();
    private static final List<String> NAMES = new ArrayList<>();

    static {
        alias(Material.ENCHANTED_GOLDEN_APPLE, "god apple", "god apples", "gods", "notch apple", "enchanted apple", "egap", "egapple", "opple");
        alias(Material.GOLDEN_APPLE, "gapple", "gapples", "gap", "gold apple", "golden apple", "goldenapple");
        alias(Material.ENDER_PEARL, "pearl", "pearls", "epearl", "ender pearl", "enderpearl");
        alias(Material.TOTEM_OF_UNDYING, "totem", "totems", "totem of undying");
        alias(Material.EXPERIENCE_BOTTLE, "xp", "xp bottle", "xp bottles", "exp bottle", "bottle o enchanting", "exp");
        alias(Material.DIAMOND, "dia", "dias", "diamonds", "dimond", "diamons", "daimond");
        alias(Material.NETHERITE_INGOT, "netherite", "neth", "netherite ingot", "neth ingot", "nethrite");
        alias(Material.NETHERITE_SCRAP, "scrap", "scraps", "netherite scrap", "ancient debris scrap");
        alias(Material.ANCIENT_DEBRIS, "debris", "ancient debris");
        alias(Material.IRON_INGOT, "iron", "irons", "iron ingot");
        alias(Material.GOLD_INGOT, "gold", "gold ingot", "golds");
        alias(Material.EMERALD, "emeralds", "ems", "emerald");
        alias(Material.OBSIDIAN, "obby", "obsi", "obsidian");
        alias(Material.END_CRYSTAL, "crystal", "crystals", "end crystal", "cristal");
        alias(Material.RESPAWN_ANCHOR, "anchor", "anchors", "respawn anchor");
        alias(Material.GLOWSTONE, "glowstone", "glow stone");
        alias(Material.FIREWORK_ROCKET, "rocket", "rockets", "firework", "fireworks");
        alias(Material.ELYTRA, "elytra", "elytras", "wings", "ely");
        alias(Material.SHULKER_BOX, "shulker", "shulkers", "shulker box", "shulk");
        alias(Material.SHULKER_SHELL, "shulker shell", "shells", "shell");
        alias(Material.ENCHANTED_BOOK, "book", "ench book", "enchanted book", "mending book", "mending");
        alias(Material.COBWEB, "web", "webs", "cobweb");
        alias(Material.TNT, "tnt", "dynamite");
        alias(Material.TRIDENT, "trident", "trident");
        alias(Material.BREEZE_ROD, "breeze rod", "breeze rods");
        alias(Material.WIND_CHARGE, "wind charge", "wind charges", "charges");
        alias(Material.HEAVY_CORE, "heavy core", "core");
        alias(Material.MACE, "mace", "maces");
        alias(Material.GOAT_HORN, "goat horn", "horn");
        alias(Material.SPLASH_POTION, "splash", "pot", "pots", "splash potion");
        alias(Material.POTION, "potion", "potions");
        alias(Material.COOKED_BEEF, "steak", "steaks", "beef");
        alias(Material.GOLDEN_CARROT, "carrots", "golden carrot", "gold carrot", "gcarrot");
        alias(Material.OAK_LOG, "log", "logs", "wood");
        alias(Material.COBBLESTONE, "cobble", "cobblestone");
        alias(Material.SPAWNER, "spawner", "spawners");
        alias(Material.BEACON, "beacon", "beacons");
        alias(Material.NETHER_STAR, "star", "nether star");
        alias(Material.DRAGON_EGG, "egg", "dragon egg");
        alias(Material.SADDLE, "saddle");
        alias(Material.NAME_TAG, "nametag", "name tag", "tag");
        for (Material m : Material.values()) {
            if (usable(m)) {
                ITEMS.add(m);
                NAMES.add(norm(m.name()));
            }
        }
    }

    private ItemMatcher() {
    }

    private static boolean usable(Material m) {
        if (m.isLegacy() || m.name().endsWith("_AIR") || m == Material.AIR) {
            return false;
        }

        try {
            return m.isItem();
        } catch (Throwable noServer) {
            // unit tests run without a server registry: accept everything that isn't obviously a block-only name
            return !m.name().startsWith("POTTED_") && !m.name().endsWith("_WALL_SIGN") && !m.name().endsWith("_WALL_TORCH");
        }
    }

    private static void alias(Material m, String... names) {
        for (String n : names) {
            ALIASES.put(norm(n), m);
        }
    }

    /** lower case, letters and digits only, words joined by single spaces. */
    static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("^minecraft:", "").replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** The item the text means, or null when nothing is close enough. */
    public static Material find(String text) {
        String q = norm(text);
        if (q.isEmpty()) {
            return null;
        }

        Material m = ALIASES.get(q);
        if (m != null) {
            return m;
        }

        for (String variant : variants(q)) {
            m = ALIASES.get(variant);
            if (m != null) {
                return m;
            }

            int i = NAMES.indexOf(variant);
            if (i >= 0) {
                return ITEMS.get(i);
            }
        }

        // closest name by edit distance (typos), short names win ties
        Material best = null;
        int bestD = Integer.MAX_VALUE;
        for (int i = 0; i < NAMES.size(); i++) {
            String n = NAMES.get(i);
            if (Math.abs(n.length() - q.length()) > 4) {
                continue;
            }

            int d = distance(q, n);
            if (d < bestD || d == bestD && best != null && n.length() < norm(best.name()).length()) {
                bestD = d;
                best = ITEMS.get(i);
            }
        }

        for (Map.Entry<String, Material> e : ALIASES.entrySet()) {
            int d = distance(q, e.getKey());
            if (d < bestD) {
                bestD = d;
                best = e.getValue();
            }
        }

        int allowed = q.length() <= 4 ? 1 : (q.length() <= 8 ? 2 : 3);
        return bestD <= allowed ? best : null;
    }

    /** plural and spacing variants: "pearls" -> "pearl", "netheritesword" stays, "diamond swords" -> "diamond sword". */
    private static List<String> variants(String q) {
        List<String> out = new ArrayList<>();
        out.add(q);
        if (q.endsWith("es")) {
            out.add(q.substring(0, q.length() - 2));
        }

        if (q.endsWith("s")) {
            out.add(q.substring(0, q.length() - 1));
        }

        out.add(q.replace(" ", ""));
        return out;
    }

    static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }

            int[] t = prev;
            prev = cur;
            cur = t;
        }

        return prev[b.length()];
    }
}
