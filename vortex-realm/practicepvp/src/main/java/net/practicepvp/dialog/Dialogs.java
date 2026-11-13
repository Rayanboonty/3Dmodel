package net.practicepvp.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.ActionButton.Builder;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.ClickCallback.Options;
import net.practicepvp.PracticePlugin;
import net.practicepvp.config.Cfg;
import net.practicepvp.config.Msg;
import net.practicepvp.config.Opt;
import net.practicepvp.data.KitStats;
import net.practicepvp.data.PvPProfile;
import net.practicepvp.ffa.FfaArena;
import net.practicepvp.fx.Fx;
import net.practicepvp.gui.menus.QueueMenu;
import net.practicepvp.kit.Kit;
import net.practicepvp.kit.KitCategory;
import net.practicepvp.rank.Tier;
import net.practicepvp.session.PlayerState;
import net.practicepvp.session.Session;
import net.practicepvp.util.ClientVersion;
import net.practicepvp.util.Fmt;
import net.practicepvp.util.Icons;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import net.practicepvp.util.TextWidth;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

public final class Dialogs {
    static final Options OPTS = Options.builder().uses(-1).lifetime(Duration.ofMinutes(10L)).build();
    private static final Options ONCE = Options.builder().uses(1).lifetime(Duration.ofMinutes(10L)).build();
    private final PracticePlugin plugin;
    private final LobbyDialogs lobby;
    private final AdminDialogs admin;
    private final LeaderboardDialogs boards = new LeaderboardDialogs(this);

    public Dialogs(PracticePlugin plugin) {
        this.plugin = plugin;
        this.lobby = new LobbyDialogs(plugin, this);
        this.admin = new AdminDialogs(plugin, this.lobby);
    }

    public AdminDialogs admin() {
        return this.admin;
    }

    public LobbyDialogs lobby() {
        return this.lobby;
    }

    public Runnable safe(Player p, Runnable r) {
        return () -> Tasks.ensureSync(() -> {
            if (p.isOnline()) {
                try {
                    r.run();
                } catch (RuntimeException var4) {
                    this.plugin.getLogger().log(Level.SEVERE, "Dialog action failed for " + p.getName(), (Throwable)var4);
                    p.closeDialog();
                }
            }
        });
    }

    public DialogAction action(Player p, Runnable r) {
        Runnable s = this.safe(p, r);
        return DialogAction.customClick((view, audience) -> s.run(), OPTS);
    }

    public DialogAction viewAction(Player p, Consumer<DialogResponseView> c) {
        return DialogAction.customClick((view, audience) -> {
            Runnable s = this.safe(p, () -> c.accept(view));
            s.run();
        }, OPTS);
    }

    public LeaderboardDialogs boards() {
        return this.boards;
    }

    PracticePlugin plugin() {
        return this.plugin;
    }

    private DialogAction formAction(Player p, Consumer<DialogResponseView> c) {
        return DialogAction.customClick((view, audience) -> {
            Runnable s = this.safe(p, () -> c.accept(view));
            s.run();
        }, ONCE);
    }

    public static ActionButton button(Component label, Component tooltip, int width, DialogAction action) {
        Builder b = ActionButton.builder(label).width(width);
        if (tooltip != null) {
            b.tooltip(tooltip);
        }

        if (action != null) {
            b.action(action);
        }

        return b.build();
    }

    ActionButton closeButton(Player p) {
        return button(Text.mm("<red>Close"), null, 200, this.action(p, p::closeDialog));
    }

    public static DialogBody itemRow(ItemStack icon, Component text, boolean tooltip) {
        int w = TextWidth.fit(TextWidth.width(text), 40, 300);
        return DialogBody.item(icon).description(DialogBody.plainMessage(text, w)).showDecorations(false).showTooltip(tooltip).build();
    }

    public static void show(Player p, DialogBase base, DialogType type) {
        Dialog d = Dialog.create(b -> b.empty().base(base).type(type));
        p.showDialog(d);
    }

    public boolean useDialog(Player p) {
        return switch ((Opt.MenuStyle)Cfg.e(Opt.MENU_STYLE, Opt.MenuStyle.class)) {
            case DIALOG -> true;
            case CHEST -> false;
            default -> ClientVersion.supportsDialogs(p);
        };
    }

    private boolean formsSupported(Player p) {
        if (ClientVersion.supportsDialogs(p)) {
            return true;
        } else {
            Msg.error(p, "general.client-too-old");
            return false;
        }
    }

    public void openQueue(Player p, boolean ranked, Dialogs.QueueTab tab) {
        Session s = this.plugin.sessions().get(p);
        if (s.state != PlayerState.LOBBY && s.state != PlayerState.QUEUE) {
            Msg.error(p, "queue.not-in-lobby");
        } else if (Cfg.bool(Opt.MAINTENANCE) && !p.hasPermission("practice.admin")) {
            Msg.error(p, "general.maintenance");
        } else if (!this.useDialog(p)) {
            new QueueMenu(this.plugin, p, Cfg.bool(Opt.RANKED_ENABLED), tab).open();
        } else {
            List<Kit> kits = this.kitsFor(tab);
            List<DialogBody> body = new ArrayList<>();
            if (kits.isEmpty()) {
                body.add(DialogBody.plainMessage(Msg.get("queue.menu-empty"), 250));
            }

            PvPProfile prof = this.plugin.profiles().get(p);
            List<ItemStack> rowIcons = new ArrayList<>();
            List<Component> rowText = new ArrayList<>();

            for (Kit kit : kits) {
                int players = this.plugin.queues().size(kit) + this.plugin.matches().playing(kit);
                boolean queued = this.plugin.queues().inQueue(p, kit, true) || this.plugin.queues().inQueue(p, kit, false);
                boolean ranks = rankedQueue(kit);
                boolean test = ranks && (Cfg.bool(Opt.PROMOTION) && this.plugin.ranks().challengeReady(prof, kit) || this.plugin.ranks().defenseDue(prof, kit) != null);
                KitStats ks = prof.statsOrNull(kit.id);
                Component name = Text.mm(kit.color + "<b>" + Text.esc(kit.displayName) + "</b>" + (test ? " <gold>⚠</gold>" : ""));
                Component count = Text.mm(
                    queued
                        ? "<green>" + Fmt.num(players) + " player" + (players == 1 ? "" : "s") + " ✓"
                        : "<gray>" + Fmt.num(players) + " player" + (players == 1 ? "" : "s")
                );
                List<Component> hover = new ArrayList<>();
                hover.add(Text.mm(kit.color + "<b>" + Text.esc(kit.displayName)));

                for (String line : kit.description) {
                    hover.add(Text.mm("<gray>" + Text.esc(line)));
                }

                hover.add(Component.empty());
                if (ranks) {
                    Tier t = ks == null ? null : ks.tier();
                    hover.add(Text.mm("<gray>Your tier: " + (t == null ? "<white>Unranked" : t.mm()) + " <dark_gray>(" + (ks == null ? 0 : ks.elo) + " pts)"));
                    Tier defenseTo = this.plugin.ranks().defenseDue(prof, kit);
                    if (defenseTo != null) {
                        hover.add(Text.mm("<gold>⚠ Tier Defense due <gray>- lose it and you drop to " + defenseTo.mm()));
                    } else if (test) {
                        Tier to = Tier.byIndex(ks.promoTarget);
                        hover.add(Text.mm("<gold>⚠ Tier Test Challenge ready <gray>→ " + (to == null ? "" : to.mm())));
                    }
                } else {
                    hover.add(Text.mm("<gray>Casual: doesn't change your rank"));
                }

                hover.add(
                    Text.mm(
                        "<gray>Queued: <white>" + this.plugin.queues().size(kit) + " <dark_gray>| <gray>Playing: <white>" + this.plugin.matches().playing(kit)
                    )
                );
                hover.add(Component.empty());
                hover.add(Text.mm(queued ? "<red>▶ Click to leave this queue" : "<green>▶ Click to enter its queue"));
                Component hoverText = Component.join(JoinConfiguration.newlines(), hover);
                Runnable click = this.safe(p, () -> this.onKitClick(p, kit, tab));
                Component desc = Component.text()
                    .append(name)
                    .append(Component.newline())
                    .append(count)
                    .clickEvent(ClickEvent.callback(a -> click.run(), OPTS))
                    .hoverEvent(HoverEvent.showText(hoverText))
                    .build();
                ItemStack icon = kit.iconStack();
                icon.editMeta(m -> {
                    m.displayName(Text.noItalic(Text.mm(kit.color + "<b>" + Text.esc(kit.displayName))));
                    List<Component> lore = new ArrayList<>();

                    for (int i = 1; i < hover.size(); i++) {
                        lore.add(Text.noItalic(hover.get(i)));
                    }

                    m.lore(lore);
                    m.addItemFlags(ItemFlag.values());
                });
                rowIcons.add(icon);
                rowText.add(desc);
            }

            int width = TextWidth.fit(TextWidth.widest(rowText), 70, 260);

            for (int i = 0; i < rowIcons.size(); i++) {
                body.add(
                    DialogBody.item(rowIcons.get(i))
                        .description(DialogBody.plainMessage(rowText.get(i), width))
                        .showDecorations(false)
                        .showTooltip(true)
                        .build()
                );
            }

            Component title = this.plugin.hackerPool() != null && this.plugin.hackerPool().pooled(p)
                ? Text.mm("<dark_red><b>⚠ HACKER POOL</b></dark_red> <gray>• queue against other pooled players")
                : Text.mm("<aqua>Click a kit to enter its queue!");
            List<ActionButton> buttons = new ArrayList<>();
            buttons.add(this.tabButton(p, Dialogs.QueueTab.SKILL, tab));
            buttons.add(this.tabButton(p, Dialogs.QueueTab.VANILLA, tab));
            buttons.add(this.tabButton(p, Dialogs.QueueTab.OTHER, tab));
            buttons.add(this.tabButton(p, Dialogs.QueueTab.MOST_PLAYED, tab));
            Kit ready = null;

            for (Kit k : this.plugin.kits().sorted()) {
                if (ready == null && rankedQueue(k) && Cfg.bool(Opt.PROMOTION) && this.plugin.ranks().challengeReady(prof, k)) {
                    ready = k;
                }
            }

            if (ready != null) {
                Kit rk = ready;
                buttons.add(
                    button(
                        Text.mm("<gold><b>⚠ Tier Test</b>"),
                        Text.mm("<gray>Your Tier Test Challenge in " + Text.esc(rk.displayName) + " is ready"),
                        150,
                        this.action(p, () -> this.tierTest(p, rk, tab))
                    )
                );
            }

            DialogBase base = DialogBase.builder(title).canCloseWithEscape(true).pause(false).afterAction(DialogAfterAction.NONE).body(body).build();
            show(p, base, DialogType.multiAction(buttons).columns(3).exitAction(this.closeButton(p)).build());
        }
    }

    private static boolean rankedQueue(Kit kit) {
        return kit.ranked && Cfg.bool(Opt.RANKED_ENABLED);
    }

    private ActionButton tabButton(Player p, Dialogs.QueueTab tab, Dialogs.QueueTab current) {
        Component icon = Component.empty();
        String label;
        switch (tab) {
            case SKILL:
                label = "<aqua>Skill Kits";
                icon = Icons.spriteOr(KitCategory.SKILL.sprite, "<aqua>⚔");
                break;
            case VANILLA:
                label = "<gold>Vanilla Kits";
                icon = Icons.spriteOr(KitCategory.VANILLA.sprite, "<gold>✦");
                break;
            case OTHER:
                label = "<light_purple>Other Kits";
                icon = Text.mm("<green>✦");
                break;
            default:
                label = "<white>Most Played Kits";
        }

        Component text = tab == current ? Text.mm("<white><b><i>" + Text.plain(Text.mm(label)) + "</i></b>") : Text.mm(label);
        Component full = tab == Dialogs.QueueTab.MOST_PLAYED ? text : icon.append((Component)Component.space()).append(text);
        return button(
            full,
            Text.mm("<gray>Show " + Text.plain(Text.mm(label))),
            tab == Dialogs.QueueTab.MOST_PLAYED ? 200 : 150,
            this.action(p, () -> this.openQueue(p, true, tab))
        );
    }

    public List<Kit> kitsFor(boolean ranked, Dialogs.QueueTab tab) {
        return this.kitsFor(tab);
    }

    public List<Kit> kitsFor(Dialogs.QueueTab tab) {
        List<Kit> pool = new ArrayList<>();

        for (Kit k : this.plugin.kits().sorted()) {
            boolean queueable = rankedQueue(k) || k.unranked && Cfg.bool(Opt.UNRANKED_ENABLED);
            if (k.enabled && queueable && !this.plugin.arenas().forKit(k).isEmpty()) {
                pool.add(k);
            }
        }
        return switch (tab) {
            case SKILL -> pool.stream().filter(kx -> kx.category == KitCategory.SKILL).toList();
            case VANILLA -> pool.stream().filter(kx -> kx.category == KitCategory.VANILLA).toList();
            case OTHER -> pool.stream().filter(kx -> kx.category == KitCategory.OTHER).toList();
            default -> pool.stream()
                .sorted(
                    Comparator.<Kit>comparingInt(kx -> -(this.plugin.queues().size(kx) + this.plugin.matches().playing(kx)))
                        .thenComparingInt(kx -> kx.category.ordinal())
                        .thenComparingInt(kx -> kx.order)
                )
                .limit(6L)
                .toList();
        };
    }

    private void onKitClick(Player p, Kit kit, Dialogs.QueueTab tab) {
        Session s = this.plugin.sessions().get(p);
        if (!s.dialogCooldown(250L)) {
            if (s.state != PlayerState.LOBBY && s.state != PlayerState.QUEUE) {
                p.closeDialog();
            } else {
                boolean ranked = rankedQueue(kit);
                if (!this.plugin.queues().inQueue(p, kit, true) && !this.plugin.queues().inQueue(p, kit, false)) {
                    PvPProfile dprof = this.plugin.profiles().get(p);
                    if (ranked && this.plugin.ranks().defenseDue(dprof, kit) != null) {
                        this.tierDefense(p, kit, tab);
                        return;
                    }

                    if (ranked
                        && Cfg.bool(Opt.PROMOTION)
                        && this.plugin.ranks().challengeReady(this.plugin.profiles().get(p), kit)
                        && this.plugin.ranks().mayPopUp(this.plugin.profiles().get(p), kit)) {
                        this.tierTest(p, kit, tab);
                        return;
                    }

                    if (ranked && Cfg.bool(Opt.PROMOTION) && this.plugin.ranks().challengeReady(this.plugin.profiles().get(p), kit)) {
                        p.sendActionBar(
                            Text.mm("<gold>⚠ Tier Test ready <gray>- press <yellow>⚠ Tier Test</yellow> in the Duels menu or <yellow>/rankup " + kit.id)
                        );
                    }

                    this.plugin.queues().join(p, kit, ranked, false);
                } else {
                    if (this.plugin.queues().inQueue(p, kit, true)) {
                        this.plugin.queues().leave(p, kit, true);
                    }

                    if (this.plugin.queues().inQueue(p, kit, false)) {
                        this.plugin.queues().leave(p, kit, false);
                    }
                }

                if (!Cfg.bool(Opt.QUEUE_REOPEN) || s.state != PlayerState.LOBBY && s.state != PlayerState.QUEUE) {
                    p.closeDialog();
                } else {
                    this.openQueue(p, true, tab);
                }
            }
        }
    }

    public void tierTest(Player p, Kit kit, Dialogs.QueueTab back) {
        PvPProfile prof = this.plugin.profiles().get(p);
        KitStats ks = prof.statsOrNull(kit.id);
        Tier to = ks == null ? null : Tier.byIndex(ks.promoTarget);
        if (to == null) {
            this.openQueue(p, true, back);
        } else {
            int firstTo = Cfg.i(Opt.TIER_TEST_FIRST_TO);
            this.plugin.ranks().markPopUpShown(prof, kit);
            boolean above = Cfg.bool(Opt.TIER_TEST_ABOVE);
            if (!ClientVersion.supportsDialogs(p)) {
                p.sendMessage(
                    Text.mm("<gold>⚠ Tier Test Challenge</gold> <gray>in ")
                        .append(kit.label())
                        .append(Text.mm("<gray>: win (first to " + firstTo + ") to rank up to "))
                        .append(Text.mm(to.mm()))
                        .append(
                            Text.mm(
                                "<gray>. <green><b><click:run_command:'/rankup "
                                    + kit.id
                                    + " accept'>[ACCEPT]</click></b> <aqua><click:run_command:'/rankup "
                                    + kit.id
                                    + " normal'>[QUEUE NORMALLY]</click>"
                            )
                        )
                );
            } else {
                List<DialogBody> body = new ArrayList<>();
                body.add(
                    DialogBody.plainMessage(
                        Text.mm("<white>You are queuing for a <gold>Tier Test Challenge</gold> in ")
                            .append(kit.iconComponent())
                            .append(
                                Text.mm(
                                    " "
                                        + kit.color
                                        + "<b>"
                                        + Text.esc(kit.displayName)
                                        + "</b><white>. First to "
                                        + firstTo
                                        + (above ? ", against a player who already has " + to.mm() + "<white>" : "")
                                        + ".\nThese matches could take longer, and queue times may vary.\nWin to rank up to "
                                )
                            )
                            .append(Text.mm(to.mm()))
                            .append(Text.mm("<white>. If you lose, you stay within your current tier.")),
                        400
                    )
                );
                body.add(DialogBody.plainMessage(ladder(ks.tier(), to), 400));
                ActionButton accept = button(
                    Text.mm("<green>Accept Challenge"),
                    Text.mm("<gray>Queue a first-to-" + firstTo + " test against a player " + (above ? "one rank above you" : "of your tier")),
                    200,
                    this.action(
                        p,
                        () -> {
                            if (this.plugin.ranks().startSeries(prof, kit) && this.plugin.queues().join(p, kit, true, true)) {
                                Snd.play(p, "ui.toast.challenge_complete", 0.6F, 1.4F);
                                p.sendMessage(
                                    Text.mm("<gold>⚠ Tier Test Challenge</gold> <gray>- looking for an opponent in ")
                                        .append(kit.label())
                                        .append(Text.mm("<gray>. Win to reach "))
                                        .append(Text.mm(to.mm()))
                                        .append(Text.mm("<gray>!"))
                                );
                            }

                            this.openQueue(p, true, back);
                        }
                    )
                );
                ActionButton normal = button(
                    Text.mm("<aqua>Queue Normally"), Text.mm("<gray>A normal match; the challenge stays available"), 200, this.action(p, () -> {
                        this.plugin.ranks().queueNormally(prof, kit);
                        this.plugin.queues().join(p, kit, true, false);
                        this.openQueue(p, true, back);
                    })
                );
                DialogBase base = DialogBase.builder(Text.mm("<gold><b>⚠</b> <white>Tier Test Challenge"))
                    .canCloseWithEscape(true)
                    .pause(false)
                    .afterAction(DialogAfterAction.NONE)
                    .body(body)
                    .build();
                show(p, base, DialogType.confirmation(accept, normal));
                Snd.play(p, "ui.toast.challenge_complete", 0.5F, 1.2F);
            }
        }
    }

    /** The MCPVP "Tier Defense Challenge" prompt shown when queuing ranked while a defense is due. */
    public void tierDefense(Player p, Kit kit, Dialogs.QueueTab back) {
        PvPProfile prof = this.plugin.profiles().get(p);
        Tier to = this.plugin.ranks().defenseDue(prof, kit);
        KitStats ks = prof.statsOrNull(kit.id);
        if (to == null || ks == null) {
            this.openQueue(p, true, back);
        } else {
            int firstTo = Cfg.i(Opt.TIER_DEFENSE_FIRST_TO);
            if (!ClientVersion.supportsDialogs(p)) {
                p.sendMessage(
                    Text.mm("<gold>⚠ Tier Defense Challenge</gold> <gray>in ")
                        .append(kit.label())
                        .append(Text.mm("<gray>: win (first to " + firstTo + ") to defend your tier, lose and you rank down to "))
                        .append(Text.mm(to.mm()))
                        .append(
                            Text.mm(
                                "<gray>. <green><b><click:run_command:'/rankup "
                                    + kit.id
                                    + " defend'>[ACCEPT]</click></b> <aqua><click:run_command:'/rankup "
                                    + kit.id
                                    + " normal'>[QUEUE NORMALLY]</click>"
                            )
                        )
                );
            } else {
                List<DialogBody> body = new ArrayList<>();
                body.add(
                    DialogBody.plainMessage(
                        Text.mm("<white>You are queuing for a <gold>Tier Defense Challenge</gold> in ")
                            .append(kit.iconComponent())
                            .append(
                                Text.mm(
                                    " "
                                        + kit.color
                                        + "<b>"
                                        + Text.esc(kit.displayName)
                                        + "</b><white>. First to "
                                        + firstTo
                                        + ".\nThese matches could take longer, and queue times may vary.\nWin to defend your tier. If you lose, you rank down to "
                                )
                            )
                            .append(Text.mm(to.mm()))
                            .append(Text.mm("<white>.")),
                        400
                    )
                );
                body.add(DialogBody.plainMessage(ladder(ks.tier(), to), 400));
                int grace = Cfg.i(Opt.TIER_DEFENSE_GRACE);
                if (grace > 0) {
                    int left = Math.max(0, grace - ks.promoDeclines);
                    body.add(
                        DialogBody.plainMessage(
                            Text.mm("<dark_gray>Normal ranked matches left before you drop automatically: <gray>" + left), 400
                        )
                    );
                }

                ActionButton accept = button(
                    Text.mm("<green>Accept Challenge"),
                    Text.mm("<gray>Queue a first-to-" + firstTo + " defense against a player near your tier"),
                    200,
                    this.action(p, () -> {
                        if (this.plugin.ranks().startDefense(prof, kit) && this.plugin.queues().join(p, kit, true, true)) {
                            Snd.play(p, "ui.toast.challenge_complete", 0.6F, 1.1F);
                            p.sendMessage(
                                Text.mm("<gold>⚠ Tier Defense Challenge</gold> <gray>- looking for an opponent in ")
                                    .append(kit.label())
                                    .append(Text.mm("<gray>. Win to keep "))
                                    .append(Text.mm(ks.tier() == null ? "" : ks.tier().mm()))
                                    .append(Text.mm("<gray>!"))
                            );
                        }

                        this.openQueue(p, true, back);
                    })
                );
                ActionButton normal = button(
                    Text.mm("<aqua>Queue Normally"), Text.mm("<gray>A normal match; the defense stays due"), 200, this.action(p, () -> {
                        this.plugin.ranks().queueNormallyDefense(prof, kit);
                        this.plugin.queues().join(p, kit, true, false);
                        this.openQueue(p, true, back);
                    })
                );
                DialogBase base = DialogBase.builder(Text.mm("<gold><b>⚠</b> <white>Tier Defense Challenge"))
                    .canCloseWithEscape(true)
                    .pause(false)
                    .afterAction(DialogAfterAction.NONE)
                    .body(body)
                    .build();
                show(p, base, DialogType.confirmation(accept, normal));
                Snd.play(p, "block.bell.use", 0.6F, 0.8F);
            }
        }
    }

    public void openFfa(Player p) {
        Session s = this.plugin.sessions().get(p);
        if (s.state != PlayerState.LOBBY && s.state != PlayerState.QUEUE) {
            Msg.error(p, "ffa.not-in-lobby");
        } else if (!this.useDialog(p)) {
            new QueueMenu.Ffa(this.plugin, p).open();
        } else {
            List<DialogBody> body = new ArrayList<>();
            List<ItemStack> rowIcons = new ArrayList<>();
            List<Component> rowText = new ArrayList<>();

            for (FfaArena a : this.plugin.ffa().all()) {
                Kit kit = this.plugin.ffa().kitOf(a);
                if (kit != null && a.ready()) {
                    int n = a.players.size();
                    Component desc = Component.text()
                        .append(Text.mm(kit.color + "<b>" + Text.esc(a.displayName) + "</b>"))
                        .append(Component.newline())
                        .append(Text.mm("<gray>" + n + " player" + (n == 1 ? "" : "s")))
                        .clickEvent(ClickEvent.callback(aud -> this.safe(p, () -> {
                            p.closeDialog();
                            this.plugin.ffa().join(p, a);
                        }).run(), OPTS))
                        .hoverEvent(HoverEvent.showText(Text.mm("<gray>Kit: ").append(kit.label()).append(Text.mm("\n<green>▶ Click to join"))))
                        .build();
                    ItemStack icon = kit.iconStack();
                    icon.editMeta(m -> {
                        m.displayName(Text.noItalic(Text.mm(kit.color + "<b>" + Text.esc(a.displayName))));
                        m.addItemFlags(ItemFlag.values());
                    });
                    rowIcons.add(icon);
                    rowText.add(desc);
                }
            }

            int width = TextWidth.fit(TextWidth.widest(rowText), 70, 260);

            for (int i = 0; i < rowIcons.size(); i++) {
                body.add(
                    DialogBody.item(rowIcons.get(i))
                        .description(DialogBody.plainMessage(rowText.get(i), width))
                        .showDecorations(false)
                        .showTooltip(true)
                        .build()
                );
            }

            if (body.isEmpty()) {
                body.add(DialogBody.plainMessage(Msg.get("ffa.menu-empty"), 250));
            }

            DialogBase base = DialogBase.builder(Text.mm("<gold><b>Free For All</b></gold> <dark_gray>»</dark_gray> <aqua>Click an arena to join!"))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogAfterAction.NONE)
                .body(body)
                .build();
            show(p, base, DialogType.notice(this.closeButton(p)));
        }
    }

    public void rankUpOffer(Player p, Kit kit) {
        this.tierTest(p, kit, Dialogs.QueueTab.MOST_PLAYED);
    }

    static Component ladder(Tier current, Tier target) {
        StringBuilder sb = new StringBuilder();

        for (Tier t : Tier.values()) {
            if (sb.length() > 0) {
                sb.append(" ");
            }

            if (t == current) {
                sb.append("<white>[</white>").append(t.mm()).append("<white>]</white>");
            } else if (t == target) {
                sb.append("<white>▶</white> <u>").append(t.mm()).append("</u>");
            } else if (current != null && t.ordinal() < current.ordinal()) {
                sb.append(t.mm());
            } else {
                sb.append("<dark_gray>").append(t.label).append("</dark_gray>");
            }
        }

        return Text.mm(sb.toString());
    }

    static Component eloBar(int elo, Tier target) {
        int need = target == null ? elo : target.threshold();
        Tier below = target == null ? null : target.prev();
        int from = below == null ? 0 : below.threshold();
        double f = need <= from ? 1.0 : Math.max(0.0, Math.min(1.0, (double)(elo - from) / (need - from)));
        int cells = 20;
        int filled = (int)Math.round(f * cells);
        return Text.mm(
            "<gray>Rank <white>"
                + elo
                + "</white> <green>"
                + "▌".repeat(filled)
                + "</green><dark_gray>"
                + "▌".repeat(cells - filled)
                + "</dark_gray> <gray>"
                + need
        );
    }

    public void rankedInvite(Player p, Kit kit, int games) {
        if (this.plugin.sessions().state(p) == PlayerState.LOBBY) {
            KitStats ks = this.plugin.profiles().get(p).statsOrNull(kit.id);
            Tier tier = ks != null && ks.tier() != null ? ks.tier() : Tier.LT5;
            if (!ClientVersion.supportsDialogs(p)) {
                Msg.send(p, "rank.invite-chat", "kit", kit.label(), "games", games, "id", kit.id);
            } else {
                List<DialogBody> body = new ArrayList<>();
                body.add(
                    itemRow(
                        kit.iconStack(),
                        Text.mm(kit.color + "<b>" + Text.esc(kit.displayName) + "</b>\n<gray>Current tier: ").append(Text.mm(tier.mm())),
                        false
                    )
                );
                body.add(DialogBody.plainMessage(ladder(tier, tier.next()), 300));
                body.add(DialogBody.plainMessage(Msg.get("rank.invite-body", "games", games, "kit", kit.displayName), 260));
                ActionButton start = button(
                    Text.mm("<green><b>Start Tier Test"), Text.mm("<gray>Join the ranked queue for " + Text.esc(kit.displayName)), 150, this.action(p, () -> {
                        p.closeDialog();
                        this.plugin.queues().join(p, kit, true, false);
                    })
                );
                ActionButton later = button(Text.mm("<gray>Later"), null, 150, this.action(p, p::closeDialog));
                DialogBase base = DialogBase.builder(Fx.shineFrame("RANKED TIER TEST", Fx.GOLD, 4.0))
                    .canCloseWithEscape(true)
                    .pause(false)
                    .afterAction(DialogAfterAction.NONE)
                    .body(body)
                    .build();
                show(p, base, DialogType.confirmation(start, later));
                Snd.play(p, "ui.toast.challenge_complete", 0.7F, 1.3F);
            }
        }
    }

    public void duelPopup(Player to, Player from, Kit kit, int rounds, String map, Component tier) {
        if (ClientVersion.supportsDialogs(to)) {
            List<DialogBody> body = new ArrayList<>();
            body.add(
                itemRow(
                    kit.iconStack(),
                    Text.mm("<white><b>" + Text.esc(from.getName()) + "</b> <dark_gray>(</dark_gray>")
                        .append(tier)
                        .append(Text.mm("<dark_gray>, </dark_gray>" + Fmt.pingColored(from.getPing()) + "<dark_gray>)</dark_gray>\n<gray>wants to duel you!")),
                    false
                )
            );
            body.add(
                DialogBody.plainMessage(
                    Text.mm("<gray>Kit: ")
                        .append(kit.label())
                        .append(
                            Text.mm(
                                " <dark_gray>•</dark_gray> <gray>First to <gold>"
                                    + rounds
                                    + "</gold> <dark_gray>•</dark_gray> <gray>Map: <white>"
                                    + Text.esc(map)
                            )
                        ),
                    260
                )
            );
            String fromName = from.getName();
            ActionButton yes = button(Text.mm("<green><b>Accept"), null, 150, this.action(to, () -> {
                to.closeDialog();
                this.plugin.duels().accept(to, fromName);
            }));
            ActionButton no = button(Text.mm("<red>Deny"), null, 150, this.action(to, () -> {
                to.closeDialog();
                this.plugin.duels().deny(to, fromName);
            }));
            DialogBase base = DialogBase.builder(Fx.shineFrame("DUEL REQUEST", Fx.GOLD, 3.0))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogAfterAction.NONE)
                .body(body)
                .build();
            show(to, base, DialogType.confirmation(yes, no));
        }
    }

    public void text(Player p, Component title, String label, String initial, int maxLength, Consumer<String> done) {
        if (this.formsSupported(p)) {
            DialogInput input = DialogInput.text("value", Text.mm("<gray>" + Text.esc(label)))
                .initial(initial == null ? "" : initial)
                .maxLength(Math.max(1, maxLength))
                .width(260)
                .build();
            ActionButton ok = button(Text.mm("<green><b>Save"), null, 120, this.formAction(p, view -> {
                String v = view.getText("value");
                p.closeDialog();
                done.accept(v == null ? "" : v.trim());
            }));
            ActionButton cancel = button(Text.mm("<red>Cancel"), null, 120, this.action(p, p::closeDialog));
            DialogBase base = DialogBase.builder(title)
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogAfterAction.NONE)
                .inputs(List.of(input))
                .build();
            show(p, base, DialogType.confirmation(ok, cancel));
        }
    }

    public void number(Player p, Component title, String label, float min, float max, float step, float initial, Consumer<Float> done) {
        if (this.formsSupported(p)) {
            float init = Math.max(min, Math.min(max, initial));
            DialogInput input = DialogInput.numberRange("value", Text.mm("<gray>" + Text.esc(label)), min, max)
                .step(step)
                .initial(init)
                .width(260)
                .labelFormat("%s: %s")
                .build();
            ActionButton ok = button(Text.mm("<green><b>Save"), null, 120, this.formAction(p, view -> {
                Float v = view.getFloat("value");
                p.closeDialog();
                if (v != null) {
                    done.accept(v);
                }
            }));
            ActionButton cancel = button(Text.mm("<red>Cancel"), null, 120, this.action(p, p::closeDialog));
            DialogBase base = DialogBase.builder(title)
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogAfterAction.NONE)
                .inputs(List.of(input))
                .build();
            show(p, base, DialogType.confirmation(ok, cancel));
        }
    }

    public void choice(Player p, Component title, String label, List<Dialogs.Choice> options, String initial, Consumer<String> done) {
        if (this.formsSupported(p)) {
            if (!options.isEmpty()) {
                List<OptionEntry> entries = new ArrayList<>();

                for (Dialogs.Choice c : options) {
                    entries.add(OptionEntry.create(c.id(), Text.mm(c.label()), c.id().equals(initial)));
                }

                DialogInput input = DialogInput.singleOption("value", Text.mm("<gray>" + Text.esc(label)), entries).width(260).build();
                ActionButton ok = button(Text.mm("<green><b>Save"), null, 120, this.formAction(p, view -> {
                    String v = view.getText("value");
                    p.closeDialog();
                    if (v != null) {
                        done.accept(v);
                    }
                }));
                ActionButton cancel = button(Text.mm("<red>Cancel"), null, 120, this.action(p, p::closeDialog));
                DialogBase base = DialogBase.builder(title)
                    .canCloseWithEscape(true)
                    .pause(false)
                    .afterAction(DialogAfterAction.NONE)
                    .inputs(List.of(input))
                    .build();
                show(p, base, DialogType.confirmation(ok, cancel));
            }
        }
    }

    public void confirm(Player p, Component title, List<Component> lines, Runnable yes) {
        if (this.formsSupported(p)) {
            List<DialogBody> body = new ArrayList<>();

            for (Component c : lines) {
                body.add(DialogBody.plainMessage(c, 260));
            }

            ActionButton ok = button(Text.mm("<green><b>Confirm"), null, 120, this.action(p, () -> {
                p.closeDialog();
                yes.run();
            }));
            ActionButton cancel = button(Text.mm("<red>Cancel"), null, 120, this.action(p, p::closeDialog));
            DialogBase base = DialogBase.builder(title).canCloseWithEscape(true).pause(false).afterAction(DialogAfterAction.NONE).body(body).build();
            show(p, base, DialogType.confirmation(ok, cancel));
        }
    }

    public void checks(Player p, Component title, List<Component> header, List<Dialogs.Check> checks, List<Dialogs.CheckButton> buttons, ActionButton back) {
        if (this.formsSupported(p)) {
            List<DialogBody> body = new ArrayList<>();

            for (Component c : header) {
                body.add(DialogBody.plainMessage(c, 300));
            }

            List<DialogInput> inputs = new ArrayList<>();

            for (int i = 0; i < checks.size(); i++) {
                Dialogs.Check c = checks.get(i);
                inputs.add(DialogInput.bool("c" + i, c.label()).initial(c.on()).build());
            }

            List<ActionButton> acts = new ArrayList<>();

            for (Dialogs.CheckButton b : buttons) {
                acts.add(button(b.label(), b.tooltip(), 150, this.formAction(p, view -> {
                    Set<String> on = new LinkedHashSet<>();

                    for (int i = 0; i < checks.size(); i++) {
                        if (Boolean.TRUE.equals(view.getBoolean("c" + i))) {
                            on.add(checks.get(i).key());
                        }
                    }

                    b.click().accept(on);
                })));
            }

            DialogBase base = DialogBase.builder(title)
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogAfterAction.NONE)
                .body(body)
                .inputs(inputs)
                .build();
            show(p, base, DialogType.multiAction(acts).columns(2).exitAction(back).build());
        }
    }

    public void generator(Player p, boolean ffa, List<Dialogs.Choice> themes, List<Dialogs.Choice> kits, Dialogs.GeneratorCallback done) {
        if (this.formsSupported(p)) {
            List<DialogInput> inputs = new ArrayList<>();
            inputs.add(DialogInput.text("name", Text.mm("<gray>Display name")).initial("").maxLength(32).width(260).build());
            List<OptionEntry> themeEntries = new ArrayList<>();

            for (int i = 0; i < themes.size(); i++) {
                themeEntries.add(OptionEntry.create(themes.get(i).id(), Text.mm(themes.get(i).label()), i == 0));
            }

            inputs.add(DialogInput.singleOption("theme", Text.mm("<gray>Biome"), themeEntries).width(260).build());
            int defSize = ffa ? Cfg.i(Opt.FFA_ARENA_SIZE) : Cfg.i(Opt.DUEL_ARENA_SIZE);
            inputs.add(
                DialogInput.numberRange("size", Text.mm("<gray>Size"), 32.0F, 200.0F)
                    .step(4.0F)
                    .initial((float)defSize)
                    .width(260)
                    .labelFormat("%s: %s")
                    .build()
            );
            if (ffa) {
                List<OptionEntry> kitEntries = new ArrayList<>();

                for (int i = 0; i < kits.size(); i++) {
                    kitEntries.add(OptionEntry.create(kits.get(i).id(), Text.mm(kits.get(i).label()), i == 0));
                }

                inputs.add(DialogInput.singleOption("kit", Text.mm("<gray>Kit"), kitEntries).width(260).build());
            } else {
                inputs.add(
                    DialogInput.numberRange("copies", Text.mm("<gray>Extra copies"), 0.0F, 20.0F)
                        .step(1.0F)
                        .initial((float)Cfg.i(Opt.DEFAULT_COPIES))
                        .width(260)
                        .labelFormat("%s: %s")
                        .build()
                );
            }

            ActionButton ok = button(
                Text.mm("<green><b>Generate"),
                null,
                120,
                this.formAction(
                    p,
                    view -> {
                        p.closeDialog();
                        Float size = view.getFloat("size");
                        Float copies = ffa ? Float.valueOf(0.0F) : view.getFloat("copies");
                        done.accept(
                            view.getText("name"),
                            view.getText("theme"),
                            size == null ? defSize : Math.round(size),
                            copies == null ? 0 : Math.round(copies),
                            ffa ? view.getText("kit") : null
                        );
                    }
                )
            );
            ActionButton cancel = button(Text.mm("<red>Cancel"), null, 120, this.action(p, p::closeDialog));
            DialogBase base = DialogBase.builder(Text.mm("<gold><b>" + (ffa ? "Generate FFA Arena" : "Generate Duel Arena")))
                .canCloseWithEscape(true)
                .pause(false)
                .afterAction(DialogAfterAction.NONE)
                .inputs(inputs)
                .build();
            show(p, base, DialogType.confirmation(ok, cancel));
        }
    }

    public record Check(String key, Component label, boolean on) {
    }

    public record CheckButton(Component label, Component tooltip, Consumer<Set<String>> click) {
    }

    public record Choice(String id, String label) {
    }

    @FunctionalInterface
    public interface GeneratorCallback {
        void accept(String var1, String var2, int var3, int var4, String var5);
    }

    public static enum QueueTab {
        MOST_PLAYED,
        SKILL,
        VANILLA,
        OTHER;
    }
}
