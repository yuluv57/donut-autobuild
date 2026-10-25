package com.donutautomine.mine;

import java.lang.reflect.Field;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
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
   private static Field breakingPosField;
   private static Field sentYawField;
   private static Field sentPitchField;
   private static boolean rearmBroken;
   private static boolean sentBroken;
   private static int staleWaits;
   /** chỉ để kiểm thử / chẩn đoán: số lần bấm lại chuột (gửi lại "bắt đầu đập") cho khối vừa đập xong mà hiện lại */
   public static int presses;
   /** chỉ để kiểm thử / chẩn đoán: số tick đã giữ chuột, số tick muốn giữ mà hướng server chưa trúng */
   public static int held;
   public static int waited;

   private MouseHold() {
   }

   /** bot muốn giữ chuột vào khối var0 ở tick đọc phím kế tiếp; var1 = mặt 3x3 cần cùng trục (null = nhát lẻ, mặt nào cũng được) */
   public static void want(BlockPos var0, Direction var1) {
      if (!var0.equals(pos)) {
         staleWaits = 0;
      }

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

   private static Field field(Class<?> var0, String var1, String var2, String var3, String var4) throws NoSuchFieldException {
      String var5;
      try {
         var5 = FabricLoader.getInstance().getMappingResolver().mapFieldName("intermediary", var1, var2, var3);
      } catch (Throwable var8) {
         var5 = var4;
      }

      Field var6;
      try {
         var6 = var0.getDeclaredField(var5);
      } catch (NoSuchFieldException var7) {
         var6 = var0.getDeclaredField(var4);
      }

      var6.setAccessible(true);
      return var6;
   }

   /**
    * 1.14.6: "nhả chuột rồi bấm lại". Game không còn đang đập khối nào (vừa vỡ / server trả lại) mà vẫn nhớ vị trí khối cũ → giữ chuột tiếp
    * vào đúng khối đó thì game chỉ cộng tiến độ rồi gửi "đập xong" mà KHÔNG gửi "bắt đầu đập" (lỗi của game khi giữ chuột liền lên khối bị
    * trả lại) → server từ chối, khối hiện lại mãi. Người dừng bot rồi tự bấm lại thì game gửi "bắt đầu đập" nên vỡ ngay. Xoá vị trí cũ để
    * game đi đường bấm mới: chờ hết 5 tick nghỉ sau nhát trước như người giữ chuột, rồi gửi "bắt đầu đập".
    */
   public static void rearm(MinecraftClient var0) {
      if (!rearmBroken && var0.interactionManager != null && !var0.interactionManager.isBreakingBlock()) {
         try {
            if (breakingPosField == null) {
               breakingPosField = field(
                  ClientPlayerInteractionManager.class, "net.minecraft.class_636", "field_3714", "Lnet/minecraft/class_2338;", "currentBreakingPos"
               );
            }

            Object var1 = breakingPosField.get(var0.interactionManager);
            if (pos != null && pos.equals(var1)) {
               presses++;
            }

            breakingPosField.set(var0.interactionManager, new BlockPos(-1, -1, -1));
         } catch (Throwable var2) {
            rearmBroken = true;
         }
      }
   }

   /** hướng nhìn server đã nhận (gói di chuyển gần nhất); null nếu không đọc được */
   private static float[] sentRotation(MinecraftClient var0) {
      if (sentBroken) {
         return null;
      } else {
         try {
            if (sentYawField == null) {
               sentYawField = field(ClientPlayerEntity.class, "net.minecraft.class_746", "field_3941", "F", "lastYaw");
               sentPitchField = field(ClientPlayerEntity.class, "net.minecraft.class_746", "field_3925", "F", "lastPitch");
            }

            return new float[]{sentYawField.getFloat(var0.player), sentPitchField.getFloat(var0.player)};
         } catch (Throwable var2) {
            sentBroken = true;
            return null;
         }
      }
   }

   private static BlockHitResult ray(MinecraftClient var0, float var1, float var2) {
      Vec3d var3 = var0.player.getEyePos();
      Vec3d var4 = var3.add(Vec3d.fromPolar(var2, var1).multiply(var0.player.getBlockInteractionRange()));
      return var0.world.raycast(new RaycastContext(var3, var4, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, var0.player));
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
            BlockHitResult var4 = ray(var0, var1[0], var1[1]);
            if (var4.getType() == Type.BLOCK && var4.getBlockPos().equals(pos)) {
               // 1.14.6: hướng nhìn server đang biết (gói di chuyển trước) cũng phải trúng khối — gói "bắt đầu đập" tới trước gói quay mặt
               // của tick này, anti-cheat soát tia nhìn theo hướng cũ. Vừa quay sang khối mới thì chờ 1 tick như người đưa chuột tới rồi
               // mới bấm; chờ quá 3 tick liền (đang vừa đi vừa quay) thì giữ luôn để không đứng im.
               float[] var5 = sentRotation(var0);
               if (var5 != null && staleWaits < 3) {
                  BlockHitResult var6 = ray(var0, var5[0], var5[1]);
                  boolean var7 = axisFace == null || BreakGeometry.normal(var5[0], var5[1]).getAxis() == axisFace.getAxis();
                  if (!var7 || var6.getType() != Type.BLOCK || !var6.getBlockPos().equals(pos)) {
                     staleWaits++;
                     waited++;
                     return null;
                  }
               }

               staleWaits = 0;
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
