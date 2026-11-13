package net.vortexrealm.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.vortexrealm.util.Items;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class Ui {
    public static final Material FRAME = Material.BLACK_STAINED_GLASS_PANE;
    public static final Material ACCENT = Material.ORANGE_STAINED_GLASS_PANE;
    public static final Material INNER = Material.GRAY_STAINED_GLASS_PANE;

    private Ui() {
    }

    public static ItemStack back() {
        return Items.of(Material.ARROW).name("<gold><b>« Back").lore("<gray>Return to the previous menu.").build();
    }

    public static ItemStack close() {
        return Items.of(Material.BARRIER).name("<red><b>Close").build();
    }

    public static ItemStack prev(int page) {
        return Items.of(Material.SPECTRAL_ARROW).name("<gold><b>« Previous Page").lore("<gray>Page " + page).build();
    }

    public static ItemStack next(int page) {
        return Items.of(Material.SPECTRAL_ARROW).name("<gold><b>Next Page »").lore("<gray>Page " + (page + 2)).build();
    }

    public static ItemStack toggle(Material icon, String name, boolean on, String... desc) {
        List<String> lore = new ArrayList<>();

        for (String d : desc) {
            lore.add("<gray>" + d);
        }

        lore.add("");
        lore.add(on ? "<gray>Status: <green><b>ON" : "<gray>Status: <red><b>OFF");
        lore.add("");
        lore.add("<yellow>▶ Click to toggle");
        return Items.of(icon).name("<gold><b>" + name).lore(lore).hideAll().glint(on).build();
    }

    public static ItemStack info(Material icon, String name, String... lines) {
        return Items.of(icon).name(name).lore(lines).hideAll().build();
    }

    public static int[] centered(int n, int rows) {
        int inner = Math.max(1, rows - 2);
        List<Integer> perRow = new ArrayList<>();
        if (n <= 4) {
            perRow.add(n);
        } else {
            for (int left = n; left > 0; left -= 7) {
                perRow.add(Math.min(7, left));
            }
        }

        int used = Math.min(perRow.size(), inner);
        int startRow = 1 + (inner - used) / 2;
        int[] out = new int[n];
        int i = 0;

        for (int r = 0; r < used; r++) {
            int k = perRow.get(r);
            int[] cols;
            if (k <= 4) {
                cols = switch (k) {
                    case 1 -> new int[]{4};
                    case 2 -> new int[]{3, 5};
                    case 3 -> new int[]{2, 4, 6};
                    default -> new int[]{1, 3, 5, 7};
                };
                if (n > 4) {
                    cols = new int[k];
                    int start = (9 - k) / 2;

                    for (int c = 0; c < k; c++) {
                        cols[c] = start + c;
                    }
                }
            } else {
                cols = new int[k];
                int start = (9 - k) / 2;

                for (int c = 0; c < k; c++) {
                    cols[c] = start + c;
                }
            }

            for (int c : cols) {
                if (i < n) {
                    out[i++] = (startRow + r) * 9 + c;
                }
            }
        }

        return Arrays.copyOf(out, i);
    }

    public static int[] fit(int n, int rows) {
        int capacity = Math.max(1, rows - 2) * 7;
        return n > 0 && n <= capacity ? centered(n, rows) : innerSlots(rows);
    }

    public static int rowsFor(int n) {
        int contentRows = n <= 7 ? 1 : (n + 6) / 7;
        return Math.max(3, Math.min(6, contentRows + 2));
    }

    public static int[] innerSlots(int rows) {
        List<Integer> out = new ArrayList<>();

        for (int r = 1; r < rows - 1; r++) {
            for (int c = 1; c < 8; c++) {
                out.add(r * 9 + c);
            }
        }

        int[] a = new int[out.size()];

        for (int i = 0; i < a.length; i++) {
            a[i] = out.get(i);
        }

        return a;
    }
}
