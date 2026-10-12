package com.donutautobuild;

import com.donutautobuild.build.Freecam;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.*;
import net.minecraft.util.math.*;
import org.joml.Vector4f;

/** Executes the injected visibility behavior in Minecraft classes from the release JAR. */
public final class FreecamVisibilityRegression {
    static int checks;
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    public static int run() throws Exception {
        checks = 0;
        ProbeCamera camera = new ProbeCamera();
        var projection = Camera.Projection.class.getDeclaredConstructor(Vec3d.class, Vec3d.class, Vec3d.class);
        projection.setAccessible(true);
        camera.projection = projection.newInstance(Vec3d.ZERO, Vec3d.ZERO, Vec3d.ZERO);
        WaterWorld world = RecoveryRegression.allocate(WaterWorld.class);
        world.state = Blocks.POWDER_SNOW.getDefaultState();
        RecoveryRegression.object(camera, Camera.class, "area", world);
        RecoveryRegression.field(Camera.class, "ready").setBoolean(camera, true);
        RecoveryRegression.object(camera, Camera.class, "pos", new Vec3d(0.5, 0.5, 0.5));
        check(camera.getSubmersionType() == CameraSubmersionType.POWDER_SNOW, "normal camera sees powder snow before enabling freecam");
        RecoveryRegression.field(Freecam.class, "active").setBoolean(null, true);
        try {
            check(camera.getSubmersionType() == CameraSubmersionType.NONE, "freecam inside powder snow is not visually submerged");
            for (BackgroundRenderer.FogType type : BackgroundRenderer.FogType.values()) {
                Fog fog = BackgroundRenderer.applyFog(camera, type, new Vector4f(1, 0, 0, 1), 240, true, 0);
                check(fog == Fog.DUMMY, "freecam bypasses fog including thick dimension fog " + type);
                check(fog.alpha() == 0, "transparent fog prevents Sodium fog-distance chunk pruning");
            }
        } finally { RecoveryRegression.field(Freecam.class, "active").setBoolean(null, false); }
        check(camera.getSubmersionType() == CameraSubmersionType.POWDER_SNOW, "normal submersion visuals return after freecam");
        RecoveryRegression.field(Camera.class, "ready").setBoolean(camera, false);
        Fog normal = BackgroundRenderer.applyFog(camera, BackgroundRenderer.FogType.FOG_TERRAIN,
            new Vector4f(1, 0, 0, 1), 240, false, 0);
        check(normal != Fog.DUMMY && normal.end() == 240, "normal configured render distance restored");
        System.out.println("PASS: " + checks + " actual freecam fog/submersion assertions.");
        return checks;
    }
    public static final class ProbeCamera extends Camera {
        Camera.Projection projection;
        @Override public Camera.Projection getProjection() { return projection; }
    }
    public static final class WaterWorld extends net.minecraft.client.world.ClientWorld {
        net.minecraft.block.BlockState state;
        private WaterWorld() { super(null,null,null,null,0,0,null,false,0L,0); }
        @Override public net.minecraft.block.BlockState getBlockState(BlockPos pos) { return state; }
        @Override public net.minecraft.fluid.FluidState getFluidState(BlockPos pos) { return state.getFluidState(); }
    }
}
