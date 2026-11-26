package com.example.antifraud.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.antifraud.R;
import com.example.antifraud.engine.SettingsStore;

/**
 * 像素风弹窗：替掉系统 AlertDialog（白底圆角、深色按钮，跟整个 App 的像素风完全不搭）。
 *
 * <p>结构：半透明黑遮罩 + 米色像素卡片（圆角描边）+ 标题 + 内容 + 底部按钮行。
 * 点遮罩可关闭；卡片宽度固定（屏宽减两侧留白），高度自适应。</p>
 */
public final class PixelDialog {

    public interface OnAction {
        void onAction(int index);
    }

    private final Activity activity;
    private final Dialog dialog;
    private final LinearLayout card;
    private final LinearLayout content;
    private final LinearLayout buttonRow;
    private final ScrollView scroll;

    private PixelDialog(Activity activity) {
        this.activity = activity;
        dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);

        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(0x99000000);

        card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.px_panel);

        int side = Ui.dp(activity, 20);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.CENTER;
        clp.leftMargin = side;
        clp.rightMargin = side;
        root.addView(card, clp);

        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);

        // 内容放进 ScrollView：分类有 6 个、案例字段多，2.1 倍字号下必然超出屏幕，
        // 不滚动的话底部按钮会被挤出可见区域。
        scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        card.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        buttonRow = new LinearLayout(activity);
        // 按钮竖着排：横排时 2.1 倍字号下 3 个按钮会把卡片撑破（「关闭」会被挤出屏幕）
        buttonRow.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams brp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        brp.topMargin = Ui.dp(activity, 12);
        card.addView(buttonRow, brp);

        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        card.setClickable(true);

        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    public static PixelDialog create(Activity activity) {
        return new PixelDialog(activity);
    }

    /** 卡片内边距随字号放大 */
    private int pad() {
        return SettingsStore.scaleBox(activity, Ui.dp(activity, 16));
    }

    public PixelDialog title(String text) {
        TextView tv = Ui.text(activity, text, 15, R.color.ink);
        tv.setLineSpacing(Ui.dp(activity, 3), 1f);
        content.addView(tv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        View line = new View(activity);
        line.setBackgroundColor(activity.getColor(R.color.line));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(2, Ui.dp(activity, 2)));
        llp.topMargin = Ui.dp(activity, 8);
        llp.bottomMargin = Ui.dp(activity, 4);
        content.addView(line, llp);
        return this;
    }

    /** 小字灰色说明（来源、日期等） */
    public PixelDialog meta(String text) {
        TextView tv = Ui.text(activity, text, 10, R.color.ink_hint);
        tv.setLineSpacing(Ui.dp(activity, 3), 1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(activity, 6);
        content.addView(tv, lp);
        return this;
    }

    /** 正文（自动换行、行距放宽） */
    public PixelDialog body(String text) {
        TextView tv = Ui.text(activity, text, 12, R.color.ink);
        tv.setLineSpacing(Ui.dp(activity, 5), 1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(activity, 10);
        content.addView(tv, lp);
        return this;
    }

    /** 带标签的字段：label 小字灰、value 正常色 */
    public PixelDialog field(String label, String value) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(activity, 12);
        box.setLayoutParams(blp);

        TextView lab = Ui.text(activity, label, 10, R.color.ink_hint);
        box.addView(lab);
        TextView val = Ui.text(activity, value, 11, R.color.ink_secondary);
        val.setLineSpacing(Ui.dp(activity, 4), 1f);
        val.setPadding(0, Ui.dp(activity, 4), 0, 0);
        box.addView(val);
        content.addView(box);
        return this;
    }

    /** 一条可点的"菜单项"：左侧色条 + 主标题 + 副说明 + 右箭头 */
    public PixelDialog item(int accentColor, String title, String desc,
                            final OnAction action, final int index) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundResource(R.drawable.px_paper);
        int p = SettingsStore.scaleBox(activity, Ui.dp(activity, 12));
        row.setPadding(p, p, p, p);

        View bar = new View(activity);
        bar.setBackgroundColor(accentColor);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                Math.max(3, Ui.dp(activity, 4)), LinearLayout.LayoutParams.MATCH_PARENT);
        barLp.rightMargin = SettingsStore.scaleBox(activity, Ui.dp(activity, 10));
        row.addView(bar, barLp);

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView t = Ui.text(activity, title, 13, R.color.ink);
        texts.addView(t);
        if (desc != null && !desc.isEmpty()) {
            TextView d = Ui.text(activity, desc, 10, R.color.ink_hint);
            d.setPadding(0, SettingsStore.scaleBox(activity, Ui.dp(activity, 3)), 0, 0);
            d.setLineSpacing(Ui.dp(activity, 2), 1f);
            texts.addView(d);
        }
        row.addView(texts);

        TextView arrow = Ui.text(activity, "›", 16, R.color.brand);
        row.addView(arrow);

        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = SettingsStore.scaleBox(activity, Ui.dp(activity, 8));
        row.setLayoutParams(rlp);
        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                if (action != null) {
                    action.onAction(index);
                }
            }
        });
        content.addView(row);
        return this;
    }

    /** 底部按钮。primary=true 用品牌红实底，否则米色描边。竖排、占满整行 */
    public PixelDialog button(String label, boolean primary, final OnAction action, final int index) {
        TextView btn = new TextView(activity);
        Ui.pixel(btn);
        btn.setText(label);
        btn.setTextSize(12);
        btn.setGravity(Gravity.CENTER);
        btn.setMaxLines(1);
        btn.setBackgroundResource(primary ? R.drawable.px_button : R.drawable.px_option);
        btn.setTextColor(activity.getColor(primary ? R.color.white : R.color.ink));
        int p = SettingsStore.scaleBox(activity, Ui.dp(activity, 9));
        btn.setPadding(SettingsStore.scaleBox(activity, Ui.dp(activity, 14)), p,
                SettingsStore.scaleBox(activity, Ui.dp(activity, 14)), p);
        btn.setMinHeight(SettingsStore.scaleBox(activity, Ui.dp(activity, 38)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (buttonRow.getChildCount() > 0) {
            lp.topMargin = SettingsStore.scaleBox(activity, Ui.dp(activity, 6));
        }
        btn.setLayoutParams(lp);
        btn.setClickable(true);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                if (action != null) {
                    action.onAction(index);
                }
            }
        });
        buttonRow.addView(btn);
        return this;
    }

    public PixelDialog cancelable() {
        dialog.setCanceledOnTouchOutside(true);
        return this;
    }

    public void show() {
        int p = pad();
        card.setPadding(p, p, p, p);

        // 限高：卡片最高占屏幕 76%，超出部分让 ScrollView 内部滚动，
        // 这样底部按钮永远固定在可见范围内（2.1 倍字号 + 6 个分类时尤其重要）
        int screenH = activity.getResources().getDisplayMetrics().heightPixels;
        final int maxH = (int) (screenH * 0.76f);
        ViewGroup.LayoutParams lp = card.getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) lp).height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        dialog.show();

        // 布局完成后再量：超出就把 ScrollView 限到剩余高度
        card.post(new Runnable() {
            @Override
            public void run() {
                if (card.getHeight() > maxH) {
                    int fixed = card.getPaddingTop() + card.getPaddingBottom()
                            + buttonRow.getHeight()
                            + ((LinearLayout.LayoutParams) buttonRow.getLayoutParams()).topMargin;
                    int avail = Math.max(Ui.dp(activity, 120), maxH - fixed);
                    ViewGroup.LayoutParams slp = scroll.getLayoutParams();
                    slp.height = avail;
                    scroll.setLayoutParams(slp);
                    card.getLayoutParams().height = maxH;
                    card.requestLayout();
                }
            }
        });
    }

    public void dismiss() {
        if (dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    public boolean isShowing() {
        return dialog.isShowing();
    }

    public Dialog raw() {
        return dialog;
    }

    public void setOnDismiss(Dialog.OnDismissListener l) {
        dialog.setOnDismissListener(l);
    }
}
