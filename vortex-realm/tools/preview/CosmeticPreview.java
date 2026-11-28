import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.practicepvp.build.Voxels;
import net.practicepvp.cosmetics.CosmeticModels;
import net.practicepvp.cosmetics.CosmeticRig;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Renders every cosmetic model (pets, shoulder pets, wings) at 1/16-block resolution with the arena renderer, to check
 * shapes and colours without a server: one picture per model plus a sheet. Pets are shown standing and mid-roll.
 *
 * java -cp practicepvp/target/classes:<paper-api + joml jars>:/tmp/prev CosmeticPreview out/dir
 */
public final class CosmeticPreview {
    /** A fine grid: one voxel = one pixel (1/16 block). */
    static final class Fine extends Voxels {
        Fine() {
            super(0, 0, 0, 40, -4, 60, 1L);
        }

        void put(int x, int y, int z, String b) {
            this.set(x, y, z, b);
        }

        @Override
        public String name() {
            return "fine";
        }
    }

    static Fine raster(CosmeticModels.Model m, CosmeticRig.Pose pose) {
        Fine f = new Fine();
        for (CosmeticModels.Part p : m.parts()) {
            Matrix4f mat = CosmeticRig.matrix(m, p, pose);
            Matrix4f px = new Matrix4f().scale(16f).mul(mat);
            Matrix4f inv = new Matrix4f(px).invert();
            float minX = 1e9f, minY = 1e9f, minZ = 1e9f, maxX = -1e9f, maxY = -1e9f, maxZ = -1e9f;
            for (int c = 0; c < 8; c++) {
                Vector3f v = px.transformPosition(new Vector3f(c & 1, c >> 1 & 1, c >> 2 & 1));
                minX = Math.min(minX, v.x); minY = Math.min(minY, v.y); minZ = Math.min(minZ, v.z);
                maxX = Math.max(maxX, v.x); maxY = Math.max(maxY, v.y); maxZ = Math.max(maxZ, v.z);
            }

            // thin boxes (feathers half a pixel thick) still fill the voxels they pass through
            float mx = 0.75f / Math.max(1e-3f, new Vector3f(px.m00(), px.m01(), px.m02()).length());
            float my = 0.75f / Math.max(1e-3f, new Vector3f(px.m10(), px.m11(), px.m12()).length());
            float mz = 0.75f / Math.max(1e-3f, new Vector3f(px.m20(), px.m21(), px.m22()).length());
            for (int x = (int)Math.floor(minX); x <= Math.ceil(maxX); x++) {
                for (int y = (int)Math.floor(minY); y <= Math.ceil(maxY); y++) {
                    for (int z = (int)Math.floor(minZ); z <= Math.ceil(maxZ); z++) {
                        Vector3f u = inv.transformPosition(new Vector3f(x + 0.5f, y + 0.5f, z + 0.5f));
                        if (u.x >= -Math.min(mx, 0.5f) * 0.5f && u.x <= 1 + Math.min(mx, 0.5f) * 0.5f && u.y >= -Math.min(my, 0.5f) * 0.5f
                            && u.y <= 1 + Math.min(my, 0.5f) * 0.5f && u.z >= -mz * 0.5f && u.z <= 1 + mz * 0.5f) {
                            f.put(x, y, z, p.block());
                        }
                    }
                }
            }
        }

        return f;
    }

    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        out.mkdirs();
        List<BufferedImage> tiles = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (CosmeticModels.Model m : CosmeticModels.all().values()) {
            CosmeticRig.Pose pose = new CosmeticRig.Pose();
            pose.yaw = 0;
            if (m.kind() == CosmeticModels.Kind.SHOULDER) {
                pose.scale = 1.0f / m.scale();
            }

            if (m.kind() == CosmeticModels.Kind.WINGS) {
                pose.group("wingL", CosmeticRig.rotY(26));
                pose.group("wingR", CosmeticRig.rotY(-26));
            }

            ArenaPreview pv = new ArenaPreview(raster(m, pose));
            BufferedImage img;
            if (m.kind() == CosmeticModels.Kind.WINGS) {
                // from behind, a little to the side
                img = pv.render(16, 12, -50, 17, 8, 50, 480, 360);
            } else {
                img = pv.render(26, 22, 34, 142, 18, 46, 480, 360);
            }

            ImageIO.write(img, "png", new File(out, m.id() + ".png"));
            tiles.add(img);
            names.add(m.name());
            if (m.fat()) {
                pose.roll = (float)Math.toRadians(100);
                BufferedImage r = new ArenaPreview(raster(m, pose)).render(32, 20, 4, 100, 12, 50, 480, 360);
                ImageIO.write(r, "png", new File(out, m.id() + "-roll.png"));
            }
        }

        int cols = 4;
        int rows = (tiles.size() + cols - 1) / cols;
        BufferedImage sheet = new BufferedImage(cols * 320, rows * 260, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 18));
        for (int i = 0; i < tiles.size(); i++) {
            int x = i % cols * 320;
            int y = i / cols * 260;
            g.drawImage(tiles.get(i), x, y, 320, 240, null);
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(x, y + 240, 320, 20);
            g.setColor(java.awt.Color.BLACK);
            g.drawString(names.get(i), x + 8, y + 256);
        }

        ImageIO.write(sheet, "png", new File(out, "cosmetics-sheet.png"));
        System.out.println(tiles.size() + " models rendered");
    }
}
