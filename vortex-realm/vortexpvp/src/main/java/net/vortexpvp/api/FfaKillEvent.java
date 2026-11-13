package net.vortexpvp.api;

import net.vortexpvp.ffa.FfaArena;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Someone died in FFA. {@code killer} may be null; streaks are the killer's new one and the victim's lost one. */
public final class FfaKillEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player killer;
    private final Player victim;
    private final FfaArena arena;
    private final int killerStreak;
    private final int victimStreak;

    public FfaKillEvent(Player killer, Player victim, FfaArena arena, int killerStreak, int victimStreak) {
        this.killer = killer;
        this.victim = victim;
        this.arena = arena;
        this.killerStreak = killerStreak;
        this.victimStreak = victimStreak;
    }

    public Player killer() {
        return this.killer;
    }

    public Player victim() {
        return this.victim;
    }

    public FfaArena arena() {
        return this.arena;
    }

    public int killerStreak() {
        return this.killerStreak;
    }

    public int victimStreak() {
        return this.victimStreak;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
