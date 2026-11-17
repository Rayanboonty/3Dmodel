package net.practicepvp.gui.menus;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.practicepvp.PracticePlugin;
import net.practicepvp.data.PvPProfile;
import net.practicepvp.data.Setting;
import net.practicepvp.gui.Menu;
import net.practicepvp.gui.Ui;
import net.practicepvp.kit.Kit;
import net.practicepvp.util.Items;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

public final class SettingsMenu extends Menu {
    private Setting.Category tab;

    public SettingsMenu(PracticePlugin plugin, Player viewer, Setting.Category tab) {
        super(plugin, viewer);
        this.tab = tab;
    }

    @Override
    protected Component title() {
        return Text.mm("<gold><b>Settings</b></gold> <dark_gray>» <gray>" + this.tab.title);
    }

    @Override
    protected int rows() {
        return 6;
    }

    public static List<Setting.Option> options(PracticePlugin plugin, Setting s) {
        if (s != Setting.NAMETAG_KIT) {
            return s.staticOptions();
        } else {
            List<Setting.Option> out = new ArrayList<>(s.staticOptions());

            for (Kit k : plugin.kits().sorted()) {
                if (k.enabled && k.ranked) {
                    out.add(new Setting.Option(k.id, k.displayName));
                }
            }

            return out;
        }
    }

    @Override
    protected void draw() {
        this.border(Ui.FRAME);
        PvPProfile prof = this.plugin.profiles().get(this.viewer);
        int[] tabSlots = new int[]{1, 3, 5, 7};
        Setting.Category[] cats = Setting.Category.values();

        for (int i = 0; i < cats.length && i < tabSlots.length; i++) {
            Setting.Category c = cats[i];
            boolean sel = c == this.tab;
            this.set(
                tabSlots[i],
                Items.of(c.icon)
                    .name((sel ? "<green><b>» " : "<gold><b>") + c.title + (sel ? " «" : ""))
                    .lore(sel ? "<gray>Currently viewing" : "<yellow>▶ Click to view")
                    .hideAll()
                    .glint(sel)
                    .build(),
                t -> {
                    this.tab = c;
                    this.redraw();
                }
            );
        }

        int[] slots = Ui.innerSlots(6);
        int idx = 0;

        for (Setting s : Setting.values()) {
            if (s.category == this.tab && idx < slots.length) {
                this.set(slots[idx++], this.settingItem(s, prof), type -> this.cycle(s, prof, type));
            }
        }

        this.set(49, Ui.close(), t -> this.viewer.closeInventory());
        this.set(
            45,
            Items.of(Material.PLAYER_HEAD)
                .skull(this.viewer)
                .name("<gold><b>" + Text.esc(this.viewer.getName()))
                .lore("<gray>Your stats and ranks", "", "<yellow>▶ Click to open")
                .build(),
            t -> new StatsMenu(this.plugin, this.viewer, this.viewer.getUniqueId(), this.viewer.getName()).open()
        );
    }

    private ItemStack settingItem(Setting s, PvPProfile prof) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + s.description);
        lore.add("");
        String current = prof.get(s);
        List<Setting.Option> opts = options(this.plugin, s);
        int shown = 0;

        for (Setting.Option o : opts) {
            if (shown++ >= 10) {
                lore.add("<dark_gray>  ...");
                break;
            }

            boolean sel = o.id().equals(current);
            lore.add(sel ? "<green><b>▶ " + Text.esc(o.label()) : "<gray>  " + Text.esc(o.label()));
        }

        if (s == Setting.LOBBY_FLY && !this.viewer.hasPermission("practice.lobbyfly")) {
            lore.add("");
            lore.add("<red>Requires the practice.lobbyfly permission");
        }

        lore.add("");
        lore.add("<yellow>Click <gray>next  <yellow>Right-click <gray>previous");
        lore.add("<yellow>Shift-click <gray>reset to default");
        return Items.of(s.icon).name("<gold><b>" + s.title).lore(lore).hideAll().build();
    }

    private void cycle(Setting s, PvPProfile prof, ClickType type) {
        List<Setting.Option> opts = options(this.plugin, s);
        if (!opts.isEmpty()) {
            String value;
            if (type.isShiftClick()) {
                value = s.defaultValue();
            } else {
                int i = 0;

                for (int j = 0; j < opts.size(); j++) {
                    if (opts.get(j).id().equals(prof.get(s))) {
                        i = j;
                    }
                }

                int dir = type.isRightClick() ? -1 : 1;
                i = Math.floorMod(i + dir, opts.size());
                value = opts.get(i).id();
            }

            if (apply(this.plugin, this.viewer, s, value)) {
                this.redraw();
            }
        }
    }

    public static String step(PracticePlugin plugin, PvPProfile prof, Setting s, int dir) {
        List<Setting.Option> opts = options(plugin, s);
        int i = 0;

        for (int j = 0; j < opts.size(); j++) {
            if (opts.get(j).id().equals(prof.get(s))) {
                i = j;
            }
        }

        return opts.get(Math.floorMod(i + dir, opts.size())).id();
    }

    public static boolean apply(PracticePlugin plugin, Player viewer, Setting s, String value) {
        if (s == Setting.LOBBY_FLY && "on".equals(value) && !viewer.hasPermission("practice.lobbyfly")) {
            Snd.error(viewer);
            return false;
        } else if ((s == Setting.KILL_EFFECT || s == Setting.TRAIL) && !"none".equals(value) && !viewer.hasPermission("practice.cosmetics")) {
            Snd.error(viewer);
            return false;
        } else {
            plugin.profiles().get(viewer).set(s, value);
            switch (s) {
                case LOBBY_TIME:
                case LOBBY_FLY:
                case DOUBLE_JUMP:
                case LOBBY_SPEED:
                    plugin.lobby().applyComforts(viewer);
                    break;
                case LOBBY_PLAYERS:
                    plugin.lobby().refreshVisibility(viewer);
                    break;
                case NAMETAG_KIT:
                    plugin.boards().refreshNametag(viewer);
            }

            return true;
        }
    }
}
