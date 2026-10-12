package com.donutautobuild.build;

import com.donutautobuild.AutoBuildMod;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

public final class ScaffoldJournal {
   public final Map<BlockPos, String> placed = new LinkedHashMap<>();
   private final String world;
   private final Path file = FabricLoader.getInstance().getConfigDir().resolve("donut-autobuild-scaffolds.json");

   public ScaffoldJournal(MinecraftClient var1) {
      this.world = worldKey(var1);

      try {
         if (Files.exists(this.file)) {
            JsonObject var2 = JsonParser.parseString(Files.readString(this.file)).getAsJsonObject();
            if (var2.has(this.world)) {
               for (JsonElement var4 : var2.getAsJsonArray(this.world)) {
                  JsonObject var5 = var4.getAsJsonObject();
                  this.placed.put(new BlockPos(var5.get("x").getAsInt(), var5.get("y").getAsInt(), var5.get("z").getAsInt()), var5.get("block").getAsString());
               }
            }
         }
      } catch (Exception var6) {
         AutoBuildMod.LOGGER.warn("Cannot load scaffold journal", var6);
      }
   }

   public static String worldKey(MinecraftClient var0) {
      String var1 = var0.getCurrentServerEntry() != null
         ? var0.getCurrentServerEntry().address
         : (var0.getServer() != null ? var0.getServer().getSaveProperties().getLevelName() : "unknown");
      return var1 + "|" + var0.world.getRegistryKey().getValue();
   }

   public int prune(MinecraftClient var1) {
      int var2 = 0;
      Iterator var3 = this.placed.entrySet().iterator();

      while (var3.hasNext()) {
         Entry var4 = (Entry)var3.next();
         if (var1.world.isChunkLoaded((BlockPos)var4.getKey())) {
            String var5 = Registries.BLOCK.getId(var1.world.getBlockState((BlockPos)var4.getKey()).getBlock()).toString();
            if (!var5.equals(var4.getValue())) {
               var3.remove();
               var2++;
            }
         }
      }

      if (var2 > 0) {
         this.save();
      }

      return var2;
   }

   public void add(BlockPos var1, BlockState var2) {
      this.placed.put(var1.toImmutable(), Registries.BLOCK.getId(var2.getBlock()).toString());
      AutoBuildMod.LOGGER.info("[AutoBuild] Ghi nhớ khối tạm @ {}", var1.toShortString());
      this.save();
   }

   public void remove(BlockPos var1) {
      if (this.placed.remove(var1) != null) {
         AutoBuildMod.LOGGER.info("[AutoBuild] Đã thu hồi khối tạm @ {}", var1.toShortString());
      }

      this.save();
   }

   private void save() {
      try {
         JsonObject var1 = Files.exists(this.file) ? JsonParser.parseString(Files.readString(this.file)).getAsJsonObject() : new JsonObject();
         JsonArray var2 = new JsonArray();

         for (Entry var4 : this.placed.entrySet()) {
            JsonObject var5 = new JsonObject();
            BlockPos var6 = (BlockPos)var4.getKey();
            var5.addProperty("x", var6.getX());
            var5.addProperty("y", var6.getY());
            var5.addProperty("z", var6.getZ());
            var5.addProperty("block", (String)var4.getValue());
            var2.add(var5);
         }

         var1.add(this.world, var2);
         Path var9 = this.file.resolveSibling(this.file.getFileName() + ".tmp");
         Files.writeString(var9, new GsonBuilder().setPrettyPrinting().create().toJson(var1));

         try {
            Files.move(var9, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
         } catch (AtomicMoveNotSupportedException var7) {
            Files.move(var9, this.file, StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (Exception var8) {
         AutoBuildMod.LOGGER.error("Cannot save scaffold journal", var8);
      }
   }
}
