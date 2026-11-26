/*
 * ---------------------------------------------------------------------------
 * StudioActivity —— 剧本工坊（用户自己写剧本的入口页）
 *
 * 这是「我写的剧本」列表页，一共管五件事：
 *   1. 列出用户自己创建的所有剧本（标题、分类、节点数、起始节点）；
 *   2. 新建：造一个「最小可玩」的剧本骨架，直接跳进编辑器；
 *   3. 编辑：跳到 EditorActivity（剧本信息 + 节点列表）；
 *   4. 导出：把剧本存成一个 .json 文件，通过系统文件选择器让用户自己挑位置；
 *   5. 导入：从系统文件选择器挑一个 .json 读进来，变成自己的剧本；
 *   另外还能删除（带二次确认弹窗）。
 *
 * 从哪进来：首页「我的」页里的「剧本工坊」入口（或书架上的创作入口）。
 *
 * 数据存到哪：全部通过 ScriptStore 操作 App 私有目录下的用户剧本文件，
 * 每个剧本一个 JSON。全程不联网、不要任何权限 —— 选文件用的是系统自带的
 * 文件选择器（ACTION_OPEN_DOCUMENT / ACTION_CREATE_DOCUMENT），
 * App 只拿到用户主动选中的那一个文件的临时读写权。
 *
 * 阅读提示：本文件只加注释、不改代码。
 * ---------------------------------------------------------------------------
 */
package com.example.antifraud.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.antifraud.R;
import com.example.antifraud.engine.ScriptRepository;
import com.example.antifraud.engine.ScriptStore;
import com.example.antifraud.model.Category;
import com.example.antifraud.model.Choice;
import com.example.antifraud.model.Script;
import com.example.antifraud.model.StoryNode;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * 剧本工坊：管理用户自己写的剧本 —— 新建、编辑、导出成文件、从文件导入、删除。
 * 全程不联网、不需要权限（文件选择器是系统组件）。
 *
 * <p>页面结构：顶部返回 + 标题；「新建剧本」「从文件导入」两个按钮；
 * 下面一列剧本卡片（每张卡片三个按钮：编辑 / 导出 / 删除）；
 * 一个剧本都没有时显示 tvEmpty 的空状态提示。
 *
 * <p>为什么用 startActivityForResult 而不是新的 ActivityResult API：
 * 项目是零依赖的最小实现，startActivityForResult 在任何一个 minSdk 上都能用，
 * 不需要额外的 androidx 库，符合「零依赖」这条项目约束。
 */
public class StudioActivity extends BaseActivity {

    /** 「导入文件」这一步的请求码，onActivityResult 里靠它区分是导入还是导出 */
    private static final int REQ_IMPORT = 1001;

    /** 「导出文件」这一步的请求码，和 REQ_IMPORT 必须不同 */
    private static final int REQ_EXPORT = 1002;

    /** 剧本卡片的竖向容器 */
    private LinearLayout llList;

    /** 「还没有剧本」的空状态文字，列表非空时会被隐藏 */
    private TextView tvEmpty;

    /**
     * 正在导出的剧本 id。
     * 导出要经过「先弹系统保存框、用户选完位置才回调」两步，
     * 回调时 Intent 里只有目标文件地址，没有剧本 id，所以要在弹框前先记下来。
     */
    private String exportScriptId;

    /**
     * 页面创建：找到控件 → 挂按钮事件 → 画剧本列表。
     *
     * @param savedInstanceState 系统重建页面时回传的状态；本页数据一律从 ScriptStore 重读，
     *                           所以不使用它
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 1. 挂上布局 activity_studio.xml
        setContentView(R.layout.activity_studio);
        // 状态栏 / 导航栏安全边距
        Ui.insetContent(this);

        // 2. 找到列表容器和空状态文字
        llList = findViewById(R.id.ll_list);
        tvEmpty = findViewById(R.id.tv_empty);

        // 3. 返回：关页面并播「退出」的翻页动画（和进入时的动画成对，观感才连贯）
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                overridePendingTransition(R.anim.page_in, R.anim.page_out);
            }
        });
        // 4. 新建剧本：造骨架 → 存盘 → 直接跳进编辑器
        findViewById(R.id.btn_new).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createNew();
            }
        });
        // 5. 导入：拉起系统文件选择器。
        //    ACTION_OPEN_DOCUMENT + CATEGORY_OPENABLE 表示「只挑能打开的文件」，
        //    type 用 */* 是因为有的文件管理器不认 application/json，太严会挑不到文件。
        findViewById(R.id.btn_import).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(i, REQ_IMPORT);
            }
        });

        // 6. 画剧本列表
        buildList();
    }

    /**
     * 每次回到本页都会走这里。
     *
     * <p>用户可能刚从编辑器回来（改了标题、增删了节点），所以每次都重新读一遍
     * 用户剧本目录，重新画列表，保证看到的一定是最新状态。
     */
    @Override
    protected void onResume() {
        super.onResume();
        buildList();
    }

    /**
     * 重画剧本列表。
     *
     * <p>步骤：清空容器 → 取全部用户剧本 id → 没有就显示空状态 → 有就逐个读出来造卡片。
     * 读不出来的（文件损坏 / 被删）直接跳过，不让一个坏文件拖垮整页。
     */
    private void buildList() {
        // 先清掉旧卡片
        llList.removeAllViews();
        // 用户剧本目录里所有剧本 id（不含内置剧本）
        List<String> ids = ScriptStore.listUserIds(this);
        // 一个都没有就显示「去写第一本」的空状态，否则隐藏
        tvEmpty.setVisibility(ids.isEmpty() ? View.VISIBLE : View.GONE);
        for (String id : ids) {
            // 逐个把 JSON 读成 Script 对象
            Script s = ScriptStore.loadUser(this, id);
            // 读失败（文件损坏）就跳过这一条，页面照常显示其它剧本
            if (s != null) {
                llList.addView(buildRow(s));
            }
        }
    }

    /**
     * 造一张剧本卡片。
     *
     * <p>卡片三层：标题 → 副标题（分类 · 节点数 · 起始节点）→ 三个按钮（编辑 / 导出 / 删除）。
     *
     * @param s 要展示的剧本对象
     * @return 组装好的卡片 View
     */
    private View buildRow(final Script s) {
        // 统一样式的卡片：参数是（内边距 14dp，卡片下边距 10dp）；
        // 圆角和描边由 Ui.card 内部的 px_panel 九宫格背景决定，不需要在这里给。
        LinearLayout card = Ui.card(this, 14, 10);

        // 第一层：剧本标题，字号最大
        TextView head = Ui.text(this, s.title, 14, R.color.ink);
        card.addView(head);

        // 第二层：副标题。分类用中文名（查 categories.json），后面跟节点数和起始节点 id。
        // 显示起始节点是为了让作者一眼确认「剧本从哪个节点开始」。
        TextView info = Ui.text(this, categoryName(s.category) + " · " + s.nodes.size() + " 个节点 · 起始 "
                + s.startNode, 10, R.color.ink_hint);
        info.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(info);

        // 第三层：三个按钮横向平分一行
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(this, 10), 0, 0);
        // 编辑：跳转编辑器，把剧本 id 传过去
        row.addView(action("编辑", R.color.white, R.drawable.px_button, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(StudioActivity.this, EditorActivity.class);
                i.putExtra("script_id", s.id);
                startActivity(i);
            }
        }));
        // 导出：先拉起系统「保存到哪儿」的界面，真正写文件在回调里做
        row.addView(action("导出", R.color.ink, R.drawable.px_option, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportScript(s.id);
            }
        }));
        // 删除：危险操作，必须弹二次确认，避免手滑删掉写完的剧本
        row.addView(action("删除", R.color.warn_red, R.drawable.px_option, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(StudioActivity.this);
                // 弹窗标题 + 正文（正文就是剧本名，让用户确认删的是哪一本）
                b.setTitle("删除这个剧本？").setMessage(s.title)
                        .setPositiveButton("删除", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int w) {
                                // 真正删文件，然后刷新列表
                                ScriptStore.deleteUser(StudioActivity.this, s.id);
                                buildList();
                            }
                        })
                        // 「取消」传 null 就是「什么都不做」，弹窗自己会关
                        .setNegativeButton("取消", null)
                        .show();
            }
        }));
        card.addView(row);
        return card;
    }

    /**
     * 造一个「胶囊按钮」（卡片底部那一排）。
     *
     * @param text  按钮文字
     * @param color 文字颜色资源 id（白 / 黑 / 警示红）
     * @param bg    背景资源 id（实心按钮 / 浅色选项底）
     * @param l     点击回调
     * @return 造好的按钮 TextView
     */
    private View action(String text, int color, int bg, View.OnClickListener l) {
        // 像素字体，和全局风格统一
        TextView tv = new PTextView(this);
        tv.setText(text);
        tv.setTextSize(11);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setTextColor(getColor(color));
        tv.setBackgroundResource(bg);
        // 左右 12dp、上下 8dp 内边距
        tv.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        // 宽度权重 1，三个按钮平分一行；左右各留 3dp 做间隔
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(l);
        return tv;
    }

    /**
     * 把分类 id 翻译成中文名，用来显示在卡片副标题上。
     *
     * @param id 分类 id（例如 game / rebate）
     * @return 分类中文名；找不到对应分类时直接返回 id 本身（不显示空白）
     */
    private String categoryName(String id) {
        // 遍历 categories.json 里的分类表做线性查找（分类只有几条，不值得建 Map）
        for (Category c : ScriptRepository.loadCategories(this)) {
            if (c.id.equals(id)) {
                return c.name;
            }
        }
        // 找不到就退回原始 id，至少用户能看到「这个剧本被归在哪个分类下」
        return id;
    }

    /** 新建一个最小可玩的剧本骨架：一个开场节点 + 两个选项 + 一个结局 */
    private void createNew() {
        // 构造剧本对象，下面逐个字段填默认值
        Script s = new Script();
        // id 用时间戳保证唯一：user-1750000000000 这种形式，不会和内置剧本撞名
        s.id = "user-" + System.currentTimeMillis();
        s.title = "我的新剧本";
        s.scamType = "自定义骗局";
        // 默认归到「游戏账号」书架，用户可以在编辑器里改
        s.category = "game";
        s.intro = "点「编辑」开始写你的剧本。";
        s.minutes = "3-5";
        s.stageCount = 1;
        // 参考损失先写 0，作者可以按自己参考的案例改
        s.referenceLoss = 0;
        // 入口节点固定叫 n1
        s.startNode = "n1";

        // ---- 节点 1：开场，两个选项（一个上钩、一个正确做法） ----
        StoryNode n1 = new StoryNode();
        n1.id = "n1";
        n1.speaker = "陌生人";
        n1.stage = 1;
        n1.stageName = "开场";
        n1.messages.add("在吗？有个好机会跟你说一下。");
        // 选项一：高风险，跳到 n2（上当路线）
        n1.options.add(choice("听听是什么机会", "n2", "high", "轻信陌生人", 0,
                "陌生人的「机会」先别急着听，先想想他为什么找你。"));
        // 选项二：低风险，跳到 n_end（止损路线）
        n1.options.add(choice("不感兴趣，拉黑", "n_end", "low", "及时止损", 0,
                "正确。对陌生人的莫名好意保持警惕。"));
        s.nodes.put(n1.id, n1);

        // ---- 节点 2：上当路线，直接就是坏结局（不再往下跳，所以没有选项） ----
        StoryNode n2 = new StoryNode();
        n2.id = "n2";
        n2.speaker = "陌生人";
        n2.stage = 1;
        n2.stageName = "开场";
        n2.messages.add("你先转一笔钱，我马上带你赚更多。");
        // 勾成结局节点：聊天页读完这段就直接进复盘
        n2.isEnd = true;
        n2.endingTitle = "你上钩了";
        n2.endingAdvice = "任何「先转钱才能赚钱」的说法都是骗局。";
        s.nodes.put(n2.id, n2);

        // ---- 节点 n_end：正确路线的结局 ----
        StoryNode end = new StoryNode();
        end.id = "n_end";
        end.speaker = "系统";        // 系统节点不带头像名，看起来像旁白
        end.stage = 1;
        end.stageName = "结局";
        end.isEnd = true;
        end.messages.add("你没有继续理会，对方再没回你。");
        end.endingTitle = "你守住了";
        end.endingAdvice = "对陌生人的「好事」保持警惕，是最简单的反诈。";
        s.nodes.put(end.id, end);

        // 先存盘：存成功才跳编辑器，否则编辑器会读不到这个剧本
        if (ScriptStore.saveUser(this, s)) {
            Intent i = new Intent(this, EditorActivity.class);
            i.putExtra("script_id", s.id);
            startActivity(i);
        } else {
            // 存盘失败（磁盘满 / 权限异常）就留在本页提示，不要跳到空白的编辑器
            Toast.makeText(this, "创建失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 造一个选项对象的小工具方法（只是为了少写几行 new Choice + 赋值）。
     *
     * @param text     选项文案
     * @param next     选中后跳到哪个节点 id
     * @param risk     风险等级 high / medium / low
     * @param tag      风险标签（复盘页显示的小标签）
     * @param cost     这一步损失多少钱，0 表示不损失
     * @param feedback 复盘反馈：为什么危险 + 正确做法
     * @return 造好的 Choice 对象（还没加入任何节点）
     */
    private Choice choice(String text, String next, String risk, String tag, int cost, String feedback) {
        Choice c = new Choice();
        c.text = text;
        c.next = next;
        c.risk = risk;
        c.tag = tag;
        c.cost = cost;
        c.feedback = feedback;
        return c;
    }

    // ---------------- 导出 / 导入 ----------------

    /**
     * 导出第一步：记下要导出的剧本，然后拉起系统「保存文件」界面。
     *
     * <p>真正的写文件在 onActivityResult → doExport 里做，
     * 因为必须等用户选完保存位置、系统给了目标 Uri 之后才能写。
     *
     * @param id 要导出的剧本 id
     */
    private void exportScript(String id) {
        // 先记下剧本 id：回调时只有文件地址，没有剧本 id
        exportScriptId = id;
        Script s = ScriptStore.loadUser(this, id);
        // 默认文件名用剧本 id + .json；读不到剧本就给个兜底名字
        String name = s == null ? "script.json" : (s.id + ".json");
        // ACTION_CREATE_DOCUMENT：让系统弹出「保存到哪里」的界面
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        // EXTRA_TITLE 是保存框里预填的文件名，用户可以改
        i.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(i, REQ_EXPORT);
    }

    /**
     * 系统文件选择器回来后统一走这里。
     *
     * <p>用户点了取消（resultCode != RESULT_OK）或者没返回地址，直接什么都不做。
     * 否则按 requestCode 分派给导入 / 导出。
     *
     * @param requestCode 发起时传的请求码（REQ_IMPORT / REQ_EXPORT）
     * @param resultCode  系统回传的结果码，RESULT_OK 才代表用户真的选好了文件
     * @param data        回传的 Intent，里面 data.getData() 是文件 Uri
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 三种「不算数」的情况：用户取消、没有回传数据、回传里没有文件地址
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        // 用户选中的目标文件 / 源文件地址
        Uri uri = data.getData();
        if (requestCode == REQ_EXPORT) {
            doExport(uri);
        } else if (requestCode == REQ_IMPORT) {
            doImport(uri);
        }
    }

    /**
     * 导出第二步：把剧本序列化成 JSON，写进用户刚选的文件。
     *
     * @param uri 用户选好的目标文件地址（系统给的临时授权 Uri）
     */
    private void doExport(Uri uri) {
        try {
            // 重新读一遍剧本（保证导出的是最新内容，不是打开页面时的旧数据）
            Script s = ScriptStore.loadUser(this, exportScriptId);
            if (s == null) {
                // 剧本可能在别处被删了
                Toast.makeText(this, "剧本不存在", Toast.LENGTH_SHORT).show();
                return;
            }
            // 打开目标文件的输出流（系统给的临时写权限）
            OutputStream out = getContentResolver().openOutputStream(uri);
            // 明确用 UTF-8 写：中文台词不能乱码，这是导出文件能不能被同学打开的关键
            out.write(ScriptStore.toJson(s).getBytes("UTF-8"));
            out.close();
            // 提示语用 LONG，让用户看清「导出的文件可以直接发给同学」
            Toast.makeText(this, "已导出，可以发给同学了", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            // 磁盘满、文件被占用、没有写权限……统一在这里兜住，不让 App 崩
            Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 导入：把用户选中的 json 文件整份读进来，交给 ScriptStore 变成一个新剧本。
     *
     * <p>为什么要一次性全读进内存：剧本文件只有几十 KB，一次读完最简单；
     * 用 ByteArrayOutputStream 缓冲是因为 InputStream 一次读到的长度不确定，
     * 必须循环读到 0 为止才是一整个文件。
     *
     * @param uri 用户选中的源文件地址
     */
    private void doImport(Uri uri) {
        try {
            // 打开源文件的输入流
            InputStream in = getContentResolver().openInputStream(uri);
            // 用内存缓冲把整份文件攒起来
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            // 一次读 4KB，这是磁盘读写的常规块大小，兼顾速度和内存
            byte[] buf = new byte[4096];
            int n;
            // read 返回 -1 表示读完了；这里用 > 0 判断，读到 0 字节也继续下一轮
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            in.close();
            // 明确按 UTF-8 解码，不然中文台词会变乱码
            String json = new String(bos.toByteArray(), "UTF-8");
            // 交给 ScriptStore 解析并生成新 id、写进用户剧本目录；格式不对会返回 null
            Script s = ScriptStore.importJson(this, json);
            if (s == null) {
                Toast.makeText(this, "导入失败：不是有效的剧本文件", Toast.LENGTH_LONG).show();
                return;
            }
            // 导入成功：提示剧本名，然后刷新列表让新剧本立刻出现
            Toast.makeText(this, "已导入：" + s.title, Toast.LENGTH_LONG).show();
            buildList();
        } catch (Exception e) {
            // 文件读不了、JSON 解析崩了，都在这里兜住
            Toast.makeText(this, "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
