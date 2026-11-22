package com.example.antifraud.engine;

import android.content.Context;

import com.example.antifraud.model.CaseSource;
import com.example.antifraud.model.Choice;
import com.example.antifraud.model.Script;
import com.example.antifraud.model.StoryNode;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 剧本工坊的存储：用户自己写的剧本放在 App 私有目录 filesDir/scripts/ 下，
 * 和 assets 里的内置剧本合并读取。不联网、不需要任何权限。
 */
public class ScriptStore {

    private static final String DIR = "scripts";

    private ScriptStore() {
    }

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), DIR);
        if (!d.exists()) {
            d.mkdirs();
        }
        return d;
    }

    public static boolean isUserScript(Context c, String id) {
        return new File(dir(c), id + ".json").exists();
    }

    public static List<String> listUserIds(Context c) {
        List<String> ids = new ArrayList<>();
        File[] files = dir(c).listFiles();
        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                if (name.endsWith(".json")) {
                    ids.add(name.substring(0, name.length() - 5));
                }
            }
        }
        Collections.sort(ids);
        return ids;
    }

    public static Script loadUser(Context c, String id) {
        try {
            File f = new File(dir(c), id + ".json");
            if (!f.exists()) {
                return null;
            }
            return ScriptRepository.parse(readFile(f));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean saveUser(Context c, Script s) {
        try {
            String json = toJson(s);
            File f = new File(dir(c), s.id + ".json");
            FileOutputStream out = new FileOutputStream(f);
            out.write(json.getBytes("UTF-8"));
            out.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /** 导入外部文件的内容（已经在 SAF 里读成字符串） */
    public static Script importJson(Context c, String json) {
        try {
            Script s = ScriptRepository.parse(json);
            if (s == null || s.id == null || s.id.isEmpty()) {
                return null;
            }
            if (!saveUser(c, s)) {
                return null;
            }
            return s;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean deleteUser(Context c, String id) {
        return new File(dir(c), id + ".json").delete();
    }

    // ---------------- 序列化 ----------------

    public static String toJson(Script s) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", s.id);
        o.put("title", s.title);
        o.put("scamType", s.scamType);
        o.put("category", s.category);
        o.put("minutes", s.minutes == null ? "" : s.minutes);
        o.put("intro", s.intro == null ? "" : s.intro);
        o.put("startNode", s.startNode);
        o.put("stageCount", s.stageCount);
        o.put("referenceLoss", s.referenceLoss);
        o.put("referenceNote", s.referenceNote == null ? "" : s.referenceNote);

        JSONArray srcArr = new JSONArray();
        for (CaseSource c : s.sources) {
            JSONObject so = new JSONObject();
            so.put("name", c.name);
            so.put("publisher", c.publisher);
            so.put("date", c.date);
            so.put("url", c.url);
            so.put("usedIn", c.usedIn);
            so.put("note", c.note);
            srcArr.put(so);
        }
        o.put("sources", srcArr);

        JSONObject nodes = new JSONObject();
        for (StoryNode n : s.nodes.values()) {
            JSONObject no = new JSONObject();
            no.put("id", n.id);
            no.put("speaker", n.speaker);
            no.put("stage", n.stage);
            no.put("stageName", n.stageName == null ? "" : n.stageName);
            no.put("isEnd", n.isEnd);
            no.put("endingTitle", n.endingTitle == null ? "" : n.endingTitle);
            no.put("endingAdvice", n.endingAdvice == null ? "" : n.endingAdvice);
            JSONArray msgs = new JSONArray();
            for (String m : n.messages) {
                msgs.put(m);
            }
            no.put("messages", msgs);
            JSONArray opts = new JSONArray();
            for (Choice ch : n.options) {
                JSONObject co = new JSONObject();
                co.put("text", ch.text);
                co.put("next", ch.next == null ? "" : ch.next);
                co.put("risk", ch.risk);
                co.put("tag", ch.tag == null ? "" : ch.tag);
                co.put("feedback", ch.feedback == null ? "" : ch.feedback);
                co.put("cost", ch.cost);
                opts.put(co);
            }
            no.put("options", opts);
            nodes.put(n.id, no);
        }
        o.put("nodes", nodes);
        return o.toString(2);
    }

    private static String readFile(File f) throws Exception {
        InputStream in = new FileInputStream(f);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        return new String(out.toByteArray(), "UTF-8");
    }
}
