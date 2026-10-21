package com.donutautomine.mine;

import com.donutautomine.MineConfig;
import com.donutautomine.util.Texts;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Identifier;

public final class Tools {
   private static final Pattern USES = Pattern.compile("([0-9][0-9,.]*)\\s*/\\s*([0-9][0-9,.]*)");
   private static final Set<String> BAD_FOOD = Set.of(
      "rotten_flesh",
      "spider_eye",
      "poisonous_potato",
      "pufferfish",
      "chorus_fruit",
      "suspicious_stew",
      "chicken",
      "enchanted_golden_apple",
      "golden_apple",
      "honey_bottle",
      "ominous_bottle"
   );
   private final MineConfig config;
   private String pickId = "";
   private String pickName = "";
   private String pickHow = "";

   public Tools(MineConfig var1) {
      this.config = var1;
   }

   public static boolean isPickaxe(ItemStack var0) {
      return !var0.isEmpty() && var0.isIn(ItemTags.PICKAXES);
   }

   private static String id(ItemStack var0) {
      Identifier var1 = Registries.ITEM.getId(var0.getItem());
      return var1 == null ? "" : var1.toString();
   }

   private static boolean says3x3(ItemStack var0) {
      for (String var2 : Texts.lines(var0)) {
         String var3 = Texts.norm(var2).replace(" ", "");
         if (var3.contains("3x3") || var3.contains("3×3") || var3.contains("drill")) {
            return true;
         }
      }

      return false;
   }

   private boolean hinted(ItemStack var1) {
      String var2 = Texts.norm(this.config.pickaxeHint);
      if (var2.isEmpty()) {
         return false;
      } else {
         for (String var4 : Texts.lines(var1)) {
            if (Texts.norm(var4).contains(var2)) {
               return true;
            }
         }

         return false;
      }
   }

   public String choose(MinecraftClient var1) {
      ItemStack var2 = ItemStack.EMPTY;
      String var3 = "";
      int var4 = -1;
      ItemStack var5 = var1.player.getMainHandStack();

      for (int var6 = 0; var6 < 36; var6++) {
         ItemStack var7 = var1.player.getInventory().getStack(var6);
         if (isPickaxe(var7) || this.hinted(var7)) {
            int var8;
            String var9;
            if (this.hinted(var7)) {
               var8 = 400;
               var9 = "khớp tên \"" + this.config.pickaxeHint + "\"";
            } else if (says3x3(var7)) {
               var8 = 300;
               var9 = "lore có 3x3";
            } else if (var7 == var5) {
               var8 = 200;
               var9 = "cúp đang cầm";
            } else {
               var8 = 100;
               var9 = "cúp tốt nhất trong túi";
            }

            if (var7.isDamageable()) {
               var8 += Math.min(90, (var7.getMaxDamage() - var7.getDamage()) / 30);
            }

            if (var8 > var4) {
               var4 = var8;
               var2 = var7;
               var3 = var9;
            }
         }
      }

      if (var2.isEmpty()) {
         this.pickId = "";
         this.pickName = "";
         return null;
      } else {
         this.pickId = id(var2);
         this.pickName = var2.getName().getString();
         this.pickHow = var3;
         return this.pickName + " (" + var3 + ")";
      }
   }

   public boolean chosen() {
      return !this.pickId.isEmpty();
   }

   public String pickLabel() {
      return this.pickName.isEmpty() ? "—" : this.pickName;
   }

   public String pickHow() {
      return this.pickHow;
   }

   public boolean isOurPick(ItemStack var1) {
      return !var1.isEmpty() && !this.pickId.isEmpty() && id(var1).equals(this.pickId) && var1.getName().getString().equals(this.pickName);
   }

   public ItemStack findPick(MinecraftClient var1) {
      if (this.isOurPick(var1.player.getMainHandStack())) {
         return var1.player.getMainHandStack();
      } else {
         for (int var2 = 0; var2 < 36; var2++) {
            ItemStack var3 = var1.player.getInventory().getStack(var2);
            if (this.isOurPick(var3)) {
               return var3;
            }
         }

         return ItemStack.EMPTY;
      }
   }

   public static int durability(ItemStack var0) {
      return !var0.isEmpty() && var0.isDamageable() && !var0.contains(DataComponentTypes.UNBREAKABLE) ? var0.getMaxDamage() - var0.getDamage() : -1;
   }

   public static int loreUses(ItemStack var0) {
      for (String var2 : Texts.lines(var0)) {
         String var3 = Texts.norm(var2);
         if (var3.contains("use") || var3.contains("durab") || var3.contains("luot")) {
            Matcher var4 = USES.matcher(var3);
            if (var4.find()) {
               try {
                  return (int)Double.parseDouble(var4.group(1).replace(",", ""));
               } catch (NumberFormatException var6) {
               }
            }
         }
      }

      return -1;
   }

   public boolean isFiller(ItemStack var1) {
      return var1.isEmpty() ? false : this.config.fillerItems.contains(id(var1));
   }

   public boolean isJunk(ItemStack var1) {
      return !var1.isEmpty() && this.config.junkItems.contains(id(var1));
   }

   public static boolean isXp(ItemStack var0) {
      return var0.isOf(Items.EXPERIENCE_BOTTLE);
   }

   public static boolean isFood(ItemStack var0) {
      if (!var0.isEmpty() && var0.get(DataComponentTypes.FOOD) != null) {
         Identifier var1 = Registries.ITEM.getId(var0.getItem());
         return var1 != null && !BAD_FOOD.contains(var1.getPath());
      } else {
         return false;
      }
   }

   public static int foodValue(ItemStack var0) {
      FoodComponent var1 = (FoodComponent)var0.get(DataComponentTypes.FOOD);
      return var1 == null ? 0 : var1.nutrition();
   }

   public Item bestFood(MinecraftClient var1) {
      Item var2 = null;
      int var3 = -1;

      for (int var4 = 0; var4 < 36; var4++) {
         ItemStack var5 = var1.player.getInventory().getStack(var4);
         if (isFood(var5) && foodValue(var5) > var3) {
            var3 = foodValue(var5);
            var2 = var5.getItem();
         }
      }

      ItemStack var6 = var1.player.getOffHandStack();
      if (var2 == null && isFood(var6)) {
         var2 = var6.getItem();
      }

      return var2;
   }

   public boolean keep(ItemStack var1) {
      if (var1.isEmpty()) {
         return true;
      } else if (var1.isDamageable() || isPickaxe(var1) || this.isOurPick(var1)) {
         return true;
      } else if (!isFood(var1) && !isXp(var1)) {
         String var2 = Registries.ITEM.getId(var1.getItem()).getPath();
         return var2.contains("totem")
            || var2.contains("shulker_box")
            || var2.equals("ender_chest")
            || var2.endsWith("bucket")
            || var2.contains("elytra")
            || var2.equals("firework_rocket")
            || var2.contains("golden_apple")
            || var2.equals("torch")
            || var2.contains("potion");
      } else {
         return true;
      }
   }
}
