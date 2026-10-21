package com.donutautomine.mine;

import com.donutautomine.AutoMineMod;
import java.lang.reflect.Field;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class FreeCam {
   private static boolean active;
   private static boolean prevCulling;
   private static ClientPlayerEntity owner;
   private static ClientWorld ownerWorld;
   private static double x;
   private static double y;
   private static double z;
   private static float yaw;
   private static float pitch;
   private static long lastNanos;
   private static Class<?> externalFreecam;
   private static boolean externalChecked;

   private FreeCam() {
   }

   public static boolean active() {
      return active;
   }

   public static float viewYaw() {
      return yaw;
   }

   public static double viewY() {
      return y;
   }

   public static boolean controlsPlayer(Entity var0) {
      return active && var0 == owner;
   }

   public static void look(double var0, double var2) {
      if (active) {
         yaw += (float)(var0 * 0.15);
         pitch = MathHelper.clamp(pitch + (float)(var2 * 0.15), -90.0F, 90.0F);
      }
   }

   public static void toggle(MinecraftClient var0) {
      if (active) {
         disable(var0);
      } else if (var0.player != null && var0.world != null) {
         owner = var0.player;
         ownerWorld = var0.world;
         Vec3d var1 = owner.getEyePos();
         x = var1.x;
         y = var1.y;
         z = var1.z;
         yaw = owner.getYaw();
         pitch = owner.getPitch();
         lastNanos = System.nanoTime();
         prevCulling = var0.chunkCullingEnabled;
         active = true;
         setCulling(var0, false);
         FreeCamTerrain.reset();
         FreeCamVisibility.begin();
         refreshTerrain(var0);
         BotInput.install(var0);
         if (AutoMineMod.controller == null || !AutoMineMod.controller.running()) {
            BotInput.INSTANCE.clear();
         }

         if (var0.gameRenderer != null) {
            var0.gameRenderer.setRenderHand(false);
         }

         owner.sendMessage(Text.literal("§b[AutoMine] §fFreecam BẬT — bot tiếp tục đào. W/A/S/D bay, Space lên, Shift xuống; N để về."), true);
      }
   }

   public static void adoptExternal(MinecraftClient var0) {
      if (!externalChecked) {
         externalChecked = true;

         for (String var4 : new String[]{"com.donutautobuild.build.Freecam", "com.donutautobuild.build.FreeCam"}) {
            try {
               externalFreecam = Class.forName(var4);
               break;
            } catch (LinkageError | ClassNotFoundException var7) {
            }
         }
      }

      if (externalFreecam != null && var0.player != null && var0.world != null) {
         try {
            if (!(Boolean)externalFreecam.getMethod("active").invoke(null)) {
               return;
            }

            double[] var8 = new double[5];
            String[] var9 = new String[]{"x", "y", "z", "yaw", "pitch"};

            for (int var10 = 0; var10 < var9.length; var10++) {
               Field var11 = externalFreecam.getDeclaredField(var9[var10]);
               var11.setAccessible(true);
               var8[var10] = ((Number)var11.get(null)).doubleValue();
            }

            externalFreecam.getMethod("disable", MinecraftClient.class).invoke(null, var0);
            if (!active) {
               toggle(var0);
               x = var8[0];
               y = var8[1];
               z = var8[2];
               yaw = (float)var8[3];
               pitch = (float)var8[4];
            }

            BotInput.install(var0);
            AutoMineMod.LOGGER.info("[AutoMine] Chuyển freecam AutoBuild sang freecam AutoMine để bot tiếp tục đi/nhảy/đào.");
         } catch (LinkageError | ReflectiveOperationException var6) {
            AutoMineMod.LOGGER.warn("[AutoMine] Không chuyển được freecam AutoBuild: {}", var6.toString());
            externalFreecam = null;
         }
      }
   }

   public static void disable(MinecraftClient var0) {
      if (active) {
         active = false;
         owner = null;
         ownerWorld = null;
         if (var0.gameRenderer != null) {
            var0.gameRenderer.setRenderHand(true);
         }

         FreeCamVisibility.restore();
         setCulling(var0, prevCulling);
         refreshTerrain(var0);
         if (AutoMineMod.controller == null || !AutoMineMod.controller.running()) {
            BotInput.uninstall(var0);
         }
      }
   }

   private static void setCulling(MinecraftClient var0, boolean var1) {
      if (var0.chunkCullingEnabled != var1) {
         var0.chunkCullingEnabled = var1;
         if (var0.worldRenderer != null) {
            var0.worldRenderer.scheduleTerrainUpdate();
         }
      }
   }

   private static void refreshTerrain(MinecraftClient var0) {
      if (var0.worldRenderer != null) {
         var0.worldRenderer.scheduleTerrainUpdate();
      }
   }

   public static void lock(MinecraftClient var0) {
   }

   public static void absorbMouse(MinecraftClient var0) {
   }

   private static void consume(KeyBinding var0) {
      if (var0 != null) {
         while (var0.wasPressed()) {
         }

         var0.setPressed(false);
      }
   }

   public static void guardInput(MinecraftClient var0) {
      if (active) {
         if (var0.player == owner && var0.world == ownerWorld && var0.player != null) {
            FreeCamVisibility.enforce();
            if (var0.chunkCullingEnabled) {
               setCulling(var0, false);
            }

            BotInput.install(var0);
            if (AutoMineMod.controller == null || !AutoMineMod.controller.running()) {
               BotInput.INSTANCE.clear();
            }

            consume(var0.options.attackKey);
            consume(var0.options.pickItemKey);
            consume(var0.options.dropKey);
            consume(var0.options.swapHandsKey);
            if (!Keys.wantUse) {
               consume(var0.options.useKey);
            }

            for (KeyBinding var4 : var0.options.hotbarKeys) {
               if (var4 != null) {
                  while (var4.wasPressed()) {
                  }
               }
            }
         } else {
            disable(var0);
         }
      }
   }

   public static double[] motion(float var0, double var1, double var3, double var5, double var7, double var9) {
      double var11 = Math.sqrt(var1 * var1 + var3 * var3 + var5 * var5);
      double var13 = var11 < 1.0E-6 ? 0.0 : var7 * Math.clamp(var9, 0.0, 0.1) / var11;
      double var15 = Math.sin(Math.toRadians(var0));
      double var17 = Math.cos(Math.toRadians(var0));
      return new double[]{(-var15 * var1 + var17 * var3) * var13, var5 * var13, (var17 * var1 + var15 * var3) * var13};
   }

   private static boolean down(KeyBinding var0) {
      return var0 != null && var0.isPressed();
   }

   public static double[] frame() {
      MinecraftClient var0 = MinecraftClient.getInstance();
      if (active && var0 != null) {
         FreeCamVisibility.enforce();
         if (var0.chunkCullingEnabled) {
            setCulling(var0, false);
         }

         long var1 = System.nanoTime();
         double var3 = (var1 - lastNanos) / 1.0E9;
         lastNanos = var1;
         if (var0.currentScreen == null && var0.player != null) {
            GameOptions var5 = var0.options;
            double[] var6 = motion(
               yaw,
               (down(var5.forwardKey) ? 1 : 0) - (down(var5.backKey) ? 1 : 0),
               (down(var5.leftKey) ? 1 : 0) - (down(var5.rightKey) ? 1 : 0),
               (down(var5.jumpKey) ? 1 : 0) - (down(var5.sneakKey) ? 1 : 0),
               AutoMineMod.config.freecamSpeed,
               var3
            );
            x = x + var6[0];
            y = y + var6[1];
            z = z + var6[2];
         }

         return new double[]{x, y, z, yaw, pitch};
      } else {
         return null;
      }
   }
}
