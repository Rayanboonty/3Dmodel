package net.vortexsmp.util;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.Sound.Emitter;
import net.kyori.adventure.sound.Sound.Source;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class Snd {
    public static final String CLICK = "ui.button.click";
    public static final String PLING = "block.note_block.pling";
    public static final String HAT = "block.note_block.hat";
    public static final String BELL = "block.note_block.bell";
    public static final String CHIME = "block.note_block.chime";
    public static final String LEVELUP = "entity.player.levelup";
    public static final String ORB = "entity.experience_orb.pickup";
    public static final String CHALLENGE = "ui.toast.challenge_complete";
    public static final String BASS = "block.note_block.bass";
    public static final String ERROR = "entity.villager.no";
    public static final String DRAGON = "entity.ender_dragon.growl";
    public static final String WITHER_SPAWN = "entity.wither.spawn";
    public static final String HORN = "item.goat_horn.sound.0";
    public static final String BEACON_ON = "block.beacon.activate";
    public static final String BEACON_AMBIENT = "block.beacon.ambient";
    public static final String PISTON_OUT = "block.piston.extend";
    public static final String PISTON_IN = "block.piston.contract";
    public static final String FENCE_OPEN = "block.fence_gate.open";
    public static final String IRON_DOOR = "block.iron_door.open";
    public static final String FIREWORK = "entity.firework_rocket.blast";
    public static final String TWINKLE = "entity.firework_rocket.twinkle";
    public static final String ARROW_HIT = "entity.arrow.hit_player";
    public static final String ANVIL = "block.anvil.land";
    public static final String TOTEM = "item.totem.use";
    public static final String POP = "entity.item.pickup";
    public static final String AMETHYST = "block.amethyst_block.chime";
    public static final String DEFEAT = "entity.blaze.death";

    private Snd() {
    }

    public static void play(Player p, String key, float volume, float pitch) {
        if (p != null) {
            p.playSound(Sound.sound(Key.key(key), Source.MASTER, volume, pitch), Emitter.self());
        }
    }

    public static void play(Player p, String key) {
        play(p, key, 1.0F, 1.0F);
    }

    public static void at(Location loc, String key, float volume, float pitch) {
        if (loc != null && loc.getWorld() != null) {
            loc.getWorld().playSound(Sound.sound(Key.key(key), Source.PLAYER, volume, pitch), loc.getX(), loc.getY(), loc.getZ());
        }
    }

    public static void broadcast(String key, float volume, float pitch) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            play(p, key, volume, pitch);
        }
    }

    public static void click(Player p) {
        play(p, "ui.button.click", 0.6F, 1.4F);
    }

    public static void error(Player p) {
        play(p, "entity.villager.no", 0.8F, 1.0F);
    }
}
