package com.example.antifraud.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.antifraud.R;
import com.example.antifraud.engine.PathRecorder;
import com.example.antifraud.engine.ScriptRepository;
import com.example.antifraud.model.Choice;
import com.example.antifraud.model.Script;
import com.example.antifraud.model.StoryNode;

/**
 * 聊天页：整个作品的核心。逐节点渲染消息和选项，选择后继续。
 * 注意：这里绝不显示风险颜色，风险只在复盘页出现，否则没有悬念。
 */
public class ChatActivity extends Activity {

    private Script script;
    private StoryNode current;
    private final PathRecorder recorder = PathRecorder.get();

    private TextView tvTitle;
    private TextView tvStage;
    private TextView tvLoss;
    private ProgressBar pbStage;
    private ScrollView svMessages;
    private LinearLayout llMessages;
    private LinearLayout llOptions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        tvTitle = findViewById(R.id.tv_title);
        tvStage = findViewById(R.id.tv_stage);
        tvLoss = findViewById(R.id.tv_loss);
        pbStage = findViewById(R.id.pb_stage);
        svMessages = findViewById(R.id.sv_messages);
        llMessages = findViewById(R.id.ll_messages);
        llOptions = findViewById(R.id.ll_options);

        String id = getIntent().getStringExtra("script_id");
        script = ScriptRepository.load(this, id);
        if (script == null) {
            Toast.makeText(this, "剧本加载失败：" + id, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        tvTitle.setText(script.title);
        pbStage.setMax(script.stageCount);

        findViewById(R.id.btn_restart).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmRestart();
            }
        });

        startScript();
    }

    /** 重新开始（也用于"重来"按钮） */
    private void startScript() {
        recorder.reset();
        llMessages.removeAllViews();
        llOptions.removeAllViews();
        updateLoss();

        current = script.nodes.get(script.startNode);
        if (current == null) {
            Toast.makeText(this, "起始节点不存在：" + script.startNode, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        renderNode(current);
    }

    /** 渲染一个节点：先追加消息气泡，再渲染选项 */
    private void renderNode(StoryNode node) {
        tvStage.setText("第 " + node.stage + " 幕 / 共 " + script.stageCount + " 幕"
                + (node.stageName.isEmpty() ? "" : "  ·  " + node.stageName));
        pbStage.setProgress(node.stage);

        boolean system = "系统".equals(node.speaker);
        for (String m : node.messages) {
            addBubble(node.speaker, m, system ? 2 : 0);
        }

        llOptions.removeAllViews();
        if (node.isEnd) {
            addEndingCard(node);
        } else {
            for (int i = 0; i < node.options.size(); i++) {
                addOption(i + 1, node.options.get(i));
            }
        }
        scrollToBottom();
    }

    /** kind: 0 = 对方，1 = 我，2 = 系统 */
    private void addBubble(String speaker, String text, int kind) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = Ui.dp(this, 8);
        row.setLayoutParams(rlp);
        if (kind == 0) {
            row.setGravity(Gravity.START);
        } else if (kind == 1) {
            row.setGravity(Gravity.END);
        } else {
            row.setGravity(Gravity.CENTER_HORIZONTAL);
        }

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 10);
        bubble.setPadding(p, p, p, p);
        if (kind == 0) {
            bubble.setBackgroundResource(R.drawable.bg_bubble_other);
        } else if (kind == 1) {
            bubble.setBackgroundResource(R.drawable.bg_bubble_me);
        } else {
            bubble.setBackgroundResource(R.drawable.bg_bubble_system);
        }

        int maxW = (int) (getResources().getDisplayMetrics().widthPixels * 0.74f);

        if (kind == 0 && speaker != null && !speaker.isEmpty()) {
            TextView name = Ui.text(this, speaker, 11, R.color.text_hint);
            name.setMaxWidth(maxW);
            bubble.addView(name);
        }

        TextView body = new TextView(this);
        body.setText(text);
        body.setTextSize(kind == 2 ? 12 : 15);
        body.setLineSpacing(Ui.dp(this, 3), 1f);
        body.setMaxWidth(maxW);
        if (kind == 1) {
            body.setTextColor(getColor(R.color.card_white));
        } else if (kind == 2) {
            body.setTextColor(getColor(R.color.text_secondary));
        } else {
            body.setTextColor(getColor(R.color.text_primary));
        }
        bubble.addView(body);

        row.addView(bubble);
        llMessages.addView(row);
    }

    private void addOption(int index, final Choice c) {
        TextView btn = new TextView(this);
        btn.setText(index + "  " + c.text);
        btn.setTextSize(14);
        btn.setTextColor(getColor(R.color.text_primary));
        btn.setLineSpacing(Ui.dp(this, 3), 1f);
        btn.setBackgroundResource(R.drawable.bg_option);
        int p = Ui.dp(this, 12);
        btn.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 8);
        btn.setLayoutParams(lp);
        btn.setClickable(true);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onChoice(c);
            }
        });
        llOptions.addView(btn);
    }

    /** 核心逻辑：记录 → 展示结果 → 进入下一节点（约 15 行） */
    private void onChoice(Choice c) {
        if (current == null) {
            return;
        }
        recorder.record(current.id, current.stageName, c);

        addBubble("我", c.text, 1);
        if (c.cost > 0) {
            addBubble("系统", "你转出 " + Ui.money(c.cost) + " 元", 2);
        } else {
            addBubble("系统", "这一次你没有转账", 2);
        }
        updateLoss();
        llOptions.removeAllViews();

        StoryNode next = (c.next == null || c.next.isEmpty()) ? null : script.nodes.get(c.next);
        if (next == null) {
            goReview(current);
            return;
        }
        current = next;
        renderNode(next);
    }

    private void addEndingCard(final StoryNode node) {
        LinearLayout card = Ui.card(this, 14, 8);
        TextView title = Ui.text(this, node.endingTitle, 17, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLineSpacing(Ui.dp(this, 3), 1f);
        card.addView(title);
        if (!node.endingAdvice.isEmpty()) {
            TextView advice = Ui.text(this, node.endingAdvice, 13, R.color.text_secondary);
            advice.setPadding(0, Ui.dp(this, 8), 0, 0);
            advice.setLineSpacing(Ui.dp(this, 3), 1f);
            card.addView(advice);
        }
        llMessages.addView(card);

        TextView btn = new TextView(this);
        btn.setText(R.string.review);
        btn.setTextSize(15);
        btn.setTextColor(getColor(R.color.card_white));
        btn.setGravity(Gravity.CENTER);
        btn.setBackgroundColor(getColor(R.color.brand_blue));
        int p = Ui.dp(this, 14);
        btn.setPadding(p, p, p, p);
        btn.setClickable(true);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goReview(node);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 4);
        btn.setLayoutParams(lp);
        llOptions.addView(btn);
    }

    private void goReview(StoryNode ending) {
        Intent i = new Intent(this, ReviewActivity.class);
        i.putExtra("script_id", script.id);
        i.putExtra("ending_title", ending == null ? "" : ending.endingTitle);
        i.putExtra("ending_advice", ending == null ? "" : ending.endingAdvice);
        startActivity(i);
        finish();
    }

    private void updateLoss() {
        tvLoss.setText("已损失 ¥" + Ui.money(recorder.totalCost()));
    }

    private void scrollToBottom() {
        svMessages.post(new Runnable() {
            @Override
            public void run() {
                svMessages.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void confirmRestart() {
        new AlertDialog.Builder(this)
                .setTitle("重来一次？")
                .setMessage("当前进度会被清空。")
                .setPositiveButton("重来", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startScript();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setTitle("退出这个剧本？")
                .setMessage("进度不会保存。")
                .setPositiveButton("退出", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .setNegativeButton("继续玩", null)
                .show();
    }
}
