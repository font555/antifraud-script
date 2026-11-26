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
 *
 * <p>这个类只负责"用户剧本"这一半，另一半（内置剧本）在
 * {@link ScriptRepository} 里；两边合起来才是书架上看到的全部剧本。
 * 之所以放在 filesDir 而不是外部存储：filesDir 是 App 私有目录，
 * 卸载 App 就一起删掉，不需要申请任何权限，也不会有别的应用偷看。</p>
 *
 * <p>和内置剧本的冲突规则写在 {@link #importJson} 里：
 * 导入的剧本如果和内置剧本同名，会自动改名成 xxx-user、xxx-user2……，
 * 避免"用户把自己的剧本存进去，结果把内置剧本顶掉"。而工坊里正常保存
 * 同名剧本时是允许覆盖的（用户自己的东西自己说了算）。</p>
 *
 * <p>异常策略和 ScriptRepository 一致：读不到、写不进都返回 null / false，
 * 不往外抛异常，保证工坊界面不会因为一个坏文件闪退。</p>
 */
public class ScriptStore {

    /** 用户剧本目录名，最终路径是 filesDir/scripts */
    private static final String DIR = "scripts";

    /** 私有构造：纯静态工具类，不允许 new */
    private ScriptStore() {
    }

    /**
     * 拿到用户剧本目录，不存在就建出来。
     *
     * <p>每次调用都判一次存在性，是为了兼容"用户手动清了 App 存储"的情况：
     * 目录被删掉后再调这里会自动重建，不至于后面写文件时找不到路。</p>
     *
     * @param c 任意 Context
     * @return 目录对象；建目录失败时也会返回对象，后面写文件时自然会失败
     */
    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), DIR);
        if (!d.exists()) {
            d.mkdirs(); // 建多层目录；返回值不判断，与 AppLog 里保持一致的"失败也不中断"
        }
        return d;
    }

    /**
     * 判断某个 id 是不是用户自己写的剧本。
     *
     * <p>只有"用户目录下确实有这个文件"才算；内置剧本、或者只存在于内存里的剧本都不算。</p>
     *
     * @param c  任意 Context
     * @param id 剧本 id
     * @return true 表示用户目录下有这个文件
     */
    public static boolean isUserScript(Context c, String id) {
        return new File(dir(c), id + ".json").exists();
    }

    /**
     * 列出用户剧本的全部 id（不带 .json 后缀），按字母排序。
     *
     * <p>排序是为了让书架顺序稳定：文件名排序在任何机器上结果都一样，
     * 不排序的话不同手机上看到的顺序会不一样，演示时容易对不上稿子。</p>
     *
     * @param c 任意 Context
     * @return id 列表；目录里没有文件时返回空列表
     */
    public static List<String> listUserIds(Context c) {
        List<String> ids = new ArrayList<>();
        File[] files = dir(c).listFiles(); // 目录刚建好时可能返回 null，所以下面要判空
        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                if (name.endsWith(".json")) { // 只认 .json，其他临时文件忽略
                    ids.add(name.substring(0, name.length() - 5)); // 去掉末尾 ".json" 得到 id
                }
            }
        }
        Collections.sort(ids);
        return ids;
    }

    /**
     * 读取用户剧本；不存在或解析失败都返回 null。
     *
     * <p>注意它和 ScriptRepository.load 的区别：这里只读用户目录，
     * 不会去碰 assets。所以找不到就是真的没有，调用方据此判断要不要回退到内置剧本。</p>
     *
     * @param c  任意 Context
     * @param id 剧本 id
     * @return 解析好的剧本，或 null
     */
    public static Script loadUser(Context c, String id) {
        try {
            File f = new File(dir(c), id + ".json");
            if (!f.exists()) {
                return null; // 文件不在，很正常（用户没写过），不是错误
            }
            return ScriptRepository.parse(readFile(f)); // 解析复用同一个 parse，保证两边格式完全一致
        } catch (Exception e) {
            e.printStackTrace(); // 文件损坏或 JSON 写坏，返回 null 让上层跳过这本书
            return null;
        }
    }

    /**
     * 把剧本对象存成用户剧本文件（覆盖同名文件）。
     *
     * <p>写入过程是"先序列化成字符串，再一次性写文件"。没有做原子替换，
     * 因为文件很小、又是本地写，写一半失败的概率极低；真失败了也只是这一个剧本读不出来。</p>
     *
     * @param c 任意 Context
     * @param s 要保存的剧本；必须已经有 id，否则会存成 ".json" 这种坏名字
     * @return true 表示写成功
     */
    public static boolean saveUser(Context c, Script s) {
        try {
            String json = toJson(s); // 先转成 JSON 文本
            File f = new File(dir(c), s.id + ".json");
            FileOutputStream out = new FileOutputStream(f); // 默认就是覆盖模式，同名文件会被替换
            out.write(json.getBytes("UTF-8")); // 固定 UTF-8，中文台词才不会乱码
            out.close();
            AppLog.i("STORE", "保存剧本 " + s.id + " 节点 " + s.nodes.size()); // 打点，方便排查"我的剧本怎么没了"
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false; // 写失败交给界面提示，不抛异常
        }
    }

    /**
     * 导入外部文件的内容（已经在 SAF 里读成字符串）。
     *
     * <p>流程：解析 → 校验有没有 id → 解决重名 → 落盘。
     * 重名处理是这里最要紧的一步：如果导入的 id 和内置剧本重名，
     * 一定要改名，否则用户导入后会把内置剧本"顶掉"（因为读取时用户剧本优先），
     * 表现为内置剧本悄悄消失，很容易被当成 bug。</p>
     *
     * @param c    任意 Context
     * @param json 剧本 JSON 全文（用户通过系统文件选择器选中的那个文件）
     * @return 导入成功返回最终落盘的剧本对象（id 可能已经改过），失败返回 null
     */
    public static Script importJson(Context c, String json) {
        try {
            Script s = ScriptRepository.parse(json);
            if (s == null || s.id == null || s.id.isEmpty()) {
                return null; // 连 id 都没有，无法命名文件，直接拒绝导入
            }
            String oldId = s.id; // 记下原始 id，只用于打日志，方便对照"我导入的是哪个"
            // 内置剧本同名会顶掉内置剧本（load 时用户剧本优先），自动加 -user 后缀避开
            s.id = resolveImportId(c, oldId);
            if (!saveUser(c, s)) {
                return null; // 改名成功但写盘失败，同样算导入失败
            }
            AppLog.i("STORE", "导入剧本 " + oldId + " → " + s.id); // 记录改名前后，出问题时能一眼看出对应关系
            return s;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 导入 id 与内置剧本冲突时改成 &lt;id&gt;-user、&lt;id&gt;-user2……直到不与内置/已有用户剧本重名。
     *
     * <p>注意循环条件同时检查两件事：既不能撞内置剧本，也不能撞用户自己已有的剧本。
     * 少检查任何一个，都会出现"导入后把之前那个覆盖掉"的数据丢失。</p>
     *
     * @param c  任意 Context
     * @param id 原始 id
     * @return 可安全使用的 id；不冲突时原样返回
     */
    private static String resolveImportId(Context c, String id) {
        if (!isBuiltin(c, id)) {
            return id; // 和内置不冲突，直接用原 id（用户自己重名覆盖自己的，属于正常操作）
        }
        String base = id + "-user"; // 第一次改名加 -user 后缀
        String candidate = base;
        int seq = 2; // 从 2 开始编号，因为第一个已经叫 xxx-user 了
        while (isBuiltin(c, candidate) || isUserScript(c, candidate)) {
            candidate = base + seq; // xxx-user2、xxx-user3……
            seq++;
        }
        return candidate;
    }

    /**
     * assets/scripts 下是否有同名内置剧本。
     *
     * <p>用逐个比对文件名的方式，而不是直接 open 再捕获异常：
     * 这样语义更清楚，也不用依赖"文件不存在会抛什么异常"这种实现细节。</p>
     *
     * @param c  任意 Context
     * @param id 剧本 id
     * @return true 表示有同名内置剧本
     */
    private static boolean isBuiltin(Context c, String id) {
        try {
            String[] files = c.getAssets().list(DIR);
            if (files != null) {
                String target = id + ".json";
                for (String f : files) {
                    if (target.equals(f)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            AppLog.e("STORE", "读取内置剧本列表失败", e); // 读不到 assets 时按"不是内置"处理，宁可改名也不误伤
        }
        return false;
    }

    /**
     * 删除一个用户剧本文件。
     *
     * <p>内置剧本删不掉（不在这个目录里），体现"内置的只读、自己的可改"。</p>
     *
     * @param c  任意 Context
     * @param id 剧本 id
     * @return File.delete 的结果：true 表示删掉了，false 表示没这个文件或删失败
     */
    public static boolean deleteUser(Context c, String id) {
        AppLog.i("STORE", "删除剧本 " + id); // 先记日志再删，删失败也能查到"曾经尝试删过谁"
        return new File(dir(c), id + ".json").delete();
    }

    // ---------------- 序列化 ----------------

    /**
     * 把 Script 对象序列化成 JSON 文本（缩进 2 格，方便人直接看文件）。
     *
     * <p>这里和 {@link ScriptRepository#parse} 是严格的一对：
     * 这边 put 出去的键名，那边必须 opt 得到；改了一边忘了另一边，
     * 用户剧本就会出现"保存时还在、重开就丢字段"的诡异现象。</p>
     *
     * <p>很多字段做了 null 保护（写成 ""）：org.json 遇到 null 会存成 JSON 的 null，
     * 下次解析出来的就是字符串 "null"，反而不如空串安全。</p>
     *
     * @param s 要导出的剧本
     * @return JSON 文本，UTF-8 友好
     * @throws Exception 理论上只有 org.json 内部异常才会抛出
     */
    public static String toJson(Script s) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", s.id);
        o.put("title", s.title);
        o.put("scamType", s.scamType);
        o.put("category", s.category);
        o.put("minutes", s.minutes == null ? "" : s.minutes); // null 一律转空串，避免存出 JSON null
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
        for (StoryNode n : s.nodes.values()) { // 用 values() 遍历，key 由节点自己的 id 决定，避免 id 和键名不一致
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
        return o.toString(2); // 2 空格缩进：文件既小又能直接打开看
    }

    /**
     * 把用户剧本文件整个读成字符串，思路和 ScriptRepository.readAsset 一样，
     * 只是数据源换成了普通文件。固定 UTF-8 解码。
     *
     * @param f 要读的文件
     * @return 文件全文
     * @throws Exception 文件不存在或读取失败时抛出
     */
    private static String readFile(File f) throws Exception {
        InputStream in = new FileInputStream(f);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096]; // 4KB 一读，和 assets 那边保持一致
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        return new String(out.toByteArray(), "UTF-8");
    }
}
