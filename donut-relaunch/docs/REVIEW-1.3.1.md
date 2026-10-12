# Hướng dẫn review — Donut Relaunch 1.3.1

Đọc theo thứ tự dưới đây là đủ để hiểu bản này đổi gì và vì sao. Lý do chi tiết từng lỗi ở [CHANGELOG](../CHANGELOG.md),
những gì đã đọc được từ mã của Legacy Launcher ở [legacy-launcher.md](legacy-launcher.md).

## 1. Tóm tắt trong 30 giây

| Vấn đề | Nguyên nhân | Sửa ở đâu | Kết quả đo (11/10) |
|---|---|---|---|
| Mỗi game ăn ~1 nhân CPU | ModMenu kiểm tra cập nhật bằng luồng ảo → luồng `Write-Poller` của Java chạy liên tục | `config/modmenu.json`; `Eco.fixModMenu` | 11–13% → 0,4–0,8% mỗi game |
| Mỗi game giữ 1,5–2,4 GB | Launcher thêm `-Xms` lớn + `AlwaysPreTouch` + `DisableExplicitGC` | script `Set-TlProperties`; `Eco.trimMemory` | 4 game 7,55 → 3,58 GB; máy 89% → 72% |
| Không mở lại được khi đang chơi Valorant | bấm chuột giả lập bị chặn; PostMessage bị Java bỏ qua | script `Restart-Launcher` | mở lại không cần chuột, 12–16 giây tới game |
| Bấm lại huỷ mất lần mở game | nút Enter the game thành nút Huỷ khi đang chuẩn bị | script khối `'clicked'` + `Update-LauncherActivity` | không còn bấm khi launcher bận |
| Launcher mới mở nhầm nick | danh sách tài khoản dùng chung, bị ghi đè | script: giữ launcher cũ, `Stop-WrongGame`, `launcher-block.txt` | game nhầm bị tắt ngay, quay về launcher cũ |
| Script tự cập nhật kẹt ở bản cũ | chỉ nạp bản mới khi không còn việc nào | script: `pending-jobs.txt` | nạp được cả khi có việc đang chờ |

## 2. Nên đọc file nào, chỗ nào

### `script/donut-relaunch.ps1` (thay đổi lớn nhất)

| Dòng | Hàm / khối | Xem gì |
|---|---|---|
| 135 | `Set-GamePriority` | chỉ hạ ưu tiên game, launcher giữ `Normal` |
| 160 | `Trim-Launcher` | chỉ trim khi cửa sổ launcher đang ẩn |
| 186 | `Log-Usage` | lần đầu chỉ lấy mốc CPU, 1 phút sau mới ghi |
| 236 | `Update-LauncherActivity` | `BusyAt` = lần cuối launcher dùng ≥ 15% 1 nhân; mới theo dõi thì coi như bận 9 giây trước (phải đo thêm ≥ 3 giây) |
| 264–319 | `$DefaultJavaArgs`, `Get-LauncherConfig`, `Set-TlProperties` | sửa từng khoá, giữ nguyên các dòng khác, đọc/ghi ISO-8859-1 như Java Properties; sao lưu 1 lần |
| 321 | `Restart-Launcher` | **không tắt launcher cũ**; lấy dòng lệnh launcher từ Win32_Process (không ghi log), bỏ phần `--username/--usertype/--launch` cũ rồi nối mới |
| 351–375 | `Get-NoRestart`, `Add-NoRestart`, `Stop-WrongGame` | game nhầm nick bị tắt được ghi vào `killedPids` để không bị tính là crash |
| 487 | khôi phục `pending-jobs.txt` | chỉ nhận file < 15 phút và launcher còn sống |
| 509 | kiểm tra nhầm nick | chạy **trước** khi coi game là "game mới" |
| 546 | game vừa tắt | bỏ qua pid trong `killedPids` |
| 573–620 | vòng xử lý việc mở lại, trạng thái `llstart` | quá 4 phút chưa có game → tắt launcher mới, quay về cũ |
| 620 | trạng thái `clicked` | logic "đã nhận lệnh / rảnh 40 giây" — chỗ dễ sai nhất, đọc kỹ |
| 667 | `$useRestart` | `mode=restart`, < 3 lần, nick không nằm trong `launcher-block.txt` |
| 723 | tự cập nhật | không chờ việc ở trạng thái `wait`, chỉ chờ `clicked`/`llstart` |

`Post-Click` (dòng 445) còn trong file nhưng **không được gọi nữa** — giữ lại để ghi chú vì sao không dùng; có thể xoá.

### `mod/.../Eco.java`

| Dòng | Hàm | Xem gì |
|---|---|---|
| 70 | `tick` | `skipGameRender` đặt lại mỗi tick vì `setScreen()` tự trả về `false`; gọi `fixModMenu` 1 lần lúc khởi động |
| 176 | `minHeap` | đọc `MinHeapSize` (rồi `InitialHeapSize`) qua HotSpotDiagnosticMXBean |
| 206 | `gcRun` | MBean `com.sun.management:type=DiagnosticCommand`, thao tác `gcRun`; lỗi thì quay về `System.gc()` |
| 218 | `trimMemory` | bỏ qua nếu heap đang giữ − `-Xms` < 128 MB (dọn chỉ tốn CPU) |
| 320 | `perf` | thêm commit + RAM máy; đếm `pollerHot` |
| 437 | `fixModMenu` | thay bằng regex, chỉ ghi file khi có thay đổi |

`RelaunchMod.java` và `AsellBridge.java` không đổi logic ở 1.3.1 (chỉ đổi số phiên bản trong `fabric.mod.json`).

## 3. Bằng chứng đã có

- `perf-<nick>.log` 12:30–12:40 PM: `Write-Poller 10,1–12,0%` ở cả 4 game; game đã tắt update checker: không còn dòng Write-Poller, `CPU 0,8% cả máy`.
- `usage.log` 12:54 PM (trước): RAM game 1,3–2,4 GB mỗi game, tổng 7.551 MB, máy 89% (14,1/15,8 GB). 1:27 PM (sau): 848–1.007 MB mỗi game, tổng 3.578 MB, máy 72% (11,3/15,8 GB).
- Dòng khởi động trong `perf-<nick>.log` sau khi đổi: `cờ JVM [-XX:+UseG1GC, … -Xms256M, -Xmx4096M …]`, heap giữ 272 MB lúc mở (trước: 1.104–2.048 MB), không còn `AlwaysPreTouch`/`DisableExplicitGC`.
- `watchdog.log`: 3 lần mở lại bằng launcher mới (1:05, 1:16, 1:17 PM) → có game sau 12–16 giây → tắt launcher cũ; mod tự vào server và gõ `/asell auto` khoảng 15 giây sau khi game mở.
- `relaunch.log`: `dọn RAM: heap đang giữ 440 → 424 MB (đang dùng 344 → 284 MB, dừng 269 ms)` và `bỏ qua dọn RAM: … -Xms … 256 MB` ⇒ cả hai nhánh của `trimMemory` đã chạy thật.
- Build: `tools/build-rl.sh` từ mã nguồn trong repo ra jar giống hệt nội dung `release/…1.3.1.jar`; kiểm tra liên kết 0 lỗi. Script qua `Parser.ParseFile` + `Add-Type` của PowerShell 7.4 (chỉ kiểm cú pháp, không chạy được API Windows trên Linux).

## 4. Rủi ro / điểm cần người review quyết định

1. **Danh sách tài khoản dùng chung giữa các cửa sổ launcher.** Đã có đường lui (giữ launcher cũ, tắt game nhầm), nhưng nếu launcher cũ đã mất thì cần người chọn lại nick bằng tay. Cách triệt để: mỗi nick một thư mục Legacy Launcher riêng (`--settings` khác nhau) — chưa làm.
2. **`tl.properties` dùng chung.** Launcher cũ (mở trước 1.3.1) vẫn giữ cài đặt cũ trong bộ nhớ và ghi lại khi đóng bình thường; script ghi đè lại trước mỗi lần mở launcher nên không sao, nhưng mở launcher bằng tay ngay sau đó sẽ đọc cài đặt cũ.
3. **Dọn RAM là Full GC:** dừng game ~270 ms mỗi 10 phút khi đang treo (không xem). Có thể tăng `gcMinutes` trong `eco.txt` hoặc đặt 0 để tắt.
4. **`-XX:ParallelGCThreads=2`:** giảm CPU khi 4 game cùng dọn rác, đổi lại mỗi lần dọn rác trẻ lâu hơn. Theo dõi cột `GC … ms` trong `perf-<nick>.log`.
5. **Chế độ bấm nút** (dự phòng) vẫn hỏng khi Valorant/app quyền admin ở trên cùng — giới hạn của Windows, không sửa được bằng script.
6. **Gradle chưa chạy thử** (môi trường build không tải được Loom).
7. Đang theo dõi: vài lần game ghi "bạn tự tắt" rồi 3–14 giây sau có game mới cùng nick — nhiều khả năng do bấm tay, chưa thấy lỗi.

## 5. Cách thử lại

1. Ghi `restart` vào `...\donut-relaunch\cmd-<nick>.txt`.
2. `watchdog.log` phải có lần lượt: `tắt: restart` → `mở lại game bằng cách khởi động lại launcher` → `mở launcher mới (pid …)` → `game đã chạy lại` → `đã tắt launcher cũ`.
3. `relaunch.log`: `game vừa được mở lại → 3 giây nữa tự vào …` → `đã vào lại server` → `bật ASell: /asell auto`.
4. `perf-<nick>.log`: dòng khởi động có `-Xms256M`, không có `AlwaysPreTouch`; 10 phút sau CPU < 1% khi không xem.
5. Đường lui nhầm nick: nhánh "tắt game nhầm + chờ người chọn lại nick" đã chạy thật lúc 1:15 PM ngày 11/10 (`launcher (pid …) mở nhầm nick … → đã tắt game nhầm đó`, rồi `CẦN BẠN …`). Nhánh "tắt launcher mới, quay về launcher cũ" **chưa có dịp chạy thật** — nên đọc kỹ khối ở dòng 509–540.
