#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
《反诈剧本杀》前后测数据汇总脚本（纯标准库，Python 3 可运行）

只做计算，不生成、不补全任何数据。没有实测数据时，表里一律写【待实测】，
不要拿本脚本的输出去伪造成绩。

用法（在项目根目录执行）：

  1) 分数汇总：CSV 表头「编号,前测,后测」，一行一个学生，满分 10 分
       py tools/summarize_test.py 前后测_成绩_XX班.csv
       py tools/summarize_test.py 前后测_成绩_XX班.csv --class "XX班"

  2) 每题正确率：CSV 表头「编号,Q1,Q2,...,Q10」，答对=1，答错=0
       单个文件（只看正确率）：
       py tools/summarize_test.py 前后测_每题_前测_XX班.csv
       两个文件（逐题前后对比）：
       py tools/summarize_test.py 前后测_每题_前测_XX班.csv 前后测_每题_后测_XX班.csv

  3) 可选项：
       --per-question       强制按「每题正确率」模式解析（自动识别失败时用）
       --scores             强制按「分数」模式解析
       --class 名称         在输出标题里标注班级 / 批次
       --target 25.0        达标线，单位百分点，默认 25（即平均分提升 2.5 分）
       --min-sample 30      目标样本数，仅用于提示，默认 30（不足时如实写实际人数）

  说明：
    * CSV 编码支持 UTF-8（含 Excel 的 BOM）和 GBK；分隔符支持逗号、制表符、分号。
    * 分数列允许 0–10 的整数或小数；超出范围、无法解析、只填了一侧的行不参与统计，会逐行列出。
    * 每题列允许 1/0、对/错、√/×、true/false；空白按“缺录”处理（不计入该题分母），
      学生实际未作答请录成 0。
    * 前后两个每题文件人数可以不同（缺考），脚本不逐人配对，只在输出里提示。
    * 分数汇总只接受 1 个文件；每题对比最多 2 个文件（前测 + 后测）。

退出码：0 = 计算完成；非 0 = 参数 / 文件 / 数据错误（错误信息以 [错误] 开头）。
"""

import argparse
import csv
import io
import os
import re
import sys
import unicodedata

TARGET_DEFAULT = 25.0
MIN_SAMPLE_DEFAULT = 30
MAX_SCORE = 10.0
TITLE = "《反诈剧本杀》前后测数据汇总"
LINE = "=" * 64
SUBLINE = "-" * 64

# 表头别名（比较前会去掉空格、下划线、连字符，全角括号转半角）
ID_ALIASES = {"编号", "序号", "学号", "学生编号", "学生号", "匿名编号", "编号(匿名)",
              "id", "no", "num", "number"}
PRE_ALIASES = {"前测", "前测分", "前测得分", "前测成绩", "前测分数",
               "pre", "prescore", "pretest", "before"}
POST_ALIASES = {"后测", "后测分", "后测得分", "后测成绩", "后测分数",
                "post", "postscore", "posttest", "after"}

TRUE_VALUES = {"1", "对", "正确", "√", "✓", "true", "t", "yes", "y", "是", "答对"}
FALSE_VALUES = {"0", "错", "错误", "×", "✗", "x", "false", "f", "no", "n", "否", "答错"}

# 分数段：含下限、不含上限；10 分单列
BAND_LABELS = ("0–2 分", "3–5 分", "6–7 分", "8–9 分", "10 分")

# 题号与考点对照，来自 docs/test-pre-post.md 第四节；改题时同步改这里
QUESTION_TOPICS = {
    1: "不脱离平台私下交易",
    2: "定金、尾款不提前付",
    3: "识破「还话费 / 垫付」",
    4: "识破「刷流水才退款」",
    5: "识破「充值解冻」",
    6: "拉黑后先保证据再报警",
    7: "96110 是预警劝阻专线",
    8: "国家反诈中心 APP 的用途",
    9: "报警、取证、告知家长老师",
    10: "二次被骗（「追款」骗局）",
}

# 这些列名一旦出现，说明表里混进了个人信息，拒绝统计（项目合规要求）
PRIVATE_HEADERS = ("姓名", "名字", "手机", "身份证", "电话", "联系电话", "微信", "qq", "邮箱", "email")

_QUESTION_PATTERNS = (
    re.compile(r"^q0*(\d{1,2})(题)?$"),
    re.compile(r"^q0*(\d{1,2})(题)?\(.*\)$"),
    re.compile(r"^q0*(\d{1,2})[^\d()]+$"),
    re.compile(r"^第0*(\d{1,2})题?$"),
    re.compile(r"^第0*(\d{1,2})题?\(.*\)$"),
    re.compile(r"^第0*(\d{1,2})[^\d()]+$"),
)
_BARE_NUMBER = re.compile(r"^0*(\d{1,2})$")


class DataError(Exception):
    """数据或参数错误，直接打印给使用者看，不抛堆栈。"""


# ---------------------------------------------------------------- 基础工具

def _norm(text):
    """表头归一化：去空格、下划线、连字符，全角括号转半角，英文转小写。"""
    s = (text or "").strip().lower()
    s = s.replace("（", "(").replace("）", ")").replace("：", ":")
    s = re.sub(r"[\s_\-]+", "", s)
    return s


def _dwidth(text):
    """按终端显示宽度算：中文/全角算 2，其余算 1。"""
    return sum(2 if unicodedata.east_asian_width(ch) in ("W", "F") else 1 for ch in str(text))


def _pad(text, width, align="left"):
    text = str(text)
    gap = max(0, width - _dwidth(text))
    if align == "right":
        return " " * gap + text
    if align == "center":
        left = gap // 2
        return " " * left + text + " " * (gap - left)
    return text + " " * gap


def _table(headers, rows, aligns):
    """输出一张中文宽度对齐的文本表。"""
    widths = []
    for i, head in enumerate(headers):
        widest = _dwidth(head)
        for row in rows:
            widest = max(widest, _dwidth(row[i]))
        widths.append(widest)
    out = ["  " + "  ".join(_pad(head, widths[i], "center") for i, head in enumerate(headers))]
    out.append("  " + "  ".join("-" * widths[i] for i in range(len(headers))))
    for row in rows:
        out.append("  " + "  ".join(_pad(row[i], widths[i], aligns[i]) for i in range(len(headers))))
    return "\n".join(out)


def _fmt_score(value):
    """整数分显示成整数，小数保留 2 位。"""
    if abs(value - round(value)) < 1e-9:
        return str(int(round(value)))
    return f"{value:.2f}"


def _cell(row, index):
    if index is None or index < 0 or index >= len(row):
        return ""
    return row[index].strip()


def parse_question_header(cell):
    """表头「Q1 / q1 / Q1（考点）/ 第1题 / 1」→ 题号；不是题号列返回 None。"""
    text = _norm(cell)
    if not text:
        return None
    for pattern in _QUESTION_PATTERNS:
        match = pattern.match(text)
        if match:
            number = int(match.group(1))
            return number if 1 <= number <= 20 else None
    match = _BARE_NUMBER.match(text)
    if match:
        number = int(match.group(1))
        return number if 1 <= number <= 20 else None
    return None


def parse_score(cell):
    """返回 (分数, 空字符串) 或 (None, 原因)。允许 0–10，允许「8分」这种写法。"""
    text = (cell or "").strip()
    if not text:
        return None, "空"
    text = text.replace("分", "").strip()
    try:
        value = float(text)
    except ValueError:
        return None, f"无法解析「{cell.strip()}」"
    if value != value:  # NaN
        return None, "无法解析"
    if value < 0 or value > MAX_SCORE:
        return None, f"超出 0–{_fmt_score(MAX_SCORE)} 分（{text}）"
    return value, ""


def parse_answer(cell):
    """返回 (1/0, 空字符串)、(None, 'blank') 或 (None, 'invalid')。"""
    text = (cell or "").strip().lower()
    if not text:
        return None, "blank"
    if text in TRUE_VALUES:
        return 1, ""
    if text in FALSE_VALUES:
        return 0, ""
    try:
        value = float(text)
    except ValueError:
        return None, "invalid"
    if abs(value - 1) < 1e-9:
        return 1, ""
    if abs(value) < 1e-9:
        return 0, ""
    return None, "invalid"


# ---------------------------------------------------------------- 读文件

def _detect_delimiter(first_line):
    best, best_count = ",", 0
    for delimiter in (",", "\t", ";"):
        count = first_line.count(delimiter)
        if count > best_count:
            best, best_count = delimiter, count
    return best


def load_table(path):
    """读 CSV，返回 {path, header, body}；body 已去掉整行空白行。"""
    if not os.path.isfile(path):
        raise DataError(f"找不到文件：{path}")
    with open(path, "rb") as handle:
        raw = handle.read()
    text = None
    for encoding in ("utf-8-sig", "utf-8", "gb18030"):
        try:
            text = raw.decode(encoding)
            break
        except UnicodeDecodeError:
            continue
    if text is None:
        raise DataError(f"无法识别文件编码，请另存为 UTF-8 或 GBK：{path}")
    if not text.strip():
        raise DataError(f"文件是空的：{path}")

    first_line = ""
    first_index = None
    lines = text.splitlines()
    for index, line in enumerate(lines):
        if line.strip():
            first_line, first_index = line, index
            break
    if first_index is None:
        raise DataError(f"没有读到任何内容：{path}")

    # Excel「另存为 CSV」偶尔会在第一行写 sep=,（告诉 Excel 用哪个分隔符）
    sep_match = re.match(r"^sep=(.)\s*$", first_line.strip(), re.IGNORECASE)
    if sep_match:
        delimiter = sep_match.group(1)
        content = "\n".join(lines[first_index + 1:])
    else:
        delimiter = _detect_delimiter(first_line)
        content = "\n".join(lines)

    rows = []
    for row in csv.reader(io.StringIO(content), delimiter=delimiter):
        cells = [c.strip() for c in row]
        if any(cells):
            rows.append(cells)
    if not rows:
        raise DataError(f"没有读到任何内容：{path}")

    header = rows[0]
    body = rows[1:]
    for column in header:
        normalized = _norm(column)
        if any(normalized.startswith(word) for word in PRIVATE_HEADERS):
            raise DataError(
                f"表头有疑似个人信息列「{column}」：本项目不录入姓名、手机号、身份证号，"
                f"请删除该列后再统计")
    if len(body) == 0:
        raise DataError(f"只有表头、没有数据行：{path}")
    return {"path": path, "header": header, "body": body}


def find_column(header, aliases):
    for index, cell in enumerate(header):
        if _norm(cell) in aliases:
            return index
    return -1


def detect_mode(header):
    for cell in header:
        if parse_question_header(cell) is not None:
            return "perq"
    if find_column(header, PRE_ALIASES) >= 0 and find_column(header, POST_ALIASES) >= 0:
        return "scores"
    return None


def question_columns(header):
    """返回 [(题号, 列下标)]，按题号排序。"""
    found = {}
    for index, cell in enumerate(header):
        number = parse_question_header(cell)
        if number is None:
            continue
        if number in found:
            raise DataError(f"表头里第 {number} 题出现了两列，请改掉重复列名")
        found[number] = index
    if not found:
        raise DataError("没有找到题号列（应命名为 Q1…Q10 或 第1题…第10题）")
    return sorted(found.items())


# ---------------------------------------------------------------- 分数模式

def band_index(value):
    if value >= MAX_SCORE - 1e-9:
        return 4
    if value >= 8:
        return 3
    if value >= 6:
        return 2
    if value >= 3:
        return 1
    return 0


def run_scores(table, args):
    header, body = table["header"], table["body"]
    index_id = find_column(header, ID_ALIASES)
    index_pre = find_column(header, PRE_ALIASES)
    index_post = find_column(header, POST_ALIASES)

    pre_scores, post_scores = [], []
    issues, blank_ids, duplicate_ids = [], [], []
    seen_ids = set()

    for line_no, row in enumerate(body, start=2):
        number = _cell(row, index_id) if index_id >= 0 else ""
        pre_ok, pre_reason = parse_score(_cell(row, index_pre))
        post_ok, post_reason = parse_score(_cell(row, index_post))

        # 编号和两个分数都空 → 当作空行跳过（load_table 已过滤纯空行，这里兜底）
        if pre_ok is None and post_ok is None and not number:
            continue
        if pre_ok is None or post_ok is None:
            reasons = []
            if pre_ok is None:
                reasons.append(f"前测{pre_reason}")
            if post_ok is None:
                reasons.append(f"后测{post_reason}")
            issues.append((line_no, "、".join(reasons)))
            continue

        pre_scores.append(pre_ok)
        post_scores.append(post_ok)
        if not number:
            blank_ids.append(line_no)
        else:
            key = _norm(number)
            if key in seen_ids:
                duplicate_ids.append(number)
            seen_ids.add(key)

    if not pre_scores:
        raise DataError("没有可用的前后测配对数据（每行必须同时有前测和后测分数）")

    count = len(pre_scores)
    pre_mean = sum(pre_scores) / count
    post_mean = sum(post_scores) / count
    gain = post_mean - pre_mean
    gain_points = gain * 10.0
    threshold_score = args.target / 10.0
    band_pre = [0] * 5
    band_post = [0] * 5
    for value in pre_scores:
        band_pre[band_index(value)] += 1
    for value in post_scores:
        band_post[band_index(value)] += 1
    improved = sum(1 for a, b in zip(pre_scores, post_scores) if b - a > 1e-9)
    flat = sum(1 for a, b in zip(pre_scores, post_scores) if abs(b - a) <= 1e-9)
    dropped = count - improved - flat
    post_pass = sum(1 for value in post_scores if value >= 6)
    post_full = sum(1 for value in post_scores if value >= MAX_SCORE - 1e-9)
    gain_hit = sum(1 for a, b in zip(pre_scores, post_scores) if b - a >= threshold_score - 1e-9)

    print(LINE)
    print(f"{TITLE} · 分数汇总")
    if args.class_name:
        print(f"班级 / 批次：{args.class_name}")
    print(f"数据文件：{os.path.basename(table['path'])}")
    print(LINE)
    print(f"有效配对数：{count} 人")
    if issues:
        print(f"未计入：{len(issues)} 行（前测、后测必须同时有效）")
        for line_no, reason in issues[:8]:
            print(f"  - 第 {line_no} 行：{reason}")
        if len(issues) > 8:
            print(f"  ……其余 {len(issues) - 8} 行略")
    else:
        print("未计入：0 行（所有数据行都同时有有效的前测、后测分数）")
    if blank_ids:
        print(f"编号为空：{len(blank_ids)} 行（已按行统计；建议补编号，方便核对前后配对）")
    if duplicate_ids:
        print(f"编号重复：{len(duplicate_ids)} 行（脚本按行统计、不自动去重，请核对是否重复录入）")

    print(SUBLINE)
    print(f"前测均分：{pre_mean:.2f} 分 / 10 分（正确率 {pre_mean * 10:.1f}%）")
    print(f"后测均分：{post_mean:.2f} 分 / 10 分（正确率 {post_mean * 10:.1f}%）")
    print(f"平均提升：{gain:+.2f} 分")
    print(f"提升百分点：{gain_points:+.1f} 个百分点（满分 10 分，1 分 = 10 个百分点）")

    print(SUBLINE)
    print(f"前测最低 / 最高：{_fmt_score(min(pre_scores))} / {_fmt_score(max(pre_scores))} 分")
    print(f"后测最低 / 最高：{_fmt_score(min(post_scores))} / {_fmt_score(max(post_scores))} 分")

    print(SUBLINE)
    print("各分数段人数（含下限、不含上限；10 分单列）")
    band_rows = [[BAND_LABELS[i], str(band_pre[i]), str(band_post[i])] for i in range(5)]
    print(_table(["分数段", "前测", "后测"], band_rows, ["left", "right", "right"]))

    print(SUBLINE)
    print(f"后测 ≥ 6 分：{post_pass} 人（{post_pass / count * 100:.1f}%）")
    print(f"后测满分（10 分）：{post_full} 人")
    print(f"提升 ≥ {threshold_score:g} 分人数：{gain_hit} 人（对应达标线 {args.target:g} 个百分点）")
    print(f"提升 / 持平 / 下降：{improved} / {flat} / {dropped} 人")

    print(SUBLINE)
    if gain_points >= args.target - 1e-9:
        print(f"达标判断（提升 ≥ {args.target:g} 个百分点）：达成，"
              f"提升 {gain_points:+.1f} 个百分点，超出 {gain_points - args.target:.1f} 个百分点")
    else:
        print(f"达标判断（提升 ≥ {args.target:g} 个百分点）：未达成，"
              f"提升 {gain_points:+.1f} 个百分点，差 {args.target - gain_points:.1f} 个百分点")
    if count < args.min_sample:
        print(f"样本提示：有效配对 {count} 人，不足 {args.min_sample} 人目标。"
              f"请如实写 {count} 人，不凑数；结论只作本班课堂描述，不做显著性推断。")
    else:
        print(f"样本提示：有效配对 {count} 人，达到 {args.min_sample} 人目标。")

    print(LINE)
    print("提醒：以上只是已录入数据的计算结果；缺考、未完成、无效卷不在其中，")
    print("      需要在 docs/前后测统计表.md 的备注里如实说明，原始答卷与 CSV 一起归档。")
    print("      填表口径：满分 10 分，1 分 = 10 个百分点；提升 = 后测均分 − 前测均分。")


# ---------------------------------------------------------------- 每题正确率模式

def compute_question_stats(table):
    header, body = table["header"], table["body"]
    columns = question_columns(header)
    correct = {number: 0 for number, _ in columns}
    valid = {number: 0 for number, _ in columns}
    blanks = {number: 0 for number, _ in columns}
    invalid = {number: 0 for number, _ in columns}

    for row in body:
        for number, index in columns:
            value, kind = parse_answer(_cell(row, index))
            if kind == "blank":
                blanks[number] += 1
            elif kind == "invalid":
                invalid[number] += 1
            else:
                valid[number] += 1
                correct[number] += value

    accuracy = {}
    for number, _ in columns:
        accuracy[number] = correct[number] / valid[number] * 100.0 if valid[number] else None
    return {
        "path": table["path"],
        "columns": dict(columns),
        "correct": correct,
        "valid": valid,
        "blanks": blanks,
        "invalid": invalid,
        "accuracy": accuracy,
        "rows": len(body),
    }


def _accuracy_text(accuracy, valid_count):
    if accuracy is None or valid_count == 0:
        return "—"
    return f"{accuracy:.1f}%"


def _mean(values):
    values = [v for v in values if v is not None]
    return sum(values) / len(values) if values else None


def run_per_question(tables, args):
    stats = [compute_question_stats(table) for table in tables]

    print(LINE)
    print(f"{TITLE} · 每题正确率")
    if args.class_name:
        print(f"班级 / 批次：{args.class_name}")
    for item in stats:
        print(f"数据文件：{os.path.basename(item['path'])}（数据行 {item['rows']}）")
    print(LINE)

    if len(stats) == 1:
        item = stats[0]
        print("只提供了 1 个文件，无法比较前后；把前测、后测两个每题 CSV 一起传入即可对比。")
        print(SUBLINE)
        rows = []
        for number in sorted(item["accuracy"]):
            topic = QUESTION_TOPICS.get(number, "—")
            rows.append([f"第 {number} 题", topic,
                         _accuracy_text(item["accuracy"][number], item["valid"][number]),
                         str(item["valid"][number])])
        print(_table(["题号", "考点", "正确率", "有效份数"], rows,
                     ["left", "left", "right", "right"]))
        print(SUBLINE)
        mean_accuracy = _mean(item["accuracy"].values())
        if mean_accuracy is None:
            print("各题正确率均值：—（没有有效作答）")
        else:
            print(f"各题正确率均值：{mean_accuracy:.1f}%（按题等权平均）")
        blank_total = sum(item["blanks"].values())
        invalid_total = sum(item["invalid"].values())
        if blank_total or invalid_total:
            print(f"空白单元格：{blank_total} 个；无法识别的值：{invalid_total} 个"
                  f"（已从对应题的分母剔除；学生实际未作答请录 0）")
        print(LINE)
        print("提醒：正确率 = 该题答对人数 ÷ 该题有效份数 × 100%；以上只统计已录入数据。")
        return

    pre_stats, post_stats = stats[0], stats[1]
    if pre_stats["rows"] != post_stats["rows"]:
        print(f"注意：两个文件数据行数不同（前测 {pre_stats['rows']} 行、后测 {post_stats['rows']} 行），"
              f"说明有缺考或无效卷；脚本不逐人配对，只在输出里如实提示。")
        print(SUBLINE)

    all_numbers = sorted(set(pre_stats["accuracy"]) | set(post_stats["accuracy"]))
    common_numbers = [n for n in all_numbers if n in pre_stats["accuracy"] and n in post_stats["accuracy"]]
    if not common_numbers:
        raise DataError("两个文件里没有相同的题号，无法对比")

    rows, deltas = [], []
    for number in all_numbers:
        topic = QUESTION_TOPICS.get(number, "—")
        pre_accuracy = pre_stats["accuracy"].get(number)
        post_accuracy = post_stats["accuracy"].get(number)
        pre_valid = pre_stats["valid"].get(number, 0)
        post_valid = post_stats["valid"].get(number, 0)
        if pre_accuracy is None or post_accuracy is None:
            delta_text = "—"
            maybe_delta = None
        else:
            delta = post_accuracy - pre_accuracy
            deltas.append((number, delta))
            delta_text = f"{delta:+.1f}"
            maybe_delta = delta
        rows.append([f"第 {number} 题", topic,
                     _accuracy_text(pre_accuracy, pre_valid),
                     _accuracy_text(post_accuracy, post_valid),
                     delta_text,
                     f"{pre_valid} / {post_valid}"])
    print(_table(["题号", "考点", "前测正确率", "后测正确率", "提升（百分点）", "有效份数（前/后）"],
                 rows, ["left", "left", "right", "right", "right", "right"]))

    print(SUBLINE)
    pre_mean = _mean(pre_stats["accuracy"][n] for n in common_numbers)
    post_mean = _mean(post_stats["accuracy"][n] for n in common_numbers)
    mean_gain = post_mean - pre_mean
    print(f"各题正确率均值：前测 {pre_mean:.1f}%、后测 {post_mean:.1f}%，"
          f"提升 {mean_gain:+.1f} 个百分点（按题等权平均）")
    print(f"换算成分数：平均提升 {mean_gain / 10.0:+.2f} 分（满分 10 分）")

    if deltas:
        best_number, best_delta = max(deltas, key=lambda item: item[1])
        worst_number, worst_delta = min(deltas, key=lambda item: item[1])
        print(f"提升最大：第 {best_number} 题（{best_delta:+.1f} 个百分点）")
        print(f"提升最小：第 {worst_number} 题（{worst_delta:+.1f} 个百分点）")
    post_candidates = [(n, post_stats["accuracy"][n]) for n in common_numbers
                       if post_stats["accuracy"][n] is not None]
    if post_candidates:
        low_number, low_accuracy = min(post_candidates, key=lambda item: item[1])
        print(f"后测正确率最低：第 {low_number} 题（{low_accuracy:.1f}%），讲评优先补这个考点")

    print(SUBLINE)
    if mean_gain >= args.target - 1e-9:
        print(f"达标判断（正确率均值提升 ≥ {args.target:g} 个百分点）：达成，"
              f"提升 {mean_gain:+.1f} 个百分点，超出 {mean_gain - args.target:.1f} 个百分点")
    else:
        print(f"达标判断（正确率均值提升 ≥ {args.target:g} 个百分点）：未达成，"
              f"提升 {mean_gain:+.1f} 个百分点，差 {args.target - mean_gain:.1f} 个百分点")
    if pre_stats["rows"] < args.min_sample or post_stats["rows"] < args.min_sample:
        print(f"样本提示：前测 {pre_stats['rows']} 行、后测 {post_stats['rows']} 行，"
              f"有文件不足 {args.min_sample} 人目标。请如实写实际人数，不凑数。")
    else:
        print(f"样本提示：前测 {pre_stats['rows']} 行、后测 {post_stats['rows']} 行，"
              f"均达到 {args.min_sample} 人目标。")

    blank_total = sum(pre_stats["blanks"].values()) + sum(post_stats["blanks"].values())
    invalid_total = sum(pre_stats["invalid"].values()) + sum(post_stats["invalid"].values())
    if blank_total or invalid_total:
        print(f"空白单元格：{blank_total} 个；无法识别的值：{invalid_total} 个"
              f"（已从对应题的分母剔除；学生实际未作答请录 0）")

    print(LINE)
    print("提醒：以上只是已录入数据的计算结果；哪道题后测仍低，就回讲对应幕（考点对照表"
          "见 docs/test-pre-post.md 第四节）。")
    print("      缺考、未完成、无效卷需在 docs/前后测统计表.md 备注里如实说明；原始卷与 CSV 一起归档。")


# ---------------------------------------------------------------- 入口

def main(argv=None):
    parser = argparse.ArgumentParser(
        prog="summarize_test.py",
        description="《反诈剧本杀》前后测数据汇总：只做计算，不生成数据。",
        epilog="示例：py tools/summarize_test.py 前后测_成绩.csv --class \"XX班\"；"
               "py tools/summarize_test.py 前测Q.csv 后测Q.csv",
    )
    parser.add_argument("files", nargs="+",
                        help="CSV 文件：1 个 = 分数汇总或单份每题统计；2 个 = 每题前后对比")
    parser.add_argument("--class", dest="class_name", default="",
                        help="班级 / 批次名称，写进输出标题")
    parser.add_argument("--target", type=float, default=TARGET_DEFAULT,
                        help="达标线，单位百分点，默认 25（= 2.5 分）")
    parser.add_argument("--min-sample", type=int, default=MIN_SAMPLE_DEFAULT,
                        help="目标样本数，默认 30（仅用于提示，不足时如实写实际人数）")
    parser.add_argument("--per-question", action="store_true",
                        help="强制按「每题正确率」模式解析")
    parser.add_argument("--scores", action="store_true",
                        help="强制按「分数」模式解析")
    args = parser.parse_args(argv)

    if args.per_question and args.scores:
        parser.error("--per-question 和 --scores 只能选一个")
    if len(args.files) > 2:
        parser.error("最多传入 2 个 CSV（每题模式：前测 + 后测）")
    if args.target <= 0:
        parser.error("--target 必须大于 0")
    if args.min_sample <= 0:
        parser.error("--min-sample 必须大于 0")

    try:
        tables = [load_table(path) for path in args.files]
        if args.per_question:
            mode = "perq"
        elif args.scores:
            mode = "scores"
        else:
            mode = detect_mode(tables[0]["header"])
        if mode is None:
            raise DataError(
                "无法识别 CSV 格式。分数表应有「编号,前测,后测」表头；"
                "每题表应有「编号,Q1,…,Q10」表头（也接受 第1题…第10题）")
        if mode == "perq":
            run_per_question(tables, args)
            return 0
        if len(tables) != 1:
            raise DataError("分数汇总只接受 1 个文件；每题前后对比请用两个每题 CSV")
        run_scores(tables[0], args)
        return 0
    except DataError as error:
        print(f"[错误] {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
