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
        LinearLayout fontCard = Ui.card(this, 14, 12);
        fontCard.addView(Ui.text(this, "字体样式", 12, R.color.ink));
        LinearLayout fontRow = new LinearLayout(this);
        fontRow.setOrientation(LinearLayout.HORIZONTAL);
        fontRow.setPadding(0, Ui.dp(this, 10), 0, 0);
        int cur = SettingsStore.fontStyle(this);
        fontRow.addView(fontChip("像素字体", SettingsStore.FONT_PIXEL));
        fontRow.addView(fontChip("系统字体", SettingsStore.FONT_SYSTEM));
        fontCard.addView(fontRow);
        TextView fontHint = Ui.text(this, "像素字体有游戏感；看久了觉得累眼，就切到系统字体。", 9, R.color.ink_hint);
        fontHint.setPadding(0, Ui.dp(this, 8), 0, 0);
        fontHint.setLineSpacing(Ui.dp(this, 3), 1f);
        fontCard.addView(fontHint);
        root.addView(fontCard);

        // ---- 字号 ----
        LinearLayout sizeCard = Ui.card(this, 14, 12);
        sizeCard.addView(Ui.text(this, "字号大小", 12, R.color.ink));
        HorizontalScrollView sizeScroll = new HorizontalScrollView(this);
        sizeScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scrollLp.topMargin = Ui.dp(this, 10);
        sizeScroll.setLayoutParams(scrollLp);
        LinearLayout sizeRow = new LinearLayout(this);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        float curScale = SettingsStore.fontScale(this);
        sizeRow.addView(sizeChip("标准", Math.abs(curScale - 1.0f) < 0.02f, 1.0f));
        sizeRow.addView(sizeChip("大", Math.abs(curScale - 1.15f) < 0.02f, 1.15f));
        sizeRow.addView(sizeChip("特大", Math.abs(curScale - 1.3f) < 0.02f, 1.3f));
        sizeRow.addView(sizeChip("超大", Math.abs(curScale - 1.55f) < 0.02f, 1.55f));
        sizeScroll.addView(sizeRow);
        sizeCard.addView(sizeScroll);
        TextView sizeHint = Ui.text(this, "调大后所有页面的文字都会跟着变大；觉得还小可以用「超大」。", 9, R.color.ink_hint);
        sizeHint.setPadding(0, Ui.dp(this, 8), 0, 0);
        sizeHint.setLineSpacing(Ui.dp(this, 3), 1f);
        sizeCard.addView(sizeHint);
        root.addView(sizeCard);
    }

    private TextView fontChip(String text, final int style) {
        final boolean selected = SettingsStore.fontStyle(this) == style;
        TextView tv = new PTextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f);
        lp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
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

    private TextView sizeChip(String text, boolean selected, final float scale) {
        TextView tv = new PTextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 44));
        lp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
        tv.setLayoutParams(lp);
        tv.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
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
