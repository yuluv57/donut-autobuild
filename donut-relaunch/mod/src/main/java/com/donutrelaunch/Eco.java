package com.donutrelaunch;

import com.sun.management.HotSpotDiagnosticMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import net.minecraft.client.MinecraftClient;

/**
 * Chế độ tiết kiệm khi cửa sổ game KHÔNG được chọn (bạn đang làm việc khác):
 * <ul>
 *   <li>không vẽ thế giới, chỉ vẽ lại 1 khung hình mỗi {@code refreshSeconds} giây (cửa sổ vẫn thấy HUD ASell, chỉ cập nhật chậm).
 *       Logic game, mạng, ASell vẫn chạy đủ 20 tick/giây. Bấm vào cửa sổ là vẽ bình thường ngay.</li>
 *   <li>mỗi {@code gcMinutes} phút dọn RAM (System.gc) và cho JVM trả bớt bộ nhớ heap thừa lại cho Windows.</li>
 *   <li>ghi số đo CPU/RAM theo luồng vào {@code perf-<tên>.log} để biết cái gì ăn tài nguyên.</li>
 * </ul>
 * Cấu hình ở {@code eco.txt} (đọc lại mỗi phút).
 */
final class Eco {
   private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
   private final Path dir;
   private final Consumer<String> log;
   private final Supplier<String> user;
   private boolean enabled = true;
   private int refreshSeconds = 5;
   private int gcMinutes = 10;
   private int perfMinutes = 10;
   private long cfgReadAt;
   private boolean skipping;
   private long lastFrameAt;
   private long lastGcAt = System.currentTimeMillis();
   private boolean heapFlagsTried;
   private int gcLogged;
   private long lastPerfAt;
   private final Map<Long, Long> threadCpu = new HashMap<>();
   private final Map<Long, String> threadNames = new HashMap<>();
   private long lastProcCpu = -1L;
   private long lastPerfWall;
   private long lastGcCount;
   private long lastGcTime;
   private boolean startLogged;
   private long minHeapBytes;
   private boolean trimSkipSaid;
   private int pollerHot;
   private boolean pollerSaid;

   Eco(Path dir, Consumer<String> log, Supplier<String> user) {
      this.dir = dir;
      this.log = log;
      this.user = user;
   }

   void tick(MinecraftClient mc) {
      long now = System.currentTimeMillis();
      this.readConfig(now);
      if (!this.startLogged) {
         this.startLogged = true;
         this.logJvm();
         this.fixModMenu();
      }

      boolean inWorld = mc.player != null && mc.world != null && mc.getOverlay() == null;
      boolean away = this.enabled && inWorld && !mc.isWindowFocused();
      if (away && this.refreshSeconds > 0) {
         if (!this.skipping) {
            this.skipping = true;
            this.lastFrameAt = now;
         }

         if (now - this.lastFrameAt >= this.refreshSeconds * 1000L) {
            // cho vẽ 1 khung hình để cửa sổ vẫn cập nhật (HUD ASell, chat)
            this.lastFrameAt = now;
            mc.skipGameRender = false;
         } else {
            // setScreen() của game tự đặt lại false mỗi lần mở GUI → đặt lại mỗi tick
            mc.skipGameRender = true;
         }
      } else if (this.skipping) {
         this.skipping = false;
         mc.skipGameRender = false;
      }

      if (away && this.gcMinutes > 0 && now - this.lastGcAt >= this.gcMinutes * 60000L) {
         this.lastGcAt = now;
         this.trimMemory();
      }

      if (this.perfMinutes > 0 && now - this.lastPerfAt >= this.perfMinutes * 60000L) {
         this.perf(now, away);
      }
   }

   /** gỡ chế độ tiết kiệm (khi game sắp thoát) để không để lại cờ */
   void release(MinecraftClient mc) {
      if (this.skipping) {
         this.skipping = false;
         mc.skipGameRender = false;
      }
   }

   private void readConfig(long now) {
      if (now - this.cfgReadAt < 60000L || this.dir == null) {
         return;
      }

      this.cfgReadAt = now;
      Path f = this.dir.resolve("eco.txt");

      try {
         if (!Files.exists(f)) {
            Files.writeString(
               f,
               "# Chế độ tiết kiệm (mod Donut Relaunch) — chỉ áp dụng khi cửa sổ game KHÔNG được chọn. Sửa xong 1 phút sau tự đọc.\n"
                  + "# enabled: true = bật, false = tắt hẳn\n"
                  + "enabled=true\n"
                  + "# không vẽ thế giới, chỉ vẽ lại 1 khung hình mỗi N giây (0 = vẽ bình thường)\n"
                  + "refreshSeconds=5\n"
                  + "# mỗi N phút dọn RAM và trả bớt bộ nhớ thừa cho Windows (0 = tắt). Tự bỏ qua khi heap đã sát mức -Xms của launcher.\n"
                  + "gcMinutes=10\n"
                  + "# mỗi N phút ghi số đo CPU/RAM vào perf-<tên>.log (0 = tắt)\n"
                  + "perfMinutes=10\n",
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
               switch (k) {
                  case "enabled":
                     this.enabled = !v.equalsIgnoreCase("false") && !v.equals("0");
                     break;
                  case "refreshseconds":
                     this.refreshSeconds = Math.max(0, Integer.parseInt(v));
                     break;
                  case "gcminutes":
                     this.gcMinutes = Math.max(0, Integer.parseInt(v));
                     break;
                  case "perfminutes":
                     this.perfMinutes = Math.max(0, Integer.parseInt(v));
               }
            } catch (NumberFormatException var11) {
            }
         }
      } catch (Throwable var12) {
      }
   }

   /** heap tối thiểu (-Xms): G1 không bao giờ trả RAM xuống dưới mức này; -1 = không đọc được */
   private long minHeap() {
      if (this.minHeapBytes != 0L) {
         return this.minHeapBytes;
      }

      this.minHeapBytes = -1L;

      try {
         HotSpotDiagnosticMXBean hs = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);

         for (String k : new String[]{"MinHeapSize", "InitialHeapSize"}) {
            try {
               long v = Long.parseLong(hs.getVMOption(k).getValue());
               if (v > 0L) {
                  this.minHeapBytes = v;
                  break;
               }
            } catch (Throwable var7) {
            }
         }
      } catch (Throwable var8) {
      }

      return this.minHeapBytes;
   }

   /**
    * dọn rác toàn phần. Legacy Launcher chạy game với -XX:+DisableExplicitGC nên System.gc() bị bỏ qua; lệnh chẩn đoán
    * GC.run của JVM (như "jcmd GC.run") thì vẫn chạy được.
    */
   private static boolean gcRun() {
      try {
         MBeanServer s = ManagementFactory.getPlatformMBeanServer();
         s.invoke(new ObjectName("com.sun.management:type=DiagnosticCommand"), "gcRun", new Object[]{new String[0]}, new String[]{String[].class.getName()});
         return true;
      } catch (Throwable var1) {
         System.gc();
         return false;
      }
   }

   /** dọn rác toàn phần rồi để G1 co heap: MinHeapFreeRatio/MaxHeapFreeRatio là cờ "manageable", đổi được lúc đang chạy */
   private void trimMemory() {
      Runtime rt = Runtime.getRuntime();
      long min = this.minHeap();
      if (min > 0L && rt.totalMemory() - min < 134217728L) {
         // heap đang giữ đã sát mức -Xms → dọn cũng không trả được RAM nào, chỉ tốn CPU
         if (!this.trimSkipSaid) {
            this.trimSkipSaid = true;
            this.log
               .accept(
                  "["
                     + this.user.get()
                     + "] bỏ qua dọn RAM: heap đang giữ "
                     + mb(rt.totalMemory())
                     + " MB mà -Xms của launcher là "
                     + mb(min)
                     + " MB, JVM không trả RAM dưới mức này"
               );
         }

         return;
      }

      if (!this.heapFlagsTried) {
         this.heapFlagsTried = true;

         try {
            HotSpotDiagnosticMXBean hs = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
            hs.setVMOption("MinHeapFreeRatio", "10");
            hs.setVMOption("MaxHeapFreeRatio", "30");
            this.log.accept("[" + this.user.get() + "] tiết kiệm RAM: cho JVM trả heap thừa (MinHeapFreeRatio=10, MaxHeapFreeRatio=30)");
         } catch (Throwable var9) {
            this.log.accept("[" + this.user.get() + "] không đổi được cờ co heap của JVM: " + var9);
         }
      }

      long before = rt.totalMemory();
      long usedBefore = before - rt.freeMemory();
      long t0 = System.nanoTime();
      boolean viaDcmd = gcRun();
      long ms = (System.nanoTime() - t0) / 1000000L;
      long after = rt.totalMemory();
      long usedAfter = after - rt.freeMemory();
      if (this.gcLogged < 3 || this.gcLogged % 12 == 0) {
         this.log
            .accept(
               "["
                  + this.user.get()
                  + "] dọn RAM: heap đang giữ "
                  + mb(before)
                  + " → "
                  + mb(after)
                  + " MB (đang dùng "
                  + mb(usedBefore)
                  + " → "
                  + mb(usedAfter)
                  + " MB, dừng "
                  + ms
                  + " ms"
                  + (viaDcmd ? "" : ", System.gc — có thể bị -XX:+DisableExplicitGC chặn")
                  + ")"
            );
      }

      this.gcLogged++;
   }

   private void logJvm() {
      try {
         Runtime rt = Runtime.getRuntime();
         List<String> flags = new ArrayList<>();

         for (String a : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            String low = a.toLowerCase(Locale.ROOT);
            // chỉ cờ bộ nhớ/GC; không bao giờ ghi thứ có thể là mã đăng nhập
            if (a.startsWith("-X") && !low.contains("token") && !low.contains("session") && !low.contains("pass")) {
               flags.add(a);
            }
         }

         List<String> gcs = new ArrayList<>();

         for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcs.add(gc.getName());
         }

         this.writePerf(
            "khởi động: heap tối đa "
               + mb(rt.maxMemory())
               + " MB, đang giữ "
               + mb(rt.totalMemory())
               + " MB, "
               + rt.availableProcessors()
               + " luồng CPU, GC "
               + gcs
               + ", cờ JVM "
               + flags
         );
      } catch (Throwable var6) {
      }
   }

   /** CPU của tiến trình + top luồng ăn CPU trong khoảng vừa qua, RAM heap, GC */
   private void perf(long now, boolean away) {
      try {
         this.lastPerfAt = now;
         ThreadMXBean tb = ManagementFactory.getThreadMXBean();
         if (tb.isThreadCpuTimeSupported() && !tb.isThreadCpuTimeEnabled()) {
            tb.setThreadCpuTimeEnabled(true);
         }

         long wallNs = System.nanoTime();
         Map<Long, Long> cur = new HashMap<>();
         Map<String, Long> deltaByName = new HashMap<>();

         for (ThreadInfo ti : tb.getThreadInfo(tb.getAllThreadIds())) {
            if (ti == null) {
               continue;
            }

            long cpu = tb.getThreadCpuTime(ti.getThreadId());
            if (cpu < 0L) {
               continue;
            }

            cur.put(ti.getThreadId(), cpu);
            this.threadNames.put(ti.getThreadId(), ti.getThreadName());
            Long prev = this.threadCpu.get(ti.getThreadId());
            if (prev != null && this.lastPerfWall > 0L) {
               // gom luồng cùng loại (Worker-Main-12 → Worker-Main)
               String name = ti.getThreadName().replaceAll("[-#\\s]*\\d+$", "");
               deltaByName.merge(name, cpu - prev, Long::sum);
            }
         }

         this.threadCpu.clear();
         this.threadCpu.putAll(cur);
         long procCpu = -1L;
         OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
         if (os instanceof com.sun.management.OperatingSystemMXBean) {
            procCpu = ((com.sun.management.OperatingSystemMXBean)os).getProcessCpuTime();
         }

         long gcCount = 0L;
         long gcTime = 0L;

         for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += Math.max(0L, gc.getCollectionCount());
            gcTime += Math.max(0L, gc.getCollectionTime());
         }

         Runtime rt = Runtime.getRuntime();
         if (this.lastPerfWall > 0L) {
            double wall = (double)(wallNs - this.lastPerfWall);
            int cores = Math.max(1, rt.availableProcessors());
            StringBuilder sb = new StringBuilder();
            sb.append(away ? "[không xem] " : "[đang xem] ");
            if (procCpu >= 0L && this.lastProcCpu >= 0L) {
               sb.append(String.format(Locale.ROOT, "CPU %.1f%% cả máy", (double)(procCpu - this.lastProcCpu) * 100.0 / wall / (double)cores));
            }

            sb.append(", heap ").append(mb(rt.totalMemory() - rt.freeMemory())).append('/').append(mb(rt.totalMemory())).append('/').append(mb(rt.maxMemory()));
            sb.append(" MB (dùng/giữ/tối đa), GC ").append(gcCount - this.lastGcCount).append(" lần ").append(gcTime - this.lastGcTime).append(" ms");
            List<Map.Entry<String, Long>> top = new ArrayList<>(deltaByName.entrySet());
            top.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
            sb.append(", luồng ăn CPU:");

            for (int i = 0; i < Math.min(6, top.size()); i++) {
               Map.Entry<String, Long> e = top.get(i);
               sb.append(String.format(Locale.ROOT, " %s %.1f%%", e.getKey(), (double)e.getValue().longValue() * 100.0 / wall / (double)cores));
            }

            if (os instanceof com.sun.management.OperatingSystemMXBean) {
               com.sun.management.OperatingSystemMXBean so = (com.sun.management.OperatingSystemMXBean)os;
               long tot = so.getTotalMemorySize();
               long free = so.getFreeMemorySize();
               sb.append(", tiến trình cam kết ").append(mb(so.getCommittedVirtualMemorySize())).append(" MB");
               if (tot > 0L) {
                  sb.append(String.format(Locale.ROOT, ", RAM cả máy dùng %.0f%% (%.1f/%.1f GB)", (double)(tot - free) * 100.0 / (double)tot, (tot - free) / 1.073741824E9, tot / 1.073741824E9));
               }
            }

            this.writePerf(sb.toString());
            // luồng Read-Poller/Write-Poller của Java chỉ phục vụ "luồng ảo" chờ mạng; nó chiếm CPU liên tục = đang quay vòng vô ích
            // (đã gặp: ModMenu tự kiểm tra cập nhật bằng luồng ảo → Write-Poller ăn trọn 1 nhân CPU suốt phiên chơi)
            long poll = deltaByName.getOrDefault("Write-Poller", 0L) + deltaByName.getOrDefault("Read-Poller", 0L);
            double pollPct = (double)poll * 100.0 / wall / (double)cores;
            this.pollerHot = pollPct >= 4.0 ? this.pollerHot + 1 : 0;
            if (this.pollerHot >= 2 && !this.pollerSaid) {
               this.pollerSaid = true;
               this.log
                  .accept(
                     "["
                        + this.user.get()
                        + String.format(Locale.ROOT, "] CẢNH BÁO: luồng Poller của Java đang quay vòng, ăn %.1f%% CPU cả máy. ", pollPct)
                        + (this.fixModMenu()
                           ? "Đã tắt tự kiểm tra cập nhật của ModMenu (nguyên nhân hay gặp) — có hiệu lực từ lần mở game sau."
                           : "ModMenu đã tắt kiểm tra cập nhật sẵn → nguyên nhân khác, xem perf log.")
                  );
            }
         }

         boolean baseline = this.lastPerfWall == 0L;
         this.lastPerfWall = wallNs;
         this.lastProcCpu = procCpu;
         this.lastGcCount = gcCount;
         this.lastGcTime = gcTime;
         if (baseline && this.perfMinutes > 1) {
            // lần đầu chỉ lấy mốc → 1 phút sau đo lần nữa cho có số liệu sớm
            this.lastPerfAt = now - (this.perfMinutes - 1) * 60000L;
         }
      } catch (Throwable var22) {
      }
   }

   /**
    * ModMenu tự kiểm tra cập nhật mod bằng luồng ảo (virtual thread) lúc mở game; trên Windows việc này làm luồng Write-Poller của Java
    * quay vòng ăn trọn 1 nhân CPU cho tới khi tắt game. Tắt update_checker trong config/modmenu.json (có hiệu lực từ lần mở sau).
    * true = vừa sửa file; false = đã tắt sẵn / không có ModMenu.
    */
   boolean fixModMenu() {
      if (this.dir == null) {
         return false;
      }

      Path f = this.dir.getParent().resolve("config").resolve("modmenu.json");

      try {
         if (!Files.exists(f)) {
            return false;
         }

         String s = Files.readString(f, StandardCharsets.UTF_8);
         String t = s.replaceAll("(\"update_checker\"\\s*:\\s*)true", "$1false").replaceAll("(\"button_update_badge\"\\s*:\\s*)true", "$1false");
         if (t.equals(s)) {
            return false;
         }

         Files.writeString(f, t, StandardCharsets.UTF_8);
         this.log.accept("[" + this.user.get() + "] đã tắt ModMenu tự kiểm tra cập nhật (config/modmenu.json) để tránh luồng Write-Poller ăn CPU");
         return true;
      } catch (Throwable var4) {
         return false;
      }
   }

   private void writePerf(String line) {
      if (this.dir != null) {
         try {
            Files.writeString(
               this.dir.resolve("perf-" + this.user.get() + ".log"),
               LocalDateTime.now().format(TS) + " pid " + ProcessHandle.current().pid() + " " + line + "\n",
               StandardCharsets.UTF_8,
               StandardOpenOption.CREATE,
               StandardOpenOption.APPEND
            );
         } catch (Throwable var3) {
         }
      }
   }

   private static long mb(long bytes) {
      return bytes / 1048576L;
   }
}
