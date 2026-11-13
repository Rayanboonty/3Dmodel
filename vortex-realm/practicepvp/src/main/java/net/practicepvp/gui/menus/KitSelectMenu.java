package net.practicepvp.gui.menus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.practicepvp.PracticePlugin;
import net.practicepvp.config.Msg;
import net.practicepvp.data.PvPProfile;
import net.practicepvp.gui.Menu;
import net.practicepvp.gui.Ui;
import net.practicepvp.kit.Kit;
import net.practicepvp.kit.KitCategory;
import net.practicepvp.util.Items;
import net.practicepvp.util.Text;
import org.bukkit.entity.Player;

public final class KitSelectMenu extends Menu {
    private final Component title;
    private final Predicate<Kit> filter;
    private final Consumer<Kit> onPick;
    private final Runnable onBack;
    private final boolean showLayouts;

    public KitSelectMenu(
        PracticePlugin plugin, Player viewer, Component title, Predicate<Kit> filter, Consumer<Kit> onPick, Runnable onBack, boolean showLayouts
    ) {
        super(plugin, viewer);
        this.title = title;
        this.filter = filter;
        this.onPick = onPick;
        this.onBack = onBack;
        this.showLayouts = showLayouts;
    }

    public static void openEditor(PracticePlugin plugin, Player p) {
        new KitSelectMenu(
                plugin,
                p,
                Text.mm("<gold><b>Kit Editor</b></gold> <dark_gray>» <gray>Pick a kit"),
                k -> k.enabled && k.hasContents(),
                k -> KitEditorMenu.start(plugin, p, k),
                null,
                true
            )
            .open();
    }

    @Override
    protected Component title() {
        return this.title;
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void draw() {
        this.border(Ui.FRAME);
        int[] slots = Ui.fit((int)this.plugin.kits().sorted().stream().filter(this.filter).count(), 6);
        int i = 0;
        PvPProfile prof = this.plugin.profiles().get(this.viewer);

        for (KitCategory cat : KitCategory.values()) {
            for (Kit k : this.plugin.kits().sorted()) {
                if (k.category == cat && this.filter.test(k) && i < slots.length) {
                    List<String> lore = new ArrayList<>();
                    lore.add(cat.color + cat.title);

                    for (String d : k.description) {
                        lore.add("<gray>" + Text.esc(d));
                    }

                    if (this.showLayouts) {
                        boolean custom = this.plugin.kits().usable(k, prof.layouts.get(k.id));
                        lore.add("");
                        lore.add("<gray>Layout: " + (custom ? "<green>Custom" : "<white>Default"));
                        lore.add("");
                        lore.add("<yellow>▶ Click to edit");
                        if (custom) {
                            lore.add("<red>Shift-click to reset to default");
                        }
                    } else {
                        lore.add("");
                        lore.add("<yellow>▶ Click to select");
                    }

                    this.set(slots[i++], Items.of(k.iconStack()).name(k.color + "<b>" + Text.esc(k.displayName)).lore(lore).hideAll().build(), type -> {
                        if (this.showLayouts && type.isShiftClick() && prof.layouts.containsKey(k.id)) {
                            prof.layouts.remove(k.id);
                            this.plugin.db().run(c -> this.plugin.db().deleteLayout(c, this.viewer.getUniqueId(), k.id));
                            Msg.send(this.viewer, "kit-editor.reset", "kit", k.label());
                            this.redraw();
                        } else {
                            this.viewer.closeInventory();
                            this.onPick.accept(k);
                        }
                    });
                }
            }
        }

        if (this.onBack != null) {
            this.set(49, Ui.back(), t -> this.onBack.run());
        } else {
            this.set(49, Ui.close(), t -> this.viewer.closeInventory());
        }
    }
}
