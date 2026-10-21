package com.donutautomine.mixin;

import com.donutautomine.mine.FreeCam;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
   targets = {"net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer"},
   remap = false
)
public abstract class FreeCamSodiumFacesMixin {
   @Inject(
      method = {"getVisibleFaces"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0,
      remap = false
   )
   private static void donutautomine$allDirections(int var0, int var1, int var2, int var3, int var4, int var5, CallbackInfoReturnable<Integer> var6) {
      if (FreeCam.active()) {
         var6.setReturnValue(ModelQuadFacing.ALL);
      }
   }
}
