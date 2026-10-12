package com.donutautobuild.build;

import com.donutautobuild.AutoBuildMod;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

public final class ServerAck {
   private static Method getter;
   private static Field mapField;
   private static boolean broken;

   private ServerAck() {
   }

   private static Method getter() throws NoSuchMethodException {
      if (getter != null) {
         return getter;
      } else {
         String var0 = "getPendingUpdateManager";

         try {
            var0 = FabricLoader.getInstance()
               .getMappingResolver()
               .mapMethodName("intermediary", "net.minecraft.class_638", "method_41925", "()Lnet/minecraft/class_7202;");
         } catch (Throwable var7) {
         }

         Method var1 = null;

         for (String var5 : new String[]{var0, "method_41925", "getPendingUpdateManager"}) {
            try {
               var1 = ClientWorld.class.getDeclaredMethod(var5);
               break;
            } catch (NoSuchMethodException var8) {
            }
         }

         if (var1 == null) {
            throw new NoSuchMethodException("getPendingUpdateManager");
         } else {
            var1.setAccessible(true);
            getter = var1;
            return var1;
         }
      }
   }

   private static Field mapField(Class<?> var0) throws NoSuchFieldException {
      if (mapField != null) {
         return mapField;
      } else {
         String var1 = "blockPosToPendingUpdate";

         try {
            var1 = FabricLoader.getInstance()
               .getMappingResolver()
               .mapFieldName("intermediary", "net.minecraft.class_7202", "field_37953", "Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;");
         } catch (Throwable var8) {
         }

         Field var2 = null;

         for (String var6 : new String[]{var1, "field_37953", "blockPosToPendingUpdate"}) {
            try {
               var2 = var0.getDeclaredField(var6);
               break;
            } catch (NoSuchFieldException var9) {
            }
         }

         if (var2 == null) {
            for (Field var13 : var0.getDeclaredFields()) {
               if (Long2ObjectMap.class.isAssignableFrom(var13.getType())) {
                  var2 = var13;
                  break;
               }
            }
         }

         if (var2 == null) {
            throw new NoSuchFieldException("blockPosToPendingUpdate");
         } else {
            var2.setAccessible(true);
            mapField = var2;
            return var2;
         }
      }
   }

   private static Long2ObjectMap<?> map(MinecraftClient var0) {
      if (!broken && var0.world != null) {
         try {
            Object var1 = getter().invoke(var0.world);
            return var1 == null ? null : (Long2ObjectMap)mapField(var1.getClass()).get(var1);
         } catch (Throwable var2) {
            broken = true;
            AutoBuildMod.LOGGER.warn("[AutoBuild] Không đọc được trạng thái xác nhận của server: {}", var2.toString());
            return null;
         }
      } else {
         return null;
      }
   }

   public static int pendingCount(MinecraftClient var0) {
      Long2ObjectMap var1 = map(var0);
      return var1 == null ? 0 : var1.size();
   }

   public static boolean pending(MinecraftClient var0, BlockPos var1) {
      Long2ObjectMap var2 = map(var0);
      return var2 != null && var2.containsKey(var1.asLong());
   }

   public static boolean pending(MinecraftClient var0) {
      return pendingCount(var0) > 0;
   }
}
