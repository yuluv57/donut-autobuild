package com.donutautobuild.build;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BrewingStandBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

/**
 * 3.0.123 (người dùng): khối chứa trong bản vẽ có đồ bên trong (dropper, dispenser, hopper, rương, thùng, lò…) → đặt xong mở ra cho
 * đúng loại + số lượng (ô nào cũng được), lấy đồ dư/sai ra; đồ có tên riêng phải đúng tên.
 * Lớp này chỉ tính: bản vẽ cần gì, và cú bấm kế tiếp trong menu đang mở. BuildController lo đi tới, mở, đóng, lấy thêm đồ.
 */
public final class ContainerFill {
   /** loại đồ: vật phẩm + tên riêng (null = không đổi tên) */
   public record Key(Item item, String name) {
      public String label() {
         return this.item.getName().getString() + (this.name == null ? "" : " tên \"" + this.name + "\"");
      }
   }

   /** cần count món key; slots = các ô bản vẽ đặt món đó (ưu tiên, vd. ô nhiên liệu của lò) */
   public record Want(Key key, int count, List<Integer> slots) {
   }

   /** một cú bấm trong menu; slot/button/type như ClientPlayerInteractionManager.clickSlot */
   public record Click(int slot, int button, SlotActionType type, String why) {
   }

   private ContainerFill() {
   }

   /** khối có menu đồ người chơi mở được bằng chuột phải (không tính hộp shulker, crafter) */
   public static boolean fillable(BlockState var0) {
      Block var1 = var0.getBlock();
      return var1 instanceof ChestBlock
         || var1 instanceof BarrelBlock
         || var1 instanceof HopperBlock
         || var1 instanceof DispenserBlock
         || var1 instanceof AbstractFurnaceBlock
         || var1 instanceof BrewingStandBlock;
   }

   public static Key key(ItemStack var0) {
      Text var1 = var0.get(DataComponentTypes.CUSTOM_NAME);
      return new Key(var0.getItem(), var1 == null ? null : var1.getString());
   }

   /** đọc danh sách "Items" của block entity trong bản vẽ; gộp theo loại + tên */
   public static Map<Key, Want> wants(NbtList var0, RegistryWrapper.WrapperLookup var1) {
      LinkedHashMap<Key, Integer> var2 = new LinkedHashMap<>();
      LinkedHashMap<Key, List<Integer>> var3 = new LinkedHashMap<>();

      for (int var4 = 0; var4 < var0.size(); var4++) {
         NbtCompound var5 = var0.getCompound(var4);
         ItemStack var6 = ItemStack.fromNbt(var1, var5).orElse(ItemStack.EMPTY);
         if (!var6.isEmpty()) {
            Key var7 = key(var6);
            var2.merge(var7, var6.getCount(), Integer::sum);
            var3.computeIfAbsent(var7, var0x -> new ArrayList<>()).add(var5.getByte("Slot") & 255);
         }
      }

      LinkedHashMap<Key, Want> var8 = new LinkedHashMap<>();
      var2.forEach((var2x, var3x) -> var8.put(var2x, new Want(var2x, var3x, var3.get(var2x))));
      return var8;
   }

   /** gộp hai nửa rương đôi (hoặc nhiều nguồn) */
   public static Map<Key, Want> merge(Map<Key, Want> var0, Map<Key, Want> var1, int var2) {
      LinkedHashMap<Key, Want> var3 = new LinkedHashMap<>(var0);
      var1.forEach((var2x, var3x) -> var3.merge(var2x, shift(var3x, var2), (var0x, var1x) -> {
         ArrayList<Integer> var2y = new ArrayList<>(var0x.slots());
         var2y.addAll(var1x.slots());
         return new Want(var2x, var0x.count() + var1x.count(), var2y);
      }));
      return var3;
   }

   private static Want shift(Want var0, int var1) {
      ArrayList<Integer> var2 = new ArrayList<>();

      for (int var4 : var0.slots()) {
         var2.add(var4 + var1);
      }

      return new Want(var0.key(), var0.count(), var2);
   }

   /** một lần cho đồ vào một khối chứa: nhớ món đang cầm trên con trỏ để đặt vào từng chiếc */
   public static final class Job {
      private final Map<Key, Want> wants;
      private Key carryKey;
      private int carryLeft;
      private int carrySource = -1;
      public final List<Want> missing = new ArrayList<>();

      public Job(Map<Key, Want> var1) {
         this.wants = var1;
      }

      public Map<Key, Want> wants() {
         return this.wants;
      }

      /** số món key đang có trong phần khối chứa (ô 0..top-1) */
      public static Map<Key, Integer> have(ScreenHandler var0, int var1) {
         LinkedHashMap<Key, Integer> var2 = new LinkedHashMap<>();

         for (int var3 = 0; var3 < var1; var3++) {
            ItemStack var4 = var0.getSlot(var3).getStack();
            if (!var4.isEmpty()) {
               var2.merge(key(var4), var4.getCount(), Integer::sum);
            }
         }

         return var2;
      }

      /** đã đúng bản vẽ (loại + số lượng, không có đồ lạ) */
      public boolean satisfied(ScreenHandler var1, int var2) {
         Map<Key, Integer> var3 = have(var1, var2);
         if (!var3.keySet().equals(this.wants.keySet())) {
            return false;
         } else {
            for (Want var5 : this.wants.values()) {
               if (!Objects.equals(var3.get(var5.key()), var5.count())) {
                  return false;
               }
            }

            return true;
         }
      }

      /**
       * cú bấm kế tiếp; null = không còn gì làm được (đã đúng, hoặc chỉ còn thiếu đồ không có trong túi → xem missing).
       * top = số ô của khối chứa; ô top..top+35 là túi đồ người chơi.
       */
      public Click next(ScreenHandler var1, int var2) {
         this.missing.clear();
         ItemStack var3 = var1.getCursorStack();
         if (!var3.isEmpty()) {
            Key var4 = key(var3);
            if (this.carryLeft > 0 && var4.equals(this.carryKey)) {
               Want var5 = this.wants.get(var4);
               int var6 = this.target(var1, var2, var3, var5);
               if (var6 >= 0) {
                  ItemStack var7 = var1.getSlot(var6).getStack();
                  // còn cần ≥ cả chồng đang cầm và ô đích trống → đặt cả chồng (chuột trái), không thì từng chiếc (chuột phải)
                  if (this.carryLeft >= var3.getCount() && var7.isEmpty()) {
                     this.carryLeft = this.carryLeft - var3.getCount();
                     return new Click(var6, 0, SlotActionType.PICKUP, "đặt " + var3.getCount() + " " + var4.label() + " vào ô " + var6);
                  }

                  this.carryLeft--;
                  return new Click(var6, 1, SlotActionType.PICKUP, "đặt 1 " + var4.label() + " vào ô " + var6);
               }

               this.carryLeft = 0;
            }

            // trả phần còn lại trên con trỏ về túi
            int var8 = this.home(var1, var2, var3);
            this.carryKey = null;
            this.carryLeft = 0;
            return var8 < 0 ? null : new Click(var8, 0, SlotActionType.PICKUP, "trả " + var3.getCount() + " " + var4.label() + " về túi");
         } else {
            Map<Key, Integer> var9 = have(var1, var2);

            // đồ lạ / dư trong khối chứa → shift-click ra túi
            for (int var10 = 0; var10 < var2; var10++) {
               ItemStack var11 = var1.getSlot(var10).getStack();
               if (!var11.isEmpty()) {
                  Key var12 = key(var11);
                  Want var13 = this.wants.get(var12);
                  if (var13 == null || var9.getOrDefault(var12, 0) > var13.count()) {
                     if (!this.roomFor(var1, var2, var11)) {
                        return null;
                     }

                     return new Click(var10, 0, SlotActionType.QUICK_MOVE, "lấy " + var11.getCount() + " " + var12.label() + (var13 == null ? " (bản vẽ không có)" : " (dư)") + " ra");
                  }
               }
            }

            // thiếu → lấy từ túi
            for (Want var15 : this.wants.values()) {
               int var16 = var15.count() - var9.getOrDefault(var15.key(), 0);
               if (var16 > 0) {
                  int var17 = this.source(var1, var2, var15.key(), var16);
                  if (var17 < 0) {
                     this.missing.add(new Want(var15.key(), var16, var15.slots()));
                  } else {
                     this.carryKey = var15.key();
                     this.carryLeft = var16;
                     this.carrySource = var17;
                     return new Click(var17, 0, SlotActionType.PICKUP, "cầm " + var15.key().label() + " (cần thêm " + var16 + ")");
                  }
               }
            }

            return null;
         }
      }

      /** ô khối chứa nhận được món đang cầm: ưu tiên ô bản vẽ, rồi ô cùng loại còn chỗ, rồi ô trống */
      private int target(ScreenHandler var1, int var2, ItemStack var3, Want var4) {
         if (var4 != null) {
            for (int var6 : var4.slots()) {
               if (var6 < var2 && accepts(var1.getSlot(var6), var3)) {
                  return var6;
               }
            }
         }

         for (int var7 = 0; var7 < var2; var7++) {
            Slot var8 = var1.getSlot(var7);
            if (var8.hasStack() && accepts(var8, var3)) {
               return var7;
            }
         }

         for (int var9 = 0; var9 < var2; var9++) {
            Slot var10 = var1.getSlot(var9);
            if (!var10.hasStack() && accepts(var10, var3)) {
               return var9;
            }
         }

         return -1;
      }

      private static boolean accepts(Slot var0, ItemStack var1) {
         if (!var0.canInsert(var1)) {
            return false;
         } else {
            ItemStack var2 = var0.getStack();
            return var2.isEmpty()
               || ItemStack.areItemsAndComponentsEqual(var2, var1) && var2.getCount() < Math.min(var2.getMaxCount(), var0.getMaxItemCount(var2));
         }
      }

      /** ô túi đồ để trả món trên con trỏ: ô vừa lấy, ô cùng loại còn chỗ, ô trống */
      private int home(ScreenHandler var1, int var2, ItemStack var3) {
         if (this.carrySource >= var2 && this.carrySource < var1.slots.size()) {
            ItemStack var4 = var1.getSlot(this.carrySource).getStack();
            if (var4.isEmpty() || ItemStack.areItemsAndComponentsEqual(var4, var3) && var4.getCount() < var4.getMaxCount()) {
               return this.carrySource;
            }
         }

         for (int var5 = var2; var5 < var1.slots.size(); var5++) {
            ItemStack var6 = var1.getSlot(var5).getStack();
            if (!var6.isEmpty() && ItemStack.areItemsAndComponentsEqual(var6, var3) && var6.getCount() < var6.getMaxCount()) {
               return var5;
            }
         }

         for (int var7 = var2; var7 < var1.slots.size(); var7++) {
            if (!var1.getSlot(var7).hasStack()) {
               return var7;
            }
         }

         return -1;
      }

      private boolean roomFor(ScreenHandler var1, int var2, ItemStack var3) {
         for (int var4 = var2; var4 < var1.slots.size(); var4++) {
            ItemStack var5 = var1.getSlot(var4).getStack();
            if (var5.isEmpty() || ItemStack.areItemsAndComponentsEqual(var5, var3) && var5.getCount() < var5.getMaxCount()) {
               return true;
            }
         }

         return false;
      }

      /** ô túi đồ có món key: ưu tiên chồng vừa đủ / nhỏ nhất ≥ cần, không thì chồng lớn nhất */
      private int source(ScreenHandler var1, int var2, Key var3, int var4) {
         int var5 = -1;
         int var6 = Integer.MAX_VALUE;
         int var7 = -1;
         int var8 = 0;

         for (int var9 = var2; var9 < var1.slots.size(); var9++) {
            ItemStack var10 = var1.getSlot(var9).getStack();
            if (!var10.isEmpty() && key(var10).equals(var3)) {
               int var11 = var10.getCount();
               if (var11 >= var4 && var11 < var6) {
                  var6 = var11;
                  var5 = var9;
               }

               if (var11 > var8) {
                  var8 = var11;
                  var7 = var9;
               }
            }
         }

         return var5 >= 0 ? var5 : var7;
      }
   }
}
