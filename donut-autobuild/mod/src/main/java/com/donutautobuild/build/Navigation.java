package com.donutautobuild.build;

import com.donutautobuild.BuildConfig;
import com.donutautobuild.logic.PathSearch;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.shape.VoxelShape;

public final class Navigation implements PathSearch.Terrain {
   private final MinecraftClient mc;
   private final BuildConfig config;
   private final SchematicPlan plan;
   private final PathSearch.Cell start;
   private final boolean allowScaffold;
   private final Integer layerLimit;
   /** 3.0.99: đang xây tầng cao → đi xuống thấp hơn mức này rất đắt (không tự rơi xuống rồi phải /home lên lại) */
   public Integer minFeetY;
   private final Map<PathSearch.Cell, Boolean> clearCache = new HashMap<>();
   private final Map<PathSearch.Cell, Boolean> standCache = new HashMap<>();
   private boolean batching;
   public boolean escape;
   public int dropLimit = 3;
   public boolean walkOnly;
   private int bMinX = Integer.MIN_VALUE;
   private int bMaxX = Integer.MAX_VALUE;
   private int bMinY = Integer.MIN_VALUE;
   private int bMaxY = Integer.MAX_VALUE;
   private int bMinZ = Integer.MIN_VALUE;
   private int bMaxZ = Integer.MAX_VALUE;
   public static final double BRIDGE_COST = 20.0;
   public static final double PILLAR_COST = 20.0;

   @Override
   public void beginBatch() {
      this.clearCache.clear();
      this.standCache.clear();
      this.batching = true;
   }

   @Override
   public void endBatch() {
      this.batching = false;
      this.clearCache.clear();
      this.standCache.clear();
   }

   public void bound(Collection<PathSearch.Cell> var1, Collection<PathSearch.Cell> var2, int var3) {
      this.bMinX = this.bMinY = this.bMinZ = Integer.MAX_VALUE;
      this.bMaxX = this.bMaxY = this.bMaxZ = Integer.MIN_VALUE;

      for (Collection<PathSearch.Cell> var5 : List.<Collection<PathSearch.Cell>>of(var1, var2)) {
         for (PathSearch.Cell var7 : var5) {
            this.bMinX = Math.min(this.bMinX, var7.x());
            this.bMaxX = Math.max(this.bMaxX, var7.x());
            this.bMinY = Math.min(this.bMinY, var7.y());
            this.bMaxY = Math.max(this.bMaxY, var7.y());
            this.bMinZ = Math.min(this.bMinZ, var7.z());
            this.bMaxZ = Math.max(this.bMaxZ, var7.z());
         }
      }

      this.bMinX -= var3;
      this.bMaxX += var3;
      this.bMinZ -= var3;
      this.bMaxZ += var3;
      this.bMinY -= 4;
      this.bMaxY += 6;
   }

   private boolean inBounds(PathSearch.Cell var1) {
      return var1.x() >= this.bMinX
         && var1.x() <= this.bMaxX
         && var1.y() >= this.bMinY
         && var1.y() <= this.bMaxY
         && var1.z() >= this.bMinZ
         && var1.z() <= this.bMaxZ;
   }

   public Navigation(MinecraftClient var1, BuildConfig var2, SchematicPlan var3, boolean var4) {
      this(var1, var2, var3, var4, null);
   }

   public Navigation(MinecraftClient var1, BuildConfig var2, SchematicPlan var3, boolean var4, Integer var5) {
      this.mc = var1;
      this.config = var2;
      this.plan = var3;
      this.start = cell(var1.player.getBlockPos());
      this.allowScaffold = var4;
      this.layerLimit = var5;
   }

   public static PathSearch.Cell cell(BlockPos var0) {
      return new PathSearch.Cell(var0.getX(), var0.getY(), var0.getZ());
   }

   public static BlockPos pos(PathSearch.Cell var0) {
      return new BlockPos(var0.x(), var0.y(), var0.z());
   }

   public static Vec3d feet(PathSearch.Cell var0) {
      return new Vec3d((double)var0.x() + 0.5, (double)var0.y(), (double)var0.z() + 0.5);
   }

   public static Vec3d eye(PathSearch.Cell var0) {
      return feet(var0).add(0.0, 1.62, 0.0);
   }

   public boolean clear(PathSearch.Cell var1) {
      if (!this.batching) {
         return this.clearLive(var1);
      } else {
         Boolean var2 = this.clearCache.get(var1);
         if (var2 != null) {
            return var2;
         } else {
            boolean var3 = this.clearLive(var1);
            this.clearCache.put(var1, var3);
            return var3;
         }
      }
   }

   public static final java.util.Set<BlockPos> GHOSTS = new java.util.HashSet<>();

   public static final java.util.Set<BlockPos> AVOID = new java.util.HashSet<>();

   private boolean clearLive(PathSearch.Cell var1) {
      BlockPos var2 = pos(var1);
      if (GHOSTS.contains(var2) || GHOSTS.contains(var2.up()) || AVOID.contains(var2)) {
         return false;
      } else if (this.plan != null && (this.snowCell(var2) || this.snowCell(var2.up()))) {
         return false;
      } else if (this.mc.world.isChunkLoaded(var2)
         && this.mc.world.getWorldBorder().contains(var2)
         && var2.getY() >= this.mc.world.getBottomY() + 1
         && var2.getY() + 2 <= this.mc.world.getTopYInclusive()) {
         // 3.0.84: nước nông (ngập tới chân, đầu còn trên mặt nước) đi được — trước đây mọi ô có nước đều bị coi là
         // nguy hiểm → đứng trong dòng nước ở tầng -49 thì "đứng được: false, lối ra: 0" và kẹt 8 tiếng liền.
         // Lava/lửa vẫn cấm; đầu ngập nước thì chỉ chấp nhận đúng ô đang đứng (để còn tìm đường ra).
         BlockState var20 = this.mc.world.getBlockState(var2);
         BlockState var21 = this.mc.world.getBlockState(var2.up());
         if (!var1.equals(this.start) && TntGuard.stepTrigger(var20) && TntGuard.tntWithin(var2, 16, null) != null) {
            // 3.0.104: không giẫm bệ áp lực / dây bẫy / ray cảm biến gần TNT (giẫm vào là phát điện)
            return false;
         }

         return !hazardDry(var20) && !hazardDry(var21) && (!waterish(var21) || var1.equals(this.start))
            ? this.mc
               .world
               .isSpaceEmpty(
                  this.mc.player,
                  new Box(
                     (double)var1.x() + 0.18,
                     (double)var1.y() + 0.01,
                     (double)var1.z() + 0.18,
                     (double)var1.x() + 0.82,
                     (double)var1.y() + 1.81,
                     (double)var1.z() + 0.82
                  )
               )
            : false;
      } else {
         return false;
      }
   }

   public boolean standable(PathSearch.Cell var1) {
      if (!this.batching) {
         return this.standableLive(var1);
      } else {
         Boolean var2 = this.standCache.get(var1);
         if (var2 != null) {
            return var2;
         } else {
            boolean var3 = this.standableLive(var1);
            this.standCache.put(var1, var3);
            return var3;
         }
      }
   }

   private boolean standableLive(PathSearch.Cell var1) {
      if (!this.clear(var1)) {
         return false;
      } else if (GHOSTS.contains(pos(var1).down())) {
         return false;
      } else {
         BlockPos var2 = pos(var1).down();
         BlockState var3 = this.mc.world.getBlockState(var2);
         // 3.0.84: sàn là khối ngập nước (vd. lá waterlogged) vẫn đứng được; nước thường không có va chạm nên tự bị loại ở dưới
         if (hazardDry(var3)) {
            return false;
         } else if (var3.isSideSolidFullSquare(this.mc.world, var2, Direction.UP)) {
            return true;
         } else {
            VoxelShape var4 = var3.getCollisionShape(this.mc.world, var2);
            if (var4.isEmpty()) {
               return false;
            } else {
               double var5 = var4.getMax(Axis.Y);
               return var5 >= 0.8
                  && var5 <= 1.0
                  && var4.getMin(Axis.X) <= 0.3
                  && var4.getMax(Axis.X) >= 0.7
                  && var4.getMin(Axis.Z) <= 0.3
                  && var4.getMax(Axis.Z) >= 0.7;
            }
         }
      }
   }

   public boolean canScaffoldAt(BlockPos var1) {
      if (this.allowScaffold
         && this.config.scaffold
         && this.mc.world.isChunkLoaded(var1)
         && this.mc.world.getWorldBorder().contains(var1)
         && this.mc.world.getBlockState(var1).isAir()) {
         BlockState var2 = this.scaffoldState();
         if (var2 == null) {
            return false;
         } else if (this.escape && var1.getX() == this.start.x() && var1.getZ() == this.start.z()) {
            return true;
         } else if (this.layerLimit != null && var1.getY() > this.layerLimit) {
            return false;
         } else if (this.capsPendingFluid(var1)) {
            return false;
         } else if (this.watchedByObserver(var1)) {
            return false;
         } else {
            SchematicPlan.Target var3 = this.plan == null ? null : this.plan.at(var1);
            if (var3 != null && this.realPillar(var3.state())) {
               return true;
            } else {
               return var3 != null && !var3.state().isAir() && !var3.state().isReplaceable()
                  ? false
                  : var3 == null
                     || StateMatch.matches(var2, var3.state())
                     || !this.config.exactBlocks && this.config.fastMode() && Materials.swappable(var3.state(), var2);
            }
         }
      } else {
         return false;
      }
   }

   private boolean capsPendingFluid(BlockPos var1) {
      if (this.plan == null) {
         return false;
      } else {
         for (int var2 = 1; var2 <= 5; var2++) {
            BlockPos var3 = var1.down(var2);
            if (!this.mc.world.isChunkLoaded(var3)) {
               return false;
            }

            BlockState var4 = this.mc.world.getBlockState(var3);
            SchematicPlan.Target var5 = this.plan.at(var3);
            if (var5 != null
               && (var5.state().isOf(Blocks.LAVA) || var5.state().isOf(Blocks.WATER))
               && var4.getFluidState().isEmpty()) {
               return true;
            }

            if (!var4.isReplaceable()) {
               return false;
            }
         }

         return false;
      }
   }

   public boolean realPillar(BlockState var1) {
      if (StateMatch.orientationFree(var1)
         && (!var1.contains(Properties.WATERLOGGED) || !(Boolean)var1.get(Properties.WATERLOGGED))
         && var1.isFullCube(this.mc.world, BlockPos.ORIGIN)
         && !var1.hasBlockEntity()
         && !Interaction.interactable(var1)
         && !(var1.getBlock() instanceof FallingBlock)
         && var1.getFluidState().isEmpty()) {
         Item var2 = var1.getBlock().asItem();
         return var2 != Items.AIR && InventoryAccess.count(this.mc, var2) > 0;
      } else {
         return false;
      }
   }

   public BlockState pillarStateAt(BlockPos var1) {
      SchematicPlan.Target var2 = this.plan == null ? null : this.plan.at(var1);
      if (var2 != null && this.realPillar(var2.state())) {
         return var2.state();
      } else {
         return var2 != null && !var2.state().isAir() && !var2.state().isReplaceable() ? null : this.scaffoldState();
      }
   }

   public BlockState designFloor(BlockPos var1) {
      SchematicPlan.Target var2 = this.plan == null ? null : this.plan.at(var1);
      if (var2 != null && this.mc.world.isChunkLoaded(var1) && this.mc.world.getBlockState(var1).isReplaceable()) {
         if (this.layerLimit != null && var1.getY() > this.layerLimit) {
            return null;
         } else if (this.capsPendingFluid(var1)) {
            return null;
         } else {
            return this.realPillar(var2.state()) ? var2.state() : null;
         }
      } else {
         return null;
      }
   }

   public BlockState scaffoldState() {
      Identifier var1 = Identifier.tryParse(this.config.scaffoldItem);
      if (var1 == null) {
         return null;
      } else {
         Block var2 = (Block)Registries.BLOCK.get(var1);
         return var2 != Blocks.COBBLESTONE && var2 != Blocks.DIRT && var2 != Blocks.STONE && var2 != Blocks.NETHERRACK && var2 != Blocks.COBBLED_DEEPSLATE
            ? null
            : var2.getDefaultState();
      }
   }

   public List<PathSearch.Cell> startCells() {
      ArrayList var1 = new ArrayList();
      var1.add(this.start);
      Box var2 = this.mc.player.getBoundingBox();
      int var3 = this.mc.player.getBlockY();
      int var4 = this.mc.player.getY() - (double)var3 > 0.5 ? 1 : 0;

      for (int var5 = (int)Math.floor(var2.minX); var5 <= (int)Math.floor(var2.maxX); var5++) {
         for (int var6 = (int)Math.floor(var2.minZ); var6 <= (int)Math.floor(var2.maxZ); var6++) {
            for (int var7 = 0; var7 <= var4; var7++) {
               PathSearch.Cell var8 = new PathSearch.Cell(var5, var3 + var7, var6);
               if (!var1.contains(var8) && this.standable(var8)) {
                  var1.add(var8);
               }
            }
         }
      }

      return var1;
   }

   public boolean edgeStillValid(PathSearch.Cell var1, PathSearch.Cell var2) {
      // 3.0.78: bước bắc cột vừa xong (khối kê đã nằm dưới ô trên) vẫn là cạnh hợp lệ.
      // Trước đây neighbors() đòi ô dưới phải còn là không khí để "bắc được" → vừa đặt xong
      // kê là báo "Đường đi thay đổi" và bỏ dở cột đang dựng.
      if (var2.x() == var1.x() && var2.z() == var1.z() && var2.y() == var1.y() + 1 && this.standable(var2)) {
         return true;
      }

      return this.neighbors(var1).stream().anyMatch(var1x -> var1x.to().equals(var2));
   }

   // ===== 3.0.81: đi nhanh — tránh băng khi có đường khác, nhảy qua khe 1–2 ô (kiểu parkour của Baritone) =====

   /** Sàn là băng (trơn) → đi được nhưng đắt hơn chút để ưu tiên khối thường cùng tầng. */
   public boolean icy(PathSearch.Cell var1) {
      BlockPos var2 = pos(var1).down();
      return this.mc.world.isChunkLoaded(var2) && this.mc.world.getBlockState(var2).getBlock().getSlipperiness() > 0.61F;
   }

   /** Ô trên đầu (y+2) trống → nhảy không cụng đầu. */
   public boolean headroom(PathSearch.Cell var1) {
      BlockPos var2 = pos(var1).up().up();
      return this.mc.world.isChunkLoaded(var2) && this.mc.world.getBlockState(var2).getCollisionShape(this.mc.world, var2).isEmpty();
   }

   /**
    * Khe có rơi xuống cũng an toàn: đáy cách ≤ 5 ô (rơi 5 ô mất tối đa 1 tim), không lava/lửa/tuyết bột.
    * 3.0.99: nới từ 3 lên 5 ô — trên công trình nhiều tầng, khe giữa các khối thường sâu 4–5 ô (người chơi vẫn nhảy qua).
    */
   private boolean gapSafe(PathSearch.Cell var1) {
      BlockPos var2 = pos(var1);

      for (int var3 = 0; var3 <= 6; var3++) {
         BlockPos var4 = var2.down(var3);
         if (!this.mc.world.isChunkLoaded(var4)) {
            return false;
         }

         BlockState var5 = this.mc.world.getBlockState(var4);
         if (hazard(var5)) {
            return false;
         }

         if (!var5.getCollisionShape(this.mc.world, var4).isEmpty()) {
            return var3 >= 1 && var3 <= 5;
         }
      }

      return false;
   }

   /** 3.0.99: chỉ các cạnh NHẢY (qua khe, lên/xuống 1 ô) từ ô này — dùng cho tìm đường trên mặt tầng. */
   public List<PathSearch.Edge> jumpEdges(PathSearch.Cell var1) {
      ArrayList<PathSearch.Edge> var2 = new ArrayList<>(4);

      for (int[] var6 : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
         this.parkour(var1, var6[0], var6[1], var2);
      }

      return var2;
   }

   /**
    * Cạnh NHẢY qua khe (parkour), thẳng hàng theo 1 trục:
    * - cùng độ cao: khe 1 ô (đi bộ nhảy), khe 2–3 ô (chạy nhanh + nhảy, cần 1 ô chạy đà phía sau);
    * - xuống thấp hơn 1 ô: khe 1–3 ô;
    * - LÊN cao hơn 1 ô: khe 1 ô (đi bộ), khe 2 ô (chạy nhanh + nhảy, cần ô chạy đà).
    * 3.0.100 (người dùng): chạy nhanh + nhảy bay xa ~3,4 ô (tính theo vật lý vanilla, xem BuildController.jumpReach) → khe 3 ô
    * trên mặt phẳng và khe 2 ô khi nhảy lên cao vẫn còn dư. Mọi ô khe phải trống 3 ô chiều cao (không cụng đầu), rơi xuống ≤ 5 ô.
    */
   private void parkour(PathSearch.Cell var1, int var2, int var3, List<PathSearch.Edge> var4) {
      if (!this.escape && this.headroom(var1)) {
         PathSearch.Cell var5 = var1.add(-var2, 0, -var3);
         // ô chạy đà ngay phía sau (nhảy khe ≥ 2 ô phải chạy nhanh)
         boolean var6 = this.inBounds(var5) && this.clear(var5) && this.standable(var5);

         for (int var7 = 1; var7 <= 3; var7++) {
            PathSearch.Cell var8 = var1.add(var2 * var7, 0, var3 * var7);
            if (!this.inBounds(var8) || !this.clear(var8) || this.standable(var8) || !this.headroom(var8) || !this.gapSafe(var8)) {
               return;
            }

            PathSearch.Cell var9 = var1.add(var2 * (var7 + 1), 0, var3 * (var7 + 1));
            if (!this.inBounds(var9)) {
               return;
            }

            if (this.clear(var9)) {
               if (this.standable(var9) && this.headroom(var9)) {
                  // cùng độ cao
                  if (var7 == 1 || var6) {
                     var4.add(new PathSearch.Edge(var9, 1.2 + 1.2 * (double)var7, false));
                  }

                  return;
               }

               PathSearch.Cell var10 = var9.add(0, -1, 0);
               if (!this.standable(var9) && this.inBounds(var10) && this.clear(var10) && this.standable(var10)) {
                  // rơi xuống khối thấp hơn 1 ô
                  if (var7 == 1 || var6) {
                     var4.add(new PathSearch.Edge(var10, 1.6 + 1.2 * (double)var7, false));
                  }

                  return;
               }
               // ô này cũng là khe → xét khe dài hơn 1 ô
            } else {
               // ô đích là khối ngang chân → nhảy LÊN đứng trên nó (khe ≤ 2 ô, đầu không cụng trên cả đường bay)
               PathSearch.Cell var11 = var9.add(0, 1, 0);
               if (var7 <= 2 && (var7 == 1 || var6) && this.inBounds(var11) && this.clear(var11) && this.standable(var11) && this.headroom(var11)) {
                  boolean var12 = true;

                  for (int var13 = 1; var13 <= var7 && var12; var13++) {
                     var12 = this.headroom(var1.add(var2 * var13, 1, var3 * var13));
                  }

                  if (var12) {
                     var4.add(new PathSearch.Edge(var11, 3.0 + 1.4 * (double)(var7 - 1), false));
                  }
               }

               return;
            }
         }
      }
   }

   /** Cầu ngang đặt khối kê nằm ngay trên một mặt sàn đứng được → cầu cao 1 khối, vô nghĩa (đi dưới sàn là được). */
   public boolean lowBridge(PathSearch.Cell var1) {
      return this.standable(var1.add(0, -1, 0));
   }

   @Override
   public List<PathSearch.Edge> neighbors(PathSearch.Cell var1) {
      ArrayList var2 = new ArrayList(13);

      for (int[] var6 : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
         int var7 = var2.size();

         for (int var11 : new int[]{0, 1, -1}) {
            PathSearch.Cell var12 = var1.add(var6[0], var11, var6[1]);
            if (Math.abs(var12.x() - this.start.x()) <= this.config.pathRange
               && Math.abs(var12.z() - this.start.z()) <= this.config.pathRange
               && Math.abs(var12.y() - this.start.y()) <= 32
               && this.inBounds(var12)
               && this.clear(var12)
               && (var11 <= 0 || this.clear(var1.add(0, 1, 0)))
               && (var11 >= 0 || this.clear(new PathSearch.Cell(var12.x(), var1.y(), var12.z())))) {
               if (this.standable(var12)) {
                  var2.add(new PathSearch.Edge(var12, (var11 == 0 ? 1.0 : 1.8) + (this.icy(var12) ? 0.7 : 0.0) + (this.wet(var12) ? 1.5 : 0.0), false));
                  break;
               }

               if (var11 == 0
                  && !this.walkOnly
                  && (this.config.bridge || this.layerLimit != null)
                  && this.canScaffoldAt(pos(var12).down())
                  && !this.lowBridge(var12)) {
                  var2.add(new PathSearch.Edge(var12, 20.0, true));
                  break;
               }
            }
         }

         this.parkour(var1, var6[0], var6[1], var2);
         if (var2.size() == var7) {
            PathSearch.Cell var23 = var1.add(var6[0], 0, var6[1]);
            if (this.inBounds(var23) && this.clear(var23) && !this.standable(var23)) {
               for (int var25 = 2; var25 <= this.dropLimit; var25++) {
                  PathSearch.Cell var27 = var1.add(var6[0], -var25, var6[1]);
                  if (!this.inBounds(var27) || !this.clear(var27)) {
                     break;
                  }

                  if (this.standable(var27)) {
                     var2.add(new PathSearch.Edge(var27, (double)(2 + var25), false));
                     break;
                  }
               }
            }
         }
      }

      for (int[] var19 : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
         PathSearch.Cell var21 = var1.add(var19[0], 0, var19[1]);
         if (Math.abs(var21.x() - this.start.x()) <= this.config.pathRange
            && Math.abs(var21.z() - this.start.z()) <= this.config.pathRange
            && this.inBounds(var21)
            && this.clear(var1.add(var19[0], 0, 0))
            && this.clear(var1.add(0, 0, var19[1]))
            && this.standable(var21)) {
            var2.add(new PathSearch.Edge(var21, 1.45 + (this.icy(var21) ? 0.7 : 0.0) + (this.wet(var21) ? 1.5 : 0.0), false));
         }
      }

      PathSearch.Cell var14 = var1.add(0, 1, 0);
      if (!this.walkOnly && var14.y() - this.start.y() <= 24 && this.inBounds(var14) && this.clear(var14) && this.canScaffoldAt(pos(var1))) {
         var2.add(new PathSearch.Edge(var14, 20.0, true));
      }

      if (var2.isEmpty()) {
         for (int[] var22 : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            PathSearch.Cell var24 = var1.add(var22[0], 0, var22[1]);
            if (this.inBounds(var24) && this.clear(var24) && !this.standable(var24)) {
               for (int var26 = this.dropLimit + 1; var26 <= 6; var26++) {
                  PathSearch.Cell var28 = var1.add(var22[0], -var26, var22[1]);
                  if (!this.inBounds(var28) || !this.clear(var28)) {
                     break;
                  }

                  if (this.standable(var28)) {
                     var2.add(new PathSearch.Edge(var28, (double)(2 + var26), false));
                     break;
                  }
               }
            }
         }
      }

      if (this.minFeetY != null && var1.y() >= this.minFeetY) {
         for (int var29 = 0; var29 < var2.size(); var29++) {
            PathSearch.Edge var30 = (PathSearch.Edge)var2.get(var29);
            if (var30.to().y() < this.minFeetY) {
               var2.set(var29, new PathSearch.Edge(var30.to(), var30.cost() + 60.0, var30.scaffold()));
            }
         }
      }

      return var2;
   }

   public boolean watchedByObserver(BlockPos var1) {
      if (this.plan == null) {
         return false;
      } else {
         for (Direction var5 : Direction.values()) {
            BlockPos var6 = var1.offset(var5);
            SchematicPlan.Target var7 = this.plan.at(var6);
            if (var7 != null
               && var7.state().getBlock() instanceof net.minecraft.block.ObserverBlock
               && var7.state().contains(net.minecraft.state.property.Properties.FACING)
               && var7.state().get(net.minecraft.state.property.Properties.FACING) == var5.getOpposite()
               && (var7.state() == this.mc.world.getBlockState(var6) || this.mc.world.getBlockState(var6).getBlock() instanceof net.minecraft.block.ObserverBlock)) {
               SchematicPlan.Target var8 = this.plan.at(var1);
               if (var8 == null || var8.state().isAir()) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private boolean snowCell(BlockPos var1) {
      SchematicPlan.Target var2 = this.plan.at(var1);
      return var2 != null && var2.state().isOf(Blocks.POWDER_SNOW);
   }

   /** 3.0.84: ô có nước (nước nguồn/chảy, khối ngập nước) — không phải lava. */
   public static boolean waterish(BlockState var0) {
      return !var0.getFluidState().isEmpty() && !var0.isOf(Blocks.LAVA);
   }

   /** 3.0.84: nguy hiểm thật (lava, lửa, xương rồng, magma, tuyết bột, mạng nhện…) — không tính nước. */
   public static boolean hazardDry(BlockState var0) {
      return hazard(var0) && !waterish(var0);
   }

   /** Đi trong nước chậm và bị dòng đẩy → đắt hơn để ưu tiên đường khô. */
   private boolean wet(PathSearch.Cell var1) {
      return waterish(this.mc.world.getBlockState(pos(var1)));
   }

   public static boolean hazard(BlockState var0) {
      return !var0.getFluidState().isEmpty()
         || var0.isOf(Blocks.FIRE)
         || var0.isOf(Blocks.SOUL_FIRE)
         || var0.isOf(Blocks.CACTUS)
         || var0.isOf(Blocks.MAGMA_BLOCK)
         || var0.isOf(Blocks.POWDER_SNOW)
         || var0.isOf(Blocks.SWEET_BERRY_BUSH)
         || var0.isOf(Blocks.COBWEB)
         || var0.isOf(Blocks.CAMPFIRE)
         || var0.isOf(Blocks.SOUL_CAMPFIRE);
   }
}
