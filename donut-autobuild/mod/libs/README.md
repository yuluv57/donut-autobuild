# mod/libs — 3 jar phụ thuộc (không đưa lên repo)

`build.gradle` cần đúng 3 file này ở thư mục `mod/libs/`:

| File | Lấy ở đâu |
|---|---|
| `litematica-fabric-1.21.4-0.21.7.jar` | thư mục `mods` của profile đang chơi, hoặc Modrinth (Litematica 0.21.7 cho 1.21.4) |
| `malilib-fabric-1.21.4-0.23.5.jar` | như trên (MaLiLib 0.23.5) |
| `fabric-rendering-v1-10.1.2+b874120c04.jar` | module của Fabric API; được đóng kèm vào jar mod (`META-INF/jars`) |

Trên máy đang dùng, cả 3 file có sẵn ở `C:\mod donut\donut-autobuild-3.0.17\libs\`.
Không đưa vào git vì là jar của mod khác (Litematica/MaLiLib: LGPL-3.0).
