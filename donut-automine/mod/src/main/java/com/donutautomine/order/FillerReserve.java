package com.donutautomine.order;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

public final class FillerReserve {
   private FillerReserve() {
   }

   public static Set<Integer> slots(PlayerInventory var0, Predicate<ItemStack> var1, int var2) {
      ArrayList<Integer> var3 = new ArrayList<>();

      for (int var4 = 0; var4 < 36; var4++) {
         if (!var0.getStack(var4).isEmpty() && var1.test(var0.getStack(var4))) {
            var3.add(var4);
         }
      }

      var3.sort(
         Comparator.<Integer>comparingInt(var1x -> var1x == var0.selectedSlot ? 0 : 1)
            .thenComparing(Comparator.<Integer>comparingInt(var1x -> var0.getStack(var1x).getCount()).reversed())
      );
      HashSet var8 = new HashSet();
      int var5 = 0;

      for (int var7 : var3) {
         var8.add(var7);
         var5 += var0.getStack(var7).getCount();
         if (var5 >= Math.max(64, var2)) {
            break;
         }
      }

      return Set.copyOf(var8);
   }
}
