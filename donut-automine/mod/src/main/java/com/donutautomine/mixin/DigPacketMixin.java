package com.donutautomine.mixin;

import com.donutautomine.mine.DigLog;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.14.6: chẩn đoán — ghi gói đập / quay mặt client gửi (xem DigLog), không đổi gì */
@Mixin(
   value = {ClientCommonNetworkHandler.class},
   remap = false
)
public abstract class DigPacketMixin {
   @Inject(
      method = {"sendPacket", "method_52787"},
      at = {@At("HEAD")},
      remap = false
   )
   private void donutautomine$logDig(Packet<?> var1, CallbackInfo var2) {
      try {
         DigLog.sent(var1);
      } catch (Throwable var4) {
      }
   }
}
