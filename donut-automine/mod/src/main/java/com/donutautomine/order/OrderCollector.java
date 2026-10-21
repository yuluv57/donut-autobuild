package com.donutautomine.order;

import com.donutautomine.AutoMineMod;
import com.donutautomine.MineConfig;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

public final class OrderCollector {
   private final MineConfig config;
   private Item wanted;
   private OrderCollector.Step step;
   private int age;
   private int stable;
   private int beforeTransfer;
   private int desiredCount;
   private int pageNumber;
   private int pageHash;
   private int sweepTicks;
   private String lastSignature = "";
   private String status = "";
   private final Set<String> visitedOrders = new HashSet<>();
   private boolean clicked;
   private OrderCollector.Result result = OrderCollector.Result.EMPTY;
   private int ownedSync = -1;

   public OrderCollector(MineConfig var1) {
      this.config = var1;
   }

   public void start(Item var1, int var2) {
      this.wanted = var1;
      this.desiredCount = Math.min(64, Math.max(1, var2));
      this.visitedOrders.clear();
      this.pageNumber = 0;
      this.sweepTicks = 0;
      this.ownedSync = -1;
      this.result = OrderCollector.Result.RUNNING;
      this.transition(OrderCollector.Step.OPEN);
      this.status = "Order: " + var1.getName().getString();
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

   public void cancel(MinecraftClient var1) {
      this.closeOwned(var1);
      this.result = OrderCollector.Result.EMPTY;
   }

   private void transition(OrderCollector.Step var1) {
      this.step = var1;
      this.age = 0;
      this.stable = 0;
      this.lastSignature = "";
      this.clicked = false;
   }

   private void closeOwned(MinecraftClient var1) {
      if (var1.player != null
         && var1.currentScreen instanceof HandledScreen
         && var1.player.currentScreenHandler.syncId == this.ownedSync
         && OrderRules.page(var1.currentScreen.getTitle().getString()) != OrderRules.Page.OTHER) {
         var1.player.closeHandledScreen();
      }

      this.ownedSync = -1;
   }

   private void finish(MinecraftClient var1, OrderCollector.Result var2, String var3) {
      this.result = var2;
      this.status = var3;
      this.closeOwned(var1);
      AutoMineMod.LOGGER.info("[AutoMine] Khối kê từ Order: {}", var3);
   }

   private List<Slot> top(MinecraftClient var1) {
      return var1.player.currentScreenHandler.slots.stream().filter(var1x -> var1x.inventory != var1.player.getInventory()).toList();
   }

   private int button(MinecraftClient var1, String var2) {
      for (Slot var4 : this.top(var1)) {
         if (var4.hasStack() && OrderRules.button(var4.getStack().getName().getString(), var2)) {
            return var4.id;
         }
      }

      return -1;
   }

   private void click(MinecraftClient var1, int var2, SlotActionType var3) {
      if (var2 >= 0 && !this.clicked && var1.player.currentScreenHandler.getCursorStack().isEmpty()) {
         if (!this.top(var1).stream().noneMatch(var1x -> var1x.id == var2)) {
            var1.interactionManager.clickSlot(var1.player.currentScreenHandler.syncId, var2, 0, var3, var1.player);
            this.clicked = true;
         }
      }
   }

   private void returnForAnother(MinecraftClient var1) {
      this.closeOwned(var1);
      this.pageNumber = 0;
      this.transition(OrderCollector.Step.RETURN);
   }

   private int count(MinecraftClient var1) {
      return SellJob.count(var1, var1x -> var1x.isOf(this.wanted));
   }

   public void tick(MinecraftClient var1) {
      if (this.active() && var1.player != null && var1.interactionManager != null) {
         this.age++;
         this.sweepTicks++;
         if (this.sweepTicks > 2400) {
            this.finish(var1, this.count(var1) > 0 ? OrderCollector.Result.COLLECTED : OrderCollector.Result.EMPTY, "Đã quét Order; chờ hàng mới.");
         } else if (this.step == OrderCollector.Step.RETURN) {
            if (this.age >= this.config.orderClickDelayTicks) {
               this.transition(OrderCollector.Step.OPEN);
            }
         } else if (this.step == OrderCollector.Step.OPEN) {
            if (var1.currentScreen == null && var1.player.currentScreenHandler == var1.player.playerScreenHandler) {
               if (Inv.freeSlots(var1) < 1) {
                  this.finish(var1, OrderCollector.Result.BLOCKED, "Cần ít nhất 1 ô trống để lấy vật liệu.");
               } else {
                  if (this.age >= this.config.orderClickDelayTicks) {
                     if (System.currentTimeMillis() - GuiWatch.lastCommandAt() < GuiWatch.gap(3500L)) {
                        return;
                     }

                     GuiWatch.markCommand();
                     GuiWatch.sent(this.config.orderCommand);
                     var1.player.networkHandler.sendChatCommand(this.config.orderCommand);
                     AutoMineMod.LOGGER.info("[AutoMine] Khối kê: /{} → Your Orders → Cobblestone → Collect (tối đa 64)", this.config.orderCommand);
                     this.transition(OrderCollector.Step.MARKET);
                  }
               }
            } else {
               this.finish(var1, OrderCollector.Result.BLOCKED, "Đóng màn hình đang mở rồi tiếp tục Auto Mine.");
            }
         } else if (this.age > this.config.orderTimeoutTicks) {
            if (this.step != OrderCollector.Step.EDIT && this.step != OrderCollector.Step.STORAGE) {
               this.finish(
                  var1,
                  this.count(var1) > 0 ? OrderCollector.Result.COLLECTED : OrderCollector.Result.EMPTY,
                  "Order chưa phản hồi hoặc chưa có vật liệu được giao."
               );
            } else {
               this.returnForAnother(var1);
            }
         } else if (var1.currentScreen instanceof HandledScreen) {
            if (!var1.player.currentScreenHandler.getCursorStack().isEmpty()) {
               this.finish(var1, OrderCollector.Result.BLOCKED, "Con trỏ đang giữ đồ; cất đồ rồi tiếp tục.");
            } else {
               OrderRules.Page var2 = OrderRules.page(var1.currentScreen.getTitle().getString());
               if (var2 == OrderRules.Page.OTHER) {
                  this.finish(var1, OrderCollector.Result.BLOCKED, "Màn hình không phải kho Order: " + var1.currentScreen.getTitle().getString());
               } else {
                  this.ownedSync = var1.player.currentScreenHandler.syncId;
                  StringBuilder var3 = new StringBuilder(var2.name()).append(this.ownedSync);

                  for (Slot var5 : this.top(var1)) {
                     var3.append('|')
                        .append(var5.id)
                        .append(':')
                        .append(var5.getStack().getItem())
                        .append(':')
                        .append(var5.getStack().getCount())
                        .append(':')
                        .append(var5.getStack().getName().getString());
                  }

                  String var9 = var3.toString();
                  if (var9.equals(this.lastSignature)) {
                     this.stable++;
                  } else {
                     this.stable = 0;
                     this.lastSignature = var9;
                  }

                  if (this.stable >= this.config.orderClickDelayTicks) {
                     if (this.count(var1) < this.desiredCount && (Inv.freeSlots(var1) >= 1 || this.count(var1) <= 0)) {
                        switch (this.step) {
                           case MARKET:
                              if (var2 == OrderRules.Page.OWN) {
                                 this.transition(OrderCollector.Step.OWN);
                              } else if (var2 == OrderRules.Page.MARKET) {
                                 this.click(var1, this.button(var1, "your orders"), SlotActionType.PICKUP);
                              }
                              break;
                           case OWN:
                              if (var2 == OrderRules.Page.EDIT && this.clicked) {
                                 this.transition(OrderCollector.Step.EDIT);
                              } else if (var2 == OrderRules.Page.STORAGE && this.clicked) {
                                 this.transition(OrderCollector.Step.STORAGE);
                              } else if (var2 == OrderRules.Page.OWN && !this.clicked) {
                                 List var12 = this.top(var1);

                                 for (int var14 = 0; var14 < Math.max(0, var12.size() - 9); var14++) {
                                    Slot var16 = (Slot)var12.get(var14);
                                    String var17 = this.pageNumber + ":" + var16.id;
                                    if (var16.getStack().isOf(this.wanted) && !this.visitedOrders.contains(var17)) {
                                       this.visitedOrders.add(var17);
                                       this.click(var1, var16.id, SlotActionType.PICKUP);
                                       return;
                                    }
                                 }

                                 int var15 = this.button(var1, "next page");
                                 if (var15 < 0) {
                                    var15 = this.button(var1, "next");
                                 }

                                 if (var15 >= 0 && this.pageNumber < 30) {
                                    this.pageHash = var9.hashCode();
                                    this.click(var1, var15, SlotActionType.PICKUP);
                                    this.transition(OrderCollector.Step.PAGE_CHANGE);
                                 } else {
                                    this.finish(
                                       var1,
                                       this.count(var1) > 0 ? OrderCollector.Result.COLLECTED : OrderCollector.Result.EMPTY,
                                       "Chưa có " + this.wanted.getName().getString() + " để Collect."
                                    );
                                 }
                              }
                              break;
                           case EDIT:
                              if (var2 == OrderRules.Page.STORAGE && this.clicked) {
                                 this.transition(OrderCollector.Step.STORAGE);
                              } else if (var2 == OrderRules.Page.EDIT && !this.clicked) {
                                 List<Slot> var11 = this.top(var1);
                                 if (!var11.stream().noneMatch(var1x -> var1x.id == 10 && var1x.getStack().isOf(this.wanted))) {
                                    int var13 = this.button(var1, "collect");
                                    if (var13 >= 0) {
                                       this.click(var1, var13, SlotActionType.PICKUP);
                                    }
                                 }
                              }
                              break;
                           case STORAGE:
                              if (var2 == OrderRules.Page.STORAGE) {
                                 int var10 = this.desiredCount - this.count(var1);
                                 List var6 = this.top(var1)
                                    .stream()
                                    .filter(var2x -> var2x.getStack().isOf(this.wanted) && var2x.getStack().getCount() <= var10)
                                    .sorted(Comparator.<Slot>comparingInt(var0 -> var0.getStack().getCount()).reversed())
                                    .toList();
                                 Iterator var7 = var6.iterator();
                                 if (var7.hasNext()) {
                                    Slot var8 = (Slot)var7.next();
                                    this.beforeTransfer = this.count(var1);
                                    this.click(var1, var8.id, SlotActionType.QUICK_MOVE);
                                    this.transition(OrderCollector.Step.WAIT_TRANSFER);
                                    return;
                                 }

                                 if (this.count(var1) > 0 && this.top(var1).stream().anyMatch(var1x -> var1x.getStack().isOf(this.wanted))) {
                                    this.finish(var1, OrderCollector.Result.COLLECTED, "Đã nhận " + this.count(var1) + " Cobblestone; giữ giới hạn một stack.");
                                 } else {
                                    this.returnForAnother(var1);
                                 }
                              }
                              break;
                           case WAIT_TRANSFER:
                              if (this.count(var1) > this.beforeTransfer) {
                                 this.transition(OrderCollector.Step.STORAGE);
                              } else if (this.age > 60) {
                                 this.finish(var1, OrderCollector.Result.BLOCKED, "Collect chưa vào túi đồ; kiểm tra kho Order và ô trống.");
                              }
                              break;
                           case PAGE_CHANGE:
                              if (var2 == OrderRules.Page.OWN && var9.hashCode() != this.pageHash) {
                                 this.pageNumber++;
                                 this.transition(OrderCollector.Step.OWN);
                              }
                        }
                     } else {
                        this.finish(var1, OrderCollector.Result.COLLECTED, "Đã nhận " + this.count(var1) + " " + this.wanted.getName().getString());
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
      BLOCKED;
   }

   private static enum Step {
      OPEN,
      MARKET,
      OWN,
      EDIT,
      STORAGE,
      WAIT_TRANSFER,
      PAGE_CHANGE,
      RETURN;
   }
}
