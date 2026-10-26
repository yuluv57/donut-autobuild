package com.donutautobuild.gui;

import com.donutautobuild.AutoBuildMod;
import com.donutautobuild.BuildConfig;
import com.donutautobuild.build.BuildController;
import com.donutautobuild.build.LayerView;
import com.donutautobuild.schematic.SchematicWorkspace;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** The 3.0.109 Công trình / Vật liệu / Cài đặt GUI, with placement editing added. */
public final class BuildScreen extends LegacyPanelScreen {
    private enum Tab { BUILD, MATERIALS, CRAFTER, SETTINGS }
    private static Tab tab = Tab.BUILD;
    private static boolean wholeBuild;
    private static Integer crafterLayer;
    private final BuildConfig config;
    private final BuildController controller;
    private TextFieldWidget yStart, yEnd, reach, delay, randomDelay, rotMin, rotMax, overshoot;
    private TextFieldWidget reactMin, reactMax, recheck, maxStacks, scaffold;
    private ButtonWidget startButton;

    public BuildScreen(BuildConfig config) {
        super(Text.literal("Donut Auto Build")); this.config = config; this.controller = AutoBuildMod.controller;
    }
    @Override protected void init() {
        beginPanel(); yStart = yEnd = null; startButton = null;
        String[] names = {"Công trình", "Vật liệu", "Crafter", "Cài đặt"};
        for (int i = 0; i < names.length; i++) {
            Tab next = Tab.values()[i]; int bx = x() + i * w() / names.length, end = x() + (i + 1) * w() / names.length;
            button(bx, layout.y() + 18, end - bx - 2, (tab == next ? "§a" : "") + names[i], () -> {
                if (saveYRange()) { tab = next; scroll = 0; clearAndInit(); }
            }, null);
        }
        int rows = switch (tab) { case BUILD -> initBuild(); case MATERIALS -> initMaterials(); case CRAFTER -> initCrafter(); case SETTINGS -> initSettings(); };
        finishPanel(rows, "Xem trong thế giới", this::close);
    }
    private int initBuild() {
        label(0, () -> "Placement: " + (SchematicWorkspace.selected() == null ? "Chưa chọn sche" : SchematicWorkspace.selected().getName()), TEXT);
        label(1, SchematicWorkspace::positionLabel, DIM);
        rowButton(2, x(), half(), "Chọn schematic", () -> {
            if (saveYRange()) client.setScreen(new SchematicFileScreen(this));
        }, "Chọn file trong thư mục schematics của profile Modrinth hoặc dán đường dẫn.");
        rowButton(2, x() + half() + 4, w() - half() - 4, "Sửa vị trí", () -> {
            if (!saveYRange()) return;
            message = SchematicWorkspace.editPosition();
            if (SchematicWorkspace.editing()) client.setScreen(new PlacementEditScreen(this));
        }, "Dừng xây, mở chỉnh vị trí. Chỉnh xong nhấn Nạp để chốt lại.");
        rowButton(3, x(), w(), SchematicWorkspace.isCommitted() ? "§aĐã nạp · Nạp lại" : "§aNạp · Chốt vị trí để xây", () -> {
            if (saveYRange()) { message = SchematicWorkspace.prepare(); clearAndInit(); }
        }, "Giữ vị trí schematic và nạp kế hoạch xây. Chưa bắt đầu xây.");
        startButton = rowButton(4, x(), half(), "Bắt đầu / Tiếp tục", () -> {
            if (!saveYRange()) return;
            message = SchematicWorkspace.start();
        }, "Nạp vị trí đã chỉnh trước, rồi bắt đầu xây.");
        startButton.active = SchematicWorkspace.isCommitted();
        rowButton(4, x() + half() + 4, w() - half() - 4, "Dừng, giữ tiến độ", () -> {
            if (saveYRange() && controller != null) controller.userStop("Đã dừng; giữ tiến độ đã xây.");
        }, null);
        previewControls(5, 6);
        rowButton(7, x(), w(), modeText(), () -> {
            config.buildMode = config.fastMode() ? "EXACT" : "FAST"; config.saveQuietly(); clearAndInit();
        }, "Giữ nguyên hai chế độ xây NHANH và ĐÚNG THIẾT KẾ.");
        label(8, x(), half(), () -> "Y đầu (trống = tất cả)", DIM);
        label(8, x() + half() + 4, w() - half() - 4, () -> "Y cuối", DIM);
        yStart = field(9, x(), half(), config.yStart == null ? "" : config.yStart.toString(), "Y đầu");
        yEnd = field(9, x() + half() + 4, w() - half() - 4, config.yEnd == null ? "" : config.yEnd.toString(), "Y cuối");
        rowButton(10, x(), w(), "Y đầu = tầng chỗ đang đứng", () -> {
            if (client != null && client.player != null) yStart.setText(String.valueOf(client.player.getBlockY()));
            yEnd.setText("");
        }, "Bắt đầu tại tầng ngay trên khối dưới chân.");
        rowButton(11, x(), half(), "Khôi phục bản lưu", () -> {
            if (!saveYRange()) return;
            if (!SchematicWorkspace.isCommitted()) { message = "Nhấn Nạp để chốt vị trí trước khi khôi phục."; return; }
            if (controller != null) { close(); controller.restore(); }
        }, null);
        rowButton(11, x() + half() + 4, w() - half() - 4, "Thu dọn khối kê", () -> {
            if (saveYRange() && controller != null) { close(); controller.cleanup(); }
        }, null);
        label(12, () -> "Trạng thái: " + (controller == null ? "Chưa vào thế giới" : controller.status()), TEXT);
        progress(13, this::layerProgress, () -> controller == null || controller.layerTotal() <= 0 ? 0.0 : (double)controller.layerDone() / controller.layerTotal());
        progress(14, () -> controller == null ? "Tổng công trình: —" : "Tổng: " + controller.completed() + " / " + controller.total() + " · " + pct(controller.completed(), controller.total()) + controller.rangeLabel(),
            () -> controller == null || controller.total() <= 0 ? 0.0 : (double)controller.completed() / controller.total());
        label(15, () -> controller == null ? "" : "Mục tiêu: " + controller.targetLabel() + " · Khối kê: " + controller.temporaryCount(), DIM);
        label(16, () -> controller == null ? "" : controller.progressDetail(), DIM);
        return 17;
    }
    private void previewControls(int modeRow, int layerRow) {
        int third = (w() - 4) / 3;
        rowButton(modeRow, x(), third, "Từng tầng", () -> action(() -> SchematicWorkspace.showLayer(null)), "Xem một tầng Y; PgUp/PgDn đổi tầng.");
        rowButton(modeRow, x() + third + 2, third, "Tất cả", () -> action(SchematicWorkspace::showAll), "Xem toàn bộ schematic.");
        rowButton(modeRow, x() + third * 2 + 4, w() - third * 2 - 4, "Ẩn bên trong", () -> action(SchematicWorkspace::hideContents), "Ẩn khối và nước bên trong, giữ khung bao.");
        rowButton(layerRow, x(), 28, "▼", () -> action(() -> SchematicWorkspace.stepLayer(-1)), null);
        rowButton(layerRow, x() + 30, w() - 60, viewLabel(), () -> action(() -> SchematicWorkspace.showLayer(controller == null ? null : controller.currentLayer())), "Về tầng đang xây.");
        rowButton(layerRow, x() + w() - 28, 28, "▲", () -> action(() -> SchematicWorkspace.stepLayer(1)), null);
    }
    private void action(Supplier<String> action) { if (saveYRange()) { message = action.get(); clearAndInit(); } }
    private String viewLabel() {
        return switch (SchematicWorkspace.viewMode()) {
            case "HIDE" -> "Đang ẩn bên trong";
            case "LAYER" -> "Đang xem tầng Y=" + LayerView.viewing();
            default -> "Đang xem tất cả tầng";
        };
    }
    private String modeText() { return config.fastMode() ? "Chế độ: §eNHANH" : "Chế độ: §aĐÚNG THIẾT KẾ"; }
    private String layerProgress() {
        if (controller == null) return "Tầng: —";
        Integer y = controller.currentLayer();
        String prefix = y == null ? "Tầng: —" : "Tầng " + controller.layerIndex(y) + "/" + controller.layerCount() + " (Y=" + y + ")";
        return prefix + ": " + controller.layerDone() + " / " + controller.layerTotal() + " · " + pct(controller.layerDone(), controller.layerTotal());
    }
    private static String pct(int done, int total) { return total <= 0 ? "0.0%" : String.format("%.1f%%", done * 100.0 / total); }
    private boolean saveYRange() {
        if (yStart == null || yEnd == null || tab != Tab.BUILD) return true;
        try {
            Integer begin = parseY(yStart.getText()), end = parseY(yEnd.getText());
            config.yStart = begin; config.yEnd = end; config.save(); return true;
        } catch (NumberFormatException error) { message = "Y đầu / Y cuối phải là số nguyên hoặc để trống."; }
        catch (Exception error) { message = "Không lưu được cấu hình."; }
        return false;
    }
    private static Integer parseY(String value) { value = value.trim(); return value.isEmpty() ? null : Integer.valueOf(value); }
    private int initMaterials() {
        rowButton(0, x(), w(), wholeBuild ? "Đang xem: toàn công trình" : "Đang xem: tầng hiện tại", () -> {
            wholeBuild = !wholeBuild; scroll = 0; clearAndInit();
        }, null);
        int nameWidth = w() - 126;
        label(1, x(), nameWidth, () -> "Vật liệu", ACCENT);
        label(1, x() + nameWidth + 4, 40, () -> "Cần", ACCENT);
        label(1, x() + nameWidth + 46, 40, () -> "Có", ACCENT);
        label(1, x() + nameWidth + 88, 38, () -> "Thiếu", ACCENT);
        List<BuildController.MaterialRow> materials = controller == null ? List.of() : wholeBuild ? controller.totalMaterials() : controller.layerMaterials();
        int row = 2;
        if (materials.isEmpty()) {
            label(row++, () -> controller != null && controller.hasPlan() ? "Không còn vật liệu cần đặt." : "Nhấn Nạp ở tab Công trình trước.", DIM);
        }
        for (BuildController.MaterialRow material : materials) {
            int missing = Math.max(0, material.needed() - material.have());
            label(row, x(), nameWidth, () -> material.item().getName().getString(), TEXT);
            label(row, x() + nameWidth + 4, 40, () -> String.valueOf(material.needed()), TEXT);
            label(row, x() + nameWidth + 46, 40, () -> String.valueOf(material.have()), TEXT);
            label(row++, x() + nameWidth + 88, 38, () -> missing == 0 ? "đủ" : String.valueOf(missing), missing == 0 ? GREEN : RED);
        }
        return row;
    }
    private int initSettings() {
        int labelWidth = half() - 58, right = x() + half() + 4;
        String[] leftNames = {"Tầm với (2–4.5)", "Delay đặt (tick)", "Delay ngẫu nhiên", "Xoay min °/t", "Xoay max °/t", "Lố góc (%)"};
        String[] rightNames = {"Phản xạ min", "Phản xạ max", "Order (giây)", "Stack/lần"};
        for (int row = 0; row < leftNames.length; row++) { final String name = leftNames[row]; label(row, x(), labelWidth, () -> name, DIM); }
        for (int row = 0; row < rightNames.length; row++) { final String name = rightNames[row]; label(row, right, labelWidth, () -> name, DIM); }
        reach = field(0, x() + labelWidth, 58, String.valueOf(config.reach), "Tầm với");
        delay = field(1, x() + labelWidth, 58, String.valueOf(config.placeDelayTicks), "Delay đặt");
        randomDelay = field(2, x() + labelWidth, 58, String.valueOf(config.randomDelayTicks), "Delay ngẫu nhiên");
        rotMin = field(3, x() + labelWidth, 58, String.valueOf(config.rotationMinSpeed), "Xoay min");
        rotMax = field(4, x() + labelWidth, 58, String.valueOf(config.rotationMaxSpeed), "Xoay max");
        overshoot = field(5, x() + labelWidth, 58, String.valueOf(config.overshootPercent), "Lố góc");
        reactMin = field(0, right + labelWidth, w() - half() - 4 - labelWidth, String.valueOf(config.reactionMinTicks), "Phản xạ min");
        reactMax = field(1, right + labelWidth, w() - half() - 4 - labelWidth, String.valueOf(config.reactionMaxTicks), "Phản xạ max");
        recheck = field(2, right + labelWidth, w() - half() - 4 - labelWidth, String.valueOf(config.orderRecheckSeconds), "Order");
        maxStacks = field(3, right + labelWidth, w() - half() - 4 - labelWidth, String.valueOf(config.materialMaxStacks), "Stack/lần");
        label(4, right, 44, () -> "Khối kê", DIM);
        scaffold = field(4, right + 44, w() - half() - 48, config.scaffoldItem, "Khối kê");
        toggle(6, x(), half(), "Lấy từ Order", () -> config.collectFromOrders, value -> config.collectFromOrders = value);
        toggle(6, right, w() - half() - 4, "Kê tự động", () -> config.scaffold, value -> config.scaffold = value);
        toggle(7, x(), half(), "Xây theo tầng", () -> config.layerMode, value -> config.layerMode = value);
        toggle(7, right, w() - half() - 4, "Thiếu đồ lên tầng", () -> config.skipLayerWhenMissing, value -> config.skipLayerWhenMissing = value);
        toggle(8, x(), half(), "Hiện HUD", () -> config.showHud, value -> config.showHud = value);
        toggle(8, right, w() - half() - 4, "Theo tầng đang xây", () -> config.syncLitematicaLayer, value -> config.syncLitematicaLayer = value);
        toggle(9, x(), half(), "Kê đúng thiết kế", () -> config.exactBlocks, value -> config.exactBlocks = value);
        toggle(9, right, w() - half() - 4, "Tự đập khối sai", () -> config.fixWrong, value -> config.fixWrong = value);
        toggle(10, x(), half(), "Tắt Easy Place", () -> config.disableEasyPlace, value -> config.disableEasyPlace = value);
        // 3.0.123: home dành cho mod. "Không dùng" = mod không bao giờ /home, /sethome (kẹt thì thoát ra vào lại).
        // Home N = home trống bạn giao cho mod: từ tầng 3 mỗi tầng mod xoá home N cũ rồi /sethome N, lỡ ngã thì /home N.
        rowButton(10, right, 28, "◀", () -> setModHome(config.modHomeNumber - 1), "Đổi số home cho mod");
        ButtonWidget[] home = new ButtonWidget[1];
        home[0] = rowButton(10, right + 30, w() - half() - 34, homeText(), () -> { setModHome(config.modHomeNumber + 1); home[0].setMessage(Text.literal(homeText())); },
            "Không dùng: mod không bao giờ /home, /sethome. Home N: chọn 1 home còn TRỐNG giao cho mod — từ tầng 3 mod tự xoá home N cũ và /sethome N ở mỗi tầng, lỡ ngã thì /home N. Các home khác mod không đụng tới.");
        rowButton(11, x(), w(), "§aLưu cài đặt", this::saveSettings, null);
        return 12;
    }
    /**
     * 3.0.124 (người dùng: "GUI cho crafter như trước"): danh sách tầng có crafter, lưới 3×3 bấm để tắt/mở từng ô cho cả tầng,
     * "Theo bản vẽ" để bỏ chỉnh tay. Bit i của mask = ô i bị tắt.
     */
    private int initCrafter() {
        toggle(0, x(), w(), "Tự chỉnh ô crafter khi đặt", () -> config.configureCrafters, value -> config.configureCrafters = value);
        List<Integer> layers = controller == null ? List.of() : controller.crafterLayers();
        if (layers.isEmpty()) {
            label(1, () -> controller != null && controller.hasPlan() ? "Bản vẽ không có crafter." : "Nhấn Nạp ở tab Công trình trước.", DIM);
            return 2;
        }
        if (crafterLayer == null || !layers.contains(crafterLayer)) crafterLayer = layers.get(0);
        final int y = crafterLayer;
        Integer override = controller.crafterLayerOverride(y);
        int schematic = controller.schematicCrafterMask(y), kinds = controller.schematicCrafterKinds(y);
        int mask = override != null ? override : Math.max(0, schematic);
        label(1, () -> "Tầng Y=" + y + " · " + controller.crafterCount(y) + " crafter · " + (override != null ? "§eđang chỉnh tay" : "§atheo bản vẽ")
            + (kinds > 1 && override == null ? " §7(bản vẽ có " + kinds + " kiểu, mỗi crafter theo kiểu của nó)" : ""), TEXT);
        int cell = Math.min(60, (w() - 8) / 3), left = x() + (w() - cell * 3 - 4) / 2;
        for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) {
            final int bit = 1 << (r * 3 + c);
            boolean off = (mask & bit) != 0;
            rowButton(2 + r, left + c * (cell + 2), cell, off ? "§c✕ Tắt" : "§a■ Mở", () -> {
                Integer cur = controller.crafterLayerOverride(y);
                int base = cur != null ? cur : Math.max(0, controller.schematicCrafterMask(y));
                controller.setCrafterLayerOverride(y, base ^ bit); clearAndInit();
            }, "Bấm để tắt/mở ô này cho mọi crafter tầng Y=" + y);
        }
        rowButton(5, x(), w(), override == null ? "§7Theo bản vẽ (đang dùng)" : "Theo bản vẽ", () -> { controller.setCrafterLayerOverride(y, null); clearAndInit(); },
            "Bỏ chỉnh tay: mỗi crafter lấy đúng kiểu trong bản vẽ" + (schematic < 0 ? " (bản vẽ không có dữ liệu → không chỉnh)" : ""));
        label(6, () -> "Các tầng có crafter:", ACCENT);
        int row = 7;
        for (int layer : layers) {
            final int ly = layer;
            Integer o = controller.crafterLayerOverride(ly);
            int k = controller.schematicCrafterKinds(ly);
            rowButton(row++, x(), w(), (ly == y ? "§a▶ " : "") + "Y=" + ly + " · " + controller.crafterCount(ly) + " crafter · bản vẽ " + k + " kiểu" + (o != null ? " · §echỉnh tay" : ""),
                () -> { crafterLayer = ly; clearAndInit(); }, null);
        }
        return row;
    }
    private String homeText() { return config.modHomeNumber <= 0 ? "Home cho mod: §eKhông dùng" : "Home cho mod: §aHome " + config.modHomeNumber; }
    private void setModHome(int number) {
        int next = number < 0 ? 10 : number > 10 ? 0 : number;
        if (next != config.modHomeNumber) {
            config.modHomeNumber = next; config.useHome = next > 0;
            // đổi số home → quên home cũ mod đã đặt (không /home về số cũ nữa)
            config.ownHomeNumber = 0; config.ownHomeManual = false; config.ownHomeLayer = Integer.MIN_VALUE;
            config.saveQuietly();
        }
        if (tab == Tab.SETTINGS) clearAndInit();
    }
    private void toggle(int row, int x, int width, String name, Supplier<Boolean> get, Consumer<Boolean> set) {
        ButtonWidget[] widget = new ButtonWidget[1];
        widget[0] = rowButton(row, x, width, name + ": " + (get.get() ? "BẬT" : "TẮT"), () -> {
            set.accept(!get.get()); config.saveQuietly();
            widget[0].setMessage(Text.literal(name + ": " + (get.get() ? "BẬT" : "TẮT")));
        }, null);
    }
    private void saveSettings() {
        try {
            double nextReach = Double.parseDouble(reach.getText().trim()), min = Double.parseDouble(rotMin.getText().trim()), max = Double.parseDouble(rotMax.getText().trim());
            if (!Double.isFinite(nextReach) || !Double.isFinite(min) || !Double.isFinite(max)) throw new NumberFormatException();
            int nextDelay = Integer.parseInt(delay.getText().trim()), random = Integer.parseInt(randomDelay.getText().trim()), over = Integer.parseInt(overshoot.getText().trim());
            int minReaction = Integer.parseInt(reactMin.getText().trim()), maxReaction = Integer.parseInt(reactMax.getText().trim());
            int order = Integer.parseInt(recheck.getText().trim()), stacks = Integer.parseInt(maxStacks.getText().trim());
            config.reach = nextReach; config.rotationMinSpeed = min; config.rotationMaxSpeed = Math.max(min, max);
            config.placeDelayTicks = nextDelay; config.randomDelayTicks = random; config.overshootPercent = over;
            config.reactionMinTicks = minReaction; config.reactionMaxTicks = Math.max(minReaction, maxReaction);
            config.orderRecheckSeconds = order; config.materialMaxStacks = stacks; config.scaffoldItem = scaffold.getText().trim();
            config.save(); message = "§aĐã lưu cài đặt."; clearAndInit();
        } catch (NumberFormatException error) { message = "Kiểm tra lại các ô số."; }
        catch (Exception error) { message = "Không lưu được cấu hình."; }
    }
    @Override protected String heading() { return "Donut Auto Build · " + (controller == null ? "Menu" : controller.stageName()); }
    @Override public void tick() { if (startButton != null) startButton.active = SchematicWorkspace.isCommitted(); }
    @Override public void close() { if (saveYRange()) super.close(); }
}
