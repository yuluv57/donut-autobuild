package com.donutautomine.mine;

/**
 * 1.14.0: vật lý nhảy qua khe, chép từ AutoBuild 3.0.121 (BuildController.simulateCross / simulateFlight / jumpWindow).
 * Toạ độ tính dọc trục nhảy, gốc là tâm ô lấy đà; ô đích (mép gần) ở gap + 0,5.
 */
public final class Parkour {
   private Parkour() {
   }

   /** Số ô khe giữa 2 ô thẳng hàng (0 = bước thường). */
   public static int gap(int dx, int dz) {
      int ax = Math.abs(dx);
      int az = Math.abs(dz);
      return (ax == 0 || az == 0) && ax + az >= 2 && ax + az <= 4 ? ax + az - 1 : 0;
   }

   /**
    * Nhảy NGAY tick này (đang giữ W, chạy nhanh = sprint), trên không giữ phím key (+1 W, 0 thả, -1 S) →
    * vị trí tâm người dọc trục lúc đi xuống qua mặt phẳng ô đích (chênh cao dy). NaN nếu rơi lại ô đứng trước đó.
    * Vật lý vanilla: tick bật nhảy dùng ma sát khối đứng (+0,2 nếu chạy nhanh), trên không cản 0,91, trọng lực 0,08·0,98;
    * trục Y di chuyển trước trục ngang; nhảy lên cao mà chân còn thấp hơn mặt khối đích thì đập mặt bên → mất đà ngang.
    */
   public static double simulateCross(double along, double vel, boolean sprint, float slip, int key, int gap, int dy) {
      double accel = (sprint ? 0.13 : 0.1) * (0.21600002 / (double)(slip * slip * slip)) * 0.98;
      double edge = (double)gap + 0.5;
      double x = along;
      double y = 0.0;
      double vy = 0.42;
      double vx = vel + accel + (sprint ? 0.2 : 0.0);
      double air = (sprint && key > 0 ? 0.02548 : 0.0196) * (double)key;

      for (int t = 0; t < 40; t++) {
         if (t > 0) {
            vx += air;
         }

         double ny = y + vy;
         if (vy < 0.0) {
            if ((double)dy <= y && (double)dy > ny) {
               return x;
            }

            if (dy < 0 && 0.0 <= y && 0.0 > ny && x - 0.3 < 0.5) {
               return Double.NaN;
            }
         }

         y = ny;
         double nx = x + vx;
         if (dy > 0 && y < (double)dy && nx + 0.3 > edge && x + 0.3 <= edge + 1.0E-9) {
            nx = edge - 0.3;
            vx = 0.0;
         }

         x = nx;
         vx *= t == 0 ? (double)(slip * 0.91F) : 0.91;
         vy = (vy - 0.08) * 0.98;
      }

      return Double.NaN;
   }

   /** Đang bay (vị trí/vận tốc dọc trục, độ cao chân so với ô đứng, vận tốc Y), giữ phím key tới lúc qua mặt phẳng ô đích. */
   public static double simulateFlight(double along, double vel, double y, double vy, boolean sprint, int key, int gap, int dy) {
      double edge = (double)gap + 0.5;
      double air = (sprint && key > 0 ? 0.02548 : 0.0196) * (double)key;
      double x = along;
      double vx = vel;

      for (int t = 0; t < 40; t++) {
         vx += air;
         double ny = y + vy;
         if (vy < 0.0) {
            if ((double)dy <= y && (double)dy > ny) {
               return x;
            }

            if (dy < 0 && 0.0 <= y && 0.0 > ny && x - 0.3 < 0.5) {
               return Double.NaN;
            }
         }

         y = ny;
         double nx = x + vx;
         if (dy > 0 && y < (double)dy && nx + 0.3 > edge && x + 0.3 <= edge + 1.0E-9) {
            nx = edge - 0.3;
            vx = 0.0;
         }

         x = nx;
         vx *= 0.91;
         vy = (vy - 0.08) * 0.98;
         if (y < (double)Math.min(0, dy) - 3.0) {
            return Double.NaN;
         }
      }

      return Double.NaN;
   }

   /** Khoảng điểm chạm với được [giữ W = far, giữ S = near] có chạm vào khoảng an toàn [lo, hi] của khối đích không. */
   public static boolean window(double far, double near, double lo, double hi) {
      return !Double.isNaN(far) && far >= lo && (Double.isNaN(near) || near <= hi);
   }
}
