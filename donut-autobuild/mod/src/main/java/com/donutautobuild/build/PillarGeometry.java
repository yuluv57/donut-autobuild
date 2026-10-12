package com.donutautobuild.build;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public final class PillarGeometry {
   private PillarGeometry() {
   }

   public static boolean sameColumn(double var0, double var2, BlockPos var4) {
      return Math.floor(var0) == (double)var4.getX() && Math.floor(var2) == (double)var4.getZ();
   }

   public static boolean reachableTop(double var0, BlockPos var2) {
      double var3 = (double)var2.getY() + 1.0 - var0;
      return var3 > 0.0 && var3 <= 1.25;
   }

   public static boolean clearToPlace(Box var0, BlockPos var1) {
      return var0.minY >= (double)var1.getY() + 1.0 && !var0.intersects(new Box(var1));
   }
}
