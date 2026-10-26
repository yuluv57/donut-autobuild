package com.donutautobuild.build;

import com.donutautobuild.AutoBuildMod;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;

public final class InventoryAccess {
   private InventoryAccess() {
   }

   public static int count(MinecraftClient var0, Item var1) {
      if (var0.player == null) {
         return 0;
      } else {
         int var2 = 0;

         for (int var3 = 0; var3 < 36; var3++) {
            if (var0.player.getInventory().getStack(var3).isOf(var1)) {
               var2 += var0.player.getInventory().getStack(var3).getCount();
            }
         }

         return var2;
      }
   }

   public static int freeSlots(MinecraftClient var0) {
      int var1 = 0;

      for (int var2 = 0; var2 < 36; var2++) {
         if (var0.player.getInventory().getStack(var2).isEmpty()) {
            var1++;
         }
      }

      return var1;
   }

    public static boolean equip(MinecraftClient mc, Item item, int hotbarSlot) {
        if (mc.player == null || mc.interactionManager == null || mc.currentScreen != null
            || mc.player.currentScreenHandler != mc.player.playerScreenHandler
            || !mc.player.currentScreenHandler.getCursorStack().isEmpty() || !synced(mc)) return false;
        PlayerInventory inventory = mc.player.getInventory();
        if (equipMatches(mc.player.getMainHandStack(), item)) { hotbarUsed[inventory.selectedSlot] = System.currentTimeMillis(); return true; }
        for (int slot = 0; slot < 9; slot++) {
            if (equipMatches(inventory.getStack(slot), item)) {
                // 3.0.123: đồ đã ở thanh nóng → đổi ô như bấm phím số 1–9 (không mở túi đồ)
                if (inventory.selectedSlot != slot) trace(mc, "chọn ô " + slot + " (" + itemName(inventory.getStack(slot)) + ")");
                inventory.selectedSlot = slot;
                hotbarUsed[slot] = System.currentTimeMillis();
                return true;
            }
        }
        for (int slot = 9; slot < 36; slot++) {
            if (equipMatches(inventory.getStack(slot), item)) {
                // 3.0.123: phải bấm vào túi đồ → đứng yên hẳn như người thật mở túi (đang đi/chạy mà bấm ô thì anti-cheat kéo lùi)
                if (!invReady(mc)) return false;
                int target = pickHotbar(mc, hotbarSlot);
                String before = "ô " + slot + "=" + itemName(inventory.getStack(slot)) + " ↔ ô " + target + "=" + itemName(inventory.getStack(target));
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot, target, SlotActionType.SWAP, mc.player);
                invClicked();
                inventory.selectedSlot = target;
                hotbarUsed[target] = System.currentTimeMillis();
                noteClick(mc, "đổi đồ (SWAP) " + before);
                // 3.0.122: sau SWAP xin server gửi lại cả túi đồ (server bỏ qua SWAP → đặt nhầm loại); 3.0.123: gói đóng túi gửi ngay sau
                confirmNow(mc, "vừa đổi đồ (SWAP) " + before);
                return false;
            }
        }
        return false;
    }

    // ===== 3.0.123: bấm vào túi đồ như người thật =====
    // Log 3.0.122 (19:29:51, 19:30:05): mod bấm ô túi đồ (SWAP, xin gửi lại túi đồ) mà KHÔNG BAO GIỜ gửi gói "đóng túi" như khi người
    // chơi đóng túi (E). Anti-cheat coi túi đồ đang mở → di chuyển bị kéo lùi, đặt khối bị huỷ ("đặt rồi nhưng không hiện"), đập bị huỷ
    // ("client thấy vỡ nhưng server chưa phá"). Giờ: chỉ bấm khi đứng yên (hết trượt, không chạy nhanh) và cuối tick gửi gói đóng túi.
    private static final long[] hotbarUsed = new long[9];
    private static boolean closePending;
    /**
     * 3.0.124: đang làm việc với menu server (shop, order, home, bán, ender chest, khối chứa…). Server đóng MỌI menu đang mở khi nhận
     * gói đóng túi (không xét số menu) → log 3.0.123: vừa gõ bảng tìm "bone meal" xong, gói xin đồng bộ + gói đóng túi đi ngay sau
     * → shop bị đóng, quay về Quick Buy. Lúc này không bấm túi đồ, không gửi gói đóng túi; để dành tới khi xong menu.
     */
    private static long menuBusyAt = -100000L;

    public static void setMenuBusy(boolean busy) {
        if (busy) menuBusyAt = System.currentTimeMillis();
    }

    /** còn hiệu lực 300 ms sau lần cuối BuildController báo bận (mod dừng hẳn thì tự hết) */
    private static boolean menuBusy() {
        return System.currentTimeMillis() - menuBusyAt < 300L;
    }
    private static long stillWantedAt;

    /** đứng yên đủ để bấm vào túi đồ; chưa yên thì yêu cầu BuildController dừng lại (stillWanted) */
    public static boolean invReady(MinecraftClient mc) {
        if (mc.player == null) return false;
        boolean still;
        try {
            net.minecraft.util.math.Vec3d velocity = mc.player.getVelocity();
            still = (mc.player.isOnGround() || mc.player.isTouchingWater())
                && (velocity == null || velocity.horizontalLengthSquared() < 4.0E-4)
                && !mc.player.isSprinting();
        } catch (RuntimeException error) {
            still = true;
        }
        if (!still) stillWantedAt = System.currentTimeMillis();
        return still;
    }

    /** vừa có ít nhất 1 cú bấm vào ô túi đồ chính → cuối tick gửi gói đóng túi */
    public static void invClicked() {
        closePending = true;
        lastInvClickAt = System.currentTimeMillis();
    }

    private static long lastInvClickAt;

    /** ms từ lần bấm ô túi đồ gần nhất (cho log khi bị server kéo lùi) */
    public static long sinceInvClick() {
        return lastInvClickAt == 0L ? -1L : System.currentTimeMillis() - lastInvClickAt;
    }

    /** trong 200 ms qua có việc chờ đứng yên để bấm túi đồ */
    public static boolean stillWanted() {
        return System.currentTimeMillis() - stillWantedAt < 200L;
    }

    /** cuối tick (AutoBuildMod): đã bấm ô túi đồ → gửi gói đóng túi (CloseHandledScreen của túi đồ, như khi người chơi đóng túi) */
    public static void flushClose(MinecraftClient mc) {
        if (!closePending) return;
        closePending = false;
        if (menuBusy()) return;
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) return;
        if (mc.currentScreen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen) return;
        try {
            mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket(mc.player.playerScreenHandler.syncId));
        } catch (Throwable error) {
            AutoBuildMod.LOGGER.warn("[AutoBuild] Không gửi được gói đóng túi đồ: {}", error.toString());
        }
    }

    /**
     * 3.0.123 (người dùng: "dùng tất cả ô thanh nóng, miễn tay trái cầm totem"): chọn ô thanh nóng nhận đồ từ túi. Giữ 1 ô cúp;
     * ưu tiên ô trống, rồi ô đang để totem (totem đã ở tay trái), rồi ô lâu nhất chưa dùng. Vật liệu dùng nhiều nằm sẵn trên thanh
     * nóng → đổi bằng phím số, ít phải mở túi.
     */
    private static int pickHotbar(MinecraftClient mc, int fallback) {
        PlayerInventory inventory = mc.player.getInventory();
        int pickaxe = -1;
        for (int slot = 0; slot < 9 && pickaxe < 0; slot++)
            if (SurvivalSupplies.usablePickaxe(inventory.getStack(slot))) pickaxe = slot;
        int best = -1; long bestScore = Long.MAX_VALUE;
        for (int slot = 0; slot < 9; slot++) {
            if (slot == pickaxe) continue;
            ItemStack stack = inventory.getStack(slot);
            long score = stack.isEmpty() ? -2L : stack.isOf(net.minecraft.item.Items.TOTEM_OF_UNDYING) ? -1L : hotbarUsed[slot];
            if (score < bestScore) { bestScore = score; best = slot; }
        }
        return best >= 0 ? best : Math.max(0, Math.min(8, fallback));
    }

    /** 3.0.123: tay trái luôn cầm totem; trống/khác thì đưa 1 totem trong túi lên (cùng điều kiện bấm túi đồ) */
    public static boolean offhandTotem(MinecraftClient mc) {
        try {
            return offhandTotemNow(mc);
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean offhandTotemNow(MinecraftClient mc) {
        if (mc.player == null || mc.interactionManager == null || mc.currentScreen != null
            || mc.player.currentScreenHandler != mc.player.playerScreenHandler
            || !mc.player.currentScreenHandler.getCursorStack().isEmpty() || mc.player.getInventory() == null) return false;
        if (mc.player.getOffHandStack().isOf(net.minecraft.item.Items.TOTEM_OF_UNDYING)) return false;
        PlayerInventory inventory = mc.player.getInventory();
        int from = -1;
        for (int slot = 9; slot < 36 && from < 0; slot++) if (inventory.getStack(slot).isOf(net.minecraft.item.Items.TOTEM_OF_UNDYING)) from = slot;
        for (int slot = 0; slot < 9 && from < 0; slot++) if (inventory.getStack(slot).isOf(net.minecraft.item.Items.TOTEM_OF_UNDYING)) from = slot;
        if (from < 0 || !synced(mc) || !invReady(mc)) return false;
        int screenSlot = from < 9 ? 36 + from : from;
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, screenSlot, 40, SlotActionType.SWAP, mc.player);
        invClicked();
        noteClick(mc, "đưa totem ở ô " + from + " lên tay trái");
        markDirty(mc, "vừa đưa totem lên tay trái");
        AutoBuildMod.LOGGER.info("[AutoBuild] Tay trái chưa cầm totem → đưa totem ở ô {} lên tay trái", from);
        return true;
    }


   private static int syncRev = -1;
   private static long syncSince;
   public static long settleMs = 350L;
   /** 3.0.106: ping hiện tại (BuildController cập nhật mỗi tick) */
   public static long rttMs = 300L;
   private static long lastClickAt = -100000L;

   // ===== 3.0.121: túi đồ "chưa kiểm chứng" (dirty) =====
   // Người dùng (29/09–01/10): "vẫn đặt sai rất nhiều, hãy rà soát từ đầu". Rà soát: mọi lần đặt nhầm loại có cùng dấu hiệu — lúc bấm,
   // client tin ô đang cầm là món A, server lại có món B ở ô đó; sau cú đặt server mới gửi lại ô thật (log: "Server đặt Oak Leaves
   // thay vì Cobblestone", tay client đổi thành Oak Leaves sau cú bấm). Client chỉ tin sai khi túi đồ đã bị đổi theo DỰ ĐOÁN mà server
   // chưa xác nhận, và server không tự gửi lại các ô đó nếu chính nó không đổi gì:
   //   - đổ/múc xô: client tự đổi Xô nước → Xô rỗng ngay khi bấm; server từ chối lần đổ thì ô đó ở server vẫn là Xô nước;
   //   - gộp xô rỗng / cất đồ trên con trỏ / ném con trỏ: 2–3 cú bấm ô liền nhau dựa trên túi đồ đang có thể sai;
   //   - vừa đóng menu server (shop, sell, order, ender chest, crafter): món mua/lấy về có thể còn trên đường.
   // Sau các việc đó túi đồ bị đánh dấu "chưa kiểm chứng": không đổi đồ, không đặt khối tới khi server gửi lại TOÀN BỘ túi đồ
   // (gói đồng bộ 3.0.106), rồi mới tin. Đổ xô mà server xác nhận nước đã vào (pourConfirmed) thì không cần chờ.
   private static boolean dirty;
   private static boolean pourOnly;
   private static String dirtyWhy = "";
   private static long dirtySince;
   private static long dirtyFirst;
   private static int dirtySeq;
   private static int dirtySeqAtSend = -1;

   public static void markDirty(MinecraftClient var0, String var1) {
      if (!dirty) {
         dirtyFirst = System.currentTimeMillis();
      }

      dirtySince = System.currentTimeMillis();
      dirty = true;
      pourOnly = false;
      dirtyWhy = var1;
      dirtySeq++;
      trace(var0, "chưa kiểm chứng: " + var1);
   }

   /** vừa đổ/múc xô (client đổi món trên tay theo dự đoán) */
   public static void markPour(MinecraftClient var0) {
      boolean var1 = !dirty || pourOnly;
      markDirty(var0, "vừa đổ/múc xô");
      pourOnly = var1;
   }

   /**
    * 3.0.121: mọi cú bấm ô đồ trong túi (SWAP, gộp xô, cất con trỏ, ném con trỏ) → server trả lời sau 1 lượt ping. Trước đây chỉ chờ
    * min(300 ms, ping/2+100) và chỉ sau SWAP; gộp xô thì bấm liên tục mỗi 2 tick (0,1 giây) dựa trên túi đồ chưa được server xác nhận.
    */
   public static void noteClick(MinecraftClient var0, String var1) {
      lastClickAt = System.currentTimeMillis();
      syncRev = 1;
      syncSince = lastClickAt;
      trace(var0, var1);
   }

   /** server đã xác nhận nước vào đúng ô → dự đoán xô rỗng trên tay là đúng, không cần đồng bộ cả túi */
   public static void pourConfirmed(MinecraftClient var0) {
      if (dirty && pourOnly && resyncSentAt == 0L) {
         dirty = false;
         pourOnly = false;
         wantResync = null;
         trace(var0, "server xác nhận đổ nước → túi đồ khớp");
      }
   }

   public static boolean dirty() {
      return dirty;
   }

   // ===== 3.0.121: nhật ký ngắn các thao tác túi đồ (in ra log khi phát hiện đặt nhầm loại để biết chính xác vì sao) =====
   private static final String[] TRACE = new String[40];
   private static int traceN;
   private static long traceStart = System.currentTimeMillis();

   private static String itemName(ItemStack var0) {
      return var0 == null || var0.isEmpty() ? "-" : Registries.ITEM.getId(var0.getItem()).getPath() + "x" + var0.getCount();
   }

   public static void trace(MinecraftClient var0, String var1) {
      try {
         String var2 = "";
         if (var0 != null && var0.player != null) {
            PlayerInventory var3 = var0.player.getInventory();
            var2 = " | tay ô " + var3.selectedSlot + "=" + itemName(var3.getStack(var3.selectedSlot)) + " | rev " + var0.player.playerScreenHandler.getRevision()
               + (var0.player.playerScreenHandler.getCursorStack().isEmpty() ? "" : " | con trỏ " + itemName(var0.player.playerScreenHandler.getCursorStack()));
         }

         TRACE[traceN++ % TRACE.length] = String.format("+%.2fs %s%s", (System.currentTimeMillis() - traceStart) / 1000.0, var1, var2);
      } catch (Throwable var4) {
      }
   }

   /** in ra log: thanh nóng hiện tại (theo client) + các thao tác túi đồ gần nhất */
   public static void dumpTrace(MinecraftClient var0, String var1) {
      try {
         StringBuilder var2 = new StringBuilder();
         if (var0 != null && var0.player != null) {
            PlayerInventory var3 = var0.player.getInventory();

            for (int var4 = 0; var4 < 9; var4++) {
               var2.append(var4 == var3.selectedSlot ? " [" : " ").append(var4).append(':').append(itemName(var3.getStack(var4))).append(var4 == var3.selectedSlot ? "]" : "");
            }
         }

         AutoBuildMod.LOGGER.info("[AutoBuild] [TÚI ĐỒ] {} → thanh nóng (client):{} | đang chờ đồng bộ={} | túi chưa kiểm chứng={} ({})", new Object[]{var1, var2, resyncSentAt > 0L, dirty, dirtyWhy});
         int var6 = Math.max(0, traceN - 14);

         for (int var5 = var6; var5 < traceN; var5++) {
            AutoBuildMod.LOGGER.info("[AutoBuild] [TÚI ĐỒ]   {}", TRACE[var5 % TRACE.length]);
         }
      } catch (Throwable var7) {
      }
   }

   /** thời điểm tới đó không xin đồng bộ nữa (server không trả lời cả 2 cách xin) */
   private static long resyncOffUntil;
   private static int doubleMissEvents;
   private static boolean menuSinceSend;

   private static String resyncHow() {
      return resyncMode == 0 ? "bấm giữa ngoài túi" : "shift-click ô trống";
   }

   /** 3.0.121: kết thúc lần xin đồng bộ đang chờ — gọi mỗi tick (kể cả lúc tạm dừng) và trong synced() */
   private static void pollResync(MinecraftClient var0) {
      if (resyncSentAt > 0L && var0.player != null) {
         long var1 = System.currentTimeMillis();
         boolean var3 = var0.player.playerScreenHandler.getRevision() != resyncRev;
         if (var3 && var1 - resyncSentAt >= rttMs + 150L || var1 - resyncSentAt > 3000L) {
            resyncSentAt = 0L;
            boolean var5 = menuSinceSend;
            menuSinceSend = false;
            syncRev = 1;
            // gói "cả túi đồ" đã về → chỉ chờ thêm 150 ms (không chờ thêm trọn 1 lượt ping như sau 1 cú bấm)
            syncSince = var1 - Math.max(0L, settleMs - 150L);
            if (var3) {
               resyncMisses = 0;
               doubleMissEvents = 0;
               if (dirtySeq == dirtySeqAtSend) {
                  // không có thao tác nào làm lệch túi đồ sau lúc gửi → túi đồ client giờ đúng như server
                  dirty = false;
                  pourOnly = false;
               }

               if (forcedCount <= 5 || forcedCount % 20 == 0) {
                  AutoBuildMod.LOGGER.info("[AutoBuild] Server đã gửi lại túi đồ sau {} ms ({}) → làm tiếp", var1 - resyncSentAtLog, resyncHow());
               }
            } else if (var5 || var0.player.currentScreenHandler != var0.player.playerScreenHandler) {
               // server vừa mở menu (shop/order…) nên bỏ qua cú bấm vào túi đồ → không tính là "server không trả lời", xin lại sau
               AutoBuildMod.LOGGER.info("[AutoBuild] Đang chờ đồng bộ túi đồ thì có menu mở → xin lại sau khi đóng menu");
            } else {
               resyncMisses++;
               String var4 = resyncHow();
               resyncMode = 1 - resyncMode;
               AutoBuildMod.LOGGER.info("[AutoBuild] 3 giây chưa thấy server gửi lại túi đồ ({}, hụt {} lần liên tiếp) → lần sau xin bằng cách {}", new Object[]{var4, resyncMisses, resyncHow()});
               if (resyncMisses >= 2) {
                  // cả 2 cách đều không thấy trả lời → tin túi đồ hiện tại như bản cũ (không đứng chờ mãi)
                  resyncMisses = 0;
                  if (dirtySeq == dirtySeqAtSend) {
                     dirty = false;
                     pourOnly = false;
                  }

                  if (++doubleMissEvents >= 3) {
                     doubleMissEvents = 0;
                     resyncOffUntil = var1 + 600000L;
                     AutoBuildMod.LOGGER.info("[AutoBuild] Server không trả lời yêu cầu đồng bộ túi đồ 3 lần liền → 10 phút tới chỉ chờ 1 lượt ping thay vì xin đồng bộ");
                  }
               }
               // hụt lần 1: vẫn "chưa kiểm chứng" → synced() xin lại ngay bằng cách còn lại
            }

            trace(var0, var3 ? "nhận túi đồ từ server" : "hết 3 giây chưa thấy túi đồ đổi phiên bản");
         }
      }
   }

   public static boolean synced(MinecraftClient var0) {
      if (var0.player == null) {
         return true;
      }

      pollResync(var0);
      long var1 = System.currentTimeMillis();
      if (resyncSentAt > 0L) {
         // 3.0.106: đã xin server gửi lại túi đồ → không đổi đồ / không đặt gì tới khi gói trả lời về (tối đa 3 giây)
         return false;
      } else if (dirty) {
         if (var1 < resyncOffUntil || var1 - dirtySince > 8000L || var1 - dirtyFirst > 20000L) {
            // lưới an toàn: không xin được (server không trả lời / chờ quá lâu) → chỉ chờ 1 lượt ping kể từ thao tác cuối rồi tin túi đồ
            if (var1 >= resyncOffUntil) {
               AutoBuildMod.LOGGER.info("[AutoBuild] Chưa đồng bộ được túi đồ ({}) → chờ 1 lượt ping rồi tin túi đồ hiện tại", dirtyWhy);
            }

            dirty = false;
            pourOnly = false;
            wantResync = null;
            syncRev = 1;
            syncSince = Math.max(syncSince, dirtySince);
            return false;
         } else {
            // vừa đổ xô: chờ server xác nhận nước vào (pourConfirmed) thêm 1 lượt ping trước khi phải xin cả túi đồ
            if (wantResync == null && (!pourOnly || var1 - dirtySince >= rttMs + 400L)) {
               wantResync = "kiểm tra lại túi đồ trước khi đặt tiếp (" + dirtyWhy + ")";
            }

            return false;
         }
      } else if (syncRev < 0) {
         return true;
      } else if (var1 - syncSince >= settleMs) {
         syncRev = -1;
         return true;
      } else {
         return false;
      }
   }

   public static void resync(MinecraftClient var0) {
      requestResync("đồ trên tay lệch với server");
   }

   private static long forcedAt = -100000L;
   private static int forcedCount;
   private static boolean menuSeen;
   private static long resyncSentAt;
   private static long resyncSentAtLog;
   private static int resyncRev;
   private static int resyncMode;
   private static int resyncMisses;
   private static String wantResync;

   /** xin đồng bộ lại túi đồ; gửi khi an toàn (không có lần đổi đồ nào đang chờ server trả lời) — xem tick() */
   public static void requestResync(String var0) {
      // 3.0.121: có bằng chứng lệch → khoá đổi đồ/đặt khối NGAY (trước đây vẫn đặt tiếp cho tới lúc gửi được yêu cầu)
      if (!dirty) {
         dirtyFirst = System.currentTimeMillis();
      }

      dirtySince = System.currentTimeMillis();
      dirty = true;
      pourOnly = false;
      dirtyWhy = var0;
      dirtySeq++;
      if (wantResync == null && resyncSentAt == 0L) {
         wantResync = var0;
      }
   }

   /** @deprecated giữ tên cũ: giờ chỉ xin đồng bộ, gửi ở tick() */
   public static boolean forceResync(MinecraftClient var0, String var1) {
      requestResync(var1);
      return true;
   }

   /**
    * 3.0.105/3.0.106: bắt server gửi lại TOÀN BỘ túi đồ: 1 cú bấm vô hại (bấm giữa, ngoài túi) với số phiên bản cố ý sai → server
    * thấy lệch phiên bản → gửi lại cả túi đồ + con trỏ (vanilla ScreenHandler.updateToClient).
    *
    * <p>3.0.106 — lỗi của 3.0.105 (người dùng: "bản này đặt nhầm nhiều thế"): 3.0.105 gửi yêu cầu này sau MỖI lần đóng /shop và chỉ
    * chờ ~0,45 giây, trong khi ping 300–500 ms. Gói "cả túi đồ" về MUỘN, sau khi mod đã đổi đồ (SWAP) theo dự đoán → ghi đè bằng
    * trạng thái cũ (client lại thấy tay cầm Oak Leaves) → mod SWAP lần nữa, server đổi ngược lại → client tưởng cầm Cobblestone,
    * server cầm Oak Leaves → đặt nhầm → lại xin đồng bộ → vòng lặp càng lúc càng nhiều khối nhầm.
    * Giờ: chỉ gửi khi không có lần đổi đồ nào đang chờ server (≥ 1 lượt ping + 0,25 giây sau lần bấm cuối), và khoá đổi đồ / đặt khối
    * tới khi gói trả lời về thật.
    *
    * <p>3.0.121: var1 = được phép gửi (đang xây hoặc chỉ tạm dừng). Lúc chưa gửi được (đang mở menu, con trỏ cầm đồ, mod dừng)
    * thì không tính vào thời gian chờ an toàn 8/20 giây — bản thử trước bỏ qua kiểm tra nếu đi bộ/mở menu lâu hơn 8 giây.
    */
   public static void tick(MinecraftClient var0, boolean var1) {
      if (var0.player == null || var0.getNetworkHandler() == null) {
         menuSeen = false;
         wantResync = null;
         resyncSentAt = 0L;
         // vào lại server → server gửi lại toàn bộ túi đồ khi vào → không còn gì "chưa kiểm chứng"
         dirty = false;
         pourOnly = false;
      } else {
         long var2 = System.currentTimeMillis();
         pollResync(var0);
         if (var0.player.currentScreenHandler != var0.player.playerScreenHandler) {
            menuSeen = true;
            if (resyncSentAt > 0L) {
               menuSinceSend = true;
            }
         } else if (menuSeen) {
            // vừa đóng menu server (shop, sell, order, ender chest…): chờ 1 lượt ping cho các gói ô đồ còn trên đường (vd. món vừa mua)
            // về hết rồi mới đổi đồ — nếu không, gói về muộn ghi đè lần đổi đồ vừa dự đoán → lệch đồ trên tay
            menuSeen = false;
            syncRev = 1;
            syncSince = Math.max(syncSince, var2 + rttMs);
            // 3.0.121: gói con trỏ/ô đồ của menu vừa đóng có thể về SAU khi đóng và bị client áp vào túi đồ chính (con trỏ "ma",
            // log 22:17:58 "Cất món đồ trên con trỏ vào ô 9" ×3 rồi đặt nhầm loại) → kiểm tra lại cả túi trước khi đặt tiếp
            markDirty(var0, "vừa đóng menu");
         }

         boolean var3 = !var1
            || menuBusy()
            || var0.currentScreen != null
            || var0.player.currentScreenHandler != var0.player.playerScreenHandler
            || !var0.player.playerScreenHandler.getCursorStack().isEmpty();
         if (dirty && resyncSentAt == 0L) {
            if (var3) {
               // chưa gửi được → không tính vào thời gian chờ an toàn
               dirtySince = var2;
               dirtyFirst = var2;
            } else if (wantResync == null && var2 >= resyncOffUntil && (!pourOnly || var2 - dirtySince >= rttMs + 400L)) {
               wantResync = "kiểm tra lại túi đồ trước khi đặt tiếp (" + dirtyWhy + ")";
            }
         }

         if (var2 < resyncOffUntil) {
            // đang tạm không xin đồng bộ (server không trả lời)
            wantResync = null;
         }

         long var7 = dirty ? Math.max(600L, rttMs + 200L) : 4000L;
         if (wantResync != null && !var3 && var2 - forcedAt >= var7 && var2 - lastClickAt >= rttMs + 250L && resyncSentAt == 0L && invReady(var0)) {
            forcedAt = var2;
            forcedCount++;

            sendResync(var0, wantResync);
            wantResync = null;
         } else if (!var1) {
            wantResync = null;
         }
      }
   }

   /** 3.0.122: đánh dấu túi đồ chưa kiểm chứng và gửi yêu cầu đồng bộ ngay (không chờ 1 lượt ping sau cú bấm như tick()) */
   public static void confirmNow(MinecraftClient var0, String var1) {
      markDirty(var0, var1);
      if (resyncSentAt == 0L
         && !menuBusy()
         && invReady(var0)
         && var0.getNetworkHandler() != null
         && var0.player.currentScreenHandler == var0.player.playerScreenHandler
         && var0.player.playerScreenHandler.getCursorStack().isEmpty()) {
         forcedAt = System.currentTimeMillis();
         forcedCount++;
         sendResync(var0, var1);
         wantResync = null;
      } else if (wantResync == null) {
         wantResync = var1;
      }
   }

   private static void sendResync(MinecraftClient var0, String var1) {
      long var2 = System.currentTimeMillis();
            try {
               int var4 = var0.player.playerScreenHandler.getRevision();
               resyncRev = var4;
               int var9 = -999;
               int var10 = 2;
               SlotActionType var11 = SlotActionType.CLONE;
               if (resyncMode == 1) {
                  // cách 2: shift-click 1 ô trống trong túi (thao tác bình thường, server không đổi gì) với số phiên bản sai
                  for (int var12 = 9; var12 < 36; var12++) {
                     if (var0.player.getInventory().getStack(var12).isEmpty()) {
                        var9 = var12;
                        var10 = 0;
                        var11 = SlotActionType.QUICK_MOVE;
                        break;
                     }
                  }
               }

               var0.getNetworkHandler()
                  .sendPacket(
                     new ClickSlotC2SPacket(
                        var0.player.playerScreenHandler.syncId,
                        (var4 + 16384) & 32767,
                        var9,
                        var10,
                        var11,
                        var0.player.playerScreenHandler.getCursorStack(),
                        new Int2ObjectOpenHashMap<>()
                     )
                  );
               invClicked();
               resyncSentAt = var2;
               resyncSentAtLog = var2;
               menuSinceSend = false;
               dirtySeqAtSend = dirtySeq;
               trace(var0, "xin server gửi lại túi đồ (" + var1 + ", " + resyncHow() + ")");
               if (forcedCount <= 5 || forcedCount % 20 == 0) {
                  AutoBuildMod.LOGGER.info("[AutoBuild] {} → xin server gửi lại toàn bộ túi đồ (lần {}), khoá đổi đồ/đặt khối tới khi nhận được", var1, forcedCount);
               }
            } catch (Throwable var6) {
               AutoBuildMod.LOGGER.warn("[AutoBuild] Không gửi được yêu cầu đồng bộ túi đồ: {}", var6.toString());
            }
   }

   /** giữ tên cũ cho BuildController */
   public static void menuTick(MinecraftClient var0, boolean var1) {
      tick(var0, var1);
   }

    private static boolean equipMatches(ItemStack stack, Item item) {
        return stack.isOf(item) && (!Registries.ITEM.getId(item).getPath().endsWith("_pickaxe")
            || SurvivalSupplies.usablePickaxe(stack));
    }

}
