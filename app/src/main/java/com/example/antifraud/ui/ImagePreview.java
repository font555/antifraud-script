package com.example.antifraud.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.antifraud.R;

/**
 * 全屏看图：点轮播配图后弹出，可双指放大、双击放大/还原，右上角关闭。
 * 纯 View 实现，不引入任何第三方库。
 *
 * <p><b>谁在用：</b>{@code CarouselView}（首页轮播）给卡片上的配图挂了点击，
 * 点中就把那张 Bitmap 传进来全屏显示。用户看完按「✕ 关闭」或按返回键退出。</p>
 *
 * <p><b>为什么不用 Activity 而用 Dialog：</b>全屏看图只是一次短暂的「放大看一下」，
 * 用 Dialog 不参与 Activity 返回栈，退出时不会触发页面重建，关掉就是原来那页，
 * 体验比新开一个 Activity 再 finish 更顺。</p>
 *
 * <p><b>缩放是怎么做的：</b>不真的去缩放 Bitmap（那样要反复重采样、很吃内存），
 * 而是直接给 ImageView 设 {@code setScaleX/setScaleY} —— 让 GPU 在绘制时放大。
 * 缩放倍数存在 {@code scale[0]} 这个单元素数组里（Java 内部类只能捕获 final 变量，
 * 用一个可变数组当「小盒子」绕开这个限制）。</p>
 */
public final class ImagePreview {

    /** 工具类：不允许 new，全部走静态方法 */
    private ImagePreview() {
    }

    /**
     * 弹出一个全屏看图窗口。
     *
     * @param activity 宿主页面，用来拿主题、资源和弹窗上下文
     * @param bitmap   要看的图；传 null 直接忽略，不会弹一个空白窗口
     *
     * <p>边界处理：activity 已经 finish 时也不再弹（否则会崩，
     * 典型场景是用户连点两下卡片、第一下关页面第二下才触发这段）。</p>
     */
    public static void show(final Activity activity, final Bitmap bitmap) {
        // 三道防线：页面为空、图为空、页面正在关闭 —— 任一命中就直接返回
        if (activity == null || bitmap == null || activity.isFinishing()) {
            return;
        }
        // 用「无标题全屏黑」的系统主题：这样底下不会露出白色窗口背景
        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        // 根容器：FrameLayout，图片铺满、关闭按钮和提示文字浮在上面
        FrameLayout root = new FrameLayout(activity);
        // 95% 不透明的黑底（0xF2）：比纯黑柔和一点，长时间看图不刺眼
        root.setBackgroundColor(0xF2000000);

        // 图片本体：FIT_CENTER 表示「等比缩放到能完整放下，居中，不裁切」
        final ImageView image = new ImageView(activity);
        image.setImageBitmap(bitmap);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(image, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 当前缩放倍数。默认 1 倍（原图大小）；用数组是为了让内部类能改它的值
        final float[] scale = {1f};
        // 双指捏合检测器：系统自带的 ScaleGestureDetector，不用自己算两指距离
        final ScaleGestureDetector scaleDetector = new ScaleGestureDetector(activity,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        // 每帧按手势比例累乘，并夹在 1~4 倍之间：
                        //   下限 1 倍是为了不让图缩成一小块（缩了就没法看）
                        //   上限 4 倍是为了不让图放大到只剩几个像素色块
                        scale[0] = Math.max(1f, Math.min(4f, scale[0] * detector.getScaleFactor()));
                        image.setScaleX(scale[0]);
                        image.setScaleY(scale[0]);
                        return true;   // 吃掉事件，表示这个手势已处理
                    }
                });

        // 双击：1 倍 ↔ 2 倍
        //   触摸监听里同时喂给上面的捏合检测器，两种手势共存互不干扰
        image.setOnTouchListener(new View.OnTouchListener() {
            /** 上一次抬手的时间戳，用来判断「两次点击间隔是否小于 300ms」 */
            private long lastTap = 0L;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // 先把事件喂给捏合手势检测器——双指缩放全靠它处理
                scaleDetector.onTouchEvent(event);
                // 只在「手指抬起」时判定双击，按下和移动都不算
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    long now = SystemClock.uptimeMillis();
                    if (now - lastTap < 300L) {
                        // 距上次抬手不到 300ms → 判定为双击
                        //   当前比 1 倍大就还原成 1 倍，否则放大到 2 倍（在 1 和 2 之间来回切）
                        scale[0] = scale[0] > 1.05f ? 1f : 2f;
                        // 用属性动画过渡 160ms，直接跳变会很突兀
                        image.animate().scaleX(scale[0]).scaleY(scale[0]).setDuration(160).start();
                        lastTap = 0L;   // 清零，防止「三连击」被当成第二次双击
                        return true;
                    }
                    lastTap = now;      // 记住这次抬手时间，等下一次来配对
                }
                return true;   // 始终返回 true：图片区域自己消化所有触摸，不往外传
            }
        });

        // 右上角关闭
        //   文字用「✕ 关闭」，13sp；四周留 14×8dp 的内边距，让点击热区比字大
        TextView close = Ui.text(activity, "✕ 关闭", 13, R.color.white);
        close.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 8),
                Ui.dp(activity, 14), Ui.dp(activity, 8));
        // 90% 不透明的黑底 + 一圈细描边，保证在亮色图片上也能看清文字
        close.setBackground(pixelBox(activity, 0xE6000000));
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();   // 关掉弹窗，回到原来那一页
            }
        });
        // 位置：贴右上角，距顶部 26dp、距右边 16dp（避开状态栏）
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.TOP | Gravity.END;
        clp.topMargin = Ui.dp(activity, 26);
        clp.rightMargin = Ui.dp(activity, 16);
        root.addView(close, clp);

        // 底部提示：告诉用户还有哪些手势可用（11sp 小字，78% 透明度不抢眼）
        TextView hint = Ui.text(activity, "双指放大 · 双击放大/还原 · 点「关闭」退出", 11, R.color.white);
        hint.setAlpha(0.78f);
        // 位置：贴底部居中，距底 30dp
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        hlp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        hlp.bottomMargin = Ui.dp(activity, 30);
        root.addView(hint, hlp);

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            // 窗口背景设成全透明，否则系统主题会再加一层黑/白底，盖住我们自己的 95% 黑
            window.setBackgroundDrawable(new ColorDrawable(0x00000000));
            // 窗口铺满整个屏幕（含状态栏区域），全屏看图才有沉浸感
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
        dialog.show();
    }

    /**
     * 造一个「像素风小方框」当背景：矩形 + 实色填充 + 1dp 深色描边。
     * 本项目零依赖，没有 9-patch 图片，所以用代码拼 GradientDrawable。
     *
     * @param activity 用来换算 dp
     * @param color    填充色（含透明度，例如 0xE6000000 = 90% 黑）
     */
    private static android.graphics.drawable.GradientDrawable pixelBox(Activity activity, int color) {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);   // 矩形（项目里不用圆角）
        gd.setColor(color);                                                 // 填充色
        gd.setStroke(Ui.dp(activity, 1), activity.getColor(R.color.border_dark));   // 1dp 深色描边
        return gd;
    }
}
