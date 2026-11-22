package com.example.antifraud.model;

/**
 * 一条真实案例来源。usedIn 说明这条来源支撑剧本里的哪几步，答辩时可以逐条对上。
 */
public class CaseSource {
    public String name;
    public String publisher;
    public String date;
    public String url;
    public String usedIn;
    public String note;
}
