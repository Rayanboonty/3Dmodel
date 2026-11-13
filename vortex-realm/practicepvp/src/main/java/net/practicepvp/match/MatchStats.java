package net.practicepvp.match;

public final class MatchStats {
    public int hits;
    public int criticalHits;
    public int combo;
    public int longestCombo;
    public double damageDealt;
    public double damageTaken;
    public int potionsUsed;
    public int totemsPopped;
    public int gapplesEaten;
    public int pearlsThrown;
    public int kills;
    public int deaths;

    public void hit(boolean crit, double damage) {
        this.hits++;
        if (crit) {
            this.criticalHits++;
        }

        this.combo++;
        this.longestCombo = Math.max(this.longestCombo, this.combo);
        this.damageDealt += damage;
    }

    public void gotHit(double damage) {
        this.combo = 0;
        this.damageTaken += damage;
    }

    public MatchStats copy() {
        MatchStats s = new MatchStats();
        s.hits = this.hits;
        s.criticalHits = this.criticalHits;
        s.combo = this.combo;
        s.longestCombo = this.longestCombo;
        s.damageDealt = this.damageDealt;
        s.damageTaken = this.damageTaken;
        s.potionsUsed = this.potionsUsed;
        s.totemsPopped = this.totemsPopped;
        s.gapplesEaten = this.gapplesEaten;
        s.pearlsThrown = this.pearlsThrown;
        s.kills = this.kills;
        s.deaths = this.deaths;
        return s;
    }
}
