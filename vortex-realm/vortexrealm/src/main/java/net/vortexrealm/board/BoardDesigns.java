package net.vortexrealm.board;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

public final class BoardDesigns {
    public static final List<BoardDesigns.Design> DESIGNS = List.of(
        new BoardDesigns.Design("CLASSIC", "Classic", "Lines between sections, label: value, the mode's colour mood."),
        new BoardDesigns.Design("MINIMAL", "Minimal", "No lines, values right-aligned - clean like a menu."),
        new BoardDesigns.Design("BOXED", "Boxed", "A frame down the left side, values in white."),
        new BoardDesigns.Design("MCPVP", "MCPVP", "Gold and yellow, » bullets, bold headers."),
        new BoardDesigns.Design("NEON", "Neon", "Glowing gradients and ✦ stars, dotted separators."),
        new BoardDesigns.Design("COMPACT", "Compact", "Only the essentials, few lines."),
        new BoardDesigns.Design("ELEGANT", "Elegant", "Small-caps labels, dots, a spaced-out title."),
        new BoardDesigns.Design("DONUT", "Donut", "Icons in front of every stat, values right-aligned (DonutSMP style).")
    );
    private static final String SMALL_FROM = "abcdefghijklmnopqrstuvwxyz";
    private static final String SMALL_TO = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

    private BoardDesigns() {
    }

    private static Map<String, List<BoardDesigns.Item>> content() {
        Map<String, List<BoardDesigns.Item>> m = new LinkedHashMap<>();
        m.put(
            "PRACTICE",
            List.of(
                BoardDesigns.Item.kv("●", "Online", "<online>"),
                BoardDesigns.Item.kv("⚔", "Playing", "<playing>"),
                BoardDesigns.Item.kv("⌛", "In Queue", "<queue>"),
                BoardDesigns.Item.blank(),
                BoardDesigns.Item.head("Your Ranks"),
                BoardDesigns.Item.raw("<rank_1>"),
                BoardDesigns.Item.raw("<rank_2>"),
                BoardDesigns.Item.raw("<rank_3>")
            )
        );
        m.put(
            "HUB",
            List.of(
                BoardDesigns.Item.kv("★", "Rank", "<rank>"),
                BoardDesigns.Item.kv("⚑", "Country", "<country>"),
                BoardDesigns.Item.blank(),
                BoardDesigns.Item.kv("●", "Online", "<online>"),
                BoardDesigns.Item.kv("✉", "Ping", "<ping>ms"),
                BoardDesigns.Item.blank(),
                BoardDesigns.Item.raw("<gray>Compass <white>→ <gray>pick a mode")
            )
        );
        m.put(
            "SURVIVAL",
            List.of(
                BoardDesigns.Item.kv("★", "Rank", "<rank>"),
                BoardDesigns.Item.blank(),
                BoardDesigns.Item.kv("⚔", "Kills", "<kills>"),
                BoardDesigns.Item.kv("☠", "Deaths", "<deaths>"),
                BoardDesigns.Item.kv("⌛", "Playtime", "<playtime>"),
                BoardDesigns.Item.blank(),
                BoardDesigns.Item.kv("●", "Online", "<here>")
            )
        );
        m.put(
            "LIFESTEAL",
            List.of(
                BoardDesigns.Item.kv("❤", "Hearts", "<red><hearts>"),
                BoardDesigns.Item.blank(),
                BoardDesigns.Item.kv("⚔", "Kills", "<kills>"),
                BoardDesigns.Item.kv("☠", "Deaths", "<deaths>"),
                BoardDesigns.Item.kv("✦", "K/D", "<kdr>"),
                BoardDesigns.Item.blank(),
                BoardDesigns.Item.kv("●", "Online", "<here>")
            )
        );
        m.put(
            "STRENGTH",
            List.of(
                BoardDesigns.Item.kv("⚡", "Strength", "<light_purple><strength>"),
                BoardDesigns.Item.blank(),
                BoardDesigns.Item.kv("⚔", "Kills", "<kills>"),
                BoardDesigns.Item.kv("☠", "Deaths", "<deaths>"),
                BoardDesigns.Item.kv("✦", "K/D", "<kdr>"),
                BoardDesigns.Item.blank(),
                BoardDesigns.Item.kv("●", "Online", "<here>")
            )
        );
        m.put(
            "RANKED",
            List.of(
                BoardDesigns.Item.kv("★", "Tier", "<tier>"),
                BoardDesigns.Item.kv("⬆", "Points", "<points>"),
                BoardDesigns.Item.blank(),
                BoardDesigns.Item.kv("⚔", "Kills", "<kills>"),
                BoardDesigns.Item.kv("☠", "Deaths", "<deaths>"),
                BoardDesigns.Item.kv("✦", "K/D", "<kdr>"),
                BoardDesigns.Item.blank(),
                BoardDesigns.Item.kv("●", "Online", "<here>")
            )
        );
        m.put(
            "ECO",
            List.of(
                BoardDesigns.Item.kv("$", "Money", "<green>$<balance>"),
                BoardDesigns.Item.blank(),
                BoardDesigns.Item.kv("⚔", "Kills", "<kills>"),
                BoardDesigns.Item.kv("☠", "Deaths", "<deaths>"),
                BoardDesigns.Item.kv("✦", "K/D", "<kdr>"),
                BoardDesigns.Item.blank(),
                BoardDesigns.Item.raw("<gray>/shop /sell /ah"),
                BoardDesigns.Item.kv("●", "Online", "<here>")
            )
        );
        return m;
    }

    private static String name(String mode) {
        return switch (mode) {
            case "PRACTICE" -> "VORTEX REALM";
            case "HUB" -> "VORTEX REALM";
            case "RANKED" -> "RANKED SMP";
            case "STRENGTH" -> "STRENGTH SMP";
            case "ECO" -> "ECO SMP";
            default -> mode;
        };
    }

    private static String small(String s) {
        StringBuilder sb = new StringBuilder();

        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            int i = "abcdefghijklmnopqrstuvwxyz".indexOf(c);
            sb.append(i >= 0 ? "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ".charAt(i) : c);
        }

        return sb.toString();
    }

    private static String spaced(String s) {
        StringBuilder sb = new StringBuilder();

        for (char c : s.toCharArray()) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }

            sb.append(c);
        }

        return sb.toString();
    }

    public static Map<String, BoardDesigns.Board> build(String id) {
        Map<String, BoardDesigns.Board> out = new LinkedHashMap<>();

        for (Entry<String, List<BoardDesigns.Item>> e : content().entrySet()) {
            out.put(e.getKey(), board(id, e.getKey(), e.getValue()));
        }

        return out;
    }

    private static BoardDesigns.Board board(String id, String mode, List<BoardDesigns.Item> items) {
        boolean pvp = mode.equals("PRACTICE");
        boolean right = !pvp && (id.equals("MINIMAL") || id.equals("DONUT"));
        String n = name(mode);
        List<String> lines = new ArrayList<>();
        String title;
        String sep;
        switch (id) {
            case "MINIMAL":
                title = "<white><b>" + n;
                sep = null;
                break;
            case "BOXED":
                title = "<dark_gray>[ <b><mood:" + n + "></b> <dark_gray>]";
                sep = "<dark_gray>┏━━━━━━━━━━━━━━━━";
                break;
            case "MCPVP":
                title = "<gold><b>" + n;
                sep = "<gray><st>                    </st>";
                break;
            case "NEON":
                title = "<b><mood:✦ " + n + " ✦></b>";
                sep = "<dark_gray>· · · · · · · · · · · ·";
                break;
            case "COMPACT":
                title = "<b><mood:" + n + "></b>";
                sep = null;
                break;
            case "ELEGANT":
                title = "<b><mood:" + spaced(n.split(" ")[0]) + "></b>";
                sep = "<dark_gray>  ─────────────";
                break;
            case "DONUT":
                title = "<b><mood:" + n + "></b>";
                sep = null;
                break;
            default:
                title = "<b><mood:" + n + "></b>";
                sep = "<dark_gray><st>                       </st>";
        }

        if (sep != null) {
            lines.add(sep);
        }

        int count = 0;

        for (BoardDesigns.Item it : items) {
            if (!id.equals("COMPACT") || !it.kind().equals("blank")) {
                if (id.equals("COMPACT") && count >= 5) {
                    break;
                }

                count++;
                String var13 = it.kind();

                String line = switch (var13) {
                    case "blank" -> "";
                    case "raw" -> it.label().startsWith("<rank_") ? it.label() : " " + it.label();
                    case "head" -> {
                        switch (id) {
                            case "MCPVP":
                                yield " <gold><b>" + it.label().toUpperCase(Locale.ROOT);
                            case "ELEGANT":
                                yield " <white>" + small(it.label());
                            case "BOXED":
                                yield "<dark_gray>┃ <white><b>" + it.label();
                            default:
                                yield " <mood:<b>" + it.label() + "</b>>";
                        }
                    }
                    default -> kv(id, it, right);
                };
                lines.add(line);
            }
        }

        switch (id) {
            case "MINIMAL":
            case "COMPACT":
            case "DONUT":
                lines.add("");
                lines.add(" <mood:<ip>>");
                break;
            case "BOXED":
                lines.add("<dark_gray>┃");
                lines.add("<dark_gray>┗ <mood:<ip>>");
                break;
            case "MCPVP":
                lines.add("");
                lines.add(" <gold><b><ip>");
                lines.add(sep + " ");
                break;
            case "NEON":
                lines.add(sep + " ");
                lines.add(" <mood:<ip>>");
                break;
            case "ELEGANT":
                lines.add(sep + " ");
                lines.add(" <gray>" + small("play at") + " <mood:<ip>>");
                break;
            default:
                lines.add("");
                lines.add(" <mood:<ip>>");
                lines.add(sep + " ");
        }

        while (lines.size() > 15) {
            lines.remove(lines.size() - 2);
        }

        return new BoardDesigns.Board(title, lines);
    }

    private static String kv(String id, BoardDesigns.Item it, boolean right) {
        String label = it.label();
        String value = it.value();

        return switch (id) {
            case "MINIMAL" -> right ? " <gray>" + label + "||<white>" + value : " <gray>" + label + " <white>" + value;
            case "BOXED" -> "<dark_gray>┃ <gray>" + label + ": <white>" + value;
            case "MCPVP" -> " <yellow>» <gray>" + label + ": <gold>" + value;
            case "NEON" -> " <mood:" + label + "> <white><b>" + value;
            case "COMPACT" -> " <gray>" + label + " <white>" + value;
            case "ELEGANT" -> " <dark_gray>• <gray>" + small(label) + " <white>" + value;
            case "DONUT" -> right
                ? " <mood:" + it.icon() + "> <white>" + label + "||<aqua>" + value
                : " <mood:" + it.icon() + "> <white>" + label + " <aqua>" + value;
            default -> " <gray>" + label + ": <white>" + value;
        };
    }

    public record Board(String title, List<String> lines) {
    }

    public record Design(String id, String name, String description) {
    }

    private record Item(String kind, String label, String value, String icon) {
        static BoardDesigns.Item kv(String icon, String label, String value) {
            return new BoardDesigns.Item("kv", label, value, icon);
        }

        static BoardDesigns.Item blank() {
            return new BoardDesigns.Item("blank", "", "", "");
        }

        static BoardDesigns.Item raw(String text) {
            return new BoardDesigns.Item("raw", text, "", "");
        }

        static BoardDesigns.Item head(String text) {
            return new BoardDesigns.Item("head", text, "", "");
        }
    }
}
