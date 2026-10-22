package com.donutautomine.test;

import net.fabricmc.api.ModInitializer;
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
 */
public final class Fake3x3Pickaxe implements ModInitializer {
   private static boolean breaking;

   @Override
   public void onInitialize() {
      PlayerBlockBreakEvents.AFTER.register(Fake3x3Pickaxe::afterBreak);
   }

   static void afterBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, Object entity) {
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
