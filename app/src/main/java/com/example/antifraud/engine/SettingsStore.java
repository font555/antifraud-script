package com.example.antifraud.engine;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 显示设置：字体样式 + 字号。只存本机，不联网。
 *
 * <p>字号四档：标准 1.0 / 大 1.3 / 特大 1.7 / 超大 2.1。
 * 相对标准的「放大量」是 0 / 0.3 / 0.7 / 1.1，即每一档的增量都比上一版翻倍，
 * 所以「超大」确实比原来的 1.55 明显大一截（正文 18sp → 实际约 38sp）。</p>
 *
 * <p>保存位置是 SharedPreferences（文件名 antifraud_settings），
 * 系统会自动把它写在 App 私有目录里，卸载就清空，不需要任何权限。</p>
 *
 * <p>这个类只"存和读"，真正把字号应用到界面的是 BaseActivity：
 * 它在 attachBaseContext 里读这里的 fontScale，改掉 Configuration.fontScale，
 * 于是所有 sp 单位的文字会自动放大——这也是为什么改字号不用挨个页面改代码。</p>
 *
 * <p>注意 changed 这个静态标记：设置页改完设置后置 true，书架回到前台时看到它
 * 就整页重建一次，否则已经画好的控件不会自己变大小。</p>
 */
public class SettingsStore {

    /** SharedPreferences 文件名；一个文件装全部设置，简单好找 */
    private static final String PREF = "antifraud_settings";
    /** 键：字体样式（0=像素字体，1=系统字体） */
    private static final String KEY_FONT_STYLE = "font_style";
    /** 键：字号倍数（float，见下面四个 SCALE_ 常量） */
    private static final String KEY_FONT_SCALE = "font_scale";
    /** 键：新手引导是否已经看过 */
    private static final String KEY_GUIDE = "guide_done";

    /** 字体样式：像素字体（项目自带的 Fusion Pixel Font，MIT 协议） */
    public static final int FONT_PIXEL = 0;
    /** 字体样式：系统默认字体（换成这个可以看清生僻字，但少了复古味道） */
    public static final int FONT_SYSTEM = 1;

    /** 四档字号倍数 */
    public static final float SCALE_STANDARD = 1.0f; // 标准：正常大小
    public static final float SCALE_LARGE = 1.3f; // 大：比标准大 30%
    public static final float SCALE_XLARGE = 1.7f; // 特大：比标准大 70%
    public static final float SCALE_HUGE = 2.1f; // 超大：比标准大一倍多，给视力不好的同学用

    /** 设置改动后置 true，书架回到前台时据此重建界面 */
    public static boolean changed = false;

    /** 私有构造：纯静态工具类，不允许 new */
    private SettingsStore() {
    }

    /**
     * 读当前字体样式。
     *
     * @param c 任意 Context
     * @return {@link #FONT_PIXEL} 或 {@link #FONT_SYSTEM}；没设置过时默认像素字体
     */
    public static int fontStyle(Context c) {
        return prefs(c).getInt(KEY_FONT_STYLE, FONT_PIXEL);
    }

    /**
     * 保存字体样式。
     *
     * <p>用 apply() 而不是 commit()：apply 是异步写盘，不卡界面线程；
     * 这里丢一次写入也不会有什么大问题，下次进设置页重新选就行。</p>
     *
     * @param c     任意 Context
     * @param style {@link #FONT_PIXEL} 或 {@link #FONT_SYSTEM}
     */
    public static void setFontStyle(Context c, int style) {
        prefs(c).edit().putInt(KEY_FONT_STYLE, style).apply();
        changed = true; // 通知页面：设置变了，下次回到前台要重建
    }

    /**
     * 读当前字号倍数。
     *
     * @param c 任意 Context
     * @return 1.0 / 1.3 / 1.7 / 2.1 之一；没设置过时是标准 1.0
     */
    public static float fontScale(Context c) {
        return prefs(c).getFloat(KEY_FONT_SCALE, SCALE_STANDARD);
    }

    /**
     * 保存字号倍数。
     *
     * <p>只接受上面四个档位的值；调用方（设置页）保证传进来的就是常量之一。</p>
     *
     * @param c     任意 Context
     * @param scale 字号倍数
     */
    public static void setFontScale(Context c, float scale) {
        prefs(c).edit().putFloat(KEY_FONT_SCALE, scale).apply();
        changed = true; // 同上，让界面知道要重建
    }

    /**
     * 把字号倍数翻译成给人看的文字（标准/大/特大/超大）。
     *
     * <p>这里用"大于等于某个阈值"而不是"等不等于某个值"，
     * 是因为 SharedPreferences 里存的是 float，浮点比较用 == 会有精度坑；
     * 用区间判断既能容忍极小误差，也方便以后增加中间档位。</p>
     *
     * @param c 任意 Context
     * @return 中文档位名，设置页放在字号选择器上
     */
    public static String fontScaleLabel(Context c) {
        float s = fontScale(c);
        if (s >= 1.9f) {
            return "超大"; // 2.1 档
        }
        if (s >= 1.5f) {
            return "特大"; // 1.7 档
        }
        if (s >= 1.15f) {
            return "大"; // 1.3 档
        }
        return "标准"; // 1.0 及以下
    }

    /**
     * 固定尺寸控件（chip、按钮、输入框、列表行高）要跟着字号一起长，
     * 否则 2.1 倍时 12sp 的文字会被 44dp 的框裁掉。
     * 放大上限 1.9 倍，避免把整页撑得没法看。
     *
     * <p>用法：原来写 Ui.dp(this, 44) 的地方改成
     * SettingsStore.scaleBox(this, Ui.dp(this, 44))。
     * 传进来的是"像素值"，返回的也是像素值，中间乘的是字号倍数。</p>
     *
     * @param c      任意 Context
     * @param sizePx 标准字号下的像素尺寸
     * @return 按当前字号放大后的像素尺寸；最大只放大到 1.9 倍
     */
    public static int scaleBox(Context c, int sizePx) {
        float scale = Math.min(1.9f, fontScale(c)); // 封顶 1.9，否则超大档控件会大到一屏放不下两个
        return Math.round(sizePx * scale); // 四舍五入取整，避免出现半像素导致模糊
    }

    /**
     * 新手引导是否已经看过。
     *
     * @param c 任意 Context
     * @return true 表示看过（首页不再自动弹引导）
     */
    public static boolean guideDone(Context c) {
        return prefs(c).getBoolean(KEY_GUIDE, false);
    }

    /** 标记新手引导已完成；引导走完最后一步时调用一次 */
    public static void setGuideDone(Context c) {
        prefs(c).edit().putBoolean(KEY_GUIDE, true).apply();
    }

    /** 允许重新播放新手引导（"我的 → 新手引导"用） */
    public static void setGuideDone(Context c, boolean done) {
        prefs(c).edit().putBoolean(KEY_GUIDE, done).apply();
    }

    /**
     * 拿 SharedPreferences 对象。每次调用都重新取，系统有缓存，开销很小，
     * 这样就不用在类里存静态 Context（存了会内存泄漏）。
     *
     * @param c 任意 Context
     * @return 本 App 私有的设置文件
     */
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE); // MODE_PRIVATE：只有本 App 能读写
    }
}
