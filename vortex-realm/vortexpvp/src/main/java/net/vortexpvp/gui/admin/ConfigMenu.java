package net.vortexpvp.gui.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Msg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.gui.Menu;
import net.vortexpvp.gui.Ui;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

public final class ConfigMenu extends Menu {
    private final Opt.Section section;

    public ConfigMenu(VortexPvP plugin, Player viewer, Opt.Section section) {
        super(plugin, viewer);
        this.section = section;
    }

    @Override
    protected Component title() {
        return Text.mm("<gold><b>Settings</b></gold>" + (this.section == null ? "" : " <dark_gray>» <gray>" + this.section.title));
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void draw() {
        this.border(Ui.FRAME);
        int[] slots = Ui.innerSlots(6);
        if (this.section == null) {
            int i = 0;

            for (Opt.Section s : Opt.Section.values()) {
                long count = Opt.all().stream().filter(ox -> ox.gui && ox.section == s).count();
                if (count != 0L) {
                    this.set(
                        slots[i++],
                        Items.of(s.icon).name("<gold><b>" + s.title).lore("<gray>" + count + " options", "", "<yellow>▶ Click to open").hideAll().build(),
                        c -> new ConfigMenu(this.plugin, this.viewer, s).open()
                    );
                }
            }

            this.set(49, Ui.back(), c -> AdminMenu.openBest(this.plugin, this.viewer));
        } else {
            int i = 0;

            for (Opt<?> o : Opt.all()) {
                if (o.gui && o.section == this.section && i < slots.length) {
                    this.set(slots[i++], this.item(o), type -> this.click(o, type));
                }
            }

            this.set(49, Ui.back(), c -> new ConfigMenu(this.plugin, this.viewer, null).open());
        }
    }

    private ItemStack item(Opt<?> o) {
        Object v = Cfg.get(o);
        List<String> lore = new ArrayList<>();

        for (String line : wrap(o.desc, 34)) {
            lore.add("<gray>" + Text.esc(line));
        }

        lore.add("");
        lore.add("<gray>Value: " + valueText(o, v));
        if (!v.equals(o.def)) {
            lore.add("<dark_gray>Default: " + Text.esc(String.valueOf(o.def)));
        }

        lore.add("");
        switch (o.type) {
            case BOOL:
                lore.add("<yellow>▶ Click to toggle");
                break;
            case INT:
            case DOUBLE:
                lore.add("<yellow>Click <gray>+" + fmt(o, o.step) + "  <yellow>Right-click <gray>-" + fmt(o, o.step));
                lore.add("<yellow>Shift <gray>x10  <yellow>Q <gray>type exact value");
                break;
            case ENUM:
                lore.add("<yellow>Click <gray>next  <yellow>Right-click <gray>previous");
                break;
            default:
                lore.add("<yellow>▶ Click to edit");
        }

        lore.add("<dark_gray>" + o.path);
        boolean glint = o.type == Opt.Type.BOOL && Boolean.TRUE.equals(v);
        return Items.of(o.icon).name("<gold><b>" + Text.esc(o.name)).lore(lore).hideAll().glint(glint).build();
    }

    private static String fmt(Opt<?> o, double d) {
        return o.type == Opt.Type.INT ? String.valueOf((int)d) : String.format(Locale.US, "%.2f", d);
    }

    private static String valueText(Opt<?> o, Object v) {
        return switch (o.type) {
            case BOOL -> Boolean.TRUE.equals(v) ? "<green><b>ON" : "<red><b>OFF";
            case STRING -> "<white>" + Text.esc(String.valueOf(v));
            default -> "<white>" + Text.esc(String.valueOf(v));
        };
    }

    private static List<String> wrap(String s, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();

        for (String word : s.split(" ")) {
            if (line.length() + word.length() + 1 > width && !line.isEmpty()) {
                out.add(line.toString());
                line = new StringBuilder();
            }

            if (!line.isEmpty()) {
                line.append(' ');
            }

            line.append(word);
        }

        if (!line.isEmpty()) {
            out.add(line.toString());
        }

        return out;
    }

    private void click(Opt<?> o, ClickType type) {
        Object v = Cfg.get(o);
        switch (o.type) {
            case BOOL:
                Cfg.set(o, !Boolean.TRUE.equals(v));
                break;
            case INT:
            case DOUBLE:
                if (type == ClickType.DROP || type == ClickType.CONTROL_DROP || type == ClickType.MIDDLE) {
                    this.exact(o);
                    return;
                }

                double step = o.step * (type.isShiftClick() ? 10 : 1) * (type.isRightClick() ? -1 : 1);
                double next = ((Number)v).doubleValue() + step;
                next = Math.max(o.min, Math.min(o.max, next));
                Cfg.set(o, o.type == Opt.Type.INT ? (Object)Integer.valueOf((int)Math.round(next)) : (Object)Double.valueOf(next));
                break;
            case ENUM:
                Enum<?>[] values = (Enum<?>[])o.enumClass.getEnumConstants();
                int i = 0;

                for (int j = 0; j < values.length; j++) {
                    if (values[j].name().equals(v)) {
                        i = j;
                    }
                }

                i = Math.floorMod(i + (type.isRightClick() ? -1 : 1), values.length);
                Cfg.set(o, values[i].name());
                break;
            case STRING:
                this.plugin.dialogs().text(this.viewer, Text.mm("<gold><b>" + Text.esc(o.name)), o.name, String.valueOf(v), 256, s -> {
                    Cfg.set(o, s);
                    applied(this.plugin, o);
                    new ConfigMenu(this.plugin, this.viewer, this.section).open();
                });
                return;
            case INT_LIST:
                List<Integer> list = (List<Integer>)v;
                StringBuilder sb = new StringBuilder();

                for (Integer n : list) {
                    if (!sb.isEmpty()) {
                        sb.append(", ");
                    }

                    sb.append(n);
                }

                this.plugin
                    .dialogs()
                    .text(
                        this.viewer,
                        Text.mm("<gold><b>" + Text.esc(o.name)),
                        "Comma separated, " + list.size() + " values, increasing",
                        sb.toString(),
                        256,
                        s -> {
                            List<Integer> parsed = new ArrayList<>();

                            try {
                                for (String part : s.split(",")) {
                                    parsed.add(Integer.parseInt(part.trim()));
                                }
                            } catch (NumberFormatException var9x) {
                                Msg.error(this.viewer, "admin.bad-number");
                                new ConfigMenu(this.plugin, this.viewer, this.section).open();
                                return;
                            }

                            if (parsed.size() != list.size()) {
                                Msg.error(this.viewer, "admin.bad-list", "count", list.size());
                            } else {
                                Cfg.set(o, parsed);
                                applied(this.plugin, o);
                            }

                            new ConfigMenu(this.plugin, this.viewer, this.section).open();
                        }
                    );
                return;
            default:
                return;
        }

        applied(this.plugin, o);
        this.redraw();
    }

    private void exact(Opt<?> o) {
        this.plugin
            .dialogs()
            .text(
                this.viewer,
                Text.mm("<gold><b>" + Text.esc(o.name)),
                "Value (" + fmt(o, o.min) + " - " + fmt(o, o.max) + ")",
                String.valueOf(Cfg.get(o)),
                20,
                s -> {
                    try {
                        double d = Double.parseDouble(s.trim().replace(',', '.'));
                        d = Math.max(o.min, Math.min(o.max, d));
                        Cfg.set(o, o.type == Opt.Type.INT ? (int)Math.round(d) : d);
                        applied(this.plugin, o);
                    } catch (NumberFormatException var5) {
                        Msg.error(this.viewer, "admin.bad-number");
                    }

                    new ConfigMenu(this.plugin, this.viewer, this.section).open();
                }
            );
    }

    public static void applied(VortexPvP plugin, Opt<?> o) {
        if (o == Opt.NAMETAG_RANKS || o == Opt.SPRITE_ICONS || o == Opt.PLAYER_COLLISION) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                plugin.boards().refreshNametag(p);
            }
        }

        if (o == Opt.FFA_ENABLED && !Cfg.bool(Opt.FFA_ENABLED)) {
            plugin.ffa().kickAll();
        }

        if (o == Opt.LOBBY_DOUBLE_JUMP || o == Opt.LOBBY_SPEED) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                plugin.lobby().applyComforts(p);
            }
        }
    }
}
