package net.vortexpvp.staff;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.bossbar.BossBar.Color;
import net.kyori.adventure.bossbar.BossBar.Overlay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.session.PlayerReset;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.session.Session;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

public final class ScreenShare implements TabExecutor, Listener {
    private static final Set<String> ALLOWED = Set.of("msg", "tell", "w", "r", "reply", "discord", "ss", "admit", "helpop");
    private final VortexPvP plugin;
    private final Map<UUID, ScreenShare.Case> cases = new LinkedHashMap<>();

    public ScreenShare(VortexPvP plugin) {
        this.plugin = plugin;
    }

    public boolean frozen(Player p) {
        return p != null && this.cases.containsKey(p.getUniqueId());
    }

    private void start(Player staff, Player target, boolean force) {
        if (this.frozen(target)) {
            staff.sendMessage(Text.mm("<red>" + Text.esc(target.getName()) + " is already being screenshared."));
        } else {
            Session s = this.plugin.sessions().get(target);
            if (force || s.state != PlayerState.MATCH && s.state != PlayerState.PREMATCH) {
                if (s.state == PlayerState.QUEUE) {
                    this.plugin.queues().leaveAll(target, true);
                }

                BossBar bar = BossBar.bossBar((Component)Component.empty(), 1.0F, Color.RED, Overlay.PROGRESS);
                ScreenShare.Case c = new ScreenShare.Case(
                    target.getUniqueId(), target.getName(), staff.getUniqueId(), staff.getName(), System.currentTimeMillis(), target.getLocation(), bar
                );
                this.cases.put(target.getUniqueId(), c);
                PlayerReset.freeze(target, s);
                target.closeInventory();
                target.showBossBar(bar);
                String discord = Cfg.s(Opt.LINK_DISCORD);
                target.showTitle(
                    Title.title(
                        Text.mm("<dark_red><b>FROZEN"),
                        Text.mm("<gray>Join <white>" + Text.esc(discord.replaceFirst("^https?://", "")) + "</white> now - don't log out"),
                        Times.times(Duration.ofMillis(200L), Duration.ofSeconds(6L), Duration.ofMillis(500L))
                    )
                );
                Snd.play(target, "block.anvil.land", 1.0F, 0.6F);
                Snd.play(target, "entity.elder_guardian.curse", 0.6F, 1.0F);
                this.instructions(target, c);
                this.log("START " + target.getName() + " by " + staff.getName());
                this.staffAlert(
                    Text.mm(
                        "<dark_aqua>[SS] <white>"
                            + Text.esc(staff.getName())
                            + " <gray>froze <white>"
                            + Text.esc(target.getName())
                            + "<gray> for a screenshare."
                    )
                );
            } else {
                staff.sendMessage(
                    Text.mm(
                        "<yellow>"
                            + Text.esc(target.getName())
                            + " is in a match. <gray>Wait for it to end, or use <white>/ss "
                            + Text.esc(target.getName())
                            + " force</white>."
                    )
                );
            }
        }
    }

    private void instructions(Player p, ScreenShare.Case c) {
        String discord = Cfg.s(Opt.LINK_DISCORD);
        String url = discord.startsWith("http") ? discord : "https://" + discord;
        p.sendMessage(Text.mm("<dark_red><b>━━━━━━━━━━━━ SCREENSHARE ━━━━━━━━━━━━"));
        p.sendMessage(Text.mm("<white>You have been frozen by <red>" + Text.esc(c.staffName()) + "</red> for a screenshare."));
        p.sendMessage(
            Text.mm("<gray>1. Join our Discord: <aqua><u><click:open_url:'" + url.replace("'", "") + "'>" + Text.esc(discord) + "</click></u></aqua>")
        );
        p.sendMessage(Text.mm("<gray>2. Go to the <white>Screenshare</white> waiting room within <white>" + Cfg.i(Opt.SS_MINUTES) + " minutes</white>."));
        String tool = Cfg.s(Opt.SS_TOOL_LINK);
        if (tool != null && !tool.isBlank()) {
            String tu = tool.startsWith("http") ? tool : "https://" + tool;
            p.sendMessage(
                Text.mm(
                    "<gray>3. When staff ask, run the checking tool: <aqua><u><click:open_url:'" + tu.replace("'", "") + "'>" + Text.esc(tool) + "</click></u>"
                )
            );
        }

        p.sendMessage(
            Text.mm(
                "<red>Logging out = admitting.</red> <gray>Admitting now gets a shorter punishment: <white><click:run_command:'/ss admit'><hover:show_text:'<gray>Click to admit'>[/ss admit]</hover></click>"
            )
        );
        p.sendMessage(Text.mm("<gray>Only staff can read your chat while you're frozen."));
        p.sendMessage(Text.mm("<dark_red><b>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }

    private void end(ScreenShare.Case c, String result, String by) {
        this.cases.remove(c.target());
        Player t = Bukkit.getPlayer(c.target());
        if (t != null) {
            t.hideBossBar(c.bar());
            PlayerReset.unfreeze(t, this.plugin.sessions().get(t));
            t.clearTitle();
        }
        String action = switch (result) {
            case "cheating" -> Cfg.s(Opt.SS_CHEAT_ACTION);
            case "admitted" -> Cfg.s(Opt.SS_ADMIT_ACTION);
            case "logout" -> Cfg.s(Opt.SS_LOGOUT_ACTION);
            default -> "";
        };
        if (t != null) {
            t.sendMessage(Text.mm(switch (result) {
                case "clean" -> "<green><b>Screenshare over - you're clean.</b> <gray>Thanks for your patience!";
                case "admitted" -> "<yellow>You admitted. <gray>The punishment for admitting was applied.";
                default -> "<red>The screenshare found cheats.";
            }));
            if ("clean".equals(result)) {
                Snd.play(t, "entity.player.levelup", 0.8F, 1.4F);
            }
        }

        if (action != null && !action.isBlank()) {
            String cmd = action.replace("{player}", c.targetName()).replace("{staff}", by);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.startsWith("/") ? cmd.substring(1) : cmd);
        }

        this.log(
            "END "
                + c.targetName()
                + " "
                + result.toUpperCase(Locale.ROOT)
                + " by "
                + by
                + " after "
                + (System.currentTimeMillis() - c.started()) / 1000L
                + "s"
        );
        this.staffAlert(
            Text.mm("<dark_aqua>[SS] <white>" + Text.esc(c.targetName()) + "<gray>: <white>" + result + "</white> <dark_gray>(" + Text.esc(by) + ")")
        );
    }

    public void tick() {
        long now = System.currentTimeMillis();
        long limit = Cfg.i(Opt.SS_MINUTES) * 60000L;

        for (ScreenShare.Case c : new ArrayList<>(this.cases.values())) {
            Player t = Bukkit.getPlayer(c.target());
            if (t != null) {
                long left = Math.max(0L, limit - (now - c.started()));
                c.bar().progress((float)Math.max(0.0, Math.min(1.0, (double)left / Math.max(1L, limit))));
                c.bar()
                    .name(
                        Text.mm(
                            "<dark_red><b>FROZEN</b></dark_red> <dark_gray>• <gray>join the Discord <dark_gray>• "
                                + (left > 0L ? "<white>" + left / 60000L + ":" + String.format("%02d", left / 1000L % 60L) : "<red>time's up - staff decide")
                        )
                    );
                long secs = (now - c.started()) / 1000L;
                if (secs > 0L && secs % 15L == 0L) {
                    t.sendActionBar(Text.mm("<red><b>Don't log out!</b> <gray>Join the Discord for your screenshare."));
                    Snd.play(t, "block.note_block.bass", 0.8F, 0.6F);
                }
            }
        }
    }

    @EventHandler(
        priority = EventPriority.HIGHEST
    )
    public void onQuit(PlayerQuitEvent e) {
        ScreenShare.Case c = this.cases.get(e.getPlayer().getUniqueId());
        if (c != null) {
            this.end(c, "logout", "auto (logged out)");
        }
    }

    @EventHandler(
        priority = EventPriority.LOWEST,
        ignoreCancelled = true
    )
    public void onMove(PlayerMoveEvent e) {
        if (this.frozen(e.getPlayer())) {
            Location f = e.getFrom();
            Location t = e.getTo();
            if (f.getX() != t.getX() || f.getZ() != t.getZ() || t.getY() > f.getY()) {
                Location back = f.clone();
                back.setYaw(t.getYaw());
                back.setPitch(t.getPitch());
                e.setTo(back);
            }
        }
    }

    @EventHandler(
        priority = EventPriority.LOWEST,
        ignoreCancelled = true
    )
    public void onTeleport(PlayerTeleportEvent e) {
        if (this.frozen(e.getPlayer()) && e.getCause() != TeleportCause.PLUGIN && e.getCause() != TeleportCause.COMMAND) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.LOWEST
    )
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (this.frozen(e.getPlayer())) {
            String cmd = e.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
            int colon = cmd.indexOf(58);
            if (colon >= 0) {
                cmd = cmd.substring(colon + 1);
            }

            if (!ALLOWED.contains(cmd)) {
                e.setCancelled(true);
                e.getPlayer().sendMessage(Text.mm("<red>You're frozen for a screenshare. <gray>Join the Discord."));
            }
        }
    }

    @EventHandler(
        priority = EventPriority.LOWEST
    )
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (this.frozen(p)) {
            e.setCancelled(true);
            String said = PlainTextComponentSerializer.plainText().serialize(e.message());
            Component line = Text.mm("<dark_aqua>[SS] <red>" + Text.esc(p.getName()) + "<dark_gray>: <white>" + Text.esc(said));
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                p.sendMessage(line);
                this.staffAlert(line);
            });
        }
    }

    @EventHandler(
        priority = EventPriority.LOWEST
    )
    public void onInteract(PlayerInteractEvent e) {
        if (this.frozen(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.LOWEST
    )
    public void onDrop(PlayerDropItemEvent e) {
        if (this.frozen(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.LOWEST
    )
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && this.frozen(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.LOWEST
    )
    public void onBreak(BlockBreakEvent e) {
        if (this.frozen(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.LOWEST
    )
    public void onPlace(BlockPlaceEvent e) {
        if (this.frozen(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGHEST
    )
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && this.frozen(p)) {
            e.setCancelled(true);
        }

        if (e instanceof EntityDamageByEntityEvent ed && ed.getDamager() instanceof Player d && this.frozen(d)) {
            e.setCancelled(true);
        }
    }

    private void staffAlert(Component msg) {
        for (Player s : Bukkit.getOnlinePlayers()) {
            if (s.hasPermission("vortexpvp.staff")) {
                s.sendMessage(msg);
            }
        }

        Bukkit.getConsoleSender().sendMessage(msg);
    }

    private void log(String line) {
        File f = new File(this.plugin.getDataFolder(), "screenshares.log");

        try (FileWriter w = new FileWriter(f, true)) {
            w.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date()) + " " + line + System.lineSeparator());
        } catch (IOException var8) {
        }
    }

    public void shutdown() {
        for (ScreenShare.Case c : new ArrayList<>(this.cases.values())) {
            Player t = Bukkit.getPlayer(c.target());
            if (t != null) {
                t.hideBossBar(c.bar());
                PlayerReset.unfreeze(t, this.plugin.sessions().get(t));
                t.sendMessage(Text.mm("<yellow>The server is restarting - your screenshare was paused."));
            }
        }

        this.cases.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("admit") || args.length == 1 && args[0].equalsIgnoreCase("admit")) {
            if (sender instanceof Player p && this.frozen(p)) {
                this.end(this.cases.get(p.getUniqueId()), "admitted", p.getName() + " (self)");
            } else {
                sender.sendMessage(Text.mm("<gray>You're not being screenshared."));
            }

            return true;
        } else if (!sender.hasPermission("vortexpvp.staff")) {
            sender.sendMessage(Text.mm("<red>You don't have permission."));
            return true;
        } else if (args.length == 0) {
            sender.sendMessage(
                Text.mm(
                    "<gold><b>Screenshare</b> <gray>/ss <player> [force] <dark_gray>• <gray>/ss end <player> <clean|cheating|admitted> <dark_gray>• <gray>/ss list"
                )
            );
            return true;
        } else if (args[0].equalsIgnoreCase("list")) {
            if (this.cases.isEmpty()) {
                sender.sendMessage(Text.mm("<gray>Nobody is frozen."));
            }

            for (ScreenShare.Case c : this.cases.values()) {
                sender.sendMessage(
                    Text.mm(
                        " <dark_gray>» <white>"
                            + Text.esc(c.targetName())
                            + " <gray>by "
                            + Text.esc(c.staffName())
                            + " <dark_gray>("
                            + (System.currentTimeMillis() - c.started()) / 1000L
                            + "s)"
                    )
                );
            }

            return true;
        } else if (args[0].equalsIgnoreCase("end")) {
            if (args.length < 3) {
                sender.sendMessage(Text.mm("<gray>/ss end <player> <clean|cheating|admitted>"));
                return true;
            } else {
                ScreenShare.Case found = null;

                for (ScreenShare.Case c : this.cases.values()) {
                    if (c.targetName().equalsIgnoreCase(args[1])) {
                        found = c;
                    }
                }

                String result = args[2].toLowerCase(Locale.ROOT);
                if (found == null) {
                    sender.sendMessage(Text.mm("<red>" + Text.esc(args[1]) + " isn't frozen."));
                } else if (!List.of("clean", "cheating", "admitted").contains(result)) {
                    sender.sendMessage(Text.mm("<red>Result: clean, cheating or admitted."));
                } else {
                    this.end(found, result, sender.getName());
                }

                return true;
            }
        } else if (!(sender instanceof Player staff)) {
            sender.sendMessage("Only players can start a screenshare.");
            return true;
        } else {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(Text.mm("<red>Player not online."));
                return true;
            } else if (target.equals(staff)) {
                sender.sendMessage(Text.mm("<red>You can't screenshare yourself."));
                return true;
            } else {
                this.start(staff, target, args.length > 1 && args[1].equalsIgnoreCase("force"));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("vortexpvp.staff")) {
            return out;
        } else {
            if (args.length == 1) {
                out.addAll(List.of("end", "list"));

                for (Player p : Bukkit.getOnlinePlayers()) {
                    out.add(p.getName());
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("end")) {
                for (ScreenShare.Case c : this.cases.values()) {
                    out.add(c.targetName());
                }
            } else if (args.length == 3 && args[0].equalsIgnoreCase("end")) {
                out.addAll(List.of("clean", "cheating", "admitted"));
            } else if (args.length == 2) {
                out.add("force");
            }

            String last = args[args.length - 1].toLowerCase(Locale.ROOT);
            out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
            return out;
        }
    }

    private record Case(UUID target, String targetName, UUID staff, String staffName, long started, Location at, BossBar bar) {
    }
}
