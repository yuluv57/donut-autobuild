package com.donutautomine.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.item.BlockItem;
import net.minecraft.util.ActionResult;
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
   /** ô mà nhát 3x3 không phá kèm (server bỏ sót) — chỉ vỡ khi đập thẳng vào */
   public static final Set<BlockPos> keep = ConcurrentHashMap.newKeySet();
   /** ô → số tick sau khi vỡ thì server đặt lại khối đó (1 lần) */
   public static final Map<BlockPos, Integer> restoreLater = new ConcurrentHashMap<>();
   private static final List<Object[]> pending = Collections.synchronizedList(new ArrayList<>());
   /** số lần người chơi đặt khối (chuột phải cầm khối) — kiểm tra "không bắc cầu / kê cột" */
   public static final java.util.concurrent.atomic.AtomicInteger placements = new java.util.concurrent.atomic.AtomicInteger();
   /** mọi nhát phá của người chơi (không tính 8 khối 3x3 kéo theo): {ô, x, z, đang ngồi, bị huỷ} */
   public static final List<Object[]> attempts = Collections.synchronizedList(new ArrayList<>());

   @Override
   public void onInitialize() {
      PlayerBlockBreakEvents.BEFORE.register(Fake3x3Pickaxe::beforeBreak);
      PlayerBlockBreakEvents.AFTER.register(Fake3x3Pickaxe::afterBreak);
      UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
         if (!world.isClient && player.getStackInHand(hand).getItem() instanceof BlockItem) {
            placements.incrementAndGet();
         }

         return ActionResult.PASS;
      });
      ServerTickEvents.END_SERVER_TICK.register(server -> {
         synchronized (pending) {
            pending.removeIf(o -> {
               o[2] = (Integer)o[2] - 1;
               if ((Integer)o[2] > 0) {
                  return false;
               }

               ((World)o[3]).setBlockState((BlockPos)o[0], (BlockState)o[1]);
               return true;
            });
         }
      });
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

      // điểm trúng trên khối (server tự bắn tia theo hướng nhìn) → kiểm tra mỗi lần đập lại có ngắm điểm khác không
      net.minecraft.util.hit.HitResult hit = player.raycast(6.0, 1.0F, false);
      attempts.add(new Object[]{pos.toImmutable(), player.getX(), player.getZ(), player.isSneaking(), cancel || restore.getOrDefault(pos, 0) > 0, hit.getPos()});
      return !cancel;
   }

   static void afterBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, Object entity) {
      try {
         breakPlane(world, player, pos);
      } finally {
         Integer later = restoreLater.remove(pos);
         if (!world.isClient && later != null) {
            pending.add(new Object[]{pos.toImmutable(), state, later, world});
         }

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
               if (!p.equals(pos) && !keep.contains(p) && !s.isAir() && h >= 0.0F && h <= 50.0F && s.getFluidState().isEmpty()) {
                  world.breakBlock(p, true, player);
               }
            }
         }
      } finally {
         breaking = false;
      }
   }
}
