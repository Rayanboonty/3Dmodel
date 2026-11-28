import java.io.File;
import javax.imageio.ImageIO;
import net.practicepvp.build.MasterArenaDesign;

/**
 * Renders a masterpiece arena: two views from above and the views a player has from each spawn and from the side.
 * <pre>java -cp ... MasterPreview DRAGONSPIRE out/dir</pre>
 */
public final class MasterPreview {
    public static void main(String[] args) throws Exception {
        MasterArenaDesign.Style st = MasterArenaDesign.Style.valueOf(args[0]);
        File out = new File(args[1]);
        out.mkdirs();
        long t0 = System.currentTimeMillis();
        MasterArenaDesign d = new MasterArenaDesign(st, 96, 1234L, 0, 64, 0, false);
        long t1 = System.currentTimeMillis();
        ArenaPreview p = new ArenaPreview(d);
        int fy = 64;
        int W = 1280;
        int H = 720;
        String name = st.name().toLowerCase();
        double r = MasterArenaDesign.RADIUS;
        ImageIO.write(p.render(r * 0.62, fy + r * 0.62, r * 0.78, 141, 30, 62, W, H), "png", new File(out, name + "-air.png"));
        ImageIO.write(p.render(-r * 0.7, fy + r * 0.45, -r * 0.55, -52, 22, 62, W, H), "png", new File(out, name + "-air2.png"));
        int[] s0 = d.spawn(0);
        ImageIO.write(p.render(s0[0], fy + 2.6, 0, -90, -4, 85, W, H), "png", new File(out, name + "-spawn0.png"));
        ImageIO.write(p.render(-s0[0], fy + 2.6, 0, 90, -4, 85, W, H), "png", new File(out, name + "-spawn1.png"));
        ImageIO.write(p.render(0, fy + 2.6, 16, 180, -2, 85, W, H), "png", new File(out, name + "-north.png"));
        ImageIO.write(p.render(0, fy + 2.6, -16, 0, -2, 85, W, H), "png", new File(out, name + "-south.png"));
        System.out.println(name + ": build " + (t1 - t0) + " ms, total " + (System.currentTimeMillis() - t0) + " ms");
    }
}
