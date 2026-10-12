package com.donutautobuild.order;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

/** 3.0.94: tách riêng để nếu thiếu Fabric message API thì chỉ lớp này lỗi (được bắt trong ChatWatch.install) */
final class ChatHook {
   private ChatHook() {
   }

   static void register() {
      ClientReceiveMessageEvents.GAME.register((var0, var1) -> {
         if (var0 != null) {
            String var2 = var0.getString();
            ChatWatch.push(var2);
            // 3.0.98: cộng tiền đã chi khi mod mua đồ
            MoneyTracker.onChat(var2);
         }
      });
   }
}
