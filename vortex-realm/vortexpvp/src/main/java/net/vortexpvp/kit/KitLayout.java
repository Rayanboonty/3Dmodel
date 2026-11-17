package net.vortexpvp.kit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class KitLayout {
    public final int version;
    public final List<KitLayout.Entry> entries;

    public KitLayout(int version, List<KitLayout.Entry> entries) {
        this.version = version;
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();

        for (KitLayout.Entry e : this.entries) {
            if (!sb.isEmpty()) {
                sb.append(',');
            }

            sb.append(e.orig).append(':').append(e.slot).append(':').append(e.amount);
        }

        return sb.toString();
    }

    public static KitLayout parse(int version, String data) {
        List<KitLayout.Entry> list = new ArrayList<>();
        if (data != null && !data.isBlank()) {
            for (String part : data.split(",")) {
                String[] p = part.split(":");
                if (p.length != 3) {
                    return null;
                }

                try {
                    list.add(new KitLayout.Entry(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])));
                } catch (NumberFormatException var9) {
                    return null;
                }
            }
        }

        return new KitLayout(version, list);
    }

    public record Entry(int orig, int slot, int amount) {
    }
}
