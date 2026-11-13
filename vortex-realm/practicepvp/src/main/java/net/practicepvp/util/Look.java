package net.practicepvp.util;

import io.papermc.paper.entity.LookAnchor;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

public final class Look {
    private static boolean native_ = true;

    private Look() {
    }

    public static void at(LivingEntity e, Location target) {
        if (native_) {
            try {
                e.lookAt(target.getX(), target.getY(), target.getZ(), LookAnchor.EYES);
                return;
            } catch (NoClassDefFoundError | UnsupportedOperationException | NoSuchMethodError var13) {
                native_ = false;
            }
        }

        Location eye = e.getEyeLocation();
        double dx = target.getX() - eye.getX();
        double dy = target.getY() - eye.getY();
        double dz = target.getZ() - eye.getZ();
        float yaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        e.setRotation(yaw, pitch);

        try {
            e.setBodyYaw(yaw);
        } catch (NoSuchMethodError var12) {
        }
    }
}
