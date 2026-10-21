package com.donutautomine.mine;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;

public final class BotInput extends Input {
   public static final BotInput INSTANCE = new BotInput();
   public boolean forward;
   public boolean back;
   public boolean left;
   public boolean right;
   public boolean jumping;
   public boolean sneaking;
   public boolean sprinting;
   public int sneakFree = 100;

   private BotInput() {
   }

   public void tick() {
      this.playerInput = new PlayerInput(this.forward, this.back, this.left, this.right, this.jumping, this.sneaking, this.sprinting);
      this.sneakFree = this.sneaking ? 0 : Math.min(1000, this.sneakFree + 1);
      this.movementForward = this.forward == this.back ? 0.0F : (this.forward ? 1.0F : -1.0F);
      this.movementSideways = this.left == this.right ? 0.0F : (this.left ? 1.0F : -1.0F);
   }

   public void clearMove() {
      this.forward = this.back = this.left = this.right = false;
      this.movementForward = this.movementSideways = 0.0F;
   }

   public void clear() {
      this.clearMove();
      this.sneaking = this.jumping = this.sprinting = false;
   }

   public static void install(MinecraftClient var0) {
      if (var0.player != null && var0.player.input != INSTANCE) {
         var0.player.input = INSTANCE;
      }
   }

   public static void uninstall(MinecraftClient var0) {
      INSTANCE.clear();
      if (var0.player != null && var0.player.input == INSTANCE) {
         var0.player.input = new KeyboardInput(var0.options);
      }
   }

   public static boolean installed(MinecraftClient var0) {
      return var0.player != null && var0.player.input == INSTANCE;
   }
}
