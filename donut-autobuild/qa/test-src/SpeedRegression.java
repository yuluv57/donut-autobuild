package com.donutautobuild;

import com.donutautobuild.build.*;
import net.minecraft.block.Blocks;
import net.minecraft.item.*;
import net.minecraft.util.math.BlockPos;

/** Faster hand preparation overlaps aiming, while wrong-item and resync guards remain mandatory. */
public final class SpeedRegression {
    static int checks;
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    static Object call(BuildController c, String name, Class<?>[] types, Object... args) throws Exception {
        return RecoveryRegression.call(c, name, types, args);
    }
    public static int run() throws Exception {
        checks = 0;
        var s = new RecoveryRegression.Session(Items.NETHERITE_PICKAXE, 1, 100);
        s.mc.world = RecoveryRegression.allocate(RecoveryRegression.World.class);
        var config = new BuildConfig(); config.syncLitematicaLayer = false;
        var c = new BuildController(config);
        RecoveryRegression.object(c, BuildController.class, "mc", s.mc);
        var target = new SchematicPlan.Target(new BlockPos(0,64,0), Blocks.OBSIDIAN.getDefaultState());
        RecoveryRegression.field(BuildController.class, "target").set(c, target);
        RecoveryRegression.field(InventoryAccess.class, "syncRev").setInt(null, -1);
        RecoveryRegression.field(InventoryAccess.class, "resyncSentAt").setLong(null, 0);
        s.player.inv.setStack(0, new ItemStack(Items.OBSIDIAN,64));
        RecoveryRegression.field(BuildController.class, "ticks").setInt(c, 100);
        call(c, "qualityPrepareHand", new Class<?>[0]);
        int latency = (int)call(c, "latencyTicks", new Class<?>[0]);
        RecoveryRegression.field(BuildController.class, "ticks").setInt(c, 100 + Math.max(2, latency + 1));
        check((boolean)call(c, "qualityClickReady", new Class<?>[]{SchematicPlan.Target.class}, target),
            "aiming time already satisfies hand stabilization without extra wait at click");
        s.player.inv.setStack(0, new ItemStack(Items.COBBLESTONE,64));
        check(!(boolean)call(c, "qualityClickReady", new Class<?>[]{SchematicPlan.Target.class}, target),
            "faster pipeline cannot use former material on a changed target");
        check((int)call(c, "qualityRetryTicks", new Class<?>[0]) < 40, "normal retry earlier than previous two seconds");
        check((int)call(c, "qualityDeferredRetryTicks", new Class<?>[0]) < 200, "repeated failure retries earlier than previous ten seconds");
        check((int)call(c, "qualityCautionTicks", new Class<?>[0]) < 400, "global cautious delay shorter than previous twenty seconds");
        RecoveryRegression.field(BuildController.class, "pending").set(c, target);
        check((int)call(c, "qualityHoldTicks", new Class<?>[0]) == 3, "plain solid uses shorter hold plus existing latency multiplier");
        RecoveryRegression.field(BuildController.class, "pending").set(c,
            new SchematicPlan.Target(target.pos(), Blocks.WATER.getDefaultState()));
        check((int)call(c, "qualityHoldTicks", new Class<?>[0]) == 8, "water keeps conservative hold");
        RecoveryRegression.field(BuildController.class, "pending").set(c,
            new SchematicPlan.Target(target.pos(), Blocks.PISTON.getDefaultState()));
        check((int)call(c, "qualityHoldTicks", new Class<?>[0]) == 8, "directional block keeps conservative hold");
        System.out.println("PASS: " + checks + " responsive build assertions.");
        return checks;
    }
}
