package com.example.antifraud.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.View;

/**
 * 聚光灯遮罩：整屏压暗，中间"挖"一个圆角矩形的高亮洞。
 * 纯 Canvas 绘制，不用任何图片资源。
 */
public class SpotlightView extends View {

    private final RectF hole = new RectF();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float radius;

    public SpotlightView(Context c) {
        super(c);
        paint.setColor(0xCC000000);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Math.max(2f, c.getResources().getDisplayMetrics().density * 2));
        ring.setColor(0xFFFFF6DC);
        radius = getResources().getDisplayMetrics().density * 10;
    }

    public void setHole(RectF rect) {
        hole.set(rect);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (hole.isEmpty()) {
            canvas.drawColor(0xCC000000);
            return;
        }
        int save = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        canvas.drawColor(0xCC000000);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        canvas.drawRoundRect(hole, radius, radius, paint);
        paint.setXfermode(null);
        canvas.restoreToCount(save);
        // 高亮描边
        RectF r = new RectF(hole);
        r.inset(-ring.getStrokeWidth() / 2f, -ring.getStrokeWidth() / 2f);
        canvas.drawRoundRect(r, radius, radius, ring);
    }
}
