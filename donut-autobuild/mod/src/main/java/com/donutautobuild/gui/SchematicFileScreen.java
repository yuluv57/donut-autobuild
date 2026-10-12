package com.donutautobuild.gui;

import com.donutautobuild.schematic.SchematicWorkspace;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** File references remain in the Modrinth profile; the build menu stays compact. */
public final class SchematicFileScreen extends LegacyPanelScreen {
    private enum Source { FOLDER, SAVED, RECENT, PLACEMENTS }
    private final BuildScreen parent;
    private Source source = Source.FOLDER;
    private Path selectedFile;
    private String pathText = "", filterText = "";
    private TextFieldWidget pathField, filterField;
    public SchematicFileScreen(BuildScreen parent) { super(Text.literal("Chọn schematic")); this.parent = parent; }
    @Override protected void init() {
        beginPanel();
        button(x(), layout.y() + 18, w(), "← Công trình", () -> { capture(); client.setScreen(parent); }, null);
        if (pathText.isEmpty()) pathText = SchematicWorkspace.libraryDirectory().toString();
        label(0, () -> "Thư mục sche hoặc đường dẫn file", DIM);
        pathField = field(1, x(), w() - 70, pathText, "Đường dẫn file / thư mục"); pathField.setMaxLength(2048);
        rowButton(1, x() + w() - 66, 66, "Áp dụng", () -> {
            capture(); var beforePlacement = SchematicWorkspace.selected(); message = SchematicWorkspace.applyPath(pathText);
            try {
                String clean = pathText.trim();
                if (clean.length() > 1 && clean.startsWith("\"") && clean.endsWith("\"")) clean = clean.substring(1, clean.length() - 1);
                Path path = Path.of(clean); if (!path.isAbsolute()) path = SchematicWorkspace.libraryDirectory().resolve(path);
                if (Files.isRegularFile(path) && SchematicWorkspace.selected() != beforePlacement && matchesSelected(path)) { client.setScreen(new PlacementEditScreen(parent)); return; }
            } catch (RuntimeException ignored) { }
            scroll = 0; clearAndInit();
        }, "Dán đường dẫn file để đặt xem trước, hoặc chọn thư mục làm thư viện.");
        filterField = field(2, x(), w() - 54, filterText, "Lọc tên schematic");
        rowButton(2, x() + w() - 50, 50, "Lọc", () -> { capture(); scroll = 0; clearAndInit(); }, null);
        int quarter = (w() - 6) / 4;
        String[] names = {"Thư mục", "Đã lưu", "Gần đây", "Đã đặt"};
        for (int i = 0; i < names.length; i++) {
            Source next = Source.values()[i]; int bx = x() + i * (quarter + 2);
            rowButton(3, bx, i == 3 ? x() + w() - bx : quarter, (source == next ? "§a" : "") + names[i], () -> {
                capture(); source = next; scroll = 0; clearAndInit();
            }, null);
        }
        rowButton(4, x(), w(), "§aĐặt sche đã chọn để chỉnh vị trí", this::pasteSelected, "Đặt bản xem trước; chỉnh xong nhấn Nạp để chốt.");
        label(5, () -> selectedFile == null ? "Chọn file bên dưới. ★ lưu tham chiếu file." : "Đã chọn: " + selectedFile.getFileName(), ACCENT);
        int row = 6;
        if (source == Source.PLACEMENTS) {
            for (var placement : SchematicWorkspace.placements()) {
                if (!placement.getName().toLowerCase(Locale.ROOT).contains(filterText.toLowerCase(Locale.ROOT))) continue;
                rowButton(row++, x(), w(), placement.getName(), () -> {
                    message = SchematicWorkspace.selectPlacement(placement); parent.message = message; client.setScreen(parent);
                }, placement.getOrigin().toShortString());
            }
        } else {
            List<Path> files = switch (source) {
                case FOLDER -> SchematicWorkspace.listFiles(filterText);
                case SAVED -> filter(SchematicWorkspace.favorites());
                case RECENT -> filter(SchematicWorkspace.recents());
                default -> List.of();
            };
            for (Path path : files) {
                rowButton(row, x(), w() - 28, (sameFile(path, selectedFile) ? "§a> " : "") + path.getFileName(), () -> {
                    capture(); selectedFile = path; scroll = 0; clearAndInit();
                }, path.toString());
                rowButton(row++, x() + w() - 24, 24, SchematicWorkspace.isFavorite(path) ? "★" : "☆", () -> {
                    capture(); message = SchematicWorkspace.toggleFavorite(path); clearAndInit();
                }, "Lưu tham chiếu; file gốc giữ nguyên.");
            }
        }
        if (row == 6) label(row++, () -> "Không tìm thấy schematic trong danh sách này.", DIM);
        rowButton(row++, x(), w(), "Thư mục schematic của profile Modrinth", () -> {
            capture(); message = SchematicWorkspace.setLibraryDirectory(SchematicWorkspace.defaultDirectory().toString());
            pathText = SchematicWorkspace.libraryDirectory().toString(); source = Source.FOLDER; scroll = 0; clearAndInit();
        }, "Đọc trực tiếp schematics của profile Minecraft/Modrinth đang chạy.");
        finishPanel(row, "Xem trong thế giới", this::close);
    }
    private void capture() { if (pathField != null) pathText = pathField.getText(); if (filterField != null) filterText = filterField.getText(); }
    private List<Path> filter(List<Path> files) { return files.stream().filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).contains(filterText.toLowerCase(Locale.ROOT))).toList(); }
    private static boolean sameFile(Path a, Path b) { return a != null && b != null && a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize()); }
    private static boolean matchesSelected(Path path) {
        return SchematicWorkspace.selected() != null && SchematicWorkspace.selected().getSchematicFile() != null && sameFile(path, SchematicWorkspace.selected().getSchematicFile().toPath());
    }
    private void pasteSelected() {
        capture();
        if (selectedFile == null) { message = "Chọn một file schematic trước."; return; }
        var beforePlacement = SchematicWorkspace.selected(); message = SchematicWorkspace.paste(selectedFile);
        if (SchematicWorkspace.selected() != beforePlacement && matchesSelected(selectedFile)) client.setScreen(new PlacementEditScreen(parent));
    }
    @Override public void close() { capture(); super.close(); }
}
