package com.donutautobuild.build;

import com.donutautobuild.AutoBuildMod;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;

public final class SessionStore {
   private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("donut-autobuild-session.json");

   private SessionStore() {
   }

   public static void save(String var0, String var1, BlockPos var2, Integer var3, Integer var4, Integer var5) {
      try {
         JsonObject var6 = new JsonObject();
         var6.addProperty("world", var0);
         var6.addProperty("placement", var1);
         var6.addProperty("x", var2.getX());
         var6.addProperty("y", var2.getY());
         var6.addProperty("z", var2.getZ());
         if (var3 != null) {
            var6.addProperty("yStart", var3);
         }

         if (var4 != null) {
            var6.addProperty("yEnd", var4);
         }

         if (var5 != null) {
            var6.addProperty("layer", var5);
         }

         var6.addProperty("savedAt", System.currentTimeMillis());
         Files.createDirectories(FILE.getParent());
         Files.writeString(FILE, new GsonBuilder().setPrettyPrinting().create().toJson(var6));
      } catch (Exception var7) {
         AutoBuildMod.LOGGER.warn("Cannot save Auto Build session", var7);
      }
   }

   public static SessionStore.Session load() {
      try {
         if (!Files.exists(FILE)) {
            return null;
         } else {
            JsonObject var0 = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
            return new SessionStore.Session(
               var0.get("world").getAsString(),
               var0.get("placement").getAsString(),
               new BlockPos(var0.get("x").getAsInt(), var0.get("y").getAsInt(), var0.get("z").getAsInt()),
               var0.has("yStart") ? var0.get("yStart").getAsInt() : null,
               var0.has("yEnd") ? var0.get("yEnd").getAsInt() : null,
               var0.has("layer") ? var0.get("layer").getAsInt() : null,
               var0.has("savedAt") ? var0.get("savedAt").getAsLong() : 0L
            );
         }
      } catch (Exception var1) {
         AutoBuildMod.LOGGER.warn("Cannot read Auto Build session", var1);
         return null;
      }
   }

   public static record Session(String world, String placement, BlockPos origin, Integer yStart, Integer yEnd, Integer layer, long savedAt) {
   }
}
