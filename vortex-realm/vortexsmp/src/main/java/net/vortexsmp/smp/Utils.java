package net.vortexsmp.smp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.vortexsmp.VortexSMP;
import net.vortexsmp.config.Cfg;
import net.vortexsmp.config.Opt;
import net.vortexsmp.util.Snd;
import net.vortexsmp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** /craft /ec /trash /hat /nv /near /playtime and /setspawn. */
public final class Utils implements TabExecutor {
    private final VortexSMP plugin;

    public Utils(VortexSMP plugin) {
        this.plugin = plugin;
    }

    private boolean on(Player p) {
        if (!Cfg.bool(Opt.SMP_UTILS)) {
            p.sendMessage(Text.mm("<red>That command is disabled."));
            return false;
        }

        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "craft":
                if (this.on(p)) {
                    p.openWorkbench(null, true);
                }
                break;
            case "enderchest":
                if (this.on(p)) {
                    p.openInventory(p.getEnderChest());
                    Snd.play(p, "block.ender_chest.open", 0.6F, 1.0F);
                }
                break;
            case "trash":
                if (this.on(p)) {
                    p.openInventory(Bukkit.createInventory(null, 36, Text.mm("<red><b>Trash</b> <gray>(deleted on close)")));
                }
                break;
            case "hat":
                if (this.on(p)) {
                    hat(p);
                }
                break;
            case "nightvision":
                if (this.on(p)) {
                    if (p.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
                        p.removePotionEffect(PotionEffectType.NIGHT_VISION);
                        p.sendMessage(Text.mm("<gray>Night vision <red>off"));
                    } else {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, -1, 0, true, false, true));
                        p.sendMessage(Text.mm("<gray>Night vision <green>on"));
                    }
                }
                break;
            case "near":
                if (this.on(p)) {
                    near(p);
                }
                break;
            case "playtime":
                Player t = args.length > 0 ? Bukkit.getPlayerExact(args[0]) : p;
                if (t == null) {
                    p.sendMessage(Text.mm("<red>That player is not online."));
                    return true;
                }

                long minutes = t.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L / 60L;
                p.sendMessage(Text.mm("<gold>" + Text.esc(t.getName()) + "</gold> <gray>has played <white>" + minutes / 60L + "h " + minutes % 60L + "m"));
                break;
            case "setspawn":
                this.plugin.modes().setSpawn(p.getLocation());
                p.sendMessage(Text.mm("<green>SMP spawn set here."));
                Snd.click(p);
                break;
            default:
                return false;
        }

        return true;
    }

    private static void hat(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack hand = inv.getItemInMainHand();
        if (hand.isEmpty()) {
            p.sendMessage(Text.mm("<red>Hold the item you want to wear."));
        } else {
            ItemStack old = inv.getHelmet();
            ItemStack one = hand.clone();
            one.setAmount(1);
            hand.setAmount(hand.getAmount() - 1);
            inv.setHelmet(one);
            if (old != null && !old.isEmpty()) {
                inv.addItem(new ItemStack[]{old}).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
            }

            p.sendMessage(Text.mm("<gray>Nice hat!"));
        }
    }

    private static void near(Player p) {
        int radius = 200;
        List<String> lines = new ArrayList<>();

        for (Player o : p.getWorld().getPlayers()) {
            if (!o.equals(p) && p.canSee(o)) {
                double d = o.getLocation().distance(p.getLocation());
                if (d <= radius) {
                    lines.add("<white>" + Text.esc(o.getName()) + " <gray>" + Math.round(d) + "m");
                }
            }
        }

        p.sendMessage(Text.mm(lines.isEmpty() ? "<gray>Nobody within " + radius + " blocks." : "<gold>Nearby <dark_gray>» </dark_gray>" + String.join("<dark_gray>, </dark_gray>", lines)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("playtime") && args.length == 1) {
            List<String> out = new ArrayList<>();

            for (Player o : Bukkit.getOnlinePlayers()) {
                if (o.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(o.getName());
                }
            }

            return out;
        }

        return List.of();
    }
}
