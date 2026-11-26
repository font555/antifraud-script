"""Generate pixel-art cover banners for the home carousel.

早期版本：为首页轮播画 640×220 的像素风封面横幅（纯几何图形，无版权风险）。
**已被 tools/draw_covers9.py 取代**——后来轮播高度压缩、画幅改成
案例 2.5:1 / 口诀 3:1，统一由 draw_covers9.py 重绘 9 张图。
本脚本保留作参考，不再参与构建流程。

Pure shapes (rectangles / polygons) so the result matches the app's pixel style
and carries no third-party image licensing risk. Output goes to
app/src/main/assets/covers/*.png (640x220, upscaled x4 with NEAREST).

Usage: python tools/make_case_covers.py
"""

import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "covers")

W, H = 160, 55          # logical pixel grid
SCALE = 4               # 160*4 = 640, 55*4 = 220

INK = (43, 43, 51)
PAPER = (255, 246, 224)
BRAND = (228, 87, 46)
BRAND_DARK = (191, 61, 28)
GOLD = (233, 180, 76)
TEAL = (63, 127, 166)
GREEN = (78, 123, 58)
PURPLE = (124, 94, 168)
GRAY = (150, 145, 135)
WHITE = (255, 255, 255)


def new_canvas(bg):
    img = Image.new("RGB", (W, H), bg)
    return img, ImageDraw.Draw(img)


def px(d, x, y, w, h, color):
    """Draw a filled rectangle on the logical grid."""
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=color)


def phone(d, x, y, w, h, body=INK, screen=TEAL, badge=BRAND):
    px(d, x, y, w, h, body)
    px(d, x + 2, y + 3, w - 4, h - 9, screen)
    px(d, x + 3, y + 5, w - 6, 5, WHITE)
    px(d, x + 3, y + 12, w - 6, 4, GOLD)
    px(d, x + w // 2 - 2, y + h - 4, 4, 2, badge)


def bubble(d, x, y, w, h, fill=WHITE, edge=INK):
    px(d, x, y, w, h, fill)
    px(d, x, y, w, 1, edge)
    px(d, x, y + h - 1, w, 1, edge)
    px(d, x, y, 1, h, edge)
    px(d, x + w - 1, y, 1, h, edge)
    px(d, x + 3, y + 4, w - 8, 2, GRAY)
    px(d, x + 3, y + 9, w - 16, 2, GRAY)


def coin(d, x, y, r=4, fill=GOLD, edge=(150, 110, 30)):
    px(d, x, y + 1, r * 2, r * 2 - 2, edge)
    px(d, x + 1, y, r * 2 - 2, r * 2, fill)
    px(d, x + r - 1, y + 2, 2, r * 2 - 4, edge)


def shield(d, x, y, w, h, fill=BRAND, edge=BRAND_DARK):
    px(d, x, y, w, h - 6, edge)
    px(d, x + 2, y + 2, w - 4, h - 10, fill)
    px(d, x + w // 2 - 6, y + h - 12, 12, 4, edge)
    px(d, x + w // 2 - 4, y + h - 8, 8, 3, edge)
    px(d, x + w // 2 - 2, y + h - 5, 4, 3, edge)


def scene_case0(d):
    """游戏账号：手机 + 手柄 + 金币"""
    phone(d, 20, 8, 34, 42)
    px(d, 64, 26, 44, 16, (60, 60, 70))
    px(d, 68, 28, 12, 12, GOLD)
    px(d, 92, 28, 8, 4, BRAND)
    px(d, 92, 34, 8, 4, TEAL)
    coin(d, 124, 12)
    coin(d, 134, 20)
    px(d, 120, 36, 24, 4, BRAND_DARK)


def scene_case1(d):
    """AI 换脸：两张脸 + 交换箭头"""
    for cx in (30, 104):
        px(d, cx, 10, 30, 30, INK)
        px(d, cx + 2, 12, 26, 26, (236, 214, 178))
        px(d, cx + 6, 20, 4, 4, INK)
        px(d, cx + 18, 20, 4, 4, INK)
        px(d, cx + 9, 28, 10, 3, BRAND_DARK)
    px(d, 66, 22, 28, 4, BRAND)
    px(d, 86, 18, 8, 12, BRAND)
    px(d, 60, 34, 4, 4, BRAND)
    px(d, 70, 36, 4, 4, BRAND)


def scene_case2(d):
    """冒充客服：耳机 + 聊天气泡"""
    px(d, 26, 10, 12, 26, INK)
    px(d, 26, 10, 40, 8, INK)
    px(d, 60, 12, 10, 18, INK)
    px(d, 24, 30, 16, 12, BRAND)
    px(d, 56, 28, 16, 12, BRAND)
    bubble(d, 84, 10, 56, 26, WHITE, INK)
    px(d, 92, 18, 26, 3, BRAND)
    px(d, 92, 24, 18, 3, GRAY)


def scene_case3(d):
    """刷单返利：金币堆 + 购物袋"""
    coin(d, 20, 30)
    coin(d, 30, 30)
    coin(d, 25, 22)
    px(d, 58, 16, 40, 34, GOLD)
    px(d, 58, 16, 40, 4, (150, 110, 30))
    px(d, 70, 10, 16, 8, (150, 110, 30))
    px(d, 72, 28, 12, 10, WHITE)
    px(d, 92, 28, 2, 10, BRAND_DARK)
    px(d, 112, 20, 34, 28, TEAL)
    px(d, 112, 20, 34, 4, INK)
    px(d, 124, 32, 10, 8, PAPER)


def scene_case4(d):
    """冒充公检法：警徽 + 法槌"""
    for cx in (22, 60):
        px(d, cx, 8, 28, 28, GOLD)
        px(d, cx + 2, 10, 24, 24, BRAND)
        px(d, cx + 9, 16, 10, 3, GOLD)
        px(d, cx + 12, 14, 4, 14, GOLD)
        px(d, cx + 4, 30, 20, 4, GOLD)
    px(d, 100, 12, 34, 8, (150, 110, 30))
    px(d, 114, 20, 8, 24, (150, 110, 30))
    px(d, 106, 44, 24, 4, INK)


def scene_case5(d):
    """校园贷兼职：钱袋 + 手"""
    px(d, 24, 24, 44, 26, (150, 110, 30))
    px(d, 30, 16, 30, 10, (150, 110, 30))
    px(d, 38, 12, 14, 6, INK)
    px(d, 40, 30, 12, 8, GOLD)
    px(d, 74, 40, 34, 10, (236, 214, 178))
    px(d, 100, 30, 8, 12, (236, 214, 178))
    px(d, 108, 26, 8, 12, (236, 214, 178))
    px(d, 120, 34, 20, 16, BRAND)
    px(d, 124, 38, 12, 3, WHITE)


def scene_tip(d, variant):
    """口诀卡：盾牌 + 三个口诀色块"""
    shield(d, 18, 6, 46, 44, BRAND, BRAND_DARK)
    px(d, 32, 16, 18, 4, WHITE)
    px(d, 38, 12, 6, 20, WHITE)
    colors = [BRAND, GOLD, TEAL] if variant == 0 else (
        [GOLD, TEAL, GREEN] if variant == 1 else [TEAL, GREEN, PURPLE])
    for i, c in enumerate(colors):
        px(d, 82 + i * 24, 12, 18, 18, c)
        px(d, 84 + i * 24, 14, 14, 14, PAPER)
        px(d, 87 + i * 24, 18, 8, 3, c)
        px(d, 87 + i * 24, 23, 5, 2, c)
    px(d, 82, 38, 66, 3, INK)
    px(d, 82, 43, 44, 2, GRAY)


def main():
    os.makedirs(OUT, exist_ok=True)
    cases = [scene_case0, scene_case1, scene_case2,
             scene_case3, scene_case4, scene_case5]
    for i, fn in enumerate(cases):
        img, d = new_canvas(PAPER)
        fn(d)
        px(d, 0, 0, W, 1, INK)
        px(d, 0, H - 1, W, 1, INK)
        img.resize((W * SCALE, H * SCALE), Image.NEAREST).save(
            os.path.join(OUT, "case%d.png" % i))
    for v in range(3):
        img, d = new_canvas(PAPER)
        scene_tip(d, v)
        px(d, 0, 0, W, 1, INK)
        px(d, 0, H - 1, W, 1, INK)
        img.resize((W * SCALE, H * SCALE), Image.NEAREST).save(
            os.path.join(OUT, "tip%d.png" % v))
    print("covers written to", OUT)
    for name in sorted(os.listdir(OUT)):
        print("  ", name, os.path.getsize(os.path.join(OUT, name)), "bytes")


if __name__ == "__main__":
    main()
