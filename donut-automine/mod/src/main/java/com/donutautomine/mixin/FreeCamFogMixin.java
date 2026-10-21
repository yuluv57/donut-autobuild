package com.donutautomine.mixin;

import com.donutautomine.mine.FreeCam;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Fog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {BackgroundRenderer.class},
   remap = false
)
public abstract class FreeCamFogMixin {
   @Inject(
      method = {"applyFog", "method_3211"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false
   )
   private static void donutautomine$freecamFog(CallbackInfoReturnable<Fog> var0) {
      if (FreeCam.active()) {
         var0.setReturnValue(Fog.DUMMY);
      }
   }
}
