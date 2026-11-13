package net.vortexrealm.kit;

import java.util.Locale;

public enum KitCategory {
    SKILL("Skill Kits", "<aqua>", "item/diamond_sword"),
    VANILLA("Vanilla Kits", "<gold>", "item/end_crystal"),
    OTHER("Other Kits", "<light_purple>", null);

    public final String title;
    public final String color;
    public final String sprite;

    private KitCategory(String title, String color, String sprite) {
        this.title = title;
        this.color = color;
        this.sprite = sprite;
    }

    public static KitCategory parse(String s, KitCategory def) {
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
