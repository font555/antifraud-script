# -*- coding: utf-8 -*-
"""统计 Java 文件注释覆盖率。

用途：补注释前后对比、CI 里卡注释率下限。
运行：python tools/comment_stats.py
统计口径：以 // 、* 、/* 开头的行 + 含行尾 // 的行，都算注释行。
注意：这是「宽口径」，比只数纯注释行得到的比例高一些。
"""
import os, sys

ROOT = r"C:\Users\Lecoo\Desktop\antifraud-script"
JAVA = os.path.join(ROOT, "app", "src", "main", "java", "com", "example", "antifraud")


def stat(path):
    lines = open(path, encoding="utf-8").read().split("\n")
    total = len(lines)
    comment = 0
    for l in lines:
        s = l.strip()
        if s.startswith(("//", "*", "/*")):
            comment += 1
        elif "//" in l:
            # 行尾注释也算
            idx = l.find("//")
            if idx > 0 and not l[:idx].rstrip().endswith(":"):  # 排除 http://
                comment += 1
    return total, comment


def main():
    rows = []
    for dp, dn, fn in os.walk(JAVA):
        for f in sorted(fn):
            if not f.endswith(".java"):
                continue
            p = os.path.join(dp, f)
            t, c = stat(p)
            rel = os.path.relpath(p, JAVA).replace("\\", "/")
            rows.append((rel, t, c))
    rows.sort()
    gt = gc = 0
    print("%-46s %5s %5s %6s" % ("文件", "行数", "注释", "占比"))
    print("-" * 68)
    for rel, t, c in rows:
        gt += t; gc += c
        print("%-46s %5d %5d %5.1f%%" % (rel, t, c, c / t * 100 if t else 0))
    print("-" * 68)
    print("%-46s %5d %5d %5.1f%%" % ("合计（%d 个文件）" % len(rows), gt, gc, gc / gt * 100))
    return 0


if __name__ == "__main__":
    sys.exit(main())
