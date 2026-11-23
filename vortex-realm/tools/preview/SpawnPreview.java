import java.io.File;
import javax.imageio.ImageIO;
import net.practicepvp.build.Voxels;

/**
 * Renders a spawn / hub design (any Voxels with a (cx, floorY, cz, radius, seed) or (cx, floorY, cz, seed)
 * constructor) to PNG: a high 3/4 view, a low view, a view from the spawn point and a top-down map.
 * <pre>java -cp practicepvp/target/classes:/tmp/prev SpawnPreview net.practicepvp.build.VortexIslesDesign 90 out/dir</pre>
 */
public final class SpawnPreview {
    public static void main(String[] args) throws Exception {
        Class<?> c = Class.forName(args[0]);
        int radius = Integer.parseInt(args[1]);
        File out = new File(args[2]);
        out.mkdirs();
        long seed = args.length > 3 ? Long.parseLong(args[3]) : 7L;
        Voxels d;
        try {
            d = (Voxels)c.getConstructor(int.class, int.class, int.class, int.class, long.class).newInstance(0, 100, 0, radius, seed);
        } catch (NoSuchMethodException e) {
            d = (Voxels)c.getConstructor(int.class, int.class, int.class, long.class).newInstance(0, 100, 0, seed);
        }

        long t0 = System.currentTimeMillis();
        ArenaPreview p = new ArenaPreview(d);
        String name = c.getSimpleName().toLowerCase();
        int W = 1280, H = 720;
        double r = (d.maxX() - d.minX()) / 2.0;
        int fy = d.floorYAt();
        double[] sp = d.spawn();
        ImageIO.write(p.render(r * 1.05, fy + r * 0.75, r * 1.15, 137, 32, 60, W, H), "png", new File(out, name + "-air.png"));
        ImageIO.write(p.render(-r * 1.1, fy + r * 0.45, -r * 0.9, -45, 20, 60, W, H), "png", new File(out, name + "-air2.png"));
        // spawn() is in world coordinates already
        ImageIO.write(p.render(sp[0], sp[1] + 1.6, sp[2], sp[3], -4, 85, W, H), "png", new File(out, name + "-spawn.png"));
        ImageIO.write(p.render(0, fy + r * 2.2, 0.01, 180, 89.5, 55, W, H), "png", new File(out, name + "-top.png"));
        // extra views: name=x,y,z,yaw,pitch[,fov] with y relative to the floor
        for (int i = 4; i < args.length; i++) {
            String[] kv = args[i].split("=", 2);
            String[] v = kv[1].split(",");
            double fov = v.length > 5 ? Double.parseDouble(v[5]) : 75;
            ImageIO.write(p.render(Double.parseDouble(v[0]), fy + Double.parseDouble(v[1]), Double.parseDouble(v[2]),
                Double.parseDouble(v[3]), Double.parseDouble(v[4]), fov, W, H), "png", new File(out, name + "-" + kv[0] + ".png"));
        }

        System.out.println(name + ": " + (d.maxX() - d.minX() + 1) + " wide, " + (System.currentTimeMillis() - t0) + " ms");
    }
}
