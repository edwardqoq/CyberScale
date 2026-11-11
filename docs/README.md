# 文档索引

按你的目的挑一篇看：

| 我想… | 读这篇 |
|---|---|
| 把项目跑起来 / 装到手机上 | [01-quickstart.md](01-quickstart.md) |
| 理解代码怎么分层、数据怎么流动 | [02-architecture.md](02-architecture.md) |
| 适配另一台秤 / 查某个字节是什么意思 | [03-ble-protocol.md](03-ble-protocol.md) |
| 核对公式、BMI 分级标准、数据库迁移 | [04-metrics-and-database.md](04-metrics-and-database.md) |
| 了解视觉规范、动效清单、四屏结构 | [05-visual-design.md](05-visual-design.md) |
| 跑校验脚本 / 了解已知限制 | [06-verification-and-limits.md](06-verification-and-limits.md) |
| 把仓库推到 GitHub / 发 Release | [07-publish-to-github.md](07-publish-to-github.md) |
| 看四屏视觉与动效演示 | [preview.html](preview.html)（单文件，下载后用浏览器打开） |
| 翻早期的 UI 设计资料 | [design-history/](design-history/)（**历史方案，非当前视觉**） |

仓库根目录另有 [README](../README.md)（项目门面）、[CHANGELOG](../CHANGELOG.md)、[CONTRIBUTING](../CONTRIBUTING.md)、[LICENSE](../LICENSE)、[NOTICE](../NOTICE)。

---

## 阅读路径建议

- **只想装来用**：README → `01-quickstart.md` → Releases 下载 APK。
- **想改代码**：`01` → `02` → `03`（BLE）/ `04`（数据）→ `06`。
- **想做设计或换皮**：README → `05` → `preview.html` → `design-history/README.md`。
- **准备开源发布**：`07` → `06` → `CONTRIBUTING.md`。

## 文档口径约定

全仓库统一使用以下说法，写新文档时请沿用：

- 「**实测 / 遥测**」= 广播帧里真实存在的数据（体重、SEQ、properties、r1、MAC、RSSI）。
- 「**公式估算**」= 由体重 + 档案推算的 8 项指标，**不是秤测出来的**，不作医学诊断。
- 「**参考工程**」= 作者既有的旧工程 `youwo_app`，本项目只参考其 BLE 帧布局，不含其代码。
- 「**初版 UI 方案**」= `design-history/` 里那套暖白底设计，与当前赛博朋克实现无关。
