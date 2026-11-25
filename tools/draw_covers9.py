"""画齐轮播的 9 张配图：6 张案例（仿截图 + 像素）+ 3 张口诀（信息图）。

画幅更宽：案例 900x360（2.5:1）、口诀 900x300（3:1），
这样轮播整体高度能压到 260dp 左右，同时图片内容依然完整不裁切。

全部由本作品自绘，不依赖任何图库照片，没有第三方版权问题。
输出直接覆盖 app/src/main/assets/covers/case0-5.jpg 和 tip0-2.jpg。
"""

import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT = os.path.join(ROOT, "app", "src", "main", "assets", "fonts", "fusion_pixel.ttf")
DST = os.path.join(ROOT, "app", "src", "main", "assets", "covers")

W = 900
CASE_H = 360
TIP_H = 300

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
    px(d, 0, 0, W, 24, INK)
    tx(d, 14, 3, "20:41", 13, WHITE)
    tx(d, W - 122, 3, "5G   96%", 13, WHITE)


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

    # 顶栏 24 ~ 74
    px(d, 0, 24, W, 50, INK)
    tx(d, 14, 36, "←", 22, WHITE)
    avatar(d, 50, 30, 36, header_color, header_avatar)
    tx(d, 98, 30, title, 18, WHITE)
    tx(d, 98, 54, subtitle, 12, (190, 186, 176))

    y = 80
    if product:
        name, price, note = product
        px(d, 16, y, W - 32, 44, WHITE)
        px(d, 22, y + 4, 36, 36, GRAY_LIGHT)
        tx(d, 27, y + 12, "商品", 12, GRAY)
        tx(d, 70, y + 2, name, 17, INK)
        tx(d, 70, y + 22, price, 17, BRAND_DARK)
        note_x = 70 + int(d.textlength(price, font=f(17))) + 14
        tx(d, note_x, y + 24, note, 12, GRAY)
        y += 52

    center(d, W // 2, y, "今天 20:41", 12, GRAY)
    y += 18

    avail = (CASE_H - 40) - y - 4
    gap = max(34, min(44, avail // max(len(lines), 1)))
    size = 16
    for side, text, style in lines:
        tw = int(d.textlength(text, font=f(size))) + 24
        bh = gap - 12
        if side == "SYS":
            wp = int(d.textlength(text, font=f(12))) + 20
            px(d, W // 2 - wp // 2, y, wp, 22, GRAY_LIGHT)
            center(d, W // 2, y + 3, text, 12, GRAY)
            y += gap
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
    px(d, 16, CASE_H - 30, W - 120, 22, WHITE)
    tx(d, 28, CASE_H - 26, bottom_hint, 13, GRAY)
    px(d, W - 96, CASE_H - 32, 22, 22, WHITE)
    tx(d, W - 90, CASE_H - 30, "＋", 16, INK)
    px(d, W - 64, CASE_H - 32, 22, 22, WHITE)
    tx(d, W - 58, CASE_H - 30, "◦", 16, INK)
    return img


def call_screen(name, line, buttons=("静音", "挂断", "免提")):
    img = Image.new("RGB", (W, CASE_H), DARK_BG)
    d = ImageDraw.Draw(img)
    status(d)
    # 视频区 24 ~ 286
    px(d, 0, 24, W, 262, (150, 168, 158))
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
    tx(d, 24, 36, "视频通话  00:07", 14, WHITE)
    px(d, W - 110, 32, 86, 26, (24, 22, 28))
    tx(d, W - 100, 36, name, 14, WHITE)

    px(d, 36, 222, W - 72, 36, (20, 18, 24))
    tx(d, 52, 230, line, 16, WHITE)

    # 按钮 286 ~ 360
    px(d, 0, 286, W, 74, (26, 24, 30))
    for label, x in zip(buttons, (150, 450, 750)):
        c = (196, 58, 58) if label == "挂断" else (58, 56, 64)
        px(d, x - 44, 302, 88, 42, c)
        center(d, x, 313, label, 15, WHITE)
    return img


def case0():
    return chat_screen(
        "卖家 · 游戏账号专营", "在线 · 已实名", "卖", BRAND,
        [("L", "账号绝对安全，先付 200 定金就发货", "normal"),
         ("R", "已转 200", "normal"),
         ("L", "账号异常，交 8000 解冻金，原路退", "normal"),
         ("R", "又转了 8000", "normal"),
         ("SYS", "对方已将你删除", "normal")],
        product=("全皮账号 · 满级", "¥888", "原价 3000"))


def case1():
    return call_screen("女儿", "妈，培训班要交 3.5 万，转给“王老师”")


def case2():
    return call_screen("表哥", "借我 3 万急用，别告诉家里，晚点还你")


def case3():
    """刷单返利：任务群 + 余额提现失败"""
    img = Image.new("RGB", (W, CASE_H), PAPER)
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
    for name, money, state, color in tasks:
        px(d, 16, y, W - 32, 52, WHITE)
        px(d, 16, y, 6, 52, color)
        tx(d, 30, y + 6, name, 17, INK)
        tx(d, 30, y + 28, money, 15, BRAND_DARK)
        w = int(d.textlength(state, font=f(12))) + 16
        px(d, W - 22 - w, y + 14, w, 22, color)
        tx(d, W - 16 - w, y + 18, state, 12, WHITE)
        y += 62

    px(d, 16, y + 2, W - 32, 48, (250, 232, 214))
    tx(d, 30, y + 6, "可提现余额  ¥0.00", 17, BRAND_DARK)
    tx(d, 30, y + 30, "提示：再完成 3 单即可提现（永远差 3 单）", 13, (120, 96, 80))

    px(d, 0, CASE_H - 38, W, 38, (240, 236, 228))
    px(d, 16, CASE_H - 30, W - 120, 22, WHITE)
    tx(d, 28, CASE_H - 26, "发送消息…", 13, GRAY)
    return img


def case4():
    return chat_screen(
        "收卡 · 日结 100", "在线", "卡", TEAL,
        [("L", "日租 100 一张，卡和验证码给我走账", "normal"),
         ("R", "卡号发你了", "normal"),
         ("L", "卡被冻结了，你得赔 2000，不然报警", "warn"),
         ("R", "我没钱…", "normal"),
         ("SYS", "对方已将你移除群聊", "normal")],
        product=("出租银行卡 · 日结", "100 元/张", "当天结算"))


def case5():
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
    img = Image.new("RGB", (W, TIP_H), PAPER)
    d = ImageDraw.Draw(img)
    px(d, 0, 0, W, 6, BRAND)
    tx(d, 30, 12, title, 24, INK)
    px(d, 30, 44, 130, 4, BRAND)

    y = 58
    for i, (key, sub, desc) in enumerate(items):
        px(d, 30, y, 130, 54, BRAND if i % 2 == 0 else BRAND_DARK)
        center(d, 30 + 65, y + 8, key, 26, WHITE)
        tx(d, 176, y + 2, sub, 19, INK)
        tx(d, 176, y + 26, desc, 14, (110, 104, 94))
        y += 62
    return img


def tip0():
    return tip_screen("先记住这 3 个字", [
        ("码", "码不给", "验证码、密码、人脸核验，谁要都不给"),
        ("链", "链不点", "陌生链接、二维码、安装包，一律不点"),
        ("钱", "钱不转", "先垫钱、交保证金、刷流水，都是开场"),
    ])


def tip1():
    return tip_screen("再记住这 3 个字", [
        ("平", "平不离", "客服不会让你离开官方平台办退款"),
        ("话", "话先挂", "可疑来电先挂断，用官方 App 回拨"),
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
