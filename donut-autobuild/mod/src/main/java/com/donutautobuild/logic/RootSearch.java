package com.donutautobuild.logic;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.function.Predicate;

public final class RootSearch {
   private RootSearch() {
   }

   public static PathSearch.Cell find(
      PathSearch.Cell var0, Predicate<PathSearch.Cell> var1, Predicate<PathSearch.Cell> var2, Predicate<PathSearch.Cell> var3, int var4
   ) {
      ArrayDeque var5 = new ArrayDeque();
      HashSet var6 = new HashSet();
      var5.add(var0);
      var6.add(var0);
      int[][] var7 = new int[][]{{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}};
      int var8 = 0;

      while (!var5.isEmpty() && var8++ < var4) {
         PathSearch.Cell var9 = (PathSearch.Cell)var5.poll();

         for (int[] var13 : var7) {
            PathSearch.Cell var14 = var9.add(var13[0], var13[1], var13[2]);
            if (var6.add(var14) && var1.test(var14)) {
               if (var2.test(var14) && var3.test(var14)) {
                  return var14;
               }

               var5.add(var14);
            }
         }
      }

      return null;
   }
}
