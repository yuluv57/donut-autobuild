package com.donutautomine.mixin;

import com.donutautomine.mine.FreeCam;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {Camera.class},
   remap = false
)
public abstract class CameraMixin {
   @Shadow(
      aliases = {"thirdPerson"},
      remap = false
   )
   private boolean field_18719;

   @Shadow(
      aliases = {"setPos"},
      remap = false
   )
   protected abstract void method_19327(double var1, double var3, double var5);

   @Shadow(
      aliases = {"setRotation"},
      remap = false
   )
   protected abstract void method_19325(float var1, float var2);

   @Inject(
      method = {"update", "method_19321"},
      at = {@At("TAIL")},
      remap = false
   )
   private void donutautomine$freecam(CallbackInfo var1) {
      if (FreeCam.active()) {
         double[] var2 = FreeCam.frame();
         if (var2 != null) {
            this.field_18719 = true;
            this.method_19325((float)var2[3], (float)var2[4]);
            this.method_19327(var2[0], var2[1], var2[2]);
         }
      }
   }

   @Inject(
      method = {"getSubmersionType", "method_19334"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false
   )
   private void donutautomine$freecamSubmersion(CallbackInfoReturnable<CameraSubmersionType> var1) {
      if (FreeCam.active()) {
         var1.setReturnValue(CameraSubmersionType.NONE);
      }
   }
}
