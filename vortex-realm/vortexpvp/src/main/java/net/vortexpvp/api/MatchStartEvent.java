package net.vortexpvp.api;

import net.vortexpvp.match.Match;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** The fight of a match starts (after the countdown). For add-ons; nothing in the plugin depends on it. */
public final class MatchStartEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Match match;

    public MatchStartEvent(Match match) {
        this.match = match;
    }

    public Match match() {
        return this.match;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
