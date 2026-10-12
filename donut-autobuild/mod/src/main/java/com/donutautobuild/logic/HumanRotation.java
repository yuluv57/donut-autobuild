package com.donutautobuild.logic;

import java.util.Random;

public final class HumanRotation {
   private final Random random;
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

   public HumanRotation(Random var1) {
      this.random = var1;
   }

   public void reset() {
      this.hasTarget = false;
      this.overshooting = false;
   }

   public void target(float var1, float var2, float var3, float var4, double var5, double var7, int var9) {
      var4 = Math.clamp(var4, -90.0F, 90.0F);
      if (this.hasTarget && Math.abs(MotionMath.wrap(var3 - this.targetYaw)) < 0.35F && Math.abs(var4 - this.targetPitch) < 0.35F) {
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
         double var10 = (double)MotionMath.wrap(var3 - var1);
         double var12 = (double)(var4 - var2);
         this.startDistance = Math.max(0.001, Math.hypot(var10, var12));
         this.peakSpeed = var5 + this.random.nextDouble() * Math.max(0.0, var7 - var5);
         this.curveSign = this.random.nextBoolean() ? 1.0 : -1.0;
         this.curveAmount = 0.04 + this.random.nextDouble() * 0.1;
         this.overshooting = this.startDistance > 20.0 && this.random.nextInt(100) < var9;
         if (this.overshooting) {
            double var14 = 0.8 + this.random.nextDouble() * 2.2;
            this.aimYaw = (float)((double)var3 + var10 / this.startDistance * var14);
            this.aimPitch = (float)Math.clamp((double)var4 + var12 / this.startDistance * var14, -90.0, 90.0);
         } else {
            this.aimYaw = var3;
            this.aimPitch = var4;
         }
      }
   }

   public HumanRotation.Angles step(float var1, float var2) {
      if (!this.hasTarget) {
         return new HumanRotation.Angles(var1, var2);
      } else {
         double var3 = (double)MotionMath.wrap(this.aimYaw - var1);
         double var5 = (double)(this.aimPitch - var2);
         double var7 = Math.hypot(var3, var5);
         if (var7 < 0.35) {
            if (this.overshooting) {
               this.overshooting = false;
               this.aimYaw = this.targetYaw;
               this.aimPitch = this.targetPitch;
               this.startDistance = Math.max(0.001, Math.hypot((double)MotionMath.wrap(this.targetYaw - var1), (double)(this.targetPitch - var2)));
               this.peakSpeed = Math.max(2.0, this.peakSpeed * 0.4);
               return new HumanRotation.Angles(var1, var2);
            } else {
               return new HumanRotation.Angles(this.targetYaw, this.targetPitch);
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
            float var23 = (float)((double)var1 + var17 * var15 - var19 * var21);
            float var24 = (float)Math.clamp((double)var2 + var19 * var15 + var17 * var21, -90.0, 90.0);
            return new HumanRotation.Angles(var23, var24);
         }
      }
   }

   public boolean settled(float var1, float var2) {
      return this.hasTarget && !this.overshooting && Math.hypot((double)MotionMath.wrap(this.targetYaw - var1), (double)(this.targetPitch - var2)) < 0.04;
   }

   public int reaction(int var1, int var2) {
      return var1 + this.random.nextInt(Math.max(1, var2 - var1 + 1));
   }

   public static record Angles(float yaw, float pitch) {
   }
}
