package com.donutautobuild.mixin;

import com.donutautobuild.build.Freecam;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MinecraftClient.class, remap = false)
public abstract class FreecamInteractionMixin {
    @Inject(method = {"doAttack", "method_1536"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void donutautobuild$freecamAttack(CallbackInfoReturnable<Boolean> ci) {
        if (Freecam.active()) ci.setReturnValue(false);
    }

    @Inject(method = {"doItemUse", "method_1583"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void donutautobuild$freecamUse(CallbackInfo ci) {
        if (Freecam.active()) ci.cancel();
    }

    @Inject(method = {"handleBlockBreaking", "method_1590"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void donutautobuild$freecamBreaking(boolean breaking, CallbackInfo ci) {
        if (Freecam.active()) ci.cancel();
    }
}
