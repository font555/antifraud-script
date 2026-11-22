package com.example.antifraud.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.antifraud.R;
import com.example.antifraud.engine.PathRecorder;
import com.example.antifraud.engine.ScriptRepository;
import com.example.antifraud.engine.UserStore;
import com.example.antifraud.model.Choice;
import com.example.antifraud.model.Script;
import com.example.antifraud.model.StoryNode;

/**
 * 聊天页：整个作品的核心。逐节点渲染消息和选项，选择后继续。
 * 注意：这里绝不显示风险颜色，风险只在复盘页出现，否则没有悬念。
 */
public class ChatActivity extends BaseActivity {

    private Script script;
    private StoryNode current;
    private final PathRecorder recorder = PathRecorder.get();
    private int lastStage = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());

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
        Ui.insetContent(this);

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

        setupAnimations();

        findViewById(R.id.btn_exit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
        findViewById(R.id.btn_restart).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmRestart();
            }
        });

        startScript();
    }

    /** 消息气泡和选项的出现/消失都带动画，避免"啪一下全出来" */
    private void setupAnimations() {
        LayoutTransition msg = new LayoutTransition();
        msg.setAnimator(LayoutTransition.APPEARING,
                ObjectAnimator.ofFloat(null, "alpha", 0f, 1f).setDuration(240));
        msg.setStartDelay(LayoutTransition.APPEARING, 20);
        msg.setAnimator(LayoutTransition.DISAPPEARING,
                ObjectAnimator.ofFloat(null, "alpha", 1f, 0f).setDuration(120));
        llMessages.setLayoutTransition(msg);

        LayoutTransition opt = new LayoutTransition();
        opt.setAnimator(LayoutTransition.APPEARING,
                ObjectAnimator.ofFloat(null, "alpha", 0f, 1f).setDuration(200));
        opt.setAnimator(LayoutTransition.DISAPPEARING,
                ObjectAnimator.ofFloat(null, "alpha", 1f, 0f).setDuration(120));
        llOptions.setLayoutTransition(opt);
    }

    /** 重新开始（也用于"重来"按钮） */
    private void startScript() {
        handler.removeCallbacksAndMessages(null);
        speakerColors.clear();
        nextColorIndex = 0;
        recorder.reset();
        lastStage = -1;
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

        // 换幕时插入一个居中分隔条，像剧本的幕次
        if (node.stage != lastStage) {
            addStageDivider(node);
            lastStage = node.stage;
        }

        final boolean system = "系统".equals(node.speaker);
        final StoryNode nd = node;
        final int count = node.messages.size();
        // 对方消息间隔 620ms，系统消息快一些
        final long gap = system ? 420 : 660;

        // 先显示"正在输入"，再逐条弹出
        if (!system && count > 0) {
            final View typing = makeTyping(node.speaker);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (isFinishing()) {
                        return;
                    }
                    llMessages.addView(typing);
                    scrollToBottom();
                }
            }, 120);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    llMessages.removeView(typing);
                }
            }, 120 + gap);
        }

        for (int i = 0; i < count; i++) {
            final String msg = node.messages.get(i);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (isFinishing()) {
                        return;
                    }
                    addBubble(nd.speaker, msg, system ? 2 : 0);
                    scrollToBottom();
                }
            }, 120 + gap + i * gap);
        }

        // 选项等消息播完再出现
        llOptions.removeAllViews();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }
                if (nd.isEnd) {
                    addEndingCard(nd);
                } else {
                    for (int i = 0; i < nd.options.size(); i++) {
                        addOption(i + 1, nd.options.get(i));
                    }
                }
                scrollToBottom();
            }
        }, 120 + gap + count * gap);
    }

    /** 不同说话人用不同颜色，方便多人对话时区分 */
    /** 对方头像色：特意避开红色系，免得和"我方"的品牌红混淆 */
    private static final int[] AVATAR_COLORS = {
            0xFF3F7FA6, 0xFF4E7B3A, 0xFF8E5B8F, 0xFF3C7A89,
            0xFFA9713F, 0xFF6B4C8A, 0xFF2F6F6F, 0xFF7A5C2E
    };

    private final java.util.HashMap<String, Integer> speakerColors = new java.util.HashMap<>();
    private int nextColorIndex = 0;

    /** 按"第一次出现顺序"分配颜色，保证同一局里不同说话人颜色一定不同（最多 8 种） */
    private int colorForSpeaker(String speaker) {
        String key = (speaker == null || speaker.isEmpty()) ? "?" : speaker;
        Integer c = speakerColors.get(key);
        if (c == null) {
            c = AVATAR_COLORS[nextColorIndex % AVATAR_COLORS.length];
            nextColorIndex++;
            speakerColors.put(key, c);
        }
        return c;
    }

    /** 像素风方形头像：色块 + 深色描边 + 首字 */
    private TextView makeAvatar(String name, int color, boolean marginRight) {
        TextView av = new PTextView(this);
        av.setText(name == null || name.isEmpty() ? "?" : name.substring(0, 1));
        av.setTextSize(11);
        av.setGravity(Gravity.CENTER);
        av.setTextColor(getColor(R.color.white));
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        gd.setStroke(Math.max(2, Ui.dp(this, 2)), getColor(R.color.border_dark));
        av.setBackground(gd);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30));
        if (marginRight) {
            lp.rightMargin = Ui.dp(this, 8);
        } else {
            lp.leftMargin = Ui.dp(this, 8);
        }
        av.setLayoutParams(lp);
        return av;
    }
    /** "对方正在输入"的像素小气泡 */
    private View makeTyping(String speaker) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = Ui.dp(this, 8);
        row.setLayoutParams(rlp);

        row.addView(makeAvatar(speaker, colorForSpeaker(speaker), true));

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackgroundResource(R.drawable.px_bubble_other);
        int p = Ui.dp(this, 10);
        bubble.setPadding(p, Ui.dp(this, 6), p, Ui.dp(this, 6));
        TextView dots = Ui.text(this, "· · ·", 13, R.color.ink_hint);
        bubble.addView(dots);
        row.addView(bubble);

        ObjectAnimator pulse = ObjectAnimator.ofFloat(dots, "alpha", 0.25f, 1f);
        pulse.setDuration(420);
        pulse.setRepeatCount(ObjectAnimator.INFINITE);
        pulse.setRepeatMode(ObjectAnimator.REVERSE);
        pulse.start();
        row.setTag(pulse);
        return row;
    }

    /** 幕次分隔条：居中一条细线 + 幕名 */
    private void addStageDivider(StoryNode node) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.dp(this, 6);
        rlp.bottomMargin = Ui.dp(this, 12);
        row.setLayoutParams(rlp);

        View left = new View(this);
        left.setBackgroundColor(getColor(R.color.paper_dark));
        left.setLayoutParams(new LinearLayout.LayoutParams(0, Math.max(1, Ui.dp(this, 1)), 1f));
        row.addView(left);

        TextView chip = Ui.text(this, "第 " + node.stage + " 幕"
                + (node.stageName.isEmpty() ? "" : " · " + node.stageName), 9, R.color.ink_hint);
        chip.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        row.addView(chip);

        View right = new View(this);
        right.setBackgroundColor(getColor(R.color.paper_dark));
        right.setLayoutParams(new LinearLayout.LayoutParams(0, Math.max(1, Ui.dp(this, 1)), 1f));
        row.addView(right);

        llMessages.addView(row);
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
            bubble.setBackgroundResource(R.drawable.px_bubble_other);
        } else if (kind == 1) {
            bubble.setBackgroundResource(R.drawable.px_bubble_me);
        } else {
            bubble.setBackgroundResource(R.drawable.px_bubble_system);
        }

        int maxW = (int) (getResources().getDisplayMetrics().widthPixels * 0.74f);

        if (kind == 0 && speaker != null && !speaker.isEmpty()) {
            TextView name = Ui.text(this, speaker, 11, R.color.ink_hint);
            name.setMaxWidth(maxW);
            bubble.addView(name);
        }

        TextView body = new TextView(this);
        Ui.pixel(body);
        body.setText(text);
        body.setTextSize(kind == 2 ? 12 : 15);
        body.setLineSpacing(Ui.dp(this, 3), 1f);
        body.setMaxWidth(maxW);
        if (kind == 1) {
            body.setTextColor(getColor(R.color.white));
        } else if (kind == 2) {
            body.setTextColor(getColor(R.color.ink_secondary));
        } else {
            body.setTextColor(getColor(R.color.ink));
        }
        bubble.addView(body);

        // 对方消息左侧带彩色头像；自己的消息右侧带品牌色头像
        if (kind == 0) {
            row.addView(makeAvatar(speaker, colorForSpeaker(speaker), true));
        }

        row.addView(bubble);

        if (kind == 1) {
            int avIndex = UserStore.avatar(this);
            String glyph = avIndex >= 0 && avIndex < MainActivity.AVATARS.length
                    ? MainActivity.AVATARS[avIndex] : "我";
            row.addView(makeAvatar(glyph, getColor(R.color.brand), false));
        }

        llMessages.addView(row);
    }

    private void addOption(int index, final Choice c) {
        TextView btn = new TextView(this);
        Ui.pixel(btn);
        btn.setText(index + "  " + c.text);
        btn.setTextSize(17);
        btn.setTextColor(getColor(R.color.ink));
        btn.setLineSpacing(Ui.dp(this, 3), 1f);
        btn.setBackgroundResource(R.drawable.px_option);
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
        TextView title = Ui.text(this, node.endingTitle, 17, R.color.ink);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLineSpacing(Ui.dp(this, 3), 1f);
        card.addView(title);
        if (!node.endingAdvice.isEmpty()) {
            TextView advice = Ui.text(this, node.endingAdvice, 13, R.color.ink_secondary);
            advice.setPadding(0, Ui.dp(this, 8), 0, 0);
            advice.setLineSpacing(Ui.dp(this, 3), 1f);
            card.addView(advice);
        }
        llMessages.addView(card);

        TextView btn = new TextView(this);
        Ui.pixel(btn);
        btn.setText(R.string.review);
        btn.setTextSize(18);
        btn.setTextColor(getColor(R.color.white));
        btn.setGravity(Gravity.CENTER);
        btn.setBackgroundColor(getColor(R.color.brand));
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
        // 记录一次通关 + 累计损失，书架首页会显示"已读 N 本"
        UserStore.addPlay(this);
        UserStore.addLoss(this, recorder.totalCost());

        Intent i = new Intent(this, ReviewActivity.class);
        i.putExtra("script_id", script.id);
        i.putExtra("ending_title", ending == null ? "" : ending.endingTitle);
        i.putExtra("ending_advice", ending == null ? "" : ending.endingAdvice);
        startActivity(i);
        overridePendingTransition(R.anim.page_in, R.anim.page_out);
        finish();
    }

    private int shownLoss = 0;

    /** 损失金额滚动增长，并轻微放大提示一下 */
    private void updateLoss() {
        int target = recorder.totalCost();
        if (target == shownLoss) {
            tvLoss.setText("已损失 ¥" + Ui.money(target));
            return;
        }
        ValueAnimator va = ValueAnimator.ofInt(shownLoss, target);
        va.setDuration(420);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int v = (int) animation.getAnimatedValue();
                tvLoss.setText("已损失 ¥" + Ui.money(v));
            }
        });
        va.start();
        shownLoss = target;
        tvLoss.animate().scaleX(1.18f).scaleY(1.18f).setDuration(120)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        tvLoss.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
                    }
                }).start();
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
