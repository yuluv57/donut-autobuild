package com.donutautobuild.build;

import com.donutautobuild.AutoBuildMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/** A detached visual camera; the survival player remains at the server position. */
public final class Freecam {
    private static KeyBinding toggleKey;
    private static ClientPlayerEntity owner;
    private static ClientWorld ownerWorld;
    private static net.minecraft.client.input.Input previousInput;
    private static FrozenInput frozenInput;
    private static boolean active;
    private static boolean previousCulling;
    private static double x, y, z;
    private static float yaw, pitch;
    private static long lastNanos;
    private static final double SPEED = 12.0;

    private Freecam() {}

    /** Called once from AutoBuildMod.onInitializeClient, before its build tick callback. */
    public static void install() {
        if (toggleKey != null) return;
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.donutautobuild.freecam", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N,
            "category.donutautobuild"));
        ClientTickEvents.START_CLIENT_TICK.register(Freecam::guard);
        ClientTickEvents.END_CLIENT_TICK.register(Freecam::tick);
    }

    public static boolean active() { return active; }

    /** Avoid taking over other entities when their look direction is updated. */
    public static boolean controlsPlayer(Entity player) {
        return active && player == owner;
    }

    private static boolean valid(MinecraftClient mc) {
        return mc.player != null && mc.player == owner && mc.world != null
            && mc.world == ownerWorld && mc.player.isAlive() && mc.getNetworkHandler() != null;
    }

    private static void tick(MinecraftClient mc) {
        while (toggleKey.wasPressed()) {
            if (mc.currentScreen == null) toggle(mc);
        }
        if (active && !valid(mc)) disable(mc);
        if (active) guard(mc);
    }

    public static void toggle(MinecraftClient mc) {
        if (active) {
            disable(mc);
            if (mc.player != null)
                mc.player.sendMessage(Text.literal("§b[AutoBuild] §fFreecam TẮT — trở về nhân vật."), true);
            return;
        }
        if (mc.player == null || mc.world == null || !mc.player.isAlive()
            || mc.getNetworkHandler() == null || Reconnector.active()) return;
        owner = mc.player;
        ownerWorld = mc.world;
        Vec3d eye = owner.getEyePos();
        x = eye.x; y = eye.y; z = eye.z;
        yaw = owner.getYaw(); pitch = owner.getPitch();
        previousInput = owner.input;
        frozenInput = new FrozenInput(owner.isSneaking());
        owner.input = frozenInput;
        previousCulling = mc.chunkCullingEnabled;
        lastNanos = System.nanoTime();
        active = true;
        setCulling(mc, false);
        guard(mc);
        owner.sendMessage(Text.literal(
            "§b[AutoBuild] §fFreecam BẬT — N để trở về; W/A/S/D bay, Space lên, Shift xuống, Ctrl bay nhanh. Tạm dừng xây."), true);
    }

    public static void disable(MinecraftClient mc) {
        if (!active) return;
        active = false;
        if (owner != null && owner.input == frozenInput) owner.input = previousInput;
        setCulling(mc, previousCulling);
        // Never restore old player coordinates or angles: server corrections remain authoritative.
        owner = null;
        ownerWorld = null;
        previousInput = null;
        frozenInput = null;
        lastNanos = 0L;
    }

    private static void setCulling(MinecraftClient mc, boolean enabled) {
        if (mc.chunkCullingEnabled == enabled) return;
        mc.chunkCullingEnabled = enabled;
        try {
            if (mc.worldRenderer != null) mc.worldRenderer.reload();
        } catch (RuntimeException error) {
            AutoBuildMod.LOGGER.warn("[AutoBuild] Không tải lại được renderer khi đổi Freecam: {}", error.toString());
        }
    }

    /** Prevent physical movement and interactions from the camera's controls. */
    private static void guard(MinecraftClient mc) {
        if (!active) return;
        if (!valid(mc)) { disable(mc); return; }
        mc.chunkCullingEnabled = false;
        if (owner.input != frozenInput) owner.input = frozenInput;
        frozenInput.tick();
        consume(mc.options.attackKey);
        consume(mc.options.useKey);
        consume(mc.options.dropKey);
        consume(mc.options.pickItemKey);
        consume(mc.options.swapHandsKey);
        Input.restoreBreaking(mc);
        if (mc.interactionManager != null && mc.interactionManager.isBreakingBlock())
            mc.interactionManager.cancelBlockBreaking();
    }

    private static void consume(KeyBinding key) {
        while (key.wasPressed()) {}
        key.setPressed(false);
    }

    /** Mouse deltas come from Entity.changeLookDirection instead of rotating the body. */
    public static void look(double mouseX, double mouseY) {
        if (!active) return;
        yaw += (float)(mouseX * 0.15);
        pitch = FreecamMotion.pitch(pitch, mouseY);
    }

    private static boolean down(MinecraftClient mc, KeyBinding binding) {
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(binding);
        if (key.getCode() < 0) return false;
        long window = mc.getWindow().getHandle();
        if (key.getCategory() == InputUtil.Type.MOUSE)
            return GLFW.glfwGetMouseButton(window, key.getCode()) == GLFW.GLFW_PRESS;
        if (key.getCategory() == InputUtil.Type.KEYSYM)
            return InputUtil.isKeyPressed(window, key.getCode());
        return binding.isPressed();
    }

    /** Runs once for each camera update. Coordinates belong only to the rendered camera. */
    public static double[] frame(MinecraftClient mc) {
        if (!active || !valid(mc)) { disable(mc); return null; }
        mc.chunkCullingEnabled = false;
        long now = System.nanoTime();
        double elapsed = (now - lastNanos) / 1.0e9;
        lastNanos = now;
        if (mc.currentScreen == null && mc.isWindowFocused()) {
            double forward = (down(mc, mc.options.forwardKey) ? 1 : 0)
                - (down(mc, mc.options.backKey) ? 1 : 0);
            double sideways = (down(mc, mc.options.rightKey) ? 1 : 0)
                - (down(mc, mc.options.leftKey) ? 1 : 0);
            double vertical = (down(mc, mc.options.jumpKey) ? 1 : 0)
                - (down(mc, mc.options.sneakKey) ? 1 : 0);
            double speed = SPEED * (down(mc, mc.options.sprintKey) ? 3.0 : 1.0);
            double[] delta = FreecamMotion.delta(yaw, forward, sideways, vertical, speed, elapsed);
            x += delta[0]; y += delta[1]; z += delta[2];
        }
        return new double[] {x, y, z, yaw, pitch};
    }

    private static final class FrozenInput extends net.minecraft.client.input.Input {
        private final boolean sneak;
        private FrozenInput(boolean sneak) { this.sneak = sneak; tick(); }
        @Override public void tick() {
            playerInput = new PlayerInput(false, false, false, false, false, sneak, false);
            movementForward = 0.0F;
            movementSideways = 0.0F;
        }
    }
}
