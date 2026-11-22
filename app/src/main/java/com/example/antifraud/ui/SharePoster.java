package com.example.antifraud.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import com.example.antifraud.R;
import com.example.antifraud.engine.PathRecorder;
import com.example.antifraud.model.CaseSource;
import com.example.antifraud.model.Script;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 复盘海报：纯 Canvas 画一张 1080×1350 的像素风长图，存到 cacheDir/share/poster.png。
 * 不申请权限、不联网；分享时由 PosterProvider 以 content:// 提供给系统分享面板。
 */
public final class SharePoster {

    public static final int WIDTH = 1080, HEIGHT = 1350;
    public static final String FILE_NAME = "poster.png", DIR_NAME = "share";
    private static final int MARGIN = 40, LEFT = 80, RIGHT = WIDTH - LEFT;
    private static final float CONTENT_WIDTH = RIGHT - LEFT;

    private SharePoster() {
    }
    /** 海报目录：PosterProvider 按同一路径读取 */
    public static File dir(Context context) {
        return new File(context.getCacheDir(), DIR_NAME);
    }

    /** 生成海报，成功返回图片文件，失败返回 null；script 可以为 null */
    public static File generate(Context context, Script script, List<PathRecorder.Step> steps,
                                int totalLoss, String title) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        File file = new File(dir(context), FILE_NAME);
        try {
            draw(context, bitmap, script, steps, totalLoss, title);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return null;
            }
            FileOutputStream out = new FileOutputStream(file);
            try {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    return null;
                }
            } finally {
                out.close();
            }
            return file;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            bitmap.recycle(); // 画完回收，别一直占着这 5MB
        }
    }

    private static void draw(Context context, Bitmap bitmap, Script script,
                             List<PathRecorder.Step> steps, int totalLoss, String title) {
        Canvas canvas = new Canvas(bitmap);
        int paper = context.getColor(R.color.paper);
        int card = context.getColor(R.color.card);
        int ink = context.getColor(R.color.ink);
        int brand = context.getColor(R.color.brand);
        int warn = context.getColor(R.color.warn_red);
        int border = context.getColor(R.color.border_dark);
        Paint fill = new Paint();
        fill.setStyle(Paint.Style.FILL);
        fill.setAntiAlias(false);
        fill.setColor(card);
        Paint stroke = new Paint();
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setAntiAlias(false);
        stroke.setColor(border);
        stroke.setStrokeWidth(8f);
        Paint text = new Paint();
        text.setStyle(Paint.Style.FILL);
        text.setAntiAlias(false);
        text.setSubpixelText(false);
        text.setFakeBoldText(true);
        text.setTypeface(Typefaces.pixel(context));
        // 羊皮纸底 + 像素粗边框
        canvas.drawColor(paper);
        canvas.drawRect(MARGIN, MARGIN, WIDTH - MARGIN, HEIGHT - MARGIN, stroke);

        drawText(canvas, text, "反诈剧本杀", LEFT, 178, 72, brand, Paint.Align.LEFT);
        drawText(canvas, text, "走一遍骗局，记住每一步的代价", LEFT, 238, 30, ink, Paint.Align.LEFT);
        divider(canvas, fill, border, 276);
        drawText(canvas, text, "剧本 · " + safe(title, "防骗复盘"), LEFT, 332, 30, ink, Paint.Align.LEFT);
        drawText(canvas, text, totalLoss > 0 ? "本局损失" : "本局结果", LEFT, 396, 30, ink, Paint.Align.LEFT);
        String amount = totalLoss > 0 ? "你损失 ¥" + Ui.money(totalLoss) : "你成功止损 ¥0";
        drawText(canvas, text, amount, LEFT, 494, 84, totalLoss > 0 ? warn : brand, Paint.Align.LEFT);

        // 真实案件损失对照条
        fill.setColor(card);
        canvas.drawRect(LEFT, 524, RIGHT, 592, fill);
        canvas.drawRect(LEFT, 524, RIGHT, 592, stroke);
        String compare = script == null || script.referenceLoss <= 0
                ? "真实案件损失：暂未收录" : "真实案件损失 ¥" + Ui.money(script.referenceLoss);
        drawText(canvas, text, compare, WIDTH / 2f, 568, 36, ink, Paint.Align.CENTER);

        // 中间：至多 3 条高风险步骤摘要
        List<PathRecorder.Step> picks = highRiskSteps(steps);
        drawText(canvas, text, picks.size() >= 3 ? "三处高风险选择" : "高风险时刻", LEFT, 650, 36, brand, Paint.Align.LEFT);
        if (picks.isEmpty()) {
            fill.setColor(card);
            stroke.setStrokeWidth(4f);
            canvas.drawRect(LEFT, 668, RIGHT, 1126, fill);
            canvas.drawRect(LEFT, 668, RIGHT, 1126, stroke);
            stroke.setStrokeWidth(8f);
            drawText(canvas, text, "这一局没有踩中高风险步骤", WIDTH / 2f, 860, 30, ink, Paint.Align.CENTER);
            drawText(canvas, text, "保持冷静，多和家人、老师商量", WIDTH / 2f, 916, 24, ink, Paint.Align.CENTER);
            drawText(canvas, text, "记住：不轻信、不透露、不转账、多核实", WIDTH / 2f, 962, 24, brand, Paint.Align.CENTER);
        } else {
            int cardsTop = 668 + (458 - picks.size() * 158 + 16) / 2;
            for (int i = 0; i < picks.size(); i++) {
                drawStep(context, canvas, fill, stroke, text, picks.get(i), cardsTop + i * 158);
            }
        }

        // 底部：案例来源（取 sources 第一条）+ 合规小字
        divider(canvas, fill, border, 1150);
        CaseSource source = (script == null || script.sources.isEmpty()) ? null : script.sources.get(0);
        drawText(canvas, text, "案例来源", LEFT, 1192, 24, brand, Paint.Align.LEFT);
        text.setTextSize(24);
        String sourceName = source == null ? "暂未收录来源" : "《" + safe(source.name, "未命名来源") + "》";
        String publisher = source == null ? "发布方未登记" : safe(source.publisher, "发布方未登记");
        drawText(canvas, text, fit(text, sourceName, CONTENT_WIDTH), LEFT, 1228, 24, ink, Paint.Align.LEFT);
        drawText(canvas, text, fit(text, publisher, CONTENT_WIDTH), LEFT, 1258, 24, ink, Paint.Align.LEFT);
        drawText(canvas, text, "零权限 · 不联网 · 教学演示作品", RIGHT, 1292, 24, ink, Paint.Align.RIGHT);
    }

    /** 一张高风险步骤卡：第 N 步 → 你选了什么 → 风险标签 */
    private static void drawStep(Context context, Canvas canvas, Paint fill, Paint stroke, Paint text, PathRecorder.Step step, int top) {
        fill.setColor(context.getColor(R.color.card));
        stroke.setColor(context.getColor(R.color.border_dark));
        stroke.setStrokeWidth(4f);
        canvas.drawRect(LEFT, top, RIGHT, top + 142, fill);
        canvas.drawRect(LEFT, top, RIGHT, top + 142, stroke);
        stroke.setStrokeWidth(8f);

        drawText(canvas, text, "第 " + step.index + " 步 · 高风险",
                LEFT + 26, top + 40, 24, context.getColor(R.color.warn_red), Paint.Align.LEFT);
        text.setTextSize(30);
        String pick = "你选了「" + safe(step.choiceText, "（未记录）") + "」";
        drawText(canvas, text, fit(text, pick, CONTENT_WIDTH - 52),
                LEFT + 26, top + 86, 30, context.getColor(R.color.ink), Paint.Align.LEFT);
        drawText(canvas, text, "→ " + safe(step.tag, "未标注风险标签"),
                LEFT + 26, top + 128, 24, context.getColor(R.color.brand), Paint.Align.LEFT);
    }

    /** 取前 3 条高风险步骤 */
    private static List<PathRecorder.Step> highRiskSteps(List<PathRecorder.Step> steps) {
        List<PathRecorder.Step> picks = new ArrayList<>();
        if (steps != null) {
            for (PathRecorder.Step step : steps) {
                if (step != null && "high".equals(step.risk)) {
                    picks.add(step);
                    if (picks.size() == 3) {
                        break;
                    }
                }
            }
        }
        return picks;
    }

    /** 单行截断：放不下就截到能放下，再补 ... */
    private static String fit(Paint paint, String value, float maxWidth) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int end = value.length();
        while (end > 0 && paint.measureText(value, 0, end) > maxWidth) {
            end--;
        }
        return end >= value.length() ? value
                : end <= 1 ? "" : value.substring(0, end - 1) + "...";
    }

    private static void divider(Canvas canvas, Paint fill, int color, int y) {
        fill.setColor(color);
        canvas.drawRect(LEFT, y, RIGHT, y + 4, fill);
    }

    private static void drawText(Canvas canvas, Paint paint, String value, float x, float y, float size, int color, Paint.Align align) {
        paint.setTextSize(size);
        paint.setColor(color);
        paint.setTextAlign(align);
        canvas.drawText(value, x, y, paint);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
