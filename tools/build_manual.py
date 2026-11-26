"""生成《反诈剧本杀 · 代码研读手册》的入口脚本。

分工：本文件负责「按顺序调用各部分、加页脚、保存 docx、做结构校验」，
具体章节内容分布在 manual_part1 / manual_part4 / manual_part2a / manual_part2b 里。
输出：docs/manual/反诈剧本杀-代码研读手册.docx（文件名可用环境变量 MANUAL_NAME 覆盖）

为什么拆成多个文件：手册 10 章内容量很大，放一个文件会难维护；
拆开后每次改某一章只动对应文件，行号索引也不会互相干扰。


用法：python tools/build_manual.py
产物：docs/manual/反诈剧本杀-代码研读手册.docx
"""

import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from manual_common import ROOT, new_document  # noqa: E402
import manual_part1  # noqa: E402
import manual_part4  # noqa: E402
import manual_part2a  # noqa: E402
import manual_part2b  # noqa: E402


def add_page_footer(doc, text="反诈剧本杀 · 代码研读手册　第 "):
    """页脚居中：书名 + 页码域。"""
    from docx.enum.text import WD_ALIGN_PARAGRAPH
    from docx.oxml import OxmlElement
    from docx.oxml.ns import qn

    footer = doc.sections[0].footer
    p = footer.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run(text)
    run.font.size = __import__("docx").shared.Pt(8)
    run.font.color.rgb = __import__("docx").shared.RGBColor(0x8A, 0x84, 0x78)

    fld_begin = OxmlElement("w:fldChar")
    fld_begin.set(qn("w:fldCharType"), "begin")
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = "PAGE"
    fld_end = OxmlElement("w:fldChar")
    fld_end.set(qn("w:fldCharType"), "end")
    run2 = p.add_run()
    run2.font.size = __import__("docx").shared.Pt(8)
    run2._r.append(fld_begin)
    run2._r.append(instr)
    run2._r.append(fld_end)
    run3 = p.add_run(" 页")
    run3.font.size = __import__("docx").shared.Pt(8)
    run3.font.color.rgb = __import__("docx").shared.RGBColor(0x8A, 0x84, 0x78)


def main():
    doc = new_document()
    add_page_footer(doc)

    manual_part1.build_cover_and_toc(doc)
    manual_part1.build_ch1(doc)
    manual_part1.build_ch2(doc)
    manual_part1.build_ch3(doc)
    manual_part4.build_ch4(doc)
    manual_part2a.build_ch5(doc)
    manual_part2a.build_ch6(doc)
    manual_part2a.build_ch7(doc)
    manual_part2b.build_ch8(doc)
    manual_part2b.build_ch9(doc)
    manual_part2b.build_ch10(doc)

    out_dir = os.path.join(ROOT, "docs", "manual")
    os.makedirs(out_dir, exist_ok=True)
    base = os.environ.get("MANUAL_NAME", "反诈剧本杀-代码研读手册")
    out = os.path.join(out_dir, base + ".docx")
    doc.save(out)
    print("已生成:", out)

    # 用 python-docx 重新打开做一次结构与内容校验
    from docx import Document
    check = Document(out)
    paras = [p for p in check.paragraphs if p.text.strip()]
    tables = check.tables
    pics = sum(1 for rel in check.part.rels.values() if "image" in rel.reltype)
    print("段落(非空):", len(paras), " 表格:", len(tables), " 图片:", pics)
    print("开头三行:", [p.text for p in paras[:3]])
    assert len(paras) > 100, "段落数太少，生成可能不完整"
    assert len(tables) > 30, "表格数太少"
    assert pics >= 5, "截图太少"


if __name__ == "__main__":
    main()
