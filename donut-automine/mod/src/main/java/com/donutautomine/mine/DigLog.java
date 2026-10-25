package com.donutautomine.mine;

import java.util.ArrayDeque;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;

/**
 * 1.14.6: ghi lại các gói đập (bắt đầu / xong / huỷ) và gói quay mặt client đã gửi, để khi server trả lại khối thì latest.log ghi rõ
 * thứ tự gói của khối đó (thiếu "bắt đầu đập", quay mặt sau khi đập…).
 */
public final class DigLog {
   private static final ArrayDeque<Object[]> RING = new ArrayDeque<>();
   /** tick của bot (MineController tăng mỗi tick) */
   public static int tick;

   private DigLog() {
   }

   public static void sent(Packet<?> var0) {
      if (var0 instanceof PlayerActionC2SPacket var1) {
         String var2 = switch (var1.getAction()) {
            case START_DESTROY_BLOCK -> "bắt đầu";
            case STOP_DESTROY_BLOCK -> "xong";
            case ABORT_DESTROY_BLOCK -> "huỷ";
            default -> null;
         };
         if (var2 != null) {
            add(var2, var1.getPos().toImmutable());
         }
      } else if (var0 instanceof PlayerMoveC2SPacket var3 && var3.changesLook()) {
         add("quay", null);
      }
   }

   private static synchronized void add(String var0, BlockPos var1) {
      RING.addLast(new Object[]{tick, var0, var1});

      while (RING.size() > 96) {
         RING.removeFirst();
      }
   }

   /** các gói của ô var0 (và gói quay mặt xen giữa) trong var1 tick gần nhất, tính theo tick tương đối: "bắt đầu@-12 quay@-12 xong@-9" */
   public static synchronized String about(BlockPos var0, int var1) {
      StringBuilder var2 = new StringBuilder();
      boolean var3 = false;

      for (Object[] var5 : RING) {
         int var6 = (Integer)var5[0] - tick;
         if (var6 >= -var1) {
            BlockPos var7 = (BlockPos)var5[2];
            if (var0.equals(var7)) {
               var3 = true;
               var2.append(var5[1]).append('@').append(var6).append(' ');
            } else if (var7 == null && var3) {
               var2.append("quay@").append(var6).append(' ');
            } else if (var7 != null && var3 && !"huỷ".equals(var5[1])) {
               var2.append("(ô khác ").append(var5[1]).append("@").append(var6).append(") ");
            }
         }
      }

      // gộp các gói quay mặt liền nhau: "quay@-16 quay@-15 quay@-13" → "quay×3@-16..-13"
      String[] var8 = var2.toString().trim().split(" ");
      StringBuilder var9 = new StringBuilder();
      for (int var10 = 0; var10 < var8.length; var10++) {
         if (var8[var10].startsWith("quay@")) {
            int var11 = var10;
            while (var11 + 1 < var8.length && var8[var11 + 1].startsWith("quay@")) {
               var11++;
            }

            var9.append(var11 == var10 ? var8[var10] : "quay×" + (var11 - var10 + 1) + "@" + var8[var10].substring(5) + ".." + var8[var11].substring(5));
            var10 = var11;
         } else {
            var9.append(var8[var10]);
         }

         var9.append(' ');
      }

      return var9.length() == 0 || var8[0].isEmpty() ? "không có gói đập nào" : var9.toString().trim();
   }
}
