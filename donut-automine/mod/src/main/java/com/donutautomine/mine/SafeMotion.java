package com.donutautomine.mine;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

public final class SafeMotion {
   private SafeMotion() {
   }

   public static boolean supported(MinecraftClient var0, double var1, double var3, int var5, int var6) {
      for (double var10 : new double[]{-0.28, 0.28}) {
         for (double var15 : new double[]{-0.28, 0.28}) {
            boolean var17 = false;

            for (int var18 = var5; var18 >= var6 - 1; var18--) {
               BlockPos var19 = BlockPos.ofFloored(var1 + var10, var18, var3 + var15);
               if (Cells.loaded(var0, var19) && Cells.floor(var0, var19)) {
                  var17 = true;
                  break;
               }
            }

            if (!var17) {
               return false;
            }
         }
      }

      return true;
   }

   public static boolean overhanging(MinecraftClient var0) {
      if (var0.player != null && var0.player.isOnGround()) {
         int var1 = (int)Math.floor(var0.player.getY() + 0.02);
         return !supported(var0, var0.player.getX(), var0.player.getZ(), var1 - 1, var1);
      } else {
         return false;
      }
   }

   public static boolean safeDirection(MinecraftClient var0, double var1, double var3, int var5) {
      double var6 = var0.player.getX();
      double var8 = var0.player.getZ();
      int var10 = (int)Math.floor(var0.player.getY() + 0.02);

      for (int var11 = 1; var11 <= 3; var11++) {
         double var12 = var11 / 3.0;
         double var14 = var6 + (var0.player.getVelocity().x * 3.0 + var1 * 0.26) * var12;
         double var16 = var8 + (var0.player.getVelocity().z * 3.0 + var3 * 0.26) * var12;
         if (!supported(var0, var14, var16, var10, var5)) {
            return false;
         }
      }

      return true;
   }
}
