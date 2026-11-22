package com.example.antifraud.ui;

import android.content.Context;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.antifraud.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚光灯式新手引导：第一次进书架时触发。
 * 每一步高亮一个控件（挖洞），旁边弹出说明卡，点「下一步」继续。
 */
public class GuideOverlay extends FrameLayout {

    public interface OnFinish {
        void onFinish();
    }

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
    private int index = 0;
    private OnFinish callback;

    public GuideOverlay(Context c) {
        super(c);
        setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        spotlight = new SpotlightView(c);
        addView(spotlight, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        tip = buildTip();
        addView(tip, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    public GuideOverlay addStep(View target, String title, String desc) {
        if (target != null) {
            steps.add(new Step(target, title, desc));
        }
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
        }, 260);
    }

    private LinearLayout buildTip() {
        Context c = getContext();
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.px_panel);
        int p = Ui.dp(c, 14);
        box.setPadding(p, p, p, p);

        TextView title = new TextView(c);
        title.setId(View.generateViewId());
        title.setTextColor(c.getColor(R.color.brand));
        Ui.pixel(title);
        title.setTextSize(13);
        box.addView(title);

        TextView desc = new TextView(c);
        desc.setId(View.generateViewId());
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

        TextView skip = new TextView(c);
        skip.setText("跳过");
        skip.setTextSize(11);
        skip.setTextColor(c.getColor(R.color.ink_hint));
        Ui.pixel(skip);
        skip.setPadding(0, Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8));
        skip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        row.addView(skip);

        TextView next = new TextView(c);
        next.setTextSize(11);
        next.setTextColor(c.getColor(R.color.white));
        Ui.pixel(next);
        next.setBackgroundResource(R.drawable.px_button);
        next.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 8));
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nextStep();
            }
        });
        row.addView(next);
        box.addView(row);

        // 点击卡片本身也进入下一步
        box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nextStep();
            }
        });
        return box;
    }

    /** 等遮罩真正完成测量后再定位说明卡，否则宽度/高度是 0 会算错位置 */
    private void tryShow() {
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
        index++;
        if (index >= steps.size()) {
            finish();
        } else {
            tryShow();
        }
    }

    private void showStep() {
        final Step step = steps.get(index);
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
        spotlight.setHole(hole);

        ((TextView) tip.getChildAt(0)).setText(step.title);
        ((TextView) tip.getChildAt(1)).setText(step.desc);
        ((TextView) ((LinearLayout) tip.getChildAt(2)).getChildAt(1))
                .setText(index == steps.size() - 1 ? "知道了" : "下一步  " + (index + 1) + "/" + steps.size());

        // 用屏幕尺寸而不是自身尺寸定位：引导层刚挂上时自身可能还没测量完
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        tip.measure(MeasureSpec.makeMeasureSpec(screenW - Ui.dp(getContext(), 48), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int tw = tip.getMeasuredWidth();
        int th = tip.getMeasuredHeight();
        int margin = Ui.dp(getContext(), 20);
        int x = Math.max(margin, Math.min(left, screenW - tw - margin));
        int y;
        if (bottom + Ui.dp(getContext(), 16) + th < screenH - margin) {
            y = bottom + Ui.dp(getContext(), 16);          // 优先放下面
        } else {
            y = Math.max(margin, top - th - Ui.dp(getContext(), 16));  // 放不下就放上面
        }
        tip.setX(x);
        tip.setY(y + Ui.dp(getContext(), 12));
        tip.setAlpha(0f);
        tip.animate().alpha(1f).y(y).setDuration(220).start();
    }

    private void finish() {
        animate().alpha(0f).setDuration(180).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (getParent() instanceof ViewGroup) {
                    ((ViewGroup) getParent()).removeView(GuideOverlay.this);
                }
                if (callback != null) {
                    callback.onFinish();
                }
            }
        }).start();
    }
}
