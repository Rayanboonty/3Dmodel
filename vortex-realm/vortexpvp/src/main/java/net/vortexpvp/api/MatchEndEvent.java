package net.vortexpvp.api;

import java.util.UUID;
import net.vortexpvp.match.Match;
import net.vortexpvp.match.MatchTeam;
import net.vortexpvp.rank.RankManager;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * A match ended, after ratings were applied. {@code winner} is null for draws and cancelled matches; the 1v1 ids and
 * {@code outcomes} ([0] winner, [1] loser) are set for decided 1v1 matches (outcomes only when ranked).
 */
public final class MatchEndEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Match match;
    private final MatchTeam winner;
    private final String reason;
    private final UUID winnerId;
    private final UUID loserId;
    private final RankManager.Outcome[] outcomes;

    public MatchEndEvent(Match match, MatchTeam winner, String reason, UUID winnerId, UUID loserId, RankManager.Outcome[] outcomes) {
        this.match = match;
        this.winner = winner;
        this.reason = reason;
        this.winnerId = winnerId;
        this.loserId = loserId;
        this.outcomes = outcomes;
    }

    public Match match() {
        return this.match;
    }

    public MatchTeam winner() {
        return this.winner;
    }

    /** NORMAL, DRAW, CANCELLED, FORFEIT... */
    public String reason() {
        return this.reason;
    }

    public UUID winnerId() {
        return this.winnerId;
    }

    public UUID loserId() {
        return this.loserId;
    }

    public RankManager.Outcome[] outcomes() {
        return this.outcomes;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
