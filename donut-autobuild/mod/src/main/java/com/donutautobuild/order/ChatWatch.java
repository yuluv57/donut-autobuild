package com.donutautobuild.order;

import com.donutautobuild.AutoBuildMod;
import com.donutautobuild.logic.OrderRules;

/**
 * 3.0.94: ghi lại tin nhắn server (chat hệ thống) gần nhất để các luồng menu biết server đã phản hồi chưa,
 * ví dụ xóa home: bấm lần 1 → "Click again to confirm…", bấm lần 2 → "Home deleted".
 */
public final class ChatWatch {
   private static final int CAP = 64;
   private static final String[] RING = new String[CAP];
   private static long seq;
   private static boolean installed;

   private ChatWatch() {
   }

   /** gọi 1 lần khi mod khởi động; thiếu Fabric message API thì bỏ qua (các luồng tự dùng cách kiểm tra cũ) */
   public static void install() {
      try {
         ChatHook.register();
         installed = true;
      } catch (Throwable var1) {
         AutoBuildMod.LOGGER.warn("[AutoBuild] Không nghe được chat server ({}) → xóa home sẽ kiểm tra bằng danh sách /home", var1.toString());
      }
   }

   public static boolean installed() {
      return installed;
   }

   static synchronized void push(String var0) {
      if (var0 != null) {
         String var1 = OrderRules.text(var0);
         if (!var1.isEmpty() && !var1.startsWith("[autobuild]")) {
            seq++;
            RING[(int)(seq % CAP)] = var1;
         }
      }
   }

   /** mốc hiện tại; các tin sau mốc này là tin mới */
   public static synchronized long mark() {
      return seq;
   }

   /** có tin mới (sau mốc) chứa một trong các cụm (so sánh không dấu, chữ thường) */
   public static synchronized boolean since(long var0, String... var2) {
      for (long var3 = Math.max(var0 + 1L, seq - CAP + 1L); var3 <= seq; var3++) {
         String var5 = RING[(int)(var3 % CAP)];
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

   /** có tin mới (sau mốc) thỏa điều kiện */
   public static synchronized boolean any(long var0, java.util.function.Predicate<String> var2) {
      for (long var3 = Math.max(var0 + 1L, seq - CAP + 1L); var3 <= seq; var3++) {
         String var5 = RING[(int)(var3 % CAP)];
         if (var5 != null && var2.test(var5)) {
            return true;
         }
      }

      return false;
   }

   /** tin mới nhất (sau mốc) chứa cụm, để ghi log; "" nếu không có */
   public static synchronized String find(long var0, String var2) {
      for (long var3 = seq; var3 >= Math.max(var0 + 1L, seq - CAP + 1L); var3--) {
         String var5 = RING[(int)(var3 % CAP)];
         if (var5 != null && var5.contains(var2)) {
            return var5;
         }
      }

      return "";
   }
}
