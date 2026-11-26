#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
剧本 JSON 校验脚本（整个项目内容流水线的第一道质检关）。

这个脚本是「剧本内容流水线」的质检员：任何一个剧本 JSON 改完，都要先跑一遍它，
确认新加的节点、选项、来源没有把整个作品弄坏；比赛现场演示之前也会再跑一次。

用法（在项目根目录执行）：
    py tools/validate_script.py
        不带参数 → 校验 app/src/main/assets/scripts/ 下的全部 *.json
    py tools/validate_script.py app/src/main/assets/scripts/game-account.json
        带参数 → 只校验指定的文件（可以一次给多个）

依赖：只用 Python 标准库（json / os / sys），不需要安装任何第三方包。
退出码：0 = 全部通过；1 = 有错误（方便以后接进 CI 流水线）。

检查内容：
  1. JSON 语法
  2. 每个 next 都指向存在的节点
  3. 非结束节点 >=2 个选项，且至少 1 个 high
  4. isEnd 节点没有 options
  5. 所有节点都能从 startNode 走到
  6. 从开局到结局的最短/最长路径步数
  7. sources 字段是否齐全
  8. 选项有金额损失（cost > 0）时，风险等级必须是 high

输出约定：
    每个文件先打一条分隔线和文件名，再打一行统计（结局数 / 最短步数 / 最长步数），
    最后统一打印所有 [提醒] 和 [错误]，避免一个文件出错就把后面的文件带偏。
"""

import json
import os
import sys

# ---------------------------------------------------------------- 路径常量
# ROOT 是「项目根目录」：本文件放在 tools/ 下，所以从本文件往上退两级正好是仓库根。
# 这样写的好处是脚本不依赖你从哪个目录执行——在项目根、在 tools/ 里、甚至在桌面点运行都能找到剧本。
# os.path.abspath 保证拿到的是绝对路径，dirname 连用两次就是「上一级、再上一级」。
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # 项目根目录（本文件在 tools/ 下，往上退两级）
DEFAULT_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "scripts")  # 剧本 JSON 的默认目录：不带参数时校验这里的全部文件

# ---------------------------------------------------------------- 字段清单
# 一条来源（sources 数组里的对象）必须写齐下面这 6 个字段，少一个就报错。
# 反诈作品最怕「查无出处」，所以来源字段是内容真实性的硬门槛：
#   name      —— 报道标题（要能搜到原文）
#   publisher —— 发布方（人民网、公安部刑侦局、地方公安官方号……）
#   date      —— 发布日期
#   url       —— 原文 http/https 链接
#   usedIn    —— 这个案例用在了剧本的哪一段
#   note      —— 改编说明（哪些是原案事实、哪些是话术改编）
SOURCE_FIELDS = ["name", "publisher", "date", "url", "usedIn", "note"]
OPTION_FIELDS = ["text", "next", "risk", "tag", "feedback"]
# 一个选项（options 数组里的对象）必须写齐下面这 5 个字段。
# text / next 是剧情流程必须的；risk / tag / feedback 是复盘页用来讲评的，同样不能空。
# risk 只允许 high / medium / low 三个值，写错会被下面的合法性检查拦下来。


def check(path, errors, warnings):
    """校验单个剧本 JSON 文件。

    参数:
        path    : 剧本 JSON 的完整路径。
        errors  : 错误列表。本函数往里「追加」而不是「返回」，是为了让一次运行里的
                  多个文件把错误汇总到一起，最后只打印一遍（见 main()）。
        warnings: 提醒列表。提醒不影响退出码，只是告诉你「还能再好一点」。

    返回:
        无返回值，结果全部通过 errors / warnings 两个列表带出去。

    异常:
        json.JSONDecodeError —— 文件不是合法 JSON 时抛给调用方去统一处理。
        OSError              —— 文件读不到（路径写错、没有读权限）时同样抛给调用方。
    """
    print("=" * 60)
    print("检查:", os.path.basename(path))
    with open(path, encoding="utf-8") as f:  # 统一按 UTF-8 读：剧本里有大量中文，用默认编码在简体中文 Windows 上可能出错
        data = json.load(f)  # 解析成 dict；JSON 语法错误会抛给 main() 捕获

    # 先取出节点表与开局节点。这两个是整份剧本的骨架，缺了后面所有检查都没意义，
    # 所以下面两处一旦不成立就直接 return，不再往下跑（避免刷屏式的连带报错）。
    nodes = data.get("nodes", {})  # 节点表，结构是 {节点id: 节点对象}
    if not nodes:  # 一个节点都没有 → 直接判错返回，后面的检查没有意义
        errors.append('缺少 nodes')
        return

    start = data.get("startNode", "")  # 开局节点 id，玩家从这一条开始读
    if start not in nodes:  # startNode 写错（或者干脆没写）时，同样直接返回
        errors.append(f'startNode "{start}" 不存在')
        return

    # ---------------------------------------------------------------- 逐节点体检
    # 一个节点一个节点地看：结局节点不允许再有选项（不然玩家点完结局还能继续玩），
    # 非结局节点必须给玩家至少 2 条路走，而且这 2 条路里必须有一条是「高风险坑」——
    # 这是本作品的设计底线：让人看到错的选择，才能记住它为什么错。
    # 2/3/4 逐节点检查
    for nid, node in nodes.items():
        options = node.get("options", [])
        is_end = node.get("isEnd", False)  # isEnd=true 表示这是结局节点
        if is_end:  # 结局节点：不许再有选项，但要有结尾标题（缺了只提醒，不报错）
            if options:
                errors.append(f'{nid}: isEnd=true 但仍有 options')
            if not node.get("endingTitle"):
                warnings.append(f'{nid}: 结局节点缺少 endingTitle')
        else:
            if len(options) < 2:
                errors.append(f'{nid}: 非结束节点只有 {len(options)} 个选项（要求 >=2）')
            if not any(o.get("risk") == "high" for o in options):  # 非结局节点：必须给玩家至少两条路，且至少一条是高风险
                errors.append(f'{nid}: 没有任何 high 风险选项')
        # 逐个选项检查：必填字段、next 是否有效、risk 取值、cost 是否为负。
        # 选项是玩家真正点击的东西，字段缺一个，聊天页就会渲染出空白按钮。
        for i, opt in enumerate(options):  # 逐条选项检查字段完整性
            for field in OPTION_FIELDS:
                if not opt.get(field):
                    errors.append(f'{nid} 选项{i + 1}: 缺少 {field}')
            nxt = opt.get("next")
            if nxt and nxt not in nodes:
                errors.append(f'{nid} 选项{i + 1}: next "{nxt}" 不存在')
            if opt.get("risk") not in ("high", "medium", "low"):
                errors.append(f'{nid} 选项{i + 1}: risk 取值非法 "{opt.get("risk")}"')
            if opt.get("cost", 0) < 0:  # cost 是这一步要损失的钱，负数没有业务含义
                errors.append(f'{nid} 选项{i + 1}: cost 不能为负')
            # 一致性：只要这一步会损失钱，就必须标 high，否则复盘会出现"损失 X 元、高风险 0 次"
            # 一致性检查：只要这一步会损失钱，风险等级就必须标 high。
            # 不这么卡的话，复盘页会出现「损失 4700 元，高风险 0 次」这种自相矛盾的结论。
            if opt.get("cost", 0) > 0 and opt.get("risk") != "high":
                errors.append(
                    f'{nid} 选项{i + 1}: 有金额损失（cost={opt.get("cost")}）却标成 {opt.get("risk")} 风险，必须标 high')

    # ---------------------------------------------------------------- 可达性
    # 用深度优先把所有能走到的节点标出来，剩下的就是「孤岛」。
    # 孤岛节点玩家永远看不到，等于白写，而且会让「节点数」这种汇报数据虚高，必须报错。
    # 5 可达性
    seen = set()
    stack = [start]
    while stack:  # 已访问集合，避免环形剧情把自己绕死
        cur = stack.pop()
        if cur in seen or cur not in nodes:  # 已经看过 / 不存在的节点直接跳过
            continue
        seen.add(cur)
        for opt in nodes[cur].get("options", []):
            if opt.get("next"):
                stack.append(opt["next"])
    unreachable = set(nodes) - seen  # 剩下的就是没人能走到的孤岛节点
    for nid in sorted(unreachable):
        errors.append(f'{nid}: 从 startNode 走不到（孤岛节点）')

    # ---------------------------------------------------------------- 路径长度
    # 用 BFS 求最短通关步数（玩家水平再差也能这么快走到结局），
    # 再用 BFS 出来的距离求最长（所有结局里步数最多的那一条）。
    # 最短步数太短说明剧本「一点就通」，最长太短说明分支不够深，两者都会影响单局时长。
    # 6 路径步数（BFS 最短，DFS 最长）
    endings = [n for n, v in nodes.items() if v.get("isEnd")]  # 先把所有结局节点挑出来
    dist, queue = {start: 0}, [start]  # dist 记录「从开局走几步能到这里」，同时兼作 BFS 的已访问表
    while queue:
        cur = queue.pop(0)
        for opt in nodes.get(cur, {}).get("options", []):
            nxt = opt.get("next")
            if nxt and nxt not in dist:
                dist[nxt] = dist[cur] + 1
                queue.append(nxt)
    reachable_endings = [e for e in endings if e in dist]
    if not reachable_endings:
        errors.append("没有任何可达的结局节点")
    else:
        shortest = min(dist[e] for e in reachable_endings)
        longest = max(dist[e] for e in reachable_endings)
        print(f"  结局数: {len(endings)}  最短通关步数: {shortest}  最长: {longest}")
        if shortest < 8:  # 太短：玩家点两下就通关，学不到东西
            errors.append(f"最短通关只要 {shortest} 步，体验太短（要求 >=8）")
        elif shortest < 12:
            warnings.append(f"最短通关 {shortest} 步，单局不足 6 分钟（建议 >=12 步）")
        if longest < 14:  # 主线不深：分支没展开，单局撑不满时间
            errors.append(f"主线最长只有 {longest} 步，分支不够深（要求 >=14 步）")

    # ---------------------------------------------------------------- 来源检查
    # 至少要有 1 条来源，且每条来源 6 个字段齐全。
    # referenceLoss（参考损失）缺失只算提醒：它不影响玩家体验，但复盘时没法和真实案件金额对照。
    # 7 来源
    sources = data.get("sources", [])  # 来源是内容真实性的底线，一条都没写直接报错
    if not sources:
        errors.append("sources 为空")
    for i, s in enumerate(sources):
        for field in SOURCE_FIELDS:
            if not s.get(field):
                errors.append(f'来源{i + 1}: 缺少 {field}')
    if not data.get("referenceLoss"):
        warnings.append("缺少 referenceLoss（复盘时无法与真实案件金额对照）")

    print(f"  节点数: {len(nodes)}  来源数: {len(sources)}  参考损失: {data.get('referenceLoss')}")


def main():
    """脚本入口：决定校验哪些文件，然后汇总结果。

    参数:
        无（直接读 sys.argv）。

    返回:
        int —— 0 表示全部通过，1 表示存在错误，交给 sys.exit 作为进程退出码。
    """
    if len(sys.argv) > 1:  # 有命令行参数就按参数来，没有就全量校验
        targets = sys.argv[1:]
    # 不带参数就校验 assets/scripts 下的全部剧本；带参数就只校验指定的那几个文件。
    # sorted() 保证输出顺序稳定，方便前后两次运行的结果做对比。
    else:
        targets = [os.path.join(DEFAULT_DIR, f)
                   for f in sorted(os.listdir(DEFAULT_DIR)) if f.endswith(".json")]

    # 逐个文件跑 check()。这里只兜住 JSON 语法错误这一种异常：
    # 其余的问题都走 errors 列表，不会让整个校验过程中断。
    errors, warnings = [], []  # 错误和提醒各自累加，最后一起打印
    for path in targets:
        try:
            check(path, errors, warnings)
        except json.JSONDecodeError as e:
            errors.append(f"{os.path.basename(path)}: JSON 语法错误 {e}")

    # 最后统一打印：先提醒、后错误，错误在最后出现，看日志时一眼就能看到。
    print("=" * 60)
    for w in warnings:
        print("[提醒]", w)
    for e in errors:
        print("[错误]", e)
    print("=" * 60)
    if errors:  # 有错误就返回 1，方便 CI 或批处理脚本判断成败
        print(f"不通过：{len(errors)} 个错误，{len(warnings)} 个提醒")
        return 1
    print(f"全部通过（{len(warnings)} 个提醒）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
