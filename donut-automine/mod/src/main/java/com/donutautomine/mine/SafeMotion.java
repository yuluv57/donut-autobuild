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

   /**
    * 1.14.3: ngồi giữ mép chỉ khi đang trôi về chỗ hụt chân (vị trí sắp tới còn dưới 2 góc chân có sàn). Đứng yên sát mép thì không rơi
    * nên không ngồi — trước đây chỉ cần 1 góc chân lơ lửng trên hố là ngồi, mà ngồi thì cúp 3x3 chỉ phá 1 khối và nhát đập hàng
    * (chờ đứng thẳng) không bao giờ ra → bot ngồi im mãi sát mép rãnh, hoặc đập lẻ từng khối quanh khối giữa.
    */
   public static boolean edgeSneak(MinecraftClient var0) {
      if (!overhanging(var0)) {
         return false;
      } else {
         int var1 = (int)Math.floor(var0.player.getY() + 0.02);
         double var2 = var0.player.getX() + var0.player.getVelocity().x * 3.0;
         double var4 = var0.player.getZ() + var0.player.getVelocity().z * 3.0;
         return !supportedAny(var0, var2, var4, var1 - 1, var1);
      }
   }

   /** 1.14.2: ít nhất 2 trong 4 góc chân trên sàn → không rơi (vanilla chỉ cần 1, giữ dư 1 góc phòng lệch) */
   public static boolean supportedAny(MinecraftClient var0, double var1, double var3, int var5, int var6) {
      int var7 = 0;

      for (double var10 : new double[]{-0.28, 0.28}) {
         for (double var15 : new double[]{-0.28, 0.28}) {
            for (int var18 = var5; var18 >= var6 - 1; var18--) {
               BlockPos var19 = BlockPos.ofFloored(var1 + var10, var18, var3 + var15);
               if (Cells.loaded(var0, var19) && Cells.floor(var0, var19)) {
                  var7++;
                  break;
               }
            }
         }
      }

      return var7 >= 2;
   }

   public static boolean safeDirection(MinecraftClient var0, double var1, double var3, int var5) {
      double var6 = var0.player.getX();
      double var8 = var0.player.getZ();
      int var10 = (int)Math.floor(var0.player.getY() + 0.02);

      for (int var11 = 1; var11 <= 3; var11++) {
         double var12 = var11 / 3.0;
         double var14 = var6 + (var0.player.getVelocity().x * 3.0 + var1 * 0.26) * var12;
         double var16 = var8 + (var0.player.getVelocity().z * 3.0 + var3 * 0.26) * var12;
         // 1.14.2: đang đi chỉ dừng khi chỗ sắp tới không còn góc chân nào có sàn (trước đây thiếu 1 góc là dừng + ngồi)
         if (!supportedAny(var0, var14, var16, var10, var5)) {
            return false;
         }
      }

      return true;
   }
}
