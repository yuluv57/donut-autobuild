# donut-automine

Mod Fabric tự đào một khung (vùng chọn XYZ hoặc placement Litematica) bằng cúp 3x3 trên **DonutSMP**:
đào từng tầng 3 khối theo hàng, vét khối sót, xử lý lava/nước, tự ăn, tự sửa cúp bằng bình XP, tự bán đồ, lấy khối kê qua Order, freecam.

| | |
|---|---|
| Phiên bản | **1.14.1** — [`release/donut-automine-fabric-1.21.4-1.14.1.jar`](release/donut-automine-fabric-1.21.4-1.14.1.jar) |
| Minecraft | 1.21.4, Fabric Loader ≥ 0.16.9, Java 21 |
| Cần thêm | Fabric API, Litematica ≥ 0.21.0, MaLiLib (Sodium/Iris không bắt buộc) |

Thay đổi của từng bản: [`CHANGELOG.md`](CHANGELOG.md).

## Cài

Đóng Minecraft, xoá JAR AutoMine cũ trong thư mục `mods`, chép `donut-automine-fabric-1.21.4-1.14.1.jar` vào, mở lại game.
Chỉ để **một** JAR AutoMine. Cấu hình cũ (`config/donut-automine.json`) dùng tiếp được; hai mục `rotationMinSpeed`/`rotationMaxSpeed`
không còn tác dụng vì 1.14.0 quay mặt tức thì.

## Mã nguồn

Bản 1.13.0 chỉ có JAR (gói mã nguồn vá không nằm trong repo). Mã nguồn trong [`mod/src`](mod/src) là bản dịch ngược 1.13.0 bằng Vineflower
(sau khi đổi tên intermediary → yarn 1.21.4+build.8), sửa đủ để biên dịch lại, rồi mới thêm thay đổi của 1.14.0. Trước khi sửa logic đã so
bản build lại với JAR 1.13.0 gốc: cùng danh sách lớp, lời gọi trong từng hàm giống nhau (chỉ khác kiểu khai báo `List`/`ArrayList` và
`Objects.requireNonNull` do trình biên dịch sinh). Tên biến `varN` là của trình dịch ngược.

Các file chính của 1.14.0:

| File | Việc |
|---|---|
| `mine/MineController.java` | `JumpTask` (nhảy qua khe kiểu AutoBuild), `WalkTask` (đi một mạch, nhìn trước, chạy nhanh), `RowTask` (hố trên sàn → nhảy), `chooseStrike` (vét sót), quay mặt tức thì |
| `mine/Parkour.java` | vật lý nhảy chép từ AutoBuild 3.0.121 |
| `mine/StrikeScore.java` | điểm của một nhát đập vét sót |
| `mine/AutoBuildNavigation.java`, `mine/DirectRoute.java` | tìm đường: nhảy qua khe cả khi có khối kê, đi thẳng một mạch |
| `mod/src/test/java/…/MovementRegression.java`, `StrikeRegression.java` | kiểm thử không cần mở game (địa hình giả qua `Cells.probe`) |
| `mod/src/gametest/java/…/AutoMineClientTest.java` | kiểm thử trong Minecraft thật: nhảy qua hố, cột 3 khối, đào trọn khung 5x3x5, đi vét ô sót ở xa (khung 50×50 và 67×65) |

## Build

```
cd mod
./gradlew build          # Windows: gradlew.bat build
```

Ra `mod/build/libs/donut-automine-fabric-1.21.4-<version>.jar`. `build` chạy luôn 2 bộ kiểm thử; chạy riêng: `./gradlew movementTest strikeTest`.

Kiểm thử trong Minecraft thật: `./gradlew runClientGameTest` (mở game, tạo thế giới đơn, chạy 5 ca trong
`mod/src/gametest/java/com/donutautomine/test/AutoMineClientTest.java` rồi tự thoát; kết quả ở dòng `[GameTest] KẾT QUẢ` trong log).
Trên Linux không có màn hình: `xvfb-run -a ./gradlew runClientGameTest`.
Litematica, MaLiLib, Sodium lấy từ Modrinth Maven nên không cần chép jar vào `libs`.
