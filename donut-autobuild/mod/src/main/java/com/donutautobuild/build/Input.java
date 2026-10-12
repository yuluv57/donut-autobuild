package com.donutautobuild.build;

import com.donutautobuild.AutoBuildMod;
import java.lang.reflect.Field;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class Input {
   private static Field breakingField;
   private static boolean broken;
   private static boolean hidden;
   private static int sneakFlip = -100;
   private static int sprintFlip = -100;
   private static int toggleWarned;
   private static int sneakTick = -1;
   private static int sprintTick = -1;
   private static boolean sneakWant;
   private static boolean sprintWant;

   private Input() {
   }

   public static void clickUse(MinecraftClient var0) {
      KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(var0.options.useKey.getBoundKeyTranslationKey()));
   }

   public static boolean crosshairOn(MinecraftClient var0, BlockPos var1, Direction var2) {
      HitResult var3 = var0.crosshairTarget;
      if (var3 instanceof BlockHitResult var4 && var3.getType() == Type.BLOCK && var4.getBlockPos().equals(var1) && (var2 == null || var4.getSide() == var2)) {
         return true;
      }

      return false;
   }

   public static boolean canHoldAttack(MinecraftClient var0) {
      return var0.mouse.isCursorLocked() && var0.currentScreen == null;
   }

   private static Field field() throws NoSuchFieldException {
      if (breakingField != null) {
         return breakingField;
      } else {
         String var0 = "breakingBlock";

         try {
            var0 = FabricLoader.getInstance().getMappingResolver().mapFieldName("intermediary", "net.minecraft.class_636", "field_3717", "Z");
         } catch (Throwable var8) {
         }

         Class<ClientPlayerInteractionManager> var1 = ClientPlayerInteractionManager.class;
         Field var2 = null;

         for (String var6 : new String[]{var0, "field_3717", "breakingBlock"}) {
            try {
               var2 = var1.getDeclaredField(var6);
               break;
            } catch (NoSuchFieldException var9) {
            }
         }

         if (var2 == null) {
            throw new NoSuchFieldException("breakingBlock");
         } else {
            var2.setAccessible(true);
            breakingField = var2;
            return var2;
         }
      }
   }

   public static void hideBreaking(MinecraftClient var0) {
      if (!broken && var0.interactionManager != null && var0.interactionManager.isBreakingBlock()) {
         try {
            field().setBoolean(var0.interactionManager, false);
            hidden = true;
         } catch (Throwable var2) {
            broken = true;
            AutoBuildMod.LOGGER.warn("[AutoBuild] Không giữ được trạng thái đào: {}", var2.toString());
         }
      }
   }

   public static void restoreBreaking(MinecraftClient var0) {
      if (hidden && var0.interactionManager != null) {
         hidden = false;

         try {
            field().setBoolean(var0.interactionManager, true);
         } catch (Throwable var2) {
         }
      } else {
         hidden = false;
      }
   }

   private static boolean toggled(MinecraftClient var0, boolean var1) {
      try {
         return (Boolean)(var1 ? var0.options.getSprintToggled() : var0.options.getSneakToggled()).getValue();
      } catch (Throwable var3) {
         return false;
      }
   }

   public static void sneak(MinecraftClient var0, boolean var1, int var2) {
      if (var0.player != null) {
         if (var2 != sneakTick) {
            sneakTick = var2;
            sneakWant = false;
         }

         sneakWant |= var1;
         if (!toggled(var0, false)) {
            var0.options.sneakKey.setPressed(sneakWant);
         } else {
            if (toggleWarned++ == 0) {
               AutoBuildMod.LOGGER.info("[AutoBuild] Cài đặt \"Ngồi: Bật/tắt\" đang bật → mod chỉ bấm một nhịp để đổi tư thế");
            }

            if (var0.player.isSneaking() == sneakWant) {
               var0.options.sneakKey.setPressed(false);
            } else if (var2 - sneakFlip > 4) {
               var0.options.sneakKey.setPressed(true);
               sneakFlip = var2;
            }
         }
      }
   }

   public static void forceSneak(MinecraftClient var0, boolean var1, int var2) {
      sneakTick = var2;
      sneakWant = var1;
      if (var0.player != null) {
         if (!toggled(var0, false)) {
            var0.options.sneakKey.setPressed(var1);
         } else if (var0.player.isSneaking() == var1) {
            var0.options.sneakKey.setPressed(false);
         } else if (var2 - sneakFlip > 4) {
            var0.options.sneakKey.setPressed(true);
            sneakFlip = var2;
         }
      }
   }

   public static void forceStand(MinecraftClient var0) {
      sneakWant = false;
      sneakTick = -1;
      if (var0.player != null) {
         if (!toggled(var0, false)) {
            var0.options.sneakKey.setPressed(false);
         } else if (var0.player.isSneaking()) {
            var0.options.sneakKey.setPressed(true);
         } else {
            var0.options.sneakKey.setPressed(false);
         }
      }
   }

   /** 3.0.102: đặt hẳn trạng thái phím chạy nhanh của tick này (không cộng dồn với lần gọi trước trong cùng tick) */
   public static void forceSprint(MinecraftClient var0, boolean var1, int var2) {
      sprintTick = var2;
      sprintWant = var1;
      if (var0.player != null) {
         if (!toggled(var0, true)) {
            var0.options.sprintKey.setPressed(var1);
         } else if (var0.player.isSprinting() == var1) {
            var0.options.sprintKey.setPressed(false);
         } else if (var2 - sprintFlip > 4) {
            var0.options.sprintKey.setPressed(true);
            sprintFlip = var2;
         }
      }
   }

   public static void sprint(MinecraftClient var0, boolean var1, int var2) {
      if (var0.player != null) {
         if (var2 != sprintTick) {
            sprintTick = var2;
            sprintWant = false;
         }

         sprintWant |= var1;
         if (!toggled(var0, true)) {
            var0.options.sprintKey.setPressed(sprintWant);
         } else if (var0.player.isSprinting() == sprintWant) {
            var0.options.sprintKey.setPressed(false);
         } else if (var2 - sprintFlip > 4) {
            var0.options.sprintKey.setPressed(true);
            sprintFlip = var2;
         }
      }
   }
}
