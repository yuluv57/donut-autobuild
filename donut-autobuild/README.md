# Donut AutoBuild

Mod Fabric **client** cho Minecraft 1.21.4. Tự xây một placement của Litematica trong chế độ Sinh tồn trên server DonutSMP. Mod điều khiển nhân vật như người chơi thật: bấm phím W/A/S/D, ngồi, nhảy, chuột trái/phải. Server không cần cài gì.

Bản hiện tại: **3.0.124** (`release/donut-autobuild-fabric-1.21.4-3.0.124.jar`).

- Lịch sử sửa lỗi: [CHANGELOG.md](CHANGELOG.md)
- Hướng dẫn review bản này: [docs/REVIEW-3.0.121.md](docs/REVIEW-3.0.121.md)

## Mod làm được gì

| Nhóm | Việc |
|---|---|
| Xây | Xây từng tầng, trong tầng xây theo hàng và theo cụm. Khối có hướng (observer, piston, hopper…) được giải góc ngắm sao cho ra đúng hướng; không có góc thật thì dùng "góc gửi riêng" (gửi lên server góc nhìn khác với góc đang ngắm). Khối lơ lửng: tìm chuỗi khối thật để bám vào, không có thì kê khối tạm, xây xong tự đập. Lá ngậm nước: đổ xô nước vào. Crafter: chỉnh ô bật/tắt theo bản vẽ. |
| Kiểm tra | Chờ server xác nhận từng khối. Khối mất hoặc sai loại/hướng được đặt lại. Quét lại tầng dưới. 25 lần đặt hỏng chưa khắc phục → thoát ra vào lại server. |
| Di chuyển | Tìm đường A* có đi chéo, nhảy khe (tính theo vật lý vanilla), kê cột leo lên, bắc cầu ngang, thoát hố/bể nước, bù dòng nước, tránh lava/hố. |
| Vật liệu | Lấy từ `/order` ("Your Orders" → Collect). Không có thì mua ở `/shop` (Quick Buy, có trần giá). Cất/lấy đồ bằng ender chest. Bán xô rỗng và đồ thừa bằng `/sell`. Tự mua đồ ăn và cúp netherite khi hết. |
| An toàn | Trước mỗi cú đặt/đập/đổ nước gần TNT: kiểm tra có kích nổ TNT không (dây đỏ, observer, nguồn điện, lửa/lava, cảm biến sculk). Tự hồi sinh, chờ hồi máu, ăn. |
| Server lag | `/home` 5 lần không lên menu → thoát ra vào lại. Mất kết nối → tự vào lại. 3 phút không có tiến triển thật → vào lại. Vào lại xong tự xây tiếp. |
| /home | Tự `/sethome` trên tầng đang xây (từ tầng 3). Lỡ ngã thì `/home N` để lên lại. Mod chỉ xoá home do chính nó tạo. |
| Giao diện | Menu chọn file schematic, chỉnh vị trí/xoay/lật, chốt vị trí ("Nạp"); HUD tiến độ và tiền đã chi; xem từng tầng; freecam. |

Hai chế độ:
- **Chính xác (exact)**: đúng loại khối như bản vẽ.
- **Nhanh (fast)**: khối trang trí được thay bằng khối rẻ cùng nhóm (cùng độ chống nổ, cùng loại dụng cụ). Ví dụ: Glass thay Stained Glass, gỗ spruce thay gỗ khác.

## Cài đặt

Cần:
- Minecraft 1.21.4 và Fabric Loader ≥ 0.16.9
- Fabric API
- Litematica 0.21.x (đang dùng 0.21.7)
- MaLiLib (đang dùng 0.23.5)
- Java 21

Chép `release/donut-autobuild-fabric-1.21.4-3.0.124.jar` vào thư mục `mods`. Trong `mods` chỉ được có **một** bản AutoBuild.

## Cách dùng

1. Nhấn **U**: mở menu.
2. Chọn file schematic. Mod đọc thư mục `schematics` của profile; có thể dán đường dẫn.
3. Chỉnh vị trí: XYZ, bước 1/5/10, đưa về chỗ đứng, xoay, lật.
4. Bấm **Nạp** để chốt vị trí.
5. Bấm **Bắt đầu** hoặc nhấn **K**. Nhấn **K** lần nữa để dừng.

Chỉ **K**, `/autobuild stop` và nút "Dừng, giữ tiến độ" mới dừng hẳn. Mọi lần mod tự tạm dừng (chết, máu thấp, mở chat, mất kết nối…), mod tự chạy lại.

| Phím | Việc |
|---|---|
| U | Menu (tab Công trình / Vật liệu / Crafter / Cài đặt) |
| K | Chạy / dừng |
| N | Freecam: W/A/S/D bay, Space lên, Shift xuống, Ctrl bay nhanh. Bật freecam thì mod tạm dừng xây |
| PgUp / PgDn | Xem tầng trên / dưới của schematic |

Các phím đổi được trong Controls.

| Lệnh | Việc |
|---|---|
| `/autobuild` hoặc `/autobuild status` | Trạng thái, tiến độ |
| `/autobuild start` · `stop` | Chạy · dừng |
| `/autobuild load` | Đọc lại placement đang chọn |
| `/autobuild menu` | Mở menu |
| `/autobuild save` · `restore` | Dừng và lưu tiến độ · khôi phục |
| `/autobuild cleanup` | Dọn khối kê tạm |
| `/autobuild range <từY> <đếnY>` · `range all` | Chỉ xây trong khoảng Y · xây toàn bộ |
| `/autobuild from <y>` | Xây từ Y này trở lên |
| `/autobuild layer <n>` | Xây từ tầng thứ n (tính từ đáy công trình) |
| `/autobuild mode exact` · `mode fast` | Đổi chế độ |
| `/autobuild spoof auto` · `on` · `off` | Góc gửi riêng: tự động (DonutSMP / thế giới đơn) · luôn bật · tắt |

## File mod tạo ra (thư mục `config/` của profile)

| File | Nội dung |
|---|---|
| `donut-autobuild.json` | Cấu hình. Một số cài đặt chỉ chỉnh được ở đây: home, crafter, ender chest, `/sell`, trần giá shop, tiền đã chi (xem `BuildConfig.java`) |
| `donut-autobuild-done.dat` | Ô đã xác nhận xong theo từng server + placement + gốc. Mở game lại mod vẫn nhớ. Dùng cho chỗ server giấu khối ở xa (anti-xray) |
| `donut-autobuild-session.json` | Tiến độ cho `save` / `restore` |
| `donutautobuild-schematics.json` | Danh sách schematic đã lưu (★) và gần đây |

## Mã nguồn

```
mod/src/main/java/com/donutautobuild/
├── AutoBuildMod.java      điểm vào: phím tắt, lệnh /autobuild, HUD
├── BuildConfig.java       cấu hình JSON
├── build/                 xây, di chuyển, túi đồ (27 file)
│   ├── BuildController    máy trạng thái chính, 18.7 nghìn dòng (xem dưới)
│   ├── PlacementSolver    góc ngắm + mặt click để khối ra đúng hướng; góc gửi riêng
│   ├── Navigation         ô nào đi/đứng/kê được, nhảy khe, nước, lava
│   ├── InventoryAccess    đổi đồ, đồng bộ túi đồ với server, nhật ký túi đồ
│   ├── TntGuard           kiểm tra kích nổ TNT trước mỗi thao tác
│   ├── Reconnector        thoát ra vào lại server
│   ├── SchematicPlan      đọc placement Litematica thành danh sách ô
│   ├── Freecam            freecam phím N (đi kèm 5 mixin trong mixin/)
│   └── …                  StateMatch, Materials, ServerAck, PlacementRecoveryBudget, SurvivalSupplies…
├── logic/                 thuật toán không phụ thuộc Minecraft: A*, chia hàng, chọn tầng
├── order/                 menu server: Order, Shop (mua/tìm), /home, /sell, theo dõi chat/menu, đếm tiền
├── gui/                   menu U (4 tab) + màn chọn file + màn chỉnh vị trí
├── schematic/             thư viện file schematic, đặt/di chuyển placement
└── mixin/                 5 mixin freecam (camera, nhìn, tương tác, tay, sương mù)
```

`BuildController` lần lượt qua các giai đoạn `IDLE → LOAD → SELECT → GOALS → SEARCH → MOVE → EQUIP → AIM → WAIT_BLOCK`, cộng thêm `ORDER`, `HOME`, `CRAFTER`, `STASH`, `SELL`, `WAIT_MATERIAL`, `MINE`, `PIT`, `CLEANUP`, `NEXT`. Hàm vào mỗi tick: `tick()` → `nextTarget()`.

**Lưu ý khi đọc mã:** phần lớn file được lấy lại từ jar bằng trình giải mã rồi sửa tiếp. Vì vậy biến cục bộ tên `var0`, `var1`… và có cast thừa. Các hàm đuôi `…Original` / tiền tố `quality…` là chỗ bản vá 3.0.110–3.0.117 (ghép bytecode) bọc hàm cũ. Chi tiết: [docs/REVIEW-3.0.121.md](docs/REVIEW-3.0.121.md).

## Build

```
cd mod
BUILD.bat            (Windows)   hoặc   ./gradlew build
```

Cần:
- Java 21;
- Internet cho lần đầu (Gradle tải Fabric Loom + Minecraft);
- 3 jar trong `mod/libs/` (xem [mod/libs/README.md](mod/libs/README.md)).

Jar ra ở `mod/build/libs/`. So jar vừa build với jar phát hành: [tools/README.md](tools/README.md).

Jar 3.0.124 trong `release/` được build bằng chính bộ Gradle này (`./gradlew build`, Loom 1.17.7). Các bản ≤ 3.0.121 được biên dịch bằng `javac` với stub Minecraft rồi remap sang tên intermediary.

## Kiểm thử

- `qa/test-src/`: 14 bộ kiểm thử hồi quy của 3.0.110–3.0.120. Cần môi trường Minecraft/Fabric dựng riêng, chưa nối vào Gradle. Xem [qa/README.md](qa/README.md).
- `qa/VALIDATION-3.0.120.txt`: kết quả chạy các bộ đó trên jar 3.0.120.
- Không có bản nào được kiểm thử tự động trên DonutSMP thật. Kiểm chứng thật = người dùng chạy và gửi `latest.log`.
