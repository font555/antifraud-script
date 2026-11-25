package com.example.antifraud.ui;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.MotionEvent;
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
 * 聚光灯式新手引导。
 *
 * <p>v3.1 重做定位规则，解决四个体验问题：</p>
 * <ol>
 *   <li><b>说明卡不再压住高亮内容</b>：先压缩过高的高亮框（超过屏幕 40% 就截断），
 *       再在剩下的上/下空间里挑一块放不重叠的卡片；都放不下才折中贴边放。</li>
 *   <li><b>切 Tab 不再割裂</b>：先切页并等目标控件量出尺寸，再淡入卡片，卡片出现时页面已经就位。</li>
 *   <li><b>「跳过引导」不压 Tab 栏</b>：按钮上移到 Tab 栏上方（由宿主传入底部安全高度）。</li>
 *   <li>引导结束由宿主切回首页（见 MainActivity）。</li>
 * </ol>
 * 另外保留三重保险：点遮罩任意处继续、固定跳过按钮、超时自动结束。
 */
public class GuideOverlay extends FrameLayout {

    public interface OnFinish {
        void onFinish();
    }

    /** 每一步显示前先做准备（比如先切到对应的 Tab） */
    public interface OnStepPrepare {
        void onPrepare(int index);
    }

    private static final long STEP_TIMEOUT_MS = 2200;
    private static final int MAX_ATTEMPTS = 12;

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
    private int index = 0;
    private int attempts = 0;
    private int bottomInsetDp = 0;
    private boolean finished = false;
    private boolean prepared = false;
    private boolean shownThisStep = false;
    private OnPrepareChain prepareChain;
    private OnStepPrepare preparer;
    private OnFinish callback;

    public GuideOverlay(Context c) {
        super(c);
        setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // 整层吞掉触摸：点空白处既不会推进、也不会误触下面的页面，只能点「下一步」
        setClickable(true);
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        spotlight = new SpotlightView(c);
        // 遮罩不响应点击：只有「下一步」能推进，避免手一偏就跳步
        addView(spotlight, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        tip = buildTip();
        tip.setVisibility(INVISIBLE);
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
        bottomInsetDp = dp;
        LayoutParams lp = (LayoutParams) skipBar.getLayoutParams();
        lp.bottomMargin = Ui.dp(getContext(), 14 + bottomInsetDp);
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
        if (target != null) {
            steps.add(new Step(target, title, desc, holeFraction));
        }
        return this;
    }

    public GuideOverlay setStepPreparer(OnStepPrepare p) {
        preparer = p;
        return this;
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
        }, 200);
    }

    /** 每一步：先准备（切 Tab）→ 等目标控件量出来 → 再定位卡片 */
    private void tryShow() {
        if (finished) {
            return;
        }
        if (!prepared) {
            prepared = true;
            attempts = 0;
            shownThisStep = false;
            tip.setVisibility(INVISIBLE);
            if (preparer != null) {
                preparer.onPrepare(index);
            }
            // 等一脚布局：Tab 切换后目标控件才有正确坐标
            postDelayed(new Runnable() {
                @Override
                public void run() {
                    tryShow();
                }
            }, 340);
            return;
        }
        View target = steps.get(index).target;
        boolean onScreen = target.isShown() && target.getGlobalVisibleRect(new Rect())
                && target.getWindowVisibility() == View.VISIBLE;
        boolean ready = getWidth() > 0 && getHeight() > 0
                && target.getWidth() > 0 && target.getHeight() > 0 && onScreen;
        if (!ready && attempts++ < MAX_ATTEMPTS) {
            post(new Runnable() {
                @Override
                public void run() {
                    tryShow();
                }
            });
            return;
        }
        doShow();
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

    /** 跳过前先确认，避免误触把引导关掉；引导结束后可在「我的 → 新手引导」重看 */
    private void confirmSkip() {
        if (finished) {
            return;
        }
        final Context ctx = getContext();
        new android.app.AlertDialog.Builder(ctx)
                .setTitle("跳过新手引导？")
                .setMessage("跳过之后，可以在「我的 → 新手引导」里再看一遍。")
                .setPositiveButton("跳过", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .setNegativeButton("继续看", null)
                .show();
    }

    private void nextStep() {
        if (finished) {
            return;
        }
        index++;
        if (index >= steps.size()) {
            finish();
            return;
        }
        prepared = false;
        attempts = 0;
        shownThisStep = false;
        tip.setVisibility(INVISIBLE);
        tryShow();
    }

    private void doShow() {
        try {
            Step step = steps.get(index);
            int screenW = getResources().getDisplayMetrics().widthPixels;
            int screenH = getResources().getDisplayMetrics().heightPixels;
            int margin = Ui.dp(getContext(), 14);
            // 底部给「跳过引导」按钮留一行，避免卡片和按钮叠在一起
            int skipRow = Ui.dp(getContext(), 44);
            int bottomLimit = screenH - Ui.dp(getContext(), 14 + bottomInsetDp) - skipRow;
            int topLimit = Ui.dp(getContext(), 14);

            int[] loc = new int[2];
            step.target.getLocationInWindow(loc);
            int[] mine = new int[2];
            getLocationInWindow(mine);
            int left = loc[0] - mine[0];
            int top = loc[1] - mine[1];
            int right = left + step.target.getWidth();
            int bottom = top + step.target.getHeight();
            int pad = Ui.dp(getContext(), 6);
            RectF hole = new RectF(left - pad, top - pad, right + pad, bottom + pad);
            // 夹在屏幕内
            hole.left = Math.max(margin, hole.left);
            hole.top = Math.max(topLimit, hole.top);
            hole.right = Math.min(screenW - margin, hole.right);
            hole.bottom = Math.min(bottomLimit, hole.bottom);
            // 不截断高亮框：完整圈住目标（比如整个书架两排书）；
            // 但剧本列表这种整屏目标可以按比例收窄，避免高亮区一大片空白
            if (step.holeFraction < 1f) {
                float maxH = hole.height() * step.holeFraction;
                if (hole.height() > maxH) {
                    hole.bottom = hole.top + maxH;
                }
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

            int cardMaxW = Math.min(screenW - Ui.dp(getContext(), 56),
                    Ui.dp(getContext(), 330));
            tip.measure(MeasureSpec.makeMeasureSpec(cardMaxW, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int tw = tip.getMeasuredWidth();
            int th = tip.getMeasuredHeight();

            int spaceBelow = bottomLimit - (int) hole.bottom;
            int spaceAbove = (int) hole.top - topLimit;
            int y;
            if (spaceBelow >= th + Ui.dp(getContext(), 12)) {
                y = (int) hole.bottom + Ui.dp(getContext(), 12);
            } else if (spaceAbove >= th + Ui.dp(getContext(), 12)) {
                y = (int) hole.top - Ui.dp(getContext(), 12) - th;
            } else if (spaceBelow >= spaceAbove) {
                // 下方更宽裕：贴底放，尽量少压高亮
                y = Math.min(bottomLimit - th, (int) hole.bottom + Ui.dp(getContext(), 8));
            } else {
                y = Math.max(topLimit, (int) hole.top - th - Ui.dp(getContext(), 8));
            }
            y = Math.max(topLimit, Math.min(y, bottomLimit - th));

            int x = Math.round(hole.left);
            if (x + tw > screenW - margin) {
                x = screenW - margin - tw;
            }
            if (x < margin) {
                x = margin;
            }

            tip.setX(x);
            tip.setY(y);
            tip.setAlpha(0f);
            tip.setVisibility(VISIBLE);
            tip.animate().alpha(1f).setDuration(160).start();
            skipBar.setVisibility(VISIBLE);
            shownThisStep = true;
            AppLog.i("GUIDE", "显示第 " + (index + 1) + "/" + steps.size() + " 步：" + step.title);
        } catch (Exception e) {
            AppLog.e("GUIDE", "引导步骤显示异常，自动结束", e);
            finish();
        }
    }

    public void finish() {
        if (finished) {
            return;
        }
        finished = true;
        if (callback != null) {
            callback.onFinish();
        }
        // 淡出后再移除，不要“啪”一下消失
        final ViewGroup parent = getParent() instanceof ViewGroup ? (ViewGroup) getParent() : null;
        animate().alpha(0f).setDuration(160).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (parent != null) {
                    parent.removeView(GuideOverlay.this);
                }
            }
        }).start();
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
        next.setPadding(Ui.dp(c, 18), Ui.dp(c, 12), Ui.dp(c, 18), Ui.dp(c, 12));
        next.setMinHeight(Ui.dp(c, 44));
        next.setMinWidth(Ui.dp(c, 96));
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

    /** 兼容旧代码占位（未使用） */
    interface OnPrepareChain {
    }
}
