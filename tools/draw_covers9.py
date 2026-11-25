"""画齐轮播的 9 张配图：6 张案例（仿截图 + 像素）+ 3 张口诀（信息图）。

画幅收窄为「案例 900x430（约 2.09:1）、口诀 900x330（约 2.7:1）」，
这样轮播整体高度可以压下来，同时图片内容不裁切。

全部由本作品自绘，不依赖任何图库照片，没有第三方版权问题。
输出直接覆盖 app/src/main/assets/covers/case0-5.jpg 和 tip0-2.jpg。
"""

import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT = os.path.join(ROOT, "app", "src", "main", "assets", "fonts", "fusion_pixel.ttf")
DST = os.path.join(ROOT, "app", "src", "main", "assets", "covers")

W = 900
CASE_H = 430
TIP_H = 330

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


def status(d):
    px(d, 0, 0, W, 26, INK)
    tx(d, 14, 4, "20:41", 14, WHITE)
    tx(d, W - 130, 4, "5G   96%", 14, WHITE)


def avatar(d, x, y, size, color, ch):
    px(d, x, y, size, size, INK)
    px(d, x + 2, y + 2, size - 4, size - 4, color)
    tw = d.textlength(ch, font=f(int(size * 0.55)))
    d.text((x + (size - tw) / 2, y + size * 0.16), ch,
           font=f(int(size * 0.55)), fill=WHITE)


# ---------------- 聊天界面（案例 0/2/4/5） ----------------

def chat_screen(title, subtitle, header_avatar, header_color, lines,
                product=None, bottom_hint="发送消息…"):
    img = Image.new("RGB", (W, CASE_H), PAPER)
    d = ImageDraw.Draw(img)
    status(d)

    # 顶栏 26 ~ 82
    px(d, 0, 26, W, 56, INK)
    tx(d, 16, 40, "←", 24, WHITE)
    avatar(d, 56, 34, 40, header_color, header_avatar)
    tx(d, 108, 36, title, 20, WHITE)
    tx(d, 108, 62, subtitle, 13, (190, 186, 176))

    y = 88
    if product:
        name, price, note = product
        px(d, 16, y, W - 32, 48, WHITE)
        px(d, 20, y + 4, 40, 40, GRAY_LIGHT)
        tx(d, 27, y + 13, "商品", 12, GRAY)
        tx(d, 84, y + 4, name, 19, INK)
        tx(d, 84, y + 28, price, 19, BRAND_DARK)
        note_x = 84 + int(d.textlength(price, font=f(19))) + 16
        tx(d, note_x, y + 30, note, 13, GRAY)
        y += 60

    center(d, W // 2, y, "今天 20:41", 13, GRAY)
    y += 22

    avail = (CASE_H - 56) - y - 6
    gap = max(36, min(48, avail // max(len(lines), 1)))
    size = 17 if gap >= 44 else 16
    for side, text, style in lines:
        tw = int(d.textlength(text, font=f(size))) + 26
        bh = gap - 12
        if side == "SYS":
            wp = int(d.textlength(text, font=f(13))) + 22
            px(d, W // 2 - wp // 2, y, wp, 24, GRAY_LIGHT)
            center(d, W // 2, y + 4, text, 13, GRAY)
            y += gap
            continue
        if side == "L":
            fill = (252, 232, 222) if style == "warn" else WHITE
            px(d, 16, y, tw, bh, INK)
            px(d, 18, y + 2, tw - 4, bh - 4, fill)
            tx(d, 28, y + (bh - size) // 2 - 2, text, size)
        else:
            x = W - 16 - tw
            px(d, x, y, tw, bh, INK)
            px(d, x + 2, y + 2, tw - 4, bh - 4, BRAND)
            center(d, x + tw / 2, y + (bh - size) // 2 - 2, text, size, WHITE)
        y += gap

    px(d, 0, CASE_H - 46, W, 46, (240, 236, 228))
    px(d, 16, CASE_H - 36, W - 130, 26, WHITE)
    tx(d, 30, CASE_H - 31, bottom_hint, 14, GRAY)
    px(d, W - 100, CASE_H - 38, 26, 26, WHITE)
    tx(d, W - 93, CASE_H - 35, "＋", 18, INK)
    px(d, W - 64, CASE_H - 38, 26, 26, WHITE)
    tx(d, W - 57, CASE_H - 35, "◦", 18, INK)
    return img


def call_screen(name, line, buttons=("静音", "挂断", "免提")):
    img = Image.new("RGB", (W, CASE_H), DARK_BG)
    d = ImageDraw.Draw(img)
    status(d)
    # 视频区 26 ~ 336
    px(d, 0, 26, W, 310, (150, 168, 158))
    cx = W // 2
    px(d, cx - 78, 64, 156, 36, (72, 54, 46))
    px(d, cx - 88, 88, 176, 34, (72, 54, 46))
    px(d, cx - 66, 80, 132, 150, (240, 210, 172))
    px(d, cx - 38, 116, 16, 16, INK)
    px(d, cx + 22, 116, 16, 16, INK)
    px(d, cx - 6, 144, 12, 9, (214, 170, 150))
    px(d, cx - 24, 166, 48, 11, BRAND_DARK)
    px(d, cx - 104, 232, 208, 56, (86, 96, 120))

    px(d, 16, 36, 232, 30, (24, 22, 28))
    tx(d, 28, 41, "视频通话  00:07", 16, WHITE)
    px(d, W - 120, 36, 96, 30, (24, 22, 28))
    tx(d, W - 108, 41, name, 16, WHITE)

    px(d, 40, 268, W - 80, 40, (20, 18, 24))
    tx(d, 58, 277, line, 17, WHITE)

    # 按钮 336 ~ 430
    px(d, 0, 336, W, 94, (26, 24, 30))
    for label, x in zip(buttons, (150, 450, 750)):
        c = (196, 58, 58) if label == "挂断" else (58, 56, 64)
        px(d, x - 46, 358, 92, 48, c)
        center(d, x, 372, label, 16, WHITE)
    return img


def case0():
    return chat_screen(
        "卖家 · 游戏账号专营", "在线 · 已实名", "卖", BRAND,
        [("L", "兄弟，账号绝对安全，先付 200 定金就发货", "normal"),
         ("R", "已转 200", "normal"),
         ("L", "系统检测账号异常，交 8000 解冻金，原路退", "normal"),
         ("R", "又转了 8000", "normal"),
         ("SYS", "对方已将你删除", "normal"),
         ("L", "最后交 3000 保证金，马上把号给你", "warn")],
        product=("全皮账号 · 满级 · 可换绑", "¥888", "原价 3000 · 库存 1"))


def case1():
    return call_screen("女儿", "妈，培训班要交 3.5 万，转给“王老师”")


def case2():
    return call_screen("表哥", "借我 3 万急用，别告诉家里，晚点还你")


def case3():
    """刷单返利：任务群 + 余额提现失败"""
    img = Image.new("RGB", (W, CASE_H), PAPER)
    d = ImageDraw.Draw(img)
    status(d)
    px(d, 0, 26, W, 56, INK)
    tx(d, 16, 40, "←", 24, WHITE)
    avatar(d, 56, 34, 40, GOLD, "单")
    tx(d, 108, 36, "兼职接单群 · 日结", 20, WHITE)
    tx(d, 108, 62, "群成员 386 人", 13, (190, 186, 176))

    tasks = [("点赞 3 条短视频", "+5 元", "已完成", GREEN),
             ("垫付 300 元拍下商品", "返 330 元", "进行中", BRAND),
             ("连刷 3 单才能提现", "当前 ¥0", "待完成", GRAY)]
    y = 90
    for name, money, state, color in tasks:
        px(d, 16, y, W - 32, 58, WHITE)
        px(d, 16, y, 6, 58, color)
        tx(d, 32, y + 8, name, 18, INK)
        tx(d, 32, y + 32, money, 16, BRAND_DARK)
        w = int(d.textlength(state, font=f(13))) + 18
        px(d, W - 24 - w, y + 16, w, 24, color)
        tx(d, W - 17 - w, y + 20, state, 13, WHITE)
        y += 70

    px(d, 16, y + 2, W - 32, 54, (250, 232, 214))
    tx(d, 32, y + 8, "可提现余额  ¥0.00", 19, BRAND_DARK)
    tx(d, 32, y + 34, "提示：再完成 3 单即可提现（永远差 3 单）", 14, (120, 96, 80))

    px(d, 0, CASE_H - 46, W, 46, (240, 236, 228))
    px(d, 16, CASE_H - 36, W - 130, 26, WHITE)
    tx(d, 30, CASE_H - 31, "发送消息…", 14, GRAY)
    return img


def case4():
    return chat_screen(
        "收卡 · 日结 100", "在线", "卡", TEAL,
        [("L", "日租 100 一张，把卡和验证码给我走账就行", "normal"),
         ("R", "卡号发你了", "normal"),
         ("L", "卡被冻结了，你得赔 2000，不然我报警说你洗钱", "warn"),
         ("R", "我没钱…", "normal"),
         ("SYS", "对方已将你移除群聊", "normal")],
        product=("出租银行卡 · 日结", "100 元/张", "当天结算"))


def case5():
    return chat_screen(
        "代练工作室 · 7 年老店", "已实名 · 押金可退", "代", GOLD,
        [("L", "代练到王者 500 元，先交 300 保证金，打完退", "normal"),
         ("R", "转了 300", "normal"),
         ("L", "系统检测你账号异常，要交 800 解冻，不然封号", "warn"),
         ("R", "又转了 800", "normal"),
         ("SYS", "对方已将你拉黑", "normal")],
        product=("王者代练 · 包上分", "¥500", "保证金 300 可退"))


# ---------------- 口诀卡 ----------------

def tip_screen(title, items):
    img = Image.new("RGB", (W, TIP_H), PAPER)
    d = ImageDraw.Draw(img)
    px(d, 0, 0, W, 8, BRAND)
    tx(d, 34, 18, title, 26, INK)
    px(d, 34, 54, 140, 4, BRAND)

    y = 72
    for i, (key, sub, desc) in enumerate(items):
        px(d, 34, y, 140, 62, BRAND if i % 2 == 0 else BRAND_DARK)
        center(d, 34 + 70, y + 10, key, 30, WHITE)
        tx(d, 190, y + 2, sub, 21, INK)
        tx(d, 190, y + 30, desc, 15, (110, 104, 94))
        y += 72
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
