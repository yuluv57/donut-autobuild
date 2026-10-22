# Lịch sử sửa — donut-automine

## 1.14.4

Theo 4 yêu cầu của người dùng (kèm ảnh HUD đứng ở "Đập lại khối giữa … lần 0 · đập"): đổi vị trí thì đổi cả điểm ngắm; không đứng im
ở trạng thái đập lại khối giữa; bị chặn thì đi vòng, không đi đi lại lại / đứng im; **không dừng vì bất cứ lý do gì** — không đào được thì
di chuyển rồi bắt đầu lại.

### 1. Đổi chỗ thì đổi cả điểm ngắm

- Mỗi lần đập lại khối giữa ngắm một điểm khác trên cùng khối (giữa mặt → 2 góc chéo → 2 góc kia → …; nhát 3x3 thì chỉ các điểm trên 2 mặt
  cùng trục với lát để vẫn phá đúng mặt phẳng 3x3, nhát lẻ thì mọi mặt). Ngắm 6 tick chưa trúng điểm nào thì đổi sang điểm khác.
- `StrikeTask` (vét sót, phá đường…) đập lại một khối đã hỏng / ngắm mãi không trúng cũng đổi điểm ngắm.

### 2. Không đứng im ở "Đập lại khối giữa … · đập"

- Nguyên nhân (dựng lại được trong gametest): khối giữa hiện lại mà từ chỗ đứng **không nhìn thấy** (bị khối giữa của lát gần hơn che, hay
  đang đứng trên mặt 3x3 của nó) → ngắm mãi không trúng, 3 giây sau bỏ, tick sau lại tạo đúng việc đó. Sỏi/cát rơi vào ("lần 0") còn
  không tính lần nên lặp vô hạn.
- Nay: khối gần người / nhìn thấy được xử lý trước; không nhìn thấy, đứng trên mặt 3x3 của nó, hay ngắm hết các điểm không trúng → **đi
  vòng** tới chỗ nhìn thấy rồi đập (tìm chỗ đứng quanh khối, không có thì đào đường tới sát khối). Lần nào cũng tính, kể cả sỏi rơi vào;
  thang lùi/ngang tính từ chỗ đứng lúc đập hỏng.

### 3. Bị chặn thì đi vòng

- Lát đang đào bị che: như cũ đập khối chắn mặt hàng; không có khối nào để đập thì đi vào làn của lát (vừa đi vừa đào), rồi phá đúng khối
  chắn tia nhìn tới mặt lát, vẫn không được thì đi vòng (chỗ đứng khác nhìn thấy lát, hoặc đào đường tới sát nó). Đi vòng 3 lần chưa được
  thì để lát đó vét cuối tầng, đào tiếp. Vòng đầu chờ đồng bộ ghost như cũ, các vòng sau của cùng lát làm ngay (trước đây mỗi vòng đứng
  chờ hơn 1 giây).

### 4. Không dừng

- 1 phút không có khối nào **được server giữ vỡ** (đập vỡ rồi bị trả lại không tính) → tự làm lại, lần lượt: làm lại kế hoạch tầng + đồng
  bộ ghost quanh người → đi tới chỗ khác trong tầng rồi làm lại → vào lại tầng; lặp mãi. Trước đây 2 phút làm lại kế hoạch, **6 phút thì
  dừng**.
- Một ô hỏng cộng dồn 15 lần (server trả lại mãi / không tới được) → bỏ qua ô đó, xong tầng rồi đào tiếp (cuối tầng báo "bỏ qua N ô").
  Trước đây đập mãi một khối và tầng không bao giờ xong.
- Không dừng nữa, thay bằng làm lại / chờ rồi tự chạy tiếp: 20 việc liền hỏng, không vào được góc nào của tầng, lỗi lặp lại (nghỉ 5 giây),
  việc hỏng "nặng" như Order chưa giao khối kê (thử lại sau 30 giây), máu ≤ 2 tim (ăn rồi đứng hồi máu, đủ máu đào tiếp), không thấy cúp
  trong túi (5 giây xem lại một lần), cúp sắp vỡ mà chưa có bình XP (không đào cho vỡ cúp, mỗi phút thử mua/lấy XP lại).
- Vẫn dừng: nhấn J, rời thế giới, nhân vật chết, chưa chọn vùng / placement.

### Kiểm thử

- Gametest thêm 5 ca; `Fake3x3Pickaxe` thêm ô 3x3 không phá kèm và đặt lại khối sau N tick; ghi cả điểm trúng trên khối mỗi nhát.
  Cùng bộ kiểm thử trên 1.14.3 và 1.14.4 (tick = 1/20 giây):

| Ca | 1.14.3 | 1.14.4 |
|---|---|---|
| K: khối giữa bị trả lại 2 lần — điểm trúng trên khối | (0,5 0,5) cả 3 nhát | (0,5 0,5) → (0,3 0,3) → (0,7 0,7) |
| K2: bị trả lại 6 lần | 6 nhát cùng một điểm | 6 điểm khác nhau, 251 tick (trước 270) |
| K4: 2 khối giữa hiện lại, khối gần che khối xa | 475 tick, **đứng im ở "Đập lại khối giữa … lần 1 · đập"** | **212 tick**, đi vòng, không đứng im |
| K5: sỏi rơi vào chỗ khối giữa (khối dưới bị 3x3 bỏ sót) | 220 tick | 219 tick |
| K6: khối giữa server không bao giờ cho phá | sau 2400 tick vẫn đập mãi khối đó, tầng không xong (6 phút thì dừng) | **452 tick**: bỏ qua ô đó, xong khung |
| K7: đang đào bị ép "kẹt" 3 lần (làm lại / đi chỗ khác / vào lại tầng) | — | 293 tick, đào sạch |
| K8: bedrock giữa hàng che kín lát sau | 729 tick, đứng im ≥ 3 giây | **460 tick**, không đứng im |
| I: đào trọn khung 15x6x15 có sỏi | 1678 tick | 1679 tick |
| J4: rãnh cắt ngang lối vào khung | 502 tick | 502 tick |

- Toàn bộ 22 ca A–K8: 76/76 phép đạt. `MovementRegression` 29, `StrikeRegression` 7 phép đạt.

## 1.14.3

Theo 2 lỗi người dùng báo (kèm ảnh nhân vật ngồi sát mép rãnh): **ngồi im sát mép rãnh nhiều phút** và **đang đào hàng thì không đập khối
giữa mà đập khối xung quanh**. Cả hai cùng một gốc: chỉ cần 1 góc chân lơ lửng trên hố là bot **ngồi** (giữ mép), mà ngồi thì cúp 3x3
chỉ phá 1 khối — nhát đập hàng chờ đứng thẳng mãi không ra (ngồi im), còn việc đập lẻ thì chuyển sang ngồi đập từng khối một.

### 1. Không ngồi im sát mép, nhảy qua rãnh

- Chỉ ngồi giữ mép khi **đang trôi** về chỗ hụt chân (vị trí sắp tới còn dưới 2 góc chân có sàn). Đứng yên sát mép thì không rơi nên
  đứng thẳng đập 3x3 bình thường (`SafeMotion.edgeSneak`, dùng cho `Keys.stand` / `releaseMove`).
- `StrikeTask` sát mép vẫn đập 3x3 (trước đây cứ chân lơ lửng là đổi sang ngồi đập lẻ). Đáp lệch còn dưới 2 góc chân có sàn mà đứng yên
  → nhích vào tâm ô sàn rồi đập (`recentre`), không ngồi chờ.
- Đang đào hàng gặp rãnh: trước đây nhảy/lấp **ngay tick khối giữa lát vỡ** — server chưa kịp báo 8 khối 3x3 còn lại vỡ nên ô đáp bên kia
  còn "đá" → không nhảy được → **lấp rãnh**. Nay: ô đáp là khối sắp vỡ theo nhát vừa đập, hoặc lát đang đào vẫn với tới từ mép, thì đứng
  đập tiếp (không đi, không lấp); ô đáp trống là nhảy. Đang đập dở lát với tới được thì đập xong rồi mới nhảy. Chỉ lấp khi không nhảy được,
  không đi qua hố nông được và cũng không đập được gì từ chỗ đứng.

### 2. Khối giữa không bao giờ bị bỏ qua

- Khối giữa của lát vừa đập (đã thấy trống) **hiện lại** — server trả lại, ghost, hay sỏi rơi vào — thì quay lại đập ngay theo thang
  đổi góc (`MiddleRetry`), **không đụng khối xung quanh** trong lúc thử:
  1. lùi 0,5 ô (ra xa mặt đập) đập lại 3x3,
  2. lùi thêm 0,5 ô đập lại,
  3. bước ngang trái 0,5 ô đập lại,
  4. bước ngang phải 0,5 ô đập lại,
  5. ngồi đập lẻ riêng khối giữa.

  Lùi/ngang bằng bước ngồi cho đúng chỗ (lệch khoảng 0,03 ô), đứng vững hết trôi rồi mới đập; chỗ mới không đứng được (tường, hố) thì đập lại
  tại chỗ. Mỗi lần chỉ chờ khoảng ping (4 + 2·ping tick) xem khối có hiện lại không. Sỏi/cát rơi vào chỗ khối giữa thì đập luôn tại chỗ.
- Thử đủ 5 lần vẫn hiện lại → cả mặt 3x3 của lát đó để vét cuối tầng; nếu nó che kín các lát sau (không với được từ phía này) thì kết thúc
  hàng luôn, phần còn lại vét cuối tầng — trước đây thử từng lát, mỗi lát đứng chờ cả chục giây.
- Trước đây: khối giữa bị trả lại → bị đưa vào hàng chờ thử lại (lùi 10–100 tick) nên các bước "khối che mặt hàng" / "phá khối chắn đường"
  chọn **khối xung quanh**, mà hỏng 1 lần là chỉ đập lẻ → ngồi đập từng khối một (7–8 nhát lẻ thay cho 1 nhát 3x3).
- Vét sót: khối đang chờ thử lại thì không đập lẻ khối xung quanh nó (chờ tới lượt đập 3x3 vào chính nó); hỏng 1–2 lần vẫn đập 3x3,
  từ 3 lần mới đập lẻ (trước: hỏng 1 lần là đập lẻ).

### Kiểm thử

- Gametest thêm 7 ca; `Fake3x3Pickaxe` giả cả server trả lại khối (huỷ nhát phá, hoặc cho 3x3 vỡ rồi đặt lại riêng khối giữa).
  Cùng bộ kiểm thử chạy trên 1.14.2 và 1.14.3 (tick = 1/20 giây):

| Ca | 1.14.2 | 1.14.3 |
|---|---|---|
| J: tầng đã đào, rãnh 1 ô cắt ngang, khối sót bên kia, đứng sát mép (như ảnh) | 92 tick | 91 tick |
| J2: đào hàng băng qua rãnh 1 ô (tầng chưa đào) | 1352 tick, **ngồi im sát mép**, **lấp rãnh 2 lần** | **507 tick**, nhảy qua, không lấp |
| J3: rãnh chạy dọc làn bên của hàng | 1363 tick, **ngồi im sát mép** | **372 tick** |
| J4: rãnh cắt ngang lối vào khung | 502 tick | 502 tick |
| K: khối giữa bị trả lại 2 lần | 444 tick, **7 nhát lẻ khối xung quanh**, ngồi đập | **149 tick**, lùi 0,5 → lùi 0,5 → vỡ, 0 nhát xung quanh |
| K2: bị trả lại 6 lần | 776 tick, **8 nhát lẻ khối xung quanh** | **270 tick**, đủ 5 bậc rồi để cuối tầng, 0 nhát xung quanh |
| K3: 3x3 vỡ nhưng khối giữa hiện lại 3 lần | 300 tick, ngồi im ≥ 3 giây | **169 tick** |
| I: đào trọn khung 15x6x15 có sỏi | 1677 tick | 1683 tick |

- Toàn bộ 17 ca A–K3: 57/57 phép đạt. `MovementRegression` 29, `StrikeRegression` 7 phép đạt.

## 1.14.2

Theo 6 yêu cầu của người dùng: đào liền khi tìm đường, đi chéo, phản ứng nhanh, đi qua hố nông, nhận diện địa hình như AutoBuild,
để dành khối thừa tới cuối tầng.

### 1. Đào liền khi tìm đường (không đào – chờ – đào – chờ)

- `WalkTask` gặp khối chắn đường thì tự ngắm và giữ đập ngay, khối vỡ là đi tiếp (trước đây giao cho `StrikeTask`: tới chỗ đứng → ngắm →
  đập → **chờ server** → **kiểm tra** → **đồng bộ ghost**, rồi mới đi tiếp). Khối ở ô sau ô đang tới mà đã với tới thì vừa đi vừa đập.
- `StrikeTask` (mọi nhát: vét sót, đào xuống, phá đường…) vỡ là xong ngay; kiểm tra server trả lại khối, đếm ô 3x3 vỡ, xin đồng bộ ghost
  chạy nền sau vài tick (`deferCheck`/`runDeferred`), không bắt bot đứng chờ.
- Không cần đợi góc nhìn đứng yên 1 tick mới đập: mặt 3x3 chỉ phụ thuộc trục của hướng nhìn, nên đủ khi hướng nhìn server đang biết đã
  chắc chắn cùng trục (`BreakGeometry.firm`). Trước đây vừa đi vừa ngắm thì góc đổi liên tục → ~10 tick mới đập được.
- Vừa đập khối trên, khối dưới sẽ vỡ theo 3x3 nhưng server chưa báo → nhớ các ô "sắp vỡ" (`expectedGone`), không đi tìm chỗ đứng để đập
  lại khối đó, vẫn bước tới trong lúc chờ.
- Giữ nguyên 5 tick nghỉ giữa 2 nhát của Minecraft gốc (bỏ đi là "đập nhanh", dễ bị anti-cheat bắt).

### 2. Đi chéo kiểu AutoBuild

- Bước chéo hợp lệ khi ô đích đứng được và 2 ô vuông góc trống thân người (luật của AutoBuild); trước đây đòi 2 ô vuông góc cũng phải
  đứng được, nên sát mép hố là đi hình chữ L. Áp dụng cho A*, đường đi thẳng và đường có đào (trước chỉ đi 4 hướng).
- Kiểm tra đường thẳng / an toàn lúc bấm phím theo vật lý vanilla: còn một phần chân trên khối là không rơi. Mỗi điểm cần ít nhất
  2 trong 4 góc chân có sàn (dư 1 góc phòng lệch ngang); trước đây đòi đủ 4 góc → sát mép hố là dừng + ngồi.

### 3. Phản ứng nhanh

- Gặp khe: việc nhảy chạy luôn trong tick tạo ra nó; tính lại đường không nhả phím; đáp xuống mà đường còn đi tiếp về phía trước thì
  không hãm (trước đây bấm lùi, tụt 0,12 → 0,02 rồi tăng tốc lại); đi thì không tự ngồi chỉ vì chân chạm mép hố; nhảy được khi lệch ngang
  tới 0,4 ô (trước 0,3 → vào khe từ hướng chéo phải lùi lấy đà).
- Việc con xong là đi tiếp ngay trong tick đó; hết bước đường thẳng thì tính bước mới và đi luôn.
- Tìm đường A* 8 ms mỗi tick (trước 3 ms).
- Đầu hàng: lát đầu nằm sát mép khung, đường đi đưa tới phía bên kia → đập mặt đối diện (cùng trục, cùng mặt 3x3). Trước đây đứng
  chờ ~90 tick rồi mới giao việc.
- Xong tầng: không chờ cố định 20 tick; chỉ chờ khi còn khối đang rơi / nhát chờ server xác nhận (tối thiểu 5 tick).

### 4. Hố nông 1 ô: đi xuống rồi bước lên

- Đi trong tầng mà gặp hố/rãnh sâu 1 ô không nhảy qua được (rộng quá 3 ô) → bước xuống, đi trong rãnh, bước lên bên kia; đắt hơn nhảy
  nhưng rẻ hơn bắc cầu. Đang đào hàng gặp hố như vậy cũng đi qua thay vì lấp.
- Đứng thấp hơn sàn tầng đúng 1 ô khi đang đi → không bật "bắc cột leo lên tầng"; dừng trong hố thì bước lên ô sàn gần nhất.

### 5. Nhận diện địa hình như AutoBuild

- Ngoài các điểm trên (đi chéo theo luật AutoBuild, hố nông, khe sâu ≤ 5 ô): nước nông (chân ngập, đầu khô, có sàn, không gần lava)
  lội qua được, đắt hơn ô khô 1,5 như AutoBuild 3.0.84; trước đây mọi ô có nước đều coi là không đi được.

### 6. Khối thừa để dành tới cuối tầng

- Bỏ vét sót giữa hàng ("khối thấp còn sót trong hàng") và cuối mỗi hàng ("vét sót hàng N"); hết hàng đi tiếp ngay, không chờ đồng bộ.
  Toàn bộ khối thừa vét một lượt cuối tầng (chọn nhát đập theo điểm như 1.14.0).
- Vẫn phá ngay: sỏi/cát rơi lại vào hàng, khối chắn đường đi, khối che mặt cần đập.

### Kiểm thử

- `MovementRegression` 29 phép (thêm: bước chéo sát góc hố, đường chéo (0,0)→(8,8) toàn bước chéo, rãnh rộng 4 sâu 1 → bước xuống,
  A* trong tầng qua rãnh không bắc cầu, nước nông lội qua). `StrikeRegression` 7 phép.
- Gametest trong Minecraft thật có **cúp 3x3 giả** (`Fake3x3Pickaxe`: cúp tên "3x3" phá 3x3 vuông góc hướng nhìn, ngồi thì 1 khối,
  Hiệu suất V) để giống DonutSMP; đếm tick theo tuổi nhân vật. Cùng bộ kiểm thử chạy trên 1.14.1 và 1.14.2:

| Ca | 1.14.1 | 1.14.2 |
|---|---|---|
| A. Đường hầm 2 khe | 66 | 61 |
| C. Đào trọn khung 5x3x5 | 206 | 136 |
| D. Khung 50×50, 6 ô sót xa | 540 | 491 |
| E. Khung 67×65 như log, 6 ô sót sát mép | 348 | 303 |
| F. Đường hầm 12 khối chặn (vừa đi vừa đào) | 191, 12 bước chờ | 83, không chờ |
| G. Đi chéo 12×12 sàn trống | 66 | 66 |
| G2. Đi chéo cạnh dãy hố | 114, quãng 20,4 ô | 71, quãng 16,6 ô (đường chéo 17) |
| H. Rãnh rộng 4 sâu 1, đi trong tầng | 79, bắc cầu 1 khối | 69, không đặt khối |
| I. Đào trọn khung 15×6×15 (2 tầng, có sỏi) | 1886 | 1681 |

1.14.2 đạt 20/20 phép trong game, 1.14.1 đạt 17/20. Vòng đào hàng còn khoảng 8–9 tick mỗi nhát: 5 tick nghỉ giữa 2 nhát của game gốc +
2–3 tick đập đá (Hiệu suất V) + bước sang lát kế.

**Chưa chạy trên DonutSMP.** Chưa kiểm thử: cúp 3x3 thật của server và anti-cheat với việc vừa đi vừa đập / đập ngay khi hướng nhìn
cùng trục, ping cao.

## 1.14.1

Sửa bot đứng im rất lâu dù còn khối sót / còn hàng chưa đào (log người dùng 21:47–22:11 trên DonutSMP, khung 67×27×65).

- **Đứng im không ghi log sau khi nhảy qua hố / đứng sát mép hố** (log 22:10:38 → 22:11:29, 51 giây không có dòng nào).
  1.14.0 cho đường đi thẳng đi một mạch nhiều ô; khi người đứng sát mép hố, góc thân chìa ra trên hố nên phép kiểm tra đường thẳng
  (tính từ vị trí thật) báo bị chặn, bot bỏ đường rồi tính lại đúng đường đó (tính từ tâm ô thì thông) — lặp mãi mỗi tick.
  Giờ phép kiểm tra bỏ qua điểm xuất phát, và đường thẳng bị từ chối 3 lần liền thì chuyển sang tìm đường A* (có ghi log).
- **Còn ô sót mà đứng chờ "thử lại sau"** (log 21:47:39–21:47:56: 6 ô sót ở mép khung, lặp "không có đường tới chỗ đứng nhìn thấy
  hình dạng thật của khối", mỗi lần chờ lâu dần 1 → 10 giây). Trước khi đi, 1.14.0 đòi tìm trọn đường bằng bộ tìm đường cũ giới hạn
  3000 ô, trong khi một tầng của khung 67×65 có 4355 ô. Giờ thấy ô sót là đi ngay: chọn chỗ đứng gần nhất (±4 ô) nhìn thấy được ô sót,
  đường đi tìm bằng bộ tìm đường kiểu AutoBuild (tầm 128 ô, 20000 nút); không có chỗ nào nhìn thấy thì đào đường tới sát ô đó.
  Ô vừa hỏng xếp sau các ô khác thay vì làm cả bot đứng chờ; thời gian chờ thử lại tối đa 10 → 5 giây.
  Log ghi `Vét sót: đi tới ô … (đứng …, đang ở …)` để lần sau xem được bot đang đi đâu.
- **Hàng không đập nhát nào vẫn chờ ~1 giây** (log 21:47:30–21:47:39: "0 nhát liền trong 0,8–0,9 giây" × 18 hàng ≈ 15 giây).
  Giờ hàng không có nhát nào hoặc không còn ô cần đồng bộ thì đi tiếp ngay.
- Chờ sỏi/cát rơi khi xong tầng: tối thiểu 40 → 20 tick.

### Kiểm thử

- `MovementRegression` 22 phép (thêm: đứng ở x = 4,28 sát mép hố, đường thẳng về phía trước phải thông — mã 1.14.0 hỏng phép này).
- Gametest trong Minecraft thật thêm 2 ca: D (khung 50×3×50, 6 ô sót rải xa) và E (khung 67×3×65 như log, 6 ô sót ở hàng trên sát mép z,
  nhân vật ở đầu kia cách ~55 ô). Đo cả "đứng im ≥ 3 giây mà không đi, không đập được gì".

| Ca | 1.14.0 | 1.14.1 |
|---|---|---|
| A. Đường hầm 2 hố | lần này kẹt sau cú nhảy đầu, đứng im 600 tick không log (lỗi trên) | 85 tick |
| D. 50×3×50, 6 ô sót xa | xong 706 tick, đứng chờ ≥ 3 giây 2 lần | xong 590 tick, 0 lần |
| E. 67×3×65, 6 ô sót sát mép | xong 447 tick, đứng chờ 1 lần | xong 367 tick, 0 lần |

1.14.1: 11/11 phép trong game. Ca E trong khung phẳng không tái hiện được đúng dòng "không có đường…" của log (trong mỏ thật có hố, tường,
nước); bước tìm-trọn-đường-trước gây ra dòng đó đã bỏ hẳn.

## 1.14.0

Bản này theo yêu cầu: di chuyển linh hoạt như AutoBuild, vét khối sót gọn hơn, bỏ những thứ làm chậm.

### Di chuyển kiểu AutoBuild

- **Nhảy qua khe thay vì đặt khối dưới chân.** Bản 1.13.0 tắt hẳn nhảy qua khe hễ trong túi còn khối kê
  (`AutoBuildNavigation.parkour` có điều kiện `!scaffold`), còn đường đi thẳng (`DirectRoute.next`) gặp hố là bắc cầu ngay.
  Giờ cả hai đều nhảy qua khe 1–3 ô (khe 2–3 ô cần 1 ô chạy đà phía sau, đáy hố cách ≤ 5 ô, không lava). Chỉ bắc cầu khi không nhảy được.
- **Đang đào hàng gặp hố trên sàn → nhảy qua** (trước đây lấp hố bằng khối kê, tầng sau lại phải đào khối đó). Nhảy hỏng 2 lần trong một hàng thì mới lấp như cũ.
- **Cú nhảy theo vật lý vanilla, chép từ AutoBuild 3.0.121** (`parkourStep`, `simulateCross`, `simulateFlight`): mặt khoá theo trục nhảy,
  A/D kéo về giữa làn, mỗi tick mô phỏng điểm chạm nếu nhảy ngay, chạm được ô đích là nhảy; trên không chọn giữ W / thả / giữ S;
  chạm đất thì hãm nếu đường rẽ hướng khác. Bản cũ phải lùi về một điểm cố định, đứng yên tới khi vận tốc < 0,04 và mặt lệch < 4° rồi mới nhảy.
- **Đi một mạch, không dừng ở từng ô.** Bước giữa đường tính là đã qua ngay khi vào tới ô (trước đây phải vào cách tâm ô ≤ 0,42);
  nhìn trước tối đa 5 ô thẳng thông cùng độ cao rồi đi thẳng tới ô xa nhất; đường đi thẳng (`DirectRoute`) đi một mạch tối đa 8 ô theo trục
  thay vì dừng hãm ở từng ô; chạy nhanh khi còn > 2,5 ô; chỉ ngồi khi sắp dừng cạnh hố (bản cũ ngồi suốt khi cạnh đường có hố → bò).
- Đi tới chỗ đứng để đập: dung sai dừng 0,08 → 0,2 ô (không phải nhích vào đúng tâm ô).

### Vét khối sót

- **Cột 3 khối → đập khối giữa, 1 nhát.** `chooseStrike` bản cũ xét chỗ đang đứng trước: hễ tại chỗ đập được khối nào (kể cả khối trên/dưới)
  là đập ngay, nên cột 3 khối mất 2 nhát và mặt 3x3 của khối trên lấn lên tầng trên. Giờ xét chung chỗ đang đứng và mọi chỗ đứng đi tới được
  (mỗi bước đi trừ 1,5 điểm), điểm = số khối cần đào mặt 3x3 phá được (×10) + khối khác của tầng (×4) − khối thuộc tầng khác (×12).
  Đập khối giữa tầng theo mặt ngang phủ đúng 3 lớp của tầng. Công thức nằm ở `StrikeScore`.
- `StrikeTask` thử mặt đã chọn trước; bản cũ thử lần lượt DOWN, UP, N, S, W, E nên có lúc đổi sang mặt trên (chỉ phá 1 lớp).

### Bỏ chậm

- **Quay mặt tức thì** (bỏ quay chậm 16–30°/tick và vượt góc 10%). Ô "Quay tối thiểu/tối đa" trong menu thay bằng dòng "Quay mặt: tức thì".
- Hướng nhìn ổn định được ghi ở cuối tick: quay xong tick sau là đập (server đã nhận hướng nhìn mới để phá đúng mặt 3x3). Bản cũ chờ 2–3 tick.

- Gặp khe 2 ô nông (sâu 1 ô): nhảy thẳng qua cả khe tới ô cùng độ cao; chỉ nhảy xuống đáy khe khi không có ô đáp cùng độ cao
  (lỗi này tìm ra khi chạy thử trong game, xem dưới).

### Kiểm thử

Không cần mở game (`./gradlew build` chạy luôn):

- `MovementRegression` (20 phép): khe 1 ô có/không khối kê, hành lang hẹp, khe 2 ô, khe 2 ô nông, hố sâu > 5 ô vẫn bắc cầu,
  đường thẳng đi một mạch, vật lý nhảy. Cùng địa hình chạy trên mã 1.13.0: hành lang có khối kê ra bước `BRIDGE` tại ô hố,
  `DirectRoute` ra `BRIDGE`; bản 1.14.0 ra `JUMP`.
- `StrikeRegression` (7 phép): cột 3 khối chọn khối giữa mặt ngang (điểm 31, khối trên 20), 2 cột sát nhau 1 nhát 6 khối,
  hàng 3 khối ở đáy tầng phá bằng mặt ngang không đụng sàn.

Trong Minecraft 1.21.4 thật (Fabric client gametest, thế giới đơn, `./gradlew runClientGameTest`, Linux chạy dưới `xvfb-run`),
có Litematica 0.21.7 + MaLiLib 0.23.5, cúp netherite thường (không 3x3), 64 cobblestone trong túi:

| Ca | 1.13.0 | 1.14.0 |
|---|---|---|
| A. Đường hầm 12 ô, hố 1 ô và hố 2 ô | bắc cầu cả 3 ô hố (đặt 3 cobblestone), 155 tick | nhảy qua cả 2 hố, không đặt khối, 87–99 tick |
| B. Cột 3 khối, khối giữa bị bedrock che khỏi chỗ đang đứng | chọn khối **trên** (5,102,20) | chọn khối **giữa** (5,101,20) mặt ngang, đi vòng rồi đập vỡ trong 19–21 tick |
| C. Đào trọn khung 5x3x5 từ mặt đất | sạch, 2300 tick | sạch, 1882–1894 tick, không đặt cobblestone nào |

Bản 1.14.0 đạt 7/7 phép kiểm tra trong game; 1.13.0 đạt 2/7. Lần chạy đầu của 1.14.0 hỏng ca khe 2 ô (bot nhảy xuống đáy ô khe thứ hai
rồi leo lên); đã sửa như trên và chạy lại đạt.

Kiểm tra liên kết JAR với `minecraft-merged-intermediary 1.21.4`: 0 lỗi. Lớp mixin không đổi so với 1.13.0.

**Chưa chơi thử trên DonutSMP.** Chưa kiểm thử: cúp 3x3 thật của server (trong thế giới đơn cúp chỉ phá 1 khối), quay mặt tức thì
có bị anti-cheat của server phản ứng không, và nhảy khi ping cao.
