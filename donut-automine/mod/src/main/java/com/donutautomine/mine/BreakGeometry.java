package com.donutautomine.mine;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class BreakGeometry {
   private BreakGeometry() {
   }

   public static Direction normal(float var0, float var1) {
      Vec3d var2 = Vec3d.fromPolar(var1, var0);
      return Direction.getFacing(var2.x, var2.y, var2.z);
   }

   public static boolean aligned(Vec3d var0, Vec3d var1, Direction var2) {
      Vec3d var3 = var1.subtract(var0);

      double var4 = Math.abs(switch (var2.getAxis()) {
         case X -> var3.x;
         case Y -> var3.y;
         case Z -> var3.z;
         default -> throw new MatchException(null, null);
      });

      double var6 = switch (var2.getAxis()) {
         case X -> Math.max(Math.abs(var3.y), Math.abs(var3.z));
         case Y -> Math.max(Math.abs(var3.x), Math.abs(var3.z));
         case Z -> Math.max(Math.abs(var3.x), Math.abs(var3.y));
         default -> throw new MatchException(null, null);
      };
      return var4 > var6 * 1.15;
   }

   /** 1.14.2: hướng nhìn (yaw, pitch) chắc chắn thuộc trục var2 (thành phần trục đó lớn hơn 1,15 lần các trục kia) → server phá 3x3 đúng mặt */
   public static boolean firm(float var0, float var1, Direction.Axis var2) {
      Vec3d var3 = Vec3d.fromPolar(var1, var0);
      double var4 = Math.abs(var3.getComponentAlongAxis(var2));
      double var6 = 0.0;

      for (Direction.Axis var11 : Direction.Axis.values()) {
         if (var11 != var2) {
            var6 = Math.max(var6, Math.abs(var3.getComponentAlongAxis(var11)));
         }
      }

      return var4 > var6 * 1.15;
   }

   public static boolean clearToPlace(Box var0, BlockPos var1) {
      return var0.minY >= var1.getY() + 1.0 && !var0.intersects(new Box(var1));
   }
}
