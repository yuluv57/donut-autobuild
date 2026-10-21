package com.donutautomine.mine;

import com.google.common.collect.UnmodifiableIterator;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement.RequiredEnabled;
import fi.dy.masa.litematica.selection.Box;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.BlockPos;

public final class Region {
   public final String name;
   public final int x0;
   public final int y0;
   public final int z0;
   public final int x1;
   public final int y1;
   public final int z1;
   private final List<Integer> floors = new ArrayList<>();

   public Region(String var1, int var2, int var3, int var4, int var5, int var6, int var7) {
      this.name = var1;
      this.x0 = Math.min(var2, var5);
      this.y0 = Math.min(var3, var6);
      this.z0 = Math.min(var4, var7);
      this.x1 = Math.max(var2, var5);
      this.y1 = Math.max(var3, var6);
      this.z1 = Math.max(var4, var7);

      for (int var8 = this.y1 - 2; var8 > this.y0; var8 -= 3) {
         this.floors.add(var8);
      }

      this.floors.add(this.y0);
   }

   public static Region dimensions(int var0, int var1, int var2, int var3, int var4, int var5) {
      if (var3 >= 1 && var4 >= 1 && var5 >= 1 && (long)var3 * var4 * var5 <= 8000000L) {
         return new Region("Vùng đào", var0, var1 - var4 + 1, var2, var0 + var3 - 1, var1, var2 + var5 - 1);
      } else {
         throw new IllegalStateException("Kích thước phải dương, tối đa 8 triệu ô.");
      }
   }

   public static Region fromSelectedPlacement() {
      SchematicPlacement var0 = DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();
      if (var0 == null) {
         throw new IllegalStateException("Chưa chọn placement nào trong Litematica (menu M → Placements → chọn một cái).");
      } else if (!var0.isEnabled()) {
         throw new IllegalStateException("Placement \"" + var0.getName() + "\" đang tắt — bật nó lên trong Litematica.");
      } else {
         int var1 = Integer.MAX_VALUE;
         int var2 = Integer.MAX_VALUE;
         int var3 = Integer.MAX_VALUE;
         int var4 = Integer.MIN_VALUE;
         int var5 = Integer.MIN_VALUE;
         int var6 = Integer.MIN_VALUE;
         int var7 = 0;
         UnmodifiableIterator var8 = var0.getSubRegionBoxes(RequiredEnabled.PLACEMENT_ENABLED).values().iterator();

         while (var8.hasNext()) {
            Box var9 = (Box)var8.next();
            BlockPos var10 = var9.getPos1();
            BlockPos var11 = var9.getPos2();
            if (var10 != null && var11 != null) {
               var1 = Math.min(var1, Math.min(var10.getX(), var11.getX()));
               var2 = Math.min(var2, Math.min(var10.getY(), var11.getY()));
               var3 = Math.min(var3, Math.min(var10.getZ(), var11.getZ()));
               var4 = Math.max(var4, Math.max(var10.getX(), var11.getX()));
               var5 = Math.max(var5, Math.max(var10.getY(), var11.getY()));
               var6 = Math.max(var6, Math.max(var10.getZ(), var11.getZ()));
               var7++;
            }
         }

         if (var7 == 0) {
            throw new IllegalStateException("Placement \"" + var0.getName() + "\" không có sub-region nào đang bật.");
         } else {
            long var12 = (long)(var4 - var1 + 1) * (var5 - var2 + 1) * (var6 - var3 + 1);
            if (var12 > 8000000L) {
               throw new IllegalStateException("Khung quá lớn (" + var12 + " ô) — chia nhỏ placement (tối đa 8 triệu ô).");
            } else {
               return new Region(var0.getName(), var1, var2, var3, var4, var5, var6);
            }
         }
      }
   }

   public boolean inside(int var1, int var2, int var3) {
      return var1 >= this.x0 && var1 <= this.x1 && var2 >= this.y0 && var2 <= this.y1 && var3 >= this.z0 && var3 <= this.z1;
   }

   public boolean inside(BlockPos var1) {
      return this.inside(var1.getX(), var1.getY(), var1.getZ());
   }

   public boolean insideColumn(int var1, int var2) {
      return var1 >= this.x0 && var1 <= this.x1 && var2 >= this.z0 && var2 <= this.z1;
   }

   public int layerCount() {
      return this.floors.size();
   }

   public int floor(int var1) {
      return this.floors.get(var1);
   }

   public int top(int var1) {
      return Math.min(this.y1, this.floor(var1) + 2);
   }

   public int bottom(int var1) {
      return this.floor(var1);
   }

   public int layerOf(int var1) {
      for (int var2 = 0; var2 < this.floors.size(); var2++) {
         if (var1 >= this.floor(var2) && var1 <= this.top(var2)) {
            return var2;
         }
      }

      return -1;
   }

   public int sizeX() {
      return this.x1 - this.x0 + 1;
   }

   public int sizeY() {
      return this.y1 - this.y0 + 1;
   }

   public int sizeZ() {
      return this.z1 - this.z0 + 1;
   }

   public long volume() {
      return (long)this.sizeX() * this.sizeY() * this.sizeZ();
   }

   public String describe() {
      return "\""
         + this.name
         + "\" "
         + this.sizeX()
         + "×"
         + this.sizeY()
         + "×"
         + this.sizeZ()
         + " ("
         + this.x0
         + ","
         + this.y0
         + ","
         + this.z0
         + " → "
         + this.x1
         + ","
         + this.y1
         + ","
         + this.z1
         + "), "
         + this.layerCount()
         + " tầng";
   }
}
