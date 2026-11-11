# 参与开发

欢迎提 Issue / PR。这是个结构简单的单人项目，没有复杂的贡献流程，按下面几步走即可。

## 1. 跑起来

按 [docs/01-quickstart.md](docs/01-quickstart.md) 配好 JDK 17 + SDK 34，确认 `./gradlew assembleDebug` 能过。真机 BLE 功能无法在模拟器上验证，涉及扫描 / 落库的改动请说明你的验证方式（真机 / 静态校验 / 仅编译）。

## 2. 改动前先读

| 想改什么 | 先看 |
|---|---|
| 广播帧格式、单位、properties 语义 | [docs/03-ble-protocol.md](docs/03-ble-protocol.md) |
| 体成分公式、BMI 分级、数据库表结构 | [docs/04-metrics-and-database.md](docs/04-metrics-and-database.md) |
| 视觉、色板、动效 | [docs/05-visual-design.md](docs/05-visual-design.md) |
| 已知限制、还没做的事 | [docs/06-verification-and-limits.md](docs/06-verification-and-limits.md) |

**两条不能破的约束**：

1. 广播帧里没有的成分数据（体脂 / 肌肉 / 水分 / 电量）不得作为「实测值」展示或伪造填充。
2. 估算指标必须保留「公式估算、非实测、不作医学诊断」的标注，且不得混进遥测卡片冒充实测。

## 3. 提交前自检

```bash
python scripts/verify_project.py        # 工程结构 / 包名 / import / Gradle 版本 / 括号 / TODO
python scripts/verify_data_layer.py     # 迁移 SQL 模拟 / 公式守恒 / BMI 国标边界
./gradlew assembleDebug                 # 真编译
```

改过 Room Entity / 数据库版本时，**必须**同步补一条显式 `Migration` 并扩展 `verify_data_layer.py` 的断言。历史健康数据宁可升级失败报错，也不能静默清库——不要重新引入 `fallbackToDestructiveMigration()`。

改过公式常量时，同步更新 `docs/04-metrics-and-database.md` 与校验脚本里的样例期望值。

## 4. 风格与提交信息

- Kotlin 官方代码风格（`kotlin.code.style=official`），4 空格缩进；Compose 组件保持无状态、状态集中在 `ScaleViewModel` 的 `StateFlow`。
- 新增依赖必须写**固定版本**，不要用 `latest.release`；Compose Compiler 与 Kotlin 版本必须配对。
- 提交信息用中文或英文均可，格式 `类型: 简述`，如 `fix: 修正 1 位小数 properties 分支`、`docs: 补充协议字段说明`。
- PR 描述里请写清：改了什么、怎么验证的、是否影响已有数据库数据。

## 5. 不要提交的东西

`local.properties`、`.gradle/`、`build/`、`*.apk`、`*.jks` / `*.keystore` 都已在 `.gitignore` 中排除。签名密钥、真实抓包里的设备 MAC、本机绝对路径请勿写进代码或文档（现有文档中的样例 MAC 已替换为示例值）。
