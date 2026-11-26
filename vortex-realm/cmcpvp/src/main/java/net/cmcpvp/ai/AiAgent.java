package net.cmcpvp.ai;

/** CMCPVP has no in-game AI assistant: just enough for the website code to compile (it is never created). */
public final class AiAgent {
    private AiAgent() {
    }

    public boolean hasKey() {
        return false;
    }

    public void reset(String session) {
    }

    public Reply chat(String session, String who, boolean op, String text) {
        return new Reply("", java.util.List.of(), false);
    }

    public record Reply(String text, java.util.List<String> changes, boolean ok) {
    }
}
