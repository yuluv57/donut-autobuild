package com.donutautobuild.build;

import com.donutautobuild.AutoBuildMod;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;

public final class PlacementAccess {
   private static Method method;

   private PlacementAccess() {
   }

   private static Method method() {
      if (method != null) {
         return method;
      } else {
         LinkedHashSet<String> var0 = new LinkedHashSet<>();

         try {
            var0.add(
               FabricLoader.getInstance()
                  .getMappingResolver()
                  .mapMethodName("intermediary", "net.minecraft.class_1747", "method_7707", "(Lnet/minecraft/class_1750;)Lnet/minecraft/class_2680;")
            );
         } catch (Throwable var4) {
         }

         var0.add("getPlacementState");
         var0.add("method_7707");

         for (String var2 : var0) {
            try {
               Method var3 = BlockItem.class.getDeclaredMethod(var2, ItemPlacementContext.class);
               var3.setAccessible(true);
               method = var3;
               return var3;
            } catch (NoSuchMethodException var5) {
            }
         }

         throw new IllegalStateException("Không tìm thấy BlockItem.getPlacementState");
      }
   }

   public static BlockState state(BlockItem var0, ItemPlacementContext var1) {
      try {
         return (BlockState)method().invoke(var0, var1);
      } catch (ReflectiveOperationException var3) {
         AutoBuildMod.LOGGER.debug("getPlacementState failed", var3);
         return null;
      }
   }
}
