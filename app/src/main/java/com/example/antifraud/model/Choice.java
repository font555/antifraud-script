package com.example.antifraud.model;

/**
 * 一个选项。risk 取值：high / medium / low
 * cost 是"选了这个选项会损失多少钱"，用于复盘时的损失结算。
 *
 * <p>对应剧本 JSON 里 options 数组的一项。玩家点一下，聊天页就把这一项
 * 记进 engine 包的 PathRecorder（复盘页靠它逐条回放），再跳到 next 指向的节点。</p>
 *
 * <p>内容约定：cost 大于 0 时 risk 必须是 high——这是校验脚本会拦的硬规则，
 * 意思是"只要会赔钱，就必须标成高风险"，避免把损失包装成中低风险。</p>
 */
public class Choice {
    /** 按钮上的文字，也就是玩家"要说的话/要做的事" */
    public String text;
    /** 选完跳到哪个节点 id；空字符串表示不走（正常剧本不该出现） */
    public String next;
    /** 风险等级：high / medium / low，复盘页按它给每一步上色 */
    public String risk = "low";
    /** 风险标签，用来概括这一步错在哪，例如「脱离平台」「私下转账」 */
    public String tag = "";
    /** 选完之后弹出的解释：为什么危险 + 正确做法，复盘页也会再显示一次 */
    public String feedback = "";
    /** 这一步会损失多少钱（元）；0 表示没损失，大于 0 时必须配 risk=high */
    public int cost = 0;
}
