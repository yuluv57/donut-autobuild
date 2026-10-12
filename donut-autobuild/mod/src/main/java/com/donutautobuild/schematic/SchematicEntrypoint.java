package com.donutautobuild.schematic;

import com.donutautobuild.AutoBuildMod;
import com.donutautobuild.gui.BuildScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

/** Routes the existing U/menu command to the schematic workspace. */
public final class SchematicEntrypoint {
    private static boolean installed;

    private SchematicEntrypoint() {}

    public static void install() {
        if (installed) return;
        installed = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> SchematicWorkspace.restorePreviewState());
    }

    public static void open(MinecraftClient client) {
        if (client == null || AutoBuildMod.config == null) return;
        if (AutoBuildMod.controller != null && (AutoBuildMod.controller.active() || AutoBuildMod.controller.wanted()))
            AutoBuildMod.controller.userStop("Đã dừng để mở menu AutoBuild. Nhấn Bắt đầu để tiếp tục.");
        SchematicWorkspace.enablePreview();
        client.setScreen(new BuildScreen(AutoBuildMod.config));
    }

    public static void browseLayer(MinecraftClient client, int direction) {
        String message = SchematicWorkspace.stepLayer(direction);
        if (client != null && client.player != null)
            client.player.sendMessage(net.minecraft.text.Text.literal(message), true);
    }
}
