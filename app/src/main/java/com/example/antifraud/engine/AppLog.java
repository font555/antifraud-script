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
 */
public class AppLog {

    private static final int MAX_LINES = 400;
    private static final long MAX_FILE_BYTES = 256 * 1024;

    private static final LinkedList<String> BUFFER = new LinkedList<>();
    private static File logFile;
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private AppLog() {
    }

    /** 在 Application/首个 Activity 里调一次，顺便接管崩溃日志 */
    public static void init(Context context) {
        if (logFile != null) {
            return;
        }
        try {
            File dir = new File(context.getFilesDir(), "logs");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            logFile = new File(dir, "app.log");
        } catch (Exception ignored) {
        }
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                e(t.getName(), "崩溃：" + e.getClass().getSimpleName() + " " + e.getMessage(), e);
                if (old != null) {
                    old.uncaughtException(t, e);
                }
            }
        });
        i("APP", "日志已启动");
    }

    public static void i(String tag, String msg) {
        write("I", tag, msg, null);
    }

    public static void e(String tag, String msg, Throwable t) {
        write("E", tag, msg, t);
    }

    private static synchronized void write(String level, String tag, String msg, Throwable t) {
        String line = TIME.format(new Date()) + " " + level + "/" + tag + "  " + msg;
        BUFFER.addLast(line);
        while (BUFFER.size() > MAX_LINES) {
            BUFFER.removeFirst();
        }
        StringBuilder sb = new StringBuilder(line).append('\n');
        if (t != null) {
            sb.append("    ").append(t.getClass().getName());
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < Math.min(6, st.length); i++) {
                sb.append("\n      at ").append(st[i]);
            }
            sb.append('\n');
        }
        if (logFile == null) {
            return;
        }
        try {
            if (logFile.length() > MAX_FILE_BYTES) {
                FileOutputStream truncate = new FileOutputStream(logFile, false);
                truncate.close();
            }
            FileOutputStream out = new FileOutputStream(logFile, true);
            out.write(sb.toString().getBytes("UTF-8"));
            out.close();
        } catch (Exception ignored) {
        }
    }

    /** 返回最近日志（含文件里更早的部分最多 400 行） */
    public static synchronized String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append("行数 ").append(BUFFER.size()).append(" / 最多 ").append(MAX_LINES).append('\n');
        if (logFile != null) {
            sb.append("文件：").append(logFile.getAbsolutePath()).append('\n');
        }
        sb.append("--------------------------------\n");
        for (String s : BUFFER) {
            sb.append(s).append('\n');
        }
        return sb.toString();
    }

    public static synchronized void clear() {
        BUFFER.clear();
        if (logFile != null) {
            try {
                new FileOutputStream(logFile, false).close();
            } catch (Exception ignored) {
            }
        }
        i("APP", "日志已清空");
    }

    public static File file() {
        return logFile;
    }
}
