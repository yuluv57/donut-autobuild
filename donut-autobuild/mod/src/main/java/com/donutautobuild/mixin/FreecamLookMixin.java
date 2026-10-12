package com.donutautobuild.mixin;

import com.donutautobuild.build.Freecam;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Entity.class, remap = false)
public abstract class FreecamLookMixin {
    @Inject(method = {"changeLookDirection", "method_5872"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void donutautobuild$freecamLook(double mouseX, double mouseY, CallbackInfo ci) {
        if (!Freecam.controlsPlayer((Entity)(Object)this)) return;
        Freecam.look(mouseX, mouseY);
        ci.cancel();
    }
}
