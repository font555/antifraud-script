# -*- coding: utf-8 -*-
"""检查全项目注释覆盖率。

用途：补注释前看基线、补完后看成果，也用于 CI 卡注释率下限。
运行：python tools/check_comment_coverage.py

统计口径（三套，各自适配语言）：
    Java   : 块注释 + // 行注释 + 行尾注释（引号内的 // 不算）
    Python : # 行注释 + 行尾注释 + docstring 的引号行
    XML    : <!-- --> 注释（支持跨行）

输出：按三类分别列文件清单与占比，最后给全项目合计与「低于目标」清单。


用法：
    python tools/check_comment_coverage.py

输出：
    1. 每个 Java / Python / XML 文件的行数与注释行数、占比
    2. 全项目合计
    3. 注释率低于目标线的文件清单（用于查漏）

注意：这里的「注释行」统计口径是宽口径——
    Java：以 // 、* 、/* 开头的行，或含行尾 // 的行
    Python：以 # 开头的行，或含行尾 # 的行（排除字符串里的 #）
    XML：含 <!-- 的行
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA_DIR = os.path.join(ROOT, "app", "src", "main", "java", "com", "example", "antifraud")
TOOLS_DIR = os.path.join(ROOT, "tools")
LAYOUT_DIR = os.path.join(ROOT, "app", "src", "main", "res", "layout")

# 各类文件的注释率目标（低于这个值会列进「待补清单」）
TARGET = {
    "java": 25.0,
    "python": 20.0,
    "xml": 15.0,
}


def java_comment_lines(lines):
    """统计 Java 注释行数（块注释 + 行注释 + 行尾注释）。"""
    n = 0
    in_block = False
    for raw in lines:
        s = raw.strip()
        if in_block:
            n += 1
            if "*/" in s:
                in_block = False
            continue
        if s.startswith("/*"):
            n += 1
            if "*/" not in s:
                in_block = True
            continue
        if s.startswith("//"):
            n += 1
            continue
        # 行尾注释：排除 http:// 这类字符串内容
        idx = raw.find("//")
        if idx > 0:
            before = raw[:idx]
            # 引号内的 // 不算注释
            if before.count('"') % 2 == 0:
                n += 1
    return n


def python_comment_lines(lines):
    """统计 Python 注释行数（# 行注释 + 行尾注释 + docstring 的引号行）。"""
    n = 0
    in_doc = False
    for raw in lines:
        s = raw.strip()
        if in_doc:
            n += 1
            if s.count('"""') >= 1 or s.count("'''") >= 1:
                in_doc = False
            continue
        if s.startswith('"""') or s.startswith("'''"):
            n += 1
            q = s[:3]
            # 单行 docstring 且闭合
            if s.count(q) < 2:
                in_doc = True
            continue
        if s.startswith("#"):
            n += 1
            continue
        # 行尾注释：引号外的 #
        if "#" in raw:
            idx = raw.find("#")
            before = raw[:idx]
            if before.count('"') % 2 == 0 and before.count("'") % 2 == 0 and before.strip():
                n += 1
    return n


def xml_comment_lines(lines):
    """统计 XML 注释行数（<!-- --> 可能跨行）。"""
    n = 0
    in_c = False
    for raw in lines:
        s = raw.strip()
        if in_c:
            n += 1
            if "-->" in s:
                in_c = False
            continue
        if "<!--" in s:
            n += 1
            if "-->" not in s:
                in_c = True
    return n


def walk(base, ext):
    """递归找出指定后缀的文件。"""
    out = []
    for dp, _, fn in os.walk(base):
        for f in sorted(fn):
            if f.endswith(ext):
                out.append(os.path.join(dp, f))
    return out


def report(title, files, counter, target, base):
    """打印一组文件的注释统计。"""
    print()
    print("=" * 78)
    print(title)
    print("=" * 78)
    print("%-52s %6s %6s %7s" % ("文件", "行数", "注释", "占比"))
    print("-" * 78)
    total = comment = 0
    low = []
    for p in files:
        try:
            lines = open(p, encoding="utf-8").read().split("\n")
        except Exception as e:  # noqa: BLE001
            print("  读取失败:", p, e)
            continue
        t = len(lines)
        c = counter(lines)
        total += t
        comment += c
        ratio = c / t * 100 if t else 0
        rel = os.path.relpath(p, base).replace("\\", "/")
        print("%-52s %6d %6d %6.1f%%" % (rel[:52], t, c, ratio))
        if ratio < target and t > 20:
            low.append((rel, ratio))
    print("-" * 78)
    ratio = comment / total * 100 if total else 0
    print("%-52s %6d %6d %6.1f%%" % ("合计（%d 个文件）" % len(files), total, comment, ratio))
    if low:
        print()
        print("注释率低于 %.0f%% 的文件（%d 个）：" % (target, len(low)))
        for rel, r in sorted(low, key=lambda x: x[1]):
            print("   %-52s %5.1f%%" % (rel[:52], r))
    return total, comment, len(low)


def main():
    grand_t = grand_c = grand_low = 0

    t, c, low = report("Java 源码（app/src/main/java）",
                       walk(JAVA_DIR, ".java"), java_comment_lines,
                       TARGET["java"], JAVA_DIR)
    grand_t += t; grand_c += c; grand_low += low

    t, c, low = report("Python 脚本（tools/）",
                       walk(TOOLS_DIR, ".py"), python_comment_lines,
                       TARGET["python"], TOOLS_DIR)
    grand_t += t; grand_c += c; grand_low += low

    xml_files = walk(LAYOUT_DIR, ".xml")
    manifest = os.path.join(ROOT, "app", "src", "main", "AndroidManifest.xml")
    if os.path.exists(manifest):
        xml_files.append(manifest)
    t, c, low = report("XML 布局与清单（layout + Manifest）",
                       xml_files, xml_comment_lines,
                       TARGET["xml"], ROOT)
    grand_t += t; grand_c += c; grand_low += low

    # build.gradle 用的是 // 注释，不能按 XML 口径统计，单独用 Java 的计法
    gradle = os.path.join(ROOT, "app", "build.gradle")
    if os.path.exists(gradle):
        t, c, low = report("Gradle 构建脚本（// 注释）",
                           [gradle], java_comment_lines, TARGET["xml"], ROOT)
        grand_t += t; grand_c += c; grand_low += low

    print()
    print("=" * 78)
    print("全项目合计：%d 行，注释 %d 行，占比 %.1f%%"
          % (grand_t, grand_c, grand_c / grand_t * 100 if grand_t else 0))
    print("低于目标的文件数：%d" % grand_low)
    print("=" * 78)
    return 0


if __name__ == "__main__":
    sys.exit(main())
