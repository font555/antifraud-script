package com.example.antifraud.engine;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 显示设置：字体样式 + 字号。只存本机，不联网。
 *
 * <p>字号四档：标准 1.0 / 大 1.3 / 特大 1.7 / 超大 2.1。
 * 相对标准的「放大量」是 0 / 0.3 / 0.7 / 1.1，即每一档的增量都比上一版翻倍，
 * 所以「超大」确实比原来的 1.55 明显大一截（正文 18sp → 实际约 38sp）。</p>
 */
public class SettingsStore {

    private static final String PREF = "antifraud_settings";
    private static final String KEY_FONT_STYLE = "font_style";
    private static final String KEY_FONT_SCALE = "font_scale";
    private static final String KEY_GUIDE = "guide_done";

    public static final int FONT_PIXEL = 0;
    public static final int FONT_SYSTEM = 1;

    /** 四档字号倍数 */
    public static final float SCALE_STANDARD = 1.0f;
    public static final float SCALE_LARGE = 1.3f;
    public static final float SCALE_XLARGE = 1.7f;
    public static final float SCALE_HUGE = 2.1f;

    /** 设置改动后置 true，书架回到前台时据此重建界面 */
    public static boolean changed = false;

    private SettingsStore() {
    }

    public static int fontStyle(Context c) {
        return prefs(c).getInt(KEY_FONT_STYLE, FONT_PIXEL);
    }

    public static void setFontStyle(Context c, int style) {
        prefs(c).edit().putInt(KEY_FONT_STYLE, style).apply();
        changed = true;
    }

    public static float fontScale(Context c) {
        return prefs(c).getFloat(KEY_FONT_SCALE, SCALE_STANDARD);
    }

    public static void setFontScale(Context c, float scale) {
        prefs(c).edit().putFloat(KEY_FONT_SCALE, scale).apply();
        changed = true;
    }

    public static String fontScaleLabel(Context c) {
        float s = fontScale(c);
        if (s >= 1.9f) {
            return "超大";
        }
        if (s >= 1.5f) {
            return "特大";
        }
        if (s >= 1.15f) {
            return "大";
        }
        return "标准";
    }

    /**
     * 固定尺寸控件（chip、按钮、输入框、列表行高）要跟着字号一起长，
     * 否则 2.1 倍时 12sp 的文字会被 44dp 的框裁掉。
     * 放大上限 1.9 倍，避免把整页撑得没法看。
     */
    public static int scaleBox(Context c, int sizePx) {
        float scale = Math.min(1.9f, fontScale(c));
        return Math.round(sizePx * scale);
    }

    public static boolean guideDone(Context c) {
        return prefs(c).getBoolean(KEY_GUIDE, false);
    }

    public static void setGuideDone(Context c) {
        prefs(c).edit().putBoolean(KEY_GUIDE, true).apply();
    }

    /** 允许重新播放新手引导（"我的 → 新手引导"用） */
    public static void setGuideDone(Context c, boolean done) {
        prefs(c).edit().putBoolean(KEY_GUIDE, done).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
