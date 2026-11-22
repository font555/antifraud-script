package com.example.antifraud.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个完整剧本，直接对应 assets/scripts 下的一个 json 文件。
 */
public class Script {
    public String id;
    public String title;
    public String scamType;
    /** 所属分类（对应书架上的那本书），取值见 assets/data/categories.json */
    public String category = "game";
    public String minutes;
    public String intro;
    public String startNode;
    public int stageCount = 5;
    /** 真实案件里受害者一共损失多少钱，用于复盘时对照 */
    public int referenceLoss = 0;
    /** 真实案件的来源说明，一句话 */
    public String referenceNote = "";
    public Map<String, StoryNode> nodes = new LinkedHashMap<>();
    public List<CaseSource> sources = new ArrayList<>();
}
