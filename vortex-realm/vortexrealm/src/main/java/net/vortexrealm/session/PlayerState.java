package net.vortexrealm.session;

public enum PlayerState {
    OUTSIDE,
    LOBBY,
    QUEUE,
    PREMATCH,
    MATCH,
    FFA,
    SPECTATING,
    EDITING,
    /** Fighting a practice bot or training with a dummy. */
    BOT;

    public boolean inLobbyArea() {
        return this == LOBBY || this == QUEUE || this == EDITING;
    }

    public boolean fighting() {
        return this == MATCH || this == FFA;
    }
}
