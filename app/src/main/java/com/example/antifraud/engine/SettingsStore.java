package com.example.antifraud.engine;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 显示设置：字体样式 + 字号。只存本机，不联网。
 */
public class SettingsStore {

    private static final String PREF = "antifraud_settings";
    private static final String KEY_FONT_STYLE = "font_style";
    private static final String KEY_FONT_SCALE = "font_scale";
    private static final String KEY_GUIDE = "guide_done";

    public static final int FONT_PIXEL = 0;
    public static final int FONT_SYSTEM = 1;

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
        return prefs(c).getFloat(KEY_FONT_SCALE, 1.0f);
    }

    public static void setFontScale(Context c, float scale) {
        prefs(c).edit().putFloat(KEY_FONT_SCALE, scale).apply();
        changed = true;
    }

    public static String fontScaleLabel(Context c) {
        float s = fontScale(c);
        if (s >= 1.28f) {
            return "特大";
        }
        if (s >= 1.12f) {
            return "大";
        }
        return "标准";
    }

public static boolean guideDone(Context c) {
        return prefs(c).getBoolean(KEY_GUIDE, false);
    }

    public static void setGuideDone(Context c) {
        prefs(c).edit().putBoolean(KEY_GUIDE, true).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
