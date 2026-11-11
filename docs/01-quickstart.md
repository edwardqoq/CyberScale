# 01 · 快速开始与环境

## 前置条件

| 项 | 要求 | 说明 |
|---|---|---|
| JDK | **17**（推荐） | 实测 21 亦可构建，Kotlin `jvmTarget = 17` |
| Android SDK | Platform **34** + Build Tools | 在 `local.properties` 的 `sdk.dir` 或 `ANDROID_HOME` 中指定 |
| Gradle | **8.7**（Wrapper 已附带） | 无需本机预装 Gradle |
| Android Studio | 任意较新版本 | 非必须，纯命令行也能构建 |
| 真机 | **Android 8.0 / API 26+ 且支持 BLE** | 模拟器扫不到真实广播；BLE 功能必须真机验证 |

## 构建版本矩阵（全部固定版本，可复现）

| 组件 | 版本 | 卡点 |
|---|---|---|
| Android Gradle Plugin | `8.5.2` | 需 Gradle 8.7 |
| Gradle Wrapper | `8.7` | `gradlew` + `gradle-wrapper.jar` 已随仓库提供 |
| Kotlin | `1.9.24` | JVM target 17 |
| KSP | `1.9.24-1.0.20` | 必须含与 Kotlin 一致的小版本，否则编译报错 |
| Compose Compiler | `1.5.14` | **必须与 Kotlin 1.9.24 严格配对** |
| Compose BOM | `2024.06.00` | 组件版本由 BOM 统一管理 |
| Room | `2.6.1` | runtime / ktx / compiler 三者同版本 |
| lifecycle / coroutines | `2.8.2` / `1.8.1` | — |
| compileSdk / targetSdk / minSdk | `34` / `34` / `26` | BLE 权限按 API 31 分支 |

### `app/build.gradle.kts` 依赖坐标

```kotlin
implementation(platform("androidx.compose:compose-bom:2024.06.00"))
implementation("androidx.core:core-ktx:1.13.1")
implementation("androidx.activity:activity-compose:1.9.0")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.ui:ui-graphics")
implementation("androidx.compose.ui:ui-tooling-preview")
implementation("androidx.compose.foundation:foundation")
implementation("androidx.compose.material3:material3")
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")
```

> 项目**没有** Hilt、Retrofit、图表库、图标库或任何第三方 SDK。DI 由 `AppContainer` 手写，图表与图标由 Compose Canvas 自绘。

### Manifest 权限

| 权限 | 适用范围 | 原因 |
|---|---|---|
| `BLUETOOTH` / `BLUETOOTH_ADMIN` / `ACCESS_FINE_LOCATION` | `maxSdkVersion=30` | API 30 及以下扫描 BLE 必须 |
| `BLUETOOTH_SCAN`（`neverForLocation`） | API 31+ | 声明不用于定位，可免定位权限 |
| `BLUETOOTH_CONNECT` | API 31+ | 读取设备信息用；**本 App 不建立 GATT 连接** |
| `android.hardware.bluetooth_le` | `required=false` | 无 BLE 设备也能安装 |

## 构建与安装

```bash
# 方式 A：Android Studio
#   File > Open > 选择仓库根目录 → 等待 Gradle Sync → Run

# 方式 B：命令行
cp local.properties.example local.properties   # 改 sdk.dir；或设 ANDROID_HOME
./gradlew assembleDebug                        # Windows: gradlew.bat assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

产物：`app/build/outputs/apk/debug/app-debug.apk`，约 8.8 MB。预编译包也可从 GitHub Releases 下载，见 [../releases/README.md](../releases/README.md)。

### debug 与 release

当前 `debug` 构建类型带 `applicationIdSuffix = ".debug"` 与 `versionNameSuffix = "-debug"`，实际包名 `com.cyberscale.app.debug`，可调试。正式发布前需要：

1. 去掉 debug 的两个后缀（或另配 release 的 `applicationId`）；
2. 为 release 配置 `signingConfig`，密钥文件放本地且不入库；
3. 执行 `./gradlew assembleRelease`，并用 `apksigner verify` 验签。

> 与旧版共存安装时，`.debug` 后缀反而方便；但要注意不同包名 = 不同数据目录，**换成正式包名会读不到旧数据**。

### 首次启动

自动创建默认成员「我」，并请求蓝牙扫描权限。**若一直扫不到数据，先打开系统「定位服务」开关**——多数国产 ROM 即使声明 `neverForLocation` 也强制要求。

## 常见坑

| 现象 | 原因 | 解法 |
|---|---|---|
| 扫描一直无结果、无报错 | 国产 ROM 要求系统定位服务开启 | 打开定位服务；代码已做 25s 扫描自动重启 |
| `Could not find SDK / sdk.dir` | 没建 `local.properties` 也没设 `ANDROID_HOME` | 复制 `local.properties.example` 并改路径 |
| AGP 报「路径含非 ASCII 字符」 | 工程 clone 到了中文目录 | `gradle.properties` 已设 `android.overridePathCheck=true`；本项目无 NDK/CMake，实测通过。若后续引入 native 工具链，请移到纯 ASCII 路径 |
| `Compose Compiler / Kotlin 不兼容` | 两者版本未配对 | 固定 `1.9.24` ↔ `1.5.14` |
| KSP 报版本不匹配 | KSP 版本号含 Kotlin 小版本 | 用 `1.9.24-1.0.20` |
| `android:exported` 编译报错 | targetSdk 31+ 要求显式声明 | `MainActivity` 已设 `exported="true"` |
| `gradlew: bad interpreter` | Windows 把 `gradlew` 换行改成 CRLF | `.gitattributes` 已强制 `eol=lf`；本地可执行 `git add --renormalize .` 或 `sed -i 's/\r$//' gradlew` |
| `./gradlew: Permission denied` | Windows 提交的 `gradlew` 没带 Unix 执行位 | 提交前 `git update-index --chmod=+x gradlew`；临时可直接 `sh gradlew assembleDebug`（Windows 用 `gradlew.bat`） |
| `gradlew` 一直卡在 `Downloading gradle-8.7-bin.zip` | wrapper 缓存里没有分发包，需联网下载约 130 MB | 换网络 / 用 Android Studio 打开（它会自行处理）/ 直接调用本机已解压的 Gradle 8.7：`gradle assembleDebug --no-daemon`。**这不影响 GitHub 上的仓库**，clone 者首次构建本就会自动下载 |
| 读 `device.address` 抛 `SecurityException` | API 31+ 需 `BLUETOOTH_CONNECT` | 优先用帧内 `[15..20]` 的 MAC，系统 address 仅作 fallback |
| 一次称重写进几十条重复记录 | 秤锁定后重复广播同一帧 | 滑窗稳定 + SEQ 去重 + 20s 同值兜底，见 [02-architecture.md](02-architecture.md) |
