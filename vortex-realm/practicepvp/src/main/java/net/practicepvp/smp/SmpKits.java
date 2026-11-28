package net.practicepvp.smp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.practicepvp.PracticePlugin;
import net.practicepvp.dialog.LobbyDialogs;
import net.practicepvp.social.Ranks;
import net.practicepvp.util.Icons;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Text;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

/**
 * 3.13.5 - SMP kits by rank: a starter kit once for everyone, then better kits the higher the rank (VIP iron, MVP and
 * Legend enchanted iron, staff and creators diamond, moderators enchanted diamond, owner and admins full netherite).
 * Each kit has its own cooldown. /kit opens the menu: every kit with its item pictures, ready / waiting / locked.
 */
public final class SmpKits implements CommandExecutor, org.bukkit.command.TabCompleter {
    private final PracticePlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    /** id, name, colour gradient, minimum rank weight, cooldown hours (0 = once), icon. */
    enum Def {
        STARTER("Starter", "#B8F5A0:#3DBE4B", 0, 0, Material.WOODEN_PICKAXE, "For new players: tools, food and a bed."),
        VIP("VIP", "#8CFF7A:#2E9B3A", 100, 24, Material.IRON_CHESTPLATE, "Iron armor and tools."),
        MVP("MVP", "#FFC14D:#FF7A00", 200, 24, Material.IRON_SWORD, "Enchanted iron and golden apples."),
        STAFF("Diamond", "#8FD8FF:#3D8BFF", 400, 24, Material.DIAMOND_CHESTPLATE, "Diamond armor and tools (staff, creators, top ranks)."),
        BOOSTER("Booster", "#FF9BEA:#F47FFF", 450, 24, Material.FIREWORK_ROCKET, "Thank you for boosting: enchanted diamond, rockets and an elytra."),
        MOD("Moderator", "#6BFF8C:#1FAF5A", 800, 24, Material.DIAMOND_SWORD, "Enchanted diamond gear."),
        DEV("Developer", "#7FF6FF:#2E9BFF", 850, 24, Material.NETHERITE_PICKAXE, "Builder's kit: netherite tools, diamond armor, blocks and rockets."),
        NETHERITE("Netherite", "#FF9A3D:#7A2E00", 900, 24, Material.NETHERITE_CHESTPLATE, "Full netherite: armor, sword, axe, tools, elytra.");

        final String display;
        final String gradient;
        final int weight;
        final int hours;
        final Material icon;
        final String about;

        Def(String display, String gradient, int weight, int hours, Material icon, String about) {
            this.display = display;
            this.gradient = gradient;
            this.weight = weight;
            this.hours = hours;
            this.icon = icon;
            this.about = about;
        }
    }

    public SmpKits(PracticePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "smp-kits.yml");
        this.data = YamlConfiguration.loadConfiguration(this.file);
    }

    private int weightOf(Player p) {
        if (p.isOp()) {
            return 1000;
        }

        Ranks r = this.plugin.serverRanks();
        Ranks.Rank rank = r == null ? null : r.of(p);
        return rank == null ? 0 : rank.weight;
    }

    private static ItemStack it(Material m, int n) {
        return new ItemStack(m, n);
    }

    private static ItemStack ench(Material m, Object... pairs) {
        ItemStack s = new ItemStack(m);
        s.editMeta(meta -> {
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                meta.addEnchant((Enchantment)pairs[i], (Integer)pairs[i + 1], true);
            }
        });
        return s;
    }

    static List<ItemStack> contents(Def d) {
        List<ItemStack> l = new ArrayList<>();
        switch (d) {
            case STARTER -> {
                l.add(it(Material.STONE_SWORD, 1));
                l.add(it(Material.STONE_PICKAXE, 1));
                l.add(it(Material.STONE_AXE, 1));
                l.add(it(Material.STONE_SHOVEL, 1));
                l.add(it(Material.LEATHER_HELMET, 1));
                l.add(it(Material.LEATHER_CHESTPLATE, 1));
                l.add(it(Material.LEATHER_LEGGINGS, 1));
                l.add(it(Material.LEATHER_BOOTS, 1));
                l.add(it(Material.BREAD, 16));
                l.add(it(Material.TORCH, 32));
                l.add(it(Material.RED_BED, 1));
                l.add(it(Material.OAK_LOG, 16));
            }
            case VIP -> {
                l.add(it(Material.IRON_HELMET, 1));
                l.add(it(Material.IRON_CHESTPLATE, 1));
                l.add(it(Material.IRON_LEGGINGS, 1));
                l.add(it(Material.IRON_BOOTS, 1));
                l.add(it(Material.IRON_SWORD, 1));
                l.add(it(Material.IRON_PICKAXE, 1));
                l.add(it(Material.IRON_AXE, 1));
                l.add(it(Material.COOKED_BEEF, 16));
            }
            case MVP -> {
                l.add(ench(Material.IRON_HELMET, Enchantment.PROTECTION, 2));
                l.add(ench(Material.IRON_CHESTPLATE, Enchantment.PROTECTION, 2));
                l.add(ench(Material.IRON_LEGGINGS, Enchantment.PROTECTION, 2));
                l.add(ench(Material.IRON_BOOTS, Enchantment.PROTECTION, 2));
                l.add(ench(Material.IRON_SWORD, Enchantment.SHARPNESS, 2));
                l.add(ench(Material.IRON_PICKAXE, Enchantment.EFFICIENCY, 2));
                l.add(it(Material.GOLDEN_APPLE, 3));
                l.add(it(Material.COOKED_BEEF, 32));
                l.add(it(Material.EXPERIENCE_BOTTLE, 16));
            }
            case STAFF -> {
                l.add(it(Material.DIAMOND_HELMET, 1));
                l.add(it(Material.DIAMOND_CHESTPLATE, 1));
                l.add(it(Material.DIAMOND_LEGGINGS, 1));
                l.add(it(Material.DIAMOND_BOOTS, 1));
                l.add(it(Material.DIAMOND_SWORD, 1));
                l.add(it(Material.DIAMOND_PICKAXE, 1));
                l.add(it(Material.DIAMOND_AXE, 1));
                l.add(it(Material.DIAMOND_SHOVEL, 1));
                l.add(it(Material.GOLDEN_APPLE, 4));
                l.add(it(Material.COOKED_BEEF, 32));
            }
            case MOD -> {
                l.add(ench(Material.DIAMOND_HELMET, Enchantment.PROTECTION, 3, Enchantment.UNBREAKING, 3));
                l.add(ench(Material.DIAMOND_CHESTPLATE, Enchantment.PROTECTION, 3, Enchantment.UNBREAKING, 3));
                l.add(ench(Material.DIAMOND_LEGGINGS, Enchantment.PROTECTION, 3, Enchantment.UNBREAKING, 3));
                l.add(ench(Material.DIAMOND_BOOTS, Enchantment.PROTECTION, 3, Enchantment.FEATHER_FALLING, 3, Enchantment.UNBREAKING, 3));
                l.add(ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 4, Enchantment.UNBREAKING, 3));
                l.add(ench(Material.DIAMOND_PICKAXE, Enchantment.EFFICIENCY, 4, Enchantment.UNBREAKING, 3));
                l.add(ench(Material.DIAMOND_AXE, Enchantment.EFFICIENCY, 4, Enchantment.UNBREAKING, 3));
                l.add(ench(Material.BOW, Enchantment.POWER, 3));
                l.add(it(Material.ARROW, 64));
                l.add(it(Material.GOLDEN_APPLE, 6));
                l.add(it(Material.COOKED_BEEF, 64));
            }
            case BOOSTER -> {
                l.add(ench(Material.DIAMOND_HELMET, Enchantment.PROTECTION, 2, Enchantment.UNBREAKING, 2));
                l.add(ench(Material.DIAMOND_CHESTPLATE, Enchantment.PROTECTION, 2, Enchantment.UNBREAKING, 2));
                l.add(ench(Material.DIAMOND_LEGGINGS, Enchantment.PROTECTION, 2, Enchantment.UNBREAKING, 2));
                l.add(ench(Material.DIAMOND_BOOTS, Enchantment.PROTECTION, 2, Enchantment.FEATHER_FALLING, 2, Enchantment.UNBREAKING, 2));
                l.add(ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 3, Enchantment.UNBREAKING, 2));
                l.add(ench(Material.DIAMOND_PICKAXE, Enchantment.EFFICIENCY, 3, Enchantment.UNBREAKING, 2));
                l.add(ench(Material.ELYTRA, Enchantment.UNBREAKING, 2));
                l.add(it(Material.FIREWORK_ROCKET, 32));
                l.add(it(Material.GOLDEN_APPLE, 5));
                l.add(it(Material.COOKED_BEEF, 48));
                l.add(it(Material.PINK_SHULKER_BOX, 1));
            }
            case DEV -> {
                l.add(ench(Material.DIAMOND_HELMET, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3));
                l.add(ench(Material.DIAMOND_CHESTPLATE, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3));
                l.add(ench(Material.DIAMOND_LEGGINGS, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3));
                l.add(ench(Material.DIAMOND_BOOTS, Enchantment.PROTECTION, 4, Enchantment.FEATHER_FALLING, 4, Enchantment.UNBREAKING, 3));
                l.add(ench(Material.NETHERITE_SWORD, Enchantment.SHARPNESS, 4, Enchantment.UNBREAKING, 3));
                l.add(ench(Material.NETHERITE_PICKAXE, Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
                l.add(ench(Material.NETHERITE_AXE, Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
                l.add(ench(Material.NETHERITE_SHOVEL, Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
                l.add(ench(Material.ELYTRA, Enchantment.UNBREAKING, 3));
                l.add(it(Material.FIREWORK_ROCKET, 64));
                l.add(it(Material.SCAFFOLDING, 64));
                l.add(it(Material.STONE_BRICKS, 64));
                l.add(it(Material.GLASS, 64));
                l.add(it(Material.GOLDEN_CARROT, 64));
                l.add(it(Material.GOLDEN_APPLE, 8));
            }
            case NETHERITE -> {
                l.add(ench(Material.NETHERITE_HELMET, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
                l.add(ench(Material.NETHERITE_CHESTPLATE, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
                l.add(ench(Material.NETHERITE_LEGGINGS, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
                l.add(ench(Material.NETHERITE_BOOTS, Enchantment.PROTECTION, 4, Enchantment.FEATHER_FALLING, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
                l.add(ench(Material.NETHERITE_SWORD, Enchantment.SHARPNESS, 5, Enchantment.LOOTING, 3, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
                l.add(ench(Material.NETHERITE_AXE, Enchantment.EFFICIENCY, 5, Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
                l.add(ench(Material.NETHERITE_PICKAXE, Enchantment.EFFICIENCY, 5, Enchantment.FORTUNE, 3, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
                l.add(ench(Material.NETHERITE_SHOVEL, Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
                l.add(ench(Material.NETHERITE_HOE, Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
                l.add(ench(Material.ELYTRA, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1));
                l.add(ench(Material.BOW, Enchantment.POWER, 5, Enchantment.INFINITY, 1, Enchantment.UNBREAKING, 3));
                l.add(it(Material.ARROW, 1));
                l.add(it(Material.FIREWORK_ROCKET, 64));
                l.add(it(Material.ENCHANTED_GOLDEN_APPLE, 2));
                l.add(it(Material.GOLDEN_APPLE, 16));
                l.add(it(Material.TOTEM_OF_UNDYING, 1));
                l.add(it(Material.GOLDEN_CARROT, 64));
            }
            default -> {
            }
        }

        return l;
    }

    /**
     * 3.13.14: how often a kit can be taken depends on the player's rank: owner and admins every hour, devs every 2h,
     * mods 3h, helpers 4h, support 5h, boosters 6h, builders/media/partners 8h, supreme/frost/legend 12h, others 24h.
     * The Starter kit stays once-only.
     */
    int hoursFor(Player p, Def d) {
        if (d.hours <= 0) {
            return d.hours;
        }

        if (p.isOp()) {
            return 1;
        }

        Ranks r = this.plugin.serverRanks();
        Ranks.Rank rank = r == null ? null : r.of(p);
        String id = rank == null ? "default" : rank.id;
        return switch (id) {
            case "owner", "admin" -> 1;
            case "dev" -> 2;
            case "mod" -> 3;
            case "helper" -> 4;
            case "support" -> 5;
            case "booster" -> 6;
            case "builder", "media", "partner" -> 8;
            case "supreme", "frost", "legend" -> 12;
            default -> d.hours;
        };
    }

    /** Milliseconds until the kit can be taken again; 0 = ready, -1 = taken (once-only). */
    private long wait(Player p, Def d) {
        long last = this.data.getLong(p.getUniqueId() + "." + d.name().toLowerCase(Locale.ROOT), 0L);
        if (last == 0L) {
            return 0L;
        }

        int hours = this.hoursFor(p, d);
        if (hours <= 0) {
            return -1L;
        }

        return Math.max(0L, last + hours * 3600_000L - System.currentTimeMillis());
    }

    private static String time(long ms) {
        long m = ms / 60000L;
        return m >= 60 ? m / 60 + "h " + m % 60 + "m" : Math.max(1, m) + "m";
    }

    /** "item/diamond_sword" sprite of an item, the picture shown in the text. */
    private static Component pic(Material m) {
        return Icons.spriteOr("item/" + m.getKey().getKey(), "<gray>•");
    }

    public void open(Player p) {
        int weight = this.weightOf(p);
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();
        for (Def d : Def.values()) {
            boolean open = weight >= d.weight;
            long wait = this.wait(p, d);
            ItemStack icon = new ItemStack(d.icon);
            icon.editMeta(m -> {
                m.addItemFlags(ItemFlag.values());
                if (open && wait == 0L) {
                    m.setEnchantmentGlintOverride(true);
                }
            });
            String state = !open ? "<red>🔒 " + this.rankNeeded(d) + " rank" : (wait == 0L ? "<green>✔ Ready to claim" : (wait < 0 ? "<gray>Already claimed" : "<yellow>⌛ " + time(wait)));
            // the kit's items as pictures, a row of item textures
            Component row = Component.empty();
            for (ItemStack s : contents(d)) {
                row = row.append(pic(s.getType()));
            }

            List<Component> hover = new ArrayList<>();
            hover.add(Text.mm("<gradient:" + d.gradient + "><b>" + d.display + " Kit"));
            hover.add(Text.mm("<gray>" + d.about));
            hover.add(row);
            hover.add(Text.mm(d.hours <= 0 ? "<dark_gray>Once" : "<dark_gray>Every " + this.hoursFor(p, d) + "h for your rank"));
            tiles.add(new LobbyDialogs.Tile(icon, Text.mm("<gradient:" + d.gradient + "><b>" + d.display), Text.mm(state), hover, () -> {
                this.claim(p, d);
                this.open(p);
            }));
        }

        this.plugin.dialogs().lobby().screen(p, LobbyDialogs.title("SMP Kits", "The higher your rank, the better the kit"), tiles,
            List.of(Text.mm("<gray>Hover a kit to see what's inside, click to claim.")), null, List.of(), 2, this.plugin.dialogs().lobby().close(p));
    }

    private String rankNeeded(Def d) {
        Ranks r = this.plugin.serverRanks();
        if (r != null) {
            Ranks.Rank best = null;
            for (Ranks.Rank x : r.all()) {
                if (x.weight >= d.weight && (best == null || x.weight < best.weight)) {
                    best = x;
                }
            }

            if (best != null && best.label != null && !best.label.isEmpty()) {
                return best.label;
            }
        }

        return d.display;
    }

    private void claim(Player p, Def d) {
        if (this.weightOf(p) < d.weight) {
            p.sendMessage(Text.mm("<red>The " + d.display + " kit needs the " + this.rankNeeded(d) + " rank."));
            Snd.error(p);
            return;
        }

        long wait = this.wait(p, d);
        if (wait != 0L) {
            p.sendMessage(Text.mm(wait < 0 ? "<red>You already claimed the " + d.display + " kit." : "<red>The " + d.display + " kit is ready in " + time(wait) + "."));
            Snd.error(p);
            return;
        }

        for (ItemStack s : contents(d)) {
            Map<Integer, ItemStack> left = p.getInventory().addItem(s);
            for (ItemStack l : left.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), l);
            }
        }

        this.data.set(p.getUniqueId() + "." + d.name().toLowerCase(Locale.ROOT), System.currentTimeMillis());
        try {
            this.data.save(this.file);
        } catch (IOException e) {
            this.plugin.getLogger().warning("Could not save smp-kits.yml");
        }

        p.sendMessage(Text.mm("<green>✔ <gradient:" + d.gradient + "><b>" + d.display + " Kit</b></gradient> <green>claimed!"));
        Snd.play(p, "entity.player.levelup", 0.7F, 1.4F);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (Def d : Def.values()) {
                String n = d.name().toLowerCase(Locale.ROOT);
                if (n.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(n);
                }
            }
        }

        return out;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (this.plugin.modes() == null || !this.plugin.modes().smpWorld(p.getWorld())) {
            p.sendMessage(Text.mm("<red>Kits are for the SMP. <gray>Use <white>/smp</white> first."));
            return true;
        }

        if (args.length >= 1) {
            for (Def d : Def.values()) {
                if (d.name().equalsIgnoreCase(args[0]) || d.display.equalsIgnoreCase(args[0])) {
                    this.claim(p, d);
                    return true;
                }
            }
        }

        this.open(p);
        return true;
    }
}
