package com.donutautomine.mixin;

import com.donutautomine.mine.FreeCam;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {MinecraftClient.class},
   remap = false
)
public abstract class FreeCamInteractionMixin {
   @Inject(
      method = {"doAttack", "method_1536"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false
   )
   private void donutautomine$cameraAttack(CallbackInfoReturnable<Boolean> var1) {
      if (FreeCam.active()) {
         var1.setReturnValue(false);
      }
   }

   @Inject(
      method = {"doItemUse", "method_1583"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false
   )
   private void donutautomine$cameraUse(CallbackInfo var1) {
      if (FreeCam.active()) {
         var1.cancel();
      }
   }

   @Inject(
      method = {"handleBlockBreaking", "method_1590"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false
   )
   private void donutautomine$cameraBreaking(boolean var1, CallbackInfo var2) {
      if (FreeCam.active()) {
         var2.cancel();
      }
   }
}
