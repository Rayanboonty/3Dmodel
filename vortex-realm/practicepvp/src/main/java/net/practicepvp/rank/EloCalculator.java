package net.practicepvp.rank;

public final class EloCalculator {
    private EloCalculator() {
    }

    public static double expected(int a, int b, int scale) {
        return 1.0 / (1.0 + Math.pow(10.0, (double)(b - a) / Math.max(1, scale)));
    }

    public static int k(int elo, int games, EloCalculator.Params p) {
        if (games < p.provisionalGames()) {
            return p.kProvisional();
        } else {
            return elo >= p.kHighFrom() ? p.kHigh() : p.kNormal();
        }
    }

    public static EloCalculator.Result compute(
        int winnerElo, int winnerGames, int winnerStreak, int loserElo, int loserGames, int winnerRounds, int loserRounds, EloCalculator.Params p
    ) {
        double ew = expected(winnerElo, loserElo, p.scale());
        double upset = 1.0 - ew;
        boolean flawless = loserRounds == 0 && winnerRounds > 1;
        double margin = flawless ? 1.0 + p.marginBonus() : 1.0;
        double streak = 1.0 + Math.min(p.streakMax(), Math.max(0, winnerStreak) * p.streakPerWin());
        int gain = (int)Math.round(k(winnerElo, winnerGames, p) * upset * margin * streak);
        gain = Math.max(p.minGain(), Math.min(p.maxGain(), gain));
        int loss = (int)Math.round(k(loserElo, loserGames, p) * upset * p.lossFactor() * margin);
        loss = Math.max(p.minLoss(), Math.min(p.maxLoss(), loss));
        loss = Math.min(loss, Math.max(0, loserElo));
        return new EloCalculator.Result(gain, -loss, ew);
    }

    public record Params(
        int scale,
        int kProvisional,
        int provisionalGames,
        int kNormal,
        int kHigh,
        int kHighFrom,
        double lossFactor,
        int minGain,
        int maxGain,
        int minLoss,
        int maxLoss,
        double streakPerWin,
        double streakMax,
        double marginBonus
    ) {
    }

    public record Result(int winnerDelta, int loserDelta, double winnerExpected) {
    }
}
