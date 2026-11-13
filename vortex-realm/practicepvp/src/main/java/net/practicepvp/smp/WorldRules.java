package net.practicepvp.smp;

import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.World;

final class WorldRules {
    private WorldRules() {
    }

    static void applyDefaults(World w, WorldMode mode) {
        switch (mode) {
            case HUB:
                w.setDifficulty(Difficulty.PEACEFUL);
                w.setGameRule(GameRules.PVP, true);
                w.setGameRule(GameRules.KEEP_INVENTORY, true);
                w.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
                w.setGameRule(GameRules.SPAWN_MOBS, false);
                w.setGameRule(GameRules.SPAWN_MONSTERS, false);
                w.setGameRule(GameRules.ADVANCE_TIME, false);
                w.setGameRule(GameRules.ADVANCE_WEATHER, false);
                w.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
                w.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
                w.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
                w.setTime(6000L);
                w.setStorm(false);
                break;
            case LIFESTEAL:
            case STRENGTH:
            case RANKED:
                w.setDifficulty(Difficulty.HARD);
                w.setGameRule(GameRules.PVP, true);
                w.setGameRule(GameRules.KEEP_INVENTORY, false);
                w.setGameRule(GameRules.SHOW_DEATH_MESSAGES, true);
                break;
            case SURVIVAL:
            case ECO:
                w.setDifficulty(Difficulty.NORMAL);
                w.setGameRule(GameRules.PVP, true);
        }
    }
}
