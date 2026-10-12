#!/usr/bin/env bash
# So một jar AutoBuild vừa build (vd. bằng Gradle) với jar phát hành trong ../release/:
#   - cùng tập lớp (bỏ qua 2 lớp thừa SchematicWorkspace$3 / $Bounds còn sót trong jar phát hành),
#   - cùng chữ ký mọi field/method (Members dump, bỏ lambda),
#   - cùng danh sách lời gọi + chuỗi trong từng method (CallDiff; lambda đánh số khác nhau nên bỏ qua).
# Dùng: ASM=<asm.jar:asm-tree.jar:asm-commons.jar> tools/compare-jar.sh <jar mới> [jar phát hành]
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
NEW="${1:?đường dẫn jar mới}"
REF="${2:-$HERE/../release/donut-autobuild-fabric-1.21.4-3.0.121.jar}"
ASM="${ASM:?đặt ASM=<asm.jar:asm-tree.jar:asm-commons.jar>}"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
javac -d "$T/tools" -cp "$ASM" "$HERE/../../donut-relaunch/tools/Members.java" "$HERE/../../donut-relaunch/tools/CallDiff.java"
run() { java -cp "$ASM:$T/tools" "$@" 2>/dev/null; }
classes() { unzip -l "$1" | awk '{print $4}' | grep '^com/donutautobuild/.*\.class$' | sed 's/\.class$//' | grep -v 'SchematicWorkspace\$3$\|SchematicWorkspace\$Bounds$' | sort; }
classes "$REF" > "$T/a"; classes "$NEW" > "$T/b"
echo "== lớp chỉ có ở jar phát hành:"; comm -23 "$T/a" "$T/b" | grep -v '\$' || true
echo "== lớp chỉ có ở jar mới:";       comm -13 "$T/a" "$T/b" | grep -v '\$' || true
run Members dump "$REF" com/donutautobuild | grep -v 'lambda\$' | grep -v 'SchematicWorkspace\$3\|SchematicWorkspace\$Bounds' | sort > "$T/ma"
run Members dump "$NEW" com/donutautobuild | grep -v 'lambda\$' | sort > "$T/mb"
echo "== chữ ký field/method: $(wc -l < "$T/ma") vs $(wc -l < "$T/mb") dòng, khác nhau: $(diff "$T/ma" "$T/mb" | grep -c '^[<>]' || true)"
bad=0
for c in $(comm -12 "$T/a" "$T/b" | grep -v '\$'); do
  out="$(run CallDiff "$REF" "$NEW" "$c" | grep -v 'lambda\$' | grep '^DIFF\|^MISSING' || true)"
  if [ -n "$out" ]; then bad=$((bad+1)); echo "-- $c"; echo "$out" | head -5; fi
done
echo "== lớp có method khác lời gọi/chuỗi: $bad"
