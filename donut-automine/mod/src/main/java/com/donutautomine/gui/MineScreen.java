package com.donutautomine.gui;

import com.donutautomine.MineConfig;
import com.donutautomine.mine.MineController;
import com.donutautomine.mine.Region;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class MineScreen extends Screen {
   private final MineConfig config;
   private final MineController controller;
   private int page;
   private int scroll;
   private int maxScroll;
   private int left;
   private int column;
   private final List<ClickableWidget> content = new ArrayList<>();
   private final Map<ClickableWidget, Integer> originalY = new HashMap<>();
   private final List<MineScreen.Label> labels = new ArrayList<>();
   private TextFieldWidget sx;
   private TextFieldWidget sy;
   private TextFieldWidget sz;
   private TextFieldWidget ox;
   private TextFieldWidget oy;
   private TextFieldWidget oz;
   private String message = "";
   private int moveStep = 1;

   public MineScreen(MineConfig var1, MineController var2) {
      super(Text.literal("Donut AutoMine 1.14.2"));
      this.config = var1;
      this.controller = var2;
   }

   private void button(String var1, int var2, int var3, int var4, Runnable var5) {
      this.addContent(ButtonWidget.builder(Text.literal(var1), var1x -> var5.run()).dimensions(var2, var3, var4, 20).build());
   }

   private <T extends ClickableWidget> T addContent(T var1) {
      this.content.add(var1);
      this.originalY.put(var1, var1.getY());
      return (T)this.addDrawableChild(var1);
   }

   private void toggle(String var1, int var2, int var3, BooleanSupplier var4, Consumer<Boolean> var5) {
      this.addContent(ButtonWidget.builder(Text.literal(var1 + ": " + (var4.getAsBoolean() ? "BẬT" : "TẮT")), var4x -> {
         var5.accept(!var4.getAsBoolean());
         this.config.saveQuietly();
         var4x.setMessage(Text.literal(var1 + ": " + (var4.getAsBoolean() ? "BẬT" : "TẮT")));
      }).dimensions(var2, var3, this.column, 20).build());
   }

   private void number(String var1, int var2, int var3, LongSupplier var4, LongConsumer var5, long var6, long var8, long var10) {
      ButtonWidget var12 = ButtonWidget.builder(Text.literal(var1 + ": " + var4.getAsLong()), var0 -> {})
         .dimensions(var2 + 22, var3, this.column - 44, 20)
         .build();
      var12.active = false;
      this.addContent(var12);
      this.button("−", var2, var3, 20, () -> {
         var5.accept(Math.max(var8, var4.getAsLong() - var6));
         this.config.saveQuietly();
         var12.setMessage(Text.literal(var1 + ": " + var4.getAsLong()));
      });
      this.button("+", var2 + this.column - 20, var3, 20, () -> {
         var5.accept(Math.min(var10, var4.getAsLong() + var6));
         this.config.saveQuietly();
         var12.setMessage(Text.literal(var1 + ": " + var4.getAsLong()));
      });
   }

   private TextFieldWidget field(String var1, int var2, int var3, int var4, int var5) {
      this.labels.add(new MineScreen.Label(var1, var2, var3 + 5));
      TextFieldWidget var6 = new TextFieldWidget(this.textRenderer, var2 + 20, var3, var4 - 20, 20, Text.literal(var1));
      var6.setMaxLength(11);
      var6.setText(Integer.toString(var5));
      return this.addContent(var6);
   }

   protected void init() {
      this.content.clear();
      this.originalY.clear();
      this.labels.clear();
      this.sx = this.sy = this.sz = this.ox = this.oy = this.oz = null;
      this.column = Math.min(176, (this.width - 24) / 2);
      this.left = this.width / 2 - this.column - 4;
      String[] var1 = new String[]{"Vùng đào", "Tự động", "Tiếp tế", "Khác"};
      int var2 = (this.column * 2 + 8) / 4;

      for (int var3 = 0; var3 < var1.length; var3++) {
         int var4 = var3;
         ButtonWidget var5 = ButtonWidget.builder(Text.literal(var1[var3]), var2x -> {
            this.page = var4;
            this.scroll = 0;
            this.clearAndInit();
         }).dimensions(this.left + var3 * var2, 54, var2 - 2, 20).build();
         var5.active = this.page != var3;
         this.addDrawableChild(var5);
      }

      int var7 = this.left + this.column + 8;
      byte var8 = 80;
      if (this.page == 0) {
         this.button("Chế độ: " + (this.config.customRegion ? "Tạo vùng theo kích thước" : "Khung Litematica"), this.left, var8, this.column * 2 + 8, () -> {
            this.config.customRegion = !this.config.customRegion;
            this.config.saveQuietly();
            this.clearAndInit();
         });
         int var9 = (this.column * 2 + 8) / 3;
         this.sx = this.field("X", this.left, var8 + 24, var9 - 3, this.config.regionSizeX);
         this.sy = this.field("Y", this.left + var9, var8 + 24, var9 - 3, this.config.regionSizeY);
         this.sz = this.field("Z", this.left + 2 * var9, var8 + 24, var9 - 3, this.config.regionSizeZ);
         this.ox = this.field("gX", this.left, var8 + 48, var9 - 3, this.config.regionX);
         this.oy = this.field("gY", this.left + var9, var8 + 48, var9 - 3, this.config.regionTopY);
         this.oz = this.field("gZ", this.left + 2 * var9, var8 + 48, var9 - 3, this.config.regionZ);
         this.button("Tạo dưới chân (X+, Z+)", this.left, var8 + 72, this.column, () -> {
            if (this.commitRegion(false)) {
               this.controller.createRegionAtPlayer();
               this.config.showRegionOutline = true;
               this.config.saveQuietly();
               this.clearAndInit();
            }
         });
         this.button("Áp dụng góc trên gX/Y/Z", var7, var8 + 72, this.column, () -> {
            if (this.commitRegion(true) && this.controller.loadRegion()) {
               this.config.showRegionOutline = true;
               this.config.saveQuietly();
               this.message = "Đã tạo vùng; Y đào từ trên xuống.";
            }
         });
         this.toggle("Khung viền", this.left, var8 + 96, () -> this.config.showRegionOutline, var1x -> this.config.showRegionOutline = var1x);
         this.toggle("Ẩn vật trong sche", var7, var8 + 96, () -> this.config.hideSchematicContents, SchematicView::contents);
         int var6 = (this.column * 2 + 8) / 3;
         this.button("← X−", this.left, var8 + 120, var6 - 3, () -> this.shiftRegion(-1, 0, 0));
         this.button("Y+ ↑", this.left + var6, var8 + 120, var6 - 3, () -> this.shiftRegion(0, 1, 0));
         this.button("X+ →", this.left + 2 * var6, var8 + 120, var6 - 3, () -> this.shiftRegion(1, 0, 0));
         this.button("Z−", this.left, var8 + 144, var6 - 3, () -> this.shiftRegion(0, 0, -1));
         this.button("Y− ↓", this.left + var6, var8 + 144, var6 - 3, () -> this.shiftRegion(0, -1, 0));
         this.button("Z+", this.left + 2 * var6, var8 + 144, var6 - 3, () -> this.shiftRegion(0, 0, 1));
         this.button("Nạp khung Litematica đã chọn", this.left, var8 + 168, this.column * 2 + 8, () -> {
            this.config.customRegion = false;
            this.config.saveQuietly();
            this.controller.loadRegion();
            this.clearAndInit();
         });
         this.addContent(ButtonWidget.builder(Text.literal("Bước dịch: " + this.moveStep + " khối"), var1x -> {
            this.moveStep = this.moveStep == 1 ? 10 : 1;
            var1x.setMessage(Text.literal("Bước dịch: " + this.moveStep + " khối"));
         }).dimensions(this.left, var8 + 192, this.column * 2 + 8, 20).build());
      } else if (this.page == 1) {
         this.toggle("Tự ăn", this.left, var8, () -> this.config.autoEat, var1x -> this.config.autoEat = var1x);
         this.toggle("XP sửa cúp", var7, var8, () -> this.config.xpRepair, var1x -> this.config.xpRepair = var1x);
         this.toggle("Mua XP", this.left, var8 + 24, () -> this.config.buyXp, var1x -> this.config.buyXp = var1x);
         this.toggle("Tự bán", var7, var8 + 24, () -> this.config.autoSell, var1x -> this.config.autoSell = var1x);
         this.toggle("Đồng bộ ghost", this.left, var8 + 48, () -> this.config.ghostResync, var1x -> this.config.ghostResync = var1x);
         this.toggle("Chặn nước/lava", var7, var8 + 48, () -> this.config.plugFluids, var1x -> this.config.plugFluids = var1x);
         this.toggle("Vứt rác khi bán lỗi", this.left, var8 + 72, () -> this.config.dropJunkIfSellFails, var1x -> this.config.dropJunkIfSellFails = var1x);
         this.toggle("Hiện HUD", var7, var8 + 72, () -> this.config.showHud, var1x -> this.config.showHud = var1x);
         this.toggle("Chạy khi Alt-Tab", this.left, var8 + 96, () -> this.config.disablePauseOnLostFocus, var1x -> this.config.disablePauseOnLostFocus = var1x);
      } else if (this.page == 2) {
         this.number("Đói thì ăn", this.left, var8, () -> this.config.eatAtHunger, var1x -> this.config.eatAtHunger = (int)var1x, 1L, 1L, 19L);
         this.number("Sửa cúp tại", var7, var8, () -> this.config.repairAt, var1x -> this.config.repairAt = (int)var1x, 25L, 5L, 2000L);
         this.number("XP mỗi lần", this.left, var8 + 24, () -> this.config.xpBuyAmount, var1x -> this.config.xpBuyAmount = (int)var1x, 8L, 1L, 64L);
         this.number("Giá XP tối đa", var7, var8 + 24, () -> this.config.shopMaxPrice, var1x -> this.config.shopMaxPrice = var1x, 50000L, 0L, 100000000L);
         this.number("Ô trống để bán", this.left, var8 + 48, () -> this.config.sellWhenFree, var1x -> this.config.sellWhenFree = (int)var1x, 1L, 0L, 10L);
         this.number("Giữ khối kê", var7, var8 + 48, () -> this.config.keepFiller, var1x -> this.config.keepFiller = (int)var1x, 32L, 64L, 576L);
         this.toggle("Lấy khối kê từ Order", this.left, var8 + 96, () -> this.config.refillFillerFromOrder, var1x -> this.config.refillFillerFromOrder = var1x);
         this.number("Máu tối thiểu", this.left, var8 + 72, () -> this.config.minHealth, var1x -> this.config.minHealth = (int)var1x, 1L, 1L, 19L);
         this.number("Dừng cúp tại", var7, var8 + 72, () -> this.config.hardStopDurability, var1x -> this.config.hardStopDurability = (int)var1x, 5L, 1L, 100L);
      } else {
         this.labels.add(new MineScreen.Label("Tên cúp 3x3:", this.left, var8 + 5));
         TextFieldWidget var10 = new TextFieldWidget(this.textRenderer, this.left + 86, var8, this.column * 2 - 78, 20, Text.literal("Tên cúp"));
         var10.setMaxLength(100);
         var10.setText(this.config.pickaxeHint);
         var10.setChangedListener(var1x -> {
            this.config.pickaxeHint = var1x.trim();
            this.config.saveQuietly();
         });
         this.addContent(var10);
         this.number("Tốc độ freecam", this.left, var8 + 24, () -> (long)this.config.freecamSpeed, var1x -> this.config.freecamSpeed = var1x, 2L, 1L, 80L);
         this.number("Resync/tick", var7, var8 + 24, () -> this.config.resyncPerTick, var1x -> this.config.resyncPerTick = (int)var1x, 1L, 1L, 6L);
         this.labels.add(new MineScreen.Label("Quay mặt: tức thì (1.14.0)", this.left, var8 + 53));
         this.button("Đặt lại tiến độ đào", this.left, var8 + 72, this.column * 2 + 8, () -> {
            this.controller.resetProgress();
            this.message = "Đã đặt lại tiến độ; sẽ quét toàn vùng.";
         });
      }

      this.maxScroll = Math.max(0, (this.page == 0 ? 302 : 228) - (this.height - 52));
      this.scroll = Math.min(this.scroll, this.maxScroll);
      this.positionContent();
      this.addDrawableChild(ButtonWidget.builder(Text.literal(this.controller.running() ? "Tạm dừng" : "Bắt đầu / Chạy tiếp"), var1x -> {
         if (this.page != 0 || !this.config.customRegion || this.commitRegion(this.config.regionPlaced)) {
            this.close();
            this.controller.toggle();
         }
      }).dimensions(this.left, this.height - 40, this.column, 20).build());
      this.addDrawableChild(ButtonWidget.builder(Text.literal("Đóng"), var1x -> this.close()).dimensions(var7, this.height - 40, this.column, 20).build());
   }

   private boolean commitRegion(boolean var1) {
      try {
         int var2 = Integer.parseInt(this.sx.getText());
         int var3 = Integer.parseInt(this.sy.getText());
         int var4 = Integer.parseInt(this.sz.getText());
         if (var2 >= 1 && var3 >= 1 && var4 >= 1 && var2 <= 200 && var3 <= 200 && var4 <= 200) {
            int var5 = 0;
            int var6 = 0;
            int var7 = 0;
            if (var1) {
               var5 = Integer.parseInt(this.ox.getText());
               var6 = Integer.parseInt(this.oy.getText());
               var7 = Integer.parseInt(this.oz.getText());
               if (Math.abs((long)var5) > 29999900L || Math.abs((long)var7) > 29999900L) {
                  throw new NumberFormatException();
               }

               if (this.client != null
                  && this.client.world != null
                  && ((long)var6 - var3 + 1L < this.client.world.getBottomY() || var6 > this.client.world.getTopYInclusive())) {
                  this.message = "Vùng vượt giới hạn Y; dịch lên hoặc giảm chiều sâu.";
                  return false;
               }
            }

            this.config.regionSizeX = var2;
            this.config.regionSizeY = var3;
            this.config.regionSizeZ = var4;
            if (var1) {
               this.config.regionX = var5;
               this.config.regionTopY = var6;
               this.config.regionZ = var7;
               this.config.regionPlaced = true;
            }

            this.config.customRegion = true;
            this.config.saveQuietly();
            return true;
         } else {
            throw new NumberFormatException();
         }
      } catch (NumberFormatException var8) {
         this.message = "Nhập kích thước 1–200 và tọa độ nguyên hợp lệ.";
         return false;
      }
   }

   private void shiftRegion(int var1, int var2, int var3) {
      int var4 = this.moveStep;
      String var5 = this.ox.getText();
      String var6 = this.oy.getText();
      String var7 = this.oz.getText();

      try {
         boolean var8 = this.client != null
            && this.client.world != null
            && (
               (long)Integer.parseInt(var6) - Integer.parseInt(this.sy.getText()) + 1L < this.client.world.getBottomY()
                  || Integer.parseInt(var6) > this.client.world.getTopYInclusive()
            );
         this.ox.setText(Integer.toString(Math.addExact(Integer.parseInt(var5), var1 * var4)));
         this.oy.setText(Integer.toString(Math.addExact(Integer.parseInt(var6), var2 * var4)));
         this.oz.setText(Integer.toString(Math.addExact(Integer.parseInt(var7), var3 * var4)));
         if (!this.commitRegion(true)) {
            if (!var8) {
               this.ox.setText(var5);
               this.oy.setText(var6);
               this.oz.setText(var7);
            }

            return;
         }
      } catch (NumberFormatException | ArithmeticException var9) {
         this.ox.setText(var5);
         this.oy.setText(var6);
         this.oz.setText(var7);
         this.message = "Nhập tọa độ nguyên hợp lệ trước khi dịch vùng.";
         return;
      }

      this.config.showRegionOutline = true;
      this.config.saveQuietly();
      if (this.controller.loadRegion()) {
         this.message = "Đã dịch vùng " + var4 + " khối; gX/Y/Z cập nhật ngay.";
      }
   }

   private void positionContent() {
      for (ClickableWidget var2 : this.content) {
         int var3 = this.originalY.get(var2) - this.scroll;
         var2.setY(var3);
         var2.visible = var3 >= 78 && var3 + var2.getHeight() <= this.height - 52;
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      this.scroll = Math.clamp((long)(this.scroll - (int)(verticalAmount * 24.0)), 0, this.maxScroll);
      this.positionContent();
      return true;
   }

   public void render(DrawContext context, int mouseX, int mouseY, float delta) {
      super.render(context, mouseX, mouseY, delta);
      context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, -8852737);
      Region var5 = this.controller.region();
      String var6 = var5 == null ? "Tạo vùng theo kích thước hoặc nạp khung Litematica." : var5.describe();
      context.drawCenteredTextWithShadow(this.textRenderer, this.textRenderer.trimToWidth(var6, this.width - 20), this.width / 2, 24, -2236963);
      context.drawCenteredTextWithShadow(
         this.textRenderer,
         this.textRenderer.trimToWidth(this.message.isEmpty() ? this.controller.status() : this.message, this.width - 20),
         this.width / 2,
         36,
         -5654071
      );

      for (MineScreen.Label var8 : this.labels) {
         if (var8.y - this.scroll >= 78 && var8.y - this.scroll + 9 <= this.height - 52) {
            context.drawTextWithShadow(this.textRenderer, var8.text, var8.x, var8.y - this.scroll, -2236963);
         }
      }

      if (this.maxScroll > 0) {
         context.drawCenteredTextWithShadow(this.textRenderer, "Cuộn chuột để xem thêm", this.width / 2, this.height - 50, -5654071);
      }
   }

   public boolean shouldPause() {
      return false;
   }

   private record Label(String text, int x, int y) {
   }
}
