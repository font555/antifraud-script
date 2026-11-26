package com.example.antifraud.ui;

/**
 * 三个主界面（首页 / 剧本 / 我的）共用的 Host 回调，由 MainActivity 实现。
 * 每个 Tab 视图只关心"我需要什么"，不直接 new 别的 Activity，方便以后替换。
 *
 * <p><b>为什么用接口而不是直接 startActivity：</b>三个 Tab 视图（HomeTabView、
 * ScriptsTabView、MineTabView）是纯粹的"展示 + 点按"组件，它们不应该知道
 * "跳转到一个页面要带什么参数、用哪种动画、怎么处理返回栈"。
 * 把这些事收口到 MainActivity 里实现，好处有三：</p>
 * <ol>
 *   <li><b>解耦：</b>Tab 视图只声明"我要跳剧本页，按分类筛"，具体怎么跳不用管；</li>
 *   <li><b>可替换：</b>以后想换掉某个 Tab 的界面，只要实现同一套回调，MainActivity 不用改；</li>
 *   <li><b>好测试：</b>写个假的 TabHost 就能单独跑 Tab 视图，不依赖真机跳转。</li>
 * </ol>
 *
 * <p><b>谁实现：</b>只有 {@link MainActivity}。</p>
 * <p><b>谁调用：</b>三个 Tab 视图内部持有一个 TabHost 引用，点按钮时回调过来。</p>
 *
 * <p>本接口全部是"命令式"方法，没有返回值：调用方只负责发出请求，
 * 结果通过界面变化体现，不靠返回值判断成功与否。</p>
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

    /**
     * 弹一条短提示（Toast）。
     *
     * <p>放在接口里而不是各 Tab 自己调 Toast：样式统一、也不怕 Tab 视图
     * 拿不到合适的 Context。</p>
     *
     * @param msg 要显示的文本
     */
    void toast(String msg);
}
