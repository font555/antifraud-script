"""Crop downloaded Burst photos into carousel covers.

Input : logs/img/<slug>.jpg   (1200px wide originals downloaded from Burst CDN)
Output: app/src/main/assets/covers/case0..5.jpg  (900x310, center-cropped)

Burst license (https://burst.shopify.com/stock-photos/licenses/shopify-some-rights-reserved):
free for commercial and non-commercial use, attribution not required,
must not resell the photos as photo files. We still credit every photographer
inside the app (case detail dialog) and in docs/sources.md.
"""

import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "logs", "img")
DST = os.path.join(ROOT, "app", "src", "main", "assets", "covers")

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
TIPS = [
    "combination-lock-on-fence",
    "woman-smiles-and-holds-a-cellphone-to-her-ear",
    "hands-hold-and-write-in-a-yellow-notebook",
]


def center_crop(img, ratio):
    w, h = img.size
    if w / h > ratio:                  # 太宽：裁左右
        new_w = int(h * ratio)
        left = (w - new_w) // 2
        return img.crop((left, 0, left + new_w, h))
    new_h = int(w / ratio)             # 太高：裁上下
    top = (h - new_h) // 2
    return img.crop((0, top, w, top + new_h))


def main():
    os.makedirs(DST, exist_ok=True)
    for i, slug in enumerate(SLUGS):
        src = os.path.join(SRC, slug + ".jpg")
        img = Image.open(src).convert("RGB")
        img = center_crop(img, TARGET_W / TARGET_H)
        img = img.resize((TARGET_W, TARGET_H), Image.LANCZOS)
        out = os.path.join(DST, "case%d.jpg" % i)
        img.save(out, "JPEG", quality=82, optimize=True, progressive=True)
        print("case%d.jpg  <- %s  %d KB" % (i, slug, os.path.getsize(out) // 1024))
    for i, slug in enumerate(TIPS):
        src = os.path.join(SRC, slug + ".jpg")
        img = Image.open(src).convert("RGB")
        img = center_crop(img, TIP_W / TIP_H)
        img = img.resize((TIP_W, TIP_H), Image.LANCZOS)
        out = os.path.join(DST, "tip%d.jpg" % i)
        img.save(out, "JPEG", quality=82, optimize=True, progressive=True)
        print("tip%d.jpg   <- %s  %d KB" % (i, slug, os.path.getsize(out) // 1024))

    # 旧的像素风图已经不用了，删掉避免 APK 里塞冗余资源
    for i in range(max(len(SLUGS), len(TIPS))):
        for name in ("case%d.png" % i, "tip%d.png" % i):
            stale = os.path.join(DST, name)
            if os.path.exists(stale):
                os.remove(stale)
                print("removed stale", name)


if __name__ == "__main__":
    main()
