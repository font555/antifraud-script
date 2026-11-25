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


def api(method, path, data=None, attempts=7, timeout=180):
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
            wait = 5 * (i + 1)
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

    # 只推“和远端不一样”的文件：先拿远端树的 blob 映射，再用 git hash-object 比对
    commit_info = api("GET", f"/repos/{REPO}/git/commits/{parent}")
    tree_sha = commit_info["tree"]["sha"]
    remote_tree = api("GET", f"/repos/{REPO}/git/trees/{tree_sha}?recursive=1")
    remote_map = {}
    for e in remote_tree.get("tree", []):
        if e.get("type") == "blob":
            remote_map[e["path"]] = e["sha"]

    changed = []
    for rel in files:
        full = os.path.join(ROOT, rel.replace("/", os.sep))
        if not os.path.exists(full):
            continue
        local_sha = subprocess.run(["git", "hash-object", full], cwd=ROOT,
                                   capture_output=True).stdout.decode().strip()
        if remote_map.get(rel) == local_sha:
            continue
        changed.append((rel, full))
    print("需要上传的文件:", len(changed))

    entries = []
    for rel, full in changed:
        raw = open(full, "rb").read()
        if len(raw) > 400 * 1024:
            # 大文件先单独建 blob，避免一次请求塞进几 MB
            print(f"  blob 直传 {rel} ({round(len(raw)/1024)} KB)")
            blob = api("POST", f"/repos/{REPO}/git/blobs",
                       {"content": base64.b64encode(raw).decode("ascii"), "encoding": "base64"})
            entries.append({"path": rel, "mode": "100644", "type": "blob", "sha": blob["sha"]})
            continue
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

    # 分块建树：每块累计不超过约 600KB 原文，用 base_tree 串起来
    chunk, chunk_size = [], 0
    for e in entries:
        size = len(e.get("content", "")) + 100
        if chunk and chunk_size + size > 260 * 1024:
            t = api("POST", f"/repos/{REPO}/git/trees", {"base_tree": tree_sha, "tree": chunk})
            tree_sha = t["sha"]
            print("  建树一块:", len(chunk), "个文件")
            chunk, chunk_size = [], 0
        chunk.append(e)
        chunk_size += size
    if chunk:
        t = api("POST", f"/repos/{REPO}/git/trees", {"base_tree": tree_sha, "tree": chunk})
        tree_sha = t["sha"]
        print("  建树一块:", len(chunk), "个文件")
    print("tree:", tree_sha[:10])

    message = (
        "feat: v4.3 快照（配图 + 引导 5 步 + 加号位置 + 设置页精简）\n\n"
        "- 首页轮播拉高：6 张案例卡按场景配图、3 张口诀卡配图，右上角标注「示意图」，图片来源可查\n"
        "- 新手引导 5 步：轮播 → 书架 → 剧本列表 → 搜索行加号 → 我的页设置；书架/列表完整高亮，切页只定位一次，去掉点遮罩跳步\n"
        "- 剧本页加号改到搜索框右侧（列表上方），不再遮挡剧本；列表底部取消预留留白\n"
        "- 设置页只保留字体样式与字号，去掉与「我的」页重复的清除记录/关于；版本号改为动态读取\n"
        "- 修复处置支线幕号错乱（第 5 幕后跳回第 3 幕），5 本剧本已统一到最后一幕\n"
        "- 内容不变：6 类 17 本剧本 405 个节点 71 条可核验来源；零第三方依赖、零权限、不联网\n\n"
        "注：通过 GitHub API 推送（本机 HTTPS git 通道受限），本地保留细粒度提交历史。"
    )

    commit = api("POST", f"/repos/{REPO}/git/commits", {
        "message": message, "tree": tree_sha, "parents": [parent]})
    print("commit:", commit["sha"][:10])

    api("PATCH", f"/repos/{REPO}/git/refs/heads/main", {"sha": commit["sha"], "force": False})
    print("推送成功:", commit["sha"])
    with open(os.path.join(ROOT, "logs", "remote-head.txt"), "w", encoding="utf-8") as f:
        f.write(commit["sha"])
    return commit["sha"]


if __name__ == "__main__":
    main()
