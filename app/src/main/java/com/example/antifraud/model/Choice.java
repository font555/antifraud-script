package com.example.antifraud.model;

/**
 * 一个选项。risk 取值：high / medium / low
 * cost 是"选了这个选项会损失多少钱"，用于复盘时的损失结算。
 */
public class Choice {
    public String text;
    public String next;
    public String risk = "low";
    public String tag = "";
    public String feedback = "";
    public int cost = 0;
}
