package net.vortexrealm.staff;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.gui.Menu;
import net.vortexrealm.gui.Ui;
import net.vortexrealm.util.Fmt;
import net.vortexrealm.util.Items;
import net.vortexrealm.util.Snd;
import net.vortexrealm.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class Reports implements TabExecutor {
    private static final String[][] REASONS = new String[][]{
        {"Kill Aura / Hitbox", "DIAMOND_SWORD"},
        {"Reach", "STICK"},
        {"Autoclicker / Macro", "STONE_BUTTON"},
        {"Velocity / Anti-KB", "PISTON"},
        {"Fly / Speed", "FEATHER"},
        {"Auto Totem / Crystal", "TOTEM_OF_UNDYING"},
        {"X-Ray", "DIAMOND_ORE"},
        {"Teaming / Abuse", "PLAYER_HEAD"},
        {"Chat / Toxicity", "WRITABLE_BOOK"},
        {"Other", "PAPER"}
    };
    private final VortexRealm plugin;
    private final File file;
    private final List<Reports.Report> open = new ArrayList<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private int nextId = 1;

    public Reports(VortexRealm plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "reports.yml");
    }

    public void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        this.nextId = y.getInt("next-id", 1);

        for (Map<?, ?> m : y.getMapList("open")) {
            try {
                this.open
                    .add(
                        new Reports.Report(
                            ((Number)m.get("id")).intValue(),
                            UUID.fromString(String.valueOf(m.get("target"))),
                            String.valueOf(m.get("target-name")),
                            UUID.fromString(String.valueOf(m.get("by"))),
                            String.valueOf(m.get("by-name")),
                            String.valueOf(m.get("reason")),
                            ((Number)m.get("at")).longValue(),
                            String.valueOf(m.get("where"))
                        )
                    );
            } catch (RuntimeException var5) {
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next-id", this.nextId);
        List<Map<String, Object>> list = new ArrayList<>();

        for (Reports.Report r : this.open) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.id());
            m.put("target", r.target().toString());
            m.put("target-name", r.targetName());
            m.put("by", r.by().toString());
            m.put("by-name", r.byName());
            m.put("reason", r.reason());
            m.put("at", r.at());
            m.put("where", r.where());
            list.add(m);
        }

        y.set("open", list);

        try {
            y.save(this.file);
        } catch (IOException var6) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save reports.yml", (Throwable)var6);
        }
    }

    public List<Reports.Report> open() {
        return new ArrayList<>(this.open);
    }

    private void submit(Player by, Player target, String reason) {
        long now = System.currentTimeMillis();
        Long last = this.cooldown.get(by.getUniqueId());
        if (last != null && now - last < 45000L && !by.hasPermission("vortex.staff")) {
            by.sendMessage(Text.mm("<red>Please wait " + Fmt.duration((45000L - (now - last)) / 1000L) + " before reporting again."));
        } else {
            this.cooldown.put(by.getUniqueId(), now);
            String where = this.plugin.sessions().state(target).name().toLowerCase(Locale.ROOT) + " @ " + target.getWorld().getName();
            Reports.Report r = new Reports.Report(this.nextId++, target.getUniqueId(), target.getName(), by.getUniqueId(), by.getName(), reason, now, where);
            this.open.add(r);

            while (this.open.size() > 300) {
                this.open.remove(0);
            }

            this.save();
            by.sendMessage(
                Text.mm("<green>✔ Report sent. <gray>Staff will look at <white>" + Text.esc(target.getName()) + "</white>. Thanks for keeping the server fair!")
            );
            Snd.play(by, "block.note_block.pling", 0.6F, 1.6F);
            int count = 0;

            for (Reports.Report o : this.open) {
                if (o.target().equals(target.getUniqueId())) {
                    count++;
                }
            }

            Component alert = Text.mm(
                "<red><b>⚑ REPORT</b> <white>"
                    + Text.esc(target.getName())
                    + " <gray>for <yellow>"
                    + Text.esc(reason)
                    + " <gray>by "
                    + Text.esc(by.getName())
                    + " <dark_gray>("
                    + count
                    + " open, "
                    + where
                    + ") <aqua><click:run_command:'/spectate "
                    + target.getName()
                    + "'>[Spectate]</click> <dark_red><click:suggest_command:'/hackerpool "
                    + target.getName()
                    + " 7d "
                    + reason
                    + "'>[Hacker Pool]</click> <gray><click:run_command:'/reports dismiss "
                    + r.id()
                    + "'>[Dismiss]</click>"
            );

            for (Player s : Bukkit.getOnlinePlayers()) {
                if (s.hasPermission("vortex.staff")) {
                    s.sendMessage(alert);
                    Snd.play(s, "block.note_block.pling", 0.8F, 0.6F);
                }
            }

            this.plugin.getLogger().info("Report #" + r.id() + ": " + target.getName() + " (" + reason + ") by " + by.getName());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("reports")) {
            if (sender instanceof Player p) {
                if (args.length == 0) {
                    p.sendMessage(Text.mm("<red>Usage: /report <player> [reason]"));
                    return true;
                } else {
                    Player t = Bukkit.getPlayer(args[0]);
                    if (t == null || !p.canSee(t)) {
                        p.sendMessage(Text.mm("<red>That player is not online."));
                        return true;
                    } else if (t.equals(p)) {
                        p.sendMessage(Text.mm("<red>You can't report yourself."));
                        return true;
                    } else {
                        if (args.length >= 2) {
                            this.submit(p, t, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                        } else {
                            new Reports.ReasonMenu(p, t).open();
                        }

                        return true;
                    }
                }
            } else {
                sender.sendMessage("Players only.");
                return true;
            }
        } else if (!sender.hasPermission("vortex.staff")) {
            sender.sendMessage(Text.mm("<red>You don't have permission."));
            return true;
        } else if (args.length >= 2 && args[0].equalsIgnoreCase("dismiss")) {
            try {
                int id = Integer.parseInt(args[1]);
                boolean removed = this.open.removeIf(rx -> rx.id() == id);
                this.save();
                sender.sendMessage(Text.mm(removed ? "<gray>Report #" + id + " dismissed." : "<red>No report #" + id));
            } catch (NumberFormatException var8) {
                sender.sendMessage(Text.mm("<red>Usage: /reports dismiss <id>"));
            }

            return true;
        } else {
            if (sender instanceof Player px) {
                new Reports.ListMenu(px).open();
            } else {
                for (Reports.Report r : this.open) {
                    sender.sendMessage("#" + r.id() + " " + r.targetName() + " - " + r.reason() + " by " + r.byName());
                }
            }

            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("report") && args.length == 1) {
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (!(sender instanceof Player p && !p.canSee(o))) {
                    out.add(o.getName());
                }
            }
        } else if (command.getName().equalsIgnoreCase("report") && args.length == 2) {
            for (String[] r : REASONS) {
                out.add(r[0].split(" ")[0]);
            }
        } else if (command.getName().equalsIgnoreCase("reports") && args.length == 1) {
            out.add("dismiss");
        }

        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    private final class ListMenu extends Menu {
        ListMenu(Player viewer) {
            super(Reports.this.plugin, viewer);
        }

        @Override
        protected Component title() {
            return Text.mm("<dark_gray>Open Reports <red>(" + Reports.this.open.size() + ")");
        }

        @Override
        protected int rows() {
            return 6;
        }

        @Override
        protected void draw() {
            int[] slots = Ui.innerSlots(6);
            List<Reports.Report> list = new ArrayList<>(Reports.this.open);
            Collections.reverse(list);

            for (int i = 0; i < slots.length && i < list.size(); i++) {
                Reports.Report r = list.get(i);
                Player t = Bukkit.getPlayer(r.target());
                this.set(
                    slots[i],
                    Items.of(Material.PLAYER_HEAD)
                        .skull(Bukkit.getOfflinePlayer(r.target()))
                        .name((t != null ? "<green>● " : "<gray>○ ") + "<white><b>" + Text.esc(r.targetName()))
                        .lore(
                            "<gray>Reason: <yellow>" + Text.esc(r.reason()),
                            "<gray>By: <white>" + Text.esc(r.byName()),
                            "<gray>When: <white>" + Fmt.duration((System.currentTimeMillis() - r.at()) / 1000L) + " ago",
                            "<gray>Where: <white>" + Text.esc(r.where()),
                            "",
                            "<aqua>Click <gray>spectate / teleport",
                            "<dark_red>Right-click <gray>hacker pool (7d)",
                            "<gray>Shift-click <gray>dismiss"
                        )
                        .build(),
                    type -> {
                        if (type.isShiftClick()) {
                            Reports.this.open.remove(r);
                            Reports.this.save();
                            this.redraw();
                        } else if (type.isRightClick()) {
                            this.viewer.closeInventory();
                            this.viewer.performCommand("hackerpool " + r.targetName() + " 7d " + r.reason());
                            Reports.this.open.removeIf(o -> o.target().equals(r.target()));
                            Reports.this.save();
                        } else {
                            this.viewer.closeInventory();
                            if (t != null) {
                                this.viewer.performCommand("spectate " + t.getName());
                            } else {
                                this.viewer.sendMessage(Text.mm("<gray>" + Text.esc(r.targetName()) + " is offline."));
                            }
                        }
                    }
                );
            }

            this.set(49, Ui.close(), c -> this.viewer.closeInventory());
            this.border(Ui.FRAME);
        }
    }

    private final class ReasonMenu extends Menu {
        private final Player target;

        ReasonMenu(Player viewer, Player target) {
            super(Reports.this.plugin, viewer);
            this.target = target;
        }

        @Override
        protected Component title() {
            return Text.mm("<dark_gray>Report <red><b>" + Text.esc(this.target.getName()));
        }

        @Override
        protected int rows() {
            return 5;
        }

        @Override
        protected void draw() {
            int[] slots = Ui.centered(Reports.REASONS.length, 5);

            for (int i = 0; i < Reports.REASONS.length && i < slots.length; i++) {
                String reason = Reports.REASONS[i][0];
                Material m = Material.matchMaterial(Reports.REASONS[i][1]);
                this.set(
                    slots[i],
                    Items.of(m == null ? Material.PAPER : m)
                        .name("<red><b>" + reason)
                        .lore("<gray>Report " + Text.esc(this.target.getName()) + " for", "<white>" + reason, "", "<green>▶ Click to send")
                        .hideAll()
                        .build(),
                    c -> {
                        this.viewer.closeInventory();
                        if (this.target.isOnline()) {
                            Reports.this.submit(this.viewer, this.target, reason);
                        }
                    }
                );
            }

            this.set(40, Ui.close(), c -> this.viewer.closeInventory());
            this.border(Ui.FRAME);
        }
    }

    public record Report(int id, UUID target, String targetName, UUID by, String byName, String reason, long at, String where) {
    }
}
