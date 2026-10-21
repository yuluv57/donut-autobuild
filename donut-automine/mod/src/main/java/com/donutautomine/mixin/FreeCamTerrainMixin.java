package com.donutautomine.mixin;

import com.donutautomine.mine.FreeCam;
import com.donutautomine.mine.FreeCamTerrain;
import java.util.List;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.ChunkRenderingDataPreparer;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.chunk.ChunkBuilder.BuiltChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {ChunkRenderingDataPreparer.class},
   remap = false
)
public abstract class FreeCamTerrainMixin {
   @Shadow(
      aliases = {"builtChunkStorage"},
      remap = false
   )
   private BuiltChunkStorage field_45623;

   @Inject(
      method = {"collectChunks", "method_52828"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false
   )
   private void donutautomine$loadedTerrain(Frustum var1, List<BuiltChunk> var2, List<BuiltChunk> var3, CallbackInfo var4) {
      if (FreeCam.active() && this.field_45623 != null && this.field_45623.chunks != null) {
         FreeCamTerrain.collect(this.field_45623.chunks, var1, var2, var3);
         var4.cancel();
      }
   }
}
