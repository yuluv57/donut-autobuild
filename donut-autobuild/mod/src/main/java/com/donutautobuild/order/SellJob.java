package com.donutautobuild.order;

import com.donutautobuild.AutoBuildMod;
import com.donutautobuild.BuildConfig;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

/**
 * 3.0.95: bán đồ bằng /sell (DonutSMP): mở menu bán, chuyển (shift-click) các món cần bán từ túi vào menu, đóng menu → server bán.
 * Món cần bán do BuildController quyết định: xô rỗng, và đồ không nằm trong bảng vật liệu của công trình
 * (trừ cúp/đồ có độ bền, đồ ăn, khối kê, totem, ender chest, shulker box, các loại xô có đồ).
 * Kiểm tra lại số món sau khi đóng menu: không giảm → ghi log, không lặp lại nhiều lần.
 */
public final class SellJob {
   private final BuildConfig config;
   private SellJob.Step step = SellJob.Step.OPEN;
   private SellJob.Result result = SellJob.Result.FAILED;
   private String status = "";
   private Predicate<ItemStack> sellable = var0 -> false;
   private int age;
   private int opens;
   private int before;
   private int moved;
   private int nextAt;
   private long chatMark;
   private final Set<Integer> tried = new HashSet<>();
   private static final int SELL_WAIT_TICKS = 160;

   public SellJob(BuildConfig var1) {
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
      this.sellable = var2;
      this.step = SellJob.Step.OPEN;
      this.result = SellJob.Result.RUNNING;
      this.age = 0;
      this.opens = 0;
      this.moved = 0;
      this.nextAt = 0;
      this.tried.clear();
      this.before = count(var1, var2);
      this.status = "Bán đồ không cần bằng /" + this.cmd();
   }

   public void cancel(MinecraftClient var1) {
      if (this.active() && menuOpen(var1)) {
         var1.player.closeHandledScreen();
      }

      MoneyTracker.selling(false);

      this.result = SellJob.Result.FAILED;
   }

   private String cmd() {
      String var1 = this.config.sellCommand == null ? "" : this.config.sellCommand.trim();

      while (var1.startsWith("/")) {
         var1 = var1.substring(1).trim();
      }

      return var1.isEmpty() ? "sell" : var1;
   }

   /** tổng số món cần bán đang có trong túi */
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
      MoneyTracker.selling(false);
      this.result = var1;
      this.status = var2;
      AutoBuildMod.LOGGER.info("[AutoBuild] Bán đồ: {}", var2);
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
               } else if (System.currentTimeMillis() - ShopBuyer.lastCommandAt() < GuiWatch.gap(GuiWatch.COOLDOWN_MS)) {
                  this.status = "Chờ hết nguội lệnh để /" + this.cmd();
               } else if (++this.opens > 2) {
                  // 3.0.98: menu /sell trên DonutSMP có lúc 4–7 giây mới hiện; gửi lại dồn dập chỉ làm server bỏ qua → tối đa 2 lần, mỗi lần chờ 8 giây
                  this.finish(SellJob.Result.FAILED, "gửi /" + this.cmd() + " 2 lần (chờ 8 giây mỗi lần) không có menu → bán sau");
               } else {
                  ShopBuyer.markCommand();
                  GuiWatch.sent(this.cmd());
                  this.chatMark = ChatWatch.mark();
                  var1.player.networkHandler.sendChatCommand(this.cmd());
                  AutoBuildMod.LOGGER.info("[AutoBuild] Bán đồ: gửi /{} (lần {}), cần bán {} món", new Object[]{this.cmd(), this.opens, this.before});
                  this.step = SellJob.Step.WAIT_GUI;
                  this.age = 0;
               }
               break;
            case WAIT_GUI:
               if (menuOpen(var1)) {
                  if (this.age >= 6 && var1.player.currentScreenHandler.getCursorStack().isEmpty()) {
                     int var2 = var1.player.currentScreenHandler.slots.size() - 36;
                     AutoBuildMod.LOGGER.info("[AutoBuild] Bán đồ: menu \"{}\" ({} ô) → chuyển đồ cần bán vào", var1.currentScreen.getTitle().getString(), var2);
                     this.step = SellJob.Step.PUT;
                     this.age = 0;
                     this.nextAt = 0;
                     MoneyTracker.selling(true);
                  }
               } else if (this.age > SELL_WAIT_TICKS) {
                  this.step = SellJob.Step.OPEN;
                  this.age = 0;
               }
               break;
            case PUT:
               if (!menuOpen(var1)) {
                  this.step = SellJob.Step.VERIFY;
                  this.age = 0;
               } else if (this.age >= this.nextAt) {
                  int var3 = var1.player.currentScreenHandler.slots.size() - 36;

                  for (int var4 = var3; var4 < var3 + 36; var4++) {
                     ItemStack var5 = var1.player.currentScreenHandler.getSlot(var4).getStack();
                     if (!var5.isEmpty() && !this.tried.contains(var4) && this.sellable.test(var5)) {
                        this.tried.add(var4);
                        this.moved = this.moved + var5.getCount();
                        var1.interactionManager.clickSlot(var1.player.currentScreenHandler.syncId, var4, 0, SlotActionType.QUICK_MOVE, var1.player);
                        this.nextAt = this.age + 2;
                        this.status = "Bỏ " + var5.getName().getString() + " vào menu bán";
                        return;
                     }
                  }

                  // đã chuyển hết → chờ server đồng bộ rồi đóng menu (đóng = bán)
                  if (this.age >= this.nextAt + 6) {
                     var1.player.closeHandledScreen();
                     this.step = SellJob.Step.VERIFY;
                     this.age = 0;
                  }
               }
               break;
            case VERIFY:
               if (this.age >= 30) {
                  int var6 = count(var1, this.sellable);
                  String var7 = ChatWatch.find(this.chatMark, "sold");
                  if (var6 < this.before) {
                     this.finish(SellJob.Result.DONE, "đã bán " + (this.before - var6) + " món" + (var7.isEmpty() ? "" : " (\"" + var7 + "\")"));
                  } else {
                     this.finish(SellJob.Result.FAILED, "đóng menu /" + this.cmd() + " mà đồ vẫn còn trong túi (đã chuyển " + this.moved + " món) → không bán kiểu này được");
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
