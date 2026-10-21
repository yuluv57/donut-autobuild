package com.donutautomine.order;

import com.donutautomine.AutoMineMod;
import com.donutautomine.MineConfig;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public final class SellJob {
   private final MineConfig config;
   private SellJob.Step step = SellJob.Step.OPEN;
   private SellJob.Result result = SellJob.Result.FAILED;
   private String status = "";
   private Predicate<ItemStack> sellable = var0 -> false;
   private Set<Integer> protectedSlots = Set.of();
   private int age;
   private int opens;
   private int before;
   private int moved;
   private int nextAt;
   private long chatMark;
   private final Set<Integer> tried = new HashSet<>();

   public SellJob(MineConfig var1) {
      this.config = var1;
   }

   public boolean active() {
      return this.result == SellJob.Result.RUNNING;
   }

   public SellJob.Result result() {
      return this.result;
   }

   public String status() {
      return this.status;
   }

   public void start(MinecraftClient var1, Predicate<ItemStack> var2) {
      this.start(var1, var2, Set.of());
   }

   public void start(MinecraftClient var1, Predicate<ItemStack> var2, Set<Integer> var3) {
      this.protectedSlots = Set.copyOf(var3);
      this.sellable = var2;
      this.step = SellJob.Step.OPEN;
      this.result = SellJob.Result.RUNNING;
      this.age = 0;
      this.opens = 0;
      this.moved = 0;
      this.nextAt = 0;
      this.tried.clear();
      this.before = count(var1, var2);
      this.status = "Bán đồ đào được bằng /" + this.cmd();
   }

   public void cancel(MinecraftClient var1) {
      if (this.active() && menuOpen(var1)) {
         var1.player.closeHandledScreen();
      }

      this.result = SellJob.Result.FAILED;
   }

   private String cmd() {
      String var1 = this.config.sellCommand == null ? "" : this.config.sellCommand.trim();

      while (var1.startsWith("/")) {
         var1 = var1.substring(1).trim();
      }

      return var1.isEmpty() ? "sell" : var1;
   }

   public static int count(MinecraftClient var0, Predicate<ItemStack> var1) {
      int var2 = 0;
      if (var0.player != null) {
         for (int var3 = 0; var3 < 36; var3++) {
            ItemStack var4 = var0.player.getInventory().getStack(var3);
            if (!var4.isEmpty() && var1.test(var4)) {
               var2 += var4.getCount();
            }
         }
      }

      return var2;
   }

   private static boolean menuOpen(MinecraftClient var0) {
      return var0.player != null && var0.currentScreen instanceof HandledScreen && var0.player.currentScreenHandler != var0.player.playerScreenHandler;
   }

   private void finish(SellJob.Result var1, String var2) {
      this.result = var1;
      this.status = var2;
      AutoMineMod.LOGGER.info("[AutoMine] Bán đồ: {}", var2);
   }

   public void tick(MinecraftClient var1) {
      if (this.active() && var1.player != null && var1.interactionManager != null) {
         this.age++;
         switch (this.step) {
            case OPEN:
               if (var1.currentScreen != null) {
                  if (this.age > 3) {
                     var1.player.closeHandledScreen();
                     this.age = 0;
                  }
               } else if (System.currentTimeMillis() - GuiWatch.lastCommandAt() < GuiWatch.gap(3500L)) {
                  this.status = "Chờ hết nguội lệnh để /" + this.cmd();
               } else if (++this.opens > 3) {
                  this.finish(SellJob.Result.FAILED, "gửi /" + this.cmd() + " 3 lần không có menu");
               } else {
                  GuiWatch.markCommand();
                  GuiWatch.sent(this.cmd());
                  this.chatMark = ChatWatch.mark();
                  var1.player.networkHandler.sendChatCommand(this.cmd());
                  AutoMineMod.LOGGER.info("[AutoMine] Bán đồ: gửi /{} (lần {}), cần bán {} món", new Object[]{this.cmd(), this.opens, this.before});
                  this.step = SellJob.Step.WAIT_GUI;
                  this.age = 0;
               }
               break;
            case WAIT_GUI:
               if (menuOpen(var1)) {
                  if (this.age >= 6 && var1.player.currentScreenHandler.getCursorStack().isEmpty()) {
                     int var7 = var1.player.currentScreenHandler.slots.size() - 36;
                     AutoMineMod.LOGGER.info("[AutoMine] Bán đồ: menu \"{}\" ({} ô) → chuyển đồ cần bán vào", var1.currentScreen.getTitle().getString(), var7);
                     this.step = SellJob.Step.PUT;
                     this.age = 0;
                     this.nextAt = 0;
                  }
               } else if (this.age > 40) {
                  this.step = SellJob.Step.OPEN;
                  this.age = 0;
               }
               break;
            case PUT:
               if (!menuOpen(var1)) {
                  this.step = SellJob.Step.VERIFY;
                  this.age = 0;
               } else if (this.age >= this.nextAt) {
                  for (Slot var4 : var1.player.currentScreenHandler.slots) {
                     if (var4.inventory == var1.player.getInventory() && var4.getIndex() < 36 && !this.protectedSlots.contains(var4.getIndex())) {
                        int var5 = var4.id;
                        ItemStack var6 = var4.getStack();
                        if (!var6.isEmpty() && !this.tried.contains(var5) && this.sellable.test(var6)) {
                           this.tried.add(var5);
                           this.moved = this.moved + var6.getCount();
                           var1.interactionManager.clickSlot(var1.player.currentScreenHandler.syncId, var5, 0, SlotActionType.QUICK_MOVE, var1.player);
                           this.nextAt = this.age + 2;
                           this.status = "Bỏ " + var6.getName().getString() + " vào menu bán";
                           return;
                        }
                     }
                  }

                  if (this.age >= this.nextAt + 6) {
                     var1.player.closeHandledScreen();
                     this.step = SellJob.Step.VERIFY;
                     this.age = 0;
                  }
               }
               break;
            case VERIFY:
               if (this.age >= 30) {
                  int var2 = count(var1, this.sellable);
                  String var3 = ChatWatch.find(this.chatMark, "sold");
                  if (var2 < this.before) {
                     this.finish(SellJob.Result.DONE, "đã bán " + (this.before - var2) + " món" + (var3.isEmpty() ? "" : " (\"" + var3 + "\")"));
                  } else {
                     this.finish(SellJob.Result.FAILED, "đóng menu /" + this.cmd() + " mà đồ vẫn còn (đã chuyển " + this.moved + " món)");
                  }
               }
         }
      }
   }

   public static enum Result {
      RUNNING,
      DONE,
      FAILED;
   }

   private static enum Step {
      OPEN,
      WAIT_GUI,
      PUT,
      VERIFY;
   }
}
