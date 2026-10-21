# Lịch sử sửa — donut-automine

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
