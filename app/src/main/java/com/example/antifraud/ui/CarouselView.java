package com.example.antifraud.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.antifraud.R;
import com.example.antifraud.engine.AppLog;
import com.example.antifraud.engine.SettingsStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * 首页热门案例轮播（纯代码构建、零第三方依赖、不联网、不申请权限）。
 *
 * <p><b>它是干什么的：</b>「首页」顶部那块会自动翻动的卡片区。数据<b>只来自</b>
 * assets/data/cases.json（真实案例，字段与文件一一对应，绝不自己编数据）和
 * assets/data/tips.json（反诈口诀，经 {@link Tips#load} 读取）；两者在
 * {@link #mixWithTips} 里按「两案一案诀」的节奏混排，最多 {@link #MAX_CARDS} 张卡。</p>
 *
 * <p><b>关键设计：</b>
 * <ul>
 *   <li><b>滑动切换</b>：没用 ViewPager——所有卡片一次性铺在同一个 FrameLayout（cardLayer）
 *       里、逐张铺满，切换时只改两张卡的 translationX 做属性动画，卡片树只有一份。</li>
 *   <li><b>自动轮播</b>：每 {@link #AUTO_DELAY_MS} 毫秒切一张；看不到时（切 Tab、
 *       退后台）只"续约"不切换，回来立刻接上。</li>
 *   <li><b>手势判定</b>：按下记 downX/downY/downAt；抬手时「横向位移 &gt; 36dp 且横向 &gt; 纵向」
 *       算滑动，「横纵都小于 touchSlop 且按时 &lt; 600ms」算点击，详见两个触摸方法的注释。</li>
 *   <li><b>点卡片</b>：弹像素风 {@link PixelDialog}，分区显示案情 / 金额 / 原文链接 / 配图说明。</li>
 * </ul>
 * 卡片里的字统统 maxLines(1) + ellipsize，2.1 倍字号也不会把卡片撑破；留白由
 * {@link #box} / {@link #vBox} 按字号放大，竖直方向封顶 {@link #BOX_MAX_SCALE} 倍。</p>
 *
 * <p><b>谁在用它：</b>{@link HomeTabView} new 出来摆到首页顶部；可见时调 {@link #start()}、
 * 不可见时调 {@link #stop()}；{@link #setHost} 注入 {@link TabHost} 回调。</p>
 */
public class CarouselView extends FrameLayout {

    /**
     * 配图署名表：covers/caseX.jpg -> "本作品自绘"。
     * 从 assets/data/covers.json 一次性读入，点开案例时显示"配图"一栏，
     * 说明所有图片都是本作品自绘的像素图，没有任何第三方版权问题。
     */
    private static final Map<String, String> CREDITS = new HashMap<>();

    /** 案例数据文件（只读 assets，不联网；缺失时由 fallbackPanel 兜底，不编造内容） */
    private static final String CASES_ASSET = "data/cases.json";
    /**
     * 最多展示几张卡。
     * 数据文件里是 6 条真实案例，混入 3 张口诀卡后正好 9 张，
     * 再留一点余量到 10；超出部分直接截断，避免卡片太多、指示点挤成一条线。
     */
    private static final int MAX_CARDS = 10;
    /** 自动轮播间隔：5 秒。太短来不及读完标题，太长又像静态图 */
    private static final long AUTO_DELAY_MS = 5000L;
    /** 判定为"滑动"的最小水平距离（dp）：小于它就算点击，避免手抖误翻页 */
    private static final int SWIPE_DISTANCE_DP = 36;
    /** 切换动画时长（毫秒）：280ms 与系统默认的翻页节奏接近 */
    private static final int SWITCH_DURATION_MS = 280;
    /**
     * 卡片内边距/指示点的放大上限。
     * 卡片高度由 HomeTabView 控制在 1.5 倍以内，内边距再按 1.9 倍长就会挤掉配图；
     * 文字本身走 sp 自动放大，这里的留白只需要"跟着喘口气"就够。
     */
    private static final float BOX_MAX_SCALE = 1.5f;

    /** 宿主 Activity：读 assets、取颜色、弹 Toast 都要用；本类不是独立页面 */
    private final Activity activity;
    /** 卡片层：所有卡片都铺满这里，靠 translationX 做左右切换 */
    private final FrameLayout cardLayer;
    /** 底部像素指示点 */
    private final LinearLayout dotRow;
    /** 主线程 Handler：自动轮播的定时器，暂停/恢复都靠它 */
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** 已构建的卡片数据（含 view 引用），索引即卡片序号 */
    private final List<Panel> panels = new ArrayList<>();
    /** 与 panels 一一对应的指示点 View */
    private final List<View> dots = new ArrayList<>();

    /** 由 HomeTabView 注入，"全部来源"按钮要用 */
    private TabHost host;
    /** 当前显示第几张（0 开始） */
    private int current;
    /** 自动轮播开关（start/stop 控制） */
    private boolean running;
    /** 正在做切换动画时，忽略新的切换请求 */
    private boolean animating;

    /** 本次手势按下的横坐标（像素）：抬手时和它比较，算横向位移 */
    private float downX;
    /** 本次手势按下的纵坐标（像素）：用来判断"是不是在上下滚页面"而不是翻卡 */
    private float downY;
    /** 本次手势按下的时间戳（SystemClock.uptimeMillis），用于区分"轻点"和"长按拖拽" */
    private long downAt;
    /** 判定滑动所需的水平距离，由 {@link #SWIPE_DISTANCE_DP} 换算成像素后只算一次 */
    private final int swipeDistancePx;
    /** 系统级"最小滑动距离"（touchSlop）：小于它的位移不算滑动，用来兜住手抖 */
    private final int clickSlopPx;

    /**
     * 5 秒自动切下一张：不可见时只续约不切换，避免后台空转出动画。
     *
     * <p>时序：start() → scheduleNext() 投递本 Runnable（延迟 5 秒）→ run() 判断是否
     * 真的可见且没在动画中，满足才 switchTo 下一张 → 最后<b>无条件</b>再调
     * scheduleNext() 把自己重新投递一次，形成「切一张、等 5 秒、再切一张」的循环。
     * 先切换后续约，意味着即便这次被 animating 挡住，也不会永久停播。</p>
     */
    private final Runnable autoNext = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                // 已经 stop() 了：直接退出，不再续约，定时器自然结束
                return;
            }
            // 四个前置条件缺一不可：在屏幕上、窗口可见、至少两张卡、不在切换动画中
            if (isShown() && getWindowVisibility() == View.VISIBLE
                    && panels.size() > 1 && !animating) {
                // 取模实现「最后一张之后回到第一张」的循环
                switchTo((current + 1) % panels.size(), 1);
            }
            // 无论这次有没有真的切，都要重新排下一次，保证轮播不会因一次异常状态就停掉
            scheduleNext();
        }
    };

    /**
     * 构造轮播：只建骨架（卡片层 + 指示点层），卡片内容由 buildCards() 读 assets 后再填。
     *
     * @param a 宿主 Activity：本控件必须挂在一个 Activity 上（要读它的 assets 和主题色）
     */
    public CarouselView(Activity a) {
        super(a);
        activity = a;
        // 两个手势阈值只算一次，触摸回调里直接用，避免每个 MOVE 事件都做 dp 换算
        swipeDistancePx = Ui.dp(a, SWIPE_DISTANCE_DP);
        clickSlopPx = ViewConfiguration.get(a).getScaledTouchSlop();
        // 卡片切换时靠 translationX 移出可视区，必须裁掉越界部分，否则会盖到下面的书架
        setClipChildren(true);

        // 卡片层（在下）：铺满整个轮播区，各卡靠 translationX 移入移出。
        // 先加 cardLayer、后加 dotRow，后加的在上层，指示点才不会被卡片盖住。
        cardLayer = new FrameLayout(a);
        addView(cardLayer, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));

        // 指示点（在上）：贴底居中，bottomMargin 走 vBox（跟着字号放大但封顶 1.5 倍）
        dotRow = new LinearLayout(a);
        dotRow.setOrientation(LinearLayout.HORIZONTAL);
        dotRow.setGravity(Gravity.CENTER);
        LayoutParams dotLp = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        dotLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        dotLp.bottomMargin = vBox(7);
        addView(dotRow, dotLp);

        // MainActivity 实现了 TabHost，先兜底自取一次；HomeTabView 构造后还会显式 setHost
        if (a instanceof TabHost) {
            host = (TabHost) a;
        }
        // 最后才读数据、建卡片：此时 cardLayer / dotRow 都已挂在树上，addView 才有意义
        buildCards();
    }

    /** HomeTabView 注入 Host 回调（构造参数按约定只有 Activity）；传 null 时"全部来源"按钮退化成弹 Toast */
    public void setHost(TabHost host) {
        this.host = host;
    }

    /** 横向留白用的「装文字」尺寸：按字号放大（scaleBox 内部上限 1.9 倍），宽度富余可以放开长 */
    private int box(float sizeDp) {
        return SettingsStore.scaleBox(activity, Ui.dp(activity, sizeDp));
    }

    /**
     * 竖直方向的留白：卡片外层高度被 HomeTabView 封顶在 1.5 倍，
     * 内边距要是按 1.9 倍长，配图和文字就会在卡片里互相挤，所以竖直方向同样封顶 1.5 倍。
     */
    private int vBox(float sizeDp) {
        int base = Ui.dp(activity, sizeDp);                 // 标准字号下的基准像素值
        int sat = SettingsStore.scaleBox(activity, base);   // 放大后的值（最多 1.9 倍）
        // 再和 1.5 倍的硬上限取小，得到最终竖直留白
        return Math.min(sat, Math.round(base * BOX_MAX_SCALE));
    }

    // ---------------- 自动轮播的启动 / 停止 ----------------

    /** 由 HomeTabView 在 onAttachedToWindow 调用：开始 5 秒自动轮播 */
    public void start() {
        running = true;
        // 重复调用是安全的：scheduleNext 会先清掉旧的定时器
        scheduleNext();
    }

    /** 由 HomeTabView 在 onDetachedFromWindow 调用：停止自动轮播并让卡片归位 */
    public void stop() {
        running = false;
        // 先摘掉定时器再复位，避免复位途中又被 autoNext 切走
        handler.removeCallbacks(autoNext);
        settleCards();
    }

    /**
     * 重新排下一次自动切换（先取消旧的，再按 running 状态决定要不要排）。
     * 所有"暂停/恢复"的分支最后都汇到这里，保证任意时刻最多只有一个待执行的 autoNext。
     */
    private void scheduleNext() {
        handler.removeCallbacks(autoNext);
        if (running) {
            handler.postDelayed(autoNext, AUTO_DELAY_MS);
        }
    }

    /**
     * 被别的 Tab 盖住 / App 退到后台时暂停，回来再续上。
     * 注意 changedView 可能是祖先视图（HomeTabView 被切成 GONE），所以不能只看 this。
     *
     * @param changedView 可见性发生变化的那个 View（可能是自己，也可能是祖先）
     * @param visibility  变化后的可见性（View.VISIBLE / INVISIBLE / GONE）
     */
    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == View.VISIBLE) {
            scheduleNext();     // 重新可见：重新开始计时（会先清旧的，不会叠加多个定时器）
        } else {
            handler.removeCallbacks(autoNext);      // 不可见：摘掉定时器，省电
        }
    }

    /**
     * 整个窗口的可见性变化（例如按 Home 键回到桌面、来电遮挡等）。
     * 与 onVisibilityChanged 分开处理，是因为窗口可见性变化时 View 自身的 visibility 不一定变。
     *
     * @param visibility 窗口可见性
     */
    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == View.VISIBLE) {
            scheduleNext();
        } else {
            handler.removeCallbacks(autoNext);
        }
    }

    /** 挂到窗口上时：如果之前处于"运行中"状态，重新把定时器接上（页面切回来时会走这里） */
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (running) {
            scheduleNext();
        }
    }

    /** 从窗口上摘下来时：先掐掉定时器，避免 Handler 持有已销毁的 View 造成内存泄漏 */
    @Override
    public void onDetachedFromWindow() {
        // 解除窗口时先掐掉定时器，避免泄漏
        handler.removeCallbacks(autoNext);
        super.onDetachedFromWindow();
    }

    // ---------------- 数据读取（只读 cases.json） ----------------

    /**
     * 重建全部卡片：清空旧视图 → 读 cases.json → 混入口诀卡 → 逐张建 View → 建指示点。
     * 只在构造时调用一次；项目里没有"数据变了要刷新轮播"的场景，所以没有做成公开方法。
     */
    private void buildCards() {
        // 先把上一次的痕迹清干净，保证重复调用也不会出现重影
        cardLayer.removeAllViews();
        panels.clear();
        dotRow.removeAllViews();
        dots.clear();
        current = 0;

        List<Panel> loaded = readPanels();        // 读盘 + 混排口诀卡（已在内部完成）
        if (loaded.isEmpty()) {
            // 数据文件缺失时给一个占位卡，不编造任何案例内容
            loaded.add(fallbackPanel());
        }
        for (int i = 0; i < loaded.size() && i < MAX_CARDS; i++) {
            Panel p = loaded.get(i);
            // 口诀卡和案例卡是两种完全不同的排版，按 isTip 分流建视图
            p.view = p.isTip ? buildTipCard(p) : buildCard(p);
            // 除第 0 张外先设成 INVISIBLE（不是 GONE）：保持布局、但不参与绘制，
            // 切换时再把目标卡设成 VISIBLE 并把旧卡设为 INVISIBLE
            p.view.setVisibility(i == 0 ? View.VISIBLE : View.INVISIBLE);
            cardLayer.addView(p.view, new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT));
            panels.add(p);
        }
        buildDots(panels.size());
        updateDots(0);
    }

    /**
     * 读取 assets/data/covers.json，把每张配图的摄影师写进 CREDITS，
     * 在案例详情弹窗里显示，避免任何版权争议（配图全部是本作品自绘的像素图）。
     * 读盘失败只记日志、不抛异常：CREDITS 留空，弹窗里就不显示"配图"一栏。
     */
    private void loadCredits() {
        // CREDITS 是 static，多个 CarouselView 实例共享一份，读过就不再读
        if (!CREDITS.isEmpty()) {
            return;
        }
        InputStream in = null;
        try {
            in = activity.getAssets().open("data/covers.json");
            // 4KB 缓冲循环读：assets 里的流拿不到长度，只能手动读满
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            // 明确按 UTF-8 解码：JSON 里是中文，用平台默认编码在部分机型上会乱码
            JSONArray arr = new JSONObject(new String(out.toByteArray(), "UTF-8"))
                    .optJSONArray("covers");
            if (arr == null) {
                return;
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;       // 非对象元素跳过，不能让整张表作废
                }
                String file = o.optString("file", "");
                String author = o.optString("author", "");
                // 两个字段缺一个就跳过：只存"文件名 -> 作者"的完整映射
                if (!file.isEmpty() && !author.isEmpty()) {
                    CREDITS.put(file, author);
                }
            }
        } catch (Exception e) {
            AppLog.e("HOME", "读取配图署名失败：data/covers.json", e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * 读取 assets/data/cases.json，最多返回 MAX_CARDS 条。
     *
     * @return 案例卡 + 口诀卡混排后的列表；读盘失败时返回空列表（由调用方给占位卡）
     */
    private List<Panel> readPanels() {
        loadCredits();      // 先备好署名表，下面算 cover 路径后要立刻查作者
        List<Panel> list = new ArrayList<>();
        InputStream in = null;
        try {
            in = activity.getAssets().open(CASES_ASSET);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            String json = new String(out.toByteArray(), "UTF-8");
            // 顶层是一个对象，真正的数组在 "cases" 键下
            JSONArray arr = new JSONObject(json).optJSONArray("cases");
            if (arr == null) {
                return list;
            }
            // 双重条件：既不能越界，也不能已经攒够 MAX_CARDS 张
            for (int i = 0; i < arr.length() && list.size() < MAX_CARDS; i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                Panel p = new Panel();
                // 每个字段都 trim：数据文件里常有多余空格，直接显示会歪
                p.title = o.optString("title", "").trim();
                p.publisher = o.optString("publisher", "").trim();
                p.date = o.optString("date", "").trim();
                p.tag = o.optString("tag", "").trim();
                p.amount = o.optString("amount", "").trim();
                p.synopsis = o.optString("synopsis", "").trim();
                p.url = o.optString("url", "").trim();
                // 没有标题的条目视为无效数据，跳过（卡片没标题等于没法看）
                if (p.title.isEmpty()) {
                    continue;
                }
                // 配图按"第几条有效案例"顺序命名，与 list.size() 对应，避免跳号
                p.cover = "covers/case" + list.size() + ".jpg";
                p.credit = CREDITS.get(p.cover);     // 查不到就是 null，弹窗不显示配图栏
                list.add(p);
            }
        } catch (Exception e) {
            AppLog.e("HOME", "读取轮播案例失败：" + CASES_ASSET, e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
        return mixWithTips(list);      // 最后把口诀卡按节奏插进去，就是最终展示顺序
    }

    /**
     * 把「口诀卡」按「两案一案诀」的节奏混进案例里。
     *
     * <p><b>顺序规则（重点）：</b>页面最终的数据是
     * <pre>
     *   案例0, 案例1, 口诀0, 案例2, 案例3, 口诀1, 案例4, 案例5, 口诀2 ...
     * </pre>
     * 即以 2 条案例为一组，每组后面插 1 张口诀卡。这样读者翻两屏就会遇到一次"反诈口诀"，
     * 既不打断案例的阅读节奏，又能反复强化记忆点；案例是奇数条时多出来的口诀卡追加到末尾。
     * 6 条案例 + 3 张口诀卡 → 正好 9 张，与 SourcesActivity 里"首页轮播的 9 张配图"一致。</p>
     *
     * @param cases 已读到的案例卡（可能为空）
     * @return 混排后的完整列表；口诀数据加载失败时原样返回 cases
     */
    private List<Panel> mixWithTips(List<Panel> cases) {
        List<Tips.Tip> all = Tips.load(activity);
        if (all.isEmpty()) {
            return cases;      // 口诀文件缺失：不做混排，轮播只剩案例卡，功能仍可用
        }
        // 3 条口诀装一张卡（p.tips 最多 3 条），卡片上的版面正好放得下三行
        List<Panel> tipPanels = new ArrayList<>();
        for (int i = 0; i < all.size(); i += 3) {
            Panel p = new Panel();
            p.isTip = true;
            p.cover = "covers/tip" + tipPanels.size() + ".jpg";   // 口诀配图按 tip0/tip1 命名
            // 内层取 [i, i+3) 这一段，最后一张卡可能不满 3 条
            for (int j = i; j < Math.min(i + 3, all.size()); j++) {
                p.tips.add(all.get(j));
            }
            tipPanels.add(p);
        }
        List<Panel> mixed = new ArrayList<>();
        int tipIndex = 0;         // 下一张待插入的口诀卡下标
        for (int i = 0; i < cases.size(); i++) {
            mixed.add(cases.get(i));
            // i % 2 == 1 即"本组第 2 条案例之后"，此刻插入一张口诀卡
            if (i % 2 == 1 && tipIndex < tipPanels.size()) {
                mixed.add(tipPanels.get(tipIndex++));
            }
        }
        // 案例先用完（或案例是奇数条）时，剩下的口诀卡全部追加到末尾
        while (tipIndex < tipPanels.size()) {
            mixed.add(tipPanels.get(tipIndex++));
        }
        return mixed;
    }

    /**
     * 口诀卡：角标「必背」，三条口诀各一行，点整张卡看全部 8 句。
     *
     * @param p 口诀卡数据（p.tips 里有 1-3 条口诀）
     * @return 建好的卡片视图
     */
    private LinearLayout buildTipCard(final Panel p) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.px_panel);
        // 左右留白可以放开长；上下留白封顶 1.5 倍，和卡片高度上限保持一致
        card.setPadding(box(12), vBox(9), box(12), vBox(12));

        // 顶行：左「反诈口诀」实心品牌红 chip，右「必背」浅底角标，中间 spacer 顶开
        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView chip = Ui.text(activity, "反诈口诀", 9, R.color.white);
        chip.setBackground(pixelBox(activity.getColor(R.color.brand), true));
        chip.setPadding(box(6), vBox(2), box(6), vBox(2));
        top.addView(chip);
        // weight=1 的空 View 吃掉中间剩余空间，把右侧角标推到最右边
        View spacer = new View(activity);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView badge = Ui.text(activity, "必背", 8, R.color.brand_deep);
        badge.setBackground(pixelBox(activity.getColor(R.color.brand_soft), true));
        badge.setPadding(box(5), vBox(2), box(5), vBox(2));
        top.addView(badge);
        card.addView(top);

        // 大标题：一句"背下 8 个字，记住 8 个动作"，说明这张卡是口诀不是案例
        TextView title = Ui.text(activity, "背下 8 个字，记住 8 个动作", 13, R.color.ink);
        title.setPadding(0, vBox(6), 0, 0);
        card.addView(title);

        // 三条口诀各一行：左边「关键动作」，右边解释，解释超长自动省略
        for (Tips.Tip t : p.tips) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, vBox(5), 0, 0);
            TextView key = Ui.text(activity, "「" + t.key + "」" + t.sub, 11, R.color.brand);
            key.setMaxLines(1);     // 口诀本身就短，左边不省略
            row.addView(key);
            // 右边解释 weight=1 占满剩余宽度，单行 + 尾部省略，字号再大也不撑高卡片
            TextView textView = Ui.text(activity, "  " + t.text, 9, R.color.ink_secondary);
            textView.setMaxLines(1);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(textView, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            card.addView(row);
        }

        // 底部提示：告诉用户整张卡可点，点开能看全部 8 句
        TextView more = Ui.text(activity, "点开看全部 8 句解释 →", 9, R.color.brand_deep);
        more.setPadding(0, vBox(7), 0, 0);
        card.addView(more);

        // 配图放在文字下方（先看口诀，再看画面）。900:300 是口诀图的固定比例
        addCoverBelow(card, p.cover, 900, 300);

        // 整张卡可点：口诀卡没有单独的详情可看，点哪里都弹"全部口诀"
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Tips.showAll(activity);
            }
        });
        return card;
    }

    /**
     * 数据文件缺失时的占位卡：文案只说明"没读到数据"，不编造任何案例内容。
     *
     * @return 只有一条提示的 Panel
     */
    private Panel fallbackPanel() {
        Panel p = new Panel();
        p.tag = "案例";
        p.title = "案例数据未加载";
        p.publisher = "assets/data/cases.json";
        p.date = "";
        p.amount = "";
        p.synopsis = "请检查数据文件是否存在";
        p.url = "";
        return p;
    }

    // ---------------- 卡片视图（全部代码构建，无 XML） ----------------

    /**
     * 案例卡：从上到下依次是「分类 chip + 真实案例角标」→ 标题 → 发布方·日期·金额 →
     * 一句话案情 → 配图。除配图外每行都限死一行并用省略号收尾，
     * 这样字号开到 2.1 倍时卡片高度也不会失控。
     *
     * @param p 一条案例数据（字段来自 cases.json）
     * @return 建好的卡片视图
     */
    private LinearLayout buildCard(final Panel p) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.px_panel);
        // 左右留白放开长；上下留白封顶 1.5 倍（卡片总高被 HomeTabView 限住了）
        card.setPadding(box(12), vBox(9), box(12), vBox(15));

        // 顶行：左侧 tag 像素 chip，右侧「真实案例」角标（品牌红实心 + 白字）
        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView chip = Ui.text(activity, p.tag.isEmpty() ? "案例" : p.tag, 9,
                R.color.brand_deep);
        chip.setBackground(pixelBox(activity.getColor(R.color.brand_soft), true));
        chip.setPadding(box(6), vBox(2), box(6), vBox(2));
        chip.setMaxLines(1);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(chip, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 中间留白，把角标顶到右边
        View spacer = new View(activity);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        TextView badge = Ui.text(activity, "真实案例", 8, R.color.white);
        badge.setBackground(pixelBox(activity.getColor(R.color.brand), true));
        badge.setPadding(box(5), vBox(2), box(5), vBox(2));
        badge.setMaxLines(1);
        top.addView(badge, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        card.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 标题：单行，超长省略（字号再大也不换行撑高卡片）
        TextView title = Ui.text(activity, p.title, 14, R.color.ink);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, vBox(6), 0, 0);
        card.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 发布方 · 日期 · 金额
        TextView meta = Ui.text(activity, joinDot(p.publisher, p.date, p.amount), 9,
                R.color.ink_hint);
        meta.setMaxLines(1);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        meta.setPadding(0, vBox(3), 0, 0);
        card.addView(meta, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 案情一句话，单行省略
        TextView synopsis = Ui.text(activity, p.synopsis, 10, R.color.ink_secondary);
        synopsis.setMaxLines(1);
        synopsis.setEllipsize(TextUtils.TruncateAt.END);
        synopsis.setPadding(0, vBox(3), 0, 0);
        card.addView(synopsis, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 配图放在文字下方：先看标题与来源，再看画面
        addCoverBelow(card, p.cover, 900, 360);

        return card;
    }

    /** 把配图加到卡片文字下方：上方 8dp 间距（vBox 封顶 1.5 倍），读不到图就整块跳过、卡片只剩文字 */
    private void addCoverBelow(LinearLayout card, String coverPath, int imgW, int imgH) {
        ImageView cover = coverView(coverPath, (float) imgW / imgH);
        if (cover == null) {
            return;
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, coverHeight(imgW, imgH));
        // 标题与插图的间距：跟着字号走，但同样封顶 1.5 倍，别把配图挤没
        lp.topMargin = vBox(8);
        card.addView(coverBox(cover), lp);
    }

    /**
     * 把配图包一层 FrameLayout 并挂上点击：点图全屏放大（{@link ImagePreview}），
     * 点文字区域仍然走"看案例详情 / 看口诀"，两者互不干扰。
     * 图片只是氛围示意、不对应具体案件，避免读者误以为是案发现场照片。
     */
    private FrameLayout coverBox(ImageView cover) {
        FrameLayout box = new FrameLayout(activity);
        box.addView(cover, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        // 点图片：全屏放大查看（点文字区域仍是看案例详情 / 口诀）
        cover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 只有能拿到 Bitmap 才能放大；别的情况（比如占位色块）就什么也不做
                android.graphics.drawable.Drawable d = ((ImageView) v).getDrawable();
                if (d instanceof android.graphics.drawable.BitmapDrawable) {
                    ImagePreview.show(activity,
                            ((android.graphics.drawable.BitmapDrawable) d).getBitmap());
                }
            }
        });
        return box;
    }

    /**
     * 按卡片实际可用宽度算图片高度，保证整张图等比显示、不被压扁或裁切。
     * 算法：屏幕宽 − 页面左右各 16dp − 卡片左右各 12dp 内边距，就是图片可用宽度；
     * 再按原图宽高比折算出高度，Math.max(1, ...) 兜住极端小屏算出的 0。
     */
    private int coverHeight(int imageWidth, int imageHeight) {
        int screenW = activity.getResources().getDisplayMetrics().widthPixels;
        // 32dp 是轮播外层左右边距，24dp 是卡片自身左右内边距之和（12 + 12）
        int cardW = screenW - Ui.dp(activity, 32) - Ui.dp(activity, 24);
        return Math.max(1, Math.round(cardW * (float) imageHeight / imageWidth));
    }

    /**
     * 读取 assets 里的卡片配图。配图是可选的：文件不存在或解码失败就返回 null，
     * 卡片会自动去掉图片区域、不会崩。
     * 这里用匿名 ImageView 子类重写 onMeasure：宽度按父容器给定，高度 = 宽度 / 宽高比，
     * 无论字号怎么变，图片都保持原比例、不会被拉扁或裁切。
     *
     * @param path  assets 内相对路径，例如 covers/case0.png
     * @param ratio 图片宽高比（案例图 900:360，口诀图 900:300）
     */
    private ImageView coverView(String path, final float ratio) {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        InputStream in = null;
        try {
            in = activity.getAssets().open(path);
            Bitmap bitmap = BitmapFactory.decodeStream(in);
            // decodeStream 失败（图坏了 / 不是图片）时返回 null，这里必须判断
            if (bitmap == null) {
                return null;
            }
            // 按图片真实比例测量高度：宽度撑满卡片，高度 = 宽度 / 比例，
            // 这样整张照片完整显示，不会像 CENTER_CROP 那样上下被切掉
            ImageView iv = new ImageView(activity) {
                @Override
                protected void onMeasure(int widthSpec, int heightSpec) {
                    int w = MeasureSpec.getSize(widthSpec);     // 父容器给的可用宽度
                    int h = Math.round(w / ratio);              // 按宽高比折算出的高度
                    setMeasuredDimension(w, h);                 // 直接写死测量结果，忽略父给的高度建议
                }
            };
            iv.setImageBitmap(bitmap);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);   // 上面已按原比例算好尺寸，这里铺满即可
            iv.setContentDescription("");                  // 纯装饰图，无障碍读屏不用念
            iv.setPadding(0, 0, 0, Ui.dp(activity, 2));    // 底部 2dp 呼吸空间
            return iv;
        } catch (Exception e) {
            AppLog.e("HOME", "轮播配图读取失败：" + path, e);
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** 像素风小方块背景：直角 + 1dp 深色描边（不用圆角），chip / 角标 / 指示点都用它 */
    private GradientDrawable pixelBox(int color, boolean border) {        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);   // 直角矩形，像素风不用圆角
        gd.setColor(color);
        if (border) {
            gd.setStroke(Ui.dp(activity, 1), activity.getColor(R.color.border_dark));
        }
        return gd;
    }

    /** 用「 · 」把非空字段拼起来（发布方 · 日期 · 金额）；空字段直接跳过，不会出现连续分隔符 */
    private String joinDot(String a, String b, String c) {
        StringBuilder sb = new StringBuilder();
        String[] parts = {a, b, c};
        for (String s : parts) {
            if (s == null || s.isEmpty()) {
                continue;
            }
            // 只有已经拼过内容时才补分隔符，第一段前面不加
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(s);
        }
        return sb.toString();
    }

    // ---------------- 指示点 ----------------

    /** 按卡片数量重建底部指示点（张数 = 点的个数）；点是纯装饰、不装文字，所以只做轻微放大 */
    private void buildDots(int count) {
        dotRow.removeAllViews();
        dots.clear();
        int size = vBox(6);
        int gap = vBox(3);
        for (int i = 0; i < count; i++) {
            View dot = new View(activity);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            // 左右各留一点间距，点与点之间才分得开
            lp.leftMargin = gap;
            lp.rightMargin = gap;
            dot.setLayoutParams(lp);
            dotRow.addView(dot);
            dots.add(dot);
        }
        updateDots(current);
    }

    /** 刷新指示点颜色：当前页用品牌红（brand），其余用浅线色（line） */
    private void updateDots(int index) {
        for (int i = 0; i < dots.size(); i++) {
            int color = activity.getColor(i == index ? R.color.brand : R.color.line);
            dots.get(i).setBackground(pixelBox(color, false));
        }
    }

    // ---------------- 切换动画 ----------------

    /**
     * 切换到第 target 张卡，dir 决定从哪一侧滑进来。
     *
     * <p>动画是"两张卡对着滑"：新卡先摆到屏幕外（dir &gt; 0 在右边、否则在左边），
     * 然后新卡平移到 0、旧卡同时平移到另一侧屏幕外，旧卡结束时藏起来并把 translationX
     * 复位，避免下次切换起点错乱。animating 在整段动画期间为 true，挡掉重复请求。</p>
     */
    private void switchTo(int target, int dir) {
        // 三层保护：目标就是当前张、越界、正在动画中——都不处理
        if (target == current || target < 0 || target >= panels.size() || animating) {
            return;
        }
        final View out = panels.get(current).view;     // 即将滑出的旧卡
        final View in = panels.get(target).view;       // 即将滑入的新卡
        if (out == null || in == null) {
            return;
        }
        // 还没完成布局（宽为 0）时直接切换，不做位移动画
        if (getWidth() <= 0) {
            out.setVisibility(View.INVISIBLE);
            out.setTranslationX(0f);
            in.setTranslationX(0f);
            in.setVisibility(View.VISIBLE);
            current = target;
            updateDots(current);
            return;
        }
        final int width = getWidth();     // 一个屏幕宽的位移量，正好把卡完全移出可视区
        // 先取消可能还在跑的旧动画，避免两段动画抢同一个属性
        in.animate().cancel();
        out.animate().cancel();
        animating = true;
        current = target;                 // 先更新 current，让指示点立刻跳到新位置
        updateDots(current);

        // 新卡从一侧滑入：dir > 0 时从右侧（+width）进来，否则从左侧（-width）进来
        in.setAlpha(1f);
        in.setTranslationX(dir > 0 ? width : -width);
        in.setVisibility(View.VISIBLE);
        in.animate()
                .translationX(0f)
                .setDuration(SWITCH_DURATION_MS)
                .setInterpolator(new DecelerateInterpolator())   // 先快后慢，收尾更自然
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        // 入场动画结束才算这一段切换真正结束，此时才允许下一次切换
                        animating = false;
                    }
                })
                .start();

        // 旧卡向另一侧滑出，结束后藏起来并归位
        out.animate()
                .translationX(-dir * width)
                .setDuration(SWITCH_DURATION_MS)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        // 藏起来 + translationX 归零：这是下一轮动画的正确起点
                        out.setVisibility(View.INVISIBLE);
                        out.setTranslationX(0f);
                    }
                })
                .start();
    }

    /** 停播时把所有卡片复位：取消动画、translationX 归零、只让 current 那张可见 */
    private void settleCards() {
        for (int i = 0; i < panels.size(); i++) {
            View v = panels.get(i).view;
            if (v == null) {
                continue;
            }
            v.animate().cancel();          // 掐断可能正在跑的滑动动画
            v.setTranslationX(0f);         // 归位
            // 只有当前卡可见，其它都藏起来（INVISIBLE 保留布局，比 GONE 更稳）
            v.setVisibility(i == current ? View.VISIBLE : View.INVISIBLE);
        }
        animating = false;                 // 动画全被掐掉了，标志也要跟着复位
    }

    /** 相对当前位置翻 step 张（负数表示往回翻）：先加 size 再取模，避免负数取模得到负下标 */
    private void goRelative(int step) {
        // 只有一张卡时没有"翻"的意义，直接忽略
        if (panels.size() < 2) {
            return;
        }
        switchTo((current + step + panels.size()) % panels.size(), step > 0 ? 1 : -1);
        if (running) {
            scheduleNext();     // 手动滑过之后重新计时，避免刚看完就被自动切走
        }
    }

    // ---------------- 手势：按下记 x，抬起判方向 ----------------

    /**
     * 触摸拦截：横向拖动超过阈值就由轮播接管，避免卡片自己的点击被误触发
     * （滑动手势变成打开弹窗）。
     *
     * <p><b>为什么是"横向位移大于纵向才拦"：</b>本控件外面还套着首页的竖向滚动区，
     * 用户上下滑是想翻书架，不能被我截走。所以 MOVE 时同时算 dx（横向位移）和 dy（纵向位移），
     * 只有「dx 超过系统 touchSlop」且「dx 明显大于 dy」时返回 true，把后续事件从子 View
     * 手里抢过来自己处理；其余情况返回 false，手势继续下发给下面的卡片，
     * 保证"轻点卡片"仍然能弹出详情。</p>
     *
     * @param ev 触摸事件
     * @return true 表示由本控件接管后续事件，false 表示下发给子 View
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // 按下时先记起点，这里不能拦（拦了子 View 就收不到"点击"了）
                downX = ev.getX();
                downY = ev.getY();
                downAt = SystemClock.uptimeMillis();
                return false;
            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(ev.getX() - downX);     // 横向移动距离
                float dy = Math.abs(ev.getY() - downY);     // 纵向移动距离
                // 两个条件同时成立才拦：够远（超过 touchSlop）、且是横向为主的滑动
                return dx > clickSlopPx && dx > dy;
            default:
                return false;
        }
    }

    /**
     * 触摸事件处理（只有拦截成功、或本控件自己消费 DOWN 时才走到这里）。
     *
     * <p><b>抬手时的判定顺序（重点）：</b>
     * <ol>
     *   <li>先算 dx / dy（相对按下点的位移）和 used（按下到抬起的毫秒数）；</li>
     *   <li>「横向位移 &gt; 36dp 且横向 &gt; 纵向」→ 翻页：dx &lt; 0 看下一张、dx &gt; 0 看上一张；</li>
     *   <li>否则「横纵位移都小于 touchSlop 且用时 &lt; 600ms」→ 判为点击，弹案例详情；</li>
     *   <li>两者都不满足（比如慢慢拖了一下又松开）→ 什么都不做，避免误操作。</li>
     * </ol>
     * 先判滑动再判点击，是因为滑动阈值（36dp）远大于点击阈值（touchSlop），
     * 顺序反过来会让长距离滑动也被当成点击。</p>
     */
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // 记下起点与时间：抬手时要用（有些机型不会走拦截回调，这里必须再记一次）
                downX = ev.getX();
                downY = ev.getY();
                downAt = SystemClock.uptimeMillis();
                return true;
            case MotionEvent.ACTION_UP: {
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;
                long used = SystemClock.uptimeMillis() - downAt;   // 按下到抬起的总时长
                // 情况一：横向拖得够远、且明显是横向动作 → 翻页
                if (Math.abs(dx) > swipeDistancePx && Math.abs(dx) > Math.abs(dy)) {
                    // 左滑看下一张，右滑看上一张
                    goRelative(dx < 0 ? 1 : -1);
                } else if (Math.abs(dx) < clickSlopPx && Math.abs(dy) < clickSlopPx
                        && used < 600) {
                    // 位移很小且很快抬起 → 当成点击，弹出案例详情
                    showDetail(current);
                }
                // 其它情况（慢慢蹭了一下）不处理，避免误翻页也避免误弹窗
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            default:
                // 手势被系统打断（来电、弹出别的窗口）时也要吃掉事件，保持状态一致
                return true;
        }
    }

    // ---------------- 点击：案例详情弹窗 ----------------

    /**
     * 弹出案例详情：标题 / 来源日期 / 案情 / 原文链接 / 配图说明分区排版，底部三个按钮。
     * 弹窗期间先 {@link #stop()} 暂停自动轮播（否则用户正在读卡片却被切走），
     * 关闭时如果原来在播报，再 {@link #start()} 恢复。
     */
    private void showDetail(int index) {
        if (index < 0 || index >= panels.size()) {
            return;
        }
        final Panel p = panels.get(index);
        final boolean wasRunning = running;    // 记住进来前的状态，关闭时按需恢复
        stop();     // 弹窗期间不自动切卡

        // 像素风弹窗：标题 / 来源日期 / 案情 / 原文链接 / 配图说明 分区排版，
        // 不再用系统 AlertDialog 把所有字段挤成一段话
        PixelDialog dlg = PixelDialog.create(activity);
        dlg.title(p.title);

        // 副标题：发布方 · 日期 · 分类，字段为空就不拼，避免出现连续的分隔符
        String metaText = p.publisher;
        if (!TextUtils.isEmpty(p.date)) {
            metaText += "　·　" + p.date;
        }
        if (!TextUtils.isEmpty(p.tag)) {
            metaText += "　·　" + p.tag;
        }
        dlg.meta(metaText);

        // 下面四个字段都是可选的：数据文件里没有就不显示这一栏，而不是显示"暂无"
        if (!TextUtils.isEmpty(p.synopsis)) {
            dlg.field("案情", p.synopsis);
        }
        if (!TextUtils.isEmpty(p.amount)) {
            dlg.field("涉案金额", p.amount);
        }
        if (!TextUtils.isEmpty(p.url)) {
            dlg.field("原文链接", p.url);
        }
        if (!TextUtils.isEmpty(p.credit)) {
            dlg.field("配图", p.credit);
        }

        // 三个按钮：第 0、1 个是主操作（高亮），第 2 个「关闭」是普通样式
        dlg.button("复制链接", true, new PixelDialog.OnAction() {
            @Override
            public void onAction(int i) {
                copyLink(p.url);
            }
        }, 0);
        dlg.button("全部来源", false, new PixelDialog.OnAction() {
            @Override
            public void onAction(int i) {
                // host 由 HomeTabView 注入；没注入时退化成 Toast，不让用户点了没反应
                if (host != null) {
                    host.openAllSources();
                } else {
                    Toast.makeText(activity, "来源入口暂不可用", Toast.LENGTH_SHORT).show();
                }
            }
        }, 1);
        dlg.button("关闭", false, null, 2);

        // 关闭弹窗时恢复自动轮播：只有进来时本来在播才恢复，否则保持暂停
        dlg.setOnDismiss(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                if (wasRunning) {
                    start();
                }
            }
        });
        dlg.show();
    }

    /**
     * 复制原文链接到剪贴板：写剪贴板不需要任何权限。
     *
     * @param url 要复制的链接；为空时弹 Toast 提示"暂无原文链接"
     */
    private void copyLink(String url) {
        if (url == null || url.isEmpty()) {
            Toast.makeText(activity, "这条案例暂无原文链接", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            // 剪贴板服务是系统服务，取不到时（极少数定制系统）直接提示失败
            ClipboardManager cm = (ClipboardManager) activity
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) {
                Toast.makeText(activity, "复制失败", Toast.LENGTH_SHORT).show();
                return;
            }
            // label 只是给系统剪贴板管理界面看的说明文字，不会复制进剪贴板内容
            cm.setPrimaryClip(ClipData.newPlainText("真实案例链接", url));
            Toast.makeText(activity, "链接已复制，可粘贴到浏览器查看", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            // 部分系统在后台限制剪贴板写入，这里兜住异常避免闪退
            AppLog.e("HOME", "复制案例链接失败", e);
            Toast.makeText(activity, "复制失败", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------- 数据模型 ----------------

    /** 一条真实案例（字段与 cases.json 一一对应，绝不自己编数据） */
    private static class Panel {
        /** 报道标题（卡片主标题，单行省略） */
        String title;
        /** 发布方，例如"人民网四川频道" */
        String publisher;
        /** 发布日期（字符串原样展示，不参与排序） */
        String date;
        /** 分类标签（案例 / 反诈口诀角标外的短词） */
        String tag;
        /** 涉案金额（原文口径，字符串原样展示） */
        String amount;
        /** 案情一句话简介（卡片上单行省略，弹窗里显示全） */
        String synopsis;
        /** 原文链接（弹窗里展示，也可一键复制） */
        String url;
        /** 卡片顶部配图（assets 内路径，可留空） */
        String cover;
        /** 配图署名：摄影师 / 图库，点开案例时显示 */
        String credit;
        /** true 表示这是一张"反诈口诀卡"，排版走 buildTipCard，否则走 buildCard */
        boolean isTip;
        /** 口诀卡里的 1-3 条口诀（isTip 为 false 时为空） */
        List<Tips.Tip> tips = new ArrayList<>();
        /** 建好的卡片视图，切换动画直接操作它 */
        View view;
    }
}
