package net.vortexrealm.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class Party {
    public final int id;
    public UUID leader;
    public final Set<UUID> members = new LinkedHashSet<>();
    public final Map<UUID, Long> invites = new HashMap<>();
    public boolean open;

    Party(int id, UUID leader) {
        this.id = id;
        this.leader = leader;
        this.members.add(leader);
    }

    public boolean isLeader(Player p) {
        return p.getUniqueId().equals(this.leader);
    }

    public List<Player> online() {
        List<Player> out = new ArrayList<>();

        for (UUID u : this.members) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                out.add(p);
            }
        }

        return out;
    }

    public String leaderName() {
        Player p = Bukkit.getPlayer(this.leader);
        return p == null ? "?" : p.getName();
    }
}
