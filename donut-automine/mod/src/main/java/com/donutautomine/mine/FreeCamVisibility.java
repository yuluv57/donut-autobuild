package com.donutautomine.mine;

import com.donutautomine.AutoMineMod;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class FreeCamVisibility {
   private static final List<FreeCamVisibility.SavedFlag> saved = new ArrayList<>();
   private static boolean started;

   private FreeCamVisibility() {
   }

   private static void capture(Object var0, Class<?> var1, String var2) {
      try {
         Field var3 = var1.getField(var2);
         if (var3.getType() != boolean.class) {
            return;
         }

         saved.add(new FreeCamVisibility.SavedFlag(var0, var3, var3.getBoolean(var0)));
         var3.setBoolean(var0, false);
      } catch (RuntimeException | ReflectiveOperationException var4) {
         AutoMineMod.LOGGER.debug("[AutoMine] Freecam: render flag {} unavailable: {}", var2, var4.toString());
      }
   }

   public static void begin() {
      if (!started) {
         started = true;

         try {
            Class var0 = Class.forName("net.caffeinemc.mods.sodium.client.SodiumClientMod");
            Object var1 = var0.getMethod("options").invoke(null);
            Object var2 = var1.getClass().getField("performance").get(var1);

            for (String var6 : new String[]{"useFogOcclusion", "useEntityCulling", "useBlockFaceCulling"}) {
               capture(var2, var2.getClass(), var6);
            }
         } catch (LinkageError | RuntimeException | ReflectiveOperationException var8) {
            AutoMineMod.LOGGER.debug("[AutoMine] Freecam: optional Sodium visibility: {}", var8.toString());
         }

         try {
            capture(null, Class.forName("dev.tr7zw.entityculling.EntityCullingModBase"), "enabled");
         } catch (LinkageError | ClassNotFoundException var7) {
            AutoMineMod.LOGGER.debug("[AutoMine] Freecam: optional EntityCulling visibility: {}", var7.toString());
         }
      }
   }

   public static void enforce() {
      begin();

      for (FreeCamVisibility.SavedFlag var1 : saved) {
         try {
            if (var1.field.getBoolean(var1.target)) {
               var1.field.setBoolean(var1.target, false);
            }
         } catch (RuntimeException | ReflectiveOperationException var3) {
            AutoMineMod.LOGGER.debug("[AutoMine] Freecam render flag changed: {}", var3.toString());
         }
      }
   }

   public static void restore() {
      for (int var0 = saved.size() - 1; var0 >= 0; var0--) {
         FreeCamVisibility.SavedFlag var1 = saved.get(var0);

         try {
            var1.field.setBoolean(var1.target, var1.value);
         } catch (RuntimeException | ReflectiveOperationException var3) {
            AutoMineMod.LOGGER.warn("[AutoMine] Không khôi phục được render flag {}: {}", var1.field.getName(), var3.toString());
         }
      }

      saved.clear();
      started = false;
   }

   private record SavedFlag(Object target, Field field, boolean value) {
   }
}
