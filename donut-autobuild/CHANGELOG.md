# CHANGELOG — Donut AutoBuild

Mỗi mục ghi: **lỗi / nhu cầu → nguyên nhân trong logic → cách sửa → đã khắc phục được gì**. Ngày ở tiêu đề = ngày jar được chép vào thư mục phát hành trên máy (giờ Việt Nam). Giờ trong nội dung lấy theo log người dùng gửi.

Toạ độ công trình đã được bỏ khỏi mục này (chỉ giữ Y khi cần). Nhật ký đầy đủ hơn (log từng phút, số dòng log) nằm ở tài liệu dự án, không đưa lên repo.

Các bản không có mục riêng: 2.0.0–2.9.9 (16–18/09), 3.0.0–3.0.16 (18–20/09), 3.0.19–3.0.24 (21/09), 3.0.35–3.0.77 (22–24/09). Không có ghi chép đi kèm ở thời điểm đưa mã lên; thay đổi của chúng đã nằm trong mã hiện tại. 3.0.106 không có jar trong thư mục phát hành.

---

## 3.0.122 — 26/10/2026 · phím/chuột thật, đào không bị huỷ giữa chừng, bỏ qua khối chỉ khác trạng thái

Bản đầu tiên build bằng Gradle (Loom) từ mã nguồn trong repo. Sửa để build được: `OrderCollector` gọi `LoreComponent.lines()` (trước là tên chưa map `comp_2400`), `build.gradle` thêm `fabric-message-api-v1` (chỉ để biên dịch, không đóng kèm).

### 1. Di chuyển bị server kéo lùi, đặt "rồi không hiện"
- **Nguyên nhân:** mod gọi `interactBlock` ngay cuối tick, tức là SAU gói góc nhìn của tick đó. Người thật bấm chuột thì game xử lý cú bấm ở đầu tick sau (`handleInputEvents → doItemUse`), TRƯỚC gói góc nhìn. Anti-cheat kiểm tra cú đặt theo đúng thứ tự vanilla, nên cú đặt bị trả về. Khối client tưởng có (ghost block) làm nhân vật bị kéo lùi khi đi lên.
- **Sửa:**
  - Mọi phím mod bấm (W/A/S/D, nhảy, ngồi, chạy, chuột trái) đi qua `Input.key()`. Cuối tick, `Input.flush()` phát sự kiện phím thật (`KeyBinding.setKeyPressed` + `onKeyPressed`) theo trạng thái cuối tick, đúng đường của bàn phím/chuột vật lý.
  - Đặt khối, đổ/múc xô, mở/đặt ender chest, bấm crafter: mod bấm chuột phải thật (`Input.queueUse`). Thao tác chỉ chạy khi game xử lý cú bấm (mixin `doItemUse`), và chỉ khi tâm ngắm game vẫn ở đúng ô/mặt.
  - Vừa bấm chuột phải thì không quay đầu cho tới khi gói góc nhìn tiếp theo đã đi.

### 2. Đào mãi không vỡ ("có vết nứt rồi mất", "client thấy vỡ nhưng server chưa phá")
- **Nguyên nhân:** `breakBlock` nhả chuột trái ở đầu mỗi lần gọi rồi mới bấm lại. Chỉ cần 1 tick góc nhìn chưa gửi xong, ngắm lệch, hoặc mod bận việc khác là chuột bị nhả. Khi đó game huỷ đập (gửi ABORT), vết nứt mất và server tính lại từ đầu.
- **Sửa:**
  - Đang giữ chuột trên đúng khối thì giữ tiếp, không xoay đầu theo điểm ngắm mới.
  - Mod bận việc khác tối đa 3 tick thì vẫn giữ chuột, miễn tâm ngắm còn trên khối.
  - Chỉ nhả khi khối đã vỡ, đổi khối, hoặc có lý do an toàn (TNT, sắp đặt khối).

### 3. Đặt nhầm loại khối ("Server đặt Composter thay vì Powered Rail")
- **Nguyên nhân:** server bỏ qua cú SWAP đồ từ túi lên tay (0,9 giây sau vẫn cầm món cũ). Khi SWAP thành công server cũng không gửi gì, nên client không biết.
- **Sửa:** sau mỗi SWAP, xin server gửi lại cả túi đồ NGAY. Gói này đi sau gói SWAP và server xử lý theo thứ tự. Mod khoá đặt khối tới khi nhận được.

### 4. Khối chỉ khác trạng thái không bị đập nữa
- Composter (`level`) và Note Block (`note`, `instrument`): coi là đúng.
- `open` (cửa, cửa sập, cổng rào), `enabled` (hopper), `powered`/`lit`/`triggered`: coi là đúng.
- Repeater khác `delay` và comparator khác `mode`: không đập, chuột phải chỉnh cho đúng (kể cả khi đang có điện).
- Lá: `distance`/`persistent` bỏ qua, nhưng có nước hay không vẫn phải đúng.

### 5. Khối có nước bên trong: múc nước trước khi đập
- Trước khi đập bất kỳ khối waterlogged nào (lá, slab, thang…), mod cầm xô rỗng múc nước ra.
- Múc xong mà khối đã đúng bản vẽ (vd. lá thừa nước) thì không đập nữa.
- Không có xô rỗng thì mua ở `/shop`. Múc 6 lần không được thì để lại, không đập.

### 6. Mua ở /shop có menu xác nhận
- Sau cú bấm mua, nếu hiện menu khác Quick Buy có ô "Confirm" / "Accept" / "Yes" (hoặc kính/len xanh lá), mod bấm ô đó (tối đa 3 lần), không bao giờ bấm Cancel. Log ghi tên menu và các ô.
- Món không cần xác nhận thì mua thẳng như cũ.

### 7. Nhanh hơn
- Thời gian tính toán mỗi tick tăng từ 10 lên 20 ms. Tìm đường dùng 18 ms/tick (trước 10), chọn chỗ đứng 8 ms/tick (trước 3), quét công trình 18 ms/tick (trước 10). Heuristic tìm đường duyệt mảng thay vì HashSet.
- Đặt trước tối đa 4–6 khối chưa được xác nhận (trước 2–4). Khối bám vào khối chưa xác nhận vẫn phải chờ.
- Khối kê: chỉ đi Order khi còn < 8 (trước < 16). Chỉ đếm khi túi đồ đã khớp server, và đếm cả tay trái.

### Kiểm thử
- Chạy offline với Minecraft 1.21.4 (đã bootstrap): BuildQuality 171, HeldPlacement 101, MovementResume 275, MovementMining (đi, tìm đường, đào giữ chuột) 206. Có thêm 15 phép mới cho mục 4 và cho tìm đường.
- Các bộ ControllerQuality, Speed, WaterShop, WaterlogAim, WaterlogTransaction dừng ở cùng phép kiểm tra như khi chạy với mã 3.0.121 gốc. Đó là lỗi có từ trước (bộ test viết cho 3.0.120), không do bản này.
- Chưa thử trên DonutSMP.

---

## 3.0.121 — 04/10/2026 · hết đặt nhầm loại do túi đồ "đoán"; mua được cúp netherite

Bản này dựng trên jar 3.0.120. Chỉ thay `BuildController*`, `InventoryAccess`, `SurvivalSupplies`; mọi lớp khác giữ nguyên từng byte. Toàn bộ thay đổi BuildController của 3.0.110–3.0.120 được ghép lại vào mã nguồn (trước đó các bản này chỉ có dạng bản vá bytecode).

### 1. Đặt nhầm loại: bấm Cobblestone mà server đặt Obsidian / Oak Leaves
- **Dấu hiệu trong log:** trước mỗi loạt đặt nhầm đều có dòng "Cất món đồ trên con trỏ vào ô …", tức server làm khác dự đoán của client. Xin server đồng bộ 2–4 lần vẫn lệch.
- **Nguyên nhân:** túi đồ phía client bị lệch so với server vì 4 nguồn:
  1. Gộp xô rỗng mỗi 0,1 giây. Mỗi lần 2–3 cú bấm dựa trên túi đồ client tự đoán, trong khi server trả lời sau 300–500 ms.
  2. Đổ/múc xô: client tự đổi Xô nước → Xô rỗng. Server từ chối thì không gửi lại ô đó.
  3. Gói "con trỏ" của menu vừa đóng (`/shop` mua xô) về muộn. Client áp nó vào túi đồ chính, sinh ra món đồ "ma" trên con trỏ.
  4. Sau khi đổi đồ (SWAP), mod chỉ chờ `min(300 ms, ping/2 + 100)`, ngắn hơn 1 lượt ping.
- **Sửa (`InventoryAccess` + `BuildController`):**
  - Túi đồ chuyển sang trạng thái "chưa kiểm chứng" (`dirty`) sau khi: đóng menu, đổ/múc xô mà món trên tay thật sự đổi, cất/ném đồ trên con trỏ, hoặc có bằng chứng lệch.
  - Trong trạng thái đó: không đổi đồ, không đặt, không gộp xô, không múc, không đặt ender chest. Mod xin server gửi lại cả túi (sau cú bấm cuối ≥ 1 lượt ping), nhận xong mới làm tiếp.
  - Server đã xác nhận nước đổ vào (`pourConfirmed`) thì không cần xin đồng bộ.
  - Mọi cú bấm ô đồ chờ trọn ping + 100 ms (trong khoảng 250–1200 ms). Gộp xô chỉ khi túi đã được xác nhận, mỗi lượt 1 cú.
  - Xin đồng bộ 3 giây không được trả lời → đổi cách xin (bấm giữa ngoài túi ↔ shift-click ô trống). Hụt cả 2 cách → tạm tin túi đồ; 3 lần như vậy → 10 phút chỉ chờ 1 lượt ping.
  - Lưới an toàn: chờ quá 8 giây (lúc gửi được) hoặc 20 giây tổng → thôi chờ.
  - Lúc tạm dừng (máu thấp, ăn) vẫn xử lý đồng bộ để cầm được đồ ăn ngay.
  - Nhật ký túi đồ 40 dòng. Khi có "Server đặt X thay vì Y" / "đặt nhầm", log in `[TÚI ĐỒ]`: thanh nóng + 14 thao tác gần nhất. Lần sau còn đặt nhầm sẽ biết chính xác thao tác nào gây lệch.
- **Đã khắc phục:** đường gây lệch túi đồ bị chặn. Chưa chạy thử trong game.

### 2. Không bao giờ mua được cúp netherite
- **Nguyên nhân:** `SurvivalSupplies` (3.0.110) dùng trần giá chung 500K. `/shop` bán cúp netherite khoảng 5,1M → luôn "giá > trần → không mua", 20 giây thử lại một lần.
- **Sửa:** cúp dùng trần riêng `PICKAXE_MAX_PRICE` = 15M.

### 3. Kiểm tra lại, không đổi
`PlacementRecoveryBudget` (3.0.110) đếm đúng: mọi lần đặt hỏng chưa khắc phục, kể cả vòng "đặt 5 lần vẫn mất → để lại". Đủ 25 thì thoát ra vào lại; ô đặt lại được thì trừ ra; vào lại thì về 0.

---

## 3.0.110 → 3.0.120 — 30/09 → 02/10/2026 (phiên làm việc khác, dạng bản vá bytecode)

Các bản này được làm bằng cách ghép bytecode (ASM) vào jar trước đó, mỗi bản kèm README và bộ kiểm thử. Từ 3.0.121 tất cả đã có trong mã nguồn.

| Bản | Thay đổi chính |
|---|---|
| 3.0.110 | Ngân sách 25 lần đặt hỏng **chưa khắc phục** (cộng dồn nhiều ô; ô khác đặt được không xoá lỗi; chỉ ô đó đặt lại được mới xoá) → thoát ra vào lại. Bỏ ngưỡng tăng dần 50/100. Hết đồ ăn (đói ≤ 14) → mua ở `/shop` (Steak trước, 32 cái). Hết cúp → mua 1 Netherite Pickaxe. |
| 3.0.111 | Kiểm tra lại món trên tay ngay trước mỗi cú đặt. Không coi khối sai là xong chỉ vì đã thử nhiều lần; ô đã lưu là xong vẫn đối chiếu lại với thế giới. **Freecam phím N.** |
| 3.0.112 | Freecam: sửa chiều A/D, bỏ sương mù, giữ khoảng nhìn theo render distance. Phản xạ nhanh hơn: cùng loại khối cùng ô thì không chờ lại; ô mất thì thử lại sau ~6 tick thay vì 40. |
| 3.0.113 | Đổ nước đúng ô theo `FluidFillable` (lá, slab, cầu thang nhận nước trong chính khối). Từ chối do tay/ô chưa đúng không còn bị hiểu nhầm là nguy cơ TNT. Có nước sẵn thì đổ xong mới chuyển việc. Hàng sửa tôn trọng dấu hoãn. |
| 3.0.114 | Tách đổ nước khỏi cách "ngồi chìa ra mép" (đổ nước cần đứng). Tia ngắm đang trúng đúng khối thì dùng luôn. Cầm sẵn xô từ lúc đi tới. |
| 3.0.115 | Phanh bằng nhả phím (ma sát vanilla), không bấm phím ngược. Không đi chéo cắt góc hố. Đập ender chest bằng giữ chuột trái qua luồng đào của Minecraft. |
| 3.0.116 | Sửa 3.0.115 chặn nhầm khi đứng sát mép: chỉ chặn khi bước vật lý kế tiếp thật sự mất sàn. Khôi phục quy tắc tìm đường cũ. |
| 3.0.117 | Đổ nước vào lá bằng `interactItem` (trước dùng `interactBlock`, có lúc server nhận nhưng không gọi `BucketItem.use`). Giữ xô và mục tiêu tới khi server xác nhận `waterlogged=true`. Chỉ lần dùng xô thật mới tính lỗi. |
| 3.0.118 | GUI mới: thư viện file schematic, đặt/di chuyển/xoay/lật placement, tô màu theo trạng thái (3 mixin vẽ). |
| 3.0.119 | Sửa ẩn/hiện schematic mất hình; tô xanh khối chưa xây. |
| 3.0.120 | Bỏ 3 mixin vẽ của 118/119, trả Litematica về cách vẽ gốc. GUI quay về 3 tab Công trình / Vật liệu / Cài đặt của 3.0.109, thêm chọn file + sửa vị trí + nút **Nạp** để chốt vị trí. Xem tầng: Từng tầng / Tất cả / Ẩn, PgUp/PgDn. |

---

## 3.0.109 — 29/09/2026 · `/shop` lại tìm được đồ; không bấm Collect liên tục
- **`/shop` không tìm được Obsidian:** mod bấm Search, server mở biển nhập chữ, cùng tick đó nhánh "tự đóng biển sau khi đặt bảng" (3.0.99) đóng luôn. Lỗi hồi quy của 3.0.99. Sửa: không tự đóng biển khi đang tìm hàng ở shop.
- **Order chưa ai giao mà bấm Collect 2 lần/giây:** `OrderCollector.onChat()` chưa từng được nối vào chat nên không bao giờ thấy "You have no items to collect". Sửa: đọc chat qua `ChatWatch`; server báo không có gì → bỏ order đó 10 phút; không mở order "0/… Delivered"; tối đa 3 lần bấm, cách 1 ping + 1,5 giây.

## 3.0.108 — 29/09/2026 · 25 lần đặt hỏng liên tiếp → thoát ra vào lại; chỉ K mới dừng
- **Log:** hơn 3 phút không đặt được khối nào (client và server lệch hẳn). Người dùng tự thoát ra vào lại thì đặt được ngay. Watchdog 3 phút của 3.0.105 không kịp vì đồng hồ còn tính cả lúc đập khối sai.
- **Sửa:** đếm lần đặt hỏng liên tiếp (không hiện, bị trả về, nhầm loại, đổ nước không ngập); đủ 25 → `Reconnector`. Biến `wanted` = ý người dùng: chỉ K / `stop` / "Dừng, giữ tiến độ" mới dừng hẳn, mọi lần tự tạm dừng đều có `autoResumeTick()` xử lý (chết → hồi sinh + `/home`; máu thấp → chờ ≤ 90 giây; mở chat/ESC → chờ đóng; menu lạ 20 giây → đóng; xây xong → 10 phút kiểm tra lại…). Tắt "dừng khi mất focus" (F3+P).

## 3.0.107 — 29/09/2026 · "đã chọn placement mà cứ báo chưa chọn"
- **Nguyên nhân:** mỗi lần K mod đọc lại bản vẽ khi lựa chọn trong Litematica khác placement đang xây; bấm vào placement đang chọn là *bỏ chọn* → lỗi → dừng, dù bản vẽ vẫn còn.
- **Sửa:** mất lựa chọn / placement bị tắt → tự chọn lại, bật lại đúng placement cũ (`keepPlanSelectionLost`). Tầng "đào không được" được lưu vào file done, không xoá mỗi lần K.

## 3.0.106 — 29/09/2026 · 3.0.105 đặt nhầm nhiều
- **Nguyên nhân:** 3.0.105 xin server đồng bộ túi đồ sau *mỗi* lần đóng `/shop`. Gói trả lời về muộn, ghi đè lên lần đổi đồ mới → client và server cầm khác nhau → đặt nhầm → lại xin đồng bộ → vòng lặp.
- **Sửa:** chỉ xin khi có bằng chứng lệch; chỉ gửi khi không còn lần đổi đồ nào chờ server; khoá đặt tới khi gói trả lời về thật.

## 3.0.105 — 29/09/2026 · kẹt 6 giờ ở một tầng
1. **Đồ trên tay lệch với server** sau khi mở `/shop` → mọi cú đặt bị trả về, client không tự sửa. Thêm `InventoryAccess.forceResync()` (gửi 1 cú bấm cố ý sai phiên bản để server gửi lại cả túi).
2. **Theo đề xuất người dùng:** 3 phút không có tiến triển thật (server xác nhận khối) → thoát ra vào lại (`stuckWatch`); lần sau chờ 3 → 5 → 10 phút.
3. Mất kết nối → tự vào lại.
4. **Server giấu khối ở xa (anti-xray):** sau mỗi lần vào lại, cả chục tầng đã xây đọc ra trống → mod quay về làm lại. Sửa: chỉ tin khối trong 12 ô quanh mắt; ô đã xác nhận được lưu ra `donut-autobuild-done.dat`.
5. `/sell` 265 lần gửi chỉ 7 lần lên menu (đều lúc đứng yên) → đứng yên 8 tick trước `/sell` và `/home`.
6. Sửa báo nhầm "ender chest biến mất"; khối kê không đập được không còn lặp mỗi 30 giây.

## 3.0.104 — 28/09/2026 · an toàn TNT
- **Yêu cầu:** mọi việc gần TNT phải xét có làm TNT nổ không.
- **Sửa:** lớp `TntGuard`: quét TNT + cảm biến sculk quanh người; xét đặt/đập/chỉnh khối, nguồn điện, dây đỏ (lan xung tới 800 ô), observer, piston, lửa/lava, tiếng động gần sculk. Nguy hiểm → hoãn 30/60/90 giây, lần 4 để lại cho người làm tay + cảnh báo chat. TNT cạnh mạch đỏ được để cuối tầng. 21 tình huống giả lập đều đúng.

## 3.0.103 — 28/09/2026 · nhảy kiểu đơn giản
- **Lỗi:** 3.0.101–102 lùi lấy đà lặp 22 lần ở một khe.
- **Sửa (theo người dùng):** chỉ chọn chạy nhanh hay đi thường; vào vùng nhảy là nhảy nếu mô phỏng thấy chạm được khối đích; trên không tới nơi thì thả hết phím. Mô phỏng 4000 lần: hỏng 0, lùi nhiều nhất 1 lần.

## 3.0.102 — 28/09/2026 · lùi lấy đà rơi khỏi khối
- **Nguyên nhân:** vào ô nhảy từ góc, lệch ngang 0,44; mặt quay về tâm ô đích (chéo) nên lùi chéo → rơi.
- **Sửa:** mặt khoá theo trục nhảy, A/D kéo về giữa làn; lùi chỉ trong ô đang đứng; mô phỏng cú nhảy vanilla mỗi tick; trên không tự chỉnh W/thả/S; chạm đất thì hãm.

## 3.0.101 — 28/09/2026 · nhảy hụt; `/home` khi khối chưa được xác nhận
- Nhảy khi đứng yên / lệch / xa mép → rơi. Sửa: chỉ nhảy khi thẳng trục, có đà, điểm chạm nằm trong khối đích.
- Vừa đặt khối (server lag, chưa hiện) → mod tưởng chưa xây → `/home` → phải đi lại. Sửa: `placeUnconfirmed()` chặn `/home` khi còn khối chờ xác nhận.

## 3.0.100 — 28/09/2026 · nước cuốn dây; vòng lặp `/home` 10 giây
- Đổ nước sau 5 giây chờ dù chưa có gì chặn → nước chảy xuống tầng dưới cuốn mất tripwire. Sửa: mô phỏng dòng chảy vanilla, chỉ đổ khi nước bị chặn đúng như bản vẽ; nước sai chỗ thì múc lại.
- `navBestDist` nhớ qua các lần `/home` → watchdog huỷ → `/home` lại. Sửa: xoá sau mỗi lần `/home`.
- Xây theo cụm: chọn chỗ đứng với tới nhiều khối nhất. Nhảy xa khi chạy nhanh (khe 1–3 ô).

## 3.0.99 — 28/09/2026 · không khối nào bị chặn vĩnh viễn
- 14 piston "không xây được" 2 giờ: lag làm mất 2 lần → cấm góc gửi riêng *vĩnh viễn*. Sửa: cấm 1 phút, không cấm khi góc gửi riêng vẫn đặt được khối khác.
- `/home` liên tục ~10 giây/lần: khối ở xa không có đường trên mặt tầng → đi xuống → `/home` → chọn lại đúng khối đó. Sửa: cùng khối `/home` 2 lần → hoãn 2 phút.
- Nhảy lên/xuống 1 ô qua khe; xây theo cụm; tab Home trong GUI; đặt bảng xong tự đóng màn sửa chữ.

## 3.0.98 — 28/09/2026 · không dừng vì "khối sai"; giữ bản vẽ khi vào lại
- Sau mỗi lần vào lại server, Litematica nạp lại placement (đối tượng mới) → mod đọc lại từ placement đang chọn (có thể khác chỗ đang xây) → "64 khối SAI" → dừng. Sửa: `keepPlanAfterRejoin()` giữ bản vẽ, tìm lại đúng placement; khối sai hàng loạt thì so lệch ±3 ô để tự dời placement, không dừng.
- Nước vô hạn (đổ ít ô nhất), đếm tiền (`MoneyTracker`), thoát ghost block, crafter không mở được thì thử lại sau, `/sell` chờ menu 8 giây.

## 3.0.97 — 25/09/2026 · ender chest
- Mỗi món thiếu mở ender chest một lần. Sửa: lập danh sách thiếu của tầng, mở 1 lần lấy hết.
- Đập ender chest lúc server lag (chưa phá thật). Sửa: chờ xác nhận rồi nhìn lại ô.
- Quay về tầng dưới sửa vài khối lại cất hết đồ. Sửa: chỉ cất khi lên tầng cao nhất mới.

## 3.0.96 — 25/09/2026 · ender chest không bao giờ được đặt; xây nhanh hơn
- `stashSpot()` đòi vùng 3×4×3 không có khối redstone → trên farm không bao giờ có chỗ. Sửa: chỉ tránh observer nhìn vào ô và piston/dispenser sát ô.
- Bỏ các khoảng chờ theo ping sau mỗi khối (nghỉ 9 → 4 tick; đổi ô thanh nóng không chờ).

## 3.0.95 — 25/09/2026 · 9 yêu cầu của người dùng
Fence/tường không ngồi chìa mép (va chạm cao 1,5 ô); mua gỗ spruce; kho ender chest; đồ dính con trỏ không làm dừng; thoát tuyết bột; chờ giá Quick Buy; chỉ thoát ra vào lại khi `/home` hụt; crafter nhanh hơn; `/sell` xô rỗng và đồ thừa.

## 3.0.94 — 25/09/2026
- Mua dư xô lava: đếm ô lava của mọi tầng. Sửa: chỉ đếm cùng tầng, cùng bước đổ.
- Xoá home không bao giờ xong: bấm xác nhận lần 1 xong mod đóng menu. Sửa: giữ menu, bấm lần 2, chờ chat "Home deleted".
- `/shop` bấm Auction liên tục. Viết lại `ShopBrowser` theo hướng dẫn: Quick Buy → ô Empty → Search → ghi tên → chọn → Amount.

## 3.0.93 — 25/09/2026
Gửi `/shop`, `/order`, `/home` 5 lần liền không có menu = server lag → thoát ra vào lại (`GuiWatch`, `Reconnector`). Sau `/sethome` không mở `/home` kiểm tra lại.

## 3.0.92 — 25/09/2026
Chỉ nhảy khi đang sprint thật; `/home` cả ở đường tìm chỗ đứng thường; comparator không có góc ở chỗ đứng cũ → cấm chỗ đứng đó.

## 3.0.91 — 25/09/2026
Order không có hàng → mua ở `/shop` (trần 500K, không đọc được giá thì không mua). Menu Order còn sót không làm dừng nữa.

## 3.0.90 — 25/09/2026
Chỉnh crafter theo bản vẽ (tab Crafter). Order lấy Glass thay Stained Glass ở chế độ nhanh. Observer kích hoạt piston đặt theo từng tầng (trước giữ tới cuối công trình → "tầng 2 thiếu 2 observer").

## 3.0.89 — 25/09/2026
"Chỉ còn block bị chặn" lặp mãi: ô mod không tự đặt (nước chảy, khối không có vật phẩm) làm `matched < total` mãi. Sửa: coi như xong khi chỉ còn ô loại đó, có báo rõ loại ô.

## 3.0.88 — 25/09/2026
Tính năng `/sethome` + `/home N` để lên lại tầng đang xây khi ngã. Mod chỉ xoá home do mod tạo.

## 3.0.87 — 25/09/2026
Bậc trèo ra khỏi bể đặt ngay dưới khối trên đầu → không trèo lên được. Sửa: tìm cặp chỗ đứng/bậc có trần trống. Bù dòng nước đẩy (`flowAt`, quay chếch ngược dòng).

## 3.0.86 — 25/09/2026
Hết Obsidian mà không đi lấy (ô thiếu vật liệu bị loại im lặng khỏi hàng đợi). Sửa: `materialRestock()` mỗi 2 giây. Khối đặt được ngay làm trước khối lơ lửng.

## 3.0.85 — 25/09/2026
Rơi xuống bể nước kín đứng mãi. Sửa: nhận ra bị nhốt (vùng < 400 ô); trong nước thì đặt bậc kê vào ô nước bên cạnh rồi trèo lên.

## 3.0.84 — 25/09/2026 · kẹt 9 giờ trong dòng nước
`Navigation.hazard()` coi mọi ô có nước là nguy hiểm → ô đang đứng "không đứng được, 0 lối ra". Sửa: nước nông đi được (chi phí cao hơn), sàn ngập nước đứng được. Kèm: bỏ qua khối có giới hạn (không bỏ cả tầng), hàng không còn lặp vô hạn, đổ nước không lặp chỗ đứng cũ, xây chuỗi khối thật trước khi bắc cầu tạm.

## 3.0.83 — 25/09/2026
Hết khối kê đứng 17 phút → `scaffoldRestock()` lấy từ Order. Mua xô nhiều cái trong 1 lần mở `/shop`.

## 3.0.82 — 24/09/2026
Vòng lặp dọn khối kê rồi bỏ cả tầng (cầu tạm bắc sai phía của hopper). Nhảy khe rồi quay đầu. Cứ đi lại ngồi. Watchdog huỷ khi đi xa. Đặt "tiện đường" khối trong tầm với.

## 3.0.81 — 24/09/2026
Không leo ra khỏi hố; bị giật về liên tục; bò trên băng; đi ngồi trên khối thường; dừng khi còn khối bỏ qua (giờ làm lại tầng, 3 vòng thì để cuối).

## 3.0.80 — 24/09/2026
Đặt hụt thì nhảy sang khối khác: chờ server xác nhận chính khối đó; hụt lần 2 thì đổi chỗ đứng.

## 3.0.79 — 24/09/2026
Chạy ra chỗ bị đá chặn để đào thay vì xây chỗ trống trước. Sửa: tách "khối chặn tự nhiên" khỏi "khối mình đặt sai", chỉ đào khi tầng không còn ô đặt được.

## 3.0.78 — 24/09/2026
Tầng lá lơ lửng: cầu tạm thấp, nhảy xuống sàn, kẹt trên lá, bỏ dở lá thiếu nước. Thêm bắc cầu ngang đúng tầng (`planeBuild`).

---

## 3.0.25 → 3.0.34 — 21–22/09/2026

| Bản | Lỗi → sửa |
|---|---|
| 3.0.34 | **Gốc rễ:** chế độ Nhanh coi lá chưa có nước là đã xong (`swappable()` "cùng Block → true" bỏ qua property) → bước đổ nước không chạy, rồi đệ quy 40 lần/tick. Sửa: nhánh Nhanh chỉ áp khi hai khối khác Block và ô không còn nhận thêm việc. |
| 3.0.32–33 | Các đường làm rơi mục tiêu đổ nước. Xô nước phải qua `clickUse` (gọi thẳng `interactBlock` không đổ nước). |
| 3.0.31 | Nhảy kê cột không đặt được khối → nhìn thẳng xuống 90°, click mặt trên khối dưới chân. |
| 3.0.30 | Dựng tháp tạm giữa sa mạc rồi kẹt → chỉ dựng cột chạm công trình; cho rơi tới 6 ô khi kẹt. |
| 3.0.29 | Chốt chặn khiến không bao giờ ngồi chìa mép (chỉ hỏi góc thật) → hỏi thêm góc gửi riêng. |
| 3.0.28 | Bắc cầu 23 khối trong khi chỉ cần ngồi sát mép → thử tổ hợp "chìa mép + góc gửi riêng"; trần `maxBridgeBlocks` = 8. |
| 3.0.27 | Góc gửi riêng đặt ra sai hướng: `predictsFor()` kiểm tra lịch sử yaw thay vì yaw sẽ gửi. Chuyển tầng trước khi server xác nhận. |
| 3.0.26 | "Đặt được = true" mà không bao giờ đặt được: observer chỉ có hàng xóm phía bắc không thể ra `facing=south` bằng góc thật. Thêm `faces()`, bật góc gửi riêng mặc định AUTO. |
| 3.0.25 | Vài khối kẹt làm dừng cả phiên → bỏ qua tạm, làm lại tầng sau. |

## 3.0.18 — 21/09/2026 · tắt/mở Order vô tận
`depMissing` chỉ được xoá trong `nextTarget()` nhưng luồng sau Order `return` trước khi tới đó. Sửa: lọc món đã có, xoá trước khi `collect()`, mỗi món chờ 400 tick mới Order lại.

## 3.0.17 — 20/09/2026 · kẹt tầng đầu vì "khối phụ thuộc"
Hopper/trapdoor cần bám vào dropper/observer chưa có trong túi, nhưng tầng vẫn có khối đặt được nên không đi Order. Sửa: ghi khối phụ thuộc đang thiếu rồi Order đúng món.
