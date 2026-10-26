package com.donutautobuild;

import com.donutautobuild.build.BuildController;
import com.donutautobuild.build.LitematicaConfig;
import com.donutautobuild.schematic.SchematicEntrypoint;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Iterator;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil.Type;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AutoBuildMod implements ClientModInitializer {
   public static final Logger LOGGER = LoggerFactory.getLogger("donutautobuild");
   public static BuildController controller;
   public static BuildConfig config;
   private static int easyPlaceTicks;

   public AutoBuildMod() {
   }

   private static void browseLayer(MinecraftClient var0, int var1) {
      // 3.0.120: xem tầng do SchematicEntrypoint xử lý (chế độ hiển thị Từng tầng / Tất cả / Ẩn bên trong)
      SchematicEntrypoint.browseLayer(var0, var1);
   }

   public void onInitializeClient() {
      SchematicEntrypoint.install();
      com.donutautobuild.build.Freecam.install();
      config = BuildConfig.load();
      controller = new BuildController(config);
      // 3.0.94: nghe chat server (vd. "Home deleted") cho các luồng menu
      com.donutautobuild.order.ChatWatch.install();
      KeyBinding var1 = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.donutautobuild.toggle", Type.KEYSYM, 75, "category.donutautobuild"));
      KeyBinding var2 = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.donutautobuild.menu", Type.KEYSYM, 85, "category.donutautobuild"));
      KeyBinding var3 = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.donutautobuild.layer_up", Type.KEYSYM, 266, "category.donutautobuild"));
      KeyBinding var4 = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.donutautobuild.layer_down", Type.KEYSYM, 267, "category.donutautobuild"));
      ClientTickEvents.END_CLIENT_TICK.register(var4x -> {
         com.donutautobuild.build.Input.useTick(var4x);
         while (var1.wasPressed()) {
            controller.toggle();
         }

         while (var2.wasPressed()) {
            openMenu(var4x);
         }

         while (var3.wasPressed()) {
            browseLayer(var4x, 1);
         }

         while (var4.wasPressed()) {
            browseLayer(var4x, -1);
         }

         if (config.disableEasyPlace && var4x.world != null && var4x.player != null && ++easyPlaceTicks % 20 == 0) {
            LitematicaConfig.relax();
         }

         // 3.0.93/3.0.95: /home 5 lần liên tiếp không có menu → server lag → thoát ra vào lại (shop/order hụt thì chỉ thử lại sau)
         com.donutautobuild.order.GuiWatch.tick(var4x);
         if (com.donutautobuild.order.GuiWatch.lagging() && !com.donutautobuild.build.Reconnector.active()) {
            com.donutautobuild.order.GuiWatch.reset();
            com.donutautobuild.build.Reconnector.request(var4x, controller, "5 lần gửi /home không có menu nào hiện");
         }

         com.donutautobuild.build.Reconnector.tick(var4x, controller);
         try {
            controller.tick();
         } finally {
            // 3.0.122: phím mod bấm trong tick này → sự kiện phím/chuột thật
            com.donutautobuild.build.Input.flush(var4x);
         }
      });
      ClientCommandRegistrationCallback.EVENT
         .register(
            (var0, var1x) -> var0.register(
                  (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal(
                                                               "autobuild"
                                                            )
                                                            .executes(var0x -> {
                                                               ((FabricClientCommandSource)var0x.getSource()).sendFeedback(Text.literal(controller.status()));
                                                               return 1;
                                                            }))
                                                         .then(ClientCommandManager.literal("start").executes(var0x -> {
                                                            ((FabricClientCommandSource)var0x.getSource()).getClient().send(controller::userStart);
                                                            return 1;
                                                         })))
                                                      .then(ClientCommandManager.literal("stop").executes(var0x -> {
                                                         ((FabricClientCommandSource)var0x.getSource())
                                                            .getClient()
                                                            .send(() -> controller.userStop("Đã dừng. Nhấn K để chạy lại."));
                                                         return 1;
                                                      })))
                                                   .then(ClientCommandManager.literal("save").executes(var0x -> {
                                                      ((FabricClientCommandSource)var0x.getSource()).getClient().send(controller::pauseAndSave);
                                                      return 1;
                                                   })))
                                                .then(ClientCommandManager.literal("restore").executes(var0x -> {
                                                   ((FabricClientCommandSource)var0x.getSource()).getClient().send(controller::restore);
                                                   return 1;
                                                })))
                                             .then(ClientCommandManager.literal("load").executes(var0x -> {
                                                ((FabricClientCommandSource)var0x.getSource()).getClient().send(controller::prepare);
                                                return 1;
                                             })))
                                          .then(ClientCommandManager.literal("menu").executes(var0x -> {
                                             MinecraftClient var1xx = ((FabricClientCommandSource)var0x.getSource()).getClient();
                                             var1xx.send(() -> openMenu(var1xx));
                                             return 1;
                                          })))
                                       .then(ClientCommandManager.literal("cleanup").executes(var0x -> {
                                          ((FabricClientCommandSource)var0x.getSource()).getClient().send(controller::cleanup);
                                          return 1;
                                       })))
                                    .then(
                                       ClientCommandManager.literal("from")
                                          .then(
                                             ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                                .executes(
                                                   var0x -> {
                                                      config.yStart = IntegerArgumentType.getInteger(var0x, "y");
                                                      config.yEnd = null;
                                                      config.saveQuietly();
                                                      ((FabricClientCommandSource)var0x.getSource())
                                                         .sendFeedback(
                                                            Text.literal("Bắt đầu xây từ Y=" + config.yStart + " trở lên (bỏ qua các tầng bên dưới).")
                                                         );
                                                      return 1;
                                                   }
                                                )
                                          )
                                    ))
                                 .then(
                                    ClientCommandManager.literal("layer")
                                       .then(
                                          ClientCommandManager.argument("n", IntegerArgumentType.integer(1))
                                             .executes(
                                                var0x -> {
                                                   if (!controller.hasPlan()) {
                                                      ((FabricClientCommandSource)var0x.getSource())
                                                         .sendFeedback(Text.literal("Nạp placement trước (/autobuild load hoặc menu U)."));
                                                      return 0;
                                                   } else {
                                                      int var1xx = IntegerArgumentType.getInteger(var0x, "n");
                                                      config.yStart = controller.minY() + var1xx - 1;
                                                      config.yEnd = null;
                                                      config.saveQuietly();
                                                      ((FabricClientCommandSource)var0x.getSource())
                                                         .sendFeedback(
                                                            Text.literal(
                                                               "Bắt đầu xây từ tầng " + var1xx + "/" + controller.layerCount() + " (Y=" + config.yStart + ")."
                                                            )
                                                         );
                                                      return 1;
                                                   }
                                                }
                                             )
                                       )
                                 ))
                              .then(
                                 ((LiteralArgumentBuilder)ClientCommandManager.literal("range").then(ClientCommandManager.literal("all").executes(var0x -> {
                                       config.yStart = null;
                                       config.yEnd = null;
                                       config.saveQuietly();
                                       ((FabricClientCommandSource)var0x.getSource()).sendFeedback(Text.literal("Xây toàn bộ Y."));
                                       return 1;
                                    })))
                                    .then(
                                       ClientCommandManager.argument("from", IntegerArgumentType.integer())
                                          .then(ClientCommandManager.argument("to", IntegerArgumentType.integer()).executes(var0x -> {
                                             config.yStart = IntegerArgumentType.getInteger(var0x, "from");
                                             config.yEnd = IntegerArgumentType.getInteger(var0x, "to");
                                             config.saveQuietly();
                                             ((FabricClientCommandSource)var0x.getSource())
                                                .sendFeedback(Text.literal("Khoảng Y: " + config.yStart + " → " + config.yEnd));
                                             return 1;
                                          }))
                                    )
                              ))
                           .then(
                              ((LiteralArgumentBuilder)ClientCommandManager.literal("mode").then(ClientCommandManager.literal("exact").executes(var0x -> {
                                    config.buildMode = "EXACT";
                                    config.saveQuietly();
                                    ((FabricClientCommandSource)var0x.getSource()).sendFeedback(Text.literal("Chế độ: đúng thiết kế"));
                                    return 1;
                                 })))
                                 .then(
                                    ClientCommandManager.literal("fast")
                                       .executes(
                                          var0x -> {
                                             config.buildMode = "FAST";
                                             config.saveQuietly();
                                             ((FabricClientCommandSource)var0x.getSource())
                                                .sendFeedback(Text.literal("Chế độ: nhanh (khối trang trí dùng đá cuội, đồ cùng loại thay nhau)"));
                                             return 1;
                                          }
                                       )
                                 )
                           ))
                        .then(
                           ((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal("spoof")
                                    .then(ClientCommandManager.literal("auto").executes(var0x -> {
                                       config.rotationSpoof = "AUTO";
                                       config.saveQuietly();
                                       ((FabricClientCommandSource)var0x.getSource())
                                          .sendFeedback(Text.literal("Góc gửi riêng: tự động (DonutSMP / thế giới đơn)"));
                                       return 1;
                                    })))
                                 .then(ClientCommandManager.literal("on").executes(var0x -> {
                                    config.rotationSpoof = "ON";
                                    config.saveQuietly();
                                    ((FabricClientCommandSource)var0x.getSource())
                                       .sendFeedback(Text.literal("Góc gửi riêng: bật (chỉ dùng trên server cho phép)"));
                                    return 1;
                                 })))
                              .then(ClientCommandManager.literal("off").executes(var0x -> {
                                 config.rotationSpoof = "OFF";
                                 config.saveQuietly();
                                 ((FabricClientCommandSource)var0x.getSource()).sendFeedback(Text.literal("Góc gửi riêng: tắt (dùng cách đào hố)"));
                                 return 1;
                              }))
                        ))
                     .then(
                        ClientCommandManager.literal("status")
                           .executes(
                              var0x -> {
                                 ((FabricClientCommandSource)var0x.getSource())
                                    .sendFeedback(
                                       Text.literal(
                                          controller.stageName()
                                             + " | "
                                             + controller.completed()
                                             + "/"
                                             + controller.total()
                                             + " | "
                                             + controller.status()
                                             + " | "
                                             + controller.progressDetail()
                                       )
                                    );
                                 return 1;
                              }
                           )
                     )
               )
         );
      HudRenderCallback.EVENT
         .register(
            (var0, var1x) -> {
               MinecraftClient var2x = MinecraftClient.getInstance();
               if (config.showHud
                  && var2x.player != null
                  && !var2x.options.hudHidden
                  && var2x.currentScreen == null
                  && (controller.active() || controller.hasPlan() || controller.preparing())) {
                  TextRenderer var3x = var2x.textRenderer;
                  int var4x = Math.min(300, var2x.getWindow().getScaledWidth() - 16);
                  byte var5 = 8;
                  byte var6 = 8;
                  byte var7 = 90;
                  var0.fill(var5, var6, var5 + var4x, var6 + var7, -1341121753);
                  var0.fill(var5, var6, var5 + 3, var6 + var7, controller.active() ? -10365258 : -2249630);
                  Integer var8 = controller.currentLayer();
                  int var9 = controller.layerDone();
                  int var10 = controller.layerTotal();
                  int var11 = controller.completed();
                  int var12 = controller.total();
                  var0.drawTextWithShadow(var3x, "AUTO BUILD · " + controller.stageName() + " · " + controller.modeLabel(), var5 + 8, var6 + 5, -9247552);
                  String var13 = (var8 == null ? "Tầng —" : "Tầng " + controller.layerIndex(var8) + "/" + controller.layerCount() + " (Y=" + var8 + ")")
                     + ": "
                     + var9
                     + " / "
                     + var10
                     + " · "
                     + pct(var9, var10);
                  String var14 = "Tổng: " + var11 + " / " + var12 + " · " + pct(var11, var12) + " · Kê: " + controller.temporaryCount() + " · Chi $" + com.donutautobuild.order.MoneyTracker.format(config.moneySpent);
                  var0.drawTextWithShadow(var3x, var13, var5 + 8, var6 + 17, -1510920);
                  var0.fill(var5 + 8, var6 + 27, var5 + var4x - 8, var6 + 29, -14273473);
                  if (var10 > 0) {
                     var0.fill(var5 + 8, var6 + 27, var5 + 8 + (int)((long)(var4x - 16) * (long)Math.min(var9, var10) / (long)var10), var6 + 29, -10365258);
                  }

                  var0.drawTextWithShadow(var3x, var3x.trimToWidth(var14, var4x - 14), var5 + 8, var6 + 32, -1510920);
                  var0.drawTextWithShadow(var3x, var3x.trimToWidth(controller.status(), var4x - 14), var5 + 8, var6 + 44, -1510920);
                  String var15 = "—";
                  Iterator var16 = controller.layerMaterials().iterator();
                  if (var16.hasNext()) {
                     BuildController.MaterialRow var17 = (BuildController.MaterialRow)var16.next();
                     var15 = var17.item().getName().getString() + ": có " + var17.have() + " · cần " + var17.needed();
                  }

                  var0.drawTextWithShadow(var3x, var3x.trimToWidth(var15, var4x - 14), var5 + 8, var6 + 56, -4206371);
                  var0.drawTextWithShadow(var3x, var3x.trimToWidth(controller.targetLabel(), var4x - 14), var5 + 8, var6 + 67, -6443327);
                  var0.drawTextWithShadow(var3x, "[U] Menu / Vật liệu / Cài đặt   [K] Chạy / Tạm dừng", var5 + 8, var6 + 78, -6443327);
               }
            }
         );
   }

   private static String pct(int var0, int var1) {
      return var1 <= 0 ? "0.0%" : String.format("%.1f%%", (double)var0 * 100.0 / (double)var1);
   }

   private static void openMenu(MinecraftClient var0) {
      // 3.0.120: GUI chọn sche / sửa vị trí / Nạp (SchematicEntrypoint → BuildScreen)
      SchematicEntrypoint.open(var0);
   }
}
