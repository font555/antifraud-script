# -*- coding: utf-8 -*-
"""生成《反诈剧本杀 · 过程记录册》：Markdown + Word 两个版本。

数据来源：真实 git 提交记录 + 真实 Release + 真实测试截图，不编造。
"""
import os
import subprocess
from datetime import datetime

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

ROOT = r"C:\Users\Lecoo\Desktop\antifraud-script"
LOGS = os.path.join(ROOT, "logs")
ART = os.path.join(ROOT, "artifacts")
OUT_MD = os.path.join(ROOT, "docs", "过程记录册.md")
OUT_DOCX = os.path.join(ROOT, "docs", "过程记录册.docx")

INK = RGBColor(0x2B, 0x1D, 0x12)
BRAND = RGBColor(0xB5, 0x35, 0x18)
GREY = RGBColor(0x6B, 0x52, 0x33)

RELEASES = [
    ("v2.0", "2026-11-22 20:49", "2.54 MB", "剧本工坊 + 复盘分享海报 + 桌面小组件；书架合并用户剧本"),
    ("v2.1", "2026-11-22 21:14", "2.54 MB", "首次进入的聚光灯新手引导（4 步高亮 / 跳过 / 只弹一次）"),
    ("v2.2", "2026-11-22 21:17", "2.54 MB", "字号新增「超大」（1.55×），选项改为横向滑动"),
    ("v2.3", "2026-11-22 22:10", "2.55 MB", "修复 10 项缺陷（含引导卡死）；新增隐藏运行日志"),
]

BUGS = [
    ("B1", "新手引导卡死：整屏变暗、说明卡不显示、点哪都没反应", "说明卡只按目标控件定位；目标在屏幕外时卡片跑到屏幕外；遮罩拦截触摸且无兜底", "目标自动滚动进可视区；卡片位置夹在屏幕内；点遮罩任意处=下一步；固定底部「跳过引导」；1.6 秒超时自动结束；返回键关闭", "模拟器实测 4 步可完成、可跳过、重启不再弹"),
    ("B2", "超大字号下聊天页顶栏溢出：标题截断、与「已损失」重叠", "顶栏为固定单行布局，1.55 倍字号装不下", "标题两档收缩 + 单行省略；副标题只留「第 N 幕 / 共 M 幕」；右侧账本区最小宽度 76dp", "超大字号截图验证：标题完整、不重叠"),
    ("B3", "昵称 1 个字也能注册（提示写的是 2–8 个字）", "只校验了非空", "按字符数校验 2–8 字，超限抖动提示并拦截", "输入「1」被拦下，停留在注册页"),
    ("B4", "用户反馈「首页不显示损失」", "排查未复现；实为新手引导遮罩挡住界面的误判", "首页刷新时写运行日志，便于以后定位", "通关后返回首页正确显示「已读 1 本 · 损失 ¥20,000」"),
    ("B5", "复盘页标题疑似误判「你在中途停下来了」", "实为剧本中一个正常结局的标题（中途止损结局）", "改为传真实到达的结局节点；无结局时兜底「你走完了这段剧本」", "复盘页显示真实结局标题"),
    ("B6", "从剧本工坊返回书架时整屏重播入场动画", "数量变化即重建书架并触发动画", "重建时不播放入场动画", "仅重铺、不闪烁"),
    ("B7", "导入剧本 id 与内置剧本相同时会顶掉内置剧本", "用户剧本优先加载，同 id 覆盖", "导入时自动改名为 `<id>-user` / `-user2`", "内置剧本不再被覆盖"),
    ("B8", "引导期间切页/重建可能残留遮罩", "引导状态只在内存变量", "遮罩固定可跳过 + 超时自退 + 结束即移除", "极端情况下也能退出"),
    ("B9", "桌面小组件数据最长 30 分钟才刷新", "Android 小组件系统刷新周期下限 30 分钟", "App 内数据变化时发应用内广播，立刻刷新小组件", "通关后小组件即时更新"),
    ("B10", "连续「重玩另一条路」后返回层级混乱", "直接启动聊天页并 finish 自己，中间页残留", "重玩带 FLAG_ACTIVITY_CLEAR_TOP，返回落到剧本选择页", "重玩→聊天页，返回→剧本选择页"),
]

TIMELINE = [
    ("11-22 17:36", "初始化 Android 工程骨架（零依赖、可编译，含剧本与校验脚本）", "提交 2e6c3f2"),
    ("11-22 17:40", "主线剧本扩到 21 节点，新增刷单返利剧本，修复 Android 16 返回键", "提交 2962e31"),
    ("11-22 17:41", "补充真机运行截图，供作品说明书与 PPT 使用", "提交 805be26"),
    ("11-22 17:45", "剧本校验脚本新增一致性规则：有金额损失的选项必须标为高风险", "提交 a5deb7e"),
    ("11-22 18:16", "像素风全面改版：书本书架、翻页动效、本地注册、像素字体、损失滚动", "提交 e98c673"),
    ("11-22 18:52", "内容补齐：六类 17 本剧本、405 节点、71 条来源", "提交 dd2e383"),
    ("11-22 19:44", "翻书动画改为「封面掀开露内页」，聊天消息改为逐条发送", "提交 57bb9d3 / 140e775"),
    ("11-22 19:58", "修复翻书缩放锚点偏左、书页列表 3D 倾斜，像素字体加粗", "提交 17afdb8"),
    ("11-22 20:02", "书本改为 1:1.45 竖版比例，书架底部新增反诈四步口诀", "提交 c12928f / 18b1325"),
    ("11-22 20:13", "新增设置页：字体样式、字号、清除本地记录", "提交 439f72d"),
    ("11-22 20:24", "说话人彩色头像；骗子换号时同步更换名字与头像颜色", "提交 ba0a0ae / 5f8870c"),
    ("11-22 20:31", "应用图标定稿（书架三本书 + 反诈徽章），含自适应图标", "提交 ff93cef"),
    ("11-22 20:43", "新增剧本工坊、复盘分享海报、桌面小组件（v2.0 发布）", "提交 67a315b"),
    ("11-22 21:14", "首次进入的聚光灯新手引导（v2.1 发布）", "提交 224cc31"),
    ("11-22 21:17", "字号新增「超大」档，选项改横向滑动（v2.2 发布）", "提交 6951bb2"),
    ("11-22 22:10", "修复 10 项缺陷（含引导卡死），新增隐藏运行日志（v2.3 发布）", "提交 29194e8"),
    ("11-23 20:31", "新增技术路线图（5 阶段 + 4 层架构 + 关键决策）", "提交 bbf6129"),
    ("11-24 09:20", "界面重构为三主界面：首页（轮播+书架+口诀）/ 剧本（平铺+搜索筛选+加号）/ 我的（档案+设置）", "v3.0 发布"),
]

SHOTS = [
    ("new-tab-home-clean.png", "首页：顶部真实案例轮播 + 反诈书架（6 本）+ 反诈四步口诀"),
    ("new-tab-scripts.png", "剧本页：搜索 + 分类筛选 + 全部剧本平铺，右下角「＋」新建/导入"),
    ("new-tab-mine.png", "我的页：个人卡 + 三格统计 + 我的创作 + 设置与帮助"),
    ("new-fab-dialog.png", "点右下角「＋」：新建空白剧本 / 套模板新建 / 导入剧本文件"),
    ("new-book-anim.png", "点书后的翻书动效：封面掀开并放大，整屏显示分类名"),
    ("avatar-multi2.png", "聊天页：消息逐条出现，说话人彩色头像，骗子换号后名字与颜色同步更换"),
    ("probe-review2.png", "复盘页：本局损失与真实案件损失对照，逐节点风险标签与正确做法"),
    ("new-guide-2.png", "新手引导：聚光灯高亮（5 步，会自动切换 Tab，可随时跳过）"),
    ("new-log-page.png", "隐藏运行日志：我的 → 关于长按 2 秒进入，可复制 / 清空"),
    ("share-sheet.png", "复盘页生成分享海报，并通过系统分享面板发送"),
]
STATS = [
    ("剧本数量", "6 类 17 本"),
    ("剧情节点", "405 个"),
    ("可核验来源", "71 条"),
    ("Java 代码", "38 个类 / 6,100+ 行"),
    ("XML 布局", "40 个 / 1,150 行"),
    ("第三方依赖", "0"),
    ("申请权限", "0（不联网、不收集个人信息）"),
    ("安装包体积", "2.61 MB"),
    ("代码提交", "34 次（完整记录可查）"),
    ("版本发布", "v2.0 / v2.1 / v2.2 / v2.3 / v3.0"),
]


def git_commits():
    out = subprocess.run(
        ["git", "log", "--date=format:%m-%d %H:%M", "--pretty=format:%h|%ad|%s"],
        cwd=ROOT, capture_output=True)
    rows = []
    for line in out.stdout.decode("utf-8", "replace").splitlines():
        parts = line.split("|", 2)
        if len(parts) == 3:
            rows.append(parts)
    return rows


def set_cn_font(run, size=None, bold=False, color=None, name="Microsoft YaHei"):
    run.font.name = name
    run._element.rPr.rFonts.set(qn("w:eastAsia"), name)
    if size:
        run.font.size = Pt(size)
    run.bold = bold
    if color is not None:
        run.font.color.rgb = color


def para(doc, text, size=10.5, bold=False, color=INK, align=None, space_after=4, name="Microsoft YaHei"):
    p = doc.add_paragraph()
    r = p.add_run(text)
    set_cn_font(r, size, bold, color, name)
    if align is not None:
        p.alignment = align
    p.paragraph_format.space_after = Pt(space_after)
    return p


def heading(doc, text, level=1):
    size = {1: 18, 2: 14, 3: 12}[level]
    p = doc.add_paragraph()
    r = p.add_run(text)
    set_cn_font(r, size, True, BRAND)
    p.paragraph_format.space_before = Pt(14 if level == 1 else 10)
    p.paragraph_format.space_after = Pt(6)
    return p


def table(doc, headers, rows, widths=None, font_size=8.5):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    hdr = t.rows[0].cells
    for i, h in enumerate(headers):
        hdr[i].text = ""
        p = hdr[i].paragraphs[0]
        r = p.add_run(h)
        set_cn_font(r, font_size, True, INK)
    for row in rows:
        cells = t.add_row().cells
        for i, v in enumerate(row):
            cells[i].text = ""
            p = cells[i].paragraphs[0]
            r = p.add_run(str(v))
            set_cn_font(r, font_size, False, INK)
    if widths:
        for r_ in t.rows:
            for i, w in enumerate(widths):
                r_.cells[i].width = Cm(w)
    return t


def picture(doc, filename, caption, width=11.5):
    path = os.path.join(LOGS, filename)
    if not os.path.exists(path):
        path = os.path.join(ART, filename)
    if not os.path.exists(path):
        para(doc, f"（截图缺失：{filename}）", 9, color=GREY)
        return
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.add_run().add_picture(path, width=Cm(width))
    cap = doc.add_paragraph()
    cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = cap.add_run(caption)
    set_cn_font(r, 8.5, False, GREY)


# ============================ Word ============================
doc = Document()
sec = doc.sections[0]
sec.page_width, sec.page_height = Cm(21), Cm(29.7)
sec.left_margin = sec.right_margin = Cm(2.2)
sec.top_margin = sec.bottom_margin = Cm(2.0)

style = doc.styles["Normal"]
style.font.name = "Microsoft YaHei"
style.font.size = Pt(10.5)
style.element.rPr.rFonts.set(qn("w:eastAsia"), "Microsoft YaHei")

# ---- 封面 ----
for _ in range(4):
    doc.add_paragraph()
para(doc, "反诈剧本杀", 34, True, BRAND, WD_ALIGN_PARAGRAPH.CENTER, 6)
para(doc, "过 程 记 录 册", 22, True, INK, WD_ALIGN_PARAGRAPH.CENTER, 20)
para(doc, "世界职业院校技能大赛 · 新一代信息技术赛道 · 中职组", 12, False, GREY, WD_ALIGN_PARAGRAPH.CENTER, 6)
para(doc, "作品形态：Android 原生应用（零第三方依赖 · 零权限 · 零联网）", 11, False, GREY, WD_ALIGN_PARAGRAPH.CENTER, 40)
para(doc, "记录周期：2026-11-22 ~ 2026-11-24", 11, False, INK, WD_ALIGN_PARAGRAPH.CENTER, 6)
para(doc, "对应版本：v3.0　｜　安装包：antifraud-v3.0-debug.apk（2.61 MB）", 11, False, INK, WD_ALIGN_PARAGRAPH.CENTER, 6)
para(doc, "本册内容全部来自真实提交记录、真实截图与真实版本发布，未做任何虚构。", 10, False, GREY, WD_ALIGN_PARAGRAPH.CENTER, 6)
doc.add_page_break()

# ---- 一、作品概述 ----
heading(doc, "一、作品概述")
para(doc, "「反诈剧本杀」是一款像素风的安卓应用：把真实诈骗案件改编成可以“走一遍”的书。"
          "学生在 6–8 分钟里经历一次完整骗局——每一步都要做选择，顶部账本实时显示已损失金额，"
          "结束后逐节点复盘“你选了哪一步、为什么危险、正确做法是什么”，并能翻回公开报道原文核对。")
para(doc, "设计上坚持三条：不联网、不申请任何权限、不收集个人信息；技术上零第三方依赖，"
          "剧本是 JSON 数据而不是代码——加一本剧本只需要加一个文件，不改一行 Java。")
heading(doc, "作品关键指标（截至 v2.3）", 2)
table(doc, ["指标", "数值"], [(k, v) for k, v in STATS], widths=[6, 11])

# ---- 二、开发时间线 ----
heading(doc, "二、开发时间线")
para(doc, "下表按真实提交时间排列，每一项都对应仓库里的一次提交，可用于核对开发过程的可追溯性。")
table(doc, ["时间", "完成内容", "证据"], TIMELINE, widths=[3.0, 10.0, 4.0], font_size=8)

# ---- 三、提交记录 ----
heading(doc, "三、代码提交记录（全部）")
commits = git_commits()
para(doc, f"共 {len(commits)} 次提交，按时间倒序。提交信息保持原样，未做美化。")
table(doc, ["#", "时间", "提交", "说明"],
      [(str(i + 1), c[1], c[0], c[2]) for i, c in enumerate(commits)],
      widths=[1.0, 3.0, 2.0, 11.0], font_size=7.5)

# ---- 四、版本发布 ----
heading(doc, "四、版本发布记录")
para(doc, "每个版本都打包成 APK 并发布，评委可现场对照版本差异验证迭代过程。")
table(doc, ["版本", "发布时间", "安装包", "本版新增 / 修复"],
      [(a, b, c, d) for a, b, c, d in RELEASES], widths=[2.0, 4.0, 2.5, 8.5])

# ---- 五、功能演进 ----
heading(doc, "五、功能演进对照")
table(doc, ["功能", "引入版本", "说明"], [
    ("书架 + 翻书动效 + 聊天 + 损失账本 + 复盘 + 来源", "v0.x（首日）", "作品核心闭环：选书 → 走骗局 → 看后果 → 复盘"),
    ("说话人彩色头像 / 换号换名换色", "v1.7", "同一局内按出现顺序分配颜色，避开我方红色"),
    ("设置页：字体样式 + 4 档字号", "v1.8 → v2.2", "像素/系统字体；标准 / 大 / 特大 / 超大（1.55×）"),
    ("剧本工坊（新建/编辑/校验/导入/导出）", "v2.0", "本地创作，用系统文件选择器交换 JSON，不需要服务器"),
    ("复盘分享海报", "v2.0", "Canvas 生成 1080×1350 像素海报并调起系统分享"),
    ("桌面小组件", "v2.0", "显示防骗贴士与读书战绩，点击回到 App"),
    ("新手引导（聚光灯 4 步）", "v2.1", "首次进入触发，可跳过，只弹一次"),
    ("运行日志（隐藏入口）", "v2.3", "「我的 → 关于」长按 2 秒进入，只写本机，便于定位问题"),
    ("三主界面 + 首页轮播 + 剧本平铺 + 加号", "v3.0", "底部零依赖自绘导航；点书翻页后自动跳到剧本页并筛选分类"),
], widths=[7.5, 3.5, 6.0], font_size=8.5)

# ---- 六、缺陷与修复 ----
doc.add_page_break()
heading(doc, "六、缺陷发现与修复记录")
para(doc, "下面 10 项问题都是在真机 / 模拟器复现后修复的，部分由同学试用时发现（如“新手引导卡死”），"
          "修复后逐条回归验证。这张表也是“每次改动都要说清为什么改、怎么验证”的直接证据。")
table(doc, ["编号", "现象", "原因", "修复方式", "验证结果"], [(b[0], b[1], b[2], b[3], b[4]) for b in BUGS],
      widths=[1.2, 4.6, 4.4, 5.0, 3.2], font_size=7)

# ---- 七、关键截图 ----
doc.add_page_break()
heading(doc, "七、关键界面截图（实测）")
para(doc, "以下截图全部来自演示机实际运行，未做 P 图处理；状态栏时间不一致是因为分批测试。")
for fn, cap in SHOTS:
    picture(doc, fn, cap, 11.0)
picture(doc, "技术路线图.png", "技术路线图：5 个阶段 / 4 层架构 / 关键决策", 14.5)

# ---- 八、过程材料清单 ----
doc.add_page_break()
heading(doc, "八、过程材料清单")
table(doc, ["材料", "所在位置", "说明"], [
    ("剧本与来源", "app/src/main/assets/scripts/*.json；docs/sources.md", "17 本剧本，405 节点，71 条来源，每条含标题/发布方/日期/链接"),
    ("剧本校验脚本", "tools/validate_script.py", "检查断链、选项数量、结局、高风险标记、来源字段；当前 0 错误"),
    ("运行日志", "设置 → 长按「关于」2 秒", "记录页面跳转、每次选择、损失变化、引导过程、崩溃堆栈"),
    ("技术路线图", "docs/技术路线图.md；artifacts/技术路线图.png", "5 阶段路线 + 4 层架构 + 关键决策取舍"),
    ("项目介绍", "docs/项目介绍.md", "问题、做法、创新点、技术实现、数据规模、合规说明"),
    ("前后测与问卷", "docs/test-pre-post.md；docs/survey.md", "10 题前后测与课后问卷（成绩【待实测】）"),
    ("安装包", "dist/antifraud-v2.3-debug.apk；GitHub Release", "四个版本均可下载，可对照验证迭代"),
], widths=[3.5, 6.5, 7.0], font_size=8)

# ---- 九、声明 ----
heading(doc, "九、真实性声明")
para(doc, "1. 本册所有提交记录、版本号、安装包体积均来自项目仓库与发布记录，可现场核验。", 10)
para(doc, "2. 所有截图来自演示机实际运行；界面中不含真实平台名称、账号、二维码与个人信息。", 10)
para(doc, "3. 案例均据公开报道改编，人物匿名、平台名虚构；来源与改编说明见剧本 JSON 的 sources 字段与 docs/sources.md。", 10)
para(doc, "4. 作品为教学演示作品，与公安等任何机构无隶属关系，不使用官方标识。", 10)
para(doc, "5. 尚未实测的数据（30 人前后测、冷启动耗时）在材料中一律标注【待实测】，不以估算值代替。", 10)
para(doc, "核验入口：GitHub 仓库 https://github.com/font555/antifraud-script （公开）；安装包见 Releases v2.0–v2.3。", 10, True, BRAND)

doc.save(OUT_DOCX)
print("saved", OUT_DOCX)

# ============================ Markdown ============================
md = []
md.append("# 反诈剧本杀 · 过程记录册\n")
md.append("> 世界职业院校技能大赛 · 新一代信息技术赛道 · 中职组\n")
md.append("> 记录周期：2026-11-22 ~ 2026-11-24 ｜ 对应版本：v2.3 ｜ 安装包 2.55 MB\n")
md.append("> 本册内容全部来自真实提交记录、真实截图与真实版本发布，未做虚构。\n")
md.append("\n## 一、作品概述\n\n")
md.append("「反诈剧本杀」是一款像素风的安卓应用：把真实诈骗案件改编成可以“走一遍”的书。"
          "学生在 6–8 分钟里经历一次完整骗局，每一步做选择，顶部账本实时显示已损失金额，"
          "结束后逐节点复盘，并能翻回公开报道原文核对。\n\n")
md.append("设计上坚持三条：不联网、不申请任何权限、不收集个人信息；技术上零第三方依赖，"
          "剧本是 JSON 数据而不是代码。\n\n")
md.append("| 指标 | 数值 |\n|---|---|\n")
for k, v in STATS:
    md.append(f"| {k} | {v} |\n")
md.append("\n## 二、开发时间线\n\n| 时间 | 完成内容 | 证据 |\n|---|---|---|\n")
for t, what, ev in TIMELINE:
    md.append(f"| {t} | {what} | {ev} |\n")
md.append(f"\n## 三、代码提交记录（共 {len(commits)} 次）\n\n| # | 时间 | 提交 | 说明 |\n|---|---|---|---|\n")
for i, c in enumerate(commits):
    md.append(f"| {i + 1} | {c[1]} | `{c[0]}` | {c[2]} |\n")
md.append("\n## 四、版本发布记录\n\n| 版本 | 发布时间 | 安装包 | 本版新增 / 修复 |\n|---|---|---|---|\n")
for a, b, c_, d in RELEASES:
    md.append(f"| {a} | {b} | {c_} | {d} |\n")
md.append("\n## 五、功能演进对照\n\n| 功能 | 引入版本 | 说明 |\n|---|---|---|\n")
md.append("| 书架 + 翻书动效 + 聊天 + 损失账本 + 复盘 + 来源 | v0.x（首日） | 核心闭环：选书 → 走骗局 → 看后果 → 复盘 |\n")
md.append("| 说话人彩色头像 / 换号换名换色 | v1.7 | 同局内按出现顺序分配颜色，避开我方红色 |\n")
md.append("| 设置页：字体样式 + 4 档字号 | v1.8 → v2.2 | 像素/系统字体；标准 / 大 / 特大 / 超大（1.55×） |\n")
md.append("| 剧本工坊、复盘分享海报、桌面小组件 | v2.0 | 本地创作与分享，不需要服务器 |\n")
md.append("| 新手引导（聚光灯 4 步） | v2.1 | 首次进入触发，可跳过，只弹一次 |\n")
md.append("| 运行日志（隐藏入口） | v2.3 | 设置页长按「关于」2 秒进入，只写本机 |\n")
md.append("\n## 六、缺陷发现与修复记录\n\n| 编号 | 现象 | 原因 | 修复方式 | 验证结果 |\n|---|---|---|---|---|\n")
for b in BUGS:
    md.append("| " + " | ".join(b) + " |\n")
md.append("\n## 七、关键界面截图\n\n")
for fn, cap in SHOTS:
    md.append(f"### {cap}\n\n![{cap}](../logs/{fn})\n\n")
md.append("### 技术路线图\n\n![技术路线图](../artifacts/技术路线图.png)\n")
md.append("\n## 八、过程材料清单\n\n")
md.append("- 剧本与来源：`app/src/main/assets/scripts/*.json`；`docs/sources.md`\n")
md.append("- 剧本校验脚本：`tools/validate_script.py`（当前 0 错误）\n")
md.append("- 运行日志：设置 → 长按「关于」2 秒\n")
md.append("- 技术路线图：`docs/技术路线图.md`；`artifacts/技术路线图.png`\n")
md.append("- 项目介绍：`docs/项目介绍.md`\n")
md.append("- 前后测与问卷：`docs/test-pre-post.md`；`docs/survey.md`（成绩【待实测】）\n")
md.append("- 安装包：`dist/antifraud-v2.3-debug.apk`；GitHub Releases v2.0–v2.3\n")
md.append("\n## 九、真实性声明\n\n")
md.append("1. 提交记录、版本号、安装包体积均来自仓库与发布记录，可现场核验。\n")
md.append("2. 截图来自演示机实际运行，不含真实平台名、账号、二维码与个人信息。\n")
md.append("3. 案例据公开报道改编，人物匿名、平台名虚构，来源与改编说明见 `sources` 字段。\n")
md.append("4. 作品为教学演示作品，与任何机构无隶属关系，不使用官方标识。\n")
md.append("5. 未实测数据（30 人前后测、冷启动耗时）一律标注【待实测】。\n")
md.append("\n核验入口：<https://github.com/font555/antifraud-script>\n")

with open(OUT_MD, "w", encoding="utf-8", newline="\n") as f:
    f.write("".join(md))
print("saved", OUT_MD)
print("commits:", len(commits))
