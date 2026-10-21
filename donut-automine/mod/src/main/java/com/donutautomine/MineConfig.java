package com.donutautomine;

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

public final class MineConfig {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("donut-automine.json");
   public int configVersion = 3;
   public boolean customRegion = false;
   public boolean regionPlaced = false;
   public int regionSizeX = 65;
   public int regionSizeY = 27;
   public int regionSizeZ = 65;
   public int regionX;
   public int regionTopY;
   public int regionZ;
   public boolean showRegionOutline = true;
   public boolean hideSchematicContents = false;
   public List<Boolean> schematicBeforeHide = new ArrayList<>();
   public String waterDamRegion = "";
   public List<Long> waterDamCells = new ArrayList<>();
   public boolean showHud = true;
   public String pickaxeHint = "";
   public double reachMargin = 0.35;
   public boolean allowEntryShaft = true;
   public int maxEntryShaft = 12;
   public boolean disablePauseOnLostFocus = true;
   public double rotationMinSpeed = 16.0;
   public double rotationMaxSpeed = 30.0;
   public int overshootPercent = 10;
   public boolean ghostResync = true;
   public int resyncPerTick = 2;
   public boolean autoEat = true;
   public int eatAtHunger = 14;
   public int minHealth = 7;
   public boolean plugFluids = true;
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
         "minecraft:granite",
         "minecraft:tuff",
         "minecraft:blackstone",
         "minecraft:basalt",
         "minecraft:end_stone",
         "minecraft:calcite"
      )
   );
   public int keepFiller = 128;
   public boolean refillFillerFromOrder = true;
   public String orderCommand = "order";
   public int orderClickDelayTicks = 10;
   public int orderTimeoutTicks = 200;
   public boolean xpRepair = true;
   public int repairAt = 150;
   public int repairMissing = 0;
   public boolean buyXp = true;
   public int xpBuyAmount = 64;
   public long shopMaxPrice = 500000L;
   public String shopCommand = "shop";
   public int hardStopDurability = 20;
   public boolean autoSell = true;
   public String sellCommand = "sell";
   public int sellWhenFree = 2;
   public boolean dropJunkIfSellFails = true;
   public List<String> junkItems = new ArrayList<>(
      List.of(
         "minecraft:cobblestone",
         "minecraft:cobbled_deepslate",
         "minecraft:dirt",
         "minecraft:gravel",
         "minecraft:andesite",
         "minecraft:diorite",
         "minecraft:granite",
         "minecraft:tuff",
         "minecraft:netherrack",
         "minecraft:calcite",
         "minecraft:flint",
         "minecraft:sand",
         "minecraft:red_sand",
         "minecraft:sandstone",
         "minecraft:clay_ball",
         "minecraft:blackstone",
         "minecraft:basalt",
         "minecraft:smooth_basalt",
         "minecraft:dripstone_block",
         "minecraft:pointed_dripstone",
         "minecraft:moss_block",
         "minecraft:rooted_dirt"
      )
   );
   public int reserveFreeSlots = 1;
   public double freecamSpeed = 12.0;

   public static MineConfig load() {
      MineConfig var0 = null;

      try {
         if (Files.exists(FILE)) {
            var0 = (MineConfig)GSON.fromJson(Files.readString(FILE), MineConfig.class);
         }
      } catch (Exception var2) {
         AutoMineMod.LOGGER.warn("[AutoMine] Không đọc được cấu hình, dùng mặc định: {}", var2.toString());
      }

      if (var0 == null) {
         var0 = new MineConfig();
      }

      var0.clamp();
      var0.saveQuietly();
      return var0;
   }

   public void clamp() {
      this.configVersion = 3;
      if (this.schematicBeforeHide == null) {
         this.schematicBeforeHide = new ArrayList<>();
      }

      if (this.waterDamCells == null) {
         this.waterDamCells = new ArrayList<>();
      }

      this.regionSizeX = Math.max(1, Math.min(200, this.regionSizeX));
      this.regionSizeY = Math.max(1, Math.min(200, this.regionSizeY));
      this.regionSizeZ = Math.max(1, Math.min(200, this.regionSizeZ));
      if (this.fillerItems == null) {
         this.fillerItems = (new MineConfig()).fillerItems;
      }

      if (this.junkItems == null) {
         this.junkItems = (new MineConfig()).junkItems;
      }

      if (this.pickaxeHint == null) {
         this.pickaxeHint = "";
      }

      if (this.shopCommand == null || this.shopCommand.isBlank()) {
         this.shopCommand = "shop";
      }

      if (this.sellCommand == null || this.sellCommand.isBlank()) {
         this.sellCommand = "sell";
      }

      this.reachMargin = Math.max(0.05, Math.min(1.5, this.reachMargin));
      this.rotationMinSpeed = Math.max(4.0, Math.min(90.0, this.rotationMinSpeed));
      this.rotationMaxSpeed = Math.max(this.rotationMinSpeed, Math.min(120.0, this.rotationMaxSpeed));
      this.overshootPercent = Math.max(0, Math.min(60, this.overshootPercent));
      this.resyncPerTick = Math.max(1, Math.min(6, this.resyncPerTick));
      this.eatAtHunger = Math.max(1, Math.min(19, this.eatAtHunger));
      this.minHealth = Math.max(1, Math.min(19, this.minHealth));
      this.repairAt = Math.max(5, this.repairAt);
      this.repairMissing = Math.max(0, this.repairMissing);
      this.xpBuyAmount = Math.max(1, Math.min(64, this.xpBuyAmount));
      this.sellWhenFree = Math.max(0, Math.min(10, this.sellWhenFree));
      this.keepFiller = Math.max(64, Math.min(576, this.keepFiller));
      if (!this.fillerItems.contains("minecraft:cobblestone")) {
         this.fillerItems.add("minecraft:cobblestone");
      }

      if (this.orderCommand == null || !this.orderCommand.matches("/?[a-zA-Z0-9_ ]{1,40}")) {
         this.orderCommand = "order";
      }

      this.orderCommand = this.orderCommand.replaceFirst("^/", "");
      this.orderClickDelayTicks = Math.clamp((long)this.orderClickDelayTicks, 8, 100);
      this.orderTimeoutTicks = Math.clamp((long)this.orderTimeoutTicks, 100, 600);
      this.maxEntryShaft = Math.max(0, Math.min(64, this.maxEntryShaft));
      this.hardStopDurability = Math.max(1, this.hardStopDurability);
      this.reserveFreeSlots = Math.max(0, Math.min(6, this.reserveFreeSlots));
      this.freecamSpeed = Math.max(1.0, Math.min(80.0, this.freecamSpeed));
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
         AutoMineMod.LOGGER.warn("[AutoMine] Không lưu được cấu hình", var2);
      }
   }
}
