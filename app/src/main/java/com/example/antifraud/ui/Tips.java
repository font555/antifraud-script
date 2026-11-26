package com.example.antifraud.ui;

import android.app.Activity;
import android.content.Context;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.antifraud.R;
import com.example.antifraud.engine.AppLog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 反诈口诀：数据在 assets/data/tips.json，首页口诀卡和轮播图共用同一份内容。
 *
 * <p>口诀只有 8 个字（码链钱平，话急警证），每个字配一句解释，
 * 学生背 8 个字就能想起 8 个动作。</p>
 *
 * <p><b>谁在调用：</b>{@link HomeTabView}（首页口诀卡与轮播卡）、
 * {@link CarouselView}（轮播里的一句口诀）、{@link MainActivity}（兜底展示）。</p>
 *
 * <p><b>为什么要缓存：</b>口诀被首页多处同时读取，如果每处都去读一次 assets 里的
 * JSON，切页面时会重复几十次 IO。这里用一个静态 List 缓存，整 App 只读一次。
 * 口诀是随包发布的静态内容，运行期不会变，所以缓存永不过期。</p>
 */
public class Tips {

    /**
     * 一条口诀的数据结构：只读容器，字段全 public 方便页面直接取。
     *
     * <p>用普通类而不用 record，是因为项目最低支持 API 24，
     * 且刻意保持"零依赖、老语法"，任何 JDK 环境都能编。</p>
     */
    public static class Tip {
        /** 口诀的"字"，例如「码」；也是这条口诀的唯一标识 */
        public String key = "";
        /** 这个字对应的一句话动作，例如「不给」，跟在字后面拼成「码」不给 */
        public String sub = "";
        /** 完整解释，弹窗里显示在动作下面，讲清"为什么" */
        public String text = "";
    }

    /** 口诀 JSON 在 assets 里的路径。改口诀只改这个文件，不用动 Java */
    private static final String ASSET = "data/tips.json";
    /** 全局缓存：第一次读出来后一直复用；null 表示还没读过（空列表表示读失败过，也缓存，避免反复重试） */
    private static List<Tip> cache;

    /** 私有构造：纯工具类，不允许 new */
    private Tips() {
    }

    /**
     * 读取全部口诀（带缓存）。读取失败不抛异常，返回空列表，页面自己判空处理。
     *
     * <p>方法加 {@code synchronized}：首页有可能在多个线程里同时取（例如轮播的定时刷新），
     * 加锁保证缓存只初始化一次，不会出现两份不同的列表。</p>
     *
     * @param c 任意 Context，用来打开 assets
     * @return 口诀列表，可能为空（JSON 缺失/损坏时），但绝不返回 null
     */
    public static synchronized List<Tip> load(Context c) {
        if (cache != null && !cache.isEmpty()) {
            return cache;   // 命中缓存，直接返回，不再读文件
        }
        List<Tip> list = new ArrayList<>();
        InputStream in = null;
        try {
            in = c.getAssets().open(ASSET);
            // 一次全读进内存：口诀文件只有几 KB，不值得做流式解析
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            // 明确用 UTF-8：口诀是中文，用平台默认编码在不同机型上可能变乱码
            JSONArray arr = new JSONObject(new String(out.toByteArray(), "UTF-8")).optJSONArray("tips");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) {
                        continue;   // 跳过数组里的非法项，不让一条坏数据毁掉整批
                    }
                    Tip t = new Tip();
                    // trim：JSON 里手写容易多打空格，去掉后界面才整齐
                    t.key = o.optString("key", "").trim();
                    t.sub = o.optString("sub", "").trim();
                    t.text = o.optString("text", "").trim();
                    if (!t.key.isEmpty()) {
                        list.add(t);   // 没有"字"的口诀无法展示，直接丢弃
                    }
                }
            }
        } catch (Exception e) {
            // 读不到口诀不应该让首页崩溃：记日志、返回空列表，页面会显示兜底文案
            AppLog.e("TIPS", "读取口诀失败", e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                    // 关闭失败无所谓，流已经读完，忽略即可
                }
            }
        }
        cache = list;   // 即使为空也缓存，避免每次进首页都重新尝试读坏文件
        return list;
    }

    /** 把若干条口诀拼成一行：「码」不给 ·「链」不点 · … */
    public static String inlineLine(List<Tip> tips) {
        StringBuilder sb = new StringBuilder();
        for (Tip t : tips) {
            if (sb.length() > 0) {
                sb.append(" · ");   // 只有非第一条前面才加分隔符，避免开头多一个点
            }
            sb.append("「").append(t.key).append("」").append(t.sub);
        }
        return sb.toString();
    }

    /**
     * 弹窗：8 句口诀 + 解释，像素风、可滚动。
     *
     * <p>入口在首页口诀卡"展开全部"或"我的 → 反诈口诀"。</p>
     *
     * @param a 当前页面，弹窗需要它当 Context 和 owner
     */
    public static void showAll(Activity a) {
        List<Tip> tips = load(a);
        if (tips.isEmpty()) {
            return;   // 没读到口诀就不弹空窗，静默返回
        }
        // 用像素风 PixelDialog，替掉系统 AlertDialog（白底圆角跟整体风格不搭，
        // 且 2.1 倍字号下正文会撑出屏幕、按钮被挤掉）
        PixelDialog dlg = PixelDialog.create(a);
        dlg.title("反诈口诀 · 全部 8 句");
        dlg.meta("背下 8 个字，记住 8 个动作。");
        for (Tip t : tips) {
            // 每条口诀一行："字 + 动作" 当小标题，完整解释当正文
            dlg.field("「" + t.key + "」" + t.sub, t.text);
        }
        dlg.body("遇到拿不准的事：先挂断、先问家长老师、打 110 或 96110。");
        dlg.button("记住了", true, null, 0);   // 只有一个确定按钮，null 表示用默认关闭行为
        dlg.show();
    }
}
