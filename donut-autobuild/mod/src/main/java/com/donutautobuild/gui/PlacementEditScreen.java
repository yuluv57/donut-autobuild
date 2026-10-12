package com.donutautobuild.gui;

import com.donutautobuild.schematic.SchematicWorkspace;
import java.util.function.Supplier;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** Placement controls stay separate from the original build menu. */
public final class PlacementEditScreen extends LegacyPanelScreen {
    private static int step = 1;
    private final BuildScreen parent;
    private TextFieldWidget xField, yField, zField;
    public PlacementEditScreen(BuildScreen parent) { super(Text.literal("Sửa vị trí schematic")); this.parent = parent; }
    @Override protected void init() {
        beginPanel();
        button(x(), layout.y() + 18, half(), "← Công trình", () -> client.setScreen(parent), null);
        button(x() + half() + 4, layout.y() + 18, w() - half() - 4, "Xem trong thế giới", this::close, null);
        label(0, SchematicWorkspace::positionLabel, TEXT);
        label(1, () -> SchematicWorkspace.locked() ? "Đang khóa trong Litematica; mở khóa tại Litematica." : "Trái / phải / trước / sau theo hướng người chơi.", DIM);
        BlockPos origin = SchematicWorkspace.selected() == null ? BlockPos.ORIGIN : SchematicWorkspace.selected().getOrigin();
        int third = (w() - 4) / 3;
        label(2, x(), third, () -> "X", DIM); label(2, x() + third + 2, third, () -> "Y", DIM);
        label(2, x() + third * 2 + 4, w() - third * 2 - 4, () -> "Z", DIM);
        xField = field(3, x(), third, String.valueOf(origin.getX()), "X");
        yField = field(3, x() + third + 2, third, String.valueOf(origin.getY()), "Y");
        zField = field(3, x() + third * 2 + 4, w() - third * 2 - 4, String.valueOf(origin.getZ()), "Z");
        transform(4, x(), half(), "Áp dụng XYZ", () -> {
            try { return SchematicWorkspace.setPosition(Integer.parseInt(xField.getText().trim()), Integer.parseInt(yField.getText().trim()), Integer.parseInt(zField.getText().trim())); }
            catch (NumberFormatException error) { return "XYZ phải là số nguyên."; }
        });
        transform(4, x() + half() + 4, w() - half() - 4, "Đưa đến người chơi", SchematicWorkspace::moveToPlayer);
        rowButton(5, x(), w(), "Bước di chuyển: " + step + " khối", () -> {
            step = step == 1 ? 5 : step == 5 ? 10 : 1; clearAndInit();
        }, "Đổi bước 1 / 5 / 10 khối.");
        transform(6, x(), half(), "↑ Trước", () -> SchematicWorkspace.nudgeRelative("FORWARD", step));
        transform(6, x() + half() + 4, w() - half() - 4, "↓ Sau", () -> SchematicWorkspace.nudgeRelative("BACK", step));
        transform(7, x(), half(), "← Trái", () -> SchematicWorkspace.nudgeRelative("LEFT", step));
        transform(7, x() + half() + 4, w() - half() - 4, "Phải →", () -> SchematicWorkspace.nudgeRelative("RIGHT", step));
        transform(8, x(), half(), "Lên +Y", () -> SchematicWorkspace.nudgeRelative("UP", step));
        transform(8, x() + half() + 4, w() - half() - 4, "Xuống −Y", () -> SchematicWorkspace.nudgeRelative("DOWN", step));
        transform(9, x(), half(), "Xoay −90°", () -> SchematicWorkspace.rotate(-1));
        transform(9, x() + half() + 4, w() - half() - 4, "Xoay +90°", () -> SchematicWorkspace.rotate(1));
        transform(10, x(), half(), "Mirror X", () -> SchematicWorkspace.mirror(true));
        transform(10, x() + half() + 4, w() - half() - 4, "Mirror Z", () -> SchematicWorkspace.mirror(false));
        label(11, () -> "Đóng menu để xem vị trí; U mở lại Công trình.", DIM);
        finishPanel(12, "§aNạp · Chốt vị trí để xây", () -> {
            message = SchematicWorkspace.prepare();
            if (SchematicWorkspace.isCommitted()) { parent.message = message; client.setScreen(parent); }
        });
    }
    private void transform(int row, int x, int width, String label, Supplier<String> action) {
        ButtonWidget button = rowButton(row, x, width, label, () -> { message = action.get(); clearAndInit(); }, null);
        button.active = SchematicWorkspace.selected() != null && !SchematicWorkspace.locked() && !SchematicWorkspace.isCommitted();
    }
}
