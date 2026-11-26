package com.example.antifraud.engine;

import com.example.antifraud.model.Choice;

import java.util.ArrayList;
import java.util.List;

/**
 * 路径记录器：记录玩家每一步选了什么，是复盘页唯一的数据来源。
 * 用单例是因为"聊天页 → 复盘页"只需要在同一个进程内传递，不必序列化，代码量最小。
 *
 * <p>整个项目没有数据库，这一局的"账"就放在这个内存对象里：
 * 聊天页每点一次选项就调 record 记一笔，一局结束时复盘页把 getSteps 取出来
 * 一条条显示，损失总额和风险次数则由 totalCost / highRiskCount / lowRiskCount 现算。</p>
 *
 * <p>使用节奏：进入新一局前一定要先调 reset 清空，否则上一局的记录会串到这一局，
 * 复盘页会多出一堆"你上一把选了什么"。因为是单例，App 被杀掉记录也就没了——
 * 这正是想要的效果：只复盘当前这一局，不长期保存行为数据。</p>
 */
public class PathRecorder {

    /**
     * 一步的完整快照：玩家在哪个节点、选了什么、冒了什么险、赔了多少钱。
     * 字段全部是 final（录进来就不许再改），这样复盘页可以放心地直接引用。
     */
    public static class Step {
        /** 第几步，从 1 开始；复盘页左边的序号就是它 */
        public final int index;
        /** 这一步发生在哪个节点 id（例如 n3），方便回到剧本原文对照 */
        public final String nodeId;
        /** 这一步属于第几幕的幕名，复盘页按幕分组显示 */
        public final String stageName;
        /** 玩家选的那句选项文字，原样保存，方便复盘时"我当时点的是这句" */
        public final String choiceText;
        /** 风险等级 high/medium/low，决定复盘页这一条的颜色 */
        public final String risk;
        /** 风险标签，一句话概括错在哪 */
        public final String tag;
        /** 选项自带的讲解（为什么危险 + 正确做法），复盘页会原样再念一遍 */
        public final String feedback;
        /** 这一步损失了多少钱（元），0 表示没损失 */
        public final int cost;

        /**
         * 造一步记录。只在 record 里被调用，参数含义和上面字段一一对应。
         *
         * @param index      第几步（从 1 开始）
         * @param nodeId     节点 id
         * @param stageName  幕名
         * @param choiceText 选项文字
         * @param risk       风险等级
         * @param tag        风险标签
         * @param feedback   选完给出的讲解
         * @param cost       这一步的损失金额
         */
        public Step(int index, String nodeId, String stageName, String choiceText,
                    String risk, String tag, String feedback, int cost) {
            this.index = index;
            this.nodeId = nodeId;
            this.stageName = stageName;
            this.choiceText = choiceText;
            this.risk = risk;
            this.tag = tag;
            this.feedback = feedback;
            this.cost = cost;
        }
    }

    /** 全局唯一实例。类加载时创建，之后一直复用，所以叫单例 */
    private static final PathRecorder INSTANCE = new PathRecorder();

    /** 取全局实例，任何页面都用 PathRecorder.get() 拿到同一个对象 */
    public static PathRecorder get() {
        return INSTANCE;
    }

    /** 本局记录下来的所有步骤，按点击顺序排列 */
    private final List<Step> steps = new ArrayList<>();

    /** 开新一局前清空记录。聊天页每次"开始/重来"都应该调一次 */
    public void reset() {
        steps.clear();
    }

    /**
     * 记录玩家的一次选择。
     *
     * <p>序号用 steps.size() + 1 现算，所以不用单独维护计数器，
     * 清空后也会自动从 1 重新开始。</p>
     *
     * @param nodeId    当前节点 id
     * @param stageName 当前节点的幕名
     * @param c         玩家点的那个选项；这里只读它的字段，不会改它
     */
    public void record(String nodeId, String stageName, Choice c) {
        steps.add(new Step(steps.size() + 1, nodeId, stageName, c.text,
                c.risk, c.tag, c.feedback, c.cost));
    }

    /** 取出全部步骤，复盘页用它渲染列表；返回的是内部列表本身，调用方只读，不要改 */
    public List<Step> getSteps() {
        return steps;
    }

    /** 累计损失金额 */
    public int totalCost() {
        int sum = 0;
        for (Step s : steps) {
            sum += s.cost; // 逐步累加，损失为 0 的步骤不影响结果
        }
        return sum;
    }

    /** 高风险选择的次数，复盘时用来说明"你一共走了几步险棋" */
    public int highRiskCount() {
        int n = 0;
        for (Step s : steps) {
            if ("high".equals(s.risk)) { // 用常量在前比较，避免 risk 为 null 时空指针
                n++;
            }
        }
        return n;
    }

    /** 低风险选择的次数，和上面成对使用，用来算"正确选择占比" */
    public int lowRiskCount() {
        int n = 0;
        for (Step s : steps) {
            if ("low".equals(s.risk)) {
                n++;
            }
        }
        return n;
    }
}
