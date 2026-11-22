package com.example.antifraud.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.antifraud.R;
import com.example.antifraud.engine.PathRecorder;
import com.example.antifraud.engine.ScriptRepository;
import com.example.antifraud.model.Script;

import java.util.List;

/**
 * 复盘页：作品的"灵魂"。逐节点输出：你选了什么 → 风险 → 为什么 → 损失金额。
 * 最后和真实案件的金额对照。
 */
public class ReviewActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review);

        LinearLayout root = findViewById(R.id.ll_root);

        final String scriptId = getIntent().getStringExtra("script_id");
        String endingTitle = getIntent().getStringExtra("ending_title");
        Script script = ScriptRepository.load(this, scriptId);

        PathRecorder rec = PathRecorder.get();
        List<PathRecorder.Step> steps = rec.getSteps();
        int total = rec.totalCost();

        // 1) 标题
        TextView head = Ui.text(this, "复盘报告", 22, R.color.text_primary);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(head);

        if (endingTitle != null && !endingTitle.isEmpty()) {
            TextView sub = Ui.text(this, endingTitle, 15, R.color.text_secondary);
            sub.setPadding(0, Ui.dp(this, 8), 0, 0);
            sub.setLineSpacing(Ui.dp(this, 3), 1f);
            root.addView(sub);
        }

        // 2) 结算卡：你的损失 vs 真实案件
        LinearLayout card = Ui.card(this, 16, 12);
        TextView big = Ui.text(this,
                total > 0 ? "你损失 ¥" + Ui.money(total) : "你成功止损，损失 ¥0",
                26, total > 0 ? R.color.warn_red : R.color.risk_low);
        big.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(big);

        if (script != null && script.referenceLoss > 0) {
            TextView compare = Ui.text(this,
                    script.referenceNote + "\n真实案件损失 ¥" + Ui.money(script.referenceLoss),
                    13, R.color.text_secondary);
            compare.setPadding(0, Ui.dp(this, 8), 0, 0);
            compare.setLineSpacing(Ui.dp(this, 4), 1f);
            card.addView(compare);
        }

        TextView stat = Ui.text(this,
                "共 " + steps.size() + " 步  ·  高风险 " + rec.highRiskCount()
                        + " 次  ·  及时止损 " + rec.lowRiskCount() + " 次",
                12, R.color.text_hint);
        stat.setPadding(0, Ui.dp(this, 10), 0, 0);
        card.addView(stat);
        root.addView(card);

        // 3) 逐节点时间轴
        for (PathRecorder.Step s : steps) {
            root.addView(buildStep(s));
        }

        // 4) 底部：看来源 / 重玩另一条路
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 24));

        actions.addView(actionButton("查看真实案例来源", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(ReviewActivity.this, SourcesActivity.class);
                i.putExtra("script_id", scriptId);
                startActivity(i);
            }
        }));

        actions.addView(actionButton("重玩另一条路", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(ReviewActivity.this, ChatActivity.class);
                i.putExtra("script_id", scriptId);
                startActivity(i);
                finish();
            }
        }));

        root.addView(actions);
    }

    private View buildStep(final PathRecorder.Step s) {
        LinearLayout card = Ui.card(this, 14, 10);

        TextView head = Ui.text(this, "第 " + s.index + " 步"
                + (s.stageName.isEmpty() ? "" : "  ·  " + s.stageName), 11, R.color.text_hint);
        card.addView(head);

        TextView pick = Ui.text(this, "你选了「" + s.choiceText + "」", 15, R.color.text_primary);
        pick.setTypeface(Typeface.DEFAULT_BOLD);
        pick.setPadding(0, Ui.dp(this, 4), 0, 0);
        pick.setLineSpacing(Ui.dp(this, 3), 1f);
        card.addView(pick);

        LinearLayout tagRow = new LinearLayout(this);
        tagRow.setOrientation(LinearLayout.HORIZONTAL);
        tagRow.setGravity(Gravity.CENTER_VERTICAL);
        tagRow.setPadding(0, Ui.dp(this, 8), 0, 0);
        tagRow.addView(Ui.riskChip(this, s.risk));
        if (s.tag != null && !s.tag.isEmpty()) {
            tagRow.addView(Ui.text(this, "  " + s.tag, 12, R.color.text_secondary));
        }
        if (s.cost > 0) {
            tagRow.addView(Ui.text(this, "   -¥" + Ui.money(s.cost), 12, R.color.warn_red));
        }
        card.addView(tagRow);

        final TextView why = Ui.text(this, s.feedback, 13, R.color.text_secondary);
        why.setPadding(0, Ui.dp(this, 8), 0, 0);
        why.setLineSpacing(Ui.dp(this, 4), 1f);
        card.addView(why);

        // 低风险步骤默认折叠，点一下展开（复盘不啰嗦）
        if ("low".equals(s.risk)) {
            why.setVisibility(View.GONE);
            card.setClickable(true);
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean show = why.getVisibility() != View.VISIBLE;
                    why.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });
        }
        return card;
    }

    private View actionButton(String label, boolean primary, View.OnClickListener l) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextSize(15);
        btn.setGravity(Gravity.CENTER);
        int p = Ui.dp(this, 14);
        btn.setPadding(p, p, p, p);
        btn.setClickable(true);
        if (primary) {
            btn.setTextColor(getColor(R.color.card_white));
            btn.setBackgroundColor(getColor(R.color.brand_blue));
        } else {
            btn.setTextColor(getColor(R.color.brand_blue));
            btn.setBackgroundResource(R.drawable.bg_option);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 8);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(l);
        return btn;
    }
}
