package com.example.antifraud.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.antifraud.R;
import com.example.antifraud.engine.ScriptRepository;
import com.example.antifraud.model.Script;

import java.util.List;

/**
 * 翻开一本书之后的"书页"：列出这个骗局分类下的剧本，点一下直接进对话。
 */
public class ScriptPickerActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_picker);
        Ui.insetContent(this);

        final String categoryId = getIntent().getStringExtra("category_id");
        String categoryName = getIntent().getStringExtra("category_name");

        TextView title = findViewById(R.id.tv_title);
        TextView sub = findViewById(R.id.tv_sub);
        title.setText(categoryName == null ? "这本书" : categoryName);

        List<Script> list = ScriptRepository.loadByCategory(this, categoryId);
        sub.setText(list.isEmpty() ? "这一册还在写" : "共 " + list.size() + " 个剧本，选一个开始");

        LinearLayout box = findViewById(R.id.ll_scripts);
        box.removeAllViews();

        if (list.isEmpty()) {
            box.addView(hintCard("这一册还在写，先去别的书架逛逛吧。"));
        }
        for (int i = 0; i < list.size(); i++) {
            box.addView(buildCard(list.get(i), i));
        }
        if (!list.isEmpty()) {
            box.addView(buildFooter(list));
        }

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                overridePendingTransition(R.anim.page_in, R.anim.page_out);
            }
        });

        // 书页翻开：整页绕 Y 轴从侧后方转正
        ScrollView sv = findViewById(R.id.sv);
        sv.setAlpha(0f);
        sv.setTranslationY(Ui.dp(this, 26));
        sv.animate().alpha(1f).translationY(0f).setDuration(280).start();
    }

    /** 底部说明：填满书页下方空白，也把"可溯源"这件事再强调一次 */
    private View buildFooter(List<Script> list) {
        int sources = 0;
        int nodes = 0;
        for (Script s : list) {
            sources += s.sources.size();
            nodes += s.nodes.size();
        }
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.px_panel_soft);
        int p = Ui.dp(this, 14);
        card.setPadding(p, p, p, p);

        TextView t1 = new PTextView(this);
        t1.setText("本册共 " + list.size() + " 个剧本 · " + nodes + " 个剧情节点 · " + sources + " 处真实来源");
        t1.setTextSize(12);
        t1.setTextColor(getColor(R.color.ink_secondary));
        t1.setLineSpacing(Ui.dp(this, 3), 1f);
        card.addView(t1);

        TextView t2 = new PTextView(this);
        t2.setText("所有剧情据公开报道改编，人物已匿名、平台名虚构。\n每个剧本结束后都会给出逐节点复盘和案例出处。");
        t2.setTextSize(11);
        t2.setTextColor(getColor(R.color.ink_hint));
        t2.setLineSpacing(Ui.dp(this, 4), 1f);
        t2.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(t2);
        return card;
    }

    private View hintCard(String text) {
        TextView tv = new PTextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(getColor(R.color.ink_secondary));
        tv.setBackgroundResource(R.drawable.px_panel);
        tv.setPadding(Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18));
        tv.setLineSpacing(Ui.dp(this, 4), 1f);
        return tv;
    }

    private View buildCard(final Script s, final int position) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.px_option);
        int p = Ui.dp(this, 16);
        card.setPadding(p, p, p, p);
        card.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 14);
        card.setLayoutParams(lp);

        TextView tag = new PTextView(this);
        tag.setText(s.scamType + "  ·  预计 " + s.minutes + " 分钟");
        tag.setTextSize(12);
        tag.setTextColor(getColor(R.color.brand));
        card.addView(tag);

        TextView title = new PTextView(this);
        title.setText(s.title);
        title.setTextSize(21);
        title.setTextColor(getColor(R.color.ink));
        title.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(title);

        TextView intro = new PTextView(this);
        intro.setText(s.intro);
        intro.setTextSize(13);
        intro.setTextColor(getColor(R.color.ink_secondary));
        intro.setLineSpacing(Ui.dp(this, 4), 1f);
        intro.setPadding(0, Ui.dp(this, 8), 0, 0);
        intro.setMaxLines(3);
        card.addView(intro);

        TextView stat = new PTextView(this);
        stat.setText(s.nodes.size() + " 个节点  ·  " + s.sources.size() + " 处真实来源");
        stat.setTextSize(11);
        stat.setTextColor(getColor(R.color.ink_hint));
        stat.setPadding(0, Ui.dp(this, 10), 0, 0);
        card.addView(stat);

        TextView go = new PTextView(this);
        go.setText("开始阅读 →");
        go.setTextSize(14);
        go.setTextColor(getColor(R.color.brand));
        go.setGravity(Gravity.END);
        go.setPadding(0, Ui.dp(this, 10), 0, 0);
        card.addView(go);

        card.setAlpha(0f);
        card.setTranslationX(Ui.dp(this, 30));
        card.animate().alpha(1f).translationX(0f).setDuration(240).setStartDelay(position * 70).start();

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                Intent i = new Intent(ScriptPickerActivity.this, ChatActivity.class);
                                i.putExtra("script_id", s.id);
                                startActivity(i);
                                overridePendingTransition(R.anim.page_in, R.anim.page_out);
                            }
                        }).start();
            }
        });
        return card;
    }
}
