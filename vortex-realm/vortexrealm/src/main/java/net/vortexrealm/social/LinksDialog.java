package net.vortexrealm.social;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.util.Fmt;
import net.vortexrealm.util.Snd;
import net.vortexrealm.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class LinksDialog {
    private static final Pattern INVITE = Pattern.compile("(?:discord\\.gg|discord(?:app)?\\.com/invite)/([A-Za-z0-9-]+)");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4L)).build();
    private static volatile int members = -1;
    private static volatile int online = -1;
    private static volatile String guild = "";
    private static volatile long fetched;

    private LinksDialog() {
    }

    public static void refresh() {
        if (System.currentTimeMillis() - fetched >= 600000L) {
            fetched = System.currentTimeMillis();
            String code = inviteCode(Cfg.s(Opt.LINK_DISCORD));
            if (code != null) {
                HttpRequest req = HttpRequest.newBuilder(URI.create("https://discord.com/api/v10/invites/" + code + "?with_counts=true"))
                    .timeout(Duration.ofSeconds(6L))
                    .header("User-Agent", "VortexRealm-Plugin (server links, 1.0)")
                    .GET()
                    .build();
                HTTP.sendAsync(req, BodyHandlers.ofString()).thenAccept(res -> {
                    if (res.statusCode() == 200) {
                        try {
                            JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
                            members = o.has("approximate_member_count") ? o.get("approximate_member_count").getAsInt() : -1;
                            online = o.has("approximate_presence_count") ? o.get("approximate_presence_count").getAsInt() : -1;
                            if (o.has("guild") && o.getAsJsonObject("guild").has("name")) {
                                guild = o.getAsJsonObject("guild").get("name").getAsString();
                            }
                        } catch (RuntimeException var2) {
                        }
                    }
                }).exceptionally(err -> null);
            }
        }
    }

    static String inviteCode(String link) {
        if (link == null) {
            return null;
        } else {
            Matcher m = INVITE.matcher(link);
            return m.find() ? m.group(1) : null;
        }
    }

    private static String url(String link) {
        String l = link.trim();
        return !l.startsWith("http://") && !l.startsWith("https://") ? "https://" + l : l;
    }

    public static void open(VortexRealm plugin, Player p, String label) {
        String l = label == null ? "" : label.toLowerCase(Locale.ROOT);
        if (l.contains("discord")) {
            discord(plugin, p);
        } else if (l.contains("store")) {
            store(plugin, p);
        } else {
            all(plugin, p);
        }
    }

    public static void discord(VortexRealm plugin, Player p) {
        String link = Cfg.s(Opt.LINK_DISCORD);
        if (link != null && !link.isBlank()) {
            refresh();
            String url = url(link);
            String server = Text.esc(Cfg.s(Opt.SERVER_NAME));
            List<DialogBody> body = new ArrayList<>();
            body.add(DialogBody.plainMessage(Text.mm("<#8C96FF>━━━━━━━━━━━━━━━━━━━━━━━━━━━━"), 300));
            body.add(
                DialogBody.item(new ItemStack(Material.ECHO_SHARD))
                    .description(
                        DialogBody.plainMessage(
                            Text.mm("<white><b>Join the " + server + " community</b>\n<gray>Tier tests, events, giveaways, news and support."), 240
                        )
                    )
                    .showDecorations(false)
                    .showTooltip(false)
                    .build()
            );
            if (members > 0) {
                body.add(
                    DialogBody.plainMessage(
                        Text.mm(
                            "<#3BA55D>●</#3BA55D> <white>"
                                + Fmt.num(Math.max(0, online))
                                + "</white> <gray>online</gray>      <#B9BBBE>●</#B9BBBE> <white>"
                                + Fmt.num(members)
                                + "</white> <gray>members"
                        ),
                        300
                    )
                );
            }

            body.add(DialogBody.plainMessage(Text.mm("<#8C96FF>" + Text.esc(link.replaceFirst("^https?://", ""))), 300));
            body.add(DialogBody.plainMessage(Text.mm("<#8C96FF>━━━━━━━━━━━━━━━━━━━━━━━━━━━━"), 300));
            List<ActionButton> buttons = new ArrayList<>();
            buttons.add(
                ActionButton.builder(Text.mm("<#FFFFFF><b>Join Discord"))
                    .width(150)
                    .tooltip(Text.mm("<gray>Opens the invite in your browser"))
                    .action(DialogAction.staticAction(ClickEvent.openUrl(url)))
                    .build()
            );
            buttons.add(
                ActionButton.builder(Text.mm("<#C9CDFB>Copy Invite"))
                    .width(150)
                    .tooltip(Text.mm("<gray>Copies " + Text.esc(url) + " to your clipboard"))
                    .action(DialogAction.staticAction(ClickEvent.copyToClipboard(url)))
                    .build()
            );
            String title = guild != null && !guild.isBlank() ? "<#5865F2><b>DISCORD</b> <dark_gray>• <white>" + Text.esc(guild) : "<#5865F2><b>DISCORD";
            show(p, Text.mm(title), body, buttons, 2);
            Snd.click(p);
        } else {
            p.sendMessage(Text.mm("<gray>The Discord link isn't set yet."));
        }
    }

    public static void store(VortexRealm plugin, Player p) {
        String link = Cfg.s(Opt.LINK_STORE);
        if (link != null && !link.isBlank()) {
            String url = url(link);
            List<DialogBody> body = new ArrayList<>();
            body.add(DialogBody.plainMessage(Text.mm("<#FFB547>━━━━━━━━━━━━━━━━━━━━━━━━━━━━"), 300));
            body.add(
                DialogBody.item(new ItemStack(Material.GOLD_INGOT))
                    .description(
                        DialogBody.plainMessage(
                            Text.mm(
                                "<white><b>Support "
                                    + Text.esc(Cfg.s(Opt.SERVER_NAME))
                                    + "</b>\n<gray>Ranks and perks - every purchase keeps the server running."
                            ),
                            240
                        )
                    )
                    .showDecorations(false)
                    .showTooltip(false)
                    .build()
            );
            body.add(DialogBody.plainMessage(Text.mm("<#FFB547>" + Text.esc(link.replaceFirst("^https?://", ""))), 300));
            List<ActionButton> buttons = new ArrayList<>();
            buttons.add(ActionButton.builder(Text.mm("<#FFD08A><b>Open Store")).width(150).action(DialogAction.staticAction(ClickEvent.openUrl(url))).build());
            buttons.add(ActionButton.builder(Text.mm("<gray>Copy Link")).width(150).action(DialogAction.staticAction(ClickEvent.copyToClipboard(url))).build());
            show(p, Text.mm("<#FF8A3D><b>STORE"), body, buttons, 2);
            Snd.click(p);
        } else {
            p.sendMessage(Text.mm("<gray>The store isn't open yet."));
        }
    }

    public static void all(VortexRealm plugin, Player p) {
        List<ActionButton> buttons = new ArrayList<>();
        String discord = Cfg.s(Opt.LINK_DISCORD);
        String store = Cfg.s(Opt.LINK_STORE);
        String site = Cfg.s(Opt.LINK_WEBSITE);
        if (discord != null && !discord.isBlank()) {
            buttons.add(
                ActionButton.builder(Text.mm("<#8C96FF><b>Discord"))
                    .width(120)
                    .tooltip(Text.mm("<gray>The community, events and support"))
                    .action(plugin.dialogs().action(p, () -> discord(plugin, p)))
                    .build()
            );
        }

        if (store != null && !store.isBlank()) {
            buttons.add(ActionButton.builder(Text.mm("<#FFB547><b>Store")).width(120).action(DialogAction.staticAction(ClickEvent.openUrl(url(store)))).build());
        }

        if (site != null && !site.isBlank()) {
            buttons.add(
                ActionButton.builder(Text.mm("<#FF7A1A><b>Website")).width(120).action(DialogAction.staticAction(ClickEvent.openUrl(url(site)))).build()
            );
        } else if (plugin.web() != null && plugin.web().available()) {
            buttons.add(
                ActionButton.builder(Text.mm("<#FF7A1A><b>Website"))
                    .width(120)
                    .action(DialogAction.staticAction(ClickEvent.openUrl(plugin.webBase(p) + "/")))
                    .build()
            );
        }

        if (buttons.isEmpty()) {
            p.sendMessage(Text.mm("<gray>No links configured yet."));
        } else {
            List<DialogBody> body = List.of(
                DialogBody.plainMessage(Text.mm("<gray>Everything " + Text.esc(Cfg.s(Opt.SERVER_NAME)) + " outside the game."), 300)
            );
            show(p, Text.mm("<#FF8A3D><b>LINKS"), body, buttons, Math.min(3, buttons.size()));
        }
    }

    private static void show(Player p, Component title, List<DialogBody> body, List<ActionButton> buttons, int cols) {
        DialogBase base = DialogBase.builder(title).canCloseWithEscape(true).pause(false).afterAction(DialogAfterAction.CLOSE).body(body).build();
        ActionButton close = ActionButton.builder(Text.mm("<gray>Close")).width(120).build();
        p.showDialog(Dialog.create(b -> b.empty().base(base).type(DialogType.multiAction(buttons).columns(cols).exitAction(close).build())));
    }
}
