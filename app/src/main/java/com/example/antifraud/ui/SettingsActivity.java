package com.example.antifraud.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.antifraud.R;
import com.example.antifraud.engine.SettingsStore;
import com.example.antifraud.engine.UserStore;

/**
 * 设置页：字体样式（像素体 / 系统体）、字号、清除本地记录。
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

        root.addView(Ui.text(this, "设置", 22, R.color.ink));

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
        LinearLayout sizeRow = new LinearLayout(this);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        sizeRow.setPadding(0, Ui.dp(this, 10), 0, 0);
        float curScale = SettingsStore.fontScale(this);
        sizeRow.addView(sizeChip("标准", Math.abs(curScale - 1.0f) < 0.02f, 1.0f));
        sizeRow.addView(sizeChip("大", Math.abs(curScale - 1.15f) < 0.02f, 1.15f));
        sizeRow.addView(sizeChip("特大", Math.abs(curScale - 1.3f) < 0.02f, 1.3f));
        sizeCard.addView(sizeRow);
        TextView sizeHint = Ui.text(this, "调大后所有页面的文字都会跟着变大。", 9, R.color.ink_hint);
        sizeHint.setPadding(0, Ui.dp(this, 8), 0, 0);
        sizeCard.addView(sizeHint);
        root.addView(sizeCard);

        // ---- 清除本地记录 ----
        LinearLayout dataCard = Ui.card(this, 14, 12);
        TextView clear = Ui.text(this, "清除本地记录", 12, R.color.warn_red);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClear();
            }
        });
        dataCard.addView(clear);
        TextView dataHint = Ui.text(this, "清空昵称、头像和阅读记录；剧本与来源不受影响。", 9, R.color.ink_hint);
        dataHint.setPadding(0, Ui.dp(this, 8), 0, 0);
        dataHint.setLineSpacing(Ui.dp(this, 3), 1f);
        dataCard.addView(dataHint);
        root.addView(dataCard);

        // ---- 关于 ----
        LinearLayout about = Ui.card(this, 14, 0);
        about.addView(Ui.text(this, "关于", 12, R.color.ink));
        TextView a1 = Ui.text(this, "反诈剧本杀 · 教学演示作品\n世界职业院校技能大赛 · 新一代信息技术赛道 · 中职组",
                9, R.color.ink_secondary);
        a1.setPadding(0, Ui.dp(this, 8), 0, 0);
        a1.setLineSpacing(Ui.dp(this, 4), 1f);
        about.addView(a1);
        TextView a2 = Ui.text(this, "零权限 · 不联网 · 不收集任何个人信息\n字体 Fusion Pixel Font（MIT）；剧情据公开报道改编",
                9, R.color.ink_hint);
        a2.setPadding(0, Ui.dp(this, 8), 0, 0);
        a2.setLineSpacing(Ui.dp(this, 4), 1f);
        about.addView(a2);
        root.addView(about);
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f);
        lp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
        tv.setLayoutParams(lp);
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

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("清除本地记录？")
                .setMessage("昵称、头像和阅读记录会被清空，剧本内容不受影响。")
                .setPositiveButton("清除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        UserStore.reset(SettingsActivity.this);
                        Toast.makeText(SettingsActivity.this, "已清除", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
