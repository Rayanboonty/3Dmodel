package net.vortexpvp.match;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SnapshotManager {
    private static final long TTL = 600000L;
    private final Map<UUID, InventorySnapshot> snapshots = new ConcurrentHashMap<>();

    public void add(InventorySnapshot s) {
        this.snapshots.put(s.id, s);
    }

    public InventorySnapshot get(UUID id) {
        return this.snapshots.get(id);
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        this.snapshots.values().removeIf(s -> now - s.created > 600000L);
    }
}
