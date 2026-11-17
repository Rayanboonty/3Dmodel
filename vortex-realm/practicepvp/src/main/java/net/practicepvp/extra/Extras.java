package net.practicepvp.extra;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.practicepvp.PracticePlugin;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Opt;
import net.practicepvp.fx.Toasts;
import net.practicepvp.session.PlayerState;
import net.practicepvp.session.Session;
import net.practicepvp.social.LinksDialog;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class Extras implements CommandExecutor, TabCompleter, Listener {
    private final PracticePlugin plugin;
    private final List<String> tips = new ArrayList<>();
    private String tipAudience = "pvp";
    private int tipIndex;
    private long lastTip = System.currentTimeMillis();
    private long lastAdToast = System.currentTimeMillis();

    public Extras(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File f = new File(this.plugin.getDataFolder(), "announcements.yml");
        if (!f.exists()) {
            YamlConfiguration d = new YamlConfiguration();
            d.options()
                .setHeader(
                    List.of(
                        "Tips broadcast by the auto announcer (MiniMessage).",
                        "audience: pvp (players in PvP worlds), smp (SMP worlds) or all.",
                        "Interval and on/off: /pvpadmin -> Settings -> SMP & Extras."
                    )
                );
            d.set("audience", "pvp");
            d.set(
                "messages",
                List.of(
                    "<gold><b>TIP</b> <dark_gray>» <gray>Duel a friend with <yellow>/duel \\<player></yellow> - choose the kit, the map and first to 1-3!",
                    "<gold><b>TIP</b> <dark_gray>» <gray>Climb from <#9E9E9E>LT5</#9E9E9E> <gray>to <gold><b>HT1</b></gold><gray>: open <aqua>Duels</aqua><gray> and press <gold>\ud83c\udfc6 Ranked</gold><gray>.",
                    "<gold><b>TIP</b> <dark_gray>» <gray>Arrange your kits with the <yellow>Kit Editor</yellow><gray> - your layout is used in every match.",
                    "<gold><b>TIP</b> <dark_gray>» <gray>Bring friends: <yellow>/party create</yellow><gray> for Party FFA and Party Split fights.",
                    "<gold><b>TIP</b> <dark_gray>» <gray>Watch the best players with <yellow>/spectate</yellow><gray> - they can't see you.",
                    "<gold><b>TIP</b> <dark_gray>» <gray>Turn off sounds, titles and more in <yellow>/settings</yellow><gray>."
                )
            );

            try {
                d.save(f);
            } catch (IOException var9) {
                this.plugin.getLogger().warning("Could not write announcements.yml: " + var9.getMessage());
            }
        }

        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        this.tips.clear();
        this.tips.addAll(y.getStringList("messages"));
        String discord = Cfg.s(Opt.LINK_DISCORD);
        if (!discord.isBlank() && this.tips.stream().noneMatch(t -> t.toLowerCase(Locale.ROOT).contains("discord"))) {
            String shown = discord.replaceFirst("^https?://", "");
            String ad = "<#5865F2><b>DISCORD</b> <dark_gray>» <gray>Tournaments, giveaways, updates and LFG - join <click:open_url:'"
                + discord
                + "'><hover:show_text:'<gray>Click to open'><#7289DA><u>"
                + shown
                + "</u></hover></click><gray>!";
            this.tips.add(ad);
            List<String> saved = new ArrayList<>(y.getStringList("messages"));
            saved.add(ad);
            y.set("messages", saved);

            try {
                y.save(f);
            } catch (IOException var8) {
                this.plugin.getLogger().warning("Could not update announcements.yml: " + var8.getMessage());
            }
        }

        this.tipAudience = y.getString("audience", "pvp").toLowerCase(Locale.ROOT);
    }

    public void tick() {
        long now = System.currentTimeMillis();
        if (Cfg.bool(Opt.ANNOUNCER) && !this.tips.isEmpty() && now - this.lastTip >= Cfg.i(Opt.ANNOUNCER_INTERVAL) * 1000L) {
            this.lastTip = now;
            Component tip = Text.mm(this.tips.get(this.tipIndex++ % this.tips.size()));

            for (Player p : Bukkit.getOnlinePlayers()) {
                boolean pvp = this.plugin.worlds().managed(p.getWorld());
                if ("all".equals(this.tipAudience) || "pvp".equals(this.tipAudience) == pvp) {
                    p.sendMessage(tip);
                }
            }
        }

        int adMinutes = Cfg.i(Opt.AD_TOAST_MINUTES);
        if (adMinutes > 0 && now - this.lastAdToast >= adMinutes * 60000L) {
            this.lastAdToast = now;

            for (Player px : Bukkit.getOnlinePlayers()) {
                PlayerState st = this.plugin.sessions().state(px);
                if (st == PlayerState.LOBBY || st == PlayerState.QUEUE || st == PlayerState.OUTSIDE && this.plugin.hub() != null && this.plugin.hub().inHub(px)
                    )
                 {
                    Toasts.ad(px);
                }
            }
        }

        int afk = Cfg.i(Opt.QUEUE_AFK_MINUTES);
        if (afk > 0) {
            for (Player pxx : this.plugin.sessions().playersIn(PlayerState.QUEUE)) {
                Session s = this.plugin.sessions().get(pxx);
                if (s.lastActive == 0L) {
                    s.lastActive = now;
                } else if (now - s.lastActive > afk * 60000L) {
                    this.plugin.queues().leaveAll(pxx, true);
                    pxx.sendMessage(Text.mm("<red>You were removed from the queue for being AFK."));
                    Snd.error(pxx);
                }
            }
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onMove(PlayerMoveEvent e) {
        Location f = e.getFrom();
        Location t = e.getTo();
        if (f.getBlockX() != t.getBlockX() || f.getBlockZ() != t.getBlockZ() || f.getYaw() != t.getYaw()) {
            Session s = this.plugin.sessions().get(e.getPlayer().getUniqueId());
            if (s != null) {
                s.lastActive = System.currentTimeMillis();
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player p) {
            String var6 = command.getName().toLowerCase(Locale.ROOT);
            switch (var6) {
                case "playtime":
                    Player t = args.length > 0 ? Bukkit.getPlayerExact(args[0]) : p;
                    if (t == null) {
                        p.sendMessage(Text.mm("<red>That player is not online."));
                        return true;
                    }

                    long minutes = t.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L / 60L;
                    p.sendMessage(Text.mm("<gold>" + Text.esc(t.getName()) + "</gold> <gray>has played <white>" + minutes / 60L + "h " + minutes % 60L + "m"));
                    break;
                case "links":
                    LinksDialog.open(this.plugin, p, label);
                    break;
                default:
                    return false;
            }

            return true;
        } else {
            sender.sendMessage("Players only.");
            return true;
        }
    }

    private static void openWorkbench(Player p) {
        p.openWorkbench(null, true);
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

            p.sendMessage(Text.mm("<green>Enjoy your new hat!"));
        }
    }

    private void near(Player p) {
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

        p.sendMessage(
            Text.mm(
                lines.isEmpty()
                    ? "<gray>Nobody within " + radius + " blocks."
                    : "<gold>Nearby <dark_gray>» </dark_gray>" + String.join("<dark_gray>, </dark_gray>", lines)
            )
        );
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
        } else {
            return List.of();
        }
    }
}
