package com.donutautobuild.build;

import net.minecraft.block.BlockState;

/** Decides completion from the observed world state rather than previous clicks. */
public final class BuildQuality {
    public static final int COMPLETE = 0;
    public static final int PLACE = 1;
    public static final int ADD = 2;
    public static final int CONFIGURE = 3;
    public static final int PARTNER = 4;
    public static final int REPAIR = 5;
    public static final int UNKNOWN = 6;

    private BuildQuality() {}

    public static boolean done(BlockState actual, BlockState wanted, boolean fast) {
        if (actual == null || wanted == null) return false;
        if (StateMatch.matches(actual, wanted)) return true;
        if (!fast || actual.getBlock() == wanted.getBlock()) return false;
        // Preserve the exact substitution policy and strength direction of FAST mode.
        if (Materials.swappable(wanted, actual)) return true;
        BlockState equivalent = effectiveWanted(actual, wanted, true);
        return equivalent != wanted && StateMatch.matches(actual, equivalent);
    }

    /** Copies the schematic's settings onto an allowed alternate material in FAST mode. */
    public static BlockState effectiveWanted(BlockState actual, BlockState wanted, boolean fast) {
        if (actual == null || wanted == null || !fast || actual.getBlock() == wanted.getBlock()) return wanted;
        String family = Materials.family(wanted.getBlock());
        if (family == null || !family.equals(Materials.family(actual.getBlock()))) return wanted;
        return Materials.copyProperties(wanted, actual.getBlock());
    }

    /** Chooses a repair only when placement, extra layers, settings, or a partner cannot finish it. */
    public static int classify(BlockState actual, BlockState wanted, boolean fast) {
        if (actual == null || wanted == null) return UNKNOWN;
        if (done(actual, wanted, fast)) return COMPLETE;
        BlockState effective = effectiveWanted(actual, wanted, fast);
        if (actual.isReplaceable() && !wanted.isAir()) return PLACE;
        if (StateMatch.chestAwaitingPartner(actual, effective)) return PARTNER;
        if (StateMatch.configurable(actual, effective)) return CONFIGURE;
        // canAdd alone accepts waterlogging/count changes even when orientation is wrong.
        if (StateMatch.placementMatches(actual, effective) && StateMatch.canAdd(actual, effective)) return ADD;
        return REPAIR;
    }

    public static boolean repairNeeded(BlockState actual, BlockState wanted, boolean fast) {
        return classify(actual, wanted, fast) == REPAIR;
    }
}
