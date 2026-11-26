package com.example.antifraud.engine;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;

/**
 * 轻量运行日志：只在本机记录最近 400 行，写进 filesDir/logs/app.log。
 *
 * <p>用途是方便自己复盘（"刚才那局为什么扣了 2 万""导入剧本失败在哪一步"），
 * 不联网、不上传、不申请权限。入口藏在设置页的「关于」标题上长按。</p>
 *
 * <p>整体结构是"内存 + 文件"两份：
 * 内存里放一个最多 400 行的队列，保证日志页能秒开；
 * 文件里追加写，保证关掉 App 再打开还看得到。两边都不用数据库，
 * 因为只需要"最近发生了什么"，不需要长期统计。</p>
 *
 * <p>谁在调用：BaseActivity 在页面首次创建时调 init 一次；ScriptStore 保存/导入/删除剧本时打点；
 * UserStore 在通关 +1、累计损失变化时打点；LogActivity 调 dump 把日志显示出来。</p>
 *
 * <p>注意：所有写日志的地方都吞掉异常（catch 后什么都不做）。
 * 这是故意的——日志本身不能成为崩溃源，宁可少记一条，也不能因为磁盘满了把主流程搞挂。</p>
 */
public class AppLog {

    /** 内存里最多保留多少行；超过就从头丢，保证内存占用恒定 */
    private static final int MAX_LINES = 400;
    /** 日志文件超过多少字节就清空重写，防止无限增长占满手机空间 */
    private static final long MAX_FILE_BYTES = 256 * 1024;

    /** 内存里的日志队列，按时间从旧到新排队；用 LinkedList 是因为要频繁"从尾巴加、从头删" */
    private static final LinkedList<String> BUFFER = new LinkedList<>();
    /** 日志文件句柄；init 之前是 null，此时只写内存不写文件 */
    private static File logFile;
    /** 时间格式，只精确到秒（HH:mm:ss）；固定用 Locale.US，避免某些系统上出现非数字的时间 */
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss", Locale.US);

    /** 私有构造：这是个纯静态工具类，不允许 new */
    private AppLog() {
    }

    /**
     * 在 Application/首个 Activity 里调一次，顺便接管崩溃日志。
     *
     * <p>做三件事：一是把日志目录建出来（filesDir/logs），二是把自己的崩溃处理器
     * 挂到默认处理器上（崩溃原因会被记进日志文件），三是打一条「日志已启动」当作分隔符。
     * 重复调用是安全的：logFile 已经有值就直接返回，不会重复挂处理器。</p>
     *
     * @param context 任意 Context，只用它来拿 filesDir，不会存起来，不会泄漏
     */
    public static void init(Context context) {
        if (logFile != null) {
            return; // 已经初始化过，直接返回，避免重复挂崩溃处理器
        }
        try {
            File dir = new File(context.getFilesDir(), "logs");
            if (!dir.exists()) {
                dir.mkdirs(); // 目录可能不存在，先建；返回值不管，后面写文件失败也会被 catch 住
            }
            logFile = new File(dir, "app.log");
        } catch (Exception ignored) {
            // 建目录失败也不影响 App 运行，只是这次没有文件日志
        }
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler(); // 记住原来的处理器，崩了以后还要交给它（系统才会弹"已停止"）
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                e(t.getName(), "崩溃：" + e.getClass().getSimpleName() + " " + e.getMessage(), e); // 先把崩溃写进日志，再放行
                if (old != null) {
                    old.uncaughtException(t, e); // 交回原来的处理器，保持系统默认行为
                }
            }
        });
        i("APP", "日志已启动");
    }

    /**
     * 记一条普通信息日志（info）。
     *
     * @param tag 小标签，用来区分是哪个模块打的，例如 "USER"、"STORE"
     * @param msg 正文，写清楚发生了什么，不要写"In here"这种没信息量的话
     */
    public static void i(String tag, String msg) {
        write("I", tag, msg, null);
    }

    /**
     * 记一条错误日志（error），可以带异常。
     *
     * @param tag 模块标签
     * @param msg 出错的简短说明，例如「读取内置剧本列表失败」
     * @param t   异常对象；传 null 就等同于一条错误级别的普通文字
     */
    public static void e(String tag, String msg, Throwable t) {
        write("E", tag, msg, t);
    }

    /**
     * 真正的写入逻辑：拼一行文本 → 进内存队列 → 追加到文件。
     *
     * <p>加了 synchronized，因为日志可能从任意线程打（包括崩溃线程），
     * 同时写文件会串行成乱码。这个方法只在类内部用。</p>
     *
     * @param level 级别字母，I/ E
     * @param tag   模块标签
     * @param msg   正文
     * @param t     异常，可为 null
     */
    private static synchronized void write(String level, String tag, String msg, Throwable t) {
        String line = TIME.format(new Date()) + " " + level + "/" + tag + "  " + msg; // 行首时间戳，方便看"哪一步在前"
        BUFFER.addLast(line);
        while (BUFFER.size() > MAX_LINES) {
            BUFFER.removeFirst(); // 超出上限就丢最老的一行，内存占用永远是 400 行以内
        }
        StringBuilder sb = new StringBuilder(line).append('\n');
        if (t != null) {
            sb.append("    ").append(t.getClass().getName()); // 先把异常类名单独写一行
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < Math.min(6, st.length); i++) { // 堆栈最多写 6 层，写全了会把日志刷满
                sb.append("\n      at ").append(st[i]);
            }
            sb.append('\n');
        }
        if (logFile == null) {
            return; // 还没 init，本次只存在于内存里，直接结束
        }
        try {
            if (logFile.length() > MAX_FILE_BYTES) {
                FileOutputStream truncate = new FileOutputStream(logFile, false); // 第二个参数 false = 覆盖写，等于清空
                truncate.close();
            }
            FileOutputStream out = new FileOutputStream(logFile, true); // 第二个参数 true = 追加模式，不覆盖历史日志
            out.write(sb.toString().getBytes("UTF-8")); // 固定 UTF-8，中文才不会变问号
            out.close();
        } catch (Exception ignored) {
            // 写文件失败（磁盘满、存储被占用等）直接忽略，不能影响 App 主流程
        }
    }

    /** 返回最近日志（含文件里更早的部分最多 400 行） */
    public static synchronized String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append("行数 ").append(BUFFER.size()).append(" / 最多 ").append(MAX_LINES).append('\n'); // 第一行先报个概况，方便看是不是已经满了
        if (logFile != null) {
            sb.append("文件：").append(logFile.getAbsolutePath()).append('\n'); // 显示真实路径，方便用文件管理器对照
        }
        sb.append("--------------------------------\n");
        for (String s : BUFFER) {
            sb.append(s).append('\n');
        }
        return sb.toString();
    }

    /** 清空日志：内存队列清掉、文件内容清掉，再留一条「日志已清空」作为新起点 */
    public static synchronized void clear() {
        BUFFER.clear();
        if (logFile != null) {
            try {
                new FileOutputStream(logFile, false).close(); // 覆盖写一个空文件，等于清空
            } catch (Exception ignored) {
                // 清不掉也不报错，下次写入时还会尝试截断
            }
        }
        i("APP", "日志已清空");
    }

    /** 拿到日志文件对象，供分享/导出用；还没 init 时返回 null，调用方要判空 */
    public static File file() {
        return logFile;
    }
}
