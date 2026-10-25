package com.donutautomine.mine;

import com.donutautomine.AutoMineMod;
import java.lang.reflect.Field;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * 1.14.5: "giữ chuột thật". Bot không gọi thẳng updateBlockBreakingProgress ở cuối tick nữa mà đi đúng đường đập khi người giữ chuột trái
 * (MinecraftClient.handleBlockBreaking, chạy lúc game đọc phím, trước khi gửi gói di chuyển của tick đó). Chỉ giữ khi hướng nhìn mà server
 * đã nhận (gói xoay gửi ở tick trước) nhắm trúng đúng khối cần đập và (nhát 3x3) cùng trục mặt — giống người: nhìn trúng rồi mới giữ chuột,
 * giữ liền tới khi vỡ. Trước đây nhát đập gửi đi lúc server còn giữ hướng nhìn cũ → server bỏ qua / trả lại khối (người dừng bot rồi tự
 * đập thì lại vỡ). Không cần cửa sổ game đang được chọn, chạy cả khi bật Freecam.
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
   private static Field yawField;
   private static Field pitchField;
   private static boolean broken;
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

   private static float[] sentRotation(ClientPlayerEntity var0) {
      if (!broken) {
         try {
            if (yawField == null) {
               yawField = field("field_3941", "lastYaw");
               pitchField = field("field_3925", "lastPitch");
            }

            return new float[]{yawField.getFloat(var0), pitchField.getFloat(var0)};
         } catch (Throwable var2) {
            broken = true;
            AutoMineMod.LOGGER.warn("[AutoMine] Không đọc được hướng nhìn đã gửi server ({}) → dùng hướng nhìn hiện tại", var2.toString());
         }
      }

      return new float[]{var0.getYaw(), var0.getPitch()};
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

   private static Field field(String var0, String var1) throws NoSuchFieldException {
      String var2;
      try {
         var2 = FabricLoader.getInstance().getMappingResolver().mapFieldName("intermediary", "net.minecraft.class_746", var0, "F");
      } catch (Throwable var5) {
         var2 = var1;
      }

      Field var3;
      try {
         var3 = ClientPlayerEntity.class.getDeclaredField(var2);
      } catch (NoSuchFieldException var4) {
         var3 = ClientPlayerEntity.class.getDeclaredField(var1);
      }

      var3.setAccessible(true);
      return var3;
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
         float[] var1 = sentRotation(var0.player);
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
