package com.donutautobuild.order;

import com.donutautobuild.AutoBuildMod;
import com.donutautobuild.BuildConfig;
import com.donutautobuild.logic.OrderRules;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * 3.0.88: dùng /sethome + /home N của DonutSMP để lên lại tầng cao nhất ngay lập tức khi lỡ ngã (không phải bắc cầu / kê cột).
 *
 * <p>GUI của server (theo hướng dẫn của người dùng):
 * <ul>
 *   <li>/home → menu "Homes": giường = home đã có (ô i = home i+1), kính đỏ = home bị khóa, ô khác = home trống.</li>
 *   <li>Bấm giường → menu "Home N": xô lava = xóa.</li>
 *   <li>Bấm xô lava → menu "Confirm Delete": kính xanh = xác nhận. Bấm lần 1 → chat "Click again to confirm…", bấm lần 2 → "Home deleted".
 *       3.0.94: giữ menu mở giữa 2 lần bấm và chờ tới khi chat báo "Home deleted" (bản cũ đóng menu ngay sau lần 1).</li>
 *   <li>/sethome → tạo home ở ô trống đầu tiên. /home N → dịch chuyển tới home N.</li>
 * </ul>
 * Mod chỉ xóa đúng home do chính mod tạo (số lưu trong cấu hình), không đụng home khác của người chơi.
 */
public final class HomeManager {
   private static final Pattern NUM = Pattern.compile("(\\d+)");
   private static long lastCommandAt;
   private final BuildConfig config;
   private HomeManager.Job job = HomeManager.Job.NONE;
   private HomeManager.Step step = HomeManager.Step.DONE;
   private HomeManager.Result result = HomeManager.Result.DONE;
   private String status = "";
   private int age;
   private int stable;
   private String lastSig = "";
   private int openTries;
   private int ownedSync = -1;
   private int clickedSync = -1;
   private int confirmClicks;
   private int deleteTries;
   // 3.0.94: xóa home — giữ menu xác nhận mở, bấm lần 2 sau khi server báo "Click again…", chờ tới khi báo "Home deleted"
   private int confirmSlot = -1;
   private int confirmAt;
   private long deleteMark;
   private long clickMark;
   private int ownSlot = -1;
   private int freeBefore = -1;
   private String editSig = "";
   // SET
   private int ownNumber;
   private int expectNumber;
   private Map<Integer, Integer> homesBefore = new HashMap<>();
   private int newNumber;
   private Vec3d setPos;
   private boolean deleted;
   private boolean noFree;
   private boolean menuFail;
   // TELEPORT
   private int tpNumber;
   private Vec3d tpTarget;
   private int tpTries;
   private long tpMark;
   private boolean tpLag;

   public HomeManager(BuildConfig var1) {
      this.config = var1;
   }

   public boolean active() {
      return this.result == HomeManager.Result.RUNNING;
   }

   public HomeManager.Result result() {
      return this.result;
   }

   /** 3.0.95: /home N gửi 2 lần mà không dịch chuyển, server không báo lý do → nên thoát ra vào lại */
   public boolean teleportLag() {
      return this.tpLag;
   }

   public boolean teleporting() {
      return this.job == HomeManager.Job.TELEPORT;
   }

   public String status() {
      return this.status;
   }

   /** số home mới đặt (SET thành công) */
   public int newNumber() {
      return this.newNumber;
   }

   public Vec3d setPos() {
      return this.setPos;
   }

   /** không mở được menu /home (thử lại sớm) */
   public boolean menuFailed() {
      return this.menuFail;
   }

   /** hết home trống (người chơi đã dùng hết) */
   public boolean noFreeSlot() {
      return this.noFree;
   }

   /** đã xóa home cũ của mod trong lần SET này (kể cả khi đặt lại thất bại) */
   public boolean deletedOld() {
      return this.deleted;
   }

   public void startSet(MinecraftClient var1, int var2) {
      this.reset();
      this.job = HomeManager.Job.SET;
      this.ownNumber = Math.max(0, var2);
      this.step = HomeManager.Step.SEND_LIST;
      this.status = "Đặt home ở tầng này" + (this.ownNumber > 0 ? " (xóa home " + this.ownNumber + " cũ của mod trước)" : "");
   }

   public void startTeleport(MinecraftClient var1, int var2, Vec3d var3) {
      this.reset();
      this.job = HomeManager.Job.TELEPORT;
      this.tpNumber = var2;
      this.tpTarget = var3;
      this.step = HomeManager.Step.SEND_TP;
      this.status = "Về home " + var2 + " (tầng đang xây)";
   }

   private void reset() {
      this.result = HomeManager.Result.RUNNING;
      this.age = 0;
      this.stable = 0;
      this.lastSig = "";
      this.openTries = 0;
      this.ownedSync = -1;
      this.clickedSync = -1;
      this.confirmClicks = 0;
      this.deleteTries = 0;
      this.confirmSlot = -1;
      this.confirmAt = 0;
      this.ownSlot = -1;
      this.freeBefore = -1;
      this.expectNumber = 0;
      this.homesBefore = new HashMap<>();
      this.newNumber = 0;
      this.setPos = null;
      this.deleted = false;
      this.noFree = false;
      this.menuFail = false;
      this.tpTries = 0;
      this.tpLag = false;
   }

   public void cancel(MinecraftClient var1) {
      if (this.active()) {
         this.closeOwned(var1);
      }

      this.result = HomeManager.Result.FAILED;
      this.job = HomeManager.Job.NONE;
   }

   private void finish(MinecraftClient var1, HomeManager.Result var2, String var3) {
      this.closeOwned(var1);
      this.result = var2;
      this.status = var3;
      AutoBuildMod.LOGGER.info("[AutoBuild] Home: {}", var3);
   }

   private void closeOwned(MinecraftClient var1) {
      if (var1.player != null && var1.currentScreen instanceof HandledScreen && this.homeScreen(var1)) {
         var1.player.closeHandledScreen();
      }

      this.ownedSync = -1;
   }

   private boolean homeScreen(MinecraftClient var1) {
      if (var1.currentScreen == null) {
         return false;
      } else {
         String var2 = OrderRules.text(var1.currentScreen.getTitle().getString());
         return var2.contains("home") || var2.contains("confirm") || var2.contains("delete");
      }
   }

   private static int topSize(MinecraftClient var0) {
      return var0.player.currentScreenHandler.slots.size() - 36;
   }

   private static String id(ItemStack var0) {
      if (var0.isEmpty()) {
         return "";
      } else {
         Identifier var1 = Registries.ITEM.getId(var0.getItem());
         return var1 == null ? "" : var1.getPath();
      }
   }

   private static boolean bed(ItemStack var0) {
      return id(var0).endsWith("_bed");
   }

   private static int nameNumber(ItemStack var0, int var1) {
      if (!var0.isEmpty()) {
         Matcher var2 = NUM.matcher(OrderRules.text(var0.getName().getString()));
         if (var2.find()) {
            try {
               int var3 = Integer.parseInt(var2.group(1));
               if (var3 > 0 && var3 <= 54) {
                  return var3;
               }
            } catch (NumberFormatException var4) {
            }
         }
      }

      return var1;
   }

   private boolean cooled() {
      long var1 = System.currentTimeMillis();
      long var3 = Math.max(lastCommandAt, ShopBuyer.lastCommandAt());
      long var5 = GuiWatch.gap(2000L);
      if (var1 - var3 < var5) {
         this.status = "Chờ hết nguội lệnh (" + (var5 - (var1 - var3)) / 1000L + "s)";
         return false;
      } else {
         return true;
      }
   }

   private void send(MinecraftClient var1, String var2) {
      // 3.0.92: dừng mọi thao tác tay (đang đập/đặt) trước khi gửi lệnh mở menu
      if (var1.options != null) {
         var1.options.attackKey.setPressed(false);
         var1.options.useKey.setPressed(false);
      }

      if (var1.interactionManager.isBreakingBlock()) {
         var1.interactionManager.cancelBlockBreaking();
      }

      lastCommandAt = System.currentTimeMillis();
      ShopBuyer.markCommand();
      var1.player.networkHandler.sendChatCommand(var2);
      AutoBuildMod.LOGGER.info("[AutoBuild] Home: gửi /{}", var2);
   }

   private static String cmd(String var0, String var1) {
      String var2 = var0 == null ? "" : var0.trim();

      while (var2.startsWith("/")) {
         var2 = var2.substring(1).trim();
      }

      return var2.isEmpty() ? var1 : var2;
   }

   /** menu đã hiện và 2 tick liên tiếp không đổi (server gửi xong vật phẩm) */
   private boolean settled(MinecraftClient var1) {
      StringBuilder var2 = new StringBuilder().append(var1.player.currentScreenHandler.syncId).append(var1.currentScreen.getTitle().getString());

      for (int var3 = 0; var3 < topSize(var1); var3++) {
         var2.append('|').append(id(var1.player.currentScreenHandler.getSlot(var3).getStack()));
      }

      String var4 = var2.toString();
      if (var4.equals(this.lastSig)) {
         this.stable++;
      } else {
         this.stable = 0;
         this.lastSig = var4;
      }

      return this.stable >= 2 && var1.player.currentScreenHandler.getCursorStack().isEmpty();
   }

   /** đọc menu Homes: số home → ô; trả về -1 nếu không phải menu Homes */
   private Map<Integer, Integer> readHomes(MinecraftClient var1, int[] var2) {
      HashMap<Integer, Integer> var3 = new HashMap<>();
      var2[0] = -1;
      int var4 = Math.min(45, topSize(var1));
      boolean var5 = false;

      for (int var6 = 0; var6 < var4; var6++) {
         ItemStack var7 = var1.player.currentScreenHandler.getSlot(var6).getStack();
         String var8 = id(var7);
         if (bed(var7)) {
            var5 = true;
            var3.put(nameNumber(var7, var6 + 1), var6);
         } else if (!var8.equals("red_stained_glass_pane") && var2[0] < 0) {
            var2[0] = var6;
         }
      }

      if (!var5 && var2[0] < 0) {
         var2[0] = -2;
      }

      return var3;
   }

   public void tick(MinecraftClient var1) {
      if (this.active() && var1.player != null && var1.interactionManager != null && var1.player.networkHandler != null) {
         this.age++;
         switch (this.step) {
            case SEND_LIST:
               if (var1.currentScreen != null) {
                  if (this.age > 3) {
                     var1.player.closeHandledScreen();
                     this.age = 0;
                  }

                  return;
               }

               if (!this.cooled()) {
                  return;
               }

               if (++this.openTries > GuiWatch.MAX_MISSES || GuiWatch.lagging()) {
                  this.menuFail = true;
                  this.finish(var1, HomeManager.Result.FAILED, "Không mở được menu /" + cmd(this.config.homeCommand, "home") + " sau 5 lần");
                  return;
               }

               GuiWatch.sent(cmd(this.config.homeCommand, "home"), true);
               this.send(var1, cmd(this.config.homeCommand, "home"));
               this.step = HomeManager.Step.WAIT_LIST;
               this.age = 0;
               this.stable = 0;
               this.lastSig = "";
               this.status = "Mở danh sách home";
               break;
            case WAIT_LIST:
               if (!(var1.currentScreen instanceof HandledScreen)) {
                  // 3.0.92: server đôi khi không mở menu ngay (log 17:12:23, 17:19:01: 4 lần/20 giây không có menu) → chờ lâu dần 5/8/11/14 giây
                  if (this.age > GuiWatch.RETRY_TICKS) {
                     AutoBuildMod.LOGGER
                        .info(
                           "[AutoBuild] Home: /{} chưa mở menu sau {} giây (lần {}; đứng đất={}, ngồi={}, màn hình={}) → gửi lại",
                           new Object[]{
                              cmd(this.config.homeCommand, "home"),
                              this.age / 20,
                              this.openTries,
                              var1.player.isOnGround(),
                              var1.player.isSneaking(),
                              var1.currentScreen == null ? "-" : var1.currentScreen.getClass().getSimpleName()
                           }
                        );
                     this.step = HomeManager.Step.SEND_LIST;
                     this.age = 0;
                  }

                  return;
               }

               if (!this.settled(var1)) {
                  if (this.age > 160) {
                     this.closeOwned(var1);
                     this.step = HomeManager.Step.SEND_LIST;
                     this.age = 0;
                  }

                  return;
               }

               this.ownedSync = var1.player.currentScreenHandler.syncId;
               int[] var2 = new int[1];
               Map<Integer, Integer> var3 = this.readHomes(var1, var2);
               if (var2[0] == -2 || !this.homeScreen(var1)) {
                  this.finish(var1, HomeManager.Result.FAILED, "Menu mở ra không phải danh sách home: " + var1.currentScreen.getTitle().getString());
                  return;
               }

               AutoBuildMod.LOGGER
                  .info("[AutoBuild] Home: danh sách home đang có {}, ô trống đầu tiên {}", var3.keySet(), var2[0] < 0 ? "-" : "home " + (var2[0] + 1));
               if (this.newNumber < 0) {
                  // đang kiểm tra sau /sethome
                  this.verifySet(var1, var3);
                  return;
               }

               if (this.ownNumber > 0 && var3.containsKey(this.ownNumber)) {
                  if (++this.deleteTries > 3) {
                     this.finish(var1, HomeManager.Result.FAILED, "Xóa home " + this.ownNumber + " cũ 3 lần chưa được");
                     return;
                  }

                  int var4 = var3.get(this.ownNumber);
                  this.ownSlot = var4;
                  this.freeBefore = var2[0];
                  this.clickedSync = this.ownedSync;
                  var1.interactionManager.clickSlot(this.ownedSync, var4, 0, SlotActionType.PICKUP, var1.player);
                  AutoBuildMod.LOGGER.info("[AutoBuild] Home: bấm home {} (ô {}) để mở menu xóa", this.ownNumber, var4);
                  this.step = HomeManager.Step.WAIT_EDIT;
                  this.age = 0;
                  this.stable = 0;
                  this.lastSig = "";
                  this.status = "Xóa home " + this.ownNumber + " cũ (tầng dưới)";
                  return;
               }

               if (this.ownNumber > 0) {
                  // home cũ không còn (đã xóa xong / người chơi tự xóa)
                  this.deleted = true;
                  this.ownNumber = 0;
               }

               if (var2[0] < 0) {
                  this.noFree = true;
                  this.finish(var1, HomeManager.Result.FAILED, "Không còn home trống (đang có " + var3.keySet() + ") → không dùng /sethome");
                  return;
               }

               this.expectNumber = var2[0] + 1;
               this.homesBefore = var3;
               this.closeOwned(var1);
               this.step = HomeManager.Step.SEND_SET;
               this.age = 0;
               this.status = "Đặt home " + this.expectNumber + " ở tầng đang xây";
               break;
            case WAIT_EDIT:
               if (!(var1.currentScreen instanceof HandledScreen)) {
                  if (this.age > 60) {
                     this.again(var1, "menu Home " + this.ownNumber + " không mở");
                  }

                  return;
               }

               if (!this.settled(var1)) {
                  return;
               }

               this.ownedSync = var1.player.currentScreenHandler.syncId;
               int var5 = this.findId(var1, "lava_bucket");
               if (var5 < 0) {
                  if (this.age > 60) {
                     this.again(var1, "không thấy nút xóa (xô lava) trong " + var1.currentScreen.getTitle().getString());
                  }

                  return;
               }

               this.editSig = this.ownedSync + "|" + var1.currentScreen.getTitle().getString();
               var1.interactionManager.clickSlot(this.ownedSync, var5, 0, SlotActionType.PICKUP, var1.player);
               AutoBuildMod.LOGGER.info("[AutoBuild] Home: bấm xô lava (xóa) ở ô {} trong {}", var5, var1.currentScreen.getTitle().getString());
               this.step = HomeManager.Step.WAIT_CONFIRM;
               this.age = 0;
               this.stable = 0;
               this.lastSig = "";
               this.confirmClicks = 0;
               break;
            case WAIT_CONFIRM:
               this.confirmTick(var1);
               break;
            case SEND_SET:
               if (var1.currentScreen != null) {
                  if (this.age > 3) {
                     var1.player.closeHandledScreen();
                     this.age = 0;
                  }

                  return;
               }

               if (!this.cooled()) {
                  return;
               }

               this.setPos = var1.player.getPos();
               this.send(var1, cmd(this.config.setHomeCommand, "sethome"));
               // 3.0.93: /sethome luôn tạo home ở ô trống đầu tiên (trái → phải) đã đọc trước đó → không mở /home kiểm tra lại
               this.newNumber = this.expectNumber;
               this.finish(var1, HomeManager.Result.DONE, "Đã /sethome → home " + this.expectNumber + " tại tầng đang xây");
               break;
            case WAIT_SET:
               if (this.age > 20) {
                  this.step = HomeManager.Step.SEND_LIST;
                  this.age = 0;
                  this.openTries = 0;
               }
               break;
            case SEND_TP:
               if (var1.currentScreen != null) {
                  if (this.age > 3) {
                     var1.player.closeHandledScreen();
                     this.age = 0;
                  }

                  return;
               }

               if (!this.cooled()) {
                  return;
               }

               if (this.tpTries++ == 0) {
                  this.tpMark = ChatWatch.mark();
               }

               this.send(var1, cmd(this.config.homeCommand, "home") + " " + this.tpNumber);
               this.step = HomeManager.Step.WAIT_TP;
               this.age = 0;
               this.status = "Chờ dịch chuyển về home " + this.tpNumber + " (đứng yên)";
               break;
            case WAIT_TP:
               if (var1.currentScreen instanceof HandledScreen && this.homeScreen(var1) && this.age > 10) {
                  var1.player.closeHandledScreen();
               }

               if (var1.player.getPos().squaredDistanceTo(this.tpTarget) < 2.25) {
                  this.finish(var1, HomeManager.Result.DONE, "Đã về home " + this.tpNumber + " sau " + this.age / 20.0F + " giây");
                  return;
               }

               if (this.age > 320) {
                  if (this.tpTries < 2) {
                     AutoBuildMod.LOGGER.info("[AutoBuild] Home: 16 giây chưa dịch chuyển về home {} → gửi lại lệnh", this.tpNumber);
                     this.step = HomeManager.Step.SEND_TP;
                     this.age = 0;
                  } else {
                     // 3.0.95: 2 lần /home N mà không dịch chuyển, server cũng không báo hủy (do di chuyển/đánh nhau…) → server lag
                     this.tpLag = !ChatWatch.any(this.tpMark, var0 -> var0.contains("cancel") || var0.contains("moved") || var0.contains("combat") || var0.contains("huy"));
                     this.finish(var1, HomeManager.Result.FAILED, "Gửi /home " + this.tpNumber + " 2 lần mà không dịch chuyển" + (this.tpLag ? " (server lag)" : ""));
                  }
               }
         }
      }
   }

   /**
    * 3.0.94: menu "Confirm Delete".
    * Lỗi cũ: bấm lần 1 xong thì server đổi nút (trạng thái "Click again to confirm…") → findConfirm không thấy kính xanh
    * → mod đóng menu ngay rồi /home lại từ đầu → lần bấm thứ 2 không bao giờ tới → home không bao giờ bị xóa.
    * Giờ: giữ menu mở, sau khi server báo "Click again…" thì bấm lần 2 đúng ô vừa bấm, rồi chờ tới khi server báo "Home deleted".
    */
   private void confirmTick(MinecraftClient var1) {
      boolean var2 = var1.currentScreen instanceof HandledScreen && var1.player.currentScreenHandler != var1.player.playerScreenHandler;
      if (this.confirmClicks > 0 && this.deletedMsg()) {
         this.deleted = true;
         AutoBuildMod.LOGGER
            .info(
               "[AutoBuild] Home: server báo đã xóa home {} (\"{}\") sau {} lần bấm xác nhận",
               new Object[]{this.ownNumber, ChatWatch.find(this.deleteMark, "delet"), this.confirmClicks}
            );
         this.closeOwned(var1);
         int var3 = this.freeBefore;
         if (this.ownSlot >= 0 && (var3 < 0 || this.ownSlot < var3)) {
            var3 = this.ownSlot;
         }

         this.ownNumber = 0;
         this.age = 0;
         if (var3 < 0) {
            this.step = HomeManager.Step.SEND_LIST;
            this.openTries = 0;
         } else {
            // /sethome dùng ô trống đầu tiên (trái → phải): ô home vừa xóa hoặc ô trống có sẵn đứng trước nó
            this.expectNumber = var3 + 1;
            this.step = HomeManager.Step.SEND_SET;
            this.status = "Đặt home " + this.expectNumber + " ở tầng đang xây";
         }

         return;
      }

      int var4 = this.age - this.confirmAt;
      if (!var2) {
         if (this.confirmClicks == 0) {
            if (this.age > 60) {
               this.again(var1, "menu xác nhận xóa không mở");
            }
         } else if (var4 > 40) {
            // menu đã đóng mà chưa thấy báo xóa → mở lại danh sách: home còn thì xóa lại, mất rồi thì coi như đã xóa
            AutoBuildMod.LOGGER.info("[AutoBuild] Home: menu xác nhận đã đóng sau {} lần bấm, chưa thấy báo xóa → mở lại danh sách kiểm tra", this.confirmClicks);
            this.step = HomeManager.Step.SEND_LIST;
            this.age = 0;
            this.openTries = 0;
         }

         return;
      }

      if (this.confirmClicks == 0) {
         // còn là menu "Home N" cũ (server chưa mở menu xác nhận) → chờ, tránh bấm nhầm nút xanh của menu cũ
         if ((var1.player.currentScreenHandler.syncId + "|" + var1.currentScreen.getTitle().getString()).equals(this.editSig) && this.age < 40) {
            return;
         }

         if (!this.settled(var1)) {
            if (this.age > 100) {
               this.again(var1, "menu xác nhận xóa chưa ổn định");
            }

            return;
         }

         this.ownedSync = var1.player.currentScreenHandler.syncId;
         int var5 = this.findConfirm(var1);
         if (var5 < 0) {
            if (this.age > 60) {
               this.again(var1, "không thấy nút xác nhận (kính xanh) trong " + var1.currentScreen.getTitle().getString());
            }

            return;
         }

         this.confirmSlot = var5;
         this.deleteMark = ChatWatch.mark();
         this.clickConfirm(var1, var5);
         return;
      }

      // đã bấm ≥ 1 lần: KHÔNG đóng menu. Chờ server trả lời lần bấm trước rồi mới bấm tiếp đúng ô đó.
      boolean var6 = ChatWatch.since(this.clickMark, "click again");
      int var7 = var6 ? 20 : (ChatWatch.installed() ? 50 : 30);
      int var9 = ChatWatch.installed() ? 4 : 2;
      if (this.confirmClicks < var9 && var4 >= var7) {
         if (!var1.player.currentScreenHandler.getCursorStack().isEmpty()) {
            return;
         }

         int var8 = this.findConfirm(var1);
         if (var8 < 0) {
            var8 = this.confirmSlot;
         }

         if (var8 >= 0 && var8 < topSize(var1) && !var1.player.currentScreenHandler.getSlot(var8).getStack().isEmpty()) {
            this.ownedSync = var1.player.currentScreenHandler.syncId;
            if (var6) {
               AutoBuildMod.LOGGER.info("[AutoBuild] Home: server báo \"{}\" → bấm xác nhận lần nữa", ChatWatch.find(this.clickMark, "click again"));
            }

            this.clickConfirm(var1, var8);
         } else if (var4 > 100) {
            this.again(var1, "nút xác nhận biến mất mà chưa thấy báo xóa");
         }
      } else if (ChatWatch.installed() && this.confirmClicks >= 4 && var4 > 120) {
         this.again(var1, "bấm xác nhận 4 lần vẫn chưa thấy server báo xóa home");
      } else if (!ChatWatch.installed() && this.confirmClicks >= 2 && var4 > 60) {
         // không nghe được chat → kiểm tra bằng danh sách /home như cũ (nhưng đã chờ đủ lâu sau lần bấm 2)
         this.closeOwned(var1);
         this.step = HomeManager.Step.SEND_LIST;
         this.age = 0;
         this.openTries = 0;
      }
   }

   /** server báo đã xóa: "Home deleted" (DonutSMP) hoặc câu tương tự có "delet…/remov…/đã xóa", không phải câu hỏi xác nhận */
   private boolean deletedMsg() {
      return ChatWatch.any(
         this.deleteMark,
         var0 -> (var0.contains("deleted") || var0.contains("removed") || var0.contains("da xoa"))
            && !var0.contains("click again")
            && !var0.contains("confirm")
            && !var0.contains("cannot")
            && !var0.contains("can't")
            && !var0.contains("khong")
      );
   }

   private void clickConfirm(MinecraftClient var1, int var2) {
      this.clickMark = ChatWatch.mark();
      var1.interactionManager.clickSlot(var1.player.currentScreenHandler.syncId, var2, 0, SlotActionType.PICKUP, var1.player);
      this.confirmClicks++;
      this.confirmAt = this.age;
      this.status = "Xóa home " + this.ownNumber + " cũ: đã bấm xác nhận " + this.confirmClicks + " lần, chờ server báo xóa";
      AutoBuildMod.LOGGER.info("[AutoBuild] Home: bấm xác nhận xóa lần {} (ô {}) — giữ menu mở, chờ server trả lời", this.confirmClicks, var2);
   }

   private void again(MinecraftClient var1, String var2) {
      AutoBuildMod.LOGGER.info("[AutoBuild] Home: {} → mở lại danh sách", var2);
      this.closeOwned(var1);
      this.step = HomeManager.Step.SEND_LIST;
      this.age = 0;
      this.openTries = 0;
   }

   private void verifySet(MinecraftClient var1, Map<Integer, Integer> var2) {
      int var3 = 0;
      if (var2.containsKey(this.expectNumber) && !this.homesBefore.containsKey(this.expectNumber)) {
         var3 = this.expectNumber;
      } else {
         for (Integer var5 : var2.keySet()) {
            if (!this.homesBefore.containsKey(var5)) {
               var3 = var5;
               break;
            }
         }
      }

      if (var3 > 0) {
         this.newNumber = var3;
         this.finish(var1, HomeManager.Result.DONE, "Đã đặt home " + var3 + " tại tầng đang xây");
      } else {
         this.newNumber = 0;
         this.finish(var1, HomeManager.Result.FAILED, "Đã /sethome nhưng danh sách home không có home mới (server từ chối?)");
      }
   }

   private int findId(MinecraftClient var1, String var2) {
      for (int var3 = 0; var3 < topSize(var1); var3++) {
         if (id(var1.player.currentScreenHandler.getSlot(var3).getStack()).equals(var2)) {
            return var3;
         }
      }

      return -1;
   }

   private int findConfirm(MinecraftClient var1) {
      for (int var2 = 0; var2 < topSize(var1); var2++) {
         ItemStack var3 = var1.player.currentScreenHandler.getSlot(var2).getStack();
         String var4 = id(var3);
         if (var4.equals("lime_stained_glass_pane")
            || var4.equals("green_stained_glass_pane")
            || var4.equals("lime_wool")
            || var4.equals("green_wool")
            || var4.equals("lime_concrete")
            || var4.equals("green_concrete")) {
            String var5 = OrderRules.text(var3.getName().getString());
            if (!var5.contains("cancel") && !var5.contains("huy")) {
               return var2;
            }
         }
      }

      return -1;
   }

   public static enum Result {
      RUNNING,
      DONE,
      FAILED;
   }

   private static enum Job {
      NONE,
      SET,
      TELEPORT;
   }

   private static enum Step {
      SEND_LIST,
      WAIT_LIST,
      WAIT_EDIT,
      WAIT_CONFIRM,
      SEND_SET,
      WAIT_SET,
      SEND_TP,
      WAIT_TP,
      DONE;
   }
}
