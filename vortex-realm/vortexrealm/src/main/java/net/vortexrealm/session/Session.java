package net.vortexrealm.session;

import java.util.UUID;
import net.vortexrealm.ffa.FfaArena;
import net.vortexrealm.kit.Kit;
import net.vortexrealm.match.Match;
import org.bukkit.Location;

public final class Session {
    public final UUID uuid;
    public final int id;
    public PlayerState state = PlayerState.OUTSIDE;
    public Match match;
    public Match spectating;
    public FfaArena ffa;
    public Kit editing;
    public UUID lastAttacker;
    public long lastAttackTime;
    public long combatUntil;
    public long spawnProtectionUntil;
    public UUID lastOpponent;
    public long lastOpponentTime;
    public long lastItemClick;
    public long lastMenuClick;
    public long lastDialogClick;
    public boolean internalTeleport;
    public long clickLockUntil;
    public boolean menuRanked;
    public long lastActive;
    public boolean buildMode;
    public boolean frozen;
    public long ffaJoinTime;
    public String lastQueueKit;
    public boolean lastQueueRanked;
    public boolean noGravity;
    public boolean thrown;
    public Location wandPos1;
    public Location wandPos2;
    public String lastMatchKit;

    public Session(UUID uuid, int id) {
        this.uuid = uuid;
        this.id = id;
    }

    public boolean inCombat() {
        return System.currentTimeMillis() < this.combatUntil;
    }

    public long combatSecondsLeft() {
        return Math.max(0L, (this.combatUntil - System.currentTimeMillis() + 999L) / 1000L);
    }

    public boolean clickCooldown(long ms) {
        long now = System.currentTimeMillis();
        if (now - this.lastItemClick < ms) {
            return true;
        } else {
            this.lastItemClick = now;
            return false;
        }
    }

    public boolean menuCooldown(long ms) {
        long now = System.currentTimeMillis();
        if (now - this.lastMenuClick < ms) {
            return true;
        } else {
            this.lastMenuClick = now;
            return false;
        }
    }

    public boolean dialogCooldown(long ms) {
        long now = System.currentTimeMillis();
        if (now - this.lastDialogClick < ms) {
            return true;
        } else {
            this.lastDialogClick = now;
            return false;
        }
    }
}
