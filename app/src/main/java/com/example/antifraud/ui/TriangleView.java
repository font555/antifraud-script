package com.example.antifraud.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * 一个像素风的小三角箭头，用于「展开 / 收起」。
 *
 * <p>没有用图片资源，也没有用系统图标：所有绘制都是硬边像素风，
 * 跟整个 App 一致。箭头朝向由 {@link #setPointDown(boolean)} 控制。</p>
 */
public class TriangleView extends View {

    private final Paint paint = new Paint();
    private boolean pointDown = true;
    private int color = 0xFF6B4C8A;

    public TriangleView(Context c) {
        super(c);
        init();
    }

    public TriangleView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(false);
    }

    /** true = 三角形朝下（表示"点击展开"），false = 朝上（表示"点击收起"） */
    public void setPointDown(boolean down) {
        this.pointDown = down;
        invalidate();
    }

    public void setArrowColor(int c) {
        this.color = c;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        if (w <= 0) {
            w = 24;
        }
        if (h <= 0) {
            h = 16;
        }
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;

        // 三角宽高按 1 : 0.8 画，比高矮一点，看起来更像"箭头"
        float halfW = Math.min(w * 0.42f, h * 0.7f);
        float halfH = halfW * 0.72f;

        Path p = new Path();
        if (pointDown) {
            p.moveTo(cx - halfW, cy - halfH * 0.6f);
            p.lineTo(cx + halfW, cy - halfH * 0.6f);
            p.lineTo(cx, cy + halfH * 0.9f);
        } else {
            p.moveTo(cx - halfW, cy + halfH * 0.6f);
            p.lineTo(cx + halfW, cy + halfH * 0.6f);
            p.lineTo(cx, cy - halfH * 0.9f);
        }
        p.close();

        paint.setColor(color);
        canvas.drawPath(p, paint);
    }
}
