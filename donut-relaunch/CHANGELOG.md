# CHANGELOG — Donut Relaunch

Mỗi mục: **lỗi / nhu cầu → nguyên nhân trong logic → cách sửa → đã khắc phục được gì**. Giờ theo giờ Việt Nam.

## 1.3.1 — 11/10/2026 (mod + script)

### 1. CPU: luồng `Write-Poller` của Java chạy vòng, mỗi game ăn trọn 1 nhân
- **Triệu chứng:** sau khi bật chế độ Eco (1.3.0), mỗi game vẫn 11–13% CPU cả máy (máy 8 luồng ⇒ ~1 nhân). `perf-<nick>.log` 12:30 PM cho thấy gần hết nằm ở luồng `Write-Poller` (10–12%), còn Render thread chỉ 0,2–0,6%.
- **Nguyên nhân:** `Read-Poller`/`Write-Poller` là luồng `sun.nio.ch.Poller` của JDK 21, chỉ phục vụ *luồng ảo* (virtual thread) đang chờ I/O mạng. Quét toàn bộ mod: chỉ ModMenu 13.0.4 tạo luồng ảo (`UpdateCheckerThreadFactory` → `Thread.ofVirtual()`), dùng để kiểm tra cập nhật mod lúc mở game. Sau lần kiểm tra đó, Write-Poller ăn CPU liên tục suốt phiên (đo được trên Windows + JDK 21; chưa xác định lỗi cụ thể bên trong JDK, chỉ chứng minh được nguyên nhân kích hoạt bằng thử A/B bên dưới).
- **Kiểm chứng A/B:** tắt `update_checker` rồi khởi động lại 1 game: không còn Write-Poller, CPU 10,9% → 0,8% (mẫu 10 phút). Áp cho cả 4 game: tổng CPU 4 game ~50% → 2,1%.
- **Sửa:** `config/modmenu.json`: `update_checker=false`, `button_update_badge=false`. Mod 1.3.1 tự đổi lại nếu ai bật lên (`Eco.fixModMenu`) và ghi CẢNH BÁO khi Poller còn ≥ 4% trong 2 lần đo liền.
- **Đã khắc phục:** CPU mỗi game 11–13% → 0,4–0,8%.

### 2. RAM: launcher ép mỗi game giữ sẵn 0,8–2 GB, và chặn dọn RAM
- **Nguyên nhân** (giải mã `MinecraftLauncher.addCommonOptimizedArguments` của launcher): bộ tham số "tối ưu" mặc định thêm `-Xms` = min(xmx, 2048) MB (RAM trống < xmx thì `max(trống/2, 512)`), `-XX:+AlwaysPreTouch` (chiếm ngay toàn bộ phần đó) và `-XX:+DisableExplicitGC` (`System.gc()` của chế độ Eco 1.3.0 không chạy gì). Tham số Java người dùng tự nhập được thêm **trước** bộ này nên không ghi đè được; phải tắt hẳn bằng `minecraft.improvedargs.type=NONE`.
- **Sửa:**
  - script ghi `tl.properties` trước mỗi lần mở launcher: `improvedargs.type=NONE`, `javaargs=` tham số gọn trong `launcher.txt` (G1, `-Xms256M`, `MaxHeapFreeRatio=30`, 2 luồng GC). Bản gốc: `backup\tl.properties.bak`;
  - mod dọn RAM bằng lệnh chẩn đoán `GC.run` (DiagnosticCommand) — chạy được dù có DisableExplicitGC (thử trên JDK 21: heap co 804 → 64 MB); chỉ dọn khi heap còn co được (đang giữ > `-Xms` + 128 MB); mỗi 10 phút thay vì 5;
  - script trả RAM thừa của launcher đang ẩn (`EmptyWorkingSet`): 250–330 MB → ~0 MB (sau đó tăng lại 100–250 MB vì launcher đọc log của game).
- **Đã khắc phục:** 4 game 7,55 GB → 3,58 GB (848–1007 MB mỗi game, commit 2,6–2,9 GB → 1,2–1,3 GB); RAM cả máy 89% → 72%.

### 3. Mở lại game bằng cách mở launcher mới, không bấm chuột
- **Vấn đề:** cách bấm nút "Enter the game" hỏng khi người dùng đang chơi Valorant (Vanguard chặn chuột giả lập / cửa sổ game che launcher), còn giật chuột của người dùng.
- **Đã thử và bỏ:** gửi `WM_LBUTTONDOWN/UP` bằng PostMessage (bản 1.3.0) — launcher Java (AWT) xác định nút theo vị trí con trỏ thật nên bỏ qua; 3/3 lần thử CPU của launcher chỉ +16–203 ms (không làm gì).
- **Sửa:** launcher nhận tham số `--username <nick> --usertype minecraft --launch` (đọc `ArgumentParser`): tự chọn nick, tự đăng nhập, 3 giây sau tự vào game; tham số dòng lệnh là "constant" nên **không bị lưu** vào `tl.properties`. Script đọc dòng lệnh của tiến trình launcher (cha của game) rồi mở launcher mới với cùng dòng lệnh + 3 tham số đó.
- **Đã khắc phục:** mở lại không cần chuột, chạy được khi đang chơi game khác; 12–16 giây từ lúc mở launcher tới lúc có game.

### 4. Bấm lại có thể HUỶ đúng lần mở game đang chạy
- **Nguyên nhân:** trong lúc launcher chuẩn bị game, nút "Enter the game" đổi thành nút Huỷ (`PlayButton.state == CANCEL` → `loginForm.stopLauncher()`). Script cũ thấy launcher còn hiện sau 45 giây (bản 1.3.0: 75 giây) thì bấm lại → có lúc huỷ lần mở đang chạy (một nick lúc 12:43 PM).
- **Sửa (chế độ bấm nút):** đo CPU của tiến trình launcher. Tăng ≥ 800 ms hoặc có lúc ≥ 15% của 1 nhân ⇒ launcher đã nhận lệnh → chỉ bấm lại khi nó đã rảnh ≥ 40 giây (lần mở trước dừng giữa chừng). 15 giây không phản ứng ⇒ bấm vị trí khác. Trước mỗi lần bấm, launcher phải rảnh ≥ 12 giây.
- Launcher giữ ưu tiên CPU Normal: lúc ẩn nó gần như không dùng CPU; 1.3.0 hạ cả launcher xuống BelowNormal, dễ làm việc chuẩn bị game chậm khi máy đang bận (chưa đo riêng được).

### 5. Launcher mới mở nhầm nick
- **Sự cố 1:07 PM:** launcher mới cho nick A mở ra nick B (đang chạy ở client khác).
- **Nguyên nhân khả dĩ:** 4 cửa sổ launcher dùng **chung** một danh sách tài khoản, cửa sổ nào lưu sau sẽ ghi đè. Nick A không còn trong danh sách ⇒ `--username A` không chọn được ⇒ launcher dùng nick đang được chọn chung (B).
- **Sửa:** mở launcher mới **song song**, giữ launcher cũ (đang chọn đúng nick). Thấy game của nick khác mọc ra từ launcher của việc mở lại ⇒ tắt ngay game đó (chưa vào server), tắt launcher mới, quay về launcher cũ, ghi nick vào `launcher-block.txt` (từ đó nick này dùng cách bấm nút). Không còn launcher cũ ⇒ đánh dấu "CẦN BẠN" trong `watchdog.log`, không tự bấm để khỏi vào nhầm nick. Game bị tắt vì nhầm nick không bị tính là crash.

### 6. Script tự cập nhật bị kẹt ở bản cũ
- **Nguyên nhân:** chỉ chạy lại bản mới khi **không có** việc mở lại nào; việc mở lại không bấm được (bị che) thì treo mãi ⇒ bản mới (có cách bấm khác) không bao giờ được nạp.
- **Sửa:** chỉ chờ việc đang ở bước "đã bấm/đang mở launcher"; việc đang chờ bấm thì ghi `pending-jobs.txt`, bản mới đọc lại và làm tiếp.

### Thêm
- `usage.log` (script, mỗi 10 phút): CPU, working set, commit của từng game + launcher, RAM cả máy.
- `perf-<nick>.log` thêm commit của tiến trình và RAM cả máy.

## 1.3.0 — 11/10/2026 12:05 PM
- **Nhu cầu:** treo 4 nick ăn nhiều CPU/RAM, muốn làm việc khác.
- Mod `Eco`: cửa sổ không được chọn ⇒ `skipGameRender` (không vẽ thế giới, 5 giây vẽ 1 khung), `System.gc()` + `MaxHeapFreeRatio` mỗi 5 phút (về sau mới biết bị DisableExplicitGC chặn — xem 1.3.1), `perf-<nick>.log`.
- Script: ưu tiên CPU BelowNormal (`priority.txt`); thử bấm nút bằng PostMessage (không chạy — xem 1.3.1).
- Mapping mới: `MinecraftClient.skipGameRender` = `field_1743`, `getOverlay` = `method_18506`.

## 1.2.0 — 10/10/2026 7:40 PM
- **Lỗi:** 2 nick đứng yên 2 tiếng (4:30–6:53 PM) dù ASell báo đang chạy.
- **Nguyên nhân** (nhật ký ASell riêng từng nick ở `%LOCALAPPDATA%\DonutSellRemote\evidence`): server lag GUI, ASell đọc "Your Orders" khi server chưa gửi xong danh sách ⇒ kết luận nhầm là không có order ⇒ `activeBuyOrderWaiting = false`. Ở `WAITING_ELYTRA_ORDER_MENU` / `WAITING_ELYTRA_DELIVERY`, mọi nhánh đều cần 1 trong 4 cờ; cả 4 false ⇒ không nhánh nào chạy, GUI đang mở nên watchdog của ASell cũng không reconnect.
- **Sửa:** canh kẹt — trạng thái không đổi quá 3 phút ⇒ `/asell stop` rồi `/asell auto`; không đụng vào lúc server restart, ASell tự dò GUI, khoá an toàn tiền. Thêm `asell-watch.txt`, file lệnh `cmd-<nick>.txt`.
- Không ghi "ASell đang dừng" trong 5 giây chờ tự bật lại (vá lỗ hổng của 1.1.0: game chết đúng lúc đó thì lần sau không bật lại ASell).
- **Màn hình đen:** game vẫn chạy (ASell vẫn mua bán) nhưng cửa sổ đen; máy lúc đó RAM 93–98%, CPU 70–100%. Chỉnh chung: `maxFps` 20, tắt VSync, mipmap 0, biome blend 0; Sodium tắt "No Error Context", 1 luồng dựng chunk.
- Script (10/10 10:30 PM): launcher còn hiện thì chờ 45 giây mới bấm lại (trước 20 giây) — đăng nhập lại Microsoft mất ~30 giây, bấm sớm dễ trúng cửa sổ launcher khác nằm bên dưới.

## 1.1.0 — 10/10/2026 2:29 AM
- **Lỗi:** mở lại xong vào server nhưng ASell không chạy tiếp. Đã giả định sai rằng ASell tự chạy khi vào server; thật ra trạng thái chạy của ASell chỉ nằm trong bộ nhớ.
- **Sửa:** `AsellBridge` đọc `taskManager.isRunning()` bằng reflection, ghi `state-<nick>.txt`; mở lại + vào lại server mà `asell=1` ⇒ 5 giây sau `/asell auto`.

## 1.0.0 — 10/10/2026 2:09 AM
- Mod bắt "Invalid session" ⇒ tự thoát; tự vào lại server cuối sau khi mở lại. Script bấm "Enter the game" trên đúng cửa sổ launcher của nick (tâm vùng trong + 47 px × DPI).
- Sửa ngay trong ngày: Get-CimInstance thỉnh thoảng đọc hụt 1 lượt ⇒ coi nhầm là crash ⇒ chỉ coi là tắt khi `Get-Process` cũng không thấy.
