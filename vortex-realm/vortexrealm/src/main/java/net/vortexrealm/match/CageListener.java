package net.vortexrealm.match;

import net.vortexrealm.VortexRealm;
import net.vortexrealm.data.PvPProfile;
import net.vortexrealm.kit.KitLayout;
import net.vortexrealm.session.Session;
import net.vortexrealm.util.Snd;
import net.vortexrealm.util.Text;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataType;

public final class CageListener implements Listener {
    private final VortexRealm plugin;

    public CageListener(VortexRealm plugin) {
        this.plugin = plugin;
    }

    private StartSequence sequence(Player p) {
        Session s = this.plugin.sessions().get(p);
        return s != null && s.match != null ? s.match.startSequence() : null;
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onInteract(PlayerInteractEvent e) {
        Block b = e.getClickedBlock();
        Player p = e.getPlayer();
        StartSequence seq = b == null ? null : this.sequence(p);
        Cage c = seq == null ? null : seq.cageOf(p);
        if (c != null) {
            if (c.isGate(b)) {
                e.setCancelled(true);
                if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
                    seq.ready(p);
                }
            } else {
                if (b.equals(c.chest)) {
                    e.setCancelled(true);
                    if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {
                        return;
                    }

                    if (!p.isSneaking()) {
                        p.sendActionBar(Text.mm("<#FFE24D>Crouch <gray>+ right click to save your kit layout"));
                        return;
                    }

                    this.saveLayout(p, c.player.equals(p) ? p : null);
                }
            }
        }
    }

    private void saveLayout(Player p, Player owner) {
        if (owner != null) {
            Session s = this.plugin.sessions().get(p);
            Match m = s.match;
            KitLayout layout = this.plugin.kits().layoutFrom(m.kit, p.getInventory().getStorageContents(), p.getInventory().getItemInOffHand());
            if (layout == null) {
                p.sendMessage(Text.mm("<red>That layout doesn't add up to the kit (an item is missing or was dropped)."));
                Snd.error(p);
            } else {
                PvPProfile prof = this.plugin.profiles().get(p);
                prof.layouts.put(m.kit.id, layout);
                prof.dirty = true;
                p.sendMessage(Text.mm("<green>✔ Layout saved <gray>- used in every ").append(m.kit.label()).append(Text.mm(" <gray>duel from now on.")));
                Snd.play(p, "entity.player.levelup", 0.6F, 1.6F);
            }
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onFrame(PlayerInteractEntityEvent e) {
        if (e.getRightClicked().getPersistentDataContainer().has(Cage.KEY, PersistentDataType.BYTE)) {
            e.setCancelled(true);
            Player p = e.getPlayer();
            StartSequence seq = this.sequence(p);
            Cage c = seq == null ? null : seq.cageOf(p);
            if (c != null && e.getRightClicked().equals(c.leave)) {
                if (!p.isSneaking()) {
                    p.sendActionBar(Text.mm("<#FF5555>Crouch <gray>+ right click to leave the duel"));
                } else {
                    Session s = this.plugin.sessions().get(p);
                    if (s.match != null) {
                        p.sendMessage(Text.mm("<gray>You left the duel."));
                        s.match.forfeit(p, false);
                    }
                }
            }
        }
    }

    @EventHandler(
        ignoreCancelled = true
    )
    public void onHanging(HangingBreakEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(Cage.KEY, PersistentDataType.BYTE)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        ignoreCancelled = true
    )
    public void onFramePunch(EntityDamageByEntityEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(Cage.KEY, PersistentDataType.BYTE)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        ignoreCancelled = true
    )
    public void onBreak(BlockBreakEvent e) {
        if (this.sequence(e.getPlayer()) != null && this.sequence(e.getPlayer()).cageOf(e.getPlayer()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        ignoreCancelled = true
    )
    public void onDrop(PlayerDropItemEvent e) {
        StartSequence seq = this.sequence(e.getPlayer());
        if (seq != null && seq.cageOf(e.getPlayer()) != null) {
            e.setCancelled(true);
        }
    }
}
