package net.practicepvp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.practicepvp.queue.Matchmaker;
import org.junit.jupiter.api.Test;

class MatchmakerTest {
    private static final Matchmaker.Params RANKED = new Matchmaker.Params(100, 10, 600, 10, true);
    private static final Matchmaker.Params CASUAL = new Matchmaker.Params(100, 10, 600, 10, false);

    private static Matchmaker.Candidate c(int elo, int ping, long joined) {
        return new Matchmaker.Candidate(UUID.randomUUID(), elo, ping, joined, Integer.MAX_VALUE, 1.0, null, 0L);
    }

    @Test
    void nobodyFightsThemselves() {
        Matchmaker.Candidate a = c(1000, 30, 0);
        assertFalse(Matchmaker.compatible(a, a, 0, RANKED));
    }

    @Test
    void closeEloMatchesAtOnce() {
        assertTrue(Matchmaker.compatible(c(1000, 30, 0), c(1050, 40, 0), 0, RANKED));
    }

    @Test
    void farEloWaitsThenWidens() {
        Matchmaker.Candidate a = c(1000, 30, 0);
        Matchmaker.Candidate b = c(1400, 30, 0);
        assertFalse(Matchmaker.compatible(a, b, 0, RANKED));
        assertTrue(Matchmaker.compatible(a, b, 60_000, RANKED));
    }

    @Test
    void casualIgnoresElo() {
        assertTrue(Matchmaker.compatible(c(500, 30, 0), c(2500, 30, 0), 0, CASUAL));
    }

    @Test
    void pairsTheClosestOpponents() {
        Matchmaker.Candidate a = c(1000, 30, 0);
        Matchmaker.Candidate b = c(1010, 30, 0);
        Matchmaker.Candidate x = c(1090, 30, 0);
        Matchmaker.Candidate y = c(1095, 30, 0);
        List<Matchmaker.Pair> pairs = Matchmaker.pair(List.of(a, b, x, y), 0, RANKED, Set.of());
        assertEquals(2, pairs.size());
        assertEquals(b, pairs.get(0).b());
        assertEquals(y, pairs.get(1).b());
    }

    @Test
    void takenPlayersAreSkipped() {
        Matchmaker.Candidate a = c(1000, 30, 0);
        Matchmaker.Candidate b = c(1000, 30, 0);
        assertTrue(Matchmaker.pair(List.of(a, b), 0, RANKED, Set.of(b.id())).isEmpty());
    }
}
