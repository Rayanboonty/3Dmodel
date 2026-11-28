package net.vortexpvp.build;

/** A voxel arena design: where the two duel spawns and the FFA spawns are (offsets from the centre, y from the floor). */
public interface ArenaVoxels {
    /** {x, y, z} of spawn 0 or 1, relative to the design centre and floor. */
    int[] spawn(int side);

    /** {x, y, z} of FFA spawn i of n, relative. */
    int[] ffaSpot(int i, int n);
}
