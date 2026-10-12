package com.donutautobuild.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Responsive rows around the original 3.0.109 panel and its palette. */
public abstract class LegacyPanelScreen extends Screen {
    protected static final int ROW = 22, BG = -535816160, EDGE = -13677988;
    protected static final int ACCENT = -10365258, TEXT = -1510920, DIM = -6443327, RED = -34953, GREEN = -8592501;
    protected Layout layout;
    protected int scroll, rows;
    protected String message = "";
    private final List<RowWidget> rowWidgets = new ArrayList<>();
    private final List<RowLabel> rowLabels = new ArrayList<>();
    private ButtonWidget scrollUp, scrollDown;

    protected LegacyPanelScreen(Text title) { super(title); }
    public record Layout(int x, int y, int w, int h, int bodyTop, int bodyBottom, int footerTop) {
        public static Layout forSize(int width, int height) {
            int w = Math.min(360, Math.max(1, width - 12));
            int h = Math.min(394, Math.max(1, height - 12));
            int x = (width - w) / 2, y = (height - h) / 2;
            return new Layout(x, y, w, h, y + 42, y + h - 38, y + h - 26);
        }
        public int contentWidth() { return Math.max(1, w - 16); }
        public int contentHeight() { return Math.max(0, bodyBottom - bodyTop); }
        public int maxScroll(int rows) { return Math.max(0, rows * ROW - contentHeight()); }
        public int clampScroll(int value, int rows) { return Math.max(0, Math.min(value, maxScroll(rows))); }
    }
    private record RowWidget(ClickableWidget widget, int row) {}
    private record RowLabel(int row, int x, int w, Supplier<String> text, int color, Supplier<Double> progress) {}
    protected final void beginPanel() {
        layout = Layout.forSize(width, height); rowWidgets.clear(); rowLabels.clear();
    }
    protected final int x() { return layout.x + 8; }
    protected final int w() { return layout.contentWidth(); }
    protected final int half() { return (w() - 4) / 2; }
    protected final ButtonWidget button(int x, int y, int width, String label, Runnable action, String tip) {
        var builder = ButtonWidget.builder(Text.literal(label), b -> action.run()).dimensions(x, y, width, 18);
        if (tip != null) builder.tooltip(Tooltip.of(Text.literal(tip)));
        return addDrawableChild(builder.build());
    }
    protected final ButtonWidget rowButton(int row, int x, int width, String label, Runnable action, String tip) {
        ButtonWidget button = button(x, layout.bodyTop + row * ROW, width, label, action, tip);
        rowWidgets.add(new RowWidget(button, row)); return button;
    }
    protected final TextFieldWidget field(int row, int x, int width, String value, String label) {
        var field = new TextFieldWidget(textRenderer, x, layout.bodyTop + row * ROW, width, 18, Text.literal(label));
        field.setMaxLength(256); field.setText(value); field.setPlaceholder(Text.literal(label));
        addDrawableChild(field); rowWidgets.add(new RowWidget(field, row)); return field;
    }
    protected final void label(int row, Supplier<String> value, int color) { label(row, x(), w(), value, color); }
    protected final void label(int row, int x, int width, Supplier<String> value, int color) {
        rowLabels.add(new RowLabel(row, x, width, value, color, null));
    }
    protected final void progress(int row, Supplier<String> value, Supplier<Double> percent) {
        rowLabels.add(new RowLabel(row, x(), w(), value, TEXT, percent));
    }
    protected final void finishPanel(int count, String closeLabel, Runnable closeAction) {
        rows = count;
        button(x(), layout.footerTop, w() - 50, closeLabel, closeAction, null);
        scrollUp = button(x() + w() - 46, layout.footerTop, 22, "▲", () -> scrollBy(-ROW * 3), "Cuộn lên");
        scrollDown = button(x() + w() - 22, layout.footerTop, 22, "▼", () -> scrollBy(ROW * 3), "Cuộn xuống");
        updateRows();
    }
    protected final void scrollBy(int amount) { scroll = layout.clampScroll(scroll + amount, rows); updateRows(); }
    private void updateRows() {
        scroll = layout.clampScroll(scroll, rows);
        for (RowWidget entry : rowWidgets) {
            ClickableWidget widget = entry.widget;
            int y = layout.bodyTop + entry.row * ROW - scroll;
            widget.setY(y); widget.visible = y >= layout.bodyTop && y + widget.getHeight() <= layout.bodyBottom;
            if (!widget.visible && widget.isFocused()) {
                widget.setFocused(false); if (getFocused() == widget) setFocused(null);
            }
        }
        scrollUp.active = scroll > 0; scrollDown.active = scroll < layout.maxScroll(rows);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (layout != null && mouseY >= layout.bodyTop && mouseY < layout.bodyBottom) {
            scrollBy(-(int)Math.signum(verticalAmount) * ROW * 3); return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        if (layout == null) return;
        context.fill(layout.x - 1, layout.y - 1, layout.x + layout.w + 1, layout.y + layout.h + 1, EDGE);
        context.fill(layout.x, layout.y, layout.x + layout.w, layout.y + layout.h, BG);
        context.fill(layout.x, layout.y, layout.x + layout.w, layout.y + 14, -15326161);
    }
    protected String heading() { return getTitle().getString(); }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        if (layout == null) return;
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(heading(), w()), layout.x + 6, layout.y + 3, ACCENT);
        context.enableScissor(x(), layout.bodyTop, x() + w(), layout.bodyBottom);
        for (RowLabel label : rowLabels) {
            int y = layout.bodyTop + label.row * ROW - scroll + 4;
            if (y >= layout.bodyTop && y + 9 <= layout.bodyBottom) {
                context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(label.text.get(), label.w), label.x, y, label.color);
                if (label.progress != null) {
                    context.fill(label.x, y + 11, label.x + label.w, y + 14, -14273473);
                    double value = Math.max(0, Math.min(1, label.progress.get()));
                    context.fill(label.x, y + 11, label.x + (int)(label.w * value), y + 14, ACCENT);
                }
            }
        }
        context.disableScissor();
        if (!message.isEmpty()) context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(message, w()), x(), layout.footerTop - 10, RED);
    }
    @Override public boolean shouldPause() { return false; }
}
