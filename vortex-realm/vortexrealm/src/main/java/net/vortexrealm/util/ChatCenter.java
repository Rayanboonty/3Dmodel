package net.vortexrealm.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.format.TextDecoration.State;

public final class ChatCenter {
    private static final int CENTER_PX = 154;

    private ChatCenter() {
    }

    public static Component center(Component line) {
        int width = width(line, false);
        int toCompensate = 154 - width / 2;
        if (toCompensate <= 0) {
            return line;
        } else {
            int spaces = toCompensate / 4;
            return Component.text(" ".repeat(spaces)).append(line);
        }
    }

    private static int width(Component c, boolean parentBold) {
        State st = c.decoration(TextDecoration.BOLD);
        boolean bold = st == State.TRUE || st == State.NOT_SET && parentBold;
        int w = 0;
        if (c instanceof TextComponent tc) {
            for (char ch : tc.content().toCharArray()) {
                w += charWidth(ch) + (bold && ch != ' ' ? 1 : 0);
            }
        } else {
            w += 9;
        }

        for (Component child : c.children()) {
            w += width(child, bold);
        }

        return w;
    }

    private static int charWidth(char c) {
        switch (c) {
            case ' ':
            case '"':
            case '(':
            case ')':
            case '*':
            case 'I':
            case '[':
            case ']':
            case 't':
            case '{':
            case '}':
                return 4;
            case '!':
            case '\'':
            case ',':
            case '.':
            case ':':
            case ';':
            case 'i':
            case '|':
                return 2;
            case '#':
            case '$':
            case '%':
            case '&':
            case '+':
            case '-':
            case '/':
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
            case '=':
            case '?':
            case 'A':
            case 'B':
            case 'C':
            case 'D':
            case 'E':
            case 'F':
            case 'G':
            case 'H':
            case 'J':
            case 'K':
            case 'L':
            case 'M':
            case 'N':
            case 'O':
            case 'P':
            case 'Q':
            case 'R':
            case 'S':
            case 'T':
            case 'U':
            case 'V':
            case 'W':
            case 'X':
            case 'Y':
            case 'Z':
            case '\\':
            case '^':
            case '_':
            case 'a':
            case 'b':
            case 'c':
            case 'd':
            case 'e':
            case 'g':
            case 'h':
            case 'j':
            case 'm':
            case 'n':
            case 'o':
            case 'p':
            case 'q':
            case 'r':
            case 's':
            case 'u':
            case 'v':
            case 'w':
            case 'x':
            case 'y':
            case 'z':
            default:
                if (c < 128) {
                    return 6;
                }

                return 9;
            case '<':
            case '>':
            case 'f':
            case 'k':
                return 5;
            case '@':
            case '~':
                return 7;
            case '`':
            case 'l':
                return 3;
        }
    }
}
