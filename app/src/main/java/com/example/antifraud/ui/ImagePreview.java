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
 */
public final class ImagePreview {

    private ImagePreview() {
    }

    public static void show(final Activity activity, final Bitmap bitmap) {
        if (activity == null || bitmap == null || activity.isFinishing()) {
            return;
        }
        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(0xF2000000);

        final ImageView image = new ImageView(activity);
        image.setImageBitmap(bitmap);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(image, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        final float[] scale = {1f};
        final ScaleGestureDetector scaleDetector = new ScaleGestureDetector(activity,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        scale[0] = Math.max(1f, Math.min(4f, scale[0] * detector.getScaleFactor()));
                        image.setScaleX(scale[0]);
                        image.setScaleY(scale[0]);
                        return true;
                    }
                });

        // 双击：1 倍 ↔ 2 倍
        image.setOnTouchListener(new View.OnTouchListener() {
            private long lastTap = 0L;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                scaleDetector.onTouchEvent(event);
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    long now = SystemClock.uptimeMillis();
                    if (now - lastTap < 300L) {
                        scale[0] = scale[0] > 1.05f ? 1f : 2f;
                        image.animate().scaleX(scale[0]).scaleY(scale[0]).setDuration(160).start();
                        lastTap = 0L;
                        return true;
                    }
                    lastTap = now;
                }
                return true;
            }
        });

        // 右上角关闭
        TextView close = Ui.text(activity, "✕ 关闭", 13, R.color.white);
        close.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 8),
                Ui.dp(activity, 14), Ui.dp(activity, 8));
        close.setBackground(pixelBox(activity, 0xE6000000));
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.TOP | Gravity.END;
        clp.topMargin = Ui.dp(activity, 26);
        clp.rightMargin = Ui.dp(activity, 16);
        root.addView(close, clp);

        // 底部提示
        TextView hint = Ui.text(activity, "双指放大 · 双击放大/还原 · 点「关闭」退出", 11, R.color.white);
        hint.setAlpha(0.78f);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        hlp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        hlp.bottomMargin = Ui.dp(activity, 30);
        root.addView(hint, hlp);

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0x00000000));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
        dialog.show();
    }

    private static android.graphics.drawable.GradientDrawable pixelBox(Activity activity, int color) {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        gd.setColor(color);
        gd.setStroke(Ui.dp(activity, 1), activity.getColor(R.color.border_dark));
        return gd;
    }
}
