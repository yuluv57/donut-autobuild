package com.donutautobuild.order;

import com.donutautobuild.AutoBuildMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

/**
 * 3.0.93: theo dõi các lệnh mở menu (/shop, /order, /home, /sell). Gửi lệnh mà 2 giây không có menu nào hiện = 1 lần hụt.
 *
 * <p>3.0.95 (người dùng đổi quy tắc): CHỈ /home hụt 5 lần liên tiếp mới coi là server lag → thoát ra vào lại.
 * /shop, /order, /sell hụt thì việc đó tự bỏ sau 5 lần, mod xây phần khác rồi thử lại sau (không thoát ra vào lại).
 */
public final class GuiWatch {
   public static final int RETRY_TICKS = 40;
   public static final int MAX_MISSES = 5;
   /** 3.0.98: nguội lệnh giữa 2 lệnh mở menu (/shop, /sell) — người dùng muốn đặt lá xong là /shop mua nước ngay (bản cũ 4 giây) */
   public static final long COOLDOWN_MS = 1500L;
   private static long sentAt = -1L;
   private static String lastCmd = "";
   private static boolean lastHome;
   private static int misses;
   private static int homeMisses;

   private GuiWatch() {
   }

   /** lệnh mở menu không phải /home (shop, order, sell) */
   public static void sent(String var0) {
      sent(var0, false);
   }

   public static void sent(String var0, boolean var1) {
      sentAt = System.currentTimeMillis();
      lastCmd = var0;
      lastHome = var1;
   }

   public static int misses() {
      return misses;
   }

   /** khoảng cách tối thiểu giữa 2 lệnh: đang gửi lại vì hụt → 2 giây, bình thường → var0 */
   public static long gap(long var0) {
      return misses > 0 ? 2000L : var0;
   }

   /** server lag thật: /home gửi 5 lần liên tiếp không có menu */
   public static boolean lagging() {
      return homeMisses >= MAX_MISSES;
   }

   public static void reset() {
      misses = 0;
      homeMisses = 0;
      sentAt = -1L;
   }

   public static void tick(MinecraftClient var0) {
      if (var0.player == null) {
         sentAt = -1L;
      } else {
         boolean var1 = var0.currentScreen instanceof HandledScreen && var0.player.currentScreenHandler != var0.player.playerScreenHandler;
         if (var1) {
            misses = 0;
            homeMisses = 0;
            sentAt = -1L;
         } else if (sentAt >= 0L && System.currentTimeMillis() - sentAt > 2000L) {
            sentAt = -1L;
            misses++;
            if (lastHome) {
               homeMisses++;
               AutoBuildMod.LOGGER.info("[AutoBuild] /{} gửi 2 giây mà không có menu nào hiện (/home hụt {} lần liên tiếp)", lastCmd, homeMisses);
            } else {
               AutoBuildMod.LOGGER.info("[AutoBuild] /{} gửi 2 giây mà không có menu nào hiện (hụt {} lần; không phải /home nên không thoát ra vào lại)", lastCmd, misses);
            }
         }
      }
   }
}
