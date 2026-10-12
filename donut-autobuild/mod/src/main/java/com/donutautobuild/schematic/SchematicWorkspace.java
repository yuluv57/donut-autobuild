package com.donutautobuild.schematic;

import com.donutautobuild.AutoBuildMod;
import com.donutautobuild.build.LayerView;
import com.donutautobuild.build.SchematicPlan;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.data.SchematicHolder;
import fi.dy.masa.litematica.render.LitematicaRenderer;
import fi.dy.masa.litematica.render.OverlayRenderer;
import fi.dy.masa.litematica.util.SchematicWorldRefresher;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.gui.interfaces.IMessageConsumer;
import fi.dy.masa.malilib.util.LayerMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import java.nio.file.*;
import java.util.*;

/** Client preview placement and a small reference library. Never pastes blocks or sends commands. */
public final class SchematicWorkspace {
    private static SchematicLibrary library;
    private static Path libraryProfile;
    private static String status = "Chọn sche, chỉnh vị trí rồi nhấn Nạp để chốt.";
    private static SchematicPlan committedPlan;
    private static boolean workflowTracked;
    private static Object workflowWorld;
    private static String requestedView;
    private static Integer requestedLayer;
    private static final IMessageConsumer MESSAGES = new IMessageConsumer() {
        public void addMessage(MessageType type, String key, Object... args) { status = net.minecraft.text.Text.translatable(key, args).getString(); }
        public void addMessage(MessageType type, int duration, String key, Object... args) { addMessage(type, key, args); }
    };
    private static MinecraftClient mc() { return MinecraftClient.getInstance(); }
    private static SchematicLibrary library() {
        Path profile = SchematicLibrary.normalize(mc().runDirectory.toPath());
        if (library == null || !profile.equals(libraryProfile)) {
            library = new SchematicLibrary(profile); libraryProfile = profile;
            committedPlan = null; workflowTracked = false; workflowWorld = null;
            requestedView = null; requestedLayer = null;
        }
        return library;
    }
    private static SchematicPlacementManager manager() { return DataManager.getSchematicPlacementManager(); }
    private static String say(String message) { status = message; return message; }
    public static String status() { return status; }
    public static Path libraryDirectory() { return library().directory(); }
    public static Path defaultDirectory() { return library().defaultDirectory(); }
    public static List<Path> listFiles(String filter) {
        try { return library().list(filter); } catch (Exception error) { say(error.getMessage()); return List.of(); }
    }
    public static List<Path> favorites() { return library().favorites(); }
    public static List<Path> recents() { return library().recents(); }
    public static boolean isFavorite(Path path) { return library().isFavorite(path); }
    public static String toggleFavorite(Path path) {
        if (path == null) return say("Chọn một file sche trước.");
        try { return say(library().toggleFavorite(path) ? "Đã lưu tham chiếu sche vào Đã lưu." : "Đã bỏ khỏi Đã lưu; file sche vẫn giữ nguyên."); }
        catch (Exception error) { return say(error.getMessage()); }
    }
    private static Path inputPath(String input) {
        input = input == null ? "" : input.strip();
        if (input.length() > 1 && input.startsWith("\"") && input.endsWith("\"")) input = input.substring(1, input.length() - 1);
        if (input.isEmpty()) throw new IllegalArgumentException("Dán đường dẫn file hoặc thư mục sche.");
        Path path = Path.of(input);
        return SchematicLibrary.normalize(path.isAbsolute() ? path : libraryDirectory().resolve(path));
    }
    public static String setLibraryDirectory(String input) {
        try { library().directory(inputPath(input)); return say("Thư viện: " + libraryDirectory()); }
        catch (Exception error) { return say(error.getMessage()); }
    }
    public static String applyPath(String input) {
        try { Path path = inputPath(input); return Files.isDirectory(path) ? setLibraryDirectory(path.toString()) : paste(path); }
        catch (Exception error) { return say(error.getMessage()); }
    }
    public static List<SchematicPlacement> placements() { return List.copyOf(manager().getAllSchematicsPlacements()); }
    public static SchematicPlacement selected() { return manager().getSelectedSchematicPlacement(); }
    public static boolean locked() { return selected() != null && selected().isLocked(); }
    private static void stopForEdit() {
        if (AutoBuildMod.controller != null && (AutoBuildMod.controller.active() || AutoBuildMod.controller.wanted()))
            AutoBuildMod.controller.userStop("Tạm dừng để chỉnh schematic; tiến độ đã xây vẫn giữ.");
    }
    private static void trackEditing() {
        workflowTracked = true; workflowWorld = mc().world; committedPlan = null;
    }
    /** Menu opening retains the player's native layer/visibility choice and the existing build plan. */
    public static void enablePreview() {
        library(); migrateNativeDisplay();
        if (requestedView == null) {
            requestedView = visible() ? (LayerView.viewing() == null ? "ALL" : "LAYER") : "HIDE";
            requestedLayer = LayerView.viewing();
        }
        if (!workflowTracked) {
            workflowTracked = true; workflowWorld = mc().world;
            if (AutoBuildMod.controller != null && AutoBuildMod.controller.qualitySchematicReady()) {
                try { committedPlan = new SchematicPlan(); } catch (RuntimeException ignored) { committedPlan = null; }
            }
        }
    }
    /** Revert only the visual options forced by 118/119, once; all other native preferences remain intact. */
    private static void migrateNativeDisplay() {
        if (!library().colorRules()) return;
        Configs.Colors.SCHEMATIC_OVERLAY_COLOR_MISSING.resetToDefault();
        Configs.Colors.SCHEMATIC_OVERLAY_COLOR_WRONG_STATE.resetToDefault();
        Configs.Visuals.SCHEMATIC_OVERLAY_TYPE_MISSING.resetToDefault();
        Configs.Visuals.SCHEMATIC_OVERLAY_TYPE_WRONG_STATE.resetToDefault();
        Configs.Visuals.SCHEMATIC_OVERLAY_ENABLE_SIDES.resetToDefault();
        Configs.Visuals.RENDER_BLOCKS_AS_TRANSLUCENT.resetToDefault();
        Configs.saveToFile();
        try { library().colorRules(false); } catch (Exception error) { say(error.getMessage()); }
        refreshRendering();
    }
    /** The legacy migration can refresh once; normal ticks never rebuild or delete render buffers. */
    public static void restorePreviewState() {
        if (mc() == null || mc().world == null || mc().player == null || (workflowWorld != null && workflowWorld != mc().world)) {
            committedPlan = null; workflowTracked = false; workflowWorld = null;
            requestedView = null; requestedLayer = null;
            return;
        }
        if (mc().runDirectory != null) { library(); migrateNativeDisplay(); }
        if (workflowTracked && committedPlan != null && !committedPlan.unchanged()) {
            committedPlan = null; stopForEdit(); say("Vị trí sche đã đổi; nhấn Nạp để chốt lại trước khi xây.");
        }
    }
    // Compatibility API for removed color mixins. Native Litematica alone now draws the schematic.
    public static boolean colorRulesEnabled() { return false; }
    public static boolean managesPosition(BlockPos pos) { return false; }
    public static void refreshRendering() {
        if (mc().world != null) {
            var renderer = LitematicaRenderer.getInstance().getWorldRenderer();
            if (renderer.hasWorld()) {
                SchematicWorldRefresher.INSTANCE.updateAll(); renderer.markNeedsUpdate();
            }
            manager().setVisibleSubChunksNeedsUpdate();
            OverlayRenderer.getInstance().updatePlacementCache();
        }
    }
    private static void saved() { DataManager.save(); }
    public static boolean isCommitted() {
        return workflowTracked && mc() != null && workflowWorld == mc().world && committedPlan != null && committedPlan.unchanged();
    }
    public static boolean isPlacementReady() { return isCommitted(); }
    public static boolean editing() { return workflowTracked && !isCommitted(); }
    public static String editPosition() {
        if (selected() == null) return say("Chọn schematic trước.");
        stopForEdit(); trackEditing();
        return say(selected().isLocked() ? "Sche đang có khóa của Litematica; mở khóa tại Litematica để sửa vị trí." :
            "Đang sửa vị trí. Chỉnh xong nhấn Nạp để chốt lại.");
    }
    public static String beginPositionEdit() { return editPosition(); }
    /** Called from native start/userStart too, so K cannot accidentally build an uncommitted preview. */
    public static boolean permitStart() {
        if (!workflowTracked || isCommitted()) return true;
        String reason = "Vị trí chưa chốt. Mở U rồi nhấn Nạp trước khi bắt đầu xây.";
        if (AutoBuildMod.controller != null) AutoBuildMod.controller.userStop(reason);
        say(reason);
        return false;
    }
    public static String selectPlacement(SchematicPlacement placement) {
        if (placement == null || !placements().contains(placement)) return say("Placement không còn tồn tại.");
        stopForEdit(); trackEditing(); manager().setSelectedSchematicPlacement(placement); saved(); refreshRendering();
        return say("Đã chọn " + placement.getName() + " · " + positionLabel());
    }
    public static String paste(Path file) {
        if (mc().player == null || mc().world == null) return say("Vào thế giới Minecraft trước khi paste sche.");
        if (file == null || !Files.isRegularFile(file)) return say("File sche không tồn tại: " + file);
        if (!SchematicLibrary.supported(file)) return say("Chọn file .litematic, .schematic, .schem hoặc .nbt.");
        try {
            file = SchematicLibrary.normalize(file);
            LitematicaSchematic schematic = SchematicHolder.getInstance().getOrLoad(file.toFile());
            if (schematic == null) return say("Không đọc được schematic; kiểm tra định dạng hoặc file bị hỏng.");
            stopForEdit(); trackEditing(); migrateNativeDisplay();
            String name = file.getFileName().toString().replaceFirst("\\.[^.]+$", "");
            Set<String> names = new HashSet<>(); for (var placement : placements()) names.add(placement.getName());
            String unique = name; for (int suffix = 2; names.contains(unique); suffix++) unique = name + " (" + suffix + ")";
            SchematicPlacement placement = SchematicPlacement.createFor(schematic, mc().player.getBlockPos(), unique, true, true);
            if (!placement.shouldRenderEnclosingBox()) placement.toggleRenderEnclosingBox();
            manager().addSchematicPlacement(placement, true); manager().setSelectedSchematicPlacement(placement);
            showAll(); saved();
            String warning = "";
            try { library().used(file); } catch (Exception error) { warning = " · Không lưu được tham chiếu: " + error.getMessage(); }
            return say("Đã đặt sche xem trước " + unique + "; chỉnh vị trí rồi nhấn Nạp để chốt." + warning);
        } catch (Exception error) {
            AutoBuildMod.LOGGER.warn("[AutoBuild] Schematic preview failed", error);
            return say("Không paste được sche: " + error.getMessage());
        }
    }
    private static SchematicPlacement editable() {
        SchematicPlacement placement = selected();
        if (placement == null) { say("Chọn hoặc paste schematic trước."); return null; }
        if (isCommitted()) { say("Vị trí đã chốt; nhấn Sửa vị trí trước khi chỉnh."); return null; }
        if (placement.isLocked()) { say("Sche đang có khóa của Litematica; mở khóa tại Litematica trước khi chỉnh."); return null; }
        if (!workflowTracked) trackEditing();
        return placement;
    }
    public static String nudge(int dx, int dy, int dz) {
        SchematicPlacement placement = editable(); if (placement == null) return status;
        return setPosition(placement.getOrigin().getX() + dx, placement.getOrigin().getY() + dy, placement.getOrigin().getZ() + dz);
    }
    public static BlockPos relativeOffset(Direction heading, String direction, int step) {
        if (heading.getAxis().isVertical()) heading = Direction.SOUTH;
        Direction move = switch (direction.toUpperCase(Locale.ROOT)) {
            case "LEFT" -> heading.rotateYCounterclockwise(); case "RIGHT" -> heading.rotateYClockwise();
            case "BACK" -> heading.getOpposite(); case "UP" -> Direction.UP; case "DOWN" -> Direction.DOWN;
            default -> heading;
        };
        return BlockPos.ORIGIN.offset(move, step);
    }
    public static String nudgeRelative(String direction, int step) {
        if (mc().player == null) return say("Vào thế giới Minecraft trước.");
        BlockPos offset = relativeOffset(mc().player.getHorizontalFacing(), direction, Math.max(1, Math.min(10, step)));
        return nudge(offset.getX(), offset.getY(), offset.getZ());
    }
    public static String setPosition(int x, int y, int z) {
        SchematicPlacement placement = editable(); if (placement == null) return status;
        BlockPos before = placement.getOrigin(), requested = new BlockPos(x, y, z);
        if (before.equals(requested)) return say("Vị trí không thay đổi: " + positionLabel());
        stopForEdit(); placement.setOrigin(requested, SchematicWorkspace::say); saved();
        return say((placement.getOrigin().equals(requested) ? "Đã di chuyển: " : "Một số trục đang khóa trong Litematica: ") + positionLabel());
    }
    public static String moveToPlayer() {
        if (mc().player == null) return say("Vào thế giới Minecraft trước.");
        BlockPos pos = mc().player.getBlockPos(); return setPosition(pos.getX(), pos.getY(), pos.getZ());
    }
    public static BlockRotation rotationAfter(BlockRotation current, int turns) {
        BlockRotation delta = switch (Math.floorMod(turns, 4)) { case 1 -> BlockRotation.CLOCKWISE_90;
            case 2 -> BlockRotation.CLOCKWISE_180; case 3 -> BlockRotation.COUNTERCLOCKWISE_90; default -> BlockRotation.NONE; };
        return current.rotate(delta);
    }
    public static String rotate(int quarterTurns) {
        SchematicPlacement placement = editable(); if (placement == null) return status;
        stopForEdit(); placement.setRotation(rotationAfter(placement.getRotation(), quarterTurns), MESSAGES);
        saved(); return say("Đã xoay quanh gốc sche · " + positionLabel());
    }
    /** Litematica mirror reflects local coordinates; X reverses X and Z reverses Z. */
    public static String mirror(boolean xAxis) {
        SchematicPlacement placement = editable(); if (placement == null) return status;
        BlockMirror desired = xAxis ? BlockMirror.FRONT_BACK : BlockMirror.LEFT_RIGHT;
        stopForEdit();
        if (placement.getMirror() == desired) placement.setMirror(BlockMirror.NONE, MESSAGES);
        else if (placement.getMirror() == BlockMirror.NONE) placement.setMirror(desired, MESSAGES);
        else {
            // Reflecting both local axes equals a half turn; retain the previous reflection geometrically.
            placement.setRotation(rotationAfter(placement.getRotation(), 2), MESSAGES);
            placement.setMirror(BlockMirror.NONE, MESSAGES);
        }
        saved(); return say("Đã đổi đối xứng " + (xAxis ? "X" : "Z") + " · " + positionLabel());
    }
    public static String toggleLock() {
        SchematicPlacement placement = selected(); if (placement == null) return say("Chọn hoặc paste schematic trước.");
        if (isCommitted()) return say("Vị trí đã chốt; nhấn Sửa vị trí trước khi chỉnh.");
        stopForEdit(); placement.toggleLocked(); saved(); return say(placement.isLocked() ? "Đã khóa placement." : "Đã mở khóa placement.");
    }
    private static ConfigBoolean[] contentOptions() {
        return new ConfigBoolean[]{Configs.Visuals.ENABLE_SCHEMATIC_BLOCKS, Configs.Visuals.ENABLE_SCHEMATIC_FLUIDS,
            Configs.Visuals.ENABLE_SCHEMATIC_OVERLAY, Configs.Visuals.RENDER_SCHEMATIC_ENTITIES, Configs.Visuals.RENDER_SCHEMATIC_TILE_ENTITIES};
    }
    private static void showContents(boolean visible) {
        for (var option : contentOptions()) option.setBooleanValue(visible);
        Configs.Visuals.ENABLE_RENDERING.setBooleanValue(true); Configs.Visuals.ENABLE_SCHEMATIC_RENDERING.setBooleanValue(true);
        Configs.Visuals.ENABLE_PLACEMENT_BOXES_RENDERING.setBooleanValue(true); Configs.Visuals.RENDER_PLACEMENT_ENCLOSING_BOX.setBooleanValue(true);
        SchematicPlacement placement = selected();
        if (placement != null) {
            if (!placement.shouldRenderEnclosingBox()) placement.toggleRenderEnclosingBox();
            if (visible && !placement.isRenderingEnabled()) placement.setRenderSchematic(true);
        }
    }
    public static boolean visible() {
        return Configs.Visuals.ENABLE_RENDERING.getBooleanValue() && Configs.Visuals.ENABLE_SCHEMATIC_RENDERING.getBooleanValue()
            && Configs.Visuals.ENABLE_SCHEMATIC_BLOCKS.getBooleanValue() && (selected() == null || selected().isRenderingEnabled());
    }
    public static String viewMode() {
        if (!visible()) return "HIDE";
        return LayerView.viewing() == null ? "ALL" : "LAYER";
    }
    public static String showAll() {
        requestedView = "ALL"; showContents(true); LayerView.browse(null);
        Configs.saveToFile(); saved(); refreshRendering(); return say("Đang xem tất cả tầng.");
    }
    public static String hideContents() {
        if (!"HIDE".equals(viewMode())) requestedLayer = LayerView.viewing();
        requestedView = "HIDE"; showContents(false); LayerView.browse(null);
        Configs.saveToFile(); saved(); refreshRendering(); return say("Đã ẩn bên trong sche; khung bao vẫn hiện.");
    }
    public static String showLayer(Integer y) {
        if (y == null) y = requestedLayer != null ? requestedLayer :
            (AutoBuildMod.controller != null && AutoBuildMod.controller.currentLayer() != null ? AutoBuildMod.controller.currentLayer() :
                (mc().player == null ? 0 : mc().player.getBlockY()));
        requestedView = "LAYER"; requestedLayer = y; showContents(true); LayerView.browse(y);
        Configs.saveToFile(); saved(); refreshRendering(); return say("Đang xem tầng Y=" + y + ".");
    }
    public static String stepLayer(int delta) {
        Integer y = "HIDE".equals(viewMode()) ? requestedLayer : LayerView.viewing();
        if (y == null) y = AutoBuildMod.controller != null && AutoBuildMod.controller.currentLayer() != null ?
            AutoBuildMod.controller.currentLayer() : (mc().player == null ? 0 : mc().player.getBlockY());
        else y += delta;
        if (AutoBuildMod.controller != null && AutoBuildMod.controller.hasPlan())
            y = Math.max(AutoBuildMod.controller.minY(), Math.min(AutoBuildMod.controller.maxY(), y));
        return showLayer(y);
    }
    /** Native layer progression follows building only when the user selected the single-layer view. */
    public static void syncLayer(int y) {
        if (workflowTracked && ("ALL".equals(requestedView) || "HIDE".equals(requestedView))) {
            if (DataManager.getRenderLayerRange().getLayerMode() != LayerMode.ALL) LayerView.browse(null);
            return;
        }
        LayerView.show(y);
        if (workflowTracked) { requestedView = "LAYER"; requestedLayer = y; }
    }
    public static String toggleVisible() { return visible() ? hideContents() : (requestedLayer == null ? showAll() : showLayer(requestedLayer)); }
    public static String positionLabel() {
        SchematicPlacement placement = selected(); if (placement == null) return "Chưa có placement";
        String rotation = switch (placement.getRotation()) { case CLOCKWISE_90 -> "90°"; case CLOCKWISE_180 -> "180°";
            case COUNTERCLOCKWISE_90 -> "270°"; default -> "0°"; };
        return "XYZ " + placement.getOrigin().toShortString() + " · Xoay " + rotation + " · Mirror " +
            switch (placement.getMirror()) { case FRONT_BACK -> "X"; case LEFT_RIGHT -> "Z"; default -> "tắt"; };
    }
    private static String readyToBuild() {
        if (mc().player == null || mc().world == null || AutoBuildMod.controller == null) return "Vào thế giới Minecraft trước.";
        SchematicPlacement placement = selected();
        if (placement == null) return "Chọn hoặc paste schematic trước.";
        if (!placement.isEnabled()) return "Placement đang bị tắt trong Litematica; bật placement trước khi xây.";
        return null;
    }
    public static String prepare() {
        String error = readyToBuild(); if (error != null) return say(error);
        stopForEdit(); workflowTracked = true; workflowWorld = mc().world; committedPlan = null;
        try {
            SchematicPlan snapshot = new SchematicPlan();
            AutoBuildMod.controller.prepare();
            committedPlan = snapshot; saved();
            return say("Đã chốt vị trí. " + AutoBuildMod.controller.status());
        } catch (RuntimeException exception) { return say(exception.getMessage()); }
    }
    public static String start() {
        String error = readyToBuild(); if (error != null) return say(error);
        if (!permitStart()) return status;
        if (mc().player.isCreative() || mc().player.isSpectator()) return say("AutoBuild cần chế độ Sinh tồn; xem trước sche vẫn dùng được.");
        mc().setScreen(null); AutoBuildMod.controller.userStart(); return say(AutoBuildMod.controller.status());
    }
}
