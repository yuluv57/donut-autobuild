package com.donutautomine.mixin;

import com.donutautomine.mine.MouseHold;
import com.donutautomine.mine.ServerSync;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 1.14.5: bot "giữ chuột trái" qua đúng đường đập của game (xem MouseHold). */
@Mixin(
   value = {MinecraftClient.class},
   remap = false
)
public abstract class HoldBreakMixin {
   @ModifyVariable(
      method = {"handleBlockBreaking", "method_1590"},
      at = @At("HEAD"),
      argsOnly = true,
      remap = false
   )
   private boolean donutautomine$hold(boolean var1) {
      if (!MouseHold.wanted()) {
         return var1;
      } else {
         MinecraftClient var2 = (MinecraftClient)(Object)this;
         BlockHitResult var3 = MouseHold.target(var2);
         if (var3 == null) {
            return var1;
         } else {
            // bot vẫn giữ trạng thái đang đào giữa các tick (ServerSync.hideBreaking) → trả lại trước khi game đập tiếp, không đập lại từ đầu
            ServerSync.restoreBreaking(var2);
            var2.crosshairTarget = var3;
            MouseHold.clearScreenCooldown(var2);
            return true;
         }
      }
   }
}
