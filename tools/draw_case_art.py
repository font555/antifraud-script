"""Draw our own case illustrations (pixel-flat style, matches the app).

Why hand-drawn: stock photos never match a specific Chinese anti-fraud case, and
real news photos are copyrighted. Drawing them ourselves keeps the picture
relevant and 100% license-clean (our own work).

Output: logs/art/caseN.png for review before it goes into assets.
"""

import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT = os.path.join(ROOT, "app", "src", "main", "assets", "fonts", "fusion_pixel.ttf")
OUT = os.path.join(ROOT, "logs", "art")

W, H = 900, 506
PAPER = (255, 246, 224)
INK = (43, 43, 51)
BRAND = (228, 87, 46)
BRAND_DARK = (191, 61, 28)
GOLD = (233, 180, 76)
TEAL = (63, 127, 166)
GREEN = (78, 123, 58)
GRAY = (150, 145, 135)
LIGHT = (240, 228, 200)
WHITE = (255, 255, 255)


def font(size):
    return ImageFont.truetype(FONT, size)


def px(d, x, y, w, h, color):
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=color)


def text(d, x, y, s, size, color=INK):
    d.text((x, y), s, font=font(size), fill=color)


def phone_frame(d, x, y, w, h):
    """A phone drawn pixel-style, returns inner screen rect."""
    px(d, x, y, w, h, INK)
    px(d, x + 4, y + 4, w - 8, h - 8, LIGHT)
    px(d, x + w // 2 - 30, y + 8, 60, 8, INK)          # speaker
    px(d, x + w // 2 - 40, y + h - 22, 80, 10, INK)    # home bar
    return (x + 12, y + 26, w - 24, h - 56)


def bubble(d, x, y, w, h, fill, text_s, size=20, color=INK):
    px(d, x, y, w, h, INK)
    px(d, x + 2, y + 2, w - 4, h - 4, fill)
    text(d, x + 12, y + (h - size) // 2 - 3, text_s, size, color)


def case0():
    """为买游戏账号被骗 5 万：低价账号 → 定金 → 尾款 → 刷流水"""
    img = Image.new("RGB", (W, H), PAPER)
    d = ImageDraw.Draw(img)
    text(d, 40, 30, "「低价全皮账号」是怎么一步步加钱的", 30, INK)
    px(d, 40, 72, 240, 4, BRAND)

    sx, sy, sw, sh = phone_frame(d, 40, 96, 300, 396)
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
    px(d, x, 400, 476, 76, (250, 232, 214))
    text(d, x + 14, 412, "记住：正规平台不会让你私下转账，", 20, BRAND_DARK)
    text(d, x + 14, 442, "更不会让你“刷流水、解冻、交保证金”。", 20, BRAND_DARK)

    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, "case0.png"))


def case1():
    """AI 换脸：视频里是“女儿”，但要钱的是骗子"""
    img = Image.new("RGB", (W, H), PAPER)
    d = ImageDraw.Draw(img)
    text(d, 40, 30, "视频里的“女儿”，不一定是女儿", 30, INK)
    px(d, 40, 72, 240, 4, BRAND)

    # 左侧手机视频通话
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

    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, "case1.png"))


if __name__ == "__main__":
    case0()
    case1()
    print("written:", OUT)
