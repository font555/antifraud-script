package com.example.antifraud.ui;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.antifraud.R;
import com.example.antifraud.engine.AppLog;
import com.example.antifraud.engine.SettingsStore;
import com.example.antifraud.engine.PathRecorder;
import com.example.antifraud.engine.ScriptRepository;
import com.example.antifraud.model.Script;

import java.io.File;
import java.util.List;

/**
 * 复盘页：作品的"灵魂"。逐节点输出：你选了什么 → 风险 → 为什么 → 损失金额。
 * 最后和真实案件的金额对照。数字会滚动增长，卡片依次滑入。
 */
public class ReviewActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review);
        Ui.insetContent(this);

        LinearLayout root = findViewById(R.id.ll_root);

        final String scriptId = getIntent().getStringExtra("script_id");
        String endingTitle = getIntent().getStringExtra("ending_title");
        Script script = ScriptRepository.load(this, scriptId);

        PathRecorder rec = PathRecorder.get();
        List<PathRecorder.Step> steps = rec.getSteps();
        int total = rec.totalCost();
        AppLog.i("REVIEW", "进入复盘 剧本 " + scriptId + " 损失 ￥" + total
                + " 步数 " + steps.size() + " 高风险 " + rec.highRiskCount() + " 次");

        // 0) 顶部返回栏
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.text(this, "←", 22, R.color.ink);
        back.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                overridePendingTransition(R.anim.page_in, R.anim.page_out);
            }
        });
        topBar.addView(back);
        topBar.addView(Ui.text(this, "复盘报告", 22, R.color.ink));
        root.addView(topBar);

        // 标题：有结局节点就用结局标题；没有（剧本缺跳转、真的中途退出）用兜底文案
        String reportTitle = (endingTitle == null || endingTitle.trim().isEmpty())
                ? "你走完了这段剧本" : endingTitle;
        TextView sub = Ui.text(this, reportTitle, 14, R.color.ink_secondary);
        sub.setPadding(0, Ui.dp(this, 8), 0, 0);
        sub.setLineSpacing(Ui.dp(this, 3), 1f);
        root.addView(sub);

        // 1) 结算卡：你的损失 vs 真实案件
        LinearLayout card = Ui.card(this, 16, 12);
        final TextView big = Ui.text(this,
                total > 0 ? "你损失 ¥0" : "你成功止损，损失 ¥0",
                26, total > 0 ? R.color.warn_red : R.color.risk_low);
        card.addView(big);

        if (script != null && script.referenceLoss > 0) {
            TextView compare = Ui.text(this,
                    script.referenceNote + "\n真实案件损失 ¥" + Ui.money(script.referenceLoss),
                    12, R.color.ink_secondary);
            compare.setPadding(0, Ui.dp(this, 8), 0, 0);
            compare.setLineSpacing(Ui.dp(this, 4), 1f);
            card.addView(compare);
        }

        TextView stat = Ui.text(this,
                "共 " + steps.size() + " 步  ·  高风险 " + rec.highRiskCount()
                        + " 次  ·  及时止损 " + rec.lowRiskCount() + " 次",
                10, R.color.ink_hint);
        stat.setPadding(0, Ui.dp(this, 10), 0, 0);
        card.addView(stat);
        root.addView(card);

        // 数字滚动
        ValueAnimator va = ValueAnimator.ofInt(0, total);
        va.setDuration(700);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                int v = (int) a.getAnimatedValue();
                big.setText("你损失 ¥" + Ui.money(v));
            }
        });
        va.start();

        // 2) 逐节点时间轴：一张张滑进来
        for (int i = 0; i < steps.size(); i++) {
            View item = buildStep(steps.get(i));
            item.setAlpha(0f);
            item.setTranslationX(Ui.dp(this, 28));
            item.animate().alpha(1f).translationX(0f)
                    .setDuration(220)
                    .setStartDelay(320 + i * 70)
                    .start();
            root.addView(item);
        }

        // 3) 底部按钮
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(0, Ui.dp(this, 8), 0, SettingsStore.scaleBox(this, Ui.dp(this, 24)));

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
                AppLog.i("REVIEW", "重玩另一条路 剧本 " + scriptId);
                // 先结束复盘页再开聊天页：返回时回到剧本选择页，不会残留旧页面层级；
                // CLEAR_TOP 让栈里可能存在的旧聊天页被清掉，按新一局重新创建。
                Intent i = new Intent(ReviewActivity.this, ChatActivity.class);
                i.putExtra("script_id", scriptId);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                finish();
                startActivity(i);
            }
        }));

        // 生成分享海报：Canvas 画图后经 PosterProvider 走系统分享，不需要任何权限
        PTextView shareBtn = new PTextView(this);
        shareBtn.setText("生成分享海报");
        shareBtn.setTextSize(14);
        shareBtn.setGravity(Gravity.CENTER);
        int sharePad = SettingsStore.scaleBox(this, Ui.dp(this, 14));
        shareBtn.setPadding(sharePad, sharePad, sharePad, sharePad);
        shareBtn.setTextColor(getColor(R.color.ink));
        shareBtn.setBackgroundResource(R.drawable.px_option);
        shareBtn.setClickable(true);
        LinearLayout.LayoutParams shareLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        shareLp.topMargin = Ui.dp(this, 8);
        shareBtn.setLayoutParams(shareLp);
        shareBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppLog.i("REVIEW", "生成分享海报 损失 ￥" + total + " 步数 " + steps.size());
                sharePoster(script, steps, total);
            }
        });
        actions.addView(shareBtn);

        root.addView(actions);
    }

    private View buildStep(final PathRecorder.Step s) {
        LinearLayout card = Ui.card(this, 14, 10);

        card.addView(Ui.text(this, "第 " + s.index + " 步"
                + (s.stageName.isEmpty() ? "" : "  ·  " + s.stageName), 10, R.color.ink_hint));

        TextView pick = Ui.text(this, "你选了「" + s.choiceText + "」", 14, R.color.ink);
        pick.setPadding(0, Ui.dp(this, 6), 0, 0);
        pick.setLineSpacing(Ui.dp(this, 3), 1f);
        card.addView(pick);

        LinearLayout tagRow = new LinearLayout(this);
        tagRow.setOrientation(LinearLayout.HORIZONTAL);
        tagRow.setGravity(Gravity.CENTER_VERTICAL);
        tagRow.setPadding(0, Ui.dp(this, 8), 0, 0);
        tagRow.addView(Ui.riskChip(this, s.risk));
        if (s.tag != null && !s.tag.isEmpty()) {
            tagRow.addView(Ui.text(this, "  " + s.tag, 10, R.color.ink_secondary));
        }
        if (s.cost > 0) {
            tagRow.addView(Ui.text(this, "   -¥" + Ui.money(s.cost), 10, R.color.warn_red));
        }
        card.addView(tagRow);

        final TextView why = Ui.text(this, s.feedback, 12, R.color.ink_secondary);
        why.setPadding(0, Ui.dp(this, 8), 0, 0);
        why.setLineSpacing(Ui.dp(this, 4), 1f);
        card.addView(why);

        // 低风险步骤默认折叠，点一下展开
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
        TextView btn = new PTextView(this);
        btn.setText(label);
        btn.setTextSize(17);
        btn.setGravity(Gravity.CENTER);
        int p = SettingsStore.scaleBox(this, Ui.dp(this, 14));
        btn.setPadding(p, p, p, p);
        btn.setClickable(true);
        if (primary) {
            btn.setTextColor(getColor(R.color.white));
            btn.setBackgroundResource(R.drawable.px_button);
        } else {
            btn.setTextColor(getColor(R.color.ink));
            btn.setBackgroundResource(R.drawable.px_option);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 8);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(l);
        return btn;
    }

    /** 生成 1080×1350 复盘海报，再以 content:// 交给系统分享面板 */
    private void sharePoster(Script script, List<PathRecorder.Step> steps, int total) {
        File file = SharePoster.generate(this, script, steps, total,
                script == null ? "防骗复盘" : script.title);
        if (file == null) {
            Toast.makeText(this, "海报生成失败，请稍后再试", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = Uri.parse("content://com.example.antifraud.poster/poster.png");
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("image/png");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.setClipData(ClipData.newUri(getContentResolver(), "复盘海报", uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, "分享复盘海报"));
    }
}
