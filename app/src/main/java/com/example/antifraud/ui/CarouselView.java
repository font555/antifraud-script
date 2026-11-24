package com.example.antifraud.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
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

    /** 案例数据文件（只读 assets，不联网） */
    private static final String CASES_ASSET = "data/cases.json";
    /** 最多展示几张卡（数据文件里有 6 条真实案例） */
    private static final int MAX_CARDS = 6;
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
            p.view = buildCard(p);
            p.view.setVisibility(i == 0 ? View.VISIBLE : View.INVISIBLE);
            cardLayer.addView(p.view, new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT));
            panels.add(p);
        }
        buildDots(panels.size());
        updateDots(0);
    }

    /** 读取 assets/data/cases.json，最多返回 MAX_CARDS 条 */
    private List<Panel> readPanels() {
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
        return list;
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

        return card;
    }

    /** 像素风小方块背景：直角 + 深色描边，不用圆角 */
    private GradientDrawable pixelBox(int color, boolean border) {
        GradientDrawable gd = new GradientDrawable();
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
                + "\n原文链接：" + p.url);
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
        View view;
    }
}
