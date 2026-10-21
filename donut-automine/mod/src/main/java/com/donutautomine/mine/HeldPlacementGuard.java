package com.donutautomine.mine;

public final class HeldPlacementGuard {
   private Object item;
   private int slot = -1;
   private int revision = -1;
   private int since;

   public boolean ready(Object var1, Object var2, int var3, int var4, int var5, int var6) {
      if (var1 == null || var1 != var2) {
         this.reset();
         return false;
      } else if (var1 == this.item && var3 == this.slot) {
         this.revision = var4;
         return var5 - this.since >= Math.max(2, var6);
      } else {
         this.item = var1;
         this.slot = var3;
         this.revision = var4;
         this.since = var5;
         return false;
      }
   }

   public void reset() {
      this.item = null;
      this.slot = this.revision = -1;
   }
}
