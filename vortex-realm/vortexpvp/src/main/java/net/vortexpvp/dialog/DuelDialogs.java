package net.vortexpvp.dialog;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.arena.Arena;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Msg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.data.KitStats;
import net.vortexpvp.data.PvPProfile;
import net.vortexpvp.data.Setting;
import net.vortexpvp.duel.DuelManager;
import net.vortexpvp.gui.menus.DuelMenu;
import net.vortexpvp.kit.Kit;
import net.vortexpvp.kit.KitCategory;
import net.vortexpvp.rank.Tier;
import net.vortexpvp.social.Regions;
import net.vortexpvp.social.SocialManager;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Text;
import org.bukkit.entity.Player;

/**
 * MCPVP duel flow: "Select a kit to challenge X in!" (Weapons / Vanilla / Skills / Most Played) → duel options
 * (First To slider, Ping Equalizer, advanced options) → Send Duel. Also the ranked Tier Challenge screen
 * ("HT5 | A VS B", First To, Location, I understand, Send Challenge).
 */
public final class DuelDialogs {
    private final VortexPvP plugin;
    private final Dialogs d;

    public DuelDialogs(VortexPvP plugin) {
        this.plugin = plugin;
        this.d = plugin.dialogs();
    }

    public static enum Tab {
        WEAPONS("<#55FFFF>⚔ <aqua>Weapons", KitCategory.SKILL),
        VANILLA("<#FF55FF>✦ <gold>Vanilla", KitCategory.VANILLA),
        SKILLS("<#FF55FF>✚ <light_purple>Skills", KitCategory.OTHER),
        MOST_PLAYED("<#55FF55>■ <white><b><i>Most Played", null);

        public final String label;
        public final KitCategory category;

        private Tab(String label, KitCategory category) {
            this.label = label;
            this.category = category;
        }
    }

    /** Options picked on the duel screen; passed along until the request is sent. */
    public record Picked(int rounds, boolean pingEq, Arena map, boolean spectators) {
    }

    private static DialogBase base(Component title, List<DialogBody> body, List<DialogInput> inputs) {
        DialogBase.Builder b = DialogBase.builder(title).canCloseWithEscape(true).pause(false).afterAction(DialogAfterAction.NONE);
        if (!body.isEmpty()) {
            b.body(body);
        }

        if (!inputs.isEmpty()) {
            b.inputs(inputs);
        }

        return b.build();
    }

    private List<Kit> kits(Player p, DuelDialogs.Tab tab, boolean rankedOnly) {
        List<Kit> pool = new ArrayList<>();
        for (Kit k : this.plugin.kits().sorted()) {
            if (k.enabled && k.hasContents() && (!rankedOnly || k.ranked) && !this.plugin.arenas().forKit(k).isEmpty()) {
                pool.add(k);
            }
        }

        if (tab.category != null) {
            pool.removeIf(k -> k.category != tab.category);
            return pool;
        } else {
            PvPProfile prof = this.plugin.profiles().get(p);
            pool.sort(Comparator.comparingInt((Kit k) -> {
                KitStats ks = prof.statsOrNull(k.id);
                return ks == null ? 0 : ks.matches();
            }).reversed());
            return pool.subList(0, Math.min(6, pool.size()));
        }
    }

    /** The overall position shown as "#39729" next to a name (empty when unranked). */
    private String rankTag(Player p) {
        SocialManager s = this.plugin.social();
        int r = s == null ? 0 : s.overallRank(p.getUniqueId());
        return r > 0 ? "<gray>#" + r + " <dark_gray>| " : "";
    }

    private Component flag(Player viewer, Player who) {
        return this.plugin.countries() != null && this.plugin.countries().picked(who.getUniqueId())
            ? this.plugin.countries().tag(who.getUniqueId(), viewer).append(Component.space())
            : Component.empty();
    }

    // ------------------------------------------------------------------ kit picker

    public void pickKit(Player p, Player target) {
        this.pickKit(p, target, DuelDialogs.Tab.WEAPONS, false);
    }

    public void pickKit(Player p, Player target, DuelDialogs.Tab tab, boolean challenge) {
        if (!this.d.useDialog(p)) {
            if (challenge) {
                p.sendMessage(Text.mm("<red>Tier challenges need Minecraft 1.21.6 or newer."));
            } else {
                new DuelMenu(this.plugin, p, target).open();
            }
        } else if (!target.isOnline()) {
            p.closeDialog();
            Msg.error(p, "general.player-offline", "player", target.getName());
        } else {
            LobbyDialogs ld = this.plugin.dialogs().lobby();
            PvPProfile prof = this.plugin.profiles().get(p);
            PvPProfile tprof = this.plugin.profiles().get(target);
            List<LobbyDialogs.Tile> tiles = new ArrayList<>();
            for (Kit k : this.kits(p, tab, challenge)) {
                KitStats mine = prof.statsOrNull(k.id);
                KitStats theirs = tprof.statsOrNull(k.id);
                Tier mt = mine == null ? null : mine.tier();
                Tier tt = theirs == null ? null : theirs.tier();
                boolean ok = !challenge || mt != null && tt != null;
                List<Component> hover = new ArrayList<>();
                hover.add(Text.mm(k.color + "<b>" + Text.esc(k.displayName)));
                for (String line : k.description) {
                    hover.add(Text.mm("<gray>" + Text.esc(line)));
                }

                hover.add(Component.empty());
                hover.add(Text.mm("<gray>You: " + (mt == null ? "<dark_gray>Unranked" : mt.mm()) + " <dark_gray>· <gray>" + Text.esc(target.getName()) + ": " + (tt == null ? "<dark_gray>Unranked" : tt.mm())));
                hover.add(Text.mm(ok ? "<green>▶ Click to continue" : "<red>Both players need a tier in this kit"));
                tiles.add(
                    new LobbyDialogs.Tile(
                        k.iconStack(),
                        Text.mm(k.color + "<b>" + Text.esc(k.displayName)),
                        Text.mm((mine == null ? "<dark_gray>Not played yet" : "<gray>" + mine.matches() + " played") + (mt == null ? "" : " <dark_gray>· ") ).append(mt == null ? Component.empty() : Text.mm(mt.mm())),
                        hover,
                        ok ? () -> {
                            if (challenge) {
                                this.challenge(p, target, k, false, false);
                            } else {
                                PvPProfile pp = this.plugin.profiles().get(p);
                                this.options(p, target, k, new DuelDialogs.Picked(k.rounds(), pp.on(Setting.PING_EQ), null, true));
                            }
                        } : null
                    )
                );
            }

            List<ActionButton> buttons = new ArrayList<>();
            for (DuelDialogs.Tab t : DuelDialogs.Tab.values()) {
                String label = t == tab ? "<white><b><i>" + Text.plain(Text.mm(t.label)) : t.label;
                buttons.add(Dialogs.button(Text.mm(label), null, t == DuelDialogs.Tab.MOST_PLAYED ? 180 : 120, this.d.action(p, () -> this.pickKit(p, target, t, challenge))));
            }

            String verb = challenge ? "send a Tier Challenge to" : "challenge";
            ld.screen(
                p,
                Text.mm("<aqua>Select a kit to " + verb + " \"" + Text.esc(target.getName()) + "\" in!"),
                tiles,
                List.of(),
                Text.mm(challenge ? "<gray>No ranked kits here.\n<dark_gray>Try another tab." : "<gray>No kits with arenas here.\n<dark_gray>Try another tab."),
                buttons,
                3,
                Dialogs.button(Text.mm("<red>Close"), null, 200, this.d.action(p, p::closeDialog))
            );
        }
    }

    // ------------------------------------------------------------------ duel options

    public void options(Player p, Player target, Kit kit, DuelDialogs.Picked picked) {
        if (!target.isOnline()) {
            p.closeDialog();
            Msg.error(p, "general.player-offline", "player", target.getName());
        } else {
            int maxRounds = Math.max(1, Cfg.i(Opt.DUEL_MAX_FIRST_TO));
            List<DialogBody> body = new ArrayList<>();
            body.add(
                DialogBody.plainMessage(
                    Text.mm(this.rankTag(p) + "<white>" + Text.esc(p.getName()) + " ")
                        .append(this.flag(p, p))
                        .append(Text.mm("<dark_gray>VS <white>" + Text.esc(target.getName()) + " "))
                        .append(this.flag(p, target)),
                    300
                )
            );
            body.add(Dialogs.itemRow(kit.iconStack(), Text.mm(kit.color + "<b>" + Text.esc(kit.displayName)), false));
            if (picked.map() != null || !picked.spectators()) {
                body.add(
                    DialogBody.plainMessage(
                        Text.mm(
                            "<gray>Map: <white>"
                                + (picked.map() == null ? "Random" : Text.esc(picked.map().displayName))
                                + " <dark_gray>· <gray>Spectators: "
                                + (picked.spectators() ? "<green>Allowed" : "<red>Blocked")
                        ),
                        300
                    )
                );
            }

            List<DialogInput> inputs = new ArrayList<>();
            inputs.add(
                DialogInput.numberRange("rounds", Text.mm("<white>First To"), 1.0F, (float)maxRounds)
                    .step(1.0F)
                    .initial((float)Math.max(1, Math.min(maxRounds, picked.rounds())))
                    .width(300)
                    .labelFormat("%s: %s")
                    .build()
            );
            if (this.plugin.pingEqualizer() != null && this.plugin.pingEqualizer().enabled()) {
                inputs.add(DialogInput.bool("pingeq", Text.mm("<white>Experimental Ping Equalizer")).initial(picked.pingEq()).build());
            }

            List<ActionButton> buttons = new ArrayList<>();
            buttons.add(Dialogs.button(Text.mm("<white>View Advanced Options"), Text.mm("<gray>Map and spectators"), 300, this.d.viewAction(p, v -> {
                this.advanced(p, target, kit, this.read(v, picked));
            })));
            buttons.add(Dialogs.button(Text.mm("<green>Send Duel"), null, 300, this.d.viewAction(p, v -> {
                DuelDialogs.Picked x = this.read(v, picked);
                p.closeDialog();
                this.plugin.duels().send(p, target, kit, x.rounds(), x.map(), new DuelManager.Options(x.pingEq(), x.spectators(), false));
            })));
            Dialogs.show(
                p,
                base(Text.mm(kit.color + Text.esc(kit.glyph) + " <b>" + Text.esc(kit.displayName)), body, inputs),
                DialogType.multiAction(buttons).columns(1).exitAction(Dialogs.button(Text.mm("<red>Back"), null, 200, this.d.action(p, () -> this.pickKit(p, target)))).build()
            );
        }
    }

    private DuelDialogs.Picked read(io.papermc.paper.dialog.DialogResponseView v, DuelDialogs.Picked old) {
        Float r = v.getFloat("rounds");
        Boolean eq = v.getBoolean("pingeq");
        int max = Math.max(1, Cfg.i(Opt.DUEL_MAX_FIRST_TO));
        int rounds = r == null ? old.rounds() : Math.max(1, Math.min(max, Math.round(r)));
        return new DuelDialogs.Picked(rounds, eq == null ? old.pingEq() : eq, old.map(), old.spectators());
    }

    public void advanced(Player p, Player target, Kit kit, DuelDialogs.Picked picked) {
        List<Arena> maps = new ArrayList<>();
        for (Arena a : this.plugin.arenas().forKit(kit)) {
            if (a.ready()) {
                maps.add(a);
            }
        }

        List<OptionEntry> entries = new ArrayList<>();
        entries.add(OptionEntry.create("random", Text.mm("<white>Random (map vote)"), picked.map() == null));
        for (Arena a : maps) {
            entries.add(OptionEntry.create(a.id, Text.mm("<white>" + Text.esc(a.displayName)), picked.map() != null && picked.map().id.equals(a.id)));
        }

        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(DialogInput.singleOption("map", Text.mm("<gray>Map"), entries).width(300).build());
        inputs.add(DialogInput.bool("spec", Text.mm("<white>Allow spectators")).initial(picked.spectators()).build());
        ActionButton save = Dialogs.button(Text.mm("<green>Save"), null, 150, this.d.viewAction(p, v -> {
            String id = v.getText("map");
            Arena map = null;
            for (Arena a : maps) {
                if (a.id.equals(id)) {
                    map = a;
                }
            }

            Boolean spec = v.getBoolean("spec");
            this.options(p, target, kit, new DuelDialogs.Picked(picked.rounds(), picked.pingEq(), map, spec == null || spec));
        }));
        ActionButton back = Dialogs.button(Text.mm("<gray>Back"), null, 150, this.d.action(p, () -> this.options(p, target, kit, picked)));
        Dialogs.show(p, base(Text.mm("<white>Advanced Options"), List.of(), inputs), DialogType.confirmation(save, back));
    }

    // ------------------------------------------------------------------ tier challenge

    /** The ranked challenge screen. {@code understood} keeps the checkbox ticked across reopenings. */
    public void challenge(Player p, Player target, Kit kit, boolean pingEq, boolean understood) {
        PvPProfile prof = this.plugin.profiles().get(p);
        KitStats mine = prof.statsOrNull(kit.id);
        Tier mt = mine == null ? null : mine.tier();
        int firstTo = Cfg.i(Opt.TIER_CHALLENGE_FIRST_TO);
        Regions regions = this.plugin.regions();
        String location = regions == null || regions.current() == null ? "Fairest" : "Fairest (" + Text.esc(regions.hostLabel()) + ")";
        List<DialogBody> body = new ArrayList<>();
        body.add(
            DialogBody.plainMessage(
                Text.mm(mt == null ? "" : mt.mm() + " <dark_gray>| ")
                    .append(Text.mm("<white>" + Text.esc(p.getName()) + " "))
                    .append(this.flag(p, p))
                    .append(Text.mm("<dark_gray>VS <white>" + Text.esc(target.getName()))),
                300
            )
        );
        body.add(DialogBody.plainMessage(Text.mm("<gold>First To: " + firstTo), 300));
        body.add(DialogBody.plainMessage(Text.mm("<aqua>Location: " + location), 300));
        body.add(
            DialogBody.plainMessage(
                Text.mm(
                    "<yellow>The result of this challenge will have a large impact on your Tier Score. By checking this box you agree that you understand this. Leaving will result in lost Tier Score."
                ),
                300
            )
        );
        List<DialogInput> inputs = new ArrayList<>();
        if (this.plugin.pingEqualizer() != null && this.plugin.pingEqualizer().enabled()) {
            inputs.add(DialogInput.bool("pingeq", Text.mm("<white>Ping Equalizer")).initial(pingEq).build());
        }

        inputs.add(DialogInput.bool("understand", Text.mm("<white>I understand")).initial(understood).build());
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(Dialogs.button(Text.mm("<white>Server: Fairest"), Text.mm("<gray>Matches are hosted on the fairest server for both players"), 150, this.d.viewAction(p, v -> {
            Boolean eq = v.getBoolean("pingeq");
            Boolean ok = v.getBoolean("understand");
            if (regions != null) {
                regions.open(p, () -> this.challenge(p, target, kit, Boolean.TRUE.equals(eq), Boolean.TRUE.equals(ok)));
            }
        })));
        buttons.add(Dialogs.button(Component.empty(), null, 150, null));
        buttons.add(Dialogs.button(Text.mm("<green>Send Challenge"), null, 300, this.d.viewAction(p, v -> {
            Boolean eq = v.getBoolean("pingeq");
            if (!Boolean.TRUE.equals(v.getBoolean("understand"))) {
                Snd.error(p);
                p.sendMessage(Text.mm("<red>Tick <white>I understand</white> to send a Tier Challenge."));
                this.challenge(p, target, kit, Boolean.TRUE.equals(eq), false);
            } else {
                p.closeDialog();
                this.plugin.duels().send(p, target, kit, firstTo, null, new DuelManager.Options(Boolean.TRUE.equals(eq), true, true));
            }
        })));
        Dialogs.show(
            p,
            base(Text.mm("<gold><b>⚠</b> <white>Tier Challenge"), body, inputs),
            DialogType.multiAction(buttons).columns(2).exitAction(Dialogs.button(Text.mm("<red>Back"), null, 200, this.d.action(p, () -> this.pickKit(p, target, DuelDialogs.Tab.WEAPONS, true)))).build()
        );
    }

    /** What the challenged player sees. They also have to tick "I understand". */
    public void challengePopup(Player to, Player from, Kit kit, int firstTo, boolean pingEq) {
        PvPProfile fp = this.plugin.profiles().get(from);
        KitStats fs = fp.statsOrNull(kit.id);
        Tier ft = fs == null ? null : fs.tier();
        List<DialogBody> body = new ArrayList<>();
        body.add(
            Dialogs.itemRow(
                kit.iconStack(),
                Text.mm(ft == null ? "" : ft.mm() + " <dark_gray>| ")
                    .append(Text.mm("<white><b>" + Text.esc(from.getName()) + "</b> <gray>sent you a <gold>Tier Challenge</gold> in "))
                    .append(Text.mm(kit.color + "<b>" + Text.esc(kit.displayName)))
                    .append(Text.mm("\n<gold>First To: " + firstTo + (pingEq ? " <dark_gray>· <aqua>Ping Equalizer" : ""))),
                false
            )
        );
        body.add(
            DialogBody.plainMessage(
                Text.mm("<yellow>The result has a large impact on your Tier Score. Leaving will result in lost Tier Score."), 300
            )
        );
        List<DialogInput> inputs = List.of(DialogInput.bool("understand", Text.mm("<white>I understand")).initial(false).build());
        String fromName = from.getName();
        ActionButton yes = Dialogs.button(Text.mm("<green><b>Accept"), null, 150, this.d.viewAction(to, v -> {
            if (!Boolean.TRUE.equals(v.getBoolean("understand"))) {
                Snd.error(to);
                to.sendMessage(Text.mm("<red>Tick <white>I understand</white> to accept the challenge."));
                this.challengePopup(to, from, kit, firstTo, pingEq);
            } else {
                to.closeDialog();
                this.plugin.duels().accept(to, fromName);
            }
        }));
        ActionButton no = Dialogs.button(Text.mm("<red>Deny"), null, 150, this.d.action(to, () -> {
            to.closeDialog();
            this.plugin.duels().deny(to, fromName);
        }));
        Dialogs.show(to, base(Text.mm("<gold><b>⚠</b> <white>Tier Challenge"), body, inputs), DialogType.confirmation(yes, no));
    }
}
