package com.donutautomine.mine;

import com.donutautomine.AutoMineMod;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

public final class ServerSync {
   private static Method getter;
   private static Field mapField;
   private static Field breakingField;
   private static boolean ackBroken;
   private static boolean breakBroken;
   private static boolean hidden;

   private ServerSync() {
   }

   private static String mapMethod(String var0, String var1, String var2, String var3) {
      try {
         return FabricLoader.getInstance().getMappingResolver().mapMethodName("intermediary", var0, var1, var2);
      } catch (Throwable var5) {
         return var3;
      }
   }

   private static String mapField(String var0, String var1, String var2, String var3) {
      try {
         return FabricLoader.getInstance().getMappingResolver().mapFieldName("intermediary", var0, var1, var2);
      } catch (Throwable var5) {
         return var3;
      }
   }

   private static Long2ObjectMap<?> pendingMap(MinecraftClient var0) {
      if (!ackBroken && var0.world != null) {
         try {
            if (getter == null) {
               String var1 = mapMethod("net.minecraft.class_638", "method_41925", "()Lnet/minecraft/class_7202;", "getPendingUpdateManager");

               for (String var5 : new String[]{var1, "method_41925", "getPendingUpdateManager"}) {
                  try {
                     getter = ClientWorld.class.getDeclaredMethod(var5);
                     break;
                  } catch (NoSuchMethodException var9) {
                  }
               }

               if (getter == null) {
                  throw new NoSuchMethodException("getPendingUpdateManager");
               }

               getter.setAccessible(true);
            }

            Object var11 = getter.invoke(var0.world);
            if (var11 == null) {
               return null;
            } else {
               if (mapField == null) {
                  String var12 = mapField(
                     "net.minecraft.class_7202", "field_37953", "Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;", "blockPosToPendingUpdate"
                  );

                  for (String var6 : new String[]{var12, "field_37953", "blockPosToPendingUpdate"}) {
                     try {
                        mapField = var11.getClass().getDeclaredField(var6);
                        break;
                     } catch (NoSuchFieldException var8) {
                     }
                  }

                  if (mapField == null) {
                     for (Field var19 : var11.getClass().getDeclaredFields()) {
                        if (Long2ObjectMap.class.isAssignableFrom(var19.getType())) {
                           mapField = var19;
                           break;
                        }
                     }
                  }

                  if (mapField == null) {
                     throw new NoSuchFieldException("blockPosToPendingUpdate");
                  }

                  mapField.setAccessible(true);
               }

               return (Long2ObjectMap<?>)mapField.get(var11);
            }
         } catch (Throwable var10) {
            ackBroken = true;
            AutoMineMod.LOGGER.warn("[AutoMine] Không đọc được trạng thái xác nhận của server: {}", var10.toString());
            return null;
         }
      } else {
         return null;
      }
   }

   public static boolean pending(MinecraftClient var0, BlockPos var1) {
      Long2ObjectMap var2 = pendingMap(var0);
      return var2 != null && var2.containsKey(var1.asLong());
   }

   public static int pendingCount(MinecraftClient var0) {
      Long2ObjectMap var1 = pendingMap(var0);
      return var1 == null ? 0 : var1.size();
   }

   public static int pingMs(MinecraftClient var0) {
      try {
         if (var0.getNetworkHandler() != null && var0.player != null) {
            PlayerListEntry var1 = var0.getNetworkHandler().getPlayerListEntry(var0.player.getUuid());
            if (var1 != null) {
               return Math.max(0, Math.min(2000, var1.getLatency()));
            }
         }
      } catch (Throwable var2) {
      }

      return 150;
   }

   public static int pingTicks(MinecraftClient var0) {
      return Math.max(1, (pingMs(var0) + 49) / 50);
   }

   private static Field breaking() throws NoSuchFieldException {
      if (breakingField != null) {
         return breakingField;
      } else {
         String var0 = mapField("net.minecraft.class_636", "field_3717", "Z", "breakingBlock");

         for (String var4 : new String[]{var0, "field_3717", "breakingBlock"}) {
            try {
               breakingField = ClientPlayerInteractionManager.class.getDeclaredField(var4);
               break;
            } catch (NoSuchFieldException var6) {
            }
         }

         if (breakingField == null) {
            throw new NoSuchFieldException("breakingBlock");
         } else {
            breakingField.setAccessible(true);
            return breakingField;
         }
      }
   }

   public static void hideBreaking(MinecraftClient var0) {
      if (!breakBroken && var0.interactionManager != null && var0.interactionManager.isBreakingBlock()) {
         try {
            breaking().setBoolean(var0.interactionManager, false);
            hidden = true;
         } catch (Throwable var2) {
            breakBroken = true;
            AutoMineMod.LOGGER.warn("[AutoMine] Không giữ được trạng thái đào: {}", var2.toString());
         }
      }
   }

   public static void restoreBreaking(MinecraftClient var0) {
      if (hidden && var0.interactionManager != null) {
         try {
            breaking().setBoolean(var0.interactionManager, true);
         } catch (Throwable var2) {
         }
      }

      hidden = false;
   }

   public static void dropHidden() {
      hidden = false;
   }
}
