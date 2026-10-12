package com.donutautobuild.logic;

public final class MotionMath {
   private MotionMath() {
   }

   public static float wrap(float var0) {
      var0 %= 360.0F;
      if (var0 >= 180.0F) {
         var0 -= 360.0F;
      }

      if (var0 < -180.0F) {
         var0 += 360.0F;
      }

      return var0;
   }

   public static float approach(float var0, float var1, float var2, boolean var3) {
      float var4 = var3 ? wrap(var1 - var0) : var1 - var0;
      float var5 = Math.min(var2, Math.max(0.12F, Math.abs(var4) * 0.32F));
      return var0 + Math.clamp(var4, -var5, var5);
   }

   public static float yaw(double var0, double var2) {
      return (float)Math.toDegrees(Math.atan2(-var0, var2));
   }

   public static float pitch(double var0, double var2, double var4) {
      return (float)(-Math.toDegrees(Math.atan2(var2, Math.sqrt(var0 * var0 + var4 * var4))));
   }
}
