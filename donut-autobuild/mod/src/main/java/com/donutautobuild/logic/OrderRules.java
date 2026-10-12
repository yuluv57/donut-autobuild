package com.donutautobuild.logic;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OrderRules {
   private static final Pattern PROGRESS = Pattern.compile("(?i)([\\d,.]+)\\s*([kmb])?\\s*/\\s*([\\d,.]+)\\s*([kmb])?\\s*delivered");

   private OrderRules() {
   }

   public static String text(String var0) {
      return Normalizer.normalize(var0.replaceAll("§.", ""), Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
   }

   public static OrderRules.Page page(String var0) {
      String var1 = text(var0);
      if (var1.equals("orders -> your orders")) {
         return OrderRules.Page.OWN;
      } else if (var1.equals("orders -> edit order")) {
         return OrderRules.Page.EDIT;
      } else if (var1.equals("orders -> collect items")) {
         return OrderRules.Page.STORAGE;
      } else {
         return !var1.equals("orders") && !var1.matches("orders \\(page [0-9]+\\)") ? OrderRules.Page.OTHER : OrderRules.Page.MARKET;
      }
   }

   public static boolean button(String var0, String var1) {
      String var2 = text(var0);
      return var2.equals(var1) || var2.equals(var1 + " items");
   }

   public static boolean maxOrdersMarker(String var0) {
      return text(var0).contains("max orders");
   }

   public static boolean noItemsMessage(String var0) {
      return text(var0).contains("you have no items to collect");
   }

   public static long[] delivered(List<String> var0) {
      for (String var2 : var0) {
         Matcher var3 = PROGRESS.matcher(text(var2));
         if (var3.find()) {
            return new long[]{number(var3.group(1), var3.group(2)), number(var3.group(3), var3.group(4))};
         }
      }

      return new long[]{-1L, -1L};
   }

   static long number(String var0, String var1) {
      try {
         double var2 = Double.parseDouble(var0.replace(",", ""));
         if (var1 != null) {
            String var4 = var1.toLowerCase(Locale.ROOT);
            switch (var4) {
               case "k":
                  var2 *= 1000.0;
                  break;
               case "m":
                  var2 *= 1000000.0;
                  break;
               case "b":
                  var2 *= 1.0E9;
            }
         }

         return Math.round(var2);
      } catch (NumberFormatException var7) {
         return -1L;
      }
   }

   public static enum Page {
      MARKET,
      OWN,
      EDIT,
      STORAGE,
      OTHER;

      private Page() {
      }
   }
}
