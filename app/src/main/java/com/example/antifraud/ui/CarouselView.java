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
 * <p>数据<b>只来自</b> assets/data/cases.json，最多展示 6 张卡；支持：
 * <ul>
 *   <li>左右滑动切换（自己实现 onTouchEvent，按下记 x、抬起判方向，带动画位移）；</li>
 *   <li>每 5 秒自动切下一张（Handler.postDelayed 循环，Activity/Tab 不可见时暂停）；</li>
 *   <li>底部像素指示点（当前品牌红，其它 line 色）；</li>
 *   <li>点卡片弹出 android.app.AlertDialog，可复制原文链接 / 打开全部来源。</li>
 * </ul>
 * 字统统 maxLines(1) + ellipsize，字号开到「超大」（1.55×）也不会把卡片撑破。</p>
 */
public class CarouselView extends FrameLayout {

    /** 配图署名表：covers/caseX.jpg -> "本作品自绘" */
    private static final Map<String, String> CREDITS = new HashMap<>();

    /** 案例数据文件（只读 assets，不联网） */
    private static final String CASES_ASSET = "data/cases.json";
    /** 最多展示几张卡（数据文件里有 6 条真实案例） */
    private static final int MAX_CARDS = 10;
    /** 自动轮播间隔 */
    private static final long AUTO_DELAY_MS = 5000L;
    /** 判定为"滑动"的最小水平距离（dp） */
    private static final int SWIPE_DISTANCE_DP = 36;
    /** 切换动画时长（毫秒） */
    private static final int SWITCH_DURATION_MS = 280;

    private final Activity activity;
    /** 卡片层：所有卡片都铺满这里，靠 translationX 做左右切换 */
    private final FrameLayout cardLayer;
    /** 底部像素指示点 */
    private final LinearLayout dotRow;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Panel> panels = new ArrayList<>();
    private final List<View> dots = new ArrayList<>();

    /** 由 HomeTabView 注入，"全部来源"按钮要用 */
    private TabHost host;
    /** 当前显示第几张（0 开始） */
    private int current;
    /** 自动轮播开关（start/stop 控制） */
    private boolean running;
    /** 正在做切换动画时，忽略新的切换请求 */
    private boolean animating;

    private float downX;
    private float downY;
    private long downAt;
    private final int swipeDistancePx;
    private final int clickSlopPx;

    /** 5 秒自动切下一张：不可见时只续约不切换，避免后台空转出动画 */
    private final Runnable autoNext = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            if (isShown() && getWindowVisibility() == View.VISIBLE
                    && panels.size() > 1 && !animating) {
                switchTo((current + 1) % panels.size(), 1);
            }
            scheduleNext();
        }
    };

    public CarouselView(Activity a) {
        super(a);
        activity = a;
        swipeDistancePx = Ui.dp(a, SWIPE_DISTANCE_DP);
        clickSlopPx = ViewConfiguration.get(a).getScaledTouchSlop();
        setClipChildren(true);

        // 卡片层（在下）：轮播卡片
        cardLayer = new FrameLayout(a);
        addView(cardLayer, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));

        // 指示点（在上）：贴底居中
        dotRow = new LinearLayout(a);
        dotRow.setOrientation(LinearLayout.HORIZONTAL);
        dotRow.setGravity(Gravity.CENTER);
        LayoutParams dotLp = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT);
        dotLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        dotLp.bottomMargin = Ui.dp(a, 7);
        addView(dotRow, dotLp);

        // MainActivity 实现了 TabHost；这里兜底自取一次，HomeTabView 构造后还会显式 setHost
        if (a instanceof TabHost) {
            host = (TabHost) a;
        }
        buildCards();
    }

    /** HomeTabView 把 Host 回调传进来（构造参数按约定只有 Activity） */
    public void setHost(TabHost host) {
        this.host = host;
    }

    // ---------------- 自动轮播的启动 / 停止 ----------------

    /** 由 HomeTabView 在 onAttachedToWindow 调用：开始 5 秒自动轮播 */
    public void start() {
        running = true;
        scheduleNext();
    }

    /** 由 HomeTabView 在 onDetachedFromWindow 调用：停止自动轮播并让卡片归位 */
    public void stop() {
        running = false;
        handler.removeCallbacks(autoNext);
        settleCards();
    }

    private void scheduleNext() {
        handler.removeCallbacks(autoNext);
        if (running) {
            handler.postDelayed(autoNext, AUTO_DELAY_MS);
        }
    }

    /**
     * 被别的 Tab 盖住 / App 退到后台时暂停，回来再续上。
     * 注意 changedView 可能是祖先视图（HomeTabView 被切成 GONE），所以不能只看 this。
     */
    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == View.VISIBLE) {
            scheduleNext();
        } else {
            handler.removeCallbacks(autoNext);
        }
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == View.VISIBLE) {
            scheduleNext();
        } else {
            handler.removeCallbacks(autoNext);
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (running) {
            scheduleNext();
        }
    }

    @Override
    public void onDetachedFromWindow() {
        // 解除窗口时先掐掉定时器，避免泄漏
        handler.removeCallbacks(autoNext);
        super.onDetachedFromWindow();
    }

    // ---------------- 数据读取（只读 cases.json） ----------------

    private void buildCards() {
        cardLayer.removeAllViews();
        panels.clear();
        dotRow.removeAllViews();
        dots.clear();
        current = 0;

        List<Panel> loaded = readPanels();
        if (loaded.isEmpty()) {
            // 数据文件缺失时给一个占位卡，不编造任何案例内容
            loaded.add(fallbackPanel());
        }
        for (int i = 0; i < loaded.size() && i < MAX_CARDS; i++) {
            Panel p = loaded.get(i);
            p.view = p.isTip ? buildTipCard(p) : buildCard(p);
            p.view.setVisibility(i == 0 ? View.VISIBLE : View.INVISIBLE);
            cardLayer.addView(p.view, new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT));
            panels.add(p);
        }
        buildDots(panels.size());
        updateDots(0);
    }

    /**
     * 读取 assets/data/covers.json，把每张配图的摄影师写进 CREDITS。
     * 配图全部是本作品自绘的像素图，没有第三方版权问题
     * 并在案例详情弹窗里显示，避免任何版权争议。
     */
    private void loadCredits() {
        if (!CREDITS.isEmpty()) {
            return;
        }
        InputStream in = null;
        try {
            in = activity.getAssets().open("data/covers.json");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            JSONArray arr = new JSONObject(new String(out.toByteArray(), "UTF-8"))
                    .optJSONArray("covers");
            if (arr == null) {
                return;
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                String file = o.optString("file", "");
                String author = o.optString("author", "");
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

    /** 读取 assets/data/cases.json，最多返回 MAX_CARDS 条 */
    private List<Panel> readPanels() {
        loadCredits();
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
            JSONArray arr = new JSONObject(json).optJSONArray("cases");
            if (arr == null) {
                return list;
            }
            for (int i = 0; i < arr.length() && list.size() < MAX_CARDS; i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                Panel p = new Panel();
                p.title = o.optString("title", "").trim();
                p.publisher = o.optString("publisher", "").trim();
                p.date = o.optString("date", "").trim();
                p.tag = o.optString("tag", "").trim();
                p.amount = o.optString("amount", "").trim();
                p.synopsis = o.optString("synopsis", "").trim();
                p.url = o.optString("url", "").trim();
                if (p.title.isEmpty()) {
                    continue;
                }
                p.cover = "covers/case" + list.size() + ".jpg";
                p.credit = CREDITS.get(p.cover);
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
        return mixWithTips(list);
    }

    /** 把「口诀卡」按「两案一案诀」的节奏混进案例里，轮到口径不变 */
    private List<Panel> mixWithTips(List<Panel> cases) {
        List<Tips.Tip> all = Tips.load(activity);
        if (all.isEmpty()) {
            return cases;
        }
        List<Panel> tipPanels = new ArrayList<>();
        for (int i = 0; i < all.size(); i += 3) {
            Panel p = new Panel();
            p.isTip = true;
            p.cover = "covers/tip" + tipPanels.size() + ".jpg";
            for (int j = i; j < Math.min(i + 3, all.size()); j++) {
                p.tips.add(all.get(j));
            }
            tipPanels.add(p);
        }
        List<Panel> mixed = new ArrayList<>();
        int tipIndex = 0;
        for (int i = 0; i < cases.size(); i++) {
            mixed.add(cases.get(i));
            if (i % 2 == 1 && tipIndex < tipPanels.size()) {
                mixed.add(tipPanels.get(tipIndex++));
            }
        }
        while (tipIndex < tipPanels.size()) {
            mixed.add(tipPanels.get(tipIndex++));
        }
        return mixed;
    }

    /** 口诀卡：角标「必背」，三条口诀各一行，点开看全部 8 句 */
    private LinearLayout buildTipCard(final Panel p) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.px_panel);
        card.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 9),
                Ui.dp(activity, 12), Ui.dp(activity, 12));

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView chip = Ui.text(activity, "反诈口诀", 9, R.color.white);
        chip.setBackground(pixelBox(activity.getColor(R.color.brand), true));
        chip.setPadding(Ui.dp(activity, 6), Ui.dp(activity, 2),
                Ui.dp(activity, 6), Ui.dp(activity, 2));
        top.addView(chip);
        View spacer = new View(activity);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        TextView badge = Ui.text(activity, "必背", 8, R.color.brand_deep);
        badge.setBackground(pixelBox(activity.getColor(R.color.brand_soft), true));
        badge.setPadding(Ui.dp(activity, 5), Ui.dp(activity, 2),
                Ui.dp(activity, 5), Ui.dp(activity, 2));
        top.addView(badge);
        card.addView(top);

        TextView title = Ui.text(activity, "背下 8 个字，记住 8 个动作", 13, R.color.ink);
        title.setPadding(0, Ui.dp(activity, 6), 0, 0);
        card.addView(title);

        for (Tips.Tip t : p.tips) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, Ui.dp(activity, 5), 0, 0);
            TextView key = Ui.text(activity, "「" + t.key + "」" + t.sub, 11, R.color.brand);
            key.setMaxLines(1);
            row.addView(key);
            TextView textView = Ui.text(activity, "  " + t.text, 9, R.color.ink_secondary);
            textView.setMaxLines(1);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(textView, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            card.addView(row);
        }

        TextView more = Ui.text(activity, "点开看全部 8 句解释 →", 9, R.color.brand_deep);
        more.setPadding(0, Ui.dp(activity, 7), 0, 0);
        card.addView(more);

        // 配图放在文字下方（先看口诀，再看画面）
        addCoverBelow(card, p.cover, 900, 300);

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Tips.showAll(activity);
            }
        });
        return card;
    }

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

    private LinearLayout buildCard(final Panel p) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.px_panel);
        card.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 9),
                Ui.dp(activity, 12), Ui.dp(activity, 15));

        // 顶行：左侧 tag 像素 chip，右侧「真实案例」角标（品牌红实心 + 白字）
        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView chip = Ui.text(activity, p.tag.isEmpty() ? "案例" : p.tag, 9,
                R.color.brand_deep);
        chip.setBackground(pixelBox(activity.getColor(R.color.brand_soft), true));
        chip.setPadding(Ui.dp(activity, 6), Ui.dp(activity, 2),
                Ui.dp(activity, 6), Ui.dp(activity, 2));
        chip.setMaxLines(1);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(chip, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 中间留白，把角标顶到右边
        View spacer = new View(activity);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        TextView badge = Ui.text(activity, "真实案例", 8, R.color.white);
        badge.setBackground(pixelBox(activity.getColor(R.color.brand), true));
        badge.setPadding(Ui.dp(activity, 5), Ui.dp(activity, 2),
                Ui.dp(activity, 5), Ui.dp(activity, 2));
        badge.setMaxLines(1);
        top.addView(badge, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        card.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 标题：单行，超长省略（1.55× 字号也不换行撑高）
        TextView title = Ui.text(activity, p.title, 14, R.color.ink);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, Ui.dp(activity, 6), 0, 0);
        card.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 发布方 · 日期 · 金额
        TextView meta = Ui.text(activity, joinDot(p.publisher, p.date, p.amount), 9,
                R.color.ink_hint);
        meta.setMaxLines(1);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        meta.setPadding(0, Ui.dp(activity, 3), 0, 0);
        card.addView(meta, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 案情一句话，单行省略
        TextView synopsis = Ui.text(activity, p.synopsis, 10, R.color.ink_secondary);
        synopsis.setMaxLines(1);
        synopsis.setEllipsize(TextUtils.TruncateAt.END);
        synopsis.setPadding(0, Ui.dp(activity, 3), 0, 0);
        card.addView(synopsis, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 配图放在文字下方：先看标题与来源，再看画面
        addCoverBelow(card, p.cover, 900, 360);

        return card;
    }

    /** 把配图加到卡片文字下方，上方留 8dp、下方留 2dp 呼吸空间 */
    private void addCoverBelow(LinearLayout card, String coverPath, int imgW, int imgH) {
        ImageView cover = coverView(coverPath, (float) imgW / imgH);
        if (cover == null) {
            return;
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, coverHeight(imgW, imgH));
        lp.topMargin = Ui.dp(activity, 8);
        card.addView(coverBox(cover), lp);
    }

    /**
     * 把配图包一层，并在右上角加「示意图」角标：
     * 照片只是氛围示意，不对应具体案件，避免读者误以为就是案发现场照片。
     */
    private FrameLayout coverBox(ImageView cover) {
        FrameLayout box = new FrameLayout(activity);
        box.addView(cover, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        // 点图片：全屏放大查看（点文字区域仍是看案例详情 / 口诀）
        cover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                android.graphics.drawable.Drawable d = ((ImageView) v).getDrawable();
                if (d instanceof android.graphics.drawable.BitmapDrawable) {
                    ImagePreview.show(activity,
                            ((android.graphics.drawable.BitmapDrawable) d).getBitmap());
                }
            }
        });
        return box;
    }

    /** 按卡片实际可用宽度算图片高度，保证整张图等比显示、不被压扁或裁切 */
    private int coverHeight(int imageWidth, int imageHeight) {
        int screenW = activity.getResources().getDisplayMetrics().widthPixels;
        int cardW = screenW - Ui.dp(activity, 32) - Ui.dp(activity, 24);
        return Math.max(1, Math.round(cardW * (float) imageHeight / imageWidth));
    }

    /**
     * 读取 assets 里的卡片配图。
     *
     * <p>配图是可选的：文件不存在或解码失败就返回 null，卡片会自动去掉图片区域，
     * 不会因为少一张图就崩掉。像素图用 CENTER_CROP 裁切填满，保证不留白边。</p>
     *
     * @param path  assets 内相对路径，例如 covers/case0.png
     * @param ratio 图片宽高比（案例图 900:310，口诀图 900:200）；
     *              高度按宽度自动算，保证整张图都显示、不被裁掉
     */
    private ImageView coverView(String path, final float ratio) {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        InputStream in = null;
        try {
            in = activity.getAssets().open(path);
            Bitmap bitmap = BitmapFactory.decodeStream(in);
            if (bitmap == null) {
                return null;
            }
            // 按图片真实比例测量高度：宽度撑满卡片，高度 = 宽度 / 比例，
            // 这样整张照片完整显示，不会像 CENTER_CROP 那样上下被切掉
            ImageView iv = new ImageView(activity) {
                @Override
                protected void onMeasure(int widthSpec, int heightSpec) {
                    int w = MeasureSpec.getSize(widthSpec);
                    int h = Math.round(w / ratio);
                    setMeasuredDimension(w, h);
                }
            };
            iv.setImageBitmap(bitmap);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            iv.setContentDescription("");
            iv.setPadding(0, 0, 0, Ui.dp(activity, 2));
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

    /** 像素风小方块背景：直角 + 深色描边，不用圆角 */
    private GradientDrawable pixelBox(int color, boolean border) {        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(color);
        if (border) {
            gd.setStroke(Ui.dp(activity, 1), activity.getColor(R.color.border_dark));
        }
        return gd;
    }

    /** 用「 · 」把非空字段拼起来 */
    private String joinDot(String a, String b, String c) {
        StringBuilder sb = new StringBuilder();
        String[] parts = {a, b, c};
        for (String s : parts) {
            if (s == null || s.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(s);
        }
        return sb.toString();
    }

    // ---------------- 指示点 ----------------

    private void buildDots(int count) {
        dotRow.removeAllViews();
        dots.clear();
        for (int i = 0; i < count; i++) {
            View dot = new View(activity);
            int size = Ui.dp(activity, 6);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.leftMargin = Ui.dp(activity, 3);
            lp.rightMargin = Ui.dp(activity, 3);
            dot.setLayoutParams(lp);
            dotRow.addView(dot);
            dots.add(dot);
        }
        updateDots(current);
    }

    private void updateDots(int index) {
        for (int i = 0; i < dots.size(); i++) {
            int color = activity.getColor(i == index ? R.color.brand : R.color.line);
            dots.get(i).setBackground(pixelBox(color, false));
        }
    }

    // ---------------- 切换动画 ----------------

    private void switchTo(int target, int dir) {
        if (target == current || target < 0 || target >= panels.size() || animating) {
            return;
        }
        final View out = panels.get(current).view;
        final View in = panels.get(target).view;
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
        final int width = getWidth();
        in.animate().cancel();
        out.animate().cancel();
        animating = true;
        current = target;
        updateDots(current);

        // 新卡从一侧滑入
        in.setAlpha(1f);
        in.setTranslationX(dir > 0 ? width : -width);
        in.setVisibility(View.VISIBLE);
        in.animate()
                .translationX(0f)
                .setDuration(SWITCH_DURATION_MS)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
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
                        out.setVisibility(View.INVISIBLE);
                        out.setTranslationX(0f);
                    }
                })
                .start();
    }

    /** 停播时把所有卡片复位，避免停在中途位置 */
    private void settleCards() {
        for (int i = 0; i < panels.size(); i++) {
            View v = panels.get(i).view;
            if (v == null) {
                continue;
            }
            v.animate().cancel();
            v.setTranslationX(0f);
            v.setVisibility(i == current ? View.VISIBLE : View.INVISIBLE);
        }
        animating = false;
    }

    private void goRelative(int step) {
        if (panels.size() < 2) {
            return;
        }
        switchTo((current + step + panels.size()) % panels.size(), step > 0 ? 1 : -1);
        if (running) {
            scheduleNext();     // 手动滑过之后重新计时，避免刚看完就被自动切走
        }
    }

    // ---------------- 手势：按下记 x，抬起判方向 ----------------

    /** 横向拖动超过阈值就由轮播接管，避免卡片自己的点击被误触发（滑动手势变成打开弹窗） */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                downAt = SystemClock.uptimeMillis();
                return false;
            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(ev.getX() - downX);
                float dy = Math.abs(ev.getY() - downY);
                return dx > clickSlopPx && dx > dy;
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX();
                downY = ev.getY();
                downAt = SystemClock.uptimeMillis();
                return true;
            case MotionEvent.ACTION_UP: {
                float dx = ev.getX() - downX;
                float dy = ev.getY() - downY;
                long used = SystemClock.uptimeMillis() - downAt;
                if (Math.abs(dx) > swipeDistancePx && Math.abs(dx) > Math.abs(dy)) {
                    // 左滑看下一张，右滑看上一张
                    goRelative(dx < 0 ? 1 : -1);
                } else if (Math.abs(dx) < clickSlopPx && Math.abs(dy) < clickSlopPx
                        && used < 600) {
                    // 位移很小且很快抬起 → 当成点击，弹出案例详情
                    showDetail(current);
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            default:
                return true;
        }
    }

    // ---------------- 点击：案例详情弹窗 ----------------

    private void showDetail(int index) {
        if (index < 0 || index >= panels.size()) {
            return;
        }
        final Panel p = panels.get(index);
        final boolean wasRunning = running;
        stop();     // 弹窗期间不自动切卡

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(p.title);
        builder.setMessage(p.publisher + "\n" + p.date
                + "\n案情：" + p.synopsis
                + "\n原文链接：" + p.url
                + (TextUtils.isEmpty(p.credit) ? "" : "\n配图：" + p.credit));
        builder.setPositiveButton("复制链接", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                copyLink(p.url);
            }
        });
        builder.setNeutralButton("全部来源", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (host != null) {
                    host.openAllSources();
                } else {
                    Toast.makeText(activity, "来源入口暂不可用", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("关闭", null);
        AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                if (wasRunning) {
                    start();
                }
            }
        });
        dialog.show();
    }

    /** 复制原文链接到剪贴板：写剪贴板不需要任何权限 */
    private void copyLink(String url) {
        if (url == null || url.isEmpty()) {
            Toast.makeText(activity, "这条案例暂无原文链接", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            ClipboardManager cm = (ClipboardManager) activity
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) {
                Toast.makeText(activity, "复制失败", Toast.LENGTH_SHORT).show();
                return;
            }
            cm.setPrimaryClip(ClipData.newPlainText("真实案例链接", url));
            Toast.makeText(activity, "链接已复制，可粘贴到浏览器查看", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            AppLog.e("HOME", "复制案例链接失败", e);
            Toast.makeText(activity, "复制失败", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------- 数据模型 ----------------

    /** 一条真实案例（字段与 cases.json 一一对应，绝不自己编数据） */
    private static class Panel {
        String title;
        String publisher;
        String date;
        String tag;
        String amount;
        String synopsis;
        String url;
        /** 卡片顶部配图（assets 内路径，可留空） */
        String cover;
        /** 配图署名：摄影师 / 图库，点开案例时显示 */
        String credit;
        boolean isTip;
        List<Tips.Tip> tips = new ArrayList<>();
        View view;
    }
}
