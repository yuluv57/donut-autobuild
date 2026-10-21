package com.donutautomine;

import com.donutautomine.gui.MineScreen;
import com.donutautomine.mine.FreeCam;
import com.donutautomine.mine.MineController;
import com.donutautomine.mine.Region;
import com.donutautomine.mine.Tools;
import com.donutautomine.order.ChatWatch;
import com.donutautomine.order.Inv;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.StartTick;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil.Type;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AutoMineMod implements ClientModInitializer {
   public static final Logger LOGGER = LoggerFactory.getLogger("donutautomine");
   public static MineConfig config;
   public static MineController controller;

   public void onInitializeClient() {
      config = MineConfig.load();
      controller = new MineController(config);
      ChatWatch.install();
      KeyBinding var1 = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.donutautomine.toggle", Type.KEYSYM, 74, "category.donutautomine"));
      KeyBinding var2 = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.donutautomine.menu", Type.KEYSYM, 72, "category.donutautomine"));
      KeyBinding var3 = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.donutautomine.freecam", Type.KEYSYM, 78, "category.donutautomine"));
      ClientTickEvents.START_CLIENT_TICK.register((StartTick)var0 -> {
         if (var0.player == null && FreeCam.active()) {
            FreeCam.disable(var0);
         }

         FreeCam.guardInput(var0);
      });
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)var3x -> {
         while (var1.wasPressed()) {
            controller.toggle();
         }

         while (var2.wasPressed()) {
            openMenu(var3x);
         }

         while (var3.wasPressed()) {
            FreeCam.toggle(var3x);
         }

         controller.tick();
         FreeCam.lock(var3x);
      });
      ClientCommandRegistrationCallback.EVENT
         .register(
            (ClientCommandRegistrationCallback)(var0, var1x) -> var0.register(
               (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommandManager.literal(
                                             "automine"
                                          )
                                          .executes(var0x -> {
                                             ((FabricClientCommandSource)var0x.getSource()).sendFeedback(Text.literal(statusLine()));
                                             return 1;
                                          }))
                                       .then(ClientCommandManager.literal("start").executes(var0x -> {
                                          ((FabricClientCommandSource)var0x.getSource()).getClient().send(controller::start);
                                          return 1;
                                       })))
                                    .then(ClientCommandManager.literal("stop").executes(var0x -> {
                                       ((FabricClientCommandSource)var0x.getSource()).getClient().send(() -> controller.pause("Đã dừng. Nhấn J để chạy tiếp."));
                                       return 1;
                                    })))
                                 .then(ClientCommandManager.literal("load").executes(var0x -> {
                                    ((FabricClientCommandSource)var0x.getSource()).getClient().send(() -> {
                                       if (controller.loadRegion()) {
                                          controller.say("Khung: " + controller.region().describe());
                                       }
                                    });
                                    return 1;
                                 })))
                              .then(ClientCommandManager.literal("reset").executes(var0x -> {
                                 ((FabricClientCommandSource)var0x.getSource()).getClient().send(() -> {
                                    controller.pause("Đặt lại tiến độ.");
                                    controller.resetProgress();
                                 });
                                 return 1;
                              })))
                           .then(
                              ClientCommandManager.literal("layer").then(ClientCommandManager.argument("n", IntegerArgumentType.integer(1)).executes(var0x -> {
                                 int var1xx = IntegerArgumentType.getInteger(var0x, "n");
                                 ((FabricClientCommandSource)var0x.getSource()).getClient().send(() -> controller.startFromLayer(var1xx));
                                 return 1;
                              }))
                           ))
                        .then(ClientCommandManager.literal("menu").executes(var0x -> {
                           MinecraftClient var1xx = ((FabricClientCommandSource)var0x.getSource()).getClient();
                           var1xx.send(() -> openMenu(var1xx));
                           return 1;
                        })))
                     .then(ClientCommandManager.literal("freecam").executes(var0x -> {
                        MinecraftClient var1xx = ((FabricClientCommandSource)var0x.getSource()).getClient();
                        var1xx.send(() -> FreeCam.toggle(var1xx));
                        return 1;
                     })))
                  .then(ClientCommandManager.literal("status").executes(var0x -> {
                     ((FabricClientCommandSource)var0x.getSource()).sendFeedback(Text.literal(statusLine() + " | " + controller.taskLabel()));
                     return 1;
                  }))
            )
         );
      HudRenderCallback.EVENT.register((HudRenderCallback)(var0, var1x) -> renderHud(var0));
   }

   public static void openMenu(MinecraftClient var0) {
      if (controller.running()) {
         controller.pause("Mở menu — tạm dừng, tiến độ vẫn giữ.");
      }

      var0.setScreen(new MineScreen(config, controller));
   }

   private static String statusLine() {
      Region var0 = controller.region();
      return "AutoMine: "
         + controller.state()
         + " | "
         + (var0 == null ? "chưa có khung" : "tầng " + (controller.layer() + 1) + "/" + var0.layerCount())
         + " | "
         + controller.strikes()
         + " nhát, "
         + controller.cleared()
         + " ô | "
         + controller.status();
   }

   private static String pct(int var0, int var1) {
      return var1 <= 0 ? "0%" : String.format("%.0f%%", Math.max(0.0, Math.min(100.0, var0 * 100.0 / var1)));
   }

   private static void renderHud(DrawContext var0) {
      MinecraftClient var1 = MinecraftClient.getInstance();
      if (config.showHud && var1.player != null && !var1.options.hudHidden && var1.currentScreen == null) {
         if (controller.state() != MineController.State.IDLE || controller.strikes() != 0 || FreeCam.active()) {
            TextRenderer var2 = var1.textRenderer;
            if (FreeCam.active()) {
               String var17 = "§bFREECAM§r · "
                  + stateName()
                  + (
                     controller.region() != null && controller.layer() >= 0
                        ? " · tầng " + (controller.layer() + 1) + "/" + controller.region().layerCount()
                        : ""
                  )
                  + " · "
                  + controller.strikes()
                  + " nhát";
               String var18 = "§7" + controller.status() + " §8· [N] về nhân vật · [J] chạy/dừng";
               int var19 = Math.min(var1.getWindow().getScaledWidth() - 8, Math.max(var2.getWidth(var17), var2.getWidth(var18)) + 8);
               var0.fill(4, 4, 4 + var19, 28, -2146429928);
               var0.drawTextWithShadow(var2, var2.trimToWidth(var17, var19 - 6), 8, 7, -1512723);
               var0.drawTextWithShadow(var2, var2.trimToWidth(var18, var19 - 6), 8, 17, -5194043);
            } else {
               int var3 = Math.min(290, var1.getWindow().getScaledWidth() - 16);
               byte var4 = 8;
               int var5 = var1.getWindow().getScaledHeight() / 2 - 40;
               byte var6 = 82;
               var0.fill(var4, var5, var4 + var3, var5 + var6, -1341123560);
               int var7 = controller.running() ? -11549705 : (controller.state() == MineController.State.DONE ? -10044566 : -22746);
               var0.fill(var4, var5, var4 + 3, var5 + var6, var7);
               Boolean var8 = controller.threeByThree();
               String var9 = var8 == null ? "3x3: đang dò" : (var8 ? "3x3 ✓" : "1 khối");
               var0.drawTextWithShadow(var2, "AUTO MINE · " + stateName() + " · " + var9, var4 + 8, var5 + 5, -7351297);
               Region var10 = controller.region();
               int var12 = 0;
               String var11;
               if (var10 != null && controller.layer() >= 0) {
                  int var13 = controller.layerCells();
                  int var14 = Math.max(0, controller.layerRemaining());
                  var12 = var13 <= 0 ? 0 : (int)((long)(var3 - 16) * Math.max(0, var13 - var14) / var13);
                  var11 = "Tầng "
                     + (controller.layer() + 1)
                     + "/"
                     + var10.layerCount()
                     + " (Y "
                     + var10.floor(controller.layer())
                     + "…"
                     + var10.top(controller.layer())
                     + ")"
                     + (
                        controller.plan() != null
                           ? " · hàng " + Math.min(controller.plan().stripOfCurrent() + 1, controller.plan().strips) + "/" + controller.plan().strips
                           : ""
                     )
                     + " · còn "
                     + var14
                     + " ô";
               } else {
                  var11 = "Khung: —";
               }

               var0.drawTextWithShadow(var2, var2.trimToWidth(var11, var3 - 14), var4 + 8, var5 + 17, -1512723);
               var0.fill(var4 + 8, var5 + 27, var4 + var3 - 8, var5 + 29, -14011328);
               if (var12 > 0) {
                  var0.fill(var4 + 8, var5 + 27, var4 + 8 + var12, var5 + 29, var7);
               }

               String var20 = controller.strikes()
                  + " nhát · "
                  + controller.cleared()
                  + " ô · "
                  + String.format("%.0f", controller.strikesPerMinute())
                  + " nhát/phút"
                  + (controller.skippedCount() > 0 ? " · bỏ qua " + controller.skippedCount() : "");
               var0.drawTextWithShadow(var2, var2.trimToWidth(var20, var3 - 14), var4 + 8, var5 + 32, -1512723);
               ItemStack var21 = controller.tools().chosen() ? controller.tools().findPick(var1) : ItemStack.EMPTY;
               int var15 = Tools.durability(var21);
               String var16 = "Cúp: "
                  + (var21.isEmpty() ? "—" : (var15 < 0 ? "không mất độ bền" : var15 + "/" + var21.getMaxDamage()))
                  + " · XP: "
                  + Inv.count(var1, Tools::isXp)
                  + " · Đói: "
                  + var1.player.getHungerManager().getFoodLevel()
                  + " · Ô trống: "
                  + Inv.freeSlots(var1);
               var0.drawTextWithShadow(var2, var2.trimToWidth(var16, var3 - 14), var4 + 8, var5 + 44, -3814679);
               var0.drawTextWithShadow(var2, var2.trimToWidth(controller.status(), var3 - 14), var4 + 8, var5 + 56, -1512723);
               var0.drawTextWithShadow(
                  var2,
                  FreeCam.active() ? "§bFREECAM §7— [N] về nhân vật · [J] Chạy/Dừng" : "[J] Chạy / Tạm dừng   [H] Menu   [N] Freecam",
                  var4 + 8,
                  var5 + 69,
                  -6642510
               );
            }
         }
      }
   }

   private static String stateName() {
      return switch (controller.state()) {
         case RUNNING -> "ĐANG ĐÀO";
         case DONE -> "XONG";
         default -> "TẠM DỪNG";
      };
   }
}
