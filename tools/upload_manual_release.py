# -*- coding: utf-8 -*-
"""把《代码研读手册》PDF / DOCX 作为 Release 附件上传（GitHub API）。

用法：python tools/upload_manual_release.py
"""

import json
import os
import subprocess
import time
import urllib.parse
import urllib.request

ROOT = r"C:\Users\Lecoo\Desktop\antifraud-script"
REPO = "font555/antifraud-script"
API = "https://api.github.com"
UPLOADS = "https://uploads.github.com"
TAG = "v5.4"

FILES = [
    ("docs/manual/反诈剧本杀-代码研读手册.pdf", "antifraud-manual-v5.4.pdf", "application/pdf"),
    ("docs/manual/反诈剧本杀-代码研读手册.docx",
     "antifraud-manual-v5.4.docx",
     "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
]


def token():
    out = subprocess.run(
        ["git", "credential", "fill"],
        input="protocol=https\nhost=github.com\n\n",
        capture_output=True, text=True, cwd=ROOT)
    for line in out.stdout.splitlines():
        if line.startswith("password="):
            return line[len("password="):]
    raise SystemExit("无法从凭据管理器拿到 token")


TOKEN = token()
HEADERS = {
    "Authorization": "token " + TOKEN,
    "Accept": "application/vnd.github+json",
    "User-Agent": "dsh",
    "Content-Type": "application/json",
}


def api(method, path, data=None, attempts=5, timeout=180):
    body = json.dumps(data).encode("utf-8") if data is not None else None
    last = None
    for i in range(attempts):
        try:
            req = urllib.request.Request(API + path, data=body, method=method, headers=HEADERS)
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except Exception as e:  # noqa: BLE001
            last = e
            print("  重试 %d/%d（%s）" % (i + 1, attempts, type(e).__name__))
            time.sleep(4 * (i + 1))
    raise SystemExit("请求失败: %s %s -> %s" % (method, path, last))


def upload(release_id, path, name, mime, attempts=4):
    full = os.path.join(ROOT, path)
    with open(full, "rb") as f:
        data = f.read()
    print("上传 %s（%.1f MB）" % (name, len(data) / 1024 / 1024))

    url = "%s/repos/%s/releases/%s/assets?%s" % (
        UPLOADS, REPO, release_id, urllib.parse.urlencode({"name": name}))
    headers = {
        "Authorization": "token " + TOKEN,
        "Accept": "application/vnd.github+json",
        "User-Agent": "dsh",
        "Content-Type": mime,
    }
    last = None
    for i in range(attempts):
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
    rel = api("GET", "/repos/%s/releases/tags/%s" % (REPO, TAG))
    release_id = rel["id"]
    print("release:", TAG, "id", release_id)

    existing = api("GET", "/repos/%s/releases/%s/assets" % (REPO, release_id))
    names = {a["name"]: a["id"] for a in existing}
    print("已有附件:", list(names.keys()) or "无")

    for path, name, mime in FILES:
        if name in names:
            api("DELETE", "/repos/%s/releases/assets/%s" % (REPO, names[name]))
            print("已删除旧附件:", name)
        asset = upload(release_id, path, name, mime)
        print("  附件地址:", asset["browser_download_url"])

    rel2 = api("GET", "/repos/%s/releases/tags/%s" % (REPO, TAG))
    print("\nRELEASE_URL:", rel2["html_url"])
    for a in rel2["assets"]:
        print("  -", a["name"], "%.2f MB" % (a["size"] / 1024 / 1024), a["browser_download_url"])


if __name__ == "__main__":
    main()
