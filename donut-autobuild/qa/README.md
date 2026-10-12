# qa — kiểm thử hồi quy

## `test-src/`

14 bộ kiểm thử viết cho 3.0.110–3.0.120 (phiên làm bản vá bytecode). Mỗi bộ đếm số phép kiểm tra đã qua; sai thì ném `AssertionError`.

| File | Kiểm tra |
|---|---|
| `RecoveryRegression` | ngân sách 25 lần đặt hỏng, thoát ra vào lại, mua đồ ăn/cúp. Chứa luôn các hàm dùng chung (dựng `MinecraftClient`/người chơi/thế giới giả, đọc/ghi field bằng reflection) |
| `HeldPlacementRegression`, `SpeedRegression` | món trên tay đúng loại trước khi đặt; chờ ổn định tay theo ping |
| `BuildQualityRegression`, `ControllerQualityRegression` | khối sai/thiếu thuộc tính không bị coi là xong; hàng sửa tôn trọng dấu hoãn |
| `WaterShopRegression`, `WaterlogAimRegression`, `WaterlogTransactionRegression` | mua xô, đổ nước đúng ô, giữ xô tới khi server xác nhận |
| `MovementMiningRegression`, `MovementResumeRegression` | phanh, mép hố/lava, đập bằng giữ chuột trái |
| `FreecamRegression`, `FreecamVisibilityRegression` | freecam phím N |
| `SchematicWorkspaceRegression`, `LegacyGuiRegression` | thư viện file, đặt/di chuyển placement, GUI 3 tab |

**Chưa chạy được bằng Gradle.** Các bộ này cần một môi trường dựng riêng:
- Minecraft 1.21.4 tên yarn + Fabric + Litematica 0.21.7 trên classpath;
- nhiều đối tượng Minecraft được tạo không qua constructor (`allocate`);
- truy cập field/method private của mod bằng reflection, theo tên.

Môi trường đó không nằm trong repo. Chạy lại cho 3.0.121 là việc chưa làm. Tên field/method mà các bộ này gọi bằng reflection đều còn trong mã 3.0.121, nhưng logic túi đồ (`InventoryAccess.synced`) đã chặt hơn nên vài kiểm tra có thể phải chỉnh.

`WaterShopRegression` và `RecoveryRegression` vốn dùng toạ độ thật ngoài thế giới. Khi đưa lên repo đã đổi sang toạ độ nhỏ, giữ nguyên vị trí trong chunk (x, z mod 16).

## `VALIDATION-3.0.120.txt`

Kết quả chạy các bộ trên với jar 3.0.120 (SHA256 của jar, số phép kiểm tra từng nhóm). Ví dụ: 21.369 phép hồi quy, 18.794 phép GUI cũ, 5 mixin freecam áp được vào Minecraft. Chưa bộ nào chạy trên DonutSMP thật.
