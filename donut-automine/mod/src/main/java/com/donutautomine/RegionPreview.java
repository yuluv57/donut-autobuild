package com.donutautomine;

import com.donutautomine.gui.SchematicView;
import com.donutautomine.mine.FreeCam;
import com.donutautomine.mine.FreeCamTerrain;
import com.donutautomine.mine.Region;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AfterEntities;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class RegionPreview implements ClientModInitializer {
   public void onInitializeClient() {
      if (AutoMineMod.config.hideSchematicContents) {
         SchematicView.contents(true);
      }

      HudRenderCallback.EVENT.register((HudRenderCallback)(var0, var1) -> {
         MinecraftClient var2 = MinecraftClient.getInstance();
         if (FreeCam.active() && var2.world != null && !var2.options.hudHidden) {
            String var3 = FreeCamTerrain.status(FreeCam.viewY(), var2.world.getBottomY());
            int var4 = var2.textRenderer.getWidth(var3);
            var0.fill(4, 26, var4 + 12, 38, Integer.MIN_VALUE);
            var0.drawTextWithShadow(var2.textRenderer, var3, 8, 28, -1579033);
         }
      });
      WorldRenderEvents.AFTER_ENTITIES
         .register(
            (AfterEntities)var0 -> {
               if (AutoMineMod.config.showRegionOutline && var0.matrixStack() != null && var0.consumers() != null) {
                  Region var1 = AutoMineMod.controller.region();
                  MineConfig var2 = AutoMineMod.config;
                  if (var1 == null && var2.customRegion && var2.regionPlaced) {
                     var1 = Region.dimensions(var2.regionX, var2.regionTopY, var2.regionZ, var2.regionSizeX, var2.regionSizeY, var2.regionSizeZ);
                  }

                  if (var1 != null) {
                     MatrixStack var3 = var0.matrixStack();
                     Vec3d var4 = var0.camera().getPos();
                     var3.push();
                     var3.translate(-var4.x, -var4.y, -var4.z);
                     VertexRendering.drawBox(
                        var3,
                        var0.consumers().getBuffer(RenderLayer.getLines()),
                        new Box(var1.x0, var1.y0, var1.z0, var1.x1 + 1, var1.y1 + 1, var1.z1 + 1),
                        1.0F,
                        0.7F,
                        0.12F,
                        1.0F
                     );
                     var3.pop();
                  }
               }
            }
         );
   }
}
