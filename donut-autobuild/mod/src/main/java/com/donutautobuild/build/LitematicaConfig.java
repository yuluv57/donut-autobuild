package com.donutautobuild.build;

import com.donutautobuild.AutoBuildMod;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.config.Configs.Generic;
import fi.dy.masa.malilib.config.options.ConfigBoolean;

public final class LitematicaConfig {
   private static int warned;

   private LitematicaConfig() {
   }

   public static void relax() {
      try {
         boolean var0 = off(Generic.EASY_PLACE_MODE, "Easy Place mode");
         var0 |= off(Generic.EASY_PLACE_HOLD_ENABLED, "Easy Place (giữ chuột)");
         var0 |= off(Generic.PLACEMENT_RESTRICTION, "Placement restriction");
         if (var0) {
            Configs.saveToFile();
         }
      } catch (Throwable var1) {
         if (warned++ == 0) {
            AutoBuildMod.LOGGER.warn("[AutoBuild] Không tắt được Easy Place của Litematica", var1);
         }
      }
   }

   private static boolean off(ConfigBoolean var0, String var1) {
      if (var0 != null && var0.getBooleanValue()) {
         var0.setBooleanValue(false);
         AutoBuildMod.LOGGER.info("[AutoBuild] Đã tắt {} của Litematica (nó chặn thao tác đặt/phá khối)", var1);
         return true;
      } else {
         return false;
      }
   }
}
