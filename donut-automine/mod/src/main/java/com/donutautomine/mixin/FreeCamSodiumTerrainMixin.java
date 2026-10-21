package com.donutautomine.mixin;

import com.donutautomine.mine.FreeCam;
import com.donutautomine.mine.FreeCamTerrain;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.Comparator;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller.Visitor;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
   targets = {"net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller"},
   remap = false
)
public abstract class FreeCamSodiumTerrainMixin {
   @Shadow(
      remap = false
   )
   @Final
   private Long2ReferenceMap<RenderSection> sections;

   @Inject(
      method = {"findVisible"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0,
      remap = false
   )
   private void donutautomine$loadedTerrain(Visitor var1, Viewport var2, float var3, boolean var4, int var5, CallbackInfo var6) {
      if (FreeCam.active()) {
         ArrayList<RenderSection> var7 = new ArrayList<>();
         BlockPos var8 = var2.getBlockCoord();
         int var9 = 0;
         int var10 = Integer.MAX_VALUE;
         int var11 = Integer.MIN_VALUE;
         ObjectIterator var12 = this.sections.values().iterator();

         while (var12.hasNext()) {
            RenderSection var13 = (RenderSection)var12.next();
            if (!var13.isDisposed()) {
               var9++;
               var10 = Math.min(var10, var13.getOriginY());
               var11 = Math.max(var11, var13.getOriginY() + 15);
               if (OcclusionCuller.isWithinFrustum(var2, var13) || var13.getSquaredDistance(var8) <= 4096.0F) {
                  var7.add(var13);
               }
            }
         }

         var7.sort(Comparator.comparingDouble(var1x -> var1x.getSquaredDistance(var8)));
         int var29 = 0;
         int var30 = 0;
         int var14 = 0;
         int var15 = 0;
         int var16 = 0;
         int var17 = 0;
         int var18 = Integer.MAX_VALUE;
         int var19 = Integer.MIN_VALUE;
         int var20 = 0;
         double var21 = Math.toRadians(FreeCam.viewYaw());
         double var23 = Math.cos(var21);
         double var25 = Math.sin(var21);

         for (RenderSection var28 : var7) {
            if (var28.isBuilt()) {
               var29++;
            }

            if ((var28.getFlags() & 1) != 0) {
               var30++;
               var18 = Math.min(var18, var28.getOriginY());
               var19 = Math.max(var19, var28.getOriginY() + 15);
               if ((var28.getCenterX() - var8.getX()) * var23 + (var28.getCenterZ() - var8.getZ()) * var25 >= 0.0) {
                  var16++;
               } else {
                  var17++;
               }
            }

            if (var28.getPendingUpdate() != null) {
               var14++;
            }

            if (var28.getTaskCancellationToken() != null) {
               var15++;
            }

            if ((!var28.isBuilt() || var28.getPendingUpdate() != null || var28.getTaskCancellationToken() != null) && var28.getSquaredDistance(var8) <= 4096.0F
               )
             {
               var20++;
            }

            var28.setLastVisibleFrame(var5);
            var28.setIncomingDirections(0);
            var1.visit(var28);
         }

         FreeCamTerrain.pendingNear(var20);
         FreeCamTerrain.report(
            "Sodium",
            var8,
            var9,
            var7.size(),
            var10,
            var11,
            "built="
               + var29
               + ", geometry="
               + var30
               + ", pending="
               + var14
               + ", inFlight="
               + var15
               + ", leftGeometry="
               + var16
               + ", rightGeometry="
               + var17
               + ", geometryY="
               + (var30 == 0 ? "none" : var18 + ".." + var19)
               + ", pendingNear="
               + var20
         );
         var6.cancel();
      }
   }
}
