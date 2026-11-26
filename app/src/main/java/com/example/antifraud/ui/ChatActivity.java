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
 *
 * <p><b>它在整个流程里的位置：</b>首页书架（HomeTabView）点一本书 → 传 script_id
 * 启动本页 → 玩家一轮一轮地"看话术、做选择"→ 走到结局后进复盘页
 * （{@link ReviewActivity}）看逐节点的风险讲解和损失账本。</p>
 *
 * <p><b>关键设计：</b>
 * <ul>
 *   <li><b>剧本数据只读不写</b>：{@link ScriptRepository#load} 把剧本读进内存，本页只做展示和推进；
 *       玩家走过的每一步由 {@link PathRecorder} 单例记录，供复盘页回放。</li>
 *   <li><b>消息是一条一条蹦出来的</b>：{@link #renderNode} 用一串 postDelayed 把每条消息
 *       排进主线程队列，配合"对方正在输入"气泡模拟真实聊天节奏，时序见该方法注释。</li>
 *   <li><b>不剧透风险</b>：选项按钮只显示文字，不显示高/中/低风险，风险要到复盘页才揭晓。</li>
 *   <li><b>四档字号全适配</b>：文字走 sp（被 {@link BaseActivity} 的 fontScale 自动放大），
 *       "装文字的容器"尺寸走 {@link SettingsStore#scaleBox}，见 {@link #protectHeader()}。</li>
 * </ul>
 * 界面骨架来自 XML 布局 activity_chat.xml，消息区和选项区的内容全部代码构建。</p>
 */
public class ChatActivity extends BaseActivity {

    /** 当前正在玩的剧本（从 assets 或用户目录加载，含全部节点） */
    private Script script;
    /** 当前所处的节点；推进时整体替换，null 表示尚未开始 */
    private StoryNode current;
    /** 路径记录器（全局单例）：每选一次就记一条，复盘页按它回放整条链路 */
    private final PathRecorder recorder = PathRecorder.get();
    /** 上一次渲染的幕次号：用来判断"是不是换幕了"，换幕才插分隔条 */
    private int lastStage = -1;
    /** 超大字号（1.9 倍及以上，对应「超大 2.1」档）：顶栏只保留必要信息，避免换行和挤压 */
    private boolean bigFont;
    /** 当前字号倍数（启动时读一次，画气泡时用，不用每次都读设置） */
    private float fontScale = 1f;
    /** 主线程 Handler：消息延迟播放、重来时统一清理，都靠它 */
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** 顶栏标题：剧本名，只显示一行，超长省略 */
    private TextView tvTitle;
    /** 顶栏副标题：「第 N 幕 / 共 M 幕 · 幕名」 */
    private TextView tvStage;
    /** 顶栏右侧损失账本：「已损失 ¥xxx」，数字会滚动增长 */
    private TextView tvLoss;
    /** 顶栏幕次进度条，进度 = 当前幕次 */
    private ProgressBar pbStage;
    /** 消息滚动区，每加一条消息都自动滚到底 */
    private ScrollView svMessages;
    /** 消息容器：逐条 addView 气泡，带 LayoutTransition 淡入动画 */
    private LinearLayout llMessages;
    /** 选项容器：消息播完后才填按钮，选完立刻清空 */
    private LinearLayout llOptions;

    /**
     * 页面入口：绑定布局 → 读剧本 → 保护顶栏 → 装动画 → 挂按钮 → 开始第一幕。
     *
     * @param savedInstanceState 系统恢复用的状态包；本页刻意不保存进度，重建后一律从第一幕重来，
     *                           避免"消息已放完、选项还没出来"的半截状态
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        // 处理刘海屏/状态栏：把内容整体下移，避免顶栏被状态栏盖住
        Ui.insetContent(this);

        // 先把 7 个关键控件抓在手里，后面所有渲染都靠这几个引用
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

        // 剧本 id 由首页/剧本页通过 Intent 传进来；没传就相当于加载失败
        String id = getIntent().getStringExtra("script_id");
        script = ScriptRepository.load(this, id);
        if (script == null) {
            // 剧本文件缺了/格式坏了：提示后直接退出，不要停在一个空白聊天页
            Toast.makeText(this, "剧本加载失败：" + id, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        tvTitle.setText(script.title);
        pbStage.setMax(script.stageCount);      // 进度条满格 = 剧本总幕数

        // 先按字号把顶栏/气泡的尺寸规则定下来，再开始渲染，避免首帧跳动
        protectHeader();

        // 消息条目的淡入淡出动画（LayoutTransition），一次装好全局生效
        setupAnimations();

        // 左上角「←」退出（走 onBackPressed 的确认弹窗），右上「重来」重新开始
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

        // 一切就绪，从 startNode 开始播第一段
        startScript();
    }

    /**
     * 顶栏防溢出：标题/金额按字号设置自动收缩，副标题只留幕次，右侧账本区固定最小宽度并靠右。
     *
     * <p>这里做的是「反向压缩」：系统 fontScale 会把 sp 放大 2.1 倍，标题若还是 18sp，
     * 两个字就占满一行。所以按档位把 sp 值调小（18 → 15 → 14 → 12），
     * 乘上 2.1 倍之后实际渲染尺寸才回到可接受的范围。</p>
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

        // 幕次副标题：同样只占一行，超长省略（超大字号下还会去掉幕名，见 renderNode）
        tvStage.setSingleLine(true);
        tvStage.setMaxLines(1);
        tvStage.setEllipsize(TextUtils.TruncateAt.END);

        // 右侧「已损失 / 重来」：留最小宽度并整体靠右，避免被标题挤压
        tvLoss.setTextSize(bigFont ? 12f : (hugeFont ? 13f : 14f));
        tvLoss.setGravity(Gravity.END);
        // lossBox 是 tvLoss 外面那层 LinearLayout：给它一个随字号放大的最小宽度，
        // 这样金额位数变化时右侧区域宽度稳定，不会把左边标题挤来挤去
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
                // vFactor：纵向放大幅度 = 1 + (倍数 - 1) * 0.4，2.1 倍字号时只放大到 1.44 倍
                float vFactor = 1f + (fontScale - 1f) * 0.4f;
                int hPad = SettingsStore.scaleBox(this, Ui.dp(this, 12));
                int vTop = Math.round(Ui.dp(this, 14) * vFactor);
                int vBottom = Math.round(Ui.dp(this, 12) * vFactor);
                topBar.setPadding(hPad, vTop, hPad, vBottom);
            }
        }
        // 「重来」按钮左右的内边距（左大右小，因为右边还要和账本分开）
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

    /**
     * 消息气泡和选项的出现/消失都带动画，避免"啪一下全出来"。
     * 用 {@link LayoutTransition}：往 LinearLayout 里 addView / removeView 时，
     * 框架自动给新条目跑 APPEARING、给被移除的条目跑 DISAPPEARING，不用逐个写 animate()。
     */
    private void setupAnimations() {
        // 消息区：淡入 240ms（稍慢，读起来不慌），淡出 120ms（快一点，别拖泥带水）
        LayoutTransition msg = new LayoutTransition();
        msg.setAnimator(LayoutTransition.APPEARING,
                ObjectAnimator.ofFloat(null, "alpha", 0f, 1f).setDuration(240));
        // 延迟 20ms 起播：让首次布局先完成，否则动画第一帧会跳
        msg.setStartDelay(LayoutTransition.APPEARING, 20);
        msg.setAnimator(LayoutTransition.DISAPPEARING,
                ObjectAnimator.ofFloat(null, "alpha", 1f, 0f).setDuration(120));
        llMessages.setLayoutTransition(msg);

        // 选项区：淡入 200ms / 淡出 120ms，比消息区略快，点完马上换下一组
        LayoutTransition opt = new LayoutTransition();
        opt.setAnimator(LayoutTransition.APPEARING,
                ObjectAnimator.ofFloat(null, "alpha", 0f, 1f).setDuration(200));
        opt.setAnimator(LayoutTransition.DISAPPEARING,
                ObjectAnimator.ofFloat(null, "alpha", 1f, 0f).setDuration(120));
        llOptions.setLayoutTransition(opt);
    }

    /**
     * 重新开始（也用于"重来"按钮）：清空一切状态，从 startNode 重新渲染。
     *
     * <p>注意必须把 Handler 里的待执行任务清掉——否则"重来"前已经排队的消息
     * 会在重来之后继续冒出来，和新一轮的消息混在一起。</p>
     */
    private void startScript() {
        AppLog.i("CHAT", "开始剧本 " + script.id + " 《" + script.title + "》");
        // 清掉所有排队中的延迟任务（上一轮没播完的消息、还没出现的选项）
        handler.removeCallbacksAndMessages(null);
        // 说话人配色表也要清空：重来后按"第一次出现顺序"重新分配颜色
        speakerColors.clear();
        nextColorIndex = 0;
        // 复盘用的路径记录清零（重来等于放弃上一次的路径）
        recorder.reset();
        lastStage = -1;          // -1 保证第一幕一定会插分隔条
        llMessages.removeAllViews();
        llOptions.removeAllViews();
        updateLoss();            // 账本显示归零

        current = script.nodes.get(script.startNode);
        if (current == null) {
            // 数据文件里 startNode 写错了：明确提示并退出，不猜任何一个节点
            Toast.makeText(this, "起始节点不存在：" + script.startNode, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        renderNode(current);
    }

    /**
     * 渲染一个节点：先追加消息气泡，再渲染选项。
     *
     * <p><b>消息延迟时序（本页最容易看晕的一段，务必按这里读）：</b>
     * 假设对方消息有 count 条，单条间隔 gap（对方 660ms、系统 420ms），
     * 那么每条任务被排进 Handler 的时刻是：
     * <pre>
     *   t = 120                    → 显示"正在输入"气泡，并滚动到底
     *   t = 120 + gap              → 撤掉"正在输入"气泡
     *   t = 120 + gap + 0*gap      → 第 1 条消息出现
     *   t = 120 + gap + 1*gap      → 第 2 条消息出现
     *   ...
     *   t = 120 + gap + (count-1)*gap → 第 count 条消息出现
     *   t = 120 + gap + count*gap  → 消息全部播完，开始渲染选项 / 结局卡
     * </pre>
     * 也就是说：先留 120ms 让"正在输入"露个脸，再等一个 gap 才出第一条真实消息
     * （否则打字气泡一闪就没，像卡帧）。选项的延迟用的是 count * gap，
     * 比最后一条消息晚整整一个 gap，保证读者读完最后一句才看到按钮，
     * 不会出现"文字还没读完就急着点"的情况。</p>
     *
     * <p>所有 postDelayed 回调里都先判断 isFinishing()：用户在消息播放途中按返回键，
     * 已经排队的任务仍会执行，此时 View 树已经销毁，不判会崩。</p>
     *
     * @param node 要渲染的剧情节点（含 speaker、messages、options 或结局字段）
     */
    private void renderNode(StoryNode node) {
        // 超大字号只显示幕次，省掉「 · 阶段名」，避免副标题换行错乱
        String stageText = "第 " + node.stage + " 幕 / 共 " + script.stageCount + " 幕";
        if (!bigFont && !node.stageName.isEmpty()) {
            stageText += "  ·  " + node.stageName;
        }
        tvStage.setText(stageText);
        pbStage.setProgress(node.stage);     // 进度条按幕次推进

        // 换幕时插入一个居中分隔条，像剧本的幕次
        if (node.stage != lastStage) {
            addStageDivider(node);
            lastStage = node.stage;
        }

        // system 表示这条节点的消息是"系统旁白"（居中灰字），不是对方说的话
        final boolean system = "系统".equals(node.speaker);
        final StoryNode nd = node;           // 匿名内部类要用的 final 副本（node 是参数，不能直接捕获）
        final int count = node.messages.size();
        // 对方消息间隔 620ms，系统消息快一些
        final long gap = system ? 420 : 660;

        // 先显示"正在输入"，再逐条弹出
        if (!system && count > 0) {
            final View typing = makeTyping(node.speaker);
            // t = 120ms：挂上"正在输入"气泡
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (isFinishing()) {
                        return;      // 页面已在销毁，直接放弃这次绘制
                    }
                    llMessages.addView(typing);
                    scrollToBottom();
                }
            }, 120);
            // t = 120 + gap：撤掉"正在输入"，紧接着第一条消息就要出现
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    llMessages.removeView(typing);
                }
            }, 120 + gap);
        }

        // 逐条消息：第 i 条排在 t = 120 + gap + i*gap
        for (int i = 0; i < count; i++) {
            final String msg = node.messages.get(i);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (isFinishing()) {
                        return;
                    }
                    // kind：系统消息传 2（居中灰气泡），对方消息传 0（左侧带头像）
                    addBubble(nd.speaker, msg, system ? 2 : 0);
                    scrollToBottom();      // 每出一条就滚到底，保证最新一条始终可见
                }
            }, 120 + gap + i * gap);
        }

        // 选项等消息播完再出现：先清空上一组按钮（上一节点选完已经清过，这里再保险一次）
        llOptions.removeAllViews();
        // t = 120 + gap + count*gap：比最后一条消息正好晚一个 gap
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }
                if (nd.isEnd) {
                    // 结局节点：不显示选项，改显示"结局卡 + 去复盘"按钮
                    addEndingCard(nd);
                } else {
                    // 普通节点：每条选项一个按钮，编号从 1 开始（界面上显示「1  xxx」）
                    for (int i = 0; i < nd.options.size(); i++) {
                        addOption(i + 1, nd.options.get(i));
                    }
                }
                scrollToBottom();
            }
        }, 120 + gap + count * gap);
    }

    /**
     * 不同说话人用不同颜色，方便多人对话时区分。
     * 对方头像色：特意避开红色系，免得和"我方"的品牌红混淆。
     */
    private static final int[] AVATAR_COLORS = {
            0xFF3F7FA6, 0xFF4E7B3A, 0xFF8E5B8F, 0xFF3C7A89,
            0xFFA9713F, 0xFF6B4C8A, 0xFF2F6F6F, 0xFF7A5C2E
    };

    /** 说话人 -> 头像颜色 的缓存表，只增不减，键是 speaker 原文 */
    private final java.util.HashMap<String, Integer> speakerColors = new java.util.HashMap<>();
    /** 下一个要分配的颜色下标，分配后自增，与 AVATAR_COLORS 取模循环 */
    private int nextColorIndex = 0;

    /**
     * 按"第一次出现顺序"分配颜色，保证同一局里不同说话人颜色一定不同（最多 8 种）。
     *
     * <p>超过 8 个说话人时会绕回第 1 种颜色（剧本里最多 6 个 speaker，正常不会触发）。</p>
     *
     * @param speaker 说话人名字；null 或空串统一按 "?" 处理，避免空指针
     * @return 该说话人对应的颜色值（ARGB int）
     */
    private int colorForSpeaker(String speaker) {
        // 空名字统一归到 "?"，保证所有"无名氏"共用一个颜色
        String key = (speaker == null || speaker.isEmpty()) ? "?" : speaker;
        Integer c = speakerColors.get(key);
        if (c == null) {
            // 第一次见到这个说话人：按下标取色并登记，之后一直用这个颜色
            c = AVATAR_COLORS[nextColorIndex % AVATAR_COLORS.length];
            nextColorIndex++;
            speakerColors.put(key, c);
        }
        return c;
    }

    /**
     * 像素风头像：Color 区分说话人，type 决定画哪个小家伙（学生/学霸/猫/兔/熊/星）。
     *
     * @param color       头像底色（由 colorForSpeaker 分配，或"我"的品牌红）
     * @param typeIndex   形象下标 0-5，由说话人名字的 hash 稳定映射
     * @param marginRight true 表示头像是右侧的（"我"的消息），留右边距；
     *                    false 表示左侧的（对方消息），留左边距
     * @return 配置好尺寸与间距的头像 View
     */
    private AvatarView makeAvatar(int color, int typeIndex, boolean marginRight) {
        AvatarView av = new AvatarView(this);
        av.setAvatar(color, typeIndex);
        // 头像跟着字号一起长大，否则 2.1 倍时头像比气泡小一大截，看着断层
        int size = SettingsStore.scaleBox(this, Ui.dp(this, 30));
        int gap = SettingsStore.scaleBox(this, Ui.dp(this, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        // 头像与气泡之间的 8dp 间距，放在靠气泡那一侧
        if (marginRight) {
            lp.rightMargin = gap;
        } else {
            lp.leftMargin = gap;
        }
        av.setLayoutParams(lp);
        return av;
    }

    /**
     * 用名字稳定地映射到一个头像形象（同一个人每局都是同一张脸）。
     *
     * @param name 说话人名字
     * @return 0-5 的形象下标
     */
    private int speakerType(String name) {
        // 同一个名字 hashCode 稳定，所以每次进同一剧本都是同一张脸；
        // Math.abs 兜住负 hash，% 6 对应 AvatarView 里 6 种像素形象的编号
        String key = (name == null || name.isEmpty()) ? "?" : name;
        return Math.abs(key.hashCode()) % 6;
    }

    /**
     * "对方正在输入"的像素小气泡：左边一个头像，右边一个写着「· · ·」的灰气泡，
     * 三个点用透明度呼吸动画反复闪，等到第一条真实消息出现就被移除。
     *
     * @param speaker 正在输入的人（决定头像颜色和形象）
     * @return 整行视图（挂到消息区后由 renderNode 定时移除）
     */
    private View makeTyping(String speaker) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        // 和普通气泡一样留 8dp 的条目间距，出现/消失时不会"跳一下"
        rlp.bottomMargin = SettingsStore.scaleBox(this, Ui.dp(this, 8));
        row.setLayoutParams(rlp);

        // 左侧头像（和这个说话人后面所有消息的头像完全一致）
        row.addView(makeAvatar(colorForSpeaker(speaker), speakerType(speaker), true));

        // 右侧灰气泡：复用"对方气泡"的背景图，上下内边距比普通气泡小一点，显得更轻
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackgroundResource(R.drawable.px_bubble_other);
        int p = SettingsStore.scaleBox(this, Ui.dp(this, 10));
        int pv = SettingsStore.scaleBox(this, Ui.dp(this, 6));
        bubble.setPadding(p, pv, p, pv);
        TextView dots = Ui.text(this, "· · ·", 13, R.color.ink_hint);
        bubble.addView(dots);
        row.addView(bubble);

        // 三个小点透明度在 0.25 ~ 1 之间来回渐变，形成"正在输入"的呼吸感
        ObjectAnimator pulse = ObjectAnimator.ofFloat(dots, "alpha", 0.25f, 1f);
        pulse.setDuration(420);
        pulse.setRepeatCount(ObjectAnimator.INFINITE);
        pulse.setRepeatMode(ObjectAnimator.REVERSE);
        pulse.start();
        // 把动画挂在 Tag 上：气泡被 removeView 后动画对象仍被引用着，
        // 宿主 View 一销毁就一起回收，不会造成泄漏（也方便调试时找到它）
        row.setTag(pulse);
        return row;
    }

    /**
     * 幕次分隔条：居中一条细线 + 幕名。
     * 布局是「左细线(weight=1) + 幕名 + 右细线(weight=1)」，两条线自动等分剩余宽度。
     *
     * @param node 当前节点（用来取 stage 与 stageName）
     */
    private void addStageDivider(StoryNode node) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        // 分隔条上 6dp / 下 12dp：下面留得多一些，视觉上和后面的消息拉开距离
        rlp.topMargin = SettingsStore.scaleBox(this, Ui.dp(this, 6));
        rlp.bottomMargin = SettingsStore.scaleBox(this, Ui.dp(this, 12));
        row.setLayoutParams(rlp);

        // 左细线：weight=1 吃掉左半边剩余空间，高度 1dp（至少 1px，否则看不见）
        View left = new View(this);
        left.setBackgroundColor(getColor(R.color.paper_dark));
        left.setLayoutParams(new LinearLayout.LayoutParams(0, Math.max(1, Ui.dp(this, 1)), 1f));
        row.addView(left);

        // 中间幕名：写成「第 N 幕 · 幕名」，幕名为空时只写「第 N 幕」
        TextView chip = Ui.text(this, "第 " + node.stage + " 幕"
                + (node.stageName.isEmpty() ? "" : " · " + node.stageName), 9, R.color.ink_hint);
        // 字号放大后幕名会变长，最多两行并限制宽度，别把两侧的细线挤没了
        chip.setMaxLines(2);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        // 最大宽度：至少 72dp，最多屏幕宽度的 66%（两者取大，保证大屏上也有足够空间）
        chip.setMaxWidth(Math.max(Ui.dp(this, 72),
                (int) (getResources().getDisplayMetrics().widthPixels * 0.66f)));
        int chipPad = SettingsStore.scaleBox(this, Ui.dp(this, 10));
        chip.setPadding(chipPad, 0, chipPad, 0);
        row.addView(chip);

        // 右细线：与左细线对称
        View right = new View(this);
        right.setBackgroundColor(getColor(R.color.paper_dark));
        right.setLayoutParams(new LinearLayout.LayoutParams(0, Math.max(1, Ui.dp(this, 1)), 1f));
        row.addView(right);

        llMessages.addView(row);
    }

    /**
     * 气泡最大宽度占屏幕的比例。
     * 字号越大字越宽，固定 0.74 会逼得每行只剩两三个字，所以按档位逐级放宽。
     *
     * @return 0.74 / 0.80 / 0.86 三档比例
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
     * 气泡里文字的实际上限宽度（像素）。除了按比例放宽，还要扣掉两侧外边距、头像和气泡内边距，
     * 否则 2.1 倍时「我的消息」的气泡会把自己那侧的头像挤出屏幕。
     *
     * @param kind 0 = 对方（左侧带头像），1 = 我（右侧带头像），2 = 系统（无头像）
     */
    private int bubbleMaxWidth(int kind) {
        int width = getResources().getDisplayMetrics().widthPixels;
        int outer = SettingsStore.scaleBox(this, Ui.dp(this, 12)) * 2;    // 消息区左右外边距
        int pad = SettingsStore.scaleBox(this, Ui.dp(this, 10)) * 2;      // 气泡左右内边距
        int reserve = outer + pad;
        if (kind == 0 || kind == 1) {
            // 一条消息只在一侧有头像：头像宽 30dp + 间距 8dp 也要从可用宽度里扣掉
            reserve += SettingsStore.scaleBox(this, Ui.dp(this, 30))
                    + SettingsStore.scaleBox(this, Ui.dp(this, 8));
        }
        int fit = width - reserve;                    // 真正放得下的宽度
        int cap = (int) (width * bubbleWidthRatio()); // 按字号档位允许的上限
        // 两个上限取小（谁更严格听谁的），下限 60dp 兜住极端窄屏
        return Math.max(Ui.dp(this, 60), Math.min(cap, fit));
    }

    /**
     * 画一个消息气泡（一行）。横向 row 里按 kind 决定对齐——
     * 对方靠左「头像 + 气泡」；我靠右「气泡 + 头像」；系统居中「只有气泡」。
     *
     * @param speaker 说话人名字（kind=0 时显示在气泡顶部）
     * @param text    消息正文
     * @param kind    0 = 对方，1 = 我，2 = 系统
     */
    private void addBubble(String speaker, String text, int kind) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = SettingsStore.scaleBox(this, Ui.dp(this, 8));   // 每条消息之间 8dp
        row.setLayoutParams(rlp);
        // 三种对齐方式决定气泡站在屏幕的哪一侧，这是"聊天感"的来源
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
        // 三种气泡背景：对方浅灰、我方品牌红、系统更淡的灰
        if (kind == 0) {
            bubble.setBackgroundResource(R.drawable.px_bubble_other);
        } else if (kind == 1) {
            bubble.setBackgroundResource(R.drawable.px_bubble_me);
        } else {
            bubble.setBackgroundResource(R.drawable.px_bubble_system);
        }

        int maxW = bubbleMaxWidth(kind);

        // 对方消息在气泡顶部显示一行小字名字（自己发的不用显示名字）
        if (kind == 0 && speaker != null && !speaker.isEmpty()) {
            TextView name = Ui.text(this, speaker, 11, R.color.ink_hint);
            name.setMaxWidth(maxW);
            bubble.addView(name);
        }

        // 正文：像素字体 + 自动换行 + 最大宽度限制
        TextView body = new TextView(this);
        Ui.pixel(body);
        body.setText(text);
        // 走 sp，由 BaseActivity 的 fontScale 自动放大，这里绝对不要再乘倍数
        body.setTextSize(kind == 2 ? 12 : 15);
        body.setLineSpacing(Ui.dp(this, 3), 1f);    // 行间距 3dp，大字号下也读得开
        body.setMaxWidth(maxW);
        // 三种字色：我方白字（压在品牌红上）、系统次要灰、对方主墨色
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

        // "我"的头像用当前用户设置的形象（UserStore.avatar），底色固定品牌红
        if (kind == 1) {
            row.addView(makeAvatar(getColor(R.color.brand), UserStore.avatar(this), false));
        }

        llMessages.addView(row);
    }

    /**
     * 画一个选项按钮：编号 + 文字，整行可点，点下去进 onChoice。
     * 按钮上<b>不显示风险提示</b>——风险要到复盘页才揭晓，否则玩家一眼就能"选对的"。
     *
     * @param index 显示用的编号（从 1 开始）
     * @param c     选项数据（文字、下一节点、风险、话费、反馈都由它带）
     */
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
        lp.bottomMargin = SettingsStore.scaleBox(this, Ui.dp(this, 8));   // 按钮之间 8dp
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

    /**
     * 核心逻辑：记录 → 展示结果 → 进入下一节点。
     *
     * <p>点了选项之后发生三件事：
     * <ol>
     *   <li>把这一步记进 {@link PathRecorder}（复盘页靠它回放）；</li>
     *   <li>在聊天区补两条消息——先"我"说这句话，再一条系统消息报告这次的损失；</li>
     *   <li>如果这个选项指向下一节点就继续渲染，指不到就进复盘页。</li>
     * </ol></p>
     */
    private void onChoice(Choice c) {
        if (current == null) {
            return;
        }
        // 1) 记录到路径（chapter 用当前节点的幕名，复盘页显示"在哪一幕做了什么选择"）
        recorder.record(current.id, current.stageName, c);
        int step = recorder.getSteps().size();
        AppLog.i("CHAT", "选择 " + step + " " + riskLabel(c.risk) + " ￥" + c.cost + " " + c.text);

        // 2) 先在聊天区补上"我"的这句话（右侧气泡），再补一条系统结算
        addBubble("我", c.text, 1);
        if (c.cost > 0) {
            addBubble("系统", "你转出 " + Ui.money(c.cost) + " 元", 2);
        } else {
            addBubble("系统", "这一次你没有转账", 2);
        }
        updateLoss();       // 顶栏账本跟着滚动增长
        AppLog.i("CHAT", "累计损失 ￥" + recorder.totalCost() + " 步数 " + step);
        // 选项点完立刻清空，避免连点两次造成重复记录
        llOptions.removeAllViews();

        // 3) 找下一节点：next 为空/空白都当作"没有下一节点"
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

    /** 运行日志用：把数据里的英文风险等级（high / medium / 其它）换成中文名 */
    private static String riskLabel(String risk) {
        if ("high".equals(risk)) {
            return "高风险";
        }
        if ("medium".equals(risk)) {
            return "中风险";
        }
        return "低风险";
    }

    /** 结局卡：一块浅色面板写结局标题 + 建议，下面再接一个「去复盘」大按钮（结束节点唯一出口） */
    private void addEndingCard(final StoryNode node) {
        LinearLayout card = Ui.card(this, 14, 8);
        // 卡片里装的是结局标题+建议，内边距和下边距一起跟着字号放大
        int cp = SettingsStore.scaleBox(this, Ui.dp(this, 14));
        card.setPadding(cp, cp, cp, cp);
        // Ui.card 返回的卡片带 LayoutParams，这里改它的下边距让卡片和按钮之间有 8dp 呼吸
        if (card.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams clp = (LinearLayout.LayoutParams) card.getLayoutParams();
            clp.bottomMargin = SettingsStore.scaleBox(this, Ui.dp(this, 8));
            card.setLayoutParams(clp);
        }
        // 标题加粗，是这张卡最醒目的信息（"你守住了" / "你上当了"）
        TextView title = Ui.text(this, node.endingTitle, 17, R.color.ink);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLineSpacing(Ui.dp(this, 3), 1f);
        card.addView(title);
        // 建议文字可选：有就加，没有就只有标题
        if (!node.endingAdvice.isEmpty()) {
            TextView advice = Ui.text(this, node.endingAdvice, 13, R.color.ink_secondary);
            advice.setPadding(0, SettingsStore.scaleBox(this, Ui.dp(this, 8)), 0, 0);
            advice.setLineSpacing(Ui.dp(this, 3), 1f);
            card.addView(advice);
        }
        llMessages.addView(card);

        // 「去复盘」按钮：品牌红底白字，占满整行，点它进复盘页
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

    /**
     * 进复盘页：先把这一局的成绩写进用户档案，再把结局信息传过去。
     *
     * @param ending 结局节点；为 null 表示"没走到任何结局就断了"，此时传空串由复盘页兜底
     */
    private void goReview(StoryNode ending) {
        // 记录一次通关 + 累计损失，书架首页会显示"已读 N 本"
        UserStore.addPlay(this);
        UserStore.addLoss(this, recorder.totalCost());

        String endingTitle = ending == null ? "" : ending.endingTitle;
        AppLog.i("CHAT", "进入复盘 损失 ￥" + recorder.totalCost()
                + " 步数 " + recorder.getSteps().size()
                + " 结局「" + (endingTitle.isEmpty() ? "无" : endingTitle) + "」");

        // 只传三样东西：剧本 id、结局标题、结局建议；完整路径在 PathRecorder 单例里，不用序列化
        Intent i = new Intent(this, ReviewActivity.class);
        i.putExtra("script_id", script.id);
        i.putExtra("ending_title", endingTitle);
        i.putExtra("ending_advice", ending == null ? "" : ending.endingAdvice);
        startActivity(i);
        overridePendingTransition(R.anim.page_in, R.anim.page_out);   // 统一页面切换动画
        finish();       // 复盘页返回时直接回首页，不再回到聊天页
    }

    /** 账本当前已经显示到的金额（动画起点），与 recorder.totalCost() 一起决定动画区间 */
    private int shownLoss = 0;

    /**
     * 损失金额滚动增长，并轻微放大提示一下：用 {@link ValueAnimator} 从旧值滚到新值（420ms），
     * 同时把数字放大到 1.18 倍再弹回，让"又亏钱了"更醒目；金额没变时只刷新文字、不跑动画。
     */
    private void updateLoss() {
        int target = recorder.totalCost();
        if (target == shownLoss) {
            // 没变化（例如重来后都是 0）：只更新文字，省掉一次动画
            tvLoss.setText("已损失 ¥" + Ui.money(target));
            return;
        }
        ValueAnimator va = ValueAnimator.ofInt(shownLoss, target);
        va.setDuration(420);
        va.setInterpolator(new DecelerateInterpolator());   // 先快后慢，数字滚得更自然
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int v = (int) animation.getAnimatedValue();
                tvLoss.setText("已损失 ¥" + Ui.money(v));
            }
        });
        va.start();
        shownLoss = target;      // 记下新值，下次动画从这里开始
        // 数字放大 1.18 倍 → 弹回原大小，两段动画串起来做"心跳"效果
        tvLoss.animate().scaleX(1.18f).scaleY(1.18f).setDuration(120)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        tvLoss.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
                    }
                }).start();
    }

    /**
     * 把消息区滚到最底部。用 post 而不是直接 fullScroll：刚 addView 的新气泡此刻还没测量完，
     * 立刻滚会滚不到真正的底部，等下一帧布局完成后再滚才准确。
     */
    private void scrollToBottom() {
        svMessages.post(new Runnable() {
            @Override
            public void run() {
                svMessages.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    /**
     * 「重来」确认弹窗：提示当前进度会清空，确认后调 startScript 从头开始。
     * 用像素风 {@link PixelDialog}，和整个 App 的视觉统一。
     */
    private void confirmRestart() {
        PixelDialog dlg = PixelDialog.create(this);
        dlg.title("重来一次？");
        dlg.body("当前进度会被清空，损失金额也会归零。");
        dlg.button("重来", true, new PixelDialog.OnAction() {
            @Override
            public void onAction(int index) {
                // 日志里留一条旧成绩，方便排查"重来后数据不对"之类的问题
                AppLog.i("CHAT", "重来按钮 清空进度 原损失 ￥"
                        + recorder.totalCost() + " 步数 " + recorder.getSteps().size());
                startScript();
            }
        }, 0);
        dlg.button("取消", false, null, 1);
        dlg.show();
    }

    /**
     * 返回键/左上角「←」：先弹确认，避免误触把一整局进度丢掉。
     * 进度本来就不落盘，退出即丢弃，所以文案直接写"进度不会保存"。
     */
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
