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
 */
public final class Ui {

    private Ui() {
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static TextView text(Context c, String s, float sp, int colorRes) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(sp);
        tv.setTextColor(c.getColor(colorRes));
        tv.setTypeface(Typefaces.pixel(c));
        tv.setIncludeFontPadding(false);
        tv.getPaint().setAntiAlias(false);
        tv.getPaint().setFakeBoldText(true);
        tv.getPaint().setSubpixelText(false);
        return tv;
    }

    /** 白色圆角卡片，带下边距 */
    public static LinearLayout card(Context c, float paddingDp, float marginBottomDp) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(com.example.antifraud.R.drawable.px_panel);
        int p = dp(c, paddingDp);
        box.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, marginBottomDp);
        box.setLayoutParams(lp);
        return box;
    }

    /** 风险标签：高风险 / 中风险 / 低风险 */
    public static TextView riskChip(Context c, String risk) {
        String label;
        int bg;
        int fg;
        if ("high".equals(risk)) {
            label = "高风险";
            bg = 0xFFFDECEA;
            fg = 0xFFD93025;
        } else if ("medium".equals(risk)) {
            label = "中风险";
            bg = 0xFFFDF3E3;
            fg = 0xFFC77700;
        } else {
            label = "低风险";
            bg = 0xFFE9F5EC;
            fg = 0xFF2E7D32;
        }
        TextView tv = new TextView(c);
        tv.setText(label);
        tv.setTextSize(13);
        tv.setTextColor(fg);
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(c, 5));
        gd.setColor(bg);
        tv.setBackground(gd);
        tv.setPadding(dp(c, 6), dp(c, 2), dp(c, 6), dp(c, 2));
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    /** 金额格式化：50400 -> "50,400" */
    public static String money(int v) {
        return String.format("%,d", v);
    }

    /** 给任意 TextView 套上像素字体（关闭抗锯齿，边缘才硬） */
    public static void pixel(TextView tv) {
        tv.setTypeface(Typefaces.pixel(tv.getContext()));
        tv.setIncludeFontPadding(false);
        tv.getPaint().setAntiAlias(false);
        tv.getPaint().setFakeBoldText(true);
        tv.getPaint().setSubpixelText(false);
    }

    /**
     * Android 15+ 强制边到边，状态栏会盖住内容。这里给内容区补顶部内边距。
     * 各 Activity 在 setContentView 之后调用一次即可。
     */
    public static void insetContent(final android.app.Activity activity) {
        final View content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        final int pl = content.getPaddingLeft();
        final int pt = content.getPaddingTop();
        final int pr = content.getPaddingRight();
        final int pb = content.getPaddingBottom();
        content.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets insets) {
                int top;
                int bottom;
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                    top = bars.top;
                    bottom = bars.bottom;
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                }
                v.setPadding(pl, pt + top, pr, pb + bottom);
                return insets;
            }
        });
        content.requestApplyInsets();
    }

    /** 12 号灰色小字，用于各种说明 */
    public static TextView hint(Context c, String s) {
        TextView tv = text(c, s, 12, com.example.antifraud.R.color.ink_hint);
        tv.setLineSpacing(dp(c, 3), 1f);
        return tv;
    }
}
