package net.vortexpvp.kit;

import org.bukkit.Material;

public record KitVote(String id, String question, KitVote.Action action, Material material, int amount) {
    public String serialize() {
        return this.id + "|" + this.question + "|" + this.action.name() + "|" + this.material.name() + "|" + this.amount;
    }

    public static KitVote parse(String s) {
        if (s == null) {
            return null;
        } else {
            String[] p = s.split("\\|");
            if (p.length != 5) {
                return null;
            } else {
                try {
                    Material m = Material.matchMaterial(p[3]);
                    return m == null ? null : new KitVote(p[0], p[1], KitVote.Action.valueOf(p[2]), m, Integer.parseInt(p[4]));
                } catch (IllegalArgumentException var3) {
                    return null;
                }
            }
        }
    }

    public static enum Action {
        LIMIT,
        REMOVE;
    }
}
