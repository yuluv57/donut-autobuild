package com.donutautobuild.build;

import java.util.Iterator;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.ComparatorBlock;
import net.minecraft.block.ComposterBlock;
import net.minecraft.block.DaylightDetectorBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.RepeaterBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class StateMatch {
   private static final Set<String> LIVE = Set.of(
      "powered",
      "power",
      "extended",
      "triggered",
      "lit",
      "occupied",
      "locked",
      "age",
      "stage",
      "distance",
      "persistent",
      "moisture",
      "honey_level",
      "snowy",
      "bloom",
      "tip",
      "bottom",
      // 3.0.122 (người dùng): "open" chỉ là đang mở/đóng (cửa, cửa sập, cổng rào, thùng) → cùng khối là đúng, không đập
      "open",
      "enabled"
   );
   // 3.0.122: bỏ "open" (giờ là trạng thái, không bấm chuột phải đóng/mở nữa); delay/mode của repeater/comparator vẫn chỉnh cho đúng
   private static final Set<String> SETTINGS = Set.of("delay", "mode", "inverted");
   private static final Set<String> ORIENTATION = Set.of(
      "facing", "axis", "half", "type", "face", "rotation", "attachment", "hanging", "orientation", "vertical_direction", "part"
   );
   private static final Set<String> COUNTS = Set.of("layers", "candles", "pickles", "eggs");

   private StateMatch() {
   }

   public static boolean matches(BlockState var0, BlockState var1) {
      return compatible(var0, var1, false);
   }

   public static boolean placementMatches(BlockState var0, BlockState var1) {
      return compatible(var0, var1, true);
   }

   public static boolean orientationFree(BlockState var0) {
      for (Property var2 : var0.getProperties()) {
         if (ORIENTATION.contains(var2.getName())) {
            return false;
         }
      }

      return true;
   }

   private static boolean compatible(BlockState var0, BlockState var1, boolean var2) {
      if (var0 != null && var0.getBlock() == var1.getBlock()) {
         Iterator var3 = var1.getProperties().iterator();

         while (true) {
            Property var4;
            while (true) {
               if (!var3.hasNext()) {
                  return true;
               }

               var4 = (Property)var3.next();
               String var5 = var4.getName();
               if (var2) {
                  if (COUNTS.contains(var5)) {
                     if (Integer.parseInt(property(var0, var5)) > Integer.parseInt(property(var1, var5))) {
                        return false;
                     }
                  } else if (ORIENTATION.contains(var5)
                     && (
                        !var5.equals("type")
                           || !(var1.getBlock() instanceof ChestBlock)
                           || !property(var0, "type").equals("single")
                           || property(var1, "type").equals("single")
                     )
                     && (!var5.equals("type") || !(var1.getBlock() instanceof SlabBlock) || !property(var1, "type").equals("double"))) {
                     break;
                  }
               } else if (!LIVE.contains(var5)
                  && !blockState(var1, var5)
                  && !var5.equals("north")
                  && !var5.equals("south")
                  && !var5.equals("east")
                  && !var5.equals("west")
                  && !var5.equals("up")) {
                  break;
               }
            }

            if (!var0.contains(var4) || !var0.get(var4).equals(var1.get(var4))) {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   /**
    * 3.0.122 (người dùng): khối chỉ khác trạng thái thì bản chất vẫn thế, không cần đập — Litematica tô vàng nhưng vẫn đúng:
    * Composter (mức phân "level"), Note Block (nốt "note", nhạc cụ "instrument" — nhạc cụ do khối bên dưới quyết định).
    */
   private static boolean blockState(BlockState var0, String var1) {
      if (var0.getBlock() instanceof ComposterBlock) {
         return var1.equals("level");
      } else {
         return var0.getBlock() instanceof NoteBlock ? var1.equals("note") || var1.equals("instrument") : false;
      }
   }

   public static boolean placed(BlockState var0, BlockState var1) {
      if (var0 != null && var0.getBlock() == var1.getBlock()) {
         return matches(var0, var1)
            ? true
            : !canAdd(var0, var1) && !configurable(var0, var1) && (!(var1.getBlock() instanceof ChestBlock) || !chestAwaitingPartner(var0, var1));
      } else {
         return false;
      }
   }

   public static boolean chestAwaitingPartner(BlockState var0, BlockState var1) {
      return var0 != null
         && var1.getBlock() instanceof ChestBlock
         && var0.getBlock() == var1.getBlock()
         && property(var0, "type").equals("single")
         && !property(var1, "type").equals("single")
         && placementMatches(var0, var1);
   }

   public static BlockPos chestPartner(BlockPos var0, BlockState var1) {
      if (!(var1.getBlock() instanceof ChestBlock)) {
         return null;
      } else {
         String var2 = property(var1, "type");
         if (var2.equals("single")) {
            return null;
         } else {
            Direction var3 = (Direction)var1.get(Properties.HORIZONTAL_FACING);
            return var0.offset(var2.equals("left") ? var3.rotateYClockwise() : var3.rotateYCounterclockwise());
         }
      }
   }

   public static boolean canAdd(BlockState var0, BlockState var1) {
      if (var0.isReplaceable()) {
         return true;
      } else if (var0.getBlock() != var1.getBlock()) {
         return false;
      } else if (Materials.waterlog(var0, var1)) {
         return true;
      } else if (!(var1.getBlock() instanceof SlabBlock)) {
         for (String var3 : Set.of("layers", "candles", "pickles", "eggs")) {
            if (!property(var1, var3).isEmpty() && Integer.parseInt(property(var0, var3)) < Integer.parseInt(property(var1, var3))) {
               return true;
            }
         }

         return false;
      } else {
         return property(var1, "type").equals("double") && !property(var0, "type").equals("double");
      }
   }

   public static String property(BlockState var0, String var1) {
      for (Property var3 : var0.getProperties()) {
         if (var3.getName().equals(var1)) {
            return valueName(var0, var3);
         }
      }

      return "";
   }

   private static String valueName(BlockState var0, Property var1) {
      return var1.name(var0.get(var1));
   }

   public static boolean configurable(BlockState var0, BlockState var1) {
      if (!placementMatches(var0, var1)) {
         return false;
      } else if (property(var0, "powered").equals("true") && !(var0.getBlock() instanceof RepeaterBlock) && !(var0.getBlock() instanceof ComparatorBlock)) {
         // 3.0.122: repeater/comparator đang có điện vẫn chỉnh delay/mode bằng chuột phải được (chỉ khác "powered" thì đã coi là đúng)
         return false;
      } else {
         return !(var0.getBlock() instanceof RepeaterBlock)
               && !(var0.getBlock() instanceof ComparatorBlock)
               && !(var0.getBlock() instanceof TrapdoorBlock)
               && !(var0.getBlock() instanceof DoorBlock)
               && !(var0.getBlock() instanceof DaylightDetectorBlock)
            ? false
            : SETTINGS.stream().anyMatch(var2 -> !property(var0, var2).equals(property(var1, var2)));
      }
   }
}
