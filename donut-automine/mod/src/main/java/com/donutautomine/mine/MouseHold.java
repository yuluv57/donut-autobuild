package com.donutautomine.mine;

import java.lang.reflect.Field;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * 1.14.5: "giữ chuột thật". Bot không gọi thẳng updateBlockBreakingProgress ở cuối tick nữa mà đi đúng đường đập khi người giữ chuột trái
 * (MinecraftClient.handleBlockBreaking, chạy lúc game đọc phím, trước khi gửi gói di chuyển của tick đó). Chỉ giữ khi tâm ngắm đang nằm
 * đúng trên khối cần đập và (nhát 3x3) cùng trục mặt — giống người: đưa chuột tới rồi giữ, giữ liền tới khi vỡ. Trước đây nhát đập gọi ở
 * cuối tick, gửi sau gói di chuyển → thứ tự gói khác người chơi thật (người dừng bot rồi tự đập thì lại vỡ). Không cần cửa sổ game đang được
 * chọn, chạy cả khi bật Freecam.
 */
public final class MouseHold {
   private static BlockPos pos;
   private static Direction axisFace;
   private static int stamp;
   private static int used = -1;
   private static int heldStamp = -1;
   private static BlockPos heldPos;
   private static Field cooldownField;
   private static boolean cooldownBroken;
   /** chỉ để kiểm thử / chẩn đoán: số tick đã giữ chuột, số tick muốn giữ mà hướng server chưa trúng */
   public static int held;
   public static int waited;

   private MouseHold() {
   }

   /** bot muốn giữ chuột vào khối var0 ở tick đọc phím kế tiếp; var1 = mặt 3x3 cần cùng trục (null = nhát lẻ, mặt nào cũng được) */
   public static void want(BlockPos var0, Direction var1) {
      pos = var0.toImmutable();
      axisFace = var1;
      stamp++;
   }

   public static void release() {
      pos = null;
   }

   /** khối gần nhất game đã thật sự giữ chuột đập */
   public static BlockPos heldPos() {
      return heldPos;
   }

   public static boolean wanted() {
      return pos != null && used != stamp;
   }

   /** đang muốn giữ hoặc vừa giữ ở tick này (cho mixin Freecam không chặn, bất kể thứ tự chạy của 2 mixin) */
   public static boolean active() {
      return pos != null && (used != stamp || heldStamp == stamp);
   }

   /** còn số chờ 10000 của màn hình vừa đóng (attackCooldown) thì xoá, như lúc người nhả rồi bấm lại chuột */
   public static void clearScreenCooldown(MinecraftClient var0) {
      if (!cooldownBroken) {
         try {
            if (cooldownField == null) {
               String var1;
               try {
                  var1 = FabricLoader.getInstance().getMappingResolver().mapFieldName("intermediary", "net.minecraft.class_310", "field_1771", "I");
               } catch (Throwable var4) {
                  var1 = "attackCooldown";
               }

               try {
                  cooldownField = MinecraftClient.class.getDeclaredField(var1);
               } catch (NoSuchFieldException var3) {
                  cooldownField = MinecraftClient.class.getDeclaredField("attackCooldown");
               }

               cooldownField.setAccessible(true);
            }

            if (cooldownField.getInt(var0) > 20) {
               cooldownField.setInt(var0, 0);
            }
         } catch (Throwable var5) {
            cooldownBroken = true;
         }
      }
   }

   /**
    * Gọi từ mixin ở đầu handleBlockBreaking: trả điểm trúng (theo hướng nhìn server đã biết) nếu được giữ chuột tick này, null nếu không.
    * Mỗi lần want() chỉ dùng được một tick: bot phải muốn giữ tiếp mỗi tick thì mới giữ liền.
    */
   public static BlockHitResult target(MinecraftClient var0) {
      if (pos == null || used == stamp || var0.player == null || var0.world == null) {
         pos = null;
         return null;
      } else {
         used = stamp;
         // hướng nhìn hiện tại như người thật: chuột đã đưa tới khối rồi mới bấm; nhát đập gửi lúc game đọc phím (trước gói di chuyển của
         // tick đó) đúng thứ tự gói như người chơi — trước đây gửi sau gói di chuyển, sai thứ tự (anti-cheat bắt / server trả lại khối)
         float[] var1 = new float[]{var0.player.getYaw(), var0.player.getPitch()};
         if (axisFace != null && BreakGeometry.normal(var1[0], var1[1]).getAxis() != axisFace.getAxis()) {
            waited++;
            return null;
         } else {
            Vec3d var2 = var0.player.getEyePos();
            Vec3d var3 = var2.add(Vec3d.fromPolar(var1[1], var1[0]).multiply(var0.player.getBlockInteractionRange()));
            BlockHitResult var4 = var0.world.raycast(new RaycastContext(var2, var3, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, var0.player));
            if (var4.getType() == Type.BLOCK && var4.getBlockPos().equals(pos)) {
               held++;
               heldStamp = stamp;
               heldPos = pos;
               return var4;
            } else {
               waited++;
               return null;
            }
         }
      }
   }
}
