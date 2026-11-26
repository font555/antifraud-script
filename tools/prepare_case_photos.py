"""Crop downloaded Burst photos into carousel covers.

中文说明：
    早期版本的案例卡配图用的是 Burst 素材站的照片，脚本把它裁成统一尺寸。
    现在已经换成自绘的像素配图（见 make_case_covers.py / draw_covers9.py），
    本脚本保留下来，用于「需要真实照片素材」的场合（如宣传图、文档配图）。

输入：logs/img/<slug>.jpg（从 Burst CDN 下载的 1200 像素宽原图）
输出：app/src/main/assets/covers/case0..5.jpg（900×506，居中裁切）、tip0..2.jpg

版权说明（Burst License）：
    https://burst.shopify.com/stock-photos/licenses/shopify-some-rights-reserved
    允许商用与非商用、不强制署名，但不得把照片本身作为图片文件转售。
    本作品仍在 App 的案例详情弹窗与 docs/sources.md 里标注了每一位摄影师。

用法：
    py tools/prepare_case_photos.py

依赖：Pillow。
"""

import os  # 标准库：拼路径、建目录

from PIL import Image  # Pillow：图像处理

# ---------------------------------------------------------------- 路径
# ROOT : 项目根目录（本文件在 tools/ 下，往上退两级）
# SRC  : 原图目录（从素材站下载的原图放在这里，不进 git）
# DST  : 输出目录（App 的 covers 资源目录）
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # 项目根目录（本文件在 tools/ 下，往上退两级）
SRC = os.path.join(ROOT, "logs", "img")  # 原图目录
DST = os.path.join(ROOT, "app", "src", "main", "assets", "covers")  # 输出目录

# 每个骗局分类选一张，按 cases.json 里的顺序
SLUGS = [
    "hands-holding-a-phone",                                  # 0 游戏账号
    "woman-holding-her-face-in-both-her-hands",               # 1 AI 换脸
    "brown-eyes-of-a-person-wearing-a-face-mask",             # 2 AI 换脸（身份被冒用）
    "a-small-shopping-cart-with-a-cellphone-inside-it",       # 3 刷单返利
    "student-works-on-laptop",                                # 4 校园贷兼职
    "a-woman-plays-on-her-phone",                             # 5 游戏账号（第二个案例）
]

TARGET_W, TARGET_H = 900, 506          # 案例卡 16:9，尽量保留原图画面
TIP_W, TIP_H = 900, 360                # 口诀卡 2.5:1

# 口诀卡配图：锁（管好验证码）/ 接电话（可疑来电）/ 记笔记（留证据）
# 口诀卡配图：锁（管好验证码）/ 接电话（可疑来电）/ 记笔记（留证据）
# 三个意象分别对应「码 / 话 / 证」，和首页的三组口诀一一对应。
TIPS = [
    "combination-lock-on-fence",
    "woman-smiles-and-holds-a-cellphone-to-her-ear",
    "hands-hold-and-write-in-a-yellow-notebook",
]


def center_crop(img, ratio):
    """把图片按目标比例居中裁切（不拉伸、不变形）。

    参数:
        img  : PIL.Image 对象。
        ratio: 目标宽高比（宽 / 高）。

    返回:
        PIL.Image —— 裁好的新图片。

    算法:
        先比当前比例和目标比例：
          当前更宽 → 高度是瓶颈，裁掉左右两边（左右各裁一半）；
          当前更高 → 宽度是瓶颈，裁掉上下两端（上下各裁一半）。
        用整数除法取整，避免出现半像素导致重采样时抖动。
    """
    w, h = img.size
    if w / h > ratio:                  # 太宽：裁左右
    # 太宽：保持高度，裁左右。left 是居中后的起始 x。
        new_w = int(h * ratio)
        left = (w - new_w) // 2
        return img.crop((left, 0, left + new_w, h))
    new_h = int(w / ratio)             # 太高：裁上下
    # 太高：保持宽度，裁上下。top 是居中后的起始 y。
    top = (h - new_h) // 2
    return img.crop((0, top, w, top + new_h))


def main():
    """脚本入口：批量裁切案例卡与口诀卡，并清掉旧的 PNG 版本。

    参数:
        无。

    返回:
        无。每张图打印「输出文件名 ← 源图名 + 体积」。

    处理流程（每张图）:
        打开并转 RGB（原图可能是 RGBA，存 JPEG 会报错）→ 居中裁切到目标比例
        → LANCZOS 重采样到目标尺寸 → 存 JPEG（quality=82、渐进式）。
    """
    os.makedirs(DST, exist_ok=True)  # 输出目录不存在就先建
    for i, slug in enumerate(SLUGS):  # 逐个素材处理，i 同时当输出文件名的编号
    # 案例卡：按 SLUGS 顺序编号，编号即 categories.json 里的分类顺序。
    # 先 center_crop 再 resize：先裁到正确比例再缩放，比先缩后裁更清晰（少一次重采样）。
        src = os.path.join(SRC, slug + ".jpg")  # 按 slug 找原图（.jpg 后缀固定）
        img = Image.open(src).convert("RGB")  # 转 RGB：原图可能带透明通道，JPEG 不支持
        img = center_crop(img, TARGET_W / TARGET_H)
        img = img.resize((TARGET_W, TARGET_H), Image.LANCZOS)  # LANCZOS：缩小图片时质量最好的重采样算法
        out = os.path.join(DST, "case%d.jpg" % i)
        img.save(out, "JPEG", quality=82, optimize=True, progressive=True)  # quality 82 是体积与画质的折中；progressive 让网络加载时渐进显示
        print("case%d.jpg  <- %s  %d KB" % (i, slug, os.path.getsize(out) // 1024))  # 打印源图名和输出体积，方便核对
    for i, slug in enumerate(TIPS):
    # 口诀卡：同样的流程，只是目标尺寸不同
        src = os.path.join(SRC, slug + ".jpg")
        img = Image.open(src).convert("RGB")
        img = center_crop(img, TIP_W / TIP_H)
        img = img.resize((TIP_W, TIP_H), Image.LANCZOS)
        out = os.path.join(DST, "tip%d.jpg" % i)
        img.save(out, "JPEG", quality=82, optimize=True, progressive=True)
        print("tip%d.jpg   <- %s  %d KB" % (i, slug, os.path.getsize(out) // 1024))

    # 旧的像素风图已经不用了，删掉避免 APK 里塞冗余资源
    # 清理旧的像素风 PNG：这些资源已经不用了，
    # 留着会白白占 APK 体积（每张几十 KB，九张也是几百 KB）。
    for i in range(max(len(SLUGS), len(TIPS))):  # 两种图的最大编号决定要清理多少个旧文件
        for name in ("case%d.png" % i, "tip%d.png" % i):
            stale = os.path.join(DST, name)
            if os.path.exists(stale):  # 存在才删，不存在不报错（重复运行是安全的）
                os.remove(stale)
                print("removed stale", name)


if __name__ == "__main__":
    main()
