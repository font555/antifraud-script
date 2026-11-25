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
 */
public class Tips {

    public static class Tip {
        public String key = "";
        public String sub = "";
        public String text = "";
    }

    private static final String ASSET = "data/tips.json";
    private static List<Tip> cache;

    private Tips() {
    }

    public static synchronized List<Tip> load(Context c) {
        if (cache != null && !cache.isEmpty()) {
            return cache;
        }
        List<Tip> list = new ArrayList<>();
        InputStream in = null;
        try {
            in = c.getAssets().open(ASSET);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            JSONArray arr = new JSONObject(new String(out.toByteArray(), "UTF-8")).optJSONArray("tips");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) {
                        continue;
                    }
                    Tip t = new Tip();
                    t.key = o.optString("key", "").trim();
                    t.sub = o.optString("sub", "").trim();
                    t.text = o.optString("text", "").trim();
                    if (!t.key.isEmpty()) {
                        list.add(t);
                    }
                }
            }
        } catch (Exception e) {
            AppLog.e("TIPS", "读取口诀失败", e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
        cache = list;
        return list;
    }

    /** 把若干条口诀拼成一行：「码」不给 ·「链」不点 · … */
    public static String inlineLine(List<Tip> tips) {
        StringBuilder sb = new StringBuilder();
        for (Tip t : tips) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append("「").append(t.key).append("」").append(t.sub);
        }
        return sb.toString();
    }

    /** 弹窗：8 句口诀 + 解释，可滚动（零依赖，用系统 AlertDialog） */
    public static void showAll(Activity a) {
        List<Tip> tips = load(a);
        if (tips.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("背下 8 个字，记住 8 个动作：\n\n");
        for (Tip t : tips) {
            sb.append("「").append(t.key).append("」").append(t.sub).append("\n");
            sb.append(t.text).append("\n\n");
        }
        sb.append("遇到拿不准的事：先挂断、先问家长老师、打 110 或 96110。");

        TextView body = new TextView(a);
        Ui.pixel(body);
        body.setTextSize(11);
        body.setTextColor(a.getColor(R.color.ink_secondary));
        body.setLineSpacing(Ui.dp(a, 4), 1f);
        body.setText(sb.toString());
        int pad = Ui.dp(a, 16);
        body.setPadding(pad, pad, pad, pad);

        ScrollView scroll = new ScrollView(a);
        scroll.addView(body);

        new android.app.AlertDialog.Builder(a)
                .setTitle("反诈口诀 · 全部 8 句")
                .setView(scroll)
                .setPositiveButton("记住了", null)
                .show();
    }
}
