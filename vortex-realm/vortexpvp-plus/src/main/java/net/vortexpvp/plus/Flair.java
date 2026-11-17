package net.vortexpvp.plus;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.api.FfaKillEvent;
import net.vortexpvp.api.MatchEndEvent;
import net.vortexpvp.fx.Fx;
import net.vortexpvp.match.MatchTeam;
import net.vortexpvp.session.Session;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * The cosmetics players unlock (missions, the battle pass, achievements): win effects played where you win, styled
 * death messages for the people you beat, and the /vfx screen to pick them, along with pets and gadgets.
 */
public final class Flair implements Listener {
    private final VortexPvP plugin;
    private final PlusModule plus;

    public Flair(VortexPvP plugin, PlusModule plus) {
        this.plugin = plugin;
        this.plus = plus;
    }

    public boolean has(Player p, String key) {
        return p.hasPermission("vortexpvp.cosmetics.all") || this.plus.store().get(p).unlocks.contains(key);
    }

    public String selected(UUID u, String group) {
        PlayerData d = this.plus.store().ifLoaded(u);
        return d == null ? null : d.selected.get(group);
    }

    // ------------------------------------------------------------------ win effects

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnd(MatchEndEvent e) {
        MatchTeam w = e.winner();
        if (w == null || "CANCELLED".equals(e.reason())) {
            return;
        }

        for (UUID u : w.members) {
            Player p = Bukkit.getPlayer(u);
            String fx = this.selected(u, "win");
            if (p != null && fx != null) {
                Location at = p.getLocation();
                Tasks.later(5L, () -> this.play(fx, at));
            }
        }

        // the death message style of the winner, for the people who lost
        MatchTeam l = e.match().otherTeam(w);
        if (l != null && !w.members.isEmpty()) {
            UUID k = w.members.get(0);
            String style = this.selected(k, "death");
            if (style != null) {
                for (UUID v : l.members) {
                    String line = deathLine(style, name(v), name(k));
                    for (Player a : e.match().audience()) {
                        a.sendMessage(Text.mm(line));
                    }
                }
            }
        }
    }

    private static String name(UUID u) {
        String n = Bukkit.getOfflinePlayer(u).getName();
        return n == null ? "?" : n;
    }

    public void play(String fx, Location at) {
        World w = at.getWorld();
        if (w == null) {
            return;
        }

        Location up = at.clone().add(0.0, 1.0, 0.0);
        switch (fx) {
            case "fireworks" -> {
                for (int i = 0; i < 3; i++) {
                    int n = i;
                    Tasks.later(i * 6L, () -> this.firework(at.clone().add((n - 1) * 1.5, 0.0, 0.0)));
                }
            }
            case "lightning" -> {
                w.strikeLightningEffect(at);
                w.spawnParticle(Particle.ELECTRIC_SPARK, up, 60, 0.6, 1.0, 0.6, 0.2);
            }
            case "totem" -> {
                w.spawnParticle(Particle.TOTEM_OF_UNDYING, up, 120, 0.5, 1.0, 0.5, 0.5);
                w.playSound(at, "item.totem.use", 0.6F, 1.2F);
            }
            case "dragon" -> {
                w.spawnParticle(Particle.REVERSE_PORTAL, up, 200, 0.8, 1.2, 0.8, 0.05);
                w.spawnParticle(Particle.PORTAL, up, 120, 0.8, 1.2, 0.8, 0.6);
                w.playSound(at, "entity.ender_dragon.growl", 0.5F, 1.4F);
            }
            case "hearts" -> w.spawnParticle(Particle.HEART, up.clone().add(0.0, 1.0, 0.0), 25, 0.8, 0.6, 0.8, 0.0);
            case "souls" -> {
                w.spawnParticle(Particle.SOUL, up, 60, 0.6, 1.0, 0.6, 0.03);
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, up, 40, 0.5, 0.2, 0.5, 0.02);
                w.playSound(at, "particle.soul_escape", 1.0F, 0.8F);
            }
            case "flames" -> {
                for (int i = 0; i < 40; i++) {
                    double a = i * Math.PI / 10;
                    w.spawnParticle(Particle.FLAME, at.clone().add(Math.cos(a) * 1.2, i * 0.06, Math.sin(a) * 1.2), 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
            case "snow" -> {
                w.spawnParticle(Particle.SNOWFLAKE, up.clone().add(0.0, 1.5, 0.0), 120, 1.5, 0.8, 1.5, 0.02);
                w.playSound(at, "block.powder_snow.break", 1.0F, 1.0F);
            }
            default -> {
            }
        }
    }

    private void firework(Location at) {
        Firework fw = at.getWorld().spawn(at, Firework.class, f -> {
            f.getPersistentDataContainer().set(Fx.FX_FIREWORK, PersistentDataType.BYTE, (byte)1);
            FireworkMeta m = f.getFireworkMeta();
            m.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(Color.fromRGB(0x7B2FF7), Color.fromRGB(0x2FD3F7)).withFade(Color.WHITE).trail(true).build());
            m.setPower(1);
            f.setFireworkMeta(m);
        });
        Tasks.later(18L, fw::detonate);
    }

    // ------------------------------------------------------------------ death messages

    public static String deathLine(String style, String victim, String killer) {
        String v = "<white>" + Text.esc(victim) + "</white>";
        String k = "<white>" + Text.esc(killer) + "</white>";
        return switch (style) {
            case "dramatic" -> "<gray>☠ " + v + " fell dramatically before " + k + "'s blade...";
            case "pirate" -> "<gold>☠ " + v + " walked the plank, courtesy o' Cap'n " + k + "!";
            case "knight" -> "<yellow>⚔ " + v + " was bested in honourable combat by Sir " + k + ".";
            case "robot" -> "<aqua>🤖 " + v + ".exe has stopped working. Cause: " + k + ".";
            case "chef" -> "<red>🍳 " + v + " got cooked by Chef " + k + ". Well done!";
            default -> "<gray>" + v + " was slain by " + k;
        };
    }

    public void ffaDeathMessage(FfaKillEvent e) {
        if (e.killer() == null) {
            return;
        }

        String style = this.selected(e.killer().getUniqueId(), "death");
        if (style == null) {
            return;
        }

        String line = deathLine(style, e.victim().getName(), e.killer().getName());
        for (Player p : Bukkit.getOnlinePlayers()) {
            Session s = this.plugin.sessions().get(p);
            if (s.ffa == e.arena()) {
                p.sendMessage(Text.mm(line));
            }
        }
    }

    // ------------------------------------------------------------------ the picker

    public void menu(Player p, String group) {
        PlayerData d = this.plus.store().get(p);
        String[] all = switch (group) {
            case "death" -> Progression.DEATH_MSG;
            case "pet" -> Progression.PETS;
            case "gadget" -> Progression.GADGETS;
            default -> Progression.WIN_FX;
        };
        String g0 = switch (group) {
            case "death", "pet", "gadget" -> group;
            default -> "win";
        };
        Gui.open(this.plugin, p, "<dark_gray>Cosmetics - " + Progression.nice(g0.equals("win") ? "win effects" : g0 + "s"), 5, g -> {
            String[] groups = {"win", "death", "pet", "gadget"};
            Material[] icons = {Material.FIREWORK_ROCKET, Material.SKELETON_SKULL, Material.BONE, Material.BLAZE_ROD};
            for (int i = 0; i < 4; i++) {
                String gr = groups[i];
                g.item(1 + i * 2, Items.of(icons[i]).name((gr.equals(g0) ? "<green>▶ " : "<yellow>") + Progression.nice(gr.equals("win") ? "win effects" : gr + "s")).glint(gr.equals(g0)).hideAll().build(),
                    c -> this.menu(p, gr));
            }

            List<Integer> slots = new ArrayList<>();
            for (int s = 19; s <= 25; s++) {
                slots.add(s);
            }

            for (int s = 28; s <= 34; s++) {
                slots.add(s);
            }

            String cur = d.selected.get(g0);
            for (int i = 0; i < all.length && i < slots.size(); i++) {
                String v = all[i];
                boolean owned = this.has(p, g0 + ":" + v);
                boolean on = v.equals(cur);
                g.item(slots.get(i), Items.of(owned ? (on ? Material.LIME_DYE : Material.LIGHT_BLUE_DYE) : Material.GRAY_DYE)
                    .name((owned ? "<aqua>" : "<gray>") + Progression.nice(v))
                    .lore(g0.equals("death") ? "<gray>" + Text.plain(Text.mm(deathLine(v, "Enemy", p.getName()))) : "",
                        owned ? (on ? "<green>✔ Selected - click to turn off" : "<yellow>▶ Click to use") : "<red>Locked - unlock it from missions,", owned ? "" : "<red>the battle pass or achievements")
                    .build(), c -> {
                        if (!owned) {
                            Snd.error(p);
                            return;
                        }

                        if (on) {
                            d.selected.remove(g0);
                        } else {
                            d.selected.put(g0, v);
                            if (g0.equals("win")) {
                                this.play(v, p.getLocation());
                            }

                            if (g0.equals("gadget")) {
                                p.sendMessage(Text.mm("<light_purple>Gadget picked: <white>" + Progression.nice(v) + "</white> <gray>- press <white>F</white> (swap hands) in the hub to use it, or <white>/gadget use</white>."));
                            }
                        }

                        d.dirty = true;
                        if (g0.equals("pet") && this.plus.hub() != null) {
                            this.plus.hub().refreshPet(p);
                        }

                        Snd.click(p);
                        this.menu(p, g0);
                    });
            }
        });
    }

    public String selfCheck() {
        return deathLine("robot", "a", "b").contains("exe") ? null : "death line";
    }
}
