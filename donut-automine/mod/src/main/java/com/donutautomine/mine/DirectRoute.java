package com.donutautomine.mine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Axis;

public final class DirectRoute {
   private DirectRoute() {
   }

   private static PathFinder.Step step(BlockPos var0, PathFinder.Kind var1) {
      return new PathFinder.Step(var0, var1 == PathFinder.Kind.TUNNEL || var1 == PathFinder.Kind.DIGDOWN, var1);
   }

   public static boolean body(MinecraftClient var0, BlockPos var1, boolean var2) {
      return clear(var0, var1, var2) && clear(var0, var1.up(), var2);
   }

   private static boolean clear(MinecraftClient var0, BlockPos var1, boolean var2) {
      return !Cells.loaded(var0, var1) ? false : Cells.passable(var0, var1) || var2 && WaterControl.water(var0, var1);
   }

   /**
    * Đường thẳng từ var1 tới tâm ô var2 đi được không: mọi góc thân người (±0,31) phải trống, không nguy hiểm, không nằm trong var4.
    * 1.14.2 (theo vật lý vanilla: còn một phần chân trên khối là không rơi): mỗi điểm trên đường cần ít nhất 2 trong 4 góc chân có sàn
    * (dư 1 góc phòng lệch ngang khi đi); trước đây đòi cả 4 góc nên sát mép hố không đi chéo / không đi thẳng được.
    */
   public static boolean clearSegment(MinecraftClient var0, Vec3d var1, BlockPos var2, boolean var3, Set<BlockPos> var4) {
      int var5 = (int)Math.floor(var1.y + 0.02);
      if (var5 != var2.getY()) {
         return false;
      } else {
         double var6 = var2.getX() + 0.5 - var1.x;
         double var8 = var2.getZ() + 0.5 - var1.z;
         int var10 = Math.max(1, (int)Math.ceil(Math.hypot(var6, var8) / 0.16));
         HashMap<BlockPos, Boolean> var11 = new HashMap<>();

         // 1.14.1: bỏ điểm xuất phát (người đang đứng đó rồi) — đứng sát mép hố thì góc thân chìa ra trên hố, trước đây bị coi là đường chặn
         for (int var12 = 1; var12 <= var10; var12++) {
            double var13 = var1.x + var6 * var12 / var10;
            double var15 = var1.z + var8 * var12 / var10;
            int var17 = 0;

            for (double var20 : new double[]{-0.31, 0.31}) {
               for (double var25 : new double[]{-0.31, 0.31}) {
                  BlockPos var27 = BlockPos.ofFloored(var13 + var20, var5, var15 + var25);
                  if (!var11.containsKey(var27)) {
                     if (var4.contains(var27)
                        || !body(var0, var27, var3)
                        || Cells.hazardAt(var0, var27)
                        || Cells.hazardAt(var0, var27.up())
                        || Cells.hazardAt(var0, var27.down())) {
                        return false;
                     }

                     var11.put(var27, Cells.floor(var0, var27.down()) && (var0 == null || !ServerSync.pending(var0, var27.down())));
                  }

                  if (var11.get(var27)) {
                     var17++;
                  }
               }
            }

            if (var17 < 2) {
               return false;
            }
         }

         // ô đích phải đứng vững được
         return Cells.floor(var0, var2.down());
      }
   }

   /** 1.14.0: đi thẳng một mạch (tối đa 8 ô) theo trục tới khi hết đường thẳng thông, thay vì dừng ở từng ô. */
   private static BlockPos farthest(MinecraftClient var0, BlockPos var1, BlockPos var2, Direction var3, boolean var4, Set<BlockPos> var5) {
      int var6 = Math.abs(var3.getAxis() == Axis.X ? var2.getX() - var1.getX() : var2.getZ() - var1.getZ());
      BlockPos var7 = var1.offset(var3);
      Vec3d var8 = Cells.eyeAt(var1).add(0.0, -1.62, 0.0);

      for (int var9 = Math.min(8, var6); var9 >= 2; var9--) {
         BlockPos var10 = var1.offset(var3, var9);
         if (clearSegment(var0, var8, var10, var4, var5)) {
            return var10;
         }
      }

      return var7;
   }

   public static PathFinder.Step next(MinecraftClient var0, BlockPos var1, BlockPos var2, PathFinder.Dig var3, boolean var4, boolean var5, Set<BlockPos> var6) {
      return next(var0, var1, var2, var3, var4, var5, var6, false);
   }

   /** 1.14.2: bước chéo 1 ô theo luật AutoBuild — ô đích đứng được, 2 ô vuông góc trống thân người (không cần sàn) */
   static boolean diagonalOk(MinecraftClient var0, BlockPos var1, BlockPos var2, Set<BlockPos> var3) {
      BlockPos var4 = new BlockPos(var2.getX(), var1.getY(), var1.getZ());
      BlockPos var5 = new BlockPos(var1.getX(), var1.getY(), var2.getZ());
      return !var3.contains(var2)
         && Cells.standable(var0, var2)
         && body(var0, var4, false)
         && body(var0, var5, false)
         && !Cells.hazardAt(var0, var4.down())
         && !Cells.hazardAt(var0, var5.down())
         && (var0 == null || !ServerSync.pending(var0, var2.down()));
   }

   /**
    * 1.14.2: hố nông 1 ô mà bên kia có chỗ đứng cùng độ cao → đi xuống rồi bước lên (chỉ bước dọc trục, tối đa 5 ô đáy hố).
    * Dùng khi không nhảy qua được (hố rộng quá 3 ô), thay cho bắc cầu.
    */
   static boolean dipCrossable(MinecraftClient var0, BlockPos var1, Direction var2, Set<BlockPos> var3) {
      BlockPos var4 = var1.offset(var2);
      if (!body(var0, var4.down(), false) || !Cells.standable(var0, var4.down()) || var3.contains(var4.down())) {
         return false;
      } else {
         for (int var5 = 2; var5 <= 6; var5++) {
            BlockPos var6 = var1.offset(var2, var5);
            if (!Cells.loaded(var0, var6) || var3.contains(var6)) {
               return false;
            }

            if (Cells.standable(var0, var6)) {
               return true;
            }

            if (!Cells.standable(var0, var6.down()) || !Cells.passable(var0, var6.up())) {
               return false;
            }
         }

         return false;
      }
   }

   public static PathFinder.Step next(
      MinecraftClient var0, BlockPos var1, BlockPos var2, PathFinder.Dig var3, boolean var4, boolean var5, Set<BlockPos> var6, boolean var7x
   ) {
      if (var1.getX() != var2.getX() || var1.getZ() != var2.getZ()) {
         int var7 = var2.getX() - var1.getX();
         int var8 = var2.getZ() - var1.getZ();
         if (var7 != 0 && var8 != 0) {
            double var9 = Math.hypot(var7, var8);
            double var11 = Math.min(1.0, 8.0 / var9);
            BlockPos var13 = var1.add((int)Math.round(var7 * var11), 0, (int)Math.round(var8 * var11));
            if (!var13.equals(var1) && clearSegment(var0, Cells.eyeAt(var1).add(0.0, -1.62, 0.0), var13, var5, var6)) {
               return step(var13, PathFinder.Kind.WALK);
            }

            BlockPos var14 = var1.add(Integer.signum(var7), 0, Integer.signum(var8));
            if (clearSegment(var0, Cells.eyeAt(var1).add(0.0, -1.62, 0.0), var14, var5, var6) || diagonalOk(var0, var1, var14, var6)) {
               return step(var14, PathFinder.Kind.WALK);
            }
         }

         Direction var17 = var7 > 0 ? Direction.EAST : Direction.WEST;
         Direction var10 = var8 > 0 ? Direction.SOUTH : Direction.NORTH;

         for (Direction var19 : Math.abs(var7) >= Math.abs(var8) ? List.of(var17, var10) : List.of(var10, var17)) {
            if ((var19.getAxis() != Axis.X || var7 != 0) && (var19.getAxis() != Axis.Z || var8 != 0)) {
               BlockPos var20 = var1.offset(var19);
               if (Cells.loaded(var0, var20) && !var6.contains(var20) && (!Cells.hazardAt(var0, var20) || WaterControl.water(var0, var20))) {
                  if (body(var0, var20, var5)) {
                     if (Cells.floor(var0, var20.down())) {
                        return step(farthest(var0, var1, var2, var19, var5, var6), PathFinder.Kind.WALK);
                     }

                     // 1.14.0: khe trên đường đi → nhảy qua (kiểu AutoBuild) trước khi nghĩ tới bắc cầu
                     BlockPos var22 = new AutoBuildNavigation(var0, var1, null, var1.getY() - 1, var1.getY(), false, false, var6).jumpTarget(var1, var19);
                     boolean var23 = var7x && var2.getY() >= var1.getY() && dipCrossable(var0, var1, var19, var6);
                     if (var22 != null && var2.getY() >= var1.getY() && (var22.getY() >= var1.getY() || !var23)) {
                        return step(var22, PathFinder.Kind.JUMP);
                     }

                     if (var23) {
                        return step(var20.down(), PathFinder.Kind.WALK);
                     }

                     if (var4 && (Cells.scaffoldAt(var0, var20.down()) || var5 && WaterControl.water(var0, var20.down()))) {
                        return step(var20, PathFinder.Kind.BRIDGE);
                     }

                     for (int var15 = 1; var15 <= 3 && var20.getY() - var15 >= var2.getY(); var15++) {
                        BlockPos var16 = var20.down(var15);
                        if (!body(var0, var16, var5)) {
                           break;
                        }

                        if (Cells.floor(var0, var16.down())) {
                           return step(var16, PathFinder.Kind.WALK);
                        }
                     }
                  }

                  PathFinder.Strike var21 = PathFinder.strikeFor(var0, var1, var20);
                  if (var3 != null
                     && Cells.floor(var0, var20.down())
                     && var21 != null
                     && var21 != PathFinder.INVALID
                     && var3.strikeAllowed(var0, var21.c(), var21.f())) {
                     return step(var20, PathFinder.Kind.TUNNEL);
                  }

                  if (body(var0, var20.up(), var5)
                     && Cells.floor(var0, var20)
                     && clear(var0, var1.up(2), var5)
                     && var20.getY() + 1 <= Math.max(var1.getY(), var2.getY())) {
                     return step(var20.up(), PathFinder.Kind.WALK);
                  }
               }
            }
         }

         return null;
      } else if (var1.getY() > var2.getY()
         && var3 != null
         && Cells.mineable(var0, var1.down())
         && Cells.floor(var0, var1.down(2))
         && var3.strikeAllowed(var0, var1.down(), Direction.UP)) {
         return step(var1.down(), PathFinder.Kind.DIGDOWN);
      } else {
         return var1.getY() < var2.getY() && var4 && var3 != null && var3.pillar ? step(var1.up(), PathFinder.Kind.PILLAR) : null;
      }
   }
}
