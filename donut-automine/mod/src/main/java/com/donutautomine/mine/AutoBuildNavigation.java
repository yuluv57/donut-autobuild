package com.donutautomine.mine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Type;

public final class AutoBuildNavigation implements PathSearch.Terrain {
   private final MinecraftClient mc;
   private final BlockPos start;
   private final BlockPos hint;
   private final int minY;
   private final int maxY;
   private final int range;
   private final boolean scaffold;
   private final boolean pillar;
   private final Set<BlockPos> avoid;
   private final Map<BlockPos, Boolean> clearCache = new HashMap<>();
   private final Map<BlockPos, Boolean> standCache = new HashMap<>();

   public AutoBuildNavigation(MinecraftClient var1, BlockPos var2, BlockPos var3, int var4, int var5, boolean var6, boolean var7, Set<BlockPos> var8) {
      this.mc = var1;
      this.start = var2;
      this.hint = var3;
      this.minY = var4;
      this.maxY = var5;
      this.scaffold = var6;
      this.pillar = var7;
      this.avoid = var8;
      this.range = Math.min(128, Math.max(24, var3 == null ? 24 : Math.max(Math.abs(var3.getX() - var2.getX()), Math.abs(var3.getZ() - var2.getZ())) + 12));
   }

   public static PathSearch.Cell cell(BlockPos var0) {
      return new PathSearch.Cell(var0.getX(), var0.getY(), var0.getZ());
   }

   public static BlockPos pos(PathSearch.Cell var0) {
      return new BlockPos(var0.x(), var0.y(), var0.z());
   }

   @Override
   public void beginBatch() {
      this.clearCache.clear();
      this.standCache.clear();
   }

   @Override
   public void endBatch() {
      this.clearCache.clear();
      this.standCache.clear();
   }

   private boolean in(BlockPos var1) {
      return var1.getY() >= this.minY
         && var1.getY() <= this.maxY
         && !this.avoid.contains(var1)
         && Math.abs(var1.getX() - this.start.getX()) <= this.range
         && Math.abs(var1.getZ() - this.start.getZ()) <= this.range;
   }

   private boolean clear(BlockPos var1) {
      return this.clearCache.computeIfAbsent(var1, var1x -> Cells.clearBody(this.mc, var1x));
   }

   private boolean stand(BlockPos var1) {
      return this.standCache.computeIfAbsent(var1, var1x -> Cells.standable(this.mc, var1x) && this.clear(var1x));
   }

   private boolean safeGap(BlockPos var1) {
      for (int var2 = 0; var2 <= 5; var2++) {
         BlockPos var3 = var1.down(var2);
         if (!Cells.loaded(this.mc, var3) || Cells.hazardAt(this.mc, var3) || Cells.fluidAt(this.mc, var3)) {
            return false;
         }

         if (Cells.solid(this.mc, var3)) {
            return var2 >= 1 && Cells.floor(this.mc, var3);
         }
      }

      return false;
   }

   private boolean headroom(BlockPos var1) {
      return this.clear(var1) && Cells.passable(this.mc, var1.up(2));
   }

   private void parkour(BlockPos var1, Direction var2, List<PathSearch.Edge> var3) {
      // 1.14.0: nhảy qua khe cả khi trong túi có khối kê (trước đây có khối kê là bắc cầu, đặt khối rồi lại phải đào)
      if (this.headroom(var1)) {
         boolean var4 = this.in(var1.offset(var2.getOpposite())) && this.stand(var1.offset(var2.getOpposite()));
         // 1.14.0: ô đáp thấp hơn 1 ô mà chính nó là đáy một khe nông → giữ làm dự phòng, ưu tiên nhảy thẳng qua cả khe tới ô cùng độ cao
         PathSearch.Edge var13 = null;

         for (int var5 = 1; var5 <= 3; var5++) {
            BlockPos var6 = var1.offset(var2, var5);
            if (!this.in(var6) || !this.headroom(var6) || this.stand(var6) || !this.safeGap(var6)) {
               break;
            }

            BlockPos var7 = var1.offset(var2, var5 + 1);
            if (!this.in(var7)) {
               break;
            }

            for (int var11 : new int[]{0, -1, 1}) {
               BlockPos var12 = var7.up(var11);
               if ((var5 <= 1 || var4)
                  && (var11 <= 0 || var5 <= 2)
                  && this.in(var12)
                  && this.stand(var12)
                  && this.headroom(var12)
                  && (var11 == 0 || this.headroom(var6.up(Math.max(0, var11))))) {
                  PathSearch.Edge var14 = new PathSearch.Edge(cell(var12), 1.2 + 1.2 * var5 + Math.abs(var11) * 0.8, false);
                  if (var11 < 0 && var13 == null && var5 < 3 && this.headroom(var7) && !this.stand(var7) && this.safeGap(var7)) {
                     var13 = var14;
                     continue;
                  }

                  var3.add(var14);
                  return;
               }
            }

            if (!this.clear(var7)) {
               break;
            }
         }

         if (var13 != null) {
            var3.add(var13);
         }
      }
   }

   /** 1.14.0: ô đáp nếu nhảy qua khe từ var1 theo hướng var2 (null nếu không nhảy được). */
   public BlockPos jumpTarget(BlockPos var1, Direction var2) {
      ArrayList<PathSearch.Edge> var3 = new ArrayList<>(1);
      this.beginBatch();

      try {
         this.parkour(var1, var2, var3);
      } finally {
         this.endBatch();
      }

      return var3.isEmpty() ? null : pos(var3.get(0).to());
   }

   @Override
   public List<PathSearch.Edge> neighbors(PathSearch.Cell var1) {
      BlockPos var2 = pos(var1);
      ArrayList var3 = new ArrayList(16);

      for (Direction var5 : Type.HORIZONTAL) {
         BlockPos var6 = var2.offset(var5);

         for (int var10 : new int[]{0, 1, -1}) {
            BlockPos var11 = var6.up(var10);
            if (this.in(var11) && this.clear(var11) && (var10 <= 0 || this.clear(var2.up())) && (var10 >= 0 || this.clear(var6))) {
               if (this.stand(var11)) {
                  var3.add(new PathSearch.Edge(cell(var11), var10 == 0 ? 1.0 : 1.8, false));
                  break;
               }

               if (var10 == 0 && this.scaffold && Cells.scaffoldAt(this.mc, var11.down())) {
                  var3.add(new PathSearch.Edge(cell(var11), 20.0, true));
                  break;
               }
            }
         }

         if (this.in(var6) && this.clear(var6) && !this.stand(var6)) {
            for (int var17 = 2; var17 <= 3; var17++) {
               BlockPos var19 = var6.down(var17);
               if (!this.in(var19) || !this.clear(var19) || Cells.hazardAt(this.mc, var19.down())) {
                  break;
               }

               if (this.stand(var19)) {
                  var3.add(new PathSearch.Edge(cell(var19), 2.0 + var17, false));
                  break;
               }
            }
         }

         this.parkour(var2, var5, var3);
      }

      for (int var18 : new int[]{-1, 1}) {
         for (int var23 : new int[]{-1, 1}) {
            BlockPos var12 = var2.add(var18, 0, var23);
            if (this.in(var12) && this.stand(var12) && this.stand(var2.add(var18, 0, 0)) && this.stand(var2.add(0, 0, var23))) {
               var3.add(new PathSearch.Edge(cell(var12), 1.45, false));
            }
         }
      }

      BlockPos var14 = var2.up();
      if (this.pillar && this.scaffold && this.in(var14) && this.clear(var14) && Cells.scaffoldAt(this.mc, var2)) {
         var3.add(new PathSearch.Edge(cell(var14), 20.0, true));
      }

      return var3;
   }

   public static PathFinder.Kind kind(BlockPos var0, BlockPos var1) {
      if (var0.getX() == var1.getX() && var0.getZ() == var1.getZ() && var1.getY() == var0.getY() + 1) {
         return PathFinder.Kind.PILLAR;
      } else {
         return Math.abs(var0.getX() - var1.getX()) + Math.abs(var0.getZ() - var1.getZ()) > 2 ? PathFinder.Kind.JUMP : PathFinder.Kind.WALK;
      }
   }

   public PathFinder.Kind liveKind(BlockPos var1, BlockPos var2) {
      PathFinder.Kind var3 = kind(var1, var2);
      if (var3 == PathFinder.Kind.PILLAR) {
         return var3;
      } else if (Math.abs(var1.getX() - var2.getX()) + Math.abs(var1.getZ() - var2.getZ()) < 2 || var1.getX() != var2.getX() && var1.getZ() != var2.getZ()) {
         return Cells.floor(this.mc, var2.down()) ? var3 : PathFinder.Kind.BRIDGE;
      } else {
         return PathFinder.Kind.JUMP;
      }
   }

   public final class Search {
      private final PathSearch search;
      private final BlockPos anchor = AutoBuildNavigation.this.start;

      public Search(Predicate<BlockPos> nullx, boolean nullxx, int nullxxx) {
         this.search = nullxx && AutoBuildNavigation.this.hint != null
            ? new PathSearch(
               AutoBuildNavigation.cell(AutoBuildNavigation.this.start),
               List.of(AutoBuildNavigation.cell(AutoBuildNavigation.this.hint)),
               AutoBuildNavigation.this,
               nullxxx
            )
            : new PathSearch(
               AutoBuildNavigation.cell(AutoBuildNavigation.this.start),
               var1 -> nullx.test(AutoBuildNavigation.pos(var1)),
               AutoBuildNavigation.this.hint == null ? null : AutoBuildNavigation.cell(AutoBuildNavigation.this.hint),
               AutoBuildNavigation.this,
               nullxxx
            );
      }

      public void advance() {
         this.search.advanceFor(3000000L);
      }

      public boolean done() {
         return this.search.done();
      }

      public BlockPos anchor() {
         return this.anchor;
      }

      public List<PathFinder.Step> result() {
         List<PathSearch.Cell> var1 = this.search.result();
         if (var1 == null) {
            return null;
         } else {
            ArrayList var2 = new ArrayList();
            BlockPos var3 = AutoBuildNavigation.this.start;

            for (PathSearch.Cell var5 : var1) {
               BlockPos var6 = AutoBuildNavigation.pos(var5);
               PathFinder.Kind var7 = AutoBuildNavigation.this.liveKind(var3, var6);
               var2.add(new PathFinder.Step(var6, false, var7));
               var3 = var6;
            }

            return var2;
         }
      }
   }
}
