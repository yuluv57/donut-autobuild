package com.donutautobuild.order;

import com.donutautobuild.AutoBuildMod;
import com.donutautobuild.BuildConfig;
import com.donutautobuild.build.BucketRules;
import com.donutautobuild.build.InventoryAccess;
import com.donutautobuild.logic.OrderRules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public final class ShopBuyer {
   private final BuildConfig config;
   private Item wanted;
   private ShopBuyer.Step step;
   private int age;
   private int before;
   private int retries;
   private int openAttempts;
   private static long lastCommandAt;
   private long sentWorldTime;
   private boolean preClosed;
   private String clickTitle = "";
   private int stable;
   private int clickSync = -1;
   private int confirmClicks;
   private int ownedSync = -1;
   private String lastSig = "";
   private String status = "";
   private ShopBuyer.Result result = ShopBuyer.Result.FAILED;
   // 3.0.83: mua nhiều lần trong 1 lần mở shop (mỗi click = 1 lần mua, xô không xếp chồng)
   private int goal;
   private int lastHave;
   private int bought;
   private boolean pendingBuy;
   // 3.0.98: server lag — bấm mua mà món chưa về thì bấm thêm (tối đa 4 lần, mỗi giây 1 lần); 5 giây vẫn chưa về → đóng shop, xây tiếp
   private int clickRetries;
   private boolean lagged;
   private long moneyMark;
   private static final String[] NO_MONEY = new String[]{"not enough", "don't have enough", "dont have enough", "insufficient", "can't afford", "cannot afford", "khong du tien"};

   /** tin server báo hết tiền (bỏ qua tin chat của người chơi: có "tên: …" / "»") */
   private static boolean noMoney(String var0) {
      if (var0.length() <= 120 && !var0.contains(": ") && !var0.contains("»") && !var0.contains(">>")) {
         for (String var4 : NO_MONEY) {
            if (var0.contains(var4)) {
               return true;
            }
         }
      }

      return false;
   }

   /** số lần được bấm thêm khi món chưa về: cần nhiều thì bấm 4 lần (lỡ về hết cũng dùng được), cần 1–2 món thì chỉ bấm thêm 1 lần (tránh mua dư) */
   private int extraClicks(int var1) {
      return var1 >= 5 ? 4 : (var1 >= 3 ? 2 : 1);
   }

   /** lần mua vừa rồi bỏ dở vì server lag (bấm 5 giây không về) */
   public boolean lagged() {
      return this.lagged;
   }

   /** 3.0.88: nguội lệnh dùng chung với /home, /sethome */
   public static long lastCommandAt() {
      return lastCommandAt;
   }

   public static void markCommand() {
      lastCommandAt = System.currentTimeMillis();
   }

   public ShopBuyer(BuildConfig var1) {
      this.config = var1;
   }

   public void start(MinecraftClient var1, Item var2) {
      this.start(var1, var2, 1);
   }

   public int bought() {
      return this.bought;
   }

   public void start(MinecraftClient var1, Item var2, int var3) {
      this.wanted = var2;
      this.retries = 0;
      this.openAttempts = 0;
      this.clickTitle = "";
      this.before = InventoryAccess.count(var1, var2);
      this.step = ShopBuyer.Step.OPEN;
      this.preClosed = false;
      this.age = 0;
      this.stable = 0;
      this.lastSig = "";
      this.clickSync = -1;
      this.confirmClicks = 0;
      this.ownedSync = -1;
      this.goal = this.before + Math.max(1, var3);
      this.lastHave = this.before;
      this.bought = 0;
      this.pendingBuy = false;
      this.clickRetries = 0;
      this.lagged = false;
      this.moneyMark = ChatWatch.mark();
      this.result = this.before > 0 && var3 <= 1 ? ShopBuyer.Result.BOUGHT : ShopBuyer.Result.RUNNING;
      this.status = this.result == ShopBuyer.Result.BOUGHT
         ? "Đã có " + var2.getName().getString() + " trong túi"
         : "Mua " + Math.max(1, var3) + " " + var2.getName().getString() + " ở /" + this.config.shopCommand;
   }

   public boolean active() {
      return this.result == ShopBuyer.Result.RUNNING;
   }

   public ShopBuyer.Result result() {
      return this.result;
   }

   public String status() {
      return this.status;
   }

   public Item item() {
      return this.wanted;
   }

   public void cancel(MinecraftClient var1) {
      if (this.active()) {
         this.closeOwned(var1);
      }

      this.result = ShopBuyer.Result.FAILED;
   }

   private void closeOwned(MinecraftClient var1) {
      if (this.ownedSync < 0 && (this.step == ShopBuyer.Step.WAIT_GUI || this.step == ShopBuyer.Step.RETRY) && this.knownShop(var1)) {
         this.ownedSync = var1.player.currentScreenHandler.syncId;
      }

      if (var1.player != null && var1.currentScreen instanceof HandledScreen && var1.player.currentScreenHandler.syncId == this.ownedSync) {
         var1.player.closeHandledScreen();
      }

      this.ownedSync = -1;
   }

   private boolean knownShop(MinecraftClient var1) {
      if (var1.player != null && var1.currentScreen instanceof HandledScreen) {
         String var2 = OrderRules.text(var1.currentScreen.getTitle().getString());
         return var2.contains("shop") || var2.contains("quick buy") || var2.contains("confirm") || var2.contains("purchase");
      } else {
         return false;
      }
   }

   private static void drain(KeyBinding var0) {
      if (var0 != null) {
         var0.setPressed(false);

         while (var0.wasPressed()) {
         }
      }
   }

   private void stopInteraction(MinecraftClient var1) {
      if (var1.options != null) {
         drain(var1.options.useKey);
         drain(var1.options.attackKey);
      }

      if (var1.interactionManager.isBreakingBlock()) {
         var1.interactionManager.cancelBlockBreaking();
      }
   }

   private String screenState(MinecraftClient var1) {
      String var2 = var1.currentScreen == null
         ? "không có màn hình"
         : var1.currentScreen.getClass().getSimpleName() + "/" + var1.currentScreen.getTitle().getString();
      if (var1.player.currentScreenHandler == null) {
         return var2 + ", handler=null";
      } else {
         ItemStack var3 = var1.player.currentScreenHandler.getCursorStack();
         return var2
            + ", sync="
            + var1.player.currentScreenHandler.syncId
            + ", cursor="
            + (var3.isEmpty() ? "trống" : var3.getName().getString() + " x" + var3.getCount());
      }
   }

   private void finish(MinecraftClient var1, ShopBuyer.Result var2, String var3) {
      if (var2 == ShopBuyer.Result.FAILED) {
         this.retry(var1, var3);
      } else {
         this.result = var2;
         this.status = var3;
         this.closeOwned(var1);
         AutoBuildMod.LOGGER.info("[AutoBuild] Shop: {}", var3);
      }
   }

   /** 3.0.98: dừng hẳn (không đóng/mở lại shop): lag thì BuildController hoãn ô này, xây phần khác rồi quay lại */
   private void giveUp(MinecraftClient var1, boolean var2, ShopBuyer.Result var3, String var4) {
      this.lagged = var2;
      this.result = var3;
      this.status = var4;
      this.closeOwned(var1);
      AutoBuildMod.LOGGER.info("[AutoBuild] Shop: {}", var4);
   }

   private void retry(MinecraftClient var1, String var2) {
      this.closeOwned(var1);
      this.retries = Math.min(this.retries + 1, 1000000);
      this.step = ShopBuyer.Step.RETRY;
      this.preClosed = false;
      this.age = 0;
      this.stable = 0;
      this.lastSig = "";
      this.clickSync = -1;
      this.clickTitle = "";
      this.confirmClicks = 0;
      this.result = ShopBuyer.Result.RUNNING;
      this.status = var2 + " — đóng/mở lại shop, lần " + this.retries + "; vẫn chờ " + this.wanted.getName().getString();
      AutoBuildMod.LOGGER.info("[AutoBuild] Shop: {}", this.status);
   }

   private boolean closeOwnInventory(MinecraftClient var1) {
      if (var1.player != null
         && var1.currentScreen instanceof HandledScreen
         && var1.player.currentScreenHandler == var1.player.playerScreenHandler) {
         AutoBuildMod.LOGGER.info("[AutoBuild] Shop: túi đồ của người chơi đang mở ({}) → đóng lại rồi mua tiếp", this.screenState(var1));
         var1.player.closeHandledScreen();
         return true;
      } else {
         return false;
      }
   }

   private static int topSize(MinecraftClient var0) {
      return var0.player.currentScreenHandler.slots.size() - 36;
   }

   private int findSlot(MinecraftClient var1, Item var2) {
      for (int var3 = 0; var3 < topSize(var1); var3++) {
         Slot var4 = var1.player.currentScreenHandler.getSlot(var3);
         if (var4.hasStack() && var4.getStack().isOf(var2)) {
            return var3;
         }
      }

      return -1;
   }

   private int findConfirm(MinecraftClient var1) {
      for (int var2 = 0; var2 < topSize(var1); var2++) {
         ItemStack var3 = var1.player.currentScreenHandler.getSlot(var2).getStack();
         if (!var3.isEmpty()) {
            String var4 = OrderRules.text(var3.getName().getString());
            boolean var5 = var3.isOf(Items.LIME_STAINED_GLASS_PANE)
               || var3.isOf(Items.GREEN_STAINED_GLASS_PANE)
               || var3.isOf(Items.LIME_WOOL)
               || var3.isOf(Items.GREEN_WOOL)
               || var3.isOf(Items.LIME_CONCRETE)
               || var3.isOf(Items.GREEN_CONCRETE)
               || var3.isOf(Items.LIME_STAINED_GLASS)
               || var3.isOf(Items.GREEN_STAINED_GLASS);
            boolean var6 = var4.contains("confirm")
               || var4.contains("purchase")
               || var4.equals("buy")
               || var4.startsWith("buy ")
               || var4.contains("xac nhan")
               || var4.equals("mua");
            if (var6 || var5 && !var4.contains("cancel") && !var4.contains("huy")) {
               return var2;
            }
         }
      }

      return -1;
   }

   public void tick(MinecraftClient var1) {
      if (this.active() && var1.player != null && var1.interactionManager != null) {
         this.age++;
         if (this.step == ShopBuyer.Step.WAIT_ITEM && var1.currentScreen instanceof HandledScreen && var1.player.currentScreenHandler.syncId != this.clickSync) {
            this.ownedSync = var1.player.currentScreenHandler.syncId;
         }

         int var40 = InventoryAccess.count(var1, this.wanted);
         if (var40 > this.lastHave) {
            this.bought = this.bought + (var40 - this.lastHave);
            this.lastHave = var40;
            this.age = 0;
            this.confirmClicks = 0;
            this.pendingBuy = false;
            this.clickRetries = 0;
         }

         if (this.step == ShopBuyer.Step.WAIT_ITEM && ChatWatch.any(this.moneyMark, ShopBuyer::noMoney)) {
            this.giveUp(var1, false, this.bought > 0 ? ShopBuyer.Result.BOUGHT : ShopBuyer.Result.FAILED,
               (this.bought > 0 ? "Đã mua " + this.bought + " " + this.wanted.getName().getString() + "; " : "") + "server báo không đủ tiền");
            return;
         }

         if (var40 >= this.goal || var40 > 0 && this.goal - this.before <= 1) {
            this.finish(var1, ShopBuyer.Result.BOUGHT, "Đã mua " + (this.bought > 1 ? this.bought + " " : "") + this.wanted.getName().getString());
         } else if (this.bought > 0 && this.moreBuys(var1)) {
            return;
         } else {
            switch (this.step) {
               case OPEN:
                  if (this.closeOwnInventory(var1)) {
                     return;
                  }

                  if (var1.currentScreen instanceof HandledScreen && this.age < 60) {
                     // 3.0.91: menu server còn sót từ lần trước → tự đóng
                     if (this.age % 10 == 1) {
                        AutoBuildMod.LOGGER.info("[AutoBuild] Shop: menu \"{}\" còn mở từ lần trước → đóng rồi mở /{}", var1.currentScreen.getTitle().getString(), this.config.shopCommand);
                        var1.player.closeHandledScreen();
                     }

                     return;
                  }

                  if (var1.currentScreen != null) {
                     this.finish(var1, ShopBuyer.Result.BLOCKED, "Đóng màn hình đang mở rồi tiếp tục.");
                     return;
                  }

                  if (InventoryAccess.freeSlots(var1) < 1) {
                     this.finish(var1, ShopBuyer.Result.FAILED, "Túi đồ đầy, không mua được " + this.wanted.getName().getString());
                     return;
                  }

                  if (this.age < 2) {
                     return;
                  }

                  long var30 = System.currentTimeMillis();
                  long var31x = GuiWatch.gap(GuiWatch.COOLDOWN_MS);
                  if (var30 - lastCommandAt < var31x) {
                     this.status = "Chờ hết nguội lệnh /" + this.config.shopCommand + " (" + (var31x - (var30 - lastCommandAt)) / 1000L + "s)";
                     return;
                  }

                  this.stopInteraction(var1);
                  if (var1.player.playerScreenHandler != null && var1.player.currentScreenHandler != var1.player.playerScreenHandler) {
                     AutoBuildMod.LOGGER.info("[AutoBuild] Shop: đồng bộ lại cửa sổ cũ trước khi mở: {}", this.screenState(var1));
                     var1.player.closeHandledScreen();
                     this.age = 0;
                     return;
                  }

                  if (var1.player.networkHandler == null) {
                     return;
                  }

                  if (!this.preClosed) {
                     this.preClosed = true;
                     var1.player.closeHandledScreen();
                     this.age = 0;
                     return;
                  }

                  if (this.age < 3) {
                     return;
                  }

                  String var8 = this.config.shopCommand == null ? "" : this.config.shopCommand.trim();

                  while (var8.startsWith("/")) {
                     var8 = var8.substring(1).trim();
                  }

                  if (var8.isEmpty()) {
                     this.finish(var1, ShopBuyer.Result.BLOCKED, "Lệnh shop đang trống trong cài đặt.");
                     return;
                  }

                  this.step = ShopBuyer.Step.WAIT_GUI;
                  this.age = 0;
                  this.openAttempts++;
                  lastCommandAt = System.currentTimeMillis();
                  this.sentWorldTime = var1.world == null ? 0L : var1.world.getTime();
                  GuiWatch.sent(var8);
                  var1.player.networkHandler.sendChatCommand(var8);
                  this.status = "Đã yêu cầu mở /" + var8 + "; chờ menu (lần " + this.openAttempts + ")";
                  AutoBuildMod.LOGGER
                     .info(
                        "[AutoBuild] Shop: yêu cầu /{}, lần {}; {}; tay cầm {}",
                        new Object[]{var8, this.openAttempts, this.screenState(var1), var1.player.getMainHandStack().getItem().getName().getString()}
                     );
                  break;
               case WAIT_GUI:
                  if (this.closeOwnInventory(var1)) {
                     return;
                  }

                  if (!(var1.currentScreen instanceof HandledScreen)) {
                     // 3.0.93: 2 giây không có menu → gửi lại (5 lần liên tiếp không có → server lag, thoát ra vào lại)
                     if (this.age > GuiWatch.RETRY_TICKS) {
                        long var31 = var1.world == null ? 0L : var1.world.getTime() - this.sentWorldTime;
                        String var32 = "Chưa nhận được menu /"
                           + this.config.shopCommand
                           + " sau 2 giây: "
                           + this.screenState(var1)
                           + "; server chạy "
                           + var31
                           + "/100 tick"
                           + (var31 < 65L ? " → SERVER ĐANG LAG" : "");
                        if (this.openAttempts >= GuiWatch.MAX_MISSES || GuiWatch.lagging()) {
                           this.closeOwned(var1);
                           this.result = ShopBuyer.Result.FAILED;
                           this.status = var32 + " — tạm bỏ, xây phần khác rồi quay lại mua";
                           AutoBuildMod.LOGGER.info("[AutoBuild] Shop: {}", this.status);
                        } else {
                           this.finish(var1, ShopBuyer.Result.FAILED, var32);
                        }
                     }

                     return;
                  }

                  if (!this.knownShop(var1)) {
                     this.finish(var1, ShopBuyer.Result.BLOCKED, "Có menu khác đang mở: " + this.screenState(var1) + ". Đóng menu rồi nhấn K.");
                     return;
                  }

                  if (this.ownedSync != var1.player.currentScreenHandler.syncId) {
                     AutoBuildMod.LOGGER.info("[AutoBuild] Shop: nhận menu {}; {}", var1.currentScreen.getTitle().getString(), this.screenState(var1));
                  }

                  this.ownedSync = var1.player.currentScreenHandler.syncId;
                  if (!var1.player.currentScreenHandler.getCursorStack().isEmpty()) {
                     this.status = "Shop đã mở; chờ đồng bộ vật phẩm trên con trỏ";
                     if (this.age > 12) {
                        this.retry(var1, "Shop đã mở nhưng con trỏ còn vật phẩm: " + this.screenState(var1));
                     }

                     return;
                  }

                  if (this.age > 160) {
                     this.retry(var1, "Menu shop không ổn định: " + this.screenState(var1));
                     return;
                  }

                  StringBuilder var3 = new StringBuilder().append(this.ownedSync);

                  for (int var4 = 0; var4 < topSize(var1); var4++) {
                     var3.append('|').append(var1.player.currentScreenHandler.getSlot(var4).getStack().getItem());
                  }

                  String var9 = var3.toString();
                  if (var9.equals(this.lastSig)) {
                     this.stable++;
                  } else {
                     this.stable = 0;
                     this.lastSig = var9;
                  }

                  if (this.stable < 2) {
                     return;
                  }

                  int var5 = this.findSlot(var1, this.wanted);
                  if (var5 < 0) {
                     if (this.stable > 30) {
                        this.finish(
                           var1,
                           ShopBuyer.Result.FAILED,
                           "Không thấy " + this.wanted.getName().getString() + " trong shop (" + var1.currentScreen.getTitle().getString() + ")"
                        );
                     }

                     return;
                  }

                  this.clickSync = this.ownedSync;
                  this.clickTitle = var1.currentScreen.getTitle().getString();
                  this.moneyMark = ChatWatch.mark();
                  this.clickRetries = 0;
                  var1.interactionManager.clickSlot(this.ownedSync, var5, 0, SlotActionType.PICKUP, var1.player);
                  this.status = "Bấm mua " + this.wanted.getName().getString();
                  AutoBuildMod.LOGGER
                     .info(
                        "[AutoBuild] Shop: bấm {} ở ô {} ({})",
                        new Object[]{this.wanted.getName().getString(), var5, var1.currentScreen.getTitle().getString()}
                     );
                  this.step = ShopBuyer.Step.WAIT_ITEM;
                  this.age = 0;
                  break;
               case WAIT_ITEM:
                  if (var1.currentScreen instanceof HandledScreen && this.confirmClicks == 0 && this.age > 3) {
                     this.ownedSync = var1.player.currentScreenHandler.syncId;
                     int var6 = this.findConfirm(var1);
                     boolean var7 = var1.player.currentScreenHandler.syncId != this.clickSync
                        || !var1.currentScreen.getTitle().getString().equals(this.clickTitle)
                        || this.findSlot(var1, this.wanted) < 0;
                     if (var6 >= 0 && var7 && this.age > 10 && var1.player.currentScreenHandler.getCursorStack().isEmpty()) {
                        var1.interactionManager.clickSlot(this.ownedSync, var6, 0, SlotActionType.PICKUP, var1.player);
                        this.confirmClicks++;
                        AutoBuildMod.LOGGER.info("[AutoBuild] Shop: bấm xác nhận ở ô {}", var6);
                     }
                  }

                  // 3.0.98: server lag — món chưa về thì mỗi giây bấm thêm 1 lần (tối đa 4 lần) khi vẫn đang ở danh sách hàng
                  if (this.bought == 0
                     && this.confirmClicks == 0
                     && this.age > 0
                     && this.age % (this.goal - this.before >= 3 ? 20 : 40) == 0
                     && this.clickRetries < this.extraClicks(this.goal - this.before)
                     && var1.currentScreen instanceof HandledScreen
                     && var1.player.currentScreenHandler.syncId == this.clickSync
                     && var1.player.currentScreenHandler.getCursorStack().isEmpty()) {
                     int var33 = this.findSlot(var1, this.wanted);
                     if (var33 >= 0) {
                        this.clickRetries++;
                        var1.interactionManager.clickSlot(this.clickSync, var33, 0, SlotActionType.PICKUP, var1.player);
                        this.status = "Chưa về → bấm mua thêm lần " + (this.clickRetries + 1);
                        AutoBuildMod.LOGGER.info("[AutoBuild] Shop: {} chưa về sau {} giây → bấm thêm lần {} (server lag)", new Object[]{this.wanted.getName().getString(), this.age / 20, this.clickRetries + 1});
                     }
                  }

                  if (this.bought == 0 && this.age > 100) {
                     this.giveUp(var1, true, ShopBuyer.Result.FAILED,
                        "Bấm mua " + this.wanted.getName().getString() + " " + (this.clickRetries + 1) + " lần trong 5 giây mà chưa về (server lag) → đóng shop, xây tiếp, quay lại ô này sau");
                     return;
                  }

                  if (this.age > 200) {
                     if (this.bought > 0) {
                        this.finish(var1, ShopBuyer.Result.BOUGHT, "Đã mua " + this.bought + " " + this.wanted.getName().getString() + " (lần bấm cuối không nhận được — hết tiền?)");
                     } else {
                        this.finish(
                           var1, ShopBuyer.Result.FAILED, "Đã bấm mua nhưng chưa nhận được " + this.wanted.getName().getString() + " (hết tiền / shop đổi?)"
                        );
                     }
                  }
                  break;
               case RETRY:
                  if (this.age < (GuiWatch.misses() > 0 ? 2 : BucketRules.retryDelay(this.retries))) {
                     return;
                  }

                  if (var1.currentScreen instanceof HandledScreen) {
                     String var2 = OrderRules.text(var1.currentScreen.getTitle().getString());
                     if (var2.contains("shop") || var2.contains("quick buy") || var2.contains("confirm")) {
                        this.ownedSync = var1.player.currentScreenHandler.syncId;
                        this.closeOwned(var1);
                     }
                  }

                  if (var1.currentScreen != null) {
                     this.finish(var1, ShopBuyer.Result.BLOCKED, "Đóng màn hình đang mở rồi nhấn K; vẫn chưa hoàn tất ô cần đổ.");
                     return;
                  }

                  this.step = ShopBuyer.Step.OPEN;
                  this.age = 0;
            }
         }
      }
   }

   /**
    * 3.0.83: đã có ít nhất 1 món về túi, chưa đủ số cần → shop còn mở thì bấm mua tiếp ngay trong menu đó
    * (không đóng/mở lại /shop, không chờ nguội lệnh 4 giây). Trả về true khi đã xử lý tick này.
    */
   private boolean moreBuys(MinecraftClient var1) {
      if (InventoryAccess.freeSlots(var1) < 1) {
         this.finish(var1, ShopBuyer.Result.BOUGHT, "Đã mua " + this.bought + " " + this.wanted.getName().getString() + " (túi hết chỗ)");
         return true;
      } else if (!(var1.currentScreen instanceof HandledScreen) || !this.knownShop(var1)) {
         // menu đã đóng sau khi mua → dùng số đã có, không mở lại
         this.finish(var1, ShopBuyer.Result.BOUGHT, "Đã mua " + this.bought + " " + this.wanted.getName().getString() + " (shop đã đóng)");
         return true;
      } else if (this.pendingBuy) {
         // đã bấm, chờ món về (mỗi click = 1 lần mua). 3.0.98: server lag → mỗi giây bấm thêm (tối đa 3 lần); 5 giây vẫn chưa về → dừng với số đã mua
         if (this.age > 100) {
            this.lagged = true;
            this.finish(var1, ShopBuyer.Result.BOUGHT, "Đã mua " + this.bought + " " + this.wanted.getName().getString() + " (lần bấm cuối 5 giây chưa về — server lag, dùng số đã có)");
            return true;
         } else {
            if (this.age > 0 && this.age % 20 == 0 && this.clickRetries < Math.min(3, this.extraClicks(this.goal - this.lastHave)) && var1.player.currentScreenHandler.getCursorStack().isEmpty()) {
               int var3 = this.findSlot(var1, this.wanted);
               if (var3 >= 0) {
                  this.clickRetries++;
                  var1.interactionManager.clickSlot(var1.player.currentScreenHandler.syncId, var3, 0, SlotActionType.PICKUP, var1.player);
                  AutoBuildMod.LOGGER.info("[AutoBuild] Shop: lần mua tiếp chưa về sau {} giây → bấm thêm (server lag)", this.age / 20);
               }
            }

            // còn ở danh sách hàng → chờ; sang màn xác nhận → để nhánh WAIT_ITEM bấm xác nhận
            return this.findSlot(var1, this.wanted) >= 0;
         }
      } else if (this.step != ShopBuyer.Step.WAIT_ITEM || this.age < 3 || !var1.player.currentScreenHandler.getCursorStack().isEmpty()) {
         return this.step == ShopBuyer.Step.WAIT_ITEM;
      } else {
         this.ownedSync = var1.player.currentScreenHandler.syncId;
         int var2 = this.findSlot(var1, this.wanted);
         if (var2 < 0) {
            // đang ở màn xác nhận: để nhánh WAIT_ITEM bấm xác nhận; quá lâu thì dừng với số đã mua
            if (this.age > 60) {
               this.finish(var1, ShopBuyer.Result.BOUGHT, "Đã mua " + this.bought + " " + this.wanted.getName().getString());
               return true;
            }

            return false;
         } else {
            this.clickSync = this.ownedSync;
            this.clickTitle = var1.currentScreen.getTitle().getString();
            var1.interactionManager.clickSlot(this.ownedSync, var2, 0, SlotActionType.PICKUP, var1.player);
            this.age = 0;
            this.confirmClicks = 0;
            this.pendingBuy = true;
            this.status = "Mua tiếp " + this.wanted.getName().getString() + " (" + this.bought + "/" + (this.goal - this.before) + ")";
            return true;
         }
      }
   }

   public static enum Result {
      RUNNING,
      BOUGHT,
      FAILED,
      BLOCKED;

      private Result() {
      }
   }

   private static enum Step {
      OPEN,
      WAIT_GUI,
      WAIT_ITEM,
      RETRY;

      private Step() {
      }
   }
}
