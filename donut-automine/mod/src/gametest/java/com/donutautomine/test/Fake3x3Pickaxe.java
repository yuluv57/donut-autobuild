package com.donutautomine.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ModInitializer;
import net.minecraft.block.entity.BlockEntity;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Cúp 3x3 giả cho thế giới đơn (giống cúp 3x3 của DonutSMP): cúp có tên "3x3" phá thêm 8 khối quanh khối vừa đập,
 * trên mặt phẳng vuông góc với hướng nhìn; đang ngồi thì chỉ phá 1 khối.
 * Giả cả server trả lại khối: ô trong {@link #reject} bị huỷ nhát phá (server gửi lại khối cho client) đủ số lần ghi ở đó.
 */
public final class Fake3x3Pickaxe implements ModInitializer {
   private static boolean breaking;
   /** ô → số lần nữa server huỷ nhát phá */
   public static final Map<BlockPos, Integer> reject = new ConcurrentHashMap<>();
   /** ô → số lần nữa server cho vỡ (cả 3x3) rồi đặt lại riêng khối đó (khối giữa "vỡ rồi hiện lại") */
   public static final Map<BlockPos, Integer> restore = new ConcurrentHashMap<>();
   /** mọi nhát phá của người chơi (không tính 8 khối 3x3 kéo theo): {ô, x, z, đang ngồi, bị huỷ} */
   public static final List<Object[]> attempts = Collections.synchronizedList(new ArrayList<>());

   @Override
   public void onInitialize() {
      PlayerBlockBreakEvents.BEFORE.register(Fake3x3Pickaxe::beforeBreak);
      PlayerBlockBreakEvents.AFTER.register(Fake3x3Pickaxe::afterBreak);
   }

   static boolean beforeBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity entity) {
      if (world.isClient) {
         return true;
      }

      Integer n = reject.get(pos);
      boolean cancel = n != null && n > 0;
      if (cancel) {
         reject.put(pos.toImmutable(), n - 1);
      }

      attempts.add(new Object[]{pos.toImmutable(), player.getX(), player.getZ(), player.isSneaking(), cancel || restore.getOrDefault(pos, 0) > 0});
      return !cancel;
   }

   static void afterBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, Object entity) {
      try {
         breakPlane(world, player, pos);
      } finally {
         Integer n = restore.get(pos);
         if (!world.isClient && n != null && n > 0) {
            restore.put(pos.toImmutable(), n - 1);
            world.setBlockState(pos, state);
         }
      }
   }

   static void breakPlane(World world, PlayerEntity player, BlockPos pos) {
      ItemStack hand = player.getMainHandStack();
      if (breaking || world.isClient || player.isSneaking() || !hand.getName().getString().equals("3x3")) {
         return;
      }

      Direction.Axis axis = Direction.getFacing(player.getRotationVector()).getAxis();
      breaking = true;
      try {
         for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
               BlockPos p = switch (axis) {
                  case X -> pos.add(0, a, b);
                  case Y -> pos.add(a, 0, b);
                  case Z -> pos.add(a, b, 0);
               };
               BlockState s = world.getBlockState(p);
               float h = s.getHardness(world, p);
               if (!p.equals(pos) && !s.isAir() && h >= 0.0F && h <= 50.0F && s.getFluidState().isEmpty()) {
                  world.breakBlock(p, true, player);
               }
            }
         }
      } finally {
         breaking = false;
      }
   }
}
