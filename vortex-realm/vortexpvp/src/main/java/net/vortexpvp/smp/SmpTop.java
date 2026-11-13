package net.vortexpvp.smp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.function.ToDoubleFunction;
import net.kyori.adventure.text.Component;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.gui.Menu;
import net.vortexpvp.rank.Tier;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class SmpTop implements TabExecutor {
    private final VortexPvP plugin;

    public SmpTop(VortexPvP plugin) {
        this.plugin = plugin;
    }

    private SmpTop.Board forWorld(Player p) {
        return switch (this.plugin.modes().mode(p.getWorld())) {
            case LIFESTEAL -> SmpTop.Board.HEARTS;
            case STRENGTH -> SmpTop.Board.STRENGTH;
            case RANKED -> SmpTop.Board.POINTS;
            case ECO -> SmpTop.Board.MONEY;
            default -> SmpTop.Board.KILLS;
        };
    }

    private void show(Player viewer, final SmpTop.Board board) {
        (new Menu(this.plugin, viewer) {
                @Override
                protected Component title() {
                    return Text.mm("<dark_gray>Top " + board.title);
                }

                @Override
                protected int rows() {
                    return 6;
                }

                @Override
                protected void draw() {
                    List<Entry<UUID, Double>> list = new ArrayList<>();

                    for (Entry<UUID, ModeManager.Stats> e : this.plugin.modes().allStats().entrySet()) {
                        double v = board.value.applyAsDouble(e.getValue());
                        if (v > 0.0) {
                            list.add(Map.entry(e.getKey(), v));
                        }
                    }

                    list.sort((a, bx) -> Double.compare((Double)bx.getValue(), a.getValue()));
                    int[] slots = new int[]{13, 21, 23, 28, 29, 30, 31, 32, 33, 34};
                    String[] medals = new String[]{"<#FFD700>", "<#E0E0E0>", "<#CD7F32>"};

                    for (int i = 0; i < Math.min(slots.length, list.size()); i++) {
                        OfflinePlayer o = Bukkit.getOfflinePlayer(list.get(i).getKey());
                        double v = list.get(i).getValue();
                        String shown = board == SmpTop.Board.MONEY ? String.format(Locale.US, "%,.2f", v) : String.valueOf((long)v);
                        List<String> lore = new ArrayList<>();
                        lore.add(board.prefix + shown);
                        if (board == SmpTop.Board.POINTS) {
                            lore.add("<gray>Tier: " + Tier.forElo((int)v).mm());
                        }

                        this.set(
                            slots[i],
                            Items.of(Material.PLAYER_HEAD)
                                .skull(o)
                                .name((i < 3 ? medals[i] : "<white>") + "<b>#" + (i + 1) + " " + Text.esc(String.valueOf(o.getName())))
                                .lore(lore)
                                .build()
                        );
                    }

                    int mine = -1;

                    for (int i = 0; i < list.size(); i++) {
                        if (list.get(i).getKey().equals(this.viewer.getUniqueId())) {
                            mine = i + 1;
                        }
                    }

                    this.set(
                        40, Items.of(Material.PLAYER_HEAD).skull(this.viewer).name("<gold><b>You " + (mine > 0 ? "#" + mine : "<gray>(not ranked)")).build()
                    );
                    int[] tabs = new int[]{46, 47, 49, 51, 52};
                    SmpTop.Board[] all = SmpTop.Board.values();

                    for (int ix = 0; ix < all.length; ix++) {
                        SmpTop.Board b = all[ix];
                        this.set(
                            tabs[ix],
                            Items.of(b.icon).name((b == board ? "<white><b><i>" : "<gold>") + b.title).glint(b == board).hideAll().build(),
                            t -> SmpTop.this.show(this.viewer, b)
                        );
                    }

                    this.fill(Material.GRAY_STAINED_GLASS_PANE);
                }
            })
            .open();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player p) {
            SmpTop.Board b = this.forWorld(p);
            if (args.length > 0) {
                try {
                    b = SmpTop.Board.valueOf(args[0].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException var8) {
                }
            }

            this.show(p, b);
            return true;
        } else {
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (SmpTop.Board b : SmpTop.Board.values()) {
                out.add(b.name().toLowerCase(Locale.ROOT));
            }
        }

        return out;
    }

    private static enum Board {
        KILLS("Kills", Material.IRON_SWORD, s -> s.kills, "<red>"),
        HEARTS("Hearts", Material.RED_DYE, s -> Math.max(0, s.hearts), "<red>❤ "),
        STRENGTH("Strength", Material.BLAZE_POWDER, s -> s.strength, "<light_purple>⚡ "),
        POINTS("Ranked Points", Material.GOLDEN_SWORD, s -> s.points, "<gold>"),
        MONEY("Money", Material.SUNFLOWER, s -> Math.max(0.0, s.balance), "<#3DDC84>$");

        final String title;
        final Material icon;
        final ToDoubleFunction<ModeManager.Stats> value;
        final String prefix;

        private Board(String title, Material icon, ToDoubleFunction<ModeManager.Stats> value, String prefix) {
            this.title = title;
            this.icon = icon;
            this.value = value;
            this.prefix = prefix;
        }
    }
}
