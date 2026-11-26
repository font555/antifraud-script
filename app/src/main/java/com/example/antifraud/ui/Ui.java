package com.example.antifraud.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 界面小工具：dp 转换、造文字、造卡片、造风险标签。
 * 全项目只有这一处放"样式"，改配色/圆角只改这里。
 *
 * <p><b>谁在用：</b>几乎每个页面（MainActivity、ChatActivity、ReviewActivity、
 * SourcesActivity、SettingsActivity ……）都在用这里的方法拼界面。本项目不使用 XML
 * 约束样式，大量界面是代码里现搭的，所以把公共样式集中到这一个类，做到
 * "改配色只改一处"。</p>
 *
 * <p><b>设计约定（很重要）：</b></p>
 * <ul>
 *   <li>所有尺寸参数都用 <b>dp</b> 传进来，方法内部用 {@link #dp} 转成 px，
 *       保证不同屏幕密度下看起来一样大。</li>
 *   <li>所有字号参数都用 <b>sp</b> 传进来，交给 {@link BaseActivity} 改过的
 *       fontScale 自动放大。<b>本类绝不自己乘字号倍数</b>，否则会二次放大。</li>
 *   <li>用 {@code final class} + 私有构造，纯粹当静态工具类用，不允许被继承或 new 出来。</li>
 * </ul>
 */
public final class Ui {

    /** 私有构造：工具类不需要实例，写出来是为了禁止 new Ui() */
    private Ui() {
    }

    /**
     * dp 转 px：把设计稿上的 dp 值换算成当前屏幕的真实像素。
     *
     * <p>为什么必须转：dp 是"密度无关像素"，在低密度屏 1dp=1px、高密度屏 1dp=2px 或 3px。
     * 代码里直接写 px 数字，在别人的手机上大小就会全乱。</p>
     *
     * @param c 任意 Context，用来取屏幕密度
     * @param v dp 数值（可以带小数，例如 0.5f）
     * @return 四舍五入后的像素值
     */
    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /**
     * 造一个"像素风"文字控件：设文案、字号、颜色，并统一套上像素字体。
     *
     * <p>这是全项目造文字的总入口，页面上看到的绝大多数文字都由它生成，
     * 所以字体、内边距、渲染方式只需要在这里维护一份。</p>
     *
     * @param c        任意 Context
     * @param s        要显示的文本
     * @param sp       字号（单位 sp，会被全局 fontScale 再放大一次，别在这里乘倍数）
     * @param colorRes 颜色资源 id（如 R.color.ink）
     * @return 配置好的 TextView，还没被 addView 到任何父容器
     */
    public static TextView text(Context c, String s, float sp, int colorRes) {
        TextView tv = new TextView(c);              // 用代码创建纯 TextView，不依赖布局文件
        tv.setText(s);                              // 文案
        tv.setTextSize(sp);                         // 字号：单位 sp，由 fontScale 统一放大
        tv.setTextColor(c.getColor(colorRes));      // 文字颜色
        tv.setTypeface(Typefaces.pixel(c));         // 套像素字体（用户在设置里切"系统字体"时自动退回默认字体）
        tv.setIncludeFontPadding(false);            // 去掉字体自带上下留白，行距更紧凑、像素风更整齐
        tv.getPaint().setAntiAlias(false);          // 关抗锯齿：边缘保持硬边，才是像素块的效果
        tv.getPaint().setFakeBoldText(true);        // 伪加粗：像素字体没有粗体字重，靠描边加粗视觉上更清楚
        tv.getPaint().setSubpixelText(false);       // 关次像素渲染，避免彩色描边破坏纯色像素观感
        return tv;
    }

    /**
     * 白色圆角卡片，带下边距。
     *
     * <p>背景用的是 drawable 里的九宫格 {@code px_panel}（白底 + 深色像素描边 + 圆角），
     * 不是用 GradientDrawable 现画的，这样圆角和描边宽度在放大字号后依然清晰。</p>
     *
     * @param c            任意 Context
     * @param paddingDp    卡片四周的内边距（dp），内容不要贴边
     * @param marginBottomDp 卡片与下一张卡片之间的垂直间距（dp）
     * @return 已设好背景、内边距和外边距的竖直 LinearLayout，直接 addView 即可
     */
    public static LinearLayout card(Context c, float paddingDp, float marginBottomDp) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);                          // 卡片内部自上而下排列
        box.setBackgroundResource(com.example.antifraud.R.drawable.px_panel); // 白底 + 像素描边的九宫格
        int p = dp(c, paddingDp);                                          // 内边距由 dp 转 px
        box.setPadding(p, p, p, p);                                        // 上下左右同宽，四边留白一致
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,                    // 宽度撑满父容器
                LinearLayout.LayoutParams.WRAP_CONTENT);                   // 高度由内容决定
        lp.bottomMargin = dp(c, marginBottomDp);                           // 卡片之间的间距
        box.setLayoutParams(lp);
        return box;
    }

    /**
     * 风险标签：高风险 / 中风险 / 低风险。
     *
     * <p>三档各自配一套"浅底色 + 深字色"，保证在白色卡片上也看得清，
     * 且不依赖颜色区分（文字本身写明了高中低），色弱用户也能读懂。</p>
     *
     * @param c    任意 Context
     * @param risk 风险等级字符串，只认 "high" / "medium"，其它一律当低风险
     * @return 一个圆角小标签（TextView），一般放在选项文字旁边
     */
    public static TextView riskChip(Context c, String risk) {
        String label;   // 标签上显示的中文
        int bg;         // 背景色（浅色）
        int fg;         // 文字色（深色）
        if ("high".equals(risk)) {
            label = "高风险";
            bg = 0xFFFDECEA;   // 浅红
            fg = 0xFFD93025;   // 深红
        } else if ("medium".equals(risk)) {
            label = "中风险";
            bg = 0xFFFDF3E3;   // 浅橙
            fg = 0xFFC77700;   // 深橙
        } else {
            label = "低风险";   // 兜底分支：其它任何值都按低风险展示，宁可显示更保守
            bg = 0xFFE9F5EC;   // 浅绿
            fg = 0xFF2E7D32;   // 深绿
        }
        TextView tv = new TextView(c);
        tv.setText(label);
        tv.setTextSize(13);                 // 单位 sp，随全局字号放大
        tv.setTextColor(fg);
        GradientDrawable gd = new GradientDrawable();   // 现画圆角矩形当背景，省一个 drawable 文件
        gd.setCornerRadius(dp(c, 5));                   // 圆角半径 5dp
        gd.setColor(bg);
        tv.setBackground(gd);
        tv.setPadding(dp(c, 6), dp(c, 2), dp(c, 6), dp(c, 2)); // 左右 6dp、上下 2dp 的紧凑内边距
        tv.setGravity(Gravity.CENTER);                  // 文字在标签内居中
        return tv;
    }

    /**
     * 金额格式化：50400 -> "50,400"。
     *
     * <p>用 {@code %,d} 加千分位，数字越长越不容易看错位数——诈骗案件里
     * 少看一个 0 就是十倍的差距，所以金额一律带逗号显示。</p>
     *
     * @param v 金额（元，整数；本项目所有金额都是整数元，不涉及角和分）
     * @return 带千分位分隔符的字符串
     */
    public static String money(int v) {
        return String.format("%,d", v);
    }

    /**
     * 给任意 TextView 套上像素字体（关闭抗锯齿，边缘才硬）。
     *
     * <p>跟 {@link #text} 的区别：text 是"新建"一个控件，本方法是"改造"一个已经
     * 存在的控件（比如布局 XML 里声明的 TextView，或系统控件要统一风格时）。
     * 只改渲染参数，不动文案和尺寸。</p>
     *
     * @param tv 要改造的 TextView，不能为 null
     */
    public static void pixel(TextView tv) {
        tv.setTypeface(Typefaces.pixel(tv.getContext()));   // 套像素字体
        tv.setIncludeFontPadding(false);                    // 去掉字体上下留白
        tv.getPaint().setAntiAlias(false);                  // 关抗锯齿，保住硬边
        tv.getPaint().setFakeBoldText(true);                // 伪加粗
        tv.getPaint().setSubpixelText(false);               // 关次像素渲染
    }

    /**
     * Android 15+ 强制边到边，状态栏会盖住内容。这里给内容区补顶部内边距。
     * 各 Activity 在 setContentView 之后调用一次即可。
     *
     * <p>为什么需要它：从 Android 15（API 35）起，系统强制应用"边到边"显示，
     * 内容会一直画到状态栏和导航栏底下，标题就被状态栏盖住了。本方法在内容根视图上
     * 挂一个 insets 监听，把状态栏高度加到顶部内边距、导航栏高度加到底部内边距，
     * 相当于"手动让开"这两条系统栏。</p>
     *
     * <p>为什么用监听而不是直接读一次高度：insets 会在旋转屏幕、弹出输入法、
     * 进出分屏时变化，挂监听才能每次都正确让开，不会算错一次就永久错位。</p>
     *
     * @param activity 当前页面，用它的 android.R.id.content 作为内容根视图
     */
    public static void insetContent(final android.app.Activity activity) {
        final View content = activity.findViewById(android.R.id.content); // 整个页面的内容根容器
        if (content == null) {
            return;                                    // 根视图还没挂上（极少见），直接放弃，不影响页面显示
        }
        final int pl = content.getPaddingLeft();       // 记下原有左内边距，稍后要加回去
        final int pt = content.getPaddingTop();        // 记下原有上内边距
        final int pr = content.getPaddingRight();      // 记下原有右内边距
        final int pb = content.getPaddingBottom();     // 记下原有下内边距
        content.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets insets) {
                int top;      // 状态栏占的高度
                int bottom;   // 导航栏占的高度
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    // Android 11（API 30）起推荐用 Insets 细分类型，只取"系统栏"这一类，
                    // 不会把输入法、刘海等其他 insets 也算进来
                    android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                    top = bars.top;
                    bottom = bars.bottom;
                } else {
                    // Android 11 以下没有 Type 概念，只能取整体 insets，精度略差但够用
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                }
                // 原来的内边距 + 系统栏高度：既保住页面自己的留白，又让开状态栏和导航栏
                v.setPadding(pl, pt + top, pr, pb + bottom);
                return insets;   // 原样返回，别把 insets 吞掉，子控件可能还要用
            }
        });
        // 主动请求一次 insets 分发：不等下一次系统事件，页面一打开就立刻对齐
        content.requestApplyInsets();
    }

    /**
     * 12 号灰色小字，用于各种说明。
     *
     * <p>比正文更小更淡，用来放"补充说明、免责声明、来源注释"这类
     * 重要但不抢眼的信息，避免整页文字一样大、没有层次。</p>
     *
     * @param c 任意 Context
     * @param s 说明文字
     * @return 灰色小字 TextView（字号单位 sp，会随全局字号一起放大）
     */
    public static TextView hint(Context c, String s) {
        // 直接复用 text()，只是固定 12sp + 灰色，保证全项目"小字"长得一模一样
        TextView tv = text(c, s, 12, com.example.antifraud.R.color.ink_hint);
        tv.setLineSpacing(dp(c, 3), 1f);   // 行间距 3dp：小字排长句时不至于挤成一团
        return tv;
    }
}
