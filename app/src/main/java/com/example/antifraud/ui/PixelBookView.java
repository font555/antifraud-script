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
 *
 * <p><b>谁在用：</b>{@code HomeTabView} 的书架区把每个剧本渲染成一本书，
 * 三列网格里每个格子就是一个本控件；换封面主色即可区分不同骗局分类。</p>
 *
 * <p><b>坐标系约定：</b>整个封面只用「像素单位」u 来量。u = 屏宽 / 14，
 * 下限 2px。所有矩形坐标都写成 u 的倍数，好处是同一张封面在任何手机上
 * 都按等比例缩放，不会出现「小屏上边框糊成一团、大屏上又细得像头发丝」。
 * 线条宽度普遍取 Math.max(1f, u * 0.1 左右)，保证再小的屏幕也至少占 1 个像素。</p>
 *
 * <p><b>绘制顺序（从后往前叠，后画的盖住先画的）：</b>
 * 1 右下投影 → 2 深色外框 → 3 封面主色块 → 4 书脊 + 书脊高光 →
 * 5 右侧页边 + 页边横线 → 6 顶部深色带 / 底部色带 → 7 中间图标底托 + 图标 →
 * 8 书名 → 9 底部装饰细线 + 三个小方块。顺序不能调，否则书脊会跑到封面外面。</p>
 */
public class PixelBookView extends View {

    /** 书本外框色：深咖啡色，比正文的墨色更暖一点，像旧书的硬壳 */
    private static final int BORDER = Color.parseColor("#4A3222");
    /** 页边色：米黄色，用来画右侧露出来的书页 */
    private static final int PAGE = Color.parseColor("#F6E7C6");
    /** 深墨色：图标描边、书名字影、装饰块的底色 */
    private static final int INK_SOFT = Color.parseColor("#2E1F14");
    /** 奶白色：图标底托、书名正字，是封面里最亮的一块，用来「跳出来」 */
    private static final int CREAM = Color.parseColor("#FFF6DC");
    /** 金色：金币图标和钱袋用，象征钱 */
    private static final int GOLD = Color.parseColor("#E9B44C");
    /** 暗金色：预留的深一档金色（当前未在绘制里使用，保留给以后的金色描边） */
    private static final int GOLD_DEEP = Color.parseColor("#B8842A");

    /** 封面主色：由剧本分类决定，默认品牌橙红 */
    private int coverColor = Color.parseColor("#E4572E");
    /** 书名：显示在封面下半部分，最多取前 6 个字，超过就截断 */
    private String title = "反诈";
    /** 图标关键字：决定画哪一个小图标，只看第一个字（游/单/服/法/贷/其它） */
    private String iconKey = "反诈";
    private final Paint paint = new Paint();   // 像素风：不抗锯齿，硬边

    /** 代码里 new 出来的构造器（首页书架给每一本书都用这个） */
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
        // 书名传空就退回默认的「反诈」，避免书架上出现一本没字的空书
        this.title = (title == null || title.isEmpty()) ? "反诈" : title;
        // 图标关键字传空就复用书名，至少能画出一个图标
        this.iconKey = (iconKey == null || iconKey.isEmpty()) ? this.title : iconKey;
        invalidate();   // 属性变了要重画，不然屏幕上还是旧封面
    }

    /** 书本比例固定为 宽:高 = 1 : 1.45，不同手机上都不会变成方块 */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        // 宽度还没量出来（父容器还没定）时先按 120 兜底，避免算出 0 高
        if (w <= 0) {
            w = 120;
        }
        // 高度完全由宽度推出来：书架三列布局只要给宽度，高度自己跟着走
        int h = (int) (w * 1.45f);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final float w = getWidth();
        final float h = getHeight();
        // 像素单位 u：整张图所有坐标都是 u 的倍数，屏幕变宽 u 跟着变大，图整体等比放大
        final float u = Math.max(2f, w / 14f);          // 像素单位
        paint.setStyle(Paint.Style.FILL);              // 全部用实心填充，不描边
        paint.setAntiAlias(false);                     // 关掉抗锯齿才有像素风的硬边

        // 封面主体（不含外框）的四个边界：左上各留半格，右下多留出投影和页边
        final float left = u * 0.5f;
        final float top = u * 0.5f;
        final float right = w - u * 1.6f;
        final float bottom = h - u * 1.2f;

        // 1. 右下投影：半透明黑，从封面右下往画面右下斜铺出去，让书「立」在书架上
        paint.setColor(0x33000000);
        canvas.drawRect(u * 0.8f, u * 0.9f, w, h, paint);

        // 2. 深色外框：铺满整块（比封面大一圈），因为后面封面会盖住中间，只剩一圈边
        //    所以最终看到的是左上那圈深咖啡色的硬壳，右下被投影盖住
        paint.setColor(BORDER);
        canvas.drawRect(0, 0, w - u * 0.7f, h - u * 0.7f, paint);

        // 3. 封面主体：剧本分类色的大色块，是整本书最显眼的部分
        paint.setColor(coverColor);
        canvas.drawRect(left, top, right, bottom, paint);

        // 4. 书脊 + 高光：
        //    先把左侧一条竖条压深到原色的 70%，做出「书脊」这一侧；
        paint.setColor(shade(coverColor, 0.70f));
        canvas.drawRect(left, top, u * 2.1f, bottom, paint);
        //    再在书脊右缘画一条 25% 白的细高光，让书脊有转折、不糊成一块
        paint.setColor(0x40FFFFFF);
        canvas.drawRect(u * 1.9f, top + u * 0.4f, u * 2.1f, bottom - u * 0.4f, paint);

        // 5. 页边（右侧米色 + 横线）：模拟合上的书右侧露出的那叠纸
        paint.setColor(PAGE);
        canvas.drawRect(w - u * 1.6f, u * 0.7f, w - u * 0.9f, h - u * 1.6f, paint);
        //    页边里等距画 5 条极淡的横线（13% 黑），当成一页一页的纸缝
        paint.setColor(0x22000000);
        for (int i = 1; i < 6; i++) {
            float y = u * 0.7f + (h - u * 2.3f) * i / 6f;
            canvas.drawRect(w - u * 1.5f, y, w - u * 1.0f, y + Math.max(1f, u * 0.12f), paint);
        }

        // 6. 封面分块：顶部深色带 + 底部色带，让单色封面有层次
        //    顶带：封面顶部 10% 高度压深到 78%，看起来像书头上的一道压槽
        paint.setColor(shade(coverColor, 0.78f));
        canvas.drawRect(left, top, right, top + h * 0.10f, paint);
        //    顶带下沿一条 40% 白的亮线，把深色顶带和下面的封面隔开
        paint.setColor(0x66FFFFFF);
        canvas.drawRect(left, top + h * 0.10f, right, top + h * 0.10f + Math.max(1f, u * 0.18f), paint);
        //    底带：封面底部 10% 高度压深到 86%，像书根处的包边
        paint.setColor(shade(coverColor, 0.86f));
        canvas.drawRect(left, bottom - h * 0.10f, right, bottom, paint);
        //    底带上沿一条 20% 白的细亮线，勾出底带的上边界
        paint.setColor(0x33FFFFFF);
        canvas.drawRect(left, bottom - h * 0.10f, right, bottom - h * 0.10f + Math.max(1f, u * 0.12f), paint);

        // 7. 中间像素图标：先画米色底托（像贴在封面上的标签），再画图标
        //    横向中心取「书脊右缘」到「封面右缘」之间，避免图标压在书脊上
        float iconCx = (u * 2.1f + right) / 2f;
        float iconCy = h * 0.30f;            // 纵向放在封面上部约 30% 处，给下面的书名让位
        float iconSize = u * 2.4f;           // 图标本身的大小
        float tileHalf = iconSize * 0.86f;   // 底托半边长（比图标略大，四周留一点边）
        //    底托外圈：深墨色，比米色底托再大 0.12u，形成一圈描边
        paint.setColor(INK_SOFT);
        canvas.drawRect(iconCx - tileHalf - u * 0.12f, iconCy - tileHalf - u * 0.12f,
                iconCx + tileHalf + u * 0.12f, iconCy + tileHalf + u * 0.12f, paint);
        //    底托主体：奶白色方块，是一张贴在封面上的「标签纸」
        paint.setColor(CREAM);
        canvas.drawRect(iconCx - tileHalf, iconCy - tileHalf,
                iconCx + tileHalf, iconCy + tileHalf, paint);
        //    底托下缘：封面主色的 92% 淡版本，一条厚度 0.22u 的暗边，做出纸片厚度
        paint.setColor(shade(coverColor, 0.92f));
        canvas.drawRect(iconCx - tileHalf, iconCy + tileHalf - u * 0.22f,
                iconCx + tileHalf, iconCy + tileHalf, paint);
        //    真正的小图标：整体上移 0.12u，视觉上居中于底托
        drawIcon(canvas, iconCx, iconCy - u * 0.12f, iconSize, u);

        // 8. 书名：米色字 + 深色投影，不加衬底，保持封面干净
        //    （下半部分交给 drawTitle 自己排版：1~2 行、字号自适应）
        drawTitle(canvas, u, left, right, h);

        // 9. 底部装饰：一条细线 + 三个小方块
        //    细线：从书脊右侧起、到封面右边少 0.8u 处止，40% 白，像书脚的装饰线
        paint.setColor(0x66FFFFFF);
        canvas.drawRect(u * 2.6f, h - u * 1.45f, right - u * 0.8f, h - u * 1.45f + Math.max(1f, u * 0.12f), paint);
        //    三个小方块：60% 白，等距排在细线下方，最后一个细节装饰
        paint.setColor(0x99FFFFFF);
        for (int i = 0; i < 3; i++) {
            float x = u * 2.6f + i * u * 1.1f;
            canvas.drawRect(x, h - u * 1.3f, x + u * 0.6f, h - u * 1.05f, paint);
        }
    }

    /**
     * 画书名：横排一到两行（4~5 个字的分类拆成两行），字号按行内字数自适应。
     *
     * <p>位置在封面的下半部分（纵向以 56% 高度为中心），横向居中于「书脊右缘到封面右缘」
     * 这一段可用宽度里，保证字不会压到书脊和右侧页边。</p>
     */
    private void drawTitle(Canvas canvas, float u, float left, float right, float h) {
        // 书名最多取前 6 个字：书封面积有限，字再多只会被缩到看不清
        String text = title.length() > 6 ? title.substring(0, 6) : title;
        int n = text.length();
        if (n == 0) {
            return;   // 空书名直接不画（setBook 一般已经兜底成「反诈」，这里只是保险）
        }
        String line1;
        String line2 = "";
        if (n <= 3) {
            // 1~3 个字一行就够，不拆
            line1 = text;
        } else {
            // 4 个字以上拆两行：先算前半段字数（向上取整，前面那行略长更稳）
            int cut = (n + 1) / 2;
            line1 = text.substring(0, cut);
            line2 = text.substring(cut);
        }

        paint.setTypeface(Typefaces.pixel(getContext()));   // 统一用像素字体
        paint.setTextAlign(Paint.Align.CENTER);             // 以 cx 为水平中心对齐
        paint.setFakeBoldText(false);                       // 像素字体不要假粗，会糊边
        paint.setSubpixelText(false);                       // 关亚像素定位，保持格点对齐

        // 可用宽度 = 封面右缘 - 书脊右缘再往右挪一点（2.6u 处起排字）
        float avail = right - u * 2.6f;
        // 第一行字号：不超过 2.6u 上限，也不超过「可用宽度 / 字数」再乘 1.02 的容差
        float size1 = Math.min(u * 2.6f, avail / Math.max(1, line1.length()) * 1.02f);
        // 第二行同理，单独算自己的字号（两行字数不同，字号也不同，视觉上才平衡）
        float size2 = line2.isEmpty() ? size1
                : Math.min(u * 2.6f, avail / Math.max(1, line2.length()) * 1.02f);
        // 对齐 12px 像素网格，保持硬边
        size1 = Math.max(u * 1.3f, 12f * Math.round(size1 / 12f));
        size2 = Math.max(u * 1.3f, 12f * Math.round(size2 / 12f));

        float lineH1 = size1 * 1.22f;    // 第一行行高：字号 × 1.22，留出行距
        float lineH2 = size2 * 1.22f;    // 第二行行高
        // 两行总高：只有一行时就是第一行高；两行时相加
        float totalH = line2.isEmpty() ? lineH1 : lineH1 + lineH2;
        // 水平中心：和图标一样，取「书脊右缘(u*2.1)」到「封面右缘」的中点
        float cx = (u * 2.1f + right) / 2f;
        // 纵向：让整块文字以 56% 高度为中心；h*0.56 是「文字块中心」，减去半高得到顶部
        float topY = h * 0.56f - totalH / 2f;

        drawLine(canvas, line1, size1, cx, topY, u);
        if (!line2.isEmpty()) {
            // 第二行紧接着第一行往下排
            drawLine(canvas, line2, size2, cx, topY + lineH1, u);
        }
    }

    /**
     * 画一行书名字：先画一层深色字往右下偏一点当投影，再画米色正字盖上去。
     * 这样字在彩色封面上始终看得清，而且是很典型的像素游戏 HUD 效果。
     */
    private void drawLine(Canvas canvas, String text, float sizePx, float cx, float topY, float u) {
        paint.setTextSize(sizePx);
        Paint.FontMetrics fm = paint.getFontMetrics();
        // topY 是文字块的顶部，baseline = 顶部 - 字体上沿（ascent 是负数，所以是减）
        float baseY = topY - fm.ascent;
        float shadow = Math.max(1f, u * 0.16f);   // 投影偏移：约 1/6 个像素单位，最小 1px
        // 第一遍：深墨色，整体往右下偏移 shadow，形成硬边投影
        paint.setColor(INK_SOFT);
        canvas.drawText(text, cx + shadow, baseY + shadow, paint);
        // 第二遍：奶白色正字盖上去，只露出右下的深色边
        paint.setColor(CREAM);
        canvas.drawText(text, cx, baseY, paint);
    }

    // ---------------- 各类骗局的像素小图标 ----------------

    /**
     * 按 iconKey 的第一个字决定画哪个图标。用 startsWith 而不是 equals，
     * 这样「游戏账号」「游戏点卡」都能落到手柄图标上，调用方不用维护精确匹配表。
     * 没有匹配到任何关键字时兜底画面具（代表「伪装/冒充」，也最中性）。
     */
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

    /**
     * 画一个像素方块：x,y 是左上角，w,h 是宽高（不是右下角坐标）。
     * 所有小图标都是靠反复调用它「拼」出来的，这也是像素画的通用做法。
     */
    private void block(Canvas canvas, float x, float y, float w, float h, int color) {
        paint.setColor(color);
        canvas.drawRect(x, y, x + w, y + h, paint);
    }

    /**
     * 游戏手柄（游戏账号类）：外圈深墨色描边 + 内层奶白机身 + 左侧十字键 +
     * 右侧两个封面色按键 + 左右两个握把。整体扁宽，像横放的手柄。
     */
    private void drawGamepad(Canvas canvas, float cx, float cy, float s, float u) {
        float w = s * 1.15f;    // 手柄总宽：比图标框略宽，显得饱满
        float h = s * 0.56f;    // 手柄总高：扁的
        // 机身外圈：深墨色深色底
        block(canvas, cx - w / 2f, cy - h / 2f, w, h, INK_SOFT);
        // 机身内部：四边各缩进 0.3u 的奶白色，露出深色描边
        block(canvas, cx - w / 2f + u * 0.3f, cy - h / 2f + u * 0.3f, w - u * 0.6f, h - u * 0.6f, CREAM);
        // 左十字键
        //   先画横的一笔（中心偏左），再画竖的一笔，两笔交叉成「十」
        block(canvas, cx - w * 0.30f, cy - u * 0.18f, u * 0.9f, u * 0.36f, INK_SOFT);
        block(canvas, cx - w * 0.30f + u * 0.27f, cy - u * 0.45f, u * 0.36f, u * 0.9f, INK_SOFT);
        // 右按键
        //   两个 0.34u 的小方块，用封面主色（在奶白机身里刚好跳出来）
        block(canvas, cx + w * 0.16f, cy - u * 0.40f, u * 0.34f, u * 0.34f, coverColor);
        block(canvas, cx + w * 0.16f + u * 0.42f, cy - u * 0.05f, u * 0.34f, u * 0.34f, coverColor);
        // 两侧握把：机身左右各伸出一条 0.35u 宽的深色竖条，往下拉出握把的感觉
        block(canvas, cx - w / 2f - u * 0.25f, cy - u * 0.05f, u * 0.35f, h * 0.9f, INK_SOFT);
        block(canvas, cx + w / 2f - u * 0.10f, cy - u * 0.05f, u * 0.35f, h * 0.9f, INK_SOFT);
    }

    /**
     * 金币堆（刷单返利类）：三枚金币，两枚在下并排、一枚叠在上面。
     * 每枚金币都先画深墨色外框、再画内层金色，形成描边效果；
     * 最上面那枚再补一个「¥」符号，明确是钱。
     */
    private void drawCoins(Canvas canvas, float cx, float cy, float s, float u) {
        float c = s * 0.42f;   // 单枚金币的边长
        // 下层左侧那枚：深色底 + 金色内芯
        block(canvas, cx - s * 0.52f, cy + u * 0.25f, c, c * 0.75f, INK_SOFT);
        block(canvas, cx - s * 0.46f, cy + u * 0.30f, c * 0.84f, c * 0.55f, GOLD());
        // 下层右侧那枚：结构同上，水平镜像
        block(canvas, cx + s * 0.06f, cy + u * 0.25f, c, c * 0.75f, INK_SOFT);
        block(canvas, cx + s * 0.12f, cy + u * 0.30f, c * 0.84f, c * 0.55f, GOLD());
        // 上层那枚：位置抬高，压住下面两枚的缝
        block(canvas, cx - s * 0.23f, cy - u * 0.42f, c, c * 0.75f, INK_SOFT);
        block(canvas, cx - s * 0.17f, cy - u * 0.37f, c * 0.84f, c * 0.55f, GOLD());
        // 金币上的"¥"横杠
        //   两横是「￥」的横笔，位置落在最上面那枚金币的中心
        block(canvas, cx - s * 0.08f, cy - u * 0.14f, u * 0.55f, u * 0.12f, INK_SOFT);
        block(canvas, cx - s * 0.02f, cy - u * 0.28f, u * 0.12f, u * 0.42f, INK_SOFT);
    }

    /**
     * 客服耳机（冒充客服类）：一道横着的头梁 + 左右两根竖杆 + 两个耳罩 + 一根麦克风。
     * 头梁压在图标最顶部，耳罩用封面主色点出一个亮色圆点，像耳机上的按钮。
     */
    private void drawHeadset(Canvas canvas, float cx, float cy, float s, float u) {
        float w = s * 0.95f;
        float h = s * 0.85f;
        // 头梁：最上面一条 0.42u 厚的深色横条
        block(canvas, cx - w / 2f, cy - h / 2f, w, u * 0.42f, INK_SOFT);
        //   头梁两端往下的两根短竖杆，把横梁接成「冂」形
        block(canvas, cx - w / 2f, cy - h / 2f + u * 0.2f, u * 0.36f, h * 0.55f, INK_SOFT);
        block(canvas, cx + w / 2f - u * 0.36f, cy - h / 2f + u * 0.2f, u * 0.36f, h * 0.55f, INK_SOFT);
        // 耳罩
        //   左边：深色外框 + 里面一小块封面色（当作耳罩上的装饰灯）
        block(canvas, cx - w / 2f - u * 0.2f, cy - h * 0.12f, u * 0.75f, h * 0.5f, INK_SOFT);
        block(canvas, cx - w / 2f - u * 0.02f, cy - h * 0.04f, u * 0.45f, h * 0.34f, coverColor);
        //   右边：结构镜像
        block(canvas, cx + w / 2f - u * 0.55f, cy - h * 0.12f, u * 0.75f, h * 0.5f, INK_SOFT);
        block(canvas, cx + w / 2f - u * 0.43f, cy - h * 0.04f, u * 0.45f, h * 0.34f, coverColor);
        // 麦克风：从左耳罩往右伸出来的一根细横杆，像客服头上的麦
        block(canvas, cx - w / 2f - u * 0.1f, cy + h * 0.30f, w * 0.55f, u * 0.18f, INK_SOFT);
    }

    /**
     * 警徽（冒充公检法类）：上宽下尖的盾牌。
     * 盾牌形状是由 3 条宽度递减的深色矩形叠出来的——最宽那条在顶部，
     * 越往下越窄，天然形成「盾」的梯形+尖角；再往里画两条奶白内芯，
     * 最后用封面主色点出五角星。
     */
    private void drawBadge(Canvas canvas, float cx, float cy, float s, float u) {
        float w = s * 0.95f;
        // 上宽下尖的盾牌：用 4 行矩形拼
        //   第 1 行：整宽，盾牌最上面那条边
        block(canvas, cx - w / 2f, cy - s * 0.42f, w, s * 0.30f, INK_SOFT);
        //   第 2 行：收窄到 80%，开始往下收
        block(canvas, cx - w * 0.40f, cy - s * 0.12f, w * 0.80f, s * 0.26f, INK_SOFT);
        //   第 3 行：再收到 52%，收成尖底
        block(canvas, cx - w * 0.26f, cy + s * 0.14f, w * 0.52f, s * 0.22f, INK_SOFT);
        // 内部米色：比深色形状各缩进 0.22u，露出深色描边
        block(canvas, cx - w / 2f + u * 0.22f, cy - s * 0.42f + u * 0.22f, w - u * 0.44f, s * 0.24f, CREAM);
        block(canvas, cx - w * 0.40f + u * 0.22f, cy - s * 0.12f + u * 0.22f, w * 0.80f - u * 0.44f, s * 0.20f, CREAM);
        // 中间五角星：用「横竖两笔 + 一个方块」拼出简化星，颜色用封面主色
        block(canvas, cx - u * 0.14f, cy - s * 0.30f, u * 0.28f, u * 0.28f, coverColor);
        block(canvas, cx - u * 0.38f, cy - s * 0.22f, u * 0.76f, u * 0.20f, coverColor);
    }

    /**
     * 钱袋（校园贷/兼职类）：上窄下宽的两段袋身 + 袋口绳 + 「¥」符号。
     * 袋身同样是深色外框套金色内芯，两层错位 0.18u 形成描边。
     */
    private void drawMoneyBag(Canvas canvas, float cx, float cy, float s, float u) {
        float w = s * 0.9f;
        // 袋身：上窄下宽
        //   上面一段（袋口附近）窄一些
        block(canvas, cx - w * 0.26f, cy - s * 0.16f, w * 0.52f, s * 0.24f, INK_SOFT);
        //   下面一段（袋肚）宽到 80%
        block(canvas, cx - w * 0.40f, cy + s * 0.08f, w * 0.80f, s * 0.26f, INK_SOFT);
        //   两段各自的金色内芯，四边各缩进 0.18u
        block(canvas, cx - w * 0.26f + u * 0.18f, cy - s * 0.16f + u * 0.18f, w * 0.52f - u * 0.36f, s * 0.18f, GOLD());
        block(canvas, cx - w * 0.40f + u * 0.18f, cy + s * 0.08f + u * 0.18f, w * 0.80f - u * 0.36f, s * 0.18f, GOLD());
        // 袋口绳：袋口上方两道深色短条（一道横、一道竖），像扎紧的绳头
        block(canvas, cx - w * 0.14f, cy - s * 0.30f, w * 0.28f, u * 0.22f, INK_SOFT);
        block(canvas, cx - w * 0.06f, cy - s * 0.40f, w * 0.12f, u * 0.20f, INK_SOFT);
        // ¥：横竖两笔，落在袋肚中心
        block(canvas, cx - u * 0.26f, cy - u * 0.02f, u * 0.52f, u * 0.12f, INK_SOFT);
        block(canvas, cx - u * 0.06f, cy - u * 0.22f, u * 0.12f, u * 0.44f, INK_SOFT);
    }

    /**
     * 面具（AI 换脸/其它类别）：奶白脸框 + 两个深色眼睛 + 一张封面色嘴 + 两侧绑带。
     * 绑带故意画在脸的两侧外面，强调「这是一张戴上去的假脸」。
     */
    private void drawMask(Canvas canvas, float cx, float cy, float s, float u) {
        float w = s * 0.86f;
        float h = s * 0.94f;
        // 脸框外圈：深墨色底
        block(canvas, cx - w / 2f, cy - h / 2f, w, h, INK_SOFT);
        // 脸框内部：四边缩进 0.22u 的奶白色
        block(canvas, cx - w / 2f + u * 0.22f, cy - h / 2f + u * 0.22f, w - u * 0.44f, h - u * 0.44f, CREAM);
        // 眼睛：两个 0.62u × 0.34u 的深色横条，左右对称
        block(canvas, cx - w * 0.30f, cy - h * 0.16f, u * 0.62f, u * 0.34f, INK_SOFT);
        block(canvas, cx + w * 0.12f, cy - h * 0.16f, u * 0.62f, u * 0.34f, INK_SOFT);
        // 嘴：封面主色的一横，位置在脸下部 20% 处
        block(canvas, cx - w * 0.16f, cy + h * 0.20f, w * 0.32f, u * 0.22f, coverColor);
        // 面具绑带：脸框左右各伸出一根 0.20u 宽的深色竖条
        block(canvas, cx - w / 2f - u * 0.18f, cy - h * 0.10f, u * 0.20f, u * 0.5f, INK_SOFT);
        block(canvas, cx + w / 2f - u * 0.02f, cy - h * 0.10f, u * 0.20f, u * 0.5f, INK_SOFT);
    }

    /** 金色（金币/钱袋专用）。写成方法而不是直接用常量，是为了和历史上的调用点保持兼容 */
    private int GOLD() {
        return Color.parseColor("#E9B44C");
    }

    /**
     * 把一个颜色整体压暗：三个通道同比例乘 factor（0~1 之间），色相不变。
     * factor=0.70 就是「深 30%」，用来做书脊、顶带、底带这些阴影面。
     */
    private int shade(int color, float factor) {
        int r = (int) (Color.red(color) * factor);
        int g = (int) (Color.green(color) * factor);
        int b = (int) (Color.blue(color) * factor);
        return Color.rgb(r, g, b);
    }
}
