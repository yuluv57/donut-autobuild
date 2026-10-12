#!/usr/bin/env bash
# Build donut-relaunch KHÔNG cần Gradle/Loom (cách đã dùng để ra jar trong ../release/).
# Môi trường build không ra được maven.fabricmc.net nên phải tự làm 3 việc Loom vẫn làm:
#   1. biên dịch mã nguồn tên yarn với "stub" (lớp rỗng chỉ có chữ ký) của Minecraft + jar Fabric API đã đổi sang tên yarn;
#   2. đổi tên yarn → intermediary trong jar vừa biên dịch (Remap);
#   3. kiểm tra mọi tham chiếu tới Minecraft có thật trong client-intermediary.jar (Members link).
#
# Cần chuẩn bị (KHÔNG có trong repo vì là file của Minecraft / sinh từ file của Minecraft):
#   $WORK/client-intermediary.jar  lấy ở .fabric/remappedJars/minecraft-1.21.4-<loader>/ trên máy chơi
#   $WORK/stubs/                   mã nguồn stub tên yarn sinh từ client-intermediary.jar + yarn 1.21.4
#   $WORK/named/*.jar              fabric-api (các module dùng tới) đã remap sang tên yarn
#   $WORK/map_n2i.tsv              bảng yarn → intermediary (C/M/F/MO/FO), sinh từ yarn 1.21.4; nối thêm map_extra_rl.tsv
#   ASM 9.x (asm, asm-commons, asm-tree)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
MOD="$HERE/../mod"
WORK="${WORK:?đặt WORK=<thư mục chứa các file ở trên>}"
ASM="${ASM:?đặt ASM=<asm.jar:asm-commons.jar:asm-tree.jar>}"
VER="$(grep -o '"version": *"[^"]*"' "$MOD/src/main/resources/fabric.mod.json" | grep -o '[0-9][^"]*')"
OUT="$WORK/out"; rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/tools" "$OUT/pkg"

javac -d "$OUT/tools" -cp "$ASM" "$HERE"/*.java
CP=$(ls "$WORK"/named/*.jar | tr '\n' ':')
find -L "$WORK/stubs" "$MOD/src/main/java" -name '*.java' > "$OUT/srcs.txt"
javac -encoding UTF-8 -proc:none -nowarn --release 21 -cp "$CP" -d "$OUT/classes" @"$OUT/srcs.txt"

(cd "$OUT/classes" && find com/donutrelaunch -name '*.class' | zip -q ../named.jar -@)
cat "$WORK/map_n2i.tsv" "$HERE/map_extra_rl.tsv" > "$OUT/map.tsv"
java -cp "$ASM:$OUT/tools" Remap "$OUT/map.tsv" "$OUT/named.jar" "$OUT/int.jar"

# kiểm tra liên kết: gộp jar mod vào bản sao client-intermediary.jar rồi dò mọi tham chiếu net/minecraft/
cp "$WORK/client-intermediary.jar" "$OUT/linkchk.jar"
(cd "$OUT/pkg" && unzip -q -o "$OUT/int.jar" && zip -q -r "$OUT/linkchk.jar" com)
java -cp "$ASM:$OUT/tools" Members link "$OUT/linkchk.jar" net/minecraft/   # phải in "LINK PROBLEMS: 0"

cp "$MOD/src/main/resources/fabric.mod.json" "$OUT/pkg/"
(cd "$OUT/pkg" && zip -q -X -r "$OUT/donut-relaunch-fabric-1.21.4-$VER.jar" fabric.mod.json com)
echo "xong: $OUT/donut-relaunch-fabric-1.21.4-$VER.jar"
