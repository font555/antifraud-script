package com.example.antifraud.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.antifraud.R;
import com.example.antifraud.engine.ScriptRepository;
import com.example.antifraud.model.Script;

import java.util.List;

/**
 * 首页：列出 assets/scripts 下所有剧本。
 * 新增一个 json 文件，这里会自动多一张卡片，不用改代码。
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        LinearLayout box = findViewById(R.id.ll_scripts);
        List<Script> scripts = ScriptRepository.loadAll(this);

        if (scripts.isEmpty()) {
            TextView tv = Ui.text(this, "没有找到剧本，请检查 assets/scripts 目录", 14, R.color.warn_red);
            box.addView(tv);
        }

        for (Script s : scripts) {
            box.addView(buildCard(s));
        }

        findViewById(R.id.btn_all_sources).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 不传 script_id 表示"汇总所有剧本的来源"
                startActivity(new Intent(MainActivity.this, SourcesActivity.class));
            }
        });
    }

    private View buildCard(final Script s) {
        LinearLayout card = Ui.card(this, 16, 12);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView type = Ui.text(this, s.scamType, 12, R.color.brand_blue);
        top.addView(type);

        TextView dot = Ui.text(this, "   ·   预计 " + s.minutes + " 分钟", 12, R.color.text_hint);
        top.addView(dot);
        card.addView(top);

        TextView title = Ui.text(this, s.title, 20, R.color.text_primary);
        title.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(title);

        TextView intro = Ui.text(this, s.intro, 13, R.color.text_secondary);
        intro.setPadding(0, Ui.dp(this, 6), 0, 0);
        intro.setMaxLines(3);
        card.addView(intro);

        TextView stats = Ui.text(this,
                s.nodes.size() + " 个节点 · " + s.sources.size() + " 处真实来源",
                12, R.color.text_hint);
        stats.setPadding(0, Ui.dp(this, 10), 0, 0);
        card.addView(stats);

        TextView go = Ui.text(this, "开始 →", 14, R.color.brand_blue);
        go.setGravity(Gravity.END);
        go.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(go);

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, ChatActivity.class);
                i.putExtra("script_id", s.id);
                startActivity(i);
            }
        });
        return card;
    }
}
