package com.donutautobuild.build;

import com.donutautobuild.AutoBuildMod;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.malilib.util.LayerMode;
import fi.dy.masa.malilib.util.LayerRange;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Direction.Axis;

public final class LayerView {
   private static Integer shown;
   private static LayerMode previousMode;
   private static Integer manual;
   private static boolean manualAll;
   private static boolean connected;

   private LayerView() {
   }

   public static Integer manual() {
      return manual;
   }

   public static boolean manualAll() {
      return manualAll;
   }

   public static Integer viewing() {
      try {
         LayerRange var0 = DataManager.getRenderLayerRange();
         return var0.getLayerMode() == LayerMode.SINGLE_LAYER && var0.getAxis() == Axis.Y ? var0.getLayerSingle() : null;
      } catch (Throwable var1) {
         return null;
      }
   }

   public static void browse(Integer var0) {
      try {
         LayerRange var1 = DataManager.getRenderLayerRange();
         if (var0 == null) {
            var1.setLayerMode(LayerMode.ALL, false);
            manual = null;
            manualAll = true;
         } else {
            if (var1.getAxis() != Axis.Y) {
               var1.setAxis(Axis.Y);
            }

            if (var1.getLayerMode() != LayerMode.SINGLE_LAYER) {
               var1.setLayerMode(LayerMode.SINGLE_LAYER, false);
            }

            var1.setLayerSingle(var0);
            manual = var0;
            manualAll = false;
         }

         shown = null;
         releaseOwnership();
      } catch (Throwable var2) {
         AutoBuildMod.LOGGER.warn("Cannot change Litematica render layer", var2);
      }
   }

   public static Integer step(int var0, Integer var1, Integer var2, int var3) {
      Integer var4 = manual != null ? manual : viewing();
      int var5 = (var4 == null ? var3 : var4) + (var4 == null ? 0 : var0);
      if (var1 != null && var2 != null) {
         var5 = Math.max(var1, Math.min(var2, var5));
      }

      browse(var5);
      return var5;
   }

   public static void show(int var0) {
      if (shown == null || shown != var0) {
         manual = null;
         manualAll = false;

         try {
            LayerRange var1 = DataManager.getRenderLayerRange();
            if (shown == null) {
               previousMode = var1.getLayerMode();
            }

            if (var1.getAxis() != Axis.Y) {
               var1.setAxis(Axis.Y);
            }

            if (var1.getLayerMode() != LayerMode.SINGLE_LAYER) {
               var1.setLayerMode(LayerMode.SINGLE_LAYER, false);
            }

            if (var1.getLayerSingle() != var0) {
               var1.setLayerSingle(var0);
            }

            shown = var0;
            takeOwnership();
         } catch (Throwable var2) {
            AutoBuildMod.LOGGER.warn("Cannot change Litematica render layer", var2);
         }
      }
   }

   public static void hold(Integer var0) {
      MinecraftClient var1 = MinecraftClient.getInstance();
      if (var1 == null || var1.world == null) {
         disconnect();
      } else if (manual != null || manualAll) {
         shown = null;
      } else if (var0 == null) {
         restore();
      } else {
         show(var0);
      }
   }

   public static void restore() {
      if (shown != null && manual == null && !manualAll) {
         try {
            LayerRange var0 = DataManager.getRenderLayerRange();
            var0.setLayerMode(previousMode != null && previousMode != LayerMode.SINGLE_LAYER ? previousMode : LayerMode.ALL, false);
         } catch (Throwable var1) {
            AutoBuildMod.LOGGER.warn("Cannot restore Litematica render layer", var1);
         }

         shown = null;
      } else {
         shown = null;
      }

      releaseOwnership();
   }

   public static void clientTick(MinecraftClient var0) {
      boolean var1 = var0 != null && var0.world != null;
      if (!var1) {
         if (connected) {
            disconnect();
         }

         connected = false;
      } else {
         if (!connected) {
            connected = true;
            if (AutoBuildMod.config != null && (AutoBuildMod.config.layerFilterRepairPending || AutoBuildMod.config.litematicaLayerOwned)) {
               forceAll();
               shown = null;
               previousMode = null;
               manual = null;
               manualAll = false;
               AutoBuildMod.config.layerFilterRepairPending = false;
               AutoBuildMod.config.litematicaLayerOwned = false;
               AutoBuildMod.config.saveQuietly();
               AutoBuildMod.LOGGER.info("[AutoBuild] Đã gỡ bộ lọc tầng Litematica còn sót sau khi vào lại thế giới");
            }
         }
      }
   }

   private static void disconnect() {
      if (shown != null || AutoBuildMod.config != null && AutoBuildMod.config.litematicaLayerOwned) {
         forceAll();
         shown = null;
         previousMode = null;
         manual = null;
         manualAll = false;
         takeOwnership();
      }
   }

   private static void forceAll() {
      try {
         DataManager.getRenderLayerRange().setLayerMode(LayerMode.ALL, false);
      } catch (Throwable var1) {
         AutoBuildMod.LOGGER.warn("Cannot clear Litematica render layer", var1);
      }
   }

   private static void takeOwnership() {
      if (AutoBuildMod.config != null && !AutoBuildMod.config.litematicaLayerOwned) {
         AutoBuildMod.config.litematicaLayerOwned = true;
         AutoBuildMod.config.saveQuietly();
      }
   }

   private static void releaseOwnership() {
      if (AutoBuildMod.config != null && AutoBuildMod.config.litematicaLayerOwned) {
         AutoBuildMod.config.litematicaLayerOwned = false;
         AutoBuildMod.config.saveQuietly();
      }
   }
}
