package net.vortexsmp.smp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.vortexsmp.VortexSMP;
import net.vortexsmp.config.Cfg;
import net.vortexsmp.config.Opt;
import net.vortexsmp.util.Items;
import net.vortexsmp.util.Snd;
import net.vortexsmp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

/**
 * Drill pickaxes: mine a 2x2, 3x3 or 4x4 square in one go, facing the side you hit. Earned with Discord invites
 * (drills.tiers, e.g. 10 invites = 2x2, 20 = 3x3, 30+ = 4x4) and claimed with /drill claim. Each extra block is a
 * real break: other plugins can protect it, the pickaxe's enchantments apply and it loses durability.
 */
public final class Drills implements Listener, TabExecutor {
    private final VortexSMP plugin;
    private final NamespacedKey key;
    private final File file;
    private final YamlConfiguration claimed;
    private final Set<UUID> breaking = new HashSet<>();

    public Drills(VortexSMP plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "drill");
        this.file = new File(plugin.getDataFolder(), "drills.yml");
        this.claimed = YamlConfiguration.loadConfiguration(this.file);
    }

    /** {invites, size} tiers, lowest first. */
    public static List<int[]> tiers() {
        List<int[]> out = new ArrayList<>();
        for (String part : Cfg.s(Opt.DRILL_TIERS).split(",")) {
            String[] kv = part.trim().split(":");
            try {
                out.add(new int[]{Integer.parseInt(kv[0].trim()), Math.max(2, Math.min(5, Integer.parseInt(kv[1].trim())))});
            } catch (RuntimeException var5) {
            }
        }

        out.sort((a, b) -> a[0] - b[0]);
        return out;
    }

    public ItemStack drill(int size) {
        String name = switch (size) {
            case 2 -> "<gradient:#9EF0FF:#4FB3FF><b>2x2 Drill</b></gradient>";
            case 3 -> "<gradient:#FFE259:#FFA751><b>3x3 Drill</b></gradient>";
            default -> "<gradient:#FF7AE6:#A35CFF><b>" + size + "x" + size + " Drill</b></gradient>";
        };
        ItemStack it = Items.of(size >= 4 ? Material.NETHERITE_PICKAXE : Material.DIAMOND_PICKAXE)
            .name(name)
            .lore("<gray>Mines a <white>" + size + "x" + size + "</white> square at once.", "<gray>Earned by inviting friends to our Discord.", "", "<dark_gray>Vortex Realm SMP")
            .tag(this.key, String.valueOf(size))
            .glint(true)
            .build();
        it.editMeta(m -> {
            m.addEnchant(Enchantment.EFFICIENCY, size >= 4 ? 5 : 4, true);
            m.addEnchant(Enchantment.UNBREAKING, 3, true);
        });
        return it;
    }

    private int sizeOf(ItemStack it) {
        if (it == null || it.isEmpty() || !it.hasItemMeta()) {
            return 0;
        }

        String v = it.getItemMeta().getPersistentDataContainer().get(this.key, PersistentDataType.STRING);
        try {
            return v == null ? 0 : Integer.parseInt(v);
        } catch (NumberFormatException var4) {
            return 0;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!Cfg.bool(Opt.DRILLS) || this.breaking.contains(p.getUniqueId()) || p.isSneaking()) {
            return;
        }

        ItemStack tool = p.getInventory().getItemInMainHand();
        int size = this.sizeOf(tool);
        if (size < 2) {
            return;
        }

        RayTraceResult hit = p.rayTraceBlocks(6.0);
        BlockFace face = hit == null || hit.getHitBlockFace() == null ? BlockFace.UP : hit.getHitBlockFace();
        Block center = e.getBlock();
        int lo = -(size - 1) / 2;
        int hi = size / 2;
        this.breaking.add(p.getUniqueId());
        try {
            for (int a = lo; a <= hi; a++) {
                for (int b = lo; b <= hi; b++) {
                    if (a == 0 && b == 0) {
                        continue;
                    }

                    Block blk = switch (face) {
                        case UP, DOWN -> center.getRelative(a, 0, b);
                        case NORTH, SOUTH -> center.getRelative(a, b, 0);
                        default -> center.getRelative(0, a, b);
                    };
                    if (!this.mineable(blk, tool)) {
                        continue;
                    }

                    // a real break event: claims and protection plugins can say no
                    BlockBreakEvent ev = new BlockBreakEvent(blk, p);
                    Bukkit.getPluginManager().callEvent(ev);
                    if (ev.isCancelled()) {
                        continue;
                    }

                    if (p.getGameMode() == GameMode.CREATIVE) {
                        blk.setType(Material.AIR);
                    } else {
                        blk.breakNaturally(tool, true, true);
                        if (this.wear(p, tool)) {
                            return;
                        }
                    }
                }
            }
        } finally {
            this.breaking.remove(p.getUniqueId());
        }
    }

    private boolean mineable(Block b, ItemStack tool) {
        Material m = b.getType();
        return !m.isAir() && !b.isLiquid() && m.getHardness() >= 0.0F && m.getHardness() < 50.0F && b.isPreferredTool(tool)
            && !(b.getState() instanceof org.bukkit.block.Container);
    }

    /** One point of durability (Unbreaking respected); true when the drill broke. */
    private boolean wear(Player p, ItemStack tool) {
        int unb = tool.getEnchantmentLevel(Enchantment.UNBREAKING);
        if (unb > 0 && Math.random() >= 1.0 / (unb + 1)) {
            return false;
        }

        if (!(tool.getItemMeta() instanceof Damageable d)) {
            return false;
        }

        int dmg = d.getDamage() + 1;
        if (dmg >= tool.getType().getMaxDurability()) {
            p.getInventory().setItemInMainHand(null);
            Snd.play(p, "entity.item.break", 1.0F, 1.0F);
            return true;
        }

        d.setDamage(dmg);
        tool.setItemMeta(d);
        return false;
    }

    // ------------------------------------------------------------------ /drill

    private int earned(UUID u) {
        int inv = this.plugin.discord() == null ? 0 : this.plugin.discord().invitesOf(u);
        int best = 0;
        for (int[] t : tiers()) {
            if (inv >= t[0]) {
                best = t[1];
            }
        }

        return best;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("give") && sender.hasPermission("vortexsmp.admin")) {
            Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player sp ? sp : null);
            int size = args.length > 2 ? parse(args[2]) : 3;
            if (t == null || size < 2) {
                sender.sendMessage(Text.mm("<red>Usage: /drill give <player> <2|3|4>"));
            } else {
                t.getInventory().addItem(this.drill(size)).values().forEach(left -> t.getWorld().dropItemNaturally(t.getLocation(), left));
                sender.sendMessage(Text.mm("<green>Gave " + Text.esc(t.getName()) + " a " + size + "x" + size + " drill."));
            }

            return true;
        } else if (sub.equals("bonus") && sender.hasPermission("vortexsmp.admin") && args.length > 2) {
            org.bukkit.OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(args[1]);
            if (t != null && this.plugin.discord() != null) {
                this.plugin.discord().addBonus(t.getUniqueId(), parse(args[2]));
                sender.sendMessage(Text.mm("<green>Added " + parse(args[2]) + " invites to " + Text.esc(args[1]) + "."));
            }

            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (!Cfg.bool(Opt.DRILLS)) {
            p.sendMessage(Text.mm("<red>Drills are turned off on this server."));
            return true;
        }

        int inv = this.plugin.discord() == null ? 0 : this.plugin.discord().invitesOf(p.getUniqueId());
        boolean linked = this.plugin.discord() != null && this.plugin.discord().discordOf(p.getUniqueId()) != null;
        List<Integer> got = this.claimed.getIntegerList("claimed." + p.getUniqueId());
        if (sub.equals("claim")) {
            if (!linked) {
                p.sendMessage(Text.mm("<red>Link your Discord first: <yellow>/link"));
                return true;
            }

            int size = this.earned(p.getUniqueId());
            if (size == 0) {
                p.sendMessage(Text.mm("<red>You have " + inv + " invites - not enough for a drill yet."));
            } else if (got.contains(size)) {
                p.sendMessage(Text.mm("<yellow>You already claimed your " + size + "x" + size + " drill. <gray>Invite more friends for the next one!"));
            } else {
                got = new ArrayList<>(got);
                got.add(size);
                this.claimed.set("claimed." + p.getUniqueId(), got);
                try {
                    this.claimed.save(this.file);
                } catch (IOException var11) {
                    this.plugin.getLogger().log(Level.WARNING, "Could not save drills.yml", (Throwable)var11);
                }

                p.getInventory().addItem(this.drill(size)).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
                p.sendMessage(Text.mm("<gold><b>⛏ " + size + "x" + size + " Drill claimed!</b> <gray>Thanks for inviting " + inv + " friends."));
                Snd.play(p, "ui.toast.challenge_complete", 0.7F, 1.2F);
                Bukkit.broadcast(Text.mm("<gold>⛏ <white>" + Text.esc(p.getName()) + " <gray>earned a <yellow>" + size + "x" + size + " Drill</yellow> by inviting " + inv + " friends to our Discord!"));
            }

            return true;
        }

        p.sendMessage(Text.mm(" "));
        p.sendMessage(Text.mm("<gold><b>⛏ Drills</b> <dark_gray>- <gray>invite friends to our Discord, earn mining drills"));
        p.sendMessage(Text.mm("<gray>Discord: " + (linked ? "<green>linked" : "<red>not linked <yellow>(/link)") + " <dark_gray>• <gray>Your invites: <white>" + inv));
        for (int[] t : tiers()) {
            boolean ok = inv >= t[0];
            p.sendMessage(Text.mm((ok ? "<green>✔ " : "<dark_gray>✖ ") + "<white>" + t[1] + "x" + t[1] + " Drill <gray>- " + t[0] + " invites"
                + (got.contains(t[1]) ? " <dark_gray>(claimed)" : (ok ? " <yellow><click:run_command:'/drill claim'>[Claim]</click>" : ""))));
        }

        p.sendMessage(Text.mm(" "));
        return true;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException var2) {
            return 0;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> o = new ArrayList<>(List.of("claim", "info"));
            if (sender.hasPermission("vortexsmp.admin")) {
                o.add("give");
                o.add("bonus");
            }

            return o.stream().filter(x -> x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }

        return List.of();
    }
}
