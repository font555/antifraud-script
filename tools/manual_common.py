"""代码研读手册生成工具：统一 docx 样式、代码块、表格、截图。

依赖 python-docx（bundled python 已带）。
"""

import os
import re

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA_DIR = os.path.join(ROOT, "app", "src", "main", "java", "com", "example", "antifraud")
SHOT_DIR = os.path.join(ROOT, "logs")

INK = RGBColor(0x2B, 0x2B, 0x33)
BRAND = RGBColor(0xB3, 0x3A, 0x1C)
GRAY = RGBColor(0x6B, 0x66, 0x5E)
CODE_BG = "F4F1E8"


def set_cn_font(run, name="微软雅黑"):
    run.font.name = name
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.append(rfonts)
    rfonts.set(qn("w:eastAsia"), name)


def new_document():
    doc = Document()
    section = doc.sections[0]
    section.page_width = Cm(21)
    section.page_height = Cm(29.7)
    section.left_margin = Cm(2.2)
    section.right_margin = Cm(2.0)
    section.top_margin = Cm(2.0)
    section.bottom_margin = Cm(1.8)

    style = doc.styles["Normal"]
    style.font.name = "宋体"
    style.font.size = Pt(10.5)
    style.font.color.rgb = INK
    style.element.rPr.rFonts.set(qn("w:eastAsia"), "宋体")
    style.paragraph_format.line_spacing = 1.35
    style.paragraph_format.space_after = Pt(4)
    return doc


def cover(doc, title, subtitle, meta_lines):
    for _ in range(5):
        doc.add_paragraph()
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run(title)
    r.font.size = Pt(34)
    r.font.bold = True
    r.font.color.rgb = BRAND
    set_cn_font(r, "微软雅黑")

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run(subtitle)
    r.font.size = Pt(15)
    r.font.color.rgb = INK
    set_cn_font(r, "微软雅黑")

    for _ in range(6):
        doc.add_paragraph()
    for line in meta_lines:
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = p.add_run(line)
        r.font.size = Pt(10.5)
        r.font.color.rgb = GRAY
        set_cn_font(r)
    doc.add_page_break()


def h1(doc, text, page_break=True):
    if page_break:
        doc.add_page_break()
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(6)
    p.paragraph_format.space_after = Pt(10)
    r = p.add_run(text)
    r.font.size = Pt(19)
    r.font.bold = True
    r.font.color.rgb = BRAND
    set_cn_font(r, "微软雅黑")
    # 下边框
    pPr = p._p.get_or_add_pPr()
    borders = OxmlElement("w:pBdr")
    bottom = OxmlElement("w:bottom")
    bottom.set(qn("w:val"), "single")
    bottom.set(qn("w:sz"), "12")
    bottom.set(qn("w:color"), "B33A1C")
    borders.append(bottom)
    pPr.append(borders)


def h2(doc, text):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(12)
    p.paragraph_format.space_after = Pt(5)
    r = p.add_run(text)
    r.font.size = Pt(14)
    r.font.bold = True
    r.font.color.rgb = INK
    set_cn_font(r, "微软雅黑")
    reset_numbering()


def h3(doc, text):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(8)
    p.paragraph_format.space_after = Pt(3)
    r = p.add_run(text)
    r.font.size = Pt(11.5)
    r.font.bold = True
    r.font.color.rgb = BRAND
    set_cn_font(r, "微软雅黑")
    reset_numbering()


def para(doc, text, indent=True, size=10.5, color=None, bold=False):
    p = doc.add_paragraph()
    p.paragraph_format.first_line_indent = Cm(0.74) if indent else Cm(0)
    r = p.add_run(text)
    r.font.size = Pt(size)
    r.font.bold = bold
    r.font.color.rgb = color or INK
    set_cn_font(r)
    return p


def bullet(doc, text, size=10.5, level=0):
    p = doc.add_paragraph(style="List Bullet")
    p.paragraph_format.left_indent = Cm(0.74 + 0.6 * level)
    p.paragraph_format.space_after = Pt(2)
    r = p.add_run(text)
    r.font.size = Pt(size)
    r.font.color.rgb = INK
    set_cn_font(r)
    return p


_NUM = [0]


def reset_numbering():
    _NUM[0] = 0


def numbered(doc, text, size=10.5):
    """手动编号：每个小节（h2/h3）自动从 1 开始，避免 Word 列表跨节连续编号。"""
    _NUM[0] += 1
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.9)
    p.paragraph_format.first_line_indent = Cm(-0.55)
    p.paragraph_format.space_after = Pt(2)
    r = p.add_run("%d.  " % _NUM[0] + text)
    r.font.size = Pt(size)
    r.font.color.rgb = INK
    set_cn_font(r)
    return p


def note(doc, text, title="提示"):
    """带底色的提示框：用 1x1 表格实现。"""
    table = doc.add_table(rows=1, cols=1)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    cell = table.cell(0, 0)
    cell._tc.get_or_add_tcPr().append(shading("FBF3E4"))
    p = cell.paragraphs[0]
    r = p.add_run("【" + title + "】")
    r.font.size = Pt(9.5)
    r.font.bold = True
    r.font.color.rgb = BRAND
    set_cn_font(r)
    r2 = p.add_run(text)
    r2.font.size = Pt(9.5)
    r2.font.color.rgb = INK
    set_cn_font(r2)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)


def shading(hex_color):
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), hex_color)
    return shd


def code_block(doc, code, size=8, caption=None, dark=True, bg="1E1E1E", lang=None):
    """代码块。dark=True 时是深色主题，并按语法给不同 token 上不同颜色（类 VS Code）。"""
    if caption:
        p = doc.add_paragraph()
        p.paragraph_format.space_before = Pt(6)
        r = p.add_run(caption)
        r.font.size = Pt(9)
        r.font.color.rgb = GRAY
        set_cn_font(r)
    table = doc.add_table(rows=1, cols=1)
    cell = table.cell(0, 0)
    cell._tc.get_or_add_tcPr().append(shading(bg if dark else CODE_BG))

    language = lang or detect_lang(code)

    theme = DARK_THEME if dark else LIGHT_THEME

    lines = code.rstrip("\n").split("\n")
    first = True
    for ln in lines:
        p = cell.paragraphs[0] if first else cell.add_paragraph()
        first = False
        p.paragraph_format.space_after = Pt(0)
        p.paragraph_format.line_spacing = 1.0

        for text, kind in tokenize(ln, language):
            _code_run(p, text, size, theme[kind])
    doc.add_paragraph().paragraph_format.space_after = Pt(2)


DARK_THEME = {
    "normal": RGBColor(0xD4, 0xD4, 0xD4),
    "comment": RGBColor(0x6A, 0x99, 0x55),
    "string": RGBColor(0xCE, 0x91, 0x78),
    "keyword": RGBColor(0x56, 0x9C, 0xD6),
    "type": RGBColor(0x4E, 0xC9, 0xB0),
    "number": RGBColor(0xB5, 0xCE, 0xA8),
    "annotation": RGBColor(0xDC, 0xDC, 0xAA),
    "method": RGBColor(0xDC, 0xDC, 0xAA),
    "key": RGBColor(0x9C, 0xDC, 0xFE),
    "punct": RGBColor(0x9A, 0x9A, 0x9A),
    "text": RGBColor(0xD4, 0xD4, 0xD4),
}

LIGHT_THEME = {
    "normal": RGBColor(0x24, 0x22, 0x20),
    "comment": RGBColor(0x6B, 0x66, 0x5E),
    "string": RGBColor(0xA3, 0x1C, 0x1C),
    "keyword": RGBColor(0x00, 0x00, 0xAA),
    "type": RGBColor(0x1F, 0x6F, 0x6F),
    "number": RGBColor(0x09, 0x86, 0x58),
    "annotation": RGBColor(0x7A, 0x5C, 0x2E),
    "method": RGBColor(0x6A, 0x3D, 0x9A),
    "key": RGBColor(0x04, 0x51, 0xA3),
    "punct": RGBColor(0x55, 0x55, 0x55),
    "text": RGBColor(0x24, 0x22, 0x20),
}

JAVA_KEYWORDS = {
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
    "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
    "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
    "interface", "long", "native", "new", "package", "private", "protected", "public",
    "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
    "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false",
    "null", "var", "record", "sealed", "permits", "yield",
}

_TOKEN_RE = re.compile(
    r"(?P<string>\"(?:\\.|[^\"\\])*\"|'(?:\\.|[^'\\])*')"
    r"|(?P<number>\b\d+(?:\.\d+)?[fLdD]?\b)"
    r"|(?P<ident>[A-Za-z_$][\w$]*)"
    r"|(?P<space>\s+)"
    r"|(?P<cjk>[\u4e00-\u9fff]+)"
    r"|(?P<other>.)", re.S)


def detect_lang(code):
    """粗略判断这段代码是 java / json / bash / text（目录树、流程图）。"""
    s = code.lstrip()
    if s.startswith("{") and '":' in code:
        return "json"
    cjk = sum(1 for ch in code if "\u4e00" <= ch <= "\u9fff")
    if s.startswith(("antifraud", "用户点击", "├", "│")) or cjk > len(code) * 0.12:
        return "text"
    if "# " in code or "gradle assembleDebug" in code or "python tools/" in code:
        return "bash"
    return "java"


def tokenize(line, lang):
    """把一行拆成 [(文本, 颜色类型)]，供代码块逐段着色。"""
    if lang == "text":
        if line.lstrip().startswith(("//", "#")):
            return [(line, "comment")]
        return [(line or " ", "text")]

    # 先剥出行注释（json 不剥）
    code, comment = line, ""
    if lang in ("java", "gradle", "bash"):
        marker = "#" if lang == "bash" else "//"
        idx = _comment_index(line, marker)
        if idx >= 0:
            code, comment = line[:idx], line[idx:]

    out = []
    pos = 0
    for m in _TOKEN_RE.finditer(code):
        if m.start() > pos:
            out.append((code[pos:m.start()], "normal"))
        pos = m.end()
        text = m.group(0)
        if m.lastgroup == "string":
            rest = code[m.end():].lstrip()
            out.append((text, "key" if (lang == "json" and rest.startswith(":")) else "string"))
        elif m.lastgroup == "number":
            out.append((text, "number"))
        elif m.lastgroup == "ident":
            if text in JAVA_KEYWORDS:
                out.append((text, "keyword"))
            elif lang == "json":
                out.append((text, "keyword" if text in ("true", "false", "null") else "normal"))
            elif text[0].isupper():
                out.append((text, "type"))
            elif code[m.end():].lstrip().startswith("("):
                out.append((text, "method"))
            else:
                out.append((text, "normal"))
        elif m.lastgroup == "cjk":
            out.append((text, "normal"))
        elif m.lastgroup == "space":
            out.append((text, "normal"))
        else:
            out.append((text, "punct"))
    if pos < len(code):
        out.append((code[pos:], "normal"))
    if comment:
        out.append((comment, "comment"))
    return out or [(" ", "normal")]


def _comment_index(line, marker="#"):
    """找行注释起始位置；字符串里的 # 或 // 不算。"""
    in_str = None
    i = 0
    while i < len(line):
        ch = line[i]
        if in_str:
            if ch == "\\":
                i += 2
                continue
            if ch == in_str:
                in_str = None
        elif ch in ("'", '"'):
            in_str = ch
        elif line.startswith(marker, i):
            return i
        i += 1
    return -1


def _code_run(paragraph, text, size, color):
    r = paragraph.add_run(text)
    r.font.name = "Consolas"
    r.font.size = Pt(size)
    r.font.color.rgb = color
    r._element.get_or_add_rPr().rFonts.set(qn("w:eastAsia"), "Consolas")


def split_comment(line):
    """把一行拆成「代码」和「注释」两部分，用于分别着色。"""
    stripped = line.lstrip()
    if stripped.startswith("//"):
        idx = len(line) - len(stripped)
        return line[:idx], line[idx:]
    # 行尾注释：优先找 4 空格 + //
    for sep in ("    // ", "   // ", "  // ", " // "):
        p = line.find(sep)
        if p >= 0:
            return line[:p + len(sep) - 3], line[p + len(sep) - 3:]
    return line, ""



def table(doc, headers, rows, widths=None, size=9, header_bg="EFE7D6"):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    hdr = t.rows[0].cells
    for i, htext in enumerate(headers):
        hdr[i]._tc.get_or_add_tcPr().append(shading(header_bg))
        p = hdr[i].paragraphs[0]
        p.paragraph_format.space_after = Pt(0)
        r = p.add_run(str(htext))
        r.font.size = Pt(size)
        r.font.bold = True
        set_cn_font(r)
    for row in rows:
        cells = t.add_row().cells
        for i, val in enumerate(row):
            p = cells[i].paragraphs[0]
            p.paragraph_format.space_after = Pt(0)
            r = p.add_run(str(val))
            r.font.size = Pt(size)
            set_cn_font(r)
    t.autofit = False
    t.allow_autofit = False
    if widths:
        grid = t._tbl.tblGrid
        grid_cols = grid.findall(qn("w:gridCol"))
        for i, w in enumerate(widths):
            for row in t.rows:
                row.cells[i].width = Cm(w)
            # 同时写 tblGrid 列宽，Word 才稳定生效
            if i < len(grid_cols):
                grid_cols[i].set(qn("w:w"), str(int(w * 567)))
    # 表头跨页重复
    trPr = t.rows[0]._tr.get_or_add_trPr()
    th = OxmlElement('w:tblHeader')
    th.set(qn('w:val'), 'true')
    trPr.append(th)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)
    return t


def shot(doc, filename, width_cm=13.5, caption=None):
    path = os.path.join(SHOT_DIR, filename)
    if not os.path.exists(path):
        note(doc, "截图缺失：" + filename, "缺少文件")
        return
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.add_run().add_picture(path, width=Cm(width_cm))
    if caption:
        cp = doc.add_paragraph()
        cp.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = cp.add_run(caption)
        r.font.size = Pt(9)
        r.font.color.rgb = GRAY
        set_cn_font(r)


# ---------------- 源码读取 ----------------

def read_java(rel_path):
    path = os.path.join(JAVA_DIR, rel_path.replace("/", os.sep))
    with open(path, encoding="utf-8") as f:
        return f.read()


def java_lines(rel_path):
    return read_java(rel_path).split("\n")


def java_line_count(rel_path):
    """真实行数：文件以换行结尾时，split 会多出一个空元素。"""
    lines = java_lines(rel_path)
    if lines and lines[-1] == "":
        return len(lines) - 1
    return len(lines)


def snippet(rel_path, start, end):
    """取 1-based 行号区间（含），返回代码文本。"""
    lines = java_lines(rel_path)
    start = max(1, start)
    end = min(len(lines), end)
    return "\n".join(lines[start - 1:end])


def method_index(rel_path):
    """粗略解析方法签名与行号：返回 [(line_no, signature)]。"""
    lines = java_lines(rel_path)
    out = []
    pattern = re.compile(
        r"^    (?:public|private|protected|static|final|synchronized|abstract|@Override|\s)*"
        r"[\w<>\[\], \.]+ (\w+)\s*\(([^;]*)$")
    for i, ln in enumerate(lines, 1):
        m = pattern.match(ln)
        if not m:
            continue
        name = m.group(1)
        if name in ("if", "for", "while", "switch", "catch", "return", "new"):
            continue
        out.append((i, ln.strip()))
    return out


def class_catalog():
    """扫描所有 Java 文件：相对路径、行数、类注释首行。"""
    rows = []
    for dp, _, files in os.walk(JAVA_DIR):
        for f in sorted(files):
            if not f.endswith(".java"):
                continue
            path = os.path.join(dp, f)
            rel = os.path.relpath(path, JAVA_DIR).replace("\\", "/")
            src = open(path, encoding="utf-8").read()
            lines = src.count("\n") + (0 if src.endswith("\n") else 1)
            doc = ""
            m = re.search(r"/\*\*(.*?)\*/", src, re.S)
            if m:
                for ln in m.group(1).splitlines():
                    ln = ln.strip().lstrip("*").strip()
                    if ln:
                        doc = ln
                        break
            pkg = rel.split("/")[0] if "/" in rel else "（根）"
            rows.append((rel, lines, pkg, doc))
    rows.sort()
    return rows

def annotated(doc, rel_path, start, end, notes, caption=None, size=7.2):
    """带行尾中文注释的代码块。

    notes: {行号: 说明}，只给关键行加注释；其余行原样显示。
    """
    lines = java_lines(rel_path)
    out = []
    for i in range(start, min(end, len(lines)) + 1):
        raw = lines[i - 1].rstrip()
        tip = notes.get(i)
        out.append(raw + ("    // " + tip if tip else ""))
    code_block(doc, "\n".join(out), size=size, caption=caption)


def method_table(doc, rel_path, size=8.5):
    """把一个文件的方法清单做成表格：行号 / 签名（去掉花括号）。"""
    rows = []
    for line, sig in method_index(rel_path):
        s = sig.rstrip("{").strip()
        rows.append([str(line), s[:96]])
    table(doc, ["行号", "方法签名"], rows, widths=[1.6, 14.2], size=size)