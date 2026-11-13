package net.vortexrealm.match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.format.NamedTextColor;

public final class MatchTeam {
    public final int index;
    public final List<UUID> members;
    public final Set<UUID> alive = new LinkedHashSet<>();
    public final Set<UUID> left = new LinkedHashSet<>();
    public final NamedTextColor color;
    public int score;

    public MatchTeam(int index, List<UUID> members, NamedTextColor color) {
        this.index = index;
        this.members = Collections.unmodifiableList(new ArrayList<>(members));
        this.color = color;
    }

    public boolean has(UUID u) {
        return this.members.contains(u);
    }

    public boolean eliminated() {
        return this.alive.isEmpty();
    }

    public boolean allLeft() {
        return this.left.size() >= this.members.size();
    }

    public void resetAlive() {
        this.alive.clear();

        for (UUID u : this.members) {
            if (!this.left.contains(u)) {
                this.alive.add(u);
            }
        }
    }
}
