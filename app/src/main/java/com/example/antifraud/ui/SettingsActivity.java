package com.example.antifraud.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.antifraud.R;
import com.example.antifraud.engine.SettingsStore;

/**
 * 字体与字号页：只放「字体样式」和「字号大小」两件事。\n * 清除记录、关于等入口都在「我的」页，这里不再重复。
 * 全部存在本机 SharedPreferences，不联网。
 */
public class SettingsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Ui.insetContent(this);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                overridePendingTransition(R.anim.page_in, R.anim.page_out);
            }
        });

        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        LinearLayout root = findViewById(R.id.ll_root);
        root.removeAllViews();

        root.addView(Ui.text(this, "字体与字号", 22, R.color.ink));

        // ---- 字体样式 ----
        LinearLayout fontCard = scaledCard(14, 12);
        fontCard.addView(Ui.text(this, "字体样式", 12, R.color.ink));
        LinearLayout fontRow = new LinearLayout(this);
        fontRow.setOrientation(LinearLayout.HORIZONTAL);
        fontRow.setPadding(0, SettingsStore.scaleBox(this, Ui.dp(this, 10)), 0, 0);
        int cur = SettingsStore.fontStyle(this);
        fontRow.addView(fontChip("像素字体", SettingsStore.FONT_PIXEL));
        fontRow.addView(fontChip("系统字体", SettingsStore.FONT_SYSTEM));
        fontCard.addView(fontRow);
        TextView fontHint = Ui.text(this, "像素字体有游戏感；看久了觉得累眼，就切到系统字体。", 9, R.color.ink_hint);
        fontHint.setPadding(0, SettingsStore.scaleBox(this, Ui.dp(this, 8)), 0, 0);
        fontHint.setLineSpacing(SettingsStore.scaleBox(this, Ui.dp(this, 3)), 1f);
        fontCard.addView(fontHint);
        root.addView(fontCard);

        // ---- 字号 ----
        LinearLayout sizeCard = scaledCard(14, 12);
        sizeCard.addView(Ui.text(this, "字号大小", 12, R.color.ink));
        HorizontalScrollView sizeScroll = new HorizontalScrollView(this);
        sizeScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scrollLp.topMargin = SettingsStore.scaleBox(this, Ui.dp(this, 10));
        sizeScroll.setLayoutParams(scrollLp);
        LinearLayout sizeRow = new LinearLayout(this);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        float curScale = SettingsStore.fontScale(this);
        // 四档：标准 1.0 / 大 1.3 / 特大 1.7 / 超大 2.1（超大 = 标准的两倍再多一点）
        sizeRow.addView(sizeChip("标准", near(curScale, SettingsStore.SCALE_STANDARD), SettingsStore.SCALE_STANDARD));
        sizeRow.addView(sizeChip("大", near(curScale, SettingsStore.SCALE_LARGE), SettingsStore.SCALE_LARGE));
        sizeRow.addView(sizeChip("特大", near(curScale, SettingsStore.SCALE_XLARGE), SettingsStore.SCALE_XLARGE));
        sizeRow.addView(sizeChip("超大", near(curScale, SettingsStore.SCALE_HUGE), SettingsStore.SCALE_HUGE));
        sizeScroll.addView(sizeRow);
        sizeCard.addView(sizeScroll);
        TextView sizeHint = Ui.text(this, "调大后所有页面的文字和按钮都会跟着变大；「超大」约为标准的两倍，适合视力不便的使用者。", 9, R.color.ink_hint);
        sizeHint.setPadding(0, SettingsStore.scaleBox(this, Ui.dp(this, 8)), 0, 0);
        sizeHint.setLineSpacing(SettingsStore.scaleBox(this, Ui.dp(this, 3)), 1f);
        sizeCard.addView(sizeHint);
        root.addView(sizeCard);
    }

    /**
     * 白色卡片，内边距与下边距按字号放大。
     * Ui.card() 是全局共用的，不改它；这里在它的结果上再套一层 scaleBox。
     */
    private LinearLayout scaledCard(float paddingDp, float marginBottomDp) {
        LinearLayout card = Ui.card(this, paddingDp, marginBottomDp);
        int p = SettingsStore.scaleBox(this, Ui.dp(this, paddingDp));
        card.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = SettingsStore.scaleBox(this, Ui.dp(this, marginBottomDp));
        card.setLayoutParams(lp);
        return card;
    }

    private TextView fontChip(String text, final int style) {
        final boolean selected = SettingsStore.fontStyle(this) == style;
        TextView tv = new PTextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setGravity(Gravity.CENTER);
        // 字体 chip：固定 44dp 高要装下放大后的 12sp，必须跟着字号长
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, SettingsStore.scaleBox(this, Ui.dp(this, 44)), 1f);
        int chipGap = SettingsStore.scaleBox(this, Ui.dp(this, 3));
        lp.setMargins(chipGap, 0, chipGap, 0);
        tv.setLayoutParams(lp);
        tv.setTextColor(getColor(selected ? R.color.white : R.color.ink));
        tv.setBackgroundResource(selected ? R.drawable.px_button : R.drawable.px_option);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SettingsStore.setFontStyle(SettingsActivity.this, style);
                recreate();
            }
        });
        return tv;
    }

    /** 判断当前倍数是否落在某一档（容忍 0.05 误差） */
    private boolean near(float cur, float target) {
        return Math.abs(cur - target) < 0.05f;
    }

    private TextView sizeChip(String text, boolean selected, final float scale) {
        TextView tv = new PTextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setGravity(Gravity.CENTER);
        // 字号 chip：高度放大，但左右内边距只放大到 1.4 倍。
        // 4 个档位要在一屏内排开，内边距若按 1.9 倍放大会把「超大」挤出屏幕外。
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                SettingsStore.scaleBox(this, Ui.dp(this, 44)));
        int chipGap = SettingsStore.scaleBox(this, Ui.dp(this, 3));
        lp.setMargins(chipGap, 0, chipGap, 0);
        tv.setLayoutParams(lp);
        int chipPadH = Math.round(Ui.dp(this, 18) * Math.min(1.4f, SettingsStore.fontScale(this)));
        tv.setPadding(chipPadH, 0, chipPadH, 0);
        tv.setMaxLines(1);
        tv.setTextColor(getColor(selected ? R.color.white : R.color.ink));
        tv.setBackgroundResource(selected ? R.drawable.px_button : R.drawable.px_option);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SettingsStore.setFontScale(SettingsActivity.this, scale);
                recreate();
            }
        });
        return tv;
    }
}
