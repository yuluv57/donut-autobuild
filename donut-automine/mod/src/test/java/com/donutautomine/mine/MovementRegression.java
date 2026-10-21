package com.donutautomine.mine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** 1.14.0: kiểm thử đường đi trên địa hình giả (Cells.probe): khe 1 ô phải nhảy qua, không bắc cầu, kể cả khi có khối kê. */
public final class MovementRegression {
   static int passed;

   static void check(boolean ok, String what) {
      if (!ok) {
         throw new AssertionError(what);
      }

      passed++;
      System.out.println("  ok  " + what);
   }

   /** sàn đá ở y = -1, hố tại các ô liệt kê (sâu depth ô), ngoài vùng 0..12 là chưa tải */
   static void floor(Map<BlockPos, Integer> w, int x0, int x1, int z0, int z1) {
      for (int x = x0; x <= x1; x++) {
         for (int z = z0; z <= z1; z++) {
            for (int y = -4; y <= -1; y++) {
               w.put(new BlockPos(x, y, z), 1);
            }
         }
      }
   }

   static void hole(Map<BlockPos, Integer> w, int x, int z, int depth) {
      for (int d = 1; d <= depth; d++) {
         w.put(new BlockPos(x, -d, z), 0);
      }
   }

   static void use(Map<BlockPos, Integer> w) {
      Cells.probe = p -> {
         if (p.getX() < -2 || p.getX() > 14 || p.getZ() < -2 || p.getZ() > 14 || p.getY() < -6 || p.getY() > 6) {
            return -1;
         }

         return w.getOrDefault(p, p.getY() < -4 ? 2 : 0);
      };
   }

   /** như AutoBuildNavigation.Search nhưng chạy theo số nút (không theo thời gian) để kết quả không phụ thuộc máy/JIT */
   static List<PathFinder.Step> route(BlockPos from, BlockPos to, boolean filler) {
      AutoBuildNavigation nav = new AutoBuildNavigation(null, from, to, 0, 0, filler, false, new HashSet<>());
      PathSearch s = new PathSearch(AutoBuildNavigation.cell(from), List.of(AutoBuildNavigation.cell(to)), nav, 6000);
      while (!s.done()) {
         s.advance(1000);
      }

      if (s.result() == null) {
         return null;
      }

      List<PathFinder.Step> r = new ArrayList<>();
      BlockPos prev = from;
      for (PathSearch.Cell c : s.result()) {
         BlockPos p = AutoBuildNavigation.pos(c);
         r.add(new PathFinder.Step(p, false, nav.liveKind(prev, p)));
         prev = p;
      }

      return r;
   }

   public static void main(String[] a) {
      System.out.println("[Movement] khe 1 ô trên đường thẳng");
      Map<BlockPos, Integer> w = new HashMap<>();
      floor(w, 0, 12, 0, 12);
      hole(w, 3, 0, 1);
      use(w);
      for (boolean filler : new boolean[]{false, true}) {
         List<PathFinder.Step> r = route(new BlockPos(0, 0, 0), new BlockPos(6, 0, 0), filler);
         check(r != null, "tìm được đường (có khối kê = " + filler + ")");
         boolean jump = r.stream().anyMatch(st -> st.kind() == PathFinder.Kind.JUMP && st.feet().equals(new BlockPos(4, 0, 0)));
         boolean bridge = r.stream().anyMatch(st -> st.kind() == PathFinder.Kind.BRIDGE);
         check(jump, "nhảy từ (2,0,0) qua hố tới (4,0,0) (có khối kê = " + filler + "): " + r);
         check(!bridge, "không bắc cầu (có khối kê = " + filler + ")");
      }

      System.out.println("[Movement] hành lang hẹp 1 ô (như đường hầm trong mỏ), có khối kê");
      for (int x = 0; x <= 12; x++) {
         for (int y = 0; y <= 1; y++) {
            w.put(new BlockPos(x, y, -1), 1);
            w.put(new BlockPos(x, y, 1), 1);
         }
      }

      List<PathFinder.Step> rc = route(new BlockPos(0, 0, 0), new BlockPos(6, 0, 0), true);
      check(rc != null && rc.stream().anyMatch(st -> st.kind() == PathFinder.Kind.JUMP), "hành lang: nhảy qua hố: " + rc);
      check(rc.stream().noneMatch(st -> st.kind() == PathFinder.Kind.BRIDGE), "hành lang: không bắc cầu");

      PathFinder.Step d = DirectRoute.next(null, new BlockPos(2, 0, 0), new BlockPos(6, 0, 0), null, true, false, new HashSet<>());
      check(d != null && d.kind() == PathFinder.Kind.JUMP && d.feet().equals(new BlockPos(4, 0, 0)), "đường thẳng gặp hố → JUMP tới (4,0,0), không BRIDGE: " + d);
      PathFinder.Step d2 = DirectRoute.next(null, new BlockPos(4, 0, 0), new BlockPos(10, 0, 0), null, true, false, new HashSet<>());
      check(d2 != null && d2.kind() == PathFinder.Kind.WALK && d2.feet().equals(new BlockPos(10, 0, 0)), "đường thẳng thông → đi một mạch tới (10,0,0): " + d2);
      // 1.14.1: vừa nhảy qua hố, đáp ở x = 4,28 (góc thân chìa ra trên hố ô 3) → đường thẳng về phía trước vẫn thông.
      // 1.14.0 coi là bị chặn, bỏ đường rồi tính lại đúng đường đó mỗi tick → đứng im không ghi log.
      check(DirectRoute.clearSegment(null, new net.minecraft.util.math.Vec3d(4.28, 0.0, 0.5), new BlockPos(10, 0, 0), false, new HashSet<>()),
         "đứng sát mép hố, đi tiếp về phía trước: đường thẳng thông");
      check(!DirectRoute.clearSegment(null, new net.minecraft.util.math.Vec3d(4.28, 0.0, 0.5), new BlockPos(0, 0, 0), false, new HashSet<>()),
         "đi ngược qua hố: đường thẳng bị chặn");
      PathFinder.Step d3 = DirectRoute.next(null, new BlockPos(0, 0, 0), new BlockPos(6, 0, 0), null, true, false, new HashSet<>());
      check(d3 != null && d3.kind() == PathFinder.Kind.WALK && d3.feet().equals(new BlockPos(2, 0, 0)), "đi thẳng tới mép hố (2,0,0) rồi mới nhảy: " + d3);

      System.out.println("[Movement] khe 2 ô cần ô lấy đà, khe sâu/không đáy không nhảy");
      w.clear();
      floor(w, 0, 12, 0, 12);
      hole(w, 3, 0, 1);
      hole(w, 4, 0, 1);
      use(w);
      BlockPos j2 = new AutoBuildNavigation(null, new BlockPos(2, 0, 0), null, 0, 0, true, false, new HashSet<>()).jumpTarget(new BlockPos(2, 0, 0), Direction.EAST);
      check(new BlockPos(5, 0, 0).equals(j2), "khe 2 ô có ô chạy đà phía sau → nhảy tới (5,0,0): " + j2);
      BlockPos j2free = new AutoBuildNavigation(null, new BlockPos(2, 0, 0), null, -1, 1, true, false, new HashSet<>()).jumpTarget(new BlockPos(2, 0, 0), Direction.EAST);
      check(new BlockPos(5, 0, 0).equals(j2free), "khe 2 ô nông, cho phép xuống 1 ô → vẫn nhảy thẳng qua tới (5,0,0), không nhảy xuống đáy khe: " + j2free);
      BlockPos j1 = new AutoBuildNavigation(null, new BlockPos(5, 0, 0), null, -1, 1, true, false, new HashSet<>()).jumpTarget(new BlockPos(5, 0, 0), Direction.WEST);
      check(new BlockPos(2, 0, 0).equals(j1), "chiều ngược lại cũng nhảy qua cả khe: " + j1);
      w.clear();
      floor(w, 0, 12, 0, 12);
      for (int z = 0; z <= 12; z++) {
         hole(w, 3, z, 9);
      }

      use(w);
      BlockPos jv = new AutoBuildNavigation(null, new BlockPos(2, 0, 0), null, 0, 0, true, false, new HashSet<>()).jumpTarget(new BlockPos(2, 0, 0), Direction.EAST);
      check(jv == null, "hố sâu quá 5 ô → không nhảy: " + jv);
      List<PathFinder.Step> rb = route(new BlockPos(0, 0, 0), new BlockPos(6, 0, 0), true);
      check(rb != null && rb.stream().anyMatch(st -> st.kind() == PathFinder.Kind.BRIDGE), "hố sâu, có khối kê → vẫn bắc cầu như cũ");

      System.out.println("[Movement] vật lý nhảy (AutoBuild)");
      double walk = Parkour.simulateCross(0.2, 0.1, false, 0.6F, 1, 1, 0);
      double walkNear = Parkour.simulateCross(0.2, 0.1, false, 0.6F, -1, 1, 0);
      check(Parkour.window(walk, walkNear, 1.55, 2.3), "đi bộ nhảy khe 1 ô từ 0,2 sau tâm ô chạm được ô đích: " + walk + " / " + walkNear);
      double still = Parkour.simulateCross(-0.3, 0.0, false, 0.6F, 1, 2, 0);
      check(!Parkour.window(still, Parkour.simulateCross(-0.3, 0.0, false, 0.6F, -1, 2, 0), 2.55, 3.3), "đứng yên đi bộ không nhảy được khe 2 ô");
      double sprint = Parkour.simulateCross(0.3, 0.28, true, 0.6F, 1, 2, 0);
      check(!Double.isNaN(sprint) && sprint >= 2.55, "chạy nhanh nhảy khe 2 ô tới được: " + sprint);
      check(Parkour.gap(2, 0) == 1 && Parkour.gap(0, -3) == 2 && Parkour.gap(1, 1) == 0, "đếm khe");
      Cells.probe = null;
      System.out.println("MovementRegression: " + passed + " phép kiểm tra đạt");
   }
}
