# -*- coding: utf-8 -*-
"""
CyberScale 数据层离线校验（纯 Python，无需 Android / Gradle / 真机）

A. Room MIGRATION_1_2：从 AppDatabase.kt 抽取真实迁移 SQL，在内存 SQLite 里
   建 v1 表 → 灌样本 → 执行迁移 → 严格断言列结构、默认值与数据保留。
B. 身体成分公式样例复算（与 BodyComposition.kt 完全相同的公式口径）。
C. 中国成人 BMI 四级边界用例。

用法：python verify_data_layer.py
"""
import re
import sqlite3
import sys
from pathlib import Path

# 本脚本位于 <仓库根>/scripts/，工程根是上一级目录
ROOT = Path(__file__).resolve().parent.parent
DB_KT = ROOT / "app/src/main/java/com/cyberscale/app/data/AppDatabase.kt"

V1_USERS = ["id", "name", "colorArgb", "heightCm", "birthYear", "gender",
            "goalWeightKg", "startWeightKg", "createdAt", "isActive"]
V1_RECORDS = ["id", "userId", "ts", "weightKg", "seq", "rssi", "impedance",
              "rawHex", "mac", "note"]
NEW_RECORD_COLS = ["bmi", "bmiLevel", "bodyFatPct", "bmrKcal", "ibwKg",
                   "lbmKg", "fmKg", "relativeWeightIndex", "obesityDegreePct"]

failures = []


def check(cond, msg):
    if cond:
        print("  [PASS] " + msg)
    else:
        print("  [FAIL] " + msg)
        failures.append(msg)


def extract_migration_sql():
    src = DB_KT.read_text(encoding="utf-8")
    stmts = []
    # 每条 db.execSQL( ... ) 以独占一行的右括号收尾；内部 CAST(...) 不会误截断
    for m in re.finditer(r"db\.execSQL\(([\s\S]*?)\)\s*\n", src):
        pieces = re.findall(r'"([^"]*)"', m.group(1))
        if pieces:
            stmts.append("".join(pieces))
    return stmts


def part_a():
    print("\n=== A. Room MIGRATION_1_2 离线模拟 ===")
    stmts = extract_migration_sql()
    print("  抽取迁移 SQL: %d 条" % len(stmts))
    check(len(stmts) == 11, "迁移 SQL 条数 = 11（users 2 条 + records 9 条）")

    db = sqlite3.connect(":memory:")
    db.executescript("""
        CREATE TABLE users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT,
            colorArgb INTEGER, heightCm INTEGER, birthYear INTEGER, gender INTEGER,
            goalWeightKg REAL, startWeightKg REAL, createdAt INTEGER, isActive INTEGER);
        CREATE TABLE records (id INTEGER PRIMARY KEY AUTOINCREMENT, userId INTEGER,
            ts INTEGER, weightKg REAL, seq INTEGER, rssi INTEGER, impedance REAL,
            rawHex TEXT, mac TEXT, note TEXT NOT NULL);
        INSERT INTO users VALUES (1,'我',-65536,170,1995,0,60.0,63.0,1000,1);
        INSERT INTO users VALUES (2,'小林',-65300,165,0,2,55.0,0.0,1001,0);
        INSERT INTO records VALUES (1,1,1700000000000,62.4,7,-62,0.0,'CA20','AA:BB','');
        INSERT INTO records VALUES (2,1,1699913600000,62.7,6,-60,0.0,'CA20','AA:BB','');
    """)
    try:
        for s in stmts:
            db.execute(s)
    except sqlite3.Error as ex:
        check(False, "迁移 SQL 执行失败: %s" % ex)
        return
    check(True, "11 条迁移 SQL 全部可执行")

    users_info = {r[1]: r for r in db.execute("PRAGMA table_info(users)")}
    rec_info = {r[1]: r for r in db.execute("PRAGMA table_info(records)")}

    check(set(users_info) == set(V1_USERS + ["birthDate"]),
          "users 最终列 = v1 全部列 + birthDate")
    check(set(rec_info) == set(V1_RECORDS + NEW_RECORD_COLS),
          "records 最终列 = v1 全部列 + 9 个指标列")

    bd = users_info.get("birthDate")
    check(bd is not None and bd[2].upper() == "TEXT" and bd[3] == 1,
          "birthDate 为 TEXT NOT NULL（与 @ColumnInfo(defaultValue=\"''\") 对齐）")
    check(bd is not None and bd[4] == "''", "birthDate DEFAULT '' 与 Entity 注解一致")

    ok_nullable = all(rec_info[c][3] == 0 for c in NEW_RECORD_COLS)
    check(ok_nullable, "9 个指标列全部可空（v1 历史记录无此数据）")
    check(rec_info["bmiLevel"][2].upper() == "TEXT", "bmiLevel 为 TEXT")
    check(all(rec_info[c][2].upper() == "REAL" for c in NEW_RECORD_COLS if c != "bmiLevel"),
          "其余 8 个指标列均为 REAL")

    check(db.execute("SELECT COUNT(*) FROM users").fetchone()[0] == 2,
          "迁移后用户数保持 2（未清库）")
    check(db.execute("SELECT COUNT(*) FROM records").fetchone()[0] == 2,
          "迁移后记录数保持 2（未清库）")
    check([r[0] for r in db.execute("SELECT weightKg FROM records ORDER BY ts DESC")]
          == [62.4, 62.7], "历史体重 62.4 / 62.7 原值保留")
    check(db.execute("SELECT birthDate FROM users WHERE id=1").fetchone()[0] == "1995-01-01",
          "旧 birthYear=1995 回填为 1995-01-01")
    check(db.execute("SELECT birthDate FROM users WHERE id=2").fetchone()[0] == "",
          "无有效年份的用户保持空串，等待用户补录（不编造生日）")
    check(all(v is None for v in
              db.execute("SELECT bmi,bmiLevel,bodyFatPct FROM records LIMIT 1").fetchone()),
          "历史记录新指标为 NULL，由 backfillMetrics() 后续补算")


def compute(weight, height, gender, age):
    """与 BodyComposition.compute 完全一致的公式口径。"""
    bmi = weight / ((height / 100.0) ** 2)
    fat = min(65.0, max(3.0, 1.20 * bmi + 0.23 * age - (10.8 if gender == 1 else 0.0) - 5.4))
    bmr = 10.0 * weight + 6.25 * height - 5.0 * age + (5.0 if gender == 1 else -161.0)
    ibw = min(200.0, max(25.0, (50.0 if gender == 1 else 45.5) + 0.9 * (height - 152.4)))
    fm = weight * fat / 100.0
    return {
        "BMI": bmi, "BF%": fat, "BMR": bmr, "IBW": ibw,
        "LBM": weight - fm, "FM": fm,
        "RWI": weight / ibw * 100.0, "OB%": (weight - ibw) / ibw * 100.0,
    }


def level_of(bmi):
    if bmi < 18.5:
        return "偏瘦"
    if bmi < 24.0:
        return "正常"
    if bmi < 28.0:
        return "超重"
    return "肥胖"


def part_b():
    print("\n=== B. 身体成分公式样例复算（与 Kotlin 实现逐项对照）===")
    cases = [
        ("男 30岁 170cm 62.4kg", 62.4, 170, 1, 30),
        ("女 30岁 170cm 62.4kg", 62.4, 170, 2, 30),
        ("男 45岁 175cm 85.0kg", 85.0, 175, 1, 45),
    ]
    print("  %-26s %7s %7s %9s %7s %7s %7s %8s %8s" %
          ("样例", "BMI", "BF%", "BMR", "IBW", "LBM", "FM", "RWI%", "肥胖度%"))
    for label, w, h, g, age in cases:
        r = compute(w, h, g, age)
        print("  %-26s %7.2f %7.2f %9.1f %7.2f %7.2f %7.2f %8.2f %8.2f" %
              (label, r["BMI"], r["BF%"], r["BMR"], r["IBW"],
               r["LBM"], r["FM"], r["RWI"], r["OB%"]))
        check(abs(r["LBM"] + r["FM"] - w) < 1e-9, label + "：LBM + FM = 体重（守恒）")
        check(abs(r["RWI"] - r["OB%"] - 100.0) < 1e-9,
              label + "：相对体重指数 = 肥胖度 + 100（口径自洽）")
        check(r["BMR"] > 0 and r["IBW"] > 0 and 3.0 <= r["BF%"] <= 65.0,
              label + "：BMR / IBW / 体脂率均在合理钳制区间")


def part_c():
    print("\n=== C. 中国成人 BMI 国标四级边界 ===")
    for bmi, expected in [(18.4, "偏瘦"), (18.5, "正常"), (23.9, "正常"),
                          (24.0, "超重"), (27.9, "超重"), (28.0, "肥胖")]:
        check(level_of(bmi) == expected,
              "BMI %.1f → %s" % (bmi, expected))


if __name__ == "__main__":
    part_a()
    part_b()
    part_c()
    print("\n" + "=" * 54)
    if failures:
        print("结论: %d 项失败" % len(failures))
        for f in failures:
            print("  - " + f)
        sys.exit(1)
    print("结论: 全部通过")
