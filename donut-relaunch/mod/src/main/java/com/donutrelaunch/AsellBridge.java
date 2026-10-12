package com.donutrelaunch;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Đọc trạng thái mod ASell Elytra (id "donutsell") mà không cần mã nguồn của nó: lấy entrypoint DonutSellMod qua Fabric Loader,
 * rồi trường {@code taskManager} → {@code isRunning()}. Không có mod / đổi cấu trúc → trả null (không làm gì).
 */
final class AsellBridge {
   private static Object taskManager;
   private static Method isRunning;
   private static long triedAt;

   private AsellBridge() {
   }

   private static Object taskManager() {
      if (taskManager != null) {
         return taskManager;
      }

      long now = System.currentTimeMillis();
      if (now - triedAt < 60000L) {
         return null;
      }

      triedAt = now;

      try {
         Class<?> fl = Class.forName("net.fabricmc.loader.api.FabricLoader");
         Object loader = fl.getMethod("getInstance").invoke(null);
         Class<?> init = Class.forName("net.fabricmc.api.ClientModInitializer");
         List<?> list = (List<?>)fl.getMethod("getEntrypointContainers", String.class, Class.class).invoke(loader, "client", init);
         Class<?> ec = Class.forName("net.fabricmc.loader.api.entrypoint.EntrypointContainer");
         Class<?> mc = Class.forName("net.fabricmc.loader.api.ModContainer");
         Class<?> mm = Class.forName("net.fabricmc.loader.api.metadata.ModMetadata");

         for (Object c : list) {
            Object provider = ec.getMethod("getProvider").invoke(c);
            Object meta = mc.getMethod("getMetadata").invoke(provider);
            if ("donutsell".equals(mm.getMethod("getId").invoke(meta))) {
               Object ep = ec.getMethod("getEntrypoint").invoke(c);

               for (Class<?> k = ep.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
                  try {
                     Field f = k.getDeclaredField("taskManager");
                     f.setAccessible(true);
                     Object tm = f.get(ep);
                     if (tm != null) {
                        Method m = tm.getClass().getMethod("isRunning");
                        m.setAccessible(true);
                        isRunning = m;
                        taskManager = tm;
                        return tm;
                     }
                  } catch (NoSuchFieldException var13) {
                  }
               }
            }
         }
      } catch (Throwable var14) {
         RelaunchMod.LOG.info("[DonutRelaunch] Không đọc được trạng thái ASell: " + var14);
      }

      return null;
   }

   /** true/false = ASell đang chạy / đang dừng; null = không có ASell */
   static Boolean running() {
      Object tm = taskManager();
      if (tm == null) {
         return null;
      }

      try {
         return (Boolean)isRunning.invoke(tm);
      } catch (Throwable var2) {
         return null;
      }
   }

   private static final Map<String, Field> FIELDS = new HashMap<>();
   private static final Field MISSING;

   static {
      Field m = null;

      try {
         m = AsellBridge.class.getDeclaredField("triedAt");
      } catch (Throwable var2) {
      }

      MISSING = m;
   }

   /**
    * trường của SellTaskManager theo tên; trường do mixin của ASell thêm vào có thể mang tiền tố (vd. {@code asell123$hardResetMode}) nên
    * chấp nhận cả tên kết thúc bằng {@code $name}. Không có → null (ghi nhớ để khỏi tìm lại).
    */
   private static Field field(Object tm, String name) {
      Field cached = FIELDS.get(name);
      if (cached != null) {
         return cached == MISSING ? null : cached;
      }

      Field found = null;

      for (Class<?> k = tm.getClass(); k != null && k != Object.class && found == null; k = k.getSuperclass()) {
         for (Field f : k.getDeclaredFields()) {
            String n = f.getName();
            if (n.equals(name) || n.endsWith("$" + name)) {
               found = f;
               break;
            }
         }
      }

      if (found != null) {
         try {
            found.setAccessible(true);
         } catch (Throwable var8) {
            found = null;
         }
      }

      FIELDS.put(name, found == null ? MISSING : found);
      return found;
   }

   /** tên trạng thái hiện tại của ASell (vd. WAITING_ELYTRA_ORDER_MENU); null = không đọc được */
   static String state() {
      Object tm = taskManager();
      if (tm == null) {
         return null;
      }

      try {
         Field f = field(tm, "state");
         Object v = f == null ? null : f.get(tm);
         return v instanceof Enum ? ((Enum<?>)v).name() : null;
      } catch (Throwable var2) {
         return null;
      }
   }

   /** cờ boolean của ASell (vd. hardResetMode, serverResetPaused, safeGuiProbeMode); không có → false */
   static boolean flag(String name) {
      Object tm = taskManager();
      if (tm == null) {
         return false;
      }

      try {
         Field f = field(tm, name);
         return f != null && f.getType() == boolean.class && f.getBoolean(tm);
      } catch (Throwable var3) {
         return false;
      }
   }
}
