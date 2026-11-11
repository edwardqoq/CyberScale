# 03 · BLE 广播协议

## 硬件前提

实测机型 **康佳 KONKA KTZC-1804W-T**（云康 / OKOK Chipsea 2.0）。这类秤**只广播、不连接**：`connectGatt()` 会超时、`createBond()` 无响应。因此本项目删掉了参考工程里全部 GATT 连接 / 配对代码，只保留 `BluetoothLeScanner`。

manufacturer AD structure（AD type `0xFF`）完整 **21 字节**，含 2 字节公司 ID `0x20CA`（小端 `CA 20`）。

## 字节布局

| idx | 长度 | 样例 | 字段 | 说明 |
|---|---|---|---|---|
| 0 | 1 | `CA` | 公司 ID 低字节 / 帧头 | — |
| 1 | 1 | `20` | 公司 ID 高字节 / version | 本机 `0x20` |
| 2 | 1 | `0B` | dataLength | — |
| 3..4 | 2 | `1212` | productId | 大端 |
| 5..6 | 2 | `1212` | 保留 | — |
| 7 | 1 | `01` | deviceTypeFlag | 0=体脂秤，1=体重秤 |
| 8 | 1 | `05` | **scaleProperty** | bit4-3 单位、bit2-1 小数位、**bit0 最终值标志** |
| 9 | 1 | `25` | measureSeqNo | 每次称重 +1 |
| 10..11 | 2 | `1D74` | weight | uint16 大端原始值 |
| 12..13 | 2 | `03E8` | r1 | uint16 大端 ÷1000，本机恒为 1000 |
| 14 | 1 | `E1` | checksum | 参考工程未给算法，本项目仅记录、不参与过滤 |
| 15..20 | 6 | `AA BB CC DD EE FF` | MAC | 秤自身地址，优先用它，不依赖 `device.address` |

> 表中 MAC 为**脱敏后的示例值**（`AA BB CC DD EE FF`）。解析只读这 6 个字节的位置，不校验具体值；真实抓包地址不写入公开仓库。

## 实测样例

```
CA 20 0B 12 12 12 12 01 05 25 1D 74 03 E8 E1 AA BB CC DD EE FF
                        ↑  ↑  └─┬─┘ └──┬──┘ ↑        └──────┬──────┘
                  properties seq  weight  r1  checksum     MAC（示例值）
                   =0x05    =37   =7540   =1000
→ digitBits=2 → 7540 ÷ 100 = 75.40 kg ✓ 与秤面一致
```

## `scaleProperty` 语义

| 值 | 含义 |
|---|---|
| `0x05` | 最终值 / 已锁定，2 位小数 |
| `0x04` | 实时值，2 位小数 |
| `0x01` | 最终值，1 位小数 |
| `0x00` | 实时值，1 位小数 |

**bit0 = 最终值标志**是整套逻辑的开关：实时帧驱动 UI 动效，最终帧才进入稳定判定与落库。`0x01` / `0x00`（1 位小数）分支沿用参考工程实现，真机只在 `0x05` 上实测过。

## 单位换算

完整复刻参考工程 `YunkangBroadcast.parseWeight()`：

```
digitBits == 1 → 原始值；== 2 → ÷100；其余 → ÷1000
unitBits: 0 = kg, 1 = 市斤(×0.5), 2 = 磅(×0.4535924), 3 = 英石(×6.35029318)
                  → 统一换算成 kg 后入库
```

## 两处对参考工程的改进

1. **`ScanRecord.getManufacturerSpecificData()` 会剥掉 2 字节公司 ID**，直接按 21 字节偏移读会整体错位。`ScaleFrame.fromScanRecord()` 把 `companyId` 按小端补回再解析；若某 ROM 返回的 `SparseArray` 为空，则退化到手工遍历原始 AD structure（与参考工程 `fromAdv()` 一致）。
2. **写库时机**：参考工程帧变化就 `insert`，锁定后的重复广播会写几十条。本项目用 [MeasurementStabilizer](02-architecture.md) 的滑窗 + SEQ 去重收敛成一次测量。

> `checksum` 字节只记录、不参与过滤：参考工程没给出校验算法，硬校验有误杀真实帧的风险。

## 协议里没有的东西

体脂率、水分、肌肉量、基础代谢、骨量、内脏脂肪、温度、电量——**广播帧里不存在这些字段**，且不开放 GATT 读取。

本项目绝不把它们当作「秤体实测值」展示，也不填充伪造测量数据。界面上的「广播遥测」卡片只展示真实存在的 VERSION / PRODUCT ID / SEQ / PROPERTIES / R1 / RSSI / MAC。

v1.1 起另有一套**由体重 + 成员档案经公开公式推算**的估算指标，见 [04-metrics-and-database.md](04-metrics-and-database.md)。它与「秤体实测成分」是两回事：只在档案详情里展示、每处都标注估算口径，**不代表秤测出了这些值**，也不允许混进遥测卡片冒充实测。

## 想适配另一台秤

按影响面从小到大：

1. **单位 / 小数位不同** → 只改 `ScaleFrame.decodeWeight()` 的 `digitBits` / `unitBits` 映射。
2. **字节偏移不同** → 改 `ScaleFrame.parse()` 的索引常量与 `fromScanRecord()` 的公司 ID 补回逻辑，并同步更新本文件的字节表。
3. **properties 语义不同** → 改 `isFinal` 判定；稳定化规则可能也要调（`MeasurementStabilizer` 的滑窗时长、极差阈值）。
4. **厂商提供 GATT 服务**（能测体脂）→ 那已经是另一类硬件，需要新增连接层，并把「估算」与「实测」在数据模型上分开标记。

改完请跑 `python scripts/verify_project.py`，并尽量用真实抓包的 hex 帧补一条解析用例。
