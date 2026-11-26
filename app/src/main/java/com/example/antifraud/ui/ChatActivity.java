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
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.antifraud.R;
import com.example.antifraud.engine.AppLog;
import com.example.antifraud.engine.PathRecorder;
import com.example.antifraud.engine.ScriptRepository;
import com.example.antifraud.engine.SettingsStore;
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
    /** 超大字号（1.9 倍及以上，对应「超大 2.1」档）：顶栏只保留必要信息，避免换行和挤压 */
    private boolean bigFont;
    /** 当前字号倍数（启动时读一次，画气泡时用，不用每次都读设置） */
    private float fontScale = 1f;
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

        // 消息区和选项区的外边距 12dp 也随字号放大，2.1 倍时左右才不会贴着屏幕边缘
        int outerPadding = SettingsStore.scaleBox(this, Ui.dp(this, 12));
        llMessages.setPadding(outerPadding, outerPadding, outerPadding, outerPadding);
        llOptions.setPadding(outerPadding, outerPadding, outerPadding, outerPadding);

        String id = getIntent().getStringExtra("script_id");
        script = ScriptRepository.load(this, id);
        if (script == null) {
            Toast.makeText(this, "剧本加载失败：" + id, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        tvTitle.setText(script.title);
        pbStage.setMax(script.stageCount);

        protectHeader();

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

    /**
     * 顶栏防溢出：标题/金额按字号设置自动收缩，副标题只留幕次，
     * 右侧账本区固定最小宽度并靠右，2.1 倍「超大」字号下也不会和标题撞在一起。
     */
    private void protectHeader() {
        fontScale = SettingsStore.fontScale(this);
        // 阈值跟着新档位走：1.9 才进「超大」分支（2.1 档），1.5 以上算「特大」（1.7 档）
        bigFont = fontScale >= 1.9f;
        boolean hugeFont = fontScale >= 1.5f;
        // 标题：标准 18sp，大 15sp，特大 14sp，超大 12sp，只显示一行
        float titleSp;
        if (bigFont) {
            titleSp = 12f;
        } else if (hugeFont) {
            titleSp = 14f;
        } else if (fontScale >= 1.28f) {
            titleSp = 15f;
        } else {
            titleSp = 18f;
        }
        tvTitle.setTextSize(titleSp);
        tvTitle.setSingleLine(true);
        tvTitle.setMaxLines(1);
        tvTitle.setEllipsize(TextUtils.TruncateAt.END);

        tvStage.setSingleLine(true);
        tvStage.setMaxLines(1);
        tvStage.setEllipsize(TextUtils.TruncateAt.END);

        // 右侧「已损失 / 重来」：留最小宽度并整体靠右，避免被标题挤压
        tvLoss.setTextSize(bigFont ? 12f : (hugeFont ? 13f : 14f));
        tvLoss.setGravity(Gravity.END);
        View lossBox = (View) tvLoss.getParent();
        if (lossBox != null) {
            lossBox.setMinimumWidth(SettingsStore.scaleBox(this, Ui.dp(this, 76)));
            if (lossBox instanceof LinearLayout) {
                ((LinearLayout) lossBox).setGravity(Gravity.END);
            }
        }

        // 顶栏 返回「←」和标题行之间、按钮与账本之间的间距随字号留白
        View btnExit = findViewById(R.id.btn_exit);
        if (btnExit != null) {
            int gap = SettingsStore.scaleBox(this, Ui.dp(this, 10));
            btnExit.setPadding(0, 0, gap, 0);
            // 顶栏本体内边距：横向按字号放大；纵向只放 40%，
            // 顶栏太高会挤掉消息区，一屏看不到两句话
            View topBar = (View) btnExit.getParent();
            if (topBar != null) {
                float vFactor = 1f + (fontScale - 1f) * 0.4f;
                int hPad = SettingsStore.scaleBox(this, Ui.dp(this, 12));
                int vTop = Math.round(Ui.dp(this, 14) * vFactor);
                int vBottom = Math.round(Ui.dp(this, 12) * vFactor);
                topBar.setPadding(hPad, vTop, hPad, vBottom);
            }
        }
        View btnRestart = findViewById(R.id.btn_restart);
        if (btnRestart != null) {
            int left = SettingsStore.scaleBox(this, Ui.dp(this, 8));
            int right = SettingsStore.scaleBox(this, Ui.dp(this, 4));
            btnRestart.setPadding(left, 0, right, 0);
        }
        // 标题下「第 N 幕」和账本下「重来」的 4dp 上间距，也一起放大
        int headerGap = SettingsStore.scaleBox(this, Ui.dp(this, 4));
        if (tvStage.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams slp = (LinearLayout.LayoutParams) tvStage.getLayoutParams();
            slp.topMargin = headerGap;
            tvStage.setLayoutParams(slp);
        }
        if (btnRestart != null && btnRestart.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams rlp = (LinearLayout.LayoutParams) btnRestart.getLayoutParams();
            rlp.topMargin = headerGap;
            btnRestart.setLayoutParams(rlp);
        }
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
        AppLog.i("CHAT", "开始剧本 " + script.id + " 《" + script.title + "》");
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
        // 超大字号只显示幕次，省掉「 · 阶段名」，避免副标题换行错乱
        String stageText = "第 " + node.stage + " 幕 / 共 " + script.stageCount + " 幕";
        if (!bigFont && !node.stageName.isEmpty()) {
            stageText += "  ·  " + node.stageName;
        }
        tvStage.setText(stageText);
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

    /** 像素风头像：Color 区分说话人，type 决定画哪个小家伙（学生/学霸/猫/兔/熊/星） */
    private AvatarView makeAvatar(int color, int typeIndex, boolean marginRight) {
        AvatarView av = new AvatarView(this);
        av.setAvatar(color, typeIndex);
        // 头像跟着字号一起长大，否则 2.1 倍时头像比气泡小一大截，看着断层
        int size = SettingsStore.scaleBox(this, Ui.dp(this, 30));
        int gap = SettingsStore.scaleBox(this, Ui.dp(this, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        if (marginRight) {
            lp.rightMargin = gap;
        } else {
            lp.leftMargin = gap;
        }
        av.setLayoutParams(lp);
        return av;
    }

    /** 用名字稳定地映射到一个头像形象（同一个人每局都是同一张脸） */
    private int speakerType(String name) {
        String key = (name == null || name.isEmpty()) ? "?" : name;
        return Math.abs(key.hashCode()) % 6;
    }
    /** "对方正在输入"的像素小气泡 */
    private View makeTyping(String speaker) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = SettingsStore.scaleBox(this, Ui.dp(this, 8));
        row.setLayoutParams(rlp);

        row.addView(makeAvatar(colorForSpeaker(speaker), speakerType(speaker), true));

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackgroundResource(R.drawable.px_bubble_other);
        int p = SettingsStore.scaleBox(this, Ui.dp(this, 10));
        int pv = SettingsStore.scaleBox(this, Ui.dp(this, 6));
        bubble.setPadding(p, pv, p, pv);
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
        rlp.topMargin = SettingsStore.scaleBox(this, Ui.dp(this, 6));
        rlp.bottomMargin = SettingsStore.scaleBox(this, Ui.dp(this, 12));
        row.setLayoutParams(rlp);

        View left = new View(this);
        left.setBackgroundColor(getColor(R.color.paper_dark));
        left.setLayoutParams(new LinearLayout.LayoutParams(0, Math.max(1, Ui.dp(this, 1)), 1f));
        row.addView(left);

        TextView chip = Ui.text(this, "第 " + node.stage + " 幕"
                + (node.stageName.isEmpty() ? "" : " · " + node.stageName), 9, R.color.ink_hint);
        // 字号放大后幕名会变长，最多两行并限制宽度，别把两侧的细线挤没了
        chip.setMaxLines(2);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setMaxWidth(Math.max(Ui.dp(this, 72),
                (int) (getResources().getDisplayMetrics().widthPixels * 0.66f)));
        int chipPad = SettingsStore.scaleBox(this, Ui.dp(this, 10));
        chip.setPadding(chipPad, 0, chipPad, 0);
        row.addView(chip);

        View right = new View(this);
        right.setBackgroundColor(getColor(R.color.paper_dark));
        right.setLayoutParams(new LinearLayout.LayoutParams(0, Math.max(1, Ui.dp(this, 1)), 1f));
        row.addView(right);

        llMessages.addView(row);
    }

    /**
     * 气泡最大宽度占屏幕的比例。
     * 字号越大字越宽，固定 0.74 会逼得每行只剩两三个字，所以按档位逐级放宽。
     */
    private float bubbleWidthRatio() {
        if (fontScale >= 1.9f) {
            // 超大 2.1
            return 0.86f;
        }
        if (fontScale >= 1.5f) {
            // 特大 1.7
            return 0.80f;
        }
        return 0.74f;
    }

    /**
     * 气泡里文字的实际上限宽度（像素）。
     * 除了按比例放宽，还要扣掉两侧外边距、头像和气泡内边距——
     * 否则 2.1 倍时「我的消息」的气泡会把自己那侧的头像挤出屏幕。
     */
    private int bubbleMaxWidth(int kind) {
        int width = getResources().getDisplayMetrics().widthPixels;
        int outer = SettingsStore.scaleBox(this, Ui.dp(this, 12)) * 2;
        int pad = SettingsStore.scaleBox(this, Ui.dp(this, 10)) * 2;
        int reserve = outer + pad;
        if (kind == 0 || kind == 1) {
            // 一条消息只在一侧有头像
            reserve += SettingsStore.scaleBox(this, Ui.dp(this, 30))
                    + SettingsStore.scaleBox(this, Ui.dp(this, 8));
        }
        int fit = width - reserve;
        int cap = (int) (width * bubbleWidthRatio());
        return Math.max(Ui.dp(this, 60), Math.min(cap, fit));
    }

    /** kind: 0 = 对方，1 = 我，2 = 系统 */
    private void addBubble(String speaker, String text, int kind) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = SettingsStore.scaleBox(this, Ui.dp(this, 8));
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
        int p = SettingsStore.scaleBox(this, Ui.dp(this, 10));
        bubble.setPadding(p, p, p, p);
        if (kind == 0) {
            bubble.setBackgroundResource(R.drawable.px_bubble_other);
        } else if (kind == 1) {
            bubble.setBackgroundResource(R.drawable.px_bubble_me);
        } else {
            bubble.setBackgroundResource(R.drawable.px_bubble_system);
        }

        int maxW = bubbleMaxWidth(kind);

        if (kind == 0 && speaker != null && !speaker.isEmpty()) {
            TextView name = Ui.text(this, speaker, 11, R.color.ink_hint);
            name.setMaxWidth(maxW);
            bubble.addView(name);
        }

        TextView body = new TextView(this);
        Ui.pixel(body);
        body.setText(text);
        // 走 sp，由 BaseActivity 的 fontScale 自动放大，这里绝对不要再乘倍数
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
            row.addView(makeAvatar(colorForSpeaker(speaker), speakerType(speaker), true));
        }

        row.addView(bubble);

        if (kind == 1) {
            row.addView(makeAvatar(getColor(R.color.brand), UserStore.avatar(this), false));
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
        int p = SettingsStore.scaleBox(this, Ui.dp(this, 12));
        btn.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = SettingsStore.scaleBox(this, Ui.dp(this, 8));
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
        int step = recorder.getSteps().size();
        AppLog.i("CHAT", "选择 " + step + " " + riskLabel(c.risk) + " ￥" + c.cost + " " + c.text);

        addBubble("我", c.text, 1);
        if (c.cost > 0) {
            addBubble("系统", "你转出 " + Ui.money(c.cost) + " 元", 2);
        } else {
            addBubble("系统", "这一次你没有转账", 2);
        }
        updateLoss();
        AppLog.i("CHAT", "累计损失 ￥" + recorder.totalCost() + " 步数 " + step);
        llOptions.removeAllViews();

        StoryNode next = (c.next == null || c.next.trim().isEmpty()) ? null : script.nodes.get(c.next);
        if (next == null) {
            // 选项没有可用的下一节点：实际没有到达任何结局节点（旧代码误把上一个剧情
            // 节点当结局传过去）。当前节点本身是结局就传它，否则传 null，
            // 由复盘页显示兜底标题「你走完了这段剧本」。
            goReview(current.isEnd ? current : null);
            return;
        }
        current = next;
        renderNode(next);
    }

    /** 运行日志用：风险等级的中文名 */
    private static String riskLabel(String risk) {
        if ("high".equals(risk)) {
            return "高风险";
        }
        if ("medium".equals(risk)) {
            return "中风险";
        }
        return "低风险";
    }

    private void addEndingCard(final StoryNode node) {
        LinearLayout card = Ui.card(this, 14, 8);
        // 卡片里装的是结局标题+建议，内边距和下边距一起跟着字号放大
        int cp = SettingsStore.scaleBox(this, Ui.dp(this, 14));
        card.setPadding(cp, cp, cp, cp);
        if (card.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams clp = (LinearLayout.LayoutParams) card.getLayoutParams();
            clp.bottomMargin = SettingsStore.scaleBox(this, Ui.dp(this, 8));
            card.setLayoutParams(clp);
        }
        TextView title = Ui.text(this, node.endingTitle, 17, R.color.ink);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLineSpacing(Ui.dp(this, 3), 1f);
        card.addView(title);
        if (!node.endingAdvice.isEmpty()) {
            TextView advice = Ui.text(this, node.endingAdvice, 13, R.color.ink_secondary);
            advice.setPadding(0, SettingsStore.scaleBox(this, Ui.dp(this, 8)), 0, 0);
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
        int p = SettingsStore.scaleBox(this, Ui.dp(this, 14));
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
        lp.topMargin = SettingsStore.scaleBox(this, Ui.dp(this, 4));
        btn.setLayoutParams(lp);
        llOptions.addView(btn);
    }

    private void goReview(StoryNode ending) {
        // 记录一次通关 + 累计损失，书架首页会显示"已读 N 本"
        UserStore.addPlay(this);
        UserStore.addLoss(this, recorder.totalCost());

        String endingTitle = ending == null ? "" : ending.endingTitle;
        AppLog.i("CHAT", "进入复盘 损失 ￥" + recorder.totalCost()
                + " 步数 " + recorder.getSteps().size()
                + " 结局「" + (endingTitle.isEmpty() ? "无" : endingTitle) + "」");

        Intent i = new Intent(this, ReviewActivity.class);
        i.putExtra("script_id", script.id);
        i.putExtra("ending_title", endingTitle);
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
        PixelDialog dlg = PixelDialog.create(this);
        dlg.title("重来一次？");
        dlg.body("当前进度会被清空，损失金额也会归零。");
        dlg.button("重来", true, new PixelDialog.OnAction() {
            @Override
            public void onAction(int index) {
                AppLog.i("CHAT", "重来按钮 清空进度 原损失 ￥"
                        + recorder.totalCost() + " 步数 " + recorder.getSteps().size());
                startScript();
            }
        }, 0);
        dlg.button("取消", false, null, 1);
        dlg.show();
    }

    @Override
    public void onBackPressed() {
        PixelDialog dlg = PixelDialog.create(this);
        dlg.title("退出这个剧本？");
        dlg.body("进度不会保存。");
        dlg.button("退出", true, new PixelDialog.OnAction() {
            @Override
            public void onAction(int index) {
                finish();
            }
        }, 0);
        dlg.button("继续玩", false, null, 1);
        dlg.show();
    }
}
