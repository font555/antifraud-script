"""画案例配图样张：把「仿真实截图」和「像素风」融合，先出 2 张给用户看。

输出到 logs/art/preview-*.png（900x506，和卡片图片区同比例）。
"""

import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT = os.path.join(ROOT, "app", "src", "main", "assets", "fonts", "fusion_pixel.ttf")
OUT = os.path.join(ROOT, "logs", "art")

W, H = 900, 506
PAPER = (246, 239, 224)
INK = (43, 43, 51)
INK_SOFT = (68, 64, 60)
BRAND = (228, 87, 46)
BRAND_DARK = (191, 61, 28)
GOLD = (233, 180, 76)
TEAL = (63, 127, 166)
GRAY = (150, 145, 135)
GRAY_LIGHT = (236, 232, 222)
WHITE = (255, 255, 255)


def f(size):
    return ImageFont.truetype(FONT, size)


def px(d, x, y, w, h, c):
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=c)


def tx(d, x, y, s, size, c=INK):
    d.text((x, y), s, font=f(size), fill=c)


def status_bar(d):
    px(d, 0, 0, W, 30, INK)
    tx(d, 14, 5, "20:41", 16, WHITE)
    tx(d, W - 150, 5, "5G   96%", 16, WHITE)


def avatar(d, x, y, size, color, ch):
    px(d, x, y, size, size, INK)
    px(d, x + 2, y + 2, size - 4, size - 4, color)
    tw = d.textlength(ch, font=f(int(size * 0.55)))
    d.text((x + (size - tw) / 2, y + size * 0.18), ch, font=f(int(size * 0.55)), fill=WHITE)


def bubble_left(d, x, y, w, h, text_s, size=19, fill=WHITE):
    # 小三角
    px(d, x - 8, y + 14, 8, 10, INK)
    px(d, x, y, w, h, INK)
    px(d, x + 2, y + 2, w - 4, h - 4, fill)
    tx(d, x + 14, y + (h - size) // 2 - 2, text_s, size, INK)


def bubble_right(d, x, y, w, h, text_s, size=19):
    px(d, x + w, y + 14, 8, 10, INK)
    px(d, x, y, w, h, INK)
    px(d, x + 2, y + 2, w - 4, h - 4, BRAND)
    tw = d.textlength(text_s, font=f(size))
    tx(d, x + (w - tw) / 2, y + (h - size) // 2 - 2, text_s, size, WHITE)


def chat_case():
    """游戏账号诈骗：卖号聊天，一步步加钱"""
    img = Image.new("RGB", (W, H), PAPER)
    d = ImageDraw.Draw(img)
    status_bar(d)

    # 聊天顶栏
    px(d, 0, 30, W, 76, INK)
    tx(d, 16, 52, "←", 26, WHITE)
    avatar(d, 60, 44, 48, BRAND, "卖")
    tx(d, 122, 50, "卖家 · 游戏账号专营", 22, WHITE)
    tx(d, 122, 78, "在线 · 已实名", 15, (190, 186, 176))
    tx(d, W - 56, 52, "···", 24, WHITE)

    # 商品条
    px(d, 16, 116, W - 32, 76, WHITE)
    px(d, 26, 126, 56, 56, GRAY_LIGHT)
    tx(d, 32, 140, "全皮", 18, GRAY)
    tx(d, 96, 124, "全皮账号 · 满级 · 可换绑", 21, INK)
    tx(d, 96, 152, "¥888", 22, BRAND_DARK)
    tx(d, 190, 156, "原价 3000", 15, GRAY)
    tx(d, W - 130, 156, "库存 1", 15, TEAL)

    # 聊天记录
    ty = 206
    d.text((W // 2 - 46, ty), "今天 20:41", font=f(15), fill=GRAY)
    ty += 30

    bubble_left(d, 16, ty, 470, 42, "兄弟，账号绝对安全，先付 200 定金就发货", 18)
    ty += 58
    bubble_right(d, 596, ty, 288, 40, "已转 200", 18)
    d.text((470, ty + 44), "已读", font=f(14), fill=GRAY)
    ty += 66
    bubble_left(d, 16, ty, 520, 42, "系统检测账号异常，交 8000 解冻金，原路退", 18)
    ty += 58
    bubble_right(d, 596, ty, 288, 40, "又转了 8000", 18)
    ty += 66
    # 系统提示
    px(d, W // 2 - 150, ty, 300, 30, GRAY_LIGHT)
    d.text((W // 2 - 132, ty + 6), "对方已将你删除", font=f(15), fill=GRAY)
    ty += 42
    bubble_left(d, 16, ty, 470, 42, "最后交 3000 保证金，马上把号给你", 18, (252, 232, 222))
    ty += 58

    # 底部输入栏
    px(d, 0, H - 62, W, 62, (240, 236, 228))
    px(d, 16, H - 48, W - 130, 34, WHITE)
    tx(d, 30, H - 42, "发送消息…", 17, GRAY)
    px(d, W - 96, H - 50, 34, 34, WHITE)
    tx(d, W - 86, H - 46, "＋", 22, INK)
    px(d, W - 52, H - 50, 34, 34, WHITE)
    tx(d, W - 44, H - 46, "🎙", 20, INK)

    # 示意图角标
    px(d, W - 96, 40, 82, 26, (40, 38, 44))
    tx(d, W - 88, 44, "示意图", 14, WHITE)

    img.save(os.path.join(OUT, "preview-chat.png"))


def call_case():
    """AI 换脸：视频通话里的“女儿”"""
    img = Image.new("RGB", (W, H), (36, 34, 40))
    d = ImageDraw.Draw(img)
    status_bar(d)

    # 视频画面
    px(d, 0, 30, W, 372, (150, 168, 158))
    # 人物：头发/脸/五官/肩
    cx = W // 2
    px(d, cx - 110, 78, 220, 40, (70, 52, 44))          # 头顶
    px(d, cx - 128, 108, 256, 56, (70, 52, 44))         # 头发两侧
    px(d, cx - 92, 96, 184, 208, (240, 210, 172))       # 脸
    px(d, cx - 56, 150, 22, 22, INK)                    # 左眼
    px(d, cx + 34, 150, 22, 22, INK)                    # 右眼
    px(d, cx - 8, 186, 16, 12, (214, 170, 150))         # 鼻
    px(d, cx - 34, 216, 68, 14, BRAND_DARK)             # 嘴
    px(d, cx - 150, 300, 300, 102, (86, 96, 120))       # 上衣

    # 通话信息
    px(d, 16, 44, 268, 34, (24, 22, 28))
    tx(d, 28, 50, "视频通话  00:07", 18, WHITE)
    px(d, W - 120, 44, 104, 34, (24, 22, 28))
    tx(d, W - 108, 50, "女儿", 18, WHITE)

    # 字幕
    px(d, 40, 318, W - 80, 46, (20, 18, 24))
    tx(d, 60, 328, "妈，培训班要交 3.5 万，转给“王老师”", 20, WHITE)

    # 底部按钮
    px(d, 0, 402, W, 104, (26, 24, 30))
    labels = [("静音", 150), ("挂断", 450), ("免提", 750)]
    for name, x in labels:
        color = (196, 58, 58) if name == "挂断" else (58, 56, 64)
        px(d, x - 44, 428, 88, 48, color)
        tw = d.textlength(name, font=f(17))
        tx(d, x - tw / 2, 440, name, 17, WHITE)

    # 示意图角标
    px(d, W - 96, 40, 82, 26, (40, 38, 44))
    tx(d, W - 88, 44, "示意图", 14, WHITE)

    img.save(os.path.join(OUT, "preview-call.png"))


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    chat_case()
    call_case()
    print("written to", OUT)
