package com.donutautomine.order;

import com.donutautomine.AutoMineMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

public final class GuiWatch {
   private static long sentAt = -1L;
   private static String lastCmd = "";
   private static int misses;
   private static long lastCommandAt;

   private GuiWatch() {
   }

   public static long lastCommandAt() {
      return lastCommandAt;
   }

   public static void markCommand() {
      lastCommandAt = System.currentTimeMillis();
   }

   public static void sent(String var0) {
      sentAt = System.currentTimeMillis();
      lastCmd = var0;
   }

   public static int misses() {
      return misses;
   }

   public static long gap(long var0) {
      return misses > 0 ? 2000L : var0;
   }

   public static void tick(MinecraftClient var0) {
      if (var0.player == null) {
         sentAt = -1L;
      } else {
         boolean var1 = var0.currentScreen instanceof HandledScreen && var0.player.currentScreenHandler != var0.player.playerScreenHandler;
         if (var1) {
            misses = 0;
            sentAt = -1L;
         } else if (sentAt >= 0L && System.currentTimeMillis() - sentAt > 2000L) {
            sentAt = -1L;
            misses++;
            AutoMineMod.LOGGER.info("[AutoMine] /{} gửi 2 giây mà không có menu nào hiện (hụt {} lần liên tiếp)", lastCmd, misses);
         }
      }
   }
}
