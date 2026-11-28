import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import net.practicepvp.build.Voxels;

/**
 * Writes the plugin's designs as Sponge schematics (version 3, ".schem") that WorldEdit and FastAsyncWorldEdit
 * paste with //schem load + //paste. Usage: java -cp target/classes:. SchemExport out-dir
 */
public final class SchemExport {
    /** Minecraft 1.21.11. */
    static final int DATA_VERSION = 4671;

    public static void main(String[] a) throws Exception {
        File out = new File(a.length > 0 ? a[0] : "schematics");
        out.mkdirs();
        String[][] arcade = {{"SUMO", "sumo-dojo"}, {"BOXING", "boxing-ring"}, {"BRIDGE", "the-bridge"}, {"SPLEEF", "spleef-stadium"}, {"OITC", "oitc-courtyard"}, {"KOTH", "koth-temple"}};
        int i = 0;
        for (String[] m : arcade) {
            write(new net.practicepvp.build.ArcadeArenaDesign(m[0], 0, 0, 0, 1813L + i++), new File(out, m[1] + ".schem"));
        }

        write(new net.practicepvp.build.LegacyHallDesign(0, 0, 0, 1813L), new File(out, "legacy-hall.schem"));
        Class<?> st = Class.forName("net.practicepvp.build.CourtArenaDesign$Style");
        for (String style : new String[]{"ROYAL_COURTYARD", "TROPICAL_BEACH", "PHARAOH_TOMB"}) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object s = Enum.valueOf((Class)st, style);
            Voxels d = (Voxels)Class.forName("net.practicepvp.build.CourtArenaDesign")
                .getConstructor(st, int.class, long.class, int.class, int.class, int.class, int.class, boolean.class, boolean.class)
                .newInstance(s, 96, 1234L, 0, 0, 0, 4, false, true);
            write(d, new File(out, "duel-" + style.toLowerCase().replace('_', '-') + ".schem"));
        }
    }

    static void write(Voxels d, File f) throws IOException {
        int[] lo = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] hi = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int x = d.minX(); x <= d.maxX(); x++) {
            for (int z = d.minZ(); z <= d.maxZ(); z++) {
                d.column(x, z, (bx, by, bz, b) -> {
                    if (!b.equals("minecraft:air")) {
                        lo[0] = Math.min(lo[0], bx); lo[1] = Math.min(lo[1], by); lo[2] = Math.min(lo[2], bz);
                        hi[0] = Math.max(hi[0], bx); hi[1] = Math.max(hi[1], by); hi[2] = Math.max(hi[2], bz);
                    }
                });
            }
        }

        int w = hi[0] - lo[0] + 1, h = hi[1] - lo[1] + 1, l = hi[2] - lo[2] + 1;
        int[] cells = new int[w * h * l];
        Map<String, Integer> palette = new LinkedHashMap<>();
        palette.put("minecraft:air", 0);
        for (int x = lo[0]; x <= hi[0]; x++) {
            for (int z = lo[2]; z <= hi[2]; z++) {
                final int fx = x, fz = z;
                d.column(x, z, (bx, by, bz, b) -> {
                    if (by < lo[1] || by > hi[1]) {
                        return;
                    }

                    int id = palette.computeIfAbsent(b, k -> palette.size());
                    cells[(fx - lo[0]) + (fz - lo[2]) * w + (by - lo[1]) * w * l] = id;
                });
            }
        }

        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (int v : cells) {
            while ((v & ~0x7F) != 0) {
                data.write((v & 0x7F) | 0x80);
                v >>>= 7;
            }

            data.write(v);
        }

        try (DataOutputStream o = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(f)))) {
            o.writeByte(10);
            str(o, "");
            o.writeByte(10);
            str(o, "Schematic");
            intTag(o, "Version", 3);
            intTag(o, "DataVersion", DATA_VERSION);
            o.writeByte(2); str(o, "Width"); o.writeShort(w);
            o.writeByte(2); str(o, "Height"); o.writeShort(h);
            o.writeByte(2); str(o, "Length"); o.writeShort(l);
            // the design's middle at floor level is the paste origin
            o.writeByte(11); str(o, "Offset"); o.writeInt(3); o.writeInt(lo[0] - d.floorYAt() * 0); o.writeInt(lo[1] - d.floorYAt()); o.writeInt(lo[2]);
            o.writeByte(10); str(o, "Metadata");
            o.writeByte(8); str(o, "Name"); str(o, d.name());
            o.writeByte(8); str(o, "Author"); str(o, "VortexRealm");
            o.writeByte(0);
            o.writeByte(10); str(o, "Blocks");
            o.writeByte(10); str(o, "Palette");
            for (Map.Entry<String, Integer> e : palette.entrySet()) {
                intTag(o, e.getKey(), e.getValue());
            }

            o.writeByte(0);
            byte[] bytes = data.toByteArray();
            o.writeByte(7); str(o, "Data"); o.writeInt(bytes.length); o.write(bytes);
            o.writeByte(9); str(o, "BlockEntities"); o.writeByte(10); o.writeInt(0);
            o.writeByte(0);
            o.writeByte(0);
            o.writeByte(0);
        }

        System.out.printf("%-34s %3d x %3d x %3d, %d block states%n", f.getName(), w, h, l, palette.size());
    }

    static void str(DataOutputStream o, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        o.writeShort(b.length);
        o.write(b);
    }

    static void intTag(DataOutputStream o, String name, int v) throws IOException {
        o.writeByte(3);
        str(o, name);
        o.writeInt(v);
    }
}
