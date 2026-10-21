package com.donutautomine.order;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.Locale;

public final class OrderRules {
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

   public static enum Page {
      MARKET,
      OWN,
      EDIT,
      STORAGE,
      OTHER;
   }
}
