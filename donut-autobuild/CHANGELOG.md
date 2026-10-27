# CHANGELOG — Donut AutoBuild

Mỗi mục ghi: **lỗi / nhu cầu → nguyên nhân trong logic → cách sửa → đã khắc phục được gì**. Ngày ở tiêu đề = ngày jar được chép vào thư mục phát hành trên máy (giờ Việt Nam). Giờ trong nội dung lấy theo log người dùng gửi.

Toạ độ công trình đã được bỏ khỏi mục này (chỉ giữ Y khi cần). Nhật ký đầy đủ hơn (log từng phút, số dòng log) nằm ở tài liệu dự án, không đưa lên repo.

Các bản không có mục riêng: 2.0.0–2.9.9 (16–18/09), 3.0.0–3.0.16 (18–20/09), 3.0.19–3.0.24 (21/09), 3.0.35–3.0.77 (22–24/09). Không có ghi chép đi kèm ở thời điểm đưa mã lên; thay đổi của chúng đã nằm trong mã hiện tại. 3.0.106 không có jar trong thư mục phát hành.

---

## 3.0.128 — 27/10/2026 · hết mua dồn cúp, công tắc cho đồ vào hopper, hết kẹt 4 giờ giữa 2 hopper, ghi nguyên nhân chết

Log 3.0.127 ngày 27/10 (02:15–09:02).

### 1. Mua quá nhiều cúp netherite
- **Triệu chứng:** 02:34–02:57 mua 15 cúp, mỗi cái ~8M. Trong túi đã có cúp.
- **Nguyên nhân:**
  - Trong 23 phút nhân vật chết 23 lần, đồ rơi tại chỗ chết.
  - Hồi sinh xong, mod `/home` về rồi thấy túi không có cúp, mua ngay ở `/shop`.
  - Sau đó mod đi qua chỗ chết, nhặt lại cả cúp cũ. Cúp cứ thế dồn lại trong túi.
- **Sửa (`SurvivalSupplies`, `BuildController.pickaxeComingBack`):**
  - Chưa mua cúp khi: vừa chết dưới 30 giây, túi đồ chưa được server xác nhận, hoặc có cúp rơi trên đất trong 32 ô.
  - Cúp ở tay trái hoặc đang nằm trên con trỏ cũng tính là có cúp.
  - Mua xong 1 cúp thì 5 phút sau mới được mua cái nữa. Trong 1 giờ mua tối đa 3 cái; quá thì ghi cảnh báo và thôi mua.

### 2. Công tắc "Đồ trong hopper" / "Đồ trong rương/dropper…" (tab Cài đặt)
- **Yêu cầu:** nhiều farm không cần hopper có đúng đồ như bản vẽ.
- **Sửa:** 2 công tắc mới (`fillHoppers`, `fillContainers`, mặc định BẬT).
  - Tắt "Đồ trong hopper": mod vẫn đặt hopper đúng hướng, nhưng không mở ra cho đồ, không chờ hopper đủ đồ mới lên tầng, không đập khối che để mở hopper.
  - Tắt "Đồ trong rương/dropper…": như trên cho rương, thùng, dropper, dispenser, lò, giá pha chế.
  - Đồ chỉ dùng cho loại khối đang tắt thì không còn được giữ lại khỏi `/sell` / ender chest.

### 3. Kẹt 4 giờ 45 phút giữa 2 hopper
- **Triệu chứng:** 03:50–08:36 chỉ có 2 dòng lặp: hopper A "30 giây chưa cho đồ xong", sang hopper B, lại "30 giây chưa cho đồ xong", rồi quay lại A. 569 lần.
- **Nguyên nhân:**
  1. Hopper nối vào máy, chuyển đồ đi liên tục. Mod cho Cobblestone vào mãi mà không bao giờ đủ 320, hết 30 giây thì bỏ. Vừa kẹt vừa tốn Cobblestone (91 lần "Thiếu Cobblestone để cho vào khối chứa", có lúc mua ở shop).
  2. Giới hạn 8 lần thử nằm trong `fillQueue`. Khối bị bỏ khỏi hàng thì `fillGate` lại thêm vào với số đếm 0, nên không bao giờ chạm giới hạn.
- **Sửa (`BuildController` + `ContainerFill.Job.moved`):**
  - Đã cho vào nhiều hơn bản vẽ cần 1 chồng (64) mà vẫn thiếu → khối chứa đang tự chuyển đồ đi → coi như xong, ghi nhớ, không cho thêm.
  - Đếm số lần hỏng riêng cho từng khối chứa, không mất khi khối bị bỏ khỏi hàng. Đủ 3 lần thì báo chat, không chặn lên tầng nữa, 5 phút sau thử lại khi đi ngang.
  - Dòng "30 giây chưa cho đồ xong" ghi kèm: menu có mở không, số lần bấm mở / bấm trong menu, số món đã cho vào, số tick ngắm trượt, có đang ngồi không.

### 4. Ghi nguyên nhân mất máu và chết
- **Triệu chứng:** log có 23 lần "Nhân vật đã chết", không dòng nào cho biết vì sao. Phần lớn chết 5–10 giây sau `/home 3` (Y=-49), hai lần ở cạnh hopper -44223, -46, -208500.
- **Sửa:** mỗi lần mất máu ghi 1 dòng `Mất máu 20.0 → 17.0 @ x, y, z: <nguyên nhân>` (cùng nguyên nhân thì 3 giây 1 dòng). Lúc chết ghi `Chết @ … : nguyên nhân cuối = …`.
  - Nguyên nhân lấy từ gói damage của server (`inWall` = ngạt trong khối, `lava`, `drown`, `mob` + tên quái, `fall`…), kèm cờ đầu kẹt trong khối / trong lava / đang cháy / hết dưỡng khí.
  - Gửi log lần sau sẽ biết chỗ home hay chỗ hopper đang giết nhân vật.

### 5. Log gọn hơn
- "nằm trước mặt piston → nhiều khả năng bị piston đẩy đi" (7194 dòng) chỉ ghi kèm 5 dòng đầu mỗi 10 giây, giống "từng xong nhưng giờ chưa đúng".
- "… cần … → đưa vào hàng sửa" (~1300 dòng): 5 dòng đầu mỗi 10 giây + số tổng.
- "Đã /home 3 về tầng Y=null" → ghi Y đang đứng khi chưa chọn tầng.

### Build
`./gradlew build` (Loom 1.17.7) thành công. Chưa chạy thử trong game.

---

## 3.0.127 — 26/10/2026 · bấm Confirm khi mua, crafter nhanh hơn, chạy cả đêm, đập khối kê ngay, dùng góc gửi riêng nhiều hơn

### 1. Mua ở /shop mà không bấm Confirm
- **Triệu chứng (log 3.0.126, 01:30:15):** bấm mua Crafter thì hiện menu "Confirm Quick Buy". Mod không bấm gì, 5 giây sau báo "không nhận được hàng (hết tiền?)". Lặp 7 lần.
- **Nguyên nhân:** tên menu có chữ "Quick Buy", nên mod tưởng đó là trang Quick Buy chính.
- **Sửa:** menu nào có chữ "Confirm" / "xác nhận" thì mod luôn bấm nút Confirm (tên Confirm/Accept/Yes, hoặc kính/len xanh lá; không bao giờ bấm Cancel). Bấm xong chờ hàng về rồi mua tiếp tới khi đủ. Mỗi lần mua mới thì đếm lại số lần bấm Confirm từ đầu.

### 2. Mở crafter rồi đứng im 12 giây; ender chest và khối chứa cũng không mở được
- **Triệu chứng:**
  - 6 lần "Crafter …: 12 giây chưa chỉnh xong".
  - "không mở được ender chest" (01:33:27).
- **Nguyên nhân:** đầu mỗi tick, mod tự giữ phím ngồi khi đang chìa người ra mép. Bước mở crafter / ender chest / khối chứa có ra lệnh "đứng thẳng", nhưng lệnh này bị đè. Nhân vật vẫn ngồi, nên mod không bao giờ bấm mở.
- **Sửa:**
  - Đứng yên trên đất thì đứng thẳng hẳn để bấm mở. Không bấm phím đi nên không trượt khỏi mép.
  - Xong việc thì ngồi lại nếu đang chìa ra mép.
  - Thêm dòng log lý do khi 2 giây chưa bấm mở được.

### 3. Crafter nhanh hơn
- Crafter đặt trên đường đi ("Xây gần trước") được chỉnh ngay, không đi tiếp hay đặt crafter khác rồi mới quay lại.
- Menu vừa lên là bấm ngay, mỗi tick 1 ô (bản cũ 2 tick/ô). Gửi hết rồi kiểm tra 1 lần sau 1 lượt ping; ô nào sai thì bấm lại đúng ô đó ngay.
- Đóng crafter không còn xin server gửi lại túi đồ, vì crafter không đổi đồ trong túi. Bản cũ chờ thêm ~0,4 giây sau mỗi crafter.
- Ngắm không trúng thì 1,5 giây (bản cũ 3 giây) bỏ để đổi chỗ đứng. Crafter chưa chỉnh được thì 5 giây sau làm lại (bản cũ 20 giây).

### 4. Ender chest đập không được thì lặp mãi tại chỗ
- **Triệu chứng:** 01:33:39–01:34:23, 4 lần × 12 giây liền "chưa đập được".
- **Sửa:**
  - Bước ender chest / khối chứa / crafter không còn tính là "bận menu" khi không có menu nào mở, kể cả lúc đang giữa việc mua đồ.
  - Đập hỏng thì xây tiếp, 15 giây sau mới quay lại.
  - Thêm log chi tiết lý do chưa vỡ.

### 5. Chạy cả đêm, không dừng chờ nhấn K
- Thiếu đồ khi đang tắt "lấy từ Order": báo chat, xây phần khác, 60 giây sau kiểm tra lại.
- Túi đầy lúc cần lấy đồ hoặc mua xô: báo chat (5 phút/lần), xây phần khác hoặc chờ, rồi tự dọn túi và thử lại.
- Chết thì hồi sinh, có home của mod thì /home về rồi xây tiếp (như trước). Chưa có home mà hồi sinh xa chỗ xây: báo chat, đứng chờ ở chỗ hồi sinh, về gần công trình là tự xây tiếp.
- Mất kết nối thì tự vào lại; tiếp tế cúp và đồ ăn vẫn tự mua như trước. Chỉ K mới dừng hẳn.

### 6. Khối kê tạm để lại
- **Bắc cầu:** vừa bước hẳn sang khối mới là quay lại đập khối phía sau. Bản cũ đợi cách 1,5 ô nên để lại cả dãy.
- **Khối kê đứng trong tầm mà người còn chạm nó:** nhích vào giữa ô rồi đập. Đổi chỗ đứng tối đa 4 lần (bản cũ 2 lần) mới để tới lúc thu dọn cuối.

### 7. Góc gửi riêng (góc nhìn giả) dùng cho mọi khối có hướng
- **Triệu chứng:** log 3.0.126 có 38 lần "trong tầm nhưng sai hướng", 0 lần dùng góc gửi riêng. Mod đi vòng / bắc cầu tìm chỗ đứng có góc thật.
- **Nguyên nhân:** góc gửi riêng chỉ bật cho khối phải nhìn lên mới đặt được.
- **Sửa:**
  - Observer, piston, hopper… và mọi khối có hướng đều được dùng góc gửi riêng khi từ chỗ đứng không có góc thật. Góc thật vẫn ưu tiên trước.
  - Tìm chỗ đứng và "Xây gần trước" cũng tính các chỗ đặt được bằng góc gửi riêng.
  - Cài đặt `rotationSpoof` (OFF/AUTO/ON) vẫn như cũ.

### 8. Log
- "từng xong nhưng giờ chưa đúng/trống" chỉ ghi 5 dòng đầu mỗi 10 giây kèm số tổng. Log 3.0.126 có 2233 dòng liền.

---

## 3.0.126 — 26/10/2026 · ender chest đặt xong đứng chờ, không đập thu về

### 1. Đặt ender chest xong đứng chờ rất lâu rồi bỏ lại, không đập
- **Triệu chứng (log 3.0.125):** "Ender chest @ …: 12 giây chưa đập được → để lại", rồi "còn nằm lại từ lần trước → đập thu về" lặp mãi (01:07:26–01:08:08, 01:18:18–01:19:12).
- **Nguyên nhân:** từ 3.0.124, trong lúc làm việc với menu server mod không bấm túi đồ và không xin server gửi lại túi đồ. Nhưng bản 3.0.124 tính cả **bước ender chest** là "đang dùng menu", kể cả khi menu ender chest đã đóng.
  - Lúc đập, mod đổi cúp lên tay (SWAP). Sau khi đổi đồ, mod phải chờ server xác nhận túi đồ rồi mới đập.
  - Yêu cầu xác nhận đó bị chặn, nên mod đứng chờ đủ 12 giây rồi bỏ đi, để ender chest nằm lại.
  - Khối chứa và crafter cũng bị cùng lỗi này.
- **Sửa:** bước ender chest, khối chứa và crafter chỉ tính là "đang dùng menu" khi menu thật sự đang mở. Shop, Order, home, bán đồ và tiếp tế vẫn được chặn suốt cả lượt như 3.0.124, vì đây là chỗ đã làm shop bị đóng.

### 2. Ender chest bị đặt vào ô bản vẽ có khối
- **Triệu chứng (log 01:18:23):** ender chest đặt vào chỗ Observer của bản vẽ, nên bị đưa vào hàng sửa.
- **Sửa:** khi chọn chỗ đặt ender chest, ô có khối trong bản vẽ bị trừ điểm nặng. Mod chỉ chọn ô đó khi xung quanh không còn ô trống nào khác.

---

## 3.0.125 — 26/10/2026 · mua xô ngay khi chưa có, shop mua tới khi đủ, khối chứa bị che kín, home mất, ender chest

### 1. Chờ đổ nước mãi mà trong túi không có xô nước (log 3.0.124, 20:48:02–20:48:35)
- **Nguyên nhân:** mod đòi đứng đúng chỗ và ngắm trúng mặt đổ rồi mới mua xô. Tâm ngắm cứ trúng khối bên cạnh (Smooth Stone Slab Y=-53), nên mod "đi lại gần hơn", rồi lại chọn đúng chỗ đứng cũ. Vòng lặp này không bao giờ tới bước mua.
- **Sửa (người dùng chọn):** trong túi chưa có xô thì mod đứng yên mua ngay tại chỗ ở /shop, có xô rồi mới đi tới ngắm đổ.

### 2. /shop chỉ mua 6 lần mỗi lần mở
- **Nguyên nhân:** mỗi lần mở shop mod chỉ bấm mua tối đa 6 lần. Log: cần 476 Bone Meal, mua 384 rồi dừng, thiếu 92.
- **Sửa:** mod bấm mua tới khi đủ. Chỉ dừng khi đủ, khi túi đầy, khi giá vượt trần, hoặc khi bấm mua mà không nhận được hàng (hết tiền). 64 lần là mức chặn an toàn.
- Khối chứa còn thiếu đồ thường: nếu lần lấy trước có thêm được đồ thì mod lấy tiếp ngay, không chờ 60 giây.

### 3. Khối chứa bị che kín, đi xuống tầng dưới rồi bị rơi
- **Nguyên nhân 1:** client không biết bên trong khối chứa có gì nếu chưa mở. Vì vậy mọi khối chứa đặt từ phiên trước đều bị coi là chưa đủ đồ, kể cả khối đã cho đủ rồi.
  - **Sửa:** mod lưu lại khối chứa nào đã cho đủ đồ (trong cấu hình, theo placement + gốc + toạ độ). Vào lại game thì mod không mở lại các khối đó.
  - Khi mod đặt lại một khối chứa, khối đó bị xoá khỏi danh sách đã cho đủ.
- **Nguyên nhân 2:** cột khối chứa 217848, Y -57→-54 đã bị bao kín, không có mặt nào để mở.
  - **Sửa (người dùng chọn):** khi khối chứa không còn mặt nào hở, hoặc đi tới 3 lần vẫn không mở được, mod đập 1 khối đang che.
  - Ưu tiên khối thường, khối ở phía trên, khối có mặt lộ ra ngoài.
  - Observer, piston, repeater, comparator, dây redstone, khối có block entity: chỉ đập khi không còn khối nào khác.
  - Không bao giờ đập khối chứa khác có đồ trong bản vẽ (đồ bên trong sẽ rơi ra).
  - Khối vừa đập chỉ được đặt lại sau khi khối chứa đã cho đủ đồ, hoặc mod bỏ qua khối chứa đó (tối đa 3 phút).
  - Thử 3 khối che vẫn không được thì mod báo chat rồi xây tiếp.
- Mod vẫn quay xuống mọi tầng dưới để cho đủ đồ rồi mới lên tầng (người dùng chọn).

### 4. "/home 3" → "Home does not exist"
- **Nguyên nhân:** mod nhớ từ phiên trước là đã đặt home 3, nhưng home đó không còn trên server. Mod không đọc thông báo này, nên đứng chờ.
- **Sửa:**
  - Thấy thông báo "does not exist" thì mod dừng chờ ngay.
  - Mod quên home cũ và tự tìm đường lên.
  - Tới chỗ an toàn ở tầng đang xây thì mod /sethome lại đúng số home đã chọn ở tab Cài đặt.

### 5. Hopper hướng vào rương vẫn chưa đặt được
- Chưa rõ nguyên nhân từ log (không có dòng nào cho thấy mod đã đứng trên nóc rương).
- Đã thêm log `[KÊ TẠM]`: mỗi lần không kê khối tạm, ghi rõ lý do và mod đang đứng trên khối gì.
- Ô trên khối thấp có "khối không khí" trong bản vẽ thì vẫn được kê tạm.

### 6. Đặt ender chest: "không ngắm được mặt trên khối"
- **Nguyên nhân:** mod ngồi xuống để đặt ender chest, nhưng lúc chọn ô lại kiểm tra tia ngắm từ mắt khi đứng. Ngồi xuống thì mắt thấp hơn, nên tia trúng cạnh khối khác.
- **Sửa:**
  - Mod kiểm tra tia ngắm từ mắt lúc ngồi.
  - Ngắm 4 giây không được thì mod thử ngay ô khác quanh chỗ đứng (tối đa 3 ô), không bỏ qua 30 giây.

---

## 3.0.124 — 26/10/2026 · đặt hopper từ nóc rương, shop luôn mở khi Order trống, cho đủ đồ khối chứa mới lên tầng, shift-click, tab Crafter

### 1. Kẹt đặt hopper hướng vào rương (log 3.0.123)
- **Nguyên nhân:** hopper phải quay vào mặt tây của rương đôi bên cạnh, tức là phải bấm vào mặt bên của rương. Dưới hopper là không khí, nên chỗ đứng duy nhất là nóc rương. Rương chỉ cao 0,875 khối: muốn bấm được mặt bên thì phải nghiêng ra mép, mà nghiêng ra mép thì người lấn vào ô của hopper, nên game không cho đặt. Người thật cũng không đặt được từ chỗ đó.
- **Sửa (`liftOverLowFloor`):** đang đứng trên khối thấp hơn 1 (rương, nửa sàn dưới…) mà ô cần đặt cao hơn chân thì:
  - kê tạm 1 khối kê lên nóc khối thấp (ngồi để rương không mở);
  - đứng lên khối kê, nghiêng ra mép tối đa như người chơi rồi đặt;
  - đặt xong thì đập khối kê.
  Mỗi ô chỉ thử cách này 1 lần mỗi tầng.

### 2. Order trống nhưng không mở shop
- **Nguyên nhân 1:** sau 1 lần thử /shop, mod nghỉ 3 phút mới thử shop lại cho cùng món. Trong 3 phút đó Order trống thì mod chỉ chờ.
  - **Sửa:** bỏ thời gian nghỉ. Lần nào Order trống cũng mở /shop.
- **Nguyên nhân 2:** tìm "bone meal" bằng bảng chữ thì shop quay về Quick Buy. Lúc đóng bảng chữ, mod gửi gói "xin gửi lại túi đồ" và gói "đóng túi". Server đóng **mọi** menu đang mở khi nhận gói đóng túi, nên shop cũng bị đóng.
  - **Sửa:** khi đang làm việc với menu server (shop, Order, home, bán, ender chest, khối chứa, tiếp tế), mod không bấm túi đồ và không gửi gói đóng túi. Các việc đó để dành tới lúc xong menu.
- Xô nước, xô lava và xô tuyết bột mặc định mua thẳng ở /shop, không qua Order. Cấu hình cũ được bật lại khi chạy bản này lần đầu.

### 3. Dropper chưa có nước mà vẫn báo xong và lên tầng
- **Nguyên nhân:** việc cho đồ vào khối chứa chỉ chạy khi khối chứa ở gần. Tầng xong là mod lên tầng mới, bỏ lại khối chứa còn thiếu.
- **Sửa:** trước khi lên tầng mới, mod tìm các khối chứa ở tầng dưới còn thiếu đồ thường, rồi đi tới cho đủ. Đồ còn thiếu thì mod đi lấy (ender chest → Order → /shop) và chờ có đồ.
  - Chỉ được lên tầng khi đồ còn thiếu là đồ có tên riêng mà trong túi không có. Khi có đồ đúng tên thì mod vẫn tự cho vào.
  - Không tới được khối chứa sau 6 lần thử thì báo chat, rồi xây tiếp.

### 4. Cho đồ vào khối chứa nhanh hơn
- Chồng nào cho trọn được (số lượng ≤ số còn thiếu, khối chứa còn đủ chỗ) thì dùng shift + chuột trái cả chồng.
- Phần lẻ thì cầm lên rồi bấm chuột phải từng chiếc, đúng số lượng.
- Shift-click chỉ dùng cho rương, thùng, hopper, dropper, dispenser. Lò và giá pha thuốc tự xếp ô theo loại đồ, nên vẫn bấm từng ô.
- Kiểm thử: rương cần 138 cobble, trong túi có 64 + 64 + 40. Mod dùng 2 shift-click, cầm 1 lần, 10 chuột phải, trả 1 lần (14 cú bấm). Kết quả: rương đủ 138, trong túi còn 30.

### 5. Tab Crafter trong menu U
- Bật/tắt "Tự chỉnh ô crafter khi đặt".
- Danh sách tầng có crafter: số crafter, số kiểu trong bản vẽ, tầng nào đang chỉnh tay. Bấm vào tầng để chọn.
- Lưới 3×3 của tầng đang chọn: bấm từng ô để tắt hoặc mở, áp dụng cho mọi crafter của tầng.
- Nút "Theo bản vẽ": bỏ chỉnh tay, mỗi crafter lấy đúng kiểu của nó trong bản vẽ.

---

## 3.0.123 — 26/10/2026 · hết "túi đồ mở" khi đi/đặt/đập, cho đồ vào khối chứa, ender chest 1 lần, chọn home cho mod

### 1. Vẫn bị giật nhiều (cả khi chỉ đi trên sàn phẳng, ngay lúc bắt đầu)
- **Nguyên nhân (log 3.0.122):** khi đổi đồ, người thật mở túi (E), bấm ô, rồi đóng túi; lúc đóng, client gửi gói "đóng túi" lên server. Mod bấm thẳng vào ô túi đồ (SWAP đồ lên tay, xin server gửi lại túi đồ, gộp xô, cất con trỏ) nhưng không bao giờ gửi gói đóng túi. Anti-cheat coi túi đồ đang mở, nên:
  - di chuyển → bị kéo lùi;
  - đặt khối → bị huỷ ("đặt rồi nhưng không hiện");
  - đập khối → bị huỷ ("client thấy vỡ nhưng server chưa phá").
  Log khớp: 19:29:51 xin gửi lại túi đồ → ngay sau đó đập ender chest không vỡ; 19:30:05 SWAP Stone → Stone "không hiện" → bị giật. 3.0.122 xin gửi lại túi đồ sau MỖI lần SWAP nên càng giật nhiều.
- **Sửa (`InventoryAccess`):**
  - Mọi cú bấm vào túi đồ chỉ làm khi đứng yên: trên đất, hết trượt, không chạy nhanh. Chưa yên thì mod dừng lại (không phanh giữa lúc nhảy).
  - Cuối tick gửi gói đóng túi (`CloseHandledScreenC2SPacket`), như khi người chơi đóng túi.
  - Dùng cả 9 ô thanh nóng (giữ 1 ô cúp). Đồ từ túi lên thanh nóng vào ô trống, rồi ô đang để totem, rồi ô lâu nhất chưa dùng. Vật liệu dùng nhiều nằm sẵn trên thanh nóng, đổi bằng phím số nên ít phải mở túi.
  - Tay trái luôn cầm totem; tay trái không có totem thì đưa 1 totem trong túi lên.
  - Log `[GIẬT]` mỗi lần bị kéo lùi: bấm túi đồ cách bao lâu, đặt khối cách bao lâu, chạy nhanh/ngồi, phím đang bấm, khối dưới chân đã được server xác nhận chưa.

### 2. Cho đồ vào khối chứa theo bản vẽ
- Áp dụng cho dropper, dispenser, hopper, rương (cả rương đôi), thùng, lò các loại, giá pha thuốc. Đồ đọc từ block entity trong file `.litematic` (`SchematicPlan.containerItems`).
- Đặt xong là mở ra cho đồ vào ngay. Khối có sẵn từ trước (vào lại server, xây tiếp) mà chưa đúng đồ cũng được làm khi đứng gần.
- Phải đúng loại và số lượng, ô nào cũng được (ưu tiên ô như bản vẽ). Đồ dư hoặc sai loại thì lấy ra. Đồ có tên riêng phải đúng tên.
- Thiếu đồ có tên riêng → báo chat, để trống, xây tiếp; lần sau trong túi có đồ đúng tên thì tự cho vào.
- Thiếu đồ thường → lấy như vật liệu (ender chest → Order → /shop) rồi quay lại.
- Đồ dùng cho khối chứa không bị `/sell` và không bị cất vào ender chest.

### 3. Ender chest đặt/mở 2 lần liền nhau
- Lần lấy đồ thiếu lúc vừa lên tầng mới giờ cất luôn đồ tầng này không dùng trong cùng lần mở. Không còn mở lần 2 ngay sau đó.

### 4. Chọn home cho mod (tab Cài đặt, menu U)
- **"Home cho mod: Không dùng / Home N"**, mặc định Không dùng. Lần đầu chạy 3.0.123, số home cũ mod tự chọn bị bỏ.
- **Không dùng:** mod không bao giờ /home hay /sethome. Kẹt ghost block thì thoát ra vào lại.
- **Home N:** home trống người chơi giao cho mod.
  - Từ tầng 3, mỗi lần lên tầng mới mod xoá home N cũ (/home → chọn home → xô lava → bấm xác nhận tới khi báo đã xoá) rồi /sethome ở tầng đang xây.
  - `/sethome` trơn tạo ở ô trống đầu tiên; nếu ô đó không phải N thì gửi `/sethome N` rồi mở danh sách kiểm tra.
  - Lỡ ngã hoặc kẹt thì /home N.
  - Các home khác không bao giờ bị đụng tới.

### Kiểm thử
- Bộ mới cho mục 2 (giả lập menu và cú bấm): đủ 3 xô nước + 10 mũi tên vào dispenser và lấy đất lạ ra; đồ tên riêng thiếu → báo, đồ thường vẫn cho vào, có đồ đúng tên thì cho tiếp; chồng 64 chỉ 1–3 cú bấm; gộp rương đôi. 37 phép, qua hết.
- Các bộ cũ: BuildQuality 171, HeldPlacement 101, MovementResume 275, MovementMining (đi, tìm đường, đào giữ chuột) 206, Run122 15 — qua. Các bộ còn lại dừng ở cùng chỗ như mã gốc 3.0.121.
- Chưa thử trên DonutSMP.

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
