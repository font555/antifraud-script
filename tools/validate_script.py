#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
剧本 JSON 校验脚本。

用法：
    py tools/validate_script.py
    py tools/validate_script.py app/src/main/assets/scripts/game-account.json

检查内容：
  1. JSON 语法
  2. 每个 next 都指向存在的节点
  3. 非结束节点 >=2 个选项，且至少 1 个 high
  4. isEnd 节点没有 options
  5. 所有节点都能从 startNode 走到
  6. 从开局到结局的最短/最长路径步数
  7. sources 字段是否齐全

任何一条不通过 → 退出码 1，方便之后接进 CI。
"""

import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "scripts")

SOURCE_FIELDS = ["name", "publisher", "date", "url", "usedIn", "note"]
OPTION_FIELDS = ["text", "next", "risk", "tag", "feedback"]


def check(path, errors, warnings):
    print("=" * 60)
    print("检查:", os.path.basename(path))
    with open(path, encoding="utf-8") as f:
        data = json.load(f)

    nodes = data.get("nodes", {})
    if not nodes:
        errors.append('缺少 nodes')
        return

    start = data.get("startNode", "")
    if start not in nodes:
        errors.append(f'startNode "{start}" 不存在')
        return

    # 2/3/4 逐节点检查
    for nid, node in nodes.items():
        options = node.get("options", [])
        is_end = node.get("isEnd", False)
        if is_end:
            if options:
                errors.append(f'{nid}: isEnd=true 但仍有 options')
            if not node.get("endingTitle"):
                warnings.append(f'{nid}: 结局节点缺少 endingTitle')
        else:
            if len(options) < 2:
                errors.append(f'{nid}: 非结束节点只有 {len(options)} 个选项（要求 >=2）')
            if not any(o.get("risk") == "high" for o in options):
                errors.append(f'{nid}: 没有任何 high 风险选项')
        for i, opt in enumerate(options):
            for field in OPTION_FIELDS:
                if not opt.get(field):
                    errors.append(f'{nid} 选项{i + 1}: 缺少 {field}')
            nxt = opt.get("next")
            if nxt and nxt not in nodes:
                errors.append(f'{nid} 选项{i + 1}: next "{nxt}" 不存在')
            if opt.get("risk") not in ("high", "medium", "low"):
                errors.append(f'{nid} 选项{i + 1}: risk 取值非法 "{opt.get("risk")}"')
            if opt.get("cost", 0) < 0:
                errors.append(f'{nid} 选项{i + 1}: cost 不能为负')
            # 一致性：只要这一步会损失钱，就必须标 high，否则复盘会出现"损失 X 元、高风险 0 次"
            if opt.get("cost", 0) > 0 and opt.get("risk") != "high":
                errors.append(
                    f'{nid} 选项{i + 1}: 有金额损失（cost={opt.get("cost")}）却标成 {opt.get("risk")} 风险，必须标 high')

    # 5 可达性
    seen = set()
    stack = [start]
    while stack:
        cur = stack.pop()
        if cur in seen or cur not in nodes:
            continue
        seen.add(cur)
        for opt in nodes[cur].get("options", []):
            if opt.get("next"):
                stack.append(opt["next"])
    unreachable = set(nodes) - seen
    for nid in sorted(unreachable):
        errors.append(f'{nid}: 从 startNode 走不到（孤岛节点）')

    # 6 路径步数（BFS 最短，DFS 最长）
    endings = [n for n, v in nodes.items() if v.get("isEnd")]
    dist, queue = {start: 0}, [start]
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
        if shortest < 3:
            errors.append(f"最短通关只要 {shortest} 步，玩家点两下就结束了（要求 >=3）")
        elif shortest < 6:
            warnings.append(f"最短通关 {shortest} 步，正式版安全线建议 >=6 步（约 3 分钟）")

    # 7 来源
    sources = data.get("sources", [])
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
    if len(sys.argv) > 1:
        targets = sys.argv[1:]
    else:
        targets = [os.path.join(DEFAULT_DIR, f)
                   for f in sorted(os.listdir(DEFAULT_DIR)) if f.endswith(".json")]

    errors, warnings = [], []
    for path in targets:
        try:
            check(path, errors, warnings)
        except json.JSONDecodeError as e:
            errors.append(f"{os.path.basename(path)}: JSON 语法错误 {e}")

    print("=" * 60)
    for w in warnings:
        print("[提醒]", w)
    for e in errors:
        print("[错误]", e)
    print("=" * 60)
    if errors:
        print(f"不通过：{len(errors)} 个错误，{len(warnings)} 个提醒")
        return 1
    print(f"全部通过（{len(warnings)} 个提醒）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
