package com.donutautobuild;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

public final class BuildConfig {
   public double reach = 4.25;
   public int placeDelayTicks = 1;
   public int randomDelayTicks = 0;
   public double rotationMinSpeed = 20.0;
   public double rotationMaxSpeed = 40.0;
   public int overshootPercent = 15;
   public int reactionMinTicks = 0;
   public int reactionMaxTicks = 0;
   public double walkTurnSpeed = 25.0;
   public boolean layerMode = true;
   public boolean rowMode = true;
   public boolean skipLayerWhenMissing = false;
   public boolean syncLitematicaLayer = true;
   public boolean litematicaLayerOwned = false;
   public boolean layerFilterRepairPending = false;
   public String buildMode = "EXACT";
   public boolean exactBlocks = true;
   public boolean fixWrong = true;
   public boolean disableEasyPlace = true;
   public List<String> fillerItems = new ArrayList<>(
      List.of(
         "minecraft:cobblestone",
         "minecraft:cobbled_deepslate",
         "minecraft:stone",
         "minecraft:deepslate",
         "minecraft:netherrack",
         "minecraft:dirt",
         "minecraft:andesite",
         "minecraft:diorite",
         "minecraft:granite"
      )
   );
   public String fillerOrderItem = "minecraft:cobblestone";
   public Integer yStart = null;
   public Integer yEnd = null;
   public boolean collectFromOrders = true;
   public String orderCommand = "order";
   public boolean buyBuckets = true;
   public String shopCommand = "shop";
   public int orderClickDelayTicks = 6;
   public int orderTimeoutTicks = 200;
   public int orderRecheckSeconds = 180;
   public int materialMaxStacks = 9;
   public int reserveFreeSlots = 2;
   public int workingHotbarSlot = 7;
   public int minHealth = 6;
   public boolean showHud = true;
   public boolean showBlockedBoxes = true;
   public boolean scaffold = true;
   public boolean bridge = false;
   public boolean sweepScaffold = true;
   public String rotationSpoof = "AUTO";
   public String scaffoldItem = "minecraft:cobblestone";
   // 3.0.88: /sethome + /home N để lên lại tầng đang xây khi lỡ ngã (DonutSMP)
   public boolean useHome = true;
   public String homeCommand = "home";
   public String setHomeCommand = "sethome";
   public int homeMinLayer = 3;
   public int ownHomeNumber = 0;
   public String ownHomeWorld = "";
   public double ownHomeX;
   public double ownHomeY;
   public double ownHomeZ;
   public int ownHomeLayer = Integer.MIN_VALUE;
   // 3.0.99: home do NGƯỜI DÙNG chỉ định trong GUI (tab Home) → mod dùng nhưng không bao giờ xóa
   public boolean ownHomeManual;
   // 3.0.99: mod tự /sethome khi lên tầng mới (tắt = chỉ dùng home người dùng chọn)
   public boolean autoSetHome = true;
   // 3.0.123 (người dùng): home dành riêng cho mod, chọn ở tab Cài đặt. 0 = không dùng: mod không bao giờ /home, /sethome.
   // N > 0: home trống người dùng giao cho mod tự xử — từ tầng homeMinLayer mỗi tầng mod xoá home N cũ rồi /sethome N, ngã thì /home N.
   public int modHomeNumber = 0;
   // 3.0.90: tự chỉnh crafter (tắt ô) ngay sau khi đặt; cấu hình theo tầng: khóa "tên placement@gốc|Y" → bit ô bị tắt
   public boolean configureCrafters = true;
   // 3.0.91: Order không có hàng → tìm mua ở /shop, không mua món có giá > shopMaxPrice
   public boolean shopFallback = true;
   public long shopMaxPrice = 500000L;
   // 3.0.95: ender chest làm kho tạm (cất vật liệu tầng sau không dùng, tìm trong đó trước khi mở Order/Shop)
   public boolean useEnderChest = true;
   // 3.0.95: không ném xô rỗng (hopper hút làm hỏng farm) → gộp lại, đủ sellBucketsAt cái hoặc túi đầy thì /sell
   public boolean autoSell = true;
   public boolean sellJunk = true;
   public String sellCommand = "sell";
   public int sellBucketsAt = 10;
   // 3.0.98: tổng tiền đã bỏ ra khi xây (đọc từ chat "You bought … for $ …" lúc mod đang chạy); chỉ về 0 khi bấm nút Reset
   public double moneySpent;
   public double moneyEarned;
   public int moneyBuys;
   public long moneySince;
   public java.util.Map<String, Integer> crafterLayerMasks = new java.util.HashMap<>();
   /** 3.0.125: khối chứa đã cho đủ đồ theo bản vẽ (placement@gốc|x, y, z) → vào lại game không mở kiểm tra lại */
   public java.util.Set<String> filledContainers = new java.util.HashSet<>();
   /** 3.0.128 (người dùng): có cho đồ vào hopper theo bản vẽ không — nhiều farm không cần hopper có đúng đồ như bản vẽ */
   public boolean fillHoppers = true;
   /** 3.0.128: như trên cho rương, thùng, dropper, dispenser, lò, giá pha chế */
   public boolean fillContainers = true;
   public int configVersion;
   private static final int CURRENT_VERSION = 11;
   public int maxScaffoldBlocks = 2048;
   public int maxBridgeBlocks = 8;
   public int pathNodeLimit = 20000;
   public int pathRange = 64;
   public static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("donut-autobuild.json");
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

   public BuildConfig() {
   }

   public static BuildConfig load() {
      BuildConfig var0 = new BuildConfig();

      try {
         if (Files.exists(FILE)) {
            var0 = (BuildConfig)GSON.fromJson(Files.readString(FILE), BuildConfig.class);
         }

         if (var0 == null) {
            var0 = new BuildConfig();
         }

         if (var0.configVersion < 6) {
            BuildConfig var1 = new BuildConfig();
            var0.placeDelayTicks = var1.placeDelayTicks;
            var0.randomDelayTicks = var1.randomDelayTicks;
            var0.rotationMinSpeed = var1.rotationMinSpeed;
            var0.rotationMaxSpeed = var1.rotationMaxSpeed;
            var0.overshootPercent = var1.overshootPercent;
            var0.reactionMinTicks = var1.reactionMinTicks;
            var0.reactionMaxTicks = var1.reactionMaxTicks;
            var0.walkTurnSpeed = var1.walkTurnSpeed;
            var0.syncLitematicaLayer = true;
            var0.pathNodeLimit = Math.max(var0.pathNodeLimit, var1.pathNodeLimit);
            var0.maxScaffoldBlocks = Math.max(var0.maxScaffoldBlocks, var1.maxScaffoldBlocks);
            var0.configVersion = 6;
         }

         if (var0.configVersion < 8 && "OFF".equals(var0.rotationSpoof)) {
            var0.rotationSpoof = "AUTO";
         }

         if (var0.configVersion < 8) {
            var0.litematicaLayerOwned = false;
            var0.layerFilterRepairPending = true;
            var0.configVersion = 8;
         }

         if (var0.configVersion < 9) {
            // 3.0.96: xây nhanh hơn — nghỉ giữa 2 lần đặt tối đa 4 tick (bằng nhịp giữ chuột phải của game)
            var0.placeDelayTicks = Math.min(var0.placeDelayTicks, 4);
            var0.configVersion = 9;
         }

         if (var0.configVersion < 10) {
            // 3.0.123: home cũ do mod tự chọn số (có thể trùng home của người chơi) → bỏ; mặc định "không dùng home" tới khi người dùng chọn
            var0.modHomeNumber = 0;
            var0.ownHomeNumber = 0;
            var0.ownHomeManual = false;
            var0.ownHomeLayer = Integer.MIN_VALUE;
            var0.configVersion = 10;
         }

         if (var0.configVersion < 11) {
            // 3.0.124 (người dùng): xô nước/lava/tuyết bột mặc định mua ở /shop; Order trống thì luôn thử /shop
            var0.buyBuckets = true;
            var0.shopFallback = true;
            var0.configVersion = 11;
         }

         var0.clamp();
         var0.save();
      } catch (Exception var2) {
         AutoBuildMod.LOGGER.warn("Cannot read Auto Build configuration", var2);
      }

      var0.clamp();
      return var0;
   }

   private static double finite(double var0, double var2, double var4, double var6) {
      return Double.isFinite(var0) ? Math.clamp(var0, var4, var6) : var2;
   }

   public void clamp() {
      if (this.crafterLayerMasks == null) {
         this.crafterLayerMasks = new java.util.HashMap<>();
      }

      if (this.filledContainers == null) {
         this.filledContainers = new java.util.HashSet<>();
      }

      this.reach = finite(this.reach, 4.25, 2.0, 4.5);
      this.placeDelayTicks = Math.clamp((long)this.placeDelayTicks, 0, 100);
      this.randomDelayTicks = Math.clamp((long)this.randomDelayTicks, 0, 40);
      this.rotationMinSpeed = finite(this.rotationMinSpeed, 20.0, 1.0, 90.0);
      this.rotationMaxSpeed = finite(this.rotationMaxSpeed, 40.0, this.rotationMinSpeed, 120.0);
      this.overshootPercent = Math.clamp((long)this.overshootPercent, 0, 100);
      this.reactionMinTicks = Math.clamp((long)this.reactionMinTicks, 0, 40);
      this.reactionMaxTicks = Math.clamp((long)this.reactionMaxTicks, this.reactionMinTicks, 60);
      this.walkTurnSpeed = finite(this.walkTurnSpeed, 25.0, 2.0, 90.0);
      this.orderClickDelayTicks = Math.clamp((long)this.orderClickDelayTicks, 3, 100);
      this.orderTimeoutTicks = Math.clamp((long)this.orderTimeoutTicks, 60, 1200);
      this.orderRecheckSeconds = Math.clamp((long)this.orderRecheckSeconds, 15, 3600);
      this.sellBucketsAt = Math.clamp((long)this.sellBucketsAt, 2, 64);
      if (this.sellCommand == null || this.sellCommand.isBlank()) {
         this.sellCommand = "sell";
      }
      this.materialMaxStacks = Math.clamp((long)this.materialMaxStacks, 1, 27);
      this.reserveFreeSlots = Math.clamp((long)this.reserveFreeSlots, 1, 20);
      this.workingHotbarSlot = Math.clamp((long)this.workingHotbarSlot, 0, 8);
      this.modHomeNumber = Math.clamp((long)this.modHomeNumber, 0, 54);
      this.useHome = this.modHomeNumber > 0;
      this.minHealth = Math.clamp((long)this.minHealth, 1, 19);
      this.maxScaffoldBlocks = Math.clamp((long)this.maxScaffoldBlocks, 0, 8192);
      this.maxBridgeBlocks = Math.clamp((long)this.maxBridgeBlocks, 0, 64);
      this.pathNodeLimit = Math.clamp((long)this.pathNodeLimit, 1000, 40000);
      this.pathRange = Math.clamp((long)this.pathRange, 16, 96);
      if (this.yStart != null && this.yEnd != null && this.yStart > this.yEnd) {
         Integer var1 = this.yStart;
         this.yStart = this.yEnd;
         this.yEnd = var1;
      }

      if (this.scaffoldItem == null) {
         this.scaffoldItem = "minecraft:cobblestone";
      }

      if (!"FAST".equals(this.buildMode)) {
         this.buildMode = "EXACT";
      }

      if (!"ON".equals(this.rotationSpoof) && !"AUTO".equals(this.rotationSpoof)) {
         this.rotationSpoof = "OFF";
      }

      if (this.fillerItems == null || this.fillerItems.isEmpty()) {
         this.fillerItems = new ArrayList<>(List.of("minecraft:cobblestone"));
      }

      if (this.fillerOrderItem == null || this.fillerOrderItem.isBlank()) {
         this.fillerOrderItem = "minecraft:cobblestone";
      }

      if (this.orderCommand == null || !this.orderCommand.matches("[a-zA-Z0-9_ ]{1,40}")) {
         this.orderCommand = "order";
      }

      if (this.shopCommand == null || !this.shopCommand.matches("[a-zA-Z0-9_ ]{1,40}")) {
         this.shopCommand = "shop";
      }
   }

   public boolean fastMode() {
      return "FAST".equals(this.buildMode);
   }

   public boolean inYRange(int var1) {
      return (this.yStart == null || var1 >= this.yStart) && (this.yEnd == null || var1 <= this.yEnd);
   }

   public void save() throws IOException {
      this.clamp();
      Files.createDirectories(FILE.getParent());
      Path var1 = FILE.resolveSibling(FILE.getFileName() + ".tmp");
      Files.writeString(var1, GSON.toJson(this));

      try {
         Files.move(var1, FILE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException var3) {
         Files.move(var1, FILE, StandardCopyOption.REPLACE_EXISTING);
      }
   }

   public void saveQuietly() {
      try {
         this.save();
      } catch (IOException var2) {
         AutoBuildMod.LOGGER.warn("Cannot save Auto Build configuration", var2);
      }
   }
}
