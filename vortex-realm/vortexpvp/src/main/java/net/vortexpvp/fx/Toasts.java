package net.vortexpvp.fx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.logging.Level;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.kit.Kit;
import net.vortexpvp.rank.Tier;
import net.vortexpvp.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class Toasts {
    private static Plugin plugin;
    private static final Map<String, String> PENDING = new LinkedHashMap<>();
    private static boolean waiting;
    private static int adIndex;
    private static final Map<UUID, Integer> WINS = new HashMap<>();

    private Toasts() {
    }

    public static void init(Plugin p, Collection<Kit> kits) {
        plugin = p;
        Map<String, String> all = new LinkedHashMap<>();
        all.put("win", json("minecraft:diamond_sword", "Victory!", "#FFD54A", "You won the duel", "challenge"));
        all.put("ranked_win", json("minecraft:netherite_sword", "Ranked Victory!", "#FFAA00", "Rank points earned", "challenge"));
        all.put("streak", json("minecraft:blaze_powder", "On Fire!", "#FF6B35", "Win streak", "goal"));

        for (Kit k : kits) {
            Material icon = k.iconStack().getType();
            all.put("win_" + id(k.id), json(icon.getKey().toString(), "Victory!", "#FFD54A", "You won a " + k.displayName + " duel", "challenge"));
        }

        for (Tier t : Tier.values()) {
            String icon = switch (t.number()) {
                case 1 -> "minecraft:nether_star";
                case 2 -> "minecraft:amethyst_shard";
                case 3 -> "minecraft:diamond";
                case 4 -> "minecraft:emerald";
                default -> "minecraft:iron_ingot";
            };
            all.put("rankup_" + t.name().toLowerCase(Locale.ROOT), json(icon, "RANK UP! " + t.label, t.color, "You reached " + t.longName(), "challenge"));
            all.put("placed_" + t.name().toLowerCase(Locale.ROOT), json(icon, "Placed: " + t.label, t.color, "Placements done - your first rank!", "challenge"));
        }

        List<String> ads = Cfg.get(Opt.AD_TOASTS);

        for (int i = 0; i < ads.size(); i++) {
            String[] parts = ads.get(i).split("\\|");
            String icon = parts.length > 2 ? parts[2].trim() : "minecraft:paper";
            all.put(
                "ad_" + i + "_" + Integer.toHexString(ads.get(i).hashCode()),
                json(icon, parts[0].trim(), "#B388FF", parts.length > 1 ? parts[1].trim() : "", "goal")
            );
        }

        for (Entry<String, String> e : all.entrySet()) {
            if (Bukkit.getAdvancement(key(e.getKey())) == null) {
                PENDING.put(e.getKey(), e.getValue());
            }
        }

        flush();
    }

    private static String id(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

    private static NamespacedKey key(String id) {
        return new NamespacedKey(plugin, "toast/" + id);
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String json(String icon, String title, String color, String desc, String frame) {
        return "{\"display\":{\"icon\":{\"id\":\""
            + icon
            + "\",\"count\":1},\"title\":{\"text\":\""
            + esc(title)
            + "\",\"color\":\""
            + color
            + "\",\"bold\":true},\"description\":{\"text\":\""
            + esc(desc)
            + "\",\"color\":\"gray\"},\"frame\":\""
            + frame
            + "\",\"show_toast\":true,\"announce_to_chat\":false,\"hidden\":true},\"criteria\":{\"t\":{\"trigger\":\"minecraft:impossible\"}}}";
    }

    private static void flush() {
        if (!PENDING.isEmpty()) {
            if (!Bukkit.getOnlinePlayers().isEmpty()) {
                if (!waiting) {
                    waiting = true;
                    Tasks.timer(1200L, 1200L, () -> {
                        if (Bukkit.getOnlinePlayers().isEmpty() && !PENDING.isEmpty()) {
                            flush();
                        }
                    });
                }
            } else {
                List<String> done = new ArrayList<>();

                for (Entry<String, String> e : PENDING.entrySet()) {
                    try {
                        Bukkit.getUnsafe().loadAdvancement(key(e.getKey()), e.getValue());
                        done.add(e.getKey());
                    } catch (Throwable var4) {
                        plugin.getLogger().log(Level.FINE, "Toast " + e.getKey() + " not loaded", var4);
                        done.add(e.getKey());
                    }
                }

                done.forEach(PENDING::remove);
            }
        }
    }

    public static void show(Player p, String id) {
        if (plugin != null && p != null && Cfg.bool(Opt.TOASTS)) {
            Advancement a = Bukkit.getAdvancement(key(id(id)));
            if (a != null) {
                AdvancementProgress pr = p.getAdvancementProgress(a);

                for (String c : pr.getRemainingCriteria()) {
                    pr.awardCriteria(c);
                }

                Tasks.later(20L, () -> {
                    if (p.isOnline()) {
                        AdvancementProgress back = p.getAdvancementProgress(a);

                        for (String cx : back.getAwardedCriteria()) {
                            back.revokeCriteria(cx);
                        }
                    }
                });
            }
        }
    }

    public static void ad(Player p) {
        List<String> ads = Cfg.get(Opt.AD_TOASTS);
        if (!ads.isEmpty()) {
            int i = Math.floorMod(adIndex++, ads.size());
            show(p, "ad_" + i + "_" + Integer.toHexString(ads.get(i).hashCode()));
        }
    }

    public static void afterWin(Player p) {
        int every = Cfg.i(Opt.AD_TOAST_WINS);
        if (every > 0) {
            int n = WINS.merge(p.getUniqueId(), 1, Integer::sum);
            if (n % every == 0) {
                Tasks.later(80L, () -> {
                    if (p.isOnline()) {
                        ad(p);
                    }
                });
            }
        }
    }

    public static void win(Player p, Kit kit, boolean ranked) {
        afterWin(p);
        if (ranked) {
            show(p, "ranked_win");
        } else if (kit != null && Bukkit.getAdvancement(key("win_" + id(kit.id))) != null) {
            show(p, "win_" + id(kit.id));
        } else {
            show(p, "win");
        }
    }

    public static void rankUp(Player p, Tier to, boolean placed) {
        if (to != null) {
            show(p, (placed ? "placed_" : "rankup_") + to.name().toLowerCase(Locale.ROOT));
        }
    }
}
