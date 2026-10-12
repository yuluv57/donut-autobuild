package com.donutautobuild.build;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

public final class FluidOrder {
   private FluidOrder() {
   }

   public static int phase(BlockState var0) {
      if (Materials.flowing(var0)) {
         return -1;
      } else if (var0.isOf(Blocks.LAVA)) {
         return 2;
      } else {
         return var0.isOf(Blocks.WATER) ? 1 : 0;
      }
   }

   public static boolean eligible(boolean var0, BlockState var1, int var2) {
      return var0 && phase(var1) == var2;
   }

   public static int firstPending(int[] var0) {
      if (var0 != null) {
         for (int var1 = 0; var1 < 3; var1++) {
            if (var0[var1] > 0) {
               return var1;
            }
         }
      }

      return -1;
   }

   public static String label(int var0) {
      return var0 == 2 ? "lava cuối cùng" : (var0 == 1 ? "nước thường sau phần xây" : "khối công trình và nước trong lá");
   }
}
