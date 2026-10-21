package com.donutautomine.mixin;

import com.donutautomine.mine.FreeCam;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {Entity.class},
   remap = false
)
public abstract class FreeCamLookMixin {
   @Inject(
      method = {"changeLookDirection", "method_5872"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false
   )
   private void donutautomine$cameraLook(double var1, double var3, CallbackInfo var5) {
      if (FreeCam.controlsPlayer((Entity)(Object)this)) {
         FreeCam.look(var1, var3);
         var5.cancel();
      }
   }
}
