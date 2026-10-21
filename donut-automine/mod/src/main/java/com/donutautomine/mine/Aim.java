package com.donutautomine.mine;

import java.util.Random;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

public final class Aim {
   private final Random random = new Random();
   private float targetYaw;
   private float targetPitch;
   private float aimYaw;
   private float aimPitch;
   private boolean hasTarget;
   private boolean overshooting;
   private double startDistance;
   private double peakSpeed;
   private double curveSign;
   private double curveAmount;

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

   public static float yawTo(double var0, double var2) {
      return (float)Math.toDegrees(Math.atan2(-var0, var2));
   }

   public static float pitchTo(double var0, double var2, double var4) {
      return (float)(-Math.toDegrees(Math.atan2(var2, Math.sqrt(var0 * var0 + var4 * var4))));
   }

   public static Vec3d dir(float var0, float var1) {
      double var2 = Math.toRadians(var0);
      double var4 = Math.toRadians(var1);
      return new Vec3d(-Math.sin(var2) * Math.cos(var4), -Math.sin(var4), Math.cos(var2) * Math.cos(var4));
   }

   public void reset() {
      this.hasTarget = false;
      this.overshooting = false;
   }

   public void target(float var1, float var2, float var3, float var4, double var5, double var7, int var9) {
      var4 = Math.clamp(var4, -90.0F, 90.0F);
      if (this.hasTarget && Math.abs(wrap(var3 - this.targetYaw)) < 0.35F && Math.abs(var4 - this.targetPitch) < 0.35F) {
         this.targetYaw = var3;
         this.targetPitch = var4;
         if (!this.overshooting) {
            this.aimYaw = var3;
            this.aimPitch = var4;
         }
      } else {
         this.hasTarget = true;
         this.targetYaw = var3;
         this.targetPitch = var4;
         double var10 = wrap(var3 - var1);
         double var12 = var4 - var2;
         this.startDistance = Math.max(0.001, Math.hypot(var10, var12));
         this.peakSpeed = var5 + this.random.nextDouble() * Math.max(0.0, var7 - var5);
         this.curveSign = this.random.nextBoolean() ? 1.0 : -1.0;
         this.curveAmount = 0.04 + this.random.nextDouble() * 0.1;
         this.overshooting = this.startDistance > 20.0 && this.random.nextInt(100) < var9;
         if (this.overshooting) {
            double var14 = 0.8 + this.random.nextDouble() * 2.2;
            this.aimYaw = (float)(var3 + var10 / this.startDistance * var14);
            this.aimPitch = (float)Math.clamp(var4 + var12 / this.startDistance * var14, -90.0, 90.0);
         } else {
            this.aimYaw = var3;
            this.aimPitch = var4;
         }
      }
   }

   public float[] step(float var1, float var2) {
      if (!this.hasTarget) {
         return new float[]{var1, var2};
      } else {
         double var3 = wrap(this.aimYaw - var1);
         double var5 = this.aimPitch - var2;
         double var7 = Math.hypot(var3, var5);
         if (var7 < 0.35) {
            if (this.overshooting) {
               this.overshooting = false;
               this.aimYaw = this.targetYaw;
               this.aimPitch = this.targetPitch;
               this.startDistance = Math.max(0.001, Math.hypot(wrap(this.targetYaw - var1), this.targetPitch - var2));
               this.peakSpeed = Math.max(2.0, this.peakSpeed * 0.4);
               return new float[]{var1, var2};
            } else {
               return new float[]{this.targetYaw, this.targetPitch};
            }
         } else {
            double var9 = Math.clamp(1.0 - var7 / this.startDistance, 0.0, 1.0);
            double var11 = 0.45 + 0.55 * Math.sin(Math.PI * Math.clamp(var9, 0.08, 0.92));
            double var13 = this.peakSpeed * var11 * (0.85 + this.random.nextDouble() * 0.3);
            var13 = Math.min(var13, var7 * 0.8 + 0.4);
            double var15 = Math.min(var13, var7);
            double var17 = var3 / var7;
            double var19 = var5 / var7;
            double var21 = var7 > 3.0 ? var15 * this.curveAmount * this.curveSign * Math.sin(Math.PI * var9) : 0.0;
            float var23 = (float)(var1 + var17 * var15 - var19 * var21);
            float var24 = (float)Math.clamp(var2 + var19 * var15 + var17 * var21, -90.0, 90.0);
            return new float[]{var23, var24};
         }
      }
   }

   public boolean settled(float var1, float var2) {
      return this.hasTarget && !this.overshooting && Math.hypot(wrap(this.targetYaw - var1), this.targetPitch - var2) < 0.05;
   }

   public boolean lookAt(MinecraftClient var1, Vec3d var2, double var3, double var5, int var7) {
      Vec3d var8 = var2.subtract(var1.player.getEyePos());
      return this.look(var1, yawTo(var8.x, var8.z), pitchTo(var8.x, var8.y, var8.z), var3, var5, var7);
   }

   /** 1.14.0: quay mặt tức thì tới góc cần nhìn (bỏ quay chậm/vượt góc), trả true ngay. */
   public boolean look(MinecraftClient var1, float var2, float var3, double var4, double var6, int var8) {
      float var9 = var1.player.getYaw();
      float var10 = var9 + wrap(var2 - var9);
      float var11 = Math.clamp(var3, -90.0F, 90.0F);
      this.hasTarget = true;
      this.overshooting = false;
      this.targetYaw = this.aimYaw = var10;
      this.targetPitch = this.aimPitch = var11;
      var1.player.setYaw(var10);
      var1.player.setPitch(var11);
      var1.player.setHeadYaw(var10);
      return true;
   }
}
