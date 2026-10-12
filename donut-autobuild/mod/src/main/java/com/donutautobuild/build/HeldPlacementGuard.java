package com.donutautobuild.build;

/** Separates a changed target from the item still held by the previous target. */
public final class HeldPlacementGuard {
    private Object item;
    private int slot = -1;
    private int revision = -1;
    private int since;

    public boolean ready(Object actual, Object required, int selected, int inventoryRevision, int tick, int waitTicks) {
        if (actual == null || actual != required) {
            reset(); return false;
        }
        if (actual != item || selected != slot) {
            item = actual; slot = selected; revision = inventoryRevision; since = tick;
            return false;
        }
        // Placement changes the stack count/revision without changing the selected material.
        // Explicit swaps/resyncs still pass through InventoryAccess.synced and reset this guard.
        revision = inventoryRevision;
        return tick - since >= Math.max(2, waitTicks);
    }

    public void reset() { item = null; slot = revision = -1; }
}
