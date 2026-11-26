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
 *
 * <p><b>谁在用：</b>聊天页（{@code ChatActivity}）每条消息左侧的小头像，
 * 按「说话人名字」算一个序号，序号对 6 取模就是头像类型；
 * 背景色也是按说话人分配的，所以同一个骗子发多少条消息，头像始终是同一只，
 * 换号之后头像会换成另一种——这是剧本里「骗子换号」能被看出来的视觉线索。</p>
 *
 * <p><b>坐标系约定：</b>u = 头像短边 / 12，是一格「像素」。
 * 头像里的五官全部按 u 的倍数摆，所以窄屏上会自动变小，不会画歪。
 * 绘制顺序：底色 → 四周深色描边 → 顶部高光/底部阴影 → 具体形象（脸、耳朵、眼睛……）。</p>
 */
public class AvatarView extends View {

    /** 深墨色：所有描边、眼睛、鼻子都用它，是整个 App 统一的「墨线」 */
    private static final int INK = Color.parseColor("#3A2A1E");
    /** 肤色：脸的主体色，偏暖的米黄 */
    private static final int FACE = Color.parseColor("#F6E0BE");
    /** 深肤色：熊耳朵、熊鼻子这种要比脸深一档的地方用 */
    private static final int FACE_DARK = Color.parseColor("#E2C79E");
    /** 腮红色：用品牌橙红，少量点缀，让头像有点气色 */
    private static final int BLUSH = Color.parseColor("#E4572E");
    /** 奶白色：星星头像和部分高光用 */
    private static final int WHITE = Color.parseColor("#FFF6DC");

    /** 头像背景色：由调用方给，同一页面里不同人给不同色 */
    private int bgColor = Color.parseColor("#E4572E");
    /** 头像类型 0~5，构造时会被取模限制在这个范围内 */
    private int type = 0;
    private final Paint paint = new Paint();   // 像素风：不抗锯齿，硬边

    /** 代码里 new 出来的构造器（聊天页给每条消息建头像时用） */
    public AvatarView(Context c) {
        super(c);
    }

    /** XML 里声明的构造器（本项目目前没用到，保留给以后在布局里直接摆头像） */
    public AvatarView(Context c, AttributeSet a) {
        super(c, a);
    }

    /**
     * 设置头像外观。
     *
     * @param bgColor   背景色，用来区分不同的人
     * @param typeIndex 头像类型序号；负数会被压到 0，超过 5 的会绕回 0~5，
     *                  保证 switch 里永远有分支能命中，不会画出空白头像
     */
    public void setAvatar(int bgColor, int typeIndex) {
        this.bgColor = bgColor;
        this.type = Math.max(0, typeIndex) % 6;
        invalidate();   // 换了形象必须重画
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        // 还没量出尺寸时直接不画，避免后面除以 0
        if (w <= 0 || h <= 0) {
            return;
        }
        // 像素单位：取短边除以 12，保证头像在长方形格子里也不会被拉变形
        float u = Math.min(w, h) / 12f;      // 像素单位
        paint.setStyle(Paint.Style.FILL);    // 全部实心填充
        paint.setAntiAlias(false);           // 关抗锯齿，保留像素硬边

        // 底色 + 深色描边
        //   第一步：整块铺背景色，作为头像的「底板」
        paint.setColor(bgColor);
        canvas.drawRect(0, 0, w, h, paint);
        //   第二步：四条边各画一条深色边，围出一圈 0.7u 厚的「相框」
        paint.setColor(INK);
        float b = Math.max(2f, u * 0.7f);
        canvas.drawRect(0, 0, w, b, paint);            // 上边
        canvas.drawRect(0, h - b, w, h, paint);        // 下边
        canvas.drawRect(0, 0, b, h, paint);            // 左边
        canvas.drawRect(w - b, 0, w, h, paint);        // 右边

        // 顶部高光 / 底部阴影，让方块有点体积感
        //   顶部：20% 白的横条，贴着上边框内侧，模拟光从上面打下来
        paint.setColor(0x33FFFFFF);
        canvas.drawRect(b, b, w - b, b + u * 0.5f, paint);
        //   底部：13% 黑的横条，贴着下边框内侧，做出一点点厚度
        paint.setColor(0x22000000);
        canvas.drawRect(b, h - b - u * 0.7f, w - b, h - b, paint);

        // 形象中心：横向正中，纵向略偏下（56% 处），因为头顶要留给耳朵/头发
        float cx = w / 2f;
        float cy = h * 0.56f;
        // 按 type 分发到 6 种画法，0 和 default 都是普通学生，只是有没有眼镜的区别
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

    /**
     * 画一个像素方块：x,y 是左上角，w,h 是宽高。
     * 所有形象都是靠反复调用它拼出来的，像素画的通用做法。
     */
    private void block(Canvas c, float x, float y, float w, float h, int color) {
        paint.setColor(color);
        c.drawRect(x, y, x + w, y + h, paint);
    }

    /**
     * 学生：圆脸 + 刘海 + 眼睛 + 腮红。
     *
     * <p>脸的「圆角」是用两块矩形叠出来的：一块左右各缩进 1u（上下到底），
     * 另一块上下各缩进 1u（左右到底），叠起来就是一个四角被削掉的方块，
     * 这就是像素画里常用的「假圆角」。</p>
     *
     * @param glasses true 表示学霸款，会在眼睛外面再套一副方框眼镜
     */
    private void drawStudent(Canvas canvas, float cx, float cy, float u, boolean glasses) {
        float fw = u * 6.4f;   // 脸宽：6.4 个像素格，占头像宽度的 53% 左右
        float fh = u * 5.6f;   // 脸高：略扁，看起来更憨
        float left = cx - fw / 2f;
        float top = cy - fh / 2f;
        // 脸（像素圆角：中间大块 + 四角小块）
        //   第一块：左右各收 1u，撑满整个高度 —— 这是脸的「竖着的那一块」
        block(canvas, left + u, top, fw - u * 2, fh, FACE);
        //   第二块：上下各收 1u，撑满整个宽度 —— 这是脸的「横着的那一块」
        //   两块叠起来，四个角天然缺一块，形成像素圆角
        block(canvas, left, top + u, fw, fh - u * 2, FACE);
        // 头发
        //   刘海：脸顶上一条 1.5u 厚的深色横条，位置略高于脸顶（-0.4u），压住额头
        block(canvas, left, top - u * 0.4f, fw, u * 1.5f, INK);
        //   左右两撮鬓角：从刘海两端往下垂 0.9u，把脸框住
        block(canvas, left + u * 0.6f, top + u * 1.0f, u * 1.1f, u * 0.9f, INK);
        block(canvas, left + fw - u * 1.7f, top + u * 1.0f, u * 1.1f, u * 0.9f, INK);
        // 眼睛：两个 1u × 1.1u 的深色小方块，左右各一个，间距 3u
        block(canvas, cx - u * 2.0f, cy - u * 0.4f, u * 1.0f, u * 1.1f, INK);
        block(canvas, cx + u * 1.0f, cy - u * 0.4f, u * 1.0f, u * 1.1f, INK);
        if (glasses) {
            // 学霸：方框眼镜 + 横梁
            //   左右各一个「口」字：上横、下横两条，每条 2.2u 长、0.3u 厚
            block(canvas, cx - u * 2.6f, cy - u * 0.9f, u * 2.2f, u * 0.3f, INK);
            block(canvas, cx - u * 2.6f, cy + u * 0.7f, u * 2.2f, u * 0.3f, INK);
            block(canvas, cx + u * 0.4f, cy - u * 0.9f, u * 2.2f, u * 0.3f, INK);
            block(canvas, cx + u * 0.4f, cy + u * 0.7f, u * 2.2f, u * 0.3f, INK);
            //   鼻梁横梁：两只镜框中间的一小段，位置在两眼之间
            block(canvas, cx - u * 0.4f, cy - u * 0.2f, u * 0.8f, u * 0.3f, INK);
        }
        // 腮红 + 嘴
        //   腮红：左右各一块 1u × 0.5u 的橙红色，位置在眼睛下方一格
        block(canvas, cx - u * 2.3f, cy + u * 1.1f, u * 1.0f, u * 0.5f, BLUSH);
        block(canvas, cx + u * 1.3f, cy + u * 1.1f, u * 1.0f, u * 0.5f, BLUSH);
        //   嘴：脸中线偏下一点的一小横，1.2u 宽，简单的一字嘴
        block(canvas, cx - u * 0.6f, cy + u * 1.3f, u * 1.2f, u * 0.4f, INK);
    }

    /**
     * 猫：尖耳 + 胡须。
     * 耳朵是「阶梯三角」——由两块大小不同的方块竖着叠，看起来就是三角形的像素近似。
     */
    private void drawCat(Canvas canvas, float cx, float cy, float u) {
        float fw = u * 6.2f;
        float fh = u * 5.4f;
        float left = cx - fw / 2f;
        float top = cy - fh / 2f;
        // 耳朵（阶梯三角）
        //   左耳：先一小块窄的（0.8u）在最上面当耳尖，再一大块（1.4u）在下面当耳根
        block(canvas, left + u * 0.4f, top - u * 1.6f, u * 0.8f, u * 0.6f, INK);
        block(canvas, left + u * 0.2f, top - u * 1.0f, u * 1.4f, u * 1.2f, INK);
        //   右耳：同样的阶梯，水平镜像到右边
        block(canvas, left + fw - u * 1.6f, top - u * 1.6f, u * 0.8f, u * 0.6f, INK);
        block(canvas, left + fw - u * 1.6f, top - u * 1.0f, u * 1.4f, u * 1.2f, INK);
        // 脸：和学生一样用两块矩形叠出像素圆角
        block(canvas, left + u, top, fw - u * 2, fh, FACE);
        block(canvas, left, top + u, fw, fh - u * 2, FACE);
        // 眼睛 + 鼻子 + 胡须
        //   眼睛：两个 0.9u 的小方块，比学生的眼睛小，显得更「萌」
        block(canvas, cx - u * 1.9f, cy - u * 0.5f, u * 0.9f, u * 0.9f, INK);
        block(canvas, cx + u * 1.0f, cy - u * 0.5f, u * 0.9f, u * 0.9f, INK);
        //   鼻子：脸中间的一块橙红色小三角（0.8u 宽、0.5u 高），当猫鼻子
        block(canvas, cx - u * 0.4f, cy + u * 0.4f, u * 0.8f, u * 0.5f, BLUSH);
        //   胡须：左右各一条 1.4u 长的细横线，从脸的两侧伸出去
        block(canvas, left - u * 0.6f, cy + u * 0.5f, u * 1.4f, u * 0.25f, INK);
        block(canvas, left + fw - u * 0.8f, cy + u * 0.5f, u * 1.4f, u * 0.25f, INK);
    }

    /**
     * 兔：长耳 + 粉内耳。
     * 耳朵很高（2.8u），所以整只兔子的视觉重心比其它形象更靠上。
     */
    private void drawRabbit(Canvas canvas, float cx, float cy, float u) {
        float fw = u * 5.8f;   // 兔子脸比学生略窄，显得秀气
        float fh = u * 5.2f;
        float left = cx - fw / 2f;
        float top = cy - fh / 2f;
        // 长耳
        //   左耳：外面一层肤色 1.3u 宽、2.8u 高，里面再嵌 0.7u 宽的粉内耳
        block(canvas, left + u * 1.0f, top - u * 2.6f, u * 1.3f, u * 2.8f, FACE);
        block(canvas, left + u * 1.3f, top - u * 2.2f, u * 0.7f, u * 1.8f, BLUSH);
        //   右耳：镜像
        block(canvas, left + fw - u * 2.3f, top - u * 2.6f, u * 1.3f, u * 2.8f, FACE);
        block(canvas, left + fw - u * 2.0f, top - u * 2.2f, u * 0.7f, u * 1.8f, BLUSH);
        // 脸：两块矩形叠成像素圆角
        block(canvas, left + u, top, fw - u * 2, fh, FACE);
        block(canvas, left, top + u, fw, fh - u * 2, FACE);
        // 眼睛 + 鼻子 + 腮红
        //   眼睛：两个 0.9u 的小方块
        block(canvas, cx - u * 1.8f, cy - u * 0.4f, u * 0.9f, u * 0.9f, INK);
        block(canvas, cx + u * 0.9f, cy - u * 0.4f, u * 0.9f, u * 0.9f, INK);
        //   鼻子：嘴上方一块橙红小方块
        block(canvas, cx - u * 0.4f, cy + u * 0.6f, u * 0.8f, u * 0.5f, BLUSH);
        //   腮红：两颊各一块，位置比鼻子略低，兔子用双份腮红显得可爱
        block(canvas, cx - u * 2.3f, cy + u * 0.9f, u * 0.9f, u * 0.5f, BLUSH);
        block(canvas, cx + u * 1.4f, cy + u * 0.9f, u * 0.9f, u * 0.5f, BLUSH);
    }

    /**
     * 熊：圆耳 + 圆鼻。
     * 耳朵用「深肤色」而不是墨色，和脸的区别只在明度上，是熊区别于猫的关键。
     */
    private void drawBear(Canvas canvas, float cx, float cy, float u) {
        float fw = u * 6.2f;
        float fh = u * 5.4f;
        float left = cx - fw / 2f;
        float top = cy - fh / 2f;
        // 圆耳：两块 1.8u × 1.8u 的深肤色方块，一半压在脸顶上面
        block(canvas, left + u * 0.2f, top - u * 1.2f, u * 1.8f, u * 1.8f, FACE_DARK);
        block(canvas, left + fw - u * 2.0f, top - u * 1.2f, u * 1.8f, u * 1.8f, FACE_DARK);
        // 脸：两块矩形叠成像素圆角
        block(canvas, left + u, top, fw - u * 2, fh, FACE);
        block(canvas, left, top + u, fw, fh - u * 2, FACE);
        // 眼睛 + 鼻 + 嘴
        //   眼睛：两个 0.9u 的小方块
        block(canvas, cx - u * 1.9f, cy - u * 0.6f, u * 0.9f, u * 0.9f, INK);
        block(canvas, cx + u * 1.0f, cy - u * 0.6f, u * 0.9f, u * 0.9f, INK);
        //   鼻：脸中间一个 1.6u × 0.9u 的深肤色方块（熊的鼻子是大块的）
        block(canvas, cx - u * 0.8f, cy + u * 0.4f, u * 1.6f, u * 0.9f, FACE_DARK);
        //   嘴：鼻子下面一小块墨色，代表咧开的嘴
        block(canvas, cx - u * 0.3f, cy + u * 0.5f, u * 0.6f, u * 0.4f, INK);
    }

    /**
     * 星星：像素五角星。
     * 画法是「先画一个竖着的长条 + 一个横着的长条 + 中间一个方块」拼出十字星，
     * 再把中心挖掉一块露出背景色，最后加两个眼睛当小脸。
     */
    private void drawStar(Canvas canvas, float cx, float cy, float u) {
        int star = WHITE;
        // 竖条：宽 1u、高 6u，形成星星的上下两个尖角
        block(canvas, cx - u * 0.5f, cy - u * 3.0f, u, u * 6.0f, star);
        // 横条：高 1u、宽 6u，形成左右两个尖角
        block(canvas, cx - u * 3.0f, cy - u * 0.5f, u * 6.0f, u, star);
        // 中间大方块：4u × 4u，把十字的四个夹角填实
        block(canvas, cx - u * 2.0f, cy - u * 2.0f, u * 4.0f, u * 4.0f, star);
        // 中心再叠一块 2.8u × 2.8u 的同色方块：让星星中间更饱满（叠色不改变颜色，只是形状）
        block(canvas, cx - u * 1.4f, cy - u * 1.4f, u * 2.8f, u * 2.8f, star);
        // 中心描出一点点底色，显得是中空的星
        //   挖掉正中间 1u 的小方块，用背景色填回去，星星看起来就有个「中空」的芯
        block(canvas, cx - u * 0.5f, cy - u * 0.5f, u, u, bgColor);
        // 眼睛（把星星当小脸）
        //   两颗 0.5u 的墨色小点，位置在中心两侧上方
        block(canvas, cx - u * 1.1f, cy - u * 0.9f, u * 0.5f, u * 0.5f, INK);
        block(canvas, cx + u * 0.6f, cy - u * 0.9f, u * 0.5f, u * 0.5f, INK);
    }
}
