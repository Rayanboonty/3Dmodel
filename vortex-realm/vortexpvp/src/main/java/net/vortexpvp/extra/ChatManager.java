package net.vortexpvp.extra;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Pattern;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.board.BoardManager;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.data.KitStats;
import net.vortexpvp.data.PvPProfile;
import net.vortexpvp.data.Setting;
import net.vortexpvp.kit.Kit;
import net.vortexpvp.rank.Tier;
import net.vortexpvp.smp.WorldMode;
import net.vortexpvp.social.Countries;
import net.vortexpvp.social.Ranks;
import net.vortexpvp.util.Fmt;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public final class ChatManager implements Listener, TabExecutor {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('&').hexColors().build();
    private static final Pattern ITEM = Pattern.compile("(?i)\\[(item|i)]");
    private final VortexPvP plugin;
    private final File dataFile;
    private final Map<UUID, ChatManager.Info> info = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> ignores = new ConcurrentHashMap<>();
    private final Set<UUID> msgOff = ConcurrentHashMap.newKeySet();
    private final Set<UUID> spies = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> lastPartner = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastChat = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastText = new ConcurrentHashMap<>();
    private volatile boolean muted;
    private volatile Set<String> onlineNames = Set.of();

    public ChatManager(VortexPvP plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "chat-data.yml");
    }

    public void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.dataFile);
        this.ignores.clear();
        ConfigurationSection sec = y.getConfigurationSection("ignores");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                try {
                    Set<UUID> set = ConcurrentHashMap.newKeySet();

                    for (String s : sec.getStringList(k)) {
                        set.add(UUID.fromString(s));
                    }

                    this.ignores.put(UUID.fromString(k), set);
                } catch (IllegalArgumentException var8) {
                }
            }
        }

        this.msgOff.clear();
        this.spies.clear();

        for (String s : y.getStringList("msg-off")) {
            parse(s, this.msgOff);
        }

        for (String s : y.getStringList("spy")) {
            parse(s, this.spies);
        }
    }

    private static void parse(String s, Set<UUID> into) {
        try {
            into.add(UUID.fromString(s));
        } catch (IllegalArgumentException var3) {
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();

        for (Entry<UUID, Set<UUID>> e : this.ignores.entrySet()) {
            if (!e.getValue().isEmpty()) {
                y.set("ignores." + e.getKey(), e.getValue().stream().map(UUID::toString).toList());
            }
        }

        y.set("msg-off", this.msgOff.stream().map(UUID::toString).toList());
        y.set("spy", this.spies.stream().map(UUID::toString).toList());

        try {
            y.save(this.dataFile);
        } catch (IOException var4) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save chat-data.yml", (Throwable)var4);
        }
    }

    /** Staff with /socialspy on. */
    public boolean spying(Player p) {
        return this.spies.contains(p.getUniqueId());
    }

    public boolean ignores(Player who, Player other) {
        Set<UUID> s = this.ignores.get(who.getUniqueId());
        return s != null && s.contains(other.getUniqueId());
    }

    public void tick() {
        Set<String> names = new HashSet<>();

        for (Player p : Bukkit.getOnlinePlayers()) {
            this.refresh(p);
            names.add(p.getName().toLowerCase(Locale.ROOT));
        }

        this.onlineNames = Set.copyOf(names);
        this.info.keySet().removeIf(u -> Bukkit.getPlayer(u) == null);
    }

    private void refresh(Player p) {
        String zone;
        Component modeTag;
        if (this.plugin.worlds().managed(p.getWorld())) {
            zone = "PVP";
            modeTag = Text.mm("<dark_gray>[<gold>PvP</gold>]</dark_gray> ");
        } else if (this.plugin.modes() != null) {
            WorldMode m = this.plugin.modes().mode(p.getWorld());
            zone = m.name();
            modeTag = Text.mm("<dark_gray>[" + m.color + Text.esc(m.display) + "</dark_gray><dark_gray>]</dark_gray> ");
        } else {
            zone = "SMP";
            modeTag = Component.empty();
        }

        Component tier = Component.empty();
        if (Cfg.bool(Opt.CHAT_TIERS)) {
            PvPProfile prof = this.plugin.profiles().getIfLoaded(p.getUniqueId());
            String kit = prof == null ? null : prof.bestKit();
            Tier t = kit == null ? null : prof.tierIn(kit);
            if (t != null) {
                Kit k = this.plugin.kits().get(kit);
                tier = Text.mm(t.mm() + " ")
                    .hoverEvent(
                        HoverEvent.showText(Text.mm("<gray>Best tier: " + t.mm() + (k == null ? "" : " <gray>in " + k.color + Text.esc(k.displayName))))
                    );
            }
        }

        Component hearts = Component.empty();
        if ("LIFESTEAL".equals(zone) && this.plugin.modes() != null) {
            hearts = Text.mm("<red>❤" + this.plugin.modes().stats(p.getUniqueId()).hearts + "</red> ");
        }

        Component prefix = this.papi(p, "%luckperms_prefix%");
        String nameColor = "#FFFFFF";
        if (this.plugin.serverRanks() != null) {
            Ranks.Rank rank = this.plugin.serverRanks().of(p);
            nameColor = rank.nameColor;
            if (prefix.equals(Component.empty())) {
                prefix = rank.prefix();
            }
        }

        this.info
            .put(
                p.getUniqueId(),
                new ChatManager.Info(zone, modeTag, tier, prefix, this.papi(p, "%luckperms_suffix%"), hearts, this.card(p, modeTag), nameColor)
            );
    }

    private Component card(Player p, Component modeTag) {
        List<Component> lines = new ArrayList<>();
        Component head = Component.empty();
        if (this.plugin.serverRanks() != null) {
            Ranks.Rank rank = this.plugin.serverRanks().of(p);
            head = rank.prefix().append(rank.name(p.getName()).decorate(TextDecoration.BOLD));
        } else {
            head = Text.mm("<white><b>" + Text.esc(p.getName()));
        }

        lines.add(head);
        if (this.plugin.countries() != null) {
            Countries.Country c = this.plugin.countries().flagHidden(p.getUniqueId()) ? null : this.plugin.countries().of(p.getUniqueId());
            if (c != null) {
                lines.add(c.tag().append(Text.mm(" <gray>" + Text.esc(c.name()))));
            }
        }

        lines.add(Component.empty());
        PvPProfile prof = this.plugin.profiles().getIfLoaded(p.getUniqueId());
        if (prof != null) {
            List<Entry<String, KitStats>> ranked = new ArrayList<>();
            int wins = 0;
            int losses = 0;

            for (Entry<String, KitStats> e : prof.kits.entrySet()) {
                wins += e.getValue().wins + e.getValue().unrankedWins;
                losses += e.getValue().losses + e.getValue().unrankedLosses;
                if (e.getValue().ranked() && this.plugin.kits().get(e.getKey()) != null) {
                    ranked.add(e);
                }
            }

            ranked.sort(Comparator.<Entry<String, KitStats>>comparingInt(ex -> -ex.getValue().tier).thenComparingInt(ex -> -ex.getValue().elo));
            if (ranked.isEmpty()) {
                lines.add(Text.mm("<gray>Tiers: <white>Unranked"));
            } else {
                Component tiers = Text.mm("<gray>Tiers: ");

                for (int i = 0; i < Math.min(3, ranked.size()); i++) {
                    Kit k = this.plugin.kits().get(ranked.get(i).getKey());
                    tiers = tiers.append(k.iconComponent())
                        .append((Component)Component.space())
                        .append(BoardManager.tierTag(ranked.get(i).getValue().tier()))
                        .append((Component)Component.text("  "));
                }

                lines.add(tiers);
            }

            int total = wins + losses;
            lines.add(
                Text.mm(
                    "<gray>Duels: <green>"
                        + wins
                        + "W <red>"
                        + losses
                        + "L <dark_gray>• <gray>"
                        + (total == 0 ? "-" : Fmt.percent((double)wins / total))
                        + " win rate"
                )
            );
            lines.add(Text.mm("<gray>FFA kills: <white>" + prof.ffaKills));
        }

        lines.add(Text.mm("<gray>Playing: ").append(modeTag.equals(Component.empty()) ? Text.mm("<white>SMP") : modeTag));
        lines.add(Component.empty());
        lines.add(Text.mm("<green>▶ Click <gray>for the profile  <yellow>⇧ Shift-click <gray>to message"));
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    private Component papi(Player p, String placeholder) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return Component.empty();
        } else {
            try {
                String s = PlaceholderAPI.setPlaceholders(p, placeholder);
                return s != null && !s.isEmpty() && !s.equals(placeholder) ? LEGACY.deserialize(s.replace('§', '&')) : Component.empty();
            } catch (Throwable var4) {
                return Component.empty();
            }
        }
    }

    private String zone(UUID u) {
        ChatManager.Info i = this.info.get(u);
        return i == null ? "?" : i.zone();
    }

    @EventHandler(
        priority = EventPriority.MONITOR
    )
    public void onWorldChange(PlayerChangedWorldEvent e) {
        this.refresh(e.getPlayer());
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        this.refresh(p);
        if (!p.hasPlayedBefore() && !Cfg.s(Opt.FIRST_JOIN_MESSAGE).isBlank()) {
            int count = Bukkit.getOfflinePlayers().length;
            e.joinMessage(
                Text.mm(Cfg.s(Opt.FIRST_JOIN_MESSAGE), Placeholder.unparsed("name", p.getName()), Placeholder.unparsed("count", String.valueOf(count)))
            );
        } else {
            String f = Cfg.s(Opt.JOIN_MESSAGE);
            e.joinMessage(f.isBlank() ? null : Text.mm(f, Placeholder.unparsed("name", p.getName())));
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        String f = Cfg.s(Opt.QUIT_MESSAGE);
        e.quitMessage(f.isBlank() ? null : Text.mm(f, Placeholder.unparsed("name", e.getPlayer().getName())));
        this.lastChat.remove(u);
        this.lastText.remove(u);
        this.lastPartner.remove(u);
        this.lastPartner.values().removeIf(u::equals);
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        String plain = PlainTextComponentSerializer.plainText().serialize(e.message());
        boolean bypass = p.hasPermission("vortexpvp.chat.bypass");
        if (this.muted && !bypass) {
            e.setCancelled(true);
            p.sendMessage(Text.mm("<red>Chat is muted right now."));
        } else if (this.plugin.welcome() != null && this.plugin.welcome().mustVerify(p)) {
            e.setCancelled(true);
            p.sendMessage(
                Text.mm("<#FFB020>Verify first to chat: <white><click:run_command:'/verify'>/verify</click></white> <gray>(one quick maths question)")
            );
        } else {
            if (!bypass) {
                long now = System.currentTimeMillis();
                Long last = this.lastChat.get(p.getUniqueId());
                int cd = Cfg.i(Opt.CHAT_COOLDOWN);
                if (last != null && now - last < cd) {
                    e.setCancelled(true);
                    p.sendMessage(Text.mm("<red>Slow down! <gray>(" + String.format(Locale.US, "%.1f", (cd - (now - last)) / 1000.0) + "s)"));
                    return;
                }

                String norm = plain.trim().toLowerCase(Locale.ROOT);
                if (Cfg.bool(Opt.CHAT_ANTI_REPEAT) && norm.equals(this.lastText.get(p.getUniqueId())) && last != null && now - last < 30000L) {
                    e.setCancelled(true);
                    p.sendMessage(Text.mm("<red>Don't repeat the same message."));
                    return;
                }

                this.lastChat.put(p.getUniqueId(), now);
                this.lastText.put(p.getUniqueId(), norm);
            }

            Component message = e.message();
            if (Cfg.bool(Opt.CHAT_ANTI_CAPS) && !bypass && mostlyCaps(plain)) {
                message = Component.text(plain.toLowerCase(Locale.ROOT));
            }

            boolean global = false;
            if (Cfg.bool(Opt.CHAT_PER_MODE) && plain.startsWith("!") && plain.length() > 1) {
                global = true;
                message = message.replaceText(TextReplacementConfig.builder().matchLiteral("!").once().replacement("").build());
            }

            if (Cfg.bool(Opt.CHAT_ITEM) && ITEM.matcher(plain).find()) {
                ItemStack hand = p.getInventory().getItemInMainHand().clone();
                Component shown = hand.getType().isAir() ? Text.mm("<gray>[nothing]") : hand.displayName();
                message = message.replaceText(TextReplacementConfig.builder().match(ITEM).replacement(shown).build());
            }

            Set<String> mentioned = new HashSet<>();
            if (Cfg.bool(Opt.CHAT_MENTIONS)) {
                for (String word : plain.split("[^A-Za-z0-9_]+")) {
                    String w = word.toLowerCase(Locale.ROOT);
                    if (w.length() >= 3 && this.onlineNames.contains(w) && !w.equalsIgnoreCase(p.getName())) {
                        mentioned.add(w);
                    }
                }

                for (String name : mentioned) {
                    message = message.replaceText(
                        TextReplacementConfig.builder()
                            .match("(?i)@?\\b" + Pattern.quote(name) + "\\b")
                            .replacement(m -> Component.text(m.content(), NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
                            .build()
                    );
                }
            }

            e.message(message);
            String myZone = this.zone(p.getUniqueId());
            boolean perMode = Cfg.bool(Opt.CHAT_PER_MODE) && !global;
            boolean poolOnly = this.plugin.hackerPool() != null && this.plugin.hackerPool().pooled(p) && Cfg.bool(Opt.POOL_CHAT);
            e.viewers()
                .removeIf(
                    a -> a instanceof Player v
                        && !v.equals(p)
                        && (
                            this.ignores(v, p)
                                || poolOnly && !this.plugin.hackerPool().pooled(v) && !v.hasPermission("vortexpvp.staff")
                                || perMode && !this.zone(v.getUniqueId()).equals(myZone) && !this.spies.contains(v.getUniqueId())
                        )
                );
            if (!mentioned.isEmpty()) {
                Set<String> ping = Set.copyOf(mentioned);
                Tasks.sync(() -> {
                    for (Player o : Bukkit.getOnlinePlayers()) {
                        if (ping.contains(o.getName().toLowerCase(Locale.ROOT)) && !this.ignores(o, p)) {
                            PvPProfile prof = this.plugin.profiles().getIfLoaded(o.getUniqueId());
                            if (prof == null || prof.on(Setting.CHAT_SOUNDS)) {
                                Snd.play(o, "block.note_block.pling", 0.8F, 1.8F);
                            }

                            o.sendActionBar(Text.mm("<yellow><b>" + Text.esc(p.getName()) + "</b> <gray>mentioned you"));
                        }
                    }
                });
            }

            if (Cfg.bool(Opt.CHAT_FORMAT_ON)) {
                final boolean isGlobal = global;
                e.renderer(new ChatRenderer() {
                    @Override
                    public Component render(Player source, Component sourceDisplayName, Component msg, Audience viewer) {
                        return ChatManager.this.format(source, msg, viewer, isGlobal);
                    }
                });
            }
        }
    }

    private Component format(Player source, Component msg, Audience viewer, boolean global) {
        ChatManager.Info i = this.info.get(source.getUniqueId());
        if (i == null) {
            i = new ChatManager.Info(
                "?", Component.empty(), Component.empty(), Component.empty(), Component.empty(), Component.empty(), Component.empty(), "#FFFFFF"
            );
        }

        boolean elsewhere = !(!global && viewer instanceof Player v) || !this.zone(v.getUniqueId()).equals(i.zone());
        Component mode = (Component)(global
            ? Text.mm("<dark_gray>[<aqua>Global</aqua>]</dark_gray> ").append(i.modeTag())
            : (elsewhere && Cfg.bool(Opt.CHAT_PER_MODE) ? i.modeTag() : Component.empty()));
        Component name = Text.mm("<" + i.nameColor() + ">" + Text.esc(source.getName()))
            .hoverEvent(
                HoverEvent.showText(i.card().equals(Component.empty()) ? Text.mm("<gray>Click to message <white>" + Text.esc(source.getName())) : i.card())
            )
            .clickEvent(
                i.card().equals(Component.empty())
                    ? ClickEvent.suggestCommand("/msg " + source.getName() + " ")
                    : ClickEvent.runCommand("/stats " + source.getName())
            )
            .insertion("/msg " + source.getName() + " ");
        Player reader = viewer instanceof Player v2 ? v2 : null;
        if (this.plugin.welcome() != null) {
            name = name.append(this.plugin.welcome().badge(source.getUniqueId(), reader));
        }

        if (this.plugin.countries() != null && Cfg.bool(Opt.COUNTRY_ON_NAMETAG)) {
            name = name.append(this.plugin.countries().tag(source.getUniqueId(), reader));
        }

        name = name.append(net.vortexpvp.api.NameDecor.chat(source.getUniqueId()));

        try {
            return Text.mm(
                Cfg.s(Opt.CHAT_FORMAT),
                Placeholder.component("mode", mode),
                Placeholder.component("tier", i.tier()),
                Placeholder.component(
                    "prefix",
                    this.plugin.hackerPool() != null && this.plugin.hackerPool().pooled(source.getUniqueId())
                        ? this.plugin.hackerPool().badge(source)
                        : this.viewerPrefix(source, i.prefix(), reader)
                ),
                Placeholder.component("suffix", i.suffix()),
                Placeholder.component("hearts", i.hearts()),
                Placeholder.component("name", name),
                Placeholder.component("message", msg)
            );
        } catch (RuntimeException var11) {
            return ((TextComponent)((TextComponent)Component.text("<").append(name)).append(Component.text("> "))).append(msg);
        }
    }

    private Component viewerPrefix(Player source, Component cached, Player reader) {
        if (this.plugin.serverRanks() == null) {
            return cached;
        } else {
            Ranks.Rank rank = this.plugin.serverRanks().of(source);
            return rank.icon != null && rank.icon.startsWith("glyph:") && cached.equals(rank.prefix()) ? rank.prefix(reader) : cached;
        }
    }

    private static boolean mostlyCaps(String s) {
        int letters = 0;
        int upper = 0;

        for (char c : s.toCharArray()) {
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) {
                    upper++;
                }
            }
        }

        return letters >= 6 && upper > letters * 0.7;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        switch (cmd) {
            case "mutechat":
                if (!sender.hasPermission("vortexpvp.staff")) {
                    sender.sendMessage(Text.mm("<red>You don't have permission."));
                    return true;
                }

                this.muted = !this.muted;
                Bukkit.broadcast(Text.mm(this.muted ? "<red><b>Chat has been muted</b> <gray>by staff." : "<green><b>Chat is open again."));
                return true;
            case "clearchat":
                if (!sender.hasPermission("vortexpvp.staff")) {
                    sender.sendMessage(Text.mm("<red>You don't have permission."));
                    return true;
                }

                for (Player o : Bukkit.getOnlinePlayers()) {
                    if (!o.hasPermission("vortexpvp.staff")) {
                        for (int i = 0; i < 100; i++) {
                            o.sendMessage(Component.empty());
                        }
                    }

                    o.sendMessage(Text.mm("<gray>Chat was cleared by staff."));
                }

                return true;
            default:
                if (!Cfg.bool(Opt.PRIVATE_MESSAGES)) {
                    sender.sendMessage(Text.mm("<red>Private messages are disabled on this server."));
                    return true;
                } else {
                    switch (cmd) {
                        case "msg": {
                                if (args.length < 2) {
                                    sender.sendMessage(Text.mm("<red>Usage: /" + label + " <player> <message>"));
                                    return true;
                                }

                                Player tx = Bukkit.getPlayer(args[0]);
                                if (tx == null || sender instanceof Player sp && !sp.canSee(tx)) {
                                    sender.sendMessage(Text.mm("<red>That player is not online."));
                                    return true;
                                }

                                this.send(sender, tx, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                                break;
                        }
                        case "reply": {
                                if (!(sender instanceof Player p)) {
                                    return true;
                                }

                                if (args.length == 0) {
                                    sender.sendMessage(Text.mm("<red>Usage: /r <message>"));
                                    return true;
                                }

                                UUID partner = this.lastPartner.get(p.getUniqueId());
                                Player tx = partner == null ? null : Bukkit.getPlayer(partner);
                                if (tx == null) {
                                    sender.sendMessage(Text.mm("<red>Nobody to reply to."));
                                    return true;
                                }

                                this.send(p, tx, String.join(" ", args));
                                break;
                        }
                        case "ignore": {
                                if (!(sender instanceof Player p)) {
                                    return true;
                                }

                                Set<UUID> var21 = this.ignores.computeIfAbsent(p.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
                                if (args.length == 0) {
                                    List<String> names = new ArrayList<>();

                                    for (UUID u : var21) {
                                        String n = Bukkit.getOfflinePlayer(u).getName();
                                        names.add(n == null ? u.toString().substring(0, 8) : n);
                                    }

                                    p.sendMessage(
                                        Text.mm(
                                            names.isEmpty()
                                                ? "<gray>You don't ignore anyone. <dark_gray>/ignore <player>"
                                                : "<gray>Ignored: <white>" + Text.esc(String.join(", ", names))
                                        )
                                    );
                                    return true;
                                }

                                Player t = Bukkit.getPlayer(args[0]);
                                if (t == null) {
                                    p.sendMessage(Text.mm("<red>That player is not online."));
                                    return true;
                                }

                                if (t.equals(p)) {
                                    p.sendMessage(Text.mm("<red>You can't ignore yourself."));
                                    return true;
                                }

                                if (t.hasPermission("vortexpvp.staff") && !var21.contains(t.getUniqueId())) {
                                    p.sendMessage(Text.mm("<red>You can't ignore staff."));
                                    return true;
                                }

                                boolean now = !var21.remove(t.getUniqueId());
                                if (now) {
                                    var21.add(t.getUniqueId());
                                }

                                this.save();
                                p.sendMessage(
                                    Text.mm(
                                        now
                                            ? "<gray>You now <red>ignore</red> <white>" + Text.esc(t.getName()) + "</white> (chat, messages, teleport requests)."
                                            : "<gray>You no longer ignore <white>" + Text.esc(t.getName()) + "</white>."
                                    )
                                );
                                break;
                        }
                        case "msgtoggle": {
                                if (!(sender instanceof Player p)) {
                                    return true;
                                }

                                boolean off = !this.msgOff.remove(p.getUniqueId());
                                if (off) {
                                    this.msgOff.add(p.getUniqueId());
                                }

                                this.save();
                                p.sendMessage(Text.mm(off ? "<gray>Private messages: <red><b>OFF" : "<gray>Private messages: <green><b>ON"));
                                break;
                        }
                        case "socialspy": {
                                if (!(sender instanceof Player p) || !p.hasPermission("vortexpvp.staff")) {
                                    sender.sendMessage(Text.mm("<red>You don't have permission."));
                                    return true;
                                }

                                boolean on = !this.spies.remove(p.getUniqueId());
                                if (on) {
                                    this.spies.add(p.getUniqueId());
                                }

                                this.save();
                                p.sendMessage(
                                    Text.mm(
                                        on ? "<gray>Spy: <green><b>ON</b></green> <dark_gray>(private messages and every mode's chat)" : "<gray>Spy: <red><b>OFF"
                                    )
                                );
                                break;
                        }
                        default: {
                                return false;
                        }
                    }

                    return true;
                }
        }
    }

    private void send(CommandSender from, Player to, String text) {
        Player fp = from instanceof Player p ? p : null;
        if (fp != null && fp.equals(to)) {
            from.sendMessage(Text.mm("<red>You can't message yourself."));
        } else {
            boolean staff = from.hasPermission("vortexpvp.staff");
            if (fp == null || staff || !this.msgOff.contains(to.getUniqueId()) && !this.ignores(to, fp)) {
                if (fp != null && this.muted && !fp.hasPermission("vortexpvp.chat.bypass")) {
                    from.sendMessage(Text.mm("<red>Chat is muted right now."));
                } else if (fp != null && !staff && this.plugin.social() != null && !this.plugin.social().check(fp, to, net.vortexpvp.social.SocialManager.Kind.MESSAGE)) {
                    return;
                } else {
                    String fromName = fp == null ? "Console" : fp.getName();
                    Component body = Component.text(text, NamedTextColor.GRAY);
                    from.sendMessage(
                        Text.mm(
                                "<dark_gray>[<gold>me</gold> <gray>→</gray> <white><click:suggest_command:'/msg "
                                    + to.getName()
                                    + " '>"
                                    + Text.esc(to.getName())
                                    + "</click></white>]</dark_gray> "
                            )
                            .append(body)
                    );
                    to.sendMessage(
                        Text.mm(
                                "<dark_gray>[<white><click:suggest_command:'/msg "
                                    + fromName
                                    + " '><hover:show_text:'<gray>Click to reply'>"
                                    + Text.esc(fromName)
                                    + "</hover></click></white> <gray>→</gray> <gold>me</gold>]</dark_gray> "
                            )
                            .append(body)
                    );
                    PvPProfile prof = this.plugin.profiles().getIfLoaded(to.getUniqueId());
                    if (prof == null || prof.on(Setting.CHAT_SOUNDS)) {
                        Snd.play(to, "entity.experience_orb.pickup", 0.6F, 1.6F);
                    }

                    if (fp != null) {
                        this.lastPartner.put(fp.getUniqueId(), to.getUniqueId());
                        this.lastPartner.put(to.getUniqueId(), fp.getUniqueId());
                    }

                    Component spy = Text.mm("<dark_gray>[Spy] <gray>" + Text.esc(fromName) + " → " + Text.esc(to.getName()) + ": ").append(body);

                    for (UUID u : this.spies) {
                        Player s = Bukkit.getPlayer(u);
                        if (s != null && !s.equals(fp) && !s.equals(to)) {
                            s.sendMessage(spy);
                        }
                    }
                }
            } else {
                from.sendMessage(
                    fp != null && this.plugin.social() != null && !this.plugin.social().appearsOnline(fp, to)
                        ? Text.mm("<red>That player is not online.")
                        : Text.mm("<red>" + Text.esc(to.getName()) + " doesn't accept messages from you.")
                );
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1 && (cmd.equals("msg") || cmd.equals("ignore"))) {
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (!(sender instanceof Player p && !p.canSee(o))) {
                    out.add(o.getName());
                }
            }

            String pre = args[0].toLowerCase(Locale.ROOT);
            out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(pre));
        }

        return out;
    }

    private record Info(String zone, Component modeTag, Component tier, Component prefix, Component suffix, Component hearts, Component card, String nameColor) {
    }
}
