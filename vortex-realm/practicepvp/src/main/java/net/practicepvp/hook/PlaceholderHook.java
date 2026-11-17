package net.practicepvp.hook;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.practicepvp.PracticePlugin;
import net.practicepvp.data.Database;
import net.practicepvp.data.KitStats;
import net.practicepvp.data.PvPProfile;
import net.practicepvp.kit.Kit;
import net.practicepvp.rank.Tier;
import net.practicepvp.session.PlayerState;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public final class PlaceholderHook extends PlaceholderExpansion {
    private final PracticePlugin plugin;

    public PlaceholderHook(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    public static void install(PracticePlugin plugin) {
        new PlaceholderHook(plugin).register();
    }

    @Override
    public String getIdentifier() {
        return "practice";
    }

    @Override
    public String getAuthor() {
        return "PracticePvP";
    }

    @Override
    public String getVersion() {
        return this.plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offline, String params) {
        String p = params.toLowerCase(Locale.ROOT);
        if (p.startsWith("top_")) {
            return this.top(p.substring(4));
        } else {
            switch (p) {
                case "queued":
                    return String.valueOf(this.plugin.queues().total());
                case "playing":
                    return String.valueOf(this.plugin.matches().playingTotal() + this.plugin.ffa().playing());
                default:
                    if (p.startsWith("in_queue_")) {
                        Kit k = this.plugin.kits().get(p.substring(9));
                        return k == null ? "0" : String.valueOf(this.plugin.queues().size(k));
                    } else if (offline == null) {
                        return "";
                    } else {
                        Player online = offline.getPlayer();
                        PvPProfile prof = online == null ? null : this.plugin.profiles().getIfLoaded(online.getUniqueId());
                        if (prof == null) {
                            return "";
                        } else {
                            switch (p) {
                                case "ffa_kills":
                                    return String.valueOf(prof.ffaKills);
                                case "ffa_deaths":
                                    return String.valueOf(prof.ffaDeaths);
                                case "ffa_streak":
                                    return String.valueOf(prof.ffaStreak);
                                case "state":
                                    PlayerState st = this.plugin.sessions().state(online);
                                    return st.name().toLowerCase(Locale.ROOT);
                                case "best_kit":
                                    Kit k = this.plugin.kits().get(prof.bestKit());
                                    return k != null && this.plugin.worlds().managed(online.getWorld()) ? k.displayName : "";
                                case "best_tier":
                                case "tag":
                                    if (!this.plugin.worlds().managed(online.getWorld())) {
                                        return "";
                                    }

                                    String id = prof.bestKit();
                                    Tier t = id == null ? null : prof.tierIn(id);
                                    return t == null ? "" : t.label;
                                default:
                                    int us = p.indexOf(95);
                                    if (us < 0) {
                                        return null;
                                    } else {
                                        String what = p.substring(0, us);
                                        Kit kit = this.plugin.kits().get(p.substring(us + 1));
                                        if (kit == null) {
                                            return null;
                                        } else {
                                            KitStats ks = prof.statsOrNull(kit.id);

                                            return switch (what) {
                                                case "elo" -> String.valueOf(ks == null ? 0 : ks.elo);
                                                case "tier" -> ks != null && ks.tier() != null ? ks.tier().label : "";
                                                case "wins" -> String.valueOf(ks == null ? 0 : ks.wins);
                                                case "losses" -> String.valueOf(ks == null ? 0 : ks.losses);
                                                case "position" -> {
                                                    int pos = this.plugin.leaderboards().position(kit.id, online.getUniqueId());
                                                    yield pos < 0 ? "-" : String.valueOf(pos);
                                                }
                                                default -> null;
                                            };
                                        }
                                    }
                            }
                        }
                    }
            }
        }
    }

    private String top(String rest) {
        String[] parts = rest.split("_");
        if (parts.length < 3) {
            return null;
        } else {
            String field = parts[parts.length - 1];

            int n;
            try {
                n = Integer.parseInt(parts[parts.length - 2]);
            } catch (NumberFormatException var11) {
                return null;
            }

            String board = String.join("_", Arrays.copyOf(parts, parts.length - 2));
            List<Database.LbEntry> list = "overall".equals(board) ? this.plugin.leaderboards().overall() : this.plugin.leaderboards().top(board);
            if (n >= 1 && n <= list.size()) {
                Database.LbEntry e = list.get(n - 1);

                return switch (field) {
                    case "name" -> e.name();
                    case "elo" -> String.valueOf(e.elo());
                    case "tier" -> {
                        Tier t = Tier.byIndex(e.tier());
                        yield t == null ? "" : t.label;
                    }
                    default -> null;
                };
            } else {
                return "";
            }
        }
    }
}
