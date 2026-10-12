package com.donutautobuild;

import com.donutautobuild.gui.BuildScreen;
import com.donutautobuild.gui.LegacyPanelScreen;
import com.donutautobuild.gui.PlacementEditScreen;
import com.donutautobuild.gui.SchematicFileScreen;
import com.donutautobuild.schematic.SchematicWorkspace;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Items;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.util.math.BlockPos;

/** Real 1.21.4 widgets and Litematica placements; no graphics context is required. */
public final class LegacyGuiRegression {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static final class TestFont extends TextRenderer {
        TestFont() { super(id -> null, false); }
        @Override public int getWidth(String value) { return value.length() * 6; }
        @Override public int getWidth(StringVisitable value) { return getWidth(value.getString()); }
        @Override public int getWidth(OrderedText value) { return 80; }
        @Override public String trimToWidth(String value, int width) { return trimToWidth(value, width, false); }
        @Override public String trimToWidth(String value, int width, boolean backwards) {
            int count = Math.min(value.length(), Math.max(0, width / 6));
            return backwards ? value.substring(value.length() - count) : value.substring(0, count);
        }
    }
    private static Object read(Object owner, Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }
    private static void write(Object owner, Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
    }
    private static void init(LegacyPanelScreen screen, MinecraftClient client, TestFont font, int width, int height) throws Exception {
        write(screen, Screen.class, "client", client); write(screen, Screen.class, "textRenderer", font);
        write(screen, Screen.class, "width", width); write(screen, Screen.class, "height", height);
        Method init = screen.getClass().getDeclaredMethod("init"); init.setAccessible(true); init.invoke(screen);
        client.currentScreen = screen;
    }
    private static ButtonWidget button(Screen screen, String name) {
        return screen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
            .filter(widget -> widget.getMessage().getString().contains(name)).findFirst().orElseThrow(() -> new AssertionError("Missing button: " + name));
    }
    private static void bounds(LegacyPanelScreen screen, int width, int height) {
        var layout = LegacyPanelScreen.Layout.forSize(width, height);
        List<ClickableWidget> visible = new ArrayList<>();
        for (var child : screen.children()) if (child instanceof ClickableWidget widget) {
            if (!widget.visible) {
                check(!widget.mouseClicked(widget.getX() + 1, widget.getY() + 1, 0), "hidden native row widgets cannot receive mouse clicks"); continue;
            }
            check(widget.getX() >= 0 && widget.getX() + widget.getWidth() <= width, "native widget fits horizontal screen bounds");
            check(widget.getY() >= 0 && widget.getY() + widget.getHeight() <= height, "native widget fits vertical screen bounds");
            check(widget.getY() < layout.bodyTop() || widget.getY() >= layout.footerTop() || widget.getY() + widget.getHeight() <= layout.bodyBottom(),
                "body widget never overlaps the footer");
            visible.add(widget);
        }
        for (int i = 0; i < visible.size(); i++) for (int j = i + 1; j < visible.size(); j++) {
            var a = visible.get(i); var b = visible.get(j);
            check(a.getX() + a.getWidth() <= b.getX() || b.getX() + b.getWidth() <= a.getX() ||
                a.getY() + a.getHeight() <= b.getY() || b.getY() + b.getHeight() <= a.getY(), "native widget hit areas do not overlap");
        }
        check(button(screen, "Xem trong thế giới").visible, "return to the world remains reachable while scrolling");
    }
    private static void scrollCoverage(LegacyPanelScreen screen, int width, int height) {
        var layout = LegacyPanelScreen.Layout.forSize(width, height);
        bounds(screen, width, height);
        screen.mouseScrolled(width / 2.0, layout.bodyTop() + 3, 0, -1000); bounds(screen, width, height);
        screen.mouseScrolled(width / 2.0, layout.bodyTop() + 3, 0, 1000); bounds(screen, width, height);
    }
    public static int run() throws Exception {
        checks = 0;
        BuildConfig beforeConfig = AutoBuildMod.config; var beforeController = AutoBuildMod.controller;
        var clientField = RecoveryRegression.field(MinecraftClient.class, "instance"); Object beforeClient = clientField.get(null);
        var manager = DataManager.getSchematicPlacementManager(); var beforePlacement = manager.getSelectedSchematicPlacement();
        var layers = DataManager.getRenderLayerRange();
        Object beforeRefresher = RecoveryRegression.field(fi.dy.masa.malilib.util.LayerRange.class, "refresher").get(layers);
        Path profile = Files.createTempDirectory("autobuild-legacy-gui-"); Path folder = Files.createDirectory(profile.resolve("schematics"));
        for (int i = 0; i < 35; i++) Files.writeString(folder.resolve(String.format("sche-%02d.litematic", i)), "file-reference fixture");
        Files.writeString(folder.resolve("ignore.txt"), "unrelated file");
        var session = new RecoveryRegression.Session(Items.COBBLESTONE, 1, 50); var font = new TestFont();
        WaterlogAimRegression.position(session.player, new net.minecraft.util.math.Vec3d(10.5, 64, 20.5));
        RecoveryRegression.object(session.mc, MinecraftClient.class, "runDirectory", profile.toFile());
        RecoveryRegression.object(session.mc, MinecraftClient.class, "textRenderer", font);
        RecoveryRegression.object(session.mc, MinecraftClient.class, "navigationType", net.minecraft.client.gui.navigation.GuiNavigationType.MOUSE);
        clientField.set(null, session.mc); AutoBuildMod.config = new BuildConfig(); AutoBuildMod.controller = null;
        layers.setRefresher(new fi.dy.masa.malilib.interfaces.IRangeChangeListener() {
            public void updateAll() {} public void updateBetweenX(int a, int b) {}
            public void updateBetweenY(int a, int b) {} public void updateBetweenZ(int a, int b) {}
        });
        var area = new fi.dy.masa.litematica.selection.AreaSelection(); area.setExplicitOrigin(BlockPos.ORIGIN);
        area.addSubRegionBox(new fi.dy.masa.litematica.selection.Box(BlockPos.ORIGIN, new BlockPos(1, 1, 1), "gui"), false);
        var schematic = fi.dy.masa.litematica.schematic.LitematicaSchematic.createEmptySchematic(area, "gui");
        var placement = SchematicPlacement.createFor(schematic, new BlockPos(10, 64, 20), "existing-gui", true, true);
        manager.addSchematicPlacement(placement, false); manager.setSelectedSchematicPlacement(placement);
        SchematicWorkspace.setLibraryDirectory(folder.toString()); SchematicWorkspace.editPosition();
        int[][] sizes = {{320, 240}, {426, 240}, {640, 360}, {854, 480}, {1920, 1080}};
        try {
            for (int[] size : sizes) {
                var layout = LegacyPanelScreen.Layout.forSize(size[0], size[1]);
                check(layout.x() >= 0 && layout.y() >= 0 && layout.x() + layout.w() <= size[0] && layout.y() + layout.h() <= size[1], "old panel palette and controls fit every GUI scale");
                check(layout.contentHeight() >= 140, "smallest GUI scale retains usable scrollable body");
                var screen = new BuildScreen(AutoBuildMod.config); init(screen, session.mc, font, size[0], size[1]);
                button(screen, "Công trình").onPress();
                check(!screen.shouldPause(), "menu does not pause the Minecraft client world");
                check(screen.children().stream().noneMatch(child -> child instanceof ButtonWidget b && b.getMessage().getString().contains("Bị chặn")), "unneeded blocked tab is absent");
                check(screen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                    .filter(b -> b.getY() < layout.bodyTop()).count() == 3, "109 menu has only Công trình, Vật liệu and Cài đặt tabs");
                check(!button(screen, "Bắt đầu").active, "build cannot start before the placement is committed by Nạp");
                scrollCoverage(screen, size[0], size[1]);
                var begin = (TextFieldWidget)read(screen, BuildScreen.class, "yStart");
                begin.setText("not-a-Y"); button(screen, "Nạp").onPress();
                check(((String)read(screen, LegacyPanelScreen.class, "message")).contains("số nguyên"), "invalid Y shows a readable validation error");
                check(session.mc.currentScreen == screen, "invalid Nạp never closes the GUI");
                begin.setText(""); button(screen, "Nạp").onPress();
                check(((String)read(screen, LegacyPanelScreen.class, "message")).contains("Vào thế giới"), "Nạp validates the current world/controller before committing");
                check(!button(screen, "Bắt đầu").active, "failed Nạp keeps Start disabled");
                button(screen, "Tất cả").onPress(); check("ALL".equals(SchematicWorkspace.viewMode()), "All native preview mode works from the old GUI");
                button(screen, "Ẩn bên trong").onPress(); check("HIDE".equals(SchematicWorkspace.viewMode()), "Hide clears schematic contents through one mode button");
                button(screen, "Từng tầng").onPress(); check("LAYER".equals(SchematicWorkspace.viewMode()), "single layer restores preview after Hide");
                button(screen, "Vật liệu").onPress(); scrollCoverage(screen, size[0], size[1]);
                check(button(screen, "Đang xem:").visible, "original material scope toggle remains available");
                button(screen, "Cài đặt").onPress(); scrollCoverage(screen, size[0], size[1]);
                var reach = (TextFieldWidget)read(screen, BuildScreen.class, "reach");
                reach.setText("4.2"); button(screen, "Hiện HUD").onPress();
                check(((TextFieldWidget)read(screen, BuildScreen.class, "reach")).getText().equals("4.2"), "a settings toggle preserves other unsaved setting fields");
                int previousDelay = AutoBuildMod.config.placeDelayTicks;
                reach.setText("NaN"); button(screen, "Lưu cài đặt").onPress();
                check(AutoBuildMod.config.placeDelayTicks == previousDelay && ((String)read(screen, LegacyPanelScreen.class, "message")).contains("ô số"), "invalid numeric settings are rejected without partial mutation");
                reach.setText("4.2");
                screen.setFocused(reach); reach.setFocused(true);
                screen.mouseScrolled(size[0] / 2.0, layout.bodyTop() + 3, 0, -1000);
                if (!reach.visible) check(!reach.isFocused() && screen.getFocused() != reach && !reach.charTyped('5', 0), "scrolling a field out of view clears keyboard focus");
                var edit = new PlacementEditScreen(screen); init(edit, session.mc, font, size[0], size[1]);
                scrollCoverage(edit, size[0], size[1]);
                BlockPos before = placement.getOrigin();
                ((TextFieldWidget)read(edit, PlacementEditScreen.class, "xField")).setText("bad");
                button(edit, "Áp dụng XYZ").onPress();
                check(placement.getOrigin().equals(before) && ((String)read(edit, LegacyPanelScreen.class, "message")).contains("số nguyên"), "invalid XYZ cannot mutate placement origin");
                button(edit, "Phải").onPress(); button(edit, "Trái").onPress();
                check(placement.getOrigin().equals(before), "native GUI right and left movement are inverses");
                button(edit, "Lên +Y").onPress();
                check(placement.getOrigin().getY() == before.getY() + 1, "the default movement step is one block upward");
                button(edit, "Xuống").onPress(); check(placement.getOrigin().equals(before), "up/down GUI buttons restore origin");
                button(edit, "Xoay +90").onPress(); button(edit, "Xoay −90").onPress();
                check(placement.getRotation() == net.minecraft.util.BlockRotation.NONE, "GUI rotation buttons preserve native rotation semantics");
                placement.toggleLocked();
                var lockedEdit = new PlacementEditScreen(screen); init(lockedEdit, session.mc, font, size[0], size[1]);
                check(!button(lockedEdit, "Phải").active && !button(lockedEdit, "Áp dụng XYZ").active, "existing Litematica locks disable movement controls");
                placement.toggleLocked();
                var picker = new SchematicFileScreen(screen); init(picker, session.mc, font, size[0], size[1]);
                scrollCoverage(picker, size[0], size[1]);
                var path = (TextFieldWidget)read(picker, SchematicFileScreen.class, "pathField");
                var filter = (TextFieldWidget)read(picker, SchematicFileScreen.class, "filterField");
                path.setText(folder.toString()); filter.setText("sche-03"); button(picker, "Lọc").onPress();
                check(picker.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).filter(b -> b.getMessage().getString().contains("sche-03.litematic")).count() == 1,
                    "file picker filters schematic references by name");
                check(picker.children().stream().noneMatch(child -> child instanceof ButtonWidget b && b.getMessage().getString().contains("ignore.txt")), "file picker ignores unrelated files");
                button(picker, "sche-03.litematic").onPress();
                check(folder.resolve("sche-03.litematic").equals(read(picker, SchematicFileScreen.class, "selectedFile")), "selecting a schematic retains the original direct file reference");
                check(manager.getSelectedSchematicPlacement() == placement, "file selection alone never pastes or alters existing placement");
                button(picker, "Đặt sche").onPress();
                check(session.mc.currentScreen == picker && manager.getSelectedSchematicPlacement() == placement, "failed preview load leaves current GUI and placement intact");
                check(((String)read(picker, LegacyPanelScreen.class, "message")).contains("Vào thế giới"), "preview validation failure remains readable");
                if (!SchematicWorkspace.isFavorite(folder.resolve("sche-03.litematic"))) button(picker, "☆").onPress();
                button(picker, "Đã lưu").onPress();
                check(picker.children().stream().anyMatch(child -> child instanceof ButtonWidget b && b.getMessage().getString().contains("sche-03.litematic")), "saved references remain available in the small picker");
                ((TextFieldWidget)read(picker, SchematicFileScreen.class, "filterField")).setText("");
                button(picker, "Lọc").onPress(); button(picker, "Đã đặt").onPress();
                check(picker.children().stream().anyMatch(child -> child instanceof ButtonWidget b && b.getMessage().getString().contains("existing-gui")), "already placed schematics can be selected without loading duplicate files");
            }
        } finally {
            manager.removeSchematicPlacement(placement, false); manager.setSelectedSchematicPlacement(beforePlacement);
            AutoBuildMod.config = beforeConfig; AutoBuildMod.controller = beforeController;
            layers.setRefresher((fi.dy.masa.malilib.interfaces.IRangeChangeListener)beforeRefresher);
            clientField.set(null, beforeClient);
        }
        System.out.println("PASS: " + checks + " legacy 109 GUI, movement and commit workflow assertions.");
        return checks;
    }
}
