package com.example.antifraud.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个完整剧本，直接对应 assets/scripts 下的一个 json 文件。
 *
 * <p>这个类就是一个"数据口袋"：字段名和剧本 JSON 的顶层键一一对应。
 * 读的方向由 engine 包的 ScriptRepository.parse() 负责，把 JSON 变成 Script；
 * 写的方向由 engine 包的 ScriptStore.toJson() 负责，把 Script 变回 JSON。
 * 所以改这里的字段名，等于改剧本文件格式，两个方向必须同时改，
 * 否则用户自己存在手机里的剧本会解析不出来（表现为书架上的书消失）。</p>
 *
 * <p>谁在用它：首页书架（ScriptsTabView）拿它显示书名和简介，
 * 聊天页（ChatActivity）拿它取节点和入口，复盘页（ReviewActivity）拿它对照参考损失。</p>
 */
public class Script {
    /** 剧本唯一编号，等于 json 文件名去掉 .json（例如 game-account），聊天页靠它找剧本 */
    public String id;
    /** 剧本标题，显示在书的封面和列表里 */
    public String title;
    /** 骗局类型的一句话名称，例如「游戏账号交易」 */
    public String scamType;
    /** 所属分类（对应书架上的那本书），取值见 assets/data/categories.json */
    public String category = "game";
    /** 预计游玩时长，纯展示文案，例如 "6-8"（分钟） */
    public String minutes;
    /** 开局简介，进入聊天页之前的一句话介绍 */
    public String intro;
    /** 入口节点 id，一般是 n1；聊天页从这一节点开始渲染 */
    public String startNode;
    /** 一共分几幕，进度条按这个数字分段 */
    public int stageCount = 5;
    /** 真实案件里受害者一共损失多少钱，用于复盘时对照 */
    public int referenceLoss = 0;
    /** 真实案件的来源说明，一句话 */
    public String referenceNote = "";
    /**
     * 全部剧情节点，key 是节点 id（n1、n2……）。
     * 这里特意用 LinkedHashMap 而不是 HashMap：要保留 json 里的书写顺序，
     * 导出剧本时节点顺序才不会乱，人看着也舒服。
     */
    public Map<String, StoryNode> nodes = new LinkedHashMap<>();
    /** 这个剧本引用的真实案例来源，来源页会逐条列出来 */
    public List<CaseSource> sources = new ArrayList<>();
}
