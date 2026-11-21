package net.vortexrealm.realm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.vortexrealm.util.Snd;
import net.vortexrealm.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Story characters: they stand where an admin placed them, talk to players, and remember. What they say depends on
 * the player's reputation with them, the player's faction and choices, and what the whole server voted.
 */
public final class StoryNpcs implements Listener {
    private final Realm realm;
    private final Map<UUID, Long> cool = new HashMap<>();

    StoryNpcs(Realm realm) {
        this.realm = realm;
    }

    List<String> ids() {
        ConfigurationSection s = this.realm.story.getConfigurationSection("npcs");
        return s == null ? List.of() : new ArrayList<>(s.getKeys(false));
    }

    String name(String id) {
        return this.realm.story.getString("npcs." + id + ".name", id);
    }

    private String color(String id) {
        return this.realm.story.getString("npcs." + id + ".color", "#FFD166");
    }

    // ------------------------------------------------------------------ keeping them in the world

    void second() {
        if (System.currentTimeMillis() / 1000 % 10 != 0) {
            return;
        }

        for (Map.Entry<String, Location> e : this.realm.npcSpots.entrySet()) {
            Location l = e.getValue();
            if (l.getWorld() == null || !l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) {
                continue;
            }

            Entity found = this.find(e.getKey(), l);
            if (found == null) {
                this.spawn(e.getKey(), l);
            } else {
                if (found.getLocation().distanceSquared(l) > 4) {
                    found.teleport(l);
                }

                this.realm.hideFromOthers(found);
            }
        }
    }

    private Entity find(String id, Location l) {
        for (Entity en : l.getWorld().getNearbyEntities(l, 24, 24, 24)) {
            if (id.equals(en.getPersistentDataContainer().get(this.realm.npcKey, PersistentDataType.STRING))) {
                return en;
            }
        }

        return null;
    }

    private void spawn(String id, Location l) {
        String type = this.realm.story.getString("npcs." + id + ".type", "VILLAGER").toUpperCase(Locale.ROOT);
        EntityType t;
        try {
            t = EntityType.valueOf(type);
        } catch (IllegalArgumentException bad) {
            t = EntityType.VILLAGER;
        }

        Entity en = l.getWorld().spawnEntity(l, t);
        en.customName(Text.mm("<color:" + this.color(id) + "><b>" + Text.esc(this.name(id))));
        en.setCustomNameVisible(true);
        en.setInvulnerable(true);
        en.setSilent(false);
        en.setPersistent(true);
        en.getPersistentDataContainer().set(this.realm.npcKey, PersistentDataType.STRING, id);
        if (en instanceof LivingEntity le) {
            le.setRemoveWhenFarAway(false);
            le.setAI(false);
            le.setCollidable(false);
        }

        if (en instanceof Villager v) {
            String prof = this.realm.story.getString("npcs." + id + ".profession", "nitwit");
            Villager.Profession p = Registry.VILLAGER_PROFESSION.get(NamespacedKey.minecraft(prof.toLowerCase(Locale.ROOT)));
            if (p != null) {
                v.setProfession(p);
            }

            v.setVillagerLevel(5);
        }

        if (en instanceof Mob m) {
            m.setTarget(null);
        }
    }

    /** Preview on/off or testers changed: show the NPCs only to whoever sees the season. */
    void refreshVisibility() {
        for (Map.Entry<String, Location> e : this.realm.npcSpots.entrySet()) {
            Location l = e.getValue();
            if (l.getWorld() != null && l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) {
                Entity found = this.find(e.getKey(), l);
                if (found != null) {
                    this.realm.hideFromOthers(found);
                }
            }
        }
    }

    void admin(CommandSender s, Player p, String[] args) {
        String what = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        String id = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
        switch (what) {
            case "place" -> {
                if (p == null || !this.ids().contains(id)) {
                    s.sendMessage(Text.mm("<red>/realm npc place <" + String.join("|", this.ids()) + ">"));
                    return;
                }

                Location old = this.realm.npcSpots.get(id);
                if (old != null && old.getWorld() != null) {
                    Entity e = this.find(id, old);
                    if (e != null) {
                        e.remove();
                    }
                }

                this.realm.npcSpots.put(id, p.getLocation());
                this.realm.save();
                this.spawn(id, p.getLocation());
                s.sendMessage(Text.mm("<green>" + Text.esc(this.name(id)) + " now lives here."));
            }
            case "remove" -> {
                Location old = this.realm.npcSpots.remove(id);
                if (old != null && old.getWorld() != null) {
                    Entity e = this.find(id, old);
                    if (e != null) {
                        e.remove();
                    }
                }

                this.realm.save();
                s.sendMessage(Text.mm("<yellow>Removed " + Text.esc(id) + "."));
            }
            default -> {
                for (String n : this.ids()) {
                    Location l = this.realm.npcSpots.get(n);
                    s.sendMessage(Text.mm("<gray>• <white>" + n + " <dark_gray>(" + Text.esc(this.name(n)) + ") <gray>» "
                        + (l == null ? "<red>not placed" : "<green>" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ())));
                }
            }
        }
    }

    // ------------------------------------------------------------------ talking

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTalk(PlayerInteractEntityEvent e) {
        String id = e.getRightClicked().getPersistentDataContainer().get(this.realm.npcKey, PersistentDataType.STRING);
        if (id == null) {
            return;
        }

        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!this.realm.running() || !this.realm.sees(p)) {
            p.sendMessage(Text.mm("<gray><i>" + Text.esc(this.name(id)) + " is waiting for the season to begin..."));
            return;
        }

        long now = System.currentTimeMillis();
        if (now - this.cool.getOrDefault(p.getUniqueId(), 0L) < 4000) {
            return;
        }

        this.cool.put(p.getUniqueId(), now);
        e.getRightClicked().teleport(e.getRightClicked().getLocation().setDirection(p.getLocation().toVector().subtract(e.getRightClicked().getLocation().toVector())));
        Realm.PlayerState st = this.realm.state(p);
        if (this.realm.storyEngine.talked(p, id)) {
            st.memory.add(id + ":met");
            return;
        }

        List<Map<?, ?>> d = this.realm.story.getMapList("npcs." + id + ".dialogue");
        for (int i = 0; i < d.size(); i++) {
            Map<?, ?> entry = d.get(i);
            String cond = Story.str(entry, "if", "");
            if (!cond.isEmpty() && !this.realm.storyEngine.test(p, cond)) {
                continue;
            }

            boolean once = "true".equalsIgnoreCase(Story.str(entry, "once", "false"));
            String onceKey = id + ":said" + i;
            if (once && st.memory.contains(onceKey)) {
                continue;
            }

            this.realm.storyEngine.play(p, Story.lines(entry, "lines"), null);
            this.realm.storyEngine.apply(p, Story.lines(entry, "effects"));
            if (once) {
                st.memory.add(onceKey);
            }

            break;
        }

        st.memory.add(id + ":met");
        this.realm.markDirty();
    }

    @EventHandler(ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(this.realm.npcKey)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(this.realm.npcKey)) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ Pip's shop

    void shop(Player p, String pick) {
        String seller = null;
        for (Entity en : p.getNearbyEntities(8, 4, 8)) {
            String id = en.getPersistentDataContainer().get(this.realm.npcKey, PersistentDataType.STRING);
            if (id != null && this.realm.story.isList("npcs." + id + ".shop")) {
                seller = id;
            }
        }

        if (seller == null || !this.realm.on(p)) {
            p.sendMessage(Text.mm("<gray>Find a trader of shards first (Pip the Peddler) and stand next to them."));
            return;
        }

        List<Map<?, ?>> shop = this.realm.story.getMapList("npcs." + seller + ".shop");
        if (pick == null) {
            p.sendMessage(Text.mm("<color:" + this.color(seller) + "><b>" + Text.esc(this.name(seller)) + "'s wares</b> <gray>(you have <light_purple>"
                + this.realm.countShards(p) + "</light_purple> shards)"));
            for (int i = 0; i < shop.size(); i++) {
                Map<?, ?> t = shop.get(i);
                p.sendMessage(Text.mm("  <click:run_command:'/realm buy " + (i + 1) + "'><hover:show_text:'<gray>Click to buy'><light_purple>"
                    + Story.num(t, "cost", 1) + " ✦</light_purple> <white>" + Story.num(t, "amount", 1) + "x " + Text.esc(Story.str(t, "name", "?")) + "</hover></click>"));
            }

            return;
        }

        int i;
        try {
            i = Integer.parseInt(pick) - 1;
        } catch (NumberFormatException e) {
            i = -1;
        }

        if (i < 0 || i >= shop.size()) {
            this.shop(p, null);
            return;
        }

        Map<?, ?> t = shop.get(i);
        Material m = Material.matchMaterial(Story.str(t, "item", "STONE"));
        int cost = Story.num(t, "cost", 1);
        if (m == null || !this.realm.takeShards(p, cost)) {
            p.sendMessage(Text.mm("<red>You need " + cost + " Rift Shards. <gray>Slay Rift creatures to find them."));
            Snd.error(p);
            return;
        }

        this.realm.give(p, new ItemStack(m, Story.num(t, "amount", 1)));
        p.sendMessage(Text.mm("<color:" + this.color(seller) + ">" + Text.esc(this.name(seller)) + "</color> <dark_gray>» <white>Pleasure doing business!"));
        Snd.play(p, "entity.wandering_trader.yes", 1.0F, 1.0F);
    }
}
