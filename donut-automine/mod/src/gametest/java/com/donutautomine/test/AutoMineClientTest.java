package com.donutautomine.test;

import com.donutautomine.AutoMineMod;
import com.donutautomine.mine.MineController;
import com.donutautomine.mine.Region;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestSingleplayerContext;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Kiểm thử trong Minecraft thật (Fabric client gametest, thế giới đơn):
 * A) đường hầm có hố 1 ô và 2 ô, trong túi có cobblestone → phải nhảy qua, không đặt khối vào hố;
 * B) cột 3 khối, khối giữa bị bedrock che khỏi chỗ đang đứng → vẫn phải chọn khối giữa (đi vòng 1 đoạn), không đập khối trên;
 * C) đào trọn một khung 5x3x5 từ trên mặt đất.
 */
public final class AutoMineClientTest implements FabricClientGameTest {
   static final List<String> RESULTS = new ArrayList<>();

   static Object get(Object o, String name) throws Exception {
      Field f = MineController.class.getDeclaredField(name);
      f.setAccessible(true);
      return f.get(o);
   }

   static void set(Object o, String name, Object v) throws Exception {
      Field f = MineController.class.getDeclaredField(name);
      f.setAccessible(true);
      f.set(o, v);
   }

   static Object field(Object o, String name) throws Exception {
      Field f = o.getClass().getDeclaredField(name);
      f.setAccessible(true);
      return f.get(o);
   }

   static Class<?> inner(String n) throws Exception {
      return Class.forName("com.donutautomine.mine.MineController$" + n);
   }

   static void running(MinecraftClient mc) throws Exception {
      MineController c = AutoMineMod.controller;
      Class<?> state = inner("State");
      set(c, "state", Enum.valueOf((Class)state, "RUNNING"));
      Class.forName("com.donutautomine.mine.BotInput").getMethod("install", MinecraftClient.class).invoke(null, mc);
   }

   static void check(boolean ok, String what) {
      String line = (ok ? "  ok  " : "  FAIL ") + what;
      RESULTS.add(line);
      AutoMineMod.LOGGER.info("[GameTest] {}", line);
   }

   @Override
   public void runTest(ClientGameTestContext ctx) {
      AutoMineMod.config.refillFillerFromOrder = false;
      AutoMineMod.config.autoSell = false;
      AutoMineMod.config.buyXp = false;
      AutoMineMod.config.plugFluids = false;
      try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
         sp.getClientWorld().waitForChunksRender();
         sp.getServer().runCommand("gamemode survival @a");
         sp.getServer().runCommand("gamerule doMobSpawning false");
         sp.getServer().runCommand("gamerule doDaylightCycle false");
         sp.getServer().runCommand("difficulty peaceful");
         sp.getServer().runCommand("give @a netherite_pickaxe");
         sp.getServer().runCommand("give @a cobblestone 64");
         ctx.waitTicks(10);
         ctx.runOnClient(mc -> AutoMineMod.controller.tools().choose(mc));
         this.parkour(ctx, sp);
         this.middle(ctx, sp);
         this.fullMine(ctx, sp);
      } catch (Exception e) {
         throw new RuntimeException(e);
      }

      long fails = RESULTS.stream().filter(s -> s.contains("FAIL")).count();
      AutoMineMod.LOGGER.info("[GameTest] KẾT QUẢ: {} đạt, {} hỏng\n{}", RESULTS.size() - fails, fails, String.join("\n", RESULTS));
      if (fails > 0) {
         throw new AssertionError(fails + " phép kiểm tra hỏng");
      }
   }

   void parkour(ClientGameTestContext ctx, TestSingleplayerContext sp) throws Exception {
      String[] cmds = {
         "fill -3 96 5 16 104 11 air",
         "fill -2 98 8 14 99 8 stone",
         "fill -2 100 7 14 101 7 stone",
         "fill -2 100 9 14 101 9 stone",
         "setblock 4 99 8 air",
         "fill 8 99 8 9 99 8 air",
         "tp @a 0.5 100 8.5 -90 0"
      };
      for (String c : cmds) {
         sp.getServer().runCommand(c);
      }

      ctx.waitTicks(20);
      List<String> labels = new ArrayList<>();
      ctx.runOnClient(mc -> {
         Constructor<?> k = inner("WalkTask").getDeclaredConstructor(MineController.class, BlockPos.class, double.class, boolean.class, String.class);
         k.setAccessible(true);
         Object walk = k.newInstance(AutoMineMod.controller, new BlockPos(12, 100, 8), 0.22, false, "gametest");
         set(AutoMineMod.controller, "task", walk);
         running(mc);
      });
      int t = ctx.waitFor(mc -> {
         try {
            Object task = get(AutoMineMod.controller, "task");
            if (task != null) {
               String l = AutoMineMod.controller.taskLabel();
               if (labels.isEmpty() || !labels.getLast().equals(l)) {
                  labels.add(l);
               }
            }

            return task == null;
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }, 600);
      ctx.runOnClient(mc -> AutoMineMod.controller.pause("gametest"));
      BlockPos feet = ctx.computeOnClient(mc -> BlockPos.ofFloored(mc.player.getX(), mc.player.getY() + 0.2, mc.player.getZ()));
      boolean holes = ctx.computeOnClient(
         mc -> mc.world.getBlockState(new BlockPos(4, 99, 8)).isAir()
            && mc.world.getBlockState(new BlockPos(8, 99, 8)).isAir()
            && mc.world.getBlockState(new BlockPos(9, 99, 8)).isAir()
      );
      AutoMineMod.LOGGER.info("[GameTest] A: {} tick, nhãn: {}", t, labels);
      check(feet.equals(new BlockPos(12, 100, 8)), "A: tới đích (12,100,8) qua 2 hố trong " + t + " tick, đang ở " + feet.toShortString());
      check(holes, "A: không đặt khối vào hố (cả 3 ô hố vẫn là không khí)");
      check(labels.stream().anyMatch(l -> l.contains("Nhảy qua khe 1")), "A: có nhảy qua khe 1 ô");
      check(labels.stream().anyMatch(l -> l.contains("Nhảy qua khe 2")), "A: có nhảy qua khe 2 ô");
   }

   void middle(ClientGameTestContext ctx, TestSingleplayerContext sp) throws Exception {
      String[] cmds = {
         "fill -3 96 14 16 106 28 air",
         "fill -2 99 15 12 99 26 stone",
         "fill 5 100 20 5 102 20 stone",
         "fill 4 100 20 4 101 20 bedrock",
         "tp @a 2.5 100 20.5 -90 0"
      };
      for (String c : cmds) {
         sp.getServer().runCommand(c);
      }

      ctx.waitTicks(20);
      Object[] pick = new Object[2];
      ctx.runOnClient(mc -> {
         MineController c = AutoMineMod.controller;
         set(c, "region", new Region("gametest", -2, 100, 15, 12, 102, 26));
         set(c, "layer", 0);
         Method m = MineController.class.getDeclaredMethod("chooseStrike", List.class, String.class, boolean.class);
         m.setAccessible(true);
         Object strike = m.invoke(c, List.of(new BlockPos(5, 100, 20), new BlockPos(5, 101, 20), new BlockPos(5, 102, 20)), "gametest", false);
         pick[0] = strike == null ? null : field(strike, "c");
         pick[1] = strike == null ? null : field(strike, "f");
         if (strike != null) {
            set(c, "task", strike);
            running(mc);
         }
      });
      AutoMineMod.LOGGER.info("[GameTest] B: chọn {} mặt {}", pick[0], pick[1]);
      check(new BlockPos(5, 101, 20).equals(pick[0]) && pick[1] != null && ((Direction)pick[1]).getAxis() != Direction.Axis.Y,
         "B: cột 3 khối bị che khối giữa → vẫn chọn khối giữa mặt ngang: " + pick[0] + " " + pick[1]);
      int t = ctx.waitFor(mc -> {
         try {
            return get(AutoMineMod.controller, "task") == null;
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }, 600);
      ctx.runOnClient(mc -> {
         AutoMineMod.controller.pause("gametest");
         set(AutoMineMod.controller, "region", null);
         set(AutoMineMod.controller, "layer", -1);
      });
      boolean broke = ctx.computeOnClient(mc -> mc.world.getBlockState(new BlockPos(5, 101, 20)).isAir());
      check(broke, "B: đi tới chỗ đứng và đập vỡ khối giữa trong " + t + " tick");
   }

   void fullMine(ClientGameTestContext ctx, TestSingleplayerContext sp) throws Exception {
      String[] cmds = {
         "fill 20 88 20 34 110 34 air",
         "fill 20 88 20 34 96 34 stone",
         "tp @a 27.5 97 27.5 0 0"
      };
      for (String c : cmds) {
         sp.getServer().runCommand(c);
      }

      ctx.waitTicks(20);
      ctx.runOnClient(mc -> {
         AutoMineMod.config.customRegion = true;
         AutoMineMod.config.regionPlaced = true;
         AutoMineMod.config.regionX = 25;
         AutoMineMod.config.regionTopY = 96;
         AutoMineMod.config.regionZ = 25;
         AutoMineMod.config.regionSizeX = 5;
         AutoMineMod.config.regionSizeY = 3;
         AutoMineMod.config.regionSizeZ = 5;
         set(AutoMineMod.controller, "region", null);
         AutoMineMod.controller.resetProgress();
         AutoMineMod.controller.start();
      });
      int t = ctx.waitFor(mc -> AutoMineMod.controller.state() == MineController.State.DONE || !AutoMineMod.controller.running(), 6000);
      String status = ctx.computeOnClient(mc -> AutoMineMod.controller.status());
      int left = ctx.computeOnClient(mc -> {
         int n = 0;
         for (BlockPos p : BlockPos.iterate(25, 94, 25, 29, 96, 29)) {
            if (!mc.world.getBlockState(p).isAir()) {
               n++;
            }
         }

         return n;
      });
      int placed = ctx.computeOnClient(mc -> {
         int n = 0;
         for (BlockPos p : BlockPos.iterate(20, 88, 20, 34, 110, 34)) {
            if (mc.world.getBlockState(p).isOf(Blocks.COBBLESTONE)) {
               n++;
            }
         }

         return n;
      });
      ctx.runOnClient(mc -> AutoMineMod.controller.pause("gametest"));
      AutoMineMod.LOGGER.info("[GameTest] C: {} tick, trạng thái {}, còn {} ô, cobble đặt {}", t, status, left, placed);
      check(left == 0, "C: đào sạch khung 5x3x5 trong " + t + " tick (còn " + left + " ô, trạng thái: " + status + ")");
   }
}
