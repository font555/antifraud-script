package com.example.antifraud.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * 像素风书本封面：纯 Canvas 绘制（深色描边 + 书脊 + 页边 + 中间大字），
 * 不依赖任何图片资源，缩放后依然是硬边像素感。
 */
public class PixelBookView extends View {

    private static final int BORDER = Color.parseColor("#4A3222");
    private static final int PAGE = Color.parseColor("#F6E7C6");

    private int coverColor = Color.parseColor("#E4572E");
    private String glyph = "书";
    private final Paint paint = new Paint();   // 像素风：不抗锯齿，硬边

    public PixelBookView(Context c) {
        super(c);
    }

    public PixelBookView(Context c, AttributeSet a) {
        super(c, a);
    }

    public void setBook(int coverColor, String glyph) {
        this.coverColor = coverColor;
        this.glyph = glyph == null || glyph.isEmpty() ? "书" : glyph.substring(0, 1);
        invalidate();
    }

    /** 书本比例固定为 宽:高 = 1 : 1.45，不同手机上都不会变成方块 */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        if (w <= 0) {
            w = 120;
        }
        int h = (int) (w * 1.45f);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float u = Math.max(2f, w / 14f);          // 像素单位
        paint.setStyle(Paint.Style.FILL);

        // 1. 右下投影
        paint.setColor(0x33000000);
        canvas.drawRect(u * 0.8f, u * 0.9f, w, h, paint);

        // 2. 深色外框
        paint.setColor(BORDER);
        canvas.drawRect(0, 0, w - u * 0.7f, h - u * 0.7f, paint);

        // 3. 封面主体
        paint.setColor(coverColor);
        canvas.drawRect(u * 0.5f, u * 0.5f, w - u * 1.6f, h - u * 1.2f, paint);

        // 4. 书脊（左侧深色竖条 + 高光）
        paint.setColor(shade(coverColor, 0.72f));
        canvas.drawRect(u * 0.5f, u * 0.5f, u * 2.1f, h - u * 1.2f, paint);
        paint.setColor(0x40FFFFFF);
        canvas.drawRect(u * 1.9f, u * 0.9f, u * 2.1f, h - u * 1.6f, paint);

        // 5. 页边（右侧米色 + 横线）
        paint.setColor(PAGE);
        canvas.drawRect(w - u * 1.6f, u * 0.7f, w - u * 0.9f, h - u * 1.6f, paint);
        paint.setColor(0x22000000);
        for (int i = 1; i < 6; i++) {
            float y = u * 0.7f + (h - u * 2.3f) * i / 6f;
            canvas.drawRect(w - u * 1.5f, y, w - u * 1.0f, y + Math.max(1f, u * 0.12f), paint);
        }

        // 6. 顶部装饰横条
        paint.setColor(0x55FFFFFF);
        canvas.drawRect(u * 2.4f, u * 1.1f, w - u * 2.2f, u * 1.1f + Math.max(1f, u * 0.25f), paint);

        // 7. 中间大字：字号对齐 12px 网格 + 深色描边，保证远看也认得出
        float target = Math.max(u * 4.6f, h * 0.34f);
        float sizePx = 12f * Math.max(3f, Math.round(target / 12f));
        paint.setTypeface(Typefaces.pixel(getContext()));
        paint.setTextSize(sizePx);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
        paint.setAntiAlias(false);
        paint.setSubpixelText(false);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float cx = (u * 2.1f + (w - u * 1.6f)) / 2f;
        float cy = h * 0.52f - (fm.ascent + fm.descent) / 2f;

        float o = Math.max(2f, u * 0.3f);
        paint.setColor(0xFF2E1F14);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                canvas.drawText(glyph, cx + dx * o, cy + dy * o, paint);
            }
        }
        paint.setColor(0xFFFFF6DC);
        canvas.drawText(glyph, cx, cy, paint);

        // 8. 底部装饰块
        paint.setColor(0x66FFFFFF);
        for (int i = 0; i < 3; i++) {
            float x = u * 2.6f + i * u * 1.1f;
            canvas.drawRect(x, h - u * 2.2f, x + u * 0.7f, h - u * 1.5f, paint);
        }
    }

    private int shade(int color, float factor) {
        int r = (int) (Color.red(color) * factor);
        int g = (int) (Color.green(color) * factor);
        int b = (int) (Color.blue(color) * factor);
        return Color.rgb(r, g, b);
    }
}
