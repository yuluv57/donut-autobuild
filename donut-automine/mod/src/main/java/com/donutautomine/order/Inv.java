package com.donutautomine.order;

import java.util.function.Predicate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

public final class Inv {
   private static long swapAt;

   private Inv() {
   }

   public static int count(MinecraftClient var0, Item var1) {
      return count(var0, (Predicate<ItemStack>)(var1x -> var1x.isOf(var1)));
   }

   public static int count(MinecraftClient var0, Predicate<ItemStack> var1) {
      if (var0.player == null) {
         return 0;
      } else {
         int var2 = 0;
         PlayerInventory var3 = var0.player.getInventory();

         for (int var4 = 0; var4 < 36; var4++) {
            ItemStack var5 = var3.getStack(var4);
            if (!var5.isEmpty() && var1.test(var5)) {
               var2 += var5.getCount();
            }
         }

         ItemStack var6 = var0.player.getOffHandStack();
         if (!var6.isEmpty() && var1.test(var6)) {
            var2 += var6.getCount();
         }

         return var2;
      }
   }

   public static int freeSlots(MinecraftClient var0) {
      if (var0.player == null) {
         return 0;
      } else {
         int var1 = 0;

         for (int var2 = 0; var2 < 36; var2++) {
            if (var0.player.getInventory().getStack(var2).isEmpty()) {
               var1++;
            }
         }

         return var1;
      }
   }

   public static boolean canUseInventory(MinecraftClient var0) {
      return var0.player != null
         && var0.interactionManager != null
         && var0.currentScreen == null
         && var0.player.currentScreenHandler == var0.player.playerScreenHandler
         && var0.player.currentScreenHandler.getCursorStack().isEmpty();
   }

   public static int handlerSlot(int var0) {
      return var0 < 9 ? 36 + var0 : var0;
   }

   public static boolean settled() {
      return System.currentTimeMillis() - swapAt > 250L;
   }

   public static boolean equip(MinecraftClient var0, Predicate<ItemStack> var1, int var2) {
      if (var0.player == null) {
         return false;
      } else {
         PlayerInventory var3 = var0.player.getInventory();
         if (var1.test(var0.player.getMainHandStack())) {
            return true;
         } else {
            for (int var4 = 0; var4 < 9; var4++) {
               if (var1.test(var3.getStack(var4))) {
                  var3.selectedSlot = var4;
                  return true;
               }
            }

            if (canUseInventory(var0) && settled()) {
               for (int var6 = 9; var6 < 36; var6++) {
                  if (var1.test(var3.getStack(var6))) {
                     int var5 = Math.max(0, Math.min(8, var2));
                     var0.interactionManager.clickSlot(var0.player.playerScreenHandler.syncId, var6, var5, SlotActionType.SWAP, var0.player);
                     var3.selectedSlot = var5;
                     swapAt = System.currentTimeMillis();
                     return false;
                  }
               }

               return false;
            } else {
               return false;
            }
         }
      }
   }

   public static int findSlot(MinecraftClient var0, Predicate<ItemStack> var1) {
      if (var0.player == null) {
         return -1;
      } else {
         for (int var2 = 0; var2 < 36; var2++) {
            ItemStack var3 = var0.player.getInventory().getStack(var2);
            if (!var3.isEmpty() && var1.test(var3)) {
               return var2;
            }
         }

         return -1;
      }
   }

   public static int emptyHotbar(MinecraftClient var0) {
      if (var0.player == null) {
         return -1;
      } else {
         for (int var1 = 0; var1 < 9; var1++) {
            if (var0.player.getInventory().getStack(var1).isEmpty()) {
               return var1;
            }
         }

         return -1;
      }
   }

   public static boolean swapOffhand(MinecraftClient var0, int var1) {
      if (canUseInventory(var0) && settled()) {
         var0.interactionManager.clickSlot(var0.player.playerScreenHandler.syncId, handlerSlot(var1), 40, SlotActionType.SWAP, var0.player);
         swapAt = System.currentTimeMillis();
         return true;
      } else {
         return false;
      }
   }

   public static boolean dropStack(MinecraftClient var0, int var1) {
      if (!canUseInventory(var0)) {
         return false;
      } else {
         var0.interactionManager.clickSlot(var0.player.playerScreenHandler.syncId, handlerSlot(var1), 1, SlotActionType.THROW, var0.player);
         return true;
      }
   }
}
