# config/modmenu.json — 2 dòng cần đổi

ModMenu 13.0.4 kiểm tra cập nhật mod lúc mở game bằng luồng ảo (virtual thread). Trên Windows + JDK 21, việc này làm
luồng `Write-Poller` của Java chạy vòng vô ích, ăn trọn 1 nhân CPU mỗi game cho tới khi tắt game.

```json
  "update_checker": false,
  "button_update_badge": false,
```

Mod Donut Relaunch 1.3.1 tự đổi 2 dòng này lúc khởi động nếu đang là `true` (có hiệu lực từ lần mở game sau).
