package net.vortexrealm.util;

import java.text.NumberFormat;
import java.util.Locale;

public final class Fmt {
    private static final NumberFormat NUM = NumberFormat.getIntegerInstance(Locale.US);

    private Fmt() {
    }

    public static String clock(long seconds) {
        if (seconds < 0L) {
            seconds = 0L;
        }

        long h = seconds / 3600L;
        long m = seconds % 3600L / 60L;
        long s = seconds % 60L;
        return h > 0L ? h + ":" + pad(m) + ":" + pad(s) : m + ":" + pad(s);
    }

    public static String duration(long seconds) {
        if (seconds < 0L) {
            seconds = 0L;
        }

        long d = seconds / 86400L;
        long h = seconds % 86400L / 3600L;
        long m = seconds % 3600L / 60L;
        long s = seconds % 60L;
        if (d > 0L) {
            return d + "d" + (h > 0L ? " " + h + "h" : "");
        } else if (h > 0L) {
            return h + "h" + (m > 0L ? " " + m + "m" : "");
        } else {
            return m > 0L ? m + "m" + (s > 0L ? " " + s + "s" : "") : s + "s";
        }
    }

    private static String pad(long v) {
        return v < 10L ? "0" + v : String.valueOf(v);
    }

    public static String num(long v) {
        synchronized (NUM) {
            return NUM.format(v);
        }
    }

    public static String signed(int v) {
        return (v >= 0 ? "+" : "") + v;
    }

    public static String hp(double health) {
        double hearts = Math.round(health) / 2.0;
        return hearts == Math.floor(hearts) ? String.valueOf((int)hearts) : String.valueOf(hearts);
    }

    public static String oneDecimal(double v) {
        return String.format(Locale.US, "%.1f", v);
    }

    public static String percent(double ratio) {
        return Math.round(ratio * 100.0) + "%";
    }

    public static String kdr(int kills, int deaths) {
        return deaths == 0 ? String.valueOf(kills) : String.format(Locale.US, "%.2f", (double)kills / deaths);
    }

    public static String pingColored(int ping) {
        String color = ping < 60 ? "<green>" : (ping < 120 ? "<yellow>" : (ping < 200 ? "<gold>" : "<red>"));
        return color + ping + "ms";
    }

    public static String capitalize(String s) {
        if (s != null && !s.isEmpty()) {
            String lower = s.toLowerCase(Locale.ROOT).replace('_', ' ');
            StringBuilder out = new StringBuilder(lower.length());
            boolean up = true;

            for (char c : lower.toCharArray()) {
                out.append(up ? Character.toUpperCase(c) : c);
                up = c == ' ';
            }

            return out.toString();
        } else {
            return s;
        }
    }
}
