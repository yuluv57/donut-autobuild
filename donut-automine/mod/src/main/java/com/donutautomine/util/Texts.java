package com.donutautomine.util;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public final class Texts {
   private Texts() {
   }

   public static String norm(String var0) {
      return var0 == null
         ? ""
         : Normalizer.normalize(var0.replaceAll("§.", ""), Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .toLowerCase(Locale.ROOT)
            .trim()
            .replaceAll("\\s+", " ");
   }

   public static List<String> lines(ItemStack var0) {
      ArrayList var1 = new ArrayList();
      if (var0 != null && !var0.isEmpty()) {
         var1.add(var0.getName().getString());
         LoreComponent var2 = (LoreComponent)var0.get(DataComponentTypes.LORE);
         if (var2 != null) {
            for (Text var4 : var2.lines()) {
               var1.add(var4.getString());
            }
         }

         return var1;
      } else {
         return var1;
      }
   }
}
