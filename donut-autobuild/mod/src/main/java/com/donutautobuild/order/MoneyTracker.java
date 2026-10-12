package com.donutautobuild.order;

import com.donutautobuild.AutoBuildMod;
import com.donutautobuild.BuildConfig;
import com.donutautobuild.logic.OrderRules;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 3.0.98: cộng dồn số tiền đã bỏ ra để xây công trình.
 * Đọc tin nhắn server "You bought 1 Water Bucket for $ 3.9K" trong lúc mod đang xây (hoặc đang tự mua ở /shop),
 * và tiền thu lại khi mod tự /sell ("$ 18.2K" ngay sau khi đóng menu bán). Chỉ về 0 khi người dùng bấm nút Reset.
 */
public final class MoneyTracker {
   private static final Pattern BOUGHT = Pattern.compile("^you bought (\\d+) (.+?) for \\$ ?([0-9][0-9.,]*)\\s*([kmbt])?\\b.*$");
   private static final Pattern BARE = Pattern.compile("^\\+?\\$ ?([0-9][0-9.,]*)\\s*([kmbt])?$");
   private static volatile boolean selling;
   private static volatile long sellUntil;

   private MoneyTracker() {
   }

   /** SellJob gọi khi đang bán (menu /sell mở → đóng) để tính tiền thu lại */
   public static void selling(boolean var0) {
      if (!var0 && selling) {
         // tin "$ …" (tiền bán) có thể tới sau khi đóng menu vài giây
         sellUntil = System.currentTimeMillis() + 6000L;
      }

      selling = var0;
   }

   private static boolean counting() {
      return AutoBuildMod.controller != null && AutoBuildMod.controller.countsMoney();
   }

   public static double parse(String var0, String var1) {
      try {
         double var2 = Double.parseDouble(var0.replace(",", ""));
         if (var1 != null) {
            switch (var1) {
               case "k":
                  var2 *= 1000.0;
                  break;
               case "m":
                  var2 *= 1000000.0;
                  break;
               case "b":
                  var2 *= 1.0E9;
                  break;
               case "t":
                  var2 *= 1.0E12;
            }
         }

         return var2;
      } catch (NumberFormatException var4) {
         return -1.0;
      }
   }

   static void onChat(String var0) {
      if (var0 != null) {
         BuildConfig var1 = AutoBuildMod.config;
         if (var1 != null) {
            String var2 = OrderRules.text(var0);
            Matcher var3 = BOUGHT.matcher(var2);
            if (var3.matches()) {
               if (counting()) {
                  double var4 = parse(var3.group(3), var3.group(4));
                  if (var4 > 0.0) {
                     if (var1.moneySince <= 0L) {
                        var1.moneySince = System.currentTimeMillis();
                     }

                     var1.moneySpent += var4;
                     var1.moneyBuys++;
                     var1.saveQuietly();
                  }
               }
            } else {
               Matcher var6 = BARE.matcher(var2);
               if (var6.matches() && (selling || System.currentTimeMillis() < sellUntil)) {
                  double var7 = parse(var6.group(1), var6.group(2));
                  if (var7 > 0.0) {
                     var1.moneyEarned += var7;
                     var1.saveQuietly();
                     AutoBuildMod.LOGGER.info("[AutoBuild] /sell thu về ${} (tổng thu {})", format(var7), format(var1.moneyEarned));
                  }
               }
            }
         }
      }
   }

   public static void reset(BuildConfig var0) {
      var0.moneySpent = 0.0;
      var0.moneyEarned = 0.0;
      var0.moneyBuys = 0;
      var0.moneySince = System.currentTimeMillis();
      var0.saveQuietly();
      AutoBuildMod.LOGGER.info("[AutoBuild] Đã reset bộ đếm tiền xây");
   }

   /** 3900 → "3.9K", 1250000 → "1.25M" */
   public static String format(double var0) {
      double var2 = Math.abs(var0);
      String var4;
      if (var2 >= 1.0E9) {
         var4 = trim(var0 / 1.0E9) + "B";
      } else if (var2 >= 1000000.0) {
         var4 = trim(var0 / 1000000.0) + "M";
      } else if (var2 >= 1000.0) {
         var4 = trim(var0 / 1000.0) + "K";
      } else {
         var4 = trim(var0);
      }

      return var4;
   }

   private static String trim(double var0) {
      String var2 = String.format(Locale.ROOT, "%.2f", var0);
      if (var2.contains(".")) {
         while (var2.endsWith("0")) {
            var2 = var2.substring(0, var2.length() - 1);
         }

         if (var2.endsWith(".")) {
            var2 = var2.substring(0, var2.length() - 1);
         }
      }

      return var2;
   }

   /** dòng tóm tắt cho HUD / GUI */
   public static String summary(BuildConfig var0) {
      return "Đã chi $" + format(var0.moneySpent) + (var0.moneyEarned > 0.0 ? " · bán lại $" + format(var0.moneyEarned) : "");
   }
}
