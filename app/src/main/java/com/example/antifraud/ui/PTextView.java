package com.example.antifraud.ui;

import android.content.Context;
import android.util.AttributeSet;

/**
 * 自动套用像素字体的 TextView。布局里把 <TextView> 换成 <com.example.antifraud.ui.PTextView> 即可。
 */
public class PTextView extends android.widget.TextView {

    public PTextView(Context c) {
        super(c);
        init();
    }

    public PTextView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    public PTextView(Context c, AttributeSet a, int s) {
        super(c, a, s);
        init();
    }

    private void init() {
        setTypeface(Typefaces.pixel(getContext()));
        setIncludeFontPadding(false);
        // 像素字体：关抗锯齿、加粗，边缘才是硬像素块；系统字体保持正常渲染
        if (Typefaces.isPixel(getContext())) {
            getPaint().setAntiAlias(false);
            getPaint().setFakeBoldText(true);
            getPaint().setSubpixelText(false);
        }
    }
}
