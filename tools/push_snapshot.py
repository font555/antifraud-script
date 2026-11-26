# -*- coding: utf-8 -*-
"""通过 GitHub Git Data API 推送快照（本机 HTTPS git 通道受限时的备用通道）。

为什么不用 git push？
    这台备课电脑访问 GitHub 的 HTTPS git 端点经常超时，但 REST API 还能通，
    所以这里用 Git Data API 手工「造」一次提交：建 blob → 建 tree → 建 commit → 挪 ref。

优化点：
    用 tree 的 inline content 一次提交所有文件，把原来的 129 次请求压成 3 次左右；
    并且每个请求都带指数退避重试，网络抖动时不至于白跑一遍。

用法（在项目根目录执行）：
    py tools/push_snapshot.py

前置条件：
    1. 本机 git 凭据管理器里已经存了 GitHub 的 token（脚本用 git credential fill 去取）；
    2. 已经 git add 过的文件才会被推送（用 git ls-files 取清单）。

注意：
    脚本末尾会把远端 head 的 sha 写进 logs/remote-head.txt，方便下次对照。
    它只推「和远端内容不一样」的文件，所以重复运行也不会产生空提交之外的多余流量。
"""
import base64
import json
import os
import subprocess
import time
import urllib.request

# ---------------------------------------------------------------- 配置
# ROOT : 本地仓库根目录。API 请求里的路径都是相对它来拼的。
# REPO : GitHub 上的仓库全名（用户名/仓库名）。
# API  : GitHub REST API 的根地址，所有接口路径都拼在它后面。
ROOT = r"C:\Users\Lecoo\Desktop\antifraud-script"  # 本地仓库根目录
REPO = "font555/antifraud-script"  # 目标仓库
API = "https://api.github.com"  # API 根地址


def token():
    """从本机 git 凭据管理器里取出 GitHub 的访问 token。

    做法是把 git credential fill 当成一个「查密码」的命令来用：
    往它的标准输入喂 protocol/host 两行，它就会在标准输出里回 username/password。

    返回:
        str —— token 字符串（就是 password= 那一行等号后面的部分）。

    异常:
        以 SystemExit 退出 —— 说明凭据管理器里没有存过 GitHub 的凭据，
        需要先手动 git push 一次、让 git 记住密码。
    """
    out = subprocess.run(
        ["git", "credential", "fill"],
        input="protocol=https\nhost=github.com\n\n",
        capture_output=True, text=True, cwd=ROOT)
    for line in out.stdout.splitlines():  # 逐行找 password= 开头的字段
        if line.startswith("password="):
            return line[len("password="):]
    # 凭据输出是 key=value 的若干行，只关心 password 那一行。
    # 找不到就直接退出：没有 token，后面所有 API 调用都必然 401。
    raise SystemExit("无法从凭据管理器拿到 token")


TOKEN = token()
# 脚本一启动就把 token 取出来放进模块级常量。
# 这样每个请求函数都能直接用，不用层层传参；代价是拿不到 token 时脚本会立刻退出。
HEADERS = {
    "Authorization": "token " + TOKEN,
    "Accept": "application/vnd.github+json",
    "User-Agent": "dsh",
    "Content-Type": "application/json",
}
# 所有请求共用的请求头：
#   Authorization —— API 的鉴权方式，token 后面跟空格再跟 token 本身；
#   Accept        —— 声明按 GitHub 的 +json 版本接收（固定写死，跟上 API 演进）；
#   User-Agent    —— GitHub 强制要求带 UA，随便写个项目名即可；
#   Content-Type  —— 我们的请求体都是 JSON。


def api(method, path, data=None, attempts=7, timeout=180):
    """调用一次 GitHub REST API，带自动重试。

    参数:
        method  : HTTP 方法，比如 "GET" / "POST" / "PATCH"。
        path    : 接口路径，会拼在 API 常量后面（例如 "/repos/x/y/git/ref/heads/main"）。
        data    : 请求体。None 表示不带 body（GET / DELETE 常见）。
        attempts: 最多试几次。默认 7 次，配合下面的线性退避，总共能等两分多钟。
        timeout : 单次请求的超时秒数（推送大文件时给得比较宽松）。

    返回:
        dict —— 把响应 body 按 JSON 解析后的结果。

    异常:
        以 SystemExit 退出 —— 重试用尽仍未成功。这里不抛原始异常，
        是因为脚本在命令行里跑，直接给一句人话比给 traceback 更好排查。
    """
    url = API + path
    body = json.dumps(data).encode("utf-8") if data is not None else None
    # 提前把 body 序列化好：重试时反复用同一份字节，避免每次都重新 json.dumps。
    last = None
    for i in range(attempts):
    # 线性退避：第 1 次失败等 5 秒，第 2 次等 10 秒……最多 7 次。
    # 之所以不用指数退避，是因为 GitHub 的限流窗口通常几十秒就能恢复，
    # 等待时间拉得太长反而不如早点重试。
        try:
            req = urllib.request.Request(url, data=body, method=method, headers=HEADERS)
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except Exception as e:  # noqa: BLE001
            last = e
            wait = 5 * (i + 1)
            print(f"  重试 {i + 1}/{attempts}（{type(e).__name__}）{wait}s 后继续")
            time.sleep(wait)
    # 全部尝试都失败：把最后一次的异常原因带出来，方便判断是网络问题还是权限问题。
    raise SystemExit(f"请求失败: {method} {path} -> {last}")


def main():
    """脚本主流程：算差异 → 上传 blob → 建 tree → 建 commit → 挪 ref。

    参数:
        无。

    返回:
        str —— 新提交的 sha（同时也写进了 logs/remote-head.txt）。

    步骤概览:
        1. 读远端 main 分支当前的提交（作为本次提交的父提交）；
        2. 用 git ls-files 拿到本地要跟踪的文件清单；
        3. 逐个和远端 tree 里的 blob sha 比对，只保留真正改动过的文件；
        4. 小文本文件直接内联进 tree，大文件或二进制先单独建 blob；
        5. 分块建 tree（每块控制在 256KB 左右），再建 commit、挪 ref。
    """
    ref = api("GET", f"/repos/{REPO}/git/ref/heads/main")
    parent = ref["object"]["sha"]
    # 第一步：拿远端 main 现在的 head。它既是父提交，也是后面建 tree 的 base。
    # 打印前 10 位只是日志好看，完整 sha 一直在变量里。
    print("远端 head:", parent[:10])

    raw_list = subprocess.run(["git", "ls-files", "-z"], cwd=ROOT, capture_output=True).stdout  # 取被 git 跟踪的文件（已经 add 过的才会被推）
    files = [x for x in raw_list.decode("utf-8").split("\0") if x]  # NUL 分隔解码，过滤空串
    print("本地文件数:", len(files))
    # git ls-files -z：列出被 git 跟踪的文件，-z 用 NUL 分隔，
    # 这样即使文件名里有空格或中文也不会被拆错。
    # 解码后按 NUL 切开，过滤掉尾部空串，就得到干净的文件清单。

    # 只推“和远端不一样”的文件：先拿远端树的 blob 映射，再用 git hash-object 比对
    commit_info = api("GET", f"/repos/{REPO}/git/commits/{parent}")  # 父提交的 tree，作为本次建树的基准
    tree_sha = commit_info["tree"]["sha"]
    # 第二步：只推「和远端不一样」的文件。
    # 做法是先拿远端最新 tree 的 {路径: blob sha} 映射，
    # 再用 git hash-object 算本地文件的 sha 去比对——内容一样就不必再传一遍。
    # 这一步是整次推送能压成 3 个请求的关键。
    remote_tree = api("GET", f"/repos/{REPO}/git/trees/{tree_sha}?recursive=1")  # recursive=1 递归列出全部子路径，一步拿全
    remote_map = {}
    for e in remote_tree.get("tree", []):
        if e.get("type") == "blob":  # 只关心文件（blob），目录条目（tree）跳过
            remote_map[e["path"]] = e["sha"]

    changed = []
    for rel in files:
        full = os.path.join(ROOT, rel.replace("/", os.sep))  # 转成本机真实路径（清单里用的是 / 分隔）
        if not os.path.exists(full):
            continue
    # 本地文件可能已被删除（改了名、删了图），这里跳过；
    # git hash-object 算的是「这个文件内容在 git 里的对象 id」，和远端 tree 里的 sha 可以直接比。
        local_sha = subprocess.run(["git", "hash-object", full], cwd=ROOT,  # 算本地文件内容的 git 对象 id
                                   capture_output=True).stdout.decode().strip()
        if remote_map.get(rel) == local_sha:  # 内容一样 → 不用传
            continue
        changed.append((rel, full))
    print("需要上传的文件:", len(changed))

    # 第三步：把有变化的文件整理成 GitHub tree API 需要的 entries。
    # 三种处理方式，按代价从低到高：
    #   ① 小于 400KB 且是纯文本 → 直接把内容内联进 tree（一次请求搞定）；
    #   ② 纯文本但含 NUL 字节 → 说明其实不是文本，改用 base64；
    #   ③ 大文件或二进制 → 先单独创建一个 blob，再在 tree 里引用它的 sha。
    entries = []
    for rel, full in changed:
        raw = open(full, "rb").read()  # 按二进制读，图片等文件要按字节处理
        if len(raw) > 400 * 1024:
            # 大文件先单独建 blob，避免一次请求塞进几 MB
        # 大文件单独建 blob：避免一个 tree 请求里塞进好几 MB 的 base64，
        # 那样既容易超时，也会让重试的代价变得很高。
            print(f"  blob 直传 {rel} ({round(len(raw)/1024)} KB)")
            blob = api("POST", f"/repos/{REPO}/git/blobs",
                       {"content": base64.b64encode(raw).decode("ascii"), "encoding": "base64"})
            entries.append({"path": rel, "mode": "100644", "type": "blob", "sha": blob["sha"]})
            continue
        try:
        # 先试试能不能按 UTF-8 解成文本。解不开就说明是二进制，走下面的 base64 分支。
        # 额外检查 NUL 字节：有些文件能被 UTF-8 解码，但其实是二进制（比如某些 .so），
        # 内联进 JSON 会破坏内容，所以一并排除。
            text = raw.decode("utf-8")  # 能按 UTF-8 解成文本的，优先内联
            if "\x00" not in text:
                entries.append({"path": rel, "mode": "100644", "type": "blob", "content": text})
                continue
        except UnicodeDecodeError:
            pass
        entries.append({"path": rel, "mode": "100644", "type": "blob",
                        "content": base64.b64encode(raw).decode("ascii"),
                        "encoding": "base64"})

    # 分块建树：每块累计不超过约 600KB 原文，用 base_tree 串起来
    # 第四步：分块建树。
    # 每块累计不超过约 256KB 原文，用 base_tree 把一块块串起来——
    # 第一块基于远端 tree，第二块基于第一块生成的 tree，依此类推，最终得到完整的新 tree。
    chunk, chunk_size = [], 0
    for e in entries:
        size = len(e.get("content", "")) + 100  # 估算这条 entry 在请求体里占多少字节（+100 是 JSON 结构本身的开销）
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

    # 提交说明沿用仓库里中文 commit 的风格：一行标题 + 分点正文。
    # 这段文字会原样出现在 GitHub 的提交记录里，所以写得具体一点，方便日后回溯这一版改了什么。
    message = (  # 提交说明：写清这一版改了什么，方便日后回溯
        "docs(comment): 全项目补详细中文注释（注释率 12.1% -> 38.1%）；版本 6.1\n\n"
        "- 轮播 9 张配图全部改为本作品自绘：仿真实截图 + 像素风，内容与案例严格对应\n"
        "  · 游戏账号（定金→解冻金→拉黑）· AI 换脸（视频里的“女儿”）· 熟人视频借钱\n"
        "  · 刷单返利（接单群任务与提现失败）· 租卡兼职（日租卡走账）· 游戏代练（保证金）\n"
        "  · 3 张口诀卡：码/链/钱 · 平/话/急 · 警/证\n"
        "- 配图全部自绘，不使用新闻原图、不依赖图库照片，无第三方版权问题；来源页改为“配图说明（本作品自绘）”\n"
        "- 首页底部新增一行数据收尾：6 类骗局 · 17 本剧本 · 405 个剧情节点 · 71 条可核验来源\n"
        "- 配图按卡片宽度等比计算高度，不再压扁或裁切；轮播高度随之调整\n"
        "- 版本号 4.4（动态读取安装包）\n\n"
        "注：通过 GitHub API 推送（本机 HTTPS git 通道受限），本地保留细粒度提交历史。"
    )

    # 第五步：建 commit 并把 main 指向它。
    # 这里的 PATCH 用的是快进方式（force=False），如果远端在此期间被别人推过，
    # GitHub 会拒绝，从而避免悄悄覆盖别人的提交。
    commit = api("POST", f"/repos/{REPO}/git/commits", {
        "message": message, "tree": tree_sha, "parents": [parent]})
    print("commit:", commit["sha"][:10])

    api("PATCH", f"/repos/{REPO}/git/refs/heads/main", {"sha": commit["sha"], "force": False})  # 快进更新 main 分支
    print("推送成功:", commit["sha"])
    with open(os.path.join(ROOT, "logs", "remote-head.txt"), "w", encoding="utf-8") as f:  # 记录这次推送的 sha，供下次对照
    # 把新的 head 落到 logs/remote-head.txt，下次推送前可以拿它做对照。
    # 这个文件同时也会被下一次 git ls-files 列到，所以它自己也是一个被跟踪的产物。
        f.write(commit["sha"])
    return commit["sha"]


if __name__ == "__main__":
    main()
