package com.donutautomine.test.mixin;

import com.donutautomine.test.Fake3x3Pickaxe;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Anti-cheat giả như server thật: gói "đập xong" (STOP_DESTROY) cho khối chưa có "bắt đầu đập" (START_DESTROY) mới tương ứng → huỷ, gửi lại khối.
 * Server vanilla thì vẫn cho vỡ (nhớ vị trí đập cũ), DonutSMP thì trả lại khối — bot phải luôn gửi "bắt đầu đập" như người bấm chuột.
 */
@Mixin(value = ServerPlayerInteractionManager.class, remap = false)
public abstract class StrictDigMixin {
   @Shadow @Final protected ServerPlayerEntity player;
   @Shadow protected ServerWorld world;
   private BlockPos donutautomine$started;

   @Inject(method = "processBlockBreakingAction", at = @At("HEAD"), cancellable = true, remap = false)
   private void donutautomine$strict(BlockPos pos, PlayerActionC2SPacket.Action action, Direction direction, int worldHeight, int sequence, CallbackInfo ci) {
      if (action == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK) {
         this.donutautomine$started = pos.toImmutable();
      } else if (action == PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK) {
         this.donutautomine$started = null;
      } else if (action == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK) {
         boolean ok = pos.equals(this.donutautomine$started);
         this.donutautomine$started = null;
         if (!ok && Fake3x3Pickaxe.strictDig) {
            Fake3x3Pickaxe.strictRejects.incrementAndGet();
            this.player.networkHandler.sendPacket(new BlockUpdateS2CPacket(pos, this.world.getBlockState(pos)));
            ci.cancel();
         }
      }
   }
}
