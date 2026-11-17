package net.practicepvp.dialog;

import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.practicepvp.PracticePlugin;
import net.practicepvp.data.PvPProfile;
import net.practicepvp.data.Setting;
import net.practicepvp.gui.menus.SettingsMenu;
import net.practicepvp.rank.Tier;
import net.practicepvp.social.Regions;
import net.practicepvp.social.SocialManager;
import net.practicepvp.util.ClientVersion;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The MCPVP settings flow: Settings hub → Privacy (Requests / Teleports / Location / Visibility),
 * Chat, Name Tag, Gameplay and Regions. Every screen works with the existing {@link Setting} values;
 * clients without dialogs get the classic chest menu.
 */
public final class SettingsDialogs {
    /** Tier choices offered by the "Tier:" button on the Requests screen (-1 = off). */
    private static final int[] TIER_STEPS = new int[]{
        -1,
        Tier.LT5.ordinal(),
        Tier.HT5.ordinal(),
        Tier.LT4.ordinal(),
        Tier.HT4.ordinal(),
        Tier.LT3.ordinal(),
        Tier.HT3.ordinal(),
        Tier.LT2.ordinal(),
        Tier.HT2.ordinal(),
        Tier.LT1.ordinal(),
        Tier.HT1.ordinal()
    };
    public static final int MAX_RANK_FILTER = 1000;
    private final PracticePlugin plugin;
    private final Dialogs d;

    public SettingsDialogs(PracticePlugin plugin) {
        this.plugin = plugin;
        this.d = plugin.dialogs();
    }

    private SocialManager social() {
        return this.plugin.social();
    }

    private ActionButton btn(Player p, String mini, String tooltip, Runnable r) {
        return Dialogs.button(Text.mm(mini), tooltip == null ? null : Text.mm(tooltip), 150, this.d.action(p, r));
    }

    private ActionButton wide(Player p, String mini, Runnable r) {
        return Dialogs.button(Text.mm(mini), null, 200, this.d.action(p, r));
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

    private String statusLine(Player p) {
        SocialManager.Status s = this.social() == null ? SocialManager.Status.ONLINE : this.social().status(p);
        return "<gray>Status: " + s.color + "● " + s.label;
    }

    // ------------------------------------------------------------------ hub

    public void hub(Player p) {
        if (!this.d.useDialog(p) || !ClientVersion.supportsDialogs(p)) {
            new SettingsMenu(this.plugin, p, Setting.Category.GAMEPLAY).open();
        } else {
            List<DialogBody> body = List.of(DialogBody.plainMessage(Text.mm(this.statusLine(p)), 300));
            List<ActionButton> buttons = new ArrayList<>();
            buttons.add(this.btn(p, "<#FF5C5C>■ <white>Privacy", "<gray>Requests, teleports, location and visibility", () -> this.privacy(p)));
            buttons.add(this.btn(p, "<#FFB547>☰ <white>Chat", "<gray>Messages, spectator chat and alerts", () -> this.chat(p)));
            buttons.add(this.btn(p, "<#FFE066>✎ <white>Name Tag", "<gray>What shows above your head", () -> this.nameTag(p)));
            buttons.add(this.btn(p, "<#55FF55>⚔ <white>Gameplay", "<gray>Matchmaking, effects, sounds and the lobby", () -> this.plugin.dialogs().lobby().settings(p, Setting.Category.GAMEPLAY)));
            buttons.add(this.btn(p, "<#55FFFF>◆ <white>Regions", "<gray>Pick the server closest to you", () -> this.regions(p)));
            if (this.plugin.cosmetics() != null) {
                buttons.add(this.btn(p, "<#C9A6FF>✦ <white>Cosmetics", "<gray>Armor trims and your shield design", () -> this.plugin.cosmetics().open(p)));
            }
            if (this.social() != null) {
                buttons.add(this.btn(p, "<#FFD84A>☺ <white>Friends", "<gray>Your friends and requests", () -> this.social().openFriends(p)));
            }

            ActionButton done = Dialogs.button(Text.mm("<green>Done"), null, 200, this.d.action(p, p::closeDialog));
            Dialogs.show(p, base(Text.mm("<white>Settings"), body, List.of()), DialogType.multiAction(buttons).columns(2).exitAction(done).build());
        }
    }

    // ------------------------------------------------------------------ privacy

    public void privacy(Player p) {
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(this.btn(p, "<#C9C9C9>⚔ <white>Requests", "<gray>Who can send you duels, invites, messages and friend requests", () -> this.requests(p, null)));
        buttons.add(this.btn(p, "<#55AAFF>⚑ <white>Location", "<gray>Your flag and region", () -> this.location(p)));
        buttons.add(this.btn(p, "<#AAAAAA>◐ <white>Visibility", "<gray>Who sees you online, spectators and lobby players", () -> this.list(
            p, "Visibility", List.of(Setting.VISIBILITY, Setting.ALLOW_SPECTATORS, Setting.LOBBY_PLAYERS), () -> this.privacy(p)
        )));
        buttons.add(this.wide(p, "<gray>Back", () -> this.hub(p)));
        ActionButton close = Dialogs.button(Text.mm("<green>Close"), null, 200, this.d.action(p, p::closeDialog));
        Dialogs.show(p, base(Text.mm("<white>Privacy Settings"), List.of(), List.of()), DialogType.multiAction(buttons).columns(2).exitAction(close).build());
    }

    /**
     * The Requests screen of the screenshot: per-status group checkboxes, a Tier button, an Overall Ranking slider
     * and the three status buttons. {@code view} is the status whose filter is shown (null = the player's status).
     */
    public void requests(Player p, SocialManager.Status view) {
        SocialManager social = this.social();
        if (social == null) {
            this.hub(p);
        } else {
            SocialManager.Status current = social.status(p);
            SocialManager.Status shown = view != null ? view : current;
            SocialManager.Status filterOf = shown == SocialManager.Status.ONLINE ? SocialManager.Status.ONLINE : SocialManager.Status.DND;
            PvPProfile prof = this.plugin.profiles().get(p);
            SocialManager.Filter f = social.filter(prof, filterOf);
            List<DialogBody> body = new ArrayList<>();
            body.add(
                DialogBody.plainMessage(
                    Text.mm(
                        "<white>This applies to your Party, Duel, Messages, and Friend Request notifications."
                            + (shown == SocialManager.Status.INVISIBLE
                                ? "\n<gray>While Invisible you appear offline: nobody can send you duel requests, everything else uses these filters."
                                : "")
                    ),
                    300
                )
            );
            List<DialogInput> inputs = new ArrayList<>();
            for (SocialManager.Group g : SocialManager.Group.values()) {
                inputs.add(DialogInput.bool("g_" + g.code, Text.mm(g.icon + " <white>" + g.label)).initial(f.groups().contains(g)).build());
            }

            inputs.add(
                DialogInput.numberRange("rank", Text.mm("<yellow>Overall Ranking"), 0.0F, (float)MAX_RANK_FILTER)
                    .step(10.0F)
                    .initial((float)Math.min(MAX_RANK_FILTER, f.topRank()))
                    .width(300)
                    .labelFormat("%s: %s")
                    .build()
            );
            Consumer<DialogResponseView> save = v -> {
                SocialManager.Filter nf = f;
                for (SocialManager.Group g : SocialManager.Group.values()) {
                    Boolean on = v.getBoolean("g_" + g.code);
                    nf = nf.with(g, Boolean.TRUE.equals(on));
                }

                Float r = v.getFloat("rank");
                if (r != null) {
                    nf = nf.withRank(Math.max(0, Math.round(r)));
                }

                social.setFilter(p, filterOf, nf);
            };
            Tier tier = Tier.byIndex(f.minTier());
            List<ActionButton> buttons = new ArrayList<>();
            buttons.add(Dialogs.button(
                Text.mm("<aqua>Tier: <white>" + (tier == null ? "Off" : tier.label)),
                Text.mm("<gray>Strangers (Noobs) must have at least this tier in a kit"),
                300,
                this.d.viewAction(p, v -> {
                    save.accept(v);
                    SocialManager.Filter now = social.filter(this.plugin.profiles().get(p), filterOf);
                    social.setFilter(p, filterOf, now.withTier(nextTier(now.minTier())));
                    Snd.click(p);
                    this.requests(p, shown);
                })
            ));
            for (SocialManager.Status s : SocialManager.Status.values()) {
                String mark = s == current ? "<white>* " : "";
                buttons.add(Dialogs.button(
                    Text.mm(mark + s.color + "● " + (s == current ? "<green>" : "<gray>") + s.label),
                    Text.mm(
                        switch (s) {
                            case ONLINE -> "<gray>Everyone ticked under Online can reach you";
                            case INVISIBLE -> "<gray>Appear offline - no duel requests";
                            case DND -> "<gray>Only the groups ticked under Do Not Disturb can reach you";
                        }
                    ),
                    150,
                    this.d.viewAction(p, v -> {
                        save.accept(v);
                        social.setStatus(p, s);
                        this.requests(p, s);
                    })
                ));
            }

            buttons.add(Dialogs.button(Text.mm("<gray>Back"), null, 150, this.d.viewAction(p, v -> {
                save.accept(v);
                this.privacy(p);
            })));
            ActionButton close = Dialogs.button(Text.mm("<green>Close"), null, 200, this.d.viewAction(p, v -> {
                save.accept(v);
                p.closeDialog();
            }));
            Dialogs.show(
                p, base(Text.mm("<white>Requests - " + shown.label), body, inputs), DialogType.multiAction(buttons).columns(2).exitAction(close).build()
            );
        }
    }

    private static int nextTier(int current) {
        for (int i = 0; i < TIER_STEPS.length; i++) {
            if (TIER_STEPS[i] == current) {
                return TIER_STEPS[(i + 1) % TIER_STEPS.length];
            }
        }

        return TIER_STEPS[1];
    }

    public void location(Player p) {
        List<ActionButton> extra = new ArrayList<>();
        if (this.plugin.countries() != null) {
            extra.add(this.btn(p, "<#55AAFF>⚑ <white>Change Flag", "<gray>Pick the country shown next to your name", () -> {
                p.closeDialog();
                this.plugin.countries().picker(p, () -> this.location(p));
            }));
        }

        extra.add(this.btn(p, "<#55FFFF>◆ <white>Regions", "<gray>Pick the server closest to you", () -> this.regions(p)));
        this.list(p, "Location", List.of(Setting.SHOW_FLAG, Setting.SHOW_REGION), () -> this.privacy(p), extra);
    }

    // ------------------------------------------------------------------ chat & name tag

    public void chat(Player p) {
        this.list(
            p,
            "Chat",
            List.of(Setting.MSG_FROM, Setting.SPEC_CHAT, Setting.SPEC_NOTIFY, Setting.FRIEND_ALERTS, Setting.DEATH_MESSAGES, Setting.CHAT_SOUNDS),
            () -> this.hub(p)
        );
    }

    public void nameTag(Player p) {
        this.list(p, "Name Tag", List.of(Setting.NAMETAG_KIT, Setting.NAMETAG_RANK, Setting.NAMETAG_FLAG, Setting.HEALTH_TAGS, Setting.XP_RANK), () -> this.hub(p));
    }

    // ------------------------------------------------------------------ regions

    public void regions(Player p) {
        Regions regions = this.plugin.regions();
        if (regions == null) {
            this.hub(p);
        } else {
            regions.open(p, () -> this.hub(p));
        }
    }

    // ------------------------------------------------------------------ generic setting list

    private void list(Player p, String title, List<Setting> settings, Runnable back) {
        this.list(p, title, settings, back, List.of());
    }

    /** A screen of click-to-cycle setting tiles, like the Gameplay settings. */
    private void list(Player p, String title, List<Setting> settings, Runnable back, List<ActionButton> extra) {
        if (!this.d.useDialog(p)) {
            new SettingsMenu(this.plugin, p, Setting.Category.GAMEPLAY).open();
        } else {
            PvPProfile prof = this.plugin.profiles().get(p);
            List<LobbyDialogs.Tile> tiles = new ArrayList<>();
            for (Setting s : settings) {
                String cur = prof.get(s);
                String curLabel = cur;
                List<Component> hover = new ArrayList<>();
                hover.add(Text.mm("<gold><b>" + s.title));
                hover.add(Text.mm("<gray>" + Text.esc(s.description)));
                hover.add(Component.empty());
                int shown = 0;
                for (Setting.Option o : SettingsMenu.options(this.plugin, s)) {
                    boolean sel = o.id().equals(cur);
                    if (sel) {
                        curLabel = o.label();
                    }

                    if (shown++ < 10) {
                        hover.add(Text.mm(sel ? "<green><b>▶ " + Text.esc(o.label()) : "<gray>  " + Text.esc(o.label())));
                    }
                }

                hover.add(Component.empty());
                hover.add(Text.mm("<green>▶ Click for the next option"));
                boolean off = "off".equals(cur) || "none".equals(cur) || "nobody".equals(cur) || "hide".equals(cur);
                tiles.add(new LobbyDialogs.Tile(new ItemStack(s.icon), Text.mm("<gold><b>" + s.title), Text.mm((off ? "<red>" : "<green>") + Text.esc(curLabel)), hover, () -> {
                    if (this.apply(p, s, SettingsMenu.step(this.plugin, this.plugin.profiles().get(p), s, 1))) {
                        Snd.click(p);
                    }

                    this.list(p, title, settings, back, extra);
                }));
            }

            List<ActionButton> buttons = new ArrayList<>(extra);
            this.plugin
                .dialogs()
                .lobby()
                .screen(
                    p,
                    LobbyDialogs.title(title, "Click a setting to change it!"),
                    tiles,
                    List.of(),
                    null,
                    buttons,
                    2,
                    Dialogs.button(Text.mm("<gray>« Back"), null, 200, this.d.action(p, back))
                );
        }
    }

    /** Applies a setting change and its side effects (nametags, tab, lobby). */
    public boolean apply(Player p, Setting s, String value) {
        if (!SettingsMenu.apply(this.plugin, p, s, value)) {
            return false;
        } else {
            switch (s) {
                case NAMETAG_RANK, NAMETAG_FLAG, SHOW_FLAG -> this.plugin.boards().refreshNametag(p);
                case XP_RANK -> this.plugin.lobby().applyRankLevel(p);
                case VISIBILITY, STATUS -> {
                    if (this.plugin.tab() != null) {
                        this.plugin.tab().refreshSoon();
                    }
                }
                default -> {
                }
            }

            return true;
        }
    }
}
