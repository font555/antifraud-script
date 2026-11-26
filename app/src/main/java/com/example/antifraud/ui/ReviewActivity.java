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
 *
 * <p><b>从哪里进来：</b>{@link ChatActivity} 走到结局节点时，带着
 * {@code script_id}（剧本 id）和 {@code ending_title}（结局标题）跳过来。</p>
 *
 * <p><b>数据从哪来：</b>不读数据库、不读文件，全部来自内存里的单例
 * {@link PathRecorder}——聊天页每点一次选项就记一笔，本页把这一局的账
 * 原样铺开给用户看。App 一退出这笔账就没了，这也符合"只复盘当前这一局"的产品设定。</p>
 *
 * <p><b>页面结构（自上而下）：</b></p>
 * <ol>
 *   <li>返回栏 + 结局标题；</li>
 *   <li>结算卡：本局损失（数字滚动到最后）、真实案件损失对照、步数/高风险/止损统计；</li>
 *   <li>逐节点时间轴：每一步一张卡，错开 70ms 依次淡入并右向左滑入；</li>
 *   <li>底部三个按钮：查看来源、重玩另一条路、生成分享海报。</li>
 * </ol>
 *
 * <p><b>为什么说它是作品的核心：</b>同类反诈产品大多只做"点错了就弹一句话"，
 * 学生点完就忘。本页把整局的选择重新铺一遍：你当时点的是哪句、这句话为什么危险、
 * 这一步赔了多少钱、真实案件里同龄人赔了多少。复盘的价值就在于"事后把代价看清"，
 * 而不是"当场被骂一顿"。</p>
 *
 * <p><b>三个产品细节：</b></p>
 * <ul>
 *   <li>损失数字从 0 滚上来，而不是直接显示结果——制造"结算"的心理停顿；</li>
 *   <li>低风险步骤的讲解默认折叠，只留错误步骤全展开——避免正确选择把页面刷满；</li>
 *   <li>"重玩另一条路"是设计重点：抵抗一次不代表安全，鼓励换条路再走一遍。</li>
 * </ul>
 */
public class ReviewActivity extends BaseActivity {

    /**
     * 页面创建：拼出整张复盘报告。
     *
     * @param savedInstanceState 系统恢复用的状态包，本页不依赖它（每次进入都按当前这一局重画）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_review);
        Ui.insetContent(this);   // Android 15+ 边到边：给内容区补状态栏/导航栏内边距

        LinearLayout root = findViewById(R.id.ll_root);   // 整页的竖向容器，下面所有内容都 addView 到这里

        // 从聊天页带过来的两个参数：剧本 id 决定来源/标题，结局标题决定报告头
        // 注意：剧本内容本身不带过来，只带 id，进本页再按 id 重新加载，避免长数据跨页面传递
        final String scriptId = getIntent().getStringExtra("script_id");
        String endingTitle = getIntent().getStringExtra("ending_title");
        // 按 id 重新加载剧本（只为了拿剧名与真实案件损失，剧情本身不带过来）
        Script script = ScriptRepository.load(this, scriptId);

        // 本局的"账本"：单例，聊天页已经一步步记好了
        PathRecorder rec = PathRecorder.get();
        List<PathRecorder.Step> steps = rec.getSteps();   // 按点击顺序排好的全部步骤
        int total = rec.totalCost();                      // 累计损失
        AppLog.i("REVIEW", "进入复盘 剧本 " + scriptId + " 损失 ￥" + total
                + " 步数 " + steps.size() + " 高风险 " + rec.highRiskCount() + " 次");

        // 0) 顶部返回栏
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);   // 箭头和标题垂直居中对齐
        TextView back = Ui.text(this, "←", 22, R.color.ink);
        // 左边留 4dp、右边留 14dp：把箭头和标题拉开一点，同时保证点击热区够大
        back.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                // 和进入本页的动画反向，视觉上"退回去"
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
        sub.setPadding(0, Ui.dp(this, 8), 0, 0);      // 与返回栏拉开 8dp
        sub.setLineSpacing(Ui.dp(this, 3), 1f);       // 结局标题可能很长，行距放宽免得挤
        root.addView(sub);

        // 1) 结算卡：你的损失 vs 真实案件
        // 这张卡是整页的"视觉锤"：先让你看见自己的损失，再立刻用真实案件数字压一下，
        // 两个数字放同一个框里，冲击力比分开放强得多。
        LinearLayout card = Ui.card(this, 16, 12);
        // 先按"最终值"配色和文案占位，随后由下面的动画把数字从 0 滚上来
        final TextView big = Ui.text(this,
                total > 0 ? "你损失 ¥0" : "你成功止损，损失 ¥0",
                26, total > 0 ? R.color.warn_red : R.color.risk_low);
        card.addView(big);

        if (script != null && script.referenceLoss > 0) {
            // 真实案件损失对照：这一句是作品"有据可查"的证据，不能省
            TextView compare = Ui.text(this,
                    script.referenceNote + "\n真实案件损失 ¥" + Ui.money(script.referenceLoss),
                    12, R.color.ink_secondary);
            compare.setPadding(0, Ui.dp(this, 8), 0, 0);
            compare.setLineSpacing(Ui.dp(this, 4), 1f);
            card.addView(compare);
        }

        // 一行统计：走了几步、冒了几次险、及时止损几次
        TextView stat = Ui.text(this,
                "共 " + steps.size() + " 步  ·  高风险 " + rec.highRiskCount()
                        + " 次  ·  及时止损 " + rec.lowRiskCount() + " 次",
                10, R.color.ink_hint);
        // 三个数字的定义：
        //   共 N 步   —— 这一局一共点了多少次选项（PathRecorder 里记了几条）
        //   高风险 N 次 —— 其中 risk=high 的次数，直观说明"走了几步险棋"
        //   及时止损 N 次 —— risk=low 的次数，说明"忍住了几次"
        stat.setPadding(0, Ui.dp(this, 10), 0, 0);
        card.addView(stat);
        root.addView(card);

        // 数字滚动：从 0 滚到本局总额，700ms 减速插值，数字一路减速停下，
        // 比直接显示最终值更有"结算"的仪式感（也让学生盯着这个数字看两秒）
        ValueAnimator va = ValueAnimator.ofInt(0, total);
        va.setDuration(700);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                int v = (int) a.getAnimatedValue();
                big.setText("你损失 ¥" + Ui.money(v));   // 每帧刷新一次文本
            }
        });
        va.start();

        // 2) 逐节点时间轴：一张张滑进来
        // 为什么用延迟动画而不是一次性铺开：一屏同时出现七八张卡片，眼睛不知道从哪看；
        // 错开 70ms 依次入场，视线会自然跟着最新出现的那张往下走，等于替用户"读"了一遍顺序。
        for (int i = 0; i < steps.size(); i++) {
            View item = buildStep(steps.get(i));
            item.setAlpha(0f);                          // 先设成完全透明
            item.setTranslationX(Ui.dp(this, 28));      // 再往右偏 28dp，营造"从右侧滑入"
            item.animate().alpha(1f).translationX(0f)
                    .setDuration(220)
                    .setStartDelay(320 + i * 70)        // 每张卡比上一张晚 70ms，形成瀑布式入场
                    .start();
            root.addView(item);
        }

        // 3) 底部按钮
        // 三个按钮纵向排列、自上而下"主要 → 次要 → 附加"：
        // 主按钮（实心）是"看来源"，因为答辩现场最常被问"依据呢"；
        // 次按钮"重玩"是产品上最想鼓励的行为；海报则是可选的分享动作。
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        // 顶部 8dp、底部按字号放大 24dp：给最后一张卡和屏幕底边之间留呼吸空间
        actions.setPadding(0, Ui.dp(this, 8), 0, SettingsStore.scaleBox(this, Ui.dp(this, 24)));

        // 按钮一：查看本剧本的真实案例来源（答辩时"依据呢"就点这里）
        actions.addView(actionButton("查看真实案例来源", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(ReviewActivity.this, SourcesActivity.class);
                i.putExtra("script_id", scriptId);   // 只显示这一部剧本的来源，不铺全部
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
        // 内边距按字号放大，否则"超大"档下 14sp 的字会把按钮撑出边界
        int sharePad = SettingsStore.scaleBox(this, Ui.dp(this, 14));
        shareBtn.setPadding(sharePad, sharePad, sharePad, sharePad);
        shareBtn.setTextColor(getColor(R.color.ink));
        shareBtn.setBackgroundResource(R.drawable.px_option);   // 次要按钮：白底描边
        shareBtn.setClickable(true);
        LinearLayout.LayoutParams shareLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        shareLp.topMargin = Ui.dp(this, 8);          // 和上面两个按钮拉开距离
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

    /**
     * 造"时间轴里的一步"卡片：第几步 · 幕名 → 你选了哪句 → 风险标签/损失 → 讲解。
     *
     * <p>低风险的步骤默认把讲解折叠起来，点卡片才展开——因为低风险步骤通常是对的，
     * 讲解属于"补充说明"，全展开会把整页拉得很长，真正的错误反而被淹没。</p>
     *
     * @param s 一条步骤记录（来自 PathRecorder）
     * @return 组装好的卡片视图，调用方负责做动画和 addView
     */
    private View buildStep(final PathRecorder.Step s) {
        LinearLayout card = Ui.card(this, 14, 10);

        // 第一行：第 N 步 · 幕名（幕名为空时只显示步数，避免出现一个孤零零的「·」）
        card.addView(Ui.text(this, "第 " + s.index + " 步"
                + (s.stageName.isEmpty() ? "" : "  ·  " + s.stageName), 10, R.color.ink_hint));

        // 第二行：原样引用玩家当时点的那句选项文字，让人有"我当时真是这么点的"的实感。
        // 这里刻意不改写、不总结选项原文，复盘要的是"照镜子"，不是"再讲一遍道理"。
        TextView pick = Ui.text(this, "你选了「" + s.choiceText + "」", 14, R.color.ink);
        pick.setPadding(0, Ui.dp(this, 6), 0, 0);
        pick.setLineSpacing(Ui.dp(this, 3), 1f);
        card.addView(pick);

        // 第三行：风险标签 + 品类标签 + 这一步的损失金额（三者横向排一行）
        LinearLayout tagRow = new LinearLayout(this);
        tagRow.setOrientation(LinearLayout.HORIZONTAL);
        tagRow.setGravity(Gravity.CENTER_VERTICAL);
        tagRow.setPadding(0, Ui.dp(this, 8), 0, 0);
        tagRow.addView(Ui.riskChip(this, s.risk));                    // 高中低风险小标签
        if (s.tag != null && !s.tag.isEmpty()) {
            tagRow.addView(Ui.text(this, "  " + s.tag, 10, R.color.ink_secondary));   // 风险品类
        }
        if (s.cost > 0) {
            // 有损失才显示金额，没有损失就不占位（避免满屏 -¥0）
            tagRow.addView(Ui.text(this, "   -¥" + Ui.money(s.cost), 10, R.color.warn_red));
        }
        card.addView(tagRow);

        // 第四行：讲解（为什么危险 + 正确做法）
        final TextView why = Ui.text(this, s.feedback, 12, R.color.ink_secondary);
        why.setPadding(0, Ui.dp(this, 8), 0, 0);
        why.setLineSpacing(Ui.dp(this, 4), 1f);
        card.addView(why);

        // 低风险步骤默认折叠，点一下展开
        if ("low".equals(s.risk)) {
            why.setVisibility(View.GONE);    // 初始隐藏，只留前三行，页面更清爽
            card.setClickable(true);
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // 用可见性取反做开关：可见再点就收起，收起再点就展开
                    boolean show = why.getVisibility() != View.VISIBLE;
                    why.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });
        }
        return card;
    }

    /**
     * 造一个整宽的底部按钮。
     *
     * @param label   按钮文字
     * @param primary true=主按钮（主题色实心），false=次按钮（白底描边）
     * @param l       点击回调
     * @return 已设好样式和点击事件的按钮
     */
    private View actionButton(String label, boolean primary, View.OnClickListener l) {
        TextView btn = new PTextView(this);
        btn.setText(label);
        btn.setTextSize(17);
        btn.setGravity(Gravity.CENTER);
        // 内边距按字号放大（封顶 1.9 倍），保证"超大"档下按钮能装下文字
        int p = SettingsStore.scaleBox(this, Ui.dp(this, 14));
        btn.setPadding(p, p, p, p);
        btn.setClickable(true);
        if (primary) {
            btn.setTextColor(getColor(R.color.white));       // 主按钮：白字
            btn.setBackgroundResource(R.drawable.px_button); // + 主题色实心
        } else {
            btn.setTextColor(getColor(R.color.ink));         // 次按钮：深色字
            btn.setBackgroundResource(R.drawable.px_option); // + 白底描边
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 8);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(l);
        return btn;
    }

    /**
     * 生成 1080×1350 复盘海报，再以 content:// 交给系统分享面板。
     *
     * <p>整条链路：{@link SharePoster#generate} 把图写到 cacheDir/share/poster.png →
     * 这里拼一个 ACTION_SEND 意图，附件用 content://com.example.antifraud.poster/poster.png →
     * {@link PosterProvider} 负责把文件以只读方式交给选中的应用。
     * 全程不需要存储权限，也不会把图片落到公共相册。</p>
     *
     * @param script 当前剧本，可为 null（海报会降级显示"暂未收录"）
     * @param steps  本局步骤，用于挑高风险摘要
     * @param total  本局损失总额
     */
    private void sharePoster(Script script, List<PathRecorder.Step> steps, int total) {
        File file = SharePoster.generate(this, script, steps, total,
                script == null ? "防骗复盘" : script.title);
        if (file == null) {
            // 生成失败只提示，不影响用户继续看复盘
            Toast.makeText(this, "海报生成失败，请稍后再试", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = Uri.parse("content://com.example.antifraud.poster/poster.png");
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("image/png");
        send.putExtra(Intent.EXTRA_STREAM, uri);      // 附件地址
        // setClipData 是必须的：部分 ROM 只认 ClipData 里的 URI，不设的话对方会拿到空附件
        send.setClipData(ClipData.newUri(getContentResolver(), "复盘海报", uri));
        // 临时授予接收方读取权限；不加重启后即失效，安全
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, "分享复盘海报"));
    }
}
