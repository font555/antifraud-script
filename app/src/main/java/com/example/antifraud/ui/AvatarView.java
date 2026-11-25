package com.example.antifraud.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * 像素风头像：纯 Canvas 绘制，按索引画不同的"像素小人/小动物"。
 *
 * <p>0 学生 · 1 学霸（眼镜）· 2 猫 · 3 兔 · 4 熊 · 5 星星。
 * 背景色由调用方给，用来区分不同的人；头像本身带深色描边、顶部高光和底部阴影，
 * 比原来"纯色块 + 一个字"更像个头像。</p>
 */
public class AvatarView extends View {

    private static final int INK = Color.parseColor("#3A2A1E");
    private static final int FACE = Color.parseColor("#F6E0BE");
    private static final int FACE_DARK = Color.parseColor("#E2C79E");
    private static final int BLUSH = Color.parseColor("#E4572E");
    private static final int WHITE = Color.parseColor("#FFF6DC");

    private int bgColor = Color.parseColor("#E4572E");
    private int type = 0;
    private final Paint paint = new Paint();

    public AvatarView(Context c) {
        super(c);
    }

    public AvatarView(Context c, AttributeSet a) {
        super(c, a);
    }

    public void setAvatar(int bgColor, int typeIndex) {
        this.bgColor = bgColor;
        this.type = Math.max(0, typeIndex) % 6;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float u = Math.min(w, h) / 12f;      // 像素单位
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(false);

        // 底色 + 深色描边
        paint.setColor(bgColor);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setColor(INK);
        float b = Math.max(2f, u * 0.7f);
        canvas.drawRect(0, 0, w, b, paint);
        canvas.drawRect(0, h - b, w, h, paint);
        canvas.drawRect(0, 0, b, h, paint);
        canvas.drawRect(w - b, 0, w, h, paint);

        // 顶部高光 / 底部阴影，让方块有点体积感
        paint.setColor(0x33FFFFFF);
        canvas.drawRect(b, b, w - b, b + u * 0.5f, paint);
        paint.setColor(0x22000000);
        canvas.drawRect(b, h - b - u * 0.7f, w - b, h - b, paint);

        float cx = w / 2f;
        float cy = h * 0.56f;
        switch (type) {
            case 1:
                drawStudent(canvas, cx, cy, u, true);
                break;
            case 2:
                drawCat(canvas, cx, cy, u);
                break;
            case 3:
                drawRabbit(canvas, cx, cy, u);
                break;
            case 4:
                drawBear(canvas, cx, cy, u);
                break;
            case 5:
                drawStar(canvas, cx, cy, u);
                break;
            default:
                drawStudent(canvas, cx, cy, u, false);
                break;
        }
    }

    private void block(Canvas c, float x, float y, float w, float h, int color) {
        paint.setColor(color);
        c.drawRect(x, y, x + w, y + h, paint);
    }

    /** 学生：圆脸 + 刘海 + 眼睛 + 腮红 */
    private void drawStudent(Canvas canvas, float cx, float cy, float u, boolean glasses) {
        float fw = u * 6.4f;
        float fh = u * 5.6f;
        float left = cx - fw / 2f;
        float top = cy - fh / 2f;
        // 脸（像素圆角：中间大块 + 四角小块）
        block(canvas, left + u, top, fw - u * 2, fh, FACE);
        block(canvas, left, top + u, fw, fh - u * 2, FACE);
        // 头发
        block(canvas, left, top - u * 0.4f, fw, u * 1.5f, INK);
        block(canvas, left + u * 0.6f, top + u * 1.0f, u * 1.1f, u * 0.9f, INK);
        block(canvas, left + fw - u * 1.7f, top + u * 1.0f, u * 1.1f, u * 0.9f, INK);
        // 眼睛
        block(canvas, cx - u * 2.0f, cy - u * 0.4f, u * 1.0f, u * 1.1f, INK);
        block(canvas, cx + u * 1.0f, cy - u * 0.4f, u * 1.0f, u * 1.1f, INK);
        if (glasses) {
            // 学霸：方框眼镜 + 横梁
            block(canvas, cx - u * 2.6f, cy - u * 0.9f, u * 2.2f, u * 0.3f, INK);
            block(canvas, cx - u * 2.6f, cy + u * 0.7f, u * 2.2f, u * 0.3f, INK);
            block(canvas, cx + u * 0.4f, cy - u * 0.9f, u * 2.2f, u * 0.3f, INK);
            block(canvas, cx + u * 0.4f, cy + u * 0.7f, u * 2.2f, u * 0.3f, INK);
            block(canvas, cx - u * 0.4f, cy - u * 0.2f, u * 0.8f, u * 0.3f, INK);
        }
        // 腮红 + 嘴
        block(canvas, cx - u * 2.3f, cy + u * 1.1f, u * 1.0f, u * 0.5f, BLUSH);
        block(canvas, cx + u * 1.3f, cy + u * 1.1f, u * 1.0f, u * 0.5f, BLUSH);
        block(canvas, cx - u * 0.6f, cy + u * 1.3f, u * 1.2f, u * 0.4f, INK);
    }

    /** 猫：尖耳 + 胡须 */
    private void drawCat(Canvas canvas, float cx, float cy, float u) {
        float fw = u * 6.2f;
        float fh = u * 5.4f;
        float left = cx - fw / 2f;
        float top = cy - fh / 2f;
        // 耳朵（阶梯三角）
        block(canvas, left + u * 0.4f, top - u * 1.6f, u * 0.8f, u * 0.6f, INK);
        block(canvas, left + u * 0.2f, top - u * 1.0f, u * 1.4f, u * 1.2f, INK);
        block(canvas, left + fw - u * 1.6f, top - u * 1.6f, u * 0.8f, u * 0.6f, INK);
        block(canvas, left + fw - u * 1.6f, top - u * 1.0f, u * 1.4f, u * 1.2f, INK);
        // 脸
        block(canvas, left + u, top, fw - u * 2, fh, FACE);
        block(canvas, left, top + u, fw, fh - u * 2, FACE);
        // 眼睛 + 鼻子 + 胡须
        block(canvas, cx - u * 1.9f, cy - u * 0.5f, u * 0.9f, u * 0.9f, INK);
        block(canvas, cx + u * 1.0f, cy - u * 0.5f, u * 0.9f, u * 0.9f, INK);
        block(canvas, cx - u * 0.4f, cy + u * 0.4f, u * 0.8f, u * 0.5f, BLUSH);
        block(canvas, left - u * 0.6f, cy + u * 0.5f, u * 1.4f, u * 0.25f, INK);
        block(canvas, left + fw - u * 0.8f, cy + u * 0.5f, u * 1.4f, u * 0.25f, INK);
    }

    /** 兔：长耳 + 粉内耳 */
    private void drawRabbit(Canvas canvas, float cx, float cy, float u) {
        float fw = u * 5.8f;
        float fh = u * 5.2f;
        float left = cx - fw / 2f;
        float top = cy - fh / 2f;
        // 长耳
        block(canvas, left + u * 1.0f, top - u * 2.6f, u * 1.3f, u * 2.8f, FACE);
        block(canvas, left + u * 1.3f, top - u * 2.2f, u * 0.7f, u * 1.8f, BLUSH);
        block(canvas, left + fw - u * 2.3f, top - u * 2.6f, u * 1.3f, u * 2.8f, FACE);
        block(canvas, left + fw - u * 2.0f, top - u * 2.2f, u * 0.7f, u * 1.8f, BLUSH);
        // 脸
        block(canvas, left + u, top, fw - u * 2, fh, FACE);
        block(canvas, left, top + u, fw, fh - u * 2, FACE);
        // 眼睛 + 鼻子 + 腮红
        block(canvas, cx - u * 1.8f, cy - u * 0.4f, u * 0.9f, u * 0.9f, INK);
        block(canvas, cx + u * 0.9f, cy - u * 0.4f, u * 0.9f, u * 0.9f, INK);
        block(canvas, cx - u * 0.4f, cy + u * 0.6f, u * 0.8f, u * 0.5f, BLUSH);
        block(canvas, cx - u * 2.3f, cy + u * 0.9f, u * 0.9f, u * 0.5f, BLUSH);
        block(canvas, cx + u * 1.4f, cy + u * 0.9f, u * 0.9f, u * 0.5f, BLUSH);
    }

    /** 熊：圆耳 + 圆鼻 */
    private void drawBear(Canvas canvas, float cx, float cy, float u) {
        float fw = u * 6.2f;
        float fh = u * 5.4f;
        float left = cx - fw / 2f;
        float top = cy - fh / 2f;
        // 圆耳
        block(canvas, left + u * 0.2f, top - u * 1.2f, u * 1.8f, u * 1.8f, FACE_DARK);
        block(canvas, left + fw - u * 2.0f, top - u * 1.2f, u * 1.8f, u * 1.8f, FACE_DARK);
        // 脸
        block(canvas, left + u, top, fw - u * 2, fh, FACE);
        block(canvas, left, top + u, fw, fh - u * 2, FACE);
        // 眼睛 + 鼻 + 嘴
        block(canvas, cx - u * 1.9f, cy - u * 0.6f, u * 0.9f, u * 0.9f, INK);
        block(canvas, cx + u * 1.0f, cy - u * 0.6f, u * 0.9f, u * 0.9f, INK);
        block(canvas, cx - u * 0.8f, cy + u * 0.4f, u * 1.6f, u * 0.9f, FACE_DARK);
        block(canvas, cx - u * 0.3f, cy + u * 0.5f, u * 0.6f, u * 0.4f, INK);
    }

    /** 星星：像素五角星 */
    private void drawStar(Canvas canvas, float cx, float cy, float u) {
        int star = WHITE;
        block(canvas, cx - u * 0.5f, cy - u * 3.0f, u, u * 6.0f, star);
        block(canvas, cx - u * 3.0f, cy - u * 0.5f, u * 6.0f, u, star);
        block(canvas, cx - u * 2.0f, cy - u * 2.0f, u * 4.0f, u * 4.0f, star);
        block(canvas, cx - u * 1.4f, cy - u * 1.4f, u * 2.8f, u * 2.8f, star);
        // 中心描出一点点底色，显得是中空的星
        block(canvas, cx - u * 0.5f, cy - u * 0.5f, u, u, bgColor);
        // 眼睛（把星星当小脸）
        block(canvas, cx - u * 1.1f, cy - u * 0.9f, u * 0.5f, u * 0.5f, INK);
        block(canvas, cx + u * 0.6f, cy - u * 0.9f, u * 0.5f, u * 0.5f, INK);
    }
}
