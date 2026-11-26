package com.example.antifraud.engine;

import android.content.Context;
import android.graphics.Color;

import com.example.antifraud.model.CaseSource;
import com.example.antifraud.model.Category;
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
 *
 * <p>它是整个 App 的"读书入口"：书架要显示所有剧本、剧本页要按分类筛选、
 * 聊天页要拿某个剧本开一局，最后都会走到这里。真正的读取分两路——
 * 一路读 assets 里打包进 APK 的内置剧本（只读，改不了），
 * 一路读用户用「剧本工坊」自己写、存在 filesDir/scripts 下的剧本（可改可删）。
 * 两路合并时用户剧本优先，所以用户可以覆盖同名内置剧本。</p>
 *
 * <p>解析思路：全部字段都用 optXxx（有就给、没有就用默认值），而不是 getXxx。
 * 这是故意"宽进"的：剧本是人工手写的 JSON，少写一个字段不应该让整本书打不开，
 * 缺字段时用合理的默认值兜底，让书还能显示出来，问题留给校验脚本去发现。</p>
 *
 * <p>异常策略：所有公开方法都不往外抛异常，读到坏文件时返回 null 或空列表，
 * 顶多把错误打到 logcat。这样即使某个剧本写坏了，也只是那一本打不开，App 不会崩。</p>
 */
public class ScriptRepository {

    /** assets 里放剧本的目录名；下面拼文件名时用 DIR + "/" + id + ".json" */
    private static final String DIR = "scripts";

    /**
     * 列出所有剧本 id：用户自己写的（filesDir/scripts）在内置剧本（assets/scripts）之前。
     * 新增剧本不用改代码。
     *
     * <p>做法是先收用户剧本，再补齐内置剧本，最后用 contains 去重。
     * 顺序很关键：清单顺序就是书架顺序，用户自己写的新剧本排在最前面，
     * 演示时"我刚在工坊里写了一个"能马上看到，不用往下翻。</p>
     *
     * @param ctx 任意 Context，用来读 assets 和 filesDir
     * @return 剧本 id 列表，不含 .json 后缀；读不到时返回空列表而不是 null
     */
    public static List<String> listScriptIds(Context ctx) {
        List<String> ids = new ArrayList<>();
        for (String id : ScriptStore.listUserIds(ctx)) {
            if (!ids.contains(id)) { // 用户目录里理论上不会有重名，这里仍然去重，防御手动拷贝进来的重复文件
                ids.add(id);
            }
        }
        List<String> builtin = new ArrayList<>();
        try {
            String[] files = ctx.getAssets().list(DIR); // 列出 assets/scripts 下所有文件名
            if (files != null) {
                for (String f : files) {
                    if (f.endsWith(".json")) { // 只认 .json，其余文件（如说明文档、LICENSE）跳过
                        builtin.add(f.substring(0, f.length() - 5)); // 去掉末尾 5 个字符 ".json"，剩下就是剧本 id
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace(); // assets 读取失败极少见，打日志后继续，至少能让用户剧本显示出来
        }
        Collections.sort(builtin); // 内置剧本按字母排序，保证每次打开书架顺序一致，不会"跳来跳去"
        for (String id : builtin) {
            if (!ids.contains(id)) { // 用户剧本已经占了同名位置，内置的就不再重复出现
                ids.add(id);
            }
        }
        return ids;
    }

    /**
     * 读取并把所有剧本解析出来，首页用。
     *
     * <p>逐个 id 调 load，单个失败（返回 null）就跳过，不影响其他剧本。
     * 所以书架可能"少一本书"，但永远不会因为一本坏书整个书架空白。</p>
     *
     * @param ctx 任意 Context
     * @return 全部剧本；一个都没有时返回空列表
     */
    public static List<Script> loadAll(Context ctx) {
        List<Script> list = new ArrayList<>();
        for (String id : listScriptIds(ctx)) {
            Script s = load(ctx, id);
            if (s != null) { // null 代表这本剧本读失败或解析失败，跳过继续下一本
                list.add(s);
            }
        }
        return list;
    }

    /**
     * 按 id 读取单个剧本：先找用户剧本，找不到再找内置剧本。
     *
     * <p>这个"用户优先"的顺序是整个项目的关键约定：
     * 工坊里保存的同名剧本会覆盖内置剧本，聊天页不需要关心数据到底从哪来。</p>
     *
     * @param ctx 任意 Context
     * @param id  剧本 id（不带 .json）
     * @return 解析好的剧本；文件和解析都失败时返回 null
     */
    public static Script load(Context ctx, String id) {
        // 用户自己写的优先
        Script user = ScriptStore.loadUser(ctx, id);
        if (user != null) {
            return user;
        }
        try {
            String json = readAsset(ctx, DIR + "/" + id + ".json"); // 内置剧本：从 APK 的 assets 里读成字符串
            return parse(json);
        } catch (Exception e) {
            e.printStackTrace(); // 文件不存在或 JSON 语法错，都属于"这本打不开"，返回 null 让上层跳过
            return null;
        }
    }

    /**
     * 读取书架分类（assets/data/categories.json），并解析封面色。
     *
     * <p>分类就是书架上的"几本书"：游戏账号、刷单返利、冒充客服、冒充公检法、校园贷……
     * 每个分类带封面色和像素大字，书架和筛选条都靠这些字段画出来。</p>
     *
     * <p>颜色这里做了两层兜底：json 里没写就用默认橙色，
     * 写了但格式不对（例如少一个 #）也会被 Color.parseColor 抛异常，
     * 此时同样退回默认橙色——绝不因为一个颜色字符串写错导致整个书架画不出来。</p>
     *
     * @param ctx 任意 Context
     * @return 分类列表；文件缺失或格式错误时返回空列表
     */
    public static List<Category> loadCategories(Context ctx) {
        List<Category> list = new ArrayList<>();
        try {
            String json = readAsset(ctx, "data/categories.json");
            JSONArray arr = new JSONObject(json).optJSONArray("categories"); // 顶层是个对象，真正的分类数组在 categories 键下
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    Category c = new Category();
                    c.id = o.optString("id");
                    c.name = o.optString("name");
                    c.desc = o.optString("desc");
                    c.glyph = o.optString("glyph", "书"); // 没写封面字就用"书"字，保证封面不空着
                    c.color = o.optString("color", "#E4572E"); // 默认是主题橙色
                    try {
                        c.colorInt = Color.parseColor(c.color); // 把 "#RRGGBB" 变成可以直接画的整数颜色
                    } catch (Exception ignore) {
                        c.colorInt = 0xFFE4572E; // 颜色写错时退回默认色，不影响整页
                    }
                    list.add(c);
                }
            }
        } catch (Exception e) {
            e.printStackTrace(); // 分类文件读不到就返回空列表，书架会显示"暂无分类"，但不会崩
        }
        return list;
    }

    /**
     * 某个分类下的所有剧本。
     *
     * <p>实现很朴素：把所有剧本读一遍再筛。剧本文件都很小（几十 KB），
     * 又是本地读取，没必要为了性能做索引或缓存——代码简单更重要。</p>
     *
     * @param ctx        任意 Context
     * @param categoryId 分类 id，例如 "game"；传 null 表示"全都要"
     * @return 该分类下的剧本列表
     */
    public static List<Script> loadByCategory(Context ctx, String categoryId) {
        List<Script> result = new ArrayList<>();
        for (Script s : loadAll(ctx)) {
            if (categoryId == null || categoryId.equals(s.category)) { // categoryId 为 null 时退化成"返回全部"
                result.add(s);
            }
        }
        return result;
    }

    // ---------------- 内部实现 ----------------

    /**
     * 把 assets 里的一个文件整个读成字符串。
     *
     * <p>没有用第三方工具，手写 4KB 缓冲循环读，读完记得关流。
     * 固定按 UTF-8 解码：剧本里全是中文，编码搞错会变成乱码。</p>
     *
     * @param ctx  Context
     * @param path assets 内的相对路径，例如 "scripts/n1.json"
     * @return 文件全文
     * @throws Exception 文件不存在或读取失败时抛出，由调用方决定怎么处理
     */
    private static String readAsset(Context ctx, String path) throws Exception {
        InputStream in = ctx.getAssets().open(path);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096]; // 每次读 4KB，够小不会占太多内存，也不至于循环太多次
        int n;
        while ((n = in.read(buf)) > 0) { // read 返回 -1 表示读到文件末尾
            out.write(buf, 0, n);
        }
        in.close();
        return new String(out.toByteArray(), "UTF-8");
    }

    /**
     * 把一段 JSON 文本解析成 Script 对象。
     *
     * <p>这是"剧本格式"的唯一权威实现，字段名必须和 assets/scripts/*.json 完全对得上。
     * 三层结构依次解析：先顶层字段，再 sources 数组，最后 nodes 字典
     * （nodes 里每个值再交给 parseNode 处理）。</p>
     *
     * @param json 剧本 JSON 全文
     * @return 解析结果，永远不会返回 null
     * @throws Exception JSON 语法错误时抛出 JSONException，由上层决定是否吞掉
     */
    public static Script parse(String json) throws Exception {
        JSONObject root = new JSONObject(json); // 语法错在这里就会抛异常，属于"整本都读不了"

        Script s = new Script();
        s.id = root.optString("id");
        s.title = root.optString("title");
        s.scamType = root.optString("scamType");
        s.category = root.optString("category", "game"); // 老剧本没有 category 字段，默认归到游戏账号分类
        s.minutes = root.optString("minutes");
        s.intro = root.optString("intro");
        s.startNode = root.optString("startNode");
        s.stageCount = root.optInt("stageCount", 5); // 幕数缺失时按 5 幕算，是早期剧本的默认值
        s.referenceLoss = root.optInt("referenceLoss", 0);
        s.referenceNote = root.optString("referenceNote");

        JSONArray srcArr = root.optJSONArray("sources"); // 来源可以为空（老剧本就没有），所以先 opt 再判空
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
            Iterator<String> it = nodes.keys(); // 遍历所有节点键，例如 n1、n2……
            while (it.hasNext()) {
                String key = it.next();
                StoryNode node = parseNode(nodes.getJSONObject(key));
                if (node.id == null || node.id.isEmpty()) {
                    node.id = key; // 节点里没写 id 就用字典的 key 兜底，保证节点一定有身份
                }
                s.nodes.put(node.id, node);
            }
        }
        return s;
    }

    /**
     * 解析单个剧情节点。
     *
     * <p>节点的结构是：台词列表 messages + 选项列表 options + 结局信息。
     * 台词和选项都是数组，逐个读；选项里如果某一项不是对象（手写 JSON 常见的手误），
     * 直接 continue 跳过那一项，而不是让整个节点解析失败。</p>
     *
     * @param o 一个节点的 JSONObject
     * @return 组装好的节点
     */
    private static StoryNode parseNode(JSONObject o) {
        StoryNode n = new StoryNode();
        n.id = o.optString("id");
        n.speaker = o.optString("speaker", "对方"); // 没写说话人就当成"对方"
        n.stage = o.optInt("stage", 1);
        n.stageName = o.optString("stageName");
        n.isEnd = o.optBoolean("isEnd", false);
        n.endingTitle = o.optString("endingTitle");
        n.endingAdvice = o.optString("endingAdvice");

        JSONArray msgs = o.optJSONArray("messages");
        if (msgs != null) {
            for (int i = 0; i < msgs.length(); i++) {
                n.messages.add(msgs.optString(i)); // optString 遇到 null 会转成空串，不会抛异常
            }
        }

        JSONArray opts = o.optJSONArray("options");
        if (opts != null) {
            for (int i = 0; i < opts.length(); i++) {
                JSONObject o2 = opts.optJSONObject(i);
                if (o2 == null) {
                    continue; // 这一项不是对象（写成了字符串之类），跳过它，保住整个节点
                }
                Choice c = new Choice();
                c.text = o2.optString("text");
                c.next = o2.optString("next");
                c.risk = o2.optString("risk", "low"); // 风险等级缺失时按 low 处理，校验脚本会另外报错
                c.tag = o2.optString("tag");
                c.feedback = o2.optString("feedback");
                c.cost = o2.optInt("cost", 0);
                n.options.add(c);
            }
        }
        return n;
    }
}
