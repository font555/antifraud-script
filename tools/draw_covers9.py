"""画齐轮播的 9 张配图：6 张案例（仿截图 + 像素）+ 3 张口诀（信息图）。

全部由本作品自绘，不依赖任何图库照片，没有第三方版权问题。
输出直接覆盖 app/src/main/assets/covers/case0-5.jpg 和 tip0-2.jpg。
"""

import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT = os.path.join(ROOT, "app", "src", "main", "assets", "fonts", "fusion_pixel.ttf")
DST = os.path.join(ROOT, "app", "src", "main", "assets", "covers")

W, H = 900, 506
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
    return ImageFont.truetype(FONT, size)


def px(d, x, y, w, h, c):
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=c)


def tx(d, x, y, s, size, c=INK):
    d.text((x, y), s, font=f(size), fill=c)


def center(d, cx, y, s, size, c=INK):
    tw = d.textlength(s, font=f(size))
    d.text((cx - tw / 2, y), s, font=f(size), fill=c)


def tag(d, x, y, s="示意图"):
    w = int(d.textlength(s, font=f(13))) + 16
    px(d, x, y, w, 24, (40, 38, 44))
    tx(d, x + 8, y + 4, s, 13, WHITE)


def status(d):
    px(d, 0, 0, W, 30, INK)
    tx(d, 14, 5, "20:41", 16, WHITE)
    tx(d, W - 140, 5, "5G   96%", 16, WHITE)


def avatar(d, x, y, size, color, ch):
    px(d, x, y, size, size, INK)
    px(d, x + 2, y + 2, size - 4, size - 4, color)
    tw = d.textlength(ch, font=f(int(size * 0.55)))
    d.text((x + (size - tw) / 2, y + size * 0.16), ch,
           font=f(int(size * 0.55)), fill=WHITE)


# ---------------- 通用：聊天界面 ----------------

def chat_screen(title, subtitle, header_avatar, header_color, lines,
                product=None, bottom_hint="发送消息…"):
    """lines: [(side, text, style)] side in L/R/SYS；style in normal/warn"""
    img = Image.new("RGB", (W, H), PAPER)
    d = ImageDraw.Draw(img)
    status(d)
    px(d, 0, 30, W, 74, INK)
    tx(d, 16, 50, "←", 26, WHITE)
    avatar(d, 58, 42, 48, header_color, header_avatar)
    tx(d, 120, 46, title, 22, WHITE)
    tx(d, 120, 74, subtitle, 15, (190, 186, 176))
    tag(d, W - 110, H - 56 - 32)

    y = 112
    if product:
        name, price, note = product
        px(d, 16, y, W - 32, 68, WHITE)
        px(d, 26, y + 8, 52, 52, GRAY_LIGHT)
        tx(d, 32, y + 22, "商品", 15, GRAY)
        tx(d, 92, y + 4, name, 21, INK)
        tx(d, 92, y + 32, price, 21, BRAND_DARK)
        tx(d, 190, y + 36, note, 15, GRAY)
        y += 84

    center(d, W // 2, y, "今天 20:41", 15, GRAY)
    y += 26

    for side, text, style in lines:
        size = 18
        tw = int(d.textlength(text, font=f(size))) + 30
        if side == "SYS":
            wp = int(d.textlength(text, font=f(15))) + 24
            px(d, W // 2 - wp // 2, y, wp, 28, GRAY_LIGHT)
            center(d, W // 2, y + 6, text, 15, GRAY)
            y += 38
            continue
        if side == "L":
            fill = (252, 232, 222) if style == "warn" else WHITE
            px(d, 16, y, tw, 44, INK)
            px(d, 18, y + 2, tw - 4, 40, fill)
            tx(d, 30, y + 11, text, size)
        else:
            x = W - 16 - tw
            px(d, x, y, tw, 42, INK)
            px(d, x + 2, y + 2, tw - 4, 38, BRAND)
            center(d, x + tw / 2, y + 10, text, size, WHITE)
        y += 60

    px(d, 0, H - 56, W, 56, (240, 236, 228))
    px(d, 16, H - 44, W - 140, 32, WHITE)
    tx(d, 30, H - 38, bottom_hint, 16, GRAY)
    px(d, W - 108, H - 46, 30, 30, WHITE)
    tx(d, W - 100, H - 42, "＋", 20, INK)
    px(d, W - 68, H - 46, 30, 30, WHITE)
    tx(d, W - 60, H - 42, "◦", 20, INK)
    return img


def call_screen(name, subtitle, line, buttons=("静音", "挂断", "免提")):
    img = Image.new("RGB", (W, H), DARK_BG)
    d = ImageDraw.Draw(img)
    status(d)
    px(d, 0, 30, W, 364, (150, 168, 158))
    cx = W // 2
    # 像素人物
    px(d, cx - 84, 96, 168, 44, (72, 54, 46))
    px(d, cx - 96, 124, 192, 40, (72, 54, 46))
    px(d, cx - 72, 116, 144, 176, (240, 210, 172))
    px(d, cx - 42, 158, 18, 18, INK)
    px(d, cx + 24, 158, 18, 18, INK)
    px(d, cx - 6, 190, 12, 10, (214, 170, 150))
    px(d, cx - 26, 214, 52, 12, BRAND_DARK)
    px(d, cx - 118, 290, 236, 64, (86, 96, 120))

    px(d, 16, 44, 250, 32, (24, 22, 28))
    tx(d, 28, 50, "视频通话  00:07", 17, WHITE)
    px(d, W - 128, 44, 104, 32, (24, 22, 28))
    tx(d, W - 116, 50, name, 17, WHITE)
    px(d, 40, 306, W - 80, 44, (20, 18, 24))
    tx(d, 58, 316, line, 19, WHITE)
    px(d, 0, 394, W, 112, (26, 24, 30))
    xs = (150, 450, 750)
    for label, x in zip(buttons, xs):
        c = (196, 58, 58) if label == "挂断" else (58, 56, 64)
        px(d, x - 48, 420, 96, 52, c)
        center(d, x, 436, label, 17, WHITE)
    tag(d, W - 104, 120)
    return img


# ---------------- 6 张案例 ----------------

def case0():
    return chat_screen(
        "卖家 · 游戏账号专营", "在线 · 已实名", "卖", BRAND,
        [("L", "兄弟，账号绝对安全，先付 200 定金就发货", "normal", ),
         ("R", "已转 200", "normal"),
         ("L", "系统检测账号异常，交 8000 解冻金，原路退", "normal"),
         ("R", "又转了 8000", "normal"),
         ("SYS", "对方已将你删除", "normal"),
         ("L", "最后交 3000 保证金，马上把号给你", "warn")],
        product=("全皮账号 · 满级 · 可换绑", "¥888", "原价 3000 · 库存 1"))


def case1():
    return call_screen("女儿", "", "妈，培训班要交 3.5 万，转给“王老师”")


def case2():
    return call_screen("表哥", "", "借我 3 万急用，别告诉家里，晚点还你",
                       buttons=("静音", "挂断", "免提"))


def case3():
    """刷单返利：任务群 + 余额提现失败"""
    img = Image.new("RGB", (W, H), PAPER)
    d = ImageDraw.Draw(img)
    status(d)
    px(d, 0, 30, W, 74, INK)
    tx(d, 16, 50, "←", 26, WHITE)
    avatar(d, 58, 42, 48, GOLD, "单")
    tx(d, 120, 46, "兼职接单群 · 日结", 22, WHITE)
    tx(d, 120, 74, "群成员 386 人", 15, (190, 186, 176))
    tag(d, W - 110, H - 56 - 32)

    # 任务卡
    tasks = [("点赞 3 条短视频", "+5 元", "已完成", GREEN),
             ("垫付 300 元拍下商品", "返 330 元", "进行中", BRAND),
             ("连刷 3 单才能提现", "当前 ¥0", "待完成", GRAY)]
    y = 118
    for name, money, state, color in tasks:
        px(d, 16, y, W - 32, 64, WHITE)
        px(d, 16, y, 6, 64, color)
        tx(d, 34, y + 8, name, 20, INK)
        tx(d, 34, y + 36, money, 18, BRAND_DARK)
        w = int(d.textlength(state, font=f(15))) + 20
        px(d, W - 26 - w, y + 18, w, 28, color)
        tx(d, W - 18 - w + 2, y + 22, state, 15, WHITE)
        y += 78

    # 余额与提现
    px(d, 16, y + 4, W - 32, 62, (250, 232, 214))
    tx(d, 34, y + 12, "可提现余额  ¥0.00", 21, BRAND_DARK)
    tx(d, 34, y + 42, "提示：再完成 3 单即可提现（永远差 3 单）", 15, (120, 96, 80))

    px(d, 0, H - 56, W, 56, (240, 236, 228))
    px(d, 16, H - 44, W - 140, 32, WHITE)
    tx(d, 30, H - 38, "发送消息…", 16, GRAY)
    return img


def case4():
    """租卡/兼职：日租 100 收卡走账"""
    return chat_screen(
        "收卡 · 日结 100", "在线", "卡", TEAL,
        [("L", "日租 100 一张，把卡和验证码给我走账就行", "normal"),
         ("R", "卡号发你了", "normal"),
         ("L", "卡被冻结了，你得赔 2000，不然我报警说你洗钱", "warn"),
         ("R", "我没钱…", "normal"),
         ("SYS", "对方已将你移除群聊", "normal")],
        product=("出租银行卡 · 日结", "100 元/张", "当天结算"))


def case5():
    """游戏代练/低价皮肤：先交保证金"""
    return chat_screen(
        "代练工作室 · 7 年老店", "已实名 · 押金可退", "代", GOLD,
        [("L", "代练到王者 500 元，先交 300 保证金，打完退", "normal"),
         ("R", "转了 300", "normal"),
         ("L", "系统检测你账号异常，要交 800 解冻，不然封号", "warn"),
         ("R", "又转了 800", "normal"),
         ("SYS", "对方已将你拉黑", "normal")],
        product=("王者代练 · 包上分", "¥500", "保证金 300 可退"))


# ---------------- 3 张口诀卡 ----------------

def tip_screen(title, items):
    """口诀卡：单独用 900x360 的画布（显示比例 2.5:1，和 coverHeight 一致）"""
    TH = 360
    img = Image.new("RGB", (W, TH), PAPER)
    d = ImageDraw.Draw(img)
    px(d, 0, 0, W, 8, BRAND)
    tx(d, 34, 22, title, 28, INK)
    px(d, 34, 62, 140, 4, BRAND)

    y = 88
    for i, (key, sub, desc) in enumerate(items):
        px(d, 34, y, 150, 76, BRAND if i % 2 == 0 else BRAND_DARK)
        center(d, 34 + 75, y + 14, key, 34, WHITE)
        tx(d, 198, y + 4, sub, 24, INK)
        tx(d, 198, y + 38, desc, 16, (110, 104, 94))
        y += 92

    px(d, 0, TH - 34, W, 34, INK)
    tx(d, 34, TH - 27, "反诈口诀 · 背下来能救命", 14, WHITE)
    return img


def tip0():
    return tip_screen("先记住这 3 个字", [
        ("码", "码不给", "验证码、密码、人脸核验，谁要都不给"),
        ("链", "链不点", "陌生链接、二维码、软件安装包，一律不点"),
        ("钱", "钱不转", "先垫钱、交保证金、刷流水，都是固定开场"),
    ])


def tip1():
    return tip_screen("再记住这 3 个字", [
        ("平", "平不离", "客服不会让你离开官方平台办退款"),
        ("话", "话先挂", "可疑来电先挂断，用官方 App 回拨核实"),
        ("急", "急先停", "越催你越要停，骗子最怕你冷静"),
    ])


def tip2():
    return tip_screen("最后记住这 2 个字", [
        ("警", "警必接", "96110 是反诈专线，一定要接"),
        ("证", "证要留", "聊天、转账、对方账号，全部截图留证"),
    ])


def main():
    os.makedirs(DST, exist_ok=True)
    pairs = [("case0", case0), ("case1", case1), ("case2", case2),
             ("case3", case3), ("case4", case4), ("case5", case5),
             ("tip0", tip0), ("tip1", tip1), ("tip2", tip2)]
    for name, fn in pairs:
        img = fn()
        out = os.path.join(DST, name + ".jpg")
        img.save(out, "JPEG", quality=88, optimize=True, progressive=True)
        print("%-8s %d KB" % (name + ".jpg", os.path.getsize(out) // 1024))


if __name__ == "__main__":
    main()
