package com.example.antifraud.model;

/**
 * 书架上的"一本书"：一类骗局。一个分类下可以放多个剧本。
 */
public class Category {
    public String id;
    public String name;
    public String desc;
    /** 封面上画的像素大字，取分类名第一个字 */
    public String glyph;
    /** 封面色，形如 #C0392B */
    public String color = "#E4572E";
    public int colorInt = 0xFFE4572E;
}
