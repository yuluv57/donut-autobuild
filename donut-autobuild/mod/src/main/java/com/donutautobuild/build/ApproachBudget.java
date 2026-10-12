package com.donutautobuild.build;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class ApproachBudget {
   private Object world;
   private Object plan;
   private Integer layer;
   private int phase;
   private int placed;
   private Vec3d origin;
   private final Set<BlockPos> compared = new HashSet<>();

   public ApproachBudget() {
   }

   public boolean defer(Object var1, Object var2, Integer var3, int var4, int var5, Vec3d var6, BlockPos var7) {
      if (this.origin == null
         || this.world != var1
         || this.plan != var2
         || !Objects.equals(this.layer, var3)
         || this.phase != var4
         || this.placed != var5
         || this.origin.squaredDistanceTo(var6) > 2.25) {
         this.world = var1;
         this.plan = var2;
         this.layer = var3;
         this.phase = var4;
         this.placed = var5;
         this.origin = var6;
         this.compared.clear();
      }

      if (this.compared.size() < 2 && !this.compared.contains(var7)) {
         this.compared.add(var7.toImmutable());
         return true;
      } else {
         return false;
      }
   }
}
