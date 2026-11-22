# -*- coding: utf-8 -*-
"""生成 5 版像素风 App 图标（64x64 像素画布，放大 8 倍 -> 512x512）。"""
import os
from PIL import Image, ImageDraw

OUT = r"C:\Users\Lecoo\Desktop\antifraud-script\artifacts\icons"
os.makedirs(OUT, exist_ok=True)

PAPER = (246, 231, 198)
PAPER_D = (217, 190, 140)
DARK = (74, 50, 34)
BRAND = (228, 87, 46)
BRAND_D = (191, 61, 28)
GOLD = (233, 180, 76)
BLUE = (63, 127, 166)
GREEN = (78, 123, 58)
WHITE = (255, 246, 220)
PLUM = (142, 91, 143)

S = 8          # 放大倍数
GRID = 64


def base():
    img = Image.new("RGB", (GRID, GRID), PAPER)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, GRID - 1, GRID - 1], outline=DARK, width=2)
    d.rectangle([4, 4, GRID - 5, GRID - 5], outline=PAPER_D)
    return img, d


def save(img, name):
    big = img.resize((GRID * S, GRID * S), Image.NEAREST)
    p = os.path.join(OUT, name)
    big.save(p)
    print("saved", p)


def rect(d, box, color):
    d.rectangle(box, fill=color)


def poly(d, pts, color):
    d.polygon(pts, fill=color)


# ---------------- 方案 1：书本 + 盾牌（读书 + 防护） ----------------
img, d = base()
rect(d, [6, 8, 34, 56], DARK)
rect(d, [9, 11, 31, 53], BRAND)
rect(d, [9, 11, 15, 53], BRAND_D)          # 书脊
rect(d, [31, 13, 34, 51], WHITE)           # 页边
for y in range(17, 52, 6):
    rect(d, [31, y, 33, y + 1], PAPER_D)
# 盾牌
poly(d, [(47, 18), (60, 23), (60, 34), (47, 47), (34, 34), (34, 23)], DARK)
poly(d, [(47, 21), (57, 25), (57, 33), (47, 43), (37, 33), (37, 25)], GOLD)
d.line([(41, 31), (45, 36), (54, 25)], fill=WHITE, width=3)
save(img, "icon-1-book-shield.png")

# ---------------- 方案 2：红书 + 感叹号（警示） ----------------
img, d = base()
rect(d, [10, 5, 54, 59], DARK)
rect(d, [13, 8, 51, 56], BRAND)
rect(d, [13, 8, 20, 56], GOLD)             # 书脊
rect(d, [51, 11, 54, 53], WHITE)
for y in range(15, 53, 7):
    rect(d, [51, y, 53, y + 1], PAPER_D)
rect(d, [28, 15, 36, 37], WHITE)           # 感叹号竖
rect(d, [28, 43, 36, 51], WHITE)           # 感叹号点
save(img, "icon-2-warning-book.png")

# ---------------- 方案 3：对话气泡 + 盾 + 感叹号 ----------------
img, d = base()
rect(d, [6, 9, 58, 45], DARK)              # 气泡外框
rect(d, [9, 12, 55, 42], WHITE)            # 气泡内部
d.polygon([(16, 44), (16, 56), (30, 44)], fill=DARK)   # 尾巴
d.polygon([(19, 44), (19, 51), (28, 44)], fill=WHITE)
# 盾
poly(d, [(32, 15), (45, 20), (45, 28), (32, 40), (19, 28), (19, 20)], BRAND)
poly(d, [(32, 18), (42, 22), (42, 27), (32, 36), (22, 27), (22, 22)], BRAND_D)
rect(d, [30, 20, 34, 28], WHITE)
rect(d, [30, 31, 34, 34], WHITE)
save(img, "icon-3-bubble-shield.png")

# ---------------- 方案 4：打开的书 + 放大镜（可溯源） ----------------
img, d = base()
# 左右两页
d.polygon([(6, 46), (30, 40), (30, 14), (6, 20)], fill=WHITE, outline=DARK, width=2)
d.polygon([(58, 46), (34, 40), (34, 14), (58, 20)], fill=WHITE, outline=DARK, width=2)
rect(d, [30, 14, 34, 40], DARK)            # 书脊
for y in range(24, 38, 5):
    rect(d, [10, y, 26, y + 1], PAPER_D)
    rect(d, [38, y, 54, y + 1], PAPER_D)
# 放大镜
d.ellipse([38, 8, 56, 26], fill=DARK)
d.ellipse([41, 11, 53, 23], fill=GOLD)
d.ellipse([44, 14, 50, 20], fill=PAPER)
d.line([(52, 24), (58, 32)], fill=DARK, width=4)
save(img, "icon-4-book-magnifier.png")

# ---------------- 方案 5：书架上三本书 + 反诈徽章 ----------------
img, d = base()
rect(d, [6, 46, 58, 53], DARK)             # 书架板
rect(d, [8, 48, 56, 50], PAPER_D)
# 三本书
for x0, x1, top, color in ((9, 21, 18, BLUE), (23, 37, 10, BRAND), (39, 51, 16, GOLD)):
    rect(d, [x0 - 1, top - 1, x1 + 1, 46], DARK)
    rect(d, [x0, top, x1, 46], color)
    rect(d, [x0 + 1, top + 2, x1 - 1, top + 3], WHITE)
# 反诈徽章
d.ellipse([42, 4, 60, 22], fill=DARK)
d.ellipse([44, 6, 58, 20], fill=BRAND)
rect(d, [49, 8, 53, 15], WHITE)
rect(d, [49, 17, 53, 19], WHITE)
save(img, "icon-5-shelf-badge.png")

print("全部生成完毕：", len(os.listdir(OUT)), "个文件")
