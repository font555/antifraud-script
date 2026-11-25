package com.example.antifraud.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * 像素风书本封面：纯 Canvas 绘制，不依赖任何图片资源。
 *
 * <p>封面结构：深色外框 + 书脊 + 米色页边 + 顶部装饰条 + 中间像素图标 +
 * 竖排书名 + 底部装饰块。图标按骗局类型区分（手柄 / 金币 / 耳机 / 警徽 / 钱袋 / 面具），
 * 书名用竖排小字，比原来单个超大字更耐看、也更像一本书。</p>
 */
public class PixelBookView extends View {

    private static final int BORDER = Color.parseColor("#4A3222");
    private static final int PAGE = Color.parseColor("#F6E7C6");
    private static final int INK_SOFT = Color.parseColor("#2E1F14");
    private static final int CREAM = Color.parseColor("#FFF6DC");

    private int coverColor = Color.parseColor("#E4572E");
    private String title = "反诈";
    private String iconKey = "反诈";
    private final Paint paint = new Paint();   // 像素风：不抗锯齿，硬边

    public PixelBookView(Context c) {
        super(c);
    }

    public PixelBookView(Context c, AttributeSet a) {
        super(c, a);
    }

    /** 兼容旧调用：只给一个字时，书名和图标都用它 */
    public void setBook(int coverColor, String glyph) {
        setBook(coverColor, glyph, glyph);
    }

    /**
     * @param coverColor 封面主色
     * @param title      竖排书名（一般是骗局类型名，如「游戏账号」）
     * @param iconKey    决定画哪个像素图标的字（游 / 单 / 服 / 法 / 贷 / 险）
     */
    public void setBook(int coverColor, String title, String iconKey) {
        this.coverColor = coverColor;
        this.title = (title == null || title.isEmpty()) ? "反诈" : title;
        this.iconKey = (iconKey == null || iconKey.isEmpty()) ? this.title : iconKey;
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
        final float w = getWidth();
        final float h = getHeight();
        final float u = Math.max(2f, w / 14f);          // 像素单位
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(false);

        final float left = u * 0.5f;
        final float top = u * 0.5f;
        final float right = w - u * 1.6f;
        final float bottom = h - u * 1.2f;

        // 1. 右下投影
        paint.setColor(0x33000000);
        canvas.drawRect(u * 0.8f, u * 0.9f, w, h, paint);

        // 2. 深色外框
        paint.setColor(BORDER);
        canvas.drawRect(0, 0, w - u * 0.7f, h - u * 0.7f, paint);

        // 3. 封面主体
        paint.setColor(coverColor);
        canvas.drawRect(left, top, right, bottom, paint);

        // 4. 书脊 + 高光
        paint.setColor(shade(coverColor, 0.70f));
        canvas.drawRect(left, top, u * 2.1f, bottom, paint);
        paint.setColor(0x40FFFFFF);
        canvas.drawRect(u * 1.9f, top + u * 0.4f, u * 2.1f, bottom - u * 0.4f, paint);

        // 5. 页边（右侧米色 + 横线）
        paint.setColor(PAGE);
        canvas.drawRect(w - u * 1.6f, u * 0.7f, w - u * 0.9f, h - u * 1.6f, paint);
        paint.setColor(0x22000000);
        for (int i = 1; i < 6; i++) {
            float y = u * 0.7f + (h - u * 2.3f) * i / 6f;
            canvas.drawRect(w - u * 1.5f, y, w - u * 1.0f, y + Math.max(1f, u * 0.12f), paint);
        }

        // 6. 封面分块：顶部深色带 + 底部色带，让单色封面有层次
        paint.setColor(shade(coverColor, 0.78f));
        canvas.drawRect(left, top, right, top + h * 0.10f, paint);
        paint.setColor(0x66FFFFFF);
        canvas.drawRect(left, top + h * 0.10f, right, top + h * 0.10f + Math.max(1f, u * 0.18f), paint);
        paint.setColor(shade(coverColor, 0.86f));
        canvas.drawRect(left, bottom - h * 0.10f, right, bottom, paint);
        paint.setColor(0x33FFFFFF);
        canvas.drawRect(left, bottom - h * 0.10f, right, bottom - h * 0.10f + Math.max(1f, u * 0.12f), paint);

        // 7. 中间像素图标：先画米色底托（像贴在封面上的标签），再画图标
        float iconCx = (u * 2.1f + right) / 2f;
        float iconCy = h * 0.30f;
        float iconSize = u * 2.4f;
        float tileHalf = iconSize * 0.86f;
        paint.setColor(INK_SOFT);
        canvas.drawRect(iconCx - tileHalf - u * 0.12f, iconCy - tileHalf - u * 0.12f,
                iconCx + tileHalf + u * 0.12f, iconCy + tileHalf + u * 0.12f, paint);
        paint.setColor(CREAM);
        canvas.drawRect(iconCx - tileHalf, iconCy - tileHalf,
                iconCx + tileHalf, iconCy + tileHalf, paint);
        paint.setColor(shade(coverColor, 0.92f));
        canvas.drawRect(iconCx - tileHalf, iconCy + tileHalf - u * 0.22f,
                iconCx + tileHalf, iconCy + tileHalf, paint);
        drawIcon(canvas, iconCx, iconCy - u * 0.12f, iconSize, u);

        // 8. 书名：米色字 + 深色投影，不加衬底，保持封面干净
        drawTitle(canvas, u, left, right, h);

        // 9. 底部装饰：一条细线 + 三个小方块
        paint.setColor(0x66FFFFFF);
        canvas.drawRect(u * 2.6f, h - u * 1.45f, right - u * 0.8f, h - u * 1.45f + Math.max(1f, u * 0.12f), paint);
        paint.setColor(0x99FFFFFF);
        for (int i = 0; i < 3; i++) {
            float x = u * 2.6f + i * u * 1.1f;
            canvas.drawRect(x, h - u * 1.3f, x + u * 0.6f, h - u * 1.05f, paint);
        }
    }

    /** 书名：横排一到两行（4~5 个字的分类拆成两行），字号按行内字数自适应 */
    private void drawTitle(Canvas canvas, float u, float left, float right, float h) {
        String text = title.length() > 6 ? title.substring(0, 6) : title;
        int n = text.length();
        if (n == 0) {
            return;
        }
        String line1;
        String line2 = "";
        if (n <= 3) {
            line1 = text;
        } else {
            int cut = (n + 1) / 2;
            line1 = text.substring(0, cut);
            line2 = text.substring(cut);
        }

        paint.setTypeface(Typefaces.pixel(getContext()));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(false);
        paint.setSubpixelText(false);

        float avail = right - u * 2.6f;
        float size1 = Math.min(u * 2.6f, avail / Math.max(1, line1.length()) * 1.02f);
        float size2 = line2.isEmpty() ? size1
                : Math.min(u * 2.6f, avail / Math.max(1, line2.length()) * 1.02f);
        // 对齐 12px 像素网格，保持硬边
        size1 = Math.max(u * 1.3f, 12f * Math.round(size1 / 12f));
        size2 = Math.max(u * 1.3f, 12f * Math.round(size2 / 12f));

        float lineH1 = size1 * 1.22f;
        float lineH2 = size2 * 1.22f;
        float totalH = line2.isEmpty() ? lineH1 : lineH1 + lineH2;
        float cx = (u * 2.1f + right) / 2f;
        float topY = h * 0.56f - totalH / 2f;

        drawLine(canvas, line1, size1, cx, topY, u);
        if (!line2.isEmpty()) {
            drawLine(canvas, line2, size2, cx, topY + lineH1, u);
        }
    }

    /** 一行书名字：米色字 + 右下 1 格深色描边 */
    private void drawLine(Canvas canvas, String text, float sizePx, float cx, float topY, float u) {
        paint.setTextSize(sizePx);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseY = topY - fm.ascent;
        float shadow = Math.max(1f, u * 0.16f);
        paint.setColor(INK_SOFT);
        canvas.drawText(text, cx + shadow, baseY + shadow, paint);
        paint.setColor(CREAM);
        canvas.drawText(text, cx, baseY, paint);
    }

    // ---------------- 各类骗局的像素小图标 ----------------

    private void drawIcon(Canvas canvas, float cx, float cy, float s, float u) {
        String key = iconKey;
        if (key.startsWith("游")) {
            drawGamepad(canvas, cx, cy, s, u);
        } else if (key.startsWith("单")) {
            drawCoins(canvas, cx, cy, s, u);
        } else if (key.startsWith("服")) {
            drawHeadset(canvas, cx, cy, s, u);
        } else if (key.startsWith("法")) {
            drawBadge(canvas, cx, cy, s, u);
        } else if (key.startsWith("贷")) {
            drawMoneyBag(canvas, cx, cy, s, u);
        } else {
            drawMask(canvas, cx, cy, s, u);
        }
    }

    private void block(Canvas canvas, float x, float y, float w, float h, int color) {
        paint.setColor(color);
        canvas.drawRect(x, y, x + w, y + h, paint);
    }

    /** 手柄 */
    private void drawGamepad(Canvas canvas, float cx, float cy, float s, float u) {
        float w = s * 1.15f;
        float h = s * 0.56f;
        block(canvas, cx - w / 2f, cy - h / 2f, w, h, INK_SOFT);
        block(canvas, cx - w / 2f + u * 0.3f, cy - h / 2f + u * 0.3f, w - u * 0.6f, h - u * 0.6f, CREAM);
        // 左十字键
        block(canvas, cx - w * 0.30f, cy - u * 0.18f, u * 0.9f, u * 0.36f, INK_SOFT);
        block(canvas, cx - w * 0.30f + u * 0.27f, cy - u * 0.45f, u * 0.36f, u * 0.9f, INK_SOFT);
        // 右按键
        block(canvas, cx + w * 0.16f, cy - u * 0.40f, u * 0.34f, u * 0.34f, coverColor);
        block(canvas, cx + w * 0.16f + u * 0.42f, cy - u * 0.05f, u * 0.34f, u * 0.34f, coverColor);
        // 两侧握把
        block(canvas, cx - w / 2f - u * 0.25f, cy - u * 0.05f, u * 0.35f, h * 0.9f, INK_SOFT);
        block(canvas, cx + w / 2f - u * 0.10f, cy - u * 0.05f, u * 0.35f, h * 0.9f, INK_SOFT);
    }

    /** 金币堆（刷单返利） */
    private void drawCoins(Canvas canvas, float cx, float cy, float s, float u) {
        float c = s * 0.42f;
        block(canvas, cx - s * 0.52f, cy + u * 0.25f, c, c * 0.75f, INK_SOFT);
        block(canvas, cx - s * 0.46f, cy + u * 0.30f, c * 0.84f, c * 0.55f, GOLD());
        block(canvas, cx + s * 0.06f, cy + u * 0.25f, c, c * 0.75f, INK_SOFT);
        block(canvas, cx + s * 0.12f, cy + u * 0.30f, c * 0.84f, c * 0.55f, GOLD());
        block(canvas, cx - s * 0.23f, cy - u * 0.42f, c, c * 0.75f, INK_SOFT);
        block(canvas, cx - s * 0.17f, cy - u * 0.37f, c * 0.84f, c * 0.55f, GOLD());
        // 金币上的"¥"横杠
        block(canvas, cx - s * 0.08f, cy - u * 0.14f, u * 0.55f, u * 0.12f, INK_SOFT);
        block(canvas, cx - s * 0.02f, cy - u * 0.28f, u * 0.12f, u * 0.42f, INK_SOFT);
    }

    /** 客服耳机 */
    private void drawHeadset(Canvas canvas, float cx, float cy, float s, float u) {
        float w = s * 0.95f;
        float h = s * 0.85f;
        // 头梁
        block(canvas, cx - w / 2f, cy - h / 2f, w, u * 0.42f, INK_SOFT);
        block(canvas, cx - w / 2f, cy - h / 2f + u * 0.2f, u * 0.36f, h * 0.55f, INK_SOFT);
        block(canvas, cx + w / 2f - u * 0.36f, cy - h / 2f + u * 0.2f, u * 0.36f, h * 0.55f, INK_SOFT);
        // 耳罩
        block(canvas, cx - w / 2f - u * 0.2f, cy - h * 0.12f, u * 0.75f, h * 0.5f, INK_SOFT);
        block(canvas, cx - w / 2f - u * 0.02f, cy - h * 0.04f, u * 0.45f, h * 0.34f, coverColor);
        block(canvas, cx + w / 2f - u * 0.55f, cy - h * 0.12f, u * 0.75f, h * 0.5f, INK_SOFT);
        block(canvas, cx + w / 2f - u * 0.43f, cy - h * 0.04f, u * 0.45f, h * 0.34f, coverColor);
        // 麦克风
        block(canvas, cx - w / 2f - u * 0.1f, cy + h * 0.30f, w * 0.55f, u * 0.18f, INK_SOFT);
    }

    /** 警徽（冒充公检法） */
    private void drawBadge(Canvas canvas, float cx, float cy, float s, float u) {
        float w = s * 0.95f;
        // 上宽下尖的盾牌：用 4 行矩形拼
        block(canvas, cx - w / 2f, cy - s * 0.42f, w, s * 0.30f, INK_SOFT);
        block(canvas, cx - w * 0.40f, cy - s * 0.12f, w * 0.80f, s * 0.26f, INK_SOFT);
        block(canvas, cx - w * 0.26f, cy + s * 0.14f, w * 0.52f, s * 0.22f, INK_SOFT);
        // 内部米色
        block(canvas, cx - w / 2f + u * 0.22f, cy - s * 0.42f + u * 0.22f, w - u * 0.44f, s * 0.24f, CREAM);
        block(canvas, cx - w * 0.40f + u * 0.22f, cy - s * 0.12f + u * 0.22f, w * 0.80f - u * 0.44f, s * 0.20f, CREAM);
        // 中间五角星
        block(canvas, cx - u * 0.14f, cy - s * 0.30f, u * 0.28f, u * 0.28f, coverColor);
        block(canvas, cx - u * 0.38f, cy - s * 0.22f, u * 0.76f, u * 0.20f, coverColor);
    }

    /** 钱袋（校园贷/兼职） */
    private void drawMoneyBag(Canvas canvas, float cx, float cy, float s, float u) {
        float w = s * 0.9f;
        // 袋身：上窄下宽
        block(canvas, cx - w * 0.26f, cy - s * 0.16f, w * 0.52f, s * 0.24f, INK_SOFT);
        block(canvas, cx - w * 0.40f, cy + s * 0.08f, w * 0.80f, s * 0.26f, INK_SOFT);
        block(canvas, cx - w * 0.26f + u * 0.18f, cy - s * 0.16f + u * 0.18f, w * 0.52f - u * 0.36f, s * 0.18f, GOLD());
        block(canvas, cx - w * 0.40f + u * 0.18f, cy + s * 0.08f + u * 0.18f, w * 0.80f - u * 0.36f, s * 0.18f, GOLD());
        // 袋口绳
        block(canvas, cx - w * 0.14f, cy - s * 0.30f, w * 0.28f, u * 0.22f, INK_SOFT);
        block(canvas, cx - w * 0.06f, cy - s * 0.40f, w * 0.12f, u * 0.20f, INK_SOFT);
        // ¥
        block(canvas, cx - u * 0.26f, cy - u * 0.02f, u * 0.52f, u * 0.12f, INK_SOFT);
        block(canvas, cx - u * 0.06f, cy - u * 0.22f, u * 0.12f, u * 0.44f, INK_SOFT);
    }

    /** 面具（AI 换脸） */
    private void drawMask(Canvas canvas, float cx, float cy, float s, float u) {
        float w = s * 0.86f;
        float h = s * 0.94f;
        block(canvas, cx - w / 2f, cy - h / 2f, w, h, INK_SOFT);
        block(canvas, cx - w / 2f + u * 0.22f, cy - h / 2f + u * 0.22f, w - u * 0.44f, h - u * 0.44f, CREAM);
        // 眼睛
        block(canvas, cx - w * 0.30f, cy - h * 0.16f, u * 0.62f, u * 0.34f, INK_SOFT);
        block(canvas, cx + w * 0.12f, cy - h * 0.16f, u * 0.62f, u * 0.34f, INK_SOFT);
        // 嘴
        block(canvas, cx - w * 0.16f, cy + h * 0.20f, w * 0.32f, u * 0.22f, coverColor);
        // 面具绑带
        block(canvas, cx - w / 2f - u * 0.18f, cy - h * 0.10f, u * 0.20f, u * 0.5f, INK_SOFT);
        block(canvas, cx + w / 2f - u * 0.02f, cy - h * 0.10f, u * 0.20f, u * 0.5f, INK_SOFT);
    }

    private int GOLD() {
        return Color.parseColor("#E9B44C");
    }

    private int shade(int color, float factor) {
        int r = (int) (Color.red(color) * factor);
        int g = (int) (Color.green(color) * factor);
        int b = (int) (Color.blue(color) * factor);
        return Color.rgb(r, g, b);
    }
}
