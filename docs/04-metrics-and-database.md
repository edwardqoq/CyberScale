# 04 · 身体指标、BMI 分级与数据库迁移

> 需求来源：v1.1 起，每个成员可保存性别 / 身高 / 出生年月日 / 目标体重；每次测量按档案计算并保存 8 项指标；BMI 按国标分级并在测量页驱动圆环配色。

## 1. 成员档案字段

| 字段 | 存储 | 说明 |
|---|---|---|
| 昵称 / 主题色 | `name` / `colorArgb` | v1.0 起即有 |
| 性别 | `gender` `0/1/2` | 未设置 / 男 / 女；体脂率、BMR、IBW 公式必需 |
| 身高 | `heightCm` | 50–250 cm，BMI 与全部公式必需 |
| 出生年月日 | `birthDate` `yyyy-MM-dd` | 原生 `DatePickerDialog`；**年龄不入库** |
| 目标体重 | `goalWeightKg` | 用于目标进度环 |
| 起始体重 | `startWeightKg` | 首条记录自动填充 |

**年龄按每条记录的时间戳算周岁**（`BodyComposition.ageAt()`），同一成员不同年份的记录会得到不同年龄，避免「存一次年龄、几年后全错」。编辑表单用 `ProfileDraft` 一次提交，避免多参数顺序错位。

## 2. 八项指标与公式口径

实现集中在 `data/BodyComposition.kt`（`sex`：男 = 1，女 = 0）：

| 指标 | 公式 |
|---|---|
| BMI 身体质量指数 | `体重(kg) / 身高(m)²` |
| 体脂率 BF% | **Deurenberg**：`1.20×BMI + 0.23×年龄 − 10.8×性别 − 5.4`，钳制 3–65% |
| 基础代谢 BMR | **Mifflin-St Jeor**：`10×体重 + 6.25×身高 − 5×年龄 + (男 +5 / 女 −161)` kcal/day |
| 理想体重 IBW | **Devine**：`(男 50.0 / 女 45.5) + 0.9×(身高cm − 152.4)` kg |
| 瘦体重 LBM | `体重 × (1 − 体脂率/100)` |
| 脂肪重量 FM | `体重 − LBM` |
| 相对体重指数 | `体重 / IBW × 100` % |
| 肥胖度 | `(体重 − IBW) / IBW × 100` % |

恒等式（已由 `scripts/verify_data_layer.py` 断言）：`LBM + FM = 体重`、`相对体重指数 = 肥胖度 + 100`。

> ⚠️ **口径声明**：这台纯广播秤只提供体重，上述 8 项全部是**由体重 + 档案经公开经验公式推算的估算值，不是秤体实测**，界面每处标注「公式估算、非实测、不作医学诊断」。Deurenberg / Mifflin-St Jeor / Devine 都是人群级公式，对运动员、老年人、儿童等个体偏差较大，不可用于医疗判断。换算法只改 `BodyComposition.kt` 一处，并同步更新本文档与校验脚本。

## 3. BMI 国标四级（中国成人，非 WHO 标准）

| 等级 | BMI 区间 | 等级色 | 用途 |
|---|---|---|---|
| 偏瘦 | `< 18.5` | `#38BDF8` 冷蓝 | 测量页徽章 + 圆环主弧 |
| 正常 | `18.5 ~ 23.9` | `#39FF88` 荧光绿 | 同上 |
| 超重 | `24.0 ~ 27.9` | `#FFB020` 琥珀 | 同上 |
| 肥胖 | `≥ 28.0` | `#FF2E97` 品红 | 同上 |
| 待评估 | 身高缺失 | `#00F0FF` 主青 | 回退到成员主题色 |

测量页在仪表正下方新增 **BMI 分级卡**：大号数值 + 等级徽章 + 四级刻度条（当前段点亮）+ 档案补全引导。中央 `NeonRingGauge` 的主弧扫掠渐变、外圈刻度、旋转光点、内圈虚线与数字光晕统一改用等级色；网格背景、扫描波纹、卡片描边仍用成员主题色，保证整体观感不被打散。

六个边界值（18.4 / 18.5 / 23.9 / 24.0 / 27.9 / 28.0）由 `verify_data_layer.py` 逐项断言。

## 4. 记录详情与历史补算

- 「档案」页每条记录可点开（行内 `›` 提示），详情弹窗展示体重、BMI 等级徽章、7 项派生指标、计算所用档案快照（身高 / 性别 / 按测量当天算的年龄）与估算口径声明，底部保留 SEQ / RSSI / MAC 原始遥测。
- 新测量在 `ScaleRepository.saveMeasurement()` 落库时**同事务**算好并写入 9 个指标列；「改归属」时按新成员档案重算。
- **档案不全**（性别未设置 / 生日为空 / 身高无效）时 `compute()` 返回 `null`，指标列保持 `NULL`，绝不编造。测量页的 BMI 分级只依赖身高，仍可正常显示。
- v1.0 遗留历史记录没有指标：补全档案保存后由 `backfillMetrics()` 自动补算，也可点「按当前档案补算」手动触发。补算用**当前**身高与性别、按**每条记录自己的时间戳**算年龄——App 未存档案历史快照，历史身高不可考。

## 5. Room v1 → v2 无损迁移

`AppDatabase` 从 `version = 1` 升到 `2`，**移除 `fallbackToDestructiveMigration()`**（它会静默清空真机历史数据），改为显式 `MIGRATION_1_2`：

```sql
ALTER TABLE users ADD COLUMN birthDate TEXT NOT NULL DEFAULT '';
UPDATE users SET birthDate = CAST(birthYear AS TEXT) || '-01-01'
  WHERE birthDate = '' AND birthYear >= 1900;   -- 旧年份回填，用户可在档案页改月日

ALTER TABLE records ADD COLUMN bmi REAL;
ALTER TABLE records ADD COLUMN bmiLevel TEXT;
ALTER TABLE records ADD COLUMN bodyFatPct REAL;
ALTER TABLE records ADD COLUMN bmrKcal REAL;
ALTER TABLE records ADD COLUMN ibwKg REAL;
ALTER TABLE records ADD COLUMN lbmKg REAL;
ALTER TABLE records ADD COLUMN fmKg REAL;
ALTER TABLE records ADD COLUMN relativeWeightIndex REAL;
ALTER TABLE records ADD COLUMN obesityDegreePct REAL;
```

要点：

- `birthYear` 列保留不删（低版本 SQLite 不支持 `DROP COLUMN`），在 Entity 里作为兼容字段。
- `birthDate` 的 `@ColumnInfo(defaultValue = "''")` 必须与迁移 SQL 严格对齐，否则 Room 运行时 schema 校验会失败。
- 9 个指标列全部**可空**：v1 历史记录没有这些数据，等补算。`hasMetrics` 是 `Entities.kt` 里的顶层扩展属性（`bmi != null`），**不是数据库列**，只用于 UI 判断是否显示详情入口。
- 覆盖安装不丢数据：包名与 debug 签名一致，`addMigrations(MIGRATION_1_2)` 只加列不重建表。

`python scripts/verify_data_layer.py` 会在内存 SQLite 里完整复现这个过程：建 v1 表 → 灌样本 → 执行从 `AppDatabase.kt` 抽取的真实迁移 SQL → 断言列结构、默认值、旧数据保留与年份回填，共 23 项断言。

> **改动 Entity 时的铁律**：数据库版本 +1、补显式 `Migration`、扩展校验脚本断言。健康数据宁可升级失败报错，也不要重新引入破坏性迁移。
