package com.donutautobuild.build;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class Interaction {
   private static final Map<Class<?>, Boolean> CACHE = new HashMap<>();
   private static String onUse;
   private static String onUseWithItem;

   private Interaction() {
   }

   private static void names() {
      if (onUse == null) {
         onUse = "onUse";
         onUseWithItem = "onUseWithItem";

         try {
            MappingResolver var0 = FabricLoader.getInstance().getMappingResolver();
            onUse = var0.mapMethodName(
               "intermediary",
               "net.minecraft.class_4970",
               "method_55766",
               "(Lnet/minecraft/class_2680;Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_1657;Lnet/minecraft/class_3965;)Lnet/minecraft/class_1269;"
            );
            onUseWithItem = var0.mapMethodName(
               "intermediary",
               "net.minecraft.class_4970",
               "method_55765",
               "(Lnet/minecraft/class_1799;Lnet/minecraft/class_2680;Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_1657;Lnet/minecraft/class_1268;Lnet/minecraft/class_3965;)Lnet/minecraft/class_1269;"
            );
         } catch (Throwable var1) {
         }
      }
   }

   public static boolean interactable(BlockState var0) {
      if (var0 == null || var0.isAir()) {
         return false;
      } else if (var0.hasBlockEntity()) {
         return true;
      } else {
         Class var1 = var0.getBlock().getClass();
         Boolean var2 = CACHE.get(var1);
         if (var2 != null) {
            return var2;
         } else {
            names();

            boolean var3;
            try {
               var3 = false;

               for (Class var4 = var1; var4 != null && var4 != AbstractBlock.class && var4 != Block.class; var4 = var4.getSuperclass()) {
                  if (declares(var4, onUse, BlockState.class, World.class, BlockPos.class, PlayerEntity.class, BlockHitResult.class)
                     || declares(
                        var4,
                        onUseWithItem,
                        ItemStack.class,
                        BlockState.class,
                        World.class,
                        BlockPos.class,
                        PlayerEntity.class,
                        Hand.class,
                        BlockHitResult.class
                     )) {
                     var3 = true;
                     break;
                  }
               }
            } catch (Throwable var5) {
               var3 = true;
            }

            CACHE.put(var1, var3);
            return var3;
         }
      }
   }

   private static boolean declares(Class<?> var0, String var1, Class<?>... var2) {
      try {
         Method var3 = var0.getDeclaredMethod(var1, var2);
         return var3 != null;
      } catch (NoSuchMethodException var4) {
         return false;
      }
   }

   public static boolean sneakSensitive(BlockState var0) {
      return var0.getBlock() instanceof ChestBlock;
   }
}
