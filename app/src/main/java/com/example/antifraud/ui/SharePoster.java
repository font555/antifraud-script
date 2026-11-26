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
 *
 * <p><b>谁在调用：</b>{@link ReviewActivity#sharePoster}（复盘页的"生成分享海报"按钮）。</p>
 *
 * <p><b>为什么用 Canvas 画而不是用布局截图：</b>截图会受屏幕宽度、字号档位影响，
 * 不同手机出图不一样，还可能把状态栏、滚动条截进去。用 Canvas 按固定 1080×1350
 * 坐标画，任何设备出图都一模一样，适合当作品展示物料。</p>
 *
 * <p><b>坐标系约定：</b>所有数字都是"画布像素"，不是 dp。画布固定 1080 宽，
 * 左右各留 80 的安全边（{@link #LEFT} 到 {@link #RIGHT}），最外圈 40 像素是装饰边框，
 * 中间 {@link #CONTENT_WIDTH} = 920 是正文可用宽度，所有长文本都按这个宽度截断。</p>
 *
 * <p><b>版面自上而下分五段：</b></p>
 * <ol>
 *   <li>y≈178-238：主标题 + 副标题；</li>
 *   <li>y≈332-494：剧本名 + 本局损失（大数字）；</li>
 *   <li>y≈524-592：真实案件损失对照条；</li>
 *   <li>y≈650-1126：至多 3 张高风险步骤卡（没有高风险时显示鼓励文案）；</li>
 *   <li>y≈1150-1292：来源信息 + 合规小字。</li>
 * </ol>
 */
public final class SharePoster {

    /**
     * 画布尺寸与输出位置。
     * WIDTH × HEIGHT = 1080 × 1350（约 4:5，适合手机竖屏分享）；
     * FILE_NAME 是输出文件名，PosterProvider 按同名字读取；
     * DIR_NAME 是 cacheDir 下的子目录，系统清理缓存时会被回收，不占用户空间。
     */
    public static final int WIDTH = 1080, HEIGHT = 1350;
    public static final String FILE_NAME = "poster.png", DIR_NAME = "share";
    /**
     * 版面安全区：最外圈 MARGIN=40 是装饰边框，内容从 LEFT=80 开始、到 RIGHT 结束，
     * CONTENT_WIDTH = 920 是所有长文本的可用宽度（fit() 按这个宽度截断）。
     */
    private static final int MARGIN = 40, LEFT = 80, RIGHT = WIDTH - LEFT;
    private static final float CONTENT_WIDTH = RIGHT - LEFT;

    /** 私有构造：纯静态工具类，不允许 new */
    private SharePoster() {
    }

    /**
     * 海报目录：PosterProvider 按同一路径读取。
     *
     * <p>用 cacheDir 而不是外部存储：不需要任何权限，应用卸载时自动清掉，
     * 也不会在用户相册里留垃圾。</p>
     *
     * @param context 任意 Context
     * @return cacheDir/share 这个目录对象（目录可能还不存在，由 generate 负责创建）
     */
    public static File dir(Context context) {
        return new File(context.getCacheDir(), DIR_NAME);
    }

    /**
     * 生成海报，成功返回图片文件，失败返回 null；script 可以为 null。
     *
     * <p>失败路径全部返回 null 而不抛异常：调用方（复盘页）拿到 null 就弹一句
     * "海报生成失败"，页面不会崩。</p>
     *
     * @param context   任意 Context，用来取颜色资源和缓存目录
     * @param script    当前剧本，用来取剧本名与真实案件损失；为 null 时各项降级显示"暂未收录"
     * @param steps     本局走过的步骤，用来挑出高风险步骤摘要
     * @param totalLoss 本局损失总额（元），0 表示成功止损
     * @param title     海报上显示的剧本标题
     * @return 生成好的 poster.png；任何一步失败都返回 null
     */
    public static File generate(Context context, Script script, List<PathRecorder.Step> steps,
                                int totalLoss, String title) {
        // 1) 先按固定尺寸开一块画布内存（1080×1350×4 字节 ≈ 5.8MB）
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        File file = new File(dir(context), FILE_NAME);
        try {
            // 2) 把内容画进这块位图
            draw(context, bitmap, script, steps, totalLoss, title);
            // 3) 确保输出目录存在；mkdirs 失败说明存储不可写，直接算生成失败
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return null;
            }
            // 4) 压缩成 PNG 写盘。用 try-finally 保证无论成功失败流都会关闭
            FileOutputStream out = new FileOutputStream(file);
            try {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    return null;   // 压缩失败（极少见），当作生成失败
                }
            } finally {
                out.close();
            }
            return file;
        } catch (Exception e) {
            // 存储满、目录被占用等异常都走这里：只打日志，不向上抛
            e.printStackTrace();
            return null;
        } finally {
            bitmap.recycle(); // 画完回收，别一直占着这 5MB
        }
    }

    /**
     * 真正的绘制逻辑：把整张海报从上到下画一遍。
     *
     * <p>分三个画笔，是 Canvas 的常规用法：</p>
     * <ul>
     *   <li>{@code fill}：填充用（色块、分隔线），颜色会被各段复用前重置；</li>
     *   <li>{@code stroke}：描边用，线宽按段落不同在 8 / 4 之间来回切；</li>
     *   <li>{@code text}：文字用，统一像素字体 + 关抗锯齿 + 伪加粗，字号每段重设。</li>
     * </ul>
     *
     * @param context   取颜色资源
     * @param bitmap    目标位图
     * @param script    剧本（可为 null）
     * @param steps     本局步骤
     * @param totalLoss 本局损失
     * @param title     剧本标题
     */
    private static void draw(Context context, Bitmap bitmap, Script script,
                             List<PathRecorder.Step> steps, int totalLoss, String title) {
        Canvas canvas = new Canvas(bitmap);
        // 先把配色一次性取出来，后面各段直接引用，避免反复查资源
        int paper = context.getColor(R.color.paper);          // 羊皮纸底色
        int card = context.getColor(R.color.card);            // 卡片白
        int ink = context.getColor(R.color.ink);              // 主文字色
        int brand = context.getColor(R.color.brand);          // 主题色（强调、标题）
        int warn = context.getColor(R.color.warn_red);        // 警示红（损失金额）
        int border = context.getColor(R.color.border_dark);   // 深色描边
        Paint fill = new Paint();
        fill.setStyle(Paint.Style.FILL);          // 实心填充
        fill.setAntiAlias(false);                 // 关抗锯齿，保持像素硬边
        fill.setColor(card);
        Paint stroke = new Paint();
        stroke.setStyle(Paint.Style.STROKE);      // 只描边不填充
        stroke.setAntiAlias(false);
        stroke.setColor(border);
        stroke.setStrokeWidth(8f);                // 边框默认 8 像素粗，像素风要够厚
        Paint text = new Paint();
        text.setStyle(Paint.Style.FILL);
        text.setAntiAlias(false);                 // 文字也关抗锯齿，硬边才像素
        text.setSubpixelText(false);
        text.setFakeBoldText(true);               // 像素字体无粗体字重，靠加粗提升可读性
        text.setTypeface(Typefaces.pixel(context)); // 与 App 内保持同一款字体
        // 羊皮纸底 + 像素粗边框
        canvas.drawColor(paper);                                          // 整张铺底色
        canvas.drawRect(MARGIN, MARGIN, WIDTH - MARGIN, HEIGHT - MARGIN, stroke); // 外圈 40px 装饰框

        // —— 第 1 段：主标题与副标题 ——
        drawText(canvas, text, "反诈剧本杀", LEFT, 178, 72, brand, Paint.Align.LEFT);
        drawText(canvas, text, "走一遍骗局，记住每一步的代价", LEFT, 238, 30, ink, Paint.Align.LEFT);
        divider(canvas, fill, border, 276);   // 标题区与内容区之间的分隔线
        // —— 第 2 段：剧本名 + 本局损失 ——
        drawText(canvas, text, "剧本 · " + safe(title, "防骗复盘"), LEFT, 332, 30, ink, Paint.Align.LEFT);
        // 有损失说"本局损失"，成功止损说"本局结果"，措辞上不刺激用户
        drawText(canvas, text, totalLoss > 0 ? "本局损失" : "本局结果", LEFT, 396, 30, ink, Paint.Align.LEFT);
        String amount = totalLoss > 0 ? "你损失 ¥" + Ui.money(totalLoss) : "你成功止损 ¥0";
        // 84 号大字：这是整张海报最想让人一眼看到的数字
        drawText(canvas, text, amount, LEFT, 494, 84, totalLoss > 0 ? warn : brand, Paint.Align.LEFT);

        // 真实案件损失对照条
        fill.setColor(card);
        canvas.drawRect(LEFT, 524, RIGHT, 592, fill);                    // 对照条底色
        canvas.drawRect(LEFT, 524, RIGHT, 592, stroke);                  // 对照条描边
        String compare = script == null || script.referenceLoss <= 0
                ? "真实案件损失：暂未收录" : "真实案件损失 ¥" + Ui.money(script.referenceLoss);
        drawText(canvas, text, compare, WIDTH / 2f, 568, 36, ink, Paint.Align.CENTER); // 居中显示

        // 中间：至多 3 条高风险步骤摘要
        List<PathRecorder.Step> picks = highRiskSteps(steps);
        drawText(canvas, text, picks.size() >= 3 ? "三处高风险选择" : "高风险时刻", LEFT, 650, 36, brand, Paint.Align.LEFT);
        if (picks.isEmpty()) {
            // 没有高风险步骤 = 全程做对了，画一张大的鼓励卡
            fill.setColor(card);
            stroke.setStrokeWidth(4f);                                    // 内框用细一点的线（4px）
            canvas.drawRect(LEFT, 668, RIGHT, 1126, fill);
            canvas.drawRect(LEFT, 668, RIGHT, 1126, stroke);
            stroke.setStrokeWidth(8f);                                    // 画完立刻恢复 8px，不影响后面
            drawText(canvas, text, "这一局没有踩中高风险步骤", WIDTH / 2f, 860, 30, ink, Paint.Align.CENTER);
            drawText(canvas, text, "保持冷静，多和家人、老师商量", WIDTH / 2f, 916, 24, ink, Paint.Align.CENTER);
            drawText(canvas, text, "记住：不轻信、不透露、不转账、多核实", WIDTH / 2f, 962, 24, brand, Paint.Align.CENTER);
        } else {
            // 有 1~3 张卡时，把它们在 668~1126 这段高度里垂直居中：
            // 每张卡 142 高 + 16 间距 = 158，先算出总高，再算起始 y，视觉才平衡
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
        // 来源名可能很长，按内容区宽度自动截断（fit），绝不溢出边框
        drawText(canvas, text, fit(text, sourceName, CONTENT_WIDTH), LEFT, 1228, 24, ink, Paint.Align.LEFT);
        drawText(canvas, text, fit(text, publisher, CONTENT_WIDTH), LEFT, 1258, 24, ink, Paint.Align.LEFT);
        // 右下角合规声明：作品定位一句话说清，避免被误认为是官方出品
        drawText(canvas, text, "零权限 · 不联网 · 教学演示作品", RIGHT, 1292, 24, ink, Paint.Align.RIGHT);
    }

    /**
     * 一张高风险步骤卡：第 N 步 → 你选了什么 → 风险标签。
     *
     * <p>卡片占 142 像素高，内部三行文字分别压在 top+40 / top+86 / top+128 三条基线上，
     * 左内边距 26 像素。卡片外框用 4 像素细线，画完立刻把共享的 stroke 画笔恢复成 8，
     * 否则后面画外框时会变细——这是共用画笔时必须注意的"状态污染"问题。</p>
     *
     * @param context 取颜色资源
     * @param canvas  目标画布
     * @param fill    填充画笔（本方法会改它的颜色）
     * @param stroke  描边画笔（本方法会改它的颜色和线宽，出口处恢复线宽）
     * @param text    文字画笔（本方法会改它的字号）
     * @param step    要画的这一步记录
     * @param top     卡片顶边的 y 坐标
     */
    private static void drawStep(Context context, Canvas canvas, Paint fill, Paint stroke, Paint text, PathRecorder.Step step, int top) {
        fill.setColor(context.getColor(R.color.card));
        stroke.setColor(context.getColor(R.color.border_dark));
        stroke.setStrokeWidth(4f);                                        // 卡片内框用细线
        canvas.drawRect(LEFT, top, RIGHT, top + 142, fill);               // 卡片底
        canvas.drawRect(LEFT, top, RIGHT, top + 142, stroke);             // 卡片框
        stroke.setStrokeWidth(8f);                                        // 立刻恢复，避免影响后续绘制

        drawText(canvas, text, "第 " + step.index + " 步 · 高风险",
                LEFT + 26, top + 40, 24, context.getColor(R.color.warn_red), Paint.Align.LEFT);
        text.setTextSize(30);
        String pick = "你选了「" + safe(step.choiceText, "（未记录）") + "」";
        // 选项文字可能很长，按"卡片宽 - 左右内边距"截断，保证单行不换行不越界
        drawText(canvas, text, fit(text, pick, CONTENT_WIDTH - 52),
                LEFT + 26, top + 86, 30, context.getColor(R.color.ink), Paint.Align.LEFT);
        drawText(canvas, text, "→ " + safe(step.tag, "未标注风险标签"),
                LEFT + 26, top + 128, 24, context.getColor(R.color.brand), Paint.Align.LEFT);
    }

    /**
     * 取前 3 条高风险步骤。
     *
     * <p>为什么要限制 3 条：中间区域高度固定 458 像素，一张卡 142 高，
     * 3 张已经占满；再多就得压缩字号或换版面，反而看不清。</p>
     *
     * @param steps 本局全部步骤，允许为 null
     * @return 最多 3 条高风险步骤；没有则返回空列表（不返回 null）
     */
    private static List<PathRecorder.Step> highRiskSteps(List<PathRecorder.Step> steps) {
        List<PathRecorder.Step> picks = new ArrayList<>();
        if (steps != null) {
            for (PathRecorder.Step step : steps) {
                if (step != null && "high".equals(step.risk)) {   // 常量在前，防 step.risk 为 null
                    picks.add(step);
                    if (picks.size() == 3) {
                        break;   // 够 3 条就停，不用再扫
                    }
                }
            }
        }
        return picks;
    }

    /**
     * 单行截断：放不下就截到能放下，再补 ...。
     *
     * <p>做法是从末尾一个字一个字往回退，用 {@link Paint#measureText} 量实际宽度，
     * 直到放得下为止。像素字体每个字宽度不同，不能按"字符数"粗略估算。</p>
     *
     * @param paint    已设好字号的文字画笔（宽度计算必须用同一支笔，字号不同结果不同）
     * @param value    原始文本，null 或空串直接返回空串
     * @param maxWidth 允许的最大宽度（像素）
     * @return 截断后的文本；宽到连一个字都放不下时返回空串
     */
    private static String fit(Paint paint, String value, float maxWidth) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int end = value.length();
        while (end > 0 && paint.measureText(value, 0, end) > maxWidth) {
            end--;   // 一次退一个字，直到整行宽度达标
        }
        return end >= value.length() ? value
                : end <= 1 ? "" : value.substring(0, end - 1) + "...";   // 截掉最后一位给省略号腾位置
    }

    /**
     * 画一条水平分隔线（4 像素高）。
     *
     * @param canvas 目标画布
     * @param fill   填充画笔（会被改成指定颜色）
     * @param color  线条颜色
     * @param y      线条顶边的 y 坐标
     */
    private static void divider(Canvas canvas, Paint fill, int color, int y) {
        fill.setColor(color);
        canvas.drawRect(LEFT, y, RIGHT, y + 4, fill);
    }

    /**
     * 统一入口：设字号、颜色、对齐方式，再画一行文字。
     *
     * <p>把三行重复代码收成一个方法，是为了保证整张海报的文字渲染参数完全一致
     * （同一支画笔被反复改状态，散写极易漏设某一项）。</p>
     *
     * @param canvas 目标画布
     * @param paint  文字画笔（本方法会改它的字号/颜色/对齐）
     * @param value  要画的文字
     * @param x      基线起点 x
     * @param y      基线 y（注意是文字基线，不是文字顶边）
     * @param size   字号（像素）
     * @param color  文字颜色
     * @param align  水平对齐方式：LEFT 左对齐 / CENTER 居中 / RIGHT 右对齐
     */
    private static void drawText(Canvas canvas, Paint paint, String value, float x, float y, float size, int color, Paint.Align align) {
        paint.setTextSize(size);
        paint.setColor(color);
        paint.setTextAlign(align);
        canvas.drawText(value, x, y, paint);
    }

    /**
     * 空值兜底：null、空串、纯空格都换成 fallback。
     *
     * <p>剧本 JSON 是人工编辑的，"来源名忘了填"这种情况一定会发生，
     * 出了空串海报上就会留一块白，用兜底文案比露白好。</p>
     *
     * @param value    原始值
     * @param fallback 兜底文案
     * @return 去空格后的非空文本
     */
    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
