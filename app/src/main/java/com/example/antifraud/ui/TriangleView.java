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
 *
 * <p><b>谁在用：</b>{@code ScriptsTabView}（剧本页）分类筛选行右侧的小箭头。
 * 点一下「收起分类 / 展开分类」，{@code ScriptsTabView} 就调
 * {@code setPointDown(collapsed)} 把箭头翻过来，同时显示/隐藏下面那排分类块。
 * 箭头颜色由调用方改成灰褐色，所以它不抢眼，只作为一个「这里能点」的提示。</p>
 *
 * <p><b>坐标系约定：</b>以控件中心 (cx, cy) 为基准，先算一个「半宽 halfW」，
 * 再由 halfW 推「半高 halfH = halfW × 0.72」，这样三角形永远是「比高略宽」的扁脸，
 * 看着像箭头而不是等边三角形。宽高都受控件自身尺寸限制，不会画出去。</p>
 */
public class TriangleView extends View {

    private final Paint paint = new Paint();
    /** 箭头朝向：true 朝下（表示「点我可以展开」），false 朝上（表示「点我收起」） */
    private boolean pointDown = true;
    /** 箭头颜色：默认一个偏紫的深色（和复盘页的强调色一致），可由调用方覆盖 */
    private int color = 0xFF6B4C8A;

    /** 代码里 new 出来的构造器 */
    public TriangleView(Context c) {
        super(c);
        init();
    }

    /** XML 里声明的构造器 */
    public TriangleView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    /** 统一初始化画笔：实心、不抗锯齿（像素风要硬边） */
    private void init() {
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(false);
    }

    /** true = 三角形朝下（表示"点击展开"），false = 朝上（表示"点击收起"） */
    public void setPointDown(boolean down) {
        this.pointDown = down;
        invalidate();   // 朝向变了要重画
    }

    /** 改箭头颜色（比如详情页用主题色，列表页用灰褐色） */
    public void setArrowColor(int c) {
        this.color = c;
        invalidate();
    }

    /**
     * 测量：不追求「内容多大就多大」，而是「父容器给多大就用多大」——
     * 因为三角形的位置是按控件中心算的，控件本身只是提供一个摆放区域。
     * 只有在父容器没给尺寸（宽或高为 0）时，才退回默认 24 × 16。
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        // 没量出尺寸时给一个最小可用值，避免画成 0×0 看不见
        if (w <= 0) {
            w = 24;
        }
        if (h <= 0) {
            h = 16;
        }
        setMeasuredDimension(w, h);
    }

    /**
     * 画三角形：以控件中心为基准，朝下时「上边一条横线 + 下面一个尖」，
     * 朝上时把三个点上下镜像。
     */
    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;   // 水平中心：三角形的对称轴
        float cy = h / 2f;   // 垂直中心：三角形整体的重心所在

        // 三角宽高按 1 : 0.8 画，比高矮一点，看起来更像"箭头"
        //   halfW 同时受「宽度 42%」和「高度 70%」限制，保证在高瘦的控件里也不会横着溢出去
        float halfW = Math.min(w * 0.42f, h * 0.7f);
        float halfH = halfW * 0.72f;

        Path p = new Path();
        if (pointDown) {
            // 朝下：两个上顶点在中心线的左右，底顶点在中心偏下
            //   起始点（左上）相对中心横移 -halfW、纵移 -halfH*0.6（略高于中心）
            p.moveTo(cx - halfW, cy - halfH * 0.6f);
            p.lineTo(cx + halfW, cy - halfH * 0.6f);   // 右上：和左上同一水平线，形成顶边
            p.lineTo(cx, cy + halfH * 0.9f);           // 底顶点：横向归中、纵向略低于中心
        } else {
            // 朝上：三个点整体上下镜像——顶边跑到中心下方，尖角朝上
            p.moveTo(cx - halfW, cy + halfH * 0.6f);   // 左下
            p.lineTo(cx + halfW, cy + halfH * 0.6f);   // 右下
            p.lineTo(cx, cy - halfH * 0.9f);           // 顶顶点
        }
        p.close();   // 闭合路径，drawPath 才能填成实心三角

        paint.setColor(color);
        canvas.drawPath(p, paint);
    }
}
