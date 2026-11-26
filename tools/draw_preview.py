"""画案例配图样张：把「仿真实截图」和「像素风」融合，先出 2 张给用户看。

为什么先出样张：
    配图是首页轮播的门面，风格一旦定错，后面 9 张都要返工。
    所以先做 2 张（一张聊天、一张视频通话）给队友确认，定稿后再批量生成。

输出：
    logs/art/preview-chat.png、logs/art/preview-call.png（900×506，和卡片图片区同比例）

用法：
    py tools/draw_preview.py

依赖：Pillow + 仓库自带的像素字体。
"""

import os  # 标准库：拼路径、建目录

from PIL import Image, ImageDraw, ImageFont  # Pillow 三件套

# ---------------------------------------------------------------- 路径与画布
# ROOT : 项目根目录（本文件在 tools/ 下，往上退两级）
# FONT : 像素字体（App 同款）
# OUT  : 样张输出目录（不进 APK）
# W/H  : 画布尺寸，与案例卡图片区同比例，保证放进轮播不裁切
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # 项目根目录（本文件在 tools/ 下，往上退两级）
FONT = os.path.join(ROOT, "app", "src", "main", "assets", "fonts", "fusion_pixel.ttf")  # 像素字体（与 App 同款）
OUT = os.path.join(ROOT, "logs", "art")  # 样张输出目录（不进 APK）

W, H = 900, 506  # 画布宽
PAPER = (246, 239, 224)  # 画布高（900×506，与卡片图片区同比例）
INK = (43, 43, 51)  # 纸底米黄
# ---------------------------------------------------------------- 配色
# INK_SOFT 是比 INK 浅一点的墨色，用于需要「不是纯黑」的描边或文字层次。
INK_SOFT = (68, 64, 60)  # 墨色
BRAND = (228, 87, 46)  # 次要墨色
BRAND_DARK = (191, 61, 28)  # 品牌砖红
GOLD = (233, 180, 76)  # 深砖红
TEAL = (63, 127, 166)  # 金色
GRAY = (150, 145, 135)  # 青色
GRAY_LIGHT = (236, 232, 222)  # 灰色
WHITE = (255, 255, 255)  # 浅灰米色
  # 白色

def f(size):
    """按字号加载像素字体。

    参数:
        size: 字号（像素）。

    返回:
        ImageFont.FreeTypeFont。
    """
    return ImageFont.truetype(FONT, size)


def px(d, x, y, w, h, c):
    """画实心矩形（像素风基本笔刷）。

    参数:
        d: 画布
        x, y: 左上角
        w, h: 宽高
        c: 颜色

    说明:
        右下角减 1：Pillow 矩形是闭区间，不减会多画一个像素。
    """
    d.rectangle([x, y, x + w - 1, y + h - 1], fill=c)


def tx(d, x, y, s, size, c=INK):
    """写一行左上角对齐的文字。

    参数:
        d: 画布
        x, y: 左上角
        s: 文字
        size: 字号
        c: 颜色（默认墨色）
    """
    d.text((x, y), s, font=f(size), fill=c)


def status_bar(d):
    """画手机状态栏：深色条 + 时间 + 信号电量。

    参数:
        d: 画布

    说明:
        时间写死 20:41，与其它配图保持一致，看起来像同一台手机的截图。
    """
    px(d, 0, 0, W, 30, INK)  # 状态栏高度 30 像素
    tx(d, 14, 5, "20:41", 16, WHITE)
    tx(d, W - 150, 5, "5G   96%", 16, WHITE)


def avatar(d, x, y, size, color, ch):
    """画像素头像（方块底 + 一个首字）。

    参数:
        d: 画布
        x, y: 头像左上角
        size: 边长
        color: 底色
        ch: 头像里的字
    """
    px(d, x, y, size, size, INK)
    px(d, x + 2, y + 2, size - 4, size - 4, color)
    tw = d.textlength(ch, font=f(int(size * 0.55)))  # 头像里的字按边长 0.55 倍取字号
    d.text((x + (size - tw) / 2, y + size * 0.18), ch, font=f(int(size * 0.55)), fill=WHITE)


def bubble_left(d, x, y, w, h, text_s, size=19, fill=WHITE):
    """画左侧（对方）气泡：白底 + 左上角一个小三角指向头像。

    参数:
        d: 画布
        x, y: 气泡左上角
        w, h: 宽高
        text_s: 文字
        size: 字号
        fill: 填充色，默认白色
    """
    # 小三角
    px(d, x - 8, y + 14, 8, 10, INK)  # 小三角：贴在气泡左上角外侧
    px(d, x, y, w, h, INK)
    px(d, x + 2, y + 2, w - 4, h - 4, fill)
    tx(d, x + 14, y + (h - size) // 2 - 2, text_s, size, INK)


def bubble_right(d, x, y, w, h, text_s, size=19):
    """画右侧（我方）气泡：砖红底 + 右上角小三角，文字居中。

    参数:
        d: 画布
        x, y: 气泡左上角
        w, h: 宽高
        text_s: 文字
        size: 字号

    说明:
        文字用居中而不是左对齐，是因为我方消息都很短（「已转 200」），
        居中看起来更像真实的聊天软件。
    """
    px(d, x + w, y + 14, 8, 10, INK)  # 小三角：贴在气泡右上角外侧
    px(d, x, y, w, h, INK)
    px(d, x + 2, y + 2, w - 4, h - 4, BRAND)
    tw = d.textlength(text_s, font=f(size))
    tx(d, x + (w - tw) / 2, y + (h - size) // 2 - 2, text_s, size, WHITE)


def chat_case():
    """样张一：游戏账号诈骗的聊天界面（一步步入套的全过程）。

    参数:
        无。

    返回:
        无。直接把图存到 logs/art/preview-chat.png。

    画面思路:
        状态栏 → 聊天顶栏（返回箭头 + 卖家头像 + 在线状态）→ 商品卡 →
        四条消息（定金已付 → 又让交解冻金 → 又付 → 被删除）→ 底部输入栏。
        最后加一个「示意图」角标，明确告知这不是真实截图。
    """
    img = Image.new("RGB", (W, H), PAPER)  # 米黄纸底，和 App 的聊天页底色一致
    d = ImageDraw.Draw(img)
    status_bar(d)

    # 聊天顶栏
    px(d, 0, 30, W, 76, INK)  # 聊天顶栏：深色 76 像素高
    tx(d, 16, 52, "←", 26, WHITE)
    avatar(d, 60, 44, 48, BRAND, "卖")
    tx(d, 122, 50, "卖家 · 游戏账号专营", 22, WHITE)
    tx(d, 122, 78, "在线 · 已实名", 15, (190, 186, 176))
    tx(d, W - 56, 52, "···", 24, WHITE)

    # 商品条
    # 商品卡：左侧缩略图占位 + 名称 + 价格 + 原价对比 + 库存，
    # 「原价 3000 / 现价 888」这种价差就是钓鱼的核心诱饵。
    px(d, 16, 116, W - 32, 76, WHITE)  # 商品条：白底 76 高，下方所有内容从这个 y 开始往下排
    px(d, 26, 126, 56, 56, GRAY_LIGHT)
    tx(d, 32, 140, "全皮", 18, GRAY)
    tx(d, 96, 124, "全皮账号 · 满级 · 可换绑", 21, INK)
    tx(d, 96, 152, "¥888", 22, BRAND_DARK)
    tx(d, 190, 156, "原价 3000", 15, GRAY)
    tx(d, W - 130, 156, "库存 1", 15, TEAL)

    # 聊天记录
    # 聊天记录：ty 是不断往下推的 y 坐标，每画完一条就 += 自己的高度。
    # 「已读」两个字单独画在右侧气泡下方，增加真实聊天软件的细节。
    ty = 206  # 聊天记录从 206 开始，给商品条留够空间
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
    # 系统提示条：居中灰底，表现「对方已将你删除」这个结局
    px(d, W // 2 - 150, ty, 300, 30, GRAY_LIGHT)
    d.text((W // 2 - 132, ty + 6), "对方已将你删除", font=f(15), fill=GRAY)
    ty += 42
    bubble_left(d, 16, ty, 470, 42, "最后交 3000 保证金，马上把号给你", 18, (252, 232, 222))
    ty += 58

    # 底部输入栏
    # 底部输入栏：输入框 + 加号 + 语音按钮
    px(d, 0, H - 62, W, 62, (240, 236, 228))  # 底部输入栏：62 像素高，固定在画布底部
    px(d, 16, H - 48, W - 130, 34, WHITE)
    tx(d, 30, H - 42, "发送消息…", 17, GRAY)
    px(d, W - 96, H - 50, 34, 34, WHITE)
    tx(d, W - 86, H - 46, "＋", 22, INK)
    px(d, W - 52, H - 50, 34, 34, WHITE)
    tx(d, W - 44, H - 46, "🎙", 20, INK)

    # 示意图角标
    # 右上角「示意图」角标：必须保留，避免被误当成真实截图传播
    px(d, W - 96, 40, 82, 26, (40, 38, 44))  # 示意图角标：右上角深色小方块
    tx(d, W - 88, 44, "示意图", 14, WHITE)

    img.save(os.path.join(OUT, "preview-chat.png"))  # 存到 logs/art，供确认后再拷进 assets


def call_case():
    """样张二：AI 换脸视频通话界面。

    参数:
        无。

    返回:
        无。直接把图存到 logs/art/preview-call.png。

    画面思路:
        深色通话底 + 像素人脸（头发/脸/双眼/鼻/嘴/上衣）+ 通话计时 + 来电人 +
        字幕「妈，培训班要交 3.5 万」+ 底部三个按钮（挂断红色）。
        人脸故意画得「像但不像」，正好表达 AI 换脸的特征。
    """
    img = Image.new("RGB", (W, H), (36, 34, 40))  # 视频通话用深色底
    d = ImageDraw.Draw(img)
    status_bar(d)

    # 视频画面
    px(d, 0, 30, W, 372, (150, 168, 158))  # 视频画面：从状态栏下方一直铺到按钮区上方
    # 人物：头发/脸/五官/肩
    # 人物：全部由矩形拼成。
    # 顺序很重要——先头发、再脸、再五官，后画的会盖住先画的。
    cx = W // 2  # 人物居中对齐（cx 是画面中线）
    px(d, cx - 110, 78, 220, 40, (70, 52, 44))          # 头顶
    px(d, cx - 128, 108, 256, 56, (70, 52, 44))         # 头发两侧
    px(d, cx - 92, 96, 184, 208, (240, 210, 172))       # 脸
    px(d, cx - 56, 150, 22, 22, INK)                    # 左眼
    px(d, cx + 34, 150, 22, 22, INK)                    # 右眼
    px(d, cx - 8, 186, 16, 12, (214, 170, 150))         # 鼻
    px(d, cx - 34, 216, 68, 14, BRAND_DARK)             # 嘴
    px(d, cx - 150, 300, 300, 102, (86, 96, 120))       # 上衣

    # 通话信息
    # 顶部两块半透明深色标签：左边通话时长、右边来电人名字
    px(d, 16, 44, 268, 34, (24, 22, 28))
    tx(d, 28, 50, "视频通话  00:07", 18, WHITE)
    px(d, W - 120, 44, 104, 34, (24, 22, 28))
    tx(d, W - 108, 50, "女儿", 18, WHITE)

    # 字幕
    # 字幕条：黑底白字，模仿视频通话的实时字幕
    px(d, 40, 318, W - 80, 46, (20, 18, 24))
    tx(d, 60, 328, "妈，培训班要交 3.5 万，转给“王老师”", 20, WHITE)

    # 底部按钮
    # 底部按钮：挂断红色、其余深灰，文字按实际宽度居中
    px(d, 0, 402, W, 104, (26, 24, 30))  # 底部按钮区：深色 104 像素高
    labels = [("静音", 150), ("挂断", 450), ("免提", 750)]
    for name, x in labels:
        color = (196, 58, 58) if name == "挂断" else (58, 56, 64)
        px(d, x - 44, 428, 88, 48, color)
        tw = d.textlength(name, font=f(17))
        tx(d, x - tw / 2, 440, name, 17, WHITE)

    # 示意图角标
    # 「示意图」角标，AI 换脸题材尤其要标清楚
    px(d, W - 96, 40, 82, 26, (40, 38, 44))
    tx(d, W - 88, 44, "示意图", 14, WHITE)

    img.save(os.path.join(OUT, "preview-call.png"))  # 存样张


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)  # 输出目录不存在就先建
    chat_case()
    call_case()
    print("written to", OUT)
