package net.vortexpvp.party;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Msg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.data.Setting;
import net.vortexpvp.kit.Kit;
import net.vortexpvp.match.Match;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class PartyManager {
    private final VortexPvP plugin;
    private final Map<UUID, Party> byMember = new HashMap<>();
    private final AtomicInteger ids = new AtomicInteger();

    public PartyManager(VortexPvP plugin) {
        this.plugin = plugin;
    }

    public Party of(Player p) {
        return this.byMember.get(p.getUniqueId());
    }

    private boolean enabled(Player p) {
        if (!Cfg.bool(Opt.PARTY_ENABLED)) {
            Msg.error(p, "party.disabled");
            return false;
        } else {
            return true;
        }
    }

    private boolean inLobby(Player p) {
        PlayerState st = this.plugin.sessions().state(p);
        return st == PlayerState.LOBBY || st == PlayerState.QUEUE;
    }

    public Party create(Player leader) {
        if (!this.enabled(leader)) {
            return null;
        } else if (this.of(leader) != null) {
            Msg.error(leader, "party.already-in");
            return null;
        } else if (!this.inLobby(leader)) {
            Msg.error(leader, "party.lobby-only");
            return null;
        } else {
            this.plugin.queues().leaveAll(leader, true);
            Party party = new Party(this.ids.incrementAndGet(), leader.getUniqueId());
            this.byMember.put(leader.getUniqueId(), party);
            Msg.send(leader, "party.created");
            this.refresh(party);
            return party;
        }
    }

    public void invite(Player leader, Player target) {
        Party party = this.of(leader);
        if (party == null) {
            party = this.create(leader);
            if (party == null) {
                return;
            }
        }

        if (!party.isLeader(leader)) {
            Msg.error(leader, "party.not-leader");
        } else if (target.equals(leader)) {
            Msg.error(leader, "party.invite-self");
        } else if (this.of(target) != null) {
            Msg.error(leader, "party.target-in-party", "player", target.getName());
        } else if (this.plugin.sessions().state(target) == PlayerState.OUTSIDE) {
            Msg.error(leader, "general.player-not-in-pvp", "player", target.getName());
        } else if ("nobody".equals(this.plugin.profiles().get(target).get(Setting.PARTY_INVITES))) {
            Msg.error(leader, "party.invites-off", "player", target.getName());
        } else if (this.plugin.social() != null && !this.plugin.social().check(leader, target, net.vortexpvp.social.SocialManager.Kind.PARTY)) {
            return;
        } else if (party.members.size() >= Cfg.i(Opt.PARTY_MAX)) {
            Msg.error(leader, "party.full");
        } else {
            party.invites.put(target.getUniqueId(), System.currentTimeMillis() + Cfg.i(Opt.PARTY_INVITE_EXPIRE) * 1000L);
            Msg.send(leader, "party.invite-sent", "player", target.getName());
            Msg.send(target, "party.invite-received", "player", leader.getName());
            if (this.plugin.profiles().get(target).on(Setting.CHAT_SOUNDS)) {
                Snd.play(target, "block.note_block.pling", 1.0F, 1.2F);
            }
        }
    }

    public void accept(Player target, Player leader) {
        if (this.enabled(target)) {
            Party party = leader == null ? null : this.of(leader);
            if (party != null && party.isLeader(leader)) {
                Long exp = party.invites.remove(target.getUniqueId());
                if ((exp == null || exp < System.currentTimeMillis()) && !party.open) {
                    Msg.error(target, "party.no-invite");
                } else if (this.of(target) != null) {
                    Msg.error(target, "party.already-in");
                } else if (!this.inLobby(target)) {
                    Msg.error(target, "party.lobby-only");
                } else if (party.members.size() >= Cfg.i(Opt.PARTY_MAX)) {
                    Msg.error(target, "party.full");
                } else {
                    this.plugin.queues().leaveAll(target, true);
                    party.members.add(target.getUniqueId());
                    this.byMember.put(target.getUniqueId(), party);
                    this.broadcast(party, Msg.get("party.joined", "player", target.getName()));
                    this.refresh(party);
                }
            } else {
                Msg.error(target, "party.no-invite");
            }
        }
    }

    public void leave(Player p) {
        Party party = this.of(p);
        if (party == null) {
            Msg.error(p, "party.not-in");
        } else {
            this.remove(party, p.getUniqueId(), "party.left");
            if (this.plugin.sessions().state(p) == PlayerState.LOBBY) {
                this.plugin.lobby().giveLobbyItems(p);
            }
        }
    }

    public void remove(Party party, UUID u, String messageKey) {
        Player p = Bukkit.getPlayer(u);
        String name = p != null ? p.getName() : "?";
        party.members.remove(u);
        this.byMember.remove(u);
        if (!party.members.isEmpty()) {
            this.broadcast(party, Msg.get(messageKey, "player", name));
            if (party.leader.equals(u)) {
                party.leader = party.members.iterator().next();
                this.broadcast(party, Msg.get("party.new-leader", "player", party.leaderName()));
            }

            this.refresh(party);
        }
    }

    public void kick(Player leader, Player target) {
        Party party = this.of(leader);
        if (party != null && party.isLeader(leader)) {
            if (!target.equals(leader) && party.members.contains(target.getUniqueId())) {
                this.remove(party, target.getUniqueId(), "party.kicked");
                Msg.send(target, "party.you-were-kicked");
                if (this.plugin.sessions().state(target) == PlayerState.LOBBY) {
                    this.plugin.lobby().giveLobbyItems(target);
                }
            } else {
                Msg.error(leader, "party.not-member", "player", target.getName());
            }
        } else {
            Msg.error(leader, "party.not-leader");
        }
    }

    public void promote(Player leader, Player target) {
        Party party = this.of(leader);
        if (party != null && party.isLeader(leader)) {
            if (!party.members.contains(target.getUniqueId())) {
                Msg.error(leader, "party.not-member", "player", target.getName());
            } else {
                party.leader = target.getUniqueId();
                this.broadcast(party, Msg.get("party.new-leader", "player", target.getName()));
                this.refresh(party);
            }
        } else {
            Msg.error(leader, "party.not-leader");
        }
    }

    public void disband(Party party) {
        List<Player> online = party.online();

        for (UUID u : new ArrayList<>(party.members)) {
            this.byMember.remove(u);
        }

        party.members.clear();

        for (Player p : online) {
            Msg.send(p, "party.disbanded");
            if (this.plugin.sessions().state(p) == PlayerState.LOBBY) {
                this.plugin.lobby().giveLobbyItems(p);
            }
        }
    }

    public void setOpen(Player leader, boolean open) {
        Party party = this.of(leader);
        if (party != null && party.isLeader(leader)) {
            party.open = open;
            this.broadcast(party, Msg.get(open ? "party.opened" : "party.closed"));
        } else {
            Msg.error(leader, "party.not-leader");
        }
    }

    public void chat(Player p, String message) {
        Party party = this.of(p);
        if (party == null) {
            Msg.error(p, "party.not-in");
        } else {
            Component line = Msg.get("party.chat", "player", p.getName(), "message", message);

            for (Player o : party.online()) {
                o.sendMessage(line);
                if (!o.equals(p) && this.plugin.profiles().get(o).on(Setting.CHAT_SOUNDS)) {
                    Snd.play(o, "entity.item.pickup", 0.5F, 1.6F);
                }
            }
        }
    }

    public void broadcast(Party party, Component msg) {
        for (Player o : party.online()) {
            o.sendMessage(Msg.prefix().append(msg));
        }
    }

    private void refresh(Party party) {
        for (Player o : party.online()) {
            if (this.plugin.sessions().state(o) == PlayerState.LOBBY) {
                this.plugin.lobby().giveLobbyItems(o);
            }
        }

        this.plugin.lobby().refreshVisibilityAll();
    }

    public void startGame(Player leader, Kit kit, Match.Type type) {
        Party party = this.of(leader);
        if (party != null && party.isLeader(leader)) {
            List<Player> online = party.online();
            if (online.size() < 2) {
                Msg.error(leader, "party.need-two");
            } else {
                for (Player m : online) {
                    if (!this.inLobby(m)) {
                        Msg.error(leader, "party.member-busy", "player", m.getName());
                        return;
                    }
                }

                if (this.plugin.arenas().forKit(kit).isEmpty()) {
                    Msg.error(leader, "queue.no-arena", "kit", kit.label());
                } else {
                    List<UUID> ids = new ArrayList<>();

                    for (Player mx : online) {
                        ids.add(mx.getUniqueId());
                    }

                    Collections.shuffle(ids);
                    List<List<UUID>> teams = new ArrayList<>();
                    if (type == Match.Type.PARTY_FFA) {
                        for (UUID u : ids) {
                            teams.add(List.of(u));
                        }
                    } else {
                        int half = (ids.size() + 1) / 2;
                        teams.add(new ArrayList<>(ids.subList(0, half)));
                        teams.add(new ArrayList<>(ids.subList(half, ids.size())));
                    }

                    this.broadcast(
                        party,
                        Msg.get(
                            "party.game-starting", "mode", Text.mm(type == Match.Type.PARTY_FFA ? "<gold>Party FFA" : "<gold>Party Split"), "kit", kit.label()
                        )
                    );
                    this.plugin.matches().startParty(teams, kit, type, null);
                }
            }
        } else {
            Msg.error(leader, "party.not-leader");
        }
    }

    public void handleQuit(Player p) {
        Party party = this.of(p);
        if (party != null) {
            this.remove(party, p.getUniqueId(), "party.left");
        }

        for (Party other : new ArrayList<>(this.byMember.values())) {
            other.invites.remove(p.getUniqueId());
        }
    }

    public List<Party> all() {
        List<Party> out = new ArrayList<>();

        for (Party p : this.byMember.values()) {
            if (!out.contains(p)) {
                out.add(p);
            }
        }

        return out;
    }
}
