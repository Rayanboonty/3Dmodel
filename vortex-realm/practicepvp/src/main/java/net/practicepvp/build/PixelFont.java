package net.practicepvp.build;

import java.util.Locale;
import java.util.Map;

/** A 5 by 7 pixel font for giant lettering in builds: the server's name on cloths and signboards. */
final class PixelFont {
    private static final Map<Character, String[]> GLYPHS = Map.ofEntries(
        Map.entry('A', new String[]{" ### ", "#   #", "#   #", "#####", "#   #", "#   #", "#   #"}),
        Map.entry('B', new String[]{"#### ", "#   #", "#   #", "#### ", "#   #", "#   #", "#### "}),
        Map.entry('C', new String[]{" ####", "#    ", "#    ", "#    ", "#    ", "#    ", " ####"}),
        Map.entry('D', new String[]{"#### ", "#   #", "#   #", "#   #", "#   #", "#   #", "#### "}),
        Map.entry('E', new String[]{"#####", "#    ", "#    ", "#### ", "#    ", "#    ", "#####"}),
        Map.entry('F', new String[]{"#####", "#    ", "#    ", "#### ", "#    ", "#    ", "#    "}),
        Map.entry('G', new String[]{" ####", "#    ", "#    ", "#  ##", "#   #", "#   #", " ####"}),
        Map.entry('H', new String[]{"#   #", "#   #", "#   #", "#####", "#   #", "#   #", "#   #"}),
        Map.entry('I', new String[]{"#####", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", "#####"}),
        Map.entry('J', new String[]{"  ###", "   # ", "   # ", "   # ", "   # ", "#  # ", " ##  "}),
        Map.entry('K', new String[]{"#   #", "#  # ", "# #  ", "##   ", "# #  ", "#  # ", "#   #"}),
        Map.entry('L', new String[]{"#    ", "#    ", "#    ", "#    ", "#    ", "#    ", "#####"}),
        Map.entry('M', new String[]{"#   #", "## ##", "# # #", "# # #", "#   #", "#   #", "#   #"}),
        Map.entry('N', new String[]{"#   #", "##  #", "# # #", "#  ##", "#   #", "#   #", "#   #"}),
        Map.entry('O', new String[]{" ### ", "#   #", "#   #", "#   #", "#   #", "#   #", " ### "}),
        Map.entry('P', new String[]{"#### ", "#   #", "#   #", "#### ", "#    ", "#    ", "#    "}),
        Map.entry('Q', new String[]{" ### ", "#   #", "#   #", "#   #", "# # #", "#  # ", " ## #"}),
        Map.entry('R', new String[]{"#### ", "#   #", "#   #", "#### ", "# #  ", "#  # ", "#   #"}),
        Map.entry('S', new String[]{" ####", "#    ", "#    ", " ### ", "    #", "    #", "#### "}),
        Map.entry('T', new String[]{"#####", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", "  #  "}),
        Map.entry('U', new String[]{"#   #", "#   #", "#   #", "#   #", "#   #", "#   #", " ### "}),
        Map.entry('V', new String[]{"#   #", "#   #", "#   #", " # # ", " # # ", " # # ", "  #  "}),
        Map.entry('W', new String[]{"#   #", "#   #", "#   #", "# # #", "# # #", "## ##", "#   #"}),
        Map.entry('X', new String[]{"#   #", "#   #", " # # ", "  #  ", " # # ", "#   #", "#   #"}),
        Map.entry('Y', new String[]{"#   #", "#   #", " # # ", "  #  ", "  #  ", "  #  ", "  #  "}),
        Map.entry('Z', new String[]{"#####", "    #", "   # ", "  #  ", " #   ", "#    ", "#####"})
    );

    /** Letter height in pixels. */
    static final int ROWS = 7;

    private PixelFont() {
    }

    /** Is pixel (col 0-4, row 0-6 from the top) of this letter set? Spaces and unknown characters are empty. */
    static boolean on(char c, int col, int row) {
        String[] g = GLYPHS.get(c);
        return g != null && col >= 0 && col < 5 && row >= 0 && row < ROWS && g[row].charAt(col) == '#';
    }

    /** Width in pixels of a text: 5 per letter and 1 between letters. */
    static int width(String text) {
        return text.isEmpty() ? 0 : text.length() * 6 - 1;
    }

    /** Is pixel (col, row) of a whole text set? */
    static boolean textOn(String text, int col, int row) {
        if (col < 0) {
            return false;
        }

        int ci = col / 6;
        return ci < text.length() && on(text.charAt(ci), col % 6, row);
    }

    /** The server's name for builds (a system property can stand in for it in the preview tools). */
    static String serverName() {
        String n = System.getProperty("pro.arena.name");
        return n != null ? n : net.practicepvp.Edition.NAME;
    }

    /**
     * A name in capitals (letters and spaces only) that fits in {@code maxPixels}: the whole name, else its first word,
     * else nothing.
     */
    static String fit(String name, int maxPixels) {
        String n = name == null ? "" : name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z ]", "").trim().replaceAll(" +", " ");
        if (width(n) <= maxPixels) {
            return n;
        }

        int sp = n.indexOf(' ');
        String first = sp > 0 ? n.substring(0, sp) : n;
        return first.length() > 1 && width(first) <= maxPixels ? first : "";
    }
}
