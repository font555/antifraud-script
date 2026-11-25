package com.example.antifraud.ui;

import android.app.Activity;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.antifraud.R;
import com.example.antifraud.engine.AppLog;
import com.example.antifraud.engine.ScriptRepository;
import com.example.antifraud.engine.UserStore;
import com.example.antifraud.model.Category;
import com.example.antifraud.model.Script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页 Tab（底部三 Tab 的「首页」）。
 *
 * <p>整页可滚动，从上到下四块：
 * <ol>
 *   <li>问候条：你好 + 已读几本 / 累计损失；</li>
 *   <li>真实案例轮播（{@link CarouselView}）；</li>
 *   <li>反诈书架：3 列 × 2 行共 6 本像素书，点书保留原来的翻书动画；</li>
 *   <li>反诈四步口诀。</li>
 * </ol>
 * 读书笔记 / 今日贴士 / 剧本工坊入口已经移到「我的」页，首页不再出现。</p>
 *
 * <p>翻书动画用 HomeTabView 自身当 root：覆盖层容器 {@code layer} 和本视图
 * 同尺寸同原点（本视图不设 padding），所以 getLocationInWindow 算出来的
 * 相对坐标可以直接当覆盖层的坐标用。</p>
 */
public class HomeTabView extends LinearLayout {

    /** 书架每行放几本书 */
    private static final int PER_ROW = 3;

    private final Activity activity;
    /** 由 MainActivity 实现，负责切 Tab / 打开分类（本视图不直接 new Activity） */
    private final TabHost host;

    /** 覆盖层容器：和 HomeTabView 同尺寸同原点，scrim / stage / pageFull 都挂这里 */
    private final FrameLayout layer;
    private final ScrollView scrollView;
    private final LinearLayout content;

    private final List<Category> categories = new ArrayList<>();
    private final List<Script> scripts = new ArrayList<>();
    /** 分类 id → 书下方那行「N 个剧本」，refresh() 时只改文字不重铺 */
    private final Map<String, TextView> countViews = new LinkedHashMap<>();
    /** 书架书的入场动画：先透明，挂到窗口后逐个浮现 */
    private final List<View> introCells = new ArrayList<>();

    private CarouselView carousel;
    private LinearLayout headerCard;
    private LinearLayout shelfBox;
    private TextView tvHello;
    private TextView tvReads;
    private TextView tvLoss;

    /** 翻书动画进行中：禁止再次点击书本 */
    private boolean opening;
    /** 入场动画是否播过（只播一次） */
    private boolean introPlayed;

    // 翻书动画的临时视图（动画结束后统一清理）
    private View overlayScrim;
    private FrameLayout overlayStage;
    private View overlayPage;
    private PixelBookView overlaySource;

    /** 翻书动画最后一步：切 Tab 之前留点时间给"大名字页" */
    private Runnable stepFinish;
    /** 兜底清理：万一 Tab 切走后本视图还挂着，也要把覆盖层收掉 */
    private final Runnable overlayCleanup = new Runnable() {
        @Override
        public void run() {
            resetOverlay();
        }
    };
    /** 书架入场：透明 + 下移 → 逐个浮现 */
    private final Runnable shelfIntro = new Runnable() {
        @Override
        public void run() {
            if (introPlayed) {
                return;
            }
            introPlayed = true;
            for (int i = 0; i < introCells.size(); i++) {
                View cell = introCells.get(i);
                if (cell == null) {
                    continue;
                }
                cell.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(280)
                        .setStartDelay(i * 55L)
                        .start();
            }
        }
    };

    public HomeTabView(Activity a, TabHost host) {
        super(a);
        this.activity = a;
        this.host = host;
        setOrientation(VERTICAL);
        setBackgroundColor(a.getColor(R.color.paper));

        // 覆盖层容器（和 this 一样大、一样原点），翻书动画的临时视图都放这里
        layer = new FrameLayout(a);
        layer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        addView(layer);

        // 可滚动的首页内容
        scrollView = new ScrollView(a);
        scrollView.setFillViewport(true);
        scrollView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        layer.addView(scrollView);

        content = new LinearLayout(a);
        content.setOrientation(VERTICAL);
        content.setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 18));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        loadData();
        buildCarousel();
        buildShelf();
        buildFooter();
        // 首页只保留「轮播 + 书架」两块；顶部战绩条与底部口诀卡已删除，
        // 之前的房间场景装饰也按反馈删掉了。
    }

    // ---------------- 对外刷新 ----------------

    /** 数据变化后（通关 / 清档 / 导入剧本）由 MainActivity 调：只刷文字，不重播动画 */
    public void refresh() {
        loadData();
        updateHeader();
        updateBookCounts();
    }

    // ---------------- 给新手引导提供可高亮的 View ----------------

    /** 顶部问候条（MainActivity 的引导第 1 步） */
    public View headerView() {
        return null;   // 顶部问候条已删除
    }

    /** 真实案例轮播（引导第 2 步） */
    public View carouselView() {
        return carousel;
    }

    /** 反诈书架（引导第 3 步） */
    public View shelfView() {
        return shelfBox;
    }

    private void loadData() {
        categories.clear();
        categories.addAll(ScriptRepository.loadCategories(activity));
        scripts.clear();
        scripts.addAll(ScriptRepository.loadAll(activity));
    }

    // ---------------- ① 顶部问候条 ----------------

    private void buildHeader() {
        LinearLayout card = Ui.card(activity, 12, 0);
        headerCard = card;
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) card.getLayoutParams();
        lp.topMargin = Ui.dp(activity, 8);
        lp.bottomMargin = Ui.dp(activity, 12);

        // 左侧：你好，昵称
        LinearLayout left = new LinearLayout(activity);
        left.setOrientation(LinearLayout.VERTICAL);
        tvHello = Ui.text(activity, "", 18, R.color.ink);
        tvHello.setMaxLines(1);
        tvHello.setEllipsize(TextUtils.TruncateAt.END);   // 昵称很长也不撑破卡片
        left.addView(tvHello);
        TextView tagline = Ui.text(activity, "反诈书库 · 本地存档", 10, R.color.ink_hint);
        tagline.setPadding(0, Ui.dp(activity, 5), 0, 0);
        left.addView(tagline);
        card.addView(left, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // 右侧：已读 N 本 / 损失 ¥X
        LinearLayout right = new LinearLayout(activity);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        tvReads = Ui.text(activity, "", 12, R.color.ink_secondary);
        tvReads.setGravity(Gravity.END);
        right.addView(tvReads);
        tvLoss = Ui.text(activity, "", 13, R.color.warn_red);
        tvLoss.setGravity(Gravity.END);
        tvLoss.setPadding(0, Ui.dp(activity, 5), 0, 0);
        right.addView(tvLoss);
        card.addView(right, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        content.addView(card);
        updateHeader();
    }

    private void updateHeader() {
        if (tvHello == null || tvReads == null || tvLoss == null) {
            return;   // 顶部条已移除
        }
        if (tvHello == null) {
            return;
        }
        tvHello.setText("你好，" + UserStore.nickname(activity));
        tvReads.setText("已读 " + UserStore.plays(activity) + " 本");
        tvLoss.setText("损失 ¥" + Ui.money(UserStore.totalLoss(activity)));
        AppLog.i("HOME", "首页刷新：已读 " + UserStore.plays(activity)
                + " 本，累计损失 " + UserStore.totalLoss(activity));
    }

    // ---------------- ② 真实案例轮播 ----------------

    private void buildCarousel() {
        carousel = new CarouselView(activity);
        carousel.setHost(host);
        // 高 356dp（16:9 配图按比例自适应 + 文字 + 口诀卡三行），宽度 match_parent；左右 16dp 由 content 的 padding 留出
        carousel.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(activity, 356)));
        content.addView(carousel);
    }

    // ---------------- ③ 反诈书架 ----------------

    /** 首页收尾：一行很轻的数据条，避免页面在书架后突然断掉 */
    private void buildFooter() {
        TextView footer = Ui.text(activity,
                "6 类骗局 · 17 本剧本 · 405 个剧情节点 · 71 条可核验来源",
                10, R.color.ink_hint);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, Ui.dp(activity, 16), 0, Ui.dp(activity, 6));
        content.addView(footer);
    }

    private void buildShelf() {
        TextView title = Ui.text(activity, "反诈书架", 22, R.color.ink);
        title.setPadding(0, Ui.dp(activity, 14), 0, 0);
        content.addView(title);

        TextView sub = Ui.text(activity, "点开一本书，走进一个真实的骗局", 11, R.color.ink_hint);
        sub.setPadding(0, Ui.dp(activity, 6), 0, Ui.dp(activity, 8));
        content.addView(sub);

        shelfBox = new LinearLayout(activity);
        shelfBox.setOrientation(VERTICAL);
        content.addView(shelfBox);

        int rows = (categories.size() + PER_ROW - 1) / PER_ROW;
        int index = 0;
        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            for (int c = 0; c < PER_ROW; c++) {
                if (index < categories.size()) {
                    row.addView(buildBook(categories.get(index)));
                    index++;
                } else {
                    View space = new View(activity);
                    space.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
                    row.addView(space);
                }
            }
            shelfBox.addView(row);
            shelfBox.addView(buildBoard());
        }
    }

    /** 木板书架隔板（和旧首页一样的样式） */
    private View buildBoard() {
        View board = new View(activity);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(activity, 9));
        lp.setMargins(Ui.dp(activity, 2), 0, Ui.dp(activity, 2), Ui.dp(activity, 14));
        board.setLayoutParams(lp);
        board.setBackgroundResource(R.drawable.px_shelf);
        return board;
    }

    /** 一格：像素书 + 分类名 + 「N 个剧本」 */
    private View buildBook(final Category cat) {
        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(Ui.dp(activity, 5), 0, Ui.dp(activity, 5), Ui.dp(activity, 2));
        cell.setLayoutParams(lp);

        final PixelBookView cover = new PixelBookView(activity);
        cover.setBook(cat.colorInt, cat.name, cat.glyph);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = Ui.dp(activity, 2);
        cover.setLayoutParams(clp);
        cover.setCameraDistance(getResources().getDisplayMetrics().density * 14000);
        cell.addView(cover);

        TextView name = Ui.text(activity, cat.name, 13, R.color.ink);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, Ui.dp(activity, 8), 0, 0);
        cell.addView(name);

        TextView desc = Ui.text(activity, countOf(cat.id) + " 个剧本", 11, R.color.ink_hint);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, Ui.dp(activity, 3), 0, 0);
        cell.addView(desc);
        countViews.put(cat.id, desc);

        // 先藏起来，等挂到窗口后由 shelfIntro 逐个浮现（避免整屏闪）
        cell.setAlpha(0f);
        cell.setTranslationY(Ui.dp(activity, 20));
        introCells.add(cell);

        cell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openBook(cover, cat);
            }
        });
        // 按下时书本轻微缩小，松手回弹，给点击一点“手感”
        cell.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                int action = event.getActionMasked();
                if (action == android.view.MotionEvent.ACTION_DOWN) {
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start();
                } else if (action == android.view.MotionEvent.ACTION_UP
                        || action == android.view.MotionEvent.ACTION_CANCEL) {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                }
                return false;
            }
        });
        return cell;
    }

    private int countOf(String categoryId) {
        int n = 0;
        for (Script s : scripts) {
            if (categoryId.equals(s.category)) {
                n++;
            }
        }
        return n;
    }

    private void updateBookCounts() {
        for (Category c : categories) {
            TextView tv = countViews.get(c.id);
            if (tv != null) {
                tv.setText(countOf(c.id) + " 个剧本");
            }
        }
    }

    // ---------------- 翻书动画（从 MainActivity.openBook 迁移） ----------------

    /**
     * 翻书动效（真·翻开），整段从旧书架的 MainActivity.openBook() 迁过来：
     * 1) 全屏压暗，书从原位缓慢移到屏幕中央；
     * 2) 移动过程中放大，封面沿书脊（绕 Y 轴）掀开，露出内页；
     * 3) 内页淡出，整屏分类名页淡入铺满（字 / 分类名 / 一句话简介）；
     * 4) 不再跳旧的剧本页，改为 host.openCategory(cat.id) 切到「剧本」Tab。
     *
     * <p>root 用 HomeTabView 自身（this）；覆盖层 layer 与 this 同尺寸同原点，
     * 所以 getLocationInWindow 求出的相对坐标和 layer 的坐标系一致。</p>
     */
    private void openBook(final PixelBookView cover, final Category cat) {
        if (opening) {
            return;
        }
        opening = true;

        // 用 this 当 root：先算出封面相对 HomeTabView 的位置
        int[] coverLoc = new int[2];
        cover.getLocationInWindow(coverLoc);
        int[] rootLoc = new int[2];
        getLocationInWindow(rootLoc);
        final int startX = coverLoc[0] - rootLoc[0];
        final int startY = coverLoc[1] - rootLoc[1];
        final int w = cover.getWidth();
        final int h = cover.getHeight();
        final int rootW = getWidth() > 0 ? getWidth()
                : getResources().getDisplayMetrics().widthPixels;
        final int rootH = getHeight() > 0 ? getHeight()
                : getResources().getDisplayMetrics().heightPixels;

        // 封面还没量出宽高时（极端情况）不播动画，直接切 Tab，避免除零和残影
        if (w <= 0 || h <= 0) {
            opening = false;
            if (host != null) {
                host.openCategory(cat.id);
            }
            return;
        }

        // ---- 压暗遮罩：clickable 吃掉动画期间的触摸，防止误点/滚动 ----
        final View scrim = new View(activity);
        scrim.setBackgroundColor(0xB3000000);
        scrim.setAlpha(0f);
        scrim.setClickable(true);
        scrim.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        layer.addView(scrim);
        overlayScrim = scrim;
        scrim.animate().alpha(1f).setDuration(220).start();

        // ---- 舞台：内页 + 封面（用边距定位，避免闪到左上角） ----
        final FrameLayout stage = new FrameLayout(activity);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(w, h);
        slp.leftMargin = startX;
        slp.topMargin = startY;
        stage.setLayoutParams(slp);
        float cam = getResources().getDisplayMetrics().density * 18000;
        stage.setCameraDistance(cam);
        stage.setPivotX(0f);
        stage.setPivotY(0f);
        overlayStage = stage;

        // 舞台里的内页（"第 1 页" + 分类名 + 分隔线）
        final LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundResource(R.drawable.px_paper);
        int pp = Ui.dp(activity, 12);
        page.setPadding(pp, pp, pp, pp);
        page.addView(Ui.text(activity, "第 1 页", 7, R.color.ink_hint));
        TextView inPageName = Ui.text(activity, cat.name, 9, R.color.ink);
        inPageName.setPadding(0, Ui.dp(activity, 6), 0, 0);
        page.addView(inPageName);
        View inPageLine = new View(activity);
        inPageLine.setBackgroundColor(activity.getColor(R.color.paper_dark));
        LinearLayout.LayoutParams inLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(activity, 1)));
        inLp.topMargin = Ui.dp(activity, 8);
        inPageLine.setLayoutParams(inLp);
        page.addView(inPageLine);
        stage.addView(page, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 舞台上的封面：绕书脊（左边缘）掀开
        final PixelBookView cov = new PixelBookView(activity);
        cov.setBook(cat.colorInt, cat.name, cat.glyph);
        cov.setPivotX(0f);
        cov.setPivotY(h / 2f);
        cov.setCameraDistance(cam);
        stage.addView(cov, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        layer.addView(stage);
        cover.setVisibility(View.INVISIBLE);
        overlaySource = cover;

        // ---- 翻开后铺满整屏的"大名字"页 ----
        final LinearLayout pageFull = new LinearLayout(activity);
        pageFull.setOrientation(LinearLayout.VERTICAL);
        pageFull.setGravity(Gravity.CENTER_HORIZONTAL);
        pageFull.setBackgroundColor(activity.getColor(R.color.paper));
        pageFull.setAlpha(0f);
        pageFull.setClickable(true);
        pageFull.setPadding(Ui.dp(activity, 32), Ui.dp(activity, 70),
                Ui.dp(activity, 32), Ui.dp(activity, 40));
        pageFull.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView bigGlyph = Ui.text(activity, cat.glyph, 96, R.color.brand_deep);
        bigGlyph.setGravity(Gravity.CENTER);
        bigGlyph.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        pageFull.addView(bigGlyph);
        TextView bigName = Ui.text(activity, cat.name, 34, R.color.ink);
        bigName.setPadding(0, Ui.dp(activity, 18), 0, 0);
        bigName.setGravity(Gravity.CENTER);
        bigName.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        pageFull.addView(bigName);
        TextView bigDesc = Ui.text(activity, cat.desc, 16, R.color.ink_secondary);
        bigDesc.setPadding(0, Ui.dp(activity, 14), 0, 0);
        bigDesc.setGravity(Gravity.CENTER);
        bigDesc.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        pageFull.addView(bigDesc);
        layer.addView(pageFull);
        overlayPage = pageFull;

        final float scale = Math.min(rootW * 0.94f / w, rootH * 0.58f / h);
        final float targetX = (rootW - w * scale) / 2f;
        final float targetY = (rootH - h * scale) / 2f;

        // 等一帧，确保舞台的初始边距已经生效
        post(new Runnable() {
            @Override
            public void run() {
                // 第一步：缓慢移到 HomeTabView 中央，同时放大
                stage.animate()
                        .translationX(targetX - startX)
                        .translationY(targetY - startY)
                        .scaleX(scale).scaleY(scale)
                        .setDuration(420)
                        .setInterpolator(new DecelerateInterpolator())
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                // 第二步：封面沿书脊掀开
                                cov.animate()
                                        .rotationY(-170f)
                                        .setDuration(560)
                                        .setInterpolator(new AccelerateDecelerateInterpolator())
                                        .withEndAction(new Runnable() {
                                            @Override
                                            public void run() {
                                                // 第三步：内页淡出，整屏分类名页淡入
                                                stage.animate().alpha(0f)
                                                        .setDuration(240).start();
                                                pageFull.setAlpha(0f);
                                                pageFull.setTranslationY(Ui.dp(activity, 16));
                                                pageFull.animate().alpha(1f).translationY(0f)
                                                        .setDuration(320).start();
                                                stepFinish = new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        stepFinish = null;
                                                        // 动画途中被切走（比如点了底部 Tab）就放弃这次跳转，
                                                        // 避免用户回到首页时被"补跳"到剧本页
                                                        if (!opening) {
                                                            return;
                                                        }
                                                        // 第四步：不跳旧的书页页，交给 TabHost 切到「剧本」
                                                        if (host != null && isAttachedToWindow()) {
                                                            host.openCategory(cat.id);
                                                        }
                                                        postDelayed(overlayCleanup, 300);
                                                    }
                                                };
                                                postDelayed(stepFinish, 620);
                                            }
                                        })
                                        .start();
                            }
                        })
                        .start();
            }
        });
    }

    /** 清掉翻书动画的临时视图，恢复原封面 */
    private void resetOverlay() {
        if (layer != null) {
            if (overlayPage != null) {
                layer.removeView(overlayPage);
            }
            if (overlayStage != null) {
                layer.removeView(overlayStage);
            }
            if (overlayScrim != null) {
                layer.removeView(overlayScrim);
            }
        }
        overlayPage = null;
        overlayStage = null;
        overlayScrim = null;
        if (overlaySource != null) {
            overlaySource.setVisibility(View.VISIBLE);
            overlaySource = null;
        }
        opening = false;
    }

    // ---------------- ④ 反诈四步口诀 ----------------

    private void buildRules() {
        LinearLayout card = Ui.card(activity, 14, 0);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) card.getLayoutParams();
        lp.topMargin = Ui.dp(activity, 6);
        lp.bottomMargin = Ui.dp(activity, 4);

        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        head.addView(Ui.text(activity, "反诈口诀 · 8 个字", 13, R.color.brand));
        View spacer = new View(activity);
        head.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        head.addView(Ui.text(activity, "点开看解释 →", 9, R.color.brand_deep));
        card.addView(head);

        java.util.List<Tips.Tip> tips = Tips.load(activity);
        // 两行 × 四句：码链钱平 / 话急警证
        for (int row = 0; row < 2; row++) {
            StringBuilder sb = new StringBuilder();
            for (int i = row * 4; i < Math.min(row * 4 + 4, tips.size()); i++) {
                if (sb.length() > 0) {
                    sb.append(" · ");
                }
                sb.append("「").append(tips.get(i).key).append("」").append(tips.get(i).sub);
            }
            TextView line = Ui.text(activity, sb.toString(), 11, R.color.ink_secondary);
            line.setPadding(0, Ui.dp(activity, 8), 0, 0);
            line.setLineSpacing(Ui.dp(activity, 3), 1f);
            card.addView(line);
        }

        TextView hint = Ui.text(activity, "验证码不给 · 陌生链接不点 · 先垫钱不转 · 交易不离平台", 9,
                R.color.ink_hint);
        hint.setPadding(0, Ui.dp(activity, 8), 0, 0);
        hint.setLineSpacing(Ui.dp(activity, 2), 1f);
        card.addView(hint);

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Tips.showAll(activity);
            }
        });
        content.addView(card);
    }

    // ---------------- 生命周期：控制轮播与清理 ----------------

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (carousel != null) {
            carousel.start();
        }
        if (!introPlayed && !introCells.isEmpty()) {
            post(shelfIntro);
        }
    }

    @Override
    public void onDetachedFromWindow() {
        if (carousel != null) {
            carousel.stop();
        }
        removeCallbacks(shelfIntro);
        removeCallbacks(overlayCleanup);
        if (stepFinish != null) {
            removeCallbacks(stepFinish);
            stepFinish = null;
        }
        resetOverlay();
        super.onDetachedFromWindow();
    }
}
