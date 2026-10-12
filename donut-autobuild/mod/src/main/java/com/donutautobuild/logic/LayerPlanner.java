package com.donutautobuild.logic;

import java.util.TreeMap;
import java.util.Map.Entry;

public final class LayerPlanner {
   private LayerPlanner() {
   }

   public static LayerPlanner.Choice choose(TreeMap<Integer, LayerPlanner.Stat> var0, boolean var1) {
      Integer var2 = null;

      for (Entry var4 : var0.entrySet()) {
         if (((LayerPlanner.Stat)var4.getValue()).remaining > 0) {
            if (var2 == null) {
               var2 = (Integer)var4.getKey();
            }

            if (((LayerPlanner.Stat)var4.getValue()).ready > 0) {
               if (((Integer)var4.getKey()).equals(var2)) {
                  return new LayerPlanner.Choice(var2, false);
               }

               return new LayerPlanner.Choice(var2, true);
            }

            return new LayerPlanner.Choice(var2, true);
         }
      }

      return new LayerPlanner.Choice(var2, var2 != null);
   }

   public static record Choice(Integer layer, boolean waitingMaterial) {
   }

   public static final class Stat {
      public int remaining;
      public int ready;

      public Stat() {
      }
   }
}
