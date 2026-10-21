package com.donutautomine.mine;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

public final class Keys {
   public static boolean wantUse;

   private Keys() {
   }

   private static void set(KeyBinding var0, boolean var1) {
      if (var0 != null && var0.isPressed() != var1) {
         var0.setPressed(var1);
      }
   }

   public static void releaseMove(MinecraftClient var0) {
      BotInput.INSTANCE.clearMove();
      BotInput.INSTANCE.jumping = false;
      BotInput.INSTANCE.sprinting = false;
      if (var0.player != null && SafeMotion.overhanging(var0)) {
         BotInput.INSTANCE.sneaking = true;
      }
   }

   public static void releaseAll(MinecraftClient var0) {
      BotInput.INSTANCE.clear();
      wantUse = false;
      set(var0.options.attackKey, false);
      set(var0.options.useKey, false);
   }

   public static void jump(MinecraftClient var0, boolean var1) {
      BotInput.INSTANCE.jumping = var1;
   }

   public static void use(MinecraftClient var0, boolean var1) {
      wantUse = var1;
      set(var0.options.useKey, var1);
   }

   public static void stand(MinecraftClient var0) {
      BotInput.INSTANCE.sneaking = var0.player != null && SafeMotion.overhanging(var0);
      BotInput.INSTANCE.sprinting = false;
      if (var0.player != null && var0.player.isSprinting()) {
         var0.player.setSprinting(false);
      }
   }

   public static double drive(MinecraftClient var0, double var1, double var3, double var5, boolean var7) {
      return driveInternal(var0, var1, var3, var5, var7, (int)Math.floor(var0.player.getY() + 0.02), false);
   }

   public static double driveStep(MinecraftClient var0, double var1, double var3, double var5, boolean var7, int var8) {
      return driveInternal(var0, var1, var3, var5, var7, Math.min(var8, (int)Math.floor(var0.player.getY() + 0.02)), false);
   }

   public static double driveAir(MinecraftClient var0, double var1, double var3, double var5, boolean var7) {
      return driveInternal(var0, var1, var3, var5, var7, 0, true);
   }

   public static double lean(MinecraftClient var0, double var1, double var3) {
      BotInput.INSTANCE.sneaking = true;
      return driveInternal(var0, var1, var3, 0.02, false, 0, true);
   }

   private static double driveInternal(MinecraftClient var0, double var1, double var3, double var5, boolean var7, int var8, boolean var9) {
      BotInput var10 = BotInput.INSTANCE;
      double var11 = var1 - var0.player.getX();
      double var13 = var3 - var0.player.getZ();
      double var15 = Math.hypot(var11, var13);
      if (var15 <= var5) {
         var10.clearMove();
         return var15;
      } else {
         double var17 = var0.player.getVelocity().x;
         double var19 = var0.player.getVelocity().z;
         if (var7 && var15 < 0.6) {
            var11 -= var17 * 2.0;
            var13 -= var19 * 2.0;
         }

         double var21 = Math.hypot(var11, var13);
         if (var21 < 0.015) {
            var10.clearMove();
            return var15;
         } else {
            double var23 = Math.toRadians(var0.player.getYaw());
            double var25 = Math.sin(var23);
            double var27 = Math.cos(var23);
            double var29 = (-var11 * var25 + var13 * var27) / var21;
            double var31 = (var11 * var27 + var13 * var25) / var21;
            var10.forward = var29 > 0.38;
            var10.back = var29 < -0.38;
            var10.left = var31 > 0.38;
            var10.right = var31 < -0.38;
            double var33 = var10.forward ? 1.0 : (var10.back ? -1.0 : 0.0);
            double var35 = var10.left ? 1.0 : (var10.right ? -1.0 : 0.0);
            double var37 = -var25 * var33 + var27 * var35;
            double var39 = var27 * var33 + var25 * var35;
            double var41 = Math.hypot(var37, var39);
            if (var41 > 0.0
               && !var9
               && !var0.player.isInLava()
               && !var0.player.isTouchingWater()
               && !SafeMotion.safeDirection(var0, var37 / var41, var39 / var41, var8)) {
               var10.clearMove();
               var10.sneaking = true;
            }

            return var15;
         }
      }
   }
}
