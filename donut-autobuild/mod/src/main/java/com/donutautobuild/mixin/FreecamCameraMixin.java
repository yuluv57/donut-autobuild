package com.donutautobuild.mixin;

import com.donutautobuild.build.Freecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.block.enums.CameraSubmersionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The patch pipeline does not remap mixin strings: shadow names are already intermediary. */
@Mixin(value = Camera.class, remap = false)
public abstract class FreecamCameraMixin {
    @Shadow(aliases = "thirdPerson", remap = false) private boolean field_18719; // Camera.thirdPerson
    @Shadow(aliases = "setPos", remap = false) protected abstract void method_19327(double x, double y, double z); // setPos
    @Shadow(aliases = "setRotation", remap = false) protected abstract void method_19325(float yaw, float pitch); // setRotation

    @Inject(method = {"update", "method_19321"}, at = @At("TAIL"), remap = false)
    private void donutautobuild$freecam(CallbackInfo ci) {
        if (!Freecam.active()) return;
        double[] view = Freecam.frame(MinecraftClient.getInstance());
        if (view == null) return;
        field_18719 = true;
        method_19325((float)view[3], (float)view[4]);
        method_19327(view[0], view[1], view[2]);
    }

    @Inject(method = {"getSubmersionType", "method_19334"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void donutautobuild$freecamSubmersion(CallbackInfoReturnable<CameraSubmersionType> ci) {
        if (Freecam.active()) ci.setReturnValue(CameraSubmersionType.NONE);
    }
}
