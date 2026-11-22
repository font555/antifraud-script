package com.example.antifraud.ui;

import android.content.Context;
import android.graphics.Typeface;

import com.example.antifraud.engine.SettingsStore;

/**
 * 字体来源：像素字体（Fusion Pixel Font，MIT）或系统字体。
 * 用户可在"设置"里切换；切换后下次创建的文字立即生效。
 */
public final class Typefaces {

    private static Typeface pixel;

    private Typefaces() {
    }

    public static Typeface pixel(Context c) {
        if (SettingsStore.fontStyle(c) == SettingsStore.FONT_SYSTEM) {
            return Typeface.DEFAULT;
        }
        if (pixel == null) {
            try {
                pixel = Typeface.createFromAsset(c.getAssets(), "fonts/fusion_pixel.ttf");
            } catch (Exception e) {
                pixel = Typeface.DEFAULT;
            }
        }
        return pixel == null ? Typeface.DEFAULT : pixel;
    }

    /** 系统字体模式不需要关闭抗锯齿、也不需要加粗 */
    public static boolean isPixel(Context c) {
        return SettingsStore.fontStyle(c) != SettingsStore.FONT_SYSTEM;
    }
}
