import java.io.File;
import javax.imageio.ImageIO;
import net.practicepvp.arena.ColosseumDesign;
import net.practicepvp.build.Voxels;

/**
 * Renders a colosseum arena (the mace and tournament maps are built column by column, not as voxels): the columns
 * are poured into a voxel grid, then drawn like the other previews.
 * <pre>java -cp practicepvp/target/classes:/tmp/prev ColosseumPreview GRAND 400 out/dir</pre>
 */
public final class ColosseumPreview extends Voxels {
    private ColosseumPreview(ColosseumDesign d) {
        super(d.cx, d.floorY, d.cz, Math.max(d.halfX, d.halfZ), d.bottomY - d.floorY, d.topY - d.floorY, 1L);
        for (int x = d.minX(); x <= d.maxX(); x++) {
            for (int z = d.minZ(); z <= d.maxZ(); z++) {
                d.column(x, z, (bx, by, bz, b) -> this.set(bx - d.cx, by - d.floorY, bz - d.cz, b.startsWith("minecraft:") ? b.substring(10) : b));
            }
        }
    }

    @Override
    public String name() {
        return "colosseum";
    }

    public static void main(String[] args) throws Exception {
        ColosseumDesign.Style style = ColosseumDesign.Style.valueOf(args[0]);
        int size = Integer.parseInt(args[1]);
        File out = new File(args[2]);
        out.mkdirs();
        ColosseumDesign d = new ColosseumDesign(style, size, 1234L, 0, 64, 0, 12, true, false);
        long t0 = System.currentTimeMillis();
        ColosseumPreview v = new ColosseumPreview(d);
        ArenaPreview p = new ArenaPreview(v);
        int W = 1280, H = 720;
        String name = "colosseum_" + style.name().toLowerCase();
        int fy = d.floorY;
        int sp = d.spawnOffset();
        double r = Math.max(d.halfX, d.halfZ) * 2.0;
        ImageIO.write(p.render(r * 0.55, fy + r * 0.42, r * 0.62, 138, 30, 62, W, H), "png", new File(out, name + "-air.png"));
        ImageIO.write(p.render(sp + 2, d.surface(sp, 0) + 2.6, 0, 90, -8, 80, W, H), "png", new File(out, name + "-floor.png"));
        ImageIO.write(p.render(0, fy + 2.6, d.radius * 0.5, 180, -12, 85, W, H), "png", new File(out, name + "-north.png"));
        ImageIO.write(p.render(d.straight * 0.4, fy + 3.5, -d.radius * 0.62, 180, -14, 70, W, H), "png", new File(out, name + "-close.png"));
        ImageIO.write(p.render(d.straight * 0.4 + 6, fy + 4.5, d.radius * 0.7, 20, -10, 75, W, H), "png", new File(out, name + "-close2.png"));
        ImageIO.write(p.render(-1, fy + 10, d.radius * 0.55, 0, 12, 60, W, H), "png", new File(out, name + "-vom.png"));
        System.out.println(name + ": " + (d.maxX() - d.minX() + 1) + "x" + (d.maxZ() - d.minZ() + 1) + ", ring " + d.ringOut + ", " + (System.currentTimeMillis() - t0) + " ms");
    }
}
