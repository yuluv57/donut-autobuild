package com.donutautobuild.mixin;

import com.donutautobuild.build.Freecam;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Fog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No visual fog or fog-distance chunk pruning while inspecting already loaded terrain. */
@Mixin(value = BackgroundRenderer.class, remap = false)
public abstract class FreecamFogMixin {
    @Inject(method = {"applyFog", "method_3211"}, at = @At("HEAD"), cancellable = true, remap = false)
    private static void donutautobuild$freecamFog(CallbackInfoReturnable<Fog> ci) {
        if (Freecam.active()) ci.setReturnValue(Fog.DUMMY);
    }
}
