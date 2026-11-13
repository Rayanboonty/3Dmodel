package net.vortexrealm.gui.admin;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.config.Msg;
import net.vortexrealm.dialog.Dialogs;
import net.vortexrealm.gui.Menu;
import net.vortexrealm.gui.Ui;
import net.vortexrealm.kit.Kit;
import net.vortexrealm.kit.KitCategory;
import net.vortexrealm.kit.KitManager;
import net.vortexrealm.kit.KitVote;
import net.vortexrealm.util.Items;
import net.vortexrealm.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class KitListMenu extends Menu {
    public KitListMenu(VortexRealm plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected Component title() {
        return Text.mm("<gold><b>Admin</b></gold> <dark_gray>» <gray>Kits");
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void draw() {
        this.border(Ui.FRAME);
        int[] slots = Ui.innerSlots(6);
        int i = 0;

        for (Kit k : this.plugin.kits().sorted()) {
            if (i >= slots.length) {
                break;
            }

            this.set(
                slots[i++],
                Items.of(k.iconStack())
                    .name((k.enabled ? "" : "<red>[off] ") + k.color + "<b>" + Text.esc(k.displayName))
                    .lore(
                        "<gray>ID: <white>" + k.id,
                        "<gray>Tab: " + k.category.color + k.category.title,
                        "<gray>Ranked " + yn(k.ranked) + " <gray>Unranked " + yn(k.unranked) + " <gray>FFA " + yn(k.ffa),
                        "<gray>Build " + yn(k.build) + " <gray>Regen " + yn(k.regen) + " <gray>Hunger " + yn(k.hunger),
                        "",
                        "<yellow>▶ Click to edit"
                    )
                    .hideAll()
                    .build(),
                c -> new KitListMenu.KitEditMenu(this.plugin, this.viewer, k).open()
            );
        }

        this.set(
            48,
            Items.of(Material.LIME_CONCRETE)
                .name("<green><b>Create Kit From My Inventory")
                .lore("<gray>Copies your inventory, armor and", "<gray>offhand into a new kit.")
                .build(),
            c -> createPrompt(this.plugin, this.viewer)
        );
        this.set(
            50,
            Items.of(Material.WRITABLE_BOOK)
                .name("<green><b>Copy A Kit")
                .lore("<gray>Start your own kit from an existing", "<gray>one: items, rules, maps and votes.")
                .build(),
            c -> copyPrompt(this.plugin, this.viewer)
        );
        this.set(49, Ui.back(), c -> AdminMenu.openBest(this.plugin, this.viewer));
    }

    public static void copyPrompt(VortexRealm plugin, Player p) {
        List<Dialogs.Choice> kits = new ArrayList<>();

        for (Kit k : plugin.kits().sorted()) {
            kits.add(new Dialogs.Choice(k.id, k.color + Text.esc(k.displayName)));
        }

        if (kits.isEmpty()) {
            return;
        }

        plugin.dialogs().choice(p, Text.mm("<gold><b>Copy A Kit"), "Copy from", kits, kits.get(0).id(), from -> {
            Kit src = plugin.kits().get(from);
            if (src != null) {
                plugin.dialogs().text(p, Text.mm("<gold><b>New Kit Name"), "Kit name", src.displayName + " 2", 24, name -> {
                    String id = ArenaListMenu.slug(name);
                    if (name.isBlank() || !KitManager.ID.matcher(id).matches()) {
                        Msg.error(p, "admin.bad-name");
                    } else if (plugin.kits().get(id) != null) {
                        Msg.error(p, "admin.kit-exists", "kit", id);
                    } else {
                        Kit k = plugin.kits().copy(src, id, name);
                        Msg.send(p, "admin.kit-created", "kit", k.label());
                        new KitListMenu.KitEditMenu(plugin, p, k).open();
                    }
                });
            }
        });
    }

    private static String yn(boolean b) {
        return b ? "<green>✔" : "<red>✘";
    }

    public static void createPrompt(VortexRealm plugin, Player p) {
        plugin.dialogs().text(p, Text.mm("<gold><b>Create Kit"), "Kit name", "", 24, name -> {
            if (!name.isBlank()) {
                String id = ArenaListMenu.slug(name);
                if (!KitManager.ID.matcher(id).matches()) {
                    Msg.error(p, "admin.bad-name");
                } else if (plugin.kits().get(id) != null) {
                    Msg.error(p, "admin.kit-exists", "kit", id);
                } else {
                    Kit k = plugin.kits().create(id, name, p);
                    Msg.send(p, "admin.kit-created", "kit", k.label());
                    new KitListMenu.KitEditMenu(plugin, p, k).open();
                }
            }
        });
    }

    public static final class KitEditMenu extends Menu {
        private final Kit k;

        public KitEditMenu(VortexRealm plugin, Player viewer, Kit k) {
            super(plugin, viewer);
            this.k = k;
        }

        @Override
        protected Component title() {
            return Text.mm("<gold><b>Kit</b></gold> <dark_gray>» ").append(this.k.name());
        }

        @Override
        protected int rows() {
            return 6;
        }

        private void changed() {
            this.plugin.kits().save();
            this.plugin.lobby().refreshVisibilityAll();
            this.redraw();
        }

        @Override
        protected void draw() {
            this.border(Ui.FRAME);
            this.set(
                4,
                Items.of(this.k.iconStack())
                    .name(this.k.color + "<b>" + Text.esc(this.k.displayName))
                    .lore("<gray>ID: <white>" + this.k.id, "<gray>Version: <white>" + this.k.version, "<dark_gray>(changing contents resets player layouts)")
                    .hideAll()
                    .build()
            );
            this.set(
                10,
                Items.of(Material.CHEST)
                    .name("<gold><b>Edit Contents")
                    .lore("<gray>Inventory, hotbar, <white>armor</white> and offhand.", "", "<yellow>▶ Click to open")
                    .build(),
                c -> new KitContentsMenu(this.plugin, this.viewer, this.k).open()
            );
            this.set(
                11,
                Items.of(Material.HOPPER)
                    .name("<gold><b>Save From My Inventory")
                    .lore("<gray>Replace the contents with what", "<gray>you're wearing and carrying.")
                    .build(),
                c -> this.plugin
                    .dialogs()
                    .confirm(
                        this.viewer,
                        Text.mm("<gold><b>Overwrite kit contents?"),
                        List.of(Text.mm("<gray>Your current inventory, armor and offhand"), Text.mm("<gray>become the contents of ").append(this.k.label())),
                        () -> {
                            this.k.contents = KitManager.copyFrom(this.viewer.getInventory());
                            this.k.bumpVersion();
                            this.plugin.kits().save();
                            Msg.send(this.viewer, "admin.kit-saved", "kit", this.k.label());
                            new KitListMenu.KitEditMenu(this.plugin, this.viewer, this.k).open();
                        }
                    )
            );
            this.set(
                12, Items.of(Material.DROPPER).name("<gold><b>Load Into My Inventory").lore("<gray>Try the kit out (replaces your inventory).").build(), c -> {
                    this.viewer.closeInventory();
                    this.plugin.kits().apply(this.viewer, this.k, null, List.of());
                }
            );
            this.set(
                13,
                Items.of(Material.NAME_TAG).name("<gold><b>Rename").build(),
                c -> this.plugin.dialogs().text(this.viewer, Text.mm("<gold><b>Rename Kit"), "Display name", this.k.displayName, 24, n -> {
                    if (!n.isBlank()) {
                        this.k.displayName = n;
                        this.plugin.kits().save();
                    }

                    new KitListMenu.KitEditMenu(this.plugin, this.viewer, this.k).open();
                })
            );
            ItemStack held = this.viewer.getInventory().getItemInMainHand();
            this.set(14, Items.of(Material.ITEM_FRAME).name("<gold><b>Icon From Held Item").lore("<gray>Shown in the queue menu.").build(), c -> {
                if (held.getType().isAir()) {
                    Msg.error(this.viewer, "admin.hold-item");
                } else {
                    ItemStack icon = held.clone();
                    icon.setAmount(1);
                    this.k.icon = icon;
                    this.changed();
                }
            });
            this.set(
                15,
                Items.of(Material.PAINTING)
                    .name("<gold><b>Sprite: <white>" + Text.esc(String.valueOf(this.k.sprite)))
                    .lore(
                        "<gray>Icon shown next to names and in chat.",
                        "<gray>Examples: <white>item/mace, item/end_crystal,",
                        "<white>gui:hud/heart/full",
                        "",
                        "<yellow>▶ Click to change"
                    )
                    .build(),
                c -> this.plugin.dialogs().text(this.viewer, Text.mm("<gold><b>Kit Sprite"), "Sprite (item/<name> or gui:<sprite>)", this.k.sprite, 64, vx -> {
                    this.k.sprite = vx;
                    this.plugin.kits().save();
                    new KitListMenu.KitEditMenu(this.plugin, this.viewer, this.k).open();
                })
            );
            this.set(
                16,
                Items.of(Material.ORANGE_DYE)
                    .name("<gold><b>Name Colour")
                    .lore("<gray>Current: " + this.k.color + Text.esc(this.k.displayName), "", "<yellow>▶ Click to change")
                    .build(),
                c -> {
                    List<Dialogs.Choice> colors = new ArrayList<>();

                    for (String col : List.of(
                        "gold", "yellow", "green", "dark_green", "aqua", "dark_aqua", "blue", "light_purple", "dark_purple", "red", "dark_red", "white", "gray"
                    )) {
                        colors.add(new Dialogs.Choice("<" + col + ">", "<" + col + ">" + Text.esc(this.k.displayName)));
                    }

                    this.plugin.dialogs().choice(this.viewer, Text.mm("<gold><b>Name Colour"), "Colour", colors, this.k.color, vx -> {
                        this.k.color = vx;
                        this.plugin.kits().save();
                        new KitListMenu.KitEditMenu(this.plugin, this.viewer, this.k).open();
                    });
                }
            );
            this.set(
                19,
                Items.of(Material.BOOKSHELF)
                    .name("<gold><b>Queue Tab: " + this.k.category.color + this.k.category.title)
                    .lore("<yellow>▶ Click to cycle")
                    .build(),
                c -> {
                    KitCategory[] vx = KitCategory.values();
                    this.k.category = vx[(this.k.category.ordinal() + 1) % vx.length];
                    this.changed();
                }
            );
            this.set(20, Ui.toggle(Material.NETHERITE_SWORD, "Ranked", this.k.ranked, "Available in the ranked queue."), c -> {
                this.k.ranked = !this.k.ranked;
                this.changed();
            });
            this.set(21, Ui.toggle(Material.DIAMOND_SWORD, "Unranked", this.k.unranked, "Available in the unranked queue."), c -> {
                this.k.unranked = !this.k.unranked;
                this.changed();
            });
            this.set(22, Ui.toggle(Material.NETHER_STAR, "FFA Kit", this.k.ffa, "Can be used by FFA arenas."), c -> {
                this.k.ffa = !this.k.ffa;
                if (this.plugin.ffa().ensureArena(this.k)) {
                    this.viewer.sendMessage(Text.mm("<green>Building an FFA arena for " + Text.esc(this.k.displayName) + "..."));
                }

                this.changed();
            });
            this.set(23, Ui.toggle(Material.LIME_DYE, "Enabled", this.k.enabled, "Disabled kits disappear everywhere."), c -> {
                this.k.enabled = !this.k.enabled;
                if (!this.k.enabled) {
                    this.plugin.queues().purgeKit(this.k.id);
                }

                this.changed();
            });
            this.set(24, Ui.toggle(Material.BRICKS, "Building", this.k.build, "Place/break blocks (crystal, UHC...)."), c -> {
                this.k.build = !this.k.build;
                this.changed();
            });
            this.set(25, Ui.toggle(Material.GOLDEN_APPLE, "Natural Regen", this.k.regen, "Off = UHC style."), c -> {
                this.k.regen = !this.k.regen;
                this.changed();
            });
            this.set(28, Ui.toggle(Material.COOKED_BEEF, "Hunger", this.k.hunger, "Off = food bar never drops."), c -> {
                this.k.hunger = !this.k.hunger;
                this.changed();
            });
            this.set(
                29,
                Items.of(Material.TARGET)
                    .name("<gold><b>Rounds To Win: <white>" + (this.k.roundsToWin > 0 ? this.k.roundsToWin : "default (" + this.k.rounds() + ")"))
                    .lore("<yellow>▶ Click to set")
                    .build(),
                c -> this.plugin
                    .dialogs()
                    .number(this.viewer, Text.mm("<gold><b>Rounds To Win"), "First to (0 = default)", 0.0F, 10.0F, 1.0F, this.k.roundsToWin, vx -> {
                        this.k.roundsToWin = Math.round(vx);
                        this.plugin.kits().save();
                        new KitListMenu.KitEditMenu(this.plugin, this.viewer, this.k).open();
                    })
            );
            this.set(
                30,
                Items.of(Material.LADDER)
                    .name("<gold><b>Build Height: <white>" + (this.k.buildHeight > 0 ? this.k.buildHeight : "default"))
                    .lore("<yellow>▶ Click to set")
                    .build(),
                c -> this.plugin
                    .dialogs()
                    .number(this.viewer, Text.mm("<gold><b>Build Height"), "Blocks (0 = default)", 0.0F, 128.0F, 1.0F, Math.max(0, this.k.buildHeight), vx -> {
                        this.k.buildHeight = Math.round(vx) <= 0 ? -1 : Math.round(vx);
                        this.plugin.kits().save();
                        new KitListMenu.KitEditMenu(this.plugin, this.viewer, this.k).open();
                    })
            );
            KitVote totemVote = null;

            for (KitVote v : this.k.votes) {
                if (v.material() == Material.TOTEM_OF_UNDYING && v.action() == KitVote.Action.LIMIT) {
                    totemVote = v;
                }
            }

            KitVote currentVote = totemVote;
            this.set(
                31,
                Items.of(Material.TOTEM_OF_UNDYING)
                    .name("<gold><b>Totem Limit Vote: <white>" + (currentVote == null ? "off" : currentVote.amount()))
                    .lore("<gray>Pre-match vote 'Limit to N Totems'.", "<gray>Other votes: kits.yml.", "", "<yellow>▶ Click to set (0 = off)")
                    .build(),
                c -> this.plugin
                    .dialogs()
                    .number(
                        this.viewer,
                        Text.mm("<gold><b>Totem Limit Vote"),
                        "Totems (0 = off)",
                        0.0F,
                        30.0F,
                        1.0F,
                        currentVote == null ? 0.0F : currentVote.amount(),
                        vx -> {
                            this.k.votes.removeIf(x -> x.material() == Material.TOTEM_OF_UNDYING && x.action() == KitVote.Action.LIMIT);
                            int n = Math.round(vx);
                            if (n > 0) {
                                this.k.votes.add(new KitVote("limit_totems", "Limit to " + n + " Totems", KitVote.Action.LIMIT, Material.TOTEM_OF_UNDYING, n));
                            }

                            this.plugin.kits().save();
                            new KitListMenu.KitEditMenu(this.plugin, this.viewer, this.k).open();
                        }
                    )
            );
            this.set(
                33,
                Items.of(Material.WIND_CHARGE)
                    .name("<gold><b>Throw Between Rounds: <white>" + this.k.throwMode)
                    .lore(
                        "<gray>AUTO follows Settings → Matches",
                        "<gray>(now: " + (this.k.throwBetweenRounds() ? "<green>throws" : "<red>teleports") + "<gray>).",
                        "<gray>ON / OFF for this kit only.",
                        "",
                        "<yellow>▶ Click to cycle"
                    )
                    .build(),
                c -> {
                    Kit var10000 = this.k;
                    String var2x = this.k.throwMode;

                    var10000.throwMode = switch (var2x) {
                        case "AUTO" -> "ON";
                        case "ON" -> "OFF";
                        default -> "AUTO";
                    };
                    this.changed();
                }
            );
            this.set(
                32,
                Items.of(Material.COMPARATOR)
                    .name("<gold><b>Order: <white>" + this.k.order)
                    .lore("<gray>Position inside its tab.", "<gray>Click +1, right-click -1")
                    .build(),
                type -> {
                    this.k.order = this.k.order + (type.isRightClick() ? -1 : 1);
                    this.changed();
                }
            );
            this.set(
                34,
                Items.of(Material.TNT).name("<red><b>Delete Kit").build(),
                c -> this.plugin
                    .dialogs()
                    .confirm(
                        this.viewer,
                        Text.mm("<red><b>Delete kit " + Text.esc(this.k.displayName) + "?"),
                        List.of(Text.mm("<gray>Players' ranks for this kit stay in the database.")),
                        () -> {
                            this.plugin.queues().purgeKit(this.k.id);
                            this.plugin.kits().delete(this.k.id);
                            Msg.send(this.viewer, "admin.kit-deleted", "kit", this.k.displayName);
                            new KitListMenu(this.plugin, this.viewer).open();
                        }
                    )
            );
            this.set(
                37,
                Items.of(Material.PISTON)
                    .name("<gold><b>Knockback: <white>" + this.k.knockback + "%")
                    .lore("<gray>100% = vanilla. Lower = combo style,", "<gray>higher = pushes further.", "", "<yellow>▶ Click to set")
                    .build(),
                c -> this.number("Knockback", "Percent (100 = vanilla)", 0.0F, 300.0F, 5.0F, this.k.knockback, v -> this.k.knockback = Math.round(v))
            );
            this.set(
                38,
                Items.of(Material.CLOCK)
                    .name("<gold><b>Hit Delay: <white>" + (this.k.hitDelay >= 0 ? this.k.hitDelay + " ticks" : "default (" + this.k.hitDelayTicks() + ")"))
                    .lore("<gray>Ticks between hits. 20 = vanilla,", "<gray>low values = combo kits.", "", "<yellow>▶ Click to set (-1 = default)")
                    .build(),
                c -> this.number("Hit Delay", "Ticks (-1 = default)", -1.0F, 20.0F, 1.0F, this.k.hitDelay, v -> this.k.hitDelay = Math.round(v))
            );
            this.set(
                39,
                Items.of(Material.ENDER_PEARL)
                    .name("<gold><b>Pearl Cooldown: <white>" + (this.k.pearlCooldown >= 0 ? this.k.pearlCooldown + " ticks" : "default"))
                    .lore("<gray>20 ticks = 1 second.", "", "<yellow>▶ Click to set (-1 = default)")
                    .build(),
                c -> this.number("Pearl Cooldown", "Ticks (-1 = default)", -1.0F, 400.0F, 5.0F, this.k.pearlCooldown, v -> this.k.pearlCooldown = Math.round(v))
            );
            this.set(
                40,
                Items.of(Material.WIND_CHARGE)
                    .name("<gold><b>Wind Charge Cooldown: <white>" + (this.k.windCooldown >= 0 ? this.k.windCooldown + " ticks" : "default"))
                    .lore("<gray>20 ticks = 1 second.", "", "<yellow>▶ Click to set (-1 = default)")
                    .build(),
                c -> this.number("Wind Charge Cooldown", "Ticks (-1 = default)", -1.0F, 400.0F, 5.0F, this.k.windCooldown, v -> this.k.windCooldown = Math.round(v))
            );
            this.set(
                41,
                Items.of(Material.FILLED_MAP)
                    .name("<gold><b>Maps: <white>" + this.k.arenaType)
                    .lore("<gray>Which maps this kit plays on.", "<gray>Now: <white>" + this.k.arenaKind(), "", "<yellow>▶ Click to choose")
                    .build(),
                c -> {
                    List<Dialogs.Choice> opts = new ArrayList<>();

                    for (String[] o : new String[][]{
                        {"AUTO", "Auto (from the kit's items)"},
                        {"VANILLA", "Vanilla biome maps"},
                        {"DESIGNED", "Show arenas + colosseums"},
                        {"VANILLA,DESIGNED", "Vanilla + designed"},
                        {"CRYSTAL", "Deep crystal maps"},
                        {"MACE", "Big mace maps"},
                        {"MOLE", "Mole Hollow"},
                        {"ANY", "Any map"}
                    }) {
                        opts.add(new Dialogs.Choice(o[0], o[1]));
                    }

                    this.plugin.dialogs().choice(this.viewer, Text.mm("<gold><b>Kit Maps"), "Maps", opts, this.k.arenaType, vx -> {
                        this.k.arenaType = vx;
                        
                        this.plugin.kits().save();
                        new KitListMenu.KitEditMenu(this.plugin, this.viewer, this.k).open();
                    });
                }
            );
            this.set(42, Ui.toggle(Material.CLOCK, "Rounds Vote", this.k.roundsVote, "Offer 'play one more round' before 1v1 duels."), c -> {
                this.k.roundsVote = !this.k.roundsVote;
                this.changed();
            });
            this.set(
                43,
                Items.of(Material.WRITABLE_BOOK)
                    .name("<gold><b>Votes <white>(" + this.k.votes.size() + ")")
                    .lore("<gray>Pre-match votes for this kit:", "<gray>item limits, no regen, knockback,", "<gray>rounds and more.", "", "<yellow>▶ Click to edit")
                    .build(),
                c -> new KitListMenu.KitVotesMenu(this.plugin, this.viewer, this.k).open()
            );
            this.set(49, Ui.back(), c -> new KitListMenu(this.plugin, this.viewer).open());
        }

        private void number(String title, String label, float min, float max, float step, float now, java.util.function.Consumer<Float> set) {
            this.plugin.dialogs().number(this.viewer, Text.mm("<gold><b>" + title), label, min, max, step, now, vx -> {
                set.accept(vx);
                this.plugin.kits().save();
                new KitListMenu.KitEditMenu(this.plugin, this.viewer, this.k).open();
            });
        }
    }

    /** The pre-match votes of one kit: click a vote to remove it, add new ones from presets or the held item. */
    public static final class KitVotesMenu extends Menu {
        private final Kit k;

        public KitVotesMenu(VortexRealm plugin, Player viewer, Kit k) {
            super(plugin, viewer);
            this.k = k;
        }

        @Override
        protected Component title() {
            return Text.mm("<gold><b>Votes</b></gold> <dark_gray>» ").append(this.k.name());
        }

        @Override
        protected int rows() {
            return 5;
        }

        private void saved() {
            this.plugin.kits().save();
            this.redraw();
        }

        @Override
        protected void draw() {
            this.border(Ui.FRAME);
            int[] slots = {10, 11, 12, 13, 14, 15, 16};

            for (int i = 0; i < Math.min(slots.length, this.k.votes.size()); i++) {
                KitVote v = this.k.votes.get(i);
                this.set(
                    slots[i],
                    Items.of(v.material().isItem() ? v.material() : Material.PAPER)
                        .name("<white><b>" + Text.esc(v.question()))
                        .lore("<gray>Type: <white>" + v.action().name().toLowerCase().replace('_', ' '), "", "<red>▶ Click to remove")
                        .build(),
                    c -> {
                        this.k.votes.remove(v);
                        this.saved();
                    }
                );
            }

            List<KitVote> auto = net.vortexrealm.match.PreMatch.votesFor(this.k);
            List<String> shown = new ArrayList<>();

            for (KitVote v : auto) {
                shown.add("<gray>• <white>" + Text.esc(v.question()));
            }

            this.set(
                4,
                Items.of(Material.BOOK)
                    .name("<gold><b>Shown Before A Match")
                    .lore(shown.isEmpty() ? List.of("<gray>Nothing to vote on.") : shown)
                    .build()
            );
            this.set(28, Items.of(Material.CLOCK).name("<green><b>+ Rounds Vote").lore("<gray>'Play first to N rounds'.").build(), c -> this.plugin.dialogs()
                .number(this.viewer, Text.mm("<gold><b>Rounds Vote"), "First to", 2.0F, 10.0F, 1.0F, Math.max(2, this.k.rounds() + 1), vx -> {
                    int n = Math.round(vx);
                    this.k.votes.removeIf(x -> x.action() == KitVote.Action.ROUNDS);
                    this.k.votes.add(new KitVote("rounds", "Play First To " + n + " Rounds", KitVote.Action.ROUNDS, Material.CLOCK, n));
                    this.plugin.kits().save();
                    new KitListMenu.KitVotesMenu(this.plugin, this.viewer, this.k).open();
                }));
            this.set(29, Items.of(Material.GOLDEN_APPLE).name("<green><b>+ No Regen Vote").lore("<gray>'No natural regen' (UHC style).").build(), c -> {
                this.k.votes.removeIf(x -> x.action() == KitVote.Action.NO_REGEN);
                this.k.votes.add(new KitVote("no_regen", "No Natural Regen", KitVote.Action.NO_REGEN, Material.GOLDEN_APPLE, 0));
                this.saved();
            });
            this.set(30, Items.of(Material.PISTON).name("<green><b>+ Knockback Vote").lore("<gray>'Knockback N%' for the match.").build(), c -> this.plugin.dialogs()
                .number(this.viewer, Text.mm("<gold><b>Knockback Vote"), "Percent", 0.0F, 300.0F, 5.0F, 150.0F, vx -> {
                    int n = Math.round(vx);
                    this.k.votes.removeIf(x -> x.action() == KitVote.Action.KNOCKBACK);
                    this.k.votes.add(new KitVote("knockback", "Knockback " + n + "%", KitVote.Action.KNOCKBACK, Material.PISTON, n));
                    this.plugin.kits().save();
                    new KitListMenu.KitVotesMenu(this.plugin, this.viewer, this.k).open();
                }));
            ItemStack held = this.viewer.getInventory().getItemInMainHand();
            String heldName = held.getType().isAir() ? "nothing" : held.getType().getKey().getKey().replace('_', ' ');
            this.set(
                32,
                Items.of(Material.HOPPER).name("<green><b>+ Limit Held Item").lore("<gray>Holding: <white>" + heldName, "<gray>'Limit to N <item>'.").build(),
                c -> {
                    if (held.getType().isAir()) {
                        Msg.error(this.viewer, "admin.hold-item");
                        return;
                    }

                    Material m = held.getType();
                    this.plugin.dialogs().number(this.viewer, Text.mm("<gold><b>Limit " + Text.esc(heldName)), "Keep at most", 0.0F, 64.0F, 1.0F, 8.0F, vx -> {
                        int n = Math.round(vx);
                        this.k.votes.removeIf(x -> x.itemVote() && x.material() == m);
                        this.k.votes.add(new KitVote("limit_" + m.getKey().getKey(), "Limit to " + n + " " + Items.pretty(m), KitVote.Action.LIMIT, m, n));
                        this.plugin.kits().save();
                        new KitListMenu.KitVotesMenu(this.plugin, this.viewer, this.k).open();
                    });
                }
            );
            this.set(33, Items.of(Material.BARRIER).name("<green><b>+ Remove Held Item").lore("<gray>Holding: <white>" + heldName, "<gray>'No <item>'.").build(), c -> {
                if (held.getType().isAir()) {
                    Msg.error(this.viewer, "admin.hold-item");
                    return;
                }

                Material m = held.getType();
                this.k.votes.removeIf(x -> x.itemVote() && x.material() == m);
                this.k.votes.add(new KitVote("no_" + m.getKey().getKey(), "No " + Items.pretty(m), KitVote.Action.REMOVE, m, 0));
                this.saved();
            });
            this.set(40, Ui.back(), c -> new KitListMenu.KitEditMenu(this.plugin, this.viewer, this.k).open());
        }
    }
}
