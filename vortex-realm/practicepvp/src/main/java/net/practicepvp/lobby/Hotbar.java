package net.practicepvp.lobby;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import net.practicepvp.util.Items;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class Hotbar {
    private static final String G = "<b><gradient:#FFE066:#FFAA00>";
    private static final String GE = "</gradient></b>";
    private final JavaPlugin plugin;
    private final Map<Hotbar.Layout, List<Hotbar.Entry>> layouts = new EnumMap<>(Hotbar.Layout.class);

    public Hotbar(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private static Map<Hotbar.Layout, List<Hotbar.Def>> defaults() {
        Map<Hotbar.Layout, List<Hotbar.Def>> d = new EnumMap<>(Hotbar.Layout.class);
        Hotbar.Def leaderboards = new Hotbar.Def(
            Hotbar.Action.LEADERBOARDS,
            6,
            Material.EMERALD,
            "<b><gradient:#FFE066:#FFAA00>Leaderboards & Stats</gradient></b>",
            List.of("<gray>Top players for every kit,", "<gray>your tiers and match history.", "", "<green>▶ Click to open"),
            false
        );
        Hotbar.Def kitEditor = new Hotbar.Def(
            Hotbar.Action.KIT_EDITOR,
            7,
            Material.BOOK,
            "<b><gradient:#FFE066:#FFAA00>Kit Editor</gradient></b>",
            List.of("<gray>Move items where you want them.", "<gray>Saved per kit.", "", "<green>▶ Click to edit"),
            false
        );
        Hotbar.Def partyLeave = new Hotbar.Def(
            Hotbar.Action.PARTY_LEAVE,
            8,
            Material.RED_DYE,
            "<b><red>Leave Party</red></b>",
            List.of("<gray>Leave your current party.", "", "<red>▶ Click to leave"),
            false
        );
        d.put(
            Hotbar.Layout.LOBBY,
            List.of(
                new Hotbar.Def(
                    Hotbar.Action.UNRANKED,
                    0,
                    Material.DIAMOND_SWORD,
                    "<b><gradient:#FFE066:#FFAA00>Duels</gradient></b>",
                    List.of(
                        "<gray>Every kit in one menu - matches earn",
                        "<gray>rank points.",
                        "<gray>Enough points? Accept the <gold>Tier Test</gold>",
                        "<gray>and climb from <#9E9E9E>LT5</#9E9E9E> <gray>to <gold><b>HT1</b></gold><gray>.",
                        "",
                        "<green>▶ Click to pick kits"
                    ),
                    false
                ),
                new Hotbar.Def(
                    Hotbar.Action.FFA,
                    2,
                    Material.NETHER_STAR,
                    "<b><gradient:#FFE066:#FFAA00>Free For All</gradient></b>",
                    List.of("<gray>Drop straight into the FFA pit.", "<gray>Kill streaks, heal on kill.", "", "<green>▶ Click to choose an arena"),
                    true
                ),
                new Hotbar.Def(
                    Hotbar.Action.PARTY_CREATE,
                    4,
                    Material.NAME_TAG,
                    "<b><gradient:#FFE066:#FFAA00>Create Party</gradient></b>",
                    List.of("<gray>Team up with friends for", "<gray>party FFA and split fights.", "", "<green>▶ Click to create"),
                    false
                ),
                new Hotbar.Def(
                    Hotbar.Action.SPECTATE,
                    5,
                    Material.ENDER_EYE,
                    "<b><gradient:#FFE066:#FFAA00>Spectate</gradient></b>",
                    List.of("<gray>Watch live matches.", "", "<green>▶ Click to browse"),
                    false
                ),
                leaderboards,
                kitEditor,
                new Hotbar.Def(
                    Hotbar.Action.SETTINGS,
                    8,
                    Material.COMPARATOR,
                    "<b><gradient:#FFE066:#FFAA00>Settings</gradient></b>",
                    List.of("<gray>Scoreboard, effects, sounds,", "<gray>matchmaking and more.", "", "<green>▶ Click to open"),
                    false
                )
            )
        );
        d.put(
            Hotbar.Layout.PARTY_LEADER,
            List.of(
                new Hotbar.Def(
                    Hotbar.Action.PARTY_GAMES,
                    0,
                    Material.GOLDEN_AXE,
                    "<b><gradient:#FFE066:#FFAA00>Party Games</gradient></b>",
                    List.of("<gray>Start a Party FFA or a", "<gray>Party Split fight.", "", "<green>▶ Click to start"),
                    true
                ),
                new Hotbar.Def(
                    Hotbar.Action.PARTY_INFO,
                    1,
                    Material.PAPER,
                    "<b><gradient:#FFE066:#FFAA00>Party Info</gradient></b>",
                    List.of("<gray>Members, leader and options.", "", "<green>▶ Click to open"),
                    false
                ),
                new Hotbar.Def(
                    Hotbar.Action.PARTY_INVITE,
                    2,
                    Material.NAME_TAG,
                    "<b><gradient:#FFE066:#FFAA00>Invite Player</gradient></b>",
                    List.of("<gray>Invite someone by name.", "", "<green>▶ Click to invite"),
                    false
                ),
                leaderboards,
                kitEditor,
                partyLeave
            )
        );
        d.put(
            Hotbar.Layout.PARTY_MEMBER,
            List.of(
                new Hotbar.Def(
                    Hotbar.Action.PARTY_INFO,
                    0,
                    Material.PAPER,
                    "<b><gradient:#FFE066:#FFAA00>Party Info</gradient></b>",
                    List.of("<gray>Members and leader.", "", "<green>▶ Click to open"),
                    false
                ),
                leaderboards,
                kitEditor,
                partyLeave
            )
        );
        d.put(
            Hotbar.Layout.QUEUE,
            List.of(
                new Hotbar.Def(
                    Hotbar.Action.LEAVE_QUEUE,
                    8,
                    Material.RED_DYE,
                    "<b><red>Leave Queue</red></b>",
                    List.of("<gray>Stop searching and get your", "<gray>lobby items back.", "", "<red>▶ Click to leave"),
                    false
                )
            )
        );
        return d;
    }

    public void load() {
        File file = new File(this.plugin.getDataFolder(), "hotbar.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        Map<Hotbar.Layout, List<Hotbar.Def>> defs = defaults();
        boolean changed = false;
        if (!file.exists()) {
            y.options()
                .setHeader(
                    List.of(
                        "Hotbar items. slot 0-8, material, name/lore in MiniMessage.",
                        "Actions: ranked, unranked, ffa, party-create, spectate, leaderboards, kit-editor, settings,",
                        "leave-queue, party-games, party-info, party-invite, party-leave.",
                        "Remove an entry to remove the item. Restart or /pvpadmin reload to apply."
                    )
                );

            for (Map.Entry<Hotbar.Layout, List<Hotbar.Def>> e : defs.entrySet()) {
                String layoutKey = e.getKey().name().toLowerCase(Locale.ROOT).replace('_', '-');

                for (Hotbar.Def def : e.getValue()) {
                    String b = layoutKey + "." + def.action.name().toLowerCase(Locale.ROOT).replace('_', '-') + ".";
                    y.set(b + "slot", def.slot);
                    y.set(b + "material", def.material.name());
                    y.set(b + "name", def.name);
                    y.set(b + "lore", def.lore);
                    y.set(b + "glint", def.glint);
                }
            }

            y.set("version", 7);
            changed = true;
        }

        if (y.getInt("version", 1) < 2) {
            y.set("lobby.ranked", null);
            Hotbar.Def duels = defs.get(Hotbar.Layout.LOBBY).get(0);
            y.set("lobby.unranked.slot", duels.slot);
            y.set("lobby.unranked.material", duels.material.name());
            y.set("lobby.unranked.name", duels.name);
            y.set("lobby.unranked.lore", duels.lore);
            y.set("version", 2);
            changed = true;
        }

        if (y.getInt("version", 1) < 4) {
            y.set("lobby.ranked", null);
            Hotbar.Def duels = defs.get(Hotbar.Layout.LOBBY).get(0);
            y.set("lobby.unranked.lore", duels.lore);
            y.set("version", 4);
            changed = true;
        }

        if (y.getInt("version", 1) < 6) {
            y.set("lobby.bots", null);
            y.set("version", 6);
            changed = true;
        }

        if (y.getInt("version", 1) < 7) {
            y.set("lobby.unranked.lore", defs.get(Hotbar.Layout.LOBBY).get(0).lore);
            y.set("version", 7);
            changed = true;
        }

        this.layouts.clear();

        for (Hotbar.Layout layout : Hotbar.Layout.values()) {
            String layoutKey = layout.name().toLowerCase(Locale.ROOT).replace('_', '-');
            ConfigurationSection sec = y.getConfigurationSection(layoutKey);
            List<Hotbar.Entry> list = new ArrayList<>();
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    ConfigurationSection s = sec.getConfigurationSection(key);
                    Hotbar.Action action = Hotbar.Action.parse(key);
                    if (s != null && action != null) {
                        Material m = Material.matchMaterial(s.getString("material", "STONE"));
                        if (m == null || !m.isItem()) {
                            m = Material.PAPER;
                        }

                        int slot = Math.max(0, Math.min(8, s.getInt("slot")));
                        Items.Builder b = Items.of(m)
                            .name(s.getString("name", key))
                            .lore(s.getStringList("lore"))
                            .hideAll()
                            .unbreakable()
                            .action(action.name());
                        if (s.getBoolean("glint")) {
                            b.glint(true);
                        }

                        list.add(new Hotbar.Entry(action, slot, b.build()));
                    } else {
                        this.plugin.getLogger().warning("hotbar.yml: unknown item '" + layoutKey + "." + key + "'");
                    }
                }
            }

            this.layouts.put(layout, list);
        }

        if (changed) {
            try {
                y.save(file);
            } catch (IOException var19) {
                this.plugin.getLogger().log(Level.WARNING, "Could not write hotbar.yml", (Throwable)var19);
            }
        }
    }

    public List<Hotbar.Entry> layout(Hotbar.Layout l) {
        return this.layouts.getOrDefault(l, List.of());
    }

    public static Hotbar.Action actionOf(ItemStack item) {
        String tag = Items.tag(item, Items.ACTION);
        return tag == null ? null : Hotbar.Action.parse(tag);
    }

    public static enum Action {
        RANKED,
        UNRANKED,
        FFA,
        PARTY_CREATE,
        SPECTATE,
        LEADERBOARDS,
        KIT_EDITOR,
        SETTINGS,
        LEAVE_QUEUE,
        PARTY_GAMES,
        PARTY_INFO,
        PARTY_INVITE,
        PARTY_LEAVE;

        public static Hotbar.Action parse(String s) {
            try {
                return valueOf(s.toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (NullPointerException | IllegalArgumentException var2) {
                return null;
            }
        }
    }

    private record Def(Hotbar.Action action, int slot, Material material, String name, List<String> lore, boolean glint) {
    }

    public record Entry(Hotbar.Action action, int slot, ItemStack item) {
    }

    public static enum Layout {
        LOBBY,
        PARTY_LEADER,
        PARTY_MEMBER,
        QUEUE;
    }
}
