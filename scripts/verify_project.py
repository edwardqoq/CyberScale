# -*- coding: utf-8 -*-
"""静态一致性校验（不依赖 Gradle / Android SDK）。
检查：文件清单、包名与目录、内部引用、资源引用、Gradle 版本、括号平衡、TODO 残留。
"""
import os
import re
import sys
from pathlib import Path

# 本脚本位于 <仓库根>/scripts/，工程根是上一级目录
ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app" / "src" / "main"
KT_ROOT = SRC / "java"

issues = []
notes = []

EXPECTED_KT = [
    "com/cyberscale/app/CyberScaleApp.kt",
    "com/cyberscale/app/MainActivity.kt",
    "com/cyberscale/app/ble/ScaleFrame.kt",
    "com/cyberscale/app/ble/BleScanController.kt",
    "com/cyberscale/app/ble/MeasurementStabilizer.kt",
    "com/cyberscale/app/data/Models.kt",
    "com/cyberscale/app/data/Entities.kt",
    "com/cyberscale/app/data/Daos.kt",
    "com/cyberscale/app/data/AppDatabase.kt",
    "com/cyberscale/app/data/ScaleRepository.kt",
    "com/cyberscale/app/data/SettingsStore.kt",
    "com/cyberscale/app/data/AppContainer.kt",
    "com/cyberscale/app/vm/ScaleViewModel.kt",
    "com/cyberscale/app/ui/Formatters.kt",
    "com/cyberscale/app/ui/CyberScaleRoot.kt",
    "com/cyberscale/app/ui/theme/Color.kt",
    "com/cyberscale/app/ui/theme/Type.kt",
    "com/cyberscale/app/ui/theme/Theme.kt",
    "com/cyberscale/app/ui/components/NeonComponents.kt",
    "com/cyberscale/app/ui/components/NeonLineChart.kt",
    "com/cyberscale/app/ui/screens/MeasureScreen.kt",
    "com/cyberscale/app/ui/screens/TrendScreen.kt",
    "com/cyberscale/app/ui/screens/HistoryScreen.kt",
    "com/cyberscale/app/ui/screens/UsersScreen.kt",
]

EXPECTED_RES = [
    "AndroidManifest.xml",
    "res/values/strings.xml",
    "res/values/colors.xml",
    "res/values/themes.xml",
    "res/xml/backup_rules.xml",
    "res/xml/data_extraction_rules.xml",
    "res/mipmap-anydpi-v26/ic_launcher.xml",
    "res/drawable/ic_launcher_foreground.xml",
]

EXPECTED_ROOT = [
    "settings.gradle.kts",
    "build.gradle.kts",
    "gradle.properties",
    "gradle/wrapper/gradle-wrapper.properties",
    "app/build.gradle.kts",
    "app/proguard-rules.pro",
    "README.md",
]

for rel in EXPECTED_ROOT:
    if not (ROOT / rel).exists():
        issues.append("缺少根文件: " + rel)
for rel in EXPECTED_RES:
    if not (SRC / rel).exists():
        issues.append("缺少资源: " + rel)
for rel in EXPECTED_KT:
    if not (KT_ROOT / rel).exists():
        issues.append("缺少源码: " + rel)

# ---- 收集所有 Kotlin 文件与顶层声明 ----
kt_files = sorted(KT_ROOT.rglob("*.kt"))
declared = {}
for f in kt_files:
    text = f.read_text(encoding="utf-8")
    rel = f.relative_to(KT_ROOT).as_posix()
    pkg = re.search(r"^package\s+([\w.]+)", text, re.M)
    if not pkg:
        issues.append(f"{rel}: 缺少 package 声明")
        continue
    # KT_ROOT 就是 java/ 根，目录路径本身即完整包名
    expected_pkg = ".".join(rel.split("/")[:-1])
    if pkg.group(1) != expected_pkg:
        issues.append(f"{rel}: package={pkg.group(1)} 与目录期望 {expected_pkg} 不一致")

    for m in re.finditer(
        r"^(?:@\w+[^\n]*\n)*(?:public\s+|private\s+|internal\s+)?"
        r"(?:data\s+|enum\s+|sealed\s+|abstract\s+)*"
        r"(?:class|interface|object|fun)\s+(\w+)",
        text, re.M
    ):
        name = m.group(1)
        declared.setdefault(name, []).append(rel)
    # 顶层 val / 属性（Color.kt / Formatters.kt 等）
    # 支持扩展属性 `val Foo.bar: Type get() = ...`，此时声明名是 bar 而不是 Foo
    for m in re.finditer(r"^val\s+(?:[\w.]+\.)?(\w+)", text, re.M):
        declared.setdefault(m.group(1), []).append(rel)

    # 括号平衡：必须先替换字符串（字符串里可能含 // 或括号），再删注释
    stripped = re.sub(r'"(?:\\.|[^"\\])*"', '""', text)
    stripped = re.sub(r"/\*.*?\*/", "", stripped, flags=re.S)
    stripped = re.sub(r"//[^\n]*", "", stripped)
    for op, cl in [("{", "}"), ("(", ")"), ("[", "]")]:
        if stripped.count(op) != stripped.count(cl):
            issues.append(f"{rel}: {op}{cl} 数量不平衡 {stripped.count(op)} vs {stripped.count(cl)}")

    if "TODO" in text or "// ..." in text or "伪代码" in text:
        issues.append(f"{rel}: 存在 TODO / 省略标记")

# ---- 同一顶层类名重复 ----
for name, files in declared.items():
    if len(set(files)) > 1 and name in ("AppContainer", "CyberScaleApp", "ScaleViewModel", "ScaleFrame"):
        issues.append(f"重复定义顶层类 {name}: {sorted(set(files))}")

# ---- 内部 import 是否有定义 ----
internal_imports = set()
for f in kt_files:
    text = f.read_text(encoding="utf-8")
    for m in re.finditer(r"^import\s+(com\.cyberscale\.app\.[\w.]+)", text, re.M):
        internal_imports.add(m.group(1))
for imp in sorted(internal_imports):
    leaf = imp.split(".")[-1]
    # 包级 import（如 com.cyberscale.app.ui.theme.Mono） leaf 是顶层声明
    if leaf not in declared:
        issues.append(f"内部 import 找不到声明: {imp} (leaf={leaf})")

# ---- Manifest 资源引用 ----
manifest = (SRC / "AndroidManifest.xml").read_text(encoding="utf-8")
res_defs = {
    "@string/app_name": "res/values/strings.xml",
    "@style/Theme.CyberScale": "res/values/themes.xml",
    "@xml/backup_rules": "res/xml/backup_rules.xml",
    "@xml/data_extraction_rules": "res/xml/data_extraction_rules.xml",
    "@mipmap/ic_launcher": "res/mipmap-anydpi-v26/ic_launcher.xml",
}
for token, path in res_defs.items():
    if token in manifest and not (SRC / path).exists():
        issues.append(f"Manifest 引用 {token} 但 {path} 不存在")

themes = (SRC / "res/values/themes.xml").read_text(encoding="utf-8")
colors = (SRC / "res/values/colors.xml").read_text(encoding="utf-8")
for color_name in re.findall(r"@color/(\w+)", themes):
    if f'name="{color_name}"' not in colors:
        issues.append(f"themes.xml 引用了不存在的颜色 @color/{color_name}")

launcher = (SRC / "res/mipmap-anydpi-v26/ic_launcher.xml").read_text(encoding="utf-8")
for token in re.findall(r"@(?:color|drawable)/(\w+)", launcher):
    found = (f'name="{token}"' in colors) or (SRC / f"res/drawable/{token}.xml").exists()
    if not found:
        issues.append(f"ic_launcher.xml 引用了不存在的资源 {token}")

# ---- Application 类与 Manifest 对齐 ----
m = re.search(r'android:name="(\.[\w.]*CyberScaleApp|\.CyberScaleApp)"', manifest)
if not m:
    issues.append("Manifest 未注册 CyberScaleApp")
else:
    app_file = (KT_ROOT / "com/cyberscale/app/CyberScaleApp.kt").read_text(encoding="utf-8")
    if "class CyberScaleApp : Application()" not in app_file:
        issues.append("CyberScaleApp.kt 中类定义不符合 Manifest 注册")

# ---- Gradle 版本一致性 ----
root_gradle = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
app_gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
wrapper = (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")
if '8.5.2' not in root_gradle:
    issues.append("AGP 版本异常")
if '1.9.24' not in root_gradle:
    issues.append("Kotlin 版本异常")
if '1.5.14' not in app_gradle:
    issues.append("Compose Compiler 版本异常（须匹配 Kotlin 1.9.24）")
if 'gradle-8.7-bin.zip' not in wrapper:
    issues.append("Gradle Wrapper 版本异常（AGP 8.5.2 需 8.7）")
if 'namespace = "com.cyberscale.app"' not in app_gradle:
    issues.append("namespace 与源码包名不一致")

# ---- 协议常量校验（防止复刻时改错字节）----
frame = (KT_ROOT / "com/cyberscale/app/ble/ScaleFrame.kt").read_text(encoding="utf-8")
for token in ["HEADER = 0xCA", "MIN_LENGTH = 21", "raw / 100.0", "0.4535924", "6.35029318",
              "(sp shr 3) and 0x03", "(sp shr 1) and 0x03", "(sp and 0x01) != 0",
              "getManufacturerSpecificData", "extractManufacturer"]:
    if token not in frame:
        issues.append(f"ScaleFrame.kt 缺少协议关键片段: {token}")

print("=" * 62)
print("CyberScale 工程静态校验")
print("=" * 62)
print(f"Kotlin 源文件 : {len(kt_files)}")
print(f"顶层声明      : {len(declared)}")
print(f"内部 import   : {len(internal_imports)}")
print(f"资源文件      : {len(list((SRC / 'res').rglob('*.*')))}")
print("-" * 62)
if issues:
    for i, msg in enumerate(issues, 1):
        print(f"[{i:02d}] {msg}")
    print("-" * 62)
    print(f"结论: 发现 {len(issues)} 个问题")
    sys.exit(1)
else:
    print("结论: 全部通过，未发现结构性问题")
    sys.exit(0)
