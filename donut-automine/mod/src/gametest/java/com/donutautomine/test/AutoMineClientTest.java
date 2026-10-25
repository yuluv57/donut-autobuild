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
 * C) đào trọn một khung 5x3x5 từ trên mặt đất;
 * D) khung 50x3x50 đã đào xong, chỉ còn 6 khối sót rải ở xa → phải đi tới đào hết, không đứng im;
 * E) như log người dùng: khung 67x65 (quá 3000 ô bộ tìm đường cũ duyệt được), 6 ô sót sát mép, nhân vật ở đầu kia;
 * F) đường hầm bị 12 khối đá chặn → vừa đi vừa đào liền, không dừng chờ sau mỗi nhát;
 * G) đi chéo 12x12 ô trên sàn trống → đi thẳng đường chéo, không đi hình chữ L;
 * G2) đi chéo khi ngay cạnh đường chéo có một dãy hố (ô vuông góc của mỗi bước chéo là hố) → vẫn đi chéo như AutoBuild;
 * H) hành lang có rãnh sâu 1 ô rộng 4 ô (không nhảy qua được), đi trong tầng → xuống rãnh rồi bước lên, không bắc cầu;
 * I) đào trọn khung 15x6x15 (2 tầng) có lẫn sỏi (rơi xuống chỗ vừa đào) bằng cúp 3x3;
 * J) như ảnh người dùng: tầng đã đào, rãnh 1 ô sâu 1 ô cắt ngang sàn, khối sót bên kia, nhân vật đứng sát mép rãnh → phải nhảy qua đào hết;
 * J2) rãnh 1 ô sâu 1 ô cắt ngang sàn của tầng CHƯA đào → đào hàng băng qua rãnh (nhảy qua, không lấp, không ngồi im);
 * J3) rãnh chạy dọc làn bên của hàng → đi dọc mép rãnh vẫn đập 3x3; J4) rãnh cắt ngang lối vào khung → nhảy qua tới đầu hàng;
 * K/K2) khối giữa lát bị server trả lại 2 / 6 lần → thang đổi góc (lùi, lùi, trái, phải, ngồi), không đập khối xung quanh, quá 5 lần để cuối tầng;
 * K3) 3x3 vỡ nhưng riêng khối giữa hiện lại 3 lần; K4) 2 khối giữa hiện lại, khối gần che khối xa;
 * K5) sỏi rơi xuống nằm đúng chỗ khối giữa (khối dưới bị 3x3 bỏ sót) như ảnh "lần 0 · đập"; K6) khối không bao giờ phá được → không tự dừng;
 * K7) đang đào bị ép "làm lại vì kẹt" 3 kiểu → vẫn đào sạch; K8) bedrock giữa hàng che kín lát sau → đi vòng qua làn bên;
 * L1) hố sâu 2 ô giữa sàn tầng đã đào → đi vòng, không bắc cầu; L2) đứng dưới đáy hố sâu 2 ô → ra bằng đi bộ / đào bậc, không kê cột.
 */
public final class AutoMineClientTest implements FabricClientGameTest {
   static final List<String> RESULTS = new ArrayList<>();
   /** ca nào ghi log mỗi tick (đặt bằng -Ddonutautomine.trace=A,F) */
   static final String TRACE = System.getProperty("donutautomine.trace", "");

   static {
      MineController.debugTrace = !TRACE.isEmpty();
   }
   static final String ONLY = System.getProperty("donutautomine.only", "");

   static boolean on(String tag) {
      return ONLY.isEmpty() || List.of(ONLY.split(",")).contains(tag);
   }

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

   /** chờ tới khi điều kiện đúng (tối đa max tick), mỗi giây ghi vị trí/trạng thái để xem lại khi hỏng; trả số tick, -1 nếu hết giờ */
   static int until(ClientGameTestContext ctx, String tag, java.util.function.Predicate<MinecraftClient> pred, int max) {
      // đếm theo tuổi nhân vật (tăng đúng 1 mỗi tick game), không theo số lần chờ của luồng kiểm thử
      int start = ctx.computeOnClient(mc -> mc.player.age);
      for (int i = 0; i < max * 3; i++) {
         int t = ctx.computeOnClient(mc -> mc.player.age) - start;
         if (t >= max) {
            break;
         }

         if (ctx.computeOnClient(pred::test)) {
            return t;
         }

         if (t % 20 == 0 || TRACE.contains(tag)) {
            String line = ctx.computeOnClient(mc -> String.format(java.util.Locale.ROOT, "%.2f %.2f %.2f v=%.3f ground=%s sneak=%s sprint=%s | %s | %s",
               mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getVelocity().horizontalLength(), mc.player.isOnGround(), mc.player.isSneaking(),
               mc.player.isSprinting(), AutoMineMod.controller.status(), AutoMineMod.controller.taskLabel())
               + (TRACE.contains(tag) ? " || " + AutoMineMod.controller.debugLine : ""));
            AutoMineMod.LOGGER.info("[GameTest] {} t={}: {}", tag, t, line);
         }

         ctx.waitTick();
      }

      return -1;
   }

   static void inventory(ClientGameTestContext ctx, String tag) {
      String inv = ctx.computeOnClient(mc -> {
         StringBuilder b = new StringBuilder("slot=" + mc.player.getInventory().selectedSlot + ":");
         for (int i = 0; i < 36; i++) {
            if (!mc.player.getInventory().getStack(i).isEmpty()) {
               b.append(' ').append(i).append('=').append(mc.player.getInventory().getStack(i));
            }
         }

         return b.toString();
      });
      AutoMineMod.LOGGER.info("[GameTest] túi đồ sau {}: {}", tag, inv);
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
         // cúp 3x3 giả (Fake3x3Pickaxe) có Hiệu suất V, giống cúp đào của DonutSMP
         sp.getServer().runCommand("give @a netherite_pickaxe[minecraft:custom_name='\"3x3\"',minecraft:enchantments={levels:{\"minecraft:efficiency\":5}}]");
         sp.getServer().runCommand("give @a cobblestone 64");
         // chờ túi đồ phía client nhận cúp (đồng bộ chậm hơn lệnh /give vài tick)
         ctx.waitFor(mc -> mc.player.getMainHandStack().getName().getString().equals("3x3"), 200);
         ctx.runOnClient(mc -> AutoMineMod.controller.tools().choose(mc));
         if (on("A")) {
            this.parkour(ctx, sp);
            inventory(ctx, "A");
         }

         if (on("B")) {
            this.middle(ctx, sp);
            inventory(ctx, "B");
         }

         if (on("C")) {
            this.fullMine(ctx, sp);
            inventory(ctx, "C");
         }

         if (on("D")) {
            this.farLeftovers(ctx, sp);
            inventory(ctx, "D");
         }

         if (on("E")) {
            this.userLeftovers(ctx, sp);
            inventory(ctx, "E");
         }

         if (on("F")) {
            this.tunnel(ctx, sp);
            inventory(ctx, "F");
         }

         if (on("G")) {
            this.diagonal(ctx, sp);
            inventory(ctx, "G");
         }

         if (on("G2")) {
            this.diagonalHoles(ctx, sp);
            inventory(ctx, "G2");
         }

         if (on("H")) {
            this.dip(ctx, sp);
            inventory(ctx, "H");
         }

         if (on("I")) {
            this.bigMine(ctx, sp);
            inventory(ctx, "I");
         }

         if (on("J")) {
            this.trenchMined(ctx, sp);
            inventory(ctx, "J");
         }

         if (on("J2")) {
            this.trenchRows(ctx, sp);
            inventory(ctx, "J2");
         }

         if (on("J3")) {
            this.trenchAlong(ctx, sp);
            inventory(ctx, "J3");
         }

         if (on("J4")) {
            this.trenchEntry(ctx, sp);
            inventory(ctx, "J4");
         }

         if (on("K")) {
            this.revertRow(ctx, sp, "K", 440, 2);
         }

         if (on("K2")) {
            this.revertRow(ctx, sp, "K2", 460, 6);
         }

         if (on("K3")) {
            this.revertRow(ctx, sp, "K3", 480, 3, true);
         }

         if (on("K4")) {
            this.hiddenMiddle(ctx, sp);
         }

         if (on("K5")) {
            this.gravelMiddle(ctx, sp);
         }

         if (on("K6")) {
            this.neverStop(ctx, sp);
         }

         if (on("K7")) {
            this.forcedRecover(ctx, sp);
         }

         if (on("K8")) {
            this.detour(ctx, sp);
         }

         if (on("L1")) {
            this.deepHole(ctx, sp);
         }

         if (on("L2")) {
            this.pitEscape(ctx, sp);
         }

         if (on("M1")) {
            this.resumeRow(ctx, sp);
         }
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
      int t = until(ctx, "A", mc -> {
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
      int t = until(ctx, "B", mc -> {
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
      int t = until(ctx, "C", mc -> AutoMineMod.controller.state() == MineController.State.DONE || !AutoMineMod.controller.running(), 6000);
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

   void farLeftovers(ClientGameTestContext ctx, TestSingleplayerContext sp) throws Exception {
      int[][] blocks = {{145, 100, 145}, {145, 101, 145}, {140, 102, 130}, {120, 100, 146}, {147, 101, 110}, {130, 100, 140}};
      this.leftovers(ctx, sp, "D", 100, 100, 50, 50, blocks, 101, 101, 4000);
   }

   /** E) như log người dùng 21:47: khung 67x65, 6 ô sót ở hàng trên cùng sát mép z cuối, nhân vật ở đầu kia cách ~55 ô */
   void userLeftovers(ClientGameTestContext ctx, TestSingleplayerContext sp) throws Exception {
      int x0 = 200;
      int z0 = 300;
      int z1 = z0 + 64;
      int[][] blocks = {
         {x0 + 27, 102, z1 - 1}, {x0 + 33, 102, z1 - 4}, {x0 + 27, 102, z1}, {x0 + 33, 102, z1 - 3}, {x0 + 31, 102, z1 - 1}, {x0 + 31, 102, z1}
      };
      this.leftovers(ctx, sp, "E", x0, z0, 67, 65, blocks, x0 + 33, z0 + 8, 4000);
   }

   void leftovers(ClientGameTestContext ctx, TestSingleplayerContext sp, String tag, int x0, int z0, int sx, int sz, int[][] blocks, int px, int pz, int max)
      throws Exception {
      int x1 = x0 + sx - 1;
      int z1 = z0 + sz - 1;
      sp.getServer().runCommand("effect give @a resistance infinite 4 true");
      sp.getServer().runCommand("effect give @a slow_falling 8 0 true");
      sp.getServer().runCommand("tp @a " + (x0 + sx / 2) + ".5 120 " + (z0 + sz / 2) + ".5 0 0");
      ctx.waitTicks(60);
      sp.getClientWorld().waitForChunksDownload();
      List<String> cmds = new ArrayList<>(List.of(
         "fill " + (x0 - 2) + " 99 " + (z0 - 2) + " " + (x1 + 2) + " 99 " + (z1 + 2) + " stone",
         "fill " + (x0 - 2) + " 100 " + (z0 - 2) + " " + (x1 + 2) + " 104 " + (z1 + 2) + " air",
         "fill " + (x0 - 1) + " 100 " + (z0 - 1) + " " + (x1 + 1) + " 102 " + (z0 - 1) + " stone",
         "fill " + (x0 - 1) + " 100 " + (z1 + 1) + " " + (x1 + 1) + " 102 " + (z1 + 1) + " stone",
         "fill " + (x0 - 1) + " 100 " + (z0 - 1) + " " + (x0 - 1) + " 102 " + (z1 + 1) + " stone",
         "fill " + (x1 + 1) + " 100 " + (z0 - 1) + " " + (x1 + 1) + " 102 " + (z1 + 1) + " stone"
      ));
      for (int[] b : blocks) {
         cmds.add("setblock " + b[0] + " " + b[1] + " " + b[2] + " stone");
      }

      cmds.add("tp @a " + px + ".5 100 " + pz + ".5 0 0");
      cmds.add("effect clear @a slow_falling");
      for (String c : cmds) {
         sp.getServer().runCommand(c);
      }

      ctx.waitTicks(40);
      ctx.runOnClient(mc -> {
         AutoMineMod.config.customRegion = true;
         AutoMineMod.config.regionPlaced = true;
         AutoMineMod.config.regionX = x0;
         AutoMineMod.config.regionTopY = 102;
         AutoMineMod.config.regionZ = z0;
         AutoMineMod.config.regionSizeX = sx;
         AutoMineMod.config.regionSizeY = 3;
         AutoMineMod.config.regionSizeZ = sz;
         set(AutoMineMod.controller, "region", null);
         AutoMineMod.controller.resetProgress();
         AutoMineMod.controller.start();
      });
      List<String> idle = new ArrayList<>();
      double[] last = {Double.NaN, 0};
      int[] still = {0, 0, -1};
      int[] lastAge = {-1};
      int t = until(ctx, tag, mc -> {
         if (AutoMineMod.controller.state() == MineController.State.DONE || !AutoMineMod.controller.running()) {
            return true;
         }

         if (mc.player.age == lastAge[0]) {
            return false;
         }

         lastAge[0] = mc.player.age;

         double x = mc.player.getX();
         double z = mc.player.getZ();
         boolean moved = Double.isNaN(last[0]) || Math.hypot(x - last[0], z - last[1]) > 0.01;
         last[0] = x;
         last[1] = z;
         int done = AutoMineMod.controller.strikes() + AutoMineMod.controller.cleared();
         boolean progress = done != still[2];
         still[2] = done;
         still[0] = moved || progress ? 0 : still[0] + 1;
         if (still[0] == 60) {
            still[1]++;
            idle.add(AutoMineMod.controller.status() + " | " + AutoMineMod.controller.taskLabel());
         }

         return false;
      }, max);
      String status = ctx.computeOnClient(mc -> AutoMineMod.controller.status());
      int left = ctx.computeOnClient(mc -> {
         int n = 0;
         for (BlockPos p : BlockPos.iterate(x0, 100, z0, x1, 102, z1)) {
            if (!mc.world.getBlockState(p).isAir()) {
               n++;
            }
         }

         return n;
      });
      ctx.runOnClient(mc -> AutoMineMod.controller.pause("gametest"));
      AutoMineMod.LOGGER.info("[GameTest] {}: {} tick, trạng thái {}, còn {} ô, đứng im ≥3 giây {} lần: {}", tag, t, status, left, still[1], idle);
      check(left == 0, tag + ": khung " + sx + "x3x" + sz + ", đi đào hết " + blocks.length + " ô sót ở xa trong " + t + " tick (còn " + left + " ô, trạng thái: " + status + ")");
      check(still[1] == 0, tag + ": không lần nào đứng im ≥ 3 giây mà không đập được gì (" + still[1] + " lần: " + idle + ")");
   }

   /** chạy một WalkTask tới khi xong (tối đa max tick); trả số tick, -1 nếu hết giờ; labels nhận các nhãn việc đã qua */
   int walk(ClientGameTestContext ctx, String tag, Object walkTask, Region region, int layer, List<String> labels, List<double[]> track, int max) throws Exception {
      ctx.runOnClient(mc -> {
         set(AutoMineMod.controller, "region", region);
         set(AutoMineMod.controller, "layer", layer);
         set(AutoMineMod.controller, "task", walkTask);
         running(mc);
      });
      int t = until(ctx, tag, mc -> {
         try {
            Object task = get(AutoMineMod.controller, "task");
            if (task != null) {
               String l = AutoMineMod.controller.taskLabel();
               if (labels.isEmpty() || !labels.getLast().equals(l)) {
                  labels.add(l);
               }

               track.add(new double[]{mc.player.getX(), mc.player.getY(), mc.player.getZ()});
            }

            return task != walkTask;
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }, max);
      ctx.runOnClient(mc -> {
         AutoMineMod.controller.pause("gametest");
         set(AutoMineMod.controller, "region", null);
         set(AutoMineMod.controller, "layer", -1);
      });
      return t;
   }

   static Object walkTask(BlockPos goal, double tol, Object dig, String what) throws Exception {
      if (dig == null) {
         Constructor<?> k = inner("WalkTask").getDeclaredConstructor(MineController.class, BlockPos.class, double.class, boolean.class, String.class);
         k.setAccessible(true);
         return k.newInstance(AutoMineMod.controller, goal, tol, false, what);
      } else {
         Constructor<?> k = inner("WalkTask").getDeclaredConstructor(MineController.class, BlockPos.class, double.class, dig.getClass(), String.class, int.class, double.class);
         k.setAccessible(true);
         return k.newInstance(AutoMineMod.controller, goal, tol, dig, what, 6000, 1.0);
      }
   }

   static BlockPos feet(ClientGameTestContext ctx) {
      return ctx.computeOnClient(mc -> BlockPos.ofFloored(mc.player.getX(), mc.player.getY() + 0.2, mc.player.getZ()));
   }

   void tunnel(ClientGameTestContext ctx, TestSingleplayerContext sp) throws Exception {
      String[] cmds = {
         "fill -3 96 38 22 104 42 air",
         "fill -2 99 40 20 99 40 stone",
         "fill -2 100 39 20 102 39 stone",
         "fill -2 100 41 20 102 41 stone",
         "fill -2 102 40 20 102 40 stone",
         "fill 4 100 40 9 101 40 stone",
         "tp @a 0.5 100 40.5 -90 0"
      };
      for (String c : cmds) {
         sp.getServer().runCommand(c);
      }

      ctx.waitTicks(20);
      Object dig = new com.donutautomine.mine.PathFinder.Dig(null, true, 100, false);
      Object w = walkTask(new BlockPos(14, 100, 40), 0.22, dig, "gametest");
      List<String> labels = new ArrayList<>();
      int t = this.walk(ctx, "F", w, null, -1, labels, new ArrayList<>(), 1200);
      BlockPos f = feet(ctx);
      int left = ctx.computeOnClient(mc -> {
         int n = 0;
         for (BlockPos p : BlockPos.iterate(4, 100, 40, 9, 101, 40)) {
            if (!mc.world.getBlockState(p).isAir()) {
               n++;
            }
         }

         return n;
      });
      long waits = labels.stream().filter(l -> l.contains("chờ server") || l.contains("kiểm tra") || l.contains("đồng bộ")).count();
      AutoMineMod.LOGGER.info("[GameTest] F: {} tick, còn {} khối chặn, nhãn: {}", t, left, labels);
      check(t >= 0 && f.equals(new BlockPos(14, 100, 40)) && left == 0, "F: đào xuyên 12 khối chặn đường tới đích trong " + t + " tick (đang ở " + f.toShortString() + ", còn " + left + " khối)");
      check(waits == 0, "F: không có bước chờ server/kiểm tra/đồng bộ giữa các nhát (" + waits + " bước)");
   }

   void diagonal(ClientGameTestContext ctx, TestSingleplayerContext sp) throws Exception {
      String[] cmds = {
         "fill -3 96 48 18 106 66 air",
         "fill -2 99 49 16 99 65 stone",
         "tp @a 0.5 100 50.5 0 0"
      };
      for (String c : cmds) {
         sp.getServer().runCommand(c);
      }

      ctx.waitTicks(20);
      Object w = walkTask(new BlockPos(12, 100, 62), 0.22, null, "gametest");
      List<double[]> track = new ArrayList<>();
      int t = this.walk(ctx, "G", w, null, -1, new ArrayList<>(), track, 600);
      double off = 0.0;
      double len = 0.0;
      for (int i = 0; i < track.size(); i++) {
         double[] q = track.get(i);
         // khoảng cách tới đường thẳng nối (0.5, 50.5) → (12.5, 62.5)
         off = Math.max(off, Math.abs((q[0] - 0.5) - (q[2] - 50.5)) / Math.sqrt(2.0));
         if (i > 0) {
            len += Math.hypot(q[0] - track.get(i - 1)[0], q[2] - track.get(i - 1)[2]);
         }
      }

      BlockPos f = feet(ctx);
      AutoMineMod.LOGGER.info("[GameTest] G: {} tick, quãng đi {}, lệch xa nhất khỏi đường chéo {}", t, String.format("%.1f", len), String.format("%.2f", off));
      check(t >= 0 && f.equals(new BlockPos(12, 100, 62)), "G: tới đích chéo (12,100,62) trong " + t + " tick, đang ở " + f.toShortString());
      check(off < 1.5 && len < 19.0, "G: đi theo đường chéo (lệch xa nhất " + String.format("%.2f", off) + " ô, quãng " + String.format("%.1f", len) + " ô; đường thẳng 17,0 ô)");
   }

   void dip(ClientGameTestContext ctx, TestSingleplayerContext sp) throws Exception {
      String[] cmds = {
         "fill -3 96 68 18 104 72 air",
         "fill -2 97 70 16 99 70 stone",
         "fill -2 100 69 16 102 69 stone",
         "fill -2 100 71 16 102 71 stone",
         "fill 5 99 70 8 99 70 air",
         "setblock 0 102 70 stone",
         "tp @a 0.5 100 70.5 -90 0"
      };
      for (String c : cmds) {
         sp.getServer().runCommand(c);
      }

      ctx.waitTicks(20);
      Object w = walkTask(new BlockPos(13, 100, 70), 0.22, null, "gametest");
      Field lo = inner("WalkTask").getDeclaredField("yLo");
      Field hi = inner("WalkTask").getDeclaredField("yHi");
      lo.setAccessible(true);
      hi.setAccessible(true);
      lo.set(w, 100);
      hi.set(w, 100);
      List<String> labels = new ArrayList<>();
      int t = this.walk(ctx, "H", w, new Region("gametest", -1, 100, 70, 15, 102, 70), 0, labels, new ArrayList<>(), 900);
      BlockPos f = feet(ctx);
      boolean open = ctx.computeOnClient(mc -> {
         for (BlockPos p : BlockPos.iterate(5, 99, 70, 8, 99, 70)) {
            if (!mc.world.getBlockState(p).isAir()) {
               return false;
            }
         }

         return true;
      });
      AutoMineMod.LOGGER.info("[GameTest] H: {} tick, nhãn: {}", t, labels);
      check(t >= 0 && f.equals(new BlockPos(13, 100, 70)), "H: qua rãnh rộng 4 ô sâu 1 ô tới đích trong " + t + " tick, đang ở " + f.toShortString());
      check(open, "H: không đặt khối vào rãnh (đi xuống rồi bước lên)");
   }

   void diagonalHoles(ClientGameTestContext ctx, TestSingleplayerContext sp) throws Exception {
      List<String> cmds = new ArrayList<>(List.of("fill 30 96 48 50 106 66 air", "fill 31 97 49 48 99 65 stone", "tp @a 32.5 100 50.5 0 0"));
      for (int k = 0; k <= 11; k++) {
         // hố sâu 3 ô ngay cạnh đường chéo (32+k, 50+k) → (33+k, 51+k): ô vuông góc (33+k, 50+k)
         cmds.add("fill " + (33 + k) + " 97 " + (50 + k) + " " + (33 + k) + " 99 " + (50 + k) + " air");
      }

      for (String c : cmds) {
         sp.getServer().runCommand(c);
      }

      ctx.waitTicks(20);
      Object w = walkTask(new BlockPos(44, 100, 62), 0.22, null, "gametest");
      List<double[]> track = new ArrayList<>();
      int t = this.walk(ctx, "G2", w, null, -1, new ArrayList<>(), track, 600);
      double len = 0.0;
      for (int i = 1; i < track.size(); i++) {
         len += Math.hypot(track.get(i)[0] - track.get(i - 1)[0], track.get(i)[2] - track.get(i - 1)[2]);
      }

      BlockPos f = feet(ctx);
      boolean fell = track.stream().anyMatch(q -> q[1] < 99.5);
      AutoMineMod.LOGGER.info("[GameTest] G2: {} tick, quãng đi {}, rơi xuống hố {}", t, String.format("%.1f", len), fell);
      check(t >= 0 && f.equals(new BlockPos(44, 100, 62)) && !fell, "G2: tới đích chéo cạnh dãy hố, không rơi, trong " + t + " tick (đang ở " + f.toShortString() + ")");
      check(len < 19.5, "G2: vẫn đi đường chéo (quãng " + String.format("%.1f", len) + " ô; đường chéo 17,0 ô, chữ L 24 ô)");
   }

   void bigMine(ClientGameTestContext ctx, TestSingleplayerContext sp) throws Exception {
      // dịch chuyển lên cao để tải chunk: cho rơi chậm + kháng sát thương, không thì rơi chết mất đồ
      sp.getServer().runCommand("effect give @a resistance infinite 4 true");
      sp.getServer().runCommand("effect give @a slow_falling 10 0 true");
      sp.getServer().runCommand("tp @a 67.5 120 27.5 0 0");
      ctx.waitTicks(40);
      String[] cmds = {
         "fill 56 80 16 78 110 38 air",
         "fill 56 80 16 78 96 38 stone",
         "fill 62 95 22 64 95 24 gravel",
         "fill 70 92 28 71 92 30 gravel",
         "tp @a 67.5 97 27.5 0 0",
         "effect clear @a slow_falling"
      };
      for (String c : cmds) {
         sp.getServer().runCommand(c);
      }

      ctx.waitTicks(20);
      ctx.runOnClient(mc -> {
         AutoMineMod.config.customRegion = true;
         AutoMineMod.config.regionPlaced = true;
         AutoMineMod.config.regionX = 60;
         AutoMineMod.config.regionTopY = 96;
         AutoMineMod.config.regionZ = 20;
         AutoMineMod.config.regionSizeX = 15;
         AutoMineMod.config.regionSizeY = 6;
         AutoMineMod.config.regionSizeZ = 15;
         AutoMineMod.LOGGER.info("[GameTest] I tay: {} pickaxeTag={} chọn={}", mc.player.getMainHandStack(),
            mc.player.getMainHandStack().isIn(net.minecraft.registry.tag.ItemTags.PICKAXES), AutoMineMod.controller.tools().choose(mc));
         set(AutoMineMod.controller, "region", null);
         AutoMineMod.controller.resetProgress();
         AutoMineMod.controller.start();
      });
      int t = until(ctx, "I", mc -> AutoMineMod.controller.state() == MineController.State.DONE || !AutoMineMod.controller.running(), 12000);
      String status = ctx.computeOnClient(mc -> AutoMineMod.controller.status());
      int strikes = ctx.computeOnClient(mc -> AutoMineMod.controller.strikes());
      int left = ctx.computeOnClient(mc -> {
         int n = 0;
         for (BlockPos p : BlockPos.iterate(60, 91, 20, 74, 96, 34)) {
            if (!mc.world.getBlockState(p).isAir()) {
               n++;
            }
         }

         return n;
      });
      ctx.runOnClient(mc -> AutoMineMod.controller.pause("gametest"));
      AutoMineMod.LOGGER.info("[GameTest] I: {} tick, {} nhát, trạng thái {}, còn {} ô", t, strikes, status, left);
      check(left == 0, "I: đào sạch khung 15x6x15 (2 tầng, có sỏi) trong " + t + " tick, " + strikes + " nhát (còn " + left + " ô, trạng thái: " + status + ")");
   }

   /** dựng khung 1 tầng (y 100..102) ở (x0..x0+sx-1, z0..z0+sz-1), sàn đá y=99, viền đá; extra = lệnh dựng thêm (rãnh, lối vào); layerStone = tầng còn đá chưa đào */
   void trenchWorld(ClientGameTestContext ctx, TestSingleplayerContext sp, int x0, int z0, int sx, int sz, List<String> extra, boolean layerStone, int[][] leftovers, double px, double pz) {
      sp.getServer().runCommand("effect give @a resistance infinite 4 true");
      sp.getServer().runCommand("effect give @a slow_falling 10 0 true");
      sp.getServer().runCommand("tp @a " + (x0 + sx / 2) + ".5 120 " + (z0 + sz / 2) + ".5 0 0");
      ctx.waitTicks(60);
      sp.getClientWorld().waitForChunksDownload();
      int x1 = x0 + sx - 1;
      int z1 = z0 + sz - 1;
      List<String> cmds = new ArrayList<>(List.of(
         "fill " + (x0 - 3) + " 96 " + (z0 - 3) + " " + (x1 + 3) + " 106 " + (z1 + 3) + " air",
         "fill " + (x0 - 2) + " 96 " + (z0 - 2) + " " + (x1 + 2) + " 99 " + (z1 + 2) + " stone",
         "fill " + (x0 - 1) + " 100 " + (z0 - 1) + " " + (x1 + 1) + " 102 " + (z1 + 1) + " stone",
         "fill " + x0 + " 100 " + z0 + " " + x1 + " 102 " + z1 + " " + (layerStone ? "stone" : "air")
      ));
      cmds.addAll(extra);
      for (int[] b : leftovers) {
         cmds.add("setblock " + b[0] + " " + b[1] + " " + b[2] + " stone");
      }

      cmds.add("tp @a " + px + " 100 " + pz + " -90 0");
      cmds.add("effect clear @a slow_falling");
      for (String c : cmds) {
         sp.getServer().runCommand(c);
      }

      ctx.waitTicks(30);
   }

   /** chiều cao khung (đỉnh luôn ở y=102) cho runMine */
   int layersY = 3;
   /** chạy hook này ở tick thứ hookAt của runMine (vd. đẩy nhân vật xuống hố giữa chừng) */
   int hookAt = -1;
   java.util.function.Consumer<MinecraftClient> hook;

   int runMine(ClientGameTestContext ctx, String tag, int x0, int z0, int sx, int sz, int max) throws Exception {
      return this.runMine(ctx, tag, x0, z0, sx, sz, max, 0);
   }

   /** keepLeft = số ô cố ý không đào được (server huỷ mãi) → chỉ kiểm tra còn đúng chừng đó ô và mod vẫn chạy, không dừng */
   int runMine(ClientGameTestContext ctx, String tag, int x0, int z0, int sx, int sz, int max, int keepLeft) throws Exception {
      ctx.runOnClient(mc -> {
         AutoMineMod.config.customRegion = true;
         AutoMineMod.config.regionPlaced = true;
         AutoMineMod.config.regionX = x0;
         AutoMineMod.config.regionTopY = 102;
         AutoMineMod.config.regionZ = z0;
         AutoMineMod.config.regionSizeX = sx;
         AutoMineMod.config.regionSizeY = this.layersY;
         AutoMineMod.config.regionSizeZ = sz;
         set(AutoMineMod.controller, "region", null);
         AutoMineMod.controller.resetProgress();
         AutoMineMod.controller.start();
      });
      List<String> idle = new ArrayList<>();
      int place0 = Fake3x3Pickaxe.placements.get();
      int strict0 = Fake3x3Pickaxe.strictRejects.get();
      int[] seen = {0};
      double[] last = {Double.NaN, 0};
      int[] still = {0, 0, -1};
      int[] lastAge = {-1};
      int t = until(ctx, tag, mc -> {
         if (AutoMineMod.controller.state() == MineController.State.DONE || !AutoMineMod.controller.running()) {
            return true;
         }

         if (mc.player.age == lastAge[0]) {
            return false;
         }

         lastAge[0] = mc.player.age;
         if (++seen[0] == this.hookAt && this.hook != null) {
            this.hook.accept(mc);
         }

         boolean moved = Double.isNaN(last[0]) || Math.hypot(mc.player.getX() - last[0], mc.player.getZ() - last[1]) > 0.01;
         last[0] = mc.player.getX();
         last[1] = mc.player.getZ();
         int done = AutoMineMod.controller.strikes() + AutoMineMod.controller.cleared();
         boolean progress = done != still[2];
         still[2] = done;
         still[0] = moved || progress ? 0 : still[0] + 1;
         if (still[0] == 60) {
            still[1]++;
            idle.add(String.format(java.util.Locale.ROOT, "(%.2f %.2f %.2f sneak=%s) ", mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.isSneaking())
               + AutoMineMod.controller.status() + " | " + AutoMineMod.controller.taskLabel());
         }

         return false;
      }, max);
      int left = ctx.computeOnClient(mc -> {
         int n = 0;
         for (BlockPos p : BlockPos.iterate(x0, 103 - this.layersY, z0, x0 + sx - 1, 102, z0 + sz - 1)) {
            if (!mc.world.getBlockState(p).isAir()) {
               n++;
            }
         }

         return n;
      });
      int placed = ctx.computeOnClient(mc -> {
         int n = 0;
         for (BlockPos p : BlockPos.iterate(x0 - 10, 95, z0 - 3, x0 + sx - 1, 102, z0 + sz + 2)) {
            if (mc.world.getBlockState(p).isOf(Blocks.COBBLESTONE)) {
               n++;
            }
         }

         return n;
      });
      // đặt khối tính lúc đặt (khối kê cột / bắc cầu có thể bị đào lại về sau)
      placed = Math.max(placed, Fake3x3Pickaxe.placements.get() - place0);
      // 1.14.6: server thật (anti-cheat) huỷ gói "đập xong" không có "bắt đầu đập" đi trước → khối hiện lại mãi
      int strict = Fake3x3Pickaxe.strictRejects.get() - strict0;
      check(strict == 0, tag + ": mọi nhát đập đều gửi \"bắt đầu đập\" trước \"đập xong\" (" + strict + " gói bị server huỷ)");
      String status = ctx.computeOnClient(mc -> AutoMineMod.controller.status());
      // không tự dừng = còn chạy hoặc đã đào xong khung (không phải bị tạm dừng)
      boolean running = ctx.computeOnClient(mc -> AutoMineMod.controller.running() || AutoMineMod.controller.state() == MineController.State.DONE);
      ctx.runOnClient(mc -> AutoMineMod.controller.pause("gametest"));
      AutoMineMod.LOGGER.info("[GameTest] {}: {} tick, còn {} ô, cobble đặt {}, trạng thái {}, đứng im ≥3 giây {} lần: {}", tag, t, left, placed, status, still[1], idle);
      if (keepLeft > 0) {
         check(left <= keepLeft, tag + ": đào hết trừ " + keepLeft + " ô không phá được (còn " + left + " ô, trạng thái: " + status + ")");
         check(still[1] == 0, tag + ": không lần nào đứng im ≥ 3 giây (" + still[1] + " lần: " + idle + ")");
         check(running, tag + ": mod không tự dừng (còn chạy hoặc đã xong khung; trạng thái: " + status + ")");
         return t;
      }

      check(left == 0, tag + ": đào sạch trong " + t + " tick (còn " + left + " ô, trạng thái: " + status + ")");
      check(still[1] == 0, tag + ": không lần nào đứng im ≥ 3 giây (" + still[1] + " lần: " + idle + ")");
      check(placed == 0, tag + ": không đặt khối (lấp / bắc cầu / kê cột: " + placed + " cobble)");
      return t;
   }

   void trenchMined(ClientGameTestContext ctx, TestSingleplayerContext sp) throws Exception {
      int[][] left = {{311, 100, 304}, {312, 101, 302}, {313, 100, 306}, {310, 102, 307}};
      // nhân vật đứng sát mép rãnh (x = 306,75, rãnh ở cột x = 307), như ảnh
      this.trenchWorld(ctx, sp, 300, 300, 15, 9, List.of("fill 307 99 300 307 99 308 air"), false, left, 306.75, 304.5);
      this.runMine(ctx, "J", 300, 300, 15, 9, 1500);
   }

   void trenchRows(ClientGameTestContext ctx, TestSingleplayerContext sp) throws Exception {
      this.trenchWorld(ctx, sp, 340, 300, 12, 7, List.of("fill 346 99 300 346 99 306 air"), true, new int[0][], 340.5, 300.5);
      this.runMine(ctx, "J2", 340, 300, 12, 7, 3000);
   }

   /** J3: rãnh chạy dọc theo làn bên của hàng 2 (z = 303) → đi dọc mép rãnh mà vẫn đập 3x3, không ngồi */
   void trenchAlong(ClientGameTestContext ctx, TestSingleplayerContext sp) throws Exception {
      this.trenchWorld(ctx, sp, 380, 300, 12, 9, List.of("fill 380 99 303 391 99 303 air"), true, new int[0][], 380.5, 300.5);
      this.runMine(ctx, "J3", 380, 300, 12, 9, 3000);
   }

   /** J4: lối vào khung bị rãnh 1 ô cắt ngang (chưa vào khung) → nhảy qua để tới đầu hàng */
   void trenchEntry(ClientGameTestContext ctx, TestSingleplayerContext sp) throws Exception {
      this.trenchWorld(ctx, sp, 420, 300, 12, 7, List.of(
         "fill 410 96 299 419 106 307 air",
         "fill 410 96 299 419 99 307 stone",
         "fill 410 100 299 418 102 307 stone",
         "fill 412 100 302 419 102 304 air",
         "fill 416 99 302 416 99 304 air"
      ), true, new int[0][], 413.5, 303.5);
      this.runMine(ctx, "J4", 420, 300, 12, 7, 3000);
   }

   /**
    * K/K2: khối giữa của 1 lát bị server trả lại {@code rejects} lần → đập lại theo thang: lùi 0,5 → lùi thêm 0,5 → trái 0,5 → phải 0,5 → ngồi đập lẻ;
    * không đập lẻ khối xung quanh trong lúc thử; quá 5 lần thì để cuối tầng.
    */
   void revertRow(ClientGameTestContext ctx, TestSingleplayerContext sp, String tag, int x0, int rejects) throws Exception {
      this.revertRow(ctx, sp, tag, x0, rejects, false);
   }

   void revertRow(ClientGameTestContext ctx, TestSingleplayerContext sp, String tag, int x0, int rejects, boolean restore) throws Exception {
      BlockPos mid = new BlockPos(x0 + 4, 101, 401);
      this.trenchWorld(ctx, sp, x0, 400, 10, 3, List.of("fill " + (x0 - 2) + " 100 401 " + (x0 - 1) + " 101 401 air"), true, new int[0][], x0 - 1.5, 401.5);
      Fake3x3Pickaxe.attempts.clear();
      Fake3x3Pickaxe.reject.clear();
      Fake3x3Pickaxe.restore.clear();
      (restore ? Fake3x3Pickaxe.restore : Fake3x3Pickaxe.reject).put(mid, rejects);
      this.runMine(ctx, tag, x0, 400, 10, 3, 2400);
      List<Object[]> all;
      synchronized (Fake3x3Pickaxe.attempts) {
         all = new ArrayList<>(Fake3x3Pickaxe.attempts);
      }

      // trong lúc thử lại = từ nhát đầu vào khối giữa tới khi nó vỡ hoặc hết 6 nhát (nhát gốc + 5 lần thử); sau đó là vét cuối tầng
      int ladder = Math.min(rejects + 1, 6);
      List<Object[]> at = new ArrayList<>();
      int around = 0;
      int laterSlices = 0;
      int doneAt = -1;
      for (int i = 0; i < all.size(); i++) {
         Object[] a = all.get(i);
         BlockPos p = (BlockPos)a[0];
         if (p.equals(mid)) {
            at.add(a);
            if (!(Boolean)a[4] || at.size() == ladder) {
               doneAt = doneAt < 0 ? i : doneAt;
            }
         } else if (!at.isEmpty() && doneAt < 0) {
            if (p.getX() == mid.getX() && Math.abs(p.getY() - mid.getY()) <= 1 && Math.abs(p.getZ() - mid.getZ()) <= 1) {
               around++;
            }
         }

         if (doneAt >= 0 && i > doneAt && p.getX() > mid.getX()) {
            laterSlices++;
         }
      }

      StringBuilder b = new StringBuilder();
      for (Object[] a : at) {
         net.minecraft.util.math.Vec3d h = ((net.minecraft.util.math.Vec3d)a[5]).subtract(net.minecraft.util.math.Vec3d.of(mid));
         b.append(String.format(java.util.Locale.ROOT, " (%.2f %.2f %s %s, trúng %.2f %.2f %.2f)", a[1], a[2], (Boolean)a[3] ? "ngồi" : "đứng", (Boolean)a[4] ? "huỷ" : "vỡ", h.x, h.y, h.z));
      }

      AutoMineMod.LOGGER.info("[GameTest] {}: nhát vào khối giữa {}:{}; nhát lẻ khối xung quanh trong lúc thử: {}; nhát lát sau khi để dành: {}", tag, mid.toShortString(), b, around, laterSlices);
      check(rejects > 5 ? at.size() >= ladder : at.size() == rejects + 1, tag + ": khối giữa được đập lại (" + at.size() + " nhát, cần " + (rejects > 5 ? "≥ " + ladder : rejects + 1) + ")");
      check(around == 0, tag + ": không đập lẻ khối xung quanh trong lúc thử lại (" + around + " nhát)");
      if (at.size() >= 3) {
         double[] d = {(Double)at.get(2)[1] - (Double)at.get(1)[1], (Double)at.get(2)[2] - (Double)at.get(1)[2]};
         check((Double)at.get(1)[1] < (Double)at.get(0)[1] - 0.2, tag + ": lần 1 lùi ra xa mặt đập (x " + at.get(0)[1] + " → " + at.get(1)[1] + ")");
         check(Math.abs(d[0] + 0.5) <= 0.15 && Math.abs(d[1]) <= 0.15, tag + ": lần 2 lùi thêm 0,5 (dx=" + d[0] + " dz=" + d[1] + ")");
      }

      if (at.size() >= 6) {
         double[] l = {(Double)at.get(3)[1] - (Double)at.get(2)[1], (Double)at.get(3)[2] - (Double)at.get(2)[2]};
         double[] r = {(Double)at.get(4)[1] - (Double)at.get(2)[1], (Double)at.get(4)[2] - (Double)at.get(2)[2]};
         // đập về hướng +X: bên trái là -Z
         check(Math.abs(l[0]) <= 0.15 && Math.abs(l[1] + 0.5) <= 0.15, tag + ": lần 3 sang trái 0,5 (dx=" + l[0] + " dz=" + l[1] + ")");
         check(Math.abs(r[0]) <= 0.15 && Math.abs(r[1] - 0.5) <= 0.15, tag + ": lần 4 sang phải 0,5 (dx=" + r[0] + " dz=" + r[1] + ")");
         check((Boolean)at.get(5)[3] && !(Boolean)at.get(1)[3] && !(Boolean)at.get(4)[3], tag + ": lần 5 ngồi đập lẻ, các lần trước đứng đập 3x3");
      }

      // 1.14.4: mỗi lần đập lại ngắm một điểm khác trên khối (điểm trúng tương đối so với khối khác nhau)
      int sameAim = 0;
      for (int i = 1; i < Math.min(at.size(), ladder); i++) {
         net.minecraft.util.math.Vec3d p0 = ((net.minecraft.util.math.Vec3d)at.get(i - 1)[5]).subtract(net.minecraft.util.math.Vec3d.of(mid));
         net.minecraft.util.math.Vec3d p1 = ((net.minecraft.util.math.Vec3d)at.get(i)[5]).subtract(net.minecraft.util.math.Vec3d.of(mid));
         if (p0.distanceTo(p1) < 0.1) {
            sameAim++;
         }
      }

      if (at.size() >= 2) {
         check(sameAim == 0, tag + ": mỗi lần đập lại ngắm điểm khác trên khối (" + sameAim + " lần trùng điểm trước)");
      }

      if (rejects > 5) {
         check(laterSlices > 0, tag + ": thử 5 lần không được thì đi tiếp, không đứng thử mãi (" + laterSlices + " nhát lát sau)");
      }

      Fake3x3Pickaxe.reject.clear();
      Fake3x3Pickaxe.restore.clear();
   }

   /**
    * M1 (1.14.6, log người dùng): khối giữa hàng 1 bị server trả lại mãi (8 lần) giữa khối đá đặc → các lát sau bị che. Không được bỏ cả
    * phần còn lại của hàng cho vét lẻ (sót dải giữa các hàng): đào các hàng kế rồi quay lại đào nốt hàng 1 đúng làn của nó.
    */
   void resumeRow(ClientGameTestContext ctx, TestSingleplayerContext sp) throws Exception {
      int x0 = 700;
      BlockPos mid = new BlockPos(x0 + 4, 101, 401);
      this.trenchWorld(ctx, sp, x0, 400, 12, 9, List.of("fill " + (x0 - 2) + " 100 401 " + (x0 - 1) + " 101 401 air"), true, new int[0][], x0 - 1.5, 401.5);
      Fake3x3Pickaxe.attempts.clear();
      Fake3x3Pickaxe.reject.clear();
      Fake3x3Pickaxe.restore.clear();
      Fake3x3Pickaxe.reject.put(mid, 8);
      this.runMine(ctx, "M1", x0, 400, 12, 9, 6000);
      List<Object[]> all;
      synchronized (Fake3x3Pickaxe.attempts) {
         all = new ArrayList<>(Fake3x3Pickaxe.attempts);
      }

      int midHits = 0;
      int laneAfter = 0;
      int offLane = 0;
      StringBuilder off = new StringBuilder();
      for (Object[] a : all) {
         BlockPos p = (BlockPos)a[0];
         if (p.equals(mid)) {
            midHits++;
         } else if (p.getZ() == 401 && p.getY() == 101 && p.getX() > mid.getX()) {
            laneAfter++;
         }

         // trong mặt hàng 1 (z 400..402) chỉ có nhát 3x3 đúng làn (tâm z=401, y giữa tầng); nhát lệch làn = đào đường / vét lẻ xuyên hàng 1
         if (p.getZ() >= 400 && p.getZ() <= 402 && (p.getY() != 101 || p.getZ() != 401)) {
            offLane++;
            off.append(p.toShortString()).append("; ");
         }
      }

      AutoMineMod.LOGGER.info("[GameTest] M1: {} nhát vào khối giữa, {} nhát làn hàng 1 sau nó, {} nhát lệch làn: {}", midHits, laneAfter, offLane, off);
      check(midHits >= 9, "M1: khối giữa được thử lại tới khi vỡ (" + midHits + " nhát, cần ≥ 9)");
      check(laneAfter >= 5, "M1: quay lại đào nốt hàng 1 đúng làn sau khối giữa (" + laneAfter + " nhát, cần ≥ 5)");
      check(offLane == 0, "M1: không đào đường / vét lẻ lệch làn xuyên hàng 1 (" + offLane + " nhát: " + off + ")");
      Fake3x3Pickaxe.reject.clear();
   }

   /** K4: 2 khối giữa liền nhau hiện lại lệch nhau vài tick, khối xa hiện trước → khối gần che khối xa; phải đập khối gần trước, không đứng ngắm mãi */
   void hiddenMiddle(ClientGameTestContext ctx, TestSingleplayerContext sp) throws Exception {
      int x0 = 500;
      this.trenchWorld(ctx, sp, x0, 400, 10, 3, List.of("fill " + (x0 - 2) + " 100 401 " + (x0 - 1) + " 101 401 air"), true, new int[0][], x0 - 1.5, 401.5);
      Fake3x3Pickaxe.restoreLater.put(new BlockPos(x0 + 3, 101, 401), 30);
      Fake3x3Pickaxe.restoreLater.put(new BlockPos(x0 + 4, 101, 401), 14);
      this.runMine(ctx, "K4", x0, 400, 10, 3, 1200);
      Fake3x3Pickaxe.restoreLater.clear();
   }

   /**
    * K5 (như ảnh "Đập lại khối giữa … lần 0 · đập"): nhát 3x3 bỏ sót khối dưới và khối trên (sỏi) của 1 lát → khối giữa vỡ xong sỏi rơi xuống
    * nằm đúng chỗ khối giữa; lát sau đó cũng vậy. Phải đập được, không đứng im.
    */
   void gravelMiddle(ClientGameTestContext ctx, TestSingleplayerContext sp) throws Exception {
      int x0 = 520;
      List<String> cmds = new ArrayList<>(List.of("fill " + (x0 - 2) + " 100 401 " + (x0 - 1) + " 101 401 air"));
      for (int x : new int[]{x0 + 4, x0 + 5}) {
         cmds.add("setblock " + x + " 102 401 gravel");
         Fake3x3Pickaxe.keep.add(new BlockPos(x, 100, 401));
         Fake3x3Pickaxe.keep.add(new BlockPos(x, 102, 401));
      }

      this.trenchWorld(ctx, sp, x0, 400, 10, 3, cmds, true, new int[0][], x0 - 1.5, 401.5);
      this.runMine(ctx, "K5", x0, 400, 10, 3, 1500);
      Fake3x3Pickaxe.keep.clear();
   }

   /** K6: khối giữa server không bao giờ cho phá → đào hết phần còn lại (đi vòng qua làn bên), mod không tự dừng dù chờ lâu */
   void neverStop(ClientGameTestContext ctx, TestSingleplayerContext sp) throws Exception {
      int x0 = 540;
      this.trenchWorld(ctx, sp, x0, 400, 10, 3, List.of("fill " + (x0 - 2) + " 100 401 " + (x0 - 1) + " 101 401 air"), true, new int[0][], x0 - 1.5, 401.5);
      BlockPos stubborn = new BlockPos(x0 + 4, 101, 401);
      Fake3x3Pickaxe.reject.put(stubborn, 1000000);
      Fake3x3Pickaxe.keep.add(stubborn);
      int old = MineController.watchdogTicks;
      MineController.watchdogTicks = 200;
      int rec0 = ctx.computeOnClient(mc -> AutoMineMod.controller.recoveriesTotal);
      try {
         this.runMine(ctx, "K6", x0, 400, 10, 3, 2400, 1);
      } finally {
         MineController.watchdogTicks = old;
         Fake3x3Pickaxe.reject.clear();
         Fake3x3Pickaxe.keep.clear();
      }

      int rec = ctx.computeOnClient(mc -> AutoMineMod.controller.recoveriesTotal) - rec0;
      AutoMineMod.LOGGER.info("[GameTest] K6: làm lại vì kẹt {} lần", rec);
   }

   /** K7: đang đào thì ép làm lại vì kẹt 3 kiểu (làm lại kế hoạch, đi chỗ khác, vào lại tầng) → vẫn đào sạch, không dừng */
   void forcedRecover(ClientGameTestContext ctx, TestSingleplayerContext sp) throws Exception {
      int x0 = 560;
      this.trenchWorld(ctx, sp, x0, 400, 12, 6, List.of("fill " + (x0 - 2) + " 100 401 " + (x0 - 1) + " 101 401 air"), true, new int[0][], x0 - 1.5, 401.5);
      java.lang.reflect.Method recover = MineController.class.getDeclaredMethod("recover", String.class);
      recover.setAccessible(true);
      Thread t = new Thread(() -> {
         try {
            for (int i = 0; i < 3; i++) {
               Thread.sleep(2500);
               int kind = i;
               MinecraftClient.getInstance().execute(() -> {
                  try {
                     if (AutoMineMod.controller.running()) {
                        // lần 1 làm lại kế hoạch, lần 2 đi chỗ khác, lần 3 vào lại tầng
                        set(AutoMineMod.controller, "recoveries", kind);
                        recover.invoke(AutoMineMod.controller, "gametest ép kẹt");
                     }
                  } catch (Exception e) {
                     AutoMineMod.LOGGER.warn("[GameTest] K7: không ép được", e);
                  }
               });
            }
         } catch (Exception e) {
            AutoMineMod.LOGGER.warn("[GameTest] K7: không ép được", e);
         }
      });
      int rec0 = ctx.computeOnClient(mc -> AutoMineMod.controller.recoveriesTotal);
      t.setDaemon(true);
      t.start();
      this.runMine(ctx, "K7", x0, 400, 12, 6, 2400);
      t.join(1000);
      int rec = ctx.computeOnClient(mc -> AutoMineMod.controller.recoveriesTotal) - rec0;
      check(rec >= 2, "K7: đã ép làm lại " + rec + " lần mà vẫn đào sạch");
   }

   /** K8: khối giữa một lát là bedrock (không phá được) che kín mặt lát sau → phải đi vòng qua làn bên đào nốt, không đứng im */
   void detour(ClientGameTestContext ctx, TestSingleplayerContext sp) throws Exception {
      int x0 = 580;
      this.trenchWorld(ctx, sp, x0, 400, 10, 3, List.of(
         "fill " + (x0 - 2) + " 100 401 " + (x0 - 1) + " 101 401 air",
         "setblock " + (x0 + 4) + " 101 401 bedrock"
      ), true, new int[0][], x0 - 1.5, 401.5);
      this.runMine(ctx, "K8", x0, 400, 10, 3, 2400, 1);
   }

   /** L1: tầng đã đào, giữa sàn có hố 3x3 sâu 2 ô, khối sót bên kia, có đường vòng → đi vòng, không bắc cầu */
   void deepHole(ClientGameTestContext ctx, TestSingleplayerContext sp) throws Exception {
      int x0 = 600;
      int[][] left = {{610, 100, 304}, {611, 101, 303}, {612, 100, 305}};
      this.trenchWorld(ctx, sp, x0, 300, 14, 9, List.of("fill 605 98 303 607 99 305 air"), false, left, 603.5, 304.5);
      this.runMine(ctx, "L1", x0, 300, 14, 9, 1500);
   }

   /**
    * L2: đang đào tầng 1 (sàn tầng đã đào, còn khối sót) thì bị đẩy xuống đáy hố sâu 2 ô (3x3) nằm trong tầng 2 còn đá
    * → ra khỏi hố bằng đi bộ / đào bậc, không kê cột, rồi đào tiếp.
    */
   void pitEscape(ClientGameTestContext ctx, TestSingleplayerContext sp) throws Exception {
      int x0 = 630;
      int[][] left = {{636, 101, 307}, {631, 100, 301}, {637, 100, 302}};
      this.trenchWorld(ctx, sp, x0, 300, 8, 9, List.of("fill 634 98 303 636 99 305 air"), false, left, 632.5, 304.5);
      this.layersY = 6;
      this.hookAt = 30;
      this.hook = mc -> mc.getServer().execute(() -> mc.getServer().getCommandManager().executeWithPrefix(mc.getServer().getCommandSource(), "tp @a 635.5 98 304.5"));
      try {
         this.runMine(ctx, "L2", x0, 300, 8, 9, 6000);
      } finally {
         this.layersY = 3;
         this.hookAt = -1;
         this.hook = null;
      }
   }
}
