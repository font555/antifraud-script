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
 *
 * <p>支持「无高亮洞」状态（{@link #setHole(null)}）：整屏压暗、不挖洞，
 * 用于引导里那种没有明确目标的纯说明步骤。</p>
 */
public class SpotlightView extends View {

    private final RectF hole = new RectF();
    private boolean hasHole = false;
    private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float radius;

    private static final int DIM_COLOR = 0xCC000000;

    public SpotlightView(Context c) {
        super(c);
        dim.setColor(DIM_COLOR);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Math.max(2f, c.getResources().getDisplayMetrics().density * 2));
        ring.setColor(0xFFFFF6DC);
        radius = c.getResources().getDisplayMetrics().density * 10;
    }

    /**
     * 设置高亮洞。传 null 表示"没有高亮目标"：整屏压暗、不挖洞。
     * 这样引导里没有目标的说明步骤也能正常显示，不会出现"整屏全黑什么都看不见"。
     */
    public void setHole(RectF rect) {
        if (rect == null || rect.width() <= 0 || rect.height() <= 0) {
            hasHole = false;
            hole.setEmpty();
        } else {
            hasHole = true;
            hole.set(rect);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (!hasHole) {
            canvas.drawRect(0, 0, w, h, dim);
            return;
        }
        int save = canvas.saveLayer(0, 0, w, h, null);
        canvas.drawRect(0, 0, w, h, dim);
        canvas.drawRoundRect(hole, radius, radius, clearPaint);
        canvas.restoreToCount(save);
        // 高亮描边
        RectF r = new RectF(hole);
        r.inset(-ring.getStrokeWidth() / 2f, -ring.getStrokeWidth() / 2f);
        canvas.drawRoundRect(r, radius, radius, ring);
    }
}
