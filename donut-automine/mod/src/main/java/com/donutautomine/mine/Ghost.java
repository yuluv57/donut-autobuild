package com.donutautomine.mine;

import com.donutautomine.AutoMineMod;
import com.donutautomine.MineConfig;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BoneMealItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class Ghost {
   private final MineConfig config;
   private final Deque<BlockPos> queue = new ArrayDeque<>();
   private final Set<BlockPos> queued = new LinkedHashSet<>();
   private final Map<BlockPos, Integer> lastSent = new HashMap<>();
   private int ticks;
   public int sentTotal;
   private final double[] hx = new double[64];
   private final double[] hy = new double[64];
   private final double[] hz = new double[64];
   private int hn;
   private Vec3d lastPos;
   private Vec3d lastVel;
   public int setbacks;
   public int lastSetbackTick = -1000;
   public BlockPos lastSetbackAt;

   public Ghost(MineConfig var1) {
      this.config = var1;
   }

   public void reset() {
      this.queue.clear();
      this.queued.clear();
      this.hn = 0;
      this.lastPos = null;
      this.lastVel = null;
   }

   public boolean busy() {
      return !this.queue.isEmpty();
   }

   public void request(BlockPos var1) {
      if (this.config.ghostResync && var1 != null) {
         BlockPos var2 = var1.toImmutable();
         Integer var3 = this.lastSent.get(var2);
         if (var3 == null || this.ticks - var3 >= 20) {
            if (this.queued.add(var2)) {
               this.queue.add(var2);
            }
         }
      }
   }

   public void requestAround(BlockPos var1, Direction var2) {
      if (var2 == null) {
         this.request(var1);
         this.request(var1.up());
      } else {
         BlockPos var3 = var1.offset(var2);
         this.request(var3);
         this.request(var3.up());
         this.request(var3.down());
         this.request(var1.up(2));
      }
   }

   private static Hand safeHand(MinecraftClient var0) {
      ItemStack var1 = var0.player.getOffHandStack();
      return !var1.isEmpty()
            && (
               var1.getItem() instanceof BlockItem
                  || var1.isOf(Items.WATER_BUCKET)
                  || var1.isOf(Items.LAVA_BUCKET)
                  || var1.isOf(Items.BUCKET)
                  || var1.isOf(Items.FLINT_AND_STEEL)
                  || var1.isOf(Items.FIRE_CHARGE)
                  || var1.getItem() instanceof BoneMealItem
            )
         ? null
         : Hand.OFF_HAND;
   }

   public void tick(MinecraftClient var1) {
      this.ticks++;
      if (!this.queue.isEmpty() && var1.player != null && var1.interactionManager != null && var1.currentScreen == null) {
         Hand var2 = safeHand(var1);
         if (var2 == null) {
            if (this.ticks % 200 == 0) {
               AutoMineMod.LOGGER.info("[AutoMine] Tay phụ đang cầm khối/xô → không gửi gói đồng bộ ghost block (để trống tay phụ)");
            }

            this.queue.clear();
            this.queued.clear();
         } else {
            Vec3d var3 = var1.player.getEyePos();
            double var4 = var1.player.getBlockInteractionRange() + 0.5;
            int var6 = 0;

            while (!this.queue.isEmpty() && var6 < this.config.resyncPerTick) {
               BlockPos var7 = this.queue.poll();
               this.queued.remove(var7);
               Vec3d var8 = Vec3d.ofCenter(var7);
               if (!(var8.squaredDistanceTo(var3) > var4 * var4)) {
                  Direction var9 = Direction.getFacing(var3.x - var8.x, var3.y - var8.y, var3.z - var8.z);
                  Vec3d var10 = var8.add(var9.getOffsetX() * 0.5, var9.getOffsetY() * 0.5, var9.getOffsetZ() * 0.5);
                  var1.interactionManager.interactBlock(var1.player, var2, new BlockHitResult(var10, var9, var7, false));
                  this.lastSent.put(var7, this.ticks);
                  var6++;
                  this.sentTotal++;
               }
            }

            if (this.lastSent.size() > 512) {
               this.lastSent.entrySet().removeIf(var1x -> this.ticks - var1x.getValue() > 200);
            }
         }
      }
   }

   public boolean watchMove(MinecraftClient var1, int var2, boolean var3) {
      Vec3d var4 = var1.player.getPos();
      boolean var5 = false;
      if (var3 && this.lastPos != null && this.lastVel != null) {
         double var6 = var4.x - this.lastPos.x;
         double var8 = var4.z - this.lastPos.z;
         double var10 = Math.sqrt(var6 * var6 + var8 * var8);
         double var12 = Math.hypot(var6 - this.lastVel.x, var8 - this.lastVel.z);
         if (var12 > 0.35 && var10 > 0.3 && this.inHistory(var4) || var10 > 2.5 && var12 > 2.0) {
            var5 = true;
            this.setbacks++;
            this.lastSetbackTick = var2;
            double var14 = Math.max(1.0E-4, Math.sqrt(this.lastVel.x * this.lastVel.x + this.lastVel.z * this.lastVel.z));
            this.lastSetbackAt = BlockPos.ofFloored(
               this.lastPos.x + this.lastVel.x / var14 * 0.8, this.lastPos.y + 0.1, this.lastPos.z + this.lastVel.z / var14 * 0.8
            );
            AutoMineMod.LOGGER.info("[AutoMine] Bị server giật lại gần {} → đồng bộ lại các ô phía trước", this.lastSetbackAt.toShortString());
         }
      }

      int var16 = Math.floorMod(this.hn, 64);
      this.hx[var16] = var4.x;
      this.hy[var16] = var4.y;
      this.hz[var16] = var4.z;
      this.hn++;
      this.lastPos = var4;
      this.lastVel = var1.player.getVelocity();
      return var5;
   }

   private boolean inHistory(Vec3d var1) {
      int var2 = Math.min(this.hn, 64);

      for (int var3 = 2; var3 <= var2; var3++) {
         int var4 = Math.floorMod(this.hn - var3, 64);
         double var5 = var1.x - this.hx[var4];
         double var7 = var1.z - this.hz[var4];
         if (var5 * var5 + var7 * var7 < 0.0225 && Math.abs(var1.y - this.hy[var4]) < 0.6) {
            return true;
         }
      }

      return false;
   }
}
