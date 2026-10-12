package com.donutautobuild.build;

/** Camera-only math. No player coordinates or network packets are changed here. */
public final class FreecamMotion {
    private FreecamMotion() {}

    public static double[] delta(float yaw, double forward, double sideways, double vertical,
                                 double speed, double elapsedSeconds) {
        double length = Math.sqrt(forward * forward + sideways * sideways + vertical * vertical);
        if (length < 1.0e-6 || !Double.isFinite(length)) return new double[3];
        double radians = Math.toRadians(yaw);
        double scale = Math.max(0.0, Math.min(0.1, elapsedSeconds)) * speed / Math.max(1.0, length);
        return new double[] {
            (-Math.sin(radians) * forward - Math.cos(radians) * sideways) * scale,
            vertical * scale,
            (Math.cos(radians) * forward - Math.sin(radians) * sideways) * scale
        };
    }

    public static float pitch(float current, double mouseDelta) {
        return (float)Math.max(-90.0, Math.min(90.0, current + mouseDelta * 0.15));
    }
}
