package net.vortexpvp.queue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class Matchmaker {
    public static final int PROMO_EXACT_SECONDS = 20;
    public static final int HIGH_TIER = 9;
    public static final int TIER_GAP_SECONDS = 20;

    public static double speedFor(int waiting) {
        return waiting <= 2 ? 3.0 : (waiting <= 4 ? 2.0 : 1.0);
    }

    private static double waited(Matchmaker.Candidate c, long now, Matchmaker.Params p) {
        return Math.max(0.0, (now - c.joinedAt()) / 1000.0) * Math.max(1.0, p.speed());
    }

    public static boolean promoOk(Matchmaker.Candidate c, Matchmaker.Candidate opp, long now, Matchmaker.Params p) {
        int own = Math.max(0, c.tier());
        int target = c.promoTarget() >= 0 ? c.promoTarget() : own + 1;
        int ot = Math.max(0, opp.tier());
        double waited = waited(c, now, p);
        if (target < own) {
            return ot == target || ot == own || waited >= 20.0 && Math.abs(ot - own) <= 1;
        } else if (p.promoAbove()) {
            return ot >= target && ot <= target + 1 ? true : p.promoFallback() > 0 && waited >= p.promoFallback() && Math.abs(ot - own) <= 1;
        } else {
            return Math.abs(ot - own) <= (waited < 20.0 ? 0 : 1);
        }
    }

    public static int tierGap(Matchmaker.Candidate c, long now, Matchmaker.Params p) {
        double waited = waited(c, now, p);
        if (c.promo()) {
            return waited < 20.0 ? 0 : 1;
        } else {
            int max = Math.max(0, c.tier()) >= 9 ? Math.min(1, p.maxTierGap()) : p.maxTierGap();
            if (p.sameTierSeconds() > 0) {
                return waited < p.sameTierSeconds() ? 0 : (int)Math.min((double)max, 1.0 + Math.floor((waited - p.sameTierSeconds()) / 20.0));
            } else {
                return (int)Math.min((double)max, 1.0 + Math.floor(waited / 20.0));
            }
        }
    }

    private Matchmaker() {
    }

    public static int eloWindow(Matchmaker.Candidate c, long now, Matchmaker.Params p) {
        if (!p.ranked()) {
            return Integer.MAX_VALUE;
        } else {
            double base = p.eloStart() + p.eloPerSecond() * waited(c, now, p);
            return (int)Math.round(Math.min((double)p.eloMax(), base) * c.eloMultiplier());
        }
    }

    public static int pingWindow(Matchmaker.Candidate c, long now, Matchmaker.Params p) {
        return c.pingLimit() == Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)Math.min(1.073741823E9, c.pingLimit() + p.pingPerSecond() * waited(c, now, p));
    }

    public static boolean compatible(Matchmaker.Candidate a, Matchmaker.Candidate b, long now, Matchmaker.Params p) {
        return compatible(a, b, now, p, true);
    }

    public static boolean compatible(Matchmaker.Candidate a, Matchmaker.Candidate b, long now, Matchmaker.Params p, boolean avoidRematch) {
        if (a.id().equals(b.id())) {
            return false;
        } else if (!avoidRematch || (!a.id().equals(b.avoid()) || now >= b.avoidUntil()) && (!b.id().equals(a.avoid()) || now >= a.avoidUntil())) {
            int pingDiff = Math.abs(a.ping() - b.ping());
            boolean pingOk = pingDiff <= pingWindow(a, now, p) && pingDiff <= pingWindow(b, now, p);
            if (!p.ranked()) {
                return pingOk;
            } else if (!a.promo() && !b.promo()) {
                if (p.maxTierGap() != Integer.MAX_VALUE) {
                    int gap = Math.abs(Math.max(0, a.tier()) - Math.max(0, b.tier()));
                    if (gap > tierGap(a, now, p) || gap > tierGap(b, now, p)) {
                        return false;
                    }
                }

                int eloDiff = Math.abs(a.elo() - b.elo());
                return eloDiff <= eloWindow(a, now, p) && eloDiff <= eloWindow(b, now, p) ? pingOk : false;
            } else {
                return (!a.promo() || promoOk(a, b, now, p)) && (!b.promo() || promoOk(b, a, now, p)) ? pingOk : false;
            }
        } else {
            return false;
        }
    }

    public static List<Matchmaker.Pair> pair(List<Matchmaker.Candidate> sorted, long now, Matchmaker.Params p, Set<UUID> alreadyTaken) {
        List<Matchmaker.Pair> out = new ArrayList<>();
        Set<UUID> taken = new HashSet<>(alreadyTaken);
        boolean avoidRematch = sorted.size() > 2;

        for (int i = 0; i < sorted.size(); i++) {
            Matchmaker.Candidate a = sorted.get(i);
            if (!taken.contains(a.id())) {
                Matchmaker.Candidate best = null;
                double bestScore = Double.MAX_VALUE;

                for (int j = 0; j < sorted.size(); j++) {
                    Matchmaker.Candidate b = sorted.get(j);
                    if (i != j && !taken.contains(b.id()) && compatible(a, b, now, p, avoidRematch)) {
                        double score = Math.abs(a.elo() - b.elo())
                            + 2.0 * Math.abs(a.ping() - b.ping())
                            + (p.ranked() ? 1500.0 * Math.abs(Math.max(0, a.tier()) - Math.max(0, b.tier())) : 0.0);
                        if (score < bestScore) {
                            bestScore = score;
                            best = b;
                        }
                    }
                }

                if (best != null) {
                    taken.add(a.id());
                    taken.add(best.id());
                    out.add(new Matchmaker.Pair(a, best));
                }
            }
        }

        return out;
    }

    public record Candidate(
        UUID id, int elo, int ping, long joinedAt, int pingLimit, double eloMultiplier, UUID avoid, long avoidUntil, int tier, boolean promo, int promoTarget
    ) {
        public Candidate(UUID id, int elo, int ping, long joinedAt, int pingLimit, double eloMultiplier, UUID avoid, long avoidUntil) {
            this(id, elo, ping, joinedAt, pingLimit, eloMultiplier, avoid, avoidUntil, -1, false, -1);
        }

        public Candidate(UUID id, int elo, int ping, long joinedAt, int pingLimit, double eloMultiplier, UUID avoid, long avoidUntil, int tier) {
            this(id, elo, ping, joinedAt, pingLimit, eloMultiplier, avoid, avoidUntil, tier, false, -1);
        }
    }

    public record Pair(Matchmaker.Candidate a, Matchmaker.Candidate b) {
    }

    public record Params(
        int eloStart,
        int eloPerSecond,
        int eloMax,
        int pingPerSecond,
        boolean ranked,
        int maxTierGap,
        boolean promoAbove,
        int promoFallback,
        int sameTierSeconds,
        double speed
    ) {
        public Params(int eloStart, int eloPerSecond, int eloMax, int pingPerSecond, boolean ranked) {
            this(eloStart, eloPerSecond, eloMax, pingPerSecond, ranked, Integer.MAX_VALUE, false, 0, 0, 1.0);
        }

        public Params(int eloStart, int eloPerSecond, int eloMax, int pingPerSecond, boolean ranked, int maxTierGap) {
            this(eloStart, eloPerSecond, eloMax, pingPerSecond, ranked, maxTierGap, false, 0, 0, 1.0);
        }

        public Params(int eloStart, int eloPerSecond, int eloMax, int pingPerSecond, boolean ranked, int maxTierGap, boolean promoAbove, int promoFallback) {
            this(eloStart, eloPerSecond, eloMax, pingPerSecond, ranked, maxTierGap, promoAbove, promoFallback, 0, 1.0);
        }

        public Params(
            int eloStart,
            int eloPerSecond,
            int eloMax,
            int pingPerSecond,
            boolean ranked,
            int maxTierGap,
            boolean promoAbove,
            int promoFallback,
            int sameTierSeconds
        ) {
            this(eloStart, eloPerSecond, eloMax, pingPerSecond, ranked, maxTierGap, promoAbove, promoFallback, sameTierSeconds, 1.0);
        }
    }
}
