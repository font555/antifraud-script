package com.example.antifraud.ui;

/**
 * 三个主界面（首页 / 剧本 / 我的）共用的 Host 回调，由 MainActivity 实现。
 * 每个 Tab 视图只关心"我需要什么"，不直接 new 别的 Activity，方便以后替换。
 */
public interface TabHost {

    /** 切换主界面：0 首页，1 剧本，2 我的 */
    void switchTab(int index);

    /** 跳剧本页并筛选某个分类（"全部"传 null） */
    void openCategory(String categoryId);

    /** 直接开始一个剧本（进聊天页） */
    void openScript(String scriptId);

    /** 剧本工坊：管理我的剧本 */
    void openStudio();

    /** 系统文件选择器导入剧本 */
    void openImport();

    /** 打开设置 */
    void openSettings();

    /** 全部真实案例来源 */
    void openAllSources();

    /** 重新看一遍新手引导 */
    void openGuideAgain();

    /** 打开运行日志（隐藏入口用） */
    void openLog();

    /** 数据变了（通关、清除记录、导入剧本），让三个页面都刷新 */
    void onDataChanged();

    void toast(String msg);
}
