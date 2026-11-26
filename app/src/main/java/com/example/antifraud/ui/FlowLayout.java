package com.example.antifraud.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动折行的横向布局：一行放不下就换到下一行，不需要左右滑动。
 *
 * <p>为什么需要它：字号调到「超大」（2.1 倍）后，设置页的 4 个字号按钮、
 * 剧本页的 7 个分类筛选块，一行根本放不下。原来用 HorizontalScrollView
 * 包着，用户必须手动往右划才能看到剩下的按钮，很不友好。
 * 换成这个布局后，放不下的块会自动掉到第二行、第三行。</p>
 *
 * <p>用法和 LinearLayout 基本一样：addView 进去，它会自己算每行放几个。
 * 子 View 的宽度按各自 layoutParams.width / wrap_content 测量，不做等分。</p>
 *
 * <h3>工作流程（先量后摆，两步分开）</h3>
 * <ol>
 *   <li><b>onMeasure</b>：从左到右一个个试子 View，累计宽度；
 *       加下一个会超宽就把当前这一行「定格」存进 {@link #lines}，
 *       再从下一行开头重新累计。同时记下每行的高度和总高度。</li>
 *   <li><b>onLayout</b>：直接照着 onMeasure 算好的 {@link #lines} 逐行摆放，
 *       不再重新判断折行——保证「量的时候算几行，摆的时候就是几行」。</li>
 * </ol>
 * <p>注意：{@link #lines} 是 measure 和 layout 之间的临时数据，
 * 每次 onMeasure 都会先 clear() 重算，所以它永远反映最近一次的测量结果。</p>
 *
 * <p><b>谁在用：</b>{@code ScriptsTabView}（分类筛选块）、{@code SettingsActivity}
 * （字号/字体选项）、{@code NodeEditActivity}（标签选择）。</p>
 */
public class FlowLayout extends ViewGroup {

    /** 子 View 之间的横向间距（px） */
    private int hGap = 0;
    /** 行与行之间的纵向间距（px） */
    private int vGap = 0;

    /** 折行结果：外层是「行」，内层是「这一行里的子 View」，顺序即绘制/摆放顺序 */
    private final List<List<View>> lines = new ArrayList<>();
    /** 每行的高度（不含行间距），下标和 {@link #lines} 一一对应 */
    private final List<Integer> lineHeights = new ArrayList<>();

    /** 代码里 new 出来的构造器 */
    public FlowLayout(Context c) {
        super(c);
    }

    /** XML 里声明的构造器 */
    public FlowLayout(Context c, AttributeSet attrs) {
        super(c, attrs);
    }

    /**
     * 设置子 View 之间的间距。
     *
     * @param hGapPx 同一行里相邻两块的横向间距（px）
     * @param vGapPx 上下两行之间的纵向间距（px）
     */
    public void setGaps(int hGapPx, int vGapPx) {
        this.hGap = hGapPx;
        this.vGap = vGapPx;
        requestLayout();   // 间距变了行数可能跟着变，必须重新测量和摆放
    }

    /**
     * 测量：核心是「累计宽度 + 逐行试放」。
     * 结果同时算出自己的宽高，并把折行方案存进 {@link #lines} 给 onLayout 用。
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);

        // 宽度不确定时（比如父容器也是 wrap_content），先按子 View 想要的宽度求和
        //   随便给一个极大值，等于「不限制每行能有多宽」，所有子 View 就都会排在一行里
        int maxWidth = widthMode == MeasureSpec.UNSPECIFIED
                ? Integer.MAX_VALUE : widthSize;

        // 真正可用于放内容的宽度：扣掉左右内边距
        int available = Math.max(0, maxWidth - getPaddingLeft() - getPaddingRight());

        // 每次测量都从零开始，不能沿用上次的折行结果（屏幕旋转、字号变化都会改变行数）
        lines.clear();
        lineHeights.clear();

        List<View> line = new ArrayList<>();   // 正在拼的这一行
        int lineWidth = 0;                     // 这一行已占的宽度（含间距）
        int lineHeight = 0;                    // 这一行目前最高的那个子 View
        int totalHeight = 0;                   // 已经定格的所有行的高度之和（含行间距）

        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            // GONE 的子 View 不参与排版（和 LinearLayout 行为一致）
            if (child.getVisibility() == GONE) {
                continue;
            }
            // 关键：子 View 是「wrap_content 宽 + 固定/最小高度」的按钮时，
            // 必须用 AT_MOST 量宽度，否则它会被当成要占满整行，导致每行只放得下一个。
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
            MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            // 子 View 实际占位 = 内容尺寸 + 自己的左右/上下外边距
            int cw = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
            int ch = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;

            // 单个子 View 比整行还宽：强制压到一行内，避免溢出到屏幕外
            //   （典型场景：2.1 倍字号下某个按钮文字特别长）
            if (cw > available && available > 0) {
                int childWSpec = MeasureSpec.makeMeasureSpec(
                        Math.max(0, available - lp.leftMargin - lp.rightMargin),
                        MeasureSpec.AT_MOST);
                int childHSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
                child.measure(childWSpec, childHSpec);   // 用受限宽度重量一次，让它自己折字
                cw = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
                ch = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
            }

            // 这一行已有内容，且再加一个就超宽 → 换行
            //   hGap 也要算进去，因为换行前这一块和上一块之间已经隔了一个间距
            if (!line.isEmpty() && lineWidth + hGap + cw > available) {
                lines.add(line);                       // 当前行定格
                lineHeights.add(lineHeight);
                totalHeight += lineHeight + vGap;      // 行高 + 行间距计入总高
                line = new ArrayList<>();              // 开新行
                lineWidth = 0;
                lineHeight = 0;
            }

            // 不是行首才加间距（行首左边不留空白）
            if (!line.isEmpty()) {
                lineWidth += hGap;
            }
            line.add(child);
            lineWidth += cw;
            // 行高取当前行里最高的那个子 View
            lineHeight = Math.max(lineHeight, ch);
        }

        // 收尾：最后一行还没定格，单独补上（注意这里不再加 vGap）
        if (!line.isEmpty()) {
            lines.add(line);
            lineHeights.add(lineHeight);
            totalHeight += lineHeight;
        }

        // 自己的宽度：父容器没限制时取「最宽那一行」的宽度，否则就用父容器给的宽度
        int measuredWidth = widthMode == MeasureSpec.UNSPECIFIED ? lineMaxWidth() : widthSize;
        // 自己的高度：所有行高之和 + 上下内边距
        int measuredHeight = totalHeight + getPaddingTop() + getPaddingBottom();

        // 父容器给了确定高度时（比如高度写死），不能让内容把高度撑出边界
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        if (heightMode == MeasureSpec.AT_MOST) {
            // 最多给这么多：内容比它高就压到它
            measuredHeight = Math.min(measuredHeight, heightSize);
        } else if (heightMode == MeasureSpec.EXACTLY) {
            // 必须正好这么多：不管内容多高都用父容器的值
            measuredHeight = heightSize;
        }
        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    /**
     * 算出「最宽那一行」的宽度，含左右内边距。
     * 只在父容器没给宽度限制（UNSPECIFIED）时才用到，属于兜底算法。
     */
    private int lineMaxWidth() {
        int max = 0;
        for (List<View> line : lines) {
            int w = 0;
            for (int i = 0; i < line.size(); i++) {
                View child = line.get(i);
                MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
                if (i > 0) {
                    w += hGap;   // 非行首才加横向间距
                }
                w += child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
            }
            max = Math.max(max, w);
        }
        return max + getPaddingLeft() + getPaddingRight();
    }

    /**
     * 摆放：照着 onMeasure 存下的 {@link #lines} 逐行、逐个子 View 定位置。
     * 每行从 paddingLeft 开始往右排，排完一行就往下跳「行高 + 行间距」。
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int top = getPaddingTop();   // 当前行的顶部 y 坐标，从内边距开始
        for (int li = 0; li < lines.size(); li++) {
            List<View> line = lines.get(li);
            int left = getPaddingLeft();          // 当前行里下一块的左边界
            int lineHeight = lineHeights.get(li); // 这一行的统一高度（以最高的那个为准）
            for (int i = 0; i < line.size(); i++) {
                View child = line.get(i);
                MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
                if (i > 0) {
                    left += hGap;   // 非行首才加横向间距
                }
                int cw = child.getMeasuredWidth();
                int ch = child.getMeasuredHeight();
                // child.layout 的四个参数是「左、上、右、下」的绝对坐标
                child.layout(left + lp.leftMargin,
                        top + lp.topMargin,
                        left + lp.leftMargin + cw,
                        top + lp.topMargin + ch);
                // 光标右移：左边距 + 自身宽 + 右边距
                left += lp.leftMargin + cw + lp.rightMargin;
            }
            // 换到下一行：下移这一行的高度 + 行间距
            top += lineHeight + vGap;
        }
    }

    // ---- 让子 View 能带 margin（和 LinearLayout 一致） ----

    /** XML 里写 layout_xxx 属性时，用「带 margin」的参数对象解析 */
    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    /** 代码里 addView 但没给 LayoutParams 时，默认给 wrap_content + 可带 margin 的参数 */
    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    /** 把外部传进来的任意 LayoutParams 转成带 margin 的版本 */
    @Override
    protected LayoutParams generateLayoutParams(LayoutParams p) {
        return new MarginLayoutParams(p);
    }

    /** 校验：不是 MarginLayoutParams 就拒绝，后面才能安全地强转 */
    @Override
    protected boolean checkLayoutParams(LayoutParams p) {
        return p instanceof MarginLayoutParams;
    }
}
