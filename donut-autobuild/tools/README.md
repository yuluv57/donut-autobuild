# tools — kiểm tra jar AutoBuild

## `compare-jar.sh`

Dùng để chắc một jar vừa build từ mã nguồn (Gradle hoặc javac) có **cùng logic** với jar phát hành trong `../release/`.

Script so 3 thứ:
1. **Tập lớp.** Bỏ qua `SchematicWorkspace$3` và `SchematicWorkspace$Bounds`: 2 lớp thừa sót trong jar phát hành từ 3.0.120, mã nguồn không sinh ra chúng.
2. **Chữ ký** của mọi field/method (`Members dump`, bỏ lambda).
3. **Lời gọi và hằng chuỗi trong từng method** (`CallDiff`). Lambda bị bỏ qua vì javac đánh số khác nhau giữa các lần build.

```
ASM=asm-9.7.1.jar:asm-tree-9.7.1.jar:asm-commons-9.7.1.jar \
  tools/compare-jar.sh mod/build/libs/donut-autobuild-fabric-1.21.4-3.0.121.jar
```

Kết quả đúng:

```
== lớp chỉ có ở jar phát hành:
== lớp chỉ có ở jar mới:
== chữ ký field/method: 2932 vs 2932 dòng, khác nhau: 0
== lớp có method khác lời gọi/chuỗi: 0
```

Không so byte-for-byte vì thứ tự hằng số và tên lambda đổi theo từng lần build. Đổi tên biến cục bộ (vd. `var0` → `player`) không làm đổi kết quả, nên dùng script này để kiểm khi dọn mã.

`Members.java` và `CallDiff.java` dùng chung với `../../donut-relaunch/tools/`.

## Jar trong `release/` được build thế nào

Môi trường build không tải được Fabric Loom (`maven.fabricmc.net` bị chặn), nên jar 3.0.121 được build theo cách của `donut-relaunch/tools/build-rl.sh`:
1. biên dịch bằng `javac` với stub Minecraft (tên yarn);
2. đổi tên yarn → intermediary bằng `Remap`;
3. kiểm tra liên kết (`Members link`, 0 lỗi);
4. đóng gói trên nền jar 3.0.120 (giữ các module Fabric API trong `META-INF/jars`).

Stub và bảng tên **không có trong repo** vì sinh từ jar Minecraft.

Trên máy có Internet, dùng Gradle trong `../mod/` rồi chạy `compare-jar.sh`.
