/*
 * ---------------------------------------------------------------------------
 * ScriptPickerActivity —— 书架「翻开一本书」之后的书页（分类下的剧本列表）
 *
 * 这个页面做的事很简单：把某个骗局分类下的所有剧本列出来，
 * 每本剧本一张卡片（骗局类型 + 预计时长 + 标题 + 简介 + 节点数/来源数），
 * 用户点任意一张，就带着 script_id 跳进聊天页 ChatActivity 开始玩。
 *
 * 从哪进来：首页书架（HomeTabView）点某一本书 → 传 category_id / category_name 进来。
 *
 * 传参：Intent extra 两个 —— "category_id"（分类 id，例如 game）、
 *       "category_name"（分类中文名，用于顶部大标题）。
 *
 * 数据从哪来：ScriptRepository.loadByCategory() 读 assets 里的内置剧本，
 * 本页只读不写，不会修改任何剧本内容。
 *
 * 阅读提示：本文件只加注释、不改代码。
 * ---------------------------------------------------------------------------
 */
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
 *
 * <p>页面结构：顶部返回 + 分类大标题 + 一行副标题（共几个剧本）；
 * 中间一列剧本卡片；最下面一张「本册统计 + 可溯源声明」的说明卡。
 *
 * <p>本页没有任何写操作，所有数据都来自 assets 里的内置剧本 JSON，
 * 所以不涉及权限、不涉及存储，符合项目「零权限零联网」的要求。
 *
 * <p>为什么不用 RecyclerView：整个 App 只有几本剧本，一屏就能装下大半，
 * 普通 LinearLayout 塞进 ScrollView 就够用；RecyclerView 需要额外依赖，
 * 而本项目坚持「零依赖」，所有控件都是原生 View 手写出来的。
 *
 * <p>本页不需要处理返回键的特殊逻辑：按系统返回键就是默认的 finish()，
 * 顶部那个「返回」按钮只是给用户一个看得见的出口。
 */
public class ScriptPickerActivity extends BaseActivity {

    /**
     * 页面创建：取分类 → 读剧本列表 → 造卡片 → 放入场动画。
     *
     * @param savedInstanceState 系统重建页面时回传的状态；本页每次都重新从 assets 读数据，
     *                           所以不使用它
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 1. 挂上布局 activity_picker.xml（标题区 + 可滚动的卡片列表）
        setContentView(R.layout.activity_picker);
        // 状态栏 / 导航栏安全边距
        Ui.insetContent(this);

        // 2. 从 Intent 取出分类 id 和分类中文名。
        //    categoryId 后面要给查询用，所以声明成 final（匿名内部类里不用它，但保持只读语义）
        final String categoryId = getIntent().getStringExtra("category_id");
        String categoryName = getIntent().getStringExtra("category_name");

        // 3. 顶部大标题：优先用传进来的中文名，没传就显示「这本书」兜底
        TextView title = findViewById(R.id.tv_title);
        TextView sub = findViewById(R.id.tv_sub);
        title.setText(categoryName == null ? "这本书" : categoryName);

        // 4. 读出这个分类下的全部剧本（从 assets/scripts/*.json 里按 category 字段过滤）
        List<Script> list = ScriptRepository.loadByCategory(this, categoryId);
        // 5. 副标题：空分类说「这一册还在写」，否则告诉用户有几本可选
        sub.setText(list.isEmpty() ? "这一册还在写" : "共 " + list.size() + " 个剧本，选一个开始");

        // 6. 卡片容器先清空（本页只在 onCreate 画一次，清空是为了防止布局里预置了占位内容）
        LinearLayout box = findViewById(R.id.ll_scripts);
        box.removeAllViews();

        // 7. 空分类：放一张提示卡，别让用户看到一片空白
        if (list.isEmpty()) {
            box.addView(hintCard("这一册还在写，先去别的书架逛逛吧。"));
        }
        // 8. 逐个造剧本卡片；下标 position 拿来做入场动画的依次延迟
        for (int i = 0; i < list.size(); i++) {
            box.addView(buildCard(list.get(i), i));
        }
        // 9. 有剧本才加底部统计卡（空分类加了反而显得啰嗦）
        if (!list.isEmpty()) {
            box.addView(buildFooter(list));
        }

        // 10. 顶部返回：关页面并播退出动画（和进入动画成对）
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                overridePendingTransition(R.anim.page_in, R.anim.page_out);
            }
        });

        // 书页翻开：整页绕 Y 轴从侧后方转正
        // 这里其实是「淡入 + 从下方 26dp 滑上来」，280ms 让纸页感更自然；
        // 先设成透明并向下偏移，再启动动画，形成「书页翻起来」的错觉。
        ScrollView sv = findViewById(R.id.sv);
        sv.setAlpha(0f);
        sv.setTranslationY(Ui.dp(this, 26));
        sv.animate().alpha(1f).translationY(0f).setDuration(280).start();
    }

    /** 底部说明：填满书页下方空白，也把"可溯源"这件事再强调一次 */
    private View buildFooter(List<Script> list) {
        // 统计这一册一共有多少处真实来源、多少个剧情节点（用具体数字增强可信度）
        // 注意：这里统计的是「本册」合计，不是选中某一本的数字，所以用户还没点进剧本
        // 就能先建立「这些剧情都有出处」的预期。
        int sources = 0;
        int nodes = 0;
        for (Script s : list) {
            // sources 是剧本里登记的真实案例来源条数（对应 assets 里的 sources 数组）
            sources += s.sources.size();
            // nodes 是剧本的剧情节点数，数字越大表示这一局越长
            nodes += s.nodes.size();
        }
        // 说明卡用浅色面板底，和上面的剧本卡片区分开
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.px_panel_soft);
        // 四边等距 14dp 内边距
        int p = Ui.dp(this, 14);
        card.setPadding(p, p, p, p);

        // 第一行：具体数字统计，字号稍大、颜色稍深
        TextView t1 = new PTextView(this);
        t1.setText("本册共 " + list.size() + " 个剧本 · " + nodes + " 个剧情节点 · " + sources + " 处真实来源");
        t1.setTextSize(12);
        t1.setTextColor(getColor(R.color.ink_secondary));
        // 行间距 3dp，多行时不会挤成一片
        t1.setLineSpacing(Ui.dp(this, 3), 1f);
        card.addView(t1);

        // 第二行：免责与改编声明。这段话是作品「可溯源、不编造」的对外承诺，不能删
        // 拆成两句是有意的：第一句讲「内容怎么来的」，第二句讲「玩家能额外得到什么」，
        // 两句都压在 11 号字、浅色，视觉上比上面的数字更退后，不抢剧本卡片的注意力。
        TextView t2 = new PTextView(this);
        t2.setText("所有剧情据公开报道改编，人物已匿名、平台名虚构。\n每个剧本结束后都会给出逐节点复盘和案例出处。");
        t2.setTextSize(11);
        t2.setTextColor(getColor(R.color.ink_hint));
        // 行间距 4dp，两行说明要读得开
        t2.setLineSpacing(Ui.dp(this, 4), 1f);
        t2.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(t2);
        return card;
    }

    /**
     * 造一张「空分类」提示卡。
     *
     * @param text 提示文字
     * @return 一张居中的浅色提示卡
     */
    private View hintCard(String text) {
        // 这里就是一个带背景的 TextView，比套一层布局更省事
        TextView tv = new PTextView(this);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(getColor(R.color.ink_secondary));
        tv.setBackgroundResource(R.drawable.px_panel);
        // 左右 16dp、上下 18dp，让提示文字四周有足够留白
        tv.setPadding(Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18));
        tv.setLineSpacing(Ui.dp(this, 4), 1f);
        return tv;
    }

    /**
     * 造一张剧本卡片。
     *
     * <p>卡片从上到下：骗局类型 + 预计时长 → 剧本标题 → 简介（最多 3 行）→
     * 节点数 / 来源数 → 「开始阅读 →」。整张卡片可点，点下去有 0.96 倍缩小反馈，
     * 缩完再跳聊天页，模拟「按下去」的手感。
     *
     * @param s        剧本数据
     * @param position 在列表里的位置（0 开始），用来做依次入场的延迟
     * @return 组装好的卡片 View
     */
    private View buildCard(final Script s, final int position) {
        // 卡片容器：竖向排列，浅色选项底（比说明卡更「亮」一点，显得可点）
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.px_option);
        // 四边等距 16dp 内边距
        int p = Ui.dp(this, 16);
        card.setPadding(p, p, p, p);
        // 设成可点击，这样点击时的按压态和点击事件都能生效
        card.setClickable(true);
        // 宽度占满、高度自适应；下面留 14dp，卡片之间不会粘在一起
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 14);
        card.setLayoutParams(lp);

        // 第一行：骗局类型 + 预计时长，用品牌色，像一本小书的「分类标签」
        TextView tag = new PTextView(this);
        tag.setText(s.scamType + "  ·  预计 " + s.minutes + " 分钟");
        tag.setTextSize(12);
        tag.setTextColor(getColor(R.color.brand));
        card.addView(tag);

        // 第二行：剧本标题，21 号字是整页最大的，作为卡片视觉重心
        TextView title = new PTextView(this);
        title.setText(s.title);
        title.setTextSize(21);
        title.setTextColor(getColor(R.color.ink));
        title.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(title);

        // 第三行：剧情简介，最多 3 行（再长会挤掉下面的数据行，也降低扫读效率）
        TextView intro = new PTextView(this);
        intro.setText(s.intro);
        intro.setTextSize(13);
        intro.setTextColor(getColor(R.color.ink_secondary));
        intro.setLineSpacing(Ui.dp(this, 4), 1f);
        intro.setPadding(0, Ui.dp(this, 8), 0, 0);
        intro.setMaxLines(3);
        card.addView(intro);

        // 第四行：节点数和真实来源数 —— 明确告诉用户「这不是编的，有多少处出处」
        TextView stat = new PTextView(this);
        stat.setText(s.nodes.size() + " 个节点  ·  " + s.sources.size() + " 处真实来源");
        stat.setTextSize(11);
        stat.setTextColor(getColor(R.color.ink_hint));
        stat.setPadding(0, Ui.dp(this, 10), 0, 0);
        card.addView(stat);

        // 第五行：「开始阅读 →」靠右对齐，起到「按钮」的引导作用
        TextView go = new PTextView(this);
        go.setText("开始阅读 →");
        go.setTextSize(14);
        go.setTextColor(getColor(R.color.brand));
        go.setGravity(Gravity.END);
        go.setPadding(0, Ui.dp(this, 10), 0, 0);
        card.addView(go);

        // 入场动画：先透明 + 向右偏 30dp，再淡入并滑回原位；
        // 每张卡延迟 70ms，形成从上到下依次「飘进来」的效果。
        card.setAlpha(0f);
        card.setTranslationX(Ui.dp(this, 30));
        card.animate().alpha(1f).translationX(0f).setDuration(240).setStartDelay(position * 70).start();

        // 点击：先缩小到 0.96 倍（80ms 的按压反馈），动画结束后再跳聊天页
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                // 把剧本 id 传给聊天页，聊天页据此加载剧本并逐条播放消息
                                Intent i = new Intent(ScriptPickerActivity.this, ChatActivity.class);
                                i.putExtra("script_id", s.id);
                                startActivity(i);
                                // 翻页动画：用同一套动画保持全 App 的页面切换观感一致
                                overridePendingTransition(R.anim.page_in, R.anim.page_out);
                            }
                        }).start();
            }
        });
        return card;
    }
}
