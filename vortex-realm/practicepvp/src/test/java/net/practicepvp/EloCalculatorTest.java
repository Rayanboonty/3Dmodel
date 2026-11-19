package net.practicepvp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.practicepvp.rank.EloCalculator;
import org.junit.jupiter.api.Test;

class EloCalculatorTest {
    private static final EloCalculator.Params P = new EloCalculator.Params(400, 40, 10, 24, 16, 2000, 1.0, 1, 50, 1, 50, 0.05, 0.25, 0.1);

    @Test
    void equalPlayersExpectHalf() {
        assertEquals(0.5, EloCalculator.expected(1000, 1000, 400), 1e-9);
    }

    @Test
    void expectationsAddUpToOne() {
        for (int a = 600; a <= 2400; a += 300) {
            for (int b = 600; b <= 2400; b += 300) {
                assertEquals(1.0, EloCalculator.expected(a, b, 400) + EloCalculator.expected(b, a, 400), 1e-9);
            }
        }
    }

    @Test
    void winnerGainsLoserLoses() {
        EloCalculator.Result r = EloCalculator.compute(1000, 50, 0, 1000, 50, 1, 0, P);
        assertTrue(r.winnerDelta() > 0);
        assertTrue(r.loserDelta() < 0);
    }

    @Test
    void upsetsPayMore() {
        int underdog = EloCalculator.compute(1000, 50, 0, 1400, 50, 1, 0, P).winnerDelta();
        int favourite = EloCalculator.compute(1400, 50, 0, 1000, 50, 1, 0, P).winnerDelta();
        assertTrue(underdog > favourite, underdog + " should be more than " + favourite);
    }

    @Test
    void provisionalPlayersMoveFaster() {
        int fresh = EloCalculator.compute(1000, 2, 0, 1000, 50, 1, 0, P).winnerDelta();
        int settled = EloCalculator.compute(1000, 50, 0, 1000, 50, 1, 0, P).winnerDelta();
        assertTrue(fresh > settled);
    }

    @Test
    void neverBelowZero() {
        EloCalculator.Result r = EloCalculator.compute(3000, 50, 0, 5, 50, 1, 0, P);
        assertTrue(5 + r.loserDelta() >= 0);
    }

    @Test
    void gainsStayInsideTheLimits() {
        for (int a = 0; a <= 3000; a += 250) {
            for (int b = 0; b <= 3000; b += 250) {
                EloCalculator.Result r = EloCalculator.compute(a, 50, 3, b, 50, 2, 0, P);
                assertTrue(r.winnerDelta() >= P.minGain() && r.winnerDelta() <= P.maxGain());
                assertTrue(-r.loserDelta() <= P.maxLoss());
            }
        }
    }
}
