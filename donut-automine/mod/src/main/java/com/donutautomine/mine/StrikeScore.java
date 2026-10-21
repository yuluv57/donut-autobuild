package com.donutautomine.mine;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * 1.14.0: điểm của một nhát đập vét sót (chưa trừ quãng đi). Mỗi khối cần đào trong mặt 3x3: +10; khối khác của tầng: +4;
 * khối thuộc tầng khác (trên/dưới tầng đang đào): -12; đập lẻ (ngồi): -2; khối giữa tầng theo mặt ngang (mặt 3x3 phủ đúng
 * 3 lớp của tầng): +1. Trả NaN nếu mặt không chứa khối cần đào nào.
 */
public final class StrikeScore {
   private StrikeScore() {
   }

   public static double score(
      BlockPos target, Direction face, boolean single, List<BlockPos> plane, Set<BlockPos> wanted, Predicate<BlockPos> mineable, Predicate<BlockPos> work, int floor, int top
   ) {
      int want = 0;
      int extra = 0;
      int other = 0;

      for (BlockPos p : plane) {
         if (mineable.test(p)) {
            if (p.getY() < floor || p.getY() > top) {
               other++;
            } else if (wanted.contains(p)) {
               want++;
            } else if (work.test(p)) {
               extra++;
            }
         }
      }

      if (want == 0) {
         return Double.NaN;
      } else {
         return want * 10.0
            + extra * 4.0
            - other * 12.0
            - (single ? 2.0 : 0.0)
            + (!single && face.getAxis() != Direction.Axis.Y && target.getY() == floor + 1 ? 1.0 : 0.0);
      }
   }
}
