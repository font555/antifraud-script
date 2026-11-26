"""代码研读手册生成工具（manual_common）——61 页手册的“排版底座”。

一、这个文件在手册生成流程里的位置
    tools/build_manual.py 把四段内容脚本按顺序拼成一本 docx：
        manual_part1.py   → 封面、目录、第 0-3 章
        manual_part4.py   → 第 4 章（核心代码逐行读）
        manual_part2a.py  → 第 5-7 章（其余类、数据格式、变量命名）
        manual_part2b.py  → 第 8-10 章 + 附录
    这四个脚本只写“内容”，所有排版能力都由本文件提供，所以它们开头都
    `from manual_common import ...`。改一次本文件，全书样式一起变。

二、本文件提供的能力（按手册页面的组成顺序）
    1. 文档与字体：new_document()、set_cn_font()
    2. 封面：cover()
    3. 三级标题与正文：h1() / h2() / h3() / para() / bullet() / numbered()
    4. 提示框：note()（浅黄底色 + 【提示】前缀，手册里所有“易错点/重点”都用它）
    5. 代码块：code_block()（深色底 + 类 VS Code 语法着色）；annotated() 是它的
       “带行尾中文注释”版本，第 4 章逐行讲解全部靠 annotated()
    6. 表格：table()（表头浅棕底、跨页自动重复表头）
    7. 截图：shot()（从 logs/ 取图；文件缺失时自动降级成一条提示，不中断生成）
    8. 源码读取：read_java() / java_lines() / java_line_count() / snippet() /
       method_index() / class_catalog()——手册里的行数、方法地图、类清单都是现场扫源码算的

三、怎么调用
    正常用法（生成整本手册）：
        python tools/build_manual.py
    单独调试某个排版零件时，可以在临时脚本里：
        from manual_common import new_document, h1, para
        doc = new_document()
        h1(doc, "测试标题")
        doc.save("test.docx")

四、依赖
    python-docx（bundled python 已带）。
"""

import os            # 拼路径：定位工程根目录、Java 源码目录、截图目录
import re            # 正则：代码着色分词、方法签名匹配、类注释抽取

from docx import Document                                    # 新建 docx 文档对象
from docx.enum.section import WD_SECTION                     # 分节（预留，目前整本手册只用一个版式）
from docx.enum.table import WD_TABLE_ALIGNMENT               # 表格对齐方式（统一下表居中）
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK      # 段落对齐、分页符
from docx.oxml import OxmlElement                            # 手写 OOXML 元素：底纹、边框、表头重复
from docx.oxml.ns import qn                                  # OOXML 命名空间前缀（w:xxx）
from docx.shared import Cm, Pt, RGBColor                     # 厘米、磅、颜色

# 工程根目录：本文件在 tools/ 下，所以往上两级
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
# Java 源码根目录：手册里所有“文件行数 / 方法地图 / 类清单”都从这里现场扫描
JAVA_DIR = os.path.join(ROOT, "app", "src", "main", "java", "com", "example", "antifraud")
# 截图目录：手册正文引用的界面截图（logs/ 不进仓库，缺图时 shot() 会降级成文字提示）
SHOT_DIR = os.path.join(ROOT, "logs")

# ---- 全书统一配色（改这里 = 改整本手册的视觉）----
INK = RGBColor(0x2B, 0x2B, 0x33)     # 正文墨色（比纯黑柔和）
BRAND = RGBColor(0xB3, 0x3A, 0x1C)   # 品牌红：一级标题、封面主标题
GRAY = RGBColor(0x6B, 0x66, 0x5E)    # 灰：图注、代码块标题、封面副信息
CODE_BG = "F4F1E8"                   # 浅色代码块底色（米色，与正文纸色一致）


def set_cn_font(run, name="微软雅黑"):
    """给一个 run（一段连续文字）设置中文字体。

    参数:
        run  -- python-docx 的 Run 对象（p.add_run(...) 的返回值）
        name -- 字体名，默认“微软雅黑”
    返回:
        None
    说明:
        python-docx 的 run.font.name 只设西文字体，中文字符会退回默认字体，
        所以必须再往 rPr/rFonts 里写 w:eastAsia，中文才会真的用上这个字体。
        封面、标题这类需要“现代感”的文字都调它。
    """
    run.font.name = name                                   # 先设西文字体（英文/数字部分）
    rpr = run._element.get_or_add_rPr()                    # 拿到（必要时创建）run 的属性节点
    rfonts = rpr.find(qn("w:rFonts"))                      # 看是否已有 rFonts 子节点
    if rfonts is None:                                     # 没有就自己造一个
        rfonts = OxmlElement("w:rFonts")
        rpr.append(rfonts)
    rfonts.set(qn("w:eastAsia"), name)                     # 关键一行：中文字体走 eastAsia


def new_document():
    """新建一本空白手册（A4 + 全书基础样式）。

    参数:
        无
    返回:
        docx.Document 对象。四个 manual_part*.py 的对外入口函数都从它开始往下写。
    副作用:
        只改内存里的文档对象，不落盘；保存由 build_manual.py 负责。
    版式说明:
        A4（21 × 29.7 cm），左右 2.2/2.0 cm、上下 2.0/1.8 cm——比 Word 默认窄一点，
        因为手册里代码块和表格很多，需要更多横向空间。
        正文用宋体 10.5 磅、1.35 倍行距，是中文技术文档最省纸又看得清的组合。
    """
    doc = Document()                                       # 新建空白文档
    section = doc.sections[0]                              # 取第一节（整本手册只用一节）
    section.page_width = Cm(21)                            # A4 宽
    section.page_height = Cm(29.7)                         # A4 高
    section.left_margin = Cm(2.2)                          # 左边距稍宽，装订不压字
    section.right_margin = Cm(2.0)                         # 右边距
    section.top_margin = Cm(2.0)                           # 上边距
    section.bottom_margin = Cm(1.8)                        # 下边距（留页码位置）

    style = doc.styles["Normal"]                           # “正文”样式：全书默认字体
    style.font.name = "宋体"
    style.font.size = Pt(10.5)                             # 五号字，中文标准正文
    style.font.color.rgb = INK                             # 用墨色，不用纯黑
    style.element.rPr.rFonts.set(qn("w:eastAsia"), "宋体")  # 同上：中文必须单独指定
    style.paragraph_format.line_spacing = 1.35             # 1.35 倍行距，段落之间不挤
    style.paragraph_format.space_after = Pt(4)             # 段后 4 磅（不是空行，省纸）
    return doc


def cover(doc, title, subtitle, meta_lines):
    """画封面页。

    参数:
        doc        -- 文档对象
        title      -- 主标题（“反诈剧本杀 · 代码研读手册”，34 磅品牌红）
        subtitle   -- 副标题（一句话说明这本手册给谁看，15 磅）
        meta_lines -- 封面下方几行小字：版本号、工程规模、编写日期（可多行）
    返回:
        None
    说明:
        用“空段落”当垂直间距——python-docx 没有直接的“留白”概念，
        数几个空段落是最简单也最稳定的做法。最后一定加分页符，保证目录从新页开始。
    """
    for _ in range(5):                                     # 顶部留白：把标题压到页面偏上位置
        doc.add_paragraph()
    p = doc.add_paragraph()                                 # 主标题段落
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run(title)
    r.font.size = Pt(34)                                   # 34 磅，封面主视觉
    r.font.bold = True
    r.font.color.rgb = BRAND                               # 品牌红
    set_cn_font(r, "微软雅黑")                              # 封面用雅黑，正文才用宋体

    p = doc.add_paragraph()                                 # 副标题段落
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run(subtitle)
    r.font.size = Pt(15)
    r.font.color.rgb = INK
    set_cn_font(r, "微软雅黑")

    for _ in range(6):                                     # 副标题与信息区之间的留白
        doc.add_paragraph()
    for line in meta_lines:                                # 逐行写版本/规模/日期
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = p.add_run(line)
        r.font.size = Pt(10.5)
        r.font.color.rgb = GRAY                            # 小字用灰，不抢标题
        set_cn_font(r)
    doc.add_page_break()                                   # 封面独占一页


def h1(doc, text, page_break=True):
    """一级标题（章标题）：19 磅品牌红 + 底部红线 + 自动另起一页。

    参数:
        doc       -- 文档对象
        text      -- 标题文字，如“第 4 章  核心代码逐行读（上）”
        page_break -- 是否先分页，默认 True（每章都从新页开始）
    返回:
        None
    说明:
        下方的红线不是画出来的线条，而是段落底边框（w:pBdr）——
        这样标题换页时线跟着段落走，不会像图片那样错位。
    """
    if page_break:
        doc.add_page_break()                               # 每章新起一页
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(6)                # 章标题上方留一点空
    p.paragraph_format.space_after = Pt(10)                # 与正文拉开距离
    r = p.add_run(text)
    r.font.size = Pt(19)                                   # 19 磅，明显的章节层级
    r.font.bold = True
    r.font.color.rgb = BRAND
    set_cn_font(r, "微软雅黑")
    # 下边框
    pPr = p._p.get_or_add_pPr()                            # 段落属性节点
    borders = OxmlElement("w:pBdr")                        # 段落边框容器
    bottom = OxmlElement("w:bottom")                       # 只要下边框
    bottom.set(qn("w:val"), "single")                      # 实线
    bottom.set(qn("w:sz"), "12")                           # 线宽（1/8 磅为单位，12 ≈ 1.5 磅）
    bottom.set(qn("w:color"), "B33A1C")                    # 线色 = 品牌红
    borders.append(bottom)
    pPr.append(borders)                                    # 挂到段落属性上，标题下方就出现红线


def h2(doc, text):
    """二级标题（节标题，如“4.1 MainActivity.java：三个主界面的总调度”）。

    参数:
        doc  -- 文档对象
        text -- 标题文字（章节号由调用方自己写进字符串）
    返回:
        None
    说明:
        14 磅、墨色加粗，视觉重量低于 h1，但翻页时足以定位小节。
        结尾的 reset_numbering() 很关键：每个新的 h2 都把 numbered() 的手动编号归零，
        保证“9.2 加一本新剧本”这类清单每条小节都从 1 开始数。
    """
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(12)               # 上方多留一点，章节之间才透气
    p.paragraph_format.space_after = Pt(5)
    r = p.add_run(text)
    r.font.size = Pt(14)
    r.font.bold = True
    r.font.color.rgb = INK
    set_cn_font(r, "微软雅黑")
    reset_numbering()                                      # 新小节 → 列表编号从 1 重来


def h3(doc, text):
    """三级标题（小节内部的分组，如“4.2.6 renderNode ★ 一‘幕’是怎么演出来的”）。

    参数:
        doc  -- 文档对象
        text -- 标题文字
    返回:
        None
    说明:
        11.5 磅品牌红加粗，只比正文大 1 磅——手册里 h3 用得最多（第 4、5 章几乎全是 h3），
        太抢眼会把页面切碎，所以颜色用红但字号克制。
        同样归零编号，避免 h2 下的清单跨 h3 继续数。
    """
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(8)
    p.paragraph_format.space_after = Pt(3)
    r = p.add_run(text)
    r.font.size = Pt(11.5)
    r.font.bold = True
    r.font.color.rgb = BRAND
    set_cn_font(r, "微软雅黑")
    reset_numbering()                                      # 每个 h3 也重置手动编号


def para(doc, text, indent=True, size=10.5, color=None, bold=False):
    """普通正文段落（手册里所有“讲道理”的文字都用它）。

    参数:
        doc    -- 文档对象
        text   -- 段落文字
        indent -- 是否首行缩进两字（默认 True，符合中文排版习惯）
        size   -- 字号，默认 10.5（五号）
        color  -- 文字颜色，None 表示用墨色 INK
        bold   -- 是否加粗
    返回:
        创建出来的段落对象（调用方一般忽略；需要后续追加内容时才会用到）
    """
    p = doc.add_paragraph()
    p.paragraph_format.first_line_indent = Cm(0.74) if indent else Cm(0)  # 0.74cm ≈ 两个汉字
    r = p.add_run(text)
    r.font.size = Pt(size)
    r.font.bold = bold
    r.font.color.rgb = color or INK
    set_cn_font(r)
    return p


def bullet(doc, text, size=10.5, level=0):
    """无序列表项（黑点列表）。

    参数:
        doc   -- 文档对象
        text  -- 这一条的文字
        size  -- 字号，默认 10.5
        level -- 缩进层级，0 为一级（每级多缩 0.6cm）
    返回:
        创建出来的段落对象
    说明:
        用的是 Word 内置的 "List Bullet" 样式，圆点由样式提供，不手写符号。
        手册里的“要点提醒”“三个条件”这类短句列表都用它。
    """
    p = doc.add_paragraph(style="List Bullet")
    p.paragraph_format.left_indent = Cm(0.74 + 0.6 * level)  # 一级缩进两字，层级再加宽
    p.paragraph_format.space_after = Pt(2)                   # 条目之间紧凑一点
    r = p.add_run(text)
    r.font.size = Pt(size)
    r.font.color.rgb = INK
    set_cn_font(r)
    return p


# 手动编号的计数器：放在列表里是为了能在函数内原地修改（int 不可变，必须用容器）
_NUM = [0]


def reset_numbering():
    """把手动编号归零。

    参数: 无
    返回: None
    说明:
        由 h2() / h3() 在每次开新小节时自动调用，所以 numbered() 的编号
        永远是“这一小节内的第几条”，不会全书一路数下去。
    """
    _NUM[0] = 0


def numbered(doc, text, size=10.5):
    """手动编号：每个小节（h2/h3）自动从 1 开始，避免 Word 列表跨节连续编号。

    参数:
        doc  -- 文档对象
        text -- 这一条的文字（不含编号，编号自动加）
        size -- 字号，默认 10.5
    返回:
        创建出来的段落对象
    说明:
        “第 9 章 动手改一改”里的操作步骤全靠它——步骤必须能按序号照着做，
        所以不能用 Word 自动编号（自动编号跨节会接着数，读者会以为漏了步骤）。
        负的首行缩进 + 正的左缩进 = 悬挂缩进，编号在左、正文对齐。
    """
    _NUM[0] += 1                                            # 计数器 +1
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.9)                # 整体右移，给编号留位置
    p.paragraph_format.first_line_indent = Cm(-0.55)        # 首行回退 = 悬挂缩进
    p.paragraph_format.space_after = Pt(2)
    r = p.add_run("%d.  " % _NUM[0] + text)                 # 编号与正文拼成一条 run
    r.font.size = Pt(size)
    r.font.color.rgb = INK
    set_cn_font(r)
    return p


def note(doc, text, title="提示"):
    """带底色的提示框：用 1x1 表格实现。

    参数:
        doc   -- 文档对象
        text  -- 提示正文
        title -- 方括号里的标题，如“易错点”“重点”“取舍”“加分点”
    返回:
        None
    说明:
        Word 没有“提示框”这种对象，所以用一个单元格的表格 + 浅黄底纹来模拟。
        手册里所有“⚠️ 易错点”“答辩加分点”都是它，读者靠底色一眼就能找到重点。
        结尾补一个空段落，避免下一个表格紧贴提示框粘在一起。
    """
    table = doc.add_table(rows=1, cols=1)                   # 1 行 1 列的“框”
    table.alignment = WD_TABLE_ALIGNMENT.CENTER             # 居中
    cell = table.cell(0, 0)
    cell._tc.get_or_add_tcPr().append(shading("FBF3E4"))    # 浅黄底纹
    p = cell.paragraphs[0]
    r = p.add_run("【" + title + "】")                        # 前缀：品牌红加粗
    r.font.size = Pt(9.5)
    r.font.bold = True
    r.font.color.rgb = BRAND
    set_cn_font(r)
    r2 = p.add_run(text)                                    # 正文接同一段，读起来连贯
    r2.font.size = Pt(9.5)                                  # 框内字号比正文小半号
    r2.font.color.rgb = INK
    set_cn_font(r2)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)  # 兜底空行，隔开后面的内容


def shading(hex_color):
    """生成一个 OOXML 底纹节点（给单元格/段落上底色）。

    参数:
        hex_color -- 六位十六进制颜色，如 "FBF3E4"、"1E1E1E"
    返回:
        w:shd 元素，调用方把它 append 到 tcPr 上即可生效
    说明:
        python-docx 没有“填充底色”的高层 API，只能直接写 OOXML。
        val="clear" 表示纯色填充（不是图案），color="auto" 是图案前景色，填 auto 即可。
    """
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")                           # 纯色填充
    shd.set(qn("w:color"), "auto")                          # 图案前景色：自动
    shd.set(qn("w:fill"), hex_color)                        # 真正决定底色的属性
    return shd


def code_block(doc, code, size=8, caption=None, dark=True, bg="1E1E1E", lang=None):
    """代码块。dark=True 时是深色主题，并按语法给不同 token 上不同颜色（类 VS Code）。

    参数:
        doc     -- 文档对象
        code    -- 代码文本（可多行，末尾换行会被去掉）
        size    -- 代码字号，默认 8 磅；长代码行传 7.2 之类更小的值
        caption -- 代码块上方的说明文字，如“代码 8-1  从点击到结局的完整调用链”
        dark    -- True = 深色底（贴代码用）；False = 浅色米底（贴目录树、流程图用）
        bg      -- 深色模式的底色，默认 "1E1E1E"（VS Code 深色主题的编辑器底色）
        lang    -- 强行指定语言（"java"/"json"/"bash"/"text"）；None 表示自动猜
    返回:
        None
    说明:
        实现方式同 note()：一个单元格的表格当“代码窗口”。
        每一行代码是一个段落，行内再按 tokenize() 的结果拆成多段不同颜色的 run。
        这是整本手册出现次数最多的排版零件——第 4 章几乎每页都有。
    """
    if caption:
        p = doc.add_paragraph()                             # 代码块标题（图注式灰小字）
        p.paragraph_format.space_before = Pt(6)
        r = p.add_run(caption)
        r.font.size = Pt(9)
        r.font.color.rgb = GRAY
        set_cn_font(r)
    table = doc.add_table(rows=1, cols=1)                   # 代码窗口
    cell = table.cell(0, 0)
    cell._tc.get_or_add_tcPr().append(shading(bg if dark else CODE_BG))  # 深色底 or 米色底

    language = lang or detect_lang(code)                    # 没指定语言就按内容猜

    theme = DARK_THEME if dark else LIGHT_THEME             # 选对应的配色表

    lines = code.rstrip("\n").split("\n")                   # 去掉末尾换行再按行切
    first = True
    for ln in lines:
        # 单元格自带第一个空段落，第一行复用它，否则会多一个空行
        p = cell.paragraphs[0] if first else cell.add_paragraph()
        first = False
        p.paragraph_format.space_after = Pt(0)              # 行间不留空，代码才紧凑
        p.paragraph_format.line_spacing = 1.0               # 单倍行距，代码块不占页

        for text, kind in tokenize(ln, language):           # 一行拆成若干着色片段
            _code_run(p, text, size, theme[kind])
    doc.add_paragraph().paragraph_format.space_after = Pt(2)  # 代码块后留一点空


# ---- 两套代码配色：深色贴在深底上，浅色贴在米底上 ----
# 键名与 tokenize() 返回的 kind 一一对应；缺键会 KeyError，所以两套表的键必须始终一致
DARK_THEME = {                                              # 深色主题（默认，贴 Java/JSON 代码）
    "normal": RGBColor(0xD4, 0xD4, 0xD4),                   # 普通文本：浅灰白
    "comment": RGBColor(0x6A, 0x99, 0x55),                  # 注释：VS Code 那种墨绿
    "string": RGBColor(0xCE, 0x91, 0x78),                   # 字符串：陶土橙
    "keyword": RGBColor(0x56, 0x9C, 0xD6),                  # 关键字：蓝
    "type": RGBColor(0x4E, 0xC9, 0xB0),                     # 类名/类型：青绿
    "number": RGBColor(0xB5, 0xCE, 0xA8),                   # 数字：淡黄绿
    "annotation": RGBColor(0xDC, 0xDC, 0xAA),               # 注解：淡黄
    "method": RGBColor(0xDC, 0xDC, 0xAA),                   # 方法名：淡黄
    "key": RGBColor(0x9C, 0xDC, 0xFE),                      # JSON 的键名：亮蓝
    "punct": RGBColor(0x9A, 0x9A, 0x9A),                    # 标点：中灰（别抢视线）
    "text": RGBColor(0xD4, 0xD4, 0xD4),                     # 纯文本行（目录树等）
}

LIGHT_THEME = {                                             # 浅色主题（米底上贴目录树/流程图）
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

# Java 关键字表：命中就染成 keyword 色。只影响观感，不影响手册内容
JAVA_KEYWORDS = {
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
    "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
    "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
    "interface", "long", "native", "new", "package", "private", "protected", "public",
    "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
    "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false",
    "null", "var", "record", "sealed", "permits", "yield",
}

# 一行代码的分词正则（手册代码块着色的核心）。
# 命名分组会被 tokenize() 用 m.lastgroup 读取，顺序即优先级：
#   字符串（含转义）→ 数字 → 标识符 → 空白 → 中文 → 其它单字符
_TOKEN_RE = re.compile(
    r"(?P<string>\"(?:\\.|[^\"\\])*\"|'(?:\\.|[^'\\])*')"  # "..." 或 '...'，支持 \" 转义
    r"|(?P<number>\b\d+(?:\.\d+)?[fLdD]?\b)"               # 整数/小数，可带 f L d D 后缀
    r"|(?P<ident>[A-Za-z_$][\w$]*)"                        # 变量名/类名/方法名
    r"|(?P<space>\s+)"                                     # 空白（保留，以维持缩进）
    r"|(?P<cjk>[\u4e00-\u9fff]+)"                          # 一段中文（整段当一个 token）
    r"|(?P<other>.)", re.S)                                # 剩下的单字符（标点）


def detect_lang(code):
    """粗略判断这段代码是 java / json / bash / text（目录树、流程图）。

    参数:
        code -- 代码块文本
    返回:
        "json" / "text" / "bash" / "java" 四个字符串之一
    说明:
        只看开头和特征字符，够用就好——手册里的代码块种类很固定：
        第 2 章的目录树和流向图是 text，第 6 章的剧本片段是 json，
        第 9 章的命令行是 bash，其余都是 java。
    """
    s = code.lstrip()                                      # 去掉前导空白再看开头
    if s.startswith("{") and '":' in code:                 # 花括号 + 带引号的键 = JSON
        return "json"
    # 统计中文字符数：中文占比高的基本是目录树/流程图，不是代码
    cjk = sum(1 for ch in code if "\u4e00" <= ch <= "\u9fff")
    # 以工程名/中文/制表符开头，或中文超过 12%，判定为纯文本块
    if s.startswith(("antifraud", "用户点击", "├", "│")) or cjk > len(code) * 0.12:
        return "text"
    # 命令行特征（注释符用 #、有 gradle / python tools 这类命令）
    if "# " in code or "gradle assembleDebug" in code or "python tools/" in code:
        return "bash"
    return "java"                                          # 兜底：当 Java 处理


def tokenize(line, lang):
    """把一行拆成 [(文本, 颜色类型)]，供代码块逐段着色。

    参数:
        line -- 一行代码/文本
        lang -- 语言（决定注释符号和标识符的着色规则）
    返回:
        [(text, kind), ...]。kind 是 DARK_THEME / LIGHT_THEME 的键：
        normal / comment / string / number / keyword / type / method / key / punct / text
    说明:
        这是手册“代码像 VS Code 那样有颜色”的全部逻辑：
        先试着把行尾注释切出来，剩下的代码再用正则逐段分类；
        最后把注释单独作为一段拼回去，这样注释永远是绿色的。
    """
    if lang == "text":
        # 纯文本块（目录树/流向图）：整体一个颜色，只有以 // 或 # 开头的行当注释
        if line.lstrip().startswith(("//", "#")):
            return [(line, "comment")]
        return [(line or " ", "text")]                     # 空行也占一个空格，否则段落高度塌陷

    # 先剥出行注释（json 不剥）
    code, comment = line, ""
    if lang in ("java", "gradle", "bash"):
        marker = "#" if lang == "bash" else "//"            # bash 用 #，Java/gradle 用 //
        idx = _comment_index(line, marker)                 # 找注释起点（跳过字符串里的符号）
        if idx >= 0:
            code, comment = line[:idx], line[idx:]         # 从中间切开：代码 | 注释

    out = []
    pos = 0
    for m in _TOKEN_RE.finditer(code):
        if m.start() > pos:
            # 正则没覆盖到的空隙（正常不该有，保险起见按普通文本输出）
            out.append((code[pos:m.start()], "normal"))
        pos = m.end()
        text = m.group(0)
        if m.lastgroup == "string":
            # JSON 里紧跟着冒号的字符串是“键”，单独用 key 色（更接近编辑器观感）
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
    """往代码块段落里追加一段带颜色的等宽文字。

    参数:
        paragraph -- 代码块单元格里的段落
        text      -- 这一小段的文字
        size      -- 字号（磅）
        color     -- RGBColor，来自 DARK_THEME / LIGHT_THEME
    返回:
        None
    说明:
        字体固定 Consolas（Windows 自带等宽字体），并强制 eastAsia 也用它——
        否则中文注释会掉回宋体，代码块里中英文宽度不齐，缩进就乱了。
    """
    r = paragraph.add_run(text)
    r.font.name = "Consolas"
    r.font.size = Pt(size)
    r.font.color.rgb = color
    r._element.get_or_add_rPr().rFonts.set(qn("w:eastAsia"), "Consolas")  # 中文也走等宽


def split_comment(line):
    """把一行拆成「代码」和「注释」两部分，用于分别着色。

    参数:
        line -- 一行源码
    返回:
        (代码部分, 注释部分)。没有注释时第二项是空字符串
    说明:
        先处理“整行都是注释”，再按“4 空格 + // → 3 空格 + // → …”的顺序找行尾注释，
        优先匹配空格多的位置，避免把代码里本来就有的 //（如字符串）切错。
        这是早期版本用的简易拆分，现在代码块着色走 tokenize()，此处保留供兼容调用。
    """
    stripped = line.lstrip()
    if stripped.startswith("//"):                           # 整行注释：缩进算代码部分，注释从 // 开始
        idx = len(line) - len(stripped)
        return line[:idx], line[idx:]
    # 行尾注释：优先找 4 空格 + //
    for sep in ("    // ", "   // ", "  // ", " // "):
        p = line.find(sep)
        if p >= 0:
            return line[:p + len(sep) - 3], line[p + len(sep) - 3:]
    return line, ""                                         # 没有注释


def table(doc, headers, rows, widths=None, size=9, header_bg="EFE7D6"):
    """插入一张三线表风格的数据表（手册里所有方法表/字段表/对照表都用它）。

    参数:
        doc       -- 文档对象
        headers   -- 表头文字列表，如 ["方法", "作用"]
        rows      -- 二维列表，每行一个列表；元素会自动 str() 化
        widths    -- 每列宽度（厘米）列表；None 表示让 Word 自动分配
        size      -- 表内字号，默认 9 磅（比正文小，装得下更多列）
        header_bg -- 表头底色，默认 "EFE7D6"（浅棕，呼应品牌红）
    返回:
        创建出来的表格对象（个别地方会拿它继续改）
    说明:
        两个容易踩的坑，这里都处理了：
        1) 设了 cell.width 还不够，必须同时写 tblGrid 的列宽，Word 里才稳定生效；
        2) 手册里很多表会跨页，必须把首行标记成“标题行”，跨页时才会自动重复表头。
    """
    t = doc.add_table(rows=1, cols=len(headers))            # 先建只有表头的一行
    t.style = "Table Grid"                                  # 内置网格样式：有完整边框
    t.alignment = WD_TABLE_ALIGNMENT.CENTER                 # 整张表居中
    hdr = t.rows[0].cells
    for i, htext in enumerate(headers):
        hdr[i]._tc.get_or_add_tcPr().append(shading(header_bg))  # 表头底色
        p = hdr[i].paragraphs[0]
        p.paragraph_format.space_after = Pt(0)
        r = p.add_run(str(htext))
        r.font.size = Pt(size)
        r.font.bold = True                                  # 表头加粗，和内容区分
        set_cn_font(r)
    for row in rows:                                        # 逐行填数据
        cells = t.add_row().cells
        for i, val in enumerate(row):
            p = cells[i].paragraphs[0]
            p.paragraph_format.space_after = Pt(0)
            r = p.add_run(str(val))                         # str() 兜底：数字/None 都能进表
            r.font.size = Pt(size)
            set_cn_font(r)
    t.autofit = False                                       # 关掉自动伸缩，否则列宽会被内容重新分配
    t.allow_autofit = False
    if widths:
        grid = t._tbl.tblGrid                              # 表格的列定义节点
        grid_cols = grid.findall(qn("w:gridCol"))
        for i, w in enumerate(widths):
            for row in t.rows:
                row.cells[i].width = Cm(w)                  # 每个单元格都设一遍
            # 同时写 tblGrid 列宽，Word 才稳定生效
            if i < len(grid_cols):
                grid_cols[i].set(qn("w:w"), str(int(w * 567)))  # 1cm = 567 twips
    # 表头跨页重复
    trPr = t.rows[0]._tr.get_or_add_trPr()                  # 首行属性
    th = OxmlElement('w:tblHeader')
    th.set(qn('w:val'), 'true')                             # 标记为“标题行”
    trPr.append(th)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)  # 表后空一行，避免和正文粘连
    return t


def shot(doc, filename, width_cm=13.5, caption=None):
    """插入一张界面截图（第 1 章“先当用户玩一遍”全靠它）。

    参数:
        doc       -- 文档对象
        filename  -- logs/ 下的文件名，如 "avatar-register.png"
        width_cm  -- 图片宽度（厘米），默认 13.5——正好一个正文栏宽
        caption   -- 图注，如“图 1-1 注册页：昵称 + 6 个像素头像”，None 表示不加
    返回:
        None
    说明:
        截图都在 logs/ 目录（不进仓库）。文件缺失时不会报错中断，
        而是画一个提示框，这样别人 clone 下来照样能生成手册，只是少几张图。
        高度不指定，按图片原始比例缩放，避免变形。
    """
    path = os.path.join(SHOT_DIR, filename)
    if not os.path.exists(path):                            # 缺图：降级成提示框，不中断生成
        note(doc, "截图缺失：" + filename, "缺少文件")
        return
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER                 # 图片居中
    p.add_run().add_picture(path, width=Cm(width_cm))        # 只给宽度，高度按比例自动
    if caption:
        cp = doc.add_paragraph()
        cp.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = cp.add_run(caption)
        r.font.size = Pt(9)
        r.font.color.rgb = GRAY                             # 图注用灰小字
        set_cn_font(r)


# ---------------- 源码读取 ----------------
# 下面这一组函数让手册“活”起来：行数、方法地图、类清单都是现场扫源码得到的，
# 所以手册里的数字永远和代码一致，不用手抄。

def read_java(rel_path):
    """读一个 Java 源文件的全文。

    参数:
        rel_path -- 相对 JAVA_DIR 的路径，如 "ui/MainActivity.java"
    返回:
        文件内容字符串（UTF-8）
    说明:
        接受正斜杠写法，内部转成当前系统的分隔符，所以 Windows 上也能直接传 "ui/xxx.java"。
    """
    path = os.path.join(JAVA_DIR, rel_path.replace("/", os.sep))
    with open(path, encoding="utf-8") as f:
        return f.read()


def java_lines(rel_path):
    """读一个 Java 源文件并按行切分。

    参数:
        rel_path -- 相对 JAVA_DIR 的路径
    返回:
        行列表（保留原样的行内容，含缩进）
    说明:
        按 "\n" 切，不 strip——第 4 章要贴真实缩进，去掉首尾空白就不好看了。
    """
    return read_java(rel_path).split("\n")


def java_line_count(rel_path):
    """真实行数：文件以换行结尾时，split 会多出一个空元素。

    参数:
        rel_path -- 相对 JAVA_DIR 的路径
    返回:
        整数行数（和编辑器里看到的行号一致）
    说明:
        手册里每张“方法地图”和每节标题后面的“（xxx 行）”都是它算的。
        去掉末尾空元素，否则每个文件都会多算一行。
    """
    lines = java_lines(rel_path)
    if lines and lines[-1] == "":                           # 末尾换行产生的空元素
        return len(lines) - 1
    return len(lines)


def snippet(rel_path, start, end):
    """取一段源码（第 4 章贴代码块用）。

    参数:
        rel_path -- 相对 JAVA_DIR 的路径
        start    -- 起始行号（1 开始，含）
        end      -- 结束行号（含）
    返回:
        这段代码的文本（行之间用换行连接）
    说明:
        行号会和 max/min 夹一下，传超范围也不会抛异常，只是拿到能取到的部分。
        手册里的代码行号与工程真实行号一致，读者可以直接对照。
    """
    lines = java_lines(rel_path)
    start = max(1, start)                                   # 别小于第 1 行
    end = min(len(lines), end)                              # 别超过文件末尾
    return "\n".join(lines[start - 1:end])


def method_index(rel_path):
    """粗略解析方法签名与行号：返回 [(line_no, signature)]。

    参数:
        rel_path -- 相对 JAVA_DIR 的路径
    返回:
        [(行号, 该行去掉首尾空白的源码), ...]，按行号升序
    说明:
        用一条正则粗筛“4 空格缩进 + 修饰符 + 返回值 + 方法名 + 左括号”的行，
        再排掉 if/for/while 这类长得像方法的控制语句。
        精度够手册用（方法地图只需给读者一个索引），不追求完整的 Java 语法解析。
    """
    lines = java_lines(rel_path)
    out = []
    pattern = re.compile(
        r"^    (?:public|private|protected|static|final|synchronized|abstract|@Override|\s)*"
        r"[\w<>\[\], \.]+ (\w+)\s*\(([^;]*)$")             # 修饰符 + 返回类型 + 方法名 + (
    for i, ln in enumerate(lines, 1):
        m = pattern.match(ln)
        if not m:
            continue
        name = m.group(1)
        # 控制语句也符合上面的形状，排除掉
        if name in ("if", "for", "while", "switch", "catch", "return", "new"):
            continue
        out.append((i, ln.strip()))
    return out


def class_catalog():
    """扫描所有 Java 文件：相对路径、行数、类注释首行。

    参数:
        无
    返回:
        [(相对路径, 行数, 包名, 类注释首行), ...]，按路径排序
    说明:
        第 2 章的“41 个类全景表”和附录 A 的工程规模统计都来自这里。
        类注释首行取的是文件里第一个 /** ... */ 块的第一句非空文字，
        所以每个 Java 文件开头的类注释写得越清楚，手册里这张表就越好读。
    """
    rows = []
    for dp, _, files in os.walk(JAVA_DIR):                  # 递归遍历整个源码目录
        for f in sorted(files):
            if not f.endswith(".java"):                     # 只统计 Java 源文件
                continue
            path = os.path.join(dp, f)
            rel = os.path.relpath(path, JAVA_DIR).replace("\\", "/")  # 相对路径统一用正斜杠
            src = open(path, encoding="utf-8").read()
            # 行数：数换行符，最后一行没有换行则补 1
            lines = src.count("\n") + (0 if src.endswith("\n") else 1)
            doc = ""
            m = re.search(r"/\*\*(.*?)\*/", src, re.S)       # 找文件里第一个文档注释块
            if m:
                for ln in m.group(1).splitlines():
                    ln = ln.strip().lstrip("*").strip()     # 去掉每行开头的 * 和缩进
                    if ln:
                        doc = ln                            # 取第一句非空文字当“职责”
                        break
            # 包名 = 相对路径的第一段（engine / model / ui）
            pkg = rel.split("/")[0] if "/" in rel else "（根）"
            rows.append((rel, lines, pkg, doc))
    rows.sort()                                             # 按路径排序，输出稳定
    return rows


def annotated(doc, rel_path, start, end, notes, caption=None, size=7.2):
    """带行尾中文注释的代码块。

    这是第 4 章的核心排版函数：把源码原样贴出来，只在关键行右边补一句“这行在干什么”，
    读者不需要懂 Java 也能顺着念下来。

    参数:
        doc      -- 文档对象
        rel_path -- 相对 JAVA_DIR 的路径，如 "ui/MainActivity.java"
        start    -- 起始行号（1 开始，含）
        end      -- 结束行号（含）
        notes    -- {行号: 说明} 字典，只给关键行加注释；其余行原样显示
        caption  -- 代码块标题，None 表示不加
        size     -- 代码字号，默认 7.2（加了注释会更长，用小一号字才放得下）
    返回:
        None
    说明:
        行号从源码真实行号取，所以 notes 里的键必须与工程里一致；
        注释用 "    // " 接在行尾，和源码里本来就有的 // 注释风格一致。
    """
    lines = java_lines(rel_path)
    out = []
    for i in range(start, min(end, len(lines)) + 1):         # 逐行取，末尾自动夹住
        raw = lines[i - 1].rstrip()                          # 去掉行尾空白，避免注释飘太远
        tip = notes.get(i)                                   # 这一行有没有要补的说明
        out.append(raw + ("    // " + tip if tip else ""))    # 有说明就接在行尾
    code_block(doc, "\n".join(out), size=size, caption=caption)


def method_table(doc, rel_path, size=8.5):
    """把一个文件的方法清单做成表格：行号 / 签名（去掉花括号）。

    参数:
        doc      -- 文档对象
        rel_path -- 相对 JAVA_DIR 的路径
        size     -- 表内字号，默认 8.5
    返回:
        None
    说明:
        第 4 章每讲完一个核心文件，最后都放一张它的“方法地图”（ChatActivity 就用这个）。
        签名截到 96 个字符，防止一行撑破表格；行号保留，方便读者回源码里跳转查找。
    """
    rows = []
    for line, sig in method_index(rel_path):
        s = sig.rstrip("{").strip()                          # 去掉行尾的花括号，签名更干净
        rows.append([str(line), s[:96]])                     # 超长签名截断，保证表格不变形
    table(doc, ["行号", "方法签名"], rows, widths=[1.6, 14.2], size=size)