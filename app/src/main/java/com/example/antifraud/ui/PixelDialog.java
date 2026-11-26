package com.example.antifraud.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.antifraud.R;
import com.example.antifraud.engine.SettingsStore;

/**
 * 像素风弹窗：替掉系统 AlertDialog（白底圆角、深色按钮，跟整个 App 的像素风完全不搭）。
 *
 * <p>结构：半透明黑遮罩 + 米色像素卡片（圆角描边）+ 标题 + 内容 + 底部按钮行。
 * 点遮罩可关闭；卡片宽度固定（屏宽减两侧留白），高度自适应。</p>
 *
 * <p><b>谁在用：</b>全项目所有需要弹东西的地方——聊天页的「重来/退出」确认、
 * 轮播卡的案例详情+来源、剧本页的分类介绍、我的页的「清除记录」、
 * 反诈口诀页的全文。统一走这里，弹窗样式才不会一页一个样。</p>
 *
 * <p><b>怎么用（链式调用）：</b></p>
 * <pre>
 * PixelDialog.create(this)
 *     .title("确认重来？")
 *     .body("这一局的进度会清空")
 *     .button("确定", true, action, 0)
 *     .button("取消", false, action, 1)
 *     .show();
 * </pre>
 * <p>每个方法都返回 this，所以可以一路点下去；按钮回调只回传一个 int（调用方自己约定含义）。</p>
 *
 * <p><b>布局层级（从下往上）：</b>
 * FrameLayout 根（60% 黑遮罩）
 * └── 卡片 LinearLayout（竖排，px_panel 九宫格背景）
 *     ├── ScrollView → 内容 LinearLayout（标题/说明/正文/字段/菜单项都往这加）
 *     └── 按钮行 LinearLayout（竖排，固定贴在卡片底部，不参与滚动）</p>
 *
 * <p><b>三个「为什么要这么做」的设计点：</b></p>
 * <ol>
 *   <li><b>内容必须能滚动</b>：案例字段多、分类有 6 个，2.1 倍字号下内容一定超屏；
 *       滚动区独立、按钮区固定在下面，用户永远够得着按钮。</li>
 *   <li><b>按钮竖排</b>：横排时 2.1 倍字号下 3 个按钮会把卡片撑破，「关闭」直接被挤出屏幕。</li>
 *   <li><b>字号单独封顶 1.5 倍</b>：见 {@link #applyText}，否则标题会到 31sp、按钮到 25sp。</li>
 * </ol>
 */
public final class PixelDialog {

    /** 按钮/菜单项点击回调：index 是调用方自己传进来的编号，用来区分点了哪个 */
    public interface OnAction {
        void onAction(int index);
    }

    /** 宿主页面：拿主题、颜色、dp 换算、屏幕高度都要用它 */
    private final Activity activity;
    /** 真正的系统 Dialog，全屏无标题主题 */
    private final Dialog dialog;
    /** 米色主卡片（竖排）：上滚动区 + 下按钮区 */
    private final LinearLayout card;
    /** 内容容器：title/meta/body/field/item 这些小方块都往这里加 */
    private final LinearLayout content;
    /** 底部按钮容器：button(...) 加进来的按钮都挂在这，固定在卡片底部不滚动 */
    private final LinearLayout buttonRow;
    /** 内容滚动容器：内容超高时在这里面滚，按钮不受影响 */
    private final ScrollView scroll;

    /** 私有构造：外部统一走 {@link #create(Activity)}，避免忘了传 activity */
    private PixelDialog(Activity activity) {
        this.activity = activity;
        // 全屏 + 无标题：弹窗尺寸交给我们自己控制，系统不要加标题栏
        dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);

        // 根容器 = 整屏遮罩层
        FrameLayout root = new FrameLayout(activity);
        // 60% 黑：背后的界面还看得见一点，但已经明显「压下去」了
        root.setBackgroundColor(0x99000000);

        // 卡片本体：竖排、米色像素风九宫格背景（px_panel 带描边和阴影）
        card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.px_panel);

        // 卡片宽度 = 屏宽 - 左右各 20dp 留白；高度先按内容自适应，show() 里再限高
        int side = Ui.dp(activity, 20);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.CENTER;    // 垂直水平都居中
        clp.leftMargin = side;
        clp.rightMargin = side;
        root.addView(card, clp);

        // 内容容器：竖排，所有文字/菜单项按调用顺序从上往下叠
        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);

        // 内容放进 ScrollView：分类有 6 个、案例字段多，2.1 倍字号下必然超出屏幕，
        // 不滚动的话底部按钮会被挤出可见区域。
        scroll = new ScrollView(activity);
        scroll.setFillViewport(false);              // 内容不满一屏时不要强行撑满
        scroll.setVerticalScrollBarEnabled(true);   // 内容超屏时显示右侧滚动条
        // 内容宽度撑满、高度按内容走
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        card.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        buttonRow = new LinearLayout(activity);
        // 按钮竖着排：横排时 2.1 倍字号下 3 个按钮会把卡片撑破（「关闭」会被挤出屏幕）
        buttonRow.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams brp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        // 按钮区和上面内容之间留 12dp，别贴在一起
        brp.topMargin = Ui.dp(activity, 12);
        card.addView(buttonRow, brp);

        // 点遮罩关闭：只挂在根容器上
        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        // 卡片本身设成可点击 —— 这样点卡片时点击事件被卡片吃掉，
        // 不会冒泡到根容器触发「点遮罩关闭」。这是一个很常用的小技巧。
        card.setClickable(true);

        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            // 窗口底透明：否则系统会给窗口加一层默认底色，遮罩的 60% 黑就不准了
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            // 窗口铺满全屏，遮罩才能盖住整个屏幕（包括状态栏区域）
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    /** 工厂方法：全项目统一用这个入口创建弹窗 */
    public static PixelDialog create(Activity activity) {
        return new PixelDialog(activity);
    }

    /** 卡片内边距随字号放大（标准 16dp，最大约 30dp） */
    private int pad() {
        return SettingsStore.scaleBox(activity, Ui.dp(activity, 16));
    }

    /**
     * 加一个「标题」：大字 + 下面一条分隔线。
     *
     * @param text 标题文字
     * @return this，方便链式调用
     */
    public PixelDialog title(String text) {
        // 15sp 基础字号，最大 22sp（见 applyText 的封顶逻辑）
        TextView tv = Ui.text(activity, text, 15, R.color.ink);
        applyText(tv, 15f, 22f);
        // 行间距额外加 3dp，标题两行时不会挤在一起
        tv.setLineSpacing(Ui.dp(activity, 3), 1f);
        content.addView(tv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        // 分隔线：一条 2dp 高的横线，上留 8dp、下留 4dp
        View line = new View(activity);
        line.setBackgroundColor(activity.getColor(R.color.line));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(2, Ui.dp(activity, 2)));
        llp.topMargin = Ui.dp(activity, 8);
        llp.bottomMargin = Ui.dp(activity, 4);
        content.addView(line, llp);
        return this;
    }

    /**
     * 小字灰色说明（来源、日期等）。
     * 10sp 基础、最大 15sp，颜色用 ink_hint（比正文更灰），视觉上退到后面。
     */
    public PixelDialog meta(String text) {
        TextView tv = Ui.text(activity, text, 10, R.color.ink_hint);
        applyText(tv, 10f, 15f);
        tv.setLineSpacing(Ui.dp(activity, 3), 1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(activity, 6);   // 和上面的内容隔开 6dp
        content.addView(tv, lp);
        return this;
    }

    /**
     * 正文（自动换行、行距放宽）。
     * 12sp 基础、最大 18sp；行间距额外 5dp，长段落也不会糊成一片。
     */
    public PixelDialog body(String text) {
        TextView tv = Ui.text(activity, text, 12, R.color.ink);
        applyText(tv, 12f, 18f);
        tv.setLineSpacing(Ui.dp(activity, 5), 1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(activity, 10);   // 正文和标题之间留 10dp
        content.addView(tv, lp);
        return this;
    }

    /**
     * 带标签的字段：label 小字灰、value 正常色。
     * 案例详情页的「涉案金额：…」「发生时间：…」都是这个样式。
     *
     * @param label 字段名（小字灰）
     * @param value 字段值（正常色，可换行）
     */
    public PixelDialog field(String label, String value) {
        // 先建一个竖排小盒子装「标签 + 值」，整体作为一个字段
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(activity, 12);   // 字段之间间隔 12dp
        box.setLayoutParams(blp);

        // 标签：10sp 小字，灰色
        TextView lab = Ui.text(activity, label, 10, R.color.ink_hint);
        applyText(lab, 10f, 15f);
        box.addView(lab);
        // 值：11sp，用 ink_secondary（比正文淡一档的墨色）
        TextView val = Ui.text(activity, value, 11, R.color.ink_secondary);
        applyText(val, 11f, 16f);
        val.setLineSpacing(Ui.dp(activity, 4), 1f);
        val.setPadding(0, Ui.dp(activity, 4), 0, 0);   // 和上面的标签隔 4dp
        box.addView(val);
        content.addView(box);
        return this;
    }

    /**
     * 一条可点的"菜单项"：左侧色条 + 主标题 + 副说明 + 右箭头。
     * 剧本页的分类选择、我的页的功能入口都是这种行。
     *
     * @param accentColor 左侧竖条的颜色（一般用分类色，用来区分不同条目）
     * @param title       主标题
     * @param desc        副说明，可为 null 或空（空就不显示第二行）
     * @param action      点击回调，可为 null
     * @param index       回调时回传的编号
     */
    public PixelDialog item(int accentColor, String title, String desc,
                            final OnAction action, final int index) {
        // 整行：横排、米色纸底（px_paper），四周内边距随字号放大
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundResource(R.drawable.px_paper);
        int p = SettingsStore.scaleBox(activity, Ui.dp(activity, 12));
        row.setPadding(p, p, p, p);

        // 左侧色条：宽 4dp（最小 3px），高度撑满整行
        View bar = new View(activity);
        bar.setBackgroundColor(accentColor);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                Math.max(3, Ui.dp(activity, 4)), LinearLayout.LayoutParams.MATCH_PARENT);
        barLp.rightMargin = SettingsStore.scaleBox(activity, Ui.dp(activity, 10));   // 色条右边留 10dp
        row.addView(bar, barLp);

        // 中间文字区：宽度 0 + weight 1，占满色条和箭头之间的剩余空间
        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        // 主标题：13sp，最大 19sp
        TextView t = Ui.text(activity, title, 13, R.color.ink);
        applyText(t, 13f, 19f);
        texts.addView(t);
        // 副说明：只有真的有内容才加，避免空行占高度
        if (desc != null && !desc.isEmpty()) {
            TextView d = Ui.text(activity, desc, 10, R.color.ink_hint);
            applyText(d, 10f, 15f);
            d.setPadding(0, SettingsStore.scaleBox(activity, Ui.dp(activity, 3)), 0, 0);
            d.setLineSpacing(Ui.dp(activity, 2), 1f);
            texts.addView(d);
        }
        row.addView(texts);

        // 右箭头「›」：品牌红，16sp，提示这一行可以点进去
        TextView arrow = Ui.text(activity, "›", 16, R.color.brand);
        row.addView(arrow);

        // 整行外边距：上边 8dp，和上一条菜单隔开
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = SettingsStore.scaleBox(activity, Ui.dp(activity, 8));
        row.setLayoutParams(rlp);
        row.setClickable(true);
        // 点击：先关掉弹窗，再回调 —— 顺序很重要，先 dismiss 才不会出现
        // 「回调里又弹了新窗，旧窗还压在下面」的叠窗问题
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                if (action != null) {
                    action.onAction(index);
                }
            }
        });
        content.addView(row);
        return this;
    }

    /**
     * 底部按钮。
     *
     * @param label   按钮文字
     * @param primary true 用品牌红实底（主操作，如「确定」「清除」），
     *                false 用米色描边（次操作，如「取消」「关闭」）
     * @param action  点击回调，可为 null
     * @param index   回调时回传的编号
     */
    public PixelDialog button(String label, boolean primary, final OnAction action, final int index) {
        TextView btn = new TextView(activity);
        Ui.pixel(btn);      // 换成像素字体
        btn.setText(label);
        // 按钮文字不跟着字号全量放大：2.1 倍下「复制链接」会被压成一竖条。
        // 这里给一个上限，按钮始终横向舒展、文字完整。
        applyText(btn, 12f, 17f);
        btn.setGravity(Gravity.CENTER);
        btn.setMaxLines(1);     // 单行，配合上面的字号封顶，保证按钮不会长成两行高
        // 背景：主操作用实底（px_button），次操作用描边（px_option）
        btn.setBackgroundResource(primary ? R.drawable.px_button : R.drawable.px_option);
        // 文字色：实底按钮用白字，描边按钮用墨色字
        btn.setTextColor(activity.getColor(primary ? R.color.white : R.color.ink));
        // 内边距：左右 14dp、上下 9dp，都随字号放大（有上限）
        int p = SettingsStore.scaleBox(activity, Ui.dp(activity, 9));
        btn.setPadding(SettingsStore.scaleBox(activity, Ui.dp(activity, 14)), p,
                SettingsStore.scaleBox(activity, Ui.dp(activity, 14)), p);
        // 最小高度 38dp：手指点得到，也符合无障碍最小点击区
        btn.setMinHeight(SettingsStore.scaleBox(activity, Ui.dp(activity, 38)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        // 不是第一个按钮就往上留 6dp，让竖排按钮之间有缝
        if (buttonRow.getChildCount() > 0) {
            lp.topMargin = SettingsStore.scaleBox(activity, Ui.dp(activity, 6));
        }
        btn.setLayoutParams(lp);
        btn.setClickable(true);
        // 和菜单项一样：先 dismiss 再回调，避免叠窗
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                if (action != null) {
                    action.onAction(index);
                }
            }
        });
        buttonRow.addView(btn);
        return this;
    }

    /**
     * 弹窗内文字的字号上限。
     * 2.1 倍时若直接跟随，标题会到 31sp、按钮到 25sp，一个按钮就占掉 3 行高度。
     * 这里把弹窗里的字号压到 1.5 倍封顶，既照顾视力，又保证按钮/字段排得下。
     *
     * <p>举例：标准档 1.0 → 1.0；特大 1.7 → 1.5（封顶）；超大 2.1 → 1.5（封顶）。</p>
     */
    private float dialogScale() {
        return Math.min(1.5f, SettingsStore.fontScale(activity));
    }

    /**
     * 弹窗字号统一走 px：BaseActivity 的 fontScale 会把 setTextSize(sp) 再乘一次，
     * 直接用 sp 会让「封顶 1.5 倍」失效（20sp × 2.1 = 42sp，按钮被压扁）。
     *
     * <p>换算过程：目标 sp = min(maxSp, baseSp × dialogScale())，
     * 再乘 density 转成 px，最后用 COMPLEX_UNIT_PX 设进去——
     * 因为单位已经是 px，系统的 fontScale 就不会再乘一次了。</p>
     *
     * @param tv     要设字号的文本控件
     * @param baseSp 标准档下的字号（sp）
     * @param maxSp  这个控件允许的最大字号（sp）
     */
    private void applyText(TextView tv, float baseSp, float maxSp) {
        // 先按 1.5 倍封顶算出目标 sp，再和该控件自己的上限取小的那个
        float sp = Math.min(maxSp, baseSp * dialogScale());
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                sp * activity.getResources().getDisplayMetrics().density);
    }

    /** 允许点弹窗外部关闭（默认是不允许的，只能点按钮或遮罩） */
    public PixelDialog cancelable() {
        dialog.setCanceledOnTouchOutside(true);
        return this;
    }

    /**
     * 真正显示弹窗。
     *
     * <p>为什么不在构造时就 show：调用方要先把 title/body/button 一个个加进去，
     * 等内容齐了再显示，否则会先闪一下空卡片。</p>
     *
     * <p>这里做了两件事：把卡片内边距设好；把卡片高度限制在屏幕 76% 以内，
     * 超出的部分交给里面的 ScrollView 滚动，保证底部按钮永远可见。</p>
     */
    public void show() {
        // 内边距随字号放大，但不会超过 scaleBox 内部的 1.9 倍上限
        int p = pad();
        card.setPadding(p, p, p, p);

        // 限高：卡片最高占屏幕 76%，超出部分让 ScrollView 内部滚动，
        // 这样底部按钮永远固定在可见范围内（2.1 倍字号 + 6 个分类时尤其重要）
        int screenH = activity.getResources().getDisplayMetrics().heightPixels;
        final int maxH = (int) (screenH * 0.76f);
        // 先确保卡片高度是「自适应」，让内容自己撑开，量出来的高度才是真实内容高度
        ViewGroup.LayoutParams lp = card.getLayoutParams();
        if (lp instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) lp).height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        dialog.show();

        // 布局完成后再量：超出就把 ScrollView 限到剩余高度
        //   必须 post 到下一帧，因为 show() 返回时 View 还没真正测量布局完，
        //   此刻 getHeight() 还是 0，判断会永远不成立。
        card.post(new Runnable() {
            @Override
            public void run() {
                if (card.getHeight() > maxH) {
                    // 「固定不动」的那部分高度：卡片上下内边距 + 按钮行高度 + 按钮行上边距
                    int fixed = card.getPaddingTop() + card.getPaddingBottom()
                            + buttonRow.getHeight()
                            + ((LinearLayout.LayoutParams) buttonRow.getLayoutParams()).topMargin;
                    // 留给滚动区的高度；至少留 120dp，避免极端情况下滚动区被压成一条缝
                    int avail = Math.max(Ui.dp(activity, 120), maxH - fixed);
                    ViewGroup.LayoutParams slp = scroll.getLayoutParams();
                    slp.height = avail;      // 给 ScrollView 一个明确高度，它才知道要滚
                    scroll.setLayoutParams(slp);
                    card.getLayoutParams().height = maxH;   // 卡片本身固定成 76% 屏高
                    card.requestLayout();                   // 请求重新布局，改动才会生效
                }
            }
        });
    }

    /** 关闭弹窗：已经关了就不重复关（重复 dismiss 在某些机型上会抛异常） */
    public void dismiss() {
        if (dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    /** 当前是否正在显示（调用方据此避免重复弹窗） */
    public boolean isShowing() {
        return dialog.isShowing();
    }

    /** 拿到内部的原始 Dialog，用于设置返回键、外部点击等系统级行为 */
    public Dialog raw() {
        return dialog;
    }

    /** 注册关闭监听（例如弹窗关掉后要把某个标志位复位） */
    public void setOnDismiss(Dialog.OnDismissListener l) {
        dialog.setOnDismissListener(l);
    }
}
