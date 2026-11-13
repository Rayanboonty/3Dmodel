package net.vortexrealm.rank;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Msg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.data.KitStats;
import net.vortexrealm.data.PvPProfile;
import net.vortexrealm.fx.RankUpCelebration;
import net.vortexrealm.fx.Toasts;
import net.vortexrealm.kit.Kit;
import net.vortexrealm.util.Tasks;
import net.vortexrealm.util.Text;
import org.bukkit.entity.Player;

public final class RankManager {
    private final VortexRealm plugin;
    private static final int SEEN = 1000;
    private static final int[] LATER_WAITS = new int[]{3, 5, 8, 12};
    private static final int UNKNOWN_RATING = 700;
    private static final int SKILL_SPREAD = 1600;

    public RankManager(VortexRealm plugin) {
        this.plugin = plugin;
    }

    public static EloCalculator.Params params() {
        return new EloCalculator.Params(
            Cfg.i(Opt.ELO_SCALE),
            Cfg.i(Opt.K_PROVISIONAL),
            Cfg.i(Opt.PROVISIONAL_GAMES),
            Cfg.i(Opt.K_NORMAL),
            Cfg.i(Opt.K_HIGH),
            Cfg.i(Opt.K_HIGH_FROM),
            Cfg.d(Opt.LOSS_FACTOR),
            Cfg.i(Opt.MIN_GAIN),
            Cfg.i(Opt.MAX_GAIN),
            Cfg.i(Opt.MIN_LOSS),
            Cfg.i(Opt.MAX_LOSS),
            Cfg.d(Opt.STREAK_BONUS),
            Cfg.d(Opt.STREAK_BONUS_MAX),
            Cfg.d(Opt.MARGIN_BONUS)
        );
    }

    public static Tier seriesFrom() {
        return Cfg.e(Opt.PROMOTION_FROM, Tier.class);
    }

    public RankManager.Outcome[] applyRanked(PvPProfile winner, PvPProfile loser, Kit kit, int winnerRounds, int loserRounds) {
        return this.applyRanked(winner, loser, kit, winnerRounds, loserRounds, 1.0);
    }

    /** {@code scale} multiplies the rating change (Tier Challenges count more). */
    public RankManager.Outcome[] applyRanked(PvPProfile winner, PvPProfile loser, Kit kit, int winnerRounds, int loserRounds, double scale) {
        KitStats w = winner.stats(kit.id);
        KitStats l = loser.stats(kit.id);
        EloCalculator.Result r = EloCalculator.compute(w.elo, w.rankedGames, w.streak, l.elo, l.rankedGames, winnerRounds, loserRounds, params());
        double sc = Math.max(0.0, scale);
        RankManager.Outcome wo = this.apply(winner, w, (int)Math.round(r.winnerDelta() * sc), true);
        RankManager.Outcome lo = this.apply(loser, l, (int)Math.round(r.loserDelta() * sc), false);
        return new RankManager.Outcome[]{wo, lo};
    }

    private RankManager.Outcome apply(PvPProfile prof, KitStats k, int delta, boolean won) {
        int oldElo = k.elo;
        Tier oldTier = k.tier();
        List<RankManager.RankEvent> events = new ArrayList<>();
        k.elo = Math.max(0, k.elo + delta);
        k.peakElo = Math.max(k.peakElo, k.elo);
        k.rankedGames++;
        if (won) {
            k.wins++;
            k.streak = Math.max(0, k.streak) + 1;
            k.bestStreak = Math.max(k.bestStreak, k.streak);
        } else {
            k.losses++;
            k.streak = 0;
        }

        if (k.tier < 0) {
            int need = Cfg.i(Opt.PLACEMENT_MATCHES);
            if (k.rankedGames < need) {
                events.add(new RankManager.RankEvent(RankManager.EventType.PLACEMENT, null, null, k.rankedGames, need));
                prof.dirty = true;
                return new RankManager.Outcome(oldElo, k.elo, k.elo - oldElo, oldTier, null, events);
            }

            Tier placed = Tier.forElo(k.elo);
            Tier cap = seriesFrom().prev();
            if (Cfg.bool(Opt.PROMOTION) && cap != null && placed.ordinal() > cap.ordinal()) {
                placed = cap;
            }

            k.tier = placed.ordinal();
            events.add(new RankManager.RankEvent(RankManager.EventType.PLACED, null, placed, 0, 0));
        }

        this.updateTier(k, won, events);
        prof.dirty = true;
        return new RankManager.Outcome(oldElo, k.elo, k.elo - oldElo, oldTier, k.tier(), events);
    }

    private void updateTier(KitStats k, boolean won, List<RankManager.RankEvent> events) {
        Tier current = k.tier();
        if ((k.promoState == 4 || k.promoState == 3) && !defends(current)) {
            clearPromo(k);
        }

        if (k.promoState == 4 || k.promoState == 3) {
            this.updateDefense(k, won, events);
        } else if (k.promoState == 2) {
            Tier target = Tier.byIndex(k.promoTarget);
            if (target == null) {
                clearPromo(k);
            } else {
                if (won) {
                    k.challengeWins++;
                    k.tier = target.ordinal();
                    k.elo = Math.max(k.elo, target.threshold());
                    k.peakElo = Math.max(k.peakElo, k.elo);
                    events.add(new RankManager.RankEvent(RankManager.EventType.RANK_UP, current, target, 1, 0));
                    clearPromo(k);
                } else {
                    k.challengeLosses++;
                    events.add(new RankManager.RankEvent(RankManager.EventType.SERIES_FAILED, current, target, 0, 1));
                    if (k.elo >= target.threshold()) {
                        k.promoState = 1;
                    } else {
                        clearPromo(k);
                    }
                }
            }
        } else {
            Tier eloTier = Tier.forElo(k.elo);
            boolean series = Cfg.bool(Opt.PROMOTION);
            Tier from = seriesFrom();
            if (eloTier.ordinal() > current.ordinal()) {
                for (Tier target = current.next(); target != null && target.ordinal() <= eloTier.ordinal(); target = target.next()) {
                    if (series && target.ordinal() >= from.ordinal()) {
                        if (k.promoState == 0 || k.promoTarget != target.ordinal()) {
                            k.promoState = 1;
                            k.promoTarget = target.ordinal();
                            k.sinceRemind = 0;
                            k.promoWait = 0;
                            k.promoDeclines = 0;
                            events.add(new RankManager.RankEvent(RankManager.EventType.OFFER, current, target, 0, 0));
                        }
                        break;
                    }

                    Tier before = current;
                    current = target;
                    k.tier = target.ordinal();
                    events.add(new RankManager.RankEvent(RankManager.EventType.RANK_UP, before, target, 0, 0));
                }
            } else {
                if (k.promoState == 1) {
                    Tier target = Tier.byIndex(k.promoTarget);
                    if (target == null || k.elo < target.threshold() - Cfg.i(Opt.DEMOTION_BUFFER)) {
                        clearPromo(k);
                    }
                }

                if (eloTier.ordinal() < current.ordinal() && k.elo < current.threshold() - Cfg.i(Opt.DEMOTION_BUFFER)) {
                    Tier to = current.prev();
                    if (to != null && eloTier.ordinal() > to.ordinal()) {
                        to = eloTier;
                    }

                    if (to != null && defends(current)) {
                        clearPromo(k);
                        k.promoState = 3;
                        k.promoTarget = to.ordinal();
                        events.add(new RankManager.RankEvent(RankManager.EventType.DEFENSE_DUE, current, to, 0, 0));
                    } else if (to != null) {
                        k.tier = to.ordinal();
                        events.add(new RankManager.RankEvent(RankManager.EventType.DEMOTED, current, to, 0, 0));
                    }
                }
            }
        }
    }

    /** Whether players of this tier must win a Tier Defense Challenge before dropping. */
    public static boolean defends(Tier t) {
        if (t != null && Cfg.bool(Opt.TIER_DEFENSE)) {
            Tier from = Cfg.e(Opt.TIER_DEFENSE_FROM, Tier.class);
            return from == null || t.ordinal() >= from.ordinal();
        } else {
            return false;
        }
    }

    /**
     * promoState 3 = a Tier Defense is due, 4 = the player accepted it and is queued / playing it.
     * Winning the defense keeps the tier (points go back up to its threshold); losing it drops one tier.
     * While a defense is pending, normal ranked matches use up the grace period; after that the tier drops.
     */
    private void updateDefense(KitStats k, boolean won, List<RankManager.RankEvent> events) {
        Tier current = k.tier();
        Tier to = Tier.byIndex(k.promoTarget);
        if (current == null || to == null || to.ordinal() >= current.ordinal()) {
            clearPromo(k);
        } else if (k.promoState == 4) {
            if (won) {
                k.challengeWins++;
                k.elo = Math.max(k.elo, current.threshold());
                k.peakElo = Math.max(k.peakElo, k.elo);
                clearPromo(k);
                events.add(new RankManager.RankEvent(RankManager.EventType.DEFENDED, current, current, 1, 0));
            } else {
                k.challengeLosses++;
                k.tier = to.ordinal();
                clearPromo(k);
                events.add(new RankManager.RankEvent(RankManager.EventType.DEFENSE_FAILED, current, to, 0, 1));
            }
        } else if (k.elo >= current.threshold()) {
            clearPromo(k);
            events.add(new RankManager.RankEvent(RankManager.EventType.DEFENDED, current, current, 0, 0));
        } else {
            k.promoDeclines++;
            int grace = Cfg.i(Opt.TIER_DEFENSE_GRACE);
            if (grace > 0 && k.promoDeclines >= grace) {
                k.tier = to.ordinal();
                clearPromo(k);
                events.add(new RankManager.RankEvent(RankManager.EventType.DEMOTED, current, to, 0, 0));
            }
        }
    }

    /** The tier a pending / active Tier Defense would drop to, or null. */
    public Tier defenseDue(PvPProfile prof, Kit kit) {
        KitStats k = prof.statsOrNull(kit.id);
        return k != null && (k.promoState == 3 || k.promoState == 4) && defends(k.tier()) ? Tier.byIndex(k.promoTarget) : null;
    }

    public boolean defenseActive(PvPProfile prof, Kit kit) {
        KitStats k = prof.statsOrNull(kit.id);
        return k != null && k.promoState == 4 && defends(k.tier());
    }

    public boolean startDefense(PvPProfile prof, Kit kit) {
        KitStats k = prof.statsOrNull(kit.id);
        if (k != null && (k.promoState == 3 || k.promoState == 4)) {
            k.promoState = 4;
            prof.dirty = true;
            return true;
        } else {
            return false;
        }
    }

    public void queueNormallyDefense(PvPProfile prof, Kit kit) {
        KitStats k = prof.statsOrNull(kit.id);
        if (k != null && k.promoState == 4) {
            k.promoState = 3;
            prof.dirty = true;
        }
    }

    private static void clearPromo(KitStats k) {
        k.promoState = 0;
        k.promoWins = 0;
        k.promoLosses = 0;
        k.promoTarget = -1;
        k.sinceRemind = 0;
        k.promoWait = 0;
        k.promoDeclines = 0;
    }

    public boolean challengeReady(PvPProfile prof, Kit kit) {
        KitStats k = prof.statsOrNull(kit.id);
        return k != null && (k.promoState == 1 || k.promoState == 2) && Tier.byIndex(k.promoTarget) != null;
    }

    public boolean mayPopUp(PvPProfile prof, Kit kit) {
        KitStats k = prof.statsOrNull(kit.id);
        return k != null && (!Cfg.bool(Opt.TIER_TEST_POPUP_ONCE) || k.promoDeclines < 1000);
    }

    public void markPopUpShown(PvPProfile prof, Kit kit) {
        KitStats k = prof.statsOrNull(kit.id);
        if (k != null && Cfg.bool(Opt.TIER_TEST_POPUP_ONCE) && k.promoDeclines < 1000) {
            k.promoDeclines = 1000;
            prof.dirty = true;
        }
    }

    public void queueNormally(PvPProfile prof, Kit kit) {
        KitStats k = prof.statsOrNull(kit.id);
        if (k != null && k.promoState == 2) {
            k.promoState = 1;
            prof.dirty = true;
        }
    }

    public boolean startSeries(PvPProfile prof, Kit kit) {
        KitStats k = prof.statsOrNull(kit.id);
        if (k != null && (k.promoState == 1 || k.promoState == 2)) {
            k.promoState = 2;
            k.promoWins = 0;
            k.promoLosses = 0;
            k.promoWait = 0;
            k.promoDeclines = 0;
            prof.dirty = true;
            return true;
        } else {
            return false;
        }
    }

    public static int laterWait(int declines) {
        return LATER_WAITS[Math.max(0, Math.min(LATER_WAITS.length - 1, declines))];
    }

    public int declineSeries(PvPProfile prof, Kit kit) {
        KitStats k = prof.statsOrNull(kit.id);
        if (k != null && k.promoState == 1) {
            k.promoWait = laterWait(k.promoDeclines);
            k.promoDeclines++;
            prof.dirty = true;
            return k.promoWait;
        } else {
            return 0;
        }
    }

    public Kit dueOffer(PvPProfile prof) {
        Kit due = null;

        for (Entry<String, KitStats> e : prof.kits.entrySet()) {
            KitStats k = e.getValue();
            if (k.promoState == 1) {
                if (k.promoWait > 0) {
                    k.promoWait--;
                    prof.dirty = true;
                }

                if (k.promoWait <= 0 && due == null && (!Cfg.bool(Opt.TIER_TEST_POPUP_ONCE) || k.promoDeclines < 1000)) {
                    Kit kit = this.plugin.kits().get(e.getKey());
                    if (kit != null && kit.enabled && kit.ranked) {
                        due = kit;
                    }
                }
            }
        }

        return due;
    }

    public int skillRating(PvPProfile prof, Kit kit) {
        KitStats k = prof.statsOrNull(kit.id);
        if (k == null) {
            return 700;
        } else if (k.tier >= 0) {
            return k.elo;
        } else {
            return k.skillGames > 0 ? k.skillPoints / k.skillGames : 700;
        }
    }

    public RankManager.SkillResult skillCheck(PvPProfile prof, Kit kit, double performance, int opponentRating) {
        int need = Cfg.i(Opt.SKILL_CHECK_GAMES);
        if (need > 0 && kit.ranked) {
            KitStats k = prof.stats(kit.id);
            if (k.tier < 0 && k.skillGames < need) {
                double perf = Math.max(0.0, Math.min(1.0, performance));
                int rating = (int)Math.round(Math.max(0.0, opponentRating + (perf - 0.5) * 2.0 * 1600.0));
                k.skillGames++;
                k.skillPoints += rating;
                prof.dirty = true;
                if (k.skillGames < need) {
                    return new RankManager.SkillResult(k.skillGames, need, null, 0);
                } else {
                    int avg = k.skillPoints / k.skillGames;
                    Tier placed = Tier.forElo(avg);
                    Tier max = Cfg.e(Opt.SKILL_CHECK_MAX, Tier.class);
                    if (max != null && placed.ordinal() > max.ordinal()) {
                        placed = max;
                    }

                    int top = placed.next() == null ? Integer.MAX_VALUE : placed.next().threshold() - 1;
                    k.tier = placed.ordinal();
                    k.elo = Math.max(k.elo, Math.max(placed.threshold(), Math.min(avg, top)));
                    k.peakElo = Math.max(k.peakElo, k.elo);
                    return new RankManager.SkillResult(need, need, placed, k.elo);
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public void adminSet(PvPProfile prof, Kit kit, int elo) {
        KitStats k = prof.stats(kit.id);
        k.elo = Math.max(0, elo);
        k.peakElo = Math.max(k.peakElo, k.elo);
        k.tier = Tier.forElo(k.elo).ordinal();
        clearPromo(k);
        prof.dirty = true;
    }

    public void adminSetTier(PvPProfile prof, Kit kit, Tier tier) {
        KitStats k = prof.stats(kit.id);
        k.tier = tier.ordinal();
        k.elo = Math.max(k.elo, tier.threshold());
        if (tier.next() != null && k.elo >= tier.next().threshold()) {
            k.elo = tier.next().threshold() - 1;
        }

        clearPromo(k);
        prof.dirty = true;
    }

    public void announce(Player p, Kit kit, RankManager.Outcome o) {
        this.announce(p, kit, o, false);
    }

    public void announce(Player p, Kit kit, RankManager.Outcome o, boolean celebratedInArena) {
        for (RankManager.RankEvent e : o.events()) {
            switch (e.type()) {
                case RANK_UP:
                case PLACED:
                    boolean placed = e.type() == RankManager.EventType.PLACED;
                    Toasts.rankUp(p, e.to(), placed);
                    Tasks.later(Cfg.i(Opt.END_DELAY) + 15L, () -> {
                        if (!celebratedInArena) {
                            if (p.isOnline()) {
                                KitStats ks = this.plugin.profiles().get(p).statsOrNull(kit.id);
                                RankUpCelebration.play(this.plugin, p, e.from(), e.to(), kit.label(), ks == null ? -1 : ks.elo, kit.id, placed);
                            }
                        }
                    });
                    Tier min = Cfg.e(Opt.BROADCAST_FROM, Tier.class);
                    if (!placed && e.to().ordinal() >= min.ordinal()) {
                        RankUpCelebration.announce(p, e.to(), kit.label());
                    }
                    continue;
                case DEMOTED:
                    Msg.send(p, "rank.demoted", "from", Text.mm(e.from().mm()), "to", Text.mm(e.to().mm()), "kit", kit.label());
                case OFFER:
                case SERIES_PROGRESS:
                default:
                    continue;
                case DEFENSE_DUE:
                    p.sendMessage(
                        Text.mm("<gold>⚠ <b>Tier Defense</b></gold> <gray>- your points in ")
                            .append(kit.label())
                            .append(Text.mm("<gray> fell below "))
                            .append(Text.mm(e.from().mm()))
                            .append(Text.mm("<gray>. Queue ranked and accept the <gold>Tier Defense Challenge</gold><gray> to keep it."))
                    );
                    continue;
                case DEFENDED:
                    if (e.wins() > 0) {
                        Tasks.later(Cfg.i(Opt.END_DELAY) + 10L, () -> {
                            if (p.isOnline()) {
                                net.vortexrealm.fx.Fx.shine(p, "TIER DEFENDED", net.vortexrealm.fx.Fx.GREEN, Text.mm(e.to().mm()).append(Text.mm(" <gray>in ")).append(kit.label()), 50);
                                net.vortexrealm.util.Snd.play(p, "ui.toast.challenge_complete", 0.8F, 1.2F);
                            }
                        });
                    }

                    p.sendMessage(Text.mm("<green>✔ You defended ").append(Text.mm(e.to().mm())).append(Text.mm(" <green>in ")).append(kit.label()).append(Text.mm("<green>!")));
                    continue;
                case DEFENSE_FAILED:
                    Tasks.later(Cfg.i(Opt.END_DELAY) + 10L, () -> {
                        if (p.isOnline()) {
                            net.vortexrealm.fx.Fx.shine(p, "TIER LOST", net.vortexrealm.fx.Fx.RED, Text.mm(e.from().mm() + " <dark_gray>➜ " + e.to().mm()), 50);
                            net.vortexrealm.util.Snd.play(p, "entity.blaze.death", 0.6F, 0.8F);
                        }
                    });
                    Msg.send(p, "rank.demoted", "from", Text.mm(e.from().mm()), "to", Text.mm(e.to().mm()), "kit", kit.label());
                    continue;
                case SERIES_FAILED:
                    p.sendMessage(
                        Text.mm("<red>Tier Test failed.</red> <gray>You stay ")
                            .append(Text.mm(e.from() == null ? "<gray>unranked" : e.from().mm()))
                            .append(Text.mm("<gray> - try again whenever you're ready (queue "))
                            .append(kit.label())
                            .append(Text.mm("<gray>)."))
                    );
                    continue;
                case PLACEMENT:
            }

            int done = e.wins();
            int need = e.losses();
            StringBuilder bar = new StringBuilder();

            for (int i = 0; i < need; i++) {
                bar.append(i < done ? "<#FFB800>■" : "<dark_gray>■");
            }

            p.sendMessage(
                Text.mm("<gold><b>Placement</b> <gray>")
                    .append(kit.label())
                    .append(Text.mm(" <white>" + done + "/" + need + " " + bar + " <gray>- " + (need - done) + " more to earn your first rank."))
            );
        }
    }

    public static boolean hasOffer(List<RankManager.RankEvent> events) {
        for (RankManager.RankEvent e : events) {
            if (e.type() == RankManager.EventType.OFFER) {
                return true;
            }
        }

        return false;
    }

    public static enum EventType {
        RANK_UP,
        DEMOTED,
        DEFENSE_DUE,
        DEFENDED,
        DEFENSE_FAILED,
        OFFER,
        SERIES_PROGRESS,
        SERIES_FAILED,
        PLACED,
        PLACEMENT;
    }

    public record Outcome(int oldElo, int newElo, int delta, Tier oldTier, Tier newTier, List<RankManager.RankEvent> events) {
    }

    public record RankEvent(RankManager.EventType type, Tier from, Tier to, int wins, int losses) {
    }

    public record SkillResult(int done, int need, Tier placed, int points) {
    }
}
