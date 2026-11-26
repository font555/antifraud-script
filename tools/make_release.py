# -*- coding: utf-8 -*-
"""创建 GitHub Release 并上传 APK 作为下载附件。"""
import json
import os
import subprocess
import time
import urllib.request

ROOT = r"C:\Users\Lecoo\Desktop\antifraud-script"
REPO = "font555/antifraud-script"
API = "https://api.github.com"
UPLOADS = "https://uploads.github.com"
APK = os.path.join(ROOT, "dist", "antifraud-v5.7-debug.apk")
TAG = "v5.7"


def token():
    out = subprocess.run(["git", "credential", "fill"],
                         input="protocol=https\nhost=github.com\n\n",
                         capture_output=True, text=True, cwd=ROOT)
    for line in out.stdout.splitlines():
        if line.startswith("password="):
            return line[len("password="):]
    raise SystemExit("拿不到 token")


TOKEN = token()
H = {
    "Authorization": "token " + TOKEN,
    "Accept": "application/vnd.github+json",
    "User-Agent": "dsh",
}


def call(method, url, data=None, raw=None, attempts=3, timeout=300):
    body = raw if raw is not None else (json.dumps(data).encode() if data is not None else None)
    last = None
    for i in range(attempts):
        try:
            req = urllib.request.Request(url, data=body, method=method, headers=dict(
                H, **{"Content-Type": "application/octet-stream" if raw is not None else "application/json"}))
            with urllib.request.urlopen(req, timeout=timeout) as r:
                txt = r.read().decode("utf-8")
                return json.loads(txt) if txt.strip() else {}
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(3 * (i + 1))
            print("  重试", i + 1, type(e).__name__)
    raise SystemExit("失败: " + str(last))


body = """## 反诈剧本杀 v4.3

**像素风反诈教育应用**：把真实诈骗案件改编成可以"走一遍"的剧本，每一步都有损失账本，
结束后逐节点复盘，并对照公开报道原文。

### 这一版改了什么
- 首页轮播拉高并加入配图：6 张案例卡按场景配图、3 张口诀卡配图，右上角标注「示意图」，图片出处可查
- 新手引导改为 5 步：轮播 → 书架 → 剧本列表 → 搜索行加号 → 我的页设置；书架与列表完整高亮，切页只定位一次，去掉"点遮罩跳步"
- 剧本页加号移到搜索框右侧（列表上方），不再遮挡剧本；列表底部取消预留留白
- 设置页精简为「字体样式 + 字号大小」，去掉与「我的」页重复的清除记录/关于
- 修复处置支线幕号错乱（第 5 幕后跳回第 3 幕）

### 内容
- 6 类骗局、17 个剧本、405 个剧情节点、71 条可核验来源
- 书架 → 翻书 → 逐条聊天 → 损失账本 → 复盘 → 来源

### 技术
- Android 原生 Java，**零第三方依赖**
- **零权限、零联网、不收集任何个人信息**
- APK 约 2.9 MB

### 安装
下载下方 `antifraud-v5.7-debug.apk`，安卓手机允许"安装未知来源应用"后安装即可。

> 教学演示作品，案例据公开报道改编，人物已匿名、平台名已虚构，与任何机构无隶属关系。
"""
print("创建 release…")
rel = call("POST", f"{API}/repos/{REPO}/releases", {
    "tag_name": TAG,
    "name": "反诈剧本杀 v5.7",
    "body": body,
    "draft": False,
    "prerelease": False,
})
print("release id:", rel["id"], rel["html_url"])

size = os.path.getsize(APK)
print("上传 APK:", round(size / 1024 / 1024, 2), "MB")
data = open(APK, "rb").read()
asset = call("POST",
             f"{UPLOADS}/repos/{REPO}/releases/{rel['id']}/assets?name=antifraud-v5.7-debug.apk",
             raw=data)
print("asset:", asset["browser_download_url"])
print("RELEASE_URL:", rel["html_url"])
print("DOWNLOAD_URL:", asset["browser_download_url"])
