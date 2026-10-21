package com.donutautomine.mine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class LayerPlan {
   public final int layer;
   public final int floor;
   public final int centerY;
   public final boolean alongZ;
   public final boolean startMinB;
   public final boolean startMinA;
   public final int strips;
   public final List<LayerPlan.Slice> slices = new ArrayList<>();
   public final int pitX;
   public final int pitZ;
   public final int endX;
   public final int endZ;
   public int index;

   public LayerPlan(Region var1, int var2, int var3, int var4) {
      this.layer = var2;
      this.floor = var1.floor(var2);
      this.centerY = Math.min(this.floor + 1, var1.y1);
      this.alongZ = var1.sizeZ() >= var1.sizeX();
      int var5 = this.alongZ ? var1.z0 : var1.x0;
      int var6 = this.alongZ ? var1.z1 : var1.x1;
      int var7 = this.alongZ ? var1.x0 : var1.z0;
      int var8 = this.alongZ ? var1.x1 : var1.z1;
      int var9 = this.alongZ ? var4 : var3;
      int var10 = this.alongZ ? var3 : var4;
      this.startMinA = Math.abs(var9 - var5) <= Math.abs(var9 - var6);
      this.startMinB = Math.abs(var10 - var7) <= Math.abs(var10 - var8);
      ArrayList var11 = new ArrayList();
      if (var8 - var7 + 1 < 3) {
         var11.add(var7 + (var8 - var7 + 1) / 2);
      } else {
         int var12 = var7 + 1;

         while (true) {
            int var13 = Math.min(var12, var8 - 1);
            var11.add(var13);
            if (var13 >= var8 - 1) {
               break;
            }

            var12 += 3;
         }
      }

      if (!this.startMinB) {
         Collections.reverse(var11);
      }

      this.strips = var11.size();
      Direction var27 = this.startMinA ? this.posA() : this.posA().getOpposite();
      Direction var28 = this.startMinB ? this.posB() : this.posB().getOpposite();
      int var14 = this.startMinA ? var5 : var6;
      int var15 = this.startMinA ? var6 : var5;
      int var16 = var14;

      for (int var17 = 0; var17 < var11.size(); var17++) {
         int var18 = (Integer)var11.get(var17);
         int var19 = var15;
         int var20 = var15 >= var14 ? 1 : -1;
         int var21 = var14;

         while (true) {
            this.slices.add(new LayerPlan.Slice(this.pos(var18, this.centerY, var21), var27.getOpposite(), var17, var17));
            var16 = var21;
            if (var21 == var19) {
               if (var17 + 1 < var11.size()) {
                  var21 = (Integer)var11.get(var17 + 1);
                  int var22 = var15 - var20;
                  if (Math.abs(var15 - var14) < 2) {
                     var22 = var15;
                  }

                  int var23 = var28.getOffsetX() + var28.getOffsetZ();
                  int var24 = var18 + 2 * var23;
                  int var25 = var21 + var23;
                  if ((var25 - var24) * var23 >= 0) {
                     int var26 = var24;

                     while (true) {
                        this.slices.add(new LayerPlan.Slice(this.pos(var26, this.centerY, var22), var28.getOpposite(), var17, var17));
                        if (var26 == var25) {
                           break;
                        }

                        var26 += var23;
                     }
                  }

                  int var33 = var14;
                  var14 = var15;
                  var15 = var33;
                  var27 = var27.getOpposite();
               }
               break;
            }

            var21 += var20;
         }
      }

      int var29 = (Integer)var11.getFirst();
      int var30 = (this.startMinA ? var5 : var6) + (this.startMinA ? 1 : -1);
      if (var6 - var5 + 1 < 3) {
         var30 = var5 + (var6 - var5) / 2;
      }

      this.pitX = this.alongZ ? var29 : var30;
      this.pitZ = this.alongZ ? var30 : var29;
      int var31 = (Integer)var11.getLast();
      this.endX = this.alongZ ? var31 : var16;
      this.endZ = this.alongZ ? var16 : var31;
   }

   private Direction posA() {
      return this.alongZ ? Direction.SOUTH : Direction.EAST;
   }

   private Direction posB() {
      return this.alongZ ? Direction.EAST : Direction.SOUTH;
   }

   private BlockPos pos(int var1, int var2, int var3) {
      return this.alongZ ? new BlockPos(var1, var2, var3) : new BlockPos(var3, var2, var1);
   }

   public LayerPlan.Slice current() {
      return this.index < this.slices.size() ? this.slices.get(this.index) : null;
   }

   public List<LayerPlan.Slice> runFrom(int var1) {
      ArrayList var2 = new ArrayList();
      if (var1 >= this.slices.size()) {
         return var2;
      } else {
         int var3 = this.slices.get(var1).run();

         for (int var4 = var1; var4 < this.slices.size() && this.slices.get(var4).run() == var3; var4++) {
            var2.add(this.slices.get(var4));
         }

         return var2;
      }
   }

   public int stripOfCurrent() {
      LayerPlan.Slice var1 = this.current();
      return var1 == null ? this.strips : var1.strip();
   }

   public int runEnd(int var1) {
      if (var1 >= this.slices.size()) {
         return this.slices.size();
      } else {
         int var2 = this.slices.get(var1).run();
         int var3 = var1;

         while (var3 < this.slices.size() && this.slices.get(var3).run() == var2) {
            var3++;
         }

         return var3;
      }
   }

   public record Slice(BlockPos center, Direction face, int strip, int run) {
   }
}
