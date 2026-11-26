# -*- coding: utf-8 -*-
"""创建 GitHub Release 并上传 APK 作为下载附件（一次性发布脚本）。

场景：
    比赛提交前要把打好的 APK 放到 GitHub Release 页面上，评委点开链接就能下载安装。
    手工在网页上点太慢也容易漏，所以用这个脚本一次性完成「建 release + 传 APK」。

用法（在项目根目录执行）：
    py tools/make_release.py

依赖：
    只用标准库。另外需要本机 git 凭据管理器里存过 GitHub token。

前置条件：
    dist/antifraud-v6.1-debug.apk 已经用 gradle assembleDebug 打好。
    如果换版本号，要同时改 APK / TAG 两个常量，以及下面 body 里的文案和附件名。
"""
import json
import os
import subprocess
import time
import urllib.request

# ---------------------------------------------------------------- 配置
# ROOT    : 本地仓库根目录
# REPO    : 要发 release 的仓库全名
# API     : GitHub REST API 根地址（建 release 用）
# UPLOADS : 上传附件的专用域名（和 API 不是同一个，必须分开）
# APK     : 本地待上传的安装包路径
# TAG     : release 对应的 tag 名（会自动创建）
ROOT = r"C:\Users\Lecoo\Desktop\antifraud-script"  # 本地仓库根目录
REPO = "font555/antifraud-script"  # 目标仓库
API = "https://api.github.com"  # REST API 根地址
UPLOADS = "https://uploads.github.com"  # 附件上传专用域名
APK = os.path.join(ROOT, "dist", "antifraud-v6.1-debug.apk")  # 待上传的 APK
TAG = "v6.1"  # release 的 tag 名


def token():
    """从本机 git 凭据管理器取 GitHub token。

    返回:
        str —— token 字符串。

    异常:
        以 SystemExit 退出 —— 凭据管理器里没有 GitHub 凭据时无法继续。
    """
    out = subprocess.run(["git", "credential", "fill"],
                         input="protocol=https\nhost=github.com\n\n",  # 让 git 自己去凭据管理器里查 GitHub 密码
                         capture_output=True, text=True, cwd=ROOT)
    for line in out.stdout.splitlines():  # 逐行找 password= 开头的字段
        if line.startswith("password="):
            return line[len("password="):]
    raise SystemExit("拿不到 token")
    # git credential fill 会把凭据以 key=value 的形式打到标准输出，
    # 这里只挑 password 那一行，等号右边就是 token。


TOKEN = token()
# token 拿到后放进模块级常量，后面所有请求直接引用。
# 注意：H 里故意不带 Content-Type——建 release 要 application/json，
# 传二进制附件要 application/octet-stream，所以让 call() 按情况临时补。
H = {
    "Authorization": "token " + TOKEN,
    "Accept": "application/vnd.github+json",
    "User-Agent": "dsh",
}


def call(method, url, data=None, raw=None, attempts=3, timeout=300):
    """调用一次 GitHub API，失败自动重试。

    参数:
        method  : HTTP 方法（GET / POST / PATCH / DELETE）。
        url     : 完整的请求地址（不像 push_snapshot 那样拼 path，因为要用两个不同的域名）。
        data    : JSON 请求体，会自动 json.dumps。
        raw     : 原始字节请求体（传 APK 二进制时用），此时 Content-Type 换成 octet-stream。
        attempts: 最多重试次数。
        timeout : 单次请求超时秒数。上传 APK 比较慢，默认给了 300 秒。

    返回:
        dict —— 响应 JSON；响应体为空时返回空 dict（DELETE 请求就是这种情况）。

    异常:
        以 SystemExit 退出 —— 重试用尽仍未成功。
    """
    body = raw if raw is not None else (json.dumps(data).encode() if data is not None else None)  # 二进制请求走 octet-stream，JSON 请求走 application/json
    # 请求体三选一：raw 优先（二进制），其次 data（JSON），都没有就不带 body。
    last = None
    for i in range(attempts):
    # 重试等待用 3 秒 × 次数 递增：3s、6s、9s，最多 3 次。
    # GitHub 偶尔 502，多半几十秒内自愈，这个节奏够用又不会卡太久。
        try:
            req = urllib.request.Request(url, data=body, method=method, headers=dict(
                H, **{"Content-Type": "application/octet-stream" if raw is not None else "application/json"}))
            with urllib.request.urlopen(req, timeout=timeout) as r:
                txt = r.read().decode("utf-8")  # 响应体统一按 UTF-8 解，再交给 json 解析
        # 有些接口（比如 DELETE）成功时响应体是空的，
        # 直接 json.loads("") 会抛异常，所以先判空。
                return json.loads(txt) if txt.strip() else {}
        except Exception as e:  # noqa: BLE001
            last = e
            time.sleep(3 * (i + 1))  # 线性退避，给 GitHub 一点恢复时间
            print("  重试", i + 1, type(e).__name__)
    raise SystemExit("失败: " + str(last))


# release 页面的正文（Markdown）。这段文字会直接显示给下载的人看，所以把
# 「是什么 / 这一版改了什么 / 内容规模 / 技术特点 / 怎么安装 / 版权声明」都写清楚。
# 注意文案里的版本号和下面的 TAG、APK 文件名要保持一致，否则页面上的链接名会对不上。
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
下载下方 `antifraud-v6.1-debug.apk`，安卓手机允许"安装未知来源应用"后安装即可。

> 教学演示作品，案例据公开报道改编，人物已匿名、平台名已虚构，与任何机构无隶属关系。
"""
# ---------------------------------------------------------------- 主流程
# 创建 release → 读 APK 大小 → 上传附件 → 打印两个关键 URL。
# 这里没有包 main() 函数，因为脚本是一次性的，直接顺序执行最直观。
print("创建 release…")
rel = call("POST", f"{API}/repos/{REPO}/releases", {  # 创建 release（tag 不存在时会自动创建）
# draft=False 表示立即可见（不存草稿），prerelease=False 表示不是预发布版。
# tag_name 指定的 tag 如果不存在，GitHub 会在最新提交上自动创建。
    "tag_name": TAG,
    "name": "反诈剧本杀 v6.1",
    "body": body,
    "draft": False,
    "prerelease": False,
})
print("release id:", rel["id"], rel["html_url"])

# 上传前先报一下体积，方便判断是不是打错了包（正常 debug 包约 2~3 MB）。
size = os.path.getsize(APK)  # 先看一眼本地 APK 多大
print("上传 APK:", round(size / 1024 / 1024, 2), "MB")
data = open(APK, "rb").read()  # 整个文件读进内存（几 MB，可接受）
asset = call("POST",
# 附件上传走 uploads.github.com，URL 里的 name 参数决定下载时显示的文件名。
# 这里直接读成 bytes 走 raw 分支，Content-Type 会自动变成 octet-stream。
             f"{UPLOADS}/repos/{REPO}/releases/{rel['id']}/assets?name=antifraud-v6.1-debug.apk",
             raw=data)
print("asset:", asset["browser_download_url"])
print("RELEASE_URL:", rel["html_url"])
print("DOWNLOAD_URL:", asset["browser_download_url"])
