package com.example.antifraud.model;

/**
 * 书架上的"一本书"：一类骗局。一个分类下可以放多个剧本。
 *
 * <p>数据来自 assets/data/categories.json，由 engine 包的
 * ScriptRepository.loadCategories() 解析出来。
 * 首页书架按分类摆书，剧本页按分类筛选，用的都是这里的字段。</p>
 *
 * <p>注意 color 是给人看的字符串（写进 json），colorInt 是给画笔用的整数；
 * 解析时如果颜色写错，会自动退回默认橙色，不会因为一个字母导致整本书画不出来。</p>
 */
public class Category {
    /** 分类 id，剧本里的 category 字段就是靠它对应上的，例如 game */
    public String id;
    /** 分类名，显示在书脊和筛选按钮上，例如「游戏账号」 */
    public String name;
    /** 一句话分类说明，显示在封面下方 */
    public String desc;
    /** 封面上画的像素大字，取分类名第一个字 */
    public String glyph;
    /** 封面色，形如 #C0392B */
    public String color = "#E4572E";
    /** 把 color 解析成的整数颜色，直接给 Canvas / 背景用；解析失败时是默认橙色 */
    public int colorInt = 0xFFE4572E;
}
