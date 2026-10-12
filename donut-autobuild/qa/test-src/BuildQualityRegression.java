package com.donutautobuild;

import com.donutautobuild.build.BuildQuality;
import com.donutautobuild.build.StateMatch;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.enums.SlabType;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

/** Called after Minecraft bootstrap by the main offline regression entrypoint. */
public final class BuildQualityRegression {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void status(BlockState actual, BlockState wanted, boolean fast, int expected, String message) {
        check(BuildQuality.classify(actual, wanted, fast) == expected, message);
        check(BuildQuality.done(actual, wanted, fast) == (expected == BuildQuality.COMPLETE), message + " completion");
    }

    public static int run() {
        checks = 0;
        BlockState stone = Blocks.STONE.getDefaultState();
        BlockState cobble = Blocks.COBBLESTONE.getDefaultState();
        BlockState obsidian = Blocks.OBSIDIAN.getDefaultState();
        BlockState leaves = Blocks.OAK_LEAVES.getDefaultState();
        status(stone, stone, false, BuildQuality.COMPLETE, "correct strict material");
        status(cobble, stone, false, BuildQuality.REPAIR, "strict wrong decorative material");
        status(cobble, stone, true, BuildQuality.COMPLETE, "permitted fast decorative substitution");
        status(cobble, obsidian, true, BuildQuality.REPAIR, "fast may not weaken obsidian");
        status(obsidian, cobble, true, BuildQuality.REPAIR, "obsidian remains excluded from decorative substitutes");
        status(cobble, leaves, true, BuildQuality.REPAIR, "fast may not replace leaves with cobble");
        status(Blocks.AIR.getDefaultState(), obsidian, false, BuildQuality.PLACE, "missing cell is unfinished");
        status(Blocks.WATER.getDefaultState(), obsidian, false, BuildQuality.PLACE, "replaceable water permits placing");
        status(null, stone, false, BuildQuality.UNKNOWN, "unknown world cannot confirm success");
        status(stone, null, true, BuildQuality.UNKNOWN, "unknown target cannot confirm success");

        BlockState slab = Blocks.OAK_SLAB.getDefaultState().with(Properties.SLAB_TYPE, SlabType.BOTTOM);
        BlockState topSlab = slab.with(Properties.SLAB_TYPE, SlabType.TOP);
        BlockState doubleSlab = slab.with(Properties.SLAB_TYPE, SlabType.DOUBLE);
        check(StateMatch.placed(topSlab, slab), "baseline falsely completes wrong slab half");
        status(topSlab, slab, false, BuildQuality.REPAIR, "wrong slab half requires repair");
        status(doubleSlab, slab, false, BuildQuality.REPAIR, "surplus slab requires repair");
        status(slab, doubleSlab, false, BuildQuality.ADD, "single slab is completed by adding the other half");
        status(slab.with(Properties.WATERLOGGED, true), slab, false, BuildQuality.REPAIR, "unexpected waterlogging stays unfinished");
        status(slab, slab.with(Properties.WATERLOGGED, true), false, BuildQuality.ADD, "missing water can be added");
        status(topSlab, slab.with(Properties.WATERLOGGED, true), false, BuildQuality.REPAIR, "wrong slab half cannot be hidden by water addition");
        BlockState spruceSlab = Blocks.SPRUCE_SLAB.getDefaultState().with(Properties.SLAB_TYPE, SlabType.BOTTOM);
        status(spruceSlab, slab, true, BuildQuality.COMPLETE, "allowed fast slab family");
        status(spruceSlab.with(Properties.SLAB_TYPE, SlabType.TOP), slab, true, BuildQuality.REPAIR, "fast family still requires correct half");
        status(spruceSlab, doubleSlab, true, BuildQuality.ADD, "fast family can add second slab");
        status(spruceSlab, slab, false, BuildQuality.REPAIR, "slab substitution only in fast mode");

        BlockState log = Blocks.OAK_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.Y);
        status(log.with(Properties.AXIS, Direction.Axis.X), log, false, BuildQuality.REPAIR, "wrong log axis");
        BlockState piston = Blocks.PISTON.getDefaultState().with(Properties.FACING, Direction.NORTH);
        status(piston.with(Properties.FACING, Direction.SOUTH), piston, true, BuildQuality.REPAIR, "fast still checks piston direction");
        BlockState observer = Blocks.OBSERVER.getDefaultState().with(Properties.FACING, Direction.NORTH);
        status(observer.with(Properties.FACING, Direction.SOUTH), observer, false, BuildQuality.REPAIR, "wrong observer direction");

        BlockState candle = Blocks.CANDLE.getDefaultState().with(Properties.CANDLES, 2);
        status(candle.with(Properties.CANDLES, 1), candle, false, BuildQuality.ADD, "missing candle count");
        status(candle.with(Properties.CANDLES, 4), candle, false, BuildQuality.REPAIR, "extra candle count");
        status(candle.with(Properties.LIT, true), candle, false, BuildQuality.COMPLETE, "live candle light is not a placement error");
        BlockState repeater = Blocks.REPEATER.getDefaultState().with(Properties.DELAY, 1);
        status(repeater, repeater.with(Properties.DELAY, 4), false, BuildQuality.CONFIGURE, "repeater delay uses clicks");
        status(repeater.with(Properties.HORIZONTAL_FACING, Direction.WEST), repeater, false, BuildQuality.REPAIR, "repeater wrong facing");
        BlockState chest = Blocks.CHEST.getDefaultState().with(Properties.CHEST_TYPE, ChestType.SINGLE);
        status(chest, chest.with(Properties.CHEST_TYPE, ChestType.LEFT), false, BuildQuality.PARTNER, "double chest awaits partner");
        status(chest.with(Properties.HORIZONTAL_FACING, Direction.WEST), chest.with(Properties.CHEST_TYPE, ChestType.LEFT), false, BuildQuality.REPAIR, "wrong chest facing is not partner waiting");
        status(leaves.with(Properties.DISTANCE_1_7, 1).with(Properties.PERSISTENT, true), leaves, false, BuildQuality.COMPLETE, "live leaf distance and persistence preserved");

        // Exhaust the slab states to ensure count, orientation, material, and water never mask each other.
        for (SlabType wantedType : SlabType.values()) {
            for (SlabType actualType : SlabType.values()) {
                for (boolean wantedWater : new boolean[]{false, true}) {
                    for (boolean actualWater : new boolean[]{false, true}) {
                        BlockState wanted = slab.with(Properties.SLAB_TYPE, wantedType).with(Properties.WATERLOGGED, wantedWater);
                        BlockState actual = slab.with(Properties.SLAB_TYPE, actualType).with(Properties.WATERLOGGED, actualWater);
                        boolean exact = actualType == wantedType && actualWater == wantedWater;
                        check(BuildQuality.done(actual, wanted, false) == exact, "all strict slab states");
                        BlockState substitute = spruceSlab.with(Properties.SLAB_TYPE, actualType).with(Properties.WATERLOGGED, actualWater);
                        check(BuildQuality.done(substitute, wanted, true) == exact, "all fast slab family states");
                        check(!BuildQuality.done(substitute, wanted, false), "strict never substitutes slab family");
                    }
                }
            }
        }
        System.out.println("PASS: " + checks + " placement quality classification assertions.");
        return checks;
    }
}
