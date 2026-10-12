package com.donutautobuild.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RowPlanner {
   private RowPlanner() {
   }

   public static List<RowPlanner.Row> plan(Map<PathSearch.Cell, RowPlanner.Info> var0, PathSearch.Cell var1, int var2) {
      ArrayList var3 = new ArrayList();
      HashSet var4 = new HashSet();
      ArrayList<PathSearch.Cell> var5 = new ArrayList<>(var0.keySet());
      var5.sort(Comparator.comparingInt(PathSearch.Cell::y).thenComparingInt(PathSearch.Cell::x).thenComparingInt(PathSearch.Cell::z));

      for (PathSearch.Cell var7 : var5) {
         RowPlanner.Info var8 = (RowPlanner.Info)var0.get(var7);
         if (!var4.contains(var7) && var8.fixed()) {
            PathSearch.Cell var9 = var7;

            while (true) {
               PathSearch.Cell var10 = var9.add(var8.fx(), 0, var8.fz());
               if (!var8.equals(var0.get(var10)) || var4.contains(var10)) {
                  ArrayList var19 = new ArrayList();

                  for (PathSearch.Cell var11 = var9; var8.equals(var0.get(var11)) && !var4.contains(var11); var11 = var11.add(-var8.fx(), 0, -var8.fz())) {
                     var19.add(var11);
                  }

                  var4.addAll(var19);
                  var3.add(new RowPlanner.Row(var19, -var8.fx(), -var8.fz(), var9.add(var8.fx(), 0, var8.fz()), true, var8.key()));
                  break;
               }

               var9 = var10;
            }
         }
      }

      for (PathSearch.Cell var16 : var5) {
         RowPlanner.Info var17 = (RowPlanner.Info)var0.get(var16);
         if (!var4.contains(var16)) {
            List var18 = run(var0, var4, var16, var17, 1, 0);
            List var20 = run(var0, var4, var16, var17, 0, 1);
            List var21 = var18.size() >= var20.size() ? var18 : var20;
            if (var21.size() >= var2) {
               int var12 = var18.size() >= var20.size() ? 1 : 0;
               int var13 = 1 - var12;
               var4.addAll(var21);
               PathSearch.Cell var14 = (PathSearch.Cell)var21.getFirst();
               var3.add(new RowPlanner.Row(var21, var12, var13, var14.add(-var12, 0, -var13), false, var17.key()));
            }
         }
      }

      return order(var3, var1);
   }

   private static List<PathSearch.Cell> run(
      Map<PathSearch.Cell, RowPlanner.Info> var0, Set<PathSearch.Cell> var1, PathSearch.Cell var2, RowPlanner.Info var3, int var4, int var5
   ) {
      PathSearch.Cell var6 = var2;

      while (var3.equals(var0.get(var6.add(-var4, 0, -var5))) && !var1.contains(var6.add(-var4, 0, -var5))) {
         var6 = var6.add(-var4, 0, -var5);
      }

      ArrayList var7 = new ArrayList();

      for (PathSearch.Cell var8 = var6; var3.equals(var0.get(var8)) && !var1.contains(var8); var8 = var8.add(var4, 0, var5)) {
         var7.add(var8);
      }

      return var7;
   }

   static List<RowPlanner.Row> order(List<RowPlanner.Row> var0, PathSearch.Cell var1) {
      ArrayList<RowPlanner.Row> var2 = new ArrayList<>(var0);
      ArrayList var3 = new ArrayList(var0.size());
      PathSearch.Cell var4 = var1;

      while (!var2.isEmpty()) {
         RowPlanner.Row var5 = null;
         double var6 = Double.MAX_VALUE;

         for (RowPlanner.Row var9 : var2) {
            double var10 = var9.start().getDistance(var4);
            if (!var9.fixed()) {
               var10 = Math.min(var10, var9.end().getDistance(var4));
            }

            if (var10 < var6) {
               var6 = var10;
               var5 = var9;
            }
         }

         var2.remove(var5);
         if (!var5.fixed() && var5.end().getDistance(var4) < var5.start().getDistance(var4)) {
            var5 = var5.reversed();
         }

         var3.add(var5);
         var4 = var5.start();
      }

      return var3;
   }

   public static record Info(String key, int fx, int fz) {
      public boolean fixed() {
         return this.fx != 0 || this.fz != 0;
      }
   }

   public static record Row(List<PathSearch.Cell> cells, int dx, int dz, PathSearch.Cell front, boolean fixed, String key) {
      public PathSearch.Cell start() {
         return this.cells.getFirst();
      }

      public PathSearch.Cell end() {
         return this.cells.getLast();
      }

      public RowPlanner.Row reversed() {
         ArrayList var1 = new ArrayList<>(this.cells);
         Collections.reverse(var1);
         PathSearch.Cell var2 = this.cells.getLast();
         return new RowPlanner.Row(var1, -this.dx, -this.dz, new PathSearch.Cell(var2.x() + this.dx, var2.y(), var2.z() + this.dz), this.fixed, this.key);
      }
   }
}
