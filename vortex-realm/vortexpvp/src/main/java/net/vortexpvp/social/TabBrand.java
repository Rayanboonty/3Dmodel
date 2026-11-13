package net.vortexpvp.social;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent.Builder;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class TabBrand {
    private final List<int[]> palette = new ArrayList<>();
    private double speed = 1.0;

    public TabBrand() {
        this.colors(List.of("#3B0F7A", "#6A2BE0", "#9B5CFF", "#4FC3FF", "#8FF6FF"));
    }

    public void colors(List<String> hex) {
        this.palette.clear();

        for (String h : hex) {
            try {
                int v = Integer.parseInt(h.trim().replace("#", ""), 16);
                this.palette.add(new int[]{v >> 16 & 0xFF, v >> 8 & 0xFF, v & 0xFF});
            } catch (NumberFormatException var5) {
            }
        }

        if (this.palette.size() < 2) {
            this.palette.clear();
            this.palette.add(new int[]{196, 21, 28});
            this.palette.add(new int[]{255, 122, 26});
        }

        for (int i = this.palette.size() - 2; i > 0; i--) {
            this.palette.add(this.palette.get(i));
        }
    }

    public void speed(double s) {
        this.speed = Math.max(0.1, Math.min(4.0, s));
    }

    public static String spaced(String name) {
        StringBuilder b = new StringBuilder();
        String[] words = name.trim().toUpperCase(Locale.ROOT).split("\\s+");

        for (int w = 0; w < words.length; w++) {
            if (w > 0) {
                b.append("   ");
            }

            String word = words[w];

            for (int i = 0; i < word.length(); i++) {
                if (i > 0) {
                    b.append(' ');
                }

                b.append(word.charAt(i));
            }
        }

        return b.toString();
    }

    private int[] sample(double t) {
        t -= Math.floor(t);
        double pos = t * this.palette.size();
        int i = (int)Math.floor(pos);
        double f = pos - i;
        f = (1.0 - Math.cos(f * Math.PI)) / 2.0;
        int[] a = this.palette.get(i % this.palette.size());
        int[] c = this.palette.get((i + 1) % this.palette.size());
        return new int[]{(int)Math.round(a[0] + (c[0] - a[0]) * f), (int)Math.round(a[1] + (c[1] - a[1]) * f), (int)Math.round(a[2] + (c[2] - a[2]) * f)};
    }

    private static int[] mix(int[] a, int[] b, double f) {
        return new int[]{(int)Math.round(a[0] + (b[0] - a[0]) * f), (int)Math.round(a[1] + (b[1] - a[1]) * f), (int)Math.round(a[2] + (b[2] - a[2]) * f)};
    }

    private static TextColor color(int[] c) {
        return TextColor.color(Math.max(0, Math.min(255, c[0])), Math.max(0, Math.min(255, c[1])), Math.max(0, Math.min(255, c[2])));
    }

    public Component brand(String text, long ticks) {
        double time = ticks * 0.006 * this.speed;
        int n = Math.max(1, text.length());
        double cycle = 120.0 / this.speed;
        double sweep = ticks % (long)Math.max(1.0, cycle) / 24.0 * this.speed;
        double light = -0.3 + sweep * 1.6;
        Builder b = Component.text();
        int[] white = new int[]{255, 241, 214};

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == ' ') {
                b.append(Component.text(" "));
            } else {
                double x = (double)i / n;
                int[] c = this.sample(x * 0.75 - time);
                double d = Math.abs(x - light);
                if (d < 0.12) {
                    c = mix(c, white, Math.pow(1.0 - d / 0.12, 2.0) * 0.85);
                }

                b.append(Component.text(String.valueOf(ch), color(c)));
            }
        }

        return b.build().decorate(TextDecoration.BOLD);
    }

    public Component line(int width, long ticks) {
        Builder b = Component.text();
        double breathe = 0.85 + 0.15 * Math.sin(ticks * 0.08 * this.speed);
        int[] edge = new int[]{40, 6, 6};
        int[] mid = this.sample(0.6 - ticks * 0.004 * this.speed);

        for (int i = 0; i < width; i++) {
            double x = Math.abs(i - (width - 1) / 2.0) / ((width - 1) / 2.0 + 1.0E-9);
            double f = Math.pow(1.0 - x, 1.6) * breathe;
            b.append(Component.text("▬", color(mix(edge, mid, f))));
        }

        return b.build().decoration(TextDecoration.BOLD, false);
    }
}
