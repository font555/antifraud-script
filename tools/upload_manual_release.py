# -*- coding: utf-8 -*-
"""把《代码研读手册》的 PDF / DOCX 作为 Release 附件上传（GitHub API）。

场景：
    手册由 tools/build_manual.py 生成到 docs/manual/ 下，体积比较大（带截图），
    直接放进 git 仓库会把仓库撑爆，所以改成挂到已有的 Release 上作为附件。

用法（在项目根目录执行）：
    py tools/upload_manual_release.py
        不需要参数：要传哪些文件、传到哪个 tag，都写在下面的 FILES / TAG 里。

依赖：
    只用标准库 + 本机 git 凭据管理器里的 GitHub token。
    手册文件必须先跑过 build_manual.py，否则会报「找不到文件」。

行为特点：
    可重复执行。同名附件会先删旧的再传新的，所以改完手册直接重跑即可。
"""

import json
import os
import subprocess
import time
import urllib.parse
import urllib.request

ROOT = r"C:\Users\Lecoo\Desktop\antifraud-script"  # 本地仓库根目录
REPO = "font555/antifraud-script"  # 目标仓库
API = "https://api.github.com"  # REST API 根地址
UPLOADS = "https://uploads.github.com"  # 附件上传专用域名
TAG = "v5.4"  # 目标 release 的 tag

# 附件名里带版本号，这样下载列表里一眼能看出是哪一版手册，
# 也避免和旧的同名附件混淆（虽然下面会先删旧的）。
FILES = [
    ("docs/manual/反诈剧本杀-代码研读手册.pdf", "antifraud-manual-v5.4.pdf", "application/pdf"),
    ("docs/manual/反诈剧本杀-代码研读手册.docx",
     "antifraud-manual-v5.4.docx",
     "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
]


def token():
    """从本机 git 凭据管理器取 GitHub token。

    返回:
        str —— token。

    异常:
        以 SystemExit 退出 —— 凭据管理器里没有 GitHub 凭据时无法继续。
    """
    out = subprocess.run(
        ["git", "credential", "fill"],
        input="protocol=https\nhost=github.com\n\n",  # 让 git 自己去凭据管理器里查 GitHub 密码
        capture_output=True, text=True, cwd=ROOT)
    for line in out.stdout.splitlines():  # 逐行找 password= 开头的字段
        if line.startswith("password="):
            return line[len("password="):]
    # 只关心 password= 这一行，等号右边就是 token。
    raise SystemExit("无法从凭据管理器拿到 token")


TOKEN = token()
# REST API 用的固定请求头（建/查/删附件都走 JSON 接口）。
HEADERS = {
    "Authorization": "token " + TOKEN,
    "Accept": "application/vnd.github+json",
    "User-Agent": "dsh",
    "Content-Type": "application/json",
}


def api(method, path, data=None, attempts=5, timeout=180):
    """调用一次 GitHub REST API，带自动重试。

    参数:
        method  : HTTP 方法（GET / DELETE 等）。
        path    : 接口路径，拼在 API 常量后面。
        data    : JSON 请求体；None 表示不带 body。
        attempts: 最多尝试次数，默认 5 次。
        timeout : 单次请求超时秒数。

    返回:
        dict —— 解析后的响应 JSON。

    异常:
        以 SystemExit 退出 —— 重试用尽仍然失败。
    """
    body = json.dumps(data).encode("utf-8") if data is not None else None  # 没有请求体时传 None，urllib 就不会带 body
    last = None
    # 每次失败等 4 秒 × 已试次数：4s / 8s / 12s / 16s，最多 5 次，总共约 40 秒。
    # 上传手册这种大附件偶尔会超时，但重试基本都能过。
    for i in range(attempts):
        try:
            req = urllib.request.Request(API + path, data=body, method=method, headers=HEADERS)  # 发请求并解析 JSON 响应
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except Exception as e:  # noqa: BLE001
            last = e
            print("  重试 %d/%d（%s）" % (i + 1, attempts, type(e).__name__))
            time.sleep(4 * (i + 1))
    raise SystemExit("请求失败: %s %s -> %s" % (method, path, last))


def upload(release_id, path, name, mime, attempts=4):
    """把单个文件上传成 release 附件。

    参数:
        release_id : 目标 release 的数字 id（先用 tag 查出来）。
        path       : 文件在仓库里的相对路径。
        name       : 上传后在 GitHub 上显示的附件文件名。
        mime       : 附件 MIME 类型（PDF / DOCX 各不一样，写错浏览器会当附件下载）。
        attempts   : 最多上传尝试次数。

    返回:
        dict —— 附件信息（含 browser_download_url / size 等）。

    异常:
        以 SystemExit 退出 —— 上传重试用尽仍失败。
    """
    full = os.path.join(ROOT, path)
    with open(full, "rb") as f:  # 按二进制读，PDF/DOCX 都是二进制格式
        data = f.read()
    print("上传 %s（%.1f MB）" % (name, len(data) / 1024 / 1024))  # 先报一下体积，几 MB 的文件传起来要一会儿
    # 整个文件读进内存再传。手册是几 MB 级别，一次性读没问题，
    # 好处是可以直接算长度、也方便重试（字节不变，不必重复读盘）。

    url = "%s/repos/%s/releases/%s/assets?%s" % (
        UPLOADS, REPO, release_id, urllib.parse.urlencode({"name": name}))
    # 附件上传走 uploads.github.com；name 参数用 urlencode 处理，
    # 因为文件名里可能有中文或空格，直接拼进 query 会 400。
    headers = {
        "Authorization": "token " + TOKEN,
        "Accept": "application/vnd.github+json",
        "User-Agent": "dsh",
        "Content-Type": mime,
    }
    last = None
    for i in range(attempts):  # 上传失败等 6 秒 × 次数递增：6s / 12s / 18s
        try:
            req = urllib.request.Request(url, data=data, method="POST", headers=headers)
            with urllib.request.urlopen(req, timeout=600) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except Exception as e:  # noqa: BLE001
            last = e
            print("  上传重试 %d/%d（%s）" % (i + 1, attempts, type(e).__name__))
            time.sleep(6 * (i + 1))
    raise SystemExit("上传失败: %s -> %s" % (name, last))


def main():
    """脚本主流程：查 release → 删旧附件 → 传新附件 → 打印结果。

    参数:
        无。

    返回:
        无。全部结果通过打印展示（这是给人工确认用的脚本，不需要返回值）。
    """
    rel = api("GET", "/repos/%s/releases/tags/%s" % (REPO, TAG))
    # 先按 tag 把 release 查出来，拿到它的数字 id —— 后面所有附件接口都要用 id 而不是 tag。
    release_id = rel["id"]  # release 的数字 id，后面查/删/传附件都用它
    print("release:", TAG, "id", release_id)

    # 列出这个 release 已有的附件，做成 {附件名: 附件id} 的映射。
    # 同名附件必须先删，否则 GitHub 会拒绝重名上传（或者出现两个同名文件）。
    existing = api("GET", "/repos/%s/releases/%s/assets" % (REPO, release_id))
    names = {a["name"]: a["id"] for a in existing}  # 现有附件映射：名称 -> id
    print("已有附件:", list(names.keys()) or "无")

    # 逐个文件处理：有同名旧附件就删掉，然后上传新的。
    # 这样脚本可以反复执行，改完手册直接重跑就行。
    for path, name, mime in FILES:
        if name in names:  # 同名附件先删掉，保证「重新上传」是幂等的
            api("DELETE", "/repos/%s/releases/assets/%s" % (REPO, names[name]))
            print("已删除旧附件:", name)
        asset = upload(release_id, path, name, mime)
        print("  附件地址:", asset["browser_download_url"])

    # 上传完再查一次 release，把最终的附件清单和下载地址打出来，方便直接复制给人。
    rel2 = api("GET", "/repos/%s/releases/tags/%s" % (REPO, TAG))  # 再查一次，打印最终清单
    print("\nRELEASE_URL:", rel2["html_url"])
    for a in rel2["assets"]:
        print("  -", a["name"], "%.2f MB" % (a["size"] / 1024 / 1024), a["browser_download_url"])


if __name__ == "__main__":
    main()
