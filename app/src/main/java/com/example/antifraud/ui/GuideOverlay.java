package com.example.antifraud.ui;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.antifraud.R;
import com.example.antifraud.engine.AppLog;
import com.example.antifraud.engine.SettingsStore;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚光灯新手引导（v5.9 重写）。
 *
 * <p><b>为什么重写：</b>旧版有三个结构性问题——</p>
 * <ol>
 *   <li><b>卡顿</b>：旧版用 {@code post(Runnable)} 无延时自旋轮询，最多连转 12 帧；
 *       每一步还要 340ms 固定等待 + 160ms 淡入，五步下来近 3 秒纯等待。
 *       现在改成「布局回调 + 兜底超时」：目标控件一旦量出尺寸立刻显示，正常情况下
 *       一帧内完成，不再有固定等待。</li>
 *   <li><b>离开页面就再也不会出现</b>：旧版在 {@code onPause} 里调 {@code finish()}，
 *       而 finish 会写入「已看过」。用户第一次进入时只要锁屏、切后台、接个电话，
 *       引导就永久消失。现在区分 {@link #finish()}（正常看完才记录）和
 *       {@link #abort()}（被打断，不记录，下次再来）。</li>
 *   <li><b>步骤编号错乱</b>：旧版 {@code addStep(null, ...)} 会静默丢弃该步，
 *       但界面上的「下一步 3/5」仍按 5 步算。现在 null 目标不再丢步，
 *       而是显示为「无高亮的纯说明卡」，编号始终连续正确。</li>
 * </ol>
 *
 * <p>交互约定：整层吞掉触摸（点空白不推进、也不会误触下层页面），只有
 * 「下一步 / 知道了」能前进；「跳过引导」需二次确认。</p>
 */
public class GuideOverlay extends FrameLayout {

    public interface OnFinish {
        /** @param completed true = 看完 / 主动跳过（要记录）；false = 被打断（不记录） */
        void onFinish(boolean completed);
    }

    /** 每一步显示前先做准备（比如先切到对应的 Tab） */
    public interface OnStepPrepare {
        void onPrepare(int index);
    }

    /** 目标控件一直没准备好时的兜底：超过这个时间就跳过该步，避免卡死在光秃秃的遮罩上 */
    private static final long TARGET_WAIT_TIMEOUT_MS = 1500;
    /** 目标控件在屏幕内判定时的最小可见面积比例 */
    private static final float MIN_VISIBLE_RATIO = 0.15f;

    private static class Step {
        final View target;
        final String title;
        final String desc;
        /** 高亮框最多占目标高度的比例（1 = 完整圈住；0.55 = 只圈上面一半多） */
        final float holeFraction;

        Step(View target, String title, String desc, float holeFraction) {
            this.target = target;
            this.title = title;
            this.desc = desc;
            this.holeFraction = holeFraction;
        }
    }

    private final List<Step> steps = new ArrayList<>();
    private final SpotlightView spotlight;
    private final LinearLayout tip;
    private final TextView skipBar;
    private final TextView tipTitle;
    private final TextView tipDesc;
    private final TextView tipNext;

    private int index = 0;
    private int bottomInsetPx = 0;
    private boolean finished = false;
    private boolean started = false;
    private OnStepPrepare preparer;
    private OnFinish callback;

    /** 当前步的等待任务，切步时取消，避免旧任务把新步的卡片位置改坏 */
    private Runnable pendingShow;
    private ViewTreeObserver.OnGlobalLayoutListener layoutListener;

    public GuideOverlay(Context c) {
        super(c);
        setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // 整层吞掉触摸：点空白处既不推进、也不误触下层页面
        setClickable(true);
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        spotlight = new SpotlightView(c);
        addView(spotlight, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 说明卡：固定三块（标题 / 正文 / 按钮行），建一次复用，不再每步重建
        tip = new LinearLayout(c);
        tip.setOrientation(LinearLayout.VERTICAL);
        tip.setBackgroundResource(R.drawable.px_panel);
        int pad = SettingsStore.scaleBox(c, Ui.dp(c, 14));
        tip.setPadding(pad, pad, pad, pad);

        tipTitle = new TextView(c);
        tipTitle.setTextColor(c.getColor(R.color.brand));
        Ui.pixel(tipTitle);
        tipTitle.setTextSize(13);
        tip.addView(tipTitle);

        tipDesc = new TextView(c);
        tipDesc.setTextColor(c.getColor(R.color.ink_secondary));
        Ui.pixel(tipDesc);
        tipDesc.setTextSize(10);
        tipDesc.setLineSpacing(SettingsStore.scaleBox(c, Ui.dp(c, 4)), 1f);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = SettingsStore.scaleBox(c, Ui.dp(c, 8));
        tip.addView(tipDesc, dlp);

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = SettingsStore.scaleBox(c, Ui.dp(c, 12));
        row.setLayoutParams(rlp);

        tipNext = new TextView(c);
        tipNext.setTextSize(11);
        tipNext.setTextColor(c.getColor(R.color.white));
        Ui.pixel(tipNext);
        tipNext.setBackgroundResource(R.drawable.px_button);
        int np = SettingsStore.scaleBox(c, Ui.dp(c, 10));
        tipNext.setPadding(SettingsStore.scaleBox(c, Ui.dp(c, 18)), np,
                SettingsStore.scaleBox(c, Ui.dp(c, 18)), np);
        tipNext.setMinHeight(SettingsStore.scaleBox(c, Ui.dp(c, 44)));
        tipNext.setMinWidth(SettingsStore.scaleBox(c, Ui.dp(c, 96)));
        tipNext.setGravity(Gravity.CENTER);
        tipNext.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                nextStep();
            }
        });
        row.addView(tipNext);
        tip.addView(row);

        tip.setVisibility(INVISIBLE);
        addView(tip, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        skipBar = new TextView(c);
        skipBar.setText("跳过引导");
        skipBar.setTextSize(11);
        Ui.pixel(skipBar);
        skipBar.setTextColor(c.getColor(R.color.ink_hint));
        skipBar.setGravity(Gravity.CENTER);
        int sp = SettingsStore.scaleBox(c, Ui.dp(c, 10));
        skipBar.setPadding(SettingsStore.scaleBox(c, Ui.dp(c, 18)), sp,
                SettingsStore.scaleBox(c, Ui.dp(c, 18)), sp);
        skipBar.setBackgroundResource(R.drawable.px_panel);
        skipBar.setMinimumHeight(SettingsStore.scaleBox(c, Ui.dp(c, 40)));
        skipBar.setClickable(true);
        skipBar.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmSkip();
            }
        });
        LayoutParams slp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        slp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        slp.bottomMargin = Ui.dp(c, 34);
        addView(skipBar, slp);
    }

    /** 宿主传入底部固定栏高度（dp），「跳过引导」会放在它上方，不压住 Tab 栏 */
    public void setBottomInsetDp(int dp) {
        bottomInsetPx = Ui.dp(getContext(), dp);
        LayoutParams lp = (LayoutParams) skipBar.getLayoutParams();
        lp.bottomMargin = Ui.dp(getContext(), 14) + bottomInsetPx;
        skipBar.setLayoutParams(lp);
    }

    public GuideOverlay addStep(View target, String title, String desc) {
        return addStep(target, title, desc, 1f);
    }

    /**
     * 追加一步。
     *
     * @param holeFraction 高亮框最多占目标高度的比例；像剧本列表这种占满整屏的目标，
     *                     传 0.55 就只圈上面一半多，避免高亮区大片空白
     */
    public GuideOverlay addStep(View target, String title, String desc, float holeFraction) {
        // 注意：target 为 null 也照样入列（显示为无高亮的纯说明卡），
        // 否则步骤总数会变，界面上的「3/5」就和实际不符了。
        steps.add(new Step(target, title, desc, holeFraction));
        return this;
    }

    public GuideOverlay setStepPreparer(OnStepPrepare p) {
        preparer = p;
        return this;
    }

    public void start(OnFinish cb) {
        if (started) {
            return;
        }
        started = true;
        callback = cb;
        index = 0;
        if (steps.isEmpty()) {
            finish();
            return;
        }
        // 等遮罩自己挂到窗口并量出尺寸后再显示第一步
        addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b,
                                       int ol, int ot, int or, int ob) {
                if (getWidth() > 0 && getHeight() > 0) {
                    removeOnLayoutChangeListener(this);
                    showStep(0);
                }
            }
        });
        // 极端情况：遮罩没收到布局回调（比如父容器 0 尺寸），兜底也要往下走
        postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!finished && tip.getVisibility() != VISIBLE && index == 0) {
                    showStep(0);
                }
            }
        }, 400);
    }

    /** 显示第 i 步：先准备（切 Tab）→ 等目标就绪 → 布局卡片。全程无固定延时 */
    private void showStep(final int i) {
        if (finished) {
            return;
        }
        index = i;
        cancelPending();

        // 切步时先把卡片藏起来，避免它停留在上一步的位置"闪一下"
        tip.setVisibility(INVISIBLE);

        if (preparer != null) {
            preparer.onPrepare(i);
        }

        final Step step = steps.get(i);
        if (step.target == null) {
            // 没有目标控件：直接显示纯说明卡（居中）
            layoutTip(null);
            markShown(i);
            return;
        }

        // 目标就绪判定：已挂载、有尺寸、在窗口内可见
        if (isTargetReady(step.target)) {
            layoutTip(step.target);
            markShown(i);
            return;
        }

        // 还没就绪：挂一个一次性布局监听 + 兜底超时（不再自旋轮询）
        final View target = step.target;
        layoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (finished) {
                    return;
                }
                if (isTargetReady(target)) {
                    removeLayoutListener();
                    cancelPending();
                    layoutTip(target);
                    markShown(i);
                }
            }
        };
        target.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);

        pendingShow = new Runnable() {
            @Override
            public void run() {
                if (finished) {
                    return;
                }
                removeLayoutListener();
                // 超时还没就绪：有坐标就按当前坐标硬画，彻底没有就跳过这一步
                if (isTargetReady(target)) {
                    layoutTip(target);
                    markShown(i);
                } else {
                    AppLog.i("GUIDE", "第 " + (i + 1) + " 步目标未就绪，跳过");
                    advanceOrFinish();
                }
            }
        };
        postDelayed(pendingShow, TARGET_WAIT_TIMEOUT_MS);
    }

    /** 第一步刚显示时记录一次，用于"是否真的显示出来"的兜底 */
    private void markShown(int i) {
        AppLog.i("GUIDE", "显示第 " + (i + 1) + "/" + steps.size() + " 步："
                + steps.get(i).title);
    }

    private boolean isTargetReady(View target) {
        if (target == null || target.getWindowVisibility() != VISIBLE) {
            return false;
        }
        if (target.getWidth() <= 0 || target.getHeight() <= 0) {
            return false;
        }
        Rect visible = new Rect();
        if (!target.getGlobalVisibleRect(visible)) {
            return false;
        }
        // 大面积被裁掉（比如藏在滚动区外）时不算就绪，避免高亮框画到屏幕外
        float fullArea = target.getWidth() * (float) target.getHeight();
        float visibleArea = visible.width() * (float) visible.height();
        return fullArea > 0 && visibleArea / fullArea >= MIN_VISIBLE_RATIO;
    }

    private void layoutTip(View target) {
        int screenW = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
        int screenH = getHeight() > 0 ? getHeight() : getResources().getDisplayMetrics().heightPixels;
        int margin = SettingsStore.scaleBox(getContext(), Ui.dp(getContext(), 14));
        // 底部要同时容下「跳过引导」按钮和它下面的留白
        int skipH = skipBar.getHeight() > 0
                ? skipBar.getHeight()
                : SettingsStore.scaleBox(getContext(), Ui.dp(getContext(), 40));
        int bottomLimit = screenH - bottomInsetPx - Ui.dp(getContext(), 14) - skipH
                - SettingsStore.scaleBox(getContext(), Ui.dp(getContext(), 10));
        int topLimit = Ui.dp(getContext(), 14);

        RectF hole = null;
        if (target != null) {
            int[] loc = new int[2];
            target.getLocationInWindow(loc);
            int[] mine = new int[2];
            getLocationInWindow(mine);
            int pad = SettingsStore.scaleBox(getContext(), Ui.dp(getContext(), 6));
            float left = loc[0] - mine[0] - pad;
            float top = loc[1] - mine[1] - pad;
            float right = left + target.getWidth() + pad * 2;
            float bottom = top + target.getHeight() + pad * 2;

            // 夹到可用区域内；注意：夹的时候不裁剪高度，只平移
            if (left < margin) {
                left = margin;
            }
            if (right > screenW - margin) {
                right = screenW - margin;
            }
            if (top < topLimit) {
                top = topLimit;
            }
            if (bottom > bottomLimit) {
                bottom = bottomLimit;
            }
            if (right - left < Ui.dp(getContext(), 40)) {
                right = Math.min(screenW - margin, left + Ui.dp(getContext(), 40));
            }
            hole = new RectF(left, top, right, bottom);

            // holeFraction < 1：只圈目标的上半部分（剧本列表这种整屏目标用）
            if (steps.get(index).holeFraction < 1f) {
                float maxH = hole.height() * steps.get(index).holeFraction;
                if (hole.height() > maxH) {
                    hole.bottom = hole.top + maxH;
                }
            }
        }

        // 填内容
        tipTitle.setText(steps.get(index).title);
        tipDesc.setText(steps.get(index).desc);
        tipNext.setText(index == steps.size() - 1
                ? "知道了" : "下一步  " + (index + 1) + "/" + steps.size());

        // 量卡片
        int cardMaxW = screenW - margin * 2;
        tip.measure(MeasureSpec.makeMeasureSpec(cardMaxW, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int tw = tip.getMeasuredWidth();
        int th = tip.getMeasuredHeight();
        // 卡片不能比可用高度还高，否则会压住跳过按钮
        int availH = bottomLimit - topLimit;
        if (th > availH) {
            th = availH;
        }

        int x;
        int y;
        if (hole == null) {
            // 无目标：居中显示
            x = (screenW - tw) / 2;
            y = Math.max(topLimit, (screenH - th) / 2);
        } else {
            int gap = SettingsStore.scaleBox(getContext(), Ui.dp(getContext(), 10));
            int spaceBelow = (int) (bottomLimit - hole.bottom);
            int spaceAbove = (int) (hole.top - topLimit);
            if (spaceBelow >= th + gap) {
                y = (int) hole.bottom + gap;
            } else if (spaceAbove >= th + gap) {
                y = (int) hole.top - gap - th;
            } else if (spaceBelow >= spaceAbove) {
                y = bottomLimit - th;
            } else {
                y = topLimit;
            }
            // 卡片横向对齐高亮框，再夹进屏幕
            x = Math.round(hole.left);
            if (x + tw > screenW - margin) {
                x = screenW - margin - tw;
            }
            if (x < margin) {
                x = margin;
            }
        }

        spotlight.setHole(hole);

        // 直接设置位置与尺寸（避免 setX/setY 引起二次布局抖动）
        LayoutParams lp = (LayoutParams) tip.getLayoutParams();
        lp.width = tw;
        lp.height = th;
        lp.leftMargin = x;
        lp.topMargin = y;
        tip.setLayoutParams(lp);
        tip.setAlpha(0f);
        tip.setVisibility(VISIBLE);
        tip.animate().alpha(1f).setDuration(130).start();
        skipBar.setVisibility(VISIBLE);
    }

    private void nextStep() {
        if (finished) {
            return;
        }
        advanceOrFinish();
    }

    private void advanceOrFinish() {
        if (index + 1 >= steps.size()) {
            finish();
            return;
        }
        showStep(index + 1);
    }

    /** 跳过前先确认，避免误触把引导关掉；引导结束后可在「我的 → 新手引导」重看 */
    private void confirmSkip() {
        if (finished) {
            return;
        }
        final PixelDialog dlg = PixelDialog.create((android.app.Activity) getContext());
        dlg.title("跳过新手引导？");
        dlg.body("跳过之后，可以在「我的 → 新手引导」里再看一遍。");
        dlg.button("跳过", true, new PixelDialog.OnAction() {
            @Override
            public void onAction(int i) {
                finish();
            }
        }, 0);
        dlg.button("继续看", false, null, 1);
        dlg.show();
    }

    /** 正常结束（看完或主动跳过）：记录「已看过」 */
    public void finish() {
        end(true);
    }

    /**
     * 被打断（切后台、页面重建）：不记录「已看过」，下次进入还会再显示。
     * 这是旧版最大的 bug —— 旧版在 onPause 里调 finish()，导致引导一旦被打断就永久消失。
     */
    public void abort() {
        end(false);
    }

    private void end(boolean completed) {
        if (finished) {
            return;
        }
        finished = true;
        cancelPending();
        removeLayoutListener();
        if (callback != null) {
            callback.onFinish(completed);
        }
        final ViewGroup parent = getParent() instanceof ViewGroup ? (ViewGroup) getParent() : null;
        animate().alpha(0f).setDuration(140).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (parent != null) {
                    parent.removeView(GuideOverlay.this);
                }
            }
        }).start();
    }

    private void cancelPending() {
        if (pendingShow != null) {
            removeCallbacks(pendingShow);
            pendingShow = null;
        }
    }

    private void removeLayoutListener() {
        if (layoutListener != null) {
            View target = steps.size() > index ? steps.get(index).target : null;
            if (target != null) {
                ViewTreeObserver vto = target.getViewTreeObserver();
                if (vto.isAlive()) {
                    vto.removeOnGlobalLayoutListener(layoutListener);
                }
            }
            layoutListener = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // 被移出窗口时一定要清理，否则残留的监听会让下次引导卡住
        cancelPending();
        removeLayoutListener();
    }
}
