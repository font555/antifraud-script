"""代码研读手册 · 第一部分：封面、目录、第 0-3 章。

一、这个脚本负责手册的哪一部分
    它是 61 页手册的“开头 4 章”，共约 12 页：
        封面 + 目录表
        第 0 章  这本书怎么用 —— 谁该读、三种读法、符号约定、10 个名词
        第 1 章  先当用户玩一遍 —— 注册/首页/书架/剧本页/聊天/复盘/我的（全是截图 + 说明）
        第 2 章  工程地图 —— 目录结构、41 个类全景表、依赖流向图、APK 组成
        第 3 章  从哪开始读：5 条阅读路线 —— 每条路线给顺序、耗时、检查点
    这部分的目标读者是“完全没参与开发的人”：不要求会 Java，看完能说清 App 长什么样、
    东西都在哪个目录、想读懂该按什么顺序读。

二、怎么调用
    不单独运行本文件；由总装脚本按顺序调用其中的 4 个 build_xxx 函数：
        python tools/build_manual.py
        （build_manual.py 内部：new_document() → build_cover_and_toc(doc)
          → build_ch1(doc) → build_ch2(doc) → build_ch3(doc) → 交给下一部分...）
    想单独预览这一部分，可临时写：
        from manual_common import new_document
        from manual_part1 import build_cover_and_toc, build_ch1
        doc = new_document(); build_cover_and_toc(doc); build_ch1(doc); doc.save("p1.docx")

三、依赖
    本文件只做“内容排版”，所有排版能力来自 manual_common.py：
    cover/h1/h2/h3/para/bullet/numbered/note/code_block/table/shot 等。
    注意：本文件 import 的是 manual_common，改 import 会连带影响四个 part 脚本，不要动。
"""

import os            # 本文件目前没直接用到 os，但保留与其它 part 脚本一致的导入习惯

from docx.enum.text import WD_ALIGN_PARAGRAPH   # 段落对齐（本文件未直接用，供扩展）
from docx.shared import Cm, Pt                   # 尺寸单位（同上，供扩展）

# 从公共模块引入排版零件 + 两个“现场统计”的函数：
#   class_catalog()  / java_line_count()  用来在封面上自动算“多少个类 / 多少行”
from manual_common import (BRAND, GRAY, ROOT, bullet, class_catalog, code_block,
                           cover, h1, h2, h3, java_lines, java_line_count, new_document, note, numbered,
                           para, set_cn_font, shot, table)


def build_cover_and_toc(doc):
    """画封面 + 目录表 + 第 0 章（“这本书怎么用”）。

    参数:
        doc -- 文档对象（由 build_manual.py 用 new_document() 创建后传进来）
    返回:
        None（所有内容直接写进 doc）
    副作用:
        1) 在封面上现场统计并打印类的数量和总行数（扫描 JAVA_DIR，不写死数字，
           所以代码改了手册里的规模数字会自动跟着变）；
        2) 生成一张 11 行的目录表；
        3) 写完第 0 章的 4 个小节。
    说明:
        第 0 章是“阅读说明书”，读者如果只读一页，读的就是这一页。
    """
    # ---- 封面：标题 / 副标题 / 四行信息（版本、工程、规模、日期）----
    cover(doc,
          "反诈剧本杀 · 代码研读手册",
          "给没参与开发的人看的代码说明（含逐行注释与改动指引）",
          ["配套版本：v5.4", "工程：antifraud-script（Android 原生 Java，零第三方依赖）",
           # 这两个数字是现场扫源码算出来的：类个数、Java 总行数
           "代码规模：%d 个类 / %d 行 Java；39 个 XML；17 本剧本 13888 行 JSON"
           % (len(class_catalog()), sum(java_line_count(r) for r, _, _, _ in class_catalog())),
           "编写日期：2026-11-25"])

    # ---------------- 目录 ----------------
    # 目录是手写的三列表（章 / 标题 / 讲什么），不用 Word 自动目录：
    # 自动目录需要预先套用“标题样式”再更新域，生成脚本里不方便，手写更稳定。
    h2(doc, "目  录")
    toc = [
        ("第 0 章", "这本书怎么用", "谁该读、怎么读、先记住哪些名词"),
        ("第 1 章", "先当用户玩一遍", "注册 → 引导 → 首页 → 剧本 → 聊天 → 复盘 → 我的"),
        ("第 2 章", "工程地图", "目录结构、41 个类全景表、依赖关系、APK 组成"),
        ("第 3 章", "从哪开始读：5 条阅读路线", "半小时/半天/一天三种读法，附检查点"),
        ("第 4 章", "核心代码逐行读（上）", "MainActivity / ChatActivity / ScriptRepository / ReviewActivity / ScriptsTabView"),
        ("第 5 章", "其余 36 个类（方法级）", "engine、model、界面底座、各页面、工坊、分享"),
        ("第 6 章", "数据长什么样", "categories.json、剧本 JSON 逐字段、tips、covers"),
        ("第 7 章", "变量与命名", "命名规范、60+ 高频变量表、易混淆组、缩写表"),
        ("第 8 章", "关键机制", "三 Tab、剧本引擎、损失账本、翻书、轮播、引导、像素风、零权限"),
        ("第 9 章", "动手改一改", "改台词/加剧本/换颜色/换字体/改配图/打包发布"),
        ("第 10 章", "术语表与附录", "术语 40 条、行号索引、常见错误、版本记录"),
    ]
    table(doc, ["章", "标题", "讲什么"], toc, widths=[2.2, 6.0, 8.8], size=9.5)
    note(doc, "本手册分两次交付：第 0-4 章为第一部分（阅读方法与核心代码），第 5-10 章为第二部分（其余文件与动手改）。")

    # ---------------- 第 0 章 ----------------
    h1(doc, "第 0 章  这本书怎么用")

    # ---- 0.1 三种读者 ----
    h2(doc, "0.1 这本书写给谁")
    para(doc, "写给三类人：")
    bullet(doc, "队里没有参与写代码的同学：需要能讲清楚“这个 App 是怎么做出来的”，答辩被追问时知道去哪个文件找答案。")
    bullet(doc, "指导老师或评委：想快速判断代码是不是学生自己写的、结构是否清楚。")
    bullet(doc, "以后接手的人：想改一句话、加一个剧本、换个颜色，不用从头读 8544 行。")

    para(doc, "不需要你会写 Java。书里所有代码都配了中文注释，语法只讲“这行在干什么”，不讲语法细节。")

    # ---- 0.2 按时间选读法（三行表格：30 分钟 / 半天 / 2-3 天）----
    h2(doc, "0.2 三种读法，按你的时间选")
    table(doc, ["读法", "花多久", "读哪几章", "读完能做到"],
          [["只看地图", "30 分钟", "第 1、2 章", "说清楚项目有几个部分、每个部分在哪个目录"],
           ["跟一条主线", "半天", "第 1、2、3 章 + 第 4 章 4.1、4.2 节", "讲清楚“用户点开一本书，到复盘结束”中间发生了什么"],
           ["完整通读", "2-3 天", "全部", "能独立改台词、加剧本、重新打包"]],
          widths=[2.6, 2.4, 6.4, 5.6], size=9.5)

    # ---- 0.3 全书统一的符号：★ / ⏱ / ⚠️ / 行尾 // ----
    h2(doc, "0.3 书里的符号约定")
    bullet(doc, "★ 表示这是核心文件或核心方法，答辩最可能被问到。")
    bullet(doc, "⏱ 表示建议阅读时间。")
    bullet(doc, "⚠️ 表示容易看错、或者改动时容易出问题的地方。")
    bullet(doc, "代码块里行尾的 // 中文说明，就是“这一行在干什么”。")

    # ---- 0.4 10 个名词对照表：中文说法 ↔ 代码里的类名 ----
    # 这张表是全书的地基：后面每一章都会出现右边这一列的类名，先建立对应关系
    h2(doc, "0.4 先记住 10 个名词")
    table(doc, ["名词", "一句话解释", "在代码里叫什么"],
          [["剧本", "一本完整的反诈故事，对应一个 JSON 文件", "Script"],
           ["节点", "剧本里的一“幕”，包含几条消息和几个选项", "StoryNode"],
           ["选项", "玩家可以点的一个选择，带风险等级和损失金额", "Choice"],
           ["分类", "书架上的一本书，代表一类骗局（共 6 类）", "Category"],
           ["损失账本", "玩家在剧情里累计被骗的钱数", "UserStore.totalLoss / loss"],
           ["复盘", "玩完后逐节点展示“你选了什么、风险、损失”的页面", "ReviewActivity"],
           ["来源", "每条剧情依据的公开报道，共 71 条", "CaseSource"],
           ["工坊", "玩家自己写剧本、导出、导入的地方", "StudioActivity"],
           ["轮播", "首页顶部自动播放的案例卡片", "CarouselView"],
           ["引导", "第一次进入时的聚光灯新手教程", "GuideOverlay"]],
          widths=[2.6, 8.4, 5.8], size=9.5)


def build_ch1(doc):
    """写第 1 章“先当用户玩一遍”（约 4 页，以截图为主）。

    参数:
        doc -- 文档对象
    返回:
        None
    说明:
        读代码之前先看界面，是这一章的设计意图：先建立“用户看到什么”的直觉，
        后面看 ChatActivity / HomeTabView 的代码时才知道那些控件在屏幕上的位置。
        全章 7 个小节，每节一张截图 + 一两句说明，最后一句话总结本章要点。
        截图走 shot()，logs/ 里缺图时自动降级成提示框，不会让生成中断。
    """
    h1(doc, "第 1 章  先当用户玩一遍")
    para(doc, "读代码之前，先知道这个 App 长什么样、用户怎么走。这一章全是截图，看一遍 10 分钟。")

    # 1.1 注册 + 引导：第一次打开 App 的完整流程
    h2(doc, "1.1 首次进入：注册 + 5 步新手引导")
    para(doc, "第一次打开会先要求起一个昵称、选一个像素头像（只存在本机），然后进入 5 步引导。")
    shot(doc, "avatar-register.png", 7.6, "图 1-1 注册页：昵称 + 6 个像素头像（选中项有红框）")
    para(doc, "引导的 5 步是：轮播 → 书架 → 剧本列表 → 搜索框右边的加号 → 我的页的字体设置。"
              "引导只能用「下一步」推进，点空白处不会前进，也不会误触下面的页面。")

    # 1.2 首页三块：轮播 + 书架 + 数据条
    h2(doc, "1.2 首页：轮播 + 书架 + 数据条")
    shot(doc, "carousel-262.png", 7.4, "图 1-2 首页：顶部真实案例轮播（点图片可全屏放大），下面是 6 本书")
    para(doc, "轮播卡片的结构是：标题、来源与日期、一句案情、以及下方一张“示意图”。"
              "点文字区会弹出案例详情（可复制原文链接），点图片区会全屏放大。")

    # 1.3 书架：6 本书对应 6 类骗局
    h2(doc, "1.3 书架：6 本书 = 6 类骗局")
    shot(doc, "books-gold2.png", 7.4, "图 1-3 书架：每本书有分类图标、烫金书名、底部辅色带")
    para(doc, "点一本书会播放“翻书”动画，然后跳到剧本页并自动筛选出这一类骗局。")

    # 1.4 剧本页：搜索 + 分类筛选 + 加号（导入/新建入口）
    h2(doc, "1.4 剧本页：搜索 + 筛选 + 加号")
    para(doc, "17 本剧本平铺在这里，顶部可以搜索，下面一排 chips 按类型筛选；"
              "搜索框右边的「＋」可以新建空白剧本、套模板、或导入同学发来的 JSON。")
    shot(doc, "fast-plus2-top.png", 7.4, "图 1-4 剧本页：搜索框、分类筛选、17 本剧本")

    # 1.5 聊天页：本项目最核心的交互
    h2(doc, "1.5 玩一个剧本：聊天 → 选择 → 账本")
    shot(doc, "avatar-chat2.png", 7.4, "图 1-5 聊天页：逐条消息、底部 3 个选项，右上角实时显示已损失金额")
    para(doc, "点选项后，对方会继续发消息，损失账本会累加。走到结局后进入复盘。")

    # 1.6 复盘与海报分享
    h2(doc, "1.6 复盘与分享")
    para(doc, "复盘页逐节点列出“你选了什么 / 风险等级 / 为什么错 / 这一步损失多少钱”，"
              "最后可以生成一张 1080×1350 的像素风海报分享。")

    # 1.7 我的页：头像、战绩、工坊入口、设置、隐藏日志
    h2(doc, "1.7 我的页")
    shot(doc, "avatar-mine3.png", 7.0, "图 1-6 我的页：像素头像、战绩三格、我的创作、设置与帮助")
    para(doc, "「字体与字号」只放两件事；「真实案例来源库」可以查 71 条来源和 9 张自绘配图的说明；"
              "长按「关于」两秒可以打开隐藏的运行日志。")

    # 本章小结：提示框形式，三个问题就是第 2-4 章要回答的
    note(doc, "读完这一章，你应该能回答：这个 App 有哪三个主界面？玩一个剧本要经过几步？复盘页展示什么？")


def build_ch2(doc):
    """写第 2 章“工程地图”（约 4 页）：目录、类清单、依赖关系、APK 组成。

    参数:
        doc -- 文档对象
    返回:
        None
    说明:
        这一章回答“东西都在哪”。三个关键产出：
        1) 目录树（代码块，浅色文本样式）；
        2) 41 个类的全景表——由 class_catalog() 现场扫描生成，
           每行是“文件名 / 真实行数 / 类注释首行”，所以表里的数字永远和代码一致；
        3) 依赖流向图，说明各页面之间谁调谁。
    """
    h1(doc, "第 2 章  工程地图")
    para(doc, "这一章告诉你“东西都在哪”。不需要看懂代码，先记住目录职责。")

    # ---- 2.1 顶层目录树（纯文本代码块，detect_lang 会认成 text 走浅色主题）----
    h2(doc, "2.1 顶层目录")
    tree = """antifraud-script/
├─ app/                         Android 应用本体
│  ├─ src/main/java/…/antifraud/ 全部 Java 代码（41 个类）
│  │  ├─ engine/                 数据与存储（6 个类）
│  │  ├─ model/                  5 个数据模型
│  │  └─ ui/                     界面（30 个类）
│  ├─ src/main/res/              布局、颜色、图标、动画（39 个 XML）
│  ├─ src/main/assets/           离线内容：剧本、案例、口诀、配图、字体
│  ├─ build.gradle               版本号与依赖（依赖为空）
│  └─ AndroidManifest.xml        权限声明（没有任何权限）
├─ docs/                         比赛材料（13 份 md + 过程记录册 docx/pdf）
├─ tools/                        打包、推送、生成材料用的 Python 脚本
├─ artifacts/                    路线图、线框图、配图等图片产物
├─ dist/                         各个版本的 APK
└─ logs/                         开发和测试时的截图与日志（不进仓库）"""
    code_block(doc, tree, size=7.5)

    # ---- 2.2 每个目录 “放什么 / 改这里会怎样” ----
    h2(doc, "2.2 app/ 里每个目录的职责")
    table(doc, ["目录", "放什么", "改这里会怎样"],
          [["java/…/engine", "读数据、存数据、记日志", "改动影响所有页面读到的数据"],
           ["java/…/model", "5 个数据模型（剧本/节点/选项/分类/来源）", "改字段要同步改 JSON 解析"],
           ["java/…/ui", "所有页面和自绘控件", "改动只影响对应界面"],
           ["res/layout", "11 个 XML 布局（大部分界面是纯代码搭的）", "改布局要重新编译"],
           ["res/drawable", "像素风边框、按钮底、进度条等自绘 drawable", "换视觉风格主要改这里"],
           ["res/values", "颜色、字符串、主题", "换主题色改 colors.xml"],
           ["assets/scripts", "17 本剧本 JSON（13888 行）", "加剧本只需加文件"],
           ["assets/data", "分类、案例、口诀、配图清单 4 个 JSON", "改首页内容改这里"],
           ["assets/covers", "9 张自绘配图", "换成自己的图同名替换"],
           ["assets/fonts", "像素字体 ttf", "换字体同名替换"]],
          widths=[3.4, 7.6, 5.8], size=9)

    # ---- 2.3 41 个类全景表：按包分三组，全部由脚本扫描源码生成 ----
    h2(doc, "2.3 41 个类的全景表（★ 为核心）")
    para(doc, "这张表由脚本扫描源码自动生成，行数就是真实行数，一句话职责取的是文件头的注释。"
              "第一次看不用全记，只需要知道“什么功能大概在哪个包”。")
    catalog = class_catalog()                       # 扫描一次，下面三组复用
    # 三个分组：(表标题, 路径前缀)
    groups = [("engine（数据与存储）", "engine/"),
              ("model（5 个数据模型）", "model/"),
              ("ui（界面，共 30 个）", "ui/")]
    for title, prefix in groups:
        h3(doc, title)
        rows = []
        for rel, lines, pkg, cls_doc in catalog:
            if not rel.startswith(prefix):          # 只要当前包的文件
                continue
            name = rel.split("/")[-1]               # 只取文件名，不显示目录
            # 5 个核心文件加 ★ 标记，和目录表、第 4 章的章节顺序对应
            star = "★ " if name in ("MainActivity.java", "ChatActivity.java", "ScriptRepository.java",
                                    "ReviewActivity.java", "ScriptsTabView.java") else ""
            rows.append([name, str(lines), star + cls_doc[:44]])   # 职责截断，防止撑破表格
        table(doc, ["文件", "行数", "职责"], rows, widths=[4.6, 1.4, 10.0], size=8.5)

    # ---- 2.4 依赖流向图：用字符画表达“谁调谁” ----
    h2(doc, "2.4 谁依赖谁：一张流向图")
    flow = """用户点击
   │
   ▼
MainActivity ── 装载三个 Tab 的 View（HomeTabView / ScriptsTabView / MineTabView）
   │
   ├─ HomeTabView ── CarouselView（读 assets/data/cases.json）
   │                └─ PixelBookView（画书）→ 点书 → ScriptPickerActivity
   │
   ├─ ScriptsTabView ── ScriptRepository（读 assets/scripts/*.json）
   │                     └─ 点剧本 → ChatActivity
   │
   └─ MineTabView ── UserStore / SettingsStore / ScriptStore（本机存储）
                      └─ 来源库 → SourcesActivity

ChatActivity ── Script（模型）── StoryNode ── Choice
   │                └─ 每选一次 → PathRecorder 记一步 + UserStore 累加损失
   └─ 结局 → ReviewActivity ── 读 PathRecorder 生成复盘
                          └─ SharePoster 画海报"""
    code_block(doc, flow, size=7.5)

    # ---- 2.5 零依赖的含义（答辩常被问）----
    h2(doc, "2.5 “零依赖”是什么意思")
    para(doc, "打开 app/build.gradle 会看到 dependencies 里是空的：没有 androidx、没有 RecyclerView、"
              "没有任何第三方库。所以代码里的列表用 LinearLayout + ScrollView 手搭，"
              "底部导航、书本、头像、海报全部用 Canvas 自己画。")
    note(doc, "好处：APK 只有 2.7 MB、没有隐私风险、评委翻代码能看到全是自己写的；"
              "代价：很多现成组件要自己实现，代码量偏大（约 8500 行，见附录 A 自动统计）。")

    # ---- 2.6 APK 体积构成（评委常问“2.7MB 都装了什么”）----
    h2(doc, "2.6 APK 里装了什么")
    table(doc, ["内容", "大小（约）", "说明"],
          [["Java 代码（dex）", "1.1 MB", "41 个类编成的字节码"],
           ["17 本剧本 + 4 个数据 JSON", "0.9 MB", "13888 行剧情数据"],
           ["9 张自绘配图", "0.2 MB", "每张 14-30 KB"],
           ["像素字体", "0.4 MB", "Fusion Pixel Font（MIT 许可）"],
           ["资源与图标", "0.1 MB", "39 个 XML、启动图标"],
           ["合计", "2.73 MB", "v5.4 实测"]],
          widths=[5.4, 3.0, 8.4], size=9.5)


def build_ch3(doc):
    """写第 3 章“从哪开始读：5 条阅读路线”（约 3 页）。

    参数:
        doc -- 文档对象
    返回:
        None
    说明:
        这一章是“读法指南”，不是知识本身。核心主张是：别从第一个文件按顺序读，
        按“你想回答什么问题”选路线。5 条路线：
            A 半天看懂主线（最推荐）
            B 一天看懂数据
            C 半天看懂界面怎么搭
            D 半天看懂数据存在哪
            E 工具链（给要打包发布的人）
        每条路线都给顺序表 / 步骤 + 一个“检查点”问题，答不上来就回去重读。
        路线 A 的表里行数用 java_line_count() 现算，所以和源码永远对得上。
    """
    h1(doc, "第 3 章  从哪开始读：5 条阅读路线")
    para(doc, "别从第一个文件按顺序读。按“你想回答什么问题”选一条路线，每条路线都写清楚顺序、时间和检查点。")

    # ---- 路线 A：主线（最推荐），行数全部现场统计 ----
    h2(doc, "3.1 路线 A：半天看懂主线（★最推荐）")
    para(doc, "适合：要在答辩里讲清楚“用户点开一本书之后发生了什么”。")
    table(doc, ["顺序", "文件", "行数", "读的时候盯住什么"],
          [["1", "MainActivity.java", str(java_line_count("ui/MainActivity.java")), "三个 Tab 的 View 怎么创建、怎么切换、返回键怎么处理"],
           ["2", "HomeTabView.java", str(java_line_count("ui/HomeTabView.java")), "首页三块内容怎么拼起来、点书时翻书动画怎么接剧本页"],
           ["3", "ChatActivity.java ★", str(java_line_count("ui/ChatActivity.java")), "消息怎么一条条出现、选项点了之后怎么走下一节点、损失怎么累加"],
           ["4", "ReviewActivity.java ★", str(java_line_count("ui/ReviewActivity.java")), "复盘页的数据是从哪来的（PathRecorder）"],
           ["5", "ScriptRepository.java", str(java_line_count("engine/ScriptRepository.java")), "剧本 JSON 怎么变成 Java 对象"]],
          widths=[1.4, 5.4, 1.4, 8.6], size=9.5)
    para(doc, "⏱ 约 4 小时。每读完一个文件，回来看第 4 章的对应小节，那里有逐行注释。")

    # ---- 路线 B：数据（内容与代码分离是项目最大的可维护性优势）----
    h2(doc, "3.2 路线 B：一天看懂数据")
    para(doc, "适合：要加剧本、改台词、或者被问“你们的内容怎么组织的”。")
    numbered(doc, "先看 assets/data/categories.json：6 个分类，每类有名字和颜色。")
    numbered(doc, "再看 assets/scripts/game-account.json：一本剧本的完整结构（第 6 章有逐字段讲解）。")
    numbered(doc, "看 model 包 5 个类：Script / StoryNode / Choice / Category / CaseSource，都是“字段 + 空构造”。")
    numbered(doc, "最后看 engine/ScriptRepository.java：把 JSON 读成上面这些对象。")
    para(doc, "⏱ 约 6 小时。读完后你应该能自己写一本新剧本并放进工程。")

    # ---- 路线 C：界面（大部分是纯代码搭的，不是 XML）----
    h2(doc, "3.3 路线 C：半天看懂界面怎么搭")
    para(doc, "适合：要改界面、加按钮、换颜色。")
    bullet(doc, "先看 ui/Ui.java（135 行）：所有页面的“工具箱”，dp 转换、造文字、造卡片都在这里。")
    bullet(doc, "再看 ui/BaseActivity.java（30 行）：所有页面继承它，统一应用字号设置。")
    bullet(doc, "然后任选一个页面看布局：HomeTabView（纯代码搭）或 activity_main.xml（XML 搭）。")
    bullet(doc, "最后看 res/values/colors.xml：全应用只有十来个颜色，改主题只改这里。")
    note(doc, "⚠️ 这个项目的界面大部分是“纯代码构建”，不是 XML。看到 new LinearLayout(...) 不要奇怪——"
              "这是为了不引入第三方库，也让布局逻辑和代码在一起。")

    # ---- 路线 D：数据存在哪（“本地存档”的完整清单）----
    h2(doc, "3.4 路线 D：半天看懂数据存在哪")
    table(doc, ["存的东西", "存在哪", "代码文件"],
          [["昵称、头像索引", "SharedPreferences（本机）", "engine/UserStore.java"],
           ["已读本数、累计损失", "SharedPreferences", "engine/UserStore.java"],
           ["字体样式、字号", "SharedPreferences", "engine/SettingsStore.java"],
           ["玩家自己写的剧本", "App 私有目录 filesDir/scripts", "engine/ScriptStore.java"],
           ["运行日志", "filesDir/logs/app.log（最多 400 行）", "engine/AppLog.java"],
           ["复盘分享海报", "cacheDir/share/poster.png", "ui/SharePoster.java"]],
          widths=[4.6, 6.6, 5.6], size=9.5)
    para(doc, "全部在本机，没有服务器、没有账号系统——这也是答辩里“零权限、不联网”的底气。")

    # ---- 路线 E：工具链（给要打包发布的人）----
    h2(doc, "3.5 路线 E：工具链（给要打包发布的人）")
    table(doc, ["工具", "作用", "命令"],
          [["gradle assembleDebug", "编译出 APK", "见第 9.7 节"],
           ["tools/validate_script.py", "检查 17 本剧本格式有没有错", "python tools/validate_script.py"],
           ["tools/draw_covers9.py", "重新生成 9 张配图", "python tools/draw_covers9.py"],
           ["tools/push_snapshot.py", "把代码推到 GitHub（API 方式）", "python tools/push_snapshot.py"],
           ["tools/make_release.py", "创建 Release 并上传 APK", "python tools/make_release.py"],
           ["tools/build_process_book.py", "生成过程记录册 docx", "python tools/build_process_book.py"]],
          widths=[5.0, 6.4, 5.4], size=9)

    # ---- 检查点：每条路线一个自测问题，读完答不上来就回去重读 ----
    h2(doc, "3.6 每条路线的检查点")
    para(doc, "读完一条路线，试着回答下面的问题；答不上来就回去看对应章节。")
    table(doc, ["路线", "检查问题"],
          [["A", "点开一本书之后，代码是怎么知道要显示哪 3 个选项的？损失金额存在哪？"],
           ["B", "一个剧本 JSON 里，nodes 的 key 和 choice.next 是什么关系？"],
           ["C", "为什么这个项目几乎不用 XML 布局？Ui.java 里的 dp() 解决什么问题？"],
           ["D", "清空应用数据后，哪些东西会消失、哪些不会？"],
           ["E", "从改一行代码到手机上装好，要经过哪几步？"]],
          widths=[2.4, 13.6], size=9.5)
