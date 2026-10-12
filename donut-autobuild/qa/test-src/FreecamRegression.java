package com.donutautobuild;

import com.donutautobuild.build.FreecamMotion;

/** Independent examples of Minecraft yaw and elapsed frame time expected by the camera. */
public final class FreecamRegression {
    private static int checks;
    private static void close(double actual, double expected, String message) {
        checks++;
        if (Math.abs(actual - expected) > 1.0e-6)
            throw new AssertionError(message + ": " + actual + " != " + expected);
    }
    private static double length(double[] value) {
        return Math.sqrt(value[0] * value[0] + value[1] * value[1] + value[2] * value[2]);
    }
    public static void main(String[] args) throws Exception { run(); }
    public static int run() throws Exception {
        checks = 0;
        double[] south = FreecamMotion.delta(0.0F, 1, 0, 0, 12, 0.05);
        close(south[0], 0, "yaw 0 forward X");
        close(south[2], 0.6, "yaw 0 faces south");
        double[] west = FreecamMotion.delta(90.0F, 1, 0, 0, 12, 0.05);
        close(west[0], -0.6, "yaw 90 faces west");
        close(west[2], 0, "yaw 90 forward Z");
        double[] strafe = FreecamMotion.delta(0.0F, 0, 1, 0, 12, 0.05);
        close(strafe[0], -0.6, "facing south, D goes west (screen right)");
        // Check against Minecraft's actual camera rotation rather than repeating our own formula.
        net.minecraft.client.render.Camera camera = new net.minecraft.client.render.Camera();
        var rotation = camera.getClass().getDeclaredMethod("setRotation", float.class, float.class);
        rotation.setAccessible(true);
        for (int yaw = -180; yaw <= 180; yaw += 15) {
            rotation.invoke(camera, (float)yaw, 0F);
            var screenRight = new org.joml.Vector3f(1, 0, 0).rotate(camera.getRotation()).mul(0.6F);
            double[] d = FreecamMotion.delta(yaw, 0, 1, 0, 12, 0.05);
            double[] a = FreecamMotion.delta(yaw, 0, -1, 0, 12, 0.05);
            close(d[0], screenRight.x, "D follows camera screen right X at yaw " + yaw);
            close(d[2], screenRight.z, "D follows camera screen right Z at yaw " + yaw);
            close(a[0], -screenRight.x, "A follows camera screen left X at yaw " + yaw);
            close(a[2], -screenRight.z, "A follows camera screen left Z at yaw " + yaw);
        }
        double[] up = FreecamMotion.delta(0.0F, 0, 0, 1, 12, 0.05);
        close(up[1], 0.6, "Space rises");
        double[] down = FreecamMotion.delta(0.0F, 0, 0, -1, 12, 0.05);
        close(down[1], -0.6, "Shift lowers only camera");
        close(length(FreecamMotion.delta(35.0F, 1, 1, 1, 12, 0.05)), 0.6,
            "three-axis diagonal cannot increase flight speed");
        close(length(FreecamMotion.delta(0.0F, 0, 0, 0, 12, 1)), 0,
            "no controls means no camera movement");
        close(length(FreecamMotion.delta(0.0F, 1, 0, 0, 12, 9)), 1.2,
            "long paused frame cannot teleport camera");
        close(length(FreecamMotion.delta(0.0F, 1, 0, 0, 12, -1)), 0,
            "clock discontinuity cannot reverse movement");
        close(length(FreecamMotion.delta(0.0F, 1, 0, 0, 36, 0.05)), 1.8,
            "Ctrl speeds up three times");
        close(FreecamMotion.pitch(0, 20), 3, "mouse follows vanilla sensitivity scale");
        close(FreecamMotion.pitch(89, 1000), 90, "look down clamp");
        close(FreecamMotion.pitch(-89, -1000), -90, "look up clamp");
        System.out.println("PASS: " + checks + " freecam flight and mouse checks");
        return checks;
    }
}
