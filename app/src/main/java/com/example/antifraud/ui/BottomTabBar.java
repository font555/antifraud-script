package com.example.antifraud.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.antifraud.R;
import com.example.antifraud.engine.SettingsStore;

/**
 * 底部三 Tab 自绘导航条（像素风，零依赖）。
 * 图标用 Canvas 画：房子 / 列表 / 人形；选中为品牌红，未选中为灰褐色。
 *
 * <p>字号调到「超大」（2.1×）时，导航条只按 1.35 倍封顶放大：
 * 底部栏是常驻控件，长太胖会把整屏高度吃掉，正文反而看不到。</p>
 *
 * <p><b>谁在用：</b>{@code activity_main.xml} 里放了一整条，{@code MainActivity}
 * 通过 {@link #setOnTabSelected} 收到点击、再调 {@link #setSelected} 把高亮同步回来。
 * 三个 Tab 分别是「首页 / 剧本 / 我的」，切换时不销毁页面（本项目不用 Fragment），
 * 而是把三块自定义 View 叠在一起做显示隐藏。</p>
 *
 * <p><b>结构：</b>本类是 {@link LinearLayout}，横向三等分。每一格内部再竖着放
 * 「图标（{@link TabIconView}）」+「文字（{@link PTextView}，像素字体）」。
 * 图标高度固定、文字高度自适应，所以字号变大时只会往下长一点，不会挤掉图标。</p>
 */
public class BottomTabBar extends LinearLayout {

    /** 标准字号下的条高（dp），与 activity_main.xml 的 58dp 对齐 */
    private static final int BAR_HEIGHT_DP = 58;
    /** 导航条整体放大上限：超过它底部栏就太占地方了 */
    private static final float BAR_MAX_SCALE = 1.35f;

    /** Tab 点击回调：参数是 0/1/2，分别对应首页/剧本/我的 */
    public interface OnTabSelected {
        void onTabSelected(int index);
    }

    /** 三个 Tab 的文字，顺序必须和 {@link #icons}、{@link #labels} 一致 */
    private static final String[] LABELS = {"首页", "剧本", "我的"};
    /** 三个图标控件，下标就是 Tab 序号 */
    private final TabIconView[] icons = new TabIconView[3];
    /** 三个文字控件，下标就是 Tab 序号 */
    private final TextView[] labels = new TextView[3];
    /** 当前选中的 Tab 序号，默认停在首页 */
    private int selected = 0;
    /** 上一次做过「弹一下」动画的 Tab 序号：-1 表示还没做过，避免重复动画 */
    private int lastAnimated = -1;
    /** 外部注册的点击回调 */
    private OnTabSelected listener;

    /** 代码里 new 出来的构造器（本项目主界面目前走 XML，这个用于测试或动态添加） */
    public BottomTabBar(Context c) {
        super(c);
        build(c);
    }

    /** XML 里声明的构造器：activity_main.xml 里的一整条就是从这里实例化的 */
    public BottomTabBar(Context c, android.util.AttributeSet attrs) {
        super(c, attrs);
        build(c);
    }

    /**
     * 搭建整个导航条：设方向、背景、高度，然后循环创建 3 个「图标 + 文字」格子。
     * 只在构造时执行一次，之后尺寸/颜色变化都走 {@link #apply()}。
     */
    private void build(Context c) {
        setOrientation(HORIZONTAL);              // 三个 Tab 横向排
        setGravity(Gravity.CENTER_VERTICAL);     // 内容垂直居中
        setBackgroundColor(c.getColor(R.color.paper));   // 米色纸底，和整页同色系

        // 整条高度：标准 58dp，随字号放大但封顶 1.35 倍（最高约 78dp）。
        // XML 里已改成 wrap_content，这里的最小高度负责把条撑开，避免 label 被裁。
        int barH = scaleBar(c, Ui.dp(c, BAR_HEIGHT_DP));
        setMinimumHeight(barH);

        // 上下内边距跟着条高一起长一点点，但同样封顶，别让图标被挤扁
        int verticalPad = scaleBar(c, Ui.dp(c, 4));
        setPadding(0, verticalPad, 0, verticalPad);

        // 图标 22dp 是像素图的基准尺寸，只轻微放大（1.35 倍封顶后约 30dp）
        int iconSize = scaleBar(c, Ui.dp(c, 22));
        // 图标和文字之间的竖向间隙
        int labelGap = scaleBar(c, Ui.dp(c, 3));

        for (int i = 0; i < 3; i++) {
            // 每个 Tab 是一个竖排的 LinearLayout 容器
            LinearLayout item = new LinearLayout(c);
            item.setOrientation(VERTICAL);
            item.setGravity(Gravity.CENTER);
            // 宽度 0 + weight 1：三等分整条宽度，屏幕再宽也不会有一个 Tab 特别胖
            item.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LayoutParams.MATCH_PARENT, 1f));

            // 图标：new 的时候把 i 传进去，图标自己知道该画房子还是列表
            TabIconView icon = new TabIconView(c, i);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    iconSize, iconSize);
            icon.setLayoutParams(ilp);
            item.addView(icon);
            icons[i] = icon;

            // 文字：像素字体（PTextView），10sp 会被 BaseActivity 的 fontScale 放大
            TextView label = new PTextView(c);
            label.setText(LABELS[i]);
            label.setTextSize(10);
            label.setGravity(Gravity.CENTER);
            label.setIncludeFontPadding(false);   // 去掉字体自带的上下留白，贴紧图标
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            tlp.topMargin = labelGap;
            label.setLayoutParams(tlp);
            item.addView(label);
            labels[i] = label;

            // 点击：只把序号抛给外部，具体切哪个页面由 MainActivity 决定
            final int index = i;
            item.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onTabSelected(index);
                    }
                }
            });
            addView(item);
        }
        // 首次上屏，按 selected=0 把「首页」点亮
        apply();
    }

    /** 注册 Tab 点击回调，由 MainActivity 调用 */
    public void setOnTabSelected(OnTabSelected l) {
        listener = l;
    }

    /**
     * 底部栏专用放大：先按字号放大（SettingsStore.scaleBox 内部上限 1.9），
     * 再整体压到 1.35 倍封顶，避免「超大」档把屏幕吃光。
     *
     * <p>举个例子：标准档 58dp → 还是 58dp；1.7 档 58dp → scaleBox 后约 99dp，
     * 但 cap 是 78dp，所以取 78dp。底部栏永远不会超过 78dp。</p>
     */
    private static int scaleBar(Context c, int sizePx) {
        int scaled = SettingsStore.scaleBox(c, sizePx);     // 按字号放大（内部有 1.9 上限）
        int cap = Math.round(sizePx * BAR_MAX_SCALE);       // 本控件自己的 1.35 倍上限
        return Math.min(scaled, cap);                       // 两个上限取小的那个
    }

    /** 返回当前选中的 Tab 序号（0 首页 / 1 剧本 / 2 我的） */
    public int getSelected() {
        return selected;
    }

    /**
     * 从外部切换高亮（比如用户在「我的」页点了「去玩剧本」，要跳回首页并点亮首页图标）。
     *
     * @param index 目标序号；传入越界值或和当前相同，就只重刷颜色、不播动画
     */
    public void setSelected(int index) {
        if (index < 0 || index > 2 || index == selected) {
            apply();       // 非法值或没变化：至少保证颜色是对的
            return;
        }
        selected = index;
        apply();
    }

    /**
     * 把「选中态」刷到界面上：图标换色 + 文字换色 + 选中的图标弹一下。
     * 任何影响选中态的操作最后都会走到这里，是唯一的状态落地口。
     */
    private void apply() {
        Context c = getContext();
        for (int i = 0; i < 3; i++) {
            boolean on = i == selected;
            icons[i].setActive(on);                  // 图标：品牌红 / 灰褐
            labels[i].setTextColor(c.getColor(on ? R.color.brand : R.color.ink_hint));
        }
        // 选中的图标弹一下，给点击一个明确反馈
        //   只有「换了 Tab」才播，同一个 Tab 反复点不重复播，省电也省眼
        if (selected != lastAnimated) {
            lastAnimated = selected;
            final TabIconView icon = icons[selected];
            icon.animate().cancel();        // 先取消上一次没播完的动画，避免叠加出怪尺寸
            icon.setScaleX(1f);             // 复位到原始大小
            icon.setScaleY(1f);
            // 第一段：110ms 放大到 1.2 倍
            icon.animate().scaleX(1.2f).scaleY(1.2f).setDuration(110)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            // 第二段：150ms 缩回 1 倍，形成「弹一下」的脉冲感
                            icon.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                        }
                    }).start();
        }
    }

    /**
     * 三个像素图标：0 房子 / 1 列表 / 2 人形。
     *
     * <p>做成内部类是因为它只服务于导航条，不需要对外暴露；每个实例只画一个图形，
     * 颜色随 {@link #setActive(boolean)} 在两个主题色之间切换。
     * 像素单位 u = 控件宽 / 12，和 AvatarView、PixelBookView 的约定一致。</p>
     */
    static class TabIconView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        /** 画哪个图形：0 房子 / 1 列表 / 2 人形 */
        private final int kind;
        /** 是否是当前选中的 Tab（决定用品牌红还是灰褐） */
        private boolean active;

        TabIconView(Context c, int kind) {
            super(c);
            this.kind = kind;
            paint.setStyle(Paint.Style.FILL);
        }

        /** 切换选中态并重画 */
        void setActive(boolean a) {
            active = a;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            // 先按选中状态定颜色，三种图标共用；后面房子里的「门」会临时改成 paper 色
            paint.setColor(getResources().getColor(active ? R.color.brand : R.color.ink_hint));
            float u = w / 12f;      // 像素单位
            if (kind == 0) {
                // 房子：屋顶三角 + 主体 + 门
                //   屋顶：从顶部中点往左下、右下各拉一条斜边，成一个等腰三角形
                Path roof = new Path();
                roof.moveTo(w / 2f, u);                 // 顶点：横向正中、纵向 1 格处
                roof.lineTo(w - u, h * 0.45f);          // 右下角
                roof.lineTo(u, h * 0.45f);              // 左下角
                roof.close();
                canvas.drawPath(roof, paint);
                //   主体：屋顶下面的方墙，左右各留 2 格边距，底部留 1.5 格给地面
                canvas.drawRect(u * 2, h * 0.45f, w - u * 2, h - u * 1.5f, paint);
                //   门：先把颜色切成纸色（相当于在墙上「挖」出一个门洞），再画中间那块
                paint.setColor(getResources().getColor(R.color.paper));
                canvas.drawRect(w / 2f - u * 1.2f, h * 0.62f, w / 2f + u * 1.2f, h - u * 1.5f, paint);
            } else if (kind == 1) {
                // 列表：三条横杠 + 左侧小方块
                //   三条行，行距 2.6 格，第一条从 2 格高处开始
                for (int i = 0; i < 3; i++) {
                    float y = u * (2f + i * 2.6f);
                    // 每行左边一个小方块（1.6格宽），右边一根长横杠（到右边留 1.6 格）
                    canvas.drawRect(u * 1.6f, y, u * 3.2f, y + u * 1.2f, paint);
                    canvas.drawRect(u * 4.2f, y, w - u * 1.6f, y + u * 1.2f, paint);
                }
            } else {
                // 人形：头 + 肩膀
                //   头：一个半径 2 格的圆，圆心在 30% 高度处
                canvas.drawCircle(w / 2f, h * 0.3f, u * 2f, paint);
                //   肩膀：下面一个梯形，用二次贝塞尔从左上绕到右上，顶边被压成弧形
                Path body = new Path();
                body.moveTo(u * 1.5f, h - u * 1.2f);                    // 左下起点
                body.quadTo(w / 2f, h * 0.45f, w - u * 1.5f, h - u * 1.2f);   // 控制点在上方，弧线往上鼓
                body.close();                                            // 闭合回左下，形成实心梯形
                canvas.drawPath(body, paint);
            }
        }
    }
}
