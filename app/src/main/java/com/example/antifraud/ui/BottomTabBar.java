package com.example.antifraud.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.antifraud.R;
import com.example.antifraud.engine.SettingsStore;

/**
 * 底部三 Tab 自绘导航条（像素风，零依赖）。
 * 图标用 Canvas 画：房子 / 列表 / 人形；选中为品牌红，未选中为灰褐色。
 *
 * <p>字号调到「超大」（2.1×）时，导航条只按 1.35 倍封顶放大：
 * 底部栏是常驻控件，长太胖会把整屏高度吃掉，正文反而看不到。</p>
 */
public class BottomTabBar extends LinearLayout {

    /** 标准字号下的条高（dp），与 activity_main.xml 的 58dp 对齐 */
    private static final int BAR_HEIGHT_DP = 58;
    /** 导航条整体放大上限：超过它底部栏就太占地方了 */
    private static final float BAR_MAX_SCALE = 1.35f;

    public interface OnTabSelected {
        void onTabSelected(int index);
    }

    private static final String[] LABELS = {"首页", "剧本", "我的"};
    private final TabIconView[] icons = new TabIconView[3];
    private final TextView[] labels = new TextView[3];
    private int selected = 0;
    private int lastAnimated = -1;
    private OnTabSelected listener;

    public BottomTabBar(Context c) {
        super(c);
        build(c);
    }

    public BottomTabBar(Context c, android.util.AttributeSet attrs) {
        super(c, attrs);
        build(c);
    }

    private void build(Context c) {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundColor(c.getColor(R.color.paper));

        // 整条高度：标准 58dp，随字号放大但封顶 1.35 倍（最高约 78dp）。
        // XML 里已改成 wrap_content，这里的最小高度负责把条撑开，避免 label 被裁。
        int barH = scaleBar(c, Ui.dp(c, BAR_HEIGHT_DP));
        setMinimumHeight(barH);

        // 上下内边距跟着条高一起长一点点，但同样封顶，别让图标被挤扁
        int verticalPad = scaleBar(c, Ui.dp(c, 4));
        setPadding(0, verticalPad, 0, verticalPad);

        // 图标 22dp 是像素图的基准尺寸，只轻微放大（1.35 倍封顶后约 30dp）
        int iconSize = scaleBar(c, Ui.dp(c, 22));
        int labelGap = scaleBar(c, Ui.dp(c, 3));

        for (int i = 0; i < 3; i++) {
            LinearLayout item = new LinearLayout(c);
            item.setOrientation(VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LayoutParams.MATCH_PARENT, 1f));

            TabIconView icon = new TabIconView(c, i);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    iconSize, iconSize);
            icon.setLayoutParams(ilp);
            item.addView(icon);
            icons[i] = icon;

            TextView label = new PTextView(c);
            label.setText(LABELS[i]);
            label.setTextSize(10);
            label.setGravity(Gravity.CENTER);
            label.setIncludeFontPadding(false);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            tlp.topMargin = labelGap;
            label.setLayoutParams(tlp);
            item.addView(label);
            labels[i] = label;

            final int index = i;
            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onTabSelected(index);
                    }
                }
            });
            addView(item);
        }
        apply();
    }

    public void setOnTabSelected(OnTabSelected l) {
        listener = l;
    }

    /**
     * 底部栏专用放大：先按字号放大（SettingsStore.scaleBox 内部上限 1.9），
     * 再整体压到 1.35 倍封顶，避免「超大」档把屏幕吃光。
     */
    private static int scaleBar(Context c, int sizePx) {
        int scaled = SettingsStore.scaleBox(c, sizePx);
        int cap = Math.round(sizePx * BAR_MAX_SCALE);
        return Math.min(scaled, cap);
    }

    public int getSelected() {
        return selected;
    }

    public void setSelected(int index) {
        if (index < 0 || index > 2 || index == selected) {
            apply();
            return;
        }
        selected = index;
        apply();
    }

    private void apply() {
        Context c = getContext();
        for (int i = 0; i < 3; i++) {
            boolean on = i == selected;
            icons[i].setActive(on);
            labels[i].setTextColor(c.getColor(on ? R.color.brand : R.color.ink_hint));
        }
        // 选中的图标弹一下，给点击一个明确反馈
        if (selected != lastAnimated) {
            lastAnimated = selected;
            final TabIconView icon = icons[selected];
            icon.animate().cancel();
            icon.setScaleX(1f);
            icon.setScaleY(1f);
            icon.animate().scaleX(1.2f).scaleY(1.2f).setDuration(110)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            icon.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                        }
                    }).start();
        }
    }

    /** 三个像素图标：0 房子 / 1 列表 / 2 人形 */
    static class TabIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int kind;
        private boolean active;

        TabIconView(Context c, int kind) {
            super(c);
            this.kind = kind;
            paint.setStyle(Paint.Style.FILL);
        }

        void setActive(boolean a) {
            active = a;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            paint.setColor(getResources().getColor(active ? R.color.brand : R.color.ink_hint));
            float u = w / 12f;      // 像素单位
            if (kind == 0) {
                // 房子：屋顶三角 + 主体 + 门
                Path roof = new Path();
                roof.moveTo(w / 2f, u);
                roof.lineTo(w - u, h * 0.45f);
                roof.lineTo(u, h * 0.45f);
                roof.close();
                canvas.drawPath(roof, paint);
                canvas.drawRect(u * 2, h * 0.45f, w - u * 2, h - u * 1.5f, paint);
                paint.setColor(getResources().getColor(R.color.paper));
                canvas.drawRect(w / 2f - u * 1.2f, h * 0.62f, w / 2f + u * 1.2f, h - u * 1.5f, paint);
            } else if (kind == 1) {
                // 列表：三条横杠 + 左侧小方块
                for (int i = 0; i < 3; i++) {
                    float y = u * (2f + i * 2.6f);
                    canvas.drawRect(u * 1.6f, y, u * 3.2f, y + u * 1.2f, paint);
                    canvas.drawRect(u * 4.2f, y, w - u * 1.6f, y + u * 1.2f, paint);
                }
            } else {
                // 人形：头 + 肩膀
                canvas.drawCircle(w / 2f, h * 0.3f, u * 2f, paint);
                Path body = new Path();
                body.moveTo(u * 1.5f, h - u * 1.2f);
                body.quadTo(w / 2f, h * 0.45f, w - u * 1.5f, h - u * 1.2f);
                body.close();
                canvas.drawPath(body, paint);
            }
        }
    }
}
