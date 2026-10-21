package com.donutautomine.mixin;

import com.donutautomine.mine.FreeCam;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {InGameOverlayRenderer.class},
   remap = false
)
public abstract class FreeCamOverlayMixin {
   @Inject(
      method = {"renderOverlays", "method_23067"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false
   )
   private static void donutautomine$freecamOverlays(CallbackInfo var0) {
      if (FreeCam.active()) {
         var0.cancel();
      }
   }
}
