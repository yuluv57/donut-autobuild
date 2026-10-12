package com.donutautobuild.build;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ObserverBlock;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * 3.0.104 (người dùng): "mỗi việc làm gần TNT đều phải xem xét có làm nó kích hoạt không".
 *
 * <p>TNT bị kích khi: (1) nhận điện — nguồn điện đang bật ngay cạnh, hoặc khối đặc cạnh nó đang nhận điện; (2) lửa — lava / lửa
 * gần đó cháy lan; (3) máy xung quanh chạy — piston (máy nhân TNT), dispenser (có thể chứa TNT / bật lửa), dropper, ray kích hoạt
 * (xe TNT) nhận xung. Xung sinh ra khi: một ô đổi trạng thái mà có observer đang nhìn (observer phát xung ra phía sau lưng), đặt
 * nguồn điện, đặt khối dẫn điện / linh kiện đỏ cạnh chỗ đang có điện, đập / đặt khối làm dây đỏ đổi hướng, chỉnh comparator /
 * cảm biến ánh sáng, tiếng động gần cảm biến sculk.
 *
 * <p>Mọi phép kiểm tra đều THIÊN VỀ AN TOÀN: không chắc thì coi là nguy hiểm (không làm, hoãn lại, ghi lý do).
 */
final class TntGuard {
   private static final Map<Block, String> IDS = new HashMap<>();
   private static final Direction[] DIRS = new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
   private static final Direction[] HORIZ = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
   /** TNT đang có quanh người (BuildController quét lại mỗi 1 giây) */
   static final ArrayList<BlockPos> TNT = new ArrayList<>();
   /** cảm biến sculk quanh người (tiếng đặt / đập khối làm nó phát điện) */
   static final ArrayList<BlockPos> SENSORS = new ArrayList<>();

   private TntGuard() {
   }

   static String id(BlockState var0) {
      return IDS.computeIfAbsent(var0.getBlock(), var0x -> Registries.BLOCK.getId(var0x).toString());
   }

   static String name(BlockState var0) {
      return id(var0).replace("minecraft:", "");
   }

   static boolean tnt(BlockState var0) {
      return var0 != null && var0.isOf(Blocks.TNT);
   }

   private static boolean on(BlockState var0, String var1) {
      return "true".equals(StateMatch.property(var0, var1));
   }

   private static boolean has(BlockState var0, String var1) {
      return !StateMatch.property(var0, var1).isEmpty();
   }

   private static int power(BlockState var0) {
      String var1 = StateMatch.property(var0, "power");
      if (var1.isEmpty()) {
         return 0;
      } else {
         try {
            return Integer.parseInt(var1);
         } catch (NumberFormatException var3) {
            return 0;
         }
      }
   }

   static Direction facing(BlockState var0) {
      String var1 = StateMatch.property(var0, "facing");

      for (Direction var5 : DIRS) {
         if (var5.asString().equals(var1)) {
            return var5;
         }
      }

      return null;
   }

   /** đang PHÁT điện: khối redstone, đuốc đỏ đang sáng, dây đang có điện, repeater/comparator/cần gạt/nút/observer… đang bật */
   static boolean emitter(BlockState var0) {
      if (var0 == null || var0.isAir()) {
         return false;
      } else if (id(var0).equals("minecraft:redstone_block")) {
         return true;
      } else {
         return var0.emitsRedstonePower() && (on(var0, "powered") || power(var0) > 0 || on(var0, "lit"));
      }
   }

   /** nhận xung là có thể nổ: TNT, piston gần TNT (máy nhân TNT / đẩy TNT), dispenser, dropper, ray kích hoạt (xe TNT) */
   static String danger(BlockState var0, BlockPos var1, BlockPos var2) {
      if (var1.equals(var2)) {
         return "TNT sắp đặt @ " + var1.toShortString();
      } else if (var0 == null || var0.isAir()) {
         return null;
      } else if (tnt(var0)) {
         return "TNT @ " + var1.toShortString();
      } else {
         String var3 = id(var0);
         if (var3.equals("minecraft:dispenser") || var3.equals("minecraft:dropper") || var3.equals("minecraft:activator_rail")) {
            return name(var0) + " @ " + var1.toShortString();
         } else if (var3.equals("minecraft:piston") || var3.equals("minecraft:sticky_piston")) {
            BlockPos var4 = tntWithin(var1, 5, var2);
            return var4 == null ? null : name(var0) + " @ " + var1.toShortString() + " (cách TNT @ " + var4.toShortString() + " ≤ 5 ô)";
         } else {
            return null;
         }
      }
   }

   /** truyền điện đi tiếp: dây đỏ, repeater, comparator, đuốc đỏ, ray điện / ray cảm biến */
   static boolean carrier(BlockState var0) {
      if (var0 == null || var0.isAir()) {
         return false;
      } else {
         String var1 = id(var0);
         return var1.equals("minecraft:redstone_wire")
            || var1.equals("minecraft:repeater")
            || var1.equals("minecraft:comparator")
            || var1.equals("minecraft:redstone_torch")
            || var1.equals("minecraft:redstone_wall_torch")
            || var1.equals("minecraft:powered_rail")
            || var1.equals("minecraft:detector_rail");
      }
   }

   /** khối có trạng thái đổi theo điện (đèn, note block, cửa, piston, ray…) — đổi là observer nhìn nó phát xung */
   static boolean stateful(BlockState var0) {
      return var0 != null
         && !var0.isAir()
         && (carrier(var0) || has(var0, "powered") || has(var0, "lit") || has(var0, "power") || has(var0, "triggered") || has(var0, "extended") || has(var0, "enabled"));
   }

   /** nguồn điện BẬT ngay khi đặt xuống */
   static boolean source(BlockState var0) {
      if (var0 == null || var0.isAir()) {
         return false;
      } else {
         String var1 = id(var0);
         return var1.equals("minecraft:redstone_block")
            || var1.equals("minecraft:redstone_torch")
            || var1.equals("minecraft:redstone_wall_torch")
            || var1.equals("minecraft:daylight_detector")
            || var1.equals("minecraft:comparator");
      }
   }

   /** khối người chơi đứng / đi qua là phát điện: bệ áp lực, dây bẫy, ray cảm biến */
   static boolean stepTrigger(BlockState var0) {
      if (var0 == null || var0.isAir()) {
         return false;
      } else {
         String var1 = id(var0);
         return var1.endsWith("_pressure_plate") || var1.equals("minecraft:tripwire") || var1.equals("minecraft:detector_rail");
      }
   }

   /** có dính tới mạch đỏ / máy (dùng để quyết định đặt TNT sau cùng) */
   static boolean redstoneish(BlockState var0) {
      if (var0 == null || var0.isAir()) {
         return false;
      } else if (var0.emitsRedstonePower() || stateful(var0) || var0.getBlock() instanceof ObserverBlock) {
         return true;
      } else {
         String var1 = id(var0);
         return var1.contains("piston")
            || var1.equals("minecraft:note_block")
            || var1.equals("minecraft:slime_block")
            || var1.equals("minecraft:honey_block")
            || var1.equals("minecraft:dispenser")
            || var1.equals("minecraft:dropper")
            || var1.equals("minecraft:tripwire")
            || var1.endsWith("_rail");
      }
   }

   private static BlockState at(ClientWorld var0, BlockPos var1, BlockPos var2, BlockState var3) {
      return var2 != null && var2.equals(var1) ? var3 : var0.getBlockState(var1);
   }

   /** TNT gần nhất trong khối lập phương bán kính var1 quanh ô var0 (TNT đã quét + ô sắp đặt TNT var2) */
   static BlockPos tntWithin(BlockPos var0, int var1, BlockPos var2) {
      if (var2 != null && cheb(var2, var0) <= var1) {
         return var2;
      } else {
         for (BlockPos var4 : TNT) {
            if (cheb(var4, var0) <= var1) {
               return var4;
            }
         }

         return null;
      }
   }

   static int cheb(BlockPos var0, BlockPos var1) {
      return Math.max(Math.abs(var0.getX() - var1.getX()), Math.max(Math.abs(var0.getY() - var1.getY()), Math.abs(var0.getZ() - var1.getZ())));
   }

   /** có TNT trong bán kính var2 quanh ô var1 (thêm quét trực tiếp bán kính 6 — TNT mới đặt chưa kịp vào danh sách) */
   static boolean near(ClientWorld var0, BlockPos var1, int var2) {
      if (tntWithin(var1, var2, null) != null) {
         return true;
      } else {
         for (int var3 = -6; var3 <= 6; var3++) {
            for (int var4 = -6; var4 <= 6; var4++) {
               for (int var5 = -6; var5 <= 6; var5++) {
                  BlockPos var6 = new BlockPos(var1.getX() + var3, var1.getY() + var4, var1.getZ() + var5);
                  if (var0.getBlockState(var6).isOf(Blocks.TNT)) {
                     TNT.add(var6);
                     return true;
                  }
               }
            }
         }

         return false;
      }
   }

   /** ô var1 đang nhận điện: nguồn đang bật ngay cạnh, hoặc khối đặc cạnh nó có nguồn đang bật kề bên */
   static String receivesPower(ClientWorld var0, BlockPos var1) {
      for (Direction var5 : DIRS) {
         BlockPos var6 = var1.offset(var5);
         BlockState var7 = var0.getBlockState(var6);
         if (emitter(var7)) {
            return name(var7) + " @ " + var6.toShortString() + " đang bật";
         }

         if (var7.isSolidBlock(var0, var6)) {
            for (Direction var11 : DIRS) {
               BlockPos var12 = var6.offset(var11);
               if (!var12.equals(var1)) {
                  BlockState var13 = var0.getBlockState(var12);
                  if (emitter(var13)) {
                     return "khối " + var6.toShortString() + " đang nhận điện từ " + name(var13) + " @ " + var12.toShortString();
                  }
               }
            }
         }
      }

      return null;
   }

   private static void push(ArrayDeque<BlockPos> var0, HashSet<BlockPos> var1, BlockPos var2) {
      BlockPos var3 = var2.toImmutable();
      if (var1.add(var3)) {
         var0.add(var3);
      }
   }

   /** các observer đang nhìn vào ô var1 → thêm ô phía sau lưng chúng (nơi nhận xung) vào hàng đợi */
   private static void watchers(ClientWorld var0, BlockPos var1, BlockPos var2, BlockState var3, ArrayDeque<BlockPos> var4, HashSet<BlockPos> var5, List<String> var6) {
      for (Direction var10 : DIRS) {
         BlockPos var11 = var1.offset(var10);
         BlockState var12 = at(var0, var11, var2, var3);
         if (var12.getBlock() instanceof ObserverBlock && facing(var12) == var10.getOpposite()) {
            BlockPos var13 = var11.offset(var10).toImmutable();
            if (var5.add(var13)) {
               var4.add(var13);
               if (var6 != null && var6.isEmpty()) {
                  var6.add("observer @ " + var11.toShortString() + " nhìn ô " + var1.toShortString());
               }
            }
         }
      }
   }

   /**
    * Điện / xung đi vào các ô var1: lan theo dây đỏ, repeater, comparator, đuốc, ray; khối đặc nhận điện thì cấp cho linh kiện và TNT
    * cạnh nó; ô nào đổi trạng thái mà có observer nhìn thì observer phát xung ra sau lưng. Chạm TNT / máy nguy hiểm → trả mô tả.
    * var2/var3: ô đang xét đổi thành trạng thái var3 (khối sắp đặt / không khí nếu đập). var4: ô sắp đặt TNT (coi như đã có TNT).
    */
   static String pulse(ClientWorld var0, Collection<BlockPos> var1, BlockPos var2, BlockState var3, BlockPos var4) {
      ArrayDeque<BlockPos> var5 = new ArrayDeque<>();
      HashSet<BlockPos> var6 = new HashSet<>();
      ArrayList<String> var7 = new ArrayList<>();

      for (BlockPos var9 : var1) {
         push(var5, var6, var9);
      }

      int var22 = 0;

      while (!var5.isEmpty() && var22++ < 800) {
         BlockPos var10 = var5.poll();
         if (var0.isChunkLoaded(var10)) {
            BlockState var11 = at(var0, var10, var2, var3);
            String var12 = danger(var11, var10, var4);
            if (var12 != null) {
               return var7.isEmpty() ? var12 : var7.get(0) + " → " + var12;
            }

            if (!var11.isAir()) {
               if (stateful(var11)) {
                  watchers(var0, var10, var2, var3, var5, var6, var7);
               }

               boolean var13 = carrier(var11);
               boolean var14 = !var13 && var11.isSolidBlock(var0, var10);
               if (var13 || var14) {
                  for (Direction var18 : DIRS) {
                     BlockPos var19 = var10.offset(var18).toImmutable();
                     BlockState var20 = at(var0, var19, var2, var3);
                     String var21 = danger(var20, var19, var4);
                     if (var21 != null) {
                        return (var7.isEmpty() ? "" : var7.get(0) + " → ") + var21 + " (cạnh ô có điện " + var10.toShortString() + ")";
                     }

                     // dây/repeater/đuốc cấp điện cho mọi ô cạnh (kể cả khối đặc); khối đặc chỉ cấp cho linh kiện cạnh nó
                     if (!var20.isAir() && (var13 || stateful(var20))) {
                        push(var5, var6, var19);
                     }
                  }

                  if (var13 && id(var11).equals("minecraft:redstone_wire")) {
                     // dây đỏ leo lên / xuống 1 ô
                     for (Direction var24 : HORIZ) {
                        push(var5, var6, var10.offset(var24).up());
                        push(var5, var6, var10.offset(var24).down());
                     }
                  }
               }
            }
         }
      }

      return null;
   }

   /** ô cạnh sẽ đổi hình dạng / rơi ra khi đặt / đập khối bên cạnh (dây, hàng rào, tường, kính mỏng, đuốc, ray, nước, note block…) */
   private static boolean reshapes(ClientWorld var0, BlockPos var1, BlockState var2, boolean var3) {
      if (var2.isAir()) {
         return false;
      } else if (!var2.getFluidState().isEmpty()) {
         return true;
      } else if (!var2.isSolidBlock(var0, var1)) {
         return true;
      } else {
         return var3 && (id(var2).equals("minecraft:note_block") || has(var2, "snowy") || id(var2).endsWith("mushroom_block"));
      }
   }

   /** đổ nước: các ô var1 nước sẽ chảy tới đổi trạng thái → observer đang nhìn chúng phát xung → có chạm TNT / máy không */
   static String flood(ClientWorld var0, Collection<BlockPos> var1) {
      for (BlockPos var3 : var1) {
         ArrayDeque<BlockPos> var4 = new ArrayDeque<>();
         HashSet<BlockPos> var5 = new HashSet<>();
         ArrayList<String> var6 = new ArrayList<>();
         watchers(var0, var3, null, null, var4, var5, var6);
         if (!var4.isEmpty()) {
            String var7 = pulse(var0, var4, null, null, null);
            if (var7 != null) {
               return "nước chảy tới ô " + var3.toShortString() + ": " + (var6.isEmpty() ? "" : var6.get(0) + " sẽ phát xung → ") + var7;
            }
         }
      }

      return null;
   }

   private static ArrayList<BlockPos> around(BlockPos var0, boolean var1) {
      ArrayList<BlockPos> var2 = new ArrayList<>();
      if (var1) {
         var2.add(var0.toImmutable());
      }

      for (Direction var6 : DIRS) {
         var2.add(var0.offset(var6).toImmutable());
      }

      return var2;
   }

   /**
    * Kiểm tra 1 việc tại ô var1: var2 = trạng thái sau khi làm (null = đập thành không khí); var3 = chỉ chỉnh khối có sẵn
    * (bấm chuột phải). Trả về lý do nguy hiểm, hoặc null nếu an toàn / không có TNT quanh đó.
    */
   static String check(ClientWorld var0, BlockPos var1, BlockState var2, boolean var3) {
      if (var0 == null || var1 == null) {
         return null;
      }

      BlockPos var4 = var1.toImmutable();
      BlockState var5 = var0.getBlockState(var4);
      boolean var7 = var2 != null && !var3 && tnt(var2) && !tnt(var5);
      if (!var7 && !near(var0, var4, 24)) {
         return null;
      }

      BlockState var6 = var2 == null ? Blocks.AIR.getDefaultState() : var2;
      BlockPos var8 = var7 ? var4 : null;
      if (var2 != null && !var3) {
         String var9 = id(var2);
         if (var9.equals("minecraft:lava")
            || var9.equals("minecraft:fire")
            || var9.equals("minecraft:soul_fire")
            || var9.endsWith("campfire")
            || var9.equals("minecraft:magma_block")) {
            int var50 = var9.equals("minecraft:lava") ? 12 : 8;
            BlockPos var10 = tntWithin(var4, var50, null);
            if (var10 != null) {
               return name(var2) + " cách TNT @ " + var10.toShortString() + " ≤ " + var50 + " ô — lửa / lava chảy, cháy lan sẽ kích nổ";
            }
         }
      }

      if (var7) {
         // đặt TNT vào chỗ đang có điện → nổ ngay; cạnh lửa / lava → bắt lửa
         String var11 = receivesPower(var0, var4);
         if (var11 != null) {
            return "đặt TNT vào sẽ nổ ngay: " + var11;
         }

         for (int var12 = -3; var12 <= 3; var12++) {
            for (int var13 = -2; var13 <= 4; var13++) {
               for (int var14 = -3; var14 <= 3; var14++) {
                  BlockPos var15 = var4.add(var12, var13, var14);
                  String var16 = id(var0.getBlockState(var15));
                  if (var16.equals("minecraft:fire") || var16.equals("minecraft:soul_fire") || var16.equals("minecraft:lava")) {
                     return "đặt TNT gần " + var16.replace("minecraft:", "") + " @ " + var15.toShortString() + " sẽ bị bắt lửa";
                  }
               }
            }
         }
      }

      if (var2 == null && tnt(var5) && on(var5, "unstable")) {
         return "TNT không ổn định (unstable) — đập là nổ";
      }

      // 1) các ô đổi trạng thái: chính ô var1 + ô cạnh đổi hình dạng / rơi ra → observer đang nhìn chúng phát xung
      ArrayList<BlockPos> var17 = new ArrayList<>();
      var17.add(var4);

      for (Direction var19 : DIRS) {
         BlockPos var20 = var4.offset(var19).toImmutable();
         BlockState var21 = var0.getBlockState(var20);
         if (var3 && (var19 == Direction.UP || var19 == Direction.DOWN) || reshapes(var0, var20, var21, var19 == Direction.UP)) {
            var17.add(var20);
         }
      }

      for (BlockPos var23 : var17) {
         ArrayDeque<BlockPos> var24 = new ArrayDeque<>();
         HashSet<BlockPos> var25 = new HashSet<>();
         ArrayList<String> var26 = new ArrayList<>();
         watchers(var0, var23, var4, var6, var24, var25, var26);
         if (!var24.isEmpty()) {
            String var27 = pulse(var0, var24, var4, var6, var8);
            if (var27 != null) {
               return (var26.isEmpty() ? "" : var26.get(0) + " sẽ phát xung → ") + var27;
            }
         }

         // ô cạnh là dây / nguồn ĐANG CÓ ĐIỆN bị đổi (dây nối lại hướng khác, đuốc / cần gạt rơi ra) → điện phía sau thay đổi
         if (!var23.equals(var4)) {
            BlockState var28 = var0.getBlockState(var23);
            if (emitter(var28)) {
               String var29 = pulse(var0, List.of(var23), var4, var6, var8);
               if (var29 != null) {
                  return name(var28) + " @ " + var23.toShortString() + " bị đổi theo → " + var29;
               }
            }
         }
      }

      if (var2 != null && !var3) {
         // 2) đặt observer: ô nó nhìn đổi hình dạng (hàng rào, dây… nối vào observer) → phát xung ra sau lưng
         if (var2.getBlock() instanceof ObserverBlock) {
            Direction var30 = facing(var2);
            if (var30 != null) {
               BlockPos var31 = var4.offset(var30);
               BlockState var32 = var0.getBlockState(var31);
               if (!var32.isAir() && !var32.isSolidBlock(var0, var31)) {
                  String var33 = pulse(var0, List.of(var4.offset(var30.getOpposite()).toImmutable()), var4, var6, var8);
                  if (var33 != null) {
                     return "observer đặt nhìn vào " + name(var32) + " @ " + var31.toShortString() + " có thể phát xung → " + var33;
                  }
               }
            }
         }

         // 3) đặt nguồn điện: điện đi ra mọi ô cạnh
         if (source(var2)) {
            String var34 = pulse(var0, around(var4, false), var4, var6, var8);
            if (var34 != null) {
               return "đặt nguồn điện " + name(var2) + " → " + var34;
            }
         }

         // 4) đặt khối dẫn điện / linh kiện / máy cạnh chỗ đang có điện → điện đi qua nó
         boolean var35 = false;

         for (Direction var37 : DIRS) {
            if (emitter(var0.getBlockState(var4.offset(var37)))) {
               var35 = true;
               break;
            }
         }

         if (var35 && (carrier(var2) || stateful(var2) || var2.isSolidBlock(var0, var4) || danger(var2, var4, var8) != null)) {
            String var38 = pulse(var0, List.of(var4), var4, var6, var8);
            if (var38 != null) {
               return "khối đặt vào sẽ nhận điện từ bên cạnh → " + var38;
            }
         }

         // 5) piston / dispenser / dropper nhận điện kiểu "gần kề" (nguồn cạnh ô phía trên nó) → chạy ngay khi đặt
         if (!tnt(var2) && danger(var2, var4, var8) != null) {
            for (Direction var40 : DIRS) {
               BlockPos var41 = var4.up().offset(var40);
               if (!var41.equals(var4) && emitter(var0.getBlockState(var41))) {
                  return name(var2) + " @ " + var4.toShortString() + " có nguồn điện cạnh ô phía trên (" + var41.toShortString() + ") → chạy ngay";
               }
            }
         }
      } else if (var3 && (source(var5) || id(var5).equals("minecraft:repeater"))) {
         // 6) chỉnh comparator / cảm biến ánh sáng / repeater: tín hiệu ra có thể đổi
         String var42 = pulse(var0, around(var4, false), var4, var6, var8);
         if (var42 != null) {
            return "chỉnh " + name(var5) + " làm đổi tín hiệu → " + var42;
         }
      }

      if (var2 == null && (emitter(var5) || carrier(var5))) {
         // 7) đập nguồn / dây đang dẫn điện → điện phía sau tắt, piston co lại…
         String var43 = pulse(var0, around(var4, false), var4, var6, var8);
         if (var43 != null) {
            return "đập " + name(var5) + " đang dẫn điện → " + var43;
         }
      }

      // 8) tiếng đặt / đập khối làm cảm biến sculk trong 8 ô phát điện
      for (BlockPos var44 : SENSORS) {
         int var45 = var44.getX() - var4.getX();
         int var46 = var44.getY() - var4.getY();
         int var47 = var44.getZ() - var4.getZ();
         if (var45 * var45 + var46 * var46 + var47 * var47 <= 64) {
            String var48 = pulse(var0, around(var44, true), var4, var6, var8);
            if (var48 != null) {
               return "cảm biến sculk @ " + var44.toShortString() + " nghe tiếng động sẽ phát điện → " + var48;
            }
         }
      }

      return null;
   }
}
