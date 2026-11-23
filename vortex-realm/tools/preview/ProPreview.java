import java.io.File;
import javax.imageio.ImageIO;
import net.practicepvp.build.ProArenaDesign;

/**
 * Renders a pro arena (3.15.0) with its landscape: a high 3/4 view, a view from each spawn (toward the signboard and
 * toward the statue), a view of a long side, a close view of the stands and any extra views.
 * <pre>java -Dpro.arena.name="C MCPvP" -cp practicepvp/target/classes:/tmp/prev ProPreview ROMAN 224 out/dir [name=x,y,z,yaw,pitch[,fov]...]</pre>
 */
public final class ProPreview {
    public static void main(String[] args) throws Exception {
        ProArenaDesign.Theme t = ProArenaDesign.Theme.valueOf(args[0]);
        int size = Integer.parseInt(args[1]);
        File out = new File(args[2]);
        out.mkdirs();
        long t0 = System.currentTimeMillis();
        boolean named = Boolean.getBoolean("pro.named");
        ProArenaDesign d = new ProArenaDesign(t, size, 1234L, 0, 64, 0, 12, false, true, named);
        long t1 = System.currentTimeMillis();
        ArenaPreview p = new ArenaPreview(d);
        String name = "pro_" + t.name().toLowerCase();
        int W = 1280;
        int H = 720;
        int fy = 64;
        int sp = d.spawn(1)[0];
        double r = (d.maxX() - d.minX()) / 2.0;
        int hzf = 0;
        while (ProArenaDesign.floorDistance(t, size, 0, hzf + 1) < 0) {
            hzf++;
        }

        int hxf = 0;
        while (ProArenaDesign.floorDistance(t, size, hxf + 1, 0) < 0) {
            hxf++;
        }

        boolean all = args.length <= 3 || !args[3].equals("only");
        if (all) {
            double ax = r * 0.62;
            double ay = Math.max(r * 0.62, 110.0);
            double az = r * 0.72;
            double pitch = Math.toDegrees(Math.atan2(ay, Math.hypot(ax, az)));
            ImageIO.write(p.render(ax, fy + ay, az, 139, pitch, 62, W, H), "png", new File(out, name + "-air.png"));
            ImageIO.write(p.render(-r * 0.55, fy + ay * 0.8, -r * 0.2, -70, Math.toDegrees(Math.atan2(ay * 0.8, r * 0.58)), 70, W, H), "png", new File(out, name + "-air2.png"));
            ImageIO.write(p.render(-sp, fy + 2.6, 0, -90, -7, 85, W, H), "png", new File(out, name + "-floor.png"));
            ImageIO.write(p.render(sp, fy + 2.6, 0, 90, -7, 85, W, H), "png", new File(out, name + "-floor2.png"));
            ImageIO.write(p.render(hxf * 0.2, fy + 2.6, -hzf * 0.35, 0, -12, 85, W, H), "png", new File(out, name + "-side.png"));
            ImageIO.write(p.render(hxf * 0.25, fy + 3.0, hzf - 9, 20, -22, 70, W, H), "png", new File(out, name + "-close.png"));
        }

        for (int i = 3; i < args.length; i++) {
            if (!args[i].contains("=")) {
                continue;
            }

            String[] kv = args[i].split("=", 2);
            String[] v = kv[1].split(",");
            double fov = v.length > 5 ? Double.parseDouble(v[5]) : 75;
            ImageIO.write(p.render(Double.parseDouble(v[0]), fy + Double.parseDouble(v[1]), Double.parseDouble(v[2]),
                Double.parseDouble(v[3]), Double.parseDouble(v[4]), fov, W, H), "png", new File(out, name + "-" + kv[0] + ".png"));
        }

        System.out.println(name + ": " + (d.maxX() - d.minX() + 1) + " wide, floor " + (2 * hxf + 1) + "x" + (2 * hzf + 1) + ", spawn " + sp
            + ", build " + (t1 - t0) + " ms, total " + (System.currentTimeMillis() - t0) + " ms");
    }
}
