package com.donutautomine.order;

import com.donutautomine.AutoMineMod;
import com.donutautomine.util.Texts;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.Game;

public final class ChatWatch {
   private static final int CAP = 64;
   private static final String[] RING = new String[64];
   private static long seq;

   private ChatWatch() {
   }

   public static void install() {
      try {
         ClientReceiveMessageEvents.GAME.register((Game)(var0, var1x) -> {
            if (var0 != null) {
               push(var0.getString());
            }
         });
      } catch (Throwable var1) {
         AutoMineMod.LOGGER.warn("[AutoMine] Không nghe được chat server: {}", var1.toString());
      }
   }

   static synchronized void push(String var0) {
      String var1 = Texts.norm(var0);
      if (!var1.isEmpty() && !var1.startsWith("[automine]")) {
         seq++;
         RING[(int)(seq % 64L)] = var1;
      }
   }

   public static synchronized long mark() {
      return seq;
   }

   public static synchronized String find(long var0, String var2) {
      for (long var3 = seq; var3 >= Math.max(var0 + 1L, seq - 64L + 1L); var3--) {
         String var5 = RING[(int)(var3 % 64L)];
         if (var5 != null && var5.contains(var2)) {
            return var5;
         }
      }

      return "";
   }

   public static synchronized boolean any(long var0, String... var2) {
      for (long var3 = Math.max(var0 + 1L, seq - 64L + 1L); var3 <= seq; var3++) {
         String var5 = RING[(int)(var3 % 64L)];
         if (var5 != null) {
            for (String var9 : var2) {
               if (var5.contains(var9)) {
                  return true;
               }
            }
         }
      }

      return false;
   }
}
