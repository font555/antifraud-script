"""代码研读手册 · 第二部分 A：第 5-7 章（其余类、数据格式、变量命名）。"""

import json
import os

from manual_common import (BRAND, GRAY, ROOT, bullet, code_block, h1, h2, h3,
                           method_table, note, numbered, para, table)


def _scripts_stat():
    """扫描 assets/scripts，返回每本剧本的规模数据。"""
    d = os.path.join(ROOT, "app", "src", "main", "assets", "scripts")
    rows = []
    for f in sorted(os.listdir(d)):
        if not f.endswith(".json"):
            continue
        data = json.load(open(os.path.join(d, f), encoding="utf-8"))
        nodes = data.get("nodes", {})
        choices = sum(len(n.get("options", [])) for n in nodes.values())
        rows.append([
            f.replace(".json", ""),
            data.get("title", ""),
            data.get("categoryId", ""),
            str(len(nodes)),
            str(choices),
            str(data.get("stageCount", "")),
            str(data.get("minutes", "")),
            str(len(data.get("sources", []))),
        ])
    return rows


def build_ch5(doc):
    h1(doc, "第 5 章  其余 36 个类（方法级说明）")
    para(doc, "第 4 章讲了 5 个核心文件。这一章把剩下的类全部过一遍：每个类说清楚“它是干什么的、"
              "关键方法有哪些、改东西时要注意什么”。不用背，当字典用。")

    # ---------- 5.1 engine ----------
    h2(doc, "5.1 engine 包：数据与存储（6 个类）")
    para(doc, "engine 是所有页面共用的“后台”：读剧本、存设置、记日志、记路径。")

    h3(doc, "5.1.1 UserStore：玩家自己的那点数据（88 行）")
    table(doc, ["方法", "作用"],
          [["isRegistered(ctx)", "有没有注册过（没注册就跳注册页）"],
           ["register(ctx, nickname, avatar)", "保存昵称和头像序号"],
           ["nickname(ctx)", "读昵称，没注册时返回“同学”"],
           ["avatar(ctx)", "读头像序号（0-5），越界返回 0"],
           ["addPlay(ctx)", "通关一次：已读 +1"],
           ["addLoss(ctx, money)", "把这一局的损失累加到总账"],
           ["plays(ctx)/totalLoss(ctx)", "读已读本数 / 累计损失"],
           ["reset(ctx)", "清除本地记录（我的页那个入口）"]],
          widths=[6.2, 9.6], size=9)
    para(doc, "存哪里：SharedPreferences，文件名 antifraud_user。卸载 App 或清除数据就没了，符合“本地存档”的设定。")

    h3(doc, "5.1.2 SettingsStore：字体与引导标记（73 行）")
    table(doc, ["成员", "作用"],
          [["fontStyle(ctx)", "字体：0 = 像素字体，1 = 系统字体"],
           ["setFontStyle(ctx, style)", "换字体并存下来"],
           ["fontScale(ctx)", "字号倍数：1.0 / 1.15 / 1.28 / 1.55"],
           ["guideDone(ctx)", "新手引导看没看过"],
           ["setGuideDone(ctx) / setGuideDone(ctx, false)", "标记看过 / 重置（再看一遍）"],
           ["changed", "静态开关：设置改过之后置 true，MainActivity.onResume 看到就 recreate()"]],
          widths=[7.4, 8.4], size=9)
    note(doc, "⚠️ changed 是个静态字段，只在本进程有效。它的作用只有一个：让主界面知道“要按新字号重画一遍”。", "易错点")

    h3(doc, "5.1.3 PathRecorder：复盘页唯一的数据来源（86 行）")
    para(doc, "单例模式（PathRecorder.get()），聊天页每选一次就 record 一步，复盘页从这里读。")
    table(doc, ["Step 字段", "含义"],
          [["index", "第几步（从 1 开始）"],
           ["nodeId", "发生在哪个节点（如 n3）"],
           ["stageName", "幕名（如“骗定金”）"],
           ["choiceText", "玩家选的那句话"],
           ["risk", "high / medium / low"],
           ["tag", "这一步的类型标签（如“脱离官方平台”）"],
           ["feedback", "这一步的点评文案（来自剧本 JSON）"],
           ["cost", "这一步损失的钱"]],
          widths=[4.4, 11.4], size=9)
    table(doc, ["方法", "作用"],
          [["reset()", "重开一局时清空"],
           ["record(nodeId, stageName, choice)", "记一步（字段全部来自选项）"],
           ["getSteps()", "取全部步骤（复盘页遍历它）"],
           ["totalCost()", "累计损失（聊天页右上角、复盘页总分都用它）"],
           ["highRiskCount() / lowRiskCount()", "高风险/低风险选择的步数（复盘页评分用）"]],
          widths=[7.0, 8.8], size=9)
    note(doc, "⚠️ PathRecorder 是单例，不落盘。它只活在这一次游玩过程里："
              "中途退出，这一局的步骤就没了；复盘页读的正是这份内存数据。", "易错点")

    h3(doc, "5.1.4 ScriptStore：玩家自己写的剧本（219 行）")
    para(doc, "内置剧本在 assets（只读），玩家写的剧本存在 App 私有目录 filesDir/scripts/，两者用 id 区分。")
    table(doc, ["方法", "作用"],
          [["listUserIds(ctx)", "列出玩家自己写的剧本 id"],
           ["loadUserScript(ctx, id)", "读一本玩家剧本"],
           ["save(ctx, script)", "保存（新建或覆盖）"],
           ["delete(ctx, id)", "删除"],
           ["importJson(ctx, json)", "★ 校验并导入一个 JSON（不是合法剧本返回 null）"],
           ["exportJson(ctx, script)", "导出成字符串，供分享/保存到文件"]],
          widths=[6.4, 9.4], size=9)

    h3(doc, "5.1.5 AppLog：运行日志（122 行）")
    para(doc, "只在本机写文件：filesDir/logs/app.log，最多保留 400 行。入口藏得深——"
              "我的页长按「关于」两秒。")
    table(doc, ["方法", "作用"],
          [["i(tag, msg)", "普通日志"],
           ["e(tag, msg, throwable)", "错误日志（带堆栈）"],
           ["read(ctx)", "日志页读出来显示"],
           ["clear(ctx)", "清空日志"]],
          widths=[5.6, 10.2], size=9)
    note(doc, "用途：答辩演示时如果出现异常，可以当场打开日志页说明“每一步都有记录”。"
              "这也解释了为什么代码里遍布 AppLog.i(\"CHAT\", ...)。")

    h3(doc, "5.1.6 ScriptRepository：已在第 4.3 节讲完（225 行）")

    # ---------- 5.2 model ----------
    h2(doc, "5.2 model 包：5 个数据模型（共 89 行）")
    para(doc, "这 5 个类都非常短，只有“字段 + 构造方法”，作用是让 JSON 数据在代码里能按名字访问。")

    h3(doc, "5.2.1 Script（剧本）")
    table(doc, ["字段", "类型", "含义"],
          [["id", "String", "剧本 id = JSON 文件名（如 game-account）"],
           ["title", "String", "剧本标题（列表/聊天页顶部显示）"],
           ["scamType", "String", "骗局类型名"],
           ["minutes", "String", "建议时长（如 10-15）"],
           ["intro", "String", "一句话简介（剧本列表卡片显示）"],
           ["startNode", "String", "★ 从哪个节点开始（一般 n1）"],
           ["stageCount", "int", "总幕数（顶栏进度条分母）"],
           ["referenceLoss", "int", "真实案件的参考损失金额"],
           ["referenceNote", "String", "真实案件说明（复盘页/详情页用）"],
           ["sources", "List<CaseSource>", "来源列表（来源页用）"],
           ["nodes", "Map<String, StoryNode>", "★ 全部节点，key 是节点 id"]],
          widths=[3.2, 4.6, 8.0], size=9)

    h3(doc, "5.2.2 StoryNode（节点 = 一“幕”里的一轮对话）")
    table(doc, ["字段", "含义"],
          [["id", "节点 id（n1、n2、d1、s1…）"],
           ["speaker", "谁在说话（卖家/系统/我…）"],
           ["stage", "第几幕（数字）"],
           ["stageName", "幕名（诱饵/骗定金/收割…）"],
           ["messages", "这一轮要说的话（一句一个气泡）"],
           ["options", "玩家可选的 1-3 个选项"],
           ["isEnd", "是不是结局节点"],
           ["endingTitle", "结局标题（isEnd 时才有）"],
           ["endingAdvice", "结局建议（isEnd 时才有）"]],
          widths=[3.6, 12.2], size=9)

    h3(doc, "5.2.3 Choice（选项）")
    table(doc, ["字段", "含义"],
          [["text", "选项文字（玩家看到的那句话）"],
           ["next", "★ 选了之后跳到哪个节点 id；空字符串表示本节点就是结局"],
           ["risk", "high / medium / low（决定复盘页的风险标签和评分）"],
           ["tag", "类型标签（如“脱离官方平台”“坚持官方渠道”）"],
           ["cost", "这一步损失多少钱（0 表示没损失）"],
           ["feedback", "★ 复盘页对这一选的点评（教育点的核心）"]],
          widths=[2.6, 13.2], size=9)

    h3(doc, "5.2.4 Category（书架上的书）")
    table(doc, ["字段", "含义"],
          [["id", "分类 id（game/rebate/service/police/loan/aiface）"],
           ["name", "分类名（游戏账号…）"],
           ["desc", "一句话描述（低价买号 · 私下转账）"],
           ["glyph", "图标字（游/单/服/法/贷/脸）"],
           ["colorInt", "封面主色（从 #C0392B 这样的字符串转成 int）"]],
          widths=[2.8, 13.0], size=9)

    h3(doc, "5.2.5 CaseSource（一条来源）")
    table(doc, ["字段", "含义"],
          [["name", "报道标题"],
           ["publisher", "发布方（人民网四川频道等）"],
           ["date", "发布日期"],
           ["url", "原文链接（点一下复制）"],
           ["usedIn", "★ 这条来源支撑剧本里的哪几步（答辩时逐条对得上）"],
           ["note", "改编说明（人物匿名化、平台名虚构等）"]],
          widths=[2.8, 13.0], size=9)

    # ---------- 5.3 界面底座 ----------
    h2(doc, "5.3 界面底座：所有页面共用的小零件（7 个类）")

    h3(doc, "5.3.1 Ui：全项目的“工具箱”（135 行）")
    table(doc, ["方法", "作用"],
          [["dp(ctx, value)", "★ dp 转像素（不同屏幕密度都对齐）"],
           ["money(int)", "金额加千分位（50400 → 50,400）"],
           ["text(ctx, text, size, color)", "造一个套好像素字体的 TextView"],
           ["card(ctx, paddingH, paddingV)", "造一张米色卡片（项目里所有卡都用它）"],
           ["pixel(view)", "给任意 TextView 套像素字体"],
           ["insetContent(activity)", "处理状态栏/手势条避让"],
           ["riskColor(risk)", "风险等级 → 颜色（high 红 / medium 橙 / low 绿）"]],
          widths=[6.8, 9.0], size=9)

    h3(doc, "5.3.2 BaseActivity（30 行）")
    para(doc, "所有 Activity 的父类。它只做两件事：按用户设置应用字号倍数、在 onCreate 里记一条访问日志。")

    h3(doc, "5.3.3 PTextView（36 行）")
    para(doc, "一个自动套像素字体的 TextView。XML 里把 <TextView> 换成它，就不用每个控件手动设置字体。")

    h3(doc, "5.3.4 Typefaces：字体来源（37 行）")
    para(doc, "pixel(ctx) 从 assets/fonts/fusion_pixel.ttf 加载像素字体；system() 返回系统字体。"
              "两者都做了缓存，避免反复加载。")

    h3(doc, "5.3.5 BottomTabBar：底部三 Tab（180 行）")
    para(doc, "自己画的导航条：三个图标 + 文字，选中项变品牌红。没有用系统 TabLayout（那需要第三方库）。")
    table(doc, ["方法", "作用"],
          [["setOnTabSelected(listener)", "设置回调（MainActivity 里绑 switchTab）"],
           ["setSelected(index)", "高亮第几个 Tab（切页后调用）"]],
          widths=[5.6, 10.2], size=9)

    h3(doc, "5.3.6 TabHost：页面之间的“电话线”（40 行）")
    para(doc, "一个只有 11 个方法的接口，由 MainActivity 实现。三个 Tab 和各子页面都通过它喊话，"
              "不去直接引用 MainActivity，方便以后拆分。")

    # ---------- 5.4 首页 ----------
    h2(doc, "5.4 首页相关（6 个类）")

    h3(doc, "5.4.1 HomeTabView：首页三块内容（669 行）")
    table(doc, ["部分", "作用"],
          [["buildRoot()", "把轮播、书架、底部数据条拼成一页"],
           ["carouselView()", "给引导用：拿到轮播对象"],
           ["shelfView()", "给引导用：拿到书架容器"],
           ["openBook(cover, cat)", "★ 翻书动画：封面放大 → 翻页 → 跳剧本页"],
           ["refresh()", "重新读已读本数/损失，更新数据条"]],
          widths=[5.0, 10.8], size=9)
    para(doc, "翻书动画的做法：把封面 View 复制一份放到最上层，用 View 动画做“放大 + 旋转 + 淡出”，"
              "同时背景变暗，动画结束时跳转。没有用共享元素转场（那需要 androidx）。")

    h3(doc, "5.4.2 CarouselView：轮播（854 行，全项目最大的文件）")
    table(doc, ["部分", "作用"],
          [["Panel", "一张卡片的数据：标题/发布方/日期/标签/金额/案情/链接/配图/自绘说明"],
           ["MIXED", "★ 9 张卡片的固定顺序：案例0、案例1、口诀0、案例2、案例3、口诀1、案例4、案例5、口诀2"],
           ["buildCard(panel)", "搭一张卡片：文字在上、配图在下"],
           ["coverHeight(w, h)", "★ 按图片比例算高度（900×360 → 高度的 40%），保证不裁切"],
           ["onTouchEvent / onInterceptTouchEvent", "左右滑动判定（横向位移大于纵向才算滑动）"],
           ["autoPlay", "每 5 秒自动切下一张"],
           ["showDetail(panel)", "点文字区：弹窗显示案情 + 原文链接 + 配图说明"]],
          widths=[6.6, 9.2], size=9)
    note(doc, "⚠️ 这个文件大是因为几乎每张卡片的样式都写在代码里。想改轮播内容，"
              "优先改 assets/data/cases.json 和 covers.json，而不是改代码。", "易错点")

    h3(doc, "5.4.3 ImagePreview：点图全屏放大（120 行）")
    para(doc, "点轮播配图后弹出一个全屏 Dialog：深色背景 + 图片居中 + 双指缩放（1-4 倍）+ "
              "双击放大/还原 + 右上角关闭。纯 View 实现，没有第三方图片库。")

    h3(doc, "5.4.4 PixelBookView：书本封面（329 行）")
    table(doc, ["绘制步骤", "内容"],
          [["1", "右下投影 + 深色外框"],
           ["2", "封面主体（分类色）"],
           ["3", "书脊（深色竖条 + 高光）"],
           ["4", "右侧米色页边 + 横线"],
           ["5", "顶部深色带 + 底部辅色带（主色混金）"],
           ["6", "米色图标底托 + 像素图标（手柄/金币/耳机/警徽/钱袋/面具）"],
           ["7", "烫金铭牌：深底 + 上下金线 + 金色书名"],
           ["8", "书脊烫金：上下小金块 + 一条竖金线"]],
          widths=[2.6, 13.2], size=9)
    para(doc, "所有绘制都关了抗锯齿（setAntiAlias(false)），字和线条就是硬边像素感。")

    h3(doc, "5.4.5 AvatarView：像素头像（205 行）")
    para(doc, "按索引画 6 种形象：学生、学霸（眼镜）、猫、兔、熊、星。"
              "背景色由调用方给（注册页/聊天页用不同颜色区分人）。")

    h3(doc, "5.4.6 Tips：反诈口诀（127 行）")
    para(doc, "读 assets/data/tips.json 的 8 条口诀（码/链/钱/平/话/急/警/证）。"
              "首页口诀卡显示前 3 条，「点开看全部 8 句解释」会弹窗列出全部。")

    # ---------- 5.5 工坊 ----------
    h2(doc, "5.5 剧本与工坊（4 个类）")

    h3(doc, "5.5.1 StudioActivity：剧本工坊（296 行）")
    para(doc, "管理玩家自己写的剧本：列表、新建、编辑、导出成文件、从文件导入、删除。"
              "数据都存在 filesDir/scripts/，和内置剧本物理隔离。")

    h3(doc, "5.5.2 EditorActivity：剧本级编辑（304 行）")
    para(doc, "编辑一本剧本的“外壳”：标题、骗局类型、书架分类、简介、节点列表（点进去编辑单个节点）。"
              "保存时会做基本校验（标题不能为空、至少一个节点）。")

    h3(doc, "5.5.3 NodeEditActivity：节点编辑（283 行）")
    para(doc, "编辑一个节点：说话人、台词（一行一条）、是否结局、结局文案、最多 3 个选项。"
              "每个选项要填：文字、跳到哪个节点、风险等级、标签、损失金额、点评。")

    h3(doc, "5.5.4 ScriptPickerActivity：翻开一本书后的书页（180 行）")
    para(doc, "点书架上的书之后进入：列出这个分类下的剧本，点一本直接进聊天页。"
              "如果这一分类下只有 1 本，会直接跳过这个页面。")

    # ---------- 5.6 反馈 ----------
    h2(doc, "5.6 复盘、海报、来源（3 个类）")

    h3(doc, "5.6.1 SharePoster：复盘海报（200 行）")
    para(doc, "用 Canvas 画一张 1080×1350 的像素风长图：标题、损失金额、高风险次数、几句口诀、"
              "二维码位置留白。存到 cacheDir/share/poster.png，再通过系统分享面板发出去。")

    h3(doc, "5.6.2 PosterProvider：把图片交给系统（58 行）")
    para(doc, "一个极简 ContentProvider，只暴露 cacheDir/share 下的文件，"
              "这样系统分享面板能读到图片，又不需要申请任何存储权限。")

    h3(doc, "5.6.3 SourcesActivity：来源页（203 行）")
    para(doc, "列出 71 条来源：标题、发布方、日期、支撑哪几步、改编说明。点一条复制原文链接。"
              "底部还有 9 张自绘配图的版权说明（全部本作品原创）。")

    # ---------- 5.7 其它 ----------
    h2(doc, "5.7 其它页面与控件（6 个类）")
    table(doc, ["类", "行数", "作用"],
          [["RegisterActivity", "151", "首次进入：昵称（2-8 字）+ 6 个头像，只存本机"],
           ["SettingsActivity", "131", "字体与字号：只有这两件事"],
           ["LogActivity", "120", "运行日志页（我的页长按「关于」2 秒进入）"],
           ["TipsWidgetProvider", "91", "桌面小组件「今日防骗贴士」"],
           ["GuideOverlay", "414", "★ 聚光灯新手引导：5 步、自动切 Tab、只能点下一步"],
           ["SpotlightView", "53", "引导用的遮罩：整屏压暗，中间挖一个圆角矩形洞"]],
          widths=[4.6, 1.6, 9.6], size=9)

    h3(doc, "5.7.1 GuideOverlay 的三个关键机制")
    table(doc, ["机制", "做法"],
          [["高亮洞", "SpotlightView 用 Xfermode 在暗色遮罩上“挖洞”，洞的位置由目标控件在屏幕上的坐标算出来"],
           ["切 Tab", "每一步开始前调 OnStepPrepare 回调，由 MainActivity 切到对应 Tab 并等布局完成"],
           ["只能点下一步", "整层吞掉所有触摸；旧高亮在点下一步的瞬间收起，切页只等一帧"]],
          widths=[3.6, 12.2], size=9)


def build_ch6(doc):
    h1(doc, "第 6 章  数据长什么样")
    para(doc, "这个项目的“内容”和“代码”是分开的：所有剧情、分类、口诀、配图说明都是 JSON。"
              "改内容不用改代码，这是最容易上手的一块。")

    h2(doc, "6.1 categories.json：6 个分类")
    para(doc, "位置：assets/data/categories.json。书架上的 6 本书就是它。")
    table(doc, ["id", "name", "desc", "glyph", "color"],
          [["game", "游戏账号", "低价买号 · 私下转账", "游", "#C0392B"],
           ["rebate", "刷单返利", "点赞赚钱 · 垫付流水", "单", "#D98C1F"],
           ["service", "冒充客服", "退款理赔 · 屏幕共享", "服", "#3F7FA6"],
           ["police", "冒充公检法", "涉案洗钱 · 安全账户", "法", "#6B4C8A"],
           ["loan", "校园贷兼职", "零门槛借 · 高薪兼职", "贷", "#4E7B3A"],
           ["aiface", "AI换脸诈骗", "视频通话 · 熟人借钱", "脸", "#3C7A89"]],
          widths=[2.2, 3.8, 5.6, 1.8, 2.4], size=9)

    h2(doc, "6.2 一本剧本的完整结构")
    para(doc, "位置：assets/scripts/<剧本 id>.json。下面以 game-account.json 为例（节选真实内容）。")
    code_block(doc, """{
  "id": "game-account",
  "title": "低价全皮账号的诱惑",
  "scamType": "游戏账号交易诈骗",
  "minutes": "10-15",
  "intro": "二手平台上有人 5000 元出全皮账号……他说平台抽成太高，让你私下转账……",
  "startNode": "n1",
  "stageCount": 6,
  "referenceLoss": 50400,
  "referenceNote": "真实案件：四川内江高中生王同学……（人民网四川频道 2025-08-22）",
  "sources": [ { "name": "...", "publisher": "...", "date": "...", "url": "...",
                 "usedIn": "主线 n1-n13 的话术结构……", "note": "人物匿名化……" } ],
  "nodes": {
    "n1": {
      "id": "n1",
      "speaker": "卖家",
      "stage": 1,
      "stageName": "诱饵",
      "messages": [ "这个号 5000，全皮，稀有皮肤一个不少。",
                    "平台要抽 5%，你直接转我，我给你减 200。" ],
      "options": [
        { "text": "行，直接转你，便宜点", "next": "n2", "risk": "high",
          "tag": "脱离官方平台", "cost": 0, "feedback": "离开官方交易渠道是这类诈骗的第一步……" },
        { "text": "先加好友，让他把号发我看看", "next": "n2", "risk": "medium",
          "tag": "先验号", "cost": 0, "feedback": "验号没错，但「加好友私下聊」正是骗子下一步要的……" },
        { "text": "只在平台内交易，不接受私下转账", "next": "s1", "risk": "low",
          "tag": "坚持官方渠道", "cost": 0, "feedback": "正确。正规交易在平台内完成……" }
      ]
    },
    "n2": { "...": "..." }
  }
}""", size=7.2, caption="代码 6-1  game-account.json 的真实结构（省略了部分内容）")

    h2(doc, "6.3 字段速查表")
    table(doc, ["层级", "字段", "必填", "说明"],
          [["剧本", "id", "是", "与文件名一致，剧本列表靠它"],
           ["剧本", "title", "是", "标题"],
           ["剧本", "scamType", "是", "骗局类型"],
           ["剧本", "minutes", "是", "建议时长（列表卡片显示）"],
           ["剧本", "intro", "是", "一句话简介"],
           ["剧本", "startNode", "是", "起始节点 id"],
           ["剧本", "stageCount", "是", "总幕数（进度条分母）"],
           ["剧本", "referenceLoss", "否", "真实案件参考损失"],
           ["剧本", "referenceNote", "否", "真实案件说明"],
           ["剧本", "sources", "是", "来源数组（至少 1 条）"],
           ["剧本", "nodes", "是", "节点对象，key 为节点 id"],
           ["节点", "id / speaker / stage / stageName", "是", "节点 id、说话人、第几幕、幕名"],
           ["节点", "messages", "是", "消息数组（一句一个气泡）"],
           ["节点", "options", "是", "选项数组（恰好 3 个进入下一节点，或 isEnd 结局）"],
           ["节点", "isEnd", "否", "结局节点为 true"],
           ["节点", "endingTitle / endingAdvice", "否", "结局标题 / 建议"],
           ["选项", "text / next / risk / tag / cost / feedback", "是", "六件套，见 5.2.3 节"]],
          widths=[1.8, 5.6, 1.4, 7.0], size=8.5)

    h2(doc, "6.4 一个节点是怎么变成一次点击的")
    numbered(doc, "聊天页加载剧本 → 从 startNode（n1）开始渲染。")
    numbered(doc, "n1 的 messages 逐条出现（间隔 660ms），播完显示 options 的三个按钮。")
    numbered(doc, "玩家点第 3 个选项：next 是 s1，risk 是 low，cost 是 0。")
    numbered(doc, "代码把这一步记进 PathRecorder（含 feedback），然后渲染 s1。")
    numbered(doc, "如果 next 是空字符串（或指向 isEnd 节点），就进结局 → 复盘。")
    note(doc, "所以“剧本”本质是一张有向图：节点是点，选项是边，边的标签里带着“这是好选择还是坏选择”。"
              "支线命名有规律：n = 主线，d = 处置支线，s = 安全线。", "理解要点")

    h2(doc, "6.5 tips.json：8 条口诀")
    table(doc, ["key", "sub", "text（全文见文件）"],
          [["码", "不给", "验证码、密码、人脸核验，谁要都不给"],
           ["链", "不点", "陌生链接和二维码一律不点，软件只从应用商店装"],
           ["钱", "不转", "先垫钱、先交保证金、先刷流水，都是固定开场"],
           ["平", "不离", "买卖账号、退款、代练都留在官方平台内"],
           ["话", "先挂", "让你转账先挂断，用官方号码回拨核实"],
           ["急", "先停", "越催越要停，先问家长或老师"],
           ["警", "必接", "96110 是预警劝阻专线，一定要接"],
           ["证", "要留", "聊天记录、转账凭证、对方账号全部截图"]],
          widths=[1.6, 1.8, 12.4], size=9)

    h2(doc, "6.6 covers.json：9 张配图的说明")
    para(doc, "每张图登记：文件路径、作者（本作品自绘）、来源（留空）、主题。"
              "这个文件同时被两处使用：轮播卡片的配图说明、来源页的配图版权说明。")
    table(doc, ["文件", "主题"],
          [["covers/case0.jpg", "游戏账号诈骗（买号聊天：定金→解冻金→拉黑）"],
           ["covers/case1.jpg", "AI 换脸诈骗（视频通话里的“女儿”）"],
           ["covers/case2.jpg", "AI 换脸 / 熟人视频（视频里的“表哥”借钱）"],
           ["covers/case3.jpg", "刷单返利诈骗（接单群任务与提现失败）"],
           ["covers/case4.jpg", "校园贷 / 租卡兼职（日租卡走账聊天）"],
           ["covers/case5.jpg", "游戏代练诈骗（保证金→解冻金）"],
           ["covers/tip0.jpg", "口诀：码 / 链 / 钱"],
           ["covers/tip1.jpg", "口诀：平 / 话 / 急"],
           ["covers/tip2.jpg", "口诀：警 / 证"]],
          widths=[4.4, 11.4], size=9)

    h2(doc, "6.7 17 本剧本的规模（脚本自动统计）")
    para(doc, "下表由脚本读 assets/scripts 生成，行数就是真实数据。节点数和选项数可以说明内容量。")
    table(doc, ["剧本 id", "标题", "分类", "节点", "选项", "幕", "时长", "来源"],
          _scripts_stat(), widths=[3.0, 4.2, 1.8, 1.2, 1.2, 1.0, 1.4, 1.2], size=8)


def build_ch7(doc):
    h1(doc, "第 7 章  变量与命名")
    para(doc, "读代码时最容易卡住的是“这个变量到底是啥”。这一章把命名习惯和 50 多个常见变量列清楚。")

    h2(doc, "7.1 命名规范")
    table(doc, ["类型", "规范", "例子"],
          [["类", "大驼峰 PascalCase", "ChatActivity、ScriptRepository、PixelBookView"],
           ["方法", "小驼峰 camelCase，动词开头", "renderNode、loadScript、updateLoss"],
           ["常量", "全大写 + 下划线", "MAX_LINES、REQ_IMPORT、AVATAR_COLORS"],
           ["成员变量", "小驼峰，不加 m 前缀", "current、recorder、llMessages"],
           ["局部变量", "小驼峰，短名", "n、i、w、p、sb"],
           ["资源 id", "snake_case", "R.id.tv_loss、R.drawable.px_option"],
           ["XML 属性", "Android 默认规范", "versionCode、minSdk、compileSdk"]],
          widths=[2.4, 6.0, 7.4], size=9)

    h2(doc, "7.2 高频变量表（按出现场景分组）")
    h3(doc, "7.2.1 通用")
    table(doc, ["变量", "含义", "出现在哪"],
          [["ctx / c / context", "上下文（Activity 或 Context）", "engine 所有类"],
           ["a / activity", "当前 Activity（页面里省略写活动名）", "各 TabView"],
           ["host", "TabHost：页面用来和主界面喊话的接口", "三个 TabView"],
           ["v / view", "回调里的那个 View", "OnClickListener"],
           ["p", "padding 的像素值（临时变量）", "各处 setPadding"],
           ["w / h", "宽 / 高（像素）", "自绘 View 的 onDraw"],
           ["u", "像素单位（自绘时用来算比例）", "PixelBookView、AvatarView"],
           ["i / idx / index", "循环下标或位置", "列表构建"],
           ["sb", "StringBuilder", "日志/拼字符串"]],
          widths=[3.4, 7.4, 5.0], size=8.5)

    h3(doc, "7.2.2 剧本与聊天")
    table(doc, ["变量", "含义", "出现在哪"],
          [["script", "当前剧本对象", "ChatActivity、ReviewActivity"],
           ["current", "当前节点", "ChatActivity"],
           ["node / nd", "节点（nd 是 final 副本，给匿名类用）", "ChatActivity.renderNode"],
           ["c", "选项（Choice）", "ChatActivity.addOption"],
           ["next", "选项指向的下一节点", "ChatActivity.onChoice"],
           ["recorder", "路径记录器", "ChatActivity、ReviewActivity"],
           ["steps", "记录下来的每一步", "ReviewActivity"],
           ["loss / totalCost", "累计损失", "ChatActivity、UserStore"],
           ["shownLoss", "界面上当前显示金额（做动画用）", "ChatActivity"],
           ["lastStage", "上一次的幕号", "ChatActivity"],
           ["gap", "两条消息之间的间隔毫秒", "ChatActivity"],
           ["bigFont", "是不是超大字号", "ChatActivity、各页面"]],
          widths=[3.4, 7.4, 5.0], size=8.5)

    h3(doc, "7.2.3 首页与轮播")
    table(doc, ["变量", "含义"],
          [["panel / p", "轮播里的一张卡片（Panel）"],
           ["panels", "9 张卡片的列表"],
           ["MIXED", "卡片顺序常量：案例与口诀混排"],
           ["index / current", "当前显示第几张"],
           ["cover", "配图 ImageView"],
           ["coverBox", "配图容器"],
           ["cat / category", "分类（书架上一本书）"],
           ["shelf", "书架容器"],
           ["coverHeight", "按图片比例算出的高度"]],
          widths=[3.8, 12.0], size=9)

    h3(doc, "7.2.4 引导与设置")
    table(doc, ["变量", "含义"],
          [["guide", "GuideOverlay 对象"],
           ["guideShowing", "引导是否正在显示"],
           ["steps", "引导的步骤列表（Step）"],
           ["target", "当前要高亮的控件"],
           ["hole", "高亮洞的位置矩形"],
           ["skipBar", "跳过引导按钮"],
           ["bottomInsetDp", "底部导航条高度，避免按钮压住它"],
           ["fontScale", "字号倍数（1.0/1.15/1.28/1.55）"],
           ["fontStyle", "字体编号（0 像素 / 1 系统）"],
           ["changed", "设置改过的标记（静态）"]],
          widths=[4.0, 11.8], size=9)

    h2(doc, "7.3 最容易混淆的几组")
    table(doc, ["看起来相似", "区别"],
          [["Script / ScriptStore", "Script 是一本剧本的数据；ScriptStore 是“玩家自写剧本”的存储工具"],
           ["ScriptRepository / ScriptStore", "前者读内置（assets，只读），后者管玩家自写（filesDir，可写）"],
           ["Category / tag", "Category 是 6 大类骗局；tag 是某个选项的短标签（如“脱离官方平台”）"],
           ["node.id / node.stage", "id 是节点的唯一名字（n1）；stage 是它属于第几幕（数字）"],
           ["risk / cost", "risk 是“这一步有多危险”，cost 是“这一步亏多少钱”，两者不一定一致"],
           ["Panel / Card", "Panel 是轮播数据对象；Card 是屏幕上那张卡片的 View"]],
          widths=[4.6, 11.2], size=9)

    h2(doc, "7.4 缩写表")
    table(doc, ["缩写", "全称", "中文"],
          [["dp", "density-independent pixel", "设备无关像素（1dp ≈ 不同屏幕的同一物理尺寸）"],
           ["sp", "scale-independent pixel", "字体像素（跟随系统字号）"],
           ["ll", "LinearLayout", "线性布局（llMessages 就是消息容器）"],
           ["tv / et / pb / sv", "TextView / EditText / ProgressBar / ScrollView", "各种控件"],
           ["btn", "Button", "按钮"],
           ["ctx", "context", "上下文"],
           ["req / res", "request / result", "请求码 / 结果码"],
           ["cb", "callback", "回调"],
           ["nvl", "null-value", "空值兜底（ull 判空的方法）"],
           ["px", "pixel", "像素"]],
          widths=[3.4, 6.6, 5.8], size=9)
