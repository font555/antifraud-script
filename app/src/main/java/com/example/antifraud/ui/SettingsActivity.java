package com.example.antifraud.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.antifraud.R;
import com.example.antifraud.engine.SettingsStore;

/**
 * 字体与字号页：只放「字体样式」和「字号大小」两件事。
 * 清除记录、关于等入口都在「我的」页，这里不再重复。
 * 全部存在本机 SharedPreferences，不联网。
 *
 * <p><b>从哪里进来：</b>{@link MainActivity}（我的 → 设置）、{@link MineTabView} 的设置行。</p>
 *
 * <p><b>设置项存在哪：</b>{@link SettingsStore}（SharedPreferences），
 * 键为 font_style / font_scale。改完立刻 {@code recreate()} 重建本页，
 * 让新字号马上生效——不需要退出去再进来。</p>
 *
 * <p><b>⚠ 字号设置和"二次放大"的关系（重要）：</b></p>
 * <p>这里选的倍数由 {@link BaseActivity#attachBaseContext} 写进 Configuration.fontScale，
 * 之后全 App 的 <b>sp</b> 文字都会被系统自动乘一次。所以：</p>
 * <ul>
 *   <li>代码里 {@code setTextSize(12)} 就是 12sp，<b>不要</b>再乘 fontScale；</li>
 *   <li>只有用 <b>dp</b> 写死的"容器尺寸"（chip 高度、卡片内边距）才需要用
 *       {@link SettingsStore#scaleBox} 手动放大，否则字变大了框不变，文字会被裁掉。</li>
 * </ul>
 * <p>本页的 {@link #fontChip} 和 {@link #sizeChip} 就是这条规则的实例：
 * 高度用 scaleBox 放大，字号只写基准值。</p>
 */
public class SettingsActivity extends BaseActivity {

    /**
     * 页面创建：绑返回按钮，然后铺内容。
     *
     * @param savedInstanceState 系统恢复用的状态包，本页不使用（内容每次按当前设置重画）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Ui.insetContent(this);   // Android 15+ 边到边适配

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                overridePendingTransition(R.anim.page_in, R.anim.page_out);
            }
        });

        build();   // 首次铺内容
    }

    /**
     * 回到前台时重画一次。
     *
     * <p>为什么 onResume 还要再 build 一次：用户可能在别处（比如"我的"页）改过设置，
     * 或者从别的页面切回来时设置已被改；重画能保证看到的一定是最新状态。
     * 因为 {@link #build} 第一步就是 removeAllViews，重复调用不会出现重复卡片。</p>
     */
    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    /**
     * 铺出整页内容：标题 + 「字体样式」卡 + 「字号大小」卡。
     *
     * <p>整页都是代码拼的，不走 layout 里写死：因为字号档位、可选项数量都可能变，
     * 用代码拼才能保证换档位时布局自己跟着重排。</p>
     */
    private void build() {
        LinearLayout root = findViewById(R.id.ll_root);
        root.removeAllViews();   // 先清空，保证重复调用不会叠加

        root.addView(Ui.text(this, "字体与字号", 22, R.color.ink));

        // ---- 字体样式 ----
        LinearLayout fontCard = scaledCard(14, 12);
        fontCard.addView(Ui.text(this, "字体样式", 12, R.color.ink));
        LinearLayout fontRow = new LinearLayout(this);
        fontRow.setOrientation(LinearLayout.HORIZONTAL);
        // 标题与 chip 之间留 10dp（按字号放大），不然"超大"档下会贴在一起
        fontRow.setPadding(0, SettingsStore.scaleBox(this, Ui.dp(this, 10)), 0, 0);
        int cur = SettingsStore.fontStyle(this);   // 当前选中的字体样式，用来决定哪个 chip 高亮
        fontRow.addView(fontChip("像素字体", SettingsStore.FONT_PIXEL));
        fontRow.addView(fontChip("系统字体", SettingsStore.FONT_SYSTEM));
        fontCard.addView(fontRow);
        // 一句解释：给"为什么要有两个选项"一个理由，而不是让用户瞎猜
        TextView fontHint = Ui.text(this, "像素字体有游戏感；看久了觉得累眼，就切到系统字体。", 9, R.color.ink_hint);
        fontHint.setPadding(0, SettingsStore.scaleBox(this, Ui.dp(this, 8)), 0, 0);
        fontHint.setLineSpacing(SettingsStore.scaleBox(this, Ui.dp(this, 3)), 1f);
        fontCard.addView(fontHint);
        root.addView(fontCard);

        // ---- 字号 ----
        LinearLayout sizeCard = scaledCard(14, 12);
        sizeCard.addView(Ui.text(this, "字号大小", 12, R.color.ink));
        // 用 FlowLayout 自动折行：4 个档位在「超大」字号下一行放不下，
        // 会自动掉到第二行，不需要用户左右滑动
        FlowLayout sizeRow = new FlowLayout(this);
        LinearLayout.LayoutParams sizeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sizeLp.topMargin = SettingsStore.scaleBox(this, Ui.dp(this, 10));
        sizeRow.setLayoutParams(sizeLp);
        // 横向 / 纵向间距都按字号放大，档位掉到第二行时不会和第一行挤在一起
        sizeRow.setGaps(SettingsStore.scaleBox(this, Ui.dp(this, 6)),
                SettingsStore.scaleBox(this, Ui.dp(this, 6)));
        float curScale = SettingsStore.fontScale(this);
        // 四档：标准 1.0 / 大 1.3 / 特大 1.7 / 超大 2.1（超大 = 标准的两倍再多一点）
        // 每档都传"是否选中"，选中态由 near() 判断（带 0.05 容差，防浮点误差认错档）
        sizeRow.addView(sizeChip("标准", near(curScale, SettingsStore.SCALE_STANDARD), SettingsStore.SCALE_STANDARD));
        sizeRow.addView(sizeChip("大", near(curScale, SettingsStore.SCALE_LARGE), SettingsStore.SCALE_LARGE));
        sizeRow.addView(sizeChip("特大", near(curScale, SettingsStore.SCALE_XLARGE), SettingsStore.SCALE_XLARGE));
        sizeRow.addView(sizeChip("超大", near(curScale, SettingsStore.SCALE_HUGE), SettingsStore.SCALE_HUGE));
        sizeCard.addView(sizeRow);
        // 解释"调大会影响什么"：让用户知道这不是只影响本页，而是全 App 生效
        TextView sizeHint = Ui.text(this, "调大后所有页面的文字和按钮都会跟着变大；「超大」约为标准的两倍，适合视力不便的使用者。", 9, R.color.ink_hint);
        sizeHint.setPadding(0, SettingsStore.scaleBox(this, Ui.dp(this, 8)), 0, 0);
        sizeHint.setLineSpacing(SettingsStore.scaleBox(this, Ui.dp(this, 3)), 1f);
        sizeCard.addView(sizeHint);
        root.addView(sizeCard);
    }

    /**
     * 白色卡片，内边距与下边距按字号放大。
     * Ui.card() 是全局共用的，不改它；这里在它的结果上再套一层 scaleBox。
     *
     * <p>为什么要"套一层"而不是改 Ui.card：Ui.card 被全项目几十处调用，
     * 一旦在里面加 scaleBox，所有页面的卡片内边距都会跟着变，风险太大。
     * 只在设置页这一个页面临时放大，是最小改动。</p>
     *
     * @param paddingDp      卡片四周内边距（dp）
     * @param marginBottomDp 卡片下边距（dp）
     * @return 已按字号放大过内边距的卡片
     */
    private LinearLayout scaledCard(float paddingDp, float marginBottomDp) {
        LinearLayout card = Ui.card(this, paddingDp, marginBottomDp);
        int p = SettingsStore.scaleBox(this, Ui.dp(this, paddingDp));
        card.setPadding(p, p, p, p);   // 覆盖 Ui.card 设的固定内边距
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = SettingsStore.scaleBox(this, Ui.dp(this, marginBottomDp));
        card.setLayoutParams(lp);     // 覆盖 Ui.card 设的固定下边距
        return card;
    }

    /**
     * 造一个"字体样式"选择块。
     *
     * <p>样式规则：选中 = 主题色实心 + 白字；未选中 = 白底描边 + 深字。
     * 点击后写入设置并 {@code recreate()}，让新字体立刻生效。</p>
     *
     * @param text  块上显示的文字（"像素字体" / "系统字体"）
     * @param style 这个块代表的样式常量（{@link SettingsStore#FONT_PIXEL} 或 FONT_SYSTEM）
     * @return 可直接 addView 的选择块
     */
    private TextView fontChip(String text, final int style) {
        final boolean selected = SettingsStore.fontStyle(this) == style;
        TextView tv = new PTextView(this);
        tv.setText(text);
        tv.setTextSize(12);           // 单位 sp，由 fontScale 自动放大（只放大这一次）
        tv.setGravity(Gravity.CENTER);
        // 字体 chip：固定 44dp 高要装下放大后的 12sp，必须跟着字号长
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, SettingsStore.scaleBox(this, Ui.dp(this, 44)), 1f);   // 宽度 0 + weight 1：两个 chip 平分整行
        int chipGap = SettingsStore.scaleBox(this, Ui.dp(this, 3));
        lp.setMargins(chipGap, 0, chipGap, 0);
        tv.setLayoutParams(lp);
        tv.setTextColor(getColor(selected ? R.color.white : R.color.ink));
        tv.setBackgroundResource(selected ? R.drawable.px_button : R.drawable.px_option);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SettingsStore.setFontStyle(SettingsActivity.this, style);
                recreate();   // 重建页面：字体是创建控件时套上去的，不重建不会变
            }
        });
        return tv;
    }

    /** 判断当前倍数是否落在某一档（容忍 0.05 误差） */
    private boolean near(float cur, float target) {
        // 用容差比较而不是 ==：float 存进 SharedPreferences 再读出来会有极小的误差，
        // 直接相等在"刚好 1.3"这类值上可能永远判不中
        return Math.abs(cur - target) < 0.05f;
    }

    /**
     * 造一个"字号档位"选择块。
     *
     * <p>和 {@link #fontChip} 的差别：这个是 WRAP_CONTENT 宽度，四个并排；
     * 字体 chip 是两个平分整行。高度都按字号放大，但左右内边距刻意只放大到 1.4 倍。</p>
     *
     * @param text     档位名（"标准" / "大" / "特大" / "超大"）
     * @param selected 当前是否选中（决定配色）
     * @param scale    这个块对应的字号倍数，点击后写入设置
     * @return 可直接 addView 的档位块
     */
    private TextView sizeChip(String text, boolean selected, final float scale) {
        TextView tv = new PTextView(this);
        tv.setText(text);
        tv.setTextSize(12);           // 单位 sp，随全局字号放大
        tv.setGravity(Gravity.CENTER);
        // 字号 chip：高度放大，但左右内边距只放大到 1.4 倍。
        // 4 个档位要在一屏内排开，内边距若按 1.9 倍放大会把「超大」挤出屏幕外。
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                SettingsStore.scaleBox(this, Ui.dp(this, 44)));
        int chipGap = SettingsStore.scaleBox(this, Ui.dp(this, 3));
        lp.setMargins(chipGap, 0, chipGap, 0);
        tv.setLayoutParams(lp);
        // 关键：这里用 Math.min(1.4f, fontScale) 手动封顶，就是上面说的"内边距只放大到 1.4 倍"
        int chipPadH = Math.round(Ui.dp(this, 18) * Math.min(1.4f, SettingsStore.fontScale(this)));
        tv.setPadding(chipPadH, 0, chipPadH, 0);
        tv.setMaxLines(1);            // 档位文字绝不换行（"特大"换行会变成两行，高度不够）
        tv.setTextColor(getColor(selected ? R.color.white : R.color.ink));
        tv.setBackgroundResource(selected ? R.drawable.px_button : R.drawable.px_option);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SettingsStore.setFontScale(SettingsActivity.this, scale);
                recreate();   // 重建页面：fontScale 在 attachBaseContext 里读取，只有重建才会重新应用
            }
        });
        return tv;
    }
}
