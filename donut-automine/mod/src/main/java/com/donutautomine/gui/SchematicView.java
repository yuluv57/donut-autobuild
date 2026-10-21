package com.donutautomine.gui;

import com.donutautomine.AutoMineMod;
import fi.dy.masa.litematica.config.Configs.Visuals;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import java.util.ArrayList;
import java.util.List;

public final class SchematicView {
   private static boolean[] previous;

   private static ConfigBoolean[] options() {
      return new ConfigBoolean[]{
         Visuals.ENABLE_SCHEMATIC_BLOCKS,
         Visuals.ENABLE_SCHEMATIC_FLUIDS,
         Visuals.ENABLE_SCHEMATIC_OVERLAY,
         Visuals.RENDER_SCHEMATIC_ENTITIES,
         Visuals.RENDER_SCHEMATIC_TILE_ENTITIES
      };
   }

   public static void contents(boolean var0) {
      ConfigBoolean[] var1 = options();
      if (var0 && previous == null) {
         previous = new boolean[var1.length];
         List var2 = AutoMineMod.config.schematicBeforeHide;

         for (int var3 = 0; var3 < var1.length; var3++) {
            previous[var3] = var2.size() == var1.length ? (Boolean)var2.get(var3) : var1[var3].getBooleanValue();
         }

         AutoMineMod.config.schematicBeforeHide = new ArrayList<>();

         for (boolean var6 : previous) {
            AutoMineMod.config.schematicBeforeHide.add(var6);
         }
      }

      for (int var7 = 0; var7 < var1.length; var7++) {
         var1[var7].setBooleanValue(!var0 && (previous == null || previous[var7]));
      }

      if (!var0) {
         previous = null;
         AutoMineMod.config.schematicBeforeHide.clear();
      }

      AutoMineMod.config.hideSchematicContents = var0;
   }
}
