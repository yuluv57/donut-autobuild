# tools — build và kiểm tra jar không cần Gradle

Môi trường đã build bản 1.3.1 không ra được `maven.fabricmc.net`, nên không dùng được Fabric Loom. Thay vào đó:

| File | Việc |
|---|---|
| `build-rl.sh` | Biên dịch → đổi tên yarn sang intermediary → kiểm tra liên kết → đóng jar (đọc chú thích đầu file để biết cần chuẩn bị gì) |
| `Remap.java` | Đổi tên trong jar theo bảng `C`/`M`/`F` (theo tên) và `MO`/`FO` (theo lớp + tên + kiểu) |
| `Members.java` | `link JAR net/minecraft/` — mọi field/method mà mod gọi tới phải có thật trong `client-intermediary.jar` (tra cả lớp cha). Kết quả phải là `LINK PROBLEMS: 0` |
| `Extract.java` | Liệt kê mọi tham chiếu ra ngoài của jar mod (để so giữa 2 bản) |
| `CallDiff.java` | So các lời gọi bên trong từng method giữa 2 jar |
| `map_extra_rl.tsv` | 5 tên yarn mod này dùng mà bảng chung chưa có (đã đối chiếu với yarn 1.21.4): `getSession`, `scheduleStop`, `Session.getUsername`, `skipGameRender` (field_1743), `getOverlay` |

## Kiểm chứng đã làm cho 1.3.1

- Chạy `build-rl.sh` từ chính mã nguồn trong repo: jar ra **giống hệt nội dung** jar trong `../release/` (so `diff -r` sau khi giải nén).
- `Members link`: `LINK PROBLEMS: 0`.
- `gcRun` (DiagnosticCommand) thử trên JDK 21 với `-XX:+DisableExplicitGC`: `System.gc()` không chạy, `gcRun` chạy và heap co 804 → 64 MB.

## Muốn build bằng Gradle

Dùng `../mod/` (Fabric Loom 1.9, yarn `1.21.4+build.8`, fabric-api `0.119.4+1.21.4`): `gradle build` → `mod/build/libs/`.
Bộ gradle này **chưa được chạy thử** vì môi trường build không tải được Loom; tên trong mã nguồn đã đối chiếu với nhánh yarn 1.21.4.
