package com.donutautobuild.mixin;

import com.donutautobuild.build.Freecam;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRenderer.class, remap = false)
public abstract class FreecamHandMixin {
    @Inject(method = {"renderHand", "method_3172"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void donutautobuild$freecamHand(CallbackInfo ci) {
        if (Freecam.active()) ci.cancel();
    }
}
