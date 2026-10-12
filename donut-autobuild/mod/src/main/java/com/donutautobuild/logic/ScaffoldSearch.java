package com.donutautobuild.logic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

public final class ScaffoldSearch {
   private ScaffoldSearch() {
   }

   public static List<PathSearch.Cell> find(PathSearch.Cell var0, Predicate<PathSearch.Cell> var1, Predicate<PathSearch.Cell> var2, int var3, int var4) {
      if (var3 >= 1 && var4 >= 1) {
         int[][] var5 = new int[][]{{0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}};
         ArrayDeque var6 = new ArrayDeque();
         HashMap var7 = new HashMap();
         HashMap var8 = new HashMap();
         HashSet var9 = new HashSet();
         var9.add(var0);

         for (int[] var13 : var5) {
            PathSearch.Cell var14 = var0.add(var13[0], var13[1], var13[2]);
            if (var9.add(var14) && var1.test(var14)) {
               var7.put(var14, null);
               var8.put(var14, 1);
               var6.add(var14);
            }
         }

         while (!var6.isEmpty() && var8.size() <= var4) {
            PathSearch.Cell var17 = (PathSearch.Cell)var6.poll();
            if (var2.test(var17)) {
               ArrayList var19 = new ArrayList();

               for (PathSearch.Cell var21 = var17; var21 != null; var21 = (PathSearch.Cell)var7.get(var21)) {
                  var19.add(var21);
               }

               return List.copyOf(var19);
            }

            int var18 = (Integer)var8.get(var17) + 1;
            if (var18 <= var3) {
               for (int[] var15 : var5) {
                  PathSearch.Cell var16 = var17.add(var15[0], var15[1], var15[2]);
                  if (var9.add(var16) && var1.test(var16)) {
                     var7.put(var16, var17);
                     var8.put(var16, var18);
                     var6.add(var16);
                     if (var8.size() >= var4) {
                        break;
                     }
                  }
               }
            }
         }

         return List.of();
      } else {
         return List.of();
      }
   }
}
