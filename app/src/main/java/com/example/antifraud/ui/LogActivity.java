package com.example.antifraud.ui;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.antifraud.R;
import com.example.antifraud.engine.AppLog;

/**
 * 运行日志页。藏得比较深：设置 → 「关于」标题上长按 2 秒才会进来。
 * 只读本机日志，可复制、清空。
 */
public class LogActivity extends BaseActivity {

    private TextView tvLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.paper));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        root.setPadding(p, p, p, p);
        scroll.addView(root);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.text(this, "← 返回", 12, R.color.ink_secondary);
        back.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        top.addView(back);
        TextView title = Ui.text(this, "运行日志", 16, R.color.ink);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(tlp);
        title.setGravity(Gravity.END);
        top.addView(title);
        root.addView(top);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 10));

        TextView refresh = chip("刷新");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvLog.setText(AppLog.dump());
            }
        });
        actions.addView(refresh);

        TextView copy = chip("复制全部");
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                        getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("反诈日志", AppLog.dump()));
                Toast.makeText(LogActivity.this, "已复制，可粘贴到聊天里", Toast.LENGTH_SHORT).show();
            }
        });
        actions.addView(copy);

        TextView clear = chip("清空");
        clear.setTextColor(getColor(R.color.warn_red));
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppLog.clear();
                tvLog.setText(AppLog.dump());
            }
        });
        actions.addView(clear);
        root.addView(actions);

        tvLog = new TextView(this);
        Ui.pixel(tvLog);
        tvLog.setTextSize(9);
        tvLog.setTextColor(getColor(R.color.ink_secondary));
        tvLog.setLineSpacing(Ui.dp(this, 3), 1f);
        tvLog.setTextIsSelectable(true);
        tvLog.setMovementMethod(new ScrollingMovementMethod());
        tvLog.setText(AppLog.dump());
        root.addView(tvLog);

        setContentView(scroll);
        AppLog.i("UI", "打开运行日志");
    }

    private TextView chip(String text) {
        TextView tv = new PTextView(this);
        tv.setText(text);
        tv.setTextSize(11);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(getColor(R.color.ink));
        tv.setBackgroundResource(R.drawable.px_option);
        tv.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
        tv.setLayoutParams(lp);
        return tv;
    }
}
