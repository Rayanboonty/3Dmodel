package net.practicepvp.survival.smp;

import java.util.Locale;
import org.bukkit.Material;

public enum WorldMode {
    PVP("PvP Practice", "<gold>", Material.DIAMOND_SWORD, "Duels, ranked, FFA, kits. The PracticePvP lobby lives here."),
    HUB("Hub", "<aqua>", Material.NETHER_STAR, "Protected server spawn: no damage, no building, world selector."),
    SURVIVAL("Survival SMP", "<green>", Material.GRASS_BLOCK, "Normal survival with its own inventory."),
    LIFESTEAL("Lifesteal SMP", "<red>", Material.RED_DYE, "Kill to steal a heart, die and lose one. 0 hearts = out."),
    STRENGTH("Strength SMP", "<light_purple>", Material.AMETHYST_SHARD, "Kills give Strength levels, deaths take them. -5 to +5."),
    RANKED("Ranked SMP", "<yellow>", Material.GOLDEN_SWORD, "Kills earn rank points and tiers LT5 → HT1, shown on names."),
    ECO("Eco SMP", "<#3DDC84>", Material.EMERALD, "Survival with money: /sell, /shop, /pay, mob rewards, /baltop."),
    NONE("Nothing", "<dark_gray>", Material.BARRIER, "Not used: no rules, no own inventory, not in the selector. The plugin leaves this world alone.");

    public final String display;
    public final String color;
    public final Material icon;
    public final String description;

    private WorldMode(String display, String color, Material icon, String description) {
        this.display = display;
        this.color = color;
        this.icon = icon;
        this.description = description;
    }

    public boolean smp() {
        return this == SURVIVAL || this == LIFESTEAL || this == STRENGTH || this == RANKED || this == ECO;
    }

    public String mm() {
        return this.color + this.display;
    }

    public static WorldMode parse(String s, WorldMode def) {
        if (s == null) {
            return def;
        } else {
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException var3) {
                return def;
            }
        }
    }
}
