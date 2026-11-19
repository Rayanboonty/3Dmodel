package net.practicepvp.build;

import java.util.Map;

/** A box cut out of a bigger design (3.13.0: to stamp only the Legacy Hall part of the Club Spawn). */
public final class VoxelsRegion extends Voxels {
    private final String name;

    public VoxelsRegion(Voxels src, int x0, int z0, int x1, int z1, String name) {
        super((x0 + x1) / 2, src.floorYAt(), (z0 + z1) / 2, Math.max(x1 - x0, z1 - z0) / 2 + 1, src.bottomY() - src.floorYAt(), src.topY() - src.floorYAt(), 0L);
        this.name = name;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                src.column(x, z, (bx, by, bz, b) -> this.set(bx - this.cx, by - this.floorY, bz - this.cz, b.substring(10)));
            }
        }

        for (Map.Entry<String, double[]> f : src.features().entrySet()) {
            double[] v = f.getValue();
            if (v[0] >= x0 && v[0] <= x1 + 1 && v[2] >= z0 && v[2] <= z1 + 1) {
                this.features.put(f.getKey(), new double[]{v[0] - this.cx, v[1] - this.floorY, v[2] - this.cz, v[3]});
            }
        }
    }

    @Override
    public String name() {
        return this.name;
    }
}
