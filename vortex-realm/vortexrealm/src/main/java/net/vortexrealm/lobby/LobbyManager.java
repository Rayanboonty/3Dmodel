package net.vortexrealm.lobby;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Msg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.data.PvPProfile;
import net.vortexrealm.data.Setting;
import net.vortexrealm.party.Party;
import net.vortexrealm.session.PlayerReset;
import net.vortexrealm.session.PlayerState;
import net.vortexrealm.session.Session;
import net.vortexrealm.session.Tp;
import net.vortexrealm.util.Locs;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class LobbyManager {
    private final VortexRealm plugin;
    private final File file;
    private final Hotbar hotbar;
    private String lobbyRaw;
    private Location fallbackCache;

    public LobbyManager(VortexRealm plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "locations.yml");
        this.hotbar = new Hotbar(plugin);
    }

    public void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        this.lobbyRaw = y.getString("lobby");
        this.hotbar.load();
    }

    public Hotbar hotbar() {
        return this.hotbar;
    }

    public Location lobby() {
        Location l = Locs.parse(this.lobbyRaw);
        if (l != null && this.plugin.worlds().managed(l.getWorld())) {
            return l;
        } else {
            World main = Bukkit.getWorlds().get(0);
            return this.plugin.worlds().managed(main) ? main.getSpawnLocation() : this.fallbackPlatform();
        }
    }

    private Location fallbackPlatform() {
        if (this.fallbackCache != null) {
            return this.fallbackCache.clone();
        } else {
            World w = this.plugin.worlds().practiceWorld();
            if (w.getBlockAt(0, 100, 0).getType() != Material.POLISHED_BLACKSTONE_BRICKS) {
                for (int x = -5; x <= 5; x++) {
                    for (int z = -5; z <= 5; z++) {
                        boolean edge = Math.abs(x) == 5 || Math.abs(z) == 5;
                        w.getBlockAt(x, 100, z).setType(edge ? Material.GOLD_BLOCK : Material.POLISHED_BLACKSTONE_BRICKS, false);
                    }
                }

                w.getBlockAt(0, 100, 0).setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
            }

            this.fallbackCache = new Location(w, 0.5, 101.0, 0.5);
            return this.fallbackCache.clone();
        }
    }

    public boolean lobbySet() {
        return Locs.parse(this.lobbyRaw) != null;
    }

    public void setLobby(Location l) {
        this.lobbyRaw = Locs.serialize(l);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        y.set("lobby", this.lobbyRaw);

        try {
            y.save(this.file);
        } catch (IOException var4) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not save locations.yml", (Throwable)var4);
        }
    }

    private GameMode lobbyMode(Player p) {
        Session s = this.plugin.sessions().get(p);
        return s.buildMode ? GameMode.CREATIVE : GameMode.ADVENTURE;
    }

    public void sendToLobby(Player p) {
        Session s = this.plugin.sessions().get(p);
        if (!this.plugin.worlds().enterPvP(p, p.getLocation())) {
            Msg.error(p, "general.inventory-save-failed");
            s.state = PlayerState.OUTSIDE;
        } else {
            PlayerState was = s.state;
            if (was == PlayerState.MATCH || was == PlayerState.FFA || was == PlayerState.PREMATCH || was == PlayerState.SPECTATING) {
                s.clickLockUntil = System.currentTimeMillis() + 2000L;
            }

            s.match = null;
            s.spectating = null;
            s.ffa = null;
            s.editing = null;
            s.state = PlayerState.LOBBY;
            s.combatUntil = 0L;
            s.lastAttacker = null;
            PlayerReset.full(p, this.lobbyMode(p));
            PlayerReset.unfreeze(p, s);
            Location lobby = this.lobby();
            if (p.getWorld() != lobby.getWorld() || p.getLocation().distanceSquared(lobby) > 4.0) {
                Tp.to(p, lobby);
            }

            this.giveLobbyItems(p);
            this.applyComforts(p);
            this.refreshVisibilityAll();
            this.plugin.boards().refreshNametag(p);
        }
    }

    public void giveLobbyItems(Player p) {
        Session s = this.plugin.sessions().get(p);
        if (s.state == PlayerState.LOBBY) {
            Party party = this.plugin.parties().of(p);
            Hotbar.Layout layout = party == null ? Hotbar.Layout.LOBBY : (party.isLeader(p) ? Hotbar.Layout.PARTY_LEADER : Hotbar.Layout.PARTY_MEMBER);
            this.fill(p, layout);
        }
    }

    public void giveQueueItems(Player p) {
        this.fill(p, Hotbar.Layout.QUEUE);
    }

    private void fill(Player p, Hotbar.Layout layout) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        p.setItemOnCursor(null);

        for (Hotbar.Entry e : this.hotbar.layout(layout)) {
            inv.setItem(e.slot(), e.item().clone());
        }

        inv.setHeldItemSlot(layout == Hotbar.Layout.QUEUE ? 8 : 0);
        p.updateInventory();
    }

    public void applyComforts(Player p) {
        Session s = this.plugin.sessions().get(p);
        if (s.state != PlayerState.OUTSIDE) {
            PvPProfile prof = this.plugin.profiles().get(p);
            this.applyTime(p, prof);
            if (s.state == PlayerState.LOBBY || s.state == PlayerState.QUEUE) {
                boolean fly = prof.on(Setting.LOBBY_FLY) && p.hasPermission("vortex.lobbyfly");
                boolean doubleJump = Cfg.bool(Opt.LOBBY_DOUBLE_JUMP) && prof.on(Setting.DOUBLE_JUMP);
                if (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
                    p.setAllowFlight(fly || doubleJump);
                    if (!fly) {
                        p.setFlying(false);
                    }
                } else {
                    p.setAllowFlight(true);
                }

                this.applyRankLevel(p);
                boolean speed = Cfg.bool(Opt.LOBBY_SPEED) || prof.on(Setting.LOBBY_SPEED);
                if (speed) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, -1, 0, true, false, false));
                } else {
                    p.removePotionEffect(PotionEffectType.SPEED);
                }
            }
        }
    }

    /** MCPVP shows your overall position as the XP level while you're in the lobby. */
    public void applyRankLevel(Player p) {
        Session s = this.plugin.sessions().get(p);
        if (s.state == PlayerState.LOBBY || s.state == PlayerState.QUEUE) {
            PvPProfile prof = this.plugin.profiles().get(p);
            int pos = this.plugin.social() == null ? 0 : this.plugin.social().overallRank(p.getUniqueId());
            int level = prof.on(Setting.XP_RANK) ? Math.max(0, pos) : 0;
            if (p.getLevel() != level) {
                p.setLevel(level);
            }

            if (p.getExp() != 0.0F) {
                p.setExp(0.0F);
            }
        }
    }

    public void applyTime(Player p, PvPProfile prof) {
        String var3 = prof.get(Setting.LOBBY_TIME);
        switch (var3) {
            case "day":
                p.setPlayerTime(1000L, false);
                break;
            case "sunset":
                p.setPlayerTime(12500L, false);
                break;
            case "night":
                p.setPlayerTime(14500L, false);
                break;
            case "midnight":
                p.setPlayerTime(18000L, false);
                break;
            default:
                p.resetPlayerTime();
        }
    }

    public boolean canSee(Player viewer, Player target) {
        if (viewer.equals(target)) {
            return true;
        } else {
            PlayerState sv = this.plugin.sessions().state(viewer);
            PlayerState st = this.plugin.sessions().state(target);
            if (sv != PlayerState.OUTSIDE && st != PlayerState.OUTSIDE) {
                if (st != PlayerState.SPECTATING || sv != PlayerState.MATCH && sv != PlayerState.PREMATCH) {
                    if (sv != PlayerState.PREMATCH && st != PlayerState.PREMATCH) {
                        if (sv.inLobbyArea() && st.inLobbyArea()) {
                            String mode = this.plugin.profiles().get(viewer).get(Setting.LOBBY_PLAYERS);
                            if ("hide".equals(mode)) {
                                return target.hasPermission("vortex.admin") && this.plugin.sessions().get(target).buildMode;
                            }

                            if ("party".equals(mode)) {
                                Party a = this.plugin.parties().of(viewer);
                                return a != null && a == this.plugin.parties().of(target);
                            }
                        }

                        return true;
                    } else {
                        Session a = this.plugin.sessions().get(viewer);
                        Session b = this.plugin.sessions().get(target);
                        return a.match != null && a.match == b.match;
                    }
                } else {
                    return false;
                }
            } else {
                return this.plugin.hub() == null || !this.plugin.hub().hides(viewer, target);
            }
        }
    }

    public void refreshVisibility(Player viewer) {
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!target.equals(viewer)) {
                boolean see = this.canSee(viewer, target);
                if (see && !viewer.canSee(target)) {
                    viewer.showPlayer(this.plugin, target);
                } else if (!see && viewer.canSee(target)) {
                    viewer.hidePlayer(this.plugin, target);
                }
            }
        }
    }

    public void refreshVisibilityAll() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            this.refreshVisibility(viewer);
        }
    }

    public void join(Player p, boolean teleport) {
        if (!teleport && !Cfg.bool(Opt.LOBBY_ON_JOIN)) {
            Session s = this.plugin.sessions().get(p);
            if (!this.plugin.worlds().enterPvP(p, p.getLocation())) {
                Msg.error(p, "general.inventory-save-failed");
                s.state = PlayerState.OUTSIDE;
            } else {
                s.state = PlayerState.LOBBY;
                PlayerReset.full(p, this.lobbyMode(p));
                PlayerReset.unfreeze(p, s);
                this.giveLobbyItems(p);
                this.applyComforts(p);
                this.refreshVisibilityAll();
                this.plugin.boards().refreshNametag(p);
            }
        } else {
            this.sendToLobby(p);
        }
    }
}
