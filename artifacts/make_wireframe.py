# -*- coding: utf-8 -*-
"""三主界面线框图（像素风）：首页 / 剧本 / 我的。"""
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = r"C:\Users\Lecoo\Desktop\antifraud-script"
OUT = os.path.join(ROOT, "artifacts", "三主界面-线框图.png")
FONT = os.path.join(ROOT, "app", "src", "main", "assets", "fonts", "fusion_pixel.ttf")

S = 2
PAPER = (246, 231, 198)
WHITE = (255, 250, 238)
DARK = (74, 50, 34)
LINE = (216, 190, 146)
INK = (59, 42, 26)
INK2 = (107, 82, 51)
INK3 = (156, 133, 99)
BRAND = (228, 87, 46)
BLUE = (63, 127, 166)
GOLD = (233, 180, 76)
GREEN = (78, 123, 58)
PLUM = (107, 76, 138)

PW, PH = 360, 720          # 单个手机宽高（画布坐标）
GAP = 46
W = PW * 3 + GAP * 4
H = PH + 190


def f(size):
    return ImageFont.truetype(FONT, size * S)


def text(d, xy, s, size, color, anchor="la", bold=False):
    d.text((xy[0] * S, xy[1] * S), s, font=f(size), fill=color, anchor=anchor)


def rect(d, box, fill=None, outline=None, width=2, r=0):
    x0, y0, x1, y1 = [v * S for v in box]
    d.rectangle([x0, y0, x1, y1], fill=fill, outline=outline, width=width * S)


img = Image.new("RGB", (W * S, H * S), PAPER)
d = ImageDraw.Draw(img)

text(d, (GAP, 28), "三主界面 · 线框方案", 38, BRAND)
text(d, (GAP, 72), "底部三个 Tab：首页 / 剧本 / 我的　|　保留翻书动画与零权限 · 零依赖", 17, INK2)


TOP = 134


def phone(x, title, subtitle):
    rect(d, (x, TOP, x + PW, TOP + PH), WHITE, DARK, 3)
    rect(d, (x + 150, TOP + 8, x + PW - 150, TOP + 16), (60, 40, 28), None, 0)
    text(d, (x + PW / 2, TOP - 28), title, 22, INK, anchor="ma")
    text(d, (x + PW / 2, TOP + PH + 16), subtitle, 15, INK3, anchor="ma")


def tabbar(x, active):
    y = TOP + PH - 62
    rect(d, (x + 1, y, x + PW - 1, TOP + PH - 1), PAPER, None, 0)
    rect(d, (x + 1, y, x + PW - 1, y + 2), LINE, None, 0)
    labels = ["首页", "剧本", "我的"]
    for i, lb in enumerate(labels):
        cx = x + PW * (i + 0.5) / 3
        color = BRAND if i == active else INK3
        glyph = ["▣", "≡", "○"][i]
        text(d, (cx, y + 14), glyph, 20, color, anchor="ma")
        text(d, (cx, y + 40), lb, 14, color, anchor="ma")


# ================= 1. 首页 =================
x = GAP
phone(x, "① 首页", "轮播案例 + 书架 + 口诀")
# 顶部问候
text(d, (x + 16, 148), "你好，同学", 17, INK)
text(d, (x + PW - 16, 148), "损失 ¥0", 14, INK3, anchor="ra")
# 轮播
rect(d, (x + 16, 176, x + PW - 16, 300), (235, 214, 176), DARK, 2)
text(d, (x + PW / 2, 196), "【轮播图】真实案例", 15, INK2, anchor="ma")
text(d, (x + PW / 2, 224), "「为买游戏账号被骗 5 万余元」", 12, INK, anchor="ma")
text(d, (x + PW / 2, 250), "人民网 · 2025-08-22", 11, INK3, anchor="ma")
for i in range(4):
    cx = x + PW / 2 - 21 + i * 14
    rect(d, (cx - 3, 272, cx + 3, 278), BRAND if i == 0 else LINE, None, 0)
text(d, (x + PW / 2, 312), "← 左右滑动 →", 10, INK3, anchor="ma")
# 书架
text(d, (x + 16, 336), "反诈书架（6 本）", 16, INK)
colors = [BRAND, GOLD, BLUE, PLUM, GREEN, (60, 122, 137)]
glyphs = ["游", "单", "服", "法", "贷", "脸"]
bw, bh = 92, 104
for i in range(6):
    col = i % 3
    row = i // 3
    bx = x + 16 + col * (bw + 22)
    by = 364 + row * (bh + 32)
    rect(d, (bx, by, bx + bw, by + bh), colors[i], DARK, 2)
    text(d, (bx + bw / 2, by + bh / 2), glyphs[i], 40, WHITE, anchor="mm")
    text(d, (bx + bw / 2, by + bh + 8), ["游戏账号", "刷单返利", "冒充客服", "冒充公检法", "校园贷", "AI换脸"][i],
         10, INK2, anchor="ma")
# 口诀
rect(d, (x + 16, 638, x + PW - 16, 700), WHITE, LINE, 2)
text(d, (x + 28, 648), "反诈四步口诀", 13, BRAND)
text(d, (x + 28, 670), "不给钱 · 不转账 · 不点链接 · 先核实", 11, INK2)
tabbar(x, 0)

# ================= 2. 剧本 =================
x = GAP * 2 + PW
phone(x, "② 剧本", "不分类 · 平铺 + 右下角加号")
# 搜索/筛选
rect(d, (x + 16, 148, x + PW - 16, 182), PAPER, LINE, 2)
text(d, (x + 28, 155), "搜剧名 / 类型", 12, INK3)
# 分类筛选 chips
chips = ["全部", "游戏", "刷单", "客服", "公检法", "贷款", "换脸"]
cx0 = x + 16
for i, c in enumerate(chips):
    w = 20 + len(c) * 13
    if cx0 + w > x + PW - 16:
        break
    rect(d, (cx0, 192, cx0 + w, 218), BRAND if i == 0 else WHITE, BRAND if i == 0 else LINE, 2)
    text(d, (cx0 + w / 2, 205), c, 10, WHITE if i == 0 else INK2, anchor="mm")
    cx0 += w + 8
# 剧本列表
titles = [
    ("卖号那天来的「高价买家」", "游戏账号 · 30 节点 · 6–9 分钟"),
    ("低价全皮账号的诱惑", "游戏账号 · 25 节点 · 6–8 分钟"),
    ("点赞返现的兼职", "刷单返利 · 28 节点 · 6–8 分钟"),
    ("客服来电要退款", "冒充客服 · 30 节点 · 6–8 分钟"),
    ("屏幕里的警察", "冒充公检法 · 30 节点 · 6–8 分钟"),
    ("视频里的表哥", "AI换脸 · 22 节点 · 6–8 分钟"),
]
for i, (t, sub) in enumerate(titles):
    y = 232 + i * 66
    rect(d, (x + 16, y, x + PW - 16, y + 56), WHITE, LINE, 2)
    rect(d, (x + 16, y, x + 24, y + 56), colors[i % 6], None, 0)
    text(d, (x + 34, y + 10), t, 13, INK)
    text(d, (x + 34, y + 32), sub, 10, INK3)
# FAB
fx, fy = x + PW - 62, TOP + PH - 150
d.ellipse([(fx - 26) * S, (fy - 26) * S, (fx + 26) * S, (fy + 26) * S], fill=BRAND, outline=DARK, width=2 * S)
text(d, (fx, fy - 2), "＋", 30, WHITE, anchor="mm")
text(d, (fx - 6, fy + 40), "新建 / 导入", 10, INK2, anchor="ma")
tabbar(x, 1)

# ================= 3. 我的 =================
x = GAP * 3 + PW * 2
phone(x, "③ 我的", "档案 + 我的创作 + 设置")
# 个人卡
rect(d, (x + 16, 148, x + PW - 16, 248), WHITE, DARK, 2)
rect(d, (x + 32, 164, x + 92, 224), BRAND, DARK, 2)
text(d, (x + 62, 194), "生", 26, WHITE, anchor="mm")
text(d, (x + 104, 168), "同学", 17, INK)
text(d, (x + 104, 196), "已读 3 本 · 累计损失 ¥6,400", 10, INK3)
text(d, (x + 104, 216), "本地存档 · 不联网", 10, INK3)
# 统计条
stats = [("3", "已读"), ("¥6,400", "累计损失"), ("12", "高风险选择")]
for i, (v, k) in enumerate(stats):
    bx = x + 16 + i * 112
    rect(d, (bx, 262, bx + 104, 318), PAPER, LINE, 2)
    text(d, (bx + 52, 276), v, 17, INK, anchor="ma")
    text(d, (bx + 52, 300), k, 10, INK3, anchor="ma")
# 我的创作
text(d, (x + 16, 334), "我的创作", 14, INK)
rect(d, (x + 16, 356, x + PW - 16, 404), WHITE, LINE, 2)
text(d, (x + 32, 372), "我写的剧本　2 本", 12, INK)
text(d, (x + PW - 32, 372), "管理 →", 11, BRAND, anchor="ra")
# 设置
text(d, (x + 16, 422), "设置与帮助", 14, INK)
items = [("字体与字号", "像素 / 系统 · 标准~超大"), ("清除本地记录", "只清本机进度"),
         ("真实案例来源库", "71 条可核验链接"), ("新手引导", "再看一遍 4 步介绍"),
         ("关于", "版本 2.4 · 教学演示作品")]
for i, (t, sub) in enumerate(items):
    y = 444 + i * 44
    rect(d, (x + 16, y, x + PW - 16, y + 34), WHITE, LINE, 2)
    text(d, (x + 30, y + 8), t, 11, INK)
    text(d, (x + PW - 30, y + 10), sub, 9, INK3, anchor="ra")
tabbar(x, 2)

img.resize((W, H), Image.LANCZOS).save(OUT)
print("saved", OUT)
