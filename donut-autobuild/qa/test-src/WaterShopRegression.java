package com.donutautobuild;

import com.donutautobuild.build.*;
import com.donutautobuild.build.SchematicPlan.Target;
import com.donutautobuild.order.ShopBuyer;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

/** Replays the purchased water -> waterlogged leaf rejection found in the live log. */
public final class WaterShopRegression {
    static int checks;
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    static Object call(BuildController c, String name, Class<?>[] types, Object... args) throws Exception {
        return RecoveryRegression.call(c, name, types, args);
    }
    static void set(BuildController c, String name, Object value) throws Exception {
        RecoveryRegression.field(BuildController.class, name).set(c, value);
    }
    @SuppressWarnings("unchecked") static <T> T get(BuildController c, String name) throws Exception {
        return (T)RecoveryRegression.field(BuildController.class, name).get(c);
    }
    static void tick(BuildController c, int value) throws Exception { set(c, "ticks", value); }

    public static int run() throws Exception {
        checks = 0;
        var s = new RecoveryRegression.Session(Items.WATER_BUCKET, 1, 100);
        s.mc.world = RecoveryRegression.allocate(RecoveryRegression.World.class);
        var world = (RecoveryRegression.World)s.mc.world;
        var config = new BuildConfig(); config.syncLitematicaLayer = false; config.buildMode = "FAST";
        var c = new BuildController(config);
        RecoveryRegression.object(c, BuildController.class, "mc", s.mc);
        var pos = new BlockPos(-667, -45, 346);
        var dry = Blocks.OAK_LEAVES.getDefaultState();
        var wanted = new Target(pos, dry.with(Properties.WATERLOGGED, true));
        var plan = RecoveryRegression.allocate(SchematicPlan.class);
        RecoveryRegression.object(plan, SchematicPlan.class, "blocks", new HashMap<>(Map.of(pos, wanted)));
        plan.targets = new ArrayList<>(List.of(wanted)); plan.loaded = true;
        set(c, "plan", plan); set(c, "target", wanted); set(c, "running", true);
        set(c, "bucketBuyPos", pos); set(c, "stage", BuildController.Stage.ORDER);
        set(c, "afterOrder", BuildController.Stage.EQUIP); set(c, "orderItem", Items.WATER_BUCKET);
        set(c, "shopping", true); world.state = dry;
        var shop = (ShopBuyer)get(c, "shop");
        RecoveryRegression.field(ShopBuyer.class, "lastCommandAt").setLong(null, 0);
        shop.start(s.mc, Items.WATER_BUCKET, 29);
        // Actual ShopBuyer and actual orderTick, including menu close and afterOrder routing.
        for (int t = 100; t < 2000 && (boolean)get(c, "shopping"); t++) {
            world.gameTime = t; tick(c, t); call(c, "orderTick", new Class<?>[0]);
        }
        check(!(boolean)get(c, "shopping") && shop.result() == ShopBuyer.Result.BOUGHT, "bulk water purchase completes");
        check(InventoryAccess.count(s.mc, Items.WATER_BUCKET) == 29, "exactly 29 buckets received");
        check(s.net.commands == 1 && s.interaction.clicks == 29, "one shop visit buys 29 individual buckets");
        check(s.mc.currentScreen == null, "shop closes after confirmed delivery");
        check(get(c, "stage") == BuildController.Stage.EQUIP && get(c, "target") == wanted,
            "purchase resumes original waterlogged leaf target in EQUIP");

        RecoveryRegression.field(InventoryAccess.class, "syncRev").setInt(null, -1);
        RecoveryRegression.field(InventoryAccess.class, "resyncSentAt").setLong(null, 0);
        tick(c, 2100); set(c, "pending", wanted);
        for (Direction side : Direction.values()) {
            var hit = new BlockHitResult(Vec3d.ofCenter(pos), side, pos, false);
            check(call(c, "qualityReceiver", new Class<?>[]{BlockHitResult.class}, hit).equals(pos),
                "waterlog receiver stays inside clicked leaves from " + side);
            call(c, "qualityPlaceReady", new Class<?>[]{BlockHitResult.class}, hit);
            tick(c, 2200);
            check((boolean)call(c, "qualityPlaceReady", new Class<?>[]{BlockHitResult.class}, hit),
                "stable water bucket accepted for same-cell waterlogging from " + side);
        }
        var hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        var adjacentWater = new Target(pos.up(), Blocks.WATER.getDefaultState());
        set(c, "pending", adjacentWater);
        check(!(boolean)call(c, "qualityPlaceReady", new Class<?>[]{BlockHitResult.class}, hit),
            "water cannot go beside dry fillable leaves because vanilla fills leaves instead");
        world.state = Blocks.COBBLESTONE.getDefaultState();
        check(call(c, "qualityReceiver", new Class<?>[]{BlockHitResult.class}, hit).equals(pos.up()),
            "water on a non-fillable solid goes beside clicked face");
        world.state = dry; set(c, "pending", wanted); set(c, "stage", BuildController.Stage.AIM);
        check((boolean)call(c, "bucketPlacementPriority", new Class<?>[0]), "purchased water target has priority without exact standing-position check");
        set(c, "bucketBuyPos", null);
        check((boolean)call(c, "bucketPlacementPriority", new Class<?>[0]), "already-owned water also completes before unrelated repair work");
        check(!(boolean)call(c, "tryRepairNow", new Class<?>[0]) && !(boolean)call(c, "walkToRepair", new Class<?>[0]),
            "neither repair path may steal active purchased pour");
        call(c, "lowerLayerWatch", new Class<?>[0]);
        check(get(c, "target") == wanted, "lower-layer watcher preserves active pour");
        world.state = wanted.state();
        check(!(boolean)call(c, "bucketPlacementPriority", new Class<?>[0]), "confirmed water releases repair priority");
        world.state = dry;

        Map<BlockPos, Integer> tntDefers = get(c, "tntDefers"), deferred = get(c, "deferred");
        call(c, "tntRefuse", new Class<?>[]{Target.class, String.class, boolean.class}, wanted, "", false);
        check(get(c, "target") == wanted && tntDefers.isEmpty() && deferred.isEmpty(),
            "blank non-TNT rejection preserves target and never increments TNT deferrals");
        call(c, "tntRefuse", new Class<?>[]{Target.class, String.class, boolean.class}, wanted, null, false);
        check(get(c, "target") == wanted && tntDefers.isEmpty(), "null non-TNT reason waits too");
        set(c, "tntVetoWhy", "old unrelated TNT reason");
        s.player.inv.setStack(0, new ItemStack(Items.OBSIDIAN));
        check(!(boolean)call(c, "placeNow", new Class<?>[]{BlockHitResult.class}, hit), "wrong hand sends no place interaction");
        check("".equals(get(c, "tntVetoWhy")), "rejected hand cannot reuse old TNT veto reason");

        Map<BlockPos, BlockState> wrong = get(c, "repairWrong");
        Set<BlockPos> skipped = get(c, "skipped"), completed = get(c, "placedDone");
        var wrongPos = pos.east(); var wrongTarget = new Target(wrongPos, Blocks.OBSIDIAN.getDefaultState());
        @SuppressWarnings("unchecked") Map<BlockPos, Target> blocks = (Map<BlockPos, Target>)RecoveryRegression.field(SchematicPlan.class, "blocks").get(plan);
        blocks.put(wrongPos, wrongTarget); world.state = Blocks.COBBLESTONE.getDefaultState();
        wrong.put(wrongPos, world.state); completed.add(wrongPos); deferred.put(wrongPos, 2300); tick(c, 2200);
        call(c, "qualityObserve", new Class<?>[]{Target.class}, wrongTarget);
        check(!completed.contains(wrongPos), "wrong postponed cell remains unfinished");
        for (int t = 2200; t < 2300; t++) {
            tick(c, t);
            check(!(boolean)call(c, "qualityRepairReady", new Class<?>[]{BlockPos.class}, wrongPos),
                "navigation defer respected throughout retry interval");
        }
        tick(c, 2300);
        check((boolean)call(c, "qualityRepairReady", new Class<?>[]{BlockPos.class}, wrongPos), "repair becomes eligible when navigation defer expires");
        skipped.add(wrongPos);
        call(c, "qualityObserve", new Class<?>[]{Target.class}, wrongTarget);
        check(skipped.contains(wrongPos) && !(boolean)call(c, "qualityRepairReady", new Class<?>[]{BlockPos.class}, wrongPos),
            "periodic audit does not erase postpone-until-end-of-layer mark");
        skipped.remove(wrongPos);
        check((boolean)call(c, "qualityRepairReady", new Class<?>[]{BlockPos.class}, wrongPos), "normal layer retry re-enables repair");

        // Red schematic overlays are unrelated to FAST completion: valid alternatives remain intact.
        var substitute = new Target(wrongPos, Blocks.STONE.getDefaultState());
        blocks.put(wrongPos, substitute); skipped.clear(); deferred.clear(); wrong.put(wrongPos, world.state);
        call(c, "qualityObserve", new Class<?>[]{Target.class}, substitute);
        check(!wrong.containsKey(wrongPos), "FAST cobble instead of stone is removed from repair queue");
        check(!(boolean)call(c, "qualityNeedsRepair", new Class<?>[]{BlockPos.class, BlockState.class}, wrongPos, world.state),
            "FAST functional substitute never becomes a mining task");
        // A real TNT refusal must still execute the original protection.
        set(c, "target", wanted); world.state = dry;
        call(c, "tntRefuse", new Class<?>[]{Target.class, String.class, boolean.class}, wanted, "actual nearby TNT risk", false);
        check(tntDefers.get(pos) == 1 && deferred.get(pos) > 2300 && get(c, "target") == null,
            "nonempty TNT risk retains original protective deferral");
        System.out.println("PASS: " + checks + " water purchase resume, fluid receivers and repair postponement assertions.");
        return checks;
    }
}
