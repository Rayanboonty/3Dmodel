package net.vortexrealm.web;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class LiveFeed {
    private static final Deque<LiveFeed.Event> EVENTS = new ArrayDeque<>();

    private LiveFeed() {
    }

    public static synchronized void add(String type, String player, String tier, String tierColor, String kit) {
        EVENTS.addFirst(new LiveFeed.Event(type, player, tier, tierColor, kit, System.currentTimeMillis()));

        while (EVENTS.size() > 30) {
            EVENTS.removeLast();
        }
    }

    public static synchronized List<LiveFeed.Event> recent() {
        return new ArrayList<>(EVENTS);
    }

    public record Event(String type, String player, String tier, String tierColor, String kit, long at) {
    }
}
