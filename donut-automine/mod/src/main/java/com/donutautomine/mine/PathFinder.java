package com.donutautomine.mine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Type;

public final class PathFinder {
   public static final PathFinder.Strike INVALID = new PathFinder.Strike(BlockPos.ORIGIN, Direction.UP);

   private PathFinder() {
   }

   public static boolean fluidNear(MinecraftClient var0, BlockPos var1) {
      if (Cells.fluidAt(var0, var1)) {
         return true;
      } else {
         for (Direction var5 : Direction.values()) {
            if (var5 != Direction.DOWN && Cells.fluidAt(var0, var1.offset(var5))) {
               return true;
            }
         }

         return false;
      }
   }

   public static List<PathFinder.Step> find(
      MinecraftClient var0, Region var1, BlockPos var2, Predicate<BlockPos> var3, BlockPos var4, int var5, boolean var6, int var7, int var8, Set<BlockPos> var9
   ) {
      PathFinder.Dig var10 = var6 && var1 != null ? new PathFinder.Dig(var1, false, var7, false) : null;
      return find(var0, var2, var3, var4, var5, var10, var7, var8, var9, 1.0);
   }

   public static List<PathFinder.Step> find(
      MinecraftClient var0,
      BlockPos var1,
      Predicate<BlockPos> var2,
      BlockPos var3,
      int var4,
      PathFinder.Dig var5,
      int var6,
      int var7,
      Set<BlockPos> var8,
      double var9
   ) {
      PriorityQueue<PathFinder.Node> var11 = new PriorityQueue<>((var0x, var1x) -> Double.compare(var0x.f, var1x.f));
      HashMap<BlockPos, Double> var12 = new HashMap<>();
      double var13 = var5 != null ? 3.0 : 1.5;
      var11.add(new PathFinder.Node(var1, 0.0, h(var1, var3, var13) * var9, null, PathFinder.Kind.WALK, null, null));
      var12.put(var1, 0.0);
      PathFinder.View var15 = new PathFinder.View(var0, var5 != null ? var5 : new PathFinder.Dig(null, false, Integer.MIN_VALUE, false));
      int var16 = 0;

      while (!var11.isEmpty() && var16 < var4) {
         PathFinder.Node var19 = (PathFinder.Node)var11.poll();
         if (!(var19.g > var12.getOrDefault(var19.pos, Double.MAX_VALUE) + 1.0E-6)) {
            if (var2.test(var19.pos)) {
               return build(var19);
            }

            var16++;
            var15.load(var19);
            BlockPos var20 = var19.pos;

            for (Direction var22 : Type.HORIZONTAL) {
               BlockPos var28 = var20.offset(var22);
               if (Cells.loaded(var0, var28)) {
                  boolean var29 = false;
                  if (var15.standable(var28) && in(var28, var6, var7) && !var8.contains(var28)) {
                     double var42 = Cells.fluidAt(var0, var28) ? 2.0 : 1.0;
                     push(var11, var12, var13, var19, var28, var42, var3, var9, PathFinder.Kind.WALK, null, null);
                     var29 = true;
                  } else {
                     BlockPos var30 = var28.up();
                     if (in(var30, var6, var7) && var15.standable(var30) && var15.passable(var20.up(2)) && !var8.contains(var30)) {
                        push(var11, var12, var13, var19, var30, 2.2, var3, var9, PathFinder.Kind.WALK, null, null);
                        var29 = true;
                     } else if (var15.passable(var28) && var15.passable(var28.up())) {
                        BlockPos var27 = var28;

                        for (int var31 = 1; var31 <= 3; var31++) {
                           BlockPos var32 = var27.down();
                           if (var32.getY() < var6) {
                              break;
                           }

                           if (var15.standable(var32)) {
                              if (!var8.contains(var32)) {
                                 push(var11, var12, var13, var19, var32, 1.0 + 0.4 * var31, var3, var9, PathFinder.Kind.WALK, null, null);
                              }

                              var29 = true;
                              break;
                           }

                           if (!var15.passable(var32)) {
                              break;
                           }

                           var27 = var32;
                        }
                     }
                  }

                  if (var5 != null && !var29) {
                     Direction var43 = var22.getOpposite();
                     if (in(var28, var6, var7)
                        && !var8.contains(var28)
                        && var15.floor(var28.down())
                        && !Cells.hazardAt(var0, var28.down())
                        && !Cells.hazardAt(var0, var28)
                        && !Cells.hazardAt(var0, var28.up())) {
                        BlockPos var40 = var28.up();
                        ArrayList var44 = new ArrayList();
                        int var45 = -1;
                        if (var15.mineable(var40)) {
                           if (var15.passable(var28) || var15.mineable(var28)) {
                              var45 = var15.planeCost(var40, var43, var44);
                           }
                        } else if (var15.passable(var40) && var15.mineable(var28)) {
                           var45 = var15.planeCost(var28, Direction.UP, var44);
                        }

                        if (var45 >= 0 && !var44.contains(var28.down())) {
                           push(var11, var12, var13, var19, var28, 4.0 + var45, var3, var9, PathFinder.Kind.TUNNEL, var44, null);
                        }
                     }

                     int var24;
                     BlockPos var25;
                     ArrayList var26;
                     BlockPos var41;
                     if (in(var41 = var28.up(), var6, var7)
                        && !var8.contains(var41)
                        && var15.passable(var20.up(2))
                        && var15.floor(var28)
                        && !Cells.hazardAt(var0, var28)
                        && var15.mineable(var25 = var41.up())
                        && (var15.passable(var41) || var15.mineable(var41))
                        && !Cells.hazardAt(var0, var25.up())
                        && (var24 = var15.planeCost(var25, var43, var26 = new ArrayList())) >= 0
                        && !var26.contains(var28)) {
                        push(var11, var12, var13, var19, var41, 5.2 + var24, var3, var9, PathFinder.Kind.UP, var26, null);
                     }

                     BlockPos var23;
                     if (in(var23 = var28.down(), var6, var7)
                        && !var8.contains(var23)
                        && var15.floor(var23.down())
                        && !Cells.hazardAt(var0, var23.down())
                        && var15.mineable(var28)
                        && (var15.passable(var23) || var15.mineable(var23))
                        && (var15.passable(var28.up()) || var15.mineable(var28.up()))
                        && (var24 = var15.planeCost(var28, var43, var26 = new ArrayList())) >= 0
                        && !var26.contains(var23.down())
                        && !var26.contains(var20.down())) {
                        push(var11, var12, var13, var19, var23, 4.6 + var24, var3, var9, PathFinder.Kind.DOWN, var26, null);
                     }
                  }
               }
            }

            // 1.14.2: đường có đào cũng đi chéo được (trước chỉ 4 hướng → đi hình bậc thang)
            for (int var46 : new int[]{-1, 1}) {
               for (int var47 : new int[]{-1, 1}) {
                  BlockPos var48 = var20.add(var46, 0, var47);
                  BlockPos var49 = var20.add(var46, 0, 0);
                  BlockPos var50 = var20.add(0, 0, var47);
                  if (in(var48, var6, var7)
                     && !var8.contains(var48)
                     && Cells.loaded(var0, var48)
                     && var15.standable(var48)
                     && var15.passable(var49)
                     && var15.passable(var49.up())
                     && var15.passable(var50)
                     && var15.passable(var50.up())) {
                     push(var11, var12, var13, var19, var48, Cells.fluidAt(var0, var48) ? 2.9 : 1.45, var3, var9, PathFinder.Kind.WALK, null, null);
                  }
               }
            }

            if (var5 != null) {
               BlockPos var34 = var20.down();
               int var17;
               ArrayList var18;
               if (in(var34, var6, var7)
                  && !var8.contains(var34)
                  && var15.mineable(var34)
                  && var15.floor(var34.down())
                  && !Cells.hazardAt(var0, var34.down())
                  && (var17 = var15.planeCost(var34, Direction.UP, var18 = new ArrayList())) >= 0) {
                  push(var11, var12, var13, var19, var34, 4.0 + var17, var3, var9, PathFinder.Kind.DIGDOWN, var18, null);
               }

               if (var5.pillar) {
                  BlockPos var33 = var20.up();
                  BlockPos var35 = var20.up(2);
                  if (in(var33, var6, var7) && !var8.contains(var33) && var15.floor(var20.down()) && !Cells.hazardAt(var0, var35)) {
                     ArrayList var36 = new ArrayList();
                     int var38 = var15.passable(var35) ? 0 : (var15.mineable(var35) ? var15.planeCost(var35, Direction.DOWN, var36) : -1);
                     if (var38 >= 0) {
                        push(
                           var11,
                           var12,
                           var13,
                           var19,
                           var33,
                           (var36.isEmpty() ? 4.0 : 6.5) + var38,
                           var3,
                           var9,
                           PathFinder.Kind.PILLAR,
                           var36,
                           var20.toImmutable()
                        );
                     }
                  }
               }
            }
         }
      }

      return null;
   }

   public static PathFinder.Strike strikeFor(MinecraftClient var0, BlockPos var1, BlockPos var2) {
      int var3 = var2.getY() - var1.getY();
      int var4 = var2.getX() - var1.getX();
      int var5 = var2.getZ() - var1.getZ();
      if (var4 != 0 || var5 != 0) {
         if (Math.abs(var4) + Math.abs(var5) != 1) {
            return null;
         } else {
            Direction var6 = Direction.getFacing(var4, 0.0F, var5);
            Direction var7 = var6.getOpposite();
            BlockPos var8 = var1.offset(var6);
            if (var3 == 0) {
               if (Cells.passable(var0, var2) && Cells.passable(var0, var2.up())) {
                  return null;
               } else if (Cells.mineable(var0, var2.up())) {
                  return new PathFinder.Strike(var2.up(), var7);
               } else {
                  return Cells.mineable(var0, var2) && Cells.passable(var0, var2.up()) ? new PathFinder.Strike(var2, Direction.UP) : INVALID;
               }
            } else if (var3 == 1) {
               if (!Cells.passable(var0, var1.up(2))) {
                  return Cells.mineable(var0, var1.up(2)) ? new PathFinder.Strike(var1.up(2), Direction.DOWN) : INVALID;
               } else if (Cells.passable(var0, var2) && Cells.passable(var0, var2.up())) {
                  return null;
               } else {
                  return Cells.mineable(var0, var2.up()) ? new PathFinder.Strike(var2.up(), var7) : INVALID;
               }
            } else if (var3 == -1) {
               if (Cells.mineable(var0, var8)) {
                  return new PathFinder.Strike(var8, var7);
               } else if (Cells.mineable(var0, var8.up())) {
                  return new PathFinder.Strike(var8.up(), var7);
               } else {
                  return Cells.passable(var0, var8) && Cells.passable(var0, var8.up()) && Cells.passable(var0, var2) ? null : INVALID;
               }
            } else {
               return null;
            }
         }
      } else if (var3 != -1) {
         return null;
      } else if (Cells.mineable(var0, var1.down())) {
         return new PathFinder.Strike(var1.down(), Direction.UP);
      } else {
         return Cells.passable(var0, var1.down()) ? null : INVALID;
      }
   }

   private static boolean in(BlockPos var0, int var1, int var2) {
      return var0.getY() >= var1 && var0.getY() <= var2;
   }

   private static void push(
      PriorityQueue<PathFinder.Node> var0,
      Map<BlockPos, Double> var1,
      double var2,
      PathFinder.Node var4,
      BlockPos var5,
      double var6,
      BlockPos var8,
      double var9,
      PathFinder.Kind var11,
      List<BlockPos> var12,
      BlockPos var13
   ) {
      double var14 = var4.g + var6;
      if (var14 + 1.0E-6 < var1.getOrDefault(var5, Double.MAX_VALUE)) {
         var1.put(var5, var14);
         var0.add(new PathFinder.Node(var5, var14, var14 + h(var5, var8, var2) * var9, var4, var11, var12, var13));
      }
   }

   private static double h(BlockPos var0, BlockPos var1, double var2) {
      if (var1 == null) {
         return 0.0;
      } else {
         // 1.14.2: khoảng cách có tính đi chéo (ô chéo giá 1,45) thay cho Manhattan
         int var4 = Math.abs(var0.getX() - var1.getX());
         int var5 = Math.abs(var0.getZ() - var1.getZ());
         return Math.max(var4, var5) + 0.45 * Math.min(var4, var5) + Math.abs(var0.getY() - var1.getY()) * var2;
      }
   }

   private static List<PathFinder.Step> build(PathFinder.Node var0) {
      ArrayList var1 = new ArrayList();

      for (PathFinder.Node var2 = var0; var2 != null; var2 = var2.parent) {
         var1.add(new PathFinder.Step(var2.pos, var2.kind != PathFinder.Kind.WALK, var2.kind));
      }

      Collections.reverse(var1);
      return var1;
   }

   public static Map<BlockPos, Integer> distances(MinecraftClient var0, BlockPos var1, int var2, int var3, int var4, int var5, Set<BlockPos> var6) {
      return distances(var0, var1, var2, var3, var4, var5, var6, false);
   }

   public static Map<BlockPos, Integer> distances(MinecraftClient var0, BlockPos var1, int var2, int var3, int var4, int var5, Set<BlockPos> var6, boolean var7) {
      HashMap var8 = new HashMap();
      ArrayDeque var9 = new ArrayDeque();
      AutoBuildNavigation var10 = new AutoBuildNavigation(var0, var1, null, var3, var4, var7, false, var6);
      var8.put(var1, 0);
      var9.add(var1);
      var10.beginBatch();

      try {
         while (!var9.isEmpty() && var8.size() < var5) {
            BlockPos var11 = (BlockPos)var9.remove();
            int var12 = (Integer)var8.get(var11);

            for (PathSearch.Edge var14 : var10.neighbors(AutoBuildNavigation.cell(var11))) {
               BlockPos var15 = AutoBuildNavigation.pos(var14.to());
               if (Math.abs(var15.getX() - var1.getX()) <= var2 && Math.abs(var15.getZ() - var1.getZ()) <= var2 && !var8.containsKey(var15)) {
                  var8.put(var15, var12 + (var14.scaffold() ? 20 : 1));
                  var9.add(var15);
                  if (var8.size() >= var5) {
                     break;
                  }
               }
            }
         }
      } finally {
         var10.endBatch();
      }

      return var8;
   }

   private static Map<BlockPos, Integer> legacyDistances(MinecraftClient var0, BlockPos var1, int var2, int var3, int var4, int var5, Set<BlockPos> var6) {
      HashMap var7 = new HashMap();
      ArrayDeque var8 = new ArrayDeque();
      var7.put(var1, 0);
      var8.add(var1);

      while (!var8.isEmpty() && var7.size() < var5) {
         BlockPos var9 = (BlockPos)var8.poll();
         int var10 = (Integer)var7.get(var9);

         for (Direction var12 : Type.HORIZONTAL) {
            BlockPos var13 = var9.offset(var12);
            if (Math.abs(var13.getX() - var1.getX()) <= var2 && Math.abs(var13.getZ() - var1.getZ()) <= var2 && Cells.loaded(var0, var13)) {
               BlockPos var14 = null;
               if (Cells.standable(var0, var13)) {
                  var14 = var13;
               } else if (Cells.standable(var0, var13.up()) && Cells.passable(var0, var9.up(2))) {
                  var14 = var13.up();
               } else if (Cells.passable(var0, var13) && Cells.passable(var0, var13.up())) {
                  BlockPos var15 = var13;

                  for (int var16 = 1; var16 <= 3; var16++) {
                     BlockPos var17 = var15.down();
                     if (Cells.standable(var0, var17)) {
                        var14 = var17;
                        break;
                     }

                     if (!Cells.passable(var0, var17)) {
                        break;
                     }

                     var15 = var17;
                  }
               }

               if (var14 != null && var14.getY() >= var3 && var14.getY() <= var4 && !var6.contains(var14) && !var7.containsKey(var14)) {
                  var7.put(var14, var10 + 1);
                  var8.add(var14);
               }
            }
         }
      }

      return var7;
   }

   public static final class Dig {
      public Set<BlockPos> preserved = Set.of();
      public final Region region;
      public final boolean outside;
      public final int minBreakY;
      public final boolean pillar;
      public boolean spill;

      public Dig(Region var1, boolean var2, int var3, boolean var4) {
         this.region = var1;
         this.outside = var2;
         this.minBreakY = var3;
         this.pillar = var4;
      }

      public boolean breakable(MinecraftClient var1, BlockPos var2) {
         if (this.preserved.contains(var2)) {
            return false;
         } else if (Cells.passable(var1, var2)) {
            return true;
         } else if (!Cells.mineable(var1, var2)) {
            return false;
         } else {
            boolean var3 = this.region != null && this.region.inside(var2);
            if (!var3 && !this.outside) {
               return false;
            } else {
               return var3 && var2.getY() < this.minBreakY ? false : !PathFinder.fluidNear(var1, var2);
            }
         }
      }

      public boolean planeOk(MinecraftClient var1, BlockPos var2, Direction var3) {
         for (BlockPos var5 : Cells.plane(var2, var3)) {
            if (Cells.mineable(var1, var5) && !this.breakable(var1, var5)) {
               return false;
            }
         }

         return true;
      }

      public boolean strikeAllowed(MinecraftClient var1, BlockPos var2, Direction var3) {
         if (this.preserved.contains(var2)) {
            return false;
         } else {
            for (BlockPos var5 : Cells.plane(var2, var3)) {
               if (Cells.mineable(var1, var5)) {
                  if (this.preserved.contains(var5)) {
                     return false;
                  }

                  if (PathFinder.fluidNear(var1, var5)) {
                     return false;
                  }

                  boolean var6 = this.region != null && this.region.inside(var5);
                  if (var6 && var5.getY() < this.minBreakY) {
                     return false;
                  }

                  if (!var6 && !this.outside && !this.spill) {
                     return false;
                  }
               }
            }

            return true;
         }
      }
   }

   public static enum Kind {
      WALK,
      TUNNEL,
      UP,
      DOWN,
      DIGDOWN,
      PILLAR,
      BRIDGE,
      JUMP;
   }

   private record Node(BlockPos pos, double g, double f, PathFinder.Node parent, PathFinder.Kind kind, List<BlockPos> broken, BlockPos placed) {
   }

   public record Step(BlockPos feet, boolean mine, PathFinder.Kind kind) {
   }

   public record Strike(BlockPos c, Direction f) {
   }

   private static final class View {
      final MinecraftClient mc;
      final PathFinder.Dig dig;
      final HashSet<BlockPos> gone = new HashSet<>();
      final HashSet<BlockPos> added = new HashSet<>();

      View(MinecraftClient var1, PathFinder.Dig var2) {
         this.mc = var1;
         this.dig = var2;
      }

      void load(PathFinder.Node var1) {
         this.gone.clear();
         this.added.clear();
         PathFinder.Node var2 = var1;

         for (int var3 = 0; var2 != null && var3 < 40; var3++) {
            if (var2.broken != null) {
               this.gone.addAll(var2.broken);
            }

            if (var2.placed != null) {
               this.added.add(var2.placed);
            }

            var2 = var2.parent;
         }
      }

      boolean passable(BlockPos var1) {
         return this.added.contains(var1) ? false : this.gone.contains(var1) || Cells.passable(this.mc, var1);
      }

      boolean solid(BlockPos var1) {
         return this.added.contains(var1) ? true : !this.gone.contains(var1) && Cells.solid(this.mc, var1);
      }

      boolean floor(BlockPos var1) {
         return this.added.contains(var1) ? true : !this.gone.contains(var1) && Cells.floor(this.mc, var1);
      }

      boolean mineable(BlockPos var1) {
         return !this.gone.contains(var1) && !this.added.contains(var1) && Cells.mineable(this.mc, var1);
      }

      boolean standable(BlockPos var1) {
         if (!Cells.loaded(this.mc, var1)) {
            return false;
         } else if (this.gone.isEmpty() && this.added.isEmpty()) {
            return Cells.standable(this.mc, var1);
         } else if (this.passable(var1) && this.passable(var1.up()) && this.floor(var1.down())) {
            for (Direction var3 : Type.HORIZONTAL) {
               if (Cells.hazardAt(this.mc, var1.offset(var3)) || Cells.hazardAt(this.mc, var1.up().offset(var3))) {
                  return false;
               }
            }

            return !Cells.hazardAt(this.mc, var1.up(2)) && !Cells.hazardAt(this.mc, var1);
         } else {
            return false;
         }
      }

      boolean breakable(BlockPos var1) {
         if (this.dig.preserved.contains(var1)) {
            return false;
         } else if (this.passable(var1)) {
            return true;
         } else if (!this.mineable(var1)) {
            return false;
         } else {
            boolean var2 = this.dig.region != null && this.dig.region.inside(var1);
            if (!var2 && !this.dig.outside) {
               return false;
            } else {
               return var2 && var1.getY() < this.dig.minBreakY ? false : !PathFinder.fluidNear(this.mc, var1);
            }
         }
      }

      int planeCost(BlockPos var1, Direction var2, List<BlockPos> var3) {
         byte var4 = 0;

         for (BlockPos var6 : Cells.plane(var1, var2)) {
            if (this.mineable(var6)) {
               boolean var7 = this.dig.region != null && this.dig.region.inside(var6);
               if (!var7 && !this.dig.outside) {
                  if (!this.dig.spill || PathFinder.fluidNear(this.mc, var6)) {
                     return -1;
                  }

                  var4 += 4;
               } else if (!this.breakable(var6)) {
                  return -1;
               }

               var3.add(var6.toImmutable());
            }
         }

         return var4;
      }
   }
}
