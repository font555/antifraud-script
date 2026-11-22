package com.example.antifraud.ui;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.antifraud.R;
import com.example.antifraud.engine.AppLog;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚光灯式新手引导：第一次进书架时触发。
 *
 * <p>三重保险，保证任何情况下都不会把用户卡在遮罩里：</p>
 * <ol>
 *   <li>点遮罩任意位置 = 进入下一步；</li>
 *   <li>底部固定一个「跳过」按钮，和说明卡无关，永远可点；</li>
 *   <li>1.6 秒内没能正常显示某一步（比如目标控件量不到位置），自动结束引导。</li>
 * </ol>
 * 目标控件不在屏幕内时，会先把它所在的 ScrollView 滚到能看见的位置。
 */
public class GuideOverlay extends FrameLayout {

    public interface OnFinish {
        void onFinish();
    }

    private static final long STEP_TIMEOUT_MS = 1600;

    private static class Step {
        final View target;
        final String title;
        final String desc;

        Step(View target, String title, String desc) {
            this.target = target;
            this.title = title;
            this.desc = desc;
        }
    }

    private final List<Step> steps = new ArrayList<>();
    private final SpotlightView spotlight;
    private final LinearLayout tip;
    private final TextView skipBar;
    private int index = 0;
    private boolean finished = false;
    private boolean shownThisStep = false;
    private OnFinish callback;

    public GuideOverlay(Context c) {
        super(c);
        setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        spotlight = new SpotlightView(c);
        spotlight.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                nextStep();          // 保险 1：点遮罩也能继续
            }
        });
        addView(spotlight, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        tip = buildTip();
        tip.setAlpha(1f);           // 不做淡入，避免极端情况下看不见
        addView(tip, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        skipBar = new TextView(c);
        skipBar.setText("跳过引导");
        skipBar.setTextSize(11);
        Ui.pixel(skipBar);
        skipBar.setTextColor(c.getColor(R.color.ink_hint));
        skipBar.setGravity(Gravity.CENTER);
        skipBar.setPadding(Ui.dp(c, 18), Ui.dp(c, 10), Ui.dp(c, 18), Ui.dp(c, 10));
        skipBar.setBackgroundResource(R.drawable.px_panel);
        skipBar.setClickable(true);
        skipBar.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();            // 保险 2：固定位置，永远能点
            }
        });
        LayoutParams slp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        slp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        slp.bottomMargin = Ui.dp(c, 34);
        addView(skipBar, slp);
    }

    public GuideOverlay addStep(View target, String title, String desc) {
        if (target != null) {
            steps.add(new Step(target, title, desc));
        }
        return this;
    }

    private LinearLayout buildTip() {
        Context c = getContext();
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.px_panel);
        int p = Ui.dp(c, 14);
        box.setPadding(p, p, p, p);

        TextView title = new TextView(c);
        title.setTextColor(c.getColor(R.color.brand));
        Ui.pixel(title);
        title.setTextSize(13);
        box.addView(title);

        TextView desc = new TextView(c);
        desc.setTextColor(c.getColor(R.color.ink_secondary));
        Ui.pixel(desc);
        desc.setTextSize(10);
        desc.setLineSpacing(Ui.dp(c, 4), 1f);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = Ui.dp(c, 8);
        desc.setLayoutParams(dlp);
        box.addView(desc);

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = Ui.dp(c, 12);
        row.setLayoutParams(rlp);

        TextView next = new TextView(c);
        next.setTextSize(11);
        next.setTextColor(c.getColor(R.color.white));
        Ui.pixel(next);
        next.setBackgroundResource(R.drawable.px_button);
        next.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 8));
        next.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                nextStep();
            }
        });
        row.addView(next);
        box.addView(row);
        return box;
    }

    public void start(OnFinish cb) {
        callback = cb;
        index = 0;
        if (steps.isEmpty()) {
            finish();
            return;
        }
        postDelayed(new Runnable() {
            @Override
            public void run() {
                tryShow();
            }
        }, 220);
    }

    /** 等遮罩完成测量后再定位；量不到就直接用屏幕尺寸兜底 */
    private void tryShow() {
        if (finished) {
            return;
        }
        if (getWidth() == 0 || getHeight() == 0) {
            post(new Runnable() {
                @Override
                public void run() {
                    tryShow();
                }
            });
            return;
        }
        showStep();
    }

    private void nextStep() {
        if (finished) {
            return;
        }
        index++;
        if (index >= steps.size()) {
            finish();
        } else {
            tryShow();
        }
    }

    private void showStep() {
        if (finished) {
            return;
        }
        final Step step = steps.get(index);
        shownThisStep = false;

        Rect screen = new Rect(0, 0,
                getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels);

        // 目标不在屏幕内：先把它的滚动容器滚过去（最多滚一次）
        if (!isVisibleOnScreen(step.target, screen)) {
            if (scrollTo(step.target)) {
                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        doShow(step, false);
                    }
                }, 320);
            } else {
                doShow(step, false);
            }
        } else {
            doShow(step, true);
        }

        // 保险 3：这一屏没显示出来就自动结束
        postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!finished && !shownThisStep) {
                    AppLog.i("GUIDE", "第 " + (index + 1) + " 步未能显示，自动结束引导");
                    finish();
                }
            }
        }, STEP_TIMEOUT_MS);
    }

    private void doShow(Step step, boolean targetOk) {
        try {
            Rect screen = new Rect(0, 0,
                    getResources().getDisplayMetrics().widthPixels,
                    getResources().getDisplayMetrics().heightPixels);
            int[] loc = new int[2];
            step.target.getLocationInWindow(loc);
            int[] mine = new int[2];
            getLocationInWindow(mine);
            int left = loc[0] - mine[0];
            int top = loc[1] - mine[1];
            int right = left + Math.max(step.target.getWidth(), Ui.dp(getContext(), 24));
            int bottom = top + Math.max(step.target.getHeight(), Ui.dp(getContext(), 24));

            boolean visible = targetOk && isVisibleOnScreen(step.target, screen);
            RectF hole = new RectF(left, top, right, bottom);
            hole.inset(-Ui.dp(getContext(), 6), -Ui.dp(getContext(), 6));
            if (!visible) {
                // 目标实在量不到：把洞放到屏幕中央，至少看起来是"高亮中间"
                int w = (int) (screen.width() * 0.7f);
                int h = Ui.dp(getContext(), 120);
                hole.set((screen.width() - w) / 2f, screen.height() * 0.3f - h / 2f,
                        (screen.width() + w) / 2f, screen.height() * 0.3f + h / 2f);
            }
            spotlight.setHole(hole);

            ((TextView) tip.getChildAt(0)).setText(step.title);
            ((TextView) tip.getChildAt(1)).setText(step.desc);
            View rowView = tip.getChildAt(2);
            if (rowView instanceof ViewGroup && ((ViewGroup) rowView).getChildCount() > 0) {
                View btn = ((ViewGroup) rowView).getChildAt(0);
                if (btn instanceof TextView) {
                    ((TextView) btn).setText(index == steps.size() - 1
                            ? "知道了" : "下一步  " + (index + 1) + "/" + steps.size());
                }
            }

            int margin = Ui.dp(getContext(), 20);
            tip.measure(MeasureSpec.makeMeasureSpec(screen.width() - Ui.dp(getContext(), 48),
                            MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int tw = tip.getMeasuredWidth();
            int th = tip.getMeasuredHeight();
            int x = (int) Math.max(margin, Math.min(hole.left, screen.width() - tw - margin));
            int y;
            if (hole.bottom + Ui.dp(getContext(), 16) + th < screen.height() - Ui.dp(getContext(), 90)) {
                y = (int) hole.bottom + Ui.dp(getContext(), 16);
            } else if (hole.top - Ui.dp(getContext(), 16) - th > margin) {
                y = (int) hole.top - Ui.dp(getContext(), 16) - th;
            } else {
                // 上下都没空间：固定放到屏幕中间偏下，绝不放到屏幕外
                y = (screen.height() - th) / 2;
            }
            y = Math.max(margin, Math.min(y, screen.height() - th - Ui.dp(getContext(), 80)));
            tip.setX(x);
            tip.setY(y);
            tip.setAlpha(1f);
            tip.setVisibility(VISIBLE);
            skipBar.setVisibility(VISIBLE);
            shownThisStep = true;
            AppLog.i("GUIDE", "显示第 " + (index + 1) + "/" + steps.size() + " 步：" + step.title);
        } catch (Exception e) {
            AppLog.e("GUIDE", "引导步骤显示异常，自动结束", e);
            finish();
        }
    }

    private boolean isVisibleOnScreen(View v, Rect screen) {
        int[] loc = new int[2];
        v.getLocationInWindow(loc);
        Rect r = new Rect(loc[0], loc[1], loc[0] + v.getWidth(), loc[1] + v.getHeight());
        return v.getWidth() > 0 && v.getHeight() > 0
                && r.bottom > Ui.dp(getContext(), 8)
                && r.top < screen.height() - Ui.dp(getContext(), 90);
    }

    /** 把目标滚进可视区；返回是否真的滚动了 */
    private boolean scrollTo(View target) {
        ViewParent p = target.getParent();
        while (p != null) {
            if (p instanceof ScrollView) {
                final ScrollView sv = (ScrollView) p;
                int[] loc = new int[2];
                target.getLocationInWindow(loc);
                int[] svLoc = new int[2];
                sv.getLocationInWindow(svLoc);
                int delta = loc[1] - svLoc[1] - Ui.dp(getContext(), 120);
                if (Math.abs(delta) > Ui.dp(getContext(), 12)) {
                    sv.smoothScrollBy(0, delta);
                    return true;
                }
                return false;
            }
            p = p.getParent();
        }
        return false;
    }

    public void finish() {
        if (finished) {
            return;
        }
        finished = true;
        if (callback != null) {
            callback.onFinish();
        }
        if (getParent() instanceof ViewGroup) {
            ((ViewGroup) getParent()).removeView(this);
        }
    }
}
