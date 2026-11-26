package com.example.antifraud.ui;

import android.content.Context;
import android.graphics.Typeface;

import com.example.antifraud.engine.SettingsStore;

/**
 * 字体来源：像素字体（Fusion Pixel Font，MIT）或系统字体。
 * 用户可在"设置"里切换；切换后下次创建的文字立即生效。
 *
 * <p><b>谁在用：</b>{@link Ui#text}、{@link Ui#pixel}、{@link PTextView}、
 * {@link SharePoster}（海报绘制）四处。也就是说，屏幕上的字和海报上的字
 * 都从这一个类取字体，改字体只需要改这里。</p>
 *
 * <p><b>合规说明：</b>像素字体是 Fusion Pixel Font，MIT 协议，已随仓库附带 LICENSE，
 * 可以随作品一起分发；系统字体则用 Android 自带的默认字体，不存在授权问题。
 * 本类不加载任何网络字体。</p>
 *
 * <p><b>缓存策略：</b>{@code pixel} 静态字段只装一次。字体文件解析比较慢
 * （要读 ttf 并建对象），如果每次造文字都重新 createFromAsset，页面会明显卡顿。</p>
 */
public final class Typefaces {

    /**
     * 已加载的像素字体对象，懒加载缓存。
     * null 表示"还没加载过"（不是"加载失败"，失败时会退化成默认字体并存进这个字段）。
     */
    private static Typeface pixel;

    /** 私有构造：纯工具类，不允许 new */
    private Typefaces() {
    }

    /**
     * 取当前应该用的字体：用户在设置里选了"系统字体"就给默认字体，否则给像素字体。
     *
     * <p>三条兜底路径，保证任何情况下都不会抛异常、界面一定出得来字：</p>
     * <ol>
     *   <li>用户主动选择系统字体 → 直接用 {@link Typeface#DEFAULT}；</li>
     *   <li>字体文件读取失败（缺失/损坏）→ 捕获异常，退化为默认字体，App 不崩；</li>
     *   <li>极端情况下加载结果为 null → 再兜一层默认字体。</li>
     * </ol>
     *
     * @param c 任意 Context，用来读 assets 里的字体文件
     * @return 一定非 null 的字体对象
     */
    public static Typeface pixel(Context c) {
        if (SettingsStore.fontStyle(c) == SettingsStore.FONT_SYSTEM) {
            return Typeface.DEFAULT;   // 用户选了系统字体：不读 assets，直接返回
        }
        if (pixel == null) {
            // 第一次用到才真正去解析 ttf，避免 App 启动时白白耗时
            try {
                pixel = Typeface.createFromAsset(c.getAssets(), "fonts/fusion_pixel.ttf");
            } catch (Exception e) {
                // 读不到字体不应该让 App 崩溃：退化为系统默认字体，功能完全不受影响
                pixel = Typeface.DEFAULT;
            }
        }
        return pixel == null ? Typeface.DEFAULT : pixel;   // 第三层兜底，防极端 null
    }

    /** 系统字体模式不需要关闭抗锯齿、也不需要加粗 */
    public static boolean isPixel(Context c) {
        // 只要用户没主动切到系统字体，就按像素字体模式处理（像素字体才需要关抗锯齿）
        return SettingsStore.fontStyle(c) != SettingsStore.FONT_SYSTEM;
    }
}
