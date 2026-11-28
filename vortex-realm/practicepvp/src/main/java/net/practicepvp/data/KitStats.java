package net.practicepvp.data;

import net.practicepvp.rank.Tier;

public final class KitStats {
    public int elo;
    public int tier = -1;
    public int wins;
    public int losses;
    public int rankedGames;
    public int unrankedWins;
    public int unrankedLosses;
    public int streak;
    public int bestStreak;
    public int peakElo;
    public int promoState;
    public int promoWins;
    public int promoLosses;
    public int promoTarget = -1;
    public int sinceRemind;
    public int promoWait;
    public int promoDeclines;
    public int roundsWon;
    public int roundsLost;
    public int challengeWins;
    public int challengeLosses;
    public int skillGames;
    public int skillPoints;
    /** Last tier test (automatic series or a Tier Tester), for the retest cooldown. */
    public long lastTest;
    /** Last ranked match, for rank decay. */
    public long lastRanked;
    /** Last time decay took points (one step per missed week). */
    public long lastDecay;
    /** Highest tier ever reached here (-1 = never ranked); MCTiers-style points come from it. */
    public int peakTier = -1;

    public int matches() {
        return this.wins + this.losses + this.unrankedWins + this.unrankedLosses;
    }

    public Tier peak() {
        return Tier.byIndex(Math.max(this.peakTier, this.tier));
    }

    public Tier tier() {
        return Tier.byIndex(this.tier);
    }

    public boolean ranked() {
        return this.tier >= 0;
    }

    public KitStats copy() {
        KitStats k = new KitStats();
        k.elo = this.elo;
        k.tier = this.tier;
        k.wins = this.wins;
        k.losses = this.losses;
        k.rankedGames = this.rankedGames;
        k.unrankedWins = this.unrankedWins;
        k.unrankedLosses = this.unrankedLosses;
        k.streak = this.streak;
        k.bestStreak = this.bestStreak;
        k.peakElo = this.peakElo;
        k.promoState = this.promoState;
        k.promoWins = this.promoWins;
        k.promoLosses = this.promoLosses;
        k.promoTarget = this.promoTarget;
        k.sinceRemind = this.sinceRemind;
        k.promoWait = this.promoWait;
        k.promoDeclines = this.promoDeclines;
        k.roundsWon = this.roundsWon;
        k.roundsLost = this.roundsLost;
        k.challengeWins = this.challengeWins;
        k.challengeLosses = this.challengeLosses;
        k.skillGames = this.skillGames;
        k.skillPoints = this.skillPoints;
        k.lastTest = this.lastTest;
        k.lastRanked = this.lastRanked;
        k.lastDecay = this.lastDecay;
        k.peakTier = this.peakTier;
        return k;
    }

    public double winRate() {
        int total = this.wins + this.losses;
        return total == 0 ? 0.0 : (double)this.wins / total;
    }
}
