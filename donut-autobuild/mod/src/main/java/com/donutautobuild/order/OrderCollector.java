package com.donutautobuild.order;

import com.donutautobuild.BuildConfig;
import com.donutautobuild.build.InventoryAccess;
import com.donutautobuild.logic.OrderRules;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

public final class OrderCollector {
   private boolean preClosed;
   private static final int STORAGE_BATCH = 3;
   private static final int STORAGE_SETTLE_TICKS = 8;
   private static final int[] COLLECT_SLOTS = new int[]{15, 13};
   private final BuildConfig config;
   private final Random random = new Random();
   private Item wanted;
   private OrderCollector.Step step;
   private int age;
   private int stable;
   private int clickAt;
   private int scanIndex;
   private int predicted;
   private int syncTicks;
   private int desiredCount;
   private int pageNumber;
   private int pageHash;
   private int sweepTicks;
   private int nextClickAt;
   private int emptyStorageTicks;
   private String lastSignature = "";
   private String status = "";
   private final Set<String> visitedOrders = new HashSet<>();
   private boolean clicked;
   private boolean noItemsMessage;
   private OrderCollector.Result result = OrderCollector.Result.EMPTY;
   private int ownedSync = -1;
   private int cursorTicks;
   private int cursorRetries;
   private int openTries;
   // 3.0.95: lần lấy này hỏng vì menu không mở / lag (không phải Order hết hàng) → thử lại sớm, không sang /shop
   private boolean noMenu;
   // 3.0.109: phát hiện order chưa có hàng giao (log 29/09 22:47:33–22:47:40: bấm Collect 10 lần, server trả "You have no items to collect"
   // 10 lần mà mod không biết — onChat() chưa từng được nối vào chat nên cờ noItemsMessage không bao giờ bật)
   private long collectMark = -1L;
   private int collectClicks;
   private String openedKey = "";
   private String openedLabel = "";
   private int skippedUndelivered;
   private int skippedEmpty;
   /** order vừa báo "no items to collect" → bỏ qua tới khi hết hạn hoặc số đã giao thay đổi (khóa có kèm số đã giao) */
   private static final java.util.Map<String, Long> EMPTY_UNTIL = new java.util.HashMap<>();

   public boolean noMenu() {
      return this.noMenu;
   }

   public OrderCollector(BuildConfig var1) {
      this.config = var1;
   }

   // 3.0.90: vật phẩm thay thế được chấp nhận (chế độ nhanh: cùng họ, vd. Black Stained Glass ← Glass)
   private Set<Item> accepted = Set.of();

   public Set<Item> accepted() {
      return this.accepted;
   }

   private boolean accept(ItemStack var1) {
      return !var1.isEmpty() && this.accepted.contains(var1.getItem());
   }

   private int have(MinecraftClient var1) {
      int var2 = 0;

      for (Item var4 : this.accepted) {
         var2 += InventoryAccess.count(var1, var4);
      }

      return var2;
   }

   public void start(Item var1, int var2) {
      this.start(var1, var2, Set.of());
   }

   public void start(Item var1, int var2, Set<Item> var3) {
      HashSet<Item> var4 = new HashSet<>(var3);
      var4.add(var1);
      this.accepted = var4;
      this.wanted = var1;
      this.desiredCount = Math.max(1, var2);
      this.visitedOrders.clear();
      this.openTries = 0;
      this.cursorTicks = 0;
      this.cursorRetries = 0;
      this.noMenu = false;
      this.pageNumber = 0;
      this.sweepTicks = 0;
      this.ownedSync = -1;
      this.noItemsMessage = false;
      this.collectMark = -1L;
      this.collectClicks = 0;
      this.skippedUndelivered = 0;
      this.skippedEmpty = 0;
      this.result = OrderCollector.Result.RUNNING;
      this.transition(OrderCollector.Step.OPEN);
      this.status = "Vào Order lấy " + var1.getName().getString();
   }

   public boolean active() {
      return this.result == OrderCollector.Result.RUNNING;
   }

   public String status() {
      return this.status;
   }

   public OrderCollector.Result result() {
      return this.result;
   }

   public Item item() {
      return this.wanted;
   }

   public void cancel(MinecraftClient var1) {
      if (this.active()) {
         this.closeOwned(var1);
      }

      this.result = OrderCollector.Result.EMPTY;
   }

   public void onChat(String var1) {
      if (this.active() && OrderRules.noItemsMessage(var1)) {
         this.noItemsMessage = true;
      }
   }

   private void transition(OrderCollector.Step var1) {
      this.step = var1;
      this.age = 0;
      this.stable = 0;
      this.lastSignature = "";
      this.clicked = false;
      this.nextClickAt = this.config.orderClickDelayTicks + this.random.nextInt(4);
   }

   private void closeOwned(MinecraftClient var1) {
      if (var1.player != null && var1.currentScreen instanceof HandledScreen && var1.player.currentScreenHandler.syncId == this.ownedSync) {
         var1.player.closeHandledScreen();
      }

      this.ownedSync = -1;
   }

   private void finish(MinecraftClient var1, OrderCollector.Result var2, String var3) {
      this.result = var2;
      this.status = var3;
      this.closeOwned(var1);
   }

   private static int topSize(MinecraftClient var0) {
      return var0.player.currentScreenHandler.slots.size() - 36;
   }

   private static Slot top(MinecraftClient var0, int var1) {
      return var1 >= 0 && var1 < topSize(var0) ? var0.player.currentScreenHandler.getSlot(var1) : null;
   }

   private static String name(Slot var0) {
      return var0 != null && var0.hasStack() ? var0.getStack().getName().getString() : "";
   }

   static List<String> lines(ItemStack var0) {
      ArrayList<String> var1 = new ArrayList<>();
      var1.add(var0.getName().getString());
      LoreComponent var2 = (LoreComponent)var0.get(DataComponentTypes.LORE);
      if (var2 != null) {
         for (Text var4 : (java.util.List<Text>)var2.comp_2400()) {
            var1.add(var4.getString());
         }
      }

      return var1;
   }

   private int button(MinecraftClient var1, String var2) {
      for (int var3 = 0; var3 < topSize(var1); var3++) {
         if (OrderRules.button(name(top(var1, var3)), var2)) {
            return var3;
         }
      }

      return -1;
   }

   private int yourOrdersButton(MinecraftClient var1) {
      int var2 = topSize(var1) - 3;
      if (OrderRules.text(name(top(var1, var2))).contains("your orders")) {
         return var2;
      } else {
         for (int var3 = 0; var3 < topSize(var1); var3++) {
            if (OrderRules.text(name(top(var1, var3))).contains("your orders")) {
               return var3;
            }
         }

         return -1;
      }
   }

   private OrderRules.Page classify(MinecraftClient var1) {
      OrderRules.Page var2 = OrderRules.page(var1.currentScreen.getTitle().getString());
      if (var2 != OrderRules.Page.OTHER) {
         return var2;
      } else {
         for (int var3 = 18; var3 <= 20; var3++) {
            if (OrderRules.maxOrdersMarker(name(top(var1, var3)))) {
               return OrderRules.Page.OWN;
            }
         }

         Slot var4 = top(var1, 10);
         if (var4 == null || !this.accept(var4.getStack()) || this.collectSlot(var1) < 0 && !OrderRules.text(name(top(var1, 13))).contains("cancel")) {
            return this.yourOrdersButton(var1) >= 0 ? OrderRules.Page.MARKET : OrderRules.Page.OTHER;
         } else {
            return OrderRules.Page.EDIT;
         }
      }
   }

   private int collectSlot(MinecraftClient var1) {
      for (int var5 : COLLECT_SLOTS) {
         if (OrderRules.button(name(top(var1, var5)), "collect")) {
            return var5;
         }
      }

      return this.button(var1, "collect");
   }

   private void click(MinecraftClient var1, int var2, SlotActionType var3) {
      if (var2 >= 0 && !this.clicked && top(var1, var2) != null && var1.player.currentScreenHandler.getCursorStack().isEmpty()) {
         var1.interactionManager.clickSlot(var1.player.currentScreenHandler.syncId, var2, 0, var3, var1.player);
         this.clicked = true;
         this.clickAt = this.age;
      }
   }

   private void enterStorage() {
      this.transition(OrderCollector.Step.STORAGE);
      this.scanIndex = 0;
      this.predicted = 0;
      this.emptyStorageTicks = 0;
   }

   private void toSync() {
      this.syncTicks = 0;
      this.transition(OrderCollector.Step.SYNC);
   }

   private void backToOrders(MinecraftClient var1) {
      this.closeOwned(var1);
      this.pageNumber = 0;
      this.transition(OrderCollector.Step.RETURN);
   }

   private int capacity(MinecraftClient var1) {
      int var2 = Math.max(1, this.wanted.getMaxCount());
      return Math.min(this.desiredCount, this.config.materialMaxStacks * var2);
   }

   private boolean enough(MinecraftClient var1) {
      int var2 = this.have(var1);
      return var2 >= this.capacity(var1) || InventoryAccess.freeSlots(var1) <= this.config.reserveFreeSlots && var2 > 0;
   }

   /** server đã trả "You have no items to collect" cho lần bấm Collect gần nhất */
   private boolean noItems() {
      return this.noItemsMessage || this.collectMark >= 0L && ChatWatch.since(this.collectMark, "you have no items to collect");
   }

   private static String orderKey(ItemStack var0, long[] var1) {
      return var0.getItem() + "|" + var0.getName().getString() + "|" + var1[0] + "/" + var1[1];
   }

   private static String amount(long var0) {
      if (var0 < 0L) {
         return "?";
      } else if (var0 >= 1000000L) {
         return String.format("%.1fM", (double)var0 / 1000000.0);
      } else {
         return var0 >= 1000L ? String.format("%.1fK", (double)var0 / 1000.0) : String.valueOf(var0);
      }
   }

   /** order này không có gì để lấy → bỏ qua lần sau (tới khi có người giao thêm) */
   private void markEmpty(long var1) {
      if (!this.openedKey.isEmpty()) {
         EMPTY_UNTIL.put(this.openedKey, System.currentTimeMillis() + var1);
         if (EMPTY_UNTIL.size() > 64) {
            long var3 = System.currentTimeMillis();
            EMPTY_UNTIL.values().removeIf(var2 -> var2 < var3);
         }
      }
   }

   public void tick(MinecraftClient var1) {
      if (this.active() && var1.player != null && var1.interactionManager != null) {
         this.age++;
         this.sweepTicks++;
         int var2 = this.have(var1);
         if (this.sweepTicks > 2400) {
            this.finish(var1, var2 > 0 ? OrderCollector.Result.COLLECTED : OrderCollector.Result.EMPTY, "Đã quét hết Order; chờ hàng mới.");
         } else if (this.step == OrderCollector.Step.SYNC) {
            if (var1.currentScreen instanceof HandledScreen && var1.player.currentScreenHandler.syncId == this.ownedSync) {
               var1.player.closeHandledScreen();
               this.ownedSync = -1;
            }

            if (++this.syncTicks >= 10) {
               if (var2 >= this.capacity(var1)) {
                  this.finish(
                     var1,
                     OrderCollector.Result.COLLECTED,
                     "Đã lấy " + var2 + "/" + this.capacity(var1) + " " + this.wanted.getName().getString() + " cho tầng này"
                  );
               } else if (InventoryAccess.freeSlots(var1) <= this.config.reserveFreeSlots) {
                  this.finish(var1, var2 > 0 ? OrderCollector.Result.COLLECTED : OrderCollector.Result.FULL, "Túi đồ đầy khi đang lấy vật liệu.");
               } else {
                  this.pageNumber = 0;
                  this.transition(OrderCollector.Step.RETURN);
               }
            }
         } else if (this.step == OrderCollector.Step.RETURN) {
            if (var1.currentScreen == null && this.age >= this.config.orderClickDelayTicks + 4) {
               this.transition(OrderCollector.Step.OPEN);
            } else if (this.age > 40 && var1.currentScreen instanceof HandledScreen) {
               var1.player.closeHandledScreen();
            }
         } else if (this.step == OrderCollector.Step.OPEN) {
            if (var1.currentScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen
               && var1.player.currentScreenHandler == var1.player.playerScreenHandler) {
               com.donutautobuild.AutoBuildMod.LOGGER.info("[AutoBuild] Order: túi đồ của người chơi đang mở → đóng lại rồi mở /{}", this.config.orderCommand);
               var1.player.closeHandledScreen();
            } else if (var1.currentScreen == null && var1.player.currentScreenHandler == var1.player.playerScreenHandler) {
               if (InventoryAccess.freeSlots(var1) <= this.config.reserveFreeSlots) {
                  this.finish(
                     var1,
                     var2 > 0 ? OrderCollector.Result.COLLECTED : OrderCollector.Result.FULL,
                     "Túi đồ đầy: cần ít nhất " + (this.config.reserveFreeSlots + 1) + " ô trống để lấy vật liệu."
                  );
               } else if (this.age >= this.nextClickAt && !this.preClosed) {
                  this.preClosed = true;
                  var1.player.closeHandledScreen();
                  this.nextClickAt = this.age + 3;
               } else if (this.age >= this.nextClickAt) {
                  this.preClosed = false;
                  ShopBuyer.markCommand();
                  GuiWatch.sent(this.config.orderCommand);
                  this.openTries++;
                  var1.player.networkHandler.sendChatCommand(this.config.orderCommand);
                  this.status = "Mở /" + this.config.orderCommand + " để lấy " + this.wanted.getName().getString();
                  this.transition(OrderCollector.Step.MARKET);
               }
            } else if (var1.currentScreen instanceof HandledScreen && this.age < 60) {
               // 3.0.91: menu server còn sót từ lần Order/Shop trước (log 17:08:36: "Đóng màn hình đang mở" rồi dừng) → tự đóng rồi mở lại
               if (this.age % 10 == 1) {
                  com.donutautobuild.AutoBuildMod.LOGGER.info("[AutoBuild] Order: menu \"{}\" còn mở từ lần trước → đóng rồi mở /{}", var1.currentScreen.getTitle().getString(), this.config.orderCommand);
                  var1.player.closeHandledScreen();
               }
            } else {
               // 3.0.95: màn hình lạ không tự đóng → đóng rồi thử lại sau, không dừng việc xây
               var1.player.closeHandledScreen();
               this.noMenu = true;
               this.finish(var1, var2 > 0 ? OrderCollector.Result.COLLECTED : OrderCollector.Result.EMPTY, "Có màn hình khác chặn /" + this.config.orderCommand + " → đóng, thử lại sau");
            }
         } else if (this.step == OrderCollector.Step.MARKET && !(var1.currentScreen instanceof HandledScreen) && this.age > GuiWatch.RETRY_TICKS) {
            // 3.0.93: gửi /order 2 giây mà chưa có menu → gửi lại (tối đa 5 lần; 5 lần liên tiếp không có → server lag, thoát ra vào lại)
            if (this.openTries >= GuiWatch.MAX_MISSES || GuiWatch.lagging()) {
               this.noMenu = true;
               this.finish(var1, var2 > 0 ? OrderCollector.Result.COLLECTED : OrderCollector.Result.EMPTY, "Gửi /" + this.config.orderCommand + " " + this.openTries + " lần không có menu (server lag?)");
            } else {
               this.transition(OrderCollector.Step.OPEN);
               this.nextClickAt = 2;
            }
         } else if (this.age > this.config.orderTimeoutTicks) {
            if (this.step == OrderCollector.Step.EDIT) {
               this.backToOrders(var1);
            } else if (this.step == OrderCollector.Step.STORAGE) {
               this.toSync();
            } else {
               this.finish(var1, var2 > 0 ? OrderCollector.Result.COLLECTED : OrderCollector.Result.EMPTY, "Order không phản hồi hoặc chưa có hàng được giao.");
            }
         } else if (var1.currentScreen instanceof HandledScreen) {
            if (!var1.player.currentScreenHandler.getCursorStack().isEmpty()) {
               // 3.0.95: server lag → món vừa bấm dính trên con trỏ (người chơi không làm gì). Bản cũ dừng hẳn việc xây.
               // Giờ: đóng menu (server trả món về chỗ cũ) rồi mở lại /order làm tiếp; 3 lần vẫn dính thì bỏ, xây phần khác, lát thử lại.
               if (++this.cursorTicks > 40) {
                  this.cursorTicks = 0;
                  com.donutautobuild.AutoBuildMod.LOGGER
                     .info(
                        "[AutoBuild] Order: món {} dính trên con trỏ 2 giây (server lag) → đóng menu rồi mở lại /{} (lần {})",
                        new Object[]{var1.player.currentScreenHandler.getCursorStack().getName().getString(), this.config.orderCommand, this.cursorRetries + 1}
                     );
                  var1.player.closeHandledScreen();
                  this.ownedSync = -1;
                  if (++this.cursorRetries > 3) {
                     this.finish(var1, var2 > 0 ? OrderCollector.Result.COLLECTED : OrderCollector.Result.EMPTY, "Menu Order bị lag (món dính trên con trỏ) → thử lại sau");
                     this.noMenu = true;
                  } else {
                     this.pageNumber = 0;
                     this.transition(OrderCollector.Step.RETURN);
                  }
               }
            } else {
               this.cursorTicks = 0;
               OrderRules.Page var3 = this.classify(var1);
               if (var3 == OrderRules.Page.OTHER && var1.player.currentScreenHandler == var1.player.playerScreenHandler) {
                  com.donutautobuild.AutoBuildMod.LOGGER.info("[AutoBuild] Order: túi đồ của người chơi bật lên giữa chừng → đóng lại");
                  var1.player.closeHandledScreen();
               } else if (var3 == OrderRules.Page.OTHER) {
                  if (this.age > 30) {
                     String var23 = var1.currentScreen.getTitle().getString();
                     var1.player.closeHandledScreen();
                     this.noMenu = true;
                     this.finish(var1, var2 > 0 ? OrderCollector.Result.COLLECTED : OrderCollector.Result.EMPTY, "Màn hình lạ, không phải Order: " + var23 + " → đóng, thử lại sau");
                  }
               } else {
                  this.ownedSync = var1.player.currentScreenHandler.syncId;
                  StringBuilder var4 = new StringBuilder(var3.name()).append(this.ownedSync);

                  for (int var5 = 0; var5 < topSize(var1); var5++) {
                     ItemStack var6 = top(var1, var5).getStack();
                     var4.append('|').append(var6.getItem()).append(':').append(var6.getCount()).append(':').append(var6.getName().getString());
                  }

                  String var14 = var4.toString();
                  if (var14.equals(this.lastSignature)) {
                     this.stable++;
                  } else {
                     this.stable = 0;
                     this.lastSignature = var14;
                  }

                  if (this.step == OrderCollector.Step.EDIT && var3 == OrderRules.Page.STORAGE) {
                     this.enterStorage();
                  } else if ((this.step == OrderCollector.Step.STORAGE || this.stable >= 2) && this.age >= this.nextClickAt) {
                     if (this.enough(var1)) {
                        this.finish(var1, OrderCollector.Result.COLLECTED, "Đã lấy " + var2 + " " + this.wanted.getName().getString());
                     } else {
                        switch (this.step) {
                           case MARKET:
                              if (var3 == OrderRules.Page.OWN) {
                                 this.transition(OrderCollector.Step.OWN);
                              } else if (var3 == OrderRules.Page.MARKET && !this.clicked) {
                                 this.click(var1, this.yourOrdersButton(var1), SlotActionType.PICKUP);
                                 this.status = "Order → Your Orders";
                              }
                              break;
                           case OWN:
                              if (var3 == OrderRules.Page.EDIT && this.clicked) {
                                 this.noItemsMessage = false;
                                 this.collectMark = -1L;
                                 this.collectClicks = 0;
                                 this.transition(OrderCollector.Step.EDIT);
                              } else if (var3 == OrderRules.Page.STORAGE && this.clicked) {
                                 this.enterStorage();
                              } else if (var3 == OrderRules.Page.OWN && !this.clicked) {
                                 int var17 = topSize(var1);
                                 int var19 = -1;
                                 long var20 = -10L;

                                 String var26 = "";
                                 String var27 = "";
                                 long var28 = System.currentTimeMillis();

                                 for (int var21 = 0; var21 < Math.max(0, var17 - 9); var21++) {
                                    Slot var11 = top(var1, var21);
                                    if (this.accept(var11.getStack()) && !this.visitedOrders.contains(this.pageNumber + ":" + var21)) {
                                       long[] var24 = OrderRules.delivered(lines(var11.getStack()));
                                       String var25 = orderKey(var11.getStack(), var24);
                                       if (var24[0] == 0L) {
                                          // 3.0.109: chưa ai giao món nào → không mở, không bấm Collect
                                          this.visitedOrders.add(this.pageNumber + ":" + var21);
                                          this.skippedUndelivered++;
                                          continue;
                                       }

                                       Long var29 = EMPTY_UNTIL.get(var25);
                                       if (var29 != null && var29 > var28) {
                                          // vừa bấm Collect mà server báo không có gì, số đã giao chưa đổi → bỏ qua
                                          this.visitedOrders.add(this.pageNumber + ":" + var21);
                                          this.skippedEmpty++;
                                          continue;
                                       }

                                       // ưu tiên order có nhiều hàng đã giao; bằng nhau thì ưu tiên đúng vật phẩm
                                       long var12 = var24[0] * 2L + (var11.getStack().isOf(this.wanted) ? 1L : 0L);
                                       if (var12 > var20) {
                                          var19 = var21;
                                          var20 = var12;
                                          var26 = var25;
                                          var27 = var11.getStack().getName().getString() + " (đã giao " + amount(var24[0]) + "/" + amount(var24[1]) + ")";
                                       }
                                    }
                                 }

                                 if (var19 >= 0) {
                                    this.visitedOrders.add(this.pageNumber + ":" + var19);
                                    this.openedKey = var26;
                                    this.openedLabel = var27;
                                    com.donutautobuild.AutoBuildMod.LOGGER.info("[AutoBuild] Order: mở order {} để Collect", var27);
                                    this.click(var1, var19, SlotActionType.PICKUP);
                                    this.status = "Mở order " + var27;
                                    return;
                                 }

                                 int var22 = this.button(var1, "next page");
                                 if (var22 < 0) {
                                    var22 = this.button(var1, "next");
                                 }

                                 if (var22 >= 0 && this.pageNumber < 20) {
                                    this.pageHash = var14.hashCode();
                                    this.click(var1, var22, SlotActionType.PICKUP);
                                    this.transition(OrderCollector.Step.PAGE_CHANGE);
                                 } else {
                                    String var30 = "Không có order "
                                       + this.wanted.getName().getString()
                                       + " nào có hàng để Collect"
                                       + (this.skippedUndelivered > 0 ? " (" + this.skippedUndelivered + " order chưa ai giao)" : "")
                                       + (this.skippedEmpty > 0 ? " (" + this.skippedEmpty + " order vừa báo hết hàng)" : "")
                                       + ".";
                                    com.donutautobuild.AutoBuildMod.LOGGER.info("[AutoBuild] Order: {}", var30);
                                    this.finish(var1, var2 > 0 ? OrderCollector.Result.COLLECTED : OrderCollector.Result.EMPTY, var30);
                                 }
                              }
                              break;
                           case PAGE_CHANGE:
                              if (var3 == OrderRules.Page.OWN && var14.hashCode() != this.pageHash) {
                                 this.pageNumber++;
                                 this.transition(OrderCollector.Step.OWN);
                              }
                              break;
                           case EDIT:
                              if (this.noItems()) {
                                 // 3.0.109: server báo không có gì để lấy → bỏ order này (10 phút, hoặc tới khi số đã giao đổi), không bấm tiếp
                                 com.donutautobuild.AutoBuildMod.LOGGER
                                    .info("[AutoBuild] Order: {} chưa có hàng để lấy (server: \"You have no items to collect\") → bỏ qua order này", this.openedLabel);
                                 this.noItemsMessage = false;
                                 this.collectMark = -1L;
                                 this.markEmpty(600000L);
                                 this.backToOrders(var1);
                              } else if (var3 == OrderRules.Page.EDIT) {
                                 Slot var16 = top(var1, 10);
                                 if (var16 != null && this.accept(var16.getStack())) {
                                    int var18 = this.collectSlot(var1);
                                    // 3.0.109: bấm Collect xong phải chờ server trả lời (1 lượt ping + 1,5 giây) rồi mới bấm lại; tối đa 3 lần
                                    int var31 = (int)Math.min(80L, InventoryAccess.rttMs / 50L + 30L);
                                    if (!this.clicked || this.age - this.clickAt >= var31) {
                                       this.clicked = false;
                                       if (this.collectClicks >= 3) {
                                          com.donutautobuild.AutoBuildMod.LOGGER
                                             .info("[AutoBuild] Order: bấm Collect {} lần ở {} mà không nhận được gì → bỏ qua order này", this.collectClicks, this.openedLabel);
                                          this.markEmpty(180000L);
                                          this.backToOrders(var1);
                                       } else if (var18 >= 0) {
                                          this.collectMark = ChatWatch.mark();
                                          this.collectClicks++;
                                          this.click(var1, var18, SlotActionType.PICKUP);
                                          this.status = "Bấm Collect (ô " + var18 + ", lần " + this.collectClicks + ")";
                                       } else if (this.age > 30) {
                                          this.backToOrders(var1);
                                       }
                                    }
                                 } else if (this.age > 40) {
                                    this.backToOrders(var1);
                                 }
                              }
                              break;
                           case STORAGE:
                              if (this.noItems()) {
                                 this.noItemsMessage = false;
                                 this.collectMark = -1L;
                                 this.toSync();
                              } else if (var3 == OrderRules.Page.STORAGE) {
                                 int var15 = this.capacity(var1);
                                 int var7 = InventoryAccess.freeSlots(var1);
                                 int var8 = 0;
                                 this.predicted = Math.max(this.predicted, var2);

                                 for (int var9 = this.scanIndex;
                                    var9 < Math.max(0, topSize(var1) - 9) && var8 < 3 && this.predicted < var15 && var7 > this.config.reserveFreeSlots;
                                    var9++
                                 ) {
                                    Slot var10 = top(var1, var9);
                                    this.scanIndex = var9 + 1;
                                    if (this.accept(var10.getStack())) {
                                       this.predicted = this.predicted + var10.getStack().getCount();
                                       var1.interactionManager
                                          .clickSlot(var1.player.currentScreenHandler.syncId, var9, 0, SlotActionType.QUICK_MOVE, var1.player);
                                       var8++;
                                       var7 = InventoryAccess.freeSlots(var1);
                                    }
                                 }

                                 if (var8 > 0) {
                                    this.emptyStorageTicks = 0;
                                    this.status = "Lấy " + this.wanted.getName().getString() + " (" + Math.min(this.predicted, var15) + "/" + var15 + ")";
                                 } else if (this.predicted < var15 && var7 > this.config.reserveFreeSlots) {
                                    if (++this.emptyStorageTicks < 8) {
                                       this.scanIndex = 0;
                                    } else {
                                       this.toSync();
                                    }
                                 } else {
                                    this.toSync();
                                 }
                              }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static enum Result {
      RUNNING,
      COLLECTED,
      EMPTY,
      BLOCKED,
      FULL;

      private Result() {
      }
   }

   private static enum Step {
      OPEN,
      MARKET,
      OWN,
      PAGE_CHANGE,
      EDIT,
      STORAGE,
      SYNC,
      RETURN;

      private Step() {
      }
   }
}
