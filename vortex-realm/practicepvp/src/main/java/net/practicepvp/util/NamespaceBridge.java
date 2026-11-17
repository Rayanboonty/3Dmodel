package net.practicepvp.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Switching between our plugins (PracticePvP, VortexRealm, VortexPvP) keeps the world as it is: the tags the old
 * plugin put on entities (bot hall, NPCs, image walls), blocks (spawner stacks) and items (hub items, shop spawners,
 * kit items) are renamed to this plugin's name when they load, so the new plugin recognises them.
 * A tag is only taken over when the plugin that made it is not running on this server.
 */
public final class NamespaceBridge implements Listener {
    private static final String[] NAMESPACES = {"practicepvp", "vortexrealm", "vortexpvp"};
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final PersistentDataType[] TYPES = {
        PersistentDataType.STRING, PersistentDataType.BYTE, PersistentDataType.INTEGER, PersistentDataType.LONG, PersistentDataType.DOUBLE,
        PersistentDataType.FLOAT, PersistentDataType.SHORT, PersistentDataType.BYTE_ARRAY, PersistentDataType.INTEGER_ARRAY, PersistentDataType.LONG_ARRAY,
        PersistentDataType.TAG_CONTAINER
    };
    private final Plugin plugin;
    private final String own;
    private final List<String> foreign = new ArrayList<>();
    private int converted;

    public NamespaceBridge(Plugin plugin) {
        this.plugin = plugin;
        this.own = plugin.getName().toLowerCase(Locale.ROOT);
        for (String ns : NAMESPACES) {
            if (ns.equals(this.own)) {
                continue;
            }

            // never take over the tags of a plugin that is running next to us
            boolean running = false;
            for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                if (p.getName().toLowerCase(Locale.ROOT).equals(ns) && p.isEnabled()) {
                    running = true;
                }
            }

            if (!running) {
                this.foreign.add(ns);
            }
        }
    }

    public int converted() {
        return this.converted;
    }

    /** Renames the old plugins' keys in one container to ours. True when something changed. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public boolean migrate(PersistentDataContainer c) {
        if (this.foreign.isEmpty() || c.isEmpty()) {
            return false;
        }

        boolean changed = false;
        for (NamespacedKey k : new ArrayList<>(c.getKeys())) {
            if (!this.foreign.contains(k.getNamespace())) {
                continue;
            }

            NamespacedKey ours = new NamespacedKey(this.plugin, k.getKey());
            for (PersistentDataType t : TYPES) {
                if (c.has(k, t)) {
                    Object v = c.get(k, t);
                    if (v != null && !c.has(ours)) {
                        c.set(ours, t, v);
                    }

                    break;
                }
            }

            c.remove(k);
            changed = true;
        }

        if (changed) {
            this.converted++;
        }

        return changed;
    }

    public void entity(Entity e) {
        this.migrate(e.getPersistentDataContainer());
    }

    public void chunk(Chunk ch) {
        for (BlockState b : ch.getTileEntities()) {
            if (b instanceof TileState ts && this.migrate(ts.getPersistentDataContainer())) {
                ts.update(true, false);
            }
        }
    }

    public void items(ItemStack[] items) {
        if (items == null) {
            return;
        }

        for (ItemStack it : items) {
            if (it == null || !it.hasItemMeta()) {
                continue;
            }

            ItemMeta m = it.getItemMeta();
            if (this.migrate(m.getPersistentDataContainer())) {
                it.setItemMeta(m);
            }
        }
    }

    /** At start-up: every entity and block already loaded. */
    public void scanLoaded() {
        if (this.foreign.isEmpty()) {
            return;
        }

        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                this.entity(e);
            }

            for (Chunk ch : w.getLoadedChunks()) {
                this.chunk(ch);
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            this.player(p);
        }
    }

    private void player(Player p) {
        this.migrate(p.getPersistentDataContainer());
        ItemStack[] inv = p.getInventory().getContents();
        this.items(inv);
        p.getInventory().setContents(inv);
        ItemStack[] ender = p.getEnderChest().getContents();
        this.items(ender);
        p.getEnderChest().setContents(ender);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntities(EntitiesLoadEvent e) {
        if (!this.foreign.isEmpty()) {
            for (Entity en : e.getEntities()) {
                this.entity(en);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChunk(ChunkLoadEvent e) {
        if (!this.foreign.isEmpty()) {
            this.chunk(e.getChunk());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        if (!this.foreign.isEmpty()) {
            this.player(e.getPlayer());
        }
    }
}
