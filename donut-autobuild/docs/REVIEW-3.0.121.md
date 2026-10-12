# Hướng dẫn review — Donut AutoBuild 3.0.121

Đây là lần đầu mã nguồn AutoBuild được đưa lên repo, nên PR gồm **toàn bộ** mã (~30 nghìn dòng). Không cần đọc hết. Đọc theo thứ tự dưới đây:
1. mục 2: mã này lấy từ đâu, đã kiểm chứng ra sao;
2. mục 3: phần thay đổi của 3.0.121;
3. mục 5: các rủi ro.

Lý do từng lỗi ở [CHANGELOG](../CHANGELOG.md).

## 1. Tóm tắt trong 30 giây

| Vấn đề | Nguyên nhân | Sửa ở đâu | Trạng thái |
|---|---|---|---|
| Bấm Cobblestone mà server đặt Obsidian / Oak Leaves | Túi đồ phía client lệch server. Nguồn lệch: gộp xô 0,1 giây/lần theo túi đồ tự đoán; đổ/múc xô bị server từ chối; gói con trỏ của menu vừa đóng về muộn; chờ sau SWAP ngắn hơn 1 lượt ping | `InventoryAccess` (trạng thái `dirty` + xin đồng bộ); các cổng `InventoryAccess.synced()` trong `BuildController` | chưa chạy trong game |
| Không bao giờ mua được cúp netherite | trần giá chung 500K, shop bán ~5,1M | `SurvivalSupplies.PICKAXE_MAX_PRICE` = 15M | chưa chạy trong game |
| Mã 3.0.110–3.0.120 chỉ có dạng bản vá bytecode | phiên trước ghép bytecode (ASM) thẳng vào jar | ghép lại vào `BuildController` | mã nguồn ra jar giống jar phát hành |

## 2. Mã nguồn này từ đâu ra

Lịch sử phát hành không đi một đường thẳng:

```
3.0.17 ──(build bằng javac + stub trong container)──► … ──► 3.0.109   mã nguồn đầy đủ
3.0.109 jar ──(bản vá bytecode ASM, phiên khác)──► 3.0.110 … 3.0.120  chỉ có "patch-source"
3.0.120 jar + thay BuildController*, InventoryAccess, SurvivalSupplies ──► 3.0.121 jar (release/)
```

Mã trong PR được ghép từ 3 nguồn:

| Phần | Nguồn |
|---|---|
| `build/BuildController.java`, `InventoryAccess`, `SurvivalSupplies` | mã làm việc của 3.0.121. Thay đổi BuildController của 110–120 đã được ghép tay vào đây và so với jar 3.0.120 từng phương thức |
| GUI, `schematic/`, `mixin/`, `Freecam`, `FreecamMotion`, `PlacementRecoveryBudget`, `BuildQuality`, `HeldPlacementGuard`… | các lớp nguyên vẹn trong gói patch-source 3.0.120 |
| `AutoBuildMod.java` | mã 3.0.109, sửa `browseLayer` / `openMenu` gọi sang `SchematicEntrypoint` (khớp bytecode 3.0.120) |

### Kiểm chứng "mã nguồn = jar phát hành"

1. Biên dịch toàn bộ mã trong PR (javac, Java 21, stub Minecraft tên yarn), rồi remap sang tên intermediary.
2. So với `release/donut-autobuild-fabric-1.21.4-3.0.121.jar` bằng [`tools/compare-jar.sh`](../tools/compare-jar.sh):

```
== lớp chỉ có ở jar phát hành:          (không có)
== lớp chỉ có ở jar mới:                (không có)
== chữ ký field/method: 2932 vs 2932 dòng, khác nhau: 0
== lớp có method khác lời gọi/chuỗi: 0  (58 lớp, so lời gọi + hằng chuỗi của từng method)
```

- Không so byte-for-byte cả jar vì thứ tự hằng số và số thứ tự lambda của javac khác nhau giữa các lần build.
- Kiểm tra liên kết (mọi lời gọi tới Minecraft/Fabric/Litematica trong jar đều tồn tại): 0 lỗi.
- Tài nguyên (`lang/*.json`, `donutautobuild.freecam.mixins.json`) giống hệt trong jar. `fabric.mod.json` chỉ khác ở `${version}` và danh sách `jars` (Loom tự thêm khi build).
- Trước khi đưa lên, 6 dòng chú thích có toạ độ công trình đã được thay bằng chữ (vd. `(x, -33, z)`). Biên dịch lại sau khi thay: 108 file `.class` giống từng byte với bản trước khi thay.

## 3. Phần thay đổi của 3.0.121 — nên đọc ở đâu

### `build/InventoryAccess.java` (451 dòng, đọc hết được)

| Dòng | Hàm | Xem gì |
|---|---|---|
| 78–137 | `markDirty`, `markPour`, `noteClick`, `pourConfirmed`, `dirty` | khi nào túi đồ thành "chưa kiểm chứng"; đổ xô được server xác nhận thì không cần xin |
| 141–190 | `trace`, `dumpTrace` | nhật ký 40 thao tác; in `[TÚI ĐỒ]` khi đặt nhầm |
| 191–248 | `resyncHow`, `pollResync` | đổi cách xin đồng bộ sau 3 giây; hụt 3 lần → 10 phút chỉ chờ 1 ping |
| 249 | `synced` | **cổng chính**: false thì không đổi đồ / đặt / gộp xô |
| 305–340 | `requestResync`, `forceResync` | có bằng chứng lệch → khoá ngay |
| 341–441 | `tick` | lưới an toàn 8 giây / 20 giây; mở menu, con trỏ cầm đồ, mod dừng thì không tính giờ |
| 442 | `menuTick` | đóng menu xong → đánh dấu dirty |

### `build/BuildController.java` — chỉ các chỗ gắn nhãn `3.0.121`

`grep -n "3.0.121" BuildController.java` ra 8 chỗ:

| Dòng | Xem gì |
|---|---|
| 1232–1239 | chờ trọn 1 ping + 100 ms sau cú bấm ô đồ; `menuTick` chạy cả khi chỉ tạm dừng |
| 5359 | con trỏ tự dưng cầm đồ → cất vào túi + `markDirty` |
| 11657 | server xác nhận nước đã vào → `pourConfirmed` |
| 18031–18080 | `survivalSupplyTick`: mua đồ ăn/cúp; theo dõi menu `/shop` của việc này |
| 18650 | gộp xô: chỉ khi `synced`, mỗi lượt 1 cú |
| 18720–18732 | dùng xô: múc → `markDirty`; đổ → `markPour` |

Các cổng `InventoryAccess.synced(...)` nằm rải rác (dòng 4240, 5623, 8842, 10771, 11910, 12642, 14416, 17124…). Kiểm tra: không còn đường đặt khối nào bỏ qua cổng này. Lưới an toàn cuối cùng là `clickNow` (dòng 14397–14416).

### `build/SurvivalSupplies.java`

Dòng 21: `PICKAXE_MAX_PRICE = 15_000_000L`. Dòng 60: chỗ dùng.

## 4. Đọc phần còn lại thế nào

- Vào: `AutoBuildMod.onInitializeClient()` → mỗi tick `BuildController.tick()` (dòng 1176) → `nextTarget()` (2421) → các giai đoạn `select()` (1908), `equip()` (10735), `aim()` (10822), `waitBlock()` (11571), `orderTick()` (12203).
- Tự chạy lại / thoát ra vào lại: `stuckWatch()` (15625), `notePlaceFail()` (15731), `autoResumeTick()` (15793), lớp `Reconnector`.
- Giữ bản vẽ khi vào lại / mất lựa chọn: `keepPlanAfterRejoin()` (910), `keepPlanSelectionLost()` (15951).
- Góc đặt khối có hướng: `PlacementSolver` (871 dòng). An toàn TNT: `TntGuard` (558 dòng). Tìm đường: `Navigation` + `logic/PathSearch`.
- Menu server: `order/` (mỗi lớp là một máy trạng thái nhỏ có `tick()`).

## 5. Rủi ro / điểm cần người review quyết định

1. **Chưa chạy 3.0.121 trong game.** Các bản 110–120 có bộ kiểm thử chạy trên môi trường Minecraft/Fabric dựng riêng (`qa/`), nhưng bộ đó chưa chạy lại cho 3.0.121.
2. **Gradle chưa chạy thử với mã 3.0.121.** `build.gradle` lấy từ 3.0.17 (đã build được trên Windows; cùng danh sách module Fabric API). Từ đó tới nay có thêm 5 mixin (3.0.111). Mixin dùng **`remap = false`**: tên `@Shadow` là tên intermediary (tên yarn để ở `aliases`), mảng `method` ghi cả hai tên (vd. `{"update", "method_19321"}`). Vì vậy jar build ra không cần refmap. Lần build đầu nên chạy `tools/compare-jar.sh` để chắc ra cùng kết quả.
3. **Tên biến kiểu trình giải mã** (`var0`, `var1`…) trong `BuildController` và phần lớn `build/`, `order/`. Đổi tên là việc lớn, nên làm ở PR riêng và kiểm bằng `compare-jar.sh`; đổi tên biến cục bộ không làm đổi kết quả so sánh.
4. **Hàm `quality…` / `…Original`** (vd. `qualityPlacementVerifiedOriginal`, `qualityWaterAskedOriginal`) là vết của bản vá ASM 110–117: bản vá bọc hàm cũ, đổi tên hàm cũ thành `…Original`. Có thể gộp lại sau.
5. **Chú thích cũ không còn đúng:** ngay trên `notePlaceFail` (dòng 15725–15730) vẫn ghi "lần sau cần 50, rồi 100". Từ 3.0.110 ngưỡng luôn là 25 (`PlacementRecoveryBudget.LIMIT`).
6. **Lớp không dùng:** `logic/HumanRotation.java` không còn được gọi. Jar phát hành còn 2 lớp thừa `SchematicWorkspace$3` và `SchematicWorkspace$Bounds` (sót từ 3.0.120); mã nguồn không sinh ra chúng.
7. **GUI 3.0.120 bớt tab:** chỉ còn Công trình / Vật liệu / Cài đặt. Tab Home (3.0.99), Crafter (3.0.90), nút "Kho ender chest", "Reset tiền" và tab Bị chặn không còn trong GUI. Các tính năng vẫn chạy theo `config/donut-autobuild.json`, nhưng muốn đổi phải sửa file. Cần quyết định có đưa lại không.
8. **Trần giá cúp 15M** là số tiền lớn. Mod chỉ mua khi **không còn cúp dùng được** (hoặc cúp < 3 độ bền), mỗi lần 1 cái.
9. **`BuildController` 18.7 nghìn dòng trong 1 lớp.** Chưa tách vì mọi thay đổi đều phải so được với jar đang chạy. Nên tách theo giai đoạn ở các PR sau.

## 6. Cách thử lại

1. Chép jar trong `release/` vào `mods`, chỉ để 1 bản AutoBuild.
2. U → chọn file → Nạp → K. Xây một tầng có lá ngậm nước (cần mua xô ở `/shop`), để mod tự gộp xô rỗng và `/sell`.
3. Trong `latest.log`:
   - không có dòng "Đặt ra … thay vì …" hoặc "Server đặt … thay vì …";
   - nếu có, phải có khối `[TÚI ĐỒ]` ngay sau (thanh nóng + 14 thao tác gần nhất). Gửi đoạn đó để tìm nguồn lệch còn lại.
4. Cúp: bỏ hết cúp ra khỏi túi khi mod đang chạy. Mod phải `/shop` mua 1 Netherite Pickaxe (log có "You bought 1 Netherite Pickaxe"), rồi xây tiếp.
5. 25 lần đặt hỏng: log có "…/25 lần đặt thất bại chưa khắc phục" mỗi 5 lần; đủ 25 thì thoát ra vào lại rồi xây tiếp.
