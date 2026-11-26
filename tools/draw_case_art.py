"""Draw our own case illustrations (pixel-flat style, matches the app).

为什么自己画（中文说明）：
    素材站的照片永远对不上一个具体的中文反诈案例，而新闻原图又有版权。
    自己用矩形拼出来，画面能和案例一一对应，而且版权 100% 干净（本作品原创）。

输出：
    logs/art/case0.png、logs/art/case1.png
    先出图给队友看，确认没问题再拷进 assets，不直接覆盖线上资源。

用法：
    py tools/draw_case_art.py

依赖：Pillow + 仓库自带的像素字体。
"""

import os  # 标准库：拼路径、建目录

from PIL import Image, ImageDraw, ImageFont  # Pillow 三件套

# ---------------------------------------------------------------- 路径
# ROOT : 项目根目录（本文件在 tools/ 下，往上退两级）
# FONT : 像素字体，与 App 界面同款
# OUT  : 预览输出目录（logs/art 不进 APK，只是给人看的中间产物）
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # 项目根目录（本文件在 tools/ 下，往上退两级）
FONT = os.path.join(ROOT, "app", "src", "main", "assets", "fonts", "fusion_pixel.ttf")  # 像素字体（与 App 同款）
OUT = os.path.join(ROOT, "logs", "art")  # 预览输出目录

W, H = 900, 506  # 画布尺寸：与案例卡同比例 16:9
PAPER = (255, 246, 224)  # 纸底米黄
INK = (43, 43, 51)  # 墨色（描边/标题）
# ---------------------------------------------------------------- 配色
# 和 App 的 Ui 类保持同一套色值：纸底米黄、墨色描边、砖红品牌色。
# LIGHT 是比 PAPER 更浅一点的米色，用来做手机屏幕底色，形成层次。
BRAND = (228, 87, 46)  # 品牌砖红
BRAND_DARK = (191, 61, 28)  # 深砖红（强调文字）
GOLD = (233, 180, 76)  # 金色（账号/金币）
TEAL = (63, 127, 166)  # 青色（分类色）
GREEN = (78, 123, 58)  # 绿色（正向提示）
GRAY = (150, 145, 135)  # 灰色（次要文字）
LIGHT = (240, 228, 200)  # 浅米色（手机屏幕底）
WHITE = (255, 255, 255)  # 白色（气泡）


def font(size):
    """按字号加载像素字体。

    参数:
        size: 字号（像素）。

    返回:
        ImageFont.FreeTypeFont —— 可直接用于 draw.text。
    """
    return ImageFont.truetype(FONT, size)


def px(d, x, y, w, h, color):
    """画实心矩形（像素风基本笔刷）。

    参数:
        d: ImageDraw 画布
        x, y: 左上角坐标
        w, h: 宽高
        color: RGB 颜色

    说明:
        右下角减 1，因为 Pillow 的矩形坐标是闭区间，不减会多出一个像素。
    """
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=color)


def text(d, x, y, s, size, color=INK):
    """写一行左上角对齐的文字。

    参数:
        d: 画布
        x, y: 左上角坐标
        s: 文字
        size: 字号
        color: 颜色，默认墨色
    """
    d.text((x, y), s, font=font(size), fill=color)


def phone_frame(d, x, y, w, h):
    """画一部像素手机外框，并返回「屏幕内区」的可用矩形。

    参数:
        d: 画布
        x, y: 手机左上角
        w, h: 手机整体宽高

    返回:
        tuple —— (屏幕左上角 x, 屏幕左上角 y, 屏幕可用宽, 屏幕可用高)，
                 调用方拿它当「屏幕坐标系」继续画内容，不用重复算偏移。

    结构:
        深色机身 → 内层米色屏幕 → 顶部听筒条 → 底部 Home 条。
        返回值已经把机身厚度（12/26/24/56）扣掉了。
    """
    px(d, x, y, w, h, INK)  # 机身：深色描边
    px(d, x + 4, y + 4, w - 8, h - 8, LIGHT)  # 屏幕：向内缩 4 像素
    px(d, x + w // 2 - 30, y + 8, 60, 8, INK)          # speaker
    px(d, x + w // 2 - 40, y + h - 22, 80, 10, INK)    # home bar
    return (x + 12, y + 26, w - 24, h - 56)  # 返回屏幕内容区（已扣掉机身厚度）


def bubble(d, x, y, w, h, fill, text_s, size=20, color=INK):
    """画一个带描边的聊天气泡。

    参数:
        d: 画布
        x, y: 气泡左上角
        w, h: 气泡宽高
        fill: 气泡填充色（白=对方，砖红=我）
        text_s: 气泡里的文字
        size: 字号
        color: 文字颜色

    说明:
        先画深色矩形当描边，再往里缩 2 像素画填充色，形成像素风的一像素边框。
    """
    px(d, x, y, w, h, INK)  # 外层深色当描边
    px(d, x + 2, y + 2, w - 4, h - 4, fill)  # 内层向内缩 2 像素当填充
    text(d, x + 12, y + (h - size) // 2 - 3, text_s, size, color)


def case0():
    """画案例卡 0：买游戏账号被骗 5 万（低价账号 → 定金 → 尾款 → 刷流水）。

    画面分左右两半:
        左半屏是一部手机里的聊天记录，把「一步步加钱」按时间顺序演一遍；
        右半屏把聊天里散落的信息归纳成「三步加钱」编号列表，是整张图的教学部分。

    数据来源:
        docs/sources.md 里的人民网四川频道 2025-08-22 报道；金额与报道一致，
        人物已匿名、平台名已虚构，不出现真实账号与网址。
    """
    img = Image.new("RGB", (W, H), PAPER)
    d = ImageDraw.Draw(img)
    text(d, 40, 30, "「低价全皮账号」是怎么一步步加钱的", 30, INK)
    px(d, 40, 72, 240, 4, BRAND)

    sx, sy, sw, sh = phone_frame(d, 40, 96, 300, 396)
    # 左半屏：一部手机 + 聊天记录，把「加钱四步」按时间顺序演一遍。
    # 消息气泡左右交替（对方白、我红），最后一条故意换成警示色——
    # 那是骗子准备收网的信号，配色上先给观众一个提示。
    px(d, sx, sy, sw, 34, INK)
    text(d, sx + 10, sy + 4, "游戏账号交易", 20, WHITE)
    # 商品卡
    px(d, sx, sy + 40, sw, 92, WHITE)
    px(d, sx, sy + 40, sw, 4, BRAND)
    text(d, sx + 12, sy + 50, "全皮账号 · 满级", 22, INK)
    text(d, sx + 12, sy + 82, "原价 3000  现价 ¥888", 20, BRAND_DARK)
    # 聊天
    bubble(d, sx, sy + 142, 170, 34, LIGHT, "付 200 定金就发货", 18)
    bubble(d, sx + 120, sy + 182, 150, 34, BRAND, "定金已转", 18, WHITE)
    bubble(d, sx, sy + 224, 220, 34, LIGHT, "再刷 300 流水才发", 18)
    bubble(d, sx + 120, sy + 264, 150, 34, BRAND, "又转了", 18, WHITE)
    bubble(d, sx, sy + 306, 240, 34, (250, 220, 210), "最后 3000 解冻就退你", 17)
    # 输入栏
    px(d, sx, sy + 350, sw, 40, WHITE)
    text(d, sx + 10, sy + 357, "对方已把你拉黑", 18, GRAY)

    # 右侧：三步加钱
    # 右半屏：把聊天里散落的信息归纳成「三步加钱」的可视化编号列表，
    # 这是整张图的教学部分：就算没看懂聊天，也能记住这三步。
    x = 386
    text(d, x, 100, "骗子的固定开场", 26, BRAND_DARK)
    box = [("1", "定金", "先交 200，说马上发货"),
           ("2", "尾款", "账号“被冻结”，再补 8000"),
           ("3", "刷流水", "差多少补多少，直到你没钱")]
    yy = 150
    for num, title, desc in box:
        px(d, x, yy, 46, 46, BRAND)
        text(d, x + 12, yy + 8, num, 26, WHITE)
        text(d, x + 58, yy + 2, title, 24, INK)
        text(d, x + 58, yy + 30, desc, 18, (90, 84, 74))
        yy += 78
    # 底部警示
    # 底部一条浅红警示条：给出「正确做法」，图不能只讲骗术不给出路
    px(d, x, 400, 476, 76, (250, 232, 214))
    text(d, x + 14, 412, "记住：正规平台不会让你私下转账，", 20, BRAND_DARK)
    text(d, x + 14, 442, "更不会让你“刷流水、解冻、交保证金”。", 20, BRAND_DARK)

    os.makedirs(OUT, exist_ok=True)  # 目录不存在就建（第一次跑时 logs/art 可能还没有）
    img.save(os.path.join(OUT, "case0.png"))  # 存 PNG：中间产物不需要压缩，方便后期再改


def case1():
    """画案例卡 1：AI 换脸——视频里的「女儿」，其实是要钱的骗子。

    画面分左右两半:
        左侧是视频通话界面（像素人脸 + 「AI 换脸」角标 + 字幕「妈，培训班要交 3.5 万」）；
        右侧解释这类骗局能成的三个条件：声音像、脸也像、时间紧。

    底线:
        「AI 换脸」角标必须保留，避免这张图被误当成真实视频截图传播。
    """
    img = Image.new("RGB", (W, H), PAPER)
    d = ImageDraw.Draw(img)
    text(d, 40, 30, "视频里的“女儿”，不一定是女儿", 30, INK)
    px(d, 40, 72, 240, 4, BRAND)

    # 左侧手机视频通话
    # 左侧手机：视频通话界面。人脸用矩形拼（头发/脸/眼/嘴），
    # 并在左上角打一个「AI 换脸」角标——不让观众误以为这是真实截图。
    sx, sy, sw, sh = phone_frame(d, 40, 96, 300, 396)
    px(d, sx, sy, sw, 40, INK)
    text(d, sx + 10, sy + 6, "视频通话  00:07", 18, WHITE)
    # 脸
    px(d, sx, sy + 44, sw, 250, (198, 214, 206))
    fx = sx + sw // 2 - 62
    px(d, fx, sy + 70, 124, 150, (238, 214, 178))        # 脸
    px(d, fx + 26, sy + 110, 18, 18, INK)                # 眼
    px(d, fx + 80, sy + 110, 18, 18, INK)
    px(d, fx + 40, sy + 168, 44, 10, BRAND_DARK)         # 嘴
    px(d, fx - 20, sy + 60, 164, 22, (60, 52, 48))       # 头发
    # AI 换脸角标
    px(d, sx + 10, sy + 54, 110, 30, BRAND)
    text(d, sx + 18, sy + 58, "AI 换脸", 20, WHITE)
    # 字幕
    px(d, sx, sy + 300, sw, 44, (30, 28, 34))
    text(d, sx + 12, sy + 308, "妈，培训班要交 3.5 万", 19, WHITE)
    # 按钮
    px(d, sx, sy + 352, sw, 40, WHITE)
    text(d, sx + 10, sy + 358, "对方：转账到“老师”账户", 17, BRAND_DARK)

    x = 386
    # 右侧三条原因 + 一条破解办法：
    # 「声音像 / 脸也像 / 时间紧」正好是这类骗局能成的三个条件。
    text(d, x, 100, "为什么会被骗？", 26, BRAND_DARK)
    lines = [("声音像", "骗子用一段录音就能克隆音色"),
             ("脸也像", "一张正面照就能实时换脸"),
             ("时间紧", "“马上截止”，逼你来不及核实")]
    yy = 150
    for i, (a, b) in enumerate(lines, 1):
        px(d, x, yy, 46, 46, TEAL)
        text(d, x + 12, yy + 8, str(i), 26, WHITE)
        text(d, x + 58, yy + 2, a, 24, INK)
        text(d, x + 58, yy + 30, b, 18, (90, 84, 74))
        yy += 78
    px(d, x, 400, 476, 76, (250, 232, 214))
    text(d, x + 14, 412, "破解办法：挂断视频，用你手机里存的老号码", 19, BRAND_DARK)
    text(d, x + 14, 442, "回拨过去，或让本人做一个“只有你们知道”的动作。", 19, BRAND_DARK)

    os.makedirs(OUT, exist_ok=True)  # 同样先建目录再存图，两个函数各自独立，互不依赖
    img.save(os.path.join(OUT, "case1.png"))  # 存 PNG 供预览


if __name__ == "__main__":  # 直接运行本文件时才画图；被 import 时不会产生副作用
    case0()
    case1()
    print("written:", OUT)
