package com.donutautomine.mine;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class WaterControl {
   private WaterControl() {
   }

   public static boolean water(MinecraftClient var0, BlockPos var1) {
      return Cells.probe != null
         ? Cells.probe.kind(var1) == 4
         : Cells.loaded(var0, var1) && Cells.water(Cells.state(var0, var1)) && Cells.state(var0, var1).getBlock() instanceof FluidBlock;
   }

   public static WaterControl.Survey survey(final MinecraftClient var0, Region var1, BlockPos var2) {
      return survey(new WaterControl.Terrain() {
         @Override
         public boolean loaded(BlockPos var1) {
            return Cells.loaded(var0, var1);
         }

         @Override
         public int waterLevel(BlockPos var1) {
            if (!WaterControl.water(var0, var1)) {
               return -1;
            } else if (Cells.probe != null) {
               return 0;
            } else {
               BlockState var2x = Cells.state(var0, var1);
               return var2x.getBlock() instanceof FluidBlock ? (Integer)var2x.get(FluidBlock.LEVEL) : -1;
            }
         }
      }, var1, var2, 8192);
   }

   public static WaterControl.Survey survey(WaterControl.Terrain var0, Region var1, BlockPos var2, int var3) {
      HashSet var4 = new HashSet();
      ArrayDeque var5 = new ArrayDeque();

      for (int var6 = -4; var6 <= 4; var6++) {
         for (int var7 = -4; var7 <= 4; var7++) {
            for (int var8 = -2; var8 <= 4; var8++) {
               BlockPos var9 = var2.add(var6, var8, var7);
               if (var0.loaded(var9) && var0.waterLevel(var9) >= 0 && var4.add(var9)) {
                  var5.add(var9);
               }
            }
         }
      }

      HashSet<BlockPos> var19 = new HashSet<>();
      HashSet<BlockPos> var20 = new HashSet<>();
      HashSet<BlockPos> var21 = new HashSet<>();
      boolean var22 = true;
      int var10 = 0;

      while (!var5.isEmpty() && var10++ < var3) {
         BlockPos var11 = (BlockPos)var5.removeFirst();
         int var12 = var0.waterLevel(var11);
         if (var1.inside(var11)) {
            if (var12 == 0) {
               var20.add(var11);
            } else {
               var21.add(var11);
            }
         }

         for (Direction var16 : Direction.values()) {
            BlockPos var17 = var11.offset(var16);
            if (Math.abs(var17.getX() - var2.getX()) <= 32
               && Math.abs(var17.getZ() - var2.getZ()) <= 32
               && var17.getY() >= var2.getY() - 16
               && var17.getY() <= var2.getY() + 64) {
               if (!var0.loaded(var17)) {
                  var22 = false;
               } else {
                  int var18 = var0.waterLevel(var17);
                  if (var18 >= 0) {
                     if (var1.inside(var11) && !var1.inside(var17) && var17.getY() >= var11.getY() && (var16 == Direction.UP || var18 <= var12 || var18 >= 8)) {
                        var19.add(var17);
                     }

                     if (var4.add(var17)) {
                        var5.add(var17);
                     }
                  }
               }
            } else if (var0.loaded(var17) && var0.waterLevel(var17) >= 0) {
               var22 = false;
            }
         }
      }

      if (!var5.isEmpty()) {
         var22 = false;
      }

      Comparator<BlockPos> var23 = Comparator.comparingDouble(var1x -> var1x.getSquaredDistance(var2));
      return new WaterControl.Survey(var19.stream().sorted(var23).toList(), var20.stream().sorted(var23).toList(), var21.stream().sorted(var23).toList(), var22);
   }

   public record Survey(List<BlockPos> inlets, List<BlockPos> sources, List<BlockPos> flowing, boolean complete) {
      public BlockPos next() {
         return !this.inlets.isEmpty() ? this.inlets.getFirst() : (this.complete && !this.sources.isEmpty() ? this.sources.getFirst() : null);
      }

      public boolean wet() {
         return !this.inlets.isEmpty() || !this.sources.isEmpty() || !this.flowing.isEmpty();
      }
   }

   public interface Terrain {
      boolean loaded(BlockPos var1);

      int waterLevel(BlockPos var1);
   }
}
