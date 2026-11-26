package com.example.antifraud.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 剧情节点：一段对方/系统消息 + 一组选项。
 * isEnd = true 时表示剧情结束，进入复盘。
 *
 * <p>对应剧本 JSON 里 nodes 下面的一个键值对：key 是节点 id，value 就是这个对象。
 * 聊天页每次只渲染一个节点：先把 messages 一条条"发"出来，
 * 再把 options 做成按钮；玩家点按钮后跳到 next 指向的下一个节点。</p>
 *
 * <p>注意两个边界：isEnd=true 的节点没有 options（点到底了）；
 * 非结束节点至少要有 2 个选项、且至少 1 个 risk 是 high，这是校验脚本的硬规则。</p>
 */
public class StoryNode {
    /** 节点 id，例如 n1；解析时如果 json 里没写，就用 nodes 的 key 补上 */
    public String id;
    /** 这一屏消息是谁发的，会显示在气泡上方；默认「对方」 */
    public String speaker = "对方";
    /** 属于第几幕，从 1 开始，聊天页用它在消息之间插「第 N 幕」分隔条 */
    public int stage = 1;
    /** 幕名，例如「诱饵」「上钩」，会显示在分隔条上 */
    public String stageName = "";
    /** 这一节点里对方连续发来的几句话，一条一条按顺序冒出 */
    public List<String> messages = new ArrayList<>();
    /** 这一节点的选项按钮；isEnd=true 时这里为空列表 */
    public List<Choice> options = new ArrayList<>();
    /** 是否是结局节点：true 表示剧情到此结束，下一步进复盘页 */
    public boolean isEnd = false;
    /** 结局标题，只有 isEnd=true 才有意义，例如「及时止损：0 元」 */
    public String endingTitle = "";
    /** 结局建议，复盘前给玩家的一句话提醒；同样只在结局节点使用 */
    public String endingAdvice = "";
}
