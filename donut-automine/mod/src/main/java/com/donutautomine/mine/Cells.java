package com.donutautomine.mine;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.Direction.Type;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public final class Cells {
   public static final double EYE = 1.62;
   private static final double[][] FACE_POINTS = new double[][]{
      {0.5, 0.5}, {0.3, 0.3}, {0.7, 0.7}, {0.3, 0.7}, {0.7, 0.3}, {0.5, 0.22}, {0.5, 0.78}, {0.22, 0.5}, {0.78, 0.5}
   };
   public static Cells.Probe probe;

   private Cells() {
   }

   public static boolean fluidAt(MinecraftClient var0, BlockPos var1) {
      if (probe == null) {
         return !var0.world.getBlockState(var1).getFluidState().isEmpty();
      } else {
         int var2 = probe.kind(var1);
         return var2 == 3 || var2 == 4;
      }
   }

   public static boolean loaded(MinecraftClient var0, BlockPos var1) {
      return probe != null ? probe.kind(var1) >= 0 : var0.world != null && var0.world.isChunkLoaded(var1.getX() >> 4, var1.getZ() >> 4);
   }

   public static BlockState state(MinecraftClient var0, BlockPos var1) {
      return var0.world.getBlockState(var1);
   }

   public static boolean lava(BlockState var0) {
      return var0.getFluidState().isIn(FluidTags.LAVA);
   }

   public static boolean water(BlockState var0) {
      return var0.getFluidState().isIn(FluidTags.WATER);
   }

   public static boolean fire(BlockState var0) {
      return var0.isOf(Blocks.FIRE) || var0.isOf(Blocks.SOUL_FIRE);
   }

   public static boolean mineable(MinecraftClient var0, BlockPos var1) {
      if (probe != null) {
         return probe.kind(var1) == 1;
      } else {
         BlockState var2 = var0.world.getBlockState(var1);
         if (var2.isAir() || fire(var2) || var2.getBlock() instanceof FluidBlock) {
            return false;
         } else if (var2.isOf(Blocks.BUBBLE_COLUMN)) {
            return false;
         } else {
            float var3 = var2.getHardness(var0.world, var1);
            return var3 >= 0.0F && var3 <= 50.0F;
         }
      }
   }

   public static boolean unbreakable(MinecraftClient var0, BlockPos var1) {
      if (probe != null) {
         return probe.kind(var1) == 2;
      } else {
         BlockState var2 = var0.world.getBlockState(var1);
         if (!var2.isAir() && !(var2.getBlock() instanceof FluidBlock)) {
            float var3 = var2.getHardness(var0.world, var1);
            return var3 < 0.0F || var3 > 50.0F;
         } else {
            return false;
         }
      }
   }

   public static boolean passable(MinecraftClient var0, BlockPos var1) {
      if (probe != null) {
         int var3 = probe.kind(var1);
         return var3 == 0;
      } else {
         BlockState var2 = var0.world.getBlockState(var1);
         return !lava(var2) && !fire(var2) && !var2.isOf(Blocks.POWDER_SNOW) && !var2.isOf(Blocks.COBWEB) && !var2.isOf(Blocks.SWEET_BERRY_BUSH)
            ? !hazardAt(var0, var1) && var2.getFluidState().isEmpty() && var2.getCollisionShape(var0.world, var1).isEmpty()
            : false;
      }
   }

   public static boolean solid(MinecraftClient var0, BlockPos var1) {
      if (probe == null) {
         BlockState var3 = var0.world.getBlockState(var1);
         return !var3.getCollisionShape(var0.world, var1).isEmpty();
      } else {
         int var2 = probe.kind(var1);
         return var2 == 1 || var2 == 2;
      }
   }

   public static boolean floor(MinecraftClient var0, BlockPos var1) {
      if (probe != null) {
         int var4 = probe.kind(var1);
         return var4 == 1 || var4 == 2;
      } else {
         BlockState var2 = var0.world.getBlockState(var1);
         VoxelShape var3 = var2.getCollisionShape(var0.world, var1);
         return !hazardAt(var0, var1)
            && var2.getFluidState().isEmpty()
            && !var3.isEmpty()
            && var3.getMax(Axis.Y) >= 0.8
            && var3.getMax(Axis.Y) <= 1.0
            && var3.getMin(Axis.X) <= 0.3
            && var3.getMax(Axis.X) >= 0.7
            && var3.getMin(Axis.Z) <= 0.3
            && var3.getMax(Axis.Z) >= 0.7;
      }
   }

   public static boolean hazardAt(MinecraftClient var0, BlockPos var1) {
      if (probe != null) {
         return probe.kind(var1) == 3;
      } else {
         BlockState var2 = var0.world.getBlockState(var1);
         return lava(var2)
            || fire(var2)
            || var2.isOf(Blocks.CACTUS)
            || var2.isOf(Blocks.MAGMA_BLOCK)
            || var2.isOf(Blocks.POWDER_SNOW)
            || var2.isOf(Blocks.SWEET_BERRY_BUSH)
            || var2.isOf(Blocks.COBWEB)
            || var2.isOf(Blocks.CAMPFIRE)
            || var2.isOf(Blocks.SOUL_CAMPFIRE);
      }
   }

   public static boolean standable(MinecraftClient var0, BlockPos var1) {
      if (!loaded(var0, var1)) {
         return false;
      } else if (passable(var0, var1) && passable(var0, var1.up()) && floor(var0, var1.down())) {
         if (probe != null ? probe.kind(var1.up()) != 4 : !water(state(var0, var1.up()))) {
            for (Direction var3 : Type.HORIZONTAL) {
               if (hazardAt(var0, var1.offset(var3)) || hazardAt(var0, var1.up().offset(var3))) {
                  return false;
               }
            }

            return !hazardAt(var0, var1.up(2));
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static Vec3d eyeAt(BlockPos var0) {
      return new Vec3d(var0.getX() + 0.5, var0.getY() + 1.62, var0.getZ() + 0.5);
   }

   public static Vec3d facePoint(BlockPos var0, Direction var1, double var2, double var4) {
      double var6 = var0.getX();
      double var8 = var0.getY();
      double var10 = var0.getZ();
      switch (var1.getAxis()) {
         case X:
            var6 += var1 == Direction.EAST ? 1.0 : 0.0;
            var8 += var2;
            var10 += var4;
            break;
         case Y:
            var8 += var1 == Direction.UP ? 1.0 : 0.0;
            var6 += var2;
            var10 += var4;
            break;
         default:
            var10 += var1 == Direction.SOUTH ? 1.0 : 0.0;
            var6 += var2;
            var8 += var4;
      }

      return new Vec3d(var6, var8, var10);
   }

   public static Vec3d faceCenter(BlockPos var0, Direction var1) {
      return facePoint(var0, var1, 0.5, 0.5);
   }

   public static boolean rayHits(MinecraftClient var0, Vec3d var1, Vec3d var2, BlockPos var3, Direction var4) {
      Vec3d var5 = var2.subtract(var1);
      double var6 = var5.length();
      if (var6 < 1.0E-4) {
         return false;
      } else {
         Vec3d var8 = var2.add(var5.multiply(0.05 / var6));
         BlockHitResult var9 = var0.world.raycast(new RaycastContext(var1, var8, ShapeType.OUTLINE, FluidHandling.NONE, var0.player));
         return var9.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK && var9.getBlockPos().equals(var3) && (var4 == null || var9.getSide() == var4);
      }
   }

   public static Vec3d visibleFacePoint(MinecraftClient var0, Vec3d var1, BlockPos var2, Direction var3, double var4) {
      Vec3d var6 = Vec3d.of(var3.getVector());
      double var7 = var4 * var4;

      for (Box var10 : outlineBoxes(var0, var2)) {
         for (double[] var14 : FACE_POINTS) {
            Vec3d var15 = outlineFacePoint(var2, var10, var3, var14[0], var14[1]);
            if (!(var1.squaredDistanceTo(var15) > var7) && !(var15.subtract(var1).dotProduct(var6) >= -0.001) && rayHits(var0, var1, var15, var2, var3)) {
               return var15;
            }
         }
      }

      return null;
   }

   private static List<Box> outlineBoxes(MinecraftClient var0, BlockPos var1) {
      BlockState var2 = state(var0, var1);
      return probe == null || !var2.isAir() || probe.kind(var1) != 1 && probe.kind(var1) != 2
         ? var2.getOutlineShape(var0.world, var1).getBoundingBoxes()
         : List.of(new Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0));
   }

   private static Vec3d outlineFacePoint(BlockPos var0, Box var1, Direction var2, double var3, double var5) {
      double var7 = var1.minX;
      double var9 = var1.minY;
      double var11 = var1.minZ;
      switch (var2.getAxis()) {
         case X:
            var7 = var2 == Direction.EAST ? var1.maxX : var1.minX;
            var9 += (var1.maxY - var1.minY) * var3;
            var11 += (var1.maxZ - var1.minZ) * var5;
            break;
         case Y:
            var9 = var2 == Direction.UP ? var1.maxY : var1.minY;
            var7 += (var1.maxX - var1.minX) * var3;
            var11 += (var1.maxZ - var1.minZ) * var5;
            break;
         case Z:
            var11 = var2 == Direction.SOUTH ? var1.maxZ : var1.minZ;
            var7 += (var1.maxX - var1.minX) * var3;
            var9 += (var1.maxY - var1.minY) * var5;
      }

      return new Vec3d(var0.getX() + var7, var0.getY() + var9, var0.getZ() + var11);
   }

   public static List<BlockPos> plane(BlockPos var0, Direction var1) {
      ArrayList var2 = new ArrayList(9);
      Axis var3 = var1.getAxis();

      for (int var4 = -1; var4 <= 1; var4++) {
         for (int var5 = -1; var5 <= 1; var5++) {
            var2.add(switch (var3) {
               case X -> var0.add(0, var4, var5);
               case Y -> var0.add(var4, 0, var5);
               default -> var0.add(var4, var5, 0);
            });
         }
      }

      return var2;
   }

   public static double breakTicks(MinecraftClient var0, BlockPos var1) {
      BlockState var2 = var0.world.getBlockState(var1);
      float var3 = var2.calcBlockBreakingDelta(var0.player, var0.world, var1);
      return var3 <= 0.0F ? 600.0 : Math.ceil(1.0 / var3);
   }

   public static Vec3d drillFacePoint(MinecraftClient var0, Vec3d var1, BlockPos var2, Direction var3, double var4) {
      Vec3d var6 = Vec3d.of(var3.getVector());

      for (Box var8 : outlineBoxes(var0, var2)) {
         for (double[] var12 : FACE_POINTS) {
            Vec3d var13 = outlineFacePoint(var2, var8, var3, var12[0], var12[1]);
            if (var1.squaredDistanceTo(var13) <= var4 * var4
               && var13.subtract(var1).dotProduct(var6) < -0.001
               && BreakGeometry.aligned(var1, var13, var3)
               && rayHits(var0, var1, var13, var2, var3)) {
               return var13;
            }
         }
      }

      return null;
   }

   public static boolean clearBody(MinecraftClient var0, BlockPos var1) {
      return loaded(var0, var1) && loaded(var0, var1.up()) && passable(var0, var1) && passable(var0, var1.up())
         ? probe != null
            || var0.world.getWorldBorder().contains(var1)
               && var1.getY() > var0.world.getBottomY()
               && var1.getY() + 2 <= var0.world.getTopYInclusive()
               && var0.world
                  .isSpaceEmpty(
                     var0.player,
                     new Box(var1.getX() + 0.18, var1.getY() + 0.01, var1.getZ() + 0.18, var1.getX() + 0.82, var1.getY() + 1.81, var1.getZ() + 0.82)
                  )
         : false;
   }

   /**
    * 1.14.2 (theo AutoBuild 3.0.84): nước nông — ô chân có nước (không lava, không khối ngập nước có va chạm), đầu ở ngoài nước,
    * dưới chân là sàn → lội qua được (chậm hơn ô khô). Trước đây mọi ô có nước đều coi là không đi được.
    */
   public static boolean wading(MinecraftClient var0, BlockPos var1) {
      if (!loaded(var0, var1) || !loaded(var0, var1.up())) {
         return false;
      } else if (probe != null) {
         return probe.kind(var1) == 4 && probe.kind(var1.up()) == 0 && floor(var0, var1.down());
      } else {
         BlockState var2 = state(var0, var1);
         if (water(var2) && !lava(var2) && var2.getCollisionShape(var0.world, var1).isEmpty() && passable(var0, var1.up()) && floor(var0, var1.down())) {
            for (Direction var4 : Type.HORIZONTAL) {
               if (lava(state(var0, var1.offset(var4))) || lava(state(var0, var1.up().offset(var4)))) {
                  return false;
               }
            }

            return !hazardAt(var0, var1.up(2))
               && var0.world.getWorldBorder().contains(var1)
               && var0.world
                  .isSpaceEmpty(
                     var0.player,
                     new Box(var1.getX() + 0.18, var1.getY() + 0.01, var1.getZ() + 0.18, var1.getX() + 0.82, var1.getY() + 1.81, var1.getZ() + 0.82)
                  );
         } else {
            return false;
         }
      }
   }

   public static boolean scaffoldAt(MinecraftClient var0, BlockPos var1) {
      if (loaded(var0, var1) && !hazardAt(var0, var1) && !fluidAt(var0, var1)) {
         return probe != null ? probe.kind(var1) == 0 : state(var0, var1).isAir();
      } else {
         return false;
      }
   }

   public interface Probe {
      int kind(BlockPos var1);
   }
}
