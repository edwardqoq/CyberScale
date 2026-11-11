# 07 · 上传 GitHub 操作指南

> 本仓库已按开源标准整理完毕：`.gitignore` / `.gitattributes` / `LICENSE`(Apache-2.0) / `NOTICE` / `README` / `CHANGELOG` / `CONTRIBUTING` / `docs/` / `scripts/` / `releases/` 全部就位，抓包 MAC 与本机路径已脱敏。
> **以下命令需要你在本机执行**，把 `YOUR_NAME` / `YOUR_REPO` 换成自己的账号和仓库名。

## 0. 上传前检查

在仓库根执行 `git add .` 后，`git status --short` 里**应该看到**：

```
app/  docs/  scripts/  gradle/  gradlew  gradlew.bat
.gitignore  .gitattributes  local.properties.example
LICENSE  NOTICE  README.md  CHANGELOG.md  CONTRIBUTING.md
build.gradle.kts  settings.gradle.kts  gradle.properties
releases/README.md  docs/design-history/…（4 PNG + 2 HTML + 2 DOCX）
```

**绝不应该看到**：

```
local.properties          ← 含本机 SDK 绝对路径
.gradle/  build/  app/build/  *.apk
*.jks  *.keystore
```

init 之后可用这条命令确认忽略规则真的生效：

```bash
git check-ignore -v local.properties releases/NEON-SCALE-v1.1.0-debug.apk app/build/outputs/apk/debug/app-debug.apk
```

三条都应输出「命中哪条 `.gitignore` 规则」。如果没有输出，说明规则没生效，**先别 push**。

## 1. 建仓库（网页）

在 GitHub 点 `New repository`：

| 项 | 建议 |
|---|---|
| Repository name | `neon-scale` 或 `cyberscale` |
| Description | `赛博朋克风格的纯 BLE 广播体重秤记录 App · Kotlin + Jetpack Compose · 本地 Room 存储` |
| Public / Private | 按需要选；已配 Apache-2.0，选 Public 即开源 |
| **不要勾选** | `Add a README file` / `Add .gitignore` / `Choose a license` —— 本地都有了，勾了会产生首次 push 冲突 |
| Topics | `android` `kotlin` `jetpack-compose` `bluetooth-low-energy` `room` `cyberpunk` `body-composition` |

建完后在仓库 `About` 里填 Website（若启用了 Pages）和 Topics，GitHub 会自动识别根目录 `LICENSE` 为 Apache-2.0。

## 2. 首次推送

### 方式 A · HTTPS（最常用）

```bash
cd "C:/Users/Administrator/Desktop/电子体重秤参考图/work/CyberScale"

git init
git config user.name  "你的名字"              # 已全局配置可跳过这两行
git config user.email "you@example.com"

git add .
git update-index --chmod=+x gradlew           # ★ Windows 必备：让 Linux/macOS clone 后 gradlew 可执行
git status                                   # ★ 按「第 0 节」逐项核对
git commit -m "chore: 初始化 NEON//SCALE 仓库 (v1.1.0)"

git branch -M main
git remote add origin https://github.com/YOUR_NAME/YOUR_REPO.git
git push -u origin main
```

> GitHub 已**不支持账号密码**推送。HTTPS 方式认证时，密码请填 **Personal Access Token**（Settings → Developer settings → Personal access tokens → 勾选 `repo`）。

### 方式 B · SSH（配过 key 的话更省事）

```bash
git remote add origin git@github.com:YOUR_NAME/YOUR_REPO.git
git push -u origin main
```

### 方式 C · GitHub CLI

```bash
gh auth login
git init && git add . && git update-index --chmod=+x gradlew && git commit -m "chore: 初始化 NEON//SCALE 仓库 (v1.1.0)"
gh repo create YOUR_REPO --public --source=. --remote=origin --push \
  --description "赛博朋克风格的纯 BLE 广播体重秤记录 App"
```

## 3. 后续更新

```bash
git add .
git status
git commit -m "fix: 简述改动"
git push
```

发新版本时：更新 `CHANGELOG.md` → 改 `app/build.gradle.kts` 的 `versionCode` / `versionName` → 打 tag → 发 Release：

```bash
git tag -a v1.1.1 -m "v1.1.1"
git push origin v1.1.1
```

## 4. 上传 APK 到 Releases

仓库页 `Releases → Draft a new release`，Tag 选 `v1.1.0`，标题 `NEON//SCALE v1.1.0`，把 `releases/NEON-SCALE-v1.1.0-debug.apk` 拖进附件区，发布即可。命令方式：

```bash
gh release create v1.1.0 releases/NEON-SCALE-v1.1.0-debug.apk \
  --title "NEON//SCALE v1.1.0" \
  --notes "档案字段补全、8 项身体成分估算、BMI 国标分级与圆环联动；覆盖安装不丢历史数据。"
```

> 建议 Release 说明里写清：这是 **debug 签名包**，包名带 `.debug` 后缀；从 v1.0 覆盖安装不会丢数据；8 项指标为公式估算而非实测。

## 5. 可选 · 在线展示预览页

仓库 `Settings → Pages → Build and deployment → Deploy from a branch`，Branch 选 `main`、目录选 `/docs` → Save。稍等片刻即可访问：

```
https://YOUR_NAME.github.io/YOUR_REPO/preview.html
```

`docs/README.md` 也会成为 Pages 首页。

## 6. 可选 · 自动构建（CI）

新建 `.github/workflows/android.yml`（**示例，未在本项目 CI 上实测**；GitHub runner 自带 Android SDK）：

```yaml
name: Android CI
on:
  push:
    branches: [main]
  pull_request:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: gradle
      - run: chmod +x ./gradlew
      - run: ./gradlew assembleDebug
      - run: python scripts/verify_project.py && python scripts/verify_data_layer.py
```

若 CI 报 SDK 缺失，在 `setup-java` 后加 `uses: android-actions/setup-android@v3`。

## 7. 常见坑

| 现象 | 原因 | 解法 |
|---|---|---|
| `Authentication failed` | GitHub 停用账号密码推送 | HTTPS 用 PAT，或改用 SSH key |
| `rejected: non-fast-forward` / README 冲突 | 建仓库时勾了自动初始化文件 | 先 `git pull --rebase origin main` 再 push；或重建空仓库 |
| `gradlew: Permission denied`（Linux / macOS / CI） | Windows 文件系统没有 Unix 执行位，直接 `git add` 常记成 `100644` | 首次提交前 `git update-index --chmod=+x gradlew`；已推送过则补一次 commit；CI 里也可 `chmod +x ./gradlew`，临时应急可用 `sh gradlew assembleDebug` |
| `File exceeds 100 MB` | 把 APK / 构建产物加进了仓库 | 用 Releases 分发二进制；`.gitignore` 已排除 `*.apk` 与 `build/` |
| 中文文件名显示成八进制转义 | Git 默认 `core.quotepath=true` | `git config --global core.quotepath false`（只影响显示，不影响文件） |
| `git add` 刷出一堆 `LF will be replaced by CRLF` 警告 | `.gitattributes` 正在把工作副本按平台换行转换，**仓库内始终存 LF** | 正常现象，可忽略；这正是 `gradlew` 在 Linux 上能执行的前提 |
| License 没被识别 | 文件名不叫 `LICENSE`，或内容不是标准全文 | 本仓库文件名为 `LICENSE`，内容为 Apache-2.0 官方原文 |
| 已经把 `local.properties` / 密钥推上去了 | 提交前没检查 | 立刻改本机配置 / 轮换密钥，再用 `git filter-repo --path local.properties --invert-paths` 清历史并强推（**会改写历史，协作前需协调**） |

## 8. 敏感信息复查（push 前最后一遍）

```bash
# git add . 之后、commit 之前执行：只扫描「即将被提交」的内容
git grep --cached -n -E "HermesData|Administrator|C8 FA DF D1 C9 3D|E:/HermesData|\.jks|\.keystore" || echo "OK：未发现敏感信息"
```

> 注：`local.properties.example` 里的 `sdk.dir=/path/to/your/Android/sdk` 是占位模板，属于正常内容；`gradle.properties` 里只有 `android.overridePathCheck=true`，不含本机路径。
