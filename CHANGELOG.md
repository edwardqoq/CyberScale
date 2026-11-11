# Changelog

本项目版本号遵循 `主版本.次版本.修订号`，格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## [1.1.0] — 2026-11-11

`versionCode = 2` · `versionName = 1.1.0` · debug 构建 `1.1.0-debug`

### Added

- 成员档案新增**性别、身高、出生年月日（原生日期选择器，按记录时间戳自动算周岁）、目标体重**；档案卡片展示「性别 · 年龄 · 身高」「出生日期」「目标 / 起始」。
- 每次测量落库时同事务计算并保存 8 项指标：BMI、体脂率、BMR、IBW、LBM、FM、相对体重指数、肥胖度（`data/BodyComposition.kt`）。
- 档案页每条记录可点开**详情弹窗**：指标全览、BMI 等级徽章、计算所用档案快照、SEQ / RSSI / MAC 原始遥测与估算口径声明。
- **BMI 中国成人国标四级**（偏瘦 / 正常 / 超重 / 肥胖）：测量页新增分级卡（大号数值 + 徽章 + 四级刻度条 + 档案补全引导）。
- 中央 `NeonRingGauge` 的主弧、刻度、旋转光点、数字光晕按 BMI 等级联动变色。
- 历史记录**补算**：补全档案后自动 `backfillMetrics()`，也可点「按当前档案补算」手动触发；「改归属」时按新成员档案重算。
- 新增离线校验 `scripts/verify_data_layer.py`：迁移 SQL 模拟、公式守恒、BMI 六个国标边界，共 23 项断言。

### Changed

- Room 数据库 `version 1 → 2`，改用显式 `MIGRATION_1_2`（只加列、回填 `birthDate`），**移除 `fallbackToDestructiveMigration()`**，覆盖安装不再清空真机历史数据。
- 档案不全（性别未设 / 生日为空 / 身高无效）时指标列保持 `NULL`，绝不编造；BMI 分级只依赖身高，仍可显示。
- 文档口径由「不展示、不估算任何身体成分」更新为「**公式估算、非实测、不作医学诊断**」，界面每处标注。
- 顶栏 / 预览页版本号统一为 v1.1。

### Verified

- `assembleDebug` 0 错误 0 警告；`apksigner verify` 确认 v2 签名有效；`aapt2 dump badging` 确认 versionCode / minSdk / BLE 权限。
- **由项目所有者在真机 + 真实秤上完成验证，称重、保存、档案与 v1.1 新功能均通过。**

---

## [1.0.0] — 2026-11-11

`versionCode = 1` · 首个可安装版本

### Added

- 以既有工程的 BLE 广播协议实现为参考，从零搭建 Kotlin + Jetpack Compose 工程（不沿用 Java + XML + aapt2 手工构建）。
- 纯 BLE 广播扫描与 21 字节帧解析：处理 `getManufacturerSpecificData()` 剥掉公司 ID 的偏移问题，并保留手工遍历 AD structure 的退化路径。
- `MeasurementStabilizer`：2.5s 滑窗 + 同 SEQ ≥ 2 + 极差 ≤ 0.05kg + 20s 同值兜底，解决参考工程一次称重写几十条重复记录的问题。
- 多成员 Room 存储（`users` / `records` 一对多 + 外键级联）、保存后「改归属」、目标进度修正为 `(起始−当前)/(起始−目标)`。
- 四屏：测量 / 趋势 / 档案 / 成员；赛博朋克霓虹视觉与 Canvas 自绘图表、图标、动效。
- 广播遥测诊断卡（HEX / SEQ / RSSI / MAC）与 `scripts/verify_project.py` 静态一致性校验。

### Verified

- **由项目所有者在真机 + 真实秤上验证：体重测量与数据保存等基本功能正常。**
