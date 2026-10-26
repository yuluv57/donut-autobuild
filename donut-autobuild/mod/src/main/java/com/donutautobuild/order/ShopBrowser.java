package com.donutautobuild.order;

import com.donutautobuild.AutoBuildMod;
import com.donutautobuild.BuildConfig;
import com.donutautobuild.build.InventoryAccess;
import com.donutautobuild.logic.OrderRules;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;

/**
 * 3.0.91: Order không có hàng → mua ở /shop, KHÔNG mua nếu giá hiện trong shop vượt trần (mặc định 500K).
 *
 * <p>3.0.94: làm đúng theo cách của DonutSMP (người dùng hướng dẫn), không duyệt lung tung (bản cũ bấm Auction/Search đấu giá liên tục):
 * <ol>
 *   <li>/shop → menu "Quick Buy". Có sẵn vật phẩm cần → bấm mua luôn (kiểm tra giá ≤ trần).</li>
 *   <li>Chưa có → bấm 1 ô "Empty" (Click to choose an item to buy) → menu "Orders -> Select Item".</li>
 *   <li>Bấm "Search" → biển nhập chữ → ghi tên vật phẩm (vd. tripwire hook) → Done → hiện kết quả.</li>
 *   <li>Bấm đúng vật phẩm → biển "Amount" → ghi max stack (vd. 64) → Done → về lại "Quick Buy" có vật phẩm đó.</li>
 *   <li>Bấm vật phẩm trong Quick Buy → mua về túi.</li>
 * </ol>
 * Chỉ bấm: vật phẩm cần, ô Empty, nút Search, kết quả tìm kiếm. Không bấm Auction / Your Items / Filter / Edit.
 */
public final class ShopBrowser {
   private static final Pattern MONEY = Pattern.compile("\\$\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kmbt])?(?![a-z])");
   private static final int ROWS = 45;
   private final BuildConfig config;
   private Item wanted;
   private Set<Item> accepted = Set.of();
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
   // Quick Buy: thêm vật phẩm mới vào ô Empty
   private int adds;
   private boolean added;
   private Item picked;
   // 3.0.95: đồ gỗ (fence, sign…) → tìm/mua loại spruce (rẻ nhất) nếu loại đó được chấp nhận thay thế
   private Item prefer;
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

   public ShopBrowser(BuildConfig var1) {
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

   public void start(MinecraftClient var1, Item var2, Set<Item> var3, int var4, long var5) {
      this.wanted = var2;
      HashSet<Item> var7 = new HashSet<>(var3);
      var7.add(var2);
      this.accepted = var7;
      this.need = Math.max(1, var4);
      this.maxPrice = var5;
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
      this.confirmClicks = 0;
      this.confirmWait = 0;
      this.confirmSig = "";
      this.adds = 0;
      this.added = false;
      this.picked = null;
      this.prefer = cheapWood(var2, var7);
      this.priceWaits = 0;
      this.priceReopens = 0;
      ArrayList<String> var8 = new ArrayList<>();
      if (this.prefer != var2) {
         var8.addAll(searchNames(this.prefer));
         AutoBuildMod.LOGGER.info("[AutoBuild] Shop tìm hàng: {} là đồ gỗ → tìm {} trước (gỗ spruce rẻ nhất)", var2.getName().getString(), this.prefer.getName().getString());
      }

      for (String var10 : searchNames(var2)) {
         if (!var8.contains(var10)) {
            var8.add(var10);
         }
      }

      this.names = var8;
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
      this.status = "Order không có " + var2.getName().getString() + " → mua ở /" + this.cmd();
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
      int var2 = 0;

      for (Item var4 : this.accepted) {
         var2 += InventoryAccess.count(var1, var4);
      }

      return var2;
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
      AutoBuildMod.LOGGER.info("[AutoBuild] Shop tìm hàng: {}", var3);
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
      return var0.currentScreen == null ? "" : OrderRules.text(var0.currentScreen.getTitle().getString());
   }

   private static final String[] WOODS = new String[]{
      "pale_oak_", "dark_oak_", "oak_", "spruce_", "birch_", "jungle_", "acacia_", "mangrove_", "cherry_", "bamboo_", "crimson_", "warped_"
   };

   /** đồ gỗ (fence, sign, door…) → bản spruce nếu có trong danh sách chấp nhận; không thì chính nó */
   static Item cheapWood(Item var0, Set<Item> var1) {
      Identifier var2 = Registries.ITEM.getId(var0);
      String var3 = var2 == null ? "" : var2.getPath();

      for (String var7 : WOODS) {
         if (var3.startsWith(var7)) {
            String var8 = "spruce_" + var3.substring(var7.length());

            for (Item var10 : var1) {
               Identifier var11 = Registries.ITEM.getId(var10);
               if (var11 != null && var11.getPath().equals(var8)) {
                  return var10;
               }
            }

            return var0;
         }
      }

      return var0;
   }

   /** tên để gõ vào ô Search: tên tiếng Anh (vd. "tripwire hook"), tên theo id, rồi 1 từ khóa ngắn */
   static List<String> searchNames(Item var0) {
      LinkedHashSet<String> var1 = new LinkedHashSet<>();
      String var2 = OrderRules.text(var0.getName().getString());
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

      String var7 = "";

      for (String var11 : (var4.isEmpty() ? var2 : var4).split(" ")) {
         if (var11.length() > var7.length()) {
            var7 = var11;
         }
      }

      if (var7.length() >= 4) {
         var1.add(var7);
      }

      return new ArrayList<>(var1);
   }

   /** giá (đô) đọc từ tên + mô tả; ưu tiên dòng có buy/price/cost/giá, bỏ dòng sell; -1 nếu không có */
   static long price(ItemStack var0) {
      List<String> var1 = OrderCollector.lines(var0);
      long var2 = -1L;

      for (int var4 = 0; var4 < 2; var4++) {
         for (String var6 : var1) {
            String var7 = OrderRules.text(var6);
            if (!var7.contains("sell") && !var7.contains("ban ")) {
               boolean var8 = var7.contains("buy") || var7.contains("price") || var7.contains("cost") || var7.contains("gia") || var7.contains("mua");
               if (var4 != 0 || var8) {
                  long var9 = money(var7);
                  if (var9 >= 0L) {
                     return var9;
                  }
               }
            }
         }
      }

      return var2;
   }

   static long money(String var0) {
      Matcher var1 = MONEY.matcher(var0);
      if (!var1.find()) {
         return -1L;
      } else {
         try {
            double var2 = Double.parseDouble(var1.group(1).replace(",", ""));
            String var4 = var1.group(2);
            double var5 = var4 == null ? 1.0 : switch (var4) {
               case "k" -> 1000.0;
               case "m" -> 1000000.0;
               case "b" -> 1.0E9;
               case "t" -> 1.0E12;
               default -> 1.0;
            };
            return Math.round(var2 * var5);
         } catch (NumberFormatException var7) {
            return -1L;
         }
      }
   }

   private static String money(long var0) {
      if (var0 >= 1000000L) {
         return String.format("%.2fM", (double)var0 / 1000000.0);
      } else {
         return var0 >= 1000L ? String.format("%.1fK", (double)var0 / 1000.0) : String.valueOf(var0);
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

         AutoBuildMod.LOGGER.info("[AutoBuild] Shop tìm hàng: menu \"{}\" ({} ô, {} ô Empty): {}", new Object[]{var2, topSize(var1), var4, var3});
      }
   }

   /** ô "Empty — Click to choose an item to buy" của Quick Buy */
   private static boolean isEmptySlot(ItemStack var0) {
      if (var0.isEmpty()) {
         return false;
      } else {
         String var1 = OrderRules.text(var0.getName().getString());
         if (var1.equals("empty") || var1.startsWith("empty ")) {
            return true;
         } else {
            for (String var3 : OrderCollector.lines(var0)) {
               if (OrderRules.text(var3).contains("click to choose")) {
                  return true;
               }
            }

            return false;
         }
      }
   }

   private int emptySlot(MinecraftClient var1) {
      for (int var2 = 0; var2 < Math.min(ROWS, topSize(var1)); var2++) {
         if (isEmptySlot(stack(var1, var2))) {
            return var2;
         }
      }

      return -1;
   }

   /** ô vật phẩm cần trong phần hàng hóa (5 hàng trên, không đụng hàng nút dưới cùng); ưu tiên đúng vật phẩm, số lượng vừa đủ */
   private int wantedSlot(MinecraftClient var1) {
      int var2 = this.need - (this.have(var1) - this.before);
      int var3 = -1;
      int var4 = Integer.MAX_VALUE;
      int var5 = -1;
      int var6 = -1;

      for (int var7 = 0; var7 < 3 && var3 < 0 && var5 < 0; var7++) {
         for (int var8 = 0; var8 < Math.min(ROWS, topSize(var1)); var8++) {
            ItemStack var9 = stack(var1, var8);
            Item var11 = var9.getItem();
            if (!var9.isEmpty()
               && !isEmptySlot(var9)
               && (var7 == 0 ? var11 == this.prefer : (var7 == 1 ? var11 == this.wanted : this.accepted.contains(var11)))) {
               int var10 = var9.getCount();
               if (var10 >= var2 && var10 < var4) {
                  var4 = var10;
                  var3 = var8;
               }

               if (var10 > var6) {
                  var6 = var10;
                  var5 = var8;
               }
            }
         }
      }

      return var3 >= 0 ? var3 : var5;
   }

   /** kết quả trong menu "Select Item": đúng vật phẩm trước, rồi vật phẩm cùng họ được chấp nhận */
   private int resultSlot(MinecraftClient var1, boolean var5) {
      for (int var2 = 0; var2 < (var5 ? 3 : 2); var2++) {
         for (int var3 = 0; var3 < Math.min(ROWS, topSize(var1)); var3++) {
            ItemStack var4 = stack(var1, var3);
            Item var6 = var4.getItem();
            if (!var4.isEmpty() && (var2 == 0 ? var6 == this.prefer : (var2 == 1 ? var6 == this.wanted : this.accepted.contains(var6)))) {
               return var3;
            }
         }
      }

      return -1;
   }

   private int searchSlot(MinecraftClient var1) {
      int var2 = -1;

      for (int var3 = 0; var3 < topSize(var1); var3++) {
         ItemStack var4 = stack(var1, var3);
         if (!var4.isEmpty() && OrderRules.text(var4.getName().getString()).contains("search")) {
            if (var3 >= ROWS) {
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

   /** ghi chữ vào dòng 1 của biển rồi bấm Done (đóng màn hình → client gửi nội dung biển cho server) */
   private static boolean writeSign(Screen var0, String var1) {
      boolean var2 = false;

      try {
         for (Field var6 : AbstractSignEditScreen.class.getDeclaredFields()) {
            if (var6.getType() == String[].class) {
               var6.setAccessible(true);
               Object var7 = var6.get(var0);
               if (var7 instanceof String[] var8 && var8.length > 0) {
                  var8[0] = var1;
                  var2 = true;
               }
               break;
            }
         }
      } catch (Throwable var9) {
         AutoBuildMod.LOGGER.warn("[AutoBuild] Shop tìm hàng: không ghi thẳng được vào biển ({}) → gõ từng ký tự", var9.toString());
      }

      if (!var2) {
         for (int var10 = 0; var10 < var1.length(); var10++) {
            ((Element)var0).charTyped(var1.charAt(var10), 0);
         }
      }

      var0.close();
      return var2;
   }

   private int amount() {
      Item var1 = this.picked != null ? this.picked : this.wanted;
      return Math.max(1, var1.getMaxCount());
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
               AutoBuildMod.LOGGER.info("[AutoBuild] Shop tìm hàng: đã nhận {} (cần {})", var2 - this.before, this.need);
            }

            if (var2 - this.before >= this.need || var2 > this.before && InventoryAccess.freeSlots(var1) <= this.config.reserveFreeSlots) {
               this.finish(var1, ShopBrowser.Result.BOUGHT, "Đã mua " + (var2 - this.before) + " " + this.wantedName() + " ở /" + this.cmd());
            } else if (!this.confirmTick(var1)) {
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
      } else if (InventoryAccess.freeSlots(var1) <= this.config.reserveFreeSlots) {
         this.end(var1, "túi đồ đầy");
      } else if (System.currentTimeMillis() - ShopBuyer.lastCommandAt() < GuiWatch.gap(GuiWatch.COOLDOWN_MS)) {
         this.status = "Chờ hết nguội lệnh /" + this.cmd();
      } else if (++this.opens > 5 || GuiWatch.lagging()) {
         this.end(var1, "đã mở /" + this.cmd() + " " + (this.opens - 1) + " lần mà chưa mua được " + this.wantedName());
      } else {
         ShopBuyer.markCommand();
         GuiWatch.sent(this.cmd());
         var1.player.networkHandler.sendChatCommand(this.cmd());
         AutoBuildMod.LOGGER.info("[AutoBuild] Shop tìm hàng: mở /{} tìm {} (lần {})", new Object[]{this.cmd(), this.wantedName(), this.opens});
         this.status = "Mở /" + this.cmd() + " (Quick Buy) tìm " + this.wantedName();
         this.step = ShopBrowser.Step.QUICK;
         this.age = 0;
         this.stable = 0;
         this.lastSig = "";
         this.clickSig = "";
      }
   }

   /** chờ menu mới hiện ra sau lần bấm (hoặc sau lệnh); true = sẵn sàng đọc menu */
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
         // vừa bấm mà menu chưa đổi → chờ server (tối đa 1,5 giây)
         return this.clickSig.isEmpty() || !this.lastSig.equals(this.clickSig) || this.age >= 30;
      }
   }

   /** lạc sang menu khác (Auction, Your Items…): không bấm gì trong đó, đóng rồi mở lại /shop */
   private void wrongMenu(MinecraftClient var1, String var2) {
      this.logMenu(var1);
      if (++this.wrongMenus > 3) {
         this.end(var1, "3 lần mở ra menu không phải Quick Buy (" + var2 + ")");
      } else {
         AutoBuildMod.LOGGER.info("[AutoBuild] Shop tìm hàng: đang ở menu \"{}\" (không phải Quick Buy/Select Item) → không bấm gì, mở lại /{}", var2, this.cmd());
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
      } else if (this.menuReady(var1, GuiWatch.RETRY_TICKS)) {
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
                  AutoBuildMod.LOGGER.info("[AutoBuild] Shop tìm hàng: Quick Buy chưa có {} → bấm ô Empty {} để chọn vật phẩm", this.wantedName(), var4);
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
            // bấm ô Empty mà menu không đổi → quay lại Quick Buy thử lại
            this.step = ShopBrowser.Step.QUICK;
            this.clickSig = "";
            this.age = 0;
         } else if (!var2.contains("select item")) {
            this.wrongMenu(var1, var2);
         } else if (this.added) {
            // đã nhập số lượng mà server đưa về menu chọn vật phẩm → không chọn lại (tránh lặp), mở lại /shop xem Quick Buy
            this.close(var1);
            this.step = ShopBrowser.Step.OPEN;
            this.age = 0;
         } else {
            this.logMenu(var1);
            // trang đầu (chưa tìm): chỉ nhận đúng vật phẩm; kết quả tìm kiếm: nhận cả vật phẩm cùng họ
            int var3 = this.resultSlot(var1, this.searched);
            if (var3 >= 0) {
               ItemStack var4 = stack(var1, var3);
               this.picked = var4.getItem();
               this.pending = ShopBrowser.Sign.AMOUNT;
               AutoBuildMod.LOGGER.info("[AutoBuild] Shop tìm hàng: bấm {} (ô {}) → nhập số lượng {}", new Object[]{var4.getName().getString(), var3, this.amount()});
               this.status = "Chọn " + var4.getName().getString() + " → nhập số lượng " + this.amount();
               this.click(var1, var3);
               this.step = ShopBrowser.Step.SIGN;
            } else {
               if (this.searched) {
                  AutoBuildMod.LOGGER.info("[AutoBuild] Shop tìm hàng: tìm \"{}\" không thấy {}", this.names.get(this.nameIdx), this.wantedName());
                  this.nameIdx++;
                  this.searched = false;
                  if (this.nameIdx >= this.names.size()) {
                     this.end(var1, "/" + this.cmd() + " không có " + this.wantedName() + " (đã tìm " + this.names + ")");
                     return;
                  }
               }

               int var5 = this.searchSlot(var1);
               if (var5 < 0) {
                  this.end(var1, "không thấy nút Search trong menu chọn vật phẩm");
               } else if (++this.searchClicks > this.names.size() + 2) {
                  this.end(var1, "đã bấm Search " + (this.searchClicks - 1) + " lần mà chưa tìm được " + this.wantedName());
               } else {
                  this.pending = ShopBrowser.Sign.SEARCH;
                  AutoBuildMod.LOGGER.info("[AutoBuild] Shop tìm hàng: bấm Search (ô {}) → nhập \"{}\"", var5, this.names.get(this.nameIdx));
                  this.status = "Tìm \"" + this.names.get(this.nameIdx) + "\" trong danh sách vật phẩm";
                  this.click(var1, var5);
                  this.step = ShopBrowser.Step.SIGN;
               }
            }
         }
      }
   }

   private void signTick(MinecraftClient var1) {
      Screen var2 = var1.currentScreen;
      if (var2 instanceof AbstractSignEditScreen) {
         // chờ màn hình biển khởi tạo xong rồi mới ghi
         if (++this.signTicks >= 5) {
            ShopBrowser.Sign var3 = this.pending == null ? ShopBrowser.Sign.SEARCH : this.pending;
            String var4 = var3 == ShopBrowser.Sign.SEARCH ? this.names.get(this.nameIdx) : String.valueOf(this.amount());
            boolean var5 = writeSign(var2, var4);
            AutoBuildMod.LOGGER
               .info("[AutoBuild] Shop tìm hàng: ghi \"{}\" vào biển {} rồi bấm Done{}", new Object[]{var4, var3 == ShopBrowser.Sign.SEARCH ? "Search" : "Amount", var5 ? "" : " (gõ từng ký tự)"});
            if (var3 == ShopBrowser.Sign.SEARCH) {
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
               String var6 = title(var1);
               if (this.pending == ShopBrowser.Sign.AMOUNT && var6.contains("quick buy")) {
                  // server không hỏi số lượng mà đưa thẳng về Quick Buy
                  this.added = true;
                  this.pending = null;
                  this.step = ShopBrowser.Step.QUICK;
                  this.clickSig = "";
                  this.age = 0;
               } else if (this.age > 80) {
                  if (++this.signFails > 2) {
                     this.end(var1, "bấm mà không hiện biển nhập chữ (" + var6 + ")");
                  } else {
                     this.pending = null;
                     this.clickSig = "";
                     this.step = var6.contains("quick buy") ? ShopBrowser.Step.QUICK : ShopBrowser.Step.PICK;
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

   // ===== 3.0.122 (người dùng): vài món mua ở /shop hiện thêm menu xác nhận → bấm Confirm; món không cần thì mua thẳng như cũ =====
   private int confirmClicks;
   private int confirmWait;
   private String confirmSig = "";

   /** ô nút xác nhận: tên có "confirm"/"accept"/"yes"/"xác nhận" (không phải cancel); không có tên thì kính/len/bê tông xanh lá */
   static int confirmSlot(MinecraftClient var0) {
      int var1 = -1;

      for (int var2 = 0; var2 < topSize(var0); var2++) {
         ItemStack var3 = stack(var0, var2);
         if (!var3.isEmpty()) {
            String var4 = OrderRules.text(var3.getName().getString());
            if (!var4.contains("cancel") && !var4.contains("deny") && !var4.contains("decline") && !var4.contains("back") && !var4.contains("hủy") && !var4.contains("huỷ")) {
               if (var4.contains("confirm") || var4.contains("accept") || var4.equals("yes") || var4.startsWith("yes ") || var4.contains("xác nhận")) {
                  return var2;
               }

               String var5 = id(var3);
               if (var1 < 0 && (var5.startsWith("lime_") || var5.startsWith("green_")) && (var5.endsWith("glass_pane") || var5.endsWith("stained_glass") || var5.endsWith("wool") || var5.endsWith("concrete") || var5.endsWith("terracotta"))) {
                  var1 = var2;
               }
            }
         }
      }

      return var1;
   }

   /** true = đang ở menu xác nhận mua và đã/đang bấm Confirm (tick này không làm bước khác) */
   private boolean confirmTick(MinecraftClient var1) {
      if (this.step == ShopBrowser.Step.OPEN || !menuOpen(var1) || var1.player.currentScreenHandler.getCursorStack().isEmpty() == false) {
         return false;
      }

      String var2 = title(var1);
      if (var2.contains("quick buy") || var2.contains("select item")) {
         return false;
      }

      // chỉ sau khi đã bấm mua (WAIT_ITEM) hoặc menu ghi rõ là xác nhận
      if (this.step != ShopBrowser.Step.WAIT_ITEM && !var2.contains("confirm") && !var2.contains("xác nhận")) {
         return false;
      }

      int var3 = confirmSlot(var1);
      if (var3 < 0) {
         return false;
      }

      if (!this.settled(var1)) {
         return true;
      }

      if (this.lastSig.equals(this.confirmSig) && ++this.confirmWait < 30) {
         this.status = "Chờ server sau khi bấm Confirm";
         return true;
      }

      if (this.confirmClicks >= 3) {
         this.end(var1, "menu xác nhận \"" + var2 + "\" bấm Confirm 3 lần chưa xong");
         return true;
      }

      this.logMenu(var1);
      this.confirmClicks++;
      this.confirmWait = 0;
      this.confirmSig = this.lastSig;
      ItemStack var4 = stack(var1, var3);
      AutoBuildMod.LOGGER.info("[AutoBuild] Shop tìm hàng: menu xác nhận \"{}\" → bấm \"{}\" (ô {}, {}) để mua {}",
         new Object[]{var1.currentScreen.getTitle().getString(), var4.getName().getString(), var3, id(var4), this.wantedName()});
      this.status = "Bấm Confirm mua " + this.wantedName();
      var1.interactionManager.clickSlot(var1.player.currentScreenHandler.syncId, var3, 0, SlotActionType.PICKUP, var1.player);
      this.stable = 0;
      // sau Confirm: chờ hàng về như sau cú bấm mua
      this.step = ShopBrowser.Step.WAIT_ITEM;
      this.age = 0;
      return true;
   }

   private void waitItemTick(MinecraftClient var1, int var2) {
      if (var2 > this.clickHave) {
         // đã nhận hàng của lần bấm này → về Quick Buy, còn thiếu thì bấm tiếp
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
         // 3.0.95: vật phẩm vừa thêm vào Quick Buy chưa có giá ngay (log 18:55:50 thiếu giá, 18:55:55 mở lại thì có "$450").
         // Bản cũ thấy không có giá là bỏ luôn → thiếu Cobblestone. Giờ: chờ 2 giây, chưa có thì mở lại /shop (tối đa 2 lần).
         if (++this.priceWaits < 40) {
            this.status = "Chờ Quick Buy hiện giá " + var3.getName().getString();
         } else if (this.priceReopens < 2) {
            this.priceReopens++;
            this.priceWaits = 0;
            AutoBuildMod.LOGGER.info("[AutoBuild] Shop tìm hàng: {} trong Quick Buy chưa có giá → mở lại /{} xem giá (lần {})", new Object[]{var3.getName().getString(), this.cmd(), this.priceReopens});
            this.close(var1);
            this.opens = Math.max(0, this.opens - 1);
            this.step = ShopBrowser.Step.OPEN;
            this.age = 0;
         } else {
            this.end(var1, "thấy " + var3.getName().getString() + " trong Quick Buy nhưng mở lại 2 lần vẫn không có giá → không mua (an toàn)");
         }
      } else if (var4 > this.maxPrice) {
         this.end(var1, var3.getName().getString() + " giá $" + money(var4) + " > trần $" + money(this.maxPrice) + " → không mua");
      } else if (InventoryAccess.freeSlots(var1) <= this.config.reserveFreeSlots) {
         this.end(var1, "túi đồ đầy");
      } else if (++this.buyClicks > 64) {
         // mỗi lần bấm chỉ bấm tiếp sau khi hàng lần trước đã về túi; đủ số cần thì dừng ngay ở đầu tick
         // 3.0.125 (người dùng: "mua tới khi đủ"): bỏ giới hạn 6 lần (log: cần 476 Bone Meal, mua 384 rồi dừng). Chỉ dừng khi đủ,
         // túi đầy, giá > trần, hoặc bấm mua mà không nhận được hàng (hết tiền); 64 lần là chốt an toàn
         this.end(var1, "đã bấm mua 64 lần");
      } else {
         this.clickHave = this.have(var1);
         this.status = "Mua " + var3.getName().getString() + (var3.getCount() > 1 ? " x" + var3.getCount() : "") + " giá $" + money(var4);
         AutoBuildMod.LOGGER
            .info(
               "[AutoBuild] Shop tìm hàng: bấm mua {} x{} ở ô {} Quick Buy (giá ${} ≤ trần ${})",
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

   private static enum Step {
      OPEN,
      QUICK,
      PICK,
      SIGN,
      WAIT_ITEM;
   }

   private static enum Sign {
      SEARCH,
      AMOUNT;
   }
}
