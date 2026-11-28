package net.vortexrealm;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.vortexrealm.arena.Arena;
import net.vortexrealm.arena.ArenaInstance;
import net.vortexrealm.kit.DefaultKits;
import net.vortexrealm.kit.Kit;
import net.vortexrealm.kit.KitLayout;
import net.vortexrealm.kit.KitManager;
import net.vortexrealm.kit.KitVote;
import net.vortexrealm.match.PreMatch;
import net.vortexrealm.queue.Matchmaker;
import net.vortexrealm.rank.EloCalculator;
import net.vortexrealm.rank.RankManager;
import net.vortexrealm.rank.Tier;
import net.vortexrealm.util.Items;
import net.vortexrealm.util.Text;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

public final class SelfTest {
    /** Checks added by add-ons: each gets the running test and calls {@link #extra}. */
    public static final java.util.List<java.util.function.Consumer<SelfTest>> EXTRA = new java.util.concurrent.CopyOnWriteArrayList<>();

    private final VortexRealm plugin;
    private int passed;
    private int failed;
    private CommandSender out;

    SelfTest(VortexRealm plugin) {
        this.plugin = plugin;
    }

    /** For add-on checks (see {@link #EXTRA}). */
    public void extra(String name, boolean ok, String detail) {
        this.check(name, ok, detail);
    }

    private void check(String name, boolean ok, String detail) {
        if (ok) {
            this.passed++;
            this.out.sendMessage(Text.mm("<green>✔ <gray>" + Text.esc(name)));
        } else {
            this.failed++;
            this.out.sendMessage(Text.mm("<red>✘ <white>" + Text.esc(name) + " <dark_gray>- <red>" + Text.esc(detail)));
        }
    }

    void run(CommandSender sender) {
        this.out = sender;
        this.out.sendMessage(Text.mm("<gold><b>Vortex Realm self test"));
        this.elo();
        this.tiers();
        this.matchmaker();
        this.kits();
        this.arenas();
        this.previews();
        this.chatFilter();
        this.database();
        this.dialog();
        this.vortex();
        for (java.util.function.Consumer<SelfTest> x : EXTRA) {
            try {
                x.accept(this);
            } catch (RuntimeException var3) {
                this.check("Add-on check crashed", false, var3.toString());
            }
        }

        this.out
            .sendMessage(
                Text.mm(
                    (this.failed == 0 ? "<green><b>ALL PASSED" : "<red><b>" + this.failed + " FAILED")
                        + " <gray>("
                        + this.passed
                        + " passed, "
                        + this.failed
                        + " failed)"
                )
            );
        this.plugin.getLogger().info("Self test: " + this.passed + " passed, " + this.failed + " failed");
    }

    private void elo() {
        EloCalculator.Params p = RankManager.params();
        EloCalculator.Result equal = EloCalculator.compute(0, 0, 0, 0, 0, 2, 1, p);
        this.check("Rank: equal new players -> winner gains, loser can't go below 0", equal.winnerDelta() > 0 && equal.loserDelta() == 0, equal.toString());
        EloCalculator.Result mid = EloCalculator.compute(1500, 20, 0, 1500, 20, 2, 1, p);
        this.check("Rank: equal established players -> symmetric-ish result", mid.winnerDelta() > 0 && mid.loserDelta() < 0, mid.toString());
        EloCalculator.Result upset = EloCalculator.compute(1000, 20, 0, 2000, 20, 2, 1, p);
        EloCalculator.Result expected = EloCalculator.compute(2000, 20, 0, 1000, 20, 2, 1, p);
        this.check(
            "Rank: beating a stronger player gives more", upset.winnerDelta() > expected.winnerDelta(), upset.winnerDelta() + " vs " + expected.winnerDelta()
        );
        EloCalculator.Result flawless = EloCalculator.compute(1500, 20, 0, 1500, 20, 2, 0, p);
        this.check("Rank: 2-0 gives more than 2-1", flawless.winnerDelta() >= mid.winnerDelta(), flawless.winnerDelta() + " vs " + mid.winnerDelta());
    }

    private void tiers() {
        boolean increasing = true;

        for (Tier t : Tier.values()) {
            if (t.next() != null && t.next().threshold() <= t.threshold()) {
                increasing = false;
            }
        }

        this.check("Tiers: thresholds strictly increasing", increasing, "check ranking.tier-thresholds");
        this.check("Tiers: 0 points is LT5", Tier.forElo(0) == Tier.LT5, String.valueOf(Tier.forElo(0)));
        this.check("Tiers: HT1 threshold maps to HT1", Tier.forElo(Tier.HT1.threshold()) == Tier.HT1, "");
    }

    private void matchmaker() {
        long now = System.currentTimeMillis();
        Matchmaker.Params params = new Matchmaker.Params(150, 30, 2500, 10, true);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        List<Matchmaker.Candidate> list = new ArrayList<>();
        list.add(new Matchmaker.Candidate(a, 1000, 40, now - 5000L, Integer.MAX_VALUE, 1.0, null, 0L));
        list.add(new Matchmaker.Candidate(b, 1100, 50, now - 3000L, Integer.MAX_VALUE, 1.0, null, 0L));
        list.add(new Matchmaker.Candidate(c, 5000, 45, now - 1000L, Integer.MAX_VALUE, 1.0, null, 0L));
        List<Matchmaker.Pair> pairs = Matchmaker.pair(list, now, params, Set.of());
        this.check(
            "Matchmaker: close ranks pair, far ranks wait",
            pairs.size() == 1 && Set.of(pairs.get(0).a().id(), pairs.get(0).b().id()).equals(Set.of(a, b)),
            pairs.toString()
        );
        List<Matchmaker.Candidate> ping = List.of(
            new Matchmaker.Candidate(a, 1000, 20, now, 50, 1.0, null, 0L), new Matchmaker.Candidate(b, 1000, 300, now, 50, 1.0, null, 0L)
        );
        this.check("Matchmaker: ping limit respected", Matchmaker.pair(ping, now, params, Set.of()).isEmpty(), "paired");
        List<Matchmaker.Candidate> avoid = List.of(
            new Matchmaker.Candidate(a, 1000, 20, now, Integer.MAX_VALUE, 1.0, b, now + 10000L),
            new Matchmaker.Candidate(b, 1000, 20, now, Integer.MAX_VALUE, 1.0, a, now + 10000L),
            new Matchmaker.Candidate(c, 5000, 20, now, Integer.MAX_VALUE, 1.0, null, 0L)
        );
        this.check("Matchmaker: avoids instant rematch", Matchmaker.pair(avoid, now, params, Set.of()).isEmpty(), "paired");
        this.check(
            "Matchmaker: two players alone in a queue may rematch", Matchmaker.pair(avoid.subList(0, 2), now, params, Set.of()).size() == 1, "not paired"
        );
        Matchmaker.Params casual = new Matchmaker.Params(150, 30, 2500, 10, false);
        List<Matchmaker.Candidate> placing = List.of(
            new Matchmaker.Candidate(a, 0, 20, now, Integer.MAX_VALUE, 0.5, null, 0L, -1),
            new Matchmaker.Candidate(b, 3200, 20, now, Integer.MAX_VALUE, 0.5, null, 0L, 12)
        );
        this.check(
            "Matchmaker: unranked pairs a placement player with anyone at once", Matchmaker.pair(placing, now, casual, Set.of()).size() == 1, "not paired"
        );
        Matchmaker.Params tiered = new Matchmaker.Params(150, 30, 100000, 10, true, 2);
        List<Matchmaker.Candidate> far = List.of(
            new Matchmaker.Candidate(a, 0, 20, now - 600000L, Integer.MAX_VALUE, 50.0, null, 0L, 0),
            new Matchmaker.Candidate(b, 0, 20, now - 600000L, Integer.MAX_VALUE, 50.0, null, 0L, 14)
        );
        this.check("Matchmaker: LT5 never meets HT1 in ranked", Matchmaker.pair(far, now, tiered, Set.of()).isEmpty(), "paired");
        List<Matchmaker.Candidate> promo = List.of(
            new Matchmaker.Candidate(a, 0, 20, now - 5000L, Integer.MAX_VALUE, 50.0, null, 0L, 4, true, 5),
            new Matchmaker.Candidate(b, 0, 20, now - 5000L, Integer.MAX_VALUE, 50.0, null, 0L, 5, false, -1)
        );
        this.check("Matchmaker: rank-up series waits for the same tier", Matchmaker.pair(promo, now, tiered, Set.of()).isEmpty(), "paired");
        Matchmaker.Params above = new Matchmaker.Params(150, 30, 100000, 10, true, 2, true, 0);
        this.check("Matchmaker: Tier Test meets a player who has the wanted tier", Matchmaker.pair(promo, now, above, Set.of()).size() == 1, "not paired");
        List<Matchmaker.Candidate> same = List.of(
            new Matchmaker.Candidate(a, 0, 20, now - 5000L, Integer.MAX_VALUE, 50.0, null, 0L, 4, true, 5),
            new Matchmaker.Candidate(b, 0, 20, now - 5000L, Integer.MAX_VALUE, 50.0, null, 0L, 4, false, -1)
        );
        this.check("Matchmaker: Tier Test never meets its own tier (no fallback)", Matchmaker.pair(same, now, above, Set.of()).isEmpty(), "paired");
        List<Matchmaker.Candidate> sameLong = List.of(
            new Matchmaker.Candidate(a, 0, 20, now - 50000L, Integer.MAX_VALUE, 50.0, null, 0L, 4, true, 5),
            new Matchmaker.Candidate(b, 0, 20, now - 50000L, Integer.MAX_VALUE, 50.0, null, 0L, 4, false, -1)
        );
        this.check(
            "Matchmaker: Tier Test takes its own tier after the fallback",
            Matchmaker.pair(sameLong, now, new Matchmaker.Params(150, 30, 100000, 10, true, 2, true, 45), Set.of()).size() == 1,
            "not paired"
        );
        Matchmaker.Params quiet = new Matchmaker.Params(150, 30, 100000, 10, true, 2, true, 45, 15, Matchmaker.speedFor(2));
        List<Matchmaker.Candidate> sameShort = List.of(
            new Matchmaker.Candidate(a, 0, 20, now - 16000L, Integer.MAX_VALUE, 50.0, null, 0L, 4, true, 5),
            new Matchmaker.Candidate(b, 0, 20, now - 16000L, Integer.MAX_VALUE, 50.0, null, 0L, 4, false, -1)
        );
        this.check("Matchmaker: a quiet queue widens three times faster", Matchmaker.pair(sameShort, now, quiet, Set.of()).size() == 1, "not paired");
        this.check(
            "Tiers: old ladder converts (HT3 stays HT3)", Tier.byIndex(Tier.fromOldIndex(5)) == Tier.HT3, String.valueOf(Tier.byIndex(Tier.fromOldIndex(5)))
        );
        Kit sword = null;
        Kit mace = DefaultKits.mace();

        for (Kit k : DefaultKits.create()) {
            if (k.id.equals("sword")) {
                sword = k;
            }
        }

        List<KitVote> maceVotes = PreMatch.votesFor(mace);
        this.check(
            "Votes: Sword only votes on one more round",
            sword != null && PreMatch.votesFor(sword).stream().allMatch(v -> v.action() == KitVote.Action.ROUNDS),
            sword == null ? "no sword" : PreMatch.votesFor(sword).toString()
        );
        this.check(
            "Votes: Mace gets 3 votes and never the mace itself",
            maceVotes.size() == 3 && maceVotes.stream().noneMatch(v -> v.material() == Material.MACE),
            maceVotes.toString()
        );
    }

    private void kits() {
        KitManager km = this.plugin.kits();
        this.check("Kits: at least one kit loaded", !km.all().isEmpty(), "kits.yml empty");

        for (Kit k : km.all()) {
            if (!k.hasContents()) {
                this.check("Kit " + k.id + " has contents", false, "empty kit");
            } else {
                ItemStack[] def = km.arrange(k, null);
                ItemStack[] editor = km.editorContents(k, null);
                ItemStack[] storage = new ItemStack[36];
                System.arraycopy(editor, 0, storage, 0, 36);
                int free = -1;

                for (int i = 35; i >= 0; i--) {
                    if (Items.isEmpty(storage[i])) {
                        free = i;
                        break;
                    }
                }

                if (free >= 0 && !Items.isEmpty(storage[0])) {
                    storage[free] = storage[0];
                    storage[0] = null;
                }

                KitLayout layout = km.layoutFrom(k, storage, editor[40]);
                boolean roundTrip = layout != null;
                if (roundTrip) {
                    ItemStack[] arranged = km.arrange(k, layout);
                    roundTrip = count(arranged) == count(def);
                }

                this.check("Kit " + k.id + ": layout save/load keeps every item", roundTrip, "layout invalid");
                ItemStack extra = null;

                for (ItemStack it : storage) {
                    if (!Items.isEmpty(it)) {
                        extra = it.clone();
                        break;
                    }
                }

                this.check("Kit " + k.id + ": duplicated items are rejected", extra == null || km.layoutFrom(k, storage, extra) == null, "duplicate accepted");

                for (KitVote v : k.votes) {
                    if (v.action() == KitVote.Action.LIMIT) {
                        ItemStack[] c = km.arrange(k, null);
                        KitManager.applyVotes(c, List.of(v));
                        int n = 0;

                        for (ItemStack itx : c) {
                            if (!Items.isEmpty(itx) && itx.getType() == v.material()) {
                                n += itx.getAmount();
                            }
                        }

                        this.check("Kit " + k.id + ": vote '" + v.question() + "'", n <= v.amount(), n + " > " + v.amount());
                    }
                }
            }
        }
    }

    private static int count(ItemStack[] items) {
        int n = 0;

        for (ItemStack it : items) {
            if (!Items.isEmpty(it)) {
                n += it.getAmount();
            }
        }

        return n;
    }

    private void arenas() {
        int ready = 0;

        for (Arena a : this.plugin.arenas().all()) {
            if (a.ready()) {
                ready++;
            } else if (a.problems().isEmpty() || a.instances.stream().anyMatch(ix -> ix.state == ArenaInstance.State.BUILDING)) {
                // switched off by an admin, or being generated right now: not a fault
                this.out.sendMessage(Text.mm("<yellow>• <gray>Arena " + Text.esc(a.id) + (a.enabled ? " is being built" : " is disabled or still generating")));
            } else {
                this.check("Arena " + a.id + " ready", false, String.join(", ", a.problems()));
            }

            for (ArenaInstance i : a.instances) {
                if (i.state == ArenaInstance.State.DISABLED) {
                    this.check("Arena " + i.label() + " instance", false, "disabled after a failed reset");
                }
            }
        }

        this.check("Arenas: at least one duel arena ready", ready > 0, "create or generate one in /pvpadmin");
        int kitsWithoutArena = 0;

        for (Kit k : this.plugin.kits().all()) {
            if (k.enabled && (k.ranked || k.unranked) && this.plugin.arenas().forKit(k).isEmpty()) {
                kitsWithoutArena++;
            }
        }

        this.check("Arenas: every queue kit has an arena", kitsWithoutArena == 0, kitsWithoutArena + " kits have none");
        this.check("Practice world loaded", this.plugin.worlds().practiceWorld() != null, "missing");
    }

    /** Swear filter: obfuscated swears are caught, normal words (class, assassin, computer) pass, mute steps parse. */
    private void chatFilter() {
        net.vortexrealm.extra.ChatFilter f = this.plugin.chatFilter();
        if (f != null) {
            for (String t : net.vortexrealm.extra.ChatFilter.selfTestSwears()) {
                this.check("Swear filter blocks \"" + t + "\"", f.swears(t), "not caught (word removed from the list?)");
            }

            for (String t : net.vortexrealm.extra.ChatFilter.selfTestClean()) {
                this.check("Swear filter allows \"" + t + "\"", !f.swears(t), "blocked: " + f.censor(t));
            }

            this.check("Swear filter: mute steps " + net.vortexrealm.extra.ChatFilter.steps(), !net.vortexrealm.extra.ChatFilter.steps().isEmpty(), "chat.filter.mute-steps");
        }
    }

    /** Map vote: designed-arena kits get 3D previews within the part budget, biome kits play the biome maps. */
    private void previews() {
        for (String[] want : new String[][]{{"sword", "DESIGNED"}, {"uhc", "DESIGNED"}, {"builduhc", "DESIGNED"}, {"pot", "DESIGNED"}, {"mace", "MACE"}, {"cart", "VANILLA"}, {"creeper", "VANILLA"}}) {
            Kit k = this.plugin.kits().get(want[0]);
            if (k != null && (k.arenaType == null || k.arenaType.equals("AUTO"))) {
                this.check("Kit " + k.id + ": map vote uses " + want[1].toLowerCase() + " maps", k.arenaKind().equals(want[1]), k.arenaKind());
            }
        }

        int budget = net.vortexrealm.match.MapModel.budget();

        for (Arena a : this.plugin.arenas().all()) {
            if (net.vortexrealm.match.MapModel.wanted(a)) {
                // built off the main thread after the start; not ready yet is not a fault
                net.vortexrealm.match.MapModel m = net.vortexrealm.match.MapModel.get(a);
                if (m == null) {
                    this.out.sendMessage(Text.mm("<yellow>• <gray>3D preview of " + Text.esc(a.id) + " is still being built"));
                } else {
                    this.check(
                        "3D preview " + a.id + ": " + m.parts.size() + " parts at " + m.cells + " cells",
                        !m.parts.isEmpty() && m.parts.size() <= budget,
                        "budget " + budget
                    );
                }
            }
        }
    }

    /** Checks for the Vortex Realm additions: privacy filters, Tier Defense pairing, tables and the tab columns. */
    private void vortex() {
        net.vortexrealm.social.SocialManager.Filter f = net.vortexrealm.social.SocialManager.Filter.parse("fr.fm|5|100");
        this.check(
            "Privacy: request filter round trip",
            f.groups().contains(net.vortexrealm.social.SocialManager.Group.FRIENDS)
                && f.groups().contains(net.vortexrealm.social.SocialManager.Group.FAMOUS)
                && f.groups().size() == 2
                && f.minTier() == 5
                && f.topRank() == 100
                && f.serialize().equals("fr.fm|5|100"),
            f.serialize()
        );
        net.vortexrealm.social.SocialManager.Filter def = net.vortexrealm.social.SocialManager.Filter.parse(net.vortexrealm.data.Setting.REQ_ONLINE.defaultValue());
        this.check(
            "Privacy: Online lets everyone send requests by default",
            def.groups().size() == net.vortexrealm.social.SocialManager.Group.values().length && def.minTier() < 0 && def.topRank() == 0,
            def.serialize()
        );
        net.vortexrealm.social.SocialManager.Filter dnd = net.vortexrealm.social.SocialManager.Filter.parse(net.vortexrealm.data.Setting.REQ_DND.defaultValue());
        this.check(
            "Privacy: Do Not Disturb lets only friends through by default",
            dnd.groups().size() == 1 && dnd.groups().contains(net.vortexrealm.social.SocialManager.Group.FRIENDS),
            dnd.serialize()
        );
        this.check(
            "Privacy: garbage filter text is ignored",
            net.vortexrealm.social.SocialManager.Filter.parse("zz.|x|y").groups().isEmpty(),
            "parse(\"zz.|x|y\")"
        );
        long now = System.currentTimeMillis();
        Matchmaker.Params ranked = new Matchmaker.Params(150, 30, 100000, 10, true, 2);
        UUID def1 = UUID.randomUUID();
        Matchmaker.Candidate defender = new Matchmaker.Candidate(def1, 1500, 40, now, Integer.MAX_VALUE, 1.0, null, 0L, Tier.HT3.ordinal(), true, Tier.MT3.ordinal());
        Matchmaker.Candidate lower = new Matchmaker.Candidate(UUID.randomUUID(), 1400, 40, now, Integer.MAX_VALUE, 1.0, null, 0L, Tier.MT3.ordinal());
        Matchmaker.Candidate far = new Matchmaker.Candidate(UUID.randomUUID(), 300, 40, now, Integer.MAX_VALUE, 1.0, null, 0L, Tier.LT5.ordinal());
        this.check("Tier Defense: meets the tier it would drop to", Matchmaker.compatible(defender, lower, now, ranked), "not paired");
        this.check("Tier Defense: never meets a far lower tier", !Matchmaker.compatible(defender, far, now, ranked), "paired");
        this.check(
            "Tier Defense: HT1 defends when enabled",
            RankManager.defends(Tier.HT1) == net.vortexrealm.config.Cfg.bool(net.vortexrealm.config.Opt.TIER_DEFENSE),
            "check ranking.tier-defense"
        );
        try {
            Integer rows = this.plugin.db().supply(c -> {
                try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT (SELECT COUNT(*) FROM vr_friends) + (SELECT COUNT(*) FROM vr_follows)")) {
                    return rs.next() ? rs.getInt(1) : -1;
                }
            }).get(5L, TimeUnit.SECONDS);
            this.check("Friends: tables ready", rows != null && rows >= 0, String.valueOf(rows));
        } catch (Exception var9) {
            this.check("Friends: tables ready", false, var9.toString());
        }

        this.check("Friends: manager running", this.plugin.social() != null && this.plugin.social().ready(), "social module did not start");
        if (this.plugin.tabColumns() != null) {
            String err = this.plugin.tabColumns().available() ? this.plugin.tabColumns().selfCheck() : this.plugin.tabColumns().failure();
            this.check("Tab columns: packets build on this server", err == null, String.valueOf(err));
        }

        if (this.plugin.tab() != null) {
            for (net.vortexrealm.social.TabDesigns.Design d : net.vortexrealm.social.TabDesigns.DESIGNS) {
                String bad = this.plugin.tab().check(d);
                boolean titles = true;
                for (String t : d.style().titles().values()) {
                    titles &= !Text.plain(Text.mm(t.replace("{count}", "3").replace("{kit}", "Sword"))).contains("<");
                }

                this.check("Tab design " + d.name() + ": header, footer and titles render", bad == null && titles, String.valueOf(bad));
            }
        }

        if (this.plugin.cosmetics() != null) {
            String err = this.plugin.cosmetics().selfCheck();
            this.check("Cosmetics: armor trim and shield design apply", err == null, String.valueOf(err));
        }

        // every arena design has a map, every FFA kit an open FFA arena (while the pack still generates: just a note)
        java.util.Set<net.vortexrealm.arena.ArenaTheme> have = java.util.EnumSet.noneOf(net.vortexrealm.arena.ArenaTheme.class);
        for (Arena a : this.plugin.arenas().all()) {
            if (a.ready()) {
                have.add(a.theme);
            }
        }

        List<String> missing = new ArrayList<>();
        for (net.vortexrealm.arena.ArenaTheme t : net.vortexrealm.arena.ArenaTheme.values()) {
            // retired themes (open and small duel maps, removed in 2.3.0) are not expected any more
            if (t != net.vortexrealm.arena.ArenaTheme.CUSTOM && !t.retired() && !have.contains(t)) {
                missing.add(t.name());
            }
        }

        List<String> noFfa = new ArrayList<>();
        for (Kit k : this.plugin.kits().all()) {
            if (k.ffa && k.enabled && this.plugin.ffa().all().stream().noneMatch(fa -> k.id.equalsIgnoreCase(fa.kitId) && fa.ready())) {
                noFfa.add(k.id);
            }
        }

        if (this.plugin.packPending()) {
            this.out.sendMessage(Text.mm("<yellow>• <gray>Arena pack still generating: " + missing.size() + " designs and " + noFfa.size() + " FFA arenas to go"));
        } else {
            // a vanilla biome the source seed doesn't have (pale gardens are rare) is skipped, not a fault
            List<String> rare = new ArrayList<>();
            missing.removeIf(m -> {
                net.vortexrealm.arena.ArenaTheme t = net.vortexrealm.arena.ArenaTheme.valueOf(m);
                if (t.vanilla != null) {
                    rare.add(t.display);
                    return true;
                } else {
                    return false;
                }
            });
            if (!rare.isEmpty()) {
                this.out.sendMessage(Text.mm("<yellow>• <gray>Not in this vanilla seed (skipped): " + String.join(", ", rare)));
            }

            this.check("Arenas: every design has a map (" + have.size() + ")", missing.isEmpty(), "missing " + missing);
            this.check("FFA: every FFA kit has an open arena", noFfa.isEmpty(), "missing " + noFfa);
        }

        int fits = 0;
        String tight = null;
        for (Arena a : this.plugin.arenas().all()) {
            if (a.theme != null && a.theme.vanilla != null) {
                double[] fit = net.vortexrealm.match.PreMatch.labelFit(a);
                if (fit[0] + 0.18 <= fit[1] + 1.0E-6) {
                    fits++;
                } else if (tight == null) {
                    tight = a.id + String.format(java.util.Locale.US, " (label %.2f, room %.2f)", fit[0], fit[1]);
                }
            }
        }

        this.check("Map vote: " + fits + " biome labels fit between their neighbours", tight == null, String.valueOf(tight));
        if (this.plugin.tabColumns() != null && this.plugin.tabColumns().available()) {
            String why = this.plugin.tabColumns().checkDisplayNames();
            this.check("Tab: flag images per viewer (packet builds)", why == null, String.valueOf(why));
        }

        this.check("Pack: flag image for Morocco (MA)", net.vortexrealm.pack.FlagArt.has("MA"), "no ma.png");
        this.check("Pack: server logo bundled", VortexRealm.class.getResource("/logo/vr.png") != null, "logo/vr.png missing from the jar");
        this.check(
            "Pack: pixel spaces build (13 px = 8+4+1)",
            net.vortexrealm.pack.Glyphs.space(13) instanceof net.kyori.adventure.text.TextComponent tc && tc.content().codePointCount(0, tc.content().length()) == 3,
            "wrong glyph count"
        );
        this.check("Match found: the first version's pop by default (totem, then the kit's weapon)", "BOTH".equals(net.vortexrealm.config.Opt.FOUND_EFFECT.def), String.valueOf(net.vortexrealm.config.Opt.FOUND_EFFECT.def));
        List<net.vortexrealm.build.Voxels> designs = new ArrayList<>();
        designs.add(new net.vortexrealm.build.VortexIslesDesign(0, 100, 0, 90, 3L));
        designs.add(new net.vortexrealm.build.MoleMinesDesign(0, 100, 0, 5L, false));
        designs.add(new net.vortexrealm.build.MoleMinesDesign(0, 100, 0, 6L, true));
        for (net.vortexrealm.build.DuelArenaDesign.Style st : net.vortexrealm.build.DuelArenaDesign.Style.values()) {
            int size = net.vortexrealm.build.DuelArenaDesign.playable(st, net.vortexrealm.config.Cfg.i(net.vortexrealm.config.Opt.DUEL_COMPACT_SIZE), net.vortexrealm.config.Cfg.i(net.vortexrealm.config.Opt.DUEL_BUILD_SIZE));
            net.vortexrealm.build.DuelArenaDesign d = new net.vortexrealm.build.DuelArenaDesign(st, size, 7L, 0, 100, 0, false);
            designs.add(d);
            int[] a1 = d.spawn(0);
            int[] a2 = d.spawn(1);
            int gap = Math.abs(a1[0] - a2[0]);
            this.check("Duel map " + st.display + ": " + size + " blocks, spawns " + gap + " apart", st.build ? gap >= 26 && gap <= 52 : gap >= 22 && gap <= 34, "spawn gap " + gap);
            boolean standOk = true;
            for (int[] sp : new int[][]{a1, a2}) {
                int[] col = new int[]{Integer.MIN_VALUE};
                String[] at = new String[2];
                d.column(sp[0], sp[2], (bx, by, bz, blk) -> {
                    if (by == 100 + sp[1] - 1) {
                        at[0] = blk;
                    } else if (by == 100 + sp[1] || by == 100 + sp[1] + 1) {
                        at[1] = at[1] == null ? blk : at[1] + "," + blk;
                    }
                });
                standOk &= at[0] != null && !at[0].endsWith(":air") && (at[1] == null || at[1].replace("minecraft:air", "").replace("minecraft:barrier", "").replace(",", "").isEmpty());
            }

            this.check("Duel map " + st.display + ": both spawns stand on a block with room above", standOk, "spawn blocked");
        }

        {
            // the Town Square hub has the Training Grounds wing: six bot-map alcoves and three NPC stands
            net.vortexrealm.build.ClubHubDesign hub = new net.vortexrealm.build.ClubHubDesign(0, 100, 0, 120, 7L);
            long maps = hub.features().keySet().stream().filter(k -> k.startsWith("botmap:")).count();
            long npcs = hub.features().keySet().stream().filter(k -> k.startsWith("botnpc:")).count();
            this.check("Town Square: the Bot Practice hall with " + maps + " bot maps and " + npcs + " NPC stands", maps == 9 && npcs == 3, "maps " + maps + ", npcs " + npcs);
            designs.add(hub);
            double[] sp = hub.spawn();
            this.check("Town Square: the spawn stays at the same spot (upgrades keep it)", sp[0] == 0.5 && sp[1] == 101.0 && sp[2] == 53.5, java.util.Arrays.toString(sp));
            int blocked = 0;
            for (int z = 40; z >= 14; z--) {
                if (!hub.clearAt(0, 1, z) || !hub.clearAt(0, 2, z)) {
                    blocked++;
                }
            }

            this.check("Town Square: the walk from the spawn to the fountain is clear", blocked == 0, blocked + " blocked steps");
            int dummies = net.vortexrealm.bot.DummyType.values().length;
            this.check("Training: " + dummies + " training forms, every one in a menu tab", dummies >= 19
                && java.util.Arrays.stream(net.vortexrealm.bot.DummyType.values()).allMatch(t -> List.of("SWORD", "AIR", "SPEAR", "CRYSTAL").contains(t.group())), "groups");
            boolean training = this.plugin.arenas().all().stream().anyMatch(a -> a.theme == net.vortexrealm.arena.ArenaTheme.TRAINING_GROUNDS && a.ready());
            this.check("Training: the flat Training Grounds map is ready (bots never use biome maps)", training || !net.vortexrealm.config.Cfg.bool(net.vortexrealm.config.Opt.BOTS_FLAT_MAPS), "not generated yet");
        }

        {
            // Mole: each base is sealed in rock, and solid blocks lie between the two bases
            net.vortexrealm.build.MoleMinesDesign m = new net.vortexrealm.build.MoleMinesDesign(0, 100, 0, 9L, false);
            int[] b1 = m.spawn(0);
            int[] b2 = m.spawn(1);
            int dist = (int)Math.round(Math.sqrt(Math.pow(b1[0] - b2[0], 2) + Math.pow(b1[1] - b2[1], 2) + Math.pow(b1[2] - b2[2], 2)));
            java.util.Map<Long, String> top = new java.util.HashMap<>();
            int solidBetween = 0;
            for (int x = Math.min(b1[0], b2[0]) + 5; x <= Math.max(b1[0], b2[0]) - 5; x++) {
                int fx = x;
                String[] hit = new String[1];
                int yy = 100 + (b1[1] + b2[1]) / 2;
                int zz = (b1[2] + b2[2]) / 2;
                m.column(fx, zz, (bx, by, bz, blk) -> {
                    if (by == yy) {
                        hit[0] = blk;
                    }
                });
                if (hit[0] != null && !hit[0].endsWith(":air")) {
                    solidBetween++;
                }
            }

            String[] roof = new String[1];
            m.column(b1[0], b1[2], (bx, by, bz, blk) -> {
                if (by == 100) {
                    roof[0] = blk;
                }
            });
            this.check("Mole Hollow: bases " + dist + " blocks apart, rock between them, bedrock roof (no surface)",
                dist >= 20 && dist <= 34 && solidBetween >= 8 && "minecraft:bedrock".equals(roof[0]), "dist " + dist + ", solid " + solidBetween + ", roof " + roof[0]);
        }

        for (net.vortexrealm.build.Voxels vd : designs) {
            java.util.Set<String> blocks = new java.util.HashSet<>();
            for (int x = vd.minX(); x <= vd.maxX(); x++) {
                for (int z = vd.minZ(); z <= vd.maxZ(); z++) {
                    vd.column(x, z, (bx, by, bz, b) -> blocks.add(b));
                }
            }

            List<String> bad = new ArrayList<>();
            for (String b : blocks) {
                try {
                    org.bukkit.Bukkit.createBlockData(b.contains(":") ? b : "minecraft:" + b);
                } catch (IllegalArgumentException var9) {
                    bad.add(b);
                }
            }

            this.check("Build " + vd.name() + ": all " + blocks.size() + " block types are valid", bad.isEmpty(), String.join(", ", bad));
        }

        // the SMP lives on its own server now: nothing of it may be left in this plugin
        List<String> leftover = new ArrayList<>();
        for (String c : List.of("balance", "pay", "shop", "sell", "ah", "home", "sethome", "tpa", "rtp", "warp", "claim", "smpadmin", "ecoadmin", "smpstats", "lifesteal")) {
            if (this.plugin.getCommand(c) != null) {
                leftover.add(c);
            }
        }

        this.check("Network: no SMP command is left in the Practice plugin", leftover.isEmpty(), String.join(", ", leftover));
        if (this.plugin.serverSelector() != null) {
            net.vortexrealm.smp.ServerSelector.Entry smp = this.plugin.serverSelector().get("smp");
            this.check("Network: the selector has an SMP server entry", smp != null && smp.action() == net.vortexrealm.smp.ServerSelector.Action.SERVER, "no SERVER entry with id smp");
            net.vortexrealm.smp.ServerSelector.Entry t = new net.vortexrealm.smp.ServerSelector.Entry();
            t.id = "t";
            this.check("Network: host:port is read right", "play.example.net".equals(hostOf(t, "play.example.net:25570")) && portOf(t, "play.example.net:25570") == 25570 && portOf(t, "play.example.net") == 25565, "wrong host/port");
            net.vortexrealm.smp.ServerLink.Ping self = net.vortexrealm.smp.ServerLink.ping("127.0.0.1", org.bukkit.Bukkit.getPort(), 1500);
            this.check("Network: server-list ping works (pinged this server: " + self.players() + " online)", self.online() && self.players() == org.bukkit.Bukkit.getOnlinePlayers().size(), "no answer");
        }

        if (this.plugin.modes() != null) {
            for (org.bukkit.World w : org.bukkit.Bukkit.getWorlds()) {
                this.check("Network: " + w.getName() + " has no SMP mode here", !this.plugin.modes().mode(w).smp(), "SMP mode still active");
            }
        }

        for (net.vortexrealm.kit.Kit k : this.plugin.kits().all()) {
            if (k.enabled && !this.plugin.arenas().all().isEmpty()) {
                this.check("Kit " + k.id + ": has a map (" + k.arenaKind() + ")", !this.plugin.arenas().forKit(k).isEmpty(), "no enabled arena in its pools");
            }
        }

        this.check("Ping Equalizer: module running", this.plugin.pingEqualizer() != null, "ping equalizer did not start");
        this.check("Regions: at least one region", this.plugin.regions() != null && !this.plugin.regions().all().isEmpty(), "regions.yml has no enabled region");
    }

    private void database() {
        try {
            Integer one = this.plugin.db().supply(c -> {
                Integer var3;
                try (
                    Statement st = c.createStatement();
                    ResultSet rs = st.executeQuery("SELECT 1");
                ) {
                    var3 = rs.next() ? rs.getInt(1) : 0;
                }

                return var3;
            }).get(5L, TimeUnit.SECONDS);
            this.check("Database responds", one != null && one == 1, String.valueOf(one));
        } catch (Exception var2) {
            this.check("Database responds", false, var2.toString());
        }
    }

    private void dialog() {
        try {
            Dialog d = Dialog.create(
                b -> b.empty()
                    .base(
                        DialogBase.builder(Text.mm("<aqua>Test"))
                            .body(
                                List.of(DialogBody.item(new ItemStack(Material.DIAMOND_SWORD)).description(DialogBody.plainMessage(Text.mm("x"), 150)).build())
                            )
                            .build()
                    )
                    .type(DialogType.multiAction(List.of(ActionButton.builder(Text.mm("a")).build())).columns(3).build())
            );
            this.check("Dialog API builds the queue menu", d != null, "null");
        } catch (Throwable var2) {
            this.check("Dialog API builds the queue menu", false, var2.toString());
        }
    }

    private static String hostOf(net.vortexrealm.smp.ServerSelector.Entry e, String target) {
        e.setServer(target, "");
        return e.host();
    }

    private static int portOf(net.vortexrealm.smp.ServerSelector.Entry e, String target) {
        e.setServer(target, "");
        return e.port();
    }
}
