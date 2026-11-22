# -*- coding: utf-8 -*-
"""把方案 5（书架上三本书 + 反诈徽章）做成 Android 启动图标资源。

产出：
  mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png          （48/72/96/144/192，旧系统用）
  mipmap-{...}/ic_launcher_foreground.png                          （108~432，自适应图标前景，透明底）
  mipmap-anydpi-v26/ic_launcher.xml                                （自适应图标声明）
"""
import os
from PIL import Image, ImageDraw

RES = r"C:\Users\Lecoo\Desktop\antifraud-script\app\src\main\res"
ARTIFACT = r"C:\Users\Lecoo\Desktop\antifraud-script\artifacts\icons"

PAPER = (246, 231, 198)
PAPER_D = (217, 190, 140)
DARK = (74, 50, 34)
BRAND = (228, 87, 46)
GOLD = (233, 180, 76)
BLUE = (63, 127, 166)
WHITE = (255, 246, 220)

GRID = 64


def draw_motif(d):
    """在 64x64 画布上画：书架板 + 三本书 + 反诈徽章（方案 5）"""
    # 书架板
    d.rectangle([6, 46, 58, 53], fill=DARK)
    d.rectangle([8, 48, 56, 50], fill=PAPER_D)
    # 三本书
    for x0, x1, top, color in ((9, 21, 18, BLUE), (23, 37, 10, BRAND), (39, 51, 16, GOLD)):
        d.rectangle([x0 - 1, top - 1, x1 + 1, 46], fill=DARK)
        d.rectangle([x0, top, x1, 46], fill=color)
        d.rectangle([x0 + 1, top + 2, x1 - 1, top + 3], fill=WHITE)
    # 反诈徽章
    d.ellipse([42, 4, 60, 22], fill=DARK)
    d.ellipse([44, 6, 58, 20], fill=BRAND)
    d.rectangle([49, 8, 53, 15], fill=WHITE)
    d.rectangle([49, 17, 53, 19], fill=WHITE)


def legacy_icon(size):
    """旧系统图标：羊皮纸底 + 深色描边 + 图案"""
    img = Image.new("RGB", (GRID, GRID), PAPER)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, GRID - 1, GRID - 1], outline=DARK, width=2)
    d.rectangle([4, 4, GRID - 5, GRID - 5], outline=PAPER_D)
    draw_motif(d)
    return img.resize((size, size), Image.NEAREST)


def adaptive_foreground(size_px, grid=108):
    """自适应图标前景：透明底，图案缩到安全区内（约 60%）"""
    canvas = Image.new("RGBA", (grid, grid), (0, 0, 0, 0))
    art = Image.new("RGBA", (GRID, GRID), (0, 0, 0, 0))
    d = ImageDraw.Draw(art)
    draw_motif(d)
    inner = int(grid * 0.62)          # 内容占 62%，落在 66dp 安全区内
    art = art.resize((inner, inner), Image.NEAREST)
    off = (grid - inner) // 2
    canvas.paste(art, (off, off), art)
    return canvas.resize((size_px, size_px), Image.NEAREST)


DENSITIES = [("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)]

for name, scale in DENSITIES:
    folder = os.path.join(RES, "mipmap-" + name)
    os.makedirs(folder, exist_ok=True)
    legacy_icon(int(48 * scale)).save(os.path.join(folder, "ic_launcher.png"))
    adaptive_foreground(int(108 * scale)).save(os.path.join(folder, "ic_launcher_foreground.png"))
    print("density", name, "ok")

# 自适应图标声明
anydpi = os.path.join(RES, "mipmap-anydpi-v26")
os.makedirs(anydpi, exist_ok=True)
xml = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/paper" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
"""
for fname in ("ic_launcher.xml", "ic_launcher_round.xml"):
    with open(os.path.join(anydpi, fname), "w", encoding="utf-8", newline="\n") as f:
        f.write(xml)
print("adaptive icon xml ok")

# 同时把预览图存到 artifacts，方便文档/PPT 用
legacy_icon(512).save(os.path.join(ARTIFACT, "icon-final-512.png"))
print("preview saved")
