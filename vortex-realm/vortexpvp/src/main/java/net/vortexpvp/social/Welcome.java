package net.vortexpvp.social;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.bossbar.BossBar.Color;
import net.kyori.adventure.bossbar.BossBar.Overlay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.dialog.Dialogs;
import net.vortexpvp.fx.Toasts;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.util.ClientVersion;
import net.vortexpvp.util.Icons;
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
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class Welcome implements Listener, TabExecutor {
    private static final String ACCENT = "#FFB020";
    private static final String GOOD = "#3DDC84";
    private final VortexPvP plugin;
    private final File file;
    private final Set<UUID> welcomed = new HashSet<>();
    private final Set<UUID> verified = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> guide = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Integer> wrong = new HashMap<>();
    private final Map<UUID, Long> lockedUntil = new HashMap<>();
    private final Map<UUID, Welcome.Question> chatQuestions = new HashMap<>();

    public Welcome(VortexPvP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "welcome.yml");
    }

    public void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        readUuids(y.getStringList("welcomed"), this.welcomed);
        readUuids(y.getStringList("verified"), this.verified);
        ConfigurationSection g = y.getConfigurationSection("guide");
        if (g != null) {
            for (String k : g.getKeys(false)) {
                try {
                    this.guide.put(UUID.fromString(k), g.getInt(k));
                } catch (IllegalArgumentException var6) {
                }
            }
        }
    }

    private static void readUuids(List<String> list, Set<UUID> into) {
        for (String s : list) {
            try {
                into.add(UUID.fromString(s));
            } catch (IllegalArgumentException var5) {
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> w = new ArrayList<>();
        this.welcomed.forEach(u -> w.add(u.toString()));
        y.set("welcomed", w);
        List<String> v = new ArrayList<>();
        this.verified.forEach(u -> v.add(u.toString()));
        y.set("verified", v);
        this.guide.forEach((u, i) -> y.set("guide." + u, i));

        try {
            y.save(this.file);
        } catch (IOException var5) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save welcome.yml", (Throwable)var5);
        }
    }

    private String discord() {
        return Cfg.s(Opt.LINK_DISCORD);
    }

    public boolean verified(UUID u) {
        return !Cfg.bool(Opt.VERIFY_ENABLED) || this.verified.contains(u);
    }

    public boolean mustVerify(Player p) {
        return Cfg.bool(Opt.VERIFY_ENABLED)
            && Cfg.bool(Opt.VERIFY_BEFORE_CHAT)
            && !this.verified.contains(p.getUniqueId())
            && !p.hasPermission("vortexpvp.staff");
    }

    public Component badge(UUID u) {
        return this.badge(u, null);
    }

    public Component badge(UUID u, Player viewer) {
        if (Cfg.bool(Opt.VERIFY_ENABLED) && Cfg.bool(Opt.VERIFIED_BADGE) && this.verified.contains(u)) {
            Component icon = Icons.spriteOr("gui:icon/checkmark", "<#3DDC84>✔");
            return Component.space().append(icon.hoverEvent(HoverEvent.showText(Text.mm("<#3DDC84><b>✔ Verified</b>\n<gray>Passed the human check"))));
        } else {
            return Component.empty();
        }
    }

    public void setVerified(UUID u, boolean on) {
        if (on) {
            this.verified.add(u);
        } else {
            this.verified.remove(u);
        }

        this.save();
        Player p = Bukkit.getPlayer(u);
        if (p != null) {
            this.refreshName(p);
        }
    }

    private void refreshName(Player p) {
        try {
            this.plugin.boards().refreshNametag(p);
        } catch (RuntimeException var3) {
        }
    }

    private static Welcome.Question question() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int kind = r.nextInt(3);
        String text;
        int answer;
        switch (kind) {
            case 0: {
                int a = 2 + r.nextInt(18);
                int b = 2 + r.nextInt(18);
                text = a + " + " + b;
                answer = a + b;
                break;
            }
            case 1: {
                int a = 10 + r.nextInt(20);
                int b = 1 + r.nextInt(a - 1);
                text = a + " - " + b;
                answer = a - b;
                break;
            }
            default: {
                int a = 2 + r.nextInt(8);
                int b = 2 + r.nextInt(8);
                text = a + " × " + b;
                answer = a * b;
            }
        }

        Set<Integer> opts = new HashSet<>();
        opts.add(answer);

        while (opts.size() < 4) {
            int off = r.nextInt(1, 7) * (r.nextBoolean() ? 1 : -1);
            if (answer + off >= 0) {
                opts.add(answer + off);
            }
        }

        List<Integer> list = new ArrayList<>(opts);
        Collections.shuffle(list);
        return new Welcome.Question(text, answer, list);
    }

    public void show(Player p) {
        if (!ClientVersion.supportsDialogs(p)) {
            this.chatWelcome(p);
        } else {
            this.step(p, Welcome.Stage.WELCOME);
            Snd.play(p, "block.note_block.pling", 0.7F, 1.2F);
        }
    }

    private void chatWelcome(Player p) {
        p.sendMessage(Text.mm("<#FFB020><b>WELCOME TO " + Text.esc(Cfg.s(Opt.SERVER_NAME)).toUpperCase() + "</b>"));

        for (String line : Cfg.get(Opt.WELCOME_BRIEF)) {
            p.sendMessage(Text.mm(line));
        }

        if (!this.discord().isBlank()) {
            p.sendMessage(
                Text.mm(
                    "<#5865F2><b><click:open_url:'"
                        + this.discord()
                        + "'>» Join our Discord: "
                        + Text.esc(this.discord().replaceFirst("^https?://", ""))
                        + " «</click>"
                )
            );
        }

        p.sendMessage(Text.mm("<gold><click:run_command:'/guide'>[Start the guide]</click> <gray><click:run_command:'/country'>[Pick your country]</click>"));
        if (Cfg.bool(Opt.VERIFY_ENABLED) && !this.verified.contains(p.getUniqueId())) {
            this.chatQuestion(p);
        }
    }

    private void chatQuestion(Player p) {
        Welcome.Question q = question();
        this.chatQuestions.put(p.getUniqueId(), q);
        p.sendMessage(Text.mm("<white>Verification: <#FFB020><b>" + q.text() + " = ?</b> <gray>Type <white>/verify <answer></white>"));
    }

    private Component strip(Player p, Welcome.Stage current) {
        StringBuilder sb = new StringBuilder();
        String[] nums = new String[]{"①", "②", "③", "④"};

        for (Welcome.Stage s : Welcome.Stage.values()) {
            if (s != Welcome.Stage.VERIFY || Cfg.bool(Opt.VERIFY_ENABLED)) {
                if (!sb.isEmpty()) {
                    sb.append(" <dark_gray>───</dark_gray> ");
                }

                boolean done = s.ordinal() < current.ordinal();
                if (s == current) {
                    sb.append("<")
                        .append("#FFB020")
                        .append("><b>")
                        .append(nums[s.ordinal()])
                        .append(" ")
                        .append(s.label)
                        .append("</b></")
                        .append("#FFB020")
                        .append(">");
                } else if (done) {
                    sb.append("<").append("#3DDC84").append(">✔ ").append(s.label).append("</").append("#3DDC84").append(">");
                } else {
                    sb.append("<dark_gray>").append(nums[s.ordinal()]).append(" ").append(s.label).append("</dark_gray>");
                }
            }
        }

        return Text.mm(sb.toString());
    }

    private DialogBase base(Component title, List<DialogBody> body) {
        return DialogBase.builder(title).canCloseWithEscape(true).pause(false).afterAction(DialogAfterAction.NONE).body(body).build();
    }

    private ActionButton btn(Player p, String label, String tip, int width, Runnable r) {
        return Dialogs.button(Text.mm(label), tip == null ? null : Text.mm(tip), width, this.plugin.dialogs().action(p, r));
    }

    private Welcome.Stage after(Welcome.Stage s) {
        Welcome.Stage next = Welcome.Stage.values()[Math.min(Welcome.Stage.values().length - 1, s.ordinal() + 1)];
        return next == Welcome.Stage.VERIFY && !Cfg.bool(Opt.VERIFY_ENABLED) ? Welcome.Stage.READY : next;
    }

    private void step(Player p, Welcome.Stage stage) {
        if (p.isOnline()) {
            switch (stage) {
                case WELCOME:
                    this.welcomeStep(p);
                    break;
                case COUNTRY:
                    this.countryStep(p);
                    break;
                case VERIFY:
                    if (this.verified.contains(p.getUniqueId())) {
                        this.readyStep(p, false);
                    } else {
                        this.verifyStep(p, question(), null);
                    }
                    break;
                default:
                    this.readyStep(p, false);
            }
        }
    }

    private Component title(String sub) {
        return Text.mm("<white><b>" + Text.esc(Cfg.s(Opt.SERVER_NAME).toUpperCase()) + "</b> <dark_gray>»</dark_gray> <#FFB020>" + sub);
    }

    private void welcomeStep(Player p) {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(this.strip(p, Welcome.Stage.WELCOME), 320));
        body.add(
            DialogBody.plainMessage(
                Text.mm("<white>Hey <#FFB020><b>" + Text.esc(p.getName()) + "</b></#FFB020>, welcome to <b>" + Text.esc(Cfg.s(Opt.SERVER_NAME)) + "</b>!"), 320
            )
        );

        for (String line : Cfg.get(Opt.WELCOME_BRIEF)) {
            body.add(DialogBody.plainMessage(Text.mm(line), 320));
        }

        body.add(DialogBody.plainMessage(Text.mm("<dark_gray>It takes 20 seconds to set you up."), 320));
        List<ActionButton> b = new ArrayList<>();
        b.add(this.btn(p, "<#3DDC84><b>Let's go  ›", "<gray>Next: your country", 200, () -> this.step(p, this.after(Welcome.Stage.WELCOME))));
        Dialogs.show(
            p,
            this.base(this.title("Welcome"), body),
            DialogType.multiAction(b).columns(1).exitAction(this.btn(p, "<gray>Later", null, 200, () -> this.later(p))).build()
        );
    }

    private void later(Player p) {
        p.closeDialog();
        if (Cfg.bool(Opt.VERIFY_ENABLED) && !this.verified.contains(p.getUniqueId())) {
            p.sendMessage(Text.mm("<gray>You can finish any time with <white>/welcome</white>. <#FFB020>Verify with <white>/verify</white> to chat."));
        }
    }

    private void countryStep(Player p) {
        Countries countries = this.plugin.countries();
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(this.strip(p, Welcome.Stage.COUNTRY), 320));
        body.add(DialogBody.plainMessage(Text.mm("<white><b>Where are you from?</b>"), 320));
        body.add(DialogBody.plainMessage(Text.mm("<gray>Your flag shows next to your name - nametag, tab and chat."), 320));
        Countries.Country mine = countries == null ? null : countries.of(p.getUniqueId());
        if (mine != null) {
            body.add(
                DialogBody.plainMessage(Text.mm("<gray>Current: ").append(countries.flag(mine, p)).append(Text.mm(" <white>" + Text.esc(mine.name()))), 320)
            );
        }

        List<ActionButton> b = new ArrayList<>();
        if (countries != null) {
            for (String code : Cfg.get(Opt.WELCOME_COUNTRIES)) {
                Countries.Country c = Countries.ALL.get(code.trim().toUpperCase(Locale.ROOT));
                if (c != null) {
                    Component label = countries.flag(c, p).append(Text.mm(" <white>" + Text.esc(c.name())));
                    b.add(Dialogs.button(label, Text.mm("<gray>Pick " + Text.esc(c.name())), 100, this.plugin.dialogs().action(p, () -> {
                        countries.set(p, c.code());
                        Snd.play(p, "block.note_block.pling", 0.6F, 1.5F);
                        this.step(p, this.after(Welcome.Stage.COUNTRY));
                    })));
                }
            }

            b.add(
                this.btn(
                    p,
                    "<aqua>\ud83d\udd0d Search…",
                    "<gray>Type your country",
                    100,
                    () -> this.plugin.dialogs().text(p, Text.mm("<#FFB020><b>Search country"), "Country name or code", "", 40, q -> {
                        if (countries.setByCode(p, q.trim())) {
                            this.step(p, this.after(Welcome.Stage.COUNTRY));
                        } else {
                            countries.picker(p, () -> this.step(p, this.after(Welcome.Stage.COUNTRY)));
                        }
                    })
                )
            );
            b.add(
                this.btn(
                    p, "<white>All countries…", "<gray>By continent", 100, () -> countries.picker(p, () -> this.step(p, this.after(Welcome.Stage.COUNTRY)))
                )
            );
        }

        b.add(this.btn(p, "<gray>Skip  ›", "<gray>No flag (you can pick one later: /country)", 100, () -> this.step(p, this.after(Welcome.Stage.COUNTRY))));
        Dialogs.show(
            p,
            this.base(this.title("Your Country"), body),
            DialogType.multiAction(b).columns(3).exitAction(this.btn(p, "<gray>‹ Back", null, 200, () -> this.step(p, Welcome.Stage.WELCOME))).build()
        );
    }

    private void verifyStep(Player p, Welcome.Question q, String note) {
        long lock = this.lockedUntil.getOrDefault(p.getUniqueId(), 0L) - System.currentTimeMillis();
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(this.strip(p, Welcome.Stage.VERIFY), 320));
        body.add(DialogBody.plainMessage(Text.mm("<white><b>Quick check - are you human?</b>"), 320));
        body.add(DialogBody.plainMessage(Text.mm("<gray>Answer this to get your <#3DDC84>verified</#3DDC84> badge and unlock chat."), 320));
        if (note != null) {
            body.add(DialogBody.plainMessage(Text.mm(note), 320));
        }

        List<ActionButton> b = new ArrayList<>();
        if (lock > 0L) {
            body.add(DialogBody.plainMessage(Text.mm("<red>Too many wrong answers. Try again in " + (lock / 1000L + 1L) + "s."), 320));
            b.add(this.btn(p, "<white>⟳ Try again", null, 200, () -> this.verifyStep(p, question(), null)));
        } else {
            body.add(DialogBody.plainMessage(Text.mm("<#FFB020><b>" + q.text() + " = ?</b>"), 320));

            for (int option : q.options()) {
                b.add(this.btn(p, "<white><b>" + option, null, 70, () -> this.answer(p, q, option)));
            }
        }

        Dialogs.show(
            p,
            this.base(this.title("Verification"), body),
            DialogType.multiAction(b).columns(4).exitAction(this.btn(p, "<gray>Later", null, 200, () -> this.later(p))).build()
        );
    }

    private void answer(Player p, Welcome.Question q, int option) {
        if (System.currentTimeMillis() < this.lockedUntil.getOrDefault(p.getUniqueId(), 0L)) {
            this.verifyStep(p, question(), null);
        } else if (option == q.answer()) {
            this.wrong.remove(p.getUniqueId());
            this.passed(p);
            this.readyStep(p, true);
        } else {
            int n = this.wrong.merge(p.getUniqueId(), 1, Integer::sum);
            Snd.error(p);
            if (n >= 3) {
                this.wrong.remove(p.getUniqueId());
                this.lockedUntil.put(p.getUniqueId(), System.currentTimeMillis() + 15000L);
            }

            this.verifyStep(p, question(), "<red>Not quite - here's another one.");
        }
    }

    private void passed(Player p) {
        this.verified.add(p.getUniqueId());
        this.save();
        this.refreshName(p);
        Snd.play(p, "ui.toast.challenge_complete", 0.6F, 1.4F);
        p.sendActionBar(Text.mm("<#3DDC84><b>✔ Verified</b> <gray>- welcome to the realm!"));
    }

    private void readyStep(Player p, boolean justVerified) {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(this.strip(p, Welcome.Stage.READY), 320));
        if (this.verified.contains(p.getUniqueId())) {
            body.add(DialogBody.plainMessage(Text.mm(justVerified ? "<#3DDC84><b>✔ You're verified!</b>" : "<#3DDC84><b>✔ All set!</b>"), 320));
            body.add(
                DialogBody.plainMessage(
                    Text.mm("<gray>Your name now shows: <white>" + Text.esc(p.getName()))
                        .append(this.badge(p.getUniqueId(), p))
                        .append((Component)(this.plugin.countries() == null ? Component.empty() : this.plugin.countries().tag(p.getUniqueId(), p))),
                    320
                )
            );
        } else {
            body.add(DialogBody.plainMessage(Text.mm("<white><b>You're all set!</b>"), 320));
        }

        if (!this.discord().isBlank()) {
            body.add(DialogBody.plainMessage(Text.mm("<gray>Events, tournaments, giveaways and updates are on our Discord:"), 320));
            body.add(
                DialogBody.plainMessage(
                    Text.mm("<#5865F2><b>" + Text.esc(this.discord().replaceFirst("^https?://", "")) + "</b>").clickEvent(ClickEvent.openUrl(this.discord())),
                    320
                )
            );
        }

        List<ActionButton> b = new ArrayList<>();
        if (!this.discord().isBlank()) {
            b.add(
                ActionButton.builder(Text.mm("<#5865F2><b>Join our Discord"))
                    .tooltip(Text.mm("<gray>Opens " + Text.esc(this.discord())))
                    .width(150)
                    .action(DialogAction.staticAction(ClickEvent.openUrl(this.discord())))
                    .build()
            );
        }

        b.add(this.btn(p, "<#FFB020><b>▶ Start the guide", "<gray>A quick tour that follows you", 150, () -> {
            p.closeDialog();
            this.startGuide(p);
        }));
        b.add(this.btn(p, "<#3DDC84><b>⚔ Play now", "<gray>Open the Duels menu", 150, () -> {
            p.closeDialog();
            if (this.plugin.sessions().state(p) == PlayerState.LOBBY) {
                this.plugin.dialogs().openQueue(p, true, Dialogs.QueueTab.MOST_PLAYED);
            }
        }));
        Dialogs.show(
            p,
            this.base(this.title("Ready"), body),
            DialogType.multiAction(b).columns(3).exitAction(this.btn(p, "<gray>Close", null, 200, p::closeDialog)).build()
        );
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (Cfg.bool(Opt.WELCOME_SCREEN) && this.welcomed.add(p.getUniqueId())) {
            this.save();
            Tasks.later(60L, () -> {
                if (p.isOnline()) {
                    this.show(p);
                }
            });
        } else {
            if (Cfg.bool(Opt.VERIFY_ENABLED) && !this.verified.contains(p.getUniqueId())) {
                Tasks.later(80L, () -> {
                    if (p.isOnline() && !this.verified.contains(p.getUniqueId())) {
                        if (ClientVersion.supportsDialogs(p)) {
                            this.verifyStep(p, question(), null);
                        } else {
                            this.chatQuestion(p);
                        }
                    }
                });
            }

            if (this.guide.containsKey(p.getUniqueId())) {
                Tasks.later(40L, () -> {
                    if (p.isOnline()) {
                        this.bar(p);
                    }
                });
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        BossBar b = this.bars.remove(u);
        if (b != null) {
            e.getPlayer().hideBossBar(b);
        }

        this.chatQuestions.remove(u);
        this.wrong.remove(u);
    }

    public void startGuide(Player p) {
        this.guide.put(p.getUniqueId(), 0);
        this.save();
        p.sendMessage(Text.mm("<#FFB020><b>GUIDE</b> <gray>Follow the bar at the top - each step ticks off by itself. <dark_gray>(/guide stop to hide it)"));
        this.advance(p);
    }

    private Welcome.Step current(Player p) {
        Integer i = this.guide.get(p.getUniqueId());
        return i != null && i < Welcome.Step.values().length ? Welcome.Step.values()[i] : null;
    }

    private void advance(Player p) {
        Welcome.Step s;
        for (s = this.current(p); s != null && this.doneAlready(p, s); s = this.current(p)) {
            this.guide.put(p.getUniqueId(), s.ordinal() + 1);
        }

        this.save();
        if (s == null) {
            this.finish(p);
        } else {
            this.bar(p);
            p.sendMessage(
                Text.mm(
                    "<#FFB020><b>STEP " + (s.ordinal() + 1) + "/" + Welcome.Step.values().length + "</b> <white>" + s.title + " <dark_gray>- <gray>" + s.hint
                )
            );
            if (s == Welcome.Step.DISCORD && !this.discord().isBlank()) {
                p.sendMessage(
                    Text.mm(
                        "<#5865F2><b><click:open_url:'"
                            + this.discord()
                            + "'>» "
                            + Text.esc(this.discord())
                            + " «</click></b> <gray><click:run_command:'/guide discord'>[I joined]</click>"
                    )
                );
            }

            if (s == Welcome.Step.COUNTRY) {
                p.sendMessage(Text.mm("<gold><click:run_command:'/country'>[Pick my country]</click>"));
            }
        }
    }

    private boolean doneAlready(Player p, Welcome.Step s) {
        return s == Welcome.Step.COUNTRY && this.plugin.countries() != null && this.plugin.countries().picked(p.getUniqueId());
    }

    private void bar(Player p) {
        Welcome.Step s = this.current(p);
        BossBar b = this.bars.get(p.getUniqueId());
        if (s == null) {
            if (b != null) {
                p.hideBossBar(b);
                this.bars.remove(p.getUniqueId());
            }
        } else {
            Component title = Text.mm(
                "<#FFB020><b>GUIDE " + (s.ordinal() + 1) + "/" + Welcome.Step.values().length + "</b> <white>" + s.title + " <dark_gray>• <gray>" + s.hint
            );
            float progress = (float)s.ordinal() / Welcome.Step.values().length;
            if (b == null) {
                b = BossBar.bossBar(title, progress, Color.YELLOW, Overlay.NOTCHED_10);
                this.bars.put(p.getUniqueId(), b);
                p.showBossBar(b);
            } else {
                b.name(title);
                b.progress(progress);
            }
        }
    }

    public void done(Player p, String what) {
        Welcome.Step s = this.current(p);
        if (s != null && s.name().equalsIgnoreCase(what)) {
            this.guide.put(p.getUniqueId(), s.ordinal() + 1);
            Snd.play(p, "block.note_block.pling", 0.8F, 1.6F);
            p.sendActionBar(Text.mm("<#3DDC84>✔ " + s.title));
            Tasks.later(30L, () -> {
                if (p.isOnline()) {
                    this.advance(p);
                }
            });
        }
    }

    private void finish(Player p) {
        this.guide.remove(p.getUniqueId());
        this.save();
        this.bar(p);
        p.sendMessage(Text.mm("<#3DDC84><b>GUIDE COMPLETE!</b> <white>You know everything - now climb the tiers. <gray>Good luck!"));
        Snd.play(p, "ui.toast.challenge_complete", 0.8F, 1.2F);
        Toasts.show(p, "win");
    }

    public void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Welcome.Step s = this.current(p);
            if (s != null) {
                PlayerState st = this.plugin.sessions().state(p);
                switch (s) {
                    case COUNTRY:
                        if (this.plugin.countries() != null && this.plugin.countries().picked(p.getUniqueId())) {
                            this.done(p, "COUNTRY");
                        }
                        break;
                    case QUEUE:
                        if (st == PlayerState.QUEUE || st == PlayerState.PREMATCH || st == PlayerState.MATCH) {
                            this.done(p, "QUEUE");
                        }
                        break;
                    case PLAY:
                        if (st == PlayerState.MATCH) {
                            this.done(p, "PLAY");
                        }
                        break;
                    case EDITOR:
                        if (st == PlayerState.EDITING) {
                            this.done(p, "EDITOR");
                        }
                }
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (!cmd.equals("verify")
            || args.length < 1
            || !sender.hasPermission("vortexpvp.staff")
            || Bukkit.getPlayerExact(args[0]) == null
            || sender instanceof Player self && self.getName().equalsIgnoreCase(args[0])) {
            if (sender instanceof Player p) {
                switch (cmd) {
                    case "welcome":
                        this.show(p);
                        break;
                    case "verify":
                        if (this.verified.contains(p.getUniqueId())) {
                            p.sendMessage(Text.mm("<#3DDC84>✔ You're already verified."));
                        } else if (args.length > 0 && this.chatQuestions.containsKey(p.getUniqueId())) {
                            Welcome.Question q = this.chatQuestions.get(p.getUniqueId());

                            try {
                                if (Integer.parseInt(args[0].trim()) == q.answer()) {
                                    this.chatQuestions.remove(p.getUniqueId());
                                    this.passed(p);
                                    p.sendMessage(Text.mm("<#3DDC84>✔ Verified - you can chat now."));
                                    return true;
                                }
                            } catch (NumberFormatException var11) {
                            }

                            p.sendMessage(Text.mm("<red>Not quite."));
                            this.chatQuestion(p);
                        } else if (ClientVersion.supportsDialogs(p)) {
                            this.verifyStep(p, question(), null);
                        } else {
                            this.chatQuestion(p);
                        }
                        break;
                    case "country":
                        if (args.length > 0 && !this.plugin.countries().setByCode(p, String.join(" ", args))) {
                            p.sendMessage(Text.mm("<red>Unknown country. <gray>Use a code like MA, US, FR or the name."));
                        } else if (args.length == 0) {
                            this.plugin.countries().picker(p, null);
                        }
                        break;
                    default:
                        if (args.length > 0 && args[0].equalsIgnoreCase("stop")) {
                            this.guide.remove(p.getUniqueId());
                            this.save();
                            this.bar(p);
                            p.sendMessage(Text.mm("<gray>Guide hidden. <white>/guide</white> starts it again."));
                        } else if (args.length > 0 && args[0].equalsIgnoreCase("discord")) {
                            this.done(p, "DISCORD");
                        } else {
                            this.startGuide(p);
                        }
                }

                return true;
            } else {
                return true;
            }
        } else {
            Player t = Bukkit.getPlayerExact(args[0]);
            boolean on = args.length < 2 || !args[1].equalsIgnoreCase("off");
            this.setVerified(t.getUniqueId(), on);
            sender.sendMessage(Text.mm("<green>" + t.getName() + (on ? " is now verified." : " is no longer verified.")));
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("guide") && args.length == 1) {
            return List.of("stop");
        } else if (command.getName().equalsIgnoreCase("verify") && args.length == 1 && sender.hasPermission("vortexpvp.staff")) {
            List<String> out = new ArrayList<>();
            Bukkit.getOnlinePlayers().forEach(o -> out.add(o.getName()));
            return out;
        } else {
            return List.of();
        }
    }

    private record Question(String text, int answer, List<Integer> options) {
    }

    private static enum Stage {
        WELCOME("Welcome"),
        COUNTRY("Country"),
        VERIFY("Verify"),
        READY("Play");

        final String label;

        private Stage(String label) {
            this.label = label;
        }
    }

    private static enum Step {
        COUNTRY("Pick your country", "Shown next to your name - /country"),
        QUEUE("Join a duel queue", "Right-click the sword in your hotbar and click a kit"),
        PLAY("Play your first duel", "Win or lose - every match counts for your placement"),
        EDITOR("Arrange a kit", "Open the Kit Editor (book) and move items where you like them"),
        LEADERBOARD("Check the leaderboard", "Emerald in your hotbar, or /lb"),
        SELECTOR("Open the server selector", "/hub then the compass - Survival, Lifesteal, Eco..."),
        DISCORD("Join our Discord", "Click the link in chat - events, giveaways, updates");

        final String title;
        final String hint;

        private Step(String title, String hint) {
            this.title = title;
            this.hint = hint;
        }
    }
}
