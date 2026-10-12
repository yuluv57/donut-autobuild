package com.donutautobuild;

import com.donutautobuild.build.*;
import com.donutautobuild.build.SchematicPlan.Target;
import java.util.*;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.item.*;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;

/** Runs actual AIM -> clickNow -> placeNow with vanilla raycasts and a simulated server response. */
public final class WaterlogAimRegression {
    static int checks;
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    static Object call(BuildController c, String method, Class<?>[] types, Object... args) throws Exception {
        return RecoveryRegression.call(c, method, types, args);
    }
    static void set(BuildController c, String name, Object value) throws Exception {
        RecoveryRegression.field(BuildController.class, name).set(c, value);
    }
    @SuppressWarnings("unchecked") static <T> T get(BuildController c, String name) throws Exception {
        return (T)RecoveryRegression.field(BuildController.class, name).get(c);
    }
    static void tick(BuildController c, int tick) throws Exception { set(c, "ticks", tick); }
    static void pose(RecoveryRegression.Player p, boolean crouch) throws Exception {
        p.crouch = crouch;
        RecoveryRegression.field(Entity.class, "standingEyeHeight").setFloat(p, crouch ? 1.27F : 1.62F);
    }
    static void position(RecoveryRegression.Player p, Vec3d pos) throws Exception {
        RecoveryRegression.object(p, Entity.class, "pos", pos);
        RecoveryRegression.object(p, Entity.class, "blockPos", BlockPos.ofFloored(pos));
        RecoveryRegression.object(p, Entity.class, "velocity", Vec3d.ZERO);
        p.setBoundingBox(new Box(pos.x-.3, pos.y, pos.z-.3, pos.x+.3, pos.y+1.8, pos.z+.3));
        pose(p, false);
    }

    public static int run() throws Exception {
        checks = 0;
        var s = new RecoveryRegression.Session(Items.WATER_BUCKET, 1, 100);
        s.mc.world = RecoveryRegression.allocate(RecoveryRegression.World.class);
        var world = (RecoveryRegression.World)s.mc.world;
        world.blocks = new HashMap<>();
        var pos = new BlockPos(0, 64, 2);
        var dry = Blocks.OAK_LEAVES.getDefaultState();
        var wanted = new Target(pos, dry.with(Properties.WATERLOGGED, true));
        world.blocks.put(pos, dry);
        world.blocks.put(new BlockPos(0, 63, 0), Blocks.COBBLESTONE.getDefaultState());
        position(s.player, new Vec3d(.5, 64, .5));
        s.player.setYaw(0F); s.player.setPitch(35F);
        var config = new BuildConfig(); config.syncLitematicaLayer = false; config.reach = 4.25;
        var c = new BuildController(config);
        RecoveryRegression.object(c, BuildController.class, "mc", s.mc);
        set(c, "target", wanted); set(c, "running", true); set(c, "stage", BuildController.Stage.MOVE);
        s.player.inv.setStack(0, new ItemStack(Items.WATER_BUCKET));
        RecoveryRegression.field(InventoryAccess.class, "syncRev").setInt(null, -1);
        RecoveryRegression.field(InventoryAccess.class, "resyncSentAt").setLong(null, 0);
        tick(c, 100); call(c, "qualityPrimeWaterlog", new Class<?>[0]);
        tick(c, 100 + (int)call(c, "latencyTicks", new Class<?>[0]) + 2);
        check((boolean)call(c, "qualityClickReady", new Class<?>[]{Target.class}, wanted),
            "water already held and prepared during MOVE needs no extra arrival delay");

        set(c, "stage", BuildController.Stage.AIM); set(c, "bridgeTicks", 80);
        var hit = PlacementSolver.ray(s.mc, 4.25);
        check(hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos), "vanilla standing ray hits the dry leaf");
        var staleAim = new PlacementSolver.Aim(hit, 0F, 35F, false, true);
        set(c, "aim", staleAim); pose(s.player, true); s.mc.options.sneakKey.setPressed(true);
        for (int t = 120; t < 140; t++) {
            tick(c, t); call(c, "aim", new Class<?>[0]);
            check(!s.mc.options.sneakKey.isPressed(), "actual AIM actively releases held Shift while waiting for client pose");
            check(get(c, "stage") == BuildController.Stage.AIM && get(c, "target") == wanted,
                "crouch transition never drops or replans a usable waterlogging target");
        }
        check((int)get(c, "bridgeTicks") == 0 && (int)get(c, "goalRetries") == 0,
            "waterlogging never enters the generic crouching/lean timeout loop");
        check((int)get(c, "placeFails") == 0 && s.interaction.placements == 0,
            "pose waiting is not counted as failed placement and cannot click from stale crouching ray");

        pose(s.player, false); set(c, "poseChangedAt", 140); set(c, "screenAt", 140);
        set(c, "aim", new PlacementSolver.Aim(hit, 0F, 35F, false, false));
        tick(c, 140);
        check(!(boolean)call(c, "bucketReady", new Class<?>[]{Target.class}, wanted), "pose/menu change waits only for brief stabilization");
        tick(c, 141);
        check(!(boolean)call(c, "bucketReady", new Class<?>[]{Target.class}, wanted), "first settling tick does not click yet");
        tick(c, 142);
        check((boolean)call(c, "bucketReady", new Class<?>[]{Target.class}, wanted),
            "waterlogging ready after two ticks instead of 6 plus twice the ping");
        s.mc.crosshairTarget = new BlockHitResult(hit.getPos(), Direction.UP, pos, false);
        check((boolean)call(c, "qualityCrosshairOn", new Class<?>[]{MinecraftClient.class, BlockPos.class, Direction.class, BuildController.class},
            s.mc, pos, hit.getSide(), c), "same leaf is a valid crosshair even if rendering reports another face");
        s.mc.crosshairTarget = new BlockHitResult(Vec3d.ofCenter(pos.east()), Direction.UP, pos.east(), false);
        check(!(boolean)call(c, "qualityCrosshairOn", new Class<?>[]{MinecraftClient.class, BlockPos.class, Direction.class, BuildController.class},
            s.mc, pos, hit.getSide(), c), "waterlogging still rejects another leaf under the crosshair");
        s.mc.crosshairTarget = new BlockHitResult(hit.getPos(), Direction.UP, pos, false);
        set(c, "sentYaw", s.player.getYaw()); set(c, "sentPitch", s.player.getPitch());
        call(c, "aim", new Class<?>[0]);
        check(s.interaction.placements == 1, "actual AIM clicks immediately when water, standing pose and ray are ready");
        check(world.getBlockState(pos).get(Properties.WATERLOGGED), "simulated server receives water at the original leaf");
        check(get(c, "stage") == BuildController.Stage.WAIT_BLOCK && get(c, "pending") == wanted,
            "normal controller confirmation bookkeeping follows the real click");
        check((int)get(c, "clickTick") == 142, "no new aim or arbitrary arrival timer delays the ready click");
        check(!s.mc.options.sneakKey.isPressed(), "Shift remains released during the waterlogging click");

        // The relaxed face comparison is limited to waterlogging, not ordinary block placement.
        world.blocks.put(pos, Blocks.AIR.getDefaultState());
        var solid = new Target(pos, Blocks.COBBLESTONE.getDefaultState()); set(c, "target", solid); set(c, "pending", null);
        check(!(boolean)call(c, "qualityCrosshairOn", new Class<?>[]{MinecraftClient.class, BlockPos.class, Direction.class, BuildController.class},
            s.mc, pos, Direction.NORTH, c), "solid block placement still needs the exact selected face");
        check(!(boolean)call(c, "qualityWaterlogAim", new Class<?>[0]), "ordinary construction keeps its original AIM path");
        System.out.println("PASS: " + checks + " actual waterlogging AIM, Shift release and immediate ready-click assertions.");
        return checks;
    }
}
