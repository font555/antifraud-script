package com.example.antifraud.model;

/**
 * 一条真实案例来源。usedIn 说明这条来源支撑剧本里的哪几步，答辩时可以逐条对上。
 *
 * <p>对应剧本 JSON 的 sources 数组。来源页（SourcesActivity）会把这些条目
 * 一条条列出来，点一下用系统浏览器打开原文——这是本项目"案例可溯源"的凭据。</p>
 *
 * <p>内容纪律：这里的每条信息都必须能打开原文核对，不许编造链接、日期和金额；
 * 剧本里用了哪条来源的哪个细节，就写在 usedIn 和 note 里，方便评委抽查。</p>
 */
public class CaseSource {
    /** 报道标题，照抄原文标题 */
    public String name;
    /** 发布方，例如「人民网四川频道」「公安部刑侦局」 */
    public String publisher;
    /** 发布日期，格式 YYYY-MM-DD，必须和原文一致 */
    public String date;
    /** 原文链接，必须是能打开的 http/https 地址 */
    public String url;
    /** 这条来源支撑剧本里的哪几步，例如「n1 诱饵、n3 定金」 */
    public String usedIn;
    /** 改编说明：哪句话话术来自这篇报道、哪些地方做了改编 */
    public String note;
}
