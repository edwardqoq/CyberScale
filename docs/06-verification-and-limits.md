# 06 · 校验、验证记录与已知限制

## 离线校验脚本

两个脚本都是纯 Python，**不需要 Gradle、Android SDK 或真机**，在仓库根执行：

```bash
python scripts/verify_project.py       # 工程结构：包名 / import / Manifest / Gradle 版本 / 括号平衡 / TODO
python scripts/verify_data_layer.py    # Room 迁移模拟 + 公式复算 + BMI 国标边界
python scripts/verify_project.py && python scripts/verify_data_layer.py && ./gradlew assembleDebug
```

`verify_project.py` 会扫描 25 个 Kotlin 源文件、101 个顶层声明、72 处内部 import 与 7 个资源文件，检查包名与目录一致、内部引用可解析、资源引用存在、Gradle 版本配对、括号平衡、无 TODO 残留。

`verify_data_layer.py` 的 23 项断言分三组：

- **A · 迁移**：从 `AppDatabase.kt` 抽取真实 SQL，在内存 SQLite 建 v1 表 → 灌样本 → 执行迁移 → 断言 11 条 SQL 可执行、列结构与 `@ColumnInfo(defaultValue)` 对齐、9 个指标列可空、2 条历史体重原值保留、`birthYear=1995` 回填为 `1995-01-01`、无有效年份不编造生日。
- **B · 公式**：3 组样例（男 30 / 女 30 / 男 45）逐项复算，断言 `LBM + FM = 体重`、`相对体重指数 = 肥胖度 + 100`、BMR / IBW / 体脂率在钳制区间。
- **C · BMI 边界**：18.4 / 18.5 / 23.9 / 24.0 / 27.9 / 28.0 六个边界值分级正确。

## 验证记录

| 时间 | 内容 | 结果 |
|---|---|---|
| 2026-11-11 | v1.0 `assembleDebug`（Gradle 8.7 + AGP 8.5.2 + JDK 21 + SDK 34） | **BUILD SUCCESSFUL，0 错误 / 0 警告** |
| 2026-11-11 | v1.0 真机 + 真实秤 | 项目所有者验证：**体重测量、数据保存等基本功能正常** |
| 2026-11-11 | v1.1 `assembleDebug` | 一次编译通过，**0 错误 / 0 警告** |
| 2026-11-11 | v1.1 APK 静态检查 | `apksigner verify`：v2 签名有效；`aapt2 dump badging`：`versionCode=2` / `versionName=1.1.0-debug` / minSdk 26 / targetSdk 34 / BLE 权限齐全；包体 9,175,547 bytes |
| 2026-11-11 | v1.1 数据层 | `verify_data_layer.py` **23 项断言全通过** |
| 2026-11-11 | v1.1 真机 + 真实秤 | 项目所有者验证：**测试完毕，无问题**（含档案字段、记录详情、BMI 分级与圆环变色） |

v1.0 首轮编译曾修复 4 类真实问题：`InfiniteTransition.animateFloat` 扩展 import 缺失（连带 10 处类型歧义）、`TextPrimary` import 缺失、`UsersScreen.onAdd` 与 `ViewModel.addUser` 参数顺序错位、一处冗余安全调用。

## 已知限制

如实列出，避免误用：

- **指标是估算不是实测**：Deurenberg / Mifflin-St Jeor / Devine 均为人群经验公式，对运动员、老年人、儿童偏差较大，不可用于医疗判断。公式集中在 `BodyComposition.kt`，可按需替换。
- **历史补算用当前档案**：补算 v1.0 遗留记录时使用**当前**身高与性别，而非记录当时值（App 未存档案历史快照）；只有年龄按记录时间戳算。
- **1 位小数分支未真机验证**：`properties` 的 `0x01` / `0x00` 沿用参考工程实现，真机只在 `0x05`（最终值 2 位小数）上实测过。若发现偏差，改 `ScaleFrame.decodeWeight()` 一处。
- **checksum 不校验**：参考工程未给出算法，硬校验有误杀真实帧的风险，目前仅记录展示。
- **依赖国产 ROM 的定位服务开关**：即使声明 `neverForLocation`，多数 ROM 仍要求系统定位服务打开才能扫到广播。
- **硬件范围窄**：只验证过一台纯广播秤；带 GATT 的体脂秤需另写连接层。
- **未做的功能**：图表手势交互、CSV 导出、浅色主题（本设计只有暗色）、无障碍对比度优化、后台常驻服务、release 签名与上架合规材料、首启空态引导上秤动效。
- **未做自动化 UI 测试**：目前只有静态校验 + 数据层离线校验 + 真机人工验证，没有 Instrumentation / Compose UI Test。

## 与参考工程对照

| 维度 | `youwo_app`（参考） | NEON//SCALE（本工程） |
|---|---|---|
| 语言 / UI | Java + XML View + 位图表盘 | Kotlin + Jetpack Compose + Canvas 自绘 |
| 构建 | aapt2 手工脚本，无 Gradle | Gradle 8.7 + AGP 8.5.2（Kotlin DSL） |
| 存储 | `SQLiteOpenHelper`，单用户 | Room，多人一对多 + 外键级联 + 显式迁移 |
| 蓝牙 | BLE 扫描 + GATT 连接代码（本机秤用不上） | **只扫描**，删除全部连接 / 配对逻辑 |
| 实时体验 | 只显示最终值 | 实时帧驱动数字滚动 / 波纹，最终帧才落库 |
| 多人 | 无 | 成员增删改切换 + 保存后改归属 |
| 视觉 | 青色顶栏 + 白底 | 赛博朋克霓虹 + 数据遥测语言 |
| 身体成分 | 字段留着、显示 `--` | **不展示秤体实测成分**（协议里没有）；v1.1 起以「体重 + 档案」公式估算 8 项，明确标注非实测 |
| BMI 分级 | 无 | 国标四级 + 等级色驱动测量页中央圆环 |

参考工程只用于确认广播帧字节布局，**本仓库不含其代码、资源或构建脚本**。
