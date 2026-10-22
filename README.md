# donut-autobuild

Mod Fabric (Minecraft 1.21.4) và công cụ tự động hoá để chơi trên server **DonutSMP**.

| Thư mục | Nội dung | Trạng thái |
|---|---|---|
| [`donut-relaunch/`](donut-relaunch/) | Treo nhiều nick qua Legacy Launcher: tự mở lại game khi "Invalid session" hoặc crash, tự vào lại server, tự bật lại ASell, gỡ ASell khi kẹt, tiết kiệm CPU/RAM khi treo | 1.3.1 |
| [`donut-autobuild/`](donut-autobuild/) | Mod tự xây theo bản thiết kế Litematica trong Sinh tồn: chế độ Chính xác / Nhanh, tự lấy vật liệu từ Order / Shop / ender chest, tự `/home` khi ngã, an toàn TNT, tự vào lại server khi lag | 3.0.121 |
| [`donut-automine/`](donut-automine/) | Mod tự đào khung bằng cúp 3x3: đào theo tầng/hàng, vét khối sót, nhảy qua khe kiểu AutoBuild, xử lý lava/nước, tự ăn, sửa cúp, bán đồ, lấy khối kê qua Order | 1.14.2 |

Mỗi thư mục có `README.md` riêng: cách hoạt động, cách cài, cách build và lịch sử sửa lỗi.

## Quy ước

- Mỗi phiên bản mới đi qua một nhánh và một Pull Request. Mô tả PR ghi lỗi gì, nguyên nhân ở đâu trong logic, sửa thế nào, số đo trước/sau.
- Jar build sẵn của từng bản nằm trong `<mod>/release/`.
- Không đưa lên repo: file Minecraft (jar game, stub sinh từ jar game), log có mã đăng nhập (`.tlauncher/logs/launcher.log`), tên nick.
