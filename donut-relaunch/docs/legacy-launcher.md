# Legacy Launcher 169.4 — những gì đọc được từ mã

Giải mã `launcher/launcher.jar` (Vineflower) để biết launcher làm gì, thay vì đoán. Các lớp nhắc tới đều trong `net.legacylauncher`.

## Tiến trình

- `LL.exe` chạy `jre\x64\bin\javaw.exe -Xmx256m … -classpath launcher\bootstrap.jar org.springframework.boot.loader.PropertiesLauncher --packageMode windows --targetJar launcher/launcher.jar --targetLibFolder launcher/libraries -- --settings tl.properties` rồi thoát. Thư mục chạy: `%APPDATA%\.tlauncher\legacy\Minecraft`.
- Launcher chạy **trong chính tiến trình javaw đó** và là tiến trình cha của game. Mỗi cửa sổ launcher = 1 tiến trình (~250–330 MB).
- `minecraft.onlaunch=hide`: mở game xong cửa sổ launcher ẩn; game tắt thì hiện lại.

## Tham số dòng lệnh (`configuration.ArgumentParser`)

| Tham số | Tác dụng |
|---|---|
| `--settings <file>` | file cấu hình |
| `--username <tên>` | chọn tài khoản có `getDisplayName()` trùng tên (tài khoản Microsoft: tên profile Minecraft) |
| `--usertype <loại>` | khi không tìm thấy tên: có `--usertype` khác `plain` ⇒ chỉ cảnh báo; **không có** ⇒ tự tạo tài khoản offline trùng tên. Tài khoản Microsoft có loại `minecraft` |
| `--launch` | `login.auto` = true: 3 giây sau khi mở launcher tự `startLauncher()` (chỉ lúc khởi động — `LegacyLauncher.initAndRefreshUI`) |
| `--javaargs`, `--margs`, `--version`, `--directory`, `--window`, `--fullscreen` | đặt khoá cấu hình tương ứng |

Mọi khoá đặt qua dòng lệnh là **constant**: `Configuration.processSavingProperties` bỏ chúng khi lưu, `set()` không ghi đè được ⇒ không bao giờ lọt vào `tl.properties`.

## Tham số Java cho game (`minecraft.launcher.MinecraftLauncher`)

Thứ tự trong `createJvmArgs`: tham số của người dùng (`minecraft.javaargs`) → trust store → bộ "tối ưu" → `-Xmx`.

Bộ "tối ưu" (`minecraft.improvedargs.type` = `DEFAULT`, mặc định) — `addCommonOptimizedArguments` + `addG1OptimizedArguments`:
- `-Xms` = min(xmx, 2048) MB; nếu RAM trống < xmx: `max(RAM trống / 2, 512)` (nên mỗi lần mở mỗi game một `-Xms` khác nhau: 840, 1097, 1828, 2048…);
- `-XX:+DisableExplicitGC`, `-XX:+AlwaysPreTouch`, `-XX:MaxGCPauseMillis=50`, `-XX:+ParallelRefProcEnabled`, G1 với `G1NewSizePercent=20`, `G1HeapRegionSize=8M`, `G1ReservePercent=20`, `InitiatingHeapOccupancyPercent=15`…

JVM lấy giá trị **cuối cùng** của mỗi cờ ⇒ tham số người dùng không ghi đè được bộ này. Muốn bỏ: `minecraft.improvedargs.type=NONE` (`OptimizedArgsType.parse`, không phân biệt hoa thường). Khi đó chỉ còn tham số người dùng + `-Xmx`.

`minecraft.xmx=auto` ⇒ `MemoryAllocationService` gợi ý; máy 16 GB ra 4096.

## Nút "Enter the game" (`ui.login.buttons.PlayButton`)

- Trạng thái `PLAY` / `INSTALL` / `REINSTALL` / `CANCEL`. Khi `LoginState.LAUNCHING` ⇒ `CANCEL`; bấm lúc này gọi `loginForm.stopLauncher()` ⇒ **huỷ** lần mở game đang chạy.
- Phím Enter không bấm được nút; phải bấm chuột (kiểm chứng tay 10/10).
- Gửi `WM_LBUTTONDOWN/UP` bằng PostMessage không ăn (3/3 lần, CPU launcher gần như không đổi). Nhiều khả năng AWT xác định thành phần bị bấm theo vị trí con trỏ thật chứ không theo toạ độ trong thông điệp — chưa đọc mã AWT để khẳng định.

## Cấu hình dùng chung

- 4 cửa sổ launcher đọc **cùng** `tl.properties` lúc khởi động, giữ trong bộ nhớ và ghi lại toàn bộ khi đóng (`LegacyLauncher.kill` → `Configuration.save`). Cửa sổ đóng sau cùng thắng.
- Danh sách tài khoản cũng là một file dùng chung; nhiều khả năng bị ghi đè theo kiểu tương tự ⇒ tài khoản thêm ở cửa sổ này có thể mất khi cửa sổ khác lưu. Đây là giải thích khả dĩ nhất cho sự cố "mở nhầm nick" ngày 11/10 (`--username` không tìm thấy nick); chưa kiểm chứng trực tiếp vì không đọc file tài khoản.
- Không đọc file tài khoản và `logs\launcher.log` — cả hai chứa mã đăng nhập Microsoft.
