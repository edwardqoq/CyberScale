# 02 · 架构与数据流

## 三句话

1. **数据流**：`BluetoothLeScanner` 持续扫描 → `ScaleFrame` 从 manufacturer data 解出体重 / 序号 / properties → `MeasurementStabilizer` 把高频帧收敛为一次「已锁定」测量 → `ScaleRepository` 写入 Room 并归属当前激活成员 → `StateFlow<AppUiState>` 驱动 Compose 重绘。
2. **组件分工**：`ble/` 只管协议与扫描，`data/` 只管本地持久化与统计，`vm/` 聚合状态与用户意图，`ui/` 只渲染与播放动效；四层之间没有 Android View / XML 依赖。
3. **多人模型**：`users` 与 `records` 一对多（外键 CASCADE）。每次锁定自动存到「当前测量对象」，Snackbar 提供 5 秒内「改归属」，避免家庭成员数据串档。

```
BLE 广播帧
   │  ScanRecord
   ▼
ble/ScaleFrame            解帧（21 字节 / 单位换算 / bit0 最终值标志）
   │  ScaleFrame
   ├──────────────► 实时帧：只更新 UI 数字、波纹、HEX 流（不落库）
   ▼
ble/MeasurementStabilizer 最终帧：2.5s 滑窗 + SEQ 去重 + 极差判定
   │  LockedMeasurement
   ▼
data/ScaleRepository      同事务计算 8 项指标 + 写 records + 更新成员起始体重
   │  Room (SQLite)
   ▼
vm/ScaleViewModel         StateFlow<AppUiState>
   ▼
ui/                       Compose 四屏 + Canvas 自绘图表 / 仪表
```

## 目录结构

```
app/src/main/java/com/cyberscale/app/
├── CyberScaleApp.kt              Application + 手动 DI 容器 AppContainer
├── MainActivity.kt               唯一 Activity，setContent { CyberScaleRoot() }
├── ble/
│   ├── ScaleFrame.kt             ★ 广播帧解析（字节布局复刻参考工程）
│   ├── BleScanController.kt      扫描循环 / 权限 / 状态流
│   └── MeasurementStabilizer.kt  稳定化 + SEQ 去重
├── data/
│   ├── Models.kt                 领域模型 / UI 数据结构
│   ├── Entities.kt               Room Entity（users / records）
│   ├── Daos.kt                   DAO：查询、更新、批量 upsert、改归属
│   ├── AppDatabase.kt            ★ version 2 + MIGRATION_1_2
│   ├── BodyComposition.kt        ★ 8 项公式 + BMI 国标四级
│   ├── ScaleRepository.kt        落库计算 / 历史补算 / 改归属重算
│   └── SettingsStore.kt / AppContainer.kt
├── vm/ScaleViewModel.kt          StateFlow 聚合 + 全部交互意图
└── ui/
    ├── CyberScaleRoot.kt         页面容器 / 底部导航 / AnimatedContent
    ├── Formatters.kt             单位换算与格式化
    ├── theme/                    Color / Type / Theme
    ├── components/               NeonComponents / NeonLineChart（Canvas 自绘）
    └── screens/                  Measure / Trend / History / Users
```

仓库根的 `scripts/` 放离线校验脚本，`docs/` 放文档与设计资料，`releases/` 放本地 APK（不入库）。

## 扫描循环（`BleScanController`）

- `SCAN_MODE_LOW_LATENCY` + `setReportDelay(0)`；**不设 `ScanFilter`**（不同 ROM 对 manufacturer data 过滤支持不一致），能否解析出云康帧即唯一判据。
- 协程循环「**25s 扫描 → 1s 空档**」，规避系统对长时间扫描的静默限流；权限授予 / 蓝牙开关变化会在循环里自动重试。
- `onStop()` 停扫描省电，`onStart()` 自动恢复。
- API 31+ 与 API ≤30 走不同权限分支；`device.address` 读取做 try/catch，优先使用帧内 MAC。

## 稳定化（`MeasurementStabilizer`）

参考工程只在 `bit0=1` 时解析体重、且帧变化就 `insert`，实时波动会写大量重复记录。本项目把「显示」和「落库」彻底分开：

| 规则 | 值 |
|---|---|
| 实时帧（bit0 = 0） | 只更新 UI，绝不落库 |
| 最终帧滑窗 | 2.5 s |
| 稳定判定 | 同 SEQ ≥ 2 条，极差 ≤ 0.05 kg |
| 去重 | 同 SEQ 只记一次；20 s 内相同体重（±0.05）不重复 |
| 过滤 | `0.5 < weight < 300` kg |

**切换成员会调用 `reset()`**，避免上一轮残留样本污染下一次判定。

## 多人归属与目标进度

- `users` 与 `records` 一对多，删除成员级联删记录。
- 保存后 Snackbar 显示「记录 → 成员名」，动作「改归属」把刚存的那条改挂到别的成员；重挂时按新成员档案**重算指标**，避免 A 的记录挂着 B 的体成分数据。
- 目标进度用 `(起始体重 − 当前) / (起始体重 − 目标)`，起始体重由该成员**第一条记录自动填充**。参考工程的 `weight / goal × 100` 在超重时恒为 100%，此处已修正。
- 数据库恒定以 **kg** 存储，切换显示单位（kg / 斤 / lb）只在渲染层乘系数，历史数值不会「变化」。

## 状态管理

`ScaleViewModel` 暴露单一 `StateFlow<AppUiState>`，Composable 只收集状态、只发送意图，不直接碰 Repository 或 BluetoothLeScanner。BLE 扫描在控制器内部用协程循环驱动，通过 `StateFlow`/回调把帧送进 Repository。没有 Hilt：依赖由 `AppContainer` 在 `CyberScaleApp` 中一次性构造，便于离线校验与小项目维护。

---

协议细节见 [03-ble-protocol.md](03-ble-protocol.md)，指标与迁移见 [04-metrics-and-database.md](04-metrics-and-database.md)。
