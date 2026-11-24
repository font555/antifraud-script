# -*- coding: utf-8 -*-
"""通过 GitHub Git Data API 推送快照（HTTPS git 被墙时的备用通道）。

优化：用 tree 的 inline content 一次提交所有文件，把 129 次请求压成 3 次，并带重试。
"""
import base64
import json
import os
import subprocess
import time
import urllib.request

ROOT = r"C:\Users\Lecoo\Desktop\antifraud-script"
REPO = "font555/antifraud-script"
API = "https://api.github.com"


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


def api(method, path, data=None, attempts=4, timeout=180):
    url = API + path
    body = json.dumps(data).encode("utf-8") if data is not None else None
    last = None
    for i in range(attempts):
        try:
            req = urllib.request.Request(url, data=body, method=method, headers=HEADERS)
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except Exception as e:  # noqa: BLE001
            last = e
            wait = 3 * (i + 1)
            print(f"  重试 {i + 1}/{attempts}（{type(e).__name__}）{wait}s 后继续")
            time.sleep(wait)
    raise SystemExit(f"请求失败: {method} {path} -> {last}")


def main():
    ref = api("GET", f"/repos/{REPO}/git/ref/heads/main")
    parent = ref["object"]["sha"]
    print("远端 head:", parent[:10])

    raw_list = subprocess.run(["git", "ls-files", "-z"], cwd=ROOT, capture_output=True).stdout
    files = [x for x in raw_list.decode("utf-8").split("\0") if x]
    print("本地文件数:", len(files))

    entries = []
    for rel in files:
        full = os.path.join(ROOT, rel.replace("/", os.sep))
        if not os.path.exists(full):
            continue
        raw = open(full, "rb").read()
        try:
            text = raw.decode("utf-8")
            if "\x00" not in text:
                entries.append({"path": rel, "mode": "100644", "type": "blob", "content": text})
                continue
        except UnicodeDecodeError:
            pass
        entries.append({"path": rel, "mode": "100644", "type": "blob",
                        "content": base64.b64encode(raw).decode("ascii"),
                        "encoding": "base64"})
    print("待提交文件:", len(entries))

    tree = api("POST", f"/repos/{REPO}/git/trees", {"tree": entries})
    print("tree:", tree["sha"][:10])

    message = (
        "feat: v3.0 完整快照（三主界面：首页轮播+书架 / 剧本平铺+加号 / 我的；" + "`n"
        "零依赖自绘底部导航、翻书后自动跳剧本页并筛选、新手引导改为 5 步自动切 Tab）" + "`n`n"
        "- 6 类 17 个剧本 / 405 个剧情节点 / 71 条可核验来源" + "`n"
        "- 零权限、零联网、零第三方依赖" + "`n"
        "- 文档：项目介绍、README、过程记录册（34 次提交）同步更新" + "`n`n"
        "注：通过 GitHub API 推送（本机 HTTPS git 通道被网络阻断），本地保留细粒度提交历史。"
    )
    commit = api("POST", f"/repos/{REPO}/git/commits", {
        "message": message, "tree": tree["sha"], "parents": [parent]})
    print("commit:", commit["sha"][:10])

    api("PATCH", f"/repos/{REPO}/git/refs/heads/main", {"sha": commit["sha"], "force": False})
    print("推送成功:", commit["sha"])
    with open(os.path.join(ROOT, "logs", "remote-head.txt"), "w", encoding="utf-8") as f:
        f.write(commit["sha"])
    return commit["sha"]


if __name__ == "__main__":
    main()
