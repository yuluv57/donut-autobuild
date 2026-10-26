package com.donutautobuild.build;

import com.google.common.collect.UnmodifiableIterator;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.litematica.util.PositionUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

public final class SchematicPlan {
   // 3.0.98: không final — vào lại server Litematica tạo placement mới (cùng vị trí) → gắn bản vẽ cũ vào placement mới, không nạp lại
   public SchematicPlacement placement;
   public String fingerprint;
   /** 3.0.108: vị trí/hướng/tên placement lúc đọc bản vẽ (không tính bật/tắt) */
   public String layout;
   private final List<SchematicPlan.Region> regions = new ArrayList<>();
   private final Map<BlockPos, SchematicPlan.Target> blocks = new LinkedHashMap<>();
   private int regionIndex;
   private int x;
   private int y;
   private int z;
   public long scanned;
   public long volume;
   public List<SchematicPlan.Target> targets = List.of();
   /** 3.0.90: crafter trong bản vẽ → các ô bị tắt (bit i = ô i, đọc từ NBT "disabled_slots" của block entity trong .litematic) */
   public final Map<BlockPos, Integer> crafterMasks = new LinkedHashMap<>();
   /** 3.0.123: khối chứa (dropper, dispenser, hopper, rương, thùng, lò…) có đồ trong bản vẽ → danh sách "Items" (NBT đã qua DataFixer) */
   public final Map<BlockPos, net.minecraft.nbt.NbtList> containerItems = new LinkedHashMap<>();
   /** số crafter trong bản vẽ có block entity (đọc được cấu hình) */
   public int crafterWithData;
   public boolean loaded;
   public int minY;
   public int maxY;
   public int minX;
   public int maxX;
   public int minZ;
   public int maxZ;

   public boolean inside(BlockPos var1) {
      return var1.getX() >= this.minX
         && var1.getX() <= this.maxX
         && var1.getY() >= this.minY
         && var1.getY() <= this.maxY
         && var1.getZ() >= this.minZ
         && var1.getZ() <= this.maxZ;
   }

   /** 3.0.107: ghi chú khi mod tự chọn / tự bật placement (BuildController báo ra chat) */
   public String autoNote;
   /** 3.0.107: placement đã xây lần trước (tên + gốc, từ file lưu tiến độ) → ưu tiên khi phải tự chọn */
   public static String hintName;
   public static BlockPos hintOrigin;

   /**
    * 3.0.107 (người dùng: "chọn Litematica rồi nhưng cứ hiện là chưa chọn"): mod chỉ đọc placement ĐANG ĐƯỢC CHỌN trong danh sách
    * "Schematic Placements" và phải đang BẬT. Chọn file ở "Loaded Schematics" (chưa tạo placement), placement bị tắt (nút Enabled),
    * hoặc vừa vào lại server Litematica chưa kịp chọn lại → getSelectedSchematicPlacement() = null / isEnabled() = false → báo
    * "Chọn và bật một placement". Giờ: placement đang chọn mà tắt → tự bật; chưa chọn cái nào mà Litematica có placement →
    * tự chọn placement gần người nhất; không có placement nào → báo rõ phải tạo placement.
    */
   private static String ensureSelected() {
      fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager var0 = DataManager.getSchematicPlacementManager();
      SchematicPlacement var1 = var0.getSelectedSchematicPlacement();
      List<SchematicPlacement> var2 = var0.getAllSchematicsPlacements();
      com.donutautobuild.AutoBuildMod.LOGGER
         .info(
            "[AutoBuild] Litematica: placement đang chọn = {}, đang bật = {}, tổng số placement = {}",
            new Object[]{var1 == null ? "(không có)" : var1.getName(), var1 != null && var1.isEnabled(), var2 == null ? 0 : var2.size()}
         );
      if (var1 != null && var1.isEnabled()) {
         return null;
      } else if (var1 != null) {
         var1.setEnabled(true);
         return "Placement \"" + var1.getName() + "\" đang TẮT trong Litematica → mod đã tự bật";
      } else if (var2 != null && !var2.isEmpty()) {
         net.minecraft.client.MinecraftClient var3 = net.minecraft.client.MinecraftClient.getInstance();
         SchematicPlacement var4 = null;
         double var5 = Double.MAX_VALUE;

         for (SchematicPlacement var8 : var2) {
            BlockPos var9 = var8.getOrigin();
            double var10 = var3 != null && var3.player != null
               ? var3.player.getPos().squaredDistanceTo((double)var9.getX() + 0.5, (double)var9.getY() + 0.5, (double)var9.getZ() + 0.5)
               : 0.0;
            // đúng placement đã xây lần trước (cùng tên + gốc) luôn được chọn trước; cùng tên thì ưu tiên sau đó
            if (hintName != null && hintName.equals(var8.getName())) {
               var10 -= hintOrigin != null && hintOrigin.equals(var9) ? 4.0E12 : 2.0E12;
            }

            if (!var8.isEnabled()) {
               var10 += 1.0E12;
            }

            if (var4 == null || var10 < var5) {
               var4 = var8;
               var5 = var10;
            }
         }

         var0.setSelectedSchematicPlacement(var4);
         if (!var4.isEnabled()) {
            var4.setEnabled(true);
         }

         return "Litematica chưa chọn placement nào (đang có " + var2.size() + ") → mod tự chọn \"" + var4.getName() + "\" gần người nhất (gốc "
            + var4.getOrigin().toShortString()
            + ")";
      } else {
         return null;
      }
   }

   public SchematicPlan() {
      try {
         this.autoNote = ensureSelected();
      } catch (Throwable var12) {
         com.donutautobuild.AutoBuildMod.LOGGER.warn("[AutoBuild] Không tự chọn được placement: {}", var12.toString());
      }

      this.placement = DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();
      if (this.placement != null && this.placement.isEnabled()) {
         this.fingerprint = fingerprint(this.placement);
         this.layout = layoutKey(this.placement);
         UnmodifiableIterator var1 = this.placement.getEnabledRelativeSubRegionPlacements().values().iterator();

         while (var1.hasNext()) {
            SubRegionPlacement var2 = (SubRegionPlacement)var1.next();
            LitematicaBlockStateContainer var3 = this.placement.getSchematic().getSubRegionContainer(var2.getName());
            BlockPos var4 = this.placement.getSchematic().getAreaSize(var2.getName());
            if (var3 == null || var4 == null) {
               throw new IllegalStateException("Không đọc được region " + var2.getName());
            }

            BlockPos var5 = new BlockPos(Math.min(0, var4.getX() + 1), Math.min(0, var4.getY() + 1), Math.min(0, var4.getZ() + 1));
            BlockPos var6 = PositionUtils.getTransformedBlockPos(var2.getPos(), this.placement.getMirror(), this.placement.getRotation())
               .add(this.placement.getOrigin());
            Map<BlockPos, net.minecraft.nbt.NbtCompound> var7 = null;

            try {
               var7 = this.placement.getSchematic().getBlockEntityMapForRegion(var2.getName());
            } catch (Throwable var9) {
               var7 = null;
            }

            this.regions.add(new SchematicPlan.Region(var2, var3, var5, var6, var7));
            this.volume = this.volume + (long)var3.getSize().getX() * (long)var3.getSize().getY() * (long)var3.getSize().getZ();
         }

         if (this.volume == 0L) {
            throw new IllegalStateException("Placement không có region được bật.");
         } else if (this.volume > 32000000L) {
            throw new IllegalStateException("Placement quá lớn; bật từng region dưới 32 triệu ô.");
         }
      } else {
         throw new IllegalStateException(
            "Litematica chưa có placement nào: mở menu Litematica → Load Schematics → chọn file → bấm \"Load Schematic\" (tạo placement), rồi nhấn K."
         );
      }
   }

   public boolean loadTick() {
      long var1 = System.nanoTime() + 4000000L;

      while (this.regionIndex < this.regions.size() && System.nanoTime() < var1) {
         SchematicPlan.Region var3 = this.regions.get(this.regionIndex);
         BlockState var4 = var3.data().get(this.x, this.y, this.z);
         if (!var4.isAir() && !var4.isOf(Blocks.STRUCTURE_VOID)) {
            BlockPos var5 = PositionUtils.getTransformedPlacementPosition(var3.minimum().add(this.x, this.y, this.z), this.placement, var3.placement())
               .add(var3.offset());
            BlockMirror var6 = var3.placement().getMirror();
            if (var6 != BlockMirror.NONE
               && (this.placement.getRotation() == BlockRotation.CLOCKWISE_90 || this.placement.getRotation() == BlockRotation.COUNTERCLOCKWISE_90)) {
               var6 = var6 == BlockMirror.FRONT_BACK ? BlockMirror.LEFT_RIGHT : BlockMirror.FRONT_BACK;
            }

            var4 = var4.mirror(this.placement.getMirror()).mirror(var6).rotate(this.placement.getRotation().rotate(var3.placement().getRotation()));
            SchematicPlan.Target var7 = this.blocks.putIfAbsent(var5, new SchematicPlan.Target(var5, var4));
            if (var3.be() != null && isCrafter(var4)) {
               net.minecraft.nbt.NbtCompound var12 = var3.be().get(new BlockPos(this.x, this.y, this.z));
               if (var12 != null) {
                  int var13 = 0;
                  if (var12.contains("disabled_slots")) {
                     int[] var14 = var12.getIntArray("disabled_slots");
                     if (var14 != null) {
                        for (int var17 : var14) {
                           if (var17 >= 0 && var17 < 9) {
                              var13 |= 1 << var17;
                           }
                        }
                     }
                  }

                  this.crafterMasks.put(var5.toImmutable(), var13);
                  this.crafterWithData++;
               }
            }
            if (var3.be() != null && ContainerFill.fillable(var4)) {
               net.minecraft.nbt.NbtCompound var15 = var3.be().get(new BlockPos(this.x, this.y, this.z));
               if (var15 != null && var15.contains("Items", 9)) {
                  net.minecraft.nbt.NbtList var16 = var15.getList("Items", 10);
                  if (!var16.isEmpty()) {
                     this.containerItems.put(var5.toImmutable(), var16.copy());
                  }
               }
            }
            if (var7 != null && var7.state() != var4) {
               throw new IllegalStateException("Region chồng nhau, khác block tại " + var5.toShortString());
            }

            if (this.blocks.size() > 750000) {
               throw new IllegalStateException("Quá 750.000 block; hãy chia theo region.");
            }
         }

         this.scanned++;
         if (++this.x >= var3.data().getSize().getX()) {
            this.x = 0;
            if (++this.z >= var3.data().getSize().getZ()) {
               this.z = 0;
               if (++this.y >= var3.data().getSize().getY()) {
                  this.y = 0;
                  this.regionIndex++;
               }
            }
         }
      }

      if (this.regionIndex < this.regions.size()) {
         return false;
      } else {
         ArrayList var8 = new ArrayList<>(this.blocks.values());
         var8.sort(
            Comparator.<SchematicPlan.Target>comparingInt(var0 -> var0.pos().getY())
               .thenComparingInt(var0 -> var0.pos().getZ())
               .thenComparingInt(var0 -> var0.pos().getX())
         );
         this.targets = List.copyOf(var8);
         if (!this.targets.isEmpty()) {
            this.minY = this.targets.getFirst().pos().getY();
            this.maxY = this.targets.getLast().pos().getY();
            this.minX = this.minZ = Integer.MAX_VALUE;
            this.maxX = this.maxZ = Integer.MIN_VALUE;

            for (SchematicPlan.Target var11 : this.targets) {
               this.minX = Math.min(this.minX, var11.pos().getX());
               this.maxX = Math.max(this.maxX, var11.pos().getX());
               this.minZ = Math.min(this.minZ, var11.pos().getZ());
               this.maxZ = Math.max(this.maxZ, var11.pos().getZ());
            }
         }

         this.loaded = true;
         return true;
      }
   }

   public SchematicPlan.Target at(BlockPos var1) {
      return this.blocks.get(var1);
   }

   /** vị trí/hướng/region của placement, KHÔNG tính đối tượng schematic trong bộ nhớ (vào lại server thì đối tượng đổi, bản vẽ không đổi) */
   public static String layoutKey(SchematicPlacement var0) {
      StringBuilder var1 = new StringBuilder(String.valueOf(var0.getName()))
         .append('@')
         .append(var0.getOrigin())
         .append(':')
         .append(var0.getMirror())
         .append(':')
         .append(var0.getRotation());

      for (SubRegionPlacement var3 : var0.getAllSubRegionsPlacements()) {
         var1.append('|').append(var3.getName()).append(var3.getPos()).append(var3.getMirror()).append(var3.getRotation()).append(var3.isEnabled());
      }

      try {
         java.io.File var4 = var0.getSchematicFile();
         var1.append('#').append(var4 == null ? "-" : var4.getAbsolutePath());
      } catch (Throwable var5) {
      }

      return var1.toString();
   }

   /** gắn bản vẽ đã nạp vào placement var1 (cùng layoutKey) */
   public void rebind(SchematicPlacement var1) {
      this.placement = var1;
      this.fingerprint = fingerprint(var1);
      this.layout = layoutKey(var1);
   }

   public boolean unchanged() {
      return DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement() == this.placement
         && this.fingerprint.equals(fingerprint(this.placement));
   }

   private static String fingerprint(SchematicPlacement var0) {
      StringBuilder var1 = new StringBuilder(var0.getOrigin() + ":" + var0.getMirror() + ":" + var0.getRotation() + ":" + var0.isEnabled());

      for (SubRegionPlacement var3 : var0.getAllSubRegionsPlacements()) {
         var1.append('|').append(var3.getName()).append(var3.getPos()).append(var3.getMirror()).append(var3.getRotation()).append(var3.isEnabled());
      }

      var1.append(System.identityHashCode(var0.getSchematic()));
      return var1.toString();
   }

   public static boolean isCrafter(BlockState var0) {
      return var0 != null && net.minecraft.registry.Registries.BLOCK.getId(var0.getBlock()).getPath().equals("crafter");
   }

   private static record Region(
      SubRegionPlacement placement, LitematicaBlockStateContainer data, BlockPos minimum, BlockPos offset, Map<BlockPos, net.minecraft.nbt.NbtCompound> be
   ) {
   }

   public static record Target(BlockPos pos, BlockState state) {
   }
}
