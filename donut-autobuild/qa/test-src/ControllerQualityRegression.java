package com.donutautobuild;

import com.donutautobuild.build.*;
import com.donutautobuild.build.SchematicPlan.Target;
import java.lang.reflect.Constructor;
import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.SlabType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Exercises actual methods after the bytecode merger, rather than only standalone helpers. */
public final class ControllerQualityRegression {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static Object call(Object object, String method, Class<?>[] types, Object... values) throws Exception {
        return RecoveryRegression.call(object, method, types, values);
    }
    @SuppressWarnings("unchecked")
    private static <T> T state(BuildController controller, String name) throws Exception {
        return (T)RecoveryRegression.field(BuildController.class, name).get(controller);
    }
    private static void tick(BuildController controller, int tick) throws Exception {
        RecoveryRegression.field(BuildController.class, "ticks").setInt(controller, tick);
    }
    private static boolean matches(BuildController c, BlockState actual, BlockState wanted) throws Exception {
        return (boolean)call(c, "done", new Class<?>[]{BlockState.class, BlockState.class}, actual, wanted);
    }

    public static int run() throws Exception {
        checks = 0;
        RecoveryRegression.Session session = new RecoveryRegression.Session(Items.NETHERITE_PICKAXE, 1, 100);
        session.mc.world = RecoveryRegression.allocate(RecoveryRegression.World.class);
        RecoveryRegression.World world = (RecoveryRegression.World)session.mc.world;
        BuildConfig config = new BuildConfig(); config.buildMode = "EXACT"; config.fixWrong = true;
        config.syncLitematicaLayer = false;
        BuildController c = new BuildController(config);
        RecoveryRegression.object(c, BuildController.class, "mc", session.mc);
        BlockPos pos = new BlockPos(0, 64, 0);
        Target target = new Target(pos, Blocks.OBSIDIAN.getDefaultState());
        SchematicPlan plan = RecoveryRegression.allocate(SchematicPlan.class);
        RecoveryRegression.object(plan, SchematicPlan.class, "blocks", new HashMap<>(Map.of(pos, target)));
        plan.targets = new ArrayList<>(List.of(target)); plan.loaded = true;
        ScaffoldJournal journal = RecoveryRegression.allocate(ScaffoldJournal.class);
        RecoveryRegression.object(journal, ScaffoldJournal.class, "placed", new HashMap<BlockPos, String>());
        RecoveryRegression.field(BuildController.class, "plan").set(c, plan);
        RecoveryRegression.field(BuildController.class, "journal").set(c, journal);

        BlockState slab = Blocks.OAK_SLAB.getDefaultState().with(Properties.SLAB_TYPE, SlabType.BOTTOM);
        check(!matches(c, Blocks.COBBLESTONE.getDefaultState(), target.state()), "controller rejects wrong obsidian material");
        check(!matches(c, slab.with(Properties.SLAB_TYPE, SlabType.TOP), slab), "controller rejects wrong same-block half");
        check(!matches(c, slab.with(Properties.WATERLOGGED, true), slab), "controller rejects wrong same-block waterlogging");
        config.buildMode = "FAST";
        check(matches(c, Blocks.COBBLESTONE.getDefaultState(), Blocks.STONE.getDefaultState()), "controller preserves allowed fast substitution");
        check(!matches(c, Blocks.COBBLESTONE.getDefaultState(), target.state()), "fast cannot weaken obsidian in controller");
        config.buildMode = "EXACT";

        Set<BlockPos> completed = state(c, "placedDone");
        Map<BlockPos, BlockState> wrong = state(c, "repairWrong");
        world.state = Blocks.COBBLESTONE.getDefaultState(); completed.add(pos);
        check(!(boolean)call(c, "markPlacedDone", new Class<?>[]{Set.class, Object.class}, completed, pos), "wrong loaded world cannot be marked done");
        check(!completed.contains(pos), "false cached success removed");
        check(wrong.containsKey(pos), "wrong loaded block queued for repair");
        world.state = target.state();
        check((boolean)call(c, "markPlacedDone", new Class<?>[]{Set.class, Object.class}, completed, pos), "observed correct material can be marked done");
        check(completed.contains(pos), "correct cell added to cache");

        Class<?> scanClass = Class.forName("com.donutautobuild.build.BuildController$Scan");
        Constructor<?> scanConstructor = scanClass.getDeclaredConstructor(BuildController.class, boolean.class);
        scanConstructor.setAccessible(true);
        Object scan = scanConstructor.newInstance(c, true);
        check(!(boolean)call(scan, "assumeDone", new Class<?>[]{Target.class, BlockState.class}, target, Blocks.AIR.getDefaultState()), "saved cell cannot hide an empty loaded cell");
        check(!(boolean)call(scan, "assumeDone", new Class<?>[]{Target.class, BlockState.class}, target, Blocks.COBBLESTONE.getDefaultState()), "saved cell cannot hide wrong loaded type");

        Map<BlockPos, Integer> repairTries = state(c, "repairTries"), repairs = state(c, "repairs");
        repairTries.put(pos, 5); repairs.put(pos, 3); tick(c, 100);
        check(!(boolean)call(c, "qualityRepairReady", new Class<?>[]{BlockPos.class}, pos), "exhausted repair attempts enter cooldown");
        check(!completed.contains(pos), "repair cooldown never marks incorrect cell complete");
        tick(c, 299);
        check(!(boolean)call(c, "qualityRepairReady", new Class<?>[]{BlockPos.class}, pos), "repair cooldown keeps full interval");
        tick(c, 300);
        check((boolean)call(c, "qualityRepairReady", new Class<?>[]{BlockPos.class}, pos), "repair attempts recover after cooldown");
        check(!repairTries.containsKey(pos) && !repairs.containsKey(pos), "cooldown resets both repair counters");
        check((boolean)call(c, "breakableWrong", new Class<?>[]{BlockState.class, BlockPos.class}, slab.with(Properties.WATERLOGGED, true), pos), "solid waterlogged mismatch remains repairable");
        check(!(boolean)call(c, "breakableWrong", new Class<?>[]{BlockState.class, BlockPos.class}, Blocks.WATER.getDefaultState(), pos), "water source is not mined as a solid");
        check(!(boolean)call(c, "breakableWrong", new Class<?>[]{BlockState.class, BlockPos.class}, Blocks.BEDROCK.getDefaultState(), pos), "unbreakable block excluded");

        world.state = Blocks.AIR.getDefaultState();
        RecoveryRegression.field(InventoryAccess.class, "syncRev").setInt(null, -1);
        RecoveryRegression.field(InventoryAccess.class, "resyncSentAt").setLong(null, 0);
        session.player.inv.selectedSlot = 0;
        session.player.inv.setStack(0, new ItemStack(Items.COBBLESTONE, 64)); tick(c, 400);
        check(!(boolean)call(c, "qualityClickReady", new Class<?>[]{Target.class}, target), "actual controller refuses stale cobble at obsidian target");
        Object pendingBefore = state(c, "pending"), stageBefore = state(c, "stage");
        int clicksBefore = session.interaction.clicks;
        check((boolean)call(c, "clickNow", new Class<?>[]{Target.class, float.class, float.class}, target, 0F, 0F), "clickNow reports safe waiting while hand is wrong");
        check(state(c, "pending") == pendingBefore && state(c, "stage") == stageBefore, "early hand wait does not mutate pending or stage");
        check(session.interaction.clicks == clicksBefore, "early hand wait sends no inventory click");
        session.player.inv.setStack(0, new ItemStack(Items.OBSIDIAN, 64)); tick(c, 401);
        check(!(boolean)call(c, "qualityClickReady", new Class<?>[]{Target.class}, target), "new correct hand still waits");
        int wait = Math.max(2, (int)call(c, "latencyTicks", new Class<?>[0]) + 1);
        tick(c, 401 + wait - 1);
        check(!(boolean)call(c, "qualityClickReady", new Class<?>[]{Target.class}, target), "actual controller enforces stabilization delay");
        tick(c, 401 + wait);
        check((boolean)call(c, "qualityClickReady", new Class<?>[]{Target.class}, target), "actual controller permits stable correct hand");
        RecoveryRegression.field(InventoryAccess.class, "syncRev").setInt(null, 1);
        RecoveryRegression.field(InventoryAccess.class, "syncSince").setLong(null, System.currentTimeMillis());
        tick(c, 500);
        check(!(boolean)call(c, "qualityClickReady", new Class<?>[]{Target.class}, target), "unsynced inventory blocks placement");
        RecoveryRegression.field(InventoryAccess.class, "syncRev").setInt(null, -1); tick(c, 600);
        check(!(boolean)call(c, "qualityClickReady", new Class<?>[]{Target.class}, target), "return from unsynced inventory resets hand delay");
        tick(c, 600 + wait);
        check((boolean)call(c, "qualityClickReady", new Class<?>[]{Target.class}, target), "resynced stable hand allowed");

        repairTries.put(pos, 5); repairs.put(pos, 3);
        call(c, "resetQualityRecovery", new Class<?>[0]);
        check(repairTries.isEmpty() && repairs.isEmpty(), "reconnect reset clears repair exhaustion");
        tick(c, 900);
        check(!(boolean)call(c, "qualityClickReady", new Class<?>[]{Target.class}, target), "reconnect reset also invalidates stable hand");

        BlockHitResult intendedHit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        BlockHitResult otherHit = new BlockHitResult(Vec3d.ofCenter(pos.east()), Direction.UP, pos.east(), false);
        RecoveryRegression.field(BuildController.class, "pending").set(c, target);
        session.player.inv.setStack(0, new ItemStack(Items.COBBLESTONE, 64)); tick(c, 1000);
        check(!(boolean)call(c, "qualityPlaceReady", new Class<?>[]{BlockHitResult.class}, intendedHit), "actual hit guard blocks stale cobble at pending obsidian");
        check(!(boolean)call(c, "placeNow", new Class<?>[]{BlockHitResult.class}, intendedHit), "placeNow rejects wrong held material before interaction");
        check(state(c, "pending") == target, "rejected placement preserves intended pending target");
        check(!(boolean)call(c, "qualityPlaceReady", new Class<?>[]{BlockHitResult.class}, otherHit), "pending placement rejects a different receiver position");
        session.player.inv.setStack(0, new ItemStack(Items.OBSIDIAN, 64)); tick(c, 1001);
        check(!(boolean)call(c, "qualityPlaceReady", new Class<?>[]{BlockHitResult.class}, intendedHit), "hit guard waits for newly selected obsidian");
        tick(c, 1001 + wait);
        check((boolean)call(c, "qualityPlaceReady", new Class<?>[]{BlockHitResult.class}, intendedHit), "hit guard accepts correct stable obsidian");

        // The reverse target transition must not leak the former obsidian hand either.
        Target cobbleTarget = new Target(pos, Blocks.COBBLESTONE.getDefaultState());
        @SuppressWarnings("unchecked") Map<BlockPos, Target> planBlocks = (Map<BlockPos, Target>)RecoveryRegression.field(SchematicPlan.class, "blocks").get(plan);
        planBlocks.put(pos, cobbleTarget);
        RecoveryRegression.field(BuildController.class, "pending").set(c, cobbleTarget); tick(c, 1100);
        check(!(boolean)call(c, "qualityPlaceReady", new Class<?>[]{BlockHitResult.class}, intendedHit), "target change obsidian to cobble rejects stale obsidian hand");
        session.player.inv.setStack(0, new ItemStack(Items.COBBLESTONE, 64)); tick(c, 1101);
        check(!(boolean)call(c, "qualityPlaceReady", new Class<?>[]{BlockHitResult.class}, intendedHit), "new cobble hand starts a separate delay");
        tick(c, 1101 + wait);
        check((boolean)call(c, "qualityPlaceReady", new Class<?>[]{BlockHitResult.class}, intendedHit), "new cobble target accepts stable correct hand");
        check((boolean)call(c, "qualityNeedsRepair", new Class<?>[]{BlockPos.class, BlockState.class}, pos.east(),
            Blocks.COBBLESTONE.getDefaultState()), "queued blocker outside schematic retains mining path");
        int beforeFreecam = RecoveryRegression.field(BuildController.class, "ticks").getInt(c);
        RecoveryRegression.field(BuildController.class, "wanted").setBoolean(c, true);
        RecoveryRegression.field(Freecam.class, "active").setBoolean(null, true);
        try {
            c.tick();
            check(RecoveryRegression.field(BuildController.class, "ticks").getInt(c) == beforeFreecam,
                "freecam freezes actual controller before any building work");
            check(c.wanted(), "freecam preserves resume intent");
        } finally { RecoveryRegression.field(Freecam.class, "active").setBoolean(null, false); }
        System.out.println("PASS: " + checks + " actual controller quality regression assertions.");
        return checks;
    }
}
