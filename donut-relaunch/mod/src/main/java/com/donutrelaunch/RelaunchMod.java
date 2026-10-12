package com.donutrelaunch;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Donut Relaunch — phần trong game của bộ tự tắt/bật client.
 *
 * <p>Gặp "Invalid session" / "Failed to verify username" (phiên đăng nhập Microsoft hết hạn, chỉ launcher làm mới được) →
 * ghi {@code exit-<pid>.txt = restart} rồi tự thoát game. Script {@code donut-relaunch.ps1} thấy game tắt sẽ bấm
 * "Enter the game" trên đúng cửa sổ Legacy Launcher của tài khoản đó. Bạn tự tắt game → ghi {@code user} (script không mở lại).
 * Crash → không có file → script coi là crash và mở lại.
 *
 * <p>Mở lại xong: script ghi {@code relaunch-<tên>.txt}; mod thấy màn hình chính thì tự vào lại server cuối cùng của tài khoản
 * ({@code last-<tên>.txt}, ghi mỗi lần vào server).
 *
 * <p>1.2.0 — canh ASell kẹt: ASell báo đang chạy mà đứng yên một trạng thái quá {@code stuckMinutes} phút (mặc định 3) → tự gõ
 * {@code /asell stop} rồi {@code /asell auto}, đúng như bạn bật lại bằng tay. Không đụng vào lúc ASell khoá an toàn tiền
 * (quarantine, trừ khi đặt {@code quarantineMinutes} &gt; 0), lúc server restart/bảo trì, lúc ASell đang tự dò GUI. 3 lần trong 30 phút
 * vẫn kẹt → thoát game cho script mở lại. Cấu hình ở {@code asell-watch.txt} (đọc lại mỗi phút, không cần khởi động lại).
 *
 * <p>File lệnh {@code cmd-<tên>.txt}: {@code restart} = thoát game để script mở lại (dùng khi thay mod), {@code asell-restart} = bật lại ASell.
 */
public final class RelaunchMod implements ClientModInitializer {
   public static final Logger LOG = LoggerFactory.getLogger("donutrelaunch");
   private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
   private static final long RELAUNCH_FRESH_MS = 30L * 60L * 1000L;

   private Path dir;
   private final long pid = ProcessHandle.current().pid();
   private String user;
   private boolean exiting;
   private int ticks;
   private Screen lastDisconnected;
   private boolean joinPending;
   private long joinAt;
   private int joinTries;
   private String joinAddr;
   /** trạng thái ASell lần cuối thấy lúc đang ở trong server (null = chưa biết / không có ASell) */
   private Boolean lastAsell;
   /** tick sẽ bật lại ASell sau khi tự vào lại server (-1 = không) */
   private int resumeAt = -1;
   /** canh ASell kẹt: trạng thái đang thấy và lúc bắt đầu thấy */
   private String watchState;
   private long watchSince;
   /** tick sẽ gõ /asell auto sau khi vừa /asell stop vì kẹt (-1 = không) */
   private int autoAt = -1;
   private final ArrayDeque<Long> kicks = new ArrayDeque<>();
   private long cfgReadAt;
   private int cfgStuckMinutes = 3;
   private int cfgQuarantineMinutes = 0;
   private boolean cfgEnabled = true;
   /** chế độ tiết kiệm CPU/RAM khi cửa sổ không được chọn */
   private Eco eco;

   @Override
   public void onInitializeClient() {
      try {
         this.dir = FabricLoader.getInstance().getConfigDir().getParent().resolve("donut-relaunch");
         Files.createDirectories(this.dir);
      } catch (Throwable var2) {
         LOG.info("[DonutRelaunch] Không tạo được thư mục dữ liệu: " + var2);
      }

      this.eco = new Eco(this.dir, this::log, () -> this.user(MinecraftClient.getInstance()));
      ClientTickEvents.END_CLIENT_TICK.register(this::tick);
      ClientPlayConnectionEvents.JOIN.register((var1, var2, var3) -> this.onJoin(var3));
      ClientLifecycleEvents.CLIENT_STOPPING.register(var1 -> this.onStopping());
      ClientCommandRegistrationCallback.EVENT.register((var1, var2) -> var1.register(ClientCommandManager.literal("relaunchtest").executes(var1x -> {
         this.requestRestart(MinecraftClient.getInstance(), "thử bằng lệnh /relaunchtest");
         return 1;
      })));
      this.log("khởi động (pid " + this.pid + ")");

      this.ensureAlive();
   }

   /**
    * báo cho script: tiến trình này có mod → game tắt mà không có exit-<pid>.txt nghĩa là crash, được phép mở lại.
    * Client chưa có mod (bản cũ đang chạy) thì script không tự mở lại, kể cả khi bạn tắt tay. Ghi lại mỗi phút nếu file bị mất.
    */
   private void ensureAlive() {
      try {
         if (this.dir != null) {
            Path var1 = this.dir.resolve("alive-" + this.pid + ".txt");
            if (!Files.exists(var1)) {
               Files.writeString(var1, LocalDateTime.now().format(TS) + "\n", StandardCharsets.UTF_8);
            }
         }
      } catch (Throwable var2) {
      }
   }

   private String user(MinecraftClient var1) {
      if (this.user == null) {
         try {
            this.user = var1.getSession().getUsername();
         } catch (Throwable var3) {
         }
      }

      return this.user == null ? "unknown" : this.user;
   }

   private void tick(MinecraftClient var1) {
      this.ticks++;
      if (this.exiting) {
         return;
      }

      if (this.ticks % 1200 == 0) {
         this.ensureAlive();
      }

      try {
         this.eco.tick(var1);
      } catch (Throwable var7) {
      }

      Screen var2 = var1.currentScreen;
      if (var2 instanceof DisconnectedScreen && var2 != this.lastDisconnected) {
         this.lastDisconnected = var2;
         String var3 = reasonOf(var2);
         this.log("[" + this.user(var1) + "] mất kết nối: " + var3);
         if (isSessionError(var3)) {
            this.requestRestart(var1, var3);
            return;
         }

         if (this.joinPending) {
            // đang tự vào lại sau khi mở game mà server từ chối (đang khởi động lại, "already online"…) → chờ rồi thử lại
            long var4 = Math.min(300000L, 30000L * (1L << Math.min(4, Math.max(0, this.joinTries - 1))));
            this.joinAt = System.currentTimeMillis() + var4;
            this.log("[" + this.user(var1) + "] vào lại chưa được → thử lại sau " + var4 / 1000L + " giây");
         }
      }

      if (var1.player != null && this.ticks % 20 == 0 && this.resumeAt < 0 && this.autoAt < 0) {
         // đang chờ tự bật lại ASell thì không ghi "đang dừng" (game chết đúng lúc đó vẫn còn asell=1 để lần sau bật lại)
         this.trackAsell(var1);
      }

      if (this.resumeAt > 0 && this.ticks >= this.resumeAt) {
         this.resumeAt = -1;
         this.resumeAsell(var1);
      }

      if (this.autoAt > 0 && this.ticks >= this.autoAt) {
         this.autoAt = -1;
         this.resumeAsell(var1);
      }

      if (this.ticks % 20 == 10
         && var1.player != null
         && var1.world != null
         && !this.joinPending
         && this.resumeAt < 0
         && this.autoAt < 0
         && !(var2 instanceof DisconnectedScreen)) {
         this.watchAsell(var1);
      }

      if (this.ticks % 40 == 5) {
         this.checkCommandFile(var1);
         if (this.exiting) {
            return;
         }
      }

      if (!this.joinPending && this.ticks % 20 == 0 && var1.world == null && var2 instanceof TitleScreen) {
         this.checkRelaunchFlag(var1);
      }

      if (this.joinPending
         && var1.world == null
         && System.currentTimeMillis() >= this.joinAt
         && (var2 instanceof TitleScreen || var2 instanceof MultiplayerScreen || var2 instanceof DisconnectedScreen)) {
         this.joinTries++;
         this.joinAt = System.currentTimeMillis() + 45000L;
         this.log("[" + this.user(var1) + "] tự vào lại server " + this.joinAddr + " (lần " + this.joinTries + ")");

         try {
            ConnectScreen.connect(new MultiplayerScreen(new TitleScreen(false)), var1, ServerAddress.parse(this.joinAddr), makeInfo(this.joinAddr), false, null);
         } catch (Throwable var6) {
            this.log("[" + this.user(var1) + "] lỗi khi vào server: " + var6);
         }
      }
   }

   /** script vừa bấm "Enter the game" cho tài khoản này → có file relaunch-<tên>.txt mới → tự vào lại server cuối */
   private void checkRelaunchFlag(MinecraftClient var1) {
      if (this.dir != null) {
         String var2 = this.user(var1);
         Path var3 = this.dir.resolve("relaunch-" + var2 + ".txt");

         try {
            if (!Files.exists(var3)) {
               return;
            }

            long var4 = Files.getLastModifiedTime(var3).toMillis();
            if (System.currentTimeMillis() - var4 > RELAUNCH_FRESH_MS) {
               Files.deleteIfExists(var3);
               this.log("[" + var2 + "] bỏ yêu cầu vào lại cũ (quá 30 phút)");
               return;
            }

            Path var6 = this.dir.resolve("last-" + var2 + ".txt");
            String var7 = Files.exists(var6) ? Files.readString(var6, StandardCharsets.UTF_8).trim() : "";
            if (var7.isEmpty()) {
               Files.deleteIfExists(var3);
               this.log("[" + var2 + "] mở lại xong nhưng chưa biết server cũ → để ở màn hình chính");
               return;
            }

            this.joinAddr = var7;
            this.joinPending = true;
            this.joinTries = 0;
            this.joinAt = System.currentTimeMillis() + 3000L;
            this.log("[" + var2 + "] game vừa được mở lại → 3 giây nữa tự vào " + var7);
         } catch (IOException var8) {
            this.log("[" + var2 + "] không đọc được yêu cầu vào lại: " + var8);
         }
      }
   }

   private void onJoin(MinecraftClient var1) {
      String var2 = this.user(var1);

      try {
         ServerInfo var3 = var1.getCurrentServerEntry();
         if (var3 != null && var3.address != null && !var3.address.isBlank() && this.dir != null) {
            Files.writeString(this.dir.resolve("last-" + var2 + ".txt"), var3.address.trim(), StandardCharsets.UTF_8);
         }
      } catch (Throwable var5) {
      }

      if (this.joinPending) {
         this.joinPending = false;

         try {
            Files.deleteIfExists(this.dir.resolve("relaunch-" + var2 + ".txt"));
         } catch (Throwable var4) {
         }

         this.log("[" + var2 + "] đã vào lại server sau khi mở lại game");
         if (this.readAsellState(var2)) {
            // giống ASell tự làm sau khi nó tự reconnect: chờ 5 giây rồi /asell auto
            this.resumeAt = this.ticks + 100;
            this.log("[" + var2 + "] trước khi tắt ASell đang chạy → 5 giây nữa bật lại (/asell auto)");
         }
      }
   }

   /** nhớ ASell đang chạy hay dừng (chỉ lúc đang ở trong server; mất kết nối thì giữ trạng thái cuối) */
   private void trackAsell(MinecraftClient var1) {
      Boolean var2 = AsellBridge.running();
      if (var2 != null && !var2.equals(this.lastAsell)) {
         this.lastAsell = var2;
         String var3 = this.user(var1);

         try {
            if (this.dir != null) {
               Files.writeString(
                  this.dir.resolve("state-" + var3 + ".txt"), "asell=" + (var2 ? "1" : "0") + "\n" + LocalDateTime.now().format(TS) + "\n", StandardCharsets.UTF_8
               );
            }
         } catch (Throwable var5) {
         }

         this.log("[" + var3 + "] ASell " + (var2 ? "đang chạy" : "đang dừng"));
      }
   }

   private boolean readAsellState(String var1) {
      try {
         Path var2 = this.dir.resolve("state-" + var1 + ".txt");
         return Files.exists(var2) && Files.readString(var2, StandardCharsets.UTF_8).startsWith("asell=1");
      } catch (Throwable var3) {
         return false;
      }
   }

   private void resumeAsell(MinecraftClient var1) {
      String var2 = this.user(var1);
      Boolean var3 = AsellBridge.running();
      if (var1.player == null || var1.getNetworkHandler() == null) {
         this.log("[" + var2 + "] chưa ở trong server → chưa bật lại ASell");
      } else if (var3 == null) {
         this.log("[" + var2 + "] không thấy mod ASell → bỏ qua");
      } else if (var3) {
         this.log("[" + var2 + "] ASell đã tự chạy");
      } else {
         this.log("[" + var2 + "] bật ASell: /asell auto");
         var1.getNetworkHandler().sendChatCommand("asell auto");
      }
   }

   /** đọc asell-watch.txt (key=value) tối đa mỗi phút một lần; thiếu file → tạo file mẫu */
   private void readWatchConfig() {
      long now = System.currentTimeMillis();
      if (now - this.cfgReadAt < 60000L || this.dir == null) {
         return;
      }

      this.cfgReadAt = now;
      Path f = this.dir.resolve("asell-watch.txt");

      try {
         if (!Files.exists(f)) {
            Files.writeString(
               f,
               "# Canh ASell kẹt (mod Donut Relaunch). Sửa xong lưu lại, 1 phút sau mod tự đọc, không cần khởi động lại game.\n"
                  + "# enabled: true = bật, false = tắt hẳn\n"
                  + "enabled=true\n"
                  + "# ASell đứng yên một trạng thái quá số phút này thì /asell stop rồi /asell auto\n"
                  + "stuckMinutes=3\n"
                  + "# ASell khoá an toàn tiền (quarantine) quá số phút này thì cũng bật lại; 0 = không bao giờ đụng vào\n"
                  + "quarantineMinutes=0\n",
               StandardCharsets.UTF_8
            );
            return;
         }

         for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
            String t = line.trim();
            int eq = t.indexOf('=');
            if (t.startsWith("#") || eq <= 0) {
               continue;
            }

            String k = t.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String v = t.substring(eq + 1).trim();

            try {
               if (k.equals("enabled")) {
                  this.cfgEnabled = !v.equalsIgnoreCase("false") && !v.equals("0");
               } else if (k.equals("stuckminutes")) {
                  this.cfgStuckMinutes = Math.max(1, Integer.parseInt(v));
               } else if (k.equals("quarantineminutes")) {
                  this.cfgQuarantineMinutes = Math.max(0, Integer.parseInt(v));
               }
            } catch (NumberFormatException var11) {
            }
         }
      } catch (Throwable var12) {
      }
   }

   /**
    * ASell báo đang chạy mà trạng thái không đổi quá lâu = kẹt (vd. WAITING_ELYTRA_ORDER_MENU khi cờ chờ order bị tắt sai: mọi nhánh của
    * trạng thái đó đều cần cờ này nên không nhánh nào chạy, còn GUI đang mở nên ASell không tự reconnect). Một vòng order bình thường chỉ
    * 10–30 giây nên 3 phút đứng yên là bất thường.
    */
   private void watchAsell(MinecraftClient var1) {
      Boolean running = AsellBridge.running();
      long now = System.currentTimeMillis();
      if (running == null || !running) {
         this.watchState = null;
         return;
      }

      String st = AsellBridge.state();
      if (st == null) {
         return;
      }

      if (!st.equals(this.watchState)) {
         this.watchState = st;
         this.watchSince = now;
         return;
      }

      this.readWatchConfig();
      if (!this.cfgEnabled) {
         this.watchSince = now;
         return;
      }

      long limitMs;
      if (st.equals("WAITING_ELYTRA_SAFETY_QUARANTINE")) {
         if (this.cfgQuarantineMinutes <= 0) {
            // khoá an toàn tiền: ASell cố ý chờ người kiểm tra → không tự gỡ
            return;
         }

         limitMs = this.cfgQuarantineMinutes * 60000L;
      } else {
         if (AsellBridge.flag("hardResetMode") || AsellBridge.flag("serverResetPaused") || AsellBridge.flag("safeGuiProbeMode")) {
            // server đang restart / bảo trì, hoặc ASell đang tự thử mở GUI mỗi 5 giây → để ASell tự xử, tính lại từ đầu
            this.watchSince = now;
            return;
         }

         limitMs = this.cfgStuckMinutes * 60000L;
      }

      if (now - this.watchSince < limitMs) {
         return;
      }

      String user = this.user(var1);
      long mins = (now - this.watchSince) / 60000L;
      this.watchState = null;

      while (!this.kicks.isEmpty() && now - this.kicks.peekFirst() > 1800000L) {
         this.kicks.pollFirst();
      }

      if (this.kicks.size() >= 3) {
         this.kicks.clear();
         this.log("[" + user + "] ASell kẹt lần thứ 4 trong 30 phút (" + st + ") → thoát game để mở lại cho sạch");
         this.requestRestart(var1, "ASell kẹt nhiều lần ở " + st);
         return;
      }

      this.kicks.addLast(now);
      if (var1.getNetworkHandler() == null) {
         return;
      }

      this.log("[" + user + "] ASell đứng yên ở " + st + " " + mins + " phút → /asell stop rồi /asell auto");
      var1.getNetworkHandler().sendChatCommand("asell stop");
      this.autoAt = this.ticks + 60;
   }

   /** cmd-<tên>.txt: restart = thoát game để script mở lại; asell-restart = bật lại ASell. Đọc xong xoá file. */
   private void checkCommandFile(MinecraftClient var1) {
      if (this.dir == null || this.user == null && var1.getSession() == null) {
         return;
      }

      String user = this.user(var1);
      Path f = this.dir.resolve("cmd-" + user + ".txt");

      try {
         if (!Files.exists(f)) {
            return;
         }

         String cmd = Files.readString(f, StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
         Files.deleteIfExists(f);
         if (cmd.startsWith("restart")) {
            this.log("[" + user + "] nhận lệnh restart từ file → thoát game để script mở lại");
            this.requestRestart(var1, "lệnh restart từ file cmd");
         } else if (cmd.startsWith("asell-restart")) {
            if (var1.player != null && var1.getNetworkHandler() != null && AsellBridge.running() != null) {
               this.log("[" + user + "] nhận lệnh asell-restart từ file → /asell stop rồi /asell auto");
               var1.getNetworkHandler().sendChatCommand("asell stop");
               this.autoAt = this.ticks + 60;
            }
         } else if (!cmd.isEmpty()) {
            this.log("[" + user + "] lệnh trong file cmd không hiểu: " + cmd);
         }
      } catch (Throwable var5) {
         this.log("[" + user + "] không đọc được file lệnh: " + var5);
      }
   }

   private void onStopping() {
      if (!this.exiting) {
         // bạn tự tắt game (Quit / nút X) → script không mở lại
         this.writeExit("user", "người dùng tự tắt game");
      }
   }

   private void requestRestart(MinecraftClient var1, String var2) {
      if (!this.exiting) {
         this.exiting = true;
         this.writeExit("restart", var2);
         this.log("[" + this.user(var1) + "] thoát game để script bấm Enter the game mở lại (" + var2 + ")");

         try {
            var1.scheduleStop();
         } catch (Throwable var4) {
            this.log("không gọi được scheduleStop: " + var4 + " → thoát tiến trình");
            Runtime.getRuntime().halt(0);
         }
      }
   }

   private void writeExit(String var1, String var2) {
      if (this.dir != null) {
         try {
            String var3 = var1 + "\n" + this.user(MinecraftClient.getInstance()) + "\n" + var2.replace('\n', ' ') + "\n" + LocalDateTime.now().format(TS) + "\n";
            Files.writeString(this.dir.resolve("exit-" + this.pid + ".txt"), var3, StandardCharsets.UTF_8);
         } catch (Throwable var4) {
            LOG.info("[DonutRelaunch] Không ghi được exit-" + this.pid + ".txt: " + var4);
         }
      }
   }

   private void log(String var1) {
      LOG.info("[DonutRelaunch] " + var1);
      if (this.dir != null) {
         try {
            Files.writeString(
               this.dir.resolve("relaunch.log"),
               LocalDateTime.now().format(TS) + " pid " + this.pid + " " + var1.replace('\r', ' ').replace('\n', ' ') + "\n",
               StandardCharsets.UTF_8,
               StandardOpenOption.CREATE,
               StandardOpenOption.APPEND
            );
         } catch (Throwable var3) {
         }
      }
   }

   static boolean isSessionError(String var0) {
      String var1 = var0.toLowerCase(Locale.ROOT);
      return var1.contains("invalid session")
         || var1.contains("invalidsession")
         || var1.contains("failed to verify username")
         || var1.contains("unverified_username");
   }

   /** gom mọi dòng chữ trên màn hình mất kết nối (tiêu đề, lý do) — đọc bằng reflection để không phụ thuộc tên trường */
   static String reasonOf(Screen var0) {
      List<String> var1 = new ArrayList<>();

      for (Class<?> var2 = var0.getClass(); var2 != null && var2 != Object.class; var2 = var2.getSuperclass()) {
         for (Field var6 : var2.getDeclaredFields()) {
            if (!Modifier.isStatic(var6.getModifiers())) {
               try {
                  var6.setAccessible(true);
                  collect(var6.get(var0), var1, 0);
               } catch (Throwable var8) {
               }
            }
         }
      }

      return String.join(" | ", var1);
   }

   private static void collect(Object var0, List<String> var1, int var2) {
      if (var0 != null && var2 <= 2) {
         if (var0 instanceof Text) {
            String var3 = ((Text)var0).getString();
            if (var3 != null && !var3.isBlank() && !var1.contains(var3)) {
               var1.add(var3);
            }
         } else if (var0.getClass().isRecord()) {
            for (RecordComponent var6 : var0.getClass().getRecordComponents()) {
               try {
                  var6.getAccessor().setAccessible(true);
                  collect(var6.getAccessor().invoke(var0), var1, var2 + 1);
               } catch (Throwable var8) {
               }
            }
         }
      }
   }

   /** ServerInfo(tên, địa chỉ, ServerType.OTHER) — tạo bằng reflection (hằng enum không có tên trong mapping) */
   static ServerInfo makeInfo(String var0) {
      for (Constructor<?> var4 : ServerInfo.class.getDeclaredConstructors()) {
         Class<?>[] var5 = var4.getParameterTypes();
         if (var5.length == 3 && var5[0] == String.class && var5[1] == String.class && var5[2].isEnum()) {
            try {
               Object[] var6 = var5[2].getEnumConstants();
               var4.setAccessible(true);
               return (ServerInfo)var4.newInstance(var0, var0, var6[var6.length - 1]);
            } catch (Throwable var7) {
            }
         }
      }

      return null;
   }
}
