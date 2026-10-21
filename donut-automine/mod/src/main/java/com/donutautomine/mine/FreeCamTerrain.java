package com.donutautomine.mine;

import com.donutautomine.AutoMineMod;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.chunk.ChunkBuilder.BuiltChunk;
import net.minecraft.util.math.BlockPos;

public final class FreeCamTerrain {
   public static final int WARM_DISTANCE_SQUARED = 4096;
   private static int pendingNear;
   private static long lastReport;
   private static int lastSignature;

   private FreeCamTerrain() {
   }

   public static void reset() {
      lastReport = 0L;
      lastSignature = Integer.MIN_VALUE;
      pendingNear = 0;
   }

   public static void pendingNear(int var0) {
      pendingNear = var0;
   }

   public static String status(double var0, int var2) {
      String var3 = "Camera Y: " + (int)Math.floor(var0);
      if (var0 < var2) {
         return var3 + " · Dưới đáy thế giới (" + var2 + "); Space để lên";
      } else {
         return pendingNear > 0 ? var3 + " · Đang dựng địa hình gần: " + pendingNear : var3;
      }
   }

   public static void collect(BuiltChunk[] var0, Frustum var1, List<BuiltChunk> var2, List<BuiltChunk> var3) {
      int var4 = 0;
      int var5 = Integer.MAX_VALUE;
      int var6 = Integer.MIN_VALUE;
      BlockPos var7 = BlockPos.ofFloored(var1.getX(), var1.getY(), var1.getZ());
      int var8 = 0;

      for (BuiltChunk var12 : var0) {
         if (var12 != null && var12.shouldBuild()) {
            var4++;
            var5 = Math.min(var5, var12.getOrigin().getY());
            var6 = Math.max(var6, var12.getOrigin().getY() + 15);
            if (var1.isVisible(var12.getBoundingBox()) || !(distance(var12, var1) > 4096.0)) {
               var2.add(var12);
               if (distance(var12, var1) <= 4096.0 && var12.needsRebuild()) {
                  var8++;
               }

               if (distance(var12, var1) <= 1024.0) {
                  var3.add(var12);
               }
            }
         }
      }

      var2.sort(Comparator.comparingDouble(var1x -> distance(var1x, var1)));
      var3.sort(Comparator.comparingDouble(var1x -> distance(var1x, var1)));
      pendingNear(var8);
      report("Minecraft", var7, var4, var2.size(), var5, var6);
   }

   private static double distance(BuiltChunk var0, Frustum var1) {
      BlockPos var2 = var0.getOrigin();
      double var3 = var2.getX() + 8 - var1.getX();
      double var5 = var2.getY() + 8 - var1.getY();
      double var7 = var2.getZ() + 8 - var1.getZ();
      return var3 * var3 + var5 * var5 + var7 * var7;
   }

   public static void report(String var0, BlockPos var1, int var2, int var3, int var4, int var5) {
      report(var0, var1, var2, var3, var4, var5, "");
   }

   public static void report(String var0, BlockPos var1, int var2, int var3, int var4, int var5, String var6) {
      int var7 = Objects.hash(var0, var1.getX() >> 4, var1.getY() >> 4, var1.getZ() >> 4, var2, var3, var6);
      long var8 = System.nanoTime();
      if (lastReport == 0L || var8 - lastReport >= 5000000000L && var7 != lastSignature) {
         lastReport = var8;
         lastSignature = var7;
         AutoMineMod.LOGGER
            .info(
               "[AutoMine] Freecam {}: camera={}, residentSections={}, selected={}, residentY={}..{} {}",
               new Object[]{var0, var1, var2, var3, var2 == 0 ? "none" : var4, var2 == 0 ? "none" : var5, var6}
            );
      }
   }
}
