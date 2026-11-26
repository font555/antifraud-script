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
 */
public class FlowLayout extends ViewGroup {

    /** 子 View 之间的横向间距（px） */
    private int hGap = 0;
    /** 行与行之间的纵向间距（px） */
    private int vGap = 0;

    private final List<List<View>> lines = new ArrayList<>();
    private final List<Integer> lineHeights = new ArrayList<>();

    public FlowLayout(Context c) {
        super(c);
    }

    public FlowLayout(Context c, AttributeSet attrs) {
        super(c, attrs);
    }

    /** 设置子 View 之间的间距（dp 会由调用方换算成 px） */
    public void setGaps(int hGapPx, int vGapPx) {
        this.hGap = hGapPx;
        this.vGap = vGapPx;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);

        // 宽度不确定时（比如父容器也是 wrap_content），先按子 View 想要的宽度求和
        int maxWidth = widthMode == MeasureSpec.UNSPECIFIED
                ? Integer.MAX_VALUE : widthSize;

        int available = Math.max(0, maxWidth - getPaddingLeft() - getPaddingRight());

        lines.clear();
        lineHeights.clear();

        List<View> line = new ArrayList<>();
        int lineWidth = 0;
        int lineHeight = 0;
        int totalHeight = 0;

        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            // 关键：子 View 是「wrap_content 宽 + 固定/最小高度」的按钮时，
            // 必须用 AT_MOST 量宽度，否则它会被当成要占满整行，导致每行只放得下一个。
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
            MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
            int cw = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
            int ch = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;

            // 单个子 View 比整行还宽：强制压到一行内，避免溢出到屏幕外
            if (cw > available && available > 0) {
                int childWSpec = MeasureSpec.makeMeasureSpec(
                        Math.max(0, available - lp.leftMargin - lp.rightMargin),
                        MeasureSpec.AT_MOST);
                int childHSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
                child.measure(childWSpec, childHSpec);
                cw = child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
                ch = child.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
            }

            // 这一行已有内容，且再加一个就超宽 → 换行
            if (!line.isEmpty() && lineWidth + hGap + cw > available) {
                lines.add(line);
                lineHeights.add(lineHeight);
                totalHeight += lineHeight + vGap;
                line = new ArrayList<>();
                lineWidth = 0;
                lineHeight = 0;
            }

            if (!line.isEmpty()) {
                lineWidth += hGap;
            }
            line.add(child);
            lineWidth += cw;
            lineHeight = Math.max(lineHeight, ch);
        }

        if (!line.isEmpty()) {
            lines.add(line);
            lineHeights.add(lineHeight);
            totalHeight += lineHeight;
        }

        int measuredWidth = widthMode == MeasureSpec.UNSPECIFIED ? lineMaxWidth() : widthSize;
        int measuredHeight = totalHeight + getPaddingTop() + getPaddingBottom();

        // 父容器给了确定高度时（比如高度写死），不能让内容把高度撑出边界
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        if (heightMode == MeasureSpec.AT_MOST) {
            measuredHeight = Math.min(measuredHeight, heightSize);
        } else if (heightMode == MeasureSpec.EXACTLY) {
            measuredHeight = heightSize;
        }
        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    private int lineMaxWidth() {
        int max = 0;
        for (List<View> line : lines) {
            int w = 0;
            for (int i = 0; i < line.size(); i++) {
                View child = line.get(i);
                MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
                if (i > 0) {
                    w += hGap;
                }
                w += child.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
            }
            max = Math.max(max, w);
        }
        return max + getPaddingLeft() + getPaddingRight();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int top = getPaddingTop();
        for (int li = 0; li < lines.size(); li++) {
            List<View> line = lines.get(li);
            int left = getPaddingLeft();
            int lineHeight = lineHeights.get(li);
            for (int i = 0; i < line.size(); i++) {
                View child = line.get(i);
                MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
                if (i > 0) {
                    left += hGap;
                }
                int cw = child.getMeasuredWidth();
                int ch = child.getMeasuredHeight();
                child.layout(left + lp.leftMargin,
                        top + lp.topMargin,
                        left + lp.leftMargin + cw,
                        top + lp.topMargin + ch);
                left += lp.leftMargin + cw + lp.rightMargin;
            }
            top += lineHeight + vGap;
        }
    }

    // ---- 让子 View 能带 margin（和 LinearLayout 一致） ----

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new MarginLayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected LayoutParams generateLayoutParams(LayoutParams p) {
        return new MarginLayoutParams(p);
    }

    @Override
    protected boolean checkLayoutParams(LayoutParams p) {
        return p instanceof MarginLayoutParams;
    }
}
