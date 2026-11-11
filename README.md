# NEON//SCALE

**赛博朋克风格的纯 BLE 广播体重秤记录 App** · Android · Kotlin + Jetpack Compose

![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-BOM_2024.06.00-4285F4?logo=jetpackcompose&logoColor=white)
![Room](https://img.shields.io/badge/Room-2.6.1-34A853)
![minSdk](https://img.shields.io/badge/minSdk-26-607D8B)
![version](https://img.shields.io/badge/version-1.1.0-00F0FF)
![License](https://img.shields.io/badge/License-Apache_2.0-blue)

![NEON//SCALE 四屏动态演示](https://gcore.jsdelivr.net/gh/edwardqoq/CyberScale@main/docs/demo/neon-scale-demo.gif)

<p align="center"><sub>12 秒演示 · 画面由真实界面代码渲染 · <a href="https://gcore.jsdelivr.net/gh/edwardqoq/CyberScale@main/docs/demo/neon-scale-demo.mp4">下载 1080p 版本</a> · <a href="docs/demo/README.md">素材说明</a></sub></p>

不使用 GATT 连接、不联网、无账号、无第三方 SDK。秤只负责广播一个体重数值，其余全部由本机完成：解析帧、稳定判定、落进 Room、驱动霓虹动效。

> **硬件范围**：针对**纯广播型**体重秤（实测机型：康佳 KONKA KTZC-1804W-T，云康 / OKOK Chipsea 2.0 协议）。它不接受 GATT 连接，广播帧里**只有体重**，没有体脂、肌肉、电量等成分数据。
>
> **数据口径**：档案里的 8 项身体成分（BMI / 体脂率 / BMR / IBW / LBM / FM / 相对体重指数 / 肥胖度）是**由体重 + 成员档案经公开经验公式推算的估算值，不是秤体实测**，界面每处都有标注，**不可用于医疗判断**。详见 [docs/04-metrics-and-database.md](docs/04-metrics-and-database.md)。

---

## 它能做什么

- **实时帧驱动动效、最终帧才落库**：广播里的实时值让数字跳动、波纹扩散；锁定值经 2.5s 滑窗稳定 + 同 SEQ 去重后才写一条记录，不会一次称重写进几十条。
- **多成员档案**：性别、身高、出生年月日（按记录当天自动算周岁）、目标体重；保存后 5 秒内可「改归属」，切换成员会重置稳定器。
- **BMI 国标四级**：偏瘦 / 正常 / 超重 / 肥胖，测量页显示等级徽章与刻度条，**中央旋转圆环按等级变色**。
- **8 项身体成分估算**：随每次测量同事务计算并保存；档案页点开任意一条记录可查看完整指标、当时所用档案快照与 SEQ / RSSI / MAC 原始遥测。
- **趋势与目标**：7D / 30D / 365D 发光折线、区间统计；目标进度按 `(起始−当前)/(起始−目标)` 计算。
- **赛博朋克视觉**：透视网格、扫描线、霓虹仪表、HEX 实时流；图表与图标全部 Canvas 自绘，无图表库 / 图标库依赖。
- **100% 本地存储**：Room + SQLite，无云同步、无网络权限。v1.0 → v1.1 走显式无损迁移，覆盖安装不丢历史记录。

---

## 快速开始

**前置条件**：JDK 17+、Android SDK Platform 34、Android 8.0（API 26）以上且支持 BLE 的**真机**（模拟器无法扫到真实广播）、Android Studio 或纯命令行。

```bash
git clone https://github.com/<你的用户名>/<仓库名>.git
cd <仓库名>
cp local.properties.example local.properties     # 把 sdk.dir 改成本机 SDK 路径
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Windows 用 `copy local.properties.example local.properties` 和 `gradlew.bat assembleDebug`；也可直接设置 `ANDROID_HOME` 而不建 `local.properties`。

> 首次启动会请求蓝牙扫描权限并自动创建默认成员「我」。**如果一直扫不到数据，先确认系统「定位服务」开关已打开**——多数国产 ROM 的硬性要求。
>
> 预编译的 debug APK 不放在仓库里，请从 `https://github.com/YOUR_NAME/YOUR_REPO/releases` 下载，或按上面命令自行构建。完整步骤见 [docs/01-quickstart.md](docs/01-quickstart.md)。

---

## 文档

| 文档 | 内容 |
|---|---|
| [docs/01-quickstart.md](docs/01-quickstart.md) | 环境、依赖坐标、权限声明、构建安装、常见坑 |
| [docs/02-architecture.md](docs/02-architecture.md) | 数据流、分层、目录结构、扫描循环、稳定化、多人归属 |
| [docs/03-ble-protocol.md](docs/03-ble-protocol.md) | 21 字节广播帧布局、properties 语义、单位换算、适配其它秤 |
| [docs/04-metrics-and-database.md](docs/04-metrics-and-database.md) | 8 项公式、BMI 国标分级、Room v1→v2 无损迁移 |
| [docs/05-visual-design.md](docs/05-visual-design.md) | 色板、动效清单、四屏说明、交互预览页 |
| [docs/06-verification-and-limits.md](docs/06-verification-and-limits.md) | 校验脚本、验证记录、已知限制、与参考工程对照 |
| [docs/07-publish-to-github.md](docs/07-publish-to-github.md) | 首次推送、后续更新、Releases 上传 APK、仓库设置建议 |
| [docs/demo/](docs/demo/) | 演示片素材：README 用 GIF、1080p MP4、海报、生成脚本 |
| [docs/design-history/](docs/design-history/) | 初版 UI 重设计方案（**历史资料，非当前视觉**） |
| [CHANGELOG.md](CHANGELOG.md) · [CONTRIBUTING.md](CONTRIBUTING.md) | 版本记录 · 参与开发 |

[docs/preview.html](docs/preview.html) 是四屏视觉 / 动效交互预览（单文件、无外部依赖）：**下载后本地用浏览器打开**，或访问 [GitHub Pages 在线预览](https://edwardqoq.github.io/CyberScale/)。

---

## 工程结构

```
CyberScale/                      ← Git 仓库根
├── app/src/main/
│   ├── java/com/cyberscale/app/
│   │   ├── ble/     # ScaleFrame 帧解析 · BleScanController 扫描 · MeasurementStabilizer 稳定化
│   │   ├── data/    # Room Entity/DAO · Repository · BodyComposition 公式 · AppContainer 手动 DI
│   │   ├── vm/      # ScaleViewModel：StateFlow<AppUiState>
│   │   └── ui/      # Compose 页面 / 组件 / 主题（Canvas 自绘）
│   ├── res/         # 主题、颜色、自适应图标、备份规则
│   └── AndroidManifest.xml
├── docs/            # 专题文档 + preview.html + demo/ + design-history/
├── scripts/         # 两个离线校验脚本（纯 Python，无需 Gradle / 真机）
├── tools/demo/      # 演示片源文件：stage.html 时间轴舞台 + 逐帧截图脚本
├── releases/        # 本地 APK（.gitignore 排除，走 GitHub Releases）
├── build.gradle.kts · settings.gradle.kts · gradle.properties
├── gradlew · gradlew.bat · gradle/wrapper/
├── .gitignore · .gitattributes · local.properties.example
├── LICENSE · NOTICE · README.md · CHANGELOG.md · CONTRIBUTING.md
```

---

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 / UI | Kotlin 1.9.24 · Jetpack Compose（BOM 2024.06.00 / Compiler 1.5.14）· Material 3 |
| 构建 | Gradle 8.7 · AGP 8.5.2 · Kotlin DSL · KSP 1.9.24-1.0.20 |
| 本地存储 | Room 2.6.1（显式迁移）· DataStore 风格设置封装 |
| 异步 / 状态 | Coroutines + StateFlow · ViewModel |
| 蓝牙 | Android BLE `BluetoothLeScanner`（只扫描，不连接） |
| 图表 / 图标 | Compose Canvas 自绘，**零第三方 UI 库** |
| 平台 | compileSdk / targetSdk 34 · minSdk 26 · JVM target 17 |

---

## 数据来源与免责声明

- 广播帧里**只有体重 / SEQ / properties / r1 / MAC**，本项目绝不把不存在的字段当作「秤体实测成分」展示，也不填充伪造测量值。
- 8 项身体成分采用 Deurenberg / Mifflin-St Jeor / Devine 等**人群经验公式**，对运动员、老年人、儿童等个体偏差较大，**不构成医疗建议或诊断依据**。
- 项目为个人学习型作品，未做上架合规、隐私政策、无障碍对比度优化与后台常驻服务。

---

## 设计演进

`docs/design-history/` 保存的是项目**更早一版**的 UI 界面优化方案（暖白底、深蓝渐变、薄荷绿数据色，针对旧 base 版 App）。当前实现是完全独立的赛博朋克视觉，两者**不是同一套设计**；这批资料仅作设计决策留档，请勿当作当前界面参考。

---

## 许可证

[Apache License 2.0](LICENSE) · 版权与第三方声明见 [NOTICE](NOTICE)。
