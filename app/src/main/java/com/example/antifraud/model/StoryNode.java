package com.example.antifraud.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 剧情节点：一段对方/系统消息 + 一组选项。
 * isEnd = true 时表示剧情结束，进入复盘。
 */
public class StoryNode {
    public String id;
    public String speaker = "对方";
    public int stage = 1;
    public String stageName = "";
    public List<String> messages = new ArrayList<>();
    public List<Choice> options = new ArrayList<>();
    public boolean isEnd = false;
    public String endingTitle = "";
    public String endingAdvice = "";
}
