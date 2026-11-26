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
 *
 * <p><b>谁在用：</b>{@code GuideOverlay}（新手指引浮层）。整个浮层是
 * {@link android.widget.FrameLayout}，本控件铺满全屏当最底层的「暗幕」，
 * 说明卡片盖在它上面；每翻一步，GuideOverlay 量一下目标控件的屏幕位置，
 * 用 {@link #setHole} 把那个位置挖亮，用户就知道「该点哪里」。</p>
 *
 * <p><b>绘制原理（关键）：</b>Android 的 Canvas 本身没有「橡皮擦」，
 * 想「在暗幕上挖洞」必须借助离屏图层：</p>
 * <ol>
 *   <li>{@code saveLayer} 新建一层透明图层，后续绘制都落在这层上；</li>
 *   <li>用半透明黑铺满整层 → 得到一层「暗幕」；</li>
 *   <li>用 {@code PorterDuff.Mode.CLEAR}（橡皮擦模式）在暗幕上画圆角矩形
 *       → 那一块像素被清成完全透明；</li>
 *   <li>{@code restoreToCount} 把整层「贴」回屏幕。</li>
 * </ol>
 * <p>如果不 saveLayer 而直接 CLEAR，会因为作用在屏幕本身上而擦出黑洞，
 * 所以第 1 步和第 4 步不能省。</p>
 */
public class SpotlightView extends View {

    /** 要高亮的那块区域（屏幕坐标），没有洞时是空矩形 */
    private final RectF hole = new RectF();
    /** 当前有没有洞；false 表示整屏只压暗、不挖洞 */
    private boolean hasHole = false;
    /** 暗幕画笔：半透明黑，负责「压暗整屏」 */
    private final Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 橡皮擦画笔：Xfermode = CLEAR，负责「挖洞」 */
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 描边画笔：在洞口画一圈亮边，强调「就是这里」 */
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 洞口的圆角半径（px） */
    private float radius;

    /** 暗幕颜色：80% 黑。留 20% 透出来，用户还能隐约看到背后的界面 */
    private static final int DIM_COLOR = 0xCC000000;

    /** 代码里 new 出来的构造器（GuideOverlay 用它） */
    public SpotlightView(Context c) {
        super(c);
        dim.setColor(DIM_COLOR);
        // 关键：把画笔合成模式设成 CLEAR —— 用它画过的地方会被「擦成全透明」
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        // 洞口描边：不填充（STROKE），线宽 2dp（最小的 2px 兜底，避免 1px 屏上看不见）
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Math.max(2f, c.getResources().getDisplayMetrics().density * 2));
        // 描边颜色用奶白色，跟 App 的纸色同一体系，在暗幕上足够亮
        ring.setColor(0xFFFFF6DC);
        // 圆角半径 10dp：和界面里按钮/卡片的圆角保持同一档
        radius = c.getResources().getDisplayMetrics().density * 10;
    }

    /**
     * 设置高亮洞。传 null 表示"没有高亮目标"：整屏压暗、不挖洞。
     * 这样引导里没有目标的说明步骤也能正常显示，不会出现"整屏全黑什么都看不见"。
     *
     * <p>注意：宽或高为 0 的矩形也按「没有洞」处理——某些控件还没布局完时
     * getLocationInWindow 会返回 0 宽高，此时挖洞会挖出一个看不见的点，
     * 不如老老实实按全暗处理。</p>
     */
    public void setHole(RectF rect) {
        if (rect == null || rect.width() <= 0 || rect.height() <= 0) {
            hasHole = false;
            hole.setEmpty();
        } else {
            hasHole = true;
            // 复制一份而不是直接持有外部对象：调用方后续改动原 RectF 不会影响这里
            hole.set(rect);
        }
        invalidate();   // 洞变了要重画整块暗幕
    }

    /**
     * 画暗幕（+ 洞 + 洞的亮边）。
     * 没有洞时是一条最简单路径；有洞时走「离屏图层 + CLEAR」的四步曲。
     */
    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        // 还没布局出尺寸就什么都不画，避免画出 0 尺寸图层
        if (w <= 0 || h <= 0) {
            return;
        }
        if (!hasHole) {
            // 情况一：没有高亮目标 —— 整屏铺一层暗幕就完事（纯说明步骤用）
            canvas.drawRect(0, 0, w, h, dim);
            return;
        }
        // 情况二：要在暗幕上挖洞，必须先开一个离屏图层，否则 CLEAR 会擦穿屏幕
        int save = canvas.saveLayer(0, 0, w, h, null);
        // 第 1 步：整屏铺暗幕
        canvas.drawRect(0, 0, w, h, dim);
        // 第 2 步：用 CLEAR 画笔在暗幕上画圆角矩形 —— 那块被擦成全透明，露出后面的界面
        canvas.drawRoundRect(hole, radius, radius, clearPaint);
        // 第 3 步：把图层合回屏幕（不 restore 的话后面画的描边也会被限制在图层里）
        canvas.restoreToCount(save);
        // 高亮描边
        //   描边是画在「矩形边界线」上的，一半在里一半在外，所以先把矩形往外撑半个线宽，
        //   这样描边整体落在洞口外侧，不会盖住洞里露出来的界面
        RectF r = new RectF(hole);
        r.inset(-ring.getStrokeWidth() / 2f, -ring.getStrokeWidth() / 2f);
        canvas.drawRoundRect(r, radius, radius, ring);
    }
}
