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
import com.example.antifraud.engine.SettingsStore;
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
 *
 * <p><b>和其他页面的关系：</b>本视图由 {@link MainActivity} 第一次切到「首页」时创建，之后
 * 缓存复用（切 Tab 只改可见性不重建，滚动位置才不会丢）；要切 Tab / 打开分类时它不直接
 * new Activity，而是调构造传入的 {@link TabHost}（MainActivity 实现），页面间只靠这一个接口通信。</p>
 *
 * <p><b>为什么用代码搭而不是 XML：</b>零第三方依赖；书架放几行、每格多宽都要按分类数量现算，
 * 写死 XML 反而更绕；而且所有尺寸能统一走 {@link #box} 跟着字号放大。</p>
 *
 * <p><b>数据从哪来：</b>分类与剧本由 {@link ScriptRepository} 从 assets 的 JSON 读（纯本地、
 * 不联网、不要权限），已读本数与累计损失由 {@link UserStore} 读 SharedPreferences。</p>
 */
public class HomeTabView extends LinearLayout {

    /** 书架每行放几本书（= 屏幕宽度分几份）。三列是产品定好的：再宽一列字太小、再窄一列书太小 */
    private static final int PER_ROW = 3;

    /** 轮播卡片的基准高度（dp）：标准字号下就是这样 */
    private static final int CAROUSEL_HEIGHT_DP = 262;
    /**
     * 轮播放大上限。轮播占的是首屏最贵的一块位置，
     * 跟字号一起长到 1.9 倍就会把书架整体挤出首屏，所以封顶 1.5 倍（约 393dp）。
     */
    private static final float CAROUSEL_MAX_SCALE = 1.5f;

    /** 宿主 Activity，用来取颜色 / dp / 调 SettingsStore 等（View 自己的 getContext 也能取，这样写更直白） */
    private final Activity activity;
    /** 由 MainActivity 实现，负责切 Tab / 打开分类（本视图不直接 new Activity） */
    private final TabHost host;

    /** 覆盖层容器：和 HomeTabView 同尺寸同原点，scrim / stage / pageFull 都挂这里 */
    private final FrameLayout layer;
    /** 整页滚动容器：内容超过一屏时可以上下滑（首页内容比屏幕高） */
    private final ScrollView scrollView;
    /** 滚动容器里真正装内容的竖排 LinearLayout，所有卡片都按顺序 addView 到这里 */
    private final LinearLayout content;

    /** 全部骗局分类（6 类），决定书架上有几本书、每本书叫什么、什么颜色 */
    private final List<Category> categories = new ArrayList<>();
    /** 全部剧本（含用户自己写的），只用来统计「某个分类下有几个剧本」，不直接铺在首页 */
    private final List<Script> scripts = new ArrayList<>();
    /** 分类 id → 书下方那行「N 个剧本」，refresh() 时只改文字不重铺 */
    private final Map<String, TextView> countViews = new LinkedHashMap<>();
    /** 书架书的入场动画：先透明，挂到窗口后逐个浮现 */
    private final List<View> introCells = new ArrayList<>();

    /** 首页顶部那张「真实案例」轮播（第 2 块内容），生命周期里负责 start/stop 自动播放 */
    private CarouselView carousel;
    /** 顶部问候条卡片。注意：当前版本首页已不再创建它，字段只是为了兼容旧引导代码而保留 */
    private LinearLayout headerCard;
    /** 书架的外层竖排容器（每行书 + 一块木隔板都往里加），新手引导要拿它做高亮目标 */
    private LinearLayout shelfBox;
    /** 问候语「你好，昵称」；当前版本可能是 null（问候条已移除，方法内都有判空） */
    private TextView tvHello;
    /** 「已读 N 本」；同上，当前版本可能为 null */
    private TextView tvReads;
    /** 「损失 ¥X」；同上，当前版本可能为 null */
    private TextView tvLoss;

    /** 翻书动画进行中：禁止再次点击书本（也用来判断动画途中是否被切走） */
    private boolean opening;
    /** 入场动画是否播过（只播一次） */
    private boolean introPlayed;

    // 翻书动画的临时视图（动画结束后统一清理）
    /** 压暗遮罩：0xB3000000（约 70% 黑），同时吃掉动画期间的触摸 */
    private View overlayScrim;
    /** 舞台：把「内页 + 封面」装在同一个 FrameLayout 里，方便整体平移/缩放 */
    private FrameLayout overlayStage;
    /** 翻开后铺满整屏的「大名字」页（大图标 + 分类名 + 一句话简介） */
    private View overlayPage;
    /** 被动画挡住的原始封面，动画结束后要恢复显示，否则书上会空一块 */
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
            // 只播一次：切 Tab 来回、页面刷新都不该重播，否则每次都像闪屏
            if (introPlayed) {
                return;
            }
            introPlayed = true;
            // 逐格启动：第 i 格延后 i*55ms，形成"从左到右、从上到下"依次浮现的节奏
            for (int i = 0; i < introCells.size(); i++) {
                View cell = introCells.get(i);
                if (cell == null) {
                    continue;
                }
                cell.animate()
                        .alpha(1f)                // 从透明变完全不透明
                        .translationY(0f)         // 从下方 20dp 回到原位
                        .setDuration(280)         // 单格动画 280ms，短一点不拖沓
                        .setStartDelay(i * 55L)   // 逐格错开 55ms
                        .start();
            }
        }
    };

    /**
     * 构造函数：把首页四块内容一次性搭好。
     *
     * @param a    宿主 Activity（MainActivity），取颜色 / dp / 启动动画都用它
     * @param host 页面通信接口，翻书动画结束时通过它切到「剧本」Tab
     */
    public HomeTabView(Activity a, TabHost host) {
        super(a);
        this.activity = a;
        this.host = host;
        setOrientation(VERTICAL);
        setBackgroundColor(a.getColor(R.color.paper));   // 统一用「纸」底色，和像素风一致

        // 覆盖层容器（和 this 一样大、一样原点），翻书动画的临时视图都放这里
        // 注意：它是后 addView 的兄弟，会盖在 ScrollView 上面，所以能全屏压暗
        layer = new FrameLayout(a);
        layer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        addView(layer);

        // 可滚动的首页内容
        scrollView = new ScrollView(a);
        scrollView.setFillViewport(true);   // 内容不满一屏时也撑满，避免底部露白
        scrollView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        layer.addView(scrollView);

        content = new LinearLayout(a);
        content.setOrientation(VERTICAL);
        // 左右 16dp 保持不动：横向留白放大会直接吃掉书架每格的宽度（屏宽分 3 份）。
        // 上下的 8 / 18dp 跟着字号长一点，页面上下不显得局促。
        content.setPadding(Ui.dp(a, 16), box(8, 1.6f), Ui.dp(a, 16), box(18, 1.6f));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        // 顺序即视觉顺序：先读数据，再依次搭 轮播 → 书架 → 页脚
        loadData();
        buildCarousel();
        buildShelf();
        buildFooter();
        // 首页只保留「轮播 + 书架」两块；顶部战绩条与底部口诀卡已删除，
        // 之前的房间场景装饰也按反馈删掉了。
    }

    // ---------------- 尺寸随字号放大 ----------------

    /**
     * 「装文字」的固定尺寸统一走这里：先按字号放大（SettingsStore.scaleBox 内部上限 1.9 倍），
     * 再压到给定上限。文字本身走 sp，会被 BaseActivity 的 fontScale 自动放大，不用在这里乘。
     * 为什么容器也要放大：用户调到超大字号时，固定 44dp 的容器装不下一行字，会被裁掉。
     *
     * @param sizeDp   标准字号下的尺寸（dp）
     * @param maxScale 这一个控件允许的最大倍数（例如轮播 1.5、书架 1.9）
     * @return 换算成 px、并已按上限截断的最终尺寸
     */
    private int box(float sizeDp, float maxScale) {
        int base = Ui.dp(activity, sizeDp);            // 标准字号下的像素值
        int scaled = SettingsStore.scaleBox(activity, base);   // 跟着系统字号放大（内部封顶 1.9）
        int cap = Math.round(base * maxScale);          // 本控件自己的上限
        return Math.min(scaled, cap);                   // 取二者较小值
    }

    /**
     * 轮播高度：262dp 起步，封顶 1.5 倍（约 393dp），保证一屏还能看到书架。
     *
     * @return 轮播容器的像素高度
     */
    private int carouselHeightPx() {
        return box(CAROUSEL_HEIGHT_DP, CAROUSEL_MAX_SCALE);
    }

    // ---------------- 对外刷新 ----------------

    /** 数据变化后（通关 / 清档 / 导入剧本）由 MainActivity 调：只刷文字，不重播动画 */
    public void refresh() {
        loadData();          // 重新读一遍 assets 里的分类 + 剧本（可能刚导入过新剧本）
        updateHeader();      // 问候条上的「已读 / 损失」（当前版本可能没有顶部条，内部会判空返回）
        updateBookCounts();  // 每本书下面那行「N 个剧本」
    }

    // ---------------- 给新手引导提供可高亮的 View ----------------

    /** 顶部问候条（MainActivity 的引导第 1 步） */
    public View headerView() {
        return null;   // 顶部问候条已删除
    }

    /** 真实案例轮播（引导第 2 步）：返回 null 时引导会自动跳过这一步 */
    public View carouselView() {
        return carousel;
    }

    /** 反诈书架（引导第 3 步） */
    public View shelfView() {
        return shelfBox;
    }

    /**
     * 从 assets 里重新读分类与全部剧本。
     * 读失败时 ScriptRepository 内部已兜底（返回空列表 / 内置默认值），这里不再处理异常。
     */
    private void loadData() {
        categories.clear();
        categories.addAll(ScriptRepository.loadCategories(activity));   // 6 个骗局分类
        scripts.clear();
        scripts.addAll(ScriptRepository.loadAll(activity));             // 内置剧本 + 用户自己写的
    }

    // ---------------- ① 顶部问候条 ----------------

    /**
     * 搭顶部问候条卡片：左侧「你好，昵称」+ 副标题，右侧「已读 N 本」+「损失 ¥X」。
     *
     * <p>注意这是**旧版首页**的布局，当前版本构造函数里没有再调用它，
     * 保留代码是为了以后想恢复问候条时可以直接接上。</p>
     */
    private void buildHeader() {
        LinearLayout card = Ui.card(activity, 12, 0);   // 统一卡片样式：12dp 圆角、无边框
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
        // weight=1：左侧吃掉所有剩余宽度，把右侧数字顶到最右边
        card.addView(left, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // 右侧：已读 N 本 / 损失 ¥X
        LinearLayout right = new LinearLayout(activity);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.END);
        tvReads = Ui.text(activity, "", 12, R.color.ink_secondary);
        tvReads.setGravity(Gravity.END);
        right.addView(tvReads);
        tvLoss = Ui.text(activity, "", 13, R.color.warn_red);   // 损失用警示红，视觉上更扎眼
        tvLoss.setGravity(Gravity.END);
        tvLoss.setPadding(0, Ui.dp(activity, 5), 0, 0);
        right.addView(tvLoss);
        card.addView(right, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        content.addView(card);
        updateHeader();
    }

    /**
     * 把 UserStore 里的昵称 / 已读本数 / 累计损失写进问候条。
     * 三个 TextView 都没被创建（新版首页删掉了问候条）时直接返回。
     */
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

    /**
     * 搭第 2 块内容：真实案例轮播。
     * 轮播自己会监听手势滑动手动切换，也会定时自动播（生命周期里 start/stop 控制）。
     */
    private void buildCarousel() {
        carousel = new CarouselView(activity);
        carousel.setHost(host);   // 轮播里点「看案例」要能跳分类，所以要把 TabHost 传下去
        // 基准 262dp（2.5:1 配图 + 文字，整页更紧凑），宽度 match_parent；
        // 左右 16dp 由 content 的 padding 留出。
        // 字号变大时高度跟着涨，但封顶 1.5 倍：再高首屏就看不到书架了。
        carousel.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, carouselHeightPx()));
        content.addView(carousel);
    }

    // ---------------- ③ 反诈书架 ----------------

    /** 首页收尾：一行很轻的数据条，避免页面在书架后突然断掉 */
    private void buildFooter() {
        TextView footer = Ui.text(activity,
                "6 类骗局 · 18 本剧本 · 424 个剧情节点 · 73 条可核验来源",
                10, R.color.ink_hint);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, SettingsStore.scaleBox(activity, Ui.dp(activity, 16)), 0,
                SettingsStore.scaleBox(activity, Ui.dp(activity, 6)));
        content.addView(footer);
    }

    /**
     * 搭第 3 块内容：反诈书架（3 列 × N 行像素书）。
     *
     * <p><b>三列网格是怎么算的：</b>这里刻意**不用** GridLayout，只用最普通的
     * 「竖排 LinearLayout 套横排 LinearLayout」：</p>
     * <ol>
     *   <li>行数 = {@code (分类数 + 3 - 1) / 3}，即向上取整。例如 6 个分类 → 2 行，
     *       7 个分类 → 3 行（最后一行只放 1 本，剩下 2 格用空 View 补齐）。</li>
     *   <li>每一行是横排 LinearLayout，里面固定放 3 个子 View，每个子 View 的宽度
     *       都是 {@code weight=1、width=0dp}——三个 1 等分，正好把屏幕宽度（扣掉左右
     *       各 16dp 的页边距）平分成三份，所以**不需要手算像素宽度**，
     *       换任何屏幕尺寸、任何字号都自动等分，也不会出现"最后一列差几个像素"。</li>
     *   <li>分类不够 3 个时补一个 {@code width=0、height=1、weight=1} 的空 View，
     *       它不显示任何东西，只是占住这一份宽度，保证同一行的书左右对齐。</li>
     *   <li>每行下面再压一块木隔板 {@link #buildBoard()}，视觉上像真的书架。</li>
     * </ol>
     *
     * <p><b>字号放大的边界：</b>因为列宽是"屏宽 1/3"这个硬约束，横向的内边距
     * **不能**跟着字号放大（一放大列宽就变窄），只放大纵向留白和书与书之间的
     * 一点呼吸空间，见 {@link #buildBook}。</p>
     */
    private void buildShelf() {
        TextView title = Ui.text(activity, "反诈书架", 22, R.color.ink);
        title.setPadding(0, SettingsStore.scaleBox(activity, Ui.dp(activity, 14)), 0, 0);
        content.addView(title);

        TextView sub = Ui.text(activity, "点开一本书，走进一个真实的骗局", 11, R.color.ink_hint);
        // 小字在「超大」档会折成两行，上下留白跟着字体一起放大，避免贴住标题
        sub.setPadding(0, SettingsStore.scaleBox(activity, Ui.dp(activity, 6)), 0,
                SettingsStore.scaleBox(activity, Ui.dp(activity, 8)));
        content.addView(sub);

        shelfBox = new LinearLayout(activity);
        shelfBox.setOrientation(VERTICAL);
        content.addView(shelfBox);

        // 向上取整算行数：(n + 3 - 1) / 3。整数除法天然向下取整，加上 2 就变成向上取整
        int rows = (categories.size() + PER_ROW - 1) / PER_ROW;
        int index = 0;   // 当前该取第几个分类（跨行连续递增）
        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            for (int c = 0; c < PER_ROW; c++) {
                if (index < categories.size()) {
                    row.addView(buildBook(categories.get(index)));   // 有分类：画一本书
                    index++;
                } else {
                    // 这一行不够 3 本：补一个看不见的等宽占位，保证前面的书不被拉宽
                    View space = new View(activity);
                    space.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
                    row.addView(space);
                }
            }
            shelfBox.addView(row);
            shelfBox.addView(buildBoard());   // 每行书下面压一块木隔板
        }
    }

    /**
     * 木板书架隔板（和旧首页一样的样式）。
     *
     * @return 一条用作隔板的装饰 View
     */
    private View buildBoard() {
        View board = new View(activity);
        // 隔板本身是装饰线，高度只轻微放大（1.4 倍封顶），太厚会显得笨重
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, box(9, 1.4f));
        // 隔板比书稍微宽一点（左右各外扩 2dp），看起来才像"托着"书；下方留 14dp 给下一行
        lp.setMargins(box(2, 1.4f), 0, box(2, 1.4f), box(14, 1.6f));
        board.setLayoutParams(lp);
        board.setBackgroundResource(R.drawable.px_shelf);
        return board;
    }

    /**
     * 画书架里的一格：像素书封面 + 分类名 + 「N 个剧本」。
     *
     * <p>这一格同时绑了三个交互：
     * 点击 → {@link #openBook} 播翻书动画；
     * 按下 → 缩到 0.96 倍（有点"按下去"的手感）；松手 → 弹回 1 倍。</p>
     *
     * @param cat 这个格子代表的骗局分类
     * @return 组装好的一格（外层竖排 LinearLayout）
     */
    private View buildBook(final Category cat) {
        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        // width=0 + weight=1：这一格占屏幕宽度的三分之一（三列等分的核心写法）
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        // 列宽由屏幕分 3 份，横向不能放大，否则三列会撑出屏幕；
        // 只把「书与书之间」的呼吸空间抬一点，字号变大后不显得挤。
        lp.setMargins(box(5, 1.15f), 0, box(5, 1.15f), box(2, 1.6f));
        cell.setLayoutParams(lp);

        // 像素书封面：颜色 / 书名 / 封面上的大字都由 Category 提供
        final PixelBookView cover = new PixelBookView(activity);
        cover.setBook(cat.colorInt, cat.name, cat.glyph);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = box(2, 1.6f);
        cover.setLayoutParams(clp);
        // 相机距离拉大（14000 × 密度）：3D 旋转时透视变形不要太夸张，书看起来才正
        cover.setCameraDistance(getResources().getDisplayMetrics().density * 14000);
        cell.addView(cover);

        TextView name = Ui.text(activity, cat.name, 13, R.color.ink);
        name.setGravity(Gravity.CENTER);
        // 书架一格只有屏宽 1/3，字放大后分类名会折成两三行：固定最多 2 行 + 省略号，
        // 保证每格高度一致，不会把下面那排骨书顶得乱七八糟。
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(0, SettingsStore.scaleBox(activity, Ui.dp(activity, 8)), 0, 0);
        cell.addView(name);

        // 「N 个剧本」这一行会被 refresh() 单独更新文字，所以登记进 countViews
        TextView desc = Ui.text(activity, countOf(cat.id) + " 个剧本", 11, R.color.ink_hint);
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, SettingsStore.scaleBox(activity, Ui.dp(activity, 3)), 0, 0);
        cell.addView(desc);
        countViews.put(cat.id, desc);

        // 先藏起来，等挂到窗口后由 shelfIntro 逐个浮现（避免整屏闪）
        cell.setAlpha(0f);
        cell.setTranslationY(Ui.dp(activity, 20));   // 起始位置在下方 20dp，动画往上"浮"回来
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
                return false;   // 返回 false 才不拦截，点击事件才能继续传给 onClick
            }
        });
        return cell;
    }

    /**
     * 数一数某个分类下一共有几个剧本（内置的 + 用户自己写的都算）。
     *
     * @param categoryId 分类 id，例如 "game"
     * @return 该分类的剧本数量；找不到就是 0
     */
    private int countOf(String categoryId) {
        int n = 0;
        for (Script s : scripts) {
            if (categoryId.equals(s.category)) {
                n++;
            }
        }
        return n;
    }

    /** 只更新每本书下面那行「N 个剧本」的文字，不重建整个书架（重建会丢滚动位置） */
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
     *
     * <p><b>整段动画的完整时间轴（单位毫秒，从点击那一刻开始算）：</b></p>
     * <pre>
     *   t=0     ┌ 遮罩开始变黑（0 → 70% 黑），盖住整页，顺便吃掉期间的触摸
     *           ├ 舞台（内页 + 封面）先被摆到"被点的那本书"原来的位置
     *           └ post 一帧，等边距生效
     *   t≈0     阶段①：420ms，舞台从书本原位平移到屏幕中心，同时放大到目标比例
     *   t≈420   阶段②：560ms，封面绕左边（书脊）做 rotateY 0° → −170°
     *           转到 −90° 时是"立起来"的瞬间，超过 −90° 就露出背后的内页
     *   t≈980   阶段③：内页整体淡出 240ms；同时整屏"大名字页"从下方 16dp 淡入 320ms
     *   t≈1600  阶段④：postDelayed 620ms 后再等一会儿（让大名字页停留可读），
     *           然后调 host.openCategory() 切到剧本 Tab；
     *           再延 300ms 调 overlayCleanup 把临时视图全拆掉
     * </pre>
     *
     * <p><b>为什么坐标要自己算：</b>被点的书在 ScrollView 里，位置随滚动变化，
     * 所以不能写死。{@code getLocationInWindow} 拿到的是「屏幕坐标」，
     * 两两相减得到相对 HomeTabView 的坐标，而 layer 又和 HomeTabView 同原点，
     * 于是这个相对坐标可以直接当子 View 的 leftMargin / topMargin 用。</p>
     *
     * @param cover 被点击的那本像素书（动画期间会先隐藏，结束后恢复）
     * @param cat   这本书对应的分类，决定封面颜色、分类名和最后的跳转目标
     */
    private void openBook(final PixelBookView cover, final Category cat) {
        // 动画播到一半又点一本：直接忽略，避免两套动画叠在一起互相打架
        if (opening) {
            return;
        }
        opening = true;

        // 用 this 当 root：先算出封面相对 HomeTabView 的位置
        int[] coverLoc = new int[2];
        cover.getLocationInWindow(coverLoc);   // 书的绝对屏幕坐标
        int[] rootLoc = new int[2];
        getLocationInWindow(rootLoc);          // 首页自身的绝对屏幕坐标
        final int startX = coverLoc[0] - rootLoc[0];   // 相减 → 书在 layer 里的 x
        final int startY = coverLoc[1] - rootLoc[1];   // 相减 → 书在 layer 里的 y
        final int w = cover.getWidth();                // 书的实际宽（用于动画起点尺寸）
        final int h = cover.getHeight();               // 书的实际高
        // 极少数情况下 getWidth() 还是 0（视图还没测量），退回屏幕尺寸兜底，避免后面除零
        final int rootW = getWidth() > 0 ? getWidth()
                : getResources().getDisplayMetrics().widthPixels;
        final int rootH = getHeight() > 0 ? getHeight()
                : getResources().getDisplayMetrics().heightPixels;

        // 封面还没量出宽高时（极端情况）不播动画，直接切 Tab，避免除零和残影
        if (w <= 0 || h <= 0) {
            opening = false;   // 复位，否则以后所有书都点不动了
            if (host != null) {
                host.openCategory(cat.id);
            }
            return;
        }

        // ---- 压暗遮罩：clickable 吃掉动画期间的触摸，防止误点/滚动 ----
        // 0xB3000000 = alpha 0xB3(约 70%) + 纯黑，压暗但还能隐约看到底下的页面
        final View scrim = new View(activity);
        scrim.setBackgroundColor(0xB3000000);
        scrim.setAlpha(0f);      // 先完全透明，再淡入，避免"啪"一下变黑
        scrim.setClickable(true);
        scrim.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        layer.addView(scrim);
        overlayScrim = scrim;    // 存起来给 resetOverlay 统一拆
        scrim.animate().alpha(1f).setDuration(220).start();

        // ---- 舞台：内页 + 封面（用边距定位，避免闪到左上角） ----
        // 用一个 FrameLayout 当"舞台"：内页先叠上去当底，封面再盖在最上面。
        // 之所以不用 translationX/Y 而是用 leftMargin/topMargin 当起点：
        // 边距在布局阶段就生效，第一帧就在正确位置，不会出现"先闪到左上角再飞过去"。
        final FrameLayout stage = new FrameLayout(activity);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(w, h);
        slp.leftMargin = startX;
        slp.topMargin = startY;
        stage.setLayoutParams(slp);
        // 相机距离决定 3D 旋转的透视强度：越大越"平"，18000×密度看起来比较稳
        float cam = getResources().getDisplayMetrics().density * 18000;
        stage.setCameraDistance(cam);
        stage.setPivotX(0f);   // 枢轴放左上角，后面整体缩放/平移以左上角为基准，好算
        stage.setPivotY(0f);
        overlayStage = stage;

        // 舞台里的内页（"第 1 页" + 分类名 + 分隔线）
        // 这一层平时被封面完全盖住，只有当封面转到 −90° 之后才会露出来
        final LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundResource(R.drawable.px_paper);   // 纸张纹理，像真的书内页
        int pp = Ui.dp(activity, 12);
        page.setPadding(pp, pp, pp, pp);
        page.addView(Ui.text(activity, "第 1 页", 7, R.color.ink_hint));   // 左上角页码，小一号
        TextView inPageName = Ui.text(activity, cat.name, 9, R.color.ink);
        inPageName.setPadding(0, Ui.dp(activity, 6), 0, 0);
        page.addView(inPageName);
        View inPageLine = new View(activity);   // 一条 1dp 的分隔线，模拟"标题下划线"
        inPageLine.setBackgroundColor(activity.getColor(R.color.paper_dark));
        LinearLayout.LayoutParams inLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(activity, 1)));
        inLp.topMargin = Ui.dp(activity, 8);
        inPageLine.setLayoutParams(inLp);
        page.addView(inPageLine);
        stage.addView(page, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 舞台上的封面：绕书脊（左边缘）掀开
        // pivot 设在左边缘中点 = 书脊位置，rotateY 时就像真的从书脊那根轴翻过去
        final PixelBookView cov = new PixelBookView(activity);
        cov.setBook(cat.colorInt, cat.name, cat.glyph);
        cov.setPivotX(0f);
        cov.setPivotY(h / 2f);
        cov.setCameraDistance(cam);
        stage.addView(cov, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        layer.addView(stage);
        cover.setVisibility(View.INVISIBLE);   // 原书先藏起来，否则会和舞台上的假封面"重影"
        overlaySource = cover;

        // ---- 翻开后铺满整屏的"大名字"页 ----
        // 内页淡出后由它接棒：巨大分类字 + 分类名 + 一句话简介，像书翻开后的扉页
        final LinearLayout pageFull = new LinearLayout(activity);
        pageFull.setOrientation(LinearLayout.VERTICAL);
        pageFull.setGravity(Gravity.CENTER_HORIZONTAL);
        pageFull.setBackgroundColor(activity.getColor(R.color.paper));
        pageFull.setAlpha(0f);        // 先透明，等阶段的③再淡入
        pageFull.setClickable(true);  // 吃掉点击，避免误触到下面的书架
        pageFull.setPadding(Ui.dp(activity, 32), Ui.dp(activity, 70),
                Ui.dp(activity, 32), Ui.dp(activity, 40));
        pageFull.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // ① 96sp 的巨大分类字（例如「游」），一眼就能认出是哪本书
        TextView bigGlyph = Ui.text(activity, cat.glyph, 96, R.color.brand_deep);
        bigGlyph.setGravity(Gravity.CENTER);
        bigGlyph.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        pageFull.addView(bigGlyph);
        // ② 34sp 的分类名
        TextView bigName = Ui.text(activity, cat.name, 34, R.color.ink);
        bigName.setPadding(0, Ui.dp(activity, 18), 0, 0);
        bigName.setGravity(Gravity.CENTER);
        bigName.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        pageFull.addView(bigName);
        // ③ 16sp 的一句话简介（来自 categories.json 的 desc 字段）
        TextView bigDesc = Ui.text(activity, cat.desc, 16, R.color.ink_secondary);
        bigDesc.setPadding(0, Ui.dp(activity, 14), 0, 0);
        bigDesc.setGravity(Gravity.CENTER);
        bigDesc.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        pageFull.addView(bigDesc);
        layer.addView(pageFull);
        overlayPage = pageFull;

        // 目标缩放比：宽度方向最多占 94%、高度方向最多占 58%，取小的那个，
        // 保证书放大后既不出左右边界（0.94），也不会高到顶住状态栏（0.58）。
        final float scale = Math.min(rootW * 0.94f / w, rootH * 0.58f / h);
        // 居中：屏幕宽减去"放大后的书宽"，剩下的空白左右各一半
        final float targetX = (rootW - w * scale) / 2f;
        final float targetY = (rootH - h * scale) / 2f;

        // 等一帧，确保舞台的初始边距已经生效
        // （不 post 的话，同一帧内先 setLayoutParams 再 animate，起点可能还是旧值）
        post(new Runnable() {
            @Override
            public void run() {
                // 第一步：缓慢移到 HomeTabView 中央，同时放大
                // translationX/Y 用「目标位置 − 起点位置」的差值，
                // 因为 stage 现在的 leftMargin/topMargin 已经等于起点，平移量就是差值。
                stage.animate()
                        .translationX(targetX - startX)
                        .translationY(targetY - startY)
                        .scaleX(scale).scaleY(scale)
                        .setDuration(420)                                  // 420ms，慢而稳
                        .setInterpolator(new DecelerateInterpolator())     // 先快后慢，落点更稳
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                // 第二步：封面沿书脊掀开
                                // rotateY 转到 −170°，几乎完全翻到左边去；
                                // 用 AccelerateDecelerateInterpolator，起手和收尾都慢，像翻纸。
                                cov.animate()
                                        .rotationY(-170f)
                                        .setDuration(560)
                                        .setInterpolator(new AccelerateDecelerateInterpolator())
                                        .withEndAction(new Runnable() {
                                            @Override
                                            public void run() {
                                                // 第三步：内页淡出，整屏分类名页淡入
                                                // 此时书已经完全翻开，内页露在最上面，
                                                // 先让它 240ms 淡出，同一时刻大名字页从下方 16dp 浮上来
                                                stage.animate().alpha(0f)
                                                        .setDuration(240).start();
                                                pageFull.setAlpha(0f);
                                                pageFull.setTranslationY(Ui.dp(activity, 16));
                                                pageFull.animate().alpha(1f).translationY(0f)
                                                        .setDuration(320).start();
                                                // 给大名字页留 620ms 的阅读时间（字很大，一闪而过看不清），
                                                // 到点再执行 stepFinish 真正切 Tab
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
                                                        // isAttachedToWindow() 再确认一次视图还活着，
                                                        // 防止 Activity 已销毁还去操作页面
                                                        if (host != null && isAttachedToWindow()) {
                                                            host.openCategory(cat.id);
                                                        }
                                                        // 切 Tab 之后再等 300ms 才拆覆盖层：
                                                        // 太早拆会看见"书突然消失"，太晚拆会盖住剧本页
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

    /**
     * 清掉翻书动画的临时视图，恢复原封面。
     *
     * <p>这是整个动画唯一的"收尾出口"，三个地方会调它：
     * 正常播完（{@code overlayCleanup}）、视图被移出窗口（onDetachedFromWindow）、
     * Activity 销毁。所以它必须**幂等**——重复调用不能报错，也不能漏恢复。</p>
     */
    private void resetOverlay() {
        if (layer != null) {
            if (overlayPage != null) {
                layer.removeView(overlayPage);    // 整屏"大名字"页
            }
            if (overlayStage != null) {
                layer.removeView(overlayStage);   // 舞台（内页 + 封面）
            }
            if (overlayScrim != null) {
                layer.removeView(overlayScrim);   // 压暗遮罩
            }
        }
        // 引用全部置空，防止下一次动画误用上一次的临时视图
        overlayPage = null;
        overlayStage = null;
        overlayScrim = null;
        if (overlaySource != null) {
            overlaySource.setVisibility(View.VISIBLE);   // 把书恢复显示，否则书架上会空一块
            overlaySource = null;
        }
        opening = false;   // 解锁，允许再点下一本书
    }

    // ---------------- ④ 反诈四步口诀 ----------------

    /**
     * 搭底部「反诈四步口诀」卡片：两行 × 四句，点整张卡弹出完整解释。
     *
     * <p>注意：当前版本的构造函数里**没有调用**它（口诀卡已从首页移除），
     * 代码保留下来是为了以后需要时能直接接回首页。</p>
     */
    private void buildRules() {
        LinearLayout card = Ui.card(activity, 14, 0);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) card.getLayoutParams();
        lp.topMargin = Ui.dp(activity, 6);
        lp.bottomMargin = Ui.dp(activity, 4);

        // 卡头：左边标题，右边"点开看解释 →"，中间用 weight=1 的透明 View 撑开
        LinearLayout head = new LinearLayout(activity);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        head.addView(Ui.text(activity, "反诈口诀 · 8 个字", 13, R.color.brand));
        View spacer = new View(activity);
        head.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        head.addView(Ui.text(activity, "点开看解释 →", 9, R.color.brand_deep));
        card.addView(head);

        // 8 句口诀拆成两行，每行 4 句，句与句之间用「 · 」隔开
        java.util.List<Tips.Tip> tips = Tips.load(activity);
        // 两行 × 四句：码链钱平 / 话急警证
        for (int row = 0; row < 2; row++) {
            StringBuilder sb = new StringBuilder();
            for (int i = row * 4; i < Math.min(row * 4 + 4, tips.size()); i++) {   // 不够 8 句也不会越界
                if (sb.length() > 0) {
                    sb.append(" · ");
                }
                sb.append("「").append(tips.get(i).key).append("」").append(tips.get(i).sub);
            }
            TextView line = Ui.text(activity, sb.toString(), 11, R.color.ink_secondary);
            line.setPadding(0, Ui.dp(activity, 8), 0, 0);
            line.setLineSpacing(Ui.dp(activity, 3), 1f);   // 行距多加 3dp，两行不挤
            card.addView(line);
        }

        // 一行最实用的"四个不"提醒，用更淡的灰色，不抢主体
        TextView hint = Ui.text(activity, "验证码不给 · 陌生链接不点 · 先垫钱不转 · 交易不离平台", 9,
                R.color.ink_hint);
        hint.setPadding(0, Ui.dp(activity, 8), 0, 0);
        hint.setLineSpacing(Ui.dp(activity, 2), 1f);
        card.addView(hint);

        // 整卡可点：弹出 8 句口诀的完整解释页面
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Tips.showAll(activity);
            }
        });
        content.addView(card);
    }

    // ---------------- 生命周期：控制轮播与清理 ----------------

    /**
     * 视图被挂到窗口上：启动轮播自动播放 + 播放书架入场动画。
     * 注意这里**不能**做耗时操作，也不能假设已经被测量过（宽高此时可能还是 0）。
     */
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (carousel != null) {
            carousel.start();   // 离开页面时 stop 过，回来要重新起，否则轮播不动
        }
        if (!introPlayed && !introCells.isEmpty()) {
            post(shelfIntro);   // post 到下一帧，等布局完成再播入场
        }
    }

    /**
     * 视图被移出窗口：停掉自动播放、取消所有还在排队的回调，并强制清理翻书覆盖层。
     * 这一步是"兜底"，防止动画播到一半切走留下黑屏遮罩。
     */
    @Override
    public void onDetachedFromWindow() {
        if (carousel != null) {
            carousel.stop();   // 不复位到第 0 张，回来时还停在原来那张
        }
        removeCallbacks(shelfIntro);      // 书架入场还没播完就切走：取消它
        removeCallbacks(overlayCleanup);  // 兜底清理还没执行：取消，下面手动清
        if (stepFinish != null) {
            removeCallbacks(stepFinish);  // 动画最后一步的切 Tab 还没执行：取消，别在页面已走时乱跳
            stepFinish = null;
        }
        resetOverlay();                   // 无论什么状态，临时视图都必须拆干净
        super.onDetachedFromWindow();
    }
}
