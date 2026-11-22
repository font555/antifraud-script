package com.example.antifraud.engine;

import com.example.antifraud.model.Choice;

import java.util.ArrayList;
import java.util.List;

/**
 * 路径记录器：记录玩家每一步选了什么，是复盘页唯一的数据来源。
 * 用单例是因为"聊天页 → 复盘页"只需要在同一个进程内传递，不必序列化，代码量最小。
 */
public class PathRecorder {

    public static class Step {
        public final int index;
        public final String nodeId;
        public final String stageName;
        public final String choiceText;
        public final String risk;
        public final String tag;
        public final String feedback;
        public final int cost;

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

    private static final PathRecorder INSTANCE = new PathRecorder();

    public static PathRecorder get() {
        return INSTANCE;
    }

    private final List<Step> steps = new ArrayList<>();

    public void reset() {
        steps.clear();
    }

    public void record(String nodeId, String stageName, Choice c) {
        steps.add(new Step(steps.size() + 1, nodeId, stageName, c.text,
                c.risk, c.tag, c.feedback, c.cost));
    }

    public List<Step> getSteps() {
        return steps;
    }

    /** 累计损失金额 */
    public int totalCost() {
        int sum = 0;
        for (Step s : steps) {
            sum += s.cost;
        }
        return sum;
    }

    public int highRiskCount() {
        int n = 0;
        for (Step s : steps) {
            if ("high".equals(s.risk)) {
                n++;
            }
        }
        return n;
    }

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
