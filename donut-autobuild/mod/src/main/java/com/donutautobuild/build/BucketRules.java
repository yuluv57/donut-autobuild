package com.donutautobuild.build;

import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;

public final class BucketRules {
   private BucketRules() {
   }

   public static boolean shopBucket(Item var0) {
      return var0 == Items.WATER_BUCKET || var0 == Items.LAVA_BUCKET || var0 == Items.POWDER_SNOW_BUCKET;
   }

   public static int solidGate(int var0, boolean var1) {
      return 0;
   }

   public static int retryDelay(int var0) {
      return 40 * Math.max(1, Math.min(5, var0));
   }

   public static boolean placeableBucket(BlockState var0, BlockState var1) {
      return !Materials.flowing(var1) && Materials.bucket(var0, var1);
   }

   public static boolean layerEligible(boolean var0, BlockState var1) {
      return var0 && !Materials.flowing(var1);
   }

   public static boolean hitMatches(BlockState var0, BlockState var1, BlockPos var2, BlockHitResult var3) {
      return placeableBucket(var0, var1)
         && var3 != null
         && var3.getType() == Type.BLOCK
         && (Materials.waterlog(var0, var1) ? var3.getBlockPos().equals(var2) : var0.isReplaceable() && var3.getBlockPos().offset(var3.getSide()).equals(var2));
   }
}
