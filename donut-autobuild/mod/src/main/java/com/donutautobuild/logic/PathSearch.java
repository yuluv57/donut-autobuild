package com.donutautobuild.logic;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public final class PathSearch {
   private final PriorityQueue<PathSearch.QueueEntry> open = new PriorityQueue<>(
      Comparator.comparingDouble(PathSearch.QueueEntry::f).thenComparing(Comparator.comparingDouble(PathSearch.QueueEntry::g).reversed())
   );
   private final Map<PathSearch.Cell, Double> costs = new HashMap<>();
   private final Map<PathSearch.Cell, PathSearch.Cell> previous = new HashMap<>();
   private final Set<PathSearch.Cell> closed = new HashSet<>();
   private final Set<PathSearch.Cell> goals;
   // 3.0.122: toạ độ đích dạng mảng — heuristic chạy cho MỌI ô được xét, duyệt HashSet mỗi lần chậm
   private final int[] gx;
   private final int[] gy;
   private final int[] gz;
   private final PathSearch.Terrain terrain;
   private final int limit;
   private static final long MAX_ACTIVE_SEARCH_NANOS = 300000000L;
   private long activeSearchNanos;
   private boolean timedOut;
   private List<PathSearch.Cell> result;
   private boolean done;

   public PathSearch(PathSearch.Cell var1, Collection<PathSearch.Cell> var2, PathSearch.Terrain var3, int var4) {
      this(List.of(var1), var2, var3, var4);
   }

   public PathSearch(Collection<PathSearch.Cell> var1, Collection<PathSearch.Cell> var2, PathSearch.Terrain var3, int var4) {
      this.goals = Set.copyOf(var2);
      this.gx = new int[this.goals.size()];
      this.gy = new int[this.goals.size()];
      this.gz = new int[this.goals.size()];
      int var7 = 0;

      for (PathSearch.Cell var8 : this.goals) {
         this.gx[var7] = var8.x();
         this.gy[var7] = var8.y();
         this.gz[var7++] = var8.z();
      }

      this.terrain = var3;
      this.limit = var4;

      for (PathSearch.Cell var6 : var1) {
         if (this.costs.putIfAbsent(var6, 0.0) == null) {
            this.open.add(new PathSearch.QueueEntry(var6, 0.0, this.heuristic(var6)));
         }
      }

      if (var2.isEmpty()) {
         this.done = true;
      }
   }

   public int explored() {
      return this.closed.size();
   }

   public Set<PathSearch.Cell> reached() {
      return this.closed;
   }

   private double heuristic(PathSearch.Cell var1) {
      if (this.gx.length == 0) {
         return 0.0;
      } else {
         int var2 = Integer.MAX_VALUE;
         int var3 = var1.x();
         int var4 = var1.y();
         int var5 = var1.z();

         for (int var6 = 0; var6 < this.gx.length; var6++) {
            int var7 = Math.abs(var3 - this.gx[var6]) + Math.abs(var4 - this.gy[var6]) + Math.abs(var5 - this.gz[var6]);
            if (var7 < var2) {
               var2 = var7;
            }
         }

         return var2 * 0.7;
      }
   }

   public void advance(int var1) {
      this.terrain.beginBatch();

      try {
         this.advanceNodes(var1);
      } finally {
         this.terrain.endBatch();
      }
   }

   private void advanceNodes(int var1) {
      while (!this.done && var1-- > 0) {
         if (!this.open.isEmpty() && this.closed.size() < this.limit) {
            PathSearch.QueueEntry var2 = this.open.poll();
            if (var2.g() > this.costs.getOrDefault(var2.cell(), Double.POSITIVE_INFINITY) || !this.closed.add(var2.cell())) {
               continue;
            }

            if (!this.goals.contains(var2.cell())) {
               for (PathSearch.Edge var8 : this.terrain.neighbors(var2.cell())) {
                  if (!this.closed.contains(var8.to()) && Double.isFinite(var8.cost()) && !(var8.cost() <= 0.0)) {
                     double var5 = var2.g() + var8.cost();
                     if (var5 < this.costs.getOrDefault(var8.to(), Double.POSITIVE_INFINITY)) {
                        this.costs.put(var8.to(), var5);
                        this.previous.put(var8.to(), var2.cell());
                        this.open.add(new PathSearch.QueueEntry(var8.to(), var5, var5 + this.heuristic(var8.to())));
                     }
                  }
               }
               continue;
            }

            LinkedList var3 = new LinkedList();

            for (PathSearch.Cell var4 = var2.cell(); var4 != null; var4 = this.previous.get(var4)) {
               var3.addFirst(var4);
            }

            this.result = List.copyOf(var3);
            this.done = true;
            return;
         }

         this.done = true;
         return;
      }
   }

   public void advanceFor(long var1) {
      if (!this.done && var1 > 0L) {
         long var3 = System.nanoTime();
         this.terrain.beginBatch();

         try {
            while (!this.done && System.nanoTime() - var3 < var1) {
               this.advanceNodes(1);
            }
         } finally {
            this.terrain.endBatch();
            this.activeSearchNanos = this.activeSearchNanos + (System.nanoTime() - var3);
            if (!this.done && this.activeSearchNanos >= 300000000L) {
               this.timedOut = true;
               this.done = true;
            }
         }
      }
   }

   public boolean done() {
      return this.done;
   }

   public List<PathSearch.Cell> result() {
      return this.result;
   }

   public boolean timedOut() {
      return this.timedOut;
   }

   public boolean exhausted() {
      return this.done && this.result == null && !this.timedOut && this.open.isEmpty();
   }

   public static record Cell(int x, int y, int z) {
      public PathSearch.Cell add(int var1, int var2, int var3) {
         return new PathSearch.Cell(this.x + var1, this.y + var2, this.z + var3);
      }

      public double getDistance(PathSearch.Cell var1) {
         return (double)(Math.abs(this.x - var1.x) + Math.abs(this.y - var1.y) + Math.abs(this.z - var1.z));
      }
   }

   public static record Edge(PathSearch.Cell to, double cost, boolean scaffold) {
   }

   private static record QueueEntry(PathSearch.Cell cell, double g, double f) {
   }

   public interface Terrain {
      List<PathSearch.Edge> neighbors(PathSearch.Cell var1);

      default void beginBatch() {
      }

      default void endBatch() {
      }
   }
}
