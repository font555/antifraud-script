"""画齐轮播的 9 张配图：6 张案例（仿截图 + 像素）+ 3 张口诀（信息图）。

三个设计约束（改尺寸前先读一遍）：
    1. 画幅比例是算出来的，不是随便定的：案例卡 900×360（2.5:1）、口诀卡 900×300（3:1），
       这样首页轮播的整体高度能压在 260dp 左右，一屏还能看到下面的书架；
    2. 所有坐标都是像素绝对值，没有响应式。换画幅就要重新对一遍坐标；
    3. 颜色统一从下面那组常量取，别在函数里写死 RGB，否则改配色要满文件找。

为什么自己画：
    素材站的照片对不上一个具体的中文反诈案例，而新闻原图又有版权。
    全部用矩形拼出来，画面能与案例一一对应，版权 100% 干净（本作品原创）。

用法：
    py tools/draw_covers9.py
        直接覆盖 app/src/main/assets/covers/ 下的 case0-5.jpg 和 tip0-2.jpg。

依赖：Pillow（pip install pillow）+ 仓库自带的像素字体 fusion_pixel.ttf。
"""

import os  # 标准库：拼路径、建目录

from PIL import Image, ImageDraw, ImageFont  # Pillow：绘图三件套（画布 / 画笔 / 字体）

# ---------------------------------------------------------------- 路径与画幅
# ROOT : 项目根目录（本文件在 tools/ 下，往上退两级）
# FONT : 应用内同款像素字体，保证配图和 App 界面风格一致
# DST  : 输出目录，直接覆盖 App 的 assets，改完重新打包即可生效
# W    : 所有配图统一 900 像素宽，和卡片宽度匹配
# CASE_H / TIP_H : 案例卡与口诀卡两种高度
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # 项目根目录（本文件在 tools/ 下，往上退两级）
FONT = os.path.join(ROOT, "app", "src", "main", "assets", "fonts", "fusion_pixel.ttf")  # 像素字体文件（App 里也在用同一份）
DST = os.path.join(ROOT, "app", "src", "main", "assets", "covers")  # 输出到 App 的 covers 目录

W = 900  # 统一画幅宽度
CASE_H = 360  # 案例卡高度（900×360，2.5:1）
TIP_H = 300  # 口诀卡高度（900×300，3:1）

# ---------------------------------------------------------------- 配色
# 命名和 App 里的 Ui 类保持一致，方便对照：
#   PAPER     米黄纸底      INK       近黑墨色（描边/状态栏）
#   BRAND     品牌砖红      BRAND_DARK 深砖红（强调文字）
#   GOLD/TEAL/GREEN  分类色  GRAY/GRAY_LIGHT  次要文字与浅底
#   WHITE     白            DARK_BG    视频通话页的深色底
PAPER = (246, 239, 224)
INK = (43, 43, 51)
BRAND = (228, 87, 46)
BRAND_DARK = (191, 61, 28)
GOLD = (233, 180, 76)
TEAL = (63, 127, 166)
GREEN = (78, 123, 58)
GRAY = (150, 145, 135)
GRAY_LIGHT = (236, 232, 222)
WHITE = (255, 255, 255)
DARK_BG = (36, 34, 40)


def f(size):
    """按字号取像素字体。

    参数:
        size: 字号（像素）。

    返回:
        ImageFont.FreeTypeFont —— Pillow 的字体对象，可以直接传给 draw.text。

    说明:
        每次调用都重新 load 一次字体文件，Pillow 内部有缓存，开销可以忽略；
        好处是调用点不用自己管字体对象的生命周期。
    """
    return ImageFont.truetype(FONT, size)  # 每次按字号加载一次字体，Pillow 内部有缓存


def px(d, x, y, w, h, c):
    """画一个实心矩形（像素风的基本笔刷）。

    参数:
        d: ImageDraw 画布
        x, y: 左上角坐标
        w, h: 宽高（像素）
        c: RGB 颜色元组

    说明:
        右下角要减 1，是因为 Pillow 的矩形是「闭区间」——
        给 (x, y, x+w, y+h) 会画出 w+1 个像素宽，差一像素在像素风里非常明显。
    """
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=c)


def tx(d, x, y, s, size, c=INK):
    """在指定位置写一行文字（左上角对齐）。

    参数:
        d: ImageDraw 画布
        x, y: 文字左上角坐标
        s: 文字内容
        size: 字号
        c: 文字颜色，默认墨色
    """
    d.text((x, y), s, font=f(size), fill=c)


def center(d, cx, y, s, size, c=INK):
    """把一行文字水平居中到 cx 这条竖线上。

    参数:
        d: ImageDraw 画布
        cx: 目标中心 x 坐标
        y: 文字左上角 y 坐标
        s: 文字内容
        size: 字号
        c: 文字颜色

    说明:
        先用 textlength 量出像素宽度，再往左挪一半——不能按字符数算，
        中英文混排时字符数完全不能反映实际宽度。
    """
    tw = d.textlength(s, font=f(size))
    d.text((cx - tw / 2, y), s, font=f(size), fill=c)


def status(d):
    """画手机状态栏（顶部深色条 + 时间 + 信号电量）。

    参数:
        d: ImageDraw 画布

    说明:
        时间固定写 20:41——这是演示机截图时的时间，九张图统一，看起来像同一台手机。
    """
    px(d, 0, 0, W, 24, INK)
    tx(d, 14, 3, "20:41", 13, WHITE)
    tx(d, W - 122, 3, "5G   96%", 13, WHITE)


def avatar(d, x, y, size, color, ch):
    """画像素风头像（其实是方块头 + 一个首字）。

    参数:
        d: 画布
        x, y: 头像左上角
        size: 头像边长
        color: 底色（不同说话人用不同颜色，和 App 里一致）
        ch: 头像里的那个字（一般取名字首字）
    """
    px(d, x, y, size, size, INK)
    px(d, x + 2, y + 2, size - 4, size - 4, color)
    tw = d.textlength(ch, font=f(int(size * 0.55)))
    d.text((x + (size - tw) / 2, y + size * 0.16), ch,
           font=f(int(size * 0.55)), fill=WHITE)


# ---------------- 聊天界面（案例 0/2/4/5） ----------------

def chat_screen(title, subtitle, header_avatar, header_color, lines,
                product=None, bottom_hint="发送消息…"):
    """画一个仿真的聊天界面截图（案例卡用）。

    参数:
        title / subtitle: 顶部标题与副标题（如「卖家 · 游戏账号专营」/「在线 · 已实名」）
        header_avatar / header_color: 顶栏头像里的字与底色
        lines: 消息列表，每项是 (方向, 文本, 样式)
               方向 "L" = 对方发来的（左）、"R" = 我发出的（右）、"SYS" = 系统提示（居中）
               样式 "warn" = 用警示色气泡（红色系），"normal" = 常规白气泡
        product: 可选，商品卡 (名称, 价格, 备注)；None 表示不画商品条
        bottom_hint: 底部输入框里的灰色提示文字（如「对方已把你拉黑」）

    返回:
        PIL.Image —— 画好的 900×360 图片，由调用方决定存到哪里。

    排版思路:
        从上往下依次是：状态栏 24px → 顶栏 50px → 可选商品条 52px →
        「今天 20:41」时间标 → 气泡区（自动算行距）→ 底部输入栏 38px。
        气泡区的行距 gap 会根据剩余高度和消息条数动态算，
        所以改消息条数时不需要手动调间距，图也不会溢出。
    """
    img = Image.new("RGB", (W, CASE_H), PAPER)  # 案例卡统一米黄纸底
    d = ImageDraw.Draw(img)
    status(d)

    # 顶栏：深色底 + 返回箭头 + 头像 + 两行标题（主标题 18 号、副标题 12 号灰字）
    # 顶栏 24 ~ 74
    px(d, 0, 24, W, 50, INK)  # 顶栏 24 ~ 74
    tx(d, 14, 36, "←", 22, WHITE)
    avatar(d, 50, 30, 36, header_color, header_avatar)
    tx(d, 98, 30, title, 18, WHITE)
    tx(d, 98, 54, subtitle, 12, (190, 186, 176))

    y = 80
    if product:
    # 商品卡：左侧灰底缩略图 + 右侧名称/价格/备注。
    # note_x 是按价格文字实际宽度算出来的，价格字数变了备注也不会重叠。
        name, price, note = product
        px(d, 16, y, W - 32, 44, WHITE)
        px(d, 22, y + 4, 36, 36, GRAY_LIGHT)
        tx(d, 27, y + 12, "商品", 12, GRAY)
        tx(d, 70, y + 2, name, 17, INK)
        tx(d, 70, y + 22, price, 17, BRAND_DARK)
        note_x = 70 + int(d.textlength(price, font=f(17))) + 14
        tx(d, note_x, y + 24, note, 12, GRAY)
        y += 52

    center(d, W // 2, y, "今天 20:41", 12, GRAY)  # 时间分隔条
    # 时间分隔条：居中显示「今天 20:41」，和真实聊天软件一致
    y += 18

    avail = (CASE_H - 40) - y - 4  # 气泡区可用高度：扣掉底部输入栏 40 像素和 4 像素留白
    # 关键：气泡行距动态计算。
    # avail 是气泡区可用的总高度；用它除以消息条数得到每条的平均高度，
    # 再夹在 [34, 44] 之间——太少会挤成一团，太多会顶到底部输入栏外面。
    # 这样无论画 3 条还是 6 条消息，版面都是匀称的。
    gap = max(34, min(44, avail // max(len(lines), 1)))
    size = 16
    for side, text, style in lines:  # 逐条画消息：先量文字宽度决定气泡宽度（+24 是左右内边距）
    # 三种气泡分别处理：
    #   SYS —— 居中灰条，用来表现「对方已将你删除」这类系统提示，最扎心的一击放在最后
    #   L   —— 左侧白底气泡，warn 样式换成浅红底，视觉上提示「这句是坑」
    #   R   —— 右侧红底白字气泡，代表「我」已经把钱转出去了
    # 所有气泡都是双层矩形：外层深色当描边，内层填充色，这是像素风的统一做法。
        tw = int(d.textlength(text, font=f(size))) + 24
        bh = gap - 12
        if side == "SYS":
            wp = int(d.textlength(text, font=f(12))) + 20
            px(d, W // 2 - wp // 2, y, wp, 22, GRAY_LIGHT)
            center(d, W // 2, y + 3, text, 12, GRAY)
            y += gap  # 画完一条往下挪一个行距
            continue
        if side == "L":
            fill = (252, 232, 222) if style == "warn" else WHITE
            px(d, 16, y, tw, bh, INK)
            px(d, 18, y + 2, tw - 4, bh - 4, fill)
            tx(d, 27, y + (bh - size) // 2 - 2, text, size)
        else:
            x = W - 16 - tw
            px(d, x, y, tw, bh, INK)
            px(d, x + 2, y + 2, tw - 4, bh - 4, BRAND)
            center(d, x + tw / 2, y + (bh - size) // 2 - 2, text, size, WHITE)
        y += gap

    px(d, 0, CASE_H - 38, W, 38, (240, 236, 228))
    # 底部输入栏：浅灰底 + 白色输入框 + 加号/语音两个小按钮
    px(d, 16, CASE_H - 30, W - 120, 22, WHITE)
    tx(d, 28, CASE_H - 26, bottom_hint, 13, GRAY)
    px(d, W - 96, CASE_H - 32, 22, 22, WHITE)
    tx(d, W - 90, CASE_H - 30, "＋", 16, INK)
    px(d, W - 64, CASE_H - 32, 22, 22, WHITE)
    tx(d, W - 58, CASE_H - 30, "◦", 16, INK)
    return img


def call_screen(name, line, buttons=("静音", "挂断", "免提")):
    """画一个视频通话界面（AI 换脸 / 冒充熟人这两张案例卡用）。

    参数:
        name: 来电人显示名（如「女儿」「表哥」）
        line: 字幕台词（骗子说的那句要钱的话）
        buttons: 底部三个按钮的文字，默认「静音 / 挂断 / 免提」

    返回:
        PIL.Image —— 900×360 的深色底图片。

    画面构成:
        状态栏 → 视频区（浅绿灰底 + 像素人脸 + 通话计时 + 来电人标签 + 字幕）→ 底部按钮条。
        人脸是用几十个矩形拼出来的像素人像，故意画得「像但不太像」——
        这正是 AI 换脸的特征，配图本身就在传达信息。
    """
    img = Image.new("RGB", (W, CASE_H), DARK_BG)  # 视频通话用深色底，和聊天页形成对比
    d = ImageDraw.Draw(img)
    status(d)
    # 视频区 24 ~ 286
    # 视频区：先铺一层偏绿的背景（模拟室内光线），再用矩形拼出人物。
    # 下巴、嘴、肩膀的坐标都是按 900 宽的画面居中对齐算的，改画幅要重算。
    px(d, 0, 24, W, 262, (150, 168, 158))  # 视频区 24 ~ 286
    cx = W // 2
    px(d, cx - 66, 54, 132, 30, (72, 54, 46))
    px(d, cx - 76, 74, 152, 28, (72, 54, 46))
    px(d, cx - 56, 68, 112, 128, (240, 210, 172))
    px(d, cx - 32, 98, 14, 14, INK)
    px(d, cx + 18, 98, 14, 14, INK)
    px(d, cx - 5, 122, 10, 8, (214, 170, 150))
    px(d, cx - 20, 140, 40, 10, BRAND_DARK)
    px(d, cx - 90, 196, 180, 50, (86, 96, 120))

    px(d, 14, 32, 210, 26, (24, 22, 28))
    # 顶部两块半透明深色标签：左边是通话时长，右边是来电人名字——
    # 名字对得上、画面也对得上，才是 AI 换脸最可怕的地方。
    tx(d, 24, 36, "视频通话  00:07", 14, WHITE)
    px(d, W - 110, 32, 86, 26, (24, 22, 28))
    tx(d, W - 100, 36, name, 14, WHITE)

    px(d, 36, 222, W - 72, 36, (20, 18, 24))
    # 字幕条：黑底白字压在画面下方，模仿视频通话的实时字幕
    tx(d, 52, 230, line, 16, WHITE)

    # 按钮 286 ~ 360
    px(d, 0, 286, W, 74, (26, 24, 30))
    # 底部按钮：挂断固定用红色，其余用深灰，和真实通话界面一致
    for label, x in zip(buttons, (150, 450, 750)):  # 三个按钮等距排开，挂断单独用红色
        c = (196, 58, 58) if label == "挂断" else (58, 56, 64)
        px(d, x - 44, 302, 88, 42, c)
        center(d, x, 313, label, 15, WHITE)
    return img


def case0():
    """案例卡 0：游戏账号交易——定金 → 解冻金 → 拉黑。"""
    return chat_screen(
        "卖家 · 游戏账号专营", "在线 · 已实名", "卖", BRAND,
        [("L", "账号绝对安全，先付 200 定金就发货", "normal"),
         ("R", "已转 200", "normal"),
         ("L", "账号异常，交 8000 解冻金，原路退", "normal"),
         ("R", "又转了 8000", "normal"),
         ("SYS", "对方已将你删除", "normal")],
        product=("全皮账号 · 满级", "¥888", "原价 3000"))


def case1():
    """案例卡 1：AI 换脸——视频里的「女儿」要培训费。"""
    return call_screen("女儿", "妈，培训班要交 3.5 万，转给“王老师”")


def case2():
    """案例卡 2：冒充熟人——「表哥」借钱，还让你别告诉家里。"""
    return call_screen("表哥", "借我 3 万急用，别告诉家里，晚点还你")


def case3():
    """案例卡 3：刷单返利——任务群 + 余额永远差 3 单。

    画面:
        顶栏是「兼职接单群 · 日结」，下面是三条任务卡（点赞 / 垫付 / 连刷），
        左边缘的彩色竖条表示进度；最下面是余额区，写着「可提现 ¥0.00」。

    教学点:
        刷单骗局的核心不是「不给钱」，而是「永远差一点点」——
        让你觉得再刷一单就能提现，于是越陷越深。这句直接印在余额下面。
    """
    img = Image.new("RGB", (W, CASE_H), PAPER)  # 刷单卡用聊天界面做底，但内容是任务列表而不是气泡
    d = ImageDraw.Draw(img)
    status(d)
    px(d, 0, 24, W, 50, INK)
    tx(d, 14, 36, "←", 22, WHITE)
    avatar(d, 50, 30, 36, GOLD, "单")
    tx(d, 98, 30, "兼职接单群 · 日结", 18, WHITE)
    tx(d, 98, 54, "群成员 386 人", 12, (190, 186, 176))

    tasks = [("点赞 3 条短视频", "+5 元", "已完成", GREEN),
             ("垫付 300 元拍下商品", "返 330 元", "进行中", BRAND),
             ("连刷 3 单才能提现", "当前 ¥0", "待完成", GRAY)]
    y = 80
    # 任务列表：三条任务刚好演示「小钱给得起、大钱拿不回」的套路。
    # 每条左边一根彩色竖条：绿=已完成、红=进行中、灰=待完成，颜色本身就是进度提示。
    for name, money, state, color in tasks:  # 每条任务一张白卡：左边 6 像素彩色竖条标状态
        px(d, 16, y, W - 32, 52, WHITE)
        px(d, 16, y, 6, 52, color)
        tx(d, 30, y + 6, name, 17, INK)
        tx(d, 30, y + 28, money, 15, BRAND_DARK)
        w = int(d.textlength(state, font=f(12))) + 16  # 状态标签宽度按文字实际宽度算，中英文都能贴边
        px(d, W - 22 - w, y + 14, w, 22, color)
        tx(d, W - 16 - w, y + 18, state, 12, WHITE)
        y += 62

    px(d, 16, y + 2, W - 32, 48, (250, 232, 214))
    # 余额区：故意写「可提现 ¥0.00」+「永远差 3 单」，把刷单的死循环一句话说透
    tx(d, 30, y + 6, "可提现余额  ¥0.00", 17, BRAND_DARK)
    tx(d, 30, y + 30, "提示：再完成 3 单即可提现（永远差 3 单）", 13, (120, 96, 80))

    px(d, 0, CASE_H - 38, W, 38, (240, 236, 228))
    px(d, 16, CASE_H - 30, W - 120, 22, WHITE)
    tx(d, 28, CASE_H - 26, "发送消息…", 13, GRAY)
    return img


def case4():
    """案例卡 4：出租银行卡——日结 100，卡被冻结反被讹。"""
    return chat_screen(
        "收卡 · 日结 100", "在线", "卡", TEAL,
        [("L", "日租 100 一张，卡和验证码给我走账", "normal"),
         ("R", "卡号发你了", "normal"),
         ("L", "卡被冻结了，你得赔 2000，不然报警", "warn"),
         ("R", "我没钱…", "normal"),
         ("SYS", "对方已将你移除群聊", "normal")],
        product=("出租银行卡 · 日结", "100 元/张", "当天结算"))


def case5():
    """案例卡 5：游戏代练——先交保证金，再交解冻费，最后被拉黑。"""
    return chat_screen(
        "代练工作室 · 7 年老店", "已实名 · 押金可退", "代", GOLD,
        [("L", "代练到王者 500，先交 300 保证金", "normal"),
         ("R", "转了 300", "normal"),
         ("L", "账号异常，要交 800 解冻，不然封号", "warn"),
         ("R", "又转了 800", "normal"),
         ("SYS", "对方已将你拉黑", "normal")],
        product=("王者代练 · 包上分", "¥500", "保证金 300"))


# ---------------- 口诀卡 ----------------

def tip_screen(title, items):
    """画一张口诀卡（信息图，用于轮播最后三张）。

    参数:
        title: 卡片标题（如「先记住这 3 个字」）
        items: 口诀条目列表，每项是 (大字, 短句, 解释)
               大字是单字（码/链/钱），短句是四字口诀，解释是一句话说明。

    返回:
        PIL.Image —— 900×300 的图片。

    版式:
        顶部一条品牌色横线 → 标题 + 短下划线 → 每条口诀一行：
        左侧彩色方块放单字，右侧两行文字（短句 + 解释）。两种颜色交替，避免大色块单调。
    """
    img = Image.new("RGB", (W, TIP_H), PAPER)  # 口诀卡也是米黄底，和案例卡同一套视觉语言
    d = ImageDraw.Draw(img)
    px(d, 0, 0, W, 6, BRAND)  # 顶部品牌色细线，给整张卡一个视觉起点
    tx(d, 30, 12, title, 24, INK)
    px(d, 30, 44, 130, 4, BRAND)  # 标题下方的短横线（130 宽），只在标题下画一点，不抢戏

    y = 58
    # 每条口诀占 62 像素高：色块 54 高 + 8 像素间隔，
    # 三条约 186 像素，加上标题区刚好铺满 300 高，不会留白也不会溢出。
    for i, (key, sub, desc) in enumerate(items):
        px(d, 30, y, 130, 54, BRAND if i % 2 == 0 else BRAND_DARK)  # 色块颜色左右交替，避免连续两个同色块
        center(d, 30 + 65, y + 8, key, 26, WHITE)
        tx(d, 176, y + 2, sub, 19, INK)
        tx(d, 176, y + 26, desc, 14, (110, 104, 94))
        y += 62
    return img


def tip0():
    """口诀卡 0：码 / 链 / 钱 —— 最基础的三不原则。"""
    return tip_screen("先记住这 3 个字", [
        ("码", "码不给", "验证码、密码、人脸核验，谁要都不给"),
        ("链", "链不点", "陌生链接、二维码、安装包，一律不点"),
        ("钱", "钱不转", "先垫钱、交保证金、刷流水，都是开场"),
    ])


def tip1():
    """口诀卡 1：平 / 话 / 急 —— 交易、来电、催促三种场景。"""
    return tip_screen("再记住这 3 个字", [
        ("平", "平不离", "客服不会让你离开官方平台办退款"),
        ("话", "话先挂", "可疑来电先挂断，用官方 App 回拨"),
        ("急", "急先停", "越催你越要停，骗子最怕你冷静"),
    ])


def tip2():
    """口诀卡 2：警 / 证 —— 被骗之后该做什么。"""
    return tip_screen("最后记住这 2 个字", [
        ("警", "警必接", "96110 是反诈专线，一定要接"),
        ("证", "证要留", "聊天、转账、对方账号，全部截图留证"),
    ])


def main():
    """脚本入口：依次画 9 张图并保存为 JPG。

    参数:
        无。

    返回:
        无。每保存一张就在命令行打印文件名和体积，方便确认没有空图。

    说明:
        pairs 里是 (文件名, 画图函数) 的配对；文件名决定输出路径，
        改文件名就等于改 App 里引用的资源名，要同步改 Java 代码。
    """
    os.makedirs(DST, exist_ok=True)  # 目录不存在就建（第一次运行时 covers 可能还不存在）
    pairs = [("case0", case0), ("case1", case1), ("case2", case2),
             ("case3", case3), ("case4", case4), ("case5", case5),
             ("tip0", tip0), ("tip1", tip1), ("tip2", tip2)]
    for name, fn in pairs:
    # 逐张画、逐张存。quality=88 是压图质量和体积的折中：
    # 再高体积翻倍但看不出差别，再低会出现色块边缘的 JPEG 噪点（像素风最怕这个）。
    # progressive=True 让图片从上到下渐进加载，轮播切换时不会先白一下。
        img = fn()  # 每条都重新画一张，互不影响
        out = os.path.join(DST, name + ".jpg")  # 输出成 JPG：轮播图不需要透明通道，JPG 体积比 PNG 小很多
        img.save(out, "JPEG", quality=88, optimize=True, progressive=True)
        print("%-8s %d KB" % (name + ".jpg", os.path.getsize(out) // 1024))  # 打印文件名和体积，体积异常小说明图没画出来


if __name__ == "__main__":
    main()
