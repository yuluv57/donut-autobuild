package com.donutautomine.order;

import com.donutautomine.AutoMineMod;
import com.donutautomine.MineConfig;
import com.donutautomine.util.Texts;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;

public final class ShopBrowser {
   private static final Pattern MONEY = Pattern.compile("\\$\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kmbt])?(?![a-z])");
   private static final int ROWS = 45;
   private final MineConfig config;
   private Item wanted;
   private int need;
   private long maxPrice;
   private ShopBrowser.Step step = ShopBrowser.Step.OPEN;
   private ShopBrowser.Result result = ShopBrowser.Result.FAILED;
   private String status = "";
   private int age;
   private int total;
   private int stable;
   private String lastSig = "";
   private String clickSig = "";
   private int opens;
   private int wrongMenus;
   private int buyClicks;
   private int before;
   private int lastHave;
   private int clickHave;
   private int adds;
   private boolean added;
   private Item picked;
   private int priceWaits;
   private int priceReopens;
   private List<String> names = new ArrayList<>();
   private int nameIdx;
   private boolean searched;
   private int searchClicks;
   private int signFails;
   private int signTicks;
   private ShopBrowser.Sign pending;
   private final Set<String> logged = new HashSet<>();

   public ShopBrowser(MineConfig var1) {
      this.config = var1;
   }

   public boolean active() {
      return this.result == ShopBrowser.Result.RUNNING;
   }

   public ShopBrowser.Result result() {
      return this.result;
   }

   public String status() {
      return this.status;
   }

   public void start(MinecraftClient var1, Item var2, int var3, long var4) {
      this.wanted = var2;
      this.need = Math.max(1, var3);
      this.maxPrice = var4;
      this.step = ShopBrowser.Step.OPEN;
      this.result = ShopBrowser.Result.RUNNING;
      this.age = 0;
      this.total = 0;
      this.stable = 0;
      this.lastSig = "";
      this.clickSig = "";
      this.opens = 0;
      this.wrongMenus = 0;
      this.buyClicks = 0;
      this.adds = 0;
      this.added = false;
      this.picked = null;
      this.priceWaits = 0;
      this.priceReopens = 0;
      this.names = searchNames(var2);
      this.nameIdx = 0;
      this.searched = false;
      this.searchClicks = 0;
      this.signFails = 0;
      this.signTicks = 0;
      this.pending = null;
      this.logged.clear();
      this.before = this.have(var1);
      this.lastHave = this.before;
      this.clickHave = this.before;
      this.status = "Mua " + var2.getName().getString() + " ở /" + this.cmd();
   }

   public void cancel(MinecraftClient var1) {
      if (this.active()) {
         this.close(var1);
      }

      this.result = ShopBrowser.Result.FAILED;
   }

   private String cmd() {
      String var1 = this.config.shopCommand == null ? "" : this.config.shopCommand.trim();

      while (var1.startsWith("/")) {
         var1 = var1.substring(1).trim();
      }

      return var1.isEmpty() ? "shop" : var1;
   }

   private String wantedName() {
      return this.wanted.getName().getString();
   }

   private int have(MinecraftClient var1) {
      return Inv.count(var1, this.wanted);
   }

   private static boolean menuOpen(MinecraftClient var0) {
      return var0.currentScreen instanceof HandledScreen && var0.player.currentScreenHandler != var0.player.playerScreenHandler;
   }

   private static boolean signOpen(MinecraftClient var0) {
      return var0.currentScreen instanceof AbstractSignEditScreen;
   }

   private void close(MinecraftClient var1) {
      if (var1.player != null) {
         Screen var2 = var1.currentScreen;
         if (var2 instanceof AbstractSignEditScreen) {
            var2.close();
         } else if (menuOpen(var1)) {
            var1.player.closeHandledScreen();
         }
      }
   }

   private void finish(MinecraftClient var1, ShopBrowser.Result var2, String var3) {
      this.close(var1);
      this.result = var2;
      this.status = var3;
      AutoMineMod.LOGGER.info("[AutoMine] Shop: {}", var3);
   }

   private static int topSize(MinecraftClient var0) {
      return var0.player.currentScreenHandler.slots.size() - 36;
   }

   private static ItemStack stack(MinecraftClient var0, int var1) {
      return var0.player.currentScreenHandler.getSlot(var1).getStack();
   }

   private static String id(ItemStack var0) {
      if (var0.isEmpty()) {
         return "";
      } else {
         Identifier var1 = Registries.ITEM.getId(var0.getItem());
         return var1 == null ? "" : var1.getPath();
      }
   }

   private static String title(MinecraftClient var0) {
      return var0.currentScreen == null ? "" : Texts.norm(var0.currentScreen.getTitle().getString());
   }

   static List<String> searchNames(Item var0) {
      LinkedHashSet var1 = new LinkedHashSet();
      String var2 = Texts.norm(var0.getName().getString());
      Identifier var3 = Registries.ITEM.getId(var0);
      String var4 = var3 == null ? "" : var3.getPath().replace('_', ' ').trim();
      boolean var5 = !var2.isEmpty();

      for (int var6 = 0; var6 < var2.length() && var5; var6++) {
         if (var2.charAt(var6) > 127) {
            var5 = false;
         }
      }

      if (var5) {
         var1.add(var2);
      }

      if (!var4.isEmpty()) {
         var1.add(var4);
      }

      String var11 = "";

      for (String var10 : (var4.isEmpty() ? var2 : var4).split(" ")) {
         if (var10.length() > var11.length()) {
            var11 = var10;
         }
      }

      if (var11.length() >= 4) {
         var1.add(var11);
      }

      return new ArrayList<>(var1);
   }

   static long price(ItemStack var0) {
      List<String> var1 = Texts.lines(var0);

      for (int var2 = 0; var2 < 2; var2++) {
         for (String var4 : var1) {
            String var5 = Texts.norm(var4);
            if (!var5.contains("sell") && !var5.contains("ban ")) {
               boolean var6 = var5.contains("buy") || var5.contains("price") || var5.contains("cost") || var5.contains("gia") || var5.contains("mua");
               if (var2 != 0 || var6) {
                  long var7 = money(var5);
                  if (var7 >= 0L) {
                     return var7;
                  }
               }
            }
         }
      }

      return -1L;
   }

   static long money(String var0) {
      Matcher var1 = MONEY.matcher(var0);
      if (!var1.find()) {
         return -1L;
      } else {
         try {
            double var2 = Double.parseDouble(var1.group(1).replace(",", ""));
            String var4 = var1.group(2);
            double var10000;
            if (var4 == null) {
               var10000 = 1.0;
            } else {
               switch (var4) {
                  case "k":
                     var10000 = 1000.0;
                     break;
                  case "m":
                     var10000 = 1000000.0;
                     break;
                  case "b":
                     var10000 = 1.0E9;
                     break;
                  case "t":
                     var10000 = 1.0E12;
                     break;
                  default:
                     var10000 = 1.0;
               }
            }

            double var5 = var10000;
            return Math.round(var2 * var5);
         } catch (NumberFormatException var9) {
            return -1L;
         }
      }
   }

   private static String money(long var0) {
      if (var0 >= 1000000L) {
         return String.format("%.2fM", var0 / 1000000.0);
      } else {
         return var0 >= 1000L ? String.format("%.1fK", var0 / 1000.0) : String.valueOf(var0);
      }
   }

   private boolean settled(MinecraftClient var1) {
      StringBuilder var2 = new StringBuilder().append(var1.player.currentScreenHandler.syncId).append('|').append(var1.currentScreen.getTitle().getString());

      for (int var3 = 0; var3 < topSize(var1); var3++) {
         ItemStack var4 = stack(var1, var3);
         var2.append('|').append(id(var4)).append(':').append(var4.getCount());
      }

      String var5 = var2.toString();
      if (var5.equals(this.lastSig)) {
         this.stable++;
      } else {
         this.stable = 0;
         this.lastSig = var5;
      }

      return this.stable >= 2 && var1.player.currentScreenHandler.getCursorStack().isEmpty();
   }

   private void logMenu(MinecraftClient var1) {
      String var2 = var1.currentScreen.getTitle().getString();
      if (this.logged.size() < 40 && this.logged.add(var2 + "#" + this.lastSig.hashCode())) {
         StringBuilder var3 = new StringBuilder();
         int var4 = 0;

         for (int var5 = 0; var5 < topSize(var1); var5++) {
            ItemStack var6 = stack(var1, var5);
            if (isEmptySlot(var6)) {
               var4++;
            } else if (!var6.isEmpty() && !id(var6).endsWith("glass_pane")) {
               long var7 = price(var6);
               var3.append(var5).append('=').append(var6.getName().getString()).append(var6.getCount() > 1 ? " x" + var6.getCount() : "");
               if (var7 >= 0L) {
                  var3.append(" $").append(money(var7));
               }

               var3.append("; ");
            }
         }

         AutoMineMod.LOGGER.info("[AutoMine] Shop: menu \"{}\" ({} ô, {} ô Empty): {}", new Object[]{var2, topSize(var1), var4, var3});
      }
   }

   private static boolean isEmptySlot(ItemStack var0) {
      if (var0.isEmpty()) {
         return false;
      } else {
         String var1 = Texts.norm(var0.getName().getString());
         if (!var1.equals("empty") && !var1.startsWith("empty ")) {
            for (String var3 : Texts.lines(var0)) {
               if (Texts.norm(var3).contains("click to choose")) {
                  return true;
               }
            }

            return false;
         } else {
            return true;
         }
      }
   }

   private int emptySlot(MinecraftClient var1) {
      for (int var2 = 0; var2 < Math.min(45, topSize(var1)); var2++) {
         if (isEmptySlot(stack(var1, var2))) {
            return var2;
         }
      }

      return -1;
   }

   private int wantedSlot(MinecraftClient var1) {
      int var2 = -1;
      int var3 = -1;
      int var4 = -1;
      int var5 = Integer.MAX_VALUE;
      int var6 = this.need - (this.have(var1) - this.before);

      for (int var7 = 0; var7 < Math.min(45, topSize(var1)); var7++) {
         ItemStack var8 = stack(var1, var7);
         if (!var8.isEmpty() && !isEmptySlot(var8) && var8.getItem() == this.wanted) {
            int var9 = var8.getCount();
            if (var9 >= var6 && var9 < var5) {
               var5 = var9;
               var2 = var7;
            }

            if (var9 > var4) {
               var4 = var9;
               var3 = var7;
            }
         }
      }

      return var2 >= 0 ? var2 : var3;
   }

   private int resultSlot(MinecraftClient var1) {
      for (int var2 = 0; var2 < Math.min(45, topSize(var1)); var2++) {
         ItemStack var3 = stack(var1, var2);
         if (!var3.isEmpty() && var3.getItem() == this.wanted) {
            return var2;
         }
      }

      return -1;
   }

   private int searchSlot(MinecraftClient var1) {
      int var2 = -1;

      for (int var3 = 0; var3 < topSize(var1); var3++) {
         ItemStack var4 = stack(var1, var3);
         if (!var4.isEmpty() && Texts.norm(var4.getName().getString()).contains("search")) {
            if (var3 >= 45) {
               return var3;
            }

            if (var2 < 0) {
               var2 = var3;
            }
         }
      }

      return var2;
   }

   private void click(MinecraftClient var1, int var2) {
      this.clickSig = this.lastSig;
      var1.interactionManager.clickSlot(var1.player.currentScreenHandler.syncId, var2, 0, SlotActionType.PICKUP, var1.player);
      this.age = 0;
      this.stable = 0;
   }

   private static boolean writeSign(Screen var0, String var1) {
      boolean var2 = false;

      try {
         for (Field var6 : AbstractSignEditScreen.class.getDeclaredFields()) {
            if (var6.getType() == String[].class) {
               var6.setAccessible(true);
               if (var6.get(var0) instanceof String[] var7 && var7.length > 0) {
                  var7[0] = var1;
                  var2 = true;
               }
               break;
            }
         }
      } catch (Throwable var9) {
         AutoMineMod.LOGGER.warn("[AutoMine] Shop: không ghi thẳng được vào biển ({}) → gõ từng ký tự", var9.toString());
      }

      if (!var2) {
         for (int var10 = 0; var10 < var1.length(); var10++) {
            var0.charTyped(var1.charAt(var10), 0);
         }
      }

      var0.close();
      return var2;
   }

   private int amount() {
      Item var1 = this.picked != null ? this.picked : this.wanted;
      return Math.max(1, Math.min(this.need, var1.getMaxCount()));
   }

   public void tick(MinecraftClient var1) {
      if (this.active() && var1.player != null && var1.interactionManager != null) {
         this.age++;
         if (++this.total > 2400) {
            this.end(var1, "quá 2 phút chưa mua xong");
         } else {
            int var2 = this.have(var1);
            if (var2 > this.lastHave) {
               this.lastHave = var2;
               AutoMineMod.LOGGER.info("[AutoMine] Shop: đã nhận {} (cần {})", var2 - this.before, this.need);
            }

            if (var2 - this.before < this.need && (var2 <= this.before || Inv.freeSlots(var1) > this.config.reserveFreeSlots)) {
               switch (this.step) {
                  case OPEN:
                     this.openTick(var1);
                     break;
                  case QUICK:
                     this.quickTick(var1);
                     break;
                  case PICK:
                     this.pickTick(var1);
                     break;
                  case SIGN:
                     this.signTick(var1);
                     break;
                  case WAIT_ITEM:
                     this.waitItemTick(var1, var2);
               }
            } else {
               this.finish(var1, ShopBrowser.Result.BOUGHT, "Đã mua " + (var2 - this.before) + " " + this.wantedName() + " ở /" + this.cmd());
            }
         }
      }
   }

   private void openTick(MinecraftClient var1) {
      if (var1.currentScreen != null) {
         if (this.age > 3) {
            if (signOpen(var1)) {
               var1.currentScreen.close();
            } else {
               var1.player.closeHandledScreen();
            }

            this.age = 0;
         }
      } else if (Inv.freeSlots(var1) <= this.config.reserveFreeSlots) {
         this.end(var1, "túi đồ đầy");
      } else if (System.currentTimeMillis() - GuiWatch.lastCommandAt() < GuiWatch.gap(4000L)) {
         this.status = "Chờ hết nguội lệnh /" + this.cmd();
      } else if (++this.opens <= 5) {
         GuiWatch.markCommand();
         GuiWatch.sent(this.cmd());
         var1.player.networkHandler.sendChatCommand(this.cmd());
         AutoMineMod.LOGGER.info("[AutoMine] Shop: mở /{} tìm {} (lần {})", new Object[]{this.cmd(), this.wantedName(), this.opens});
         this.status = "Mở /" + this.cmd() + " (Quick Buy) tìm " + this.wantedName();
         this.step = ShopBrowser.Step.QUICK;
         this.age = 0;
         this.stable = 0;
         this.lastSig = "";
         this.clickSig = "";
      } else {
         this.end(var1, "đã mở /" + this.cmd() + " " + (this.opens - 1) + " lần mà chưa mua được " + this.wantedName());
      }
   }

   private boolean menuReady(MinecraftClient var1, int var2) {
      if (!menuOpen(var1)) {
         if (this.age > var2) {
            this.step = ShopBrowser.Step.OPEN;
            this.age = 0;
         }

         return false;
      } else if (!this.settled(var1)) {
         if (this.age > 160) {
            this.close(var1);
            this.step = ShopBrowser.Step.OPEN;
            this.age = 0;
         }

         return false;
      } else {
         return this.clickSig.isEmpty() || !this.lastSig.equals(this.clickSig) || this.age >= 30;
      }
   }

   private void wrongMenu(MinecraftClient var1, String var2) {
      this.logMenu(var1);
      if (++this.wrongMenus > 3) {
         this.end(var1, "3 lần mở ra menu không phải Quick Buy (" + var2 + ")");
      } else {
         AutoMineMod.LOGGER.info("[AutoMine] Shop: đang ở menu \"{}\" (không phải Quick Buy/Select Item) → không bấm gì, mở lại /{}", var2, this.cmd());
         this.close(var1);
         this.step = ShopBrowser.Step.OPEN;
         this.age = 0;
      }
   }

   private void quickTick(MinecraftClient var1) {
      if (signOpen(var1)) {
         if (this.pending != null) {
            this.step = ShopBrowser.Step.SIGN;
         } else if (this.age > 10) {
            var1.currentScreen.close();
         }
      } else if (this.menuReady(var1, 40)) {
         String var2 = title(var1);
         if (var2.contains("select item")) {
            this.step = ShopBrowser.Step.PICK;
            this.age = 0;
         } else if (!var2.contains("quick buy")) {
            this.wrongMenu(var1, var2);
         } else {
            this.logMenu(var1);
            int var3 = this.wantedSlot(var1);
            if (var3 >= 0) {
               this.tryBuy(var1, var3);
            } else if (this.added) {
               this.end(var1, "đã chọn " + this.wantedName() + " vào Quick Buy nhưng không thấy trong menu");
            } else if (this.adds >= 2) {
               this.end(var1, "đã thử thêm " + this.wantedName() + " vào Quick Buy 2 lần chưa được");
            } else {
               int var4 = this.emptySlot(var1);
               if (var4 < 0) {
                  this.end(var1, "Quick Buy không còn ô Empty để thêm " + this.wantedName() + " (xóa bớt ô trong /" + this.cmd() + " → Edit)");
               } else {
                  this.adds++;
                  this.nameIdx = 0;
                  this.searched = false;
                  AutoMineMod.LOGGER.info("[AutoMine] Shop: Quick Buy chưa có {} → bấm ô Empty {} để chọn vật phẩm", this.wantedName(), var4);
                  this.status = "Quick Buy: chọn " + this.wantedName() + " vào ô trống";
                  this.click(var1, var4);
                  this.step = ShopBrowser.Step.PICK;
               }
            }
         }
      }
   }

   private void pickTick(MinecraftClient var1) {
      if (signOpen(var1)) {
         if (this.pending != null) {
            this.step = ShopBrowser.Step.SIGN;
         } else if (this.age > 10) {
            var1.currentScreen.close();
         }
      } else if (this.menuReady(var1, 60)) {
         String var2 = title(var1);
         if (var2.contains("quick buy")) {
            this.step = ShopBrowser.Step.QUICK;
            this.clickSig = "";
            this.age = 0;
         } else if (!var2.contains("select item")) {
            this.wrongMenu(var1, var2);
         } else if (this.added) {
            this.close(var1);
            this.step = ShopBrowser.Step.OPEN;
            this.age = 0;
         } else {
            this.logMenu(var1);
            int var3 = this.resultSlot(var1);
            if (var3 >= 0) {
               ItemStack var5 = stack(var1, var3);
               this.picked = var5.getItem();
               this.pending = ShopBrowser.Sign.AMOUNT;
               AutoMineMod.LOGGER.info("[AutoMine] Shop: bấm {} (ô {}) → nhập số lượng {}", new Object[]{var5.getName().getString(), var3, this.amount()});
               this.status = "Chọn " + var5.getName().getString() + " → nhập số lượng " + this.amount();
               this.click(var1, var3);
               this.step = ShopBrowser.Step.SIGN;
               return;
            }

            if (this.searched) {
               AutoMineMod.LOGGER.info("[AutoMine] Shop: tìm \"{}\" không thấy {}", this.names.get(this.nameIdx), this.wantedName());
               this.nameIdx++;
               this.searched = false;
               if (this.nameIdx >= this.names.size()) {
                  this.end(var1, "/" + this.cmd() + " không có " + this.wantedName() + " (đã tìm " + this.names + ")");
                  return;
               }
            }

            int var4 = this.searchSlot(var1);
            if (var4 < 0) {
               this.end(var1, "không thấy nút Search trong menu chọn vật phẩm");
            } else if (++this.searchClicks > this.names.size() + 2) {
               this.end(var1, "đã bấm Search " + (this.searchClicks - 1) + " lần mà chưa tìm được " + this.wantedName());
            } else {
               this.pending = ShopBrowser.Sign.SEARCH;
               AutoMineMod.LOGGER.info("[AutoMine] Shop: bấm Search (ô {}) → nhập \"{}\"", var4, this.names.get(this.nameIdx));
               this.status = "Tìm \"" + this.names.get(this.nameIdx) + "\" trong danh sách vật phẩm";
               this.click(var1, var4);
               this.step = ShopBrowser.Step.SIGN;
            }
         }
      }
   }

   private void signTick(MinecraftClient var1) {
      Screen var2 = var1.currentScreen;
      if (var2 instanceof AbstractSignEditScreen) {
         if (++this.signTicks >= 5) {
            ShopBrowser.Sign var6 = this.pending == null ? ShopBrowser.Sign.SEARCH : this.pending;
            String var4 = var6 == ShopBrowser.Sign.SEARCH ? this.names.get(this.nameIdx) : String.valueOf(this.amount());
            boolean var5 = writeSign(var2, var4);
            AutoMineMod.LOGGER
               .info(
                  "[AutoMine] Shop: ghi \"{}\" vào biển {} rồi bấm Done{}",
                  new Object[]{var4, var6 == ShopBrowser.Sign.SEARCH ? "Search" : "Amount", var5 ? "" : " (gõ từng ký tự)"}
               );
            if (var6 == ShopBrowser.Sign.SEARCH) {
               this.searched = true;
               this.step = ShopBrowser.Step.PICK;
            } else {
               this.added = true;
               this.step = ShopBrowser.Step.QUICK;
            }

            this.pending = null;
            this.signTicks = 0;
            this.age = 0;
            this.stable = 0;
            this.lastSig = "";
            this.clickSig = "";
         }
      } else {
         this.signTicks = 0;
         if (menuOpen(var1)) {
            if (this.age > 40 && this.settled(var1)) {
               String var3 = title(var1);
               if (this.pending == ShopBrowser.Sign.AMOUNT && var3.contains("quick buy")) {
                  this.added = true;
                  this.pending = null;
                  this.step = ShopBrowser.Step.QUICK;
                  this.clickSig = "";
                  this.age = 0;
               } else if (this.age > 80) {
                  if (++this.signFails > 2) {
                     this.end(var1, "bấm mà không hiện biển nhập chữ (" + var3 + ")");
                  } else {
                     this.pending = null;
                     this.clickSig = "";
                     this.step = var3.contains("quick buy") ? ShopBrowser.Step.QUICK : ShopBrowser.Step.PICK;
                     this.age = 0;
                  }
               }
            }
         } else if (this.age > 100) {
            this.pending = null;
            this.step = ShopBrowser.Step.OPEN;
            this.age = 0;
         }
      }
   }

   private void waitItemTick(MinecraftClient var1, int var2) {
      if (var2 > this.clickHave) {
         this.step = ShopBrowser.Step.QUICK;
         this.clickSig = "";
         this.age = 0;
         this.stable = 0;
      } else if (this.age > 100) {
         this.end(var1, "đã bấm mua mà không nhận được hàng (hết tiền?)");
      } else if (!menuOpen(var1) && !signOpen(var1) && this.age > 40) {
         this.step = ShopBrowser.Step.OPEN;
         this.age = 0;
      }
   }

   private void tryBuy(MinecraftClient var1, int var2) {
      ItemStack var3 = stack(var1, var2);
      long var4 = price(var3);
      if (var4 < 0L) {
         if (++this.priceWaits < 40) {
            this.status = "Chờ Quick Buy hiện giá " + var3.getName().getString();
         } else if (this.priceReopens < 2) {
            this.priceReopens++;
            this.priceWaits = 0;
            AutoMineMod.LOGGER
               .info(
                  "[AutoMine] Shop: {} trong Quick Buy chưa có giá → mở lại /{} xem giá (lần {})",
                  new Object[]{var3.getName().getString(), this.cmd(), this.priceReopens}
               );
            this.close(var1);
            this.opens = Math.max(0, this.opens - 1);
            this.step = ShopBrowser.Step.OPEN;
            this.age = 0;
         } else {
            this.end(var1, "thấy " + var3.getName().getString() + " trong Quick Buy nhưng không có giá → không mua (an toàn)");
         }
      } else if (var4 > this.maxPrice) {
         this.end(var1, var3.getName().getString() + " giá $" + money(var4) + " > trần $" + money(this.maxPrice) + " → không mua");
      } else if (Inv.freeSlots(var1) <= this.config.reserveFreeSlots) {
         this.end(var1, "túi đồ đầy");
      } else if (++this.buyClicks > 6) {
         this.end(var1, "đã bấm mua 6 lần");
      } else {
         this.clickHave = this.have(var1);
         this.status = "Mua " + var3.getName().getString() + (var3.getCount() > 1 ? " x" + var3.getCount() : "") + " giá $" + money(var4);
         AutoMineMod.LOGGER
            .info(
               "[AutoMine] Shop: bấm mua {} x{} ở ô {} Quick Buy (giá ${} ≤ trần ${})",
               new Object[]{var3.getName().getString(), var3.getCount(), var2, money(var4), money(this.maxPrice)}
            );
         this.click(var1, var2);
         this.step = ShopBrowser.Step.WAIT_ITEM;
      }
   }

   private void end(MinecraftClient var1, String var2) {
      int var3 = this.have(var1) - this.before;
      if (var3 > 0) {
         this.finish(var1, ShopBrowser.Result.BOUGHT, "Đã mua " + var3 + " " + this.wantedName() + " (" + var2 + ")");
      } else {
         this.finish(var1, ShopBrowser.Result.FAILED, var2);
      }
   }

   public static enum Result {
      RUNNING,
      BOUGHT,
      FAILED;
   }

   private static enum Sign {
      SEARCH,
      AMOUNT;
   }

   private static enum Step {
      OPEN,
      QUICK,
      PICK,
      SIGN,
      WAIT_ITEM;
   }
}
