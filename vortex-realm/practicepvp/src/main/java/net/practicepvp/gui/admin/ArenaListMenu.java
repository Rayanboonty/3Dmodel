package net.practicepvp.gui.admin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.practicepvp.PracticePlugin;
import net.practicepvp.arena.Arena;
import net.practicepvp.arena.ArenaInstance;
import net.practicepvp.arena.ArenaTheme;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Msg;
import net.practicepvp.config.Opt;
import net.practicepvp.dialog.Dialogs;
import net.practicepvp.gui.Menu;
import net.practicepvp.gui.Ui;
import net.practicepvp.kit.Kit;
import net.practicepvp.kit.KitManager;
import net.practicepvp.util.Items;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ArenaListMenu extends Menu {
    private int page;

    public ArenaListMenu(PracticePlugin plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected Component title() {
        return Text.mm("<gold><b>Admin</b></gold> <dark_gray>» <gray>Duel Arenas");
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void draw() {
        this.border(Ui.FRAME);
        List<Arena> list = new ArrayList<>(this.plugin.arenas().all());
        int[] slots = Ui.innerSlots(6);
        int start = this.page * slots.length;

        for (int i = 0; i < slots.length && start + i < list.size(); i++) {
            Arena a = list.get(start + i);
            long free = a.freeInstances();
            long used = a.instances.stream().filter(x -> x.state == ArenaInstance.State.IN_USE).count();
            List<String> lore = new ArrayList<>();
            lore.add("<gray>ID: <white>" + a.id);
            lore.add("<gray>Theme: <white>" + a.theme.display);
            lore.add("<gray>Instances: <green>" + free + " free <gray>/ <gold>" + used + " in use <gray>/ <white>" + a.instances.size() + " total");
            lore.add("<gray>Kits: <white>" + (a.kits.isEmpty() ? "all" : String.join(", ", a.kits)));
            lore.add("<gray>Enabled: " + (a.enabled ? "<green>yes" : "<red>no"));
            String pack = this.plugin.packState(a.id);
            if ("now".equals(pack) || this.plugin.arenas().isBusy(a)) {
                lore.add("<yellow>Generating now" + (this.plugin.packProgress() == null ? "" : " <gray>(arena pack " + this.plugin.packProgress() + ")"));
            }
            List<String> problems = a.problems();
            if (!problems.isEmpty()) {
                lore.add("");

                for (String p : problems) {
                    lore.add("<red>⚠ " + p);
                }
            }

            lore.add("");
            lore.add("<yellow>▶ Click to edit");
            Material icon = a.iconBlock.isItem() ? a.iconBlock : Material.GRASS_BLOCK;
            this.set(
                slots[i],
                Items.of(icon).name((a.ready() ? "<gold><b>" : "<red><b>") + Text.esc(a.displayName)).lore(lore).build(),
                c -> new ArenaEditMenu(this.plugin, this.viewer, a).open()
            );
        }

        this.set(
            48,
            Items.of(Material.LIME_CONCRETE)
                .name("<green><b>Create Arena Here")
                .lore("<gray>Empty arena in this world.", "<gray>Then set spawns and bounds.", "", "<yellow>▶ Click to name it")
                .build(),
            c -> createPrompt(this.plugin, this.viewer)
        );
        this.set(
            50,
            Items.of(Material.OAK_SAPLING)
                .name("<green><b>Generate Biome Arena")
                .lore("<gray>Plains, Badlands Mountains, Desert,", "<gray>Snowy, Cherry Grove and more.", "", "<yellow>▶ Click to configure")
                .build(),
            c -> generatePrompt(this.plugin, this.viewer, false)
        );
        this.set(
            46,
            Items.of(Material.FILLED_MAP)
                .name("<green><b>Generate All Missing Biomes")
                .lore(
                    "<gray>One map (with copies) for every biome",
                    "<gray>you don't have yet, so players get",
                    "<gray>more choices in the map ban.",
                    "",
                    "<yellow>▶ Click"
                )
                .hideAll()
                .build(),
            c -> {
                this.viewer.closeInventory();
                generateMissing(this.plugin, this.viewer);
            }
        );
        this.set(52, enableAllButton(), c -> {
            enableAll(this.plugin, this.viewer);
            this.redraw();
        });
        this.set(49, Ui.back(), c -> AdminMenu.openBest(this.plugin, this.viewer));
        if (this.page > 0) {
            this.set(45, Ui.prev(this.page), c -> {
                this.page--;
                this.redraw();
            });
        }

        if (start + slots.length < list.size()) {
            this.set(53, Ui.next(this.page), c -> {
                this.page++;
                this.redraw();
            });
        }
    }

    /** The "Enable All" button for the arena lists: switches on every complete duel and FFA arena. */
    static ItemStack enableAllButton() {
        return Items.of(Material.LIME_DYE)
            .name("<green><b>Enable All Arenas")
            .lore(
                "<gray>Switches on every finished duel and",
                "<gray>FFA arena that is set to off. Maps that",
                "<gray>are still generating turn on when done.",
                "",
                "<yellow>▶ Click"
            )
            .hideAll()
            .build();
    }

    static void enableAll(PracticePlugin plugin, Player p) {
        for (String line : plugin.arenas().enableAll(false).lines(plugin.packProgress())) {
            p.sendMessage(Text.mm(line));
        }
    }

    static String slug(String name) {
        String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        if (s.length() > 32) {
            s = s.substring(0, 32);
        }

        return s.isEmpty() ? "arena" : s;
    }

    static String uniqueId(PracticePlugin plugin, String base, boolean ffa) {
        String id = base;
        int n = 2;

        while (ffa ? plugin.ffa().exists(id) : plugin.arenas().exists(id)) {
            id = base + "_" + n++;
        }

        return id;
    }

    public static void generateMissing(PracticePlugin plugin, Player p) {
        Set<ArenaTheme> have = EnumSet.noneOf(ArenaTheme.class);

        for (Arena a : plugin.arenas().all()) {
            have.add(a.theme);
        }

        int n = 0;

        for (ArenaTheme t : ArenaTheme.values()) {
            if (t != ArenaTheme.CUSTOM && !have.contains(t)) {
                String id = uniqueId(plugin, t.name().toLowerCase(Locale.ROOT), false);
                long started = System.currentTimeMillis();
                plugin.arenas()
                    .generate(id, t.display, t, Cfg.i(Opt.DUEL_ARENA_SIZE), Cfg.i(Opt.DEFAULT_COPIES))
                    .whenComplete(
                        (ax, err) -> Tasks.ensureSync(
                            () -> Msg.send(
                                p,
                                err == null ? "admin.generated" : "admin.generate-failed",
                                "arena",
                                t.display,
                                "seconds",
                                (System.currentTimeMillis() - started) / 1000L
                            )
                        )
                    );
                Msg.send(p, "admin.generating", "arena", t.display);
                n++;
            }
        }

        // and an FFA arena for every FFA kit that has none
        for (net.practicepvp.kit.Kit k : plugin.kits().all()) {
            if (k.ffa && plugin.ffa().ensureArena(k)) {
                Msg.send(p, "admin.generating", "arena", k.displayName + " FFA");
                n++;
            }
        }

        if (n == 0) {
            Msg.send(p, "admin.all-biomes");
        }
    }

    public static void createPrompt(PracticePlugin plugin, Player p) {
        plugin.dialogs().text(p, Text.mm("<gold><b>Create Arena"), "Arena name (shown in the map ban)", "", 32, name -> {
            if (!name.isBlank()) {
                String id = uniqueId(plugin, slug(name), false);
                Arena a = plugin.arenas().create(id, name, p.getWorld());
                Msg.send(p, "admin.arena-created", "arena", name);
                new ArenaEditMenu(plugin, p, a).open();
            }
        });
    }

    public static void generatePrompt(PracticePlugin plugin, Player p, boolean ffa) {
        List<Dialogs.Choice> themes = new ArrayList<>();

        for (ArenaTheme t : ArenaTheme.values()) {
            if (t != ArenaTheme.CUSTOM) {
                themes.add(new Dialogs.Choice(t.name(), "<white>" + t.display));
            }
        }

        List<Dialogs.Choice> kits = new ArrayList<>();

        for (Kit k : plugin.kits().sorted()) {
            if (!ffa || k.ffa) {
                kits.add(new Dialogs.Choice(k.id, k.nameMM()));
            }
        }

        if (ffa && kits.isEmpty()) {
            for (Kit kx : plugin.kits().sorted()) {
                kits.add(new Dialogs.Choice(kx.id, kx.nameMM()));
            }
        }

        plugin.dialogs()
            .generator(
                p,
                ffa,
                themes,
                kits,
                (name, themeId, size, copies, kitId) -> {
                    ArenaTheme theme = ArenaTheme.parse(themeId);
                    String display = name != null && !name.isBlank()
                        ? name
                        : (ffa && kitId != null && plugin.kits().get(kitId) != null ? plugin.kits().get(kitId).displayName + " FFA" : theme.display);
                    String id = uniqueId(plugin, slug(display), ffa);
                    if (!KitManager.ID.matcher(id).matches()) {
                        id = uniqueId(plugin, ffa ? "ffa" : "arena", ffa);
                    }

                    Msg.send(p, "admin.generating", "arena", display);
                    long started = System.currentTimeMillis();
                    if (ffa) {
                        plugin.ffa()
                            .generate(id, display, theme, size, kitId)
                            .whenComplete(
                                (a, err) -> Tasks.ensureSync(
                                    () -> Msg.send(
                                        p,
                                        err == null ? "admin.generated" : "admin.generate-failed",
                                        "arena",
                                        display,
                                        "seconds",
                                        (System.currentTimeMillis() - started) / 1000L
                                    )
                                )
                            );
                    } else {
                        plugin.arenas()
                            .generate(id, display, theme, size, copies)
                            .whenComplete(
                                (a, err) -> Tasks.ensureSync(
                                    () -> Msg.send(
                                        p,
                                        err == null ? "admin.generated" : "admin.generate-failed",
                                        "arena",
                                        display,
                                        "seconds",
                                        (System.currentTimeMillis() - started) / 1000L
                                    )
                                )
                            );
                    }
                }
            );
    }
}
