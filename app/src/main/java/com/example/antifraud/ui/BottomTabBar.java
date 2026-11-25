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

/**
 * 底部三 Tab 自绘导航条（像素风，零依赖）。
 * 图标用 Canvas 画：房子 / 列表 / 人形；选中为品牌红，未选中为灰褐色。
 */
public class BottomTabBar extends LinearLayout {

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
        setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4));

        for (int i = 0; i < 3; i++) {
            LinearLayout item = new LinearLayout(c);
            item.setOrientation(VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LayoutParams.MATCH_PARENT, 1f));

            TabIconView icon = new TabIconView(c, i);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    Ui.dp(c, 22), Ui.dp(c, 22));
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
            tlp.topMargin = Ui.dp(c, 3);
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
