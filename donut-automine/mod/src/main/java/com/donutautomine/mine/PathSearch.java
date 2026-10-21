package com.donutautomine.mine;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Predicate;

public final class PathSearch {
   private final PriorityQueue<PathSearch.QueueEntry> open = new PriorityQueue<>(
      Comparator.comparingDouble(PathSearch.QueueEntry::f).thenComparing(Comparator.comparingDouble(PathSearch.QueueEntry::g).reversed())
   );
   private final Map<PathSearch.Cell, Double> costs = new HashMap<>();
   private final Map<PathSearch.Cell, PathSearch.Cell> previous = new HashMap<>();
   private final Set<PathSearch.Cell> closed = new HashSet<>();
   private final Set<PathSearch.Cell> goals;
   private final PathSearch.Terrain terrain;
   private final int limit;
   private static final long MAX_ACTIVE_SEARCH_NANOS = 300000000L;
   private long activeSearchNanos;
   private boolean timedOut;
   private List<PathSearch.Cell> result;
   private boolean done;
   private Predicate<PathSearch.Cell> goalTest;
   private PathSearch.Cell hint;

   public PathSearch(PathSearch.Cell var1, Predicate<PathSearch.Cell> var2, PathSearch.Cell var3, PathSearch.Terrain var4, int var5) {
      this.goals = Set.of();
      this.goalTest = var2;
      this.hint = var3;
      this.terrain = var4;
      this.limit = var5;
      this.costs.put(var1, 0.0);
      this.open.add(new PathSearch.QueueEntry(var1, 0.0, this.heuristic(var1)));
   }

   public PathSearch(PathSearch.Cell var1, Collection<PathSearch.Cell> var2, PathSearch.Terrain var3, int var4) {
      this(List.of(var1), var2, var3, var4);
   }

   public PathSearch(Collection<PathSearch.Cell> var1, Collection<PathSearch.Cell> var2, PathSearch.Terrain var3, int var4) {
      this.goals = Set.copyOf(var2);
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

   public boolean exhausted() {
      return this.done && this.result == null && !this.timedOut && this.open.isEmpty();
   }

   private double heuristic(PathSearch.Cell var1) {
      if (this.goalTest != null) {
         return 0.0;
      } else {
         double var2 = Double.POSITIVE_INFINITY;

         for (PathSearch.Cell var5 : this.goals) {
            var2 = Math.min(var2, var1.distance(var5));
         }

         return Double.isFinite(var2) ? var2 * 0.7 : 0.0;
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
         if (this.open.isEmpty() || this.closed.size() >= this.limit) {
            this.done = true;
            return;
         }

         PathSearch.QueueEntry var2 = this.open.poll();
         if (!(var2.g() > this.costs.getOrDefault(var2.cell(), Double.POSITIVE_INFINITY)) && this.closed.add(var2.cell())) {
            if (this.goalTest != null ? this.goalTest.test(var2.cell()) : this.goals.contains(var2.cell())) {
               LinkedList var7 = new LinkedList();

               for (PathSearch.Cell var8 = var2.cell(); var8 != null; var8 = this.previous.get(var8)) {
                  var7.addFirst(var8);
               }

               this.result = List.copyOf(var7);
               this.done = true;
               return;
            }

            for (PathSearch.Edge var4 : this.terrain.neighbors(var2.cell())) {
               if (!this.closed.contains(var4.to()) && Double.isFinite(var4.cost()) && !(var4.cost() <= 0.0)) {
                  double var5 = var2.g() + var4.cost();
                  if (var5 < this.costs.getOrDefault(var4.to(), Double.POSITIVE_INFINITY)) {
                     this.costs.put(var4.to(), var5);
                     this.previous.put(var4.to(), var2.cell());
                     this.open.add(new PathSearch.QueueEntry(var4.to(), var5, var5 + this.heuristic(var4.to())));
                  }
               }
            }
         }
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

   public record Cell(int x, int y, int z) {
      public PathSearch.Cell add(int var1, int var2, int var3) {
         return new PathSearch.Cell(this.x + var1, this.y + var2, this.z + var3);
      }

      public double distance(PathSearch.Cell var1) {
         return Math.abs(this.x - var1.x) + Math.abs(this.y - var1.y) + Math.abs(this.z - var1.z);
      }
   }

   public record Edge(PathSearch.Cell to, double cost, boolean scaffold) {
   }

   private record QueueEntry(PathSearch.Cell cell, double g, double f) {
   }

   public interface Terrain {
      List<PathSearch.Edge> neighbors(PathSearch.Cell var1);

      default void beginBatch() {
      }

      default void endBatch() {
      }
   }
}
