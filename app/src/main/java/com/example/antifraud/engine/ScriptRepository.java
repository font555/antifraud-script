package com.example.antifraud.engine;

import android.content.Context;

import com.example.antifraud.model.CaseSource;
import com.example.antifraud.model.Choice;
import com.example.antifraud.model.Script;
import com.example.antifraud.model.StoryNode;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * 剧本仓库：负责从 assets/scripts 读取 JSON 并解析成 Script 对象。
 * 用 Android 自带的 org.json，不引入任何第三方库。
 */
public class ScriptRepository {

    private static final String DIR = "scripts";

    /** 列出 assets/scripts 下所有剧本 id（去掉 .json），自动排序。新增剧本不用改代码。 */
    public static List<String> listScriptIds(Context ctx) {
        List<String> ids = new ArrayList<>();
        try {
            String[] files = ctx.getAssets().list(DIR);
            if (files != null) {
                for (String f : files) {
                    if (f.endsWith(".json")) {
                        ids.add(f.substring(0, f.length() - 5));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Collections.sort(ids);
        return ids;
    }

    /** 读取并把所有剧本解析出来，首页用。 */
    public static List<Script> loadAll(Context ctx) {
        List<Script> list = new ArrayList<>();
        for (String id : listScriptIds(ctx)) {
            Script s = load(ctx, id);
            if (s != null) {
                list.add(s);
            }
        }
        return list;
    }

    public static Script load(Context ctx, String id) {
        try {
            String json = readAsset(ctx, DIR + "/" + id + ".json");
            return parse(json);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ---------------- 内部实现 ----------------

    private static String readAsset(Context ctx, String path) throws Exception {
        InputStream in = ctx.getAssets().open(path);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        return new String(out.toByteArray(), "UTF-8");
    }

    private static Script parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);

        Script s = new Script();
        s.id = root.optString("id");
        s.title = root.optString("title");
        s.scamType = root.optString("scamType");
        s.minutes = root.optString("minutes");
        s.intro = root.optString("intro");
        s.startNode = root.optString("startNode");
        s.stageCount = root.optInt("stageCount", 5);
        s.referenceLoss = root.optInt("referenceLoss", 0);
        s.referenceNote = root.optString("referenceNote");

        JSONArray srcArr = root.optJSONArray("sources");
        if (srcArr != null) {
            for (int i = 0; i < srcArr.length(); i++) {
                JSONObject o = srcArr.getJSONObject(i);
                CaseSource c = new CaseSource();
                c.name = o.optString("name");
                c.publisher = o.optString("publisher");
                c.date = o.optString("date");
                c.url = o.optString("url");
                c.usedIn = o.optString("usedIn");
                c.note = o.optString("note");
                s.sources.add(c);
            }
        }

        JSONObject nodes = root.optJSONObject("nodes");
        if (nodes != null) {
            Iterator<String> it = nodes.keys();
            while (it.hasNext()) {
                String key = it.next();
                StoryNode node = parseNode(nodes.getJSONObject(key));
                if (node.id == null || node.id.isEmpty()) {
                    node.id = key;
                }
                s.nodes.put(node.id, node);
            }
        }
        return s;
    }

    private static StoryNode parseNode(JSONObject o) {
        StoryNode n = new StoryNode();
        n.id = o.optString("id");
        n.speaker = o.optString("speaker", "对方");
        n.stage = o.optInt("stage", 1);
        n.stageName = o.optString("stageName");
        n.isEnd = o.optBoolean("isEnd", false);
        n.endingTitle = o.optString("endingTitle");
        n.endingAdvice = o.optString("endingAdvice");

        JSONArray msgs = o.optJSONArray("messages");
        if (msgs != null) {
            for (int i = 0; i < msgs.length(); i++) {
                n.messages.add(msgs.optString(i));
            }
        }

        JSONArray opts = o.optJSONArray("options");
        if (opts != null) {
            for (int i = 0; i < opts.length(); i++) {
                JSONObject o2 = opts.optJSONObject(i);
                if (o2 == null) {
                    continue;
                }
                Choice c = new Choice();
                c.text = o2.optString("text");
                c.next = o2.optString("next");
                c.risk = o2.optString("risk", "low");
                c.tag = o2.optString("tag");
                c.feedback = o2.optString("feedback");
                c.cost = o2.optInt("cost", 0);
                n.options.add(c);
            }
        }
        return n;
    }
}
