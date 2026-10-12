package com.donutautobuild.build;

import com.donutautobuild.AutoBuildMod;
import com.donutautobuild.order.GuiWatch;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

/**
 * 3.0.93: server lag (mở menu 5 lần liên tiếp không hiện) → thoát khỏi server, chờ 5 giây, vào lại đúng server đó,
 * chờ 5 giây cho tải chunk rồi tự bấm tiếp tục xây. Vào lại không được thì thử lại (cách nhau lâu dần), tối đa 10 lần.
 */
public final class Reconnector {
   private static int state;
   private static int ticks;
   private static int attempts;
   private static boolean resume;
   private static ServerInfo info;
   /** 3.0.105: server đang chơi (nhớ lúc còn kết nối) để tự vào lại khi bị mất kết nối */
   private static ServerInfo last;
   private static String lastReason = "";
   private static int startTries;

   private Reconnector() {
   }

   public static boolean active() {
      return state != 0;
   }

   public static void request(MinecraftClient var0, BuildController var1, String var2) {
      if (state == 0 && var0.player != null && !var0.isInSingleplayer() && var0.getCurrentServerEntry() != null) {
         info = var0.getCurrentServerEntry();
         // 3.0.108: mod đang bật theo ý người dùng (kể cả đang tạm dừng vì lý do khác) → vào xong xây tiếp
         resume = var1.isRunning() || var1.wanted();
         attempts = 0;
         AutoBuildMod.LOGGER.info("[AutoBuild] {} → thoát ra rồi vào lại {} (sau đó {})", new Object[]{var2, info.address, resume ? "tự xây tiếp" : "chờ lệnh"});
         var1.pause(var2 + " → thoát ra vào lại, vào xong tự xây tiếp.");
         state = 1;
         ticks = 0;
      }
   }

   /**
    * 3.0.105: mất kết nối (bị kick / "Connection reset") lúc đang xây → tự vào lại, không đứng chờ người bấm K (log 29/09 01:03).
    * Chỉ khi đang ở màn hình "Mất kết nối" của game — người chơi tự bấm Thoát thì không vào lại.
    */
   public static boolean requestLost(MinecraftClient var0, String var1) {
      if (state == 0 && last != null && var0.player == null && var0.currentScreen instanceof DisconnectedScreen) {
         info = last;
         resume = true;
         attempts = 0;
         lastReason = var1;
         AutoBuildMod.LOGGER.info("[AutoBuild] {} → tự vào lại {} rồi xây tiếp", var1, info.address);
         state = 2;
         ticks = 0;
         return true;
      } else {
         return false;
      }
   }

   public static void tick(MinecraftClient var0, BuildController var1) {
      if (var0.player != null && var0.world != null && !var0.isInSingleplayer() && var0.getCurrentServerEntry() != null) {
         last = var0.getCurrentServerEntry();
      }

      if (state != 0) {
         ticks++;
         switch (state) {
            case 1:
               if (ticks >= 10) {
                  try {
                     if (var0.world != null) {
                        var0.world.disconnect();
                     }

                     var0.disconnect();
                  } catch (Throwable var4) {
                     AutoBuildMod.LOGGER.warn("[AutoBuild] Lỗi khi thoát server", var4);
                  }

                  var0.setScreen(new MultiplayerScreen(new TitleScreen(false)));
                  state = 2;
                  ticks = 0;
               }
               break;
            case 2:
               if (ticks >= 100 + Math.min(attempts, 6) * 200) {
                  attempts++;
                  AutoBuildMod.LOGGER.info("[AutoBuild] Vào lại {} (lần {})", info.address, attempts);

                  try {
                     ConnectScreen.connect(new MultiplayerScreen(new TitleScreen(false)), var0, ServerAddress.parse(info.address), info, false, null);
                  } catch (Throwable var3) {
                     AutoBuildMod.LOGGER.warn("[AutoBuild] Lỗi khi vào lại server", var3);
                  }

                  state = 3;
                  ticks = 0;
               }
               break;
            case 3:
               if (var0.player != null && var0.world != null) {
                  state = 4;
                  ticks = 0;
               } else if (ticks > 200 && !(var0.currentScreen instanceof ConnectScreen) || ticks > 1200) {
                  // bị từ chối / hết giờ chờ → về màn chọn server, thử lại sau
                  if (attempts >= 10 && !(resume && var1.wanted())) {
                     AutoBuildMod.LOGGER.info("[AutoBuild] Vào lại server 10 lần không được → dừng thử");
                     state = 0;
                  } else {
                     // 3.0.108: mod vẫn bật → không bỏ cuộc, cứ ~1 phút thử vào lại 1 lần
                     if (attempts >= 10 && attempts % 10 == 0) {
                        AutoBuildMod.LOGGER.info("[AutoBuild] Đã thử vào lại {} lần chưa được → vẫn tiếp tục thử (mod đang bật)", attempts);
                     }

                     var0.setScreen(new MultiplayerScreen(new TitleScreen(false)));
                     state = 2;
                     ticks = 0;
                  }
               }
               break;
            case 4:
               if (var0.player == null || var0.world == null) {
                  state = 2;
                  ticks = 0;
               } else if (ticks >= 100 && var0.currentScreen == null) {
                  state = 0;
                  GuiWatch.reset();
                  // 3.0.108: người dùng bấm K dừng hẳn trong lúc đang vào lại → không tự xây
                  resume = resume && var1.wanted();
                  AutoBuildMod.LOGGER.info("[AutoBuild] Đã vào lại server{}", resume ? " → xây tiếp" : "");
                  if (resume) {
                     var1.start();
                     if (var1.isRunning()) {
                        var1.resetPlacementRecovery();
                     var1.afterReconnect();
                     } else {
                        // 3.0.107: vừa vào lại mà chưa bắt đầu được (vd. Litematica chưa nạp xong placement) → thử lại 6 lần, cách 5 giây
                        state = 5;
                        ticks = 0;
                        startTries = 0;
                     }
                  }
               } else if (ticks >= 400 && var0.currentScreen != null) {
                  var0.setScreen(null);
               }
               break;
            case 5:
               if (var0.player == null || var0.world == null) {
                  state = 2;
                  ticks = 0;
               } else if (var1.isRunning() || !var1.wanted()) {
                  state = 0;
               } else if (ticks >= 100 && var0.currentScreen == null) {
                  ticks = 0;
                  startTries++;
                  AutoBuildMod.LOGGER.info("[AutoBuild] Vào lại xong nhưng chưa bắt đầu xây được → thử bắt đầu lại (lần {})", startTries);
                  var1.start();
                  if (var1.isRunning()) {
                     state = 0;
                     var1.resetPlacementRecovery();
                     var1.afterReconnect();
                  } else if (startTries >= 6) {
                     state = 0;
                  }
               }
         }
      }
   }
}
