# -*- coding: utf-8 -*-
"""生成《反诈剧本杀》技术路线图（像素风 PNG，可直接放进 PPT / 作品说明书）。"""
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = r"C:\Users\Lecoo\Desktop\antifraud-script"
OUT = os.path.join(ROOT, "artifacts", "技术路线图.png")
FONT = os.path.join(ROOT, "app", "src", "main", "assets", "fonts", "fusion_pixel.ttf")

S = 2
W, H = 1800, 1240

PAPER = (246, 231, 198)
CARD = (255, 246, 224)
DARK = (74, 50, 34)
LINE = (216, 190, 146)
INK = (59, 42, 26)
INK2 = (107, 82, 51)
INK3 = (156, 133, 99)
BRAND = (228, 87, 46)
WHITE = (255, 250, 238)

C_BLUE = (63, 127, 166)
C_ORANGE = (217, 140, 31)
C_PLUM = (107, 76, 138)
C_GREEN = (78, 123, 58)
C_RED = (192, 57, 43)


def f(size):
    return ImageFont.truetype(FONT, size * S)


def text(d, xy, s, size, color, anchor="la"):
    d.text((xy[0] * S, xy[1] * S), s, font=f(size), fill=color, anchor=anchor)


def rect(d, box, fill=None, outline=None, width=3):
    x0, y0, x1, y1 = [v * S for v in box]
    d.rectangle([x0, y0, x1, y1], fill=fill, outline=outline, width=width * S)


img = Image.new("RGB", (W * S, H * S), PAPER)
d = ImageDraw.Draw(img)

rect(d, (14, 14, W - 14, H - 14), None, DARK, 4)
rect(d, (26, 26, W - 26, H - 26), None, LINE, 2)

# ============ 标题 ============
text(d, (60, 44), "反诈剧本杀 · 技术路线图", 50, BRAND)
text(d, (62, 112), "世界职业院校技能大赛 · 新一代信息技术赛道 · 中职组　|　Android 原生 Java　·　零第三方依赖", 21, INK2)
d.line([(60 * S, 158 * S), ((W - 60) * S, 158 * S)], fill=LINE, width=2 * S)

# ============ 一、开发路线 ============
text(d, (60, 178), "一、开发路线：5 个阶段 / 12 天", 30, INK)

phases = [
    (C_BLUE, "阶段 1", "需求与选题", "D1–D2", ["调研 6 类高发骗局", "收集 71 条公开报道", "确定「走一遍骗局」", "产品形态：一本像素书"]),
    (C_ORANGE, "阶段 2", "技术选型", "D3", ["原生 Java + XML，零依赖", "剧本用 JSON 驱动", "Canvas 手绘像素书", "离线优先、零权限"]),
    (C_PLUM, "阶段 3", "架构与数据", "D4–D6", ["model / engine / ui 分层", "剧本 Schema + 校验脚本", "路径记录器 PathRecorder", "本地存档 SharedPreferences"]),
    (C_GREEN, "阶段 4", "迭代实现", "D7–D10", ["书架 → 翻书 → 聊天", "损失账本 + 逐节点复盘", "剧本工坊 / 分享海报", "小组件 / 新手引导 / 日志"]),
    (C_RED, "阶段 5", "测试与交付", "D11–D12", ["剧本校验 0 错误", "真机全流程回归", "字体 / 字号 / 大屏适配", "打包装箱 + Release 发布"]),
]

CARD_W, CARD_H, GAP = 318, 300, 33
TOP = 224
x = 60
for i, (color, tag, name, span, items) in enumerate(phases):
    rect(d, (x, TOP, x + CARD_W, TOP + CARD_H), CARD, DARK, 3)
    # 顶部色条（加高，容纳两行标题）
    rect(d, (x + 6, TOP + 6, x + CARD_W - 6, TOP + 84), color, None, 0)
    text(d, (x + 18, TOP + 16), tag, 18, WHITE)
    text(d, (x + CARD_W - 18, TOP + 16), span, 17, WHITE, anchor="ra")
    text(d, (x + 18, TOP + 48), name, 25, WHITE)
    yy = TOP + 104
    for it in items:
        text(d, (x + 20, yy), "·", 18, color)
        text(d, (x + 36, yy), it, 17, INK)
        yy += 46
    if i < len(phases) - 1:
        ax = x + CARD_W + GAP / 2
        d.polygon([((ax - 7) * S, (TOP + 150) * S), ((ax + 7) * S, (TOP + 165) * S),
                   ((ax - 7) * S, (TOP + 180) * S)], fill=DARK)
    x += CARD_W + GAP

# ============ 二、技术架构 ============
text(d, (60, 566), "二、技术架构：4 层，自下而上", 30, INK)

layers = [
    ("界面层", "Java + XML + Canvas",
     "书架 / 翻书动效 / 逐条聊天 / 损失账本 / 复盘 / 剧本工坊 / 分享海报 / 小组件", C_RED, 612),
    ("引擎层", "engine/*",
     "ScriptRepository 读剧本 · ScriptStore 用户剧本 · PathRecorder 记步 · UserStore · SettingsStore · AppLog", C_PLUM, 712),
    ("数据层", "JSON + 本机存储",
     "6 类 17 本 / 405 节点 / 71 条来源；用户剧本 filesDir/scripts；进度存 SharedPreferences", C_BLUE, 812),
    ("资源层", "零第三方依赖",
     "Fusion Pixel Font（MIT）· Canvas 手绘图标 · Gradle 构建 · 全部离线可用", C_GREEN, 912),
]
for name, tech, desc, color, y in layers:
    rect(d, (60, y, W - 60, y + 88), WHITE, DARK, 3)
    rect(d, (66, y + 6, 262, y + 82), color, None, 0)
    text(d, (80, y + 16), name, 23, WHITE)
    text(d, (80, y + 50), tech, 15, WHITE)
    text(d, (284, y + 22), desc, 18, INK)

# ============ 三、关键决策 ============
text(d, (60, 1032), "三、关键决策", 30, INK)
decisions = [
    ("零第三方依赖", "2.55 MB 单包"),
    ("零权限 · 零联网", "不收集个人信息"),
    ("剧本即数据", "加剧本不改代码"),
    ("每节点可溯源", "71 条来源可打开"),
    ("Canvas 手绘", "无素材版权风险"),
]
cx = 60
for title, sub in decisions:
    rect(d, (cx, 1074, cx + 318, 1148), CARD, LINE, 2)
    text(d, (cx + 16, 1086), title, 20, INK)
    text(d, (cx + 16, 1117), sub, 15, INK3)
    cx += 330

text(d, (60, 1168), "交付：APK 2.55 MB　·　6 类 17 个剧本　·　405 个剧情节点　·　71 条可核验来源　·　本地 31 个提交", 17, INK2)

img.resize((W, H), Image.LANCZOS).save(OUT)
print("saved", OUT)
