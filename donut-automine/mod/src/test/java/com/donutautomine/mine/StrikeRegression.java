package com.donutautomine.mine;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** 1.14.0: chấm điểm nhát đập vét sót — cột 3 khối phải đập khối giữa theo mặt ngang (1 nhát, không đụng tầng khác). */
public final class StrikeRegression {
   static int passed;

   static void check(boolean ok, String what) {
      if (!ok) {
         throw new AssertionError(what);
      }

      passed++;
      System.out.println("  ok  " + what);
   }

   record Pick(BlockPos c, Direction f, double s) {
   }

   /** như chooseStrike (đứng tại chỗ, mọi mặt đều nhìn thấy): bỏ mặt 3x3 phá khối dưới sàn tầng, chọn điểm cao nhất */
   static Pick best(Set<BlockPos> world, List<BlockPos> leftovers, int floor, int top) {
      Predicate<BlockPos> mineable = world::contains;
      Pick best = null;

      for (BlockPos c : leftovers) {
         for (Direction f : Direction.values()) {
            List<BlockPos> plane = Cells.plane(c, f);
            if (plane.stream().anyMatch(p -> mineable.test(p) && p.getY() < floor)) {
               continue;
            }

            double s = StrikeScore.score(c, f, false, plane, new HashSet<>(leftovers), mineable, p -> true, floor, top);
            if (!Double.isNaN(s) && (best == null || s > best.s)) {
               best = new Pick(c, f, s);
            }
         }
      }

      return best;
   }

   public static void main(String[] a) {
      int floor = 0;
      int top = 2;
      Set<BlockPos> world = new HashSet<>();
      for (int x = -3; x <= 8; x++) {
         for (int z = -3; z <= 8; z++) {
            world.add(new BlockPos(x, -1, z));
            world.add(new BlockPos(x, -2, z));
         }
      }

      System.out.println("[Strike] cột 3 khối");
      List<BlockPos> col = List.of(new BlockPos(5, 0, 5), new BlockPos(5, 1, 5), new BlockPos(5, 2, 5));
      world.addAll(col);
      Pick p = best(world, col, floor, top);
      check(p.c.equals(new BlockPos(5, 1, 5)) && p.f.getAxis() != Direction.Axis.Y, "đập khối giữa theo mặt ngang: " + p);
      check(p.s >= 30.0, "một nhát phá cả 3 khối (điểm " + p.s + ")");
      double topH = StrikeScore.score(col.get(2), Direction.NORTH, false, Cells.plane(col.get(2), Direction.NORTH), new HashSet<>(col), world::contains, q -> true, floor, top);
      double topV = StrikeScore.score(col.get(2), Direction.UP, false, Cells.plane(col.get(2), Direction.UP), new HashSet<>(col), world::contains, q -> true, floor, top);
      check(topH < p.s && topV < p.s, "khối trên (ngang " + topH + ", nhìn xuống " + topV + ") kém khối giữa");
      world.add(new BlockPos(5, 3, 4));
      double topHigh = StrikeScore.score(col.get(2), Direction.EAST, false, Cells.plane(col.get(2), Direction.EAST), new HashSet<>(col), world::contains, q -> true, floor, top);
      check(topHigh < 20.0, "mặt 3x3 lấn lên tầng trên bị trừ: " + topHigh);
      world.remove(new BlockPos(5, 3, 4));
      world.removeAll(col);

      System.out.println("[Strike] 2 cột sát nhau");
      List<BlockPos> wall = List.of(
         new BlockPos(5, 0, 5), new BlockPos(5, 1, 5), new BlockPos(5, 2, 5), new BlockPos(5, 0, 6), new BlockPos(5, 1, 6), new BlockPos(5, 2, 6)
      );
      world.addAll(wall);
      Pick w = best(world, wall, floor, top);
      check(w.c.getY() == 1 && w.f.getAxis() == Direction.Axis.X && w.s >= 60.0, "đập giữa theo mặt vuông góc với bức tường → 6 khối 1 nhát: " + w);
      world.removeAll(wall);

      System.out.println("[Strike] hàng 3 khối dưới đáy tầng");
      List<BlockPos> row = List.of(new BlockPos(4, 0, 5), new BlockPos(5, 0, 5), new BlockPos(6, 0, 5));
      world.addAll(row);
      Pick r = best(world, row, floor, top);
      check(r.c.equals(new BlockPos(5, 0, 5)) && r.s >= 30.0, "hàng đáy: đập khối giữa, phá cả 3 mà không đụng sàn: " + r);
      check(Cells.plane(r.c, r.f).stream().noneMatch(q -> q.getY() < floor && world.contains(q)), "không phá khối sàn tầng dưới");
      System.out.println("StrikeRegression: " + passed + " phép kiểm tra đạt");
   }
}
