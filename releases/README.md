# releases/ — 本地安装包

**本目录下的 `*.apk` 不进 Git 仓库**（见根目录 `.gitignore`）。二进制包会让仓库体积虚胖、且每次构建都产生无意义的 diff；分发请用 GitHub Releases 附件。

## 本地现有文件

| 文件 | 说明 |
|---|---|
| `NEON-SCALE-v1.1.0-debug.apk` | **当前版本**，9,175,547 bytes，`versionCode 2`，debug keystore v2 签名 |
| `NEON-SCALE-v1.0.0-debug.apk` | v1.0 旧包，留作回滚对照 |

两者包名与签名一致（`com.cyberscale.app.debug`），**从 v1.0 覆盖安装到 v1.1 不会丢历史数据**。

```bash
adb install -r releases/NEON-SCALE-v1.1.0-debug.apk
```

## 自己构建

```bash
./gradlew assembleDebug     # Windows: gradlew.bat assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

这是 **debug 构建**（`applicationIdSuffix = ".debug"`、可调试）。正式发布前需去掉 debug 后缀、为 release 配置 `signingConfig`，再执行 `./gradlew assembleRelease`；签名密钥绝不能提交进仓库。

## 发到 GitHub Releases

在仓库页 `Releases → Draft a new release`，Tag 填 `v1.1.0`，把本目录的 APK 拖进附件区即可。命令方式见 [docs/07-publish-to-github.md](../docs/07-publish-to-github.md)。
