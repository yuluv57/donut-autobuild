package com.donutautobuild.build;

import com.donutautobuild.AutoBuildMod;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.StickyKeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class Input {
   private static Field breakingField;
   private static boolean broken;
   private static boolean hidden;
   private static int sneakFlip = -100;
   private static int sprintFlip = -100;
   private static int toggleWarned;
   private static int sneakTick = -1;
   private static int sprintTick = -1;
   private static boolean sneakWant;
   private static boolean sprintWant;

   private Input() {
   }

   public static void clickUse(MinecraftClient var0) {
      KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(var0.options.useKey.getBoundKeyTranslationKey()));
   }

   // ===== 3.0.122: phím/chuột "thật" =====
   // Người dùng: "phải dùng di chuyển thật từ bàn phím", "phải dùng phím thật với chuột trái chuột phải".
   // Mọi phím mod bấm (W/A/S/D, nhảy, ngồi, chạy, chuột trái) đi qua đúng đường của bàn phím/chuột vật lý:
   // KeyBinding.setKeyPressed(phím, true/false) + KeyBinding.onKeyPressed(phím) lúc vừa nhấn xuống (Keyboard.onKey / Mouse.onMouseButton).
   // key() vẫn đặt trạng thái ngay (các đoạn mã đọc isPressed() trong cùng tick thấy đúng), flush() cuối tick phát sự kiện nhấn/nhả
   // theo trạng thái CUỐI tick, nên nhả-rồi-nhấn lại trong cùng 1 tick không thành 1 cú bấm mới (chuột trái không bị "nhả" giữa chừng).
   private static final Map<KeyBinding, Boolean> SENT = new LinkedHashMap<>();

   public static void key(KeyBinding var0, boolean var1) {
      if (var0 == null) {
         return;
      }

      var0.setPressed(var1);
      if (var1 || SENT.containsKey(var0)) {
         SENT.putIfAbsent(var0, false);
      }
   }

   private static InputUtil.Key bound(KeyBinding var0) {
      try {
         return InputUtil.fromTranslationKey(var0.getBoundKeyTranslationKey());
      } catch (Throwable var2) {
         return null;
      }
   }

   /** cuối mỗi tick (AutoBuildMod, sau controller.tick): phát sự kiện phím thật cho các phím mod đang điều khiển */
   public static void flush(MinecraftClient var0) {
      if (SENT.isEmpty()) {
         return;
      }

      var it = SENT.entrySet().iterator();

      while (it.hasNext()) {
         Map.Entry<KeyBinding, Boolean> var2 = it.next();
         KeyBinding var3 = var2.getKey();
         boolean var4 = var3.isPressed();
         boolean var5 = var2.getValue();
         InputUtil.Key var6 = bound(var3);
         // phím ngồi/chạy (StickyKeyBinding): ở chế độ "bật/tắt" mỗi lần setPressed(true) là đổi trạng thái → không phát thêm sự kiện,
         // Input.sneak/sprint tự lo nhịp bấm
         boolean var7 = !(var3 instanceof StickyKeyBinding) && var6 != null && !var3.isUnbound();
         if (var4 && !var5) {
            if (var7) {
               KeyBinding.setKeyPressed(var6, true);
               KeyBinding.onKeyPressed(var6);
               if (!var3.isPressed()) {
                  var3.setPressed(true);
               }
            }

            var2.setValue(true);
         } else if (!var4 && var5) {
            if (var7) {
               KeyBinding.setKeyPressed(var6, false);
               var3.setPressed(false);
            }

            // nhả xong → trả phím lại cho người chơi
            it.remove();
         } else if (!var4) {
            it.remove();
         }
      }
   }

   // ===== 3.0.122: chuột phải thật =====
   // Bản cũ gọi interactBlock ngay cuối tick, SAU gói góc nhìn của tick đó; người thật bấm chuột thì cú bấm được xử lý ở đầu tick sau
   // (handleInputEvents → doItemUse), TRƯỚC gói góc nhìn. Anti-cheat (Grim) kiểm tra cú đặt theo đúng thứ tự vanilla nên cú đặt
   // "lệch thứ tự" hay bị trả về (log: "đặt rồi nhưng không hiện", ghost block → bị server giật lại).
   // Giờ: mod nhấn chuột phải thật (onKeyPressed); khi game xử lý cú bấm (doItemUse, mixin) mới thực hiện thao tác mod đã chuẩn bị.
   public record UseClick(BlockPos pos, Direction side, boolean checkCrosshair, Runnable action, String what, long queuedTick) {
   }

   private static UseClick pendingUse;
   private static long tickNo;

   public static long tickNo() {
      return tickNo;
   }

   /** hit = ô/mặt dự kiến (null = dùng đồ không nhắm khối, vd. xô); checkCrosshair = tâm ngắm game phải đúng ô/mặt đó lúc bấm */
   public static void queueUse(MinecraftClient var0, BlockHitResult var1, boolean var2, String var3, Runnable var4) {
      if (pendingUse != null) {
         AutoBuildMod.LOGGER.info("[AutoBuild] Chuột phải: cú bấm trước ({}) chưa được game xử lý → thay bằng cú mới ({})", pendingUse.what(), var3);
      }

      pendingUse = new UseClick(var1 == null ? null : var1.getBlockPos().toImmutable(), var1 == null ? null : var1.getSide(), var2, var4, var3, tickNo);
      KeyBinding var5 = var0.options.useKey;
      InputUtil.Key var6 = bound(var5);
      if (var6 != null && !var5.isUnbound()) {
         KeyBinding.onKeyPressed(var6);
      }
   }

   public static boolean usePending() {
      return pendingUse != null;
   }

   /**
    * mixin MinecraftClient.doItemUse (HEAD): true = mod đã xử lý cú bấm này (huỷ doItemUse vanilla).
    * Không có cú bấm của mod → để người chơi dùng chuột phải bình thường.
    */
   public static boolean onDoItemUse(MinecraftClient var0) {
      UseClick var1 = pendingUse;
      if (var1 == null) {
         return false;
      }

      pendingUse = null;
      if (var0.player == null || var0.interactionManager == null) {
         return true;
      }

      if (var1.pos() != null && var1.checkCrosshair()) {
         HitResult var2 = var0.crosshairTarget;
         if (!(var2 instanceof BlockHitResult var3) || var2.getType() != Type.BLOCK || !var3.getBlockPos().equals(var1.pos()) || var3.getSide() != var1.side()) {
            AutoBuildMod.LOGGER.info(
               "[AutoBuild] Chuột phải ({}): lúc game xử lý cú bấm tâm ngắm không còn ở {} mặt {} → không bấm, ngắm lại",
               var1.what(),
               var1.pos().toShortString(),
               var1.side()
            );
            return true;
         }
      }

      if (var0.interactionManager.isBreakingBlock()) {
         // người thật không vừa giữ chuột trái đập vừa đặt được: thả đập trước
         var0.interactionManager.cancelBlockBreaking();
      }

      try {
         var1.action().run();
      } catch (Throwable var4) {
         AutoBuildMod.LOGGER.warn("[AutoBuild] Chuột phải ({}) lỗi: {}", var1.what(), var4.toString());
      }

      return true;
   }

   /** gọi mỗi tick: cú bấm không được game xử lý (đang mở menu, đang ăn…) sau 3 tick thì bỏ, không để dồn lại thành cú bấm "ma" */
   public static void useTick(MinecraftClient var0) {
      tickNo++;
      if (pendingUse != null && tickNo - pendingUse.queuedTick() > 3) {
         AutoBuildMod.LOGGER.info("[AutoBuild] Chuột phải ({}): game không xử lý cú bấm (menu/đang dùng đồ) → bỏ", pendingUse.what());
         pendingUse = null;

         while (var0.options.useKey.wasPressed()) {
         }
      }
   }

   public static void cancelUse(MinecraftClient var0) {
      if (pendingUse != null) {
         pendingUse = null;

         while (var0.options.useKey.wasPressed()) {
         }
      }
   }

   public static boolean crosshairOn(MinecraftClient var0, BlockPos var1, Direction var2) {
      HitResult var3 = var0.crosshairTarget;
      if (var3 instanceof BlockHitResult var4 && var3.getType() == Type.BLOCK && var4.getBlockPos().equals(var1) && (var2 == null || var4.getSide() == var2)) {
         return true;
      }

      return false;
   }

   public static boolean canHoldAttack(MinecraftClient var0) {
      return var0.mouse.isCursorLocked() && var0.currentScreen == null;
   }

   private static Field field() throws NoSuchFieldException {
      if (breakingField != null) {
         return breakingField;
      } else {
         String var0 = "breakingBlock";

         try {
            var0 = FabricLoader.getInstance().getMappingResolver().mapFieldName("intermediary", "net.minecraft.class_636", "field_3717", "Z");
         } catch (Throwable var8) {
         }

         Class<ClientPlayerInteractionManager> var1 = ClientPlayerInteractionManager.class;
         Field var2 = null;

         for (String var6 : new String[]{var0, "field_3717", "breakingBlock"}) {
            try {
               var2 = var1.getDeclaredField(var6);
               break;
            } catch (NoSuchFieldException var9) {
            }
         }

         if (var2 == null) {
            throw new NoSuchFieldException("breakingBlock");
         } else {
            var2.setAccessible(true);
            breakingField = var2;
            return var2;
         }
      }
   }

   public static void hideBreaking(MinecraftClient var0) {
      if (!broken && var0.interactionManager != null && var0.interactionManager.isBreakingBlock()) {
         try {
            field().setBoolean(var0.interactionManager, false);
            hidden = true;
         } catch (Throwable var2) {
            broken = true;
            AutoBuildMod.LOGGER.warn("[AutoBuild] Không giữ được trạng thái đào: {}", var2.toString());
         }
      }
   }

   public static void restoreBreaking(MinecraftClient var0) {
      if (hidden && var0.interactionManager != null) {
         hidden = false;

         try {
            field().setBoolean(var0.interactionManager, true);
         } catch (Throwable var2) {
         }
      } else {
         hidden = false;
      }
   }

   private static boolean toggled(MinecraftClient var0, boolean var1) {
      try {
         return (Boolean)(var1 ? var0.options.getSprintToggled() : var0.options.getSneakToggled()).getValue();
      } catch (Throwable var3) {
         return false;
      }
   }

   public static void sneak(MinecraftClient var0, boolean var1, int var2) {
      if (var0.player != null) {
         if (var2 != sneakTick) {
            sneakTick = var2;
            sneakWant = false;
         }

         sneakWant |= var1;
         if (!toggled(var0, false)) {
            key(var0.options.sneakKey, sneakWant);
         } else {
            if (toggleWarned++ == 0) {
               AutoBuildMod.LOGGER.info("[AutoBuild] Cài đặt \"Ngồi: Bật/tắt\" đang bật → mod chỉ bấm một nhịp để đổi tư thế");
            }

            if (var0.player.isSneaking() == sneakWant) {
               key(var0.options.sneakKey, false);
            } else if (var2 - sneakFlip > 4) {
               key(var0.options.sneakKey, true);
               sneakFlip = var2;
            }
         }
      }
   }

   public static void forceSneak(MinecraftClient var0, boolean var1, int var2) {
      sneakTick = var2;
      sneakWant = var1;
      if (var0.player != null) {
         if (!toggled(var0, false)) {
            key(var0.options.sneakKey, var1);
         } else if (var0.player.isSneaking() == var1) {
            key(var0.options.sneakKey, false);
         } else if (var2 - sneakFlip > 4) {
            key(var0.options.sneakKey, true);
            sneakFlip = var2;
         }
      }
   }

   public static void forceStand(MinecraftClient var0) {
      sneakWant = false;
      sneakTick = -1;
      if (var0.player != null) {
         if (!toggled(var0, false)) {
            key(var0.options.sneakKey, false);
         } else if (var0.player.isSneaking()) {
            key(var0.options.sneakKey, true);
         } else {
            key(var0.options.sneakKey, false);
         }
      }
   }

   /** 3.0.102: đặt hẳn trạng thái phím chạy nhanh của tick này (không cộng dồn với lần gọi trước trong cùng tick) */
   public static void forceSprint(MinecraftClient var0, boolean var1, int var2) {
      sprintTick = var2;
      sprintWant = var1;
      if (var0.player != null) {
         if (!toggled(var0, true)) {
            key(var0.options.sprintKey, var1);
         } else if (var0.player.isSprinting() == var1) {
            key(var0.options.sprintKey, false);
         } else if (var2 - sprintFlip > 4) {
            key(var0.options.sprintKey, true);
            sprintFlip = var2;
         }
      }
   }

   public static void sprint(MinecraftClient var0, boolean var1, int var2) {
      if (var0.player != null) {
         if (var2 != sprintTick) {
            sprintTick = var2;
            sprintWant = false;
         }

         sprintWant |= var1;
         if (!toggled(var0, true)) {
            key(var0.options.sprintKey, sprintWant);
         } else if (var0.player.isSprinting() == sprintWant) {
            key(var0.options.sprintKey, false);
         } else if (var2 - sprintFlip > 4) {
            key(var0.options.sprintKey, true);
            sprintFlip = var2;
         }
      }
   }
}
