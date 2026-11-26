/*
 * ---------------------------------------------------------------------------
 * EditorActivity —— 剧本编辑器（剧本工坊里的「剧本信息页」）
 *
 * 这个页面管的是「一个剧本的整体信息」，不管单条台词：
 *   1. 剧本标题、诈骗类型、剧情简介、所属书架分类，四个可改的输入项；
 *   2. 节点列表：一个节点一张小卡片，可以新增节点、进节点编辑、删除节点；
 *   3. 简化版校验：检查跳转目标是否存在、结局节点是不是误加了选项。
 *
 * 从哪进来：
 *   - 剧本工坊 StudioActivity 里点某个剧本的「编辑」；
 *   - 剧本工坊「新建剧本」之后自动跳进来（StudioActivity.createNew）。
 *
 * 传参：Intent extra "script_id"，就是 ScriptStore 里这个用户剧本的 id。
 *
 * 数据存到哪：点「保存」时调用 ScriptStore.saveUser()，把整个 Script 对象
 * 序列化成 JSON，写进 App 私有目录下的用户剧本文件。全程本地，不联网、不要权限。
 *
 * 阅读提示：本文件只加注释、不改代码，注释是写给不看代码的人也能看懂的说明。
 * ---------------------------------------------------------------------------
 */
package com.example.antifraud.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
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

import java.util.List;

/**
 * 剧本编辑器（剧本级）：标题、类型、书架分类、简介、节点列表。
 * 单个节点的台词和选项在 NodeEditActivity 里编辑。
 *
 * <p>页面结构（从上到下）：
 * <ol>
 *   <li>标题 / 诈骗类型 / 剧情简介 三个输入框；</li>
 *   <li>书架分类 chip 行（可横向滚动），点一下就换分类；</li>
 *   <li>节点列表：每个节点一张卡片，显示「id + 说话人 + 首句台词」；</li>
 *   <li>底部校验结果文字 tvCheck；</li>
 *   <li>返回 / 保存 / 加节点 / 校验 四个按钮。</li>
 * </ol>
 *
 * <p>为什么每次都整体重写文件：用户剧本就是一份完整 JSON，结构简单、体积很小
 * （通常几十 KB），整体重写比增量修改更不容易把数据写坏。
 *
 * <p>线程模型：所有读写都在主线程完成。用户剧本是本地小文件，读写耗时可以忽略，
 * 这样写代码最简单，也不会出现「后台线程改数据、界面读到一半」的问题。
 */
public class EditorActivity extends BaseActivity {

    /** 当前编辑的剧本 id（从上一个页面的 Intent 里取，之后一直用它定位剧本） */
    private String scriptId;

    /** 当前选中的书架分类 id（例如 game / rebate），点分类 chip 时会被替换 */
    private String category;

    /** 内存里的剧本对象：所有修改先改它，点保存时才整个写盘 */
    private Script script;

    /** 剧本标题输入框 */
    private EditText etTitle;

    /** 诈骗类型输入框（显示在书架卡片上的那行小字标签） */
    private EditText etScamType;

    /** 剧情简介输入框（书架卡片上最多显示 3 行） */
    private EditText etIntro;

    /** 分类 chip 的横向容器；每次重画都会先 removeAllViews 清空 */
    private LinearLayout llCats;

    /** 节点列表容器：一个节点一张卡片，按 script.nodes 的遍历顺序排列 */
    private LinearLayout llNodes;

    /** 底部校验结果文本，点「校验」后把问题一条条列出来 */
    private TextView tvCheck;

    /**
     * 页面创建：读剧本 → 找到控件 → 回填数据 → 给按钮挂事件 → 画节点列表。
     *
     * @param savedInstanceState 系统重建页面时回传的状态。本页不做表单缓存，
     *                           所有数据一律从 ScriptStore 重新读，所以这里不用它。
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 1. 挂上布局 activity_editor.xml（输入区 + 分类 chip 区 + 节点列表 + 底部按钮）
        setContentView(R.layout.activity_editor);
        // 统一处理状态栏 / 导航栏安全边距，别让内容被刘海屏或手势条挡住
        Ui.insetContent(this);

        // 2. 取上一个页面传进来的剧本 id
        scriptId = getIntent().getStringExtra("script_id");
        // 3. 从 App 私有目录把用户剧本读进内存；读不到就提示后关页面
        script = ScriptStore.loadUser(this, scriptId);
        if (script == null) {
            Toast.makeText(this, "剧本不存在", Toast.LENGTH_SHORT).show();
            finish();
            return; // 下面每一步都要用 script，必须提前返回，否则会空指针
        }
        // 4. 记住当前分类，分类 chip 的高亮要用它比对
        category = script.category;

        // 5. 把布局里的控件找到并存成字段，后面各个方法都要用
        etTitle = findViewById(R.id.et_title);
        etScamType = findViewById(R.id.et_scamType);
        etIntro = findViewById(R.id.et_intro);
        llCats = findViewById(R.id.ll_cats);
        llNodes = findViewById(R.id.ll_nodes);
        tvCheck = findViewById(R.id.tv_check);

        // 6. 把剧本里已有的内容回填到输入框，用户进来就能看到现状
        etTitle.setText(script.title);
        etScamType.setText(script.scamType);
        etIntro.setText(script.intro);

        // 7. 画分类 chip（哪一个是选中态由 category 决定）
        buildCategories();

        // 8. 顶部返回：直接关页面。如果之前改过但没保存，改动只在内存里，丢掉即可
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        // 9. 保存：把输入框里的内容写回剧本并落盘，成功给个轻提示
        findViewById(R.id.btn_save).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (save()) {
                    Toast.makeText(EditorActivity.this, "已保存", Toast.LENGTH_SHORT).show();
                }
            }
        });
        // 10. 加节点：先保存当前表单，再在剧本末尾插入一个「最小可用」的节点
        findViewById(R.id.btn_add_node).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNode();
            }
        });
        // 11. 校验：不发请求、不改数据，只把明显的问题列在底部文字里
        findViewById(R.id.btn_check).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                check();
            }
        });

        // 12. 画节点列表
        buildNodes();
    }

    /**
     * 每次回到本页都会走这里。
     *
     * <p>为什么要重新读盘：用户可能刚从 NodeEditActivity 改完节点回来，
     * 那边保存的是「另一个 Script 对象」，本页内存里的 script 已经过期了，
     * 必须重新从文件读一遍，否则节点列表显示的还是旧台词。
     */
    @Override
    protected void onResume() {
        super.onResume();
        // 重新从私有目录读剧本（代价很小，换来的一定是最新数据）
        script = ScriptStore.loadUser(this, scriptId);
        if (script != null) {
            // 磁盘上的节点可能被增删了，整列表重画一次最稳
            buildNodes();
        }
    }

    /**
     * 画「书架分类」这一行 chip（小标签）。
     *
     * <p>做法：先清空容器，再按 categories.json 里登记的分类顺序一个个造 TextView。
     * 选中的分类用「白字 + 实心按钮底」，未选中的用「黑字 + 浅色选项底」。
     *
     * <p>为什么每次重画而不是就地改颜色：分类数量固定、控件很少，
     * 重建比维护「上次选中的是谁」的状态更简单，也不会残留旧视图。
     */
    private void buildCategories() {
        // 先清掉上一次画出来的所有 chip，避免重复叠加
        llCats.removeAllViews();
        // 从 assets/data/categories.json 读出全部书架分类（游戏账号、刷单返利……）
        List<Category> cats = ScriptRepository.loadCategories(this);
        for (Category c : cats) {
            // 用项目自绘的像素字体 TextView，保证和全局视觉风格一致
            TextView chip = new PTextView(this);
            // chip 上显示分类的中文名
            chip.setText(c.name);
            // 像素字体 10 号，在 chip 高度里刚好放得下
            chip.setTextSize(10);
            // 文字居中
            chip.setGravity(Gravity.CENTER);
            // 左右各 12dp、上下各 8dp 内边距，让 chip 看起来像个「标签」
            chip.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
            // 宽高都按内容自适应；右边留 8dp，chip 之间才不会粘在一起
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, Ui.dp(this, 8), 0);
            chip.setLayoutParams(lp);
            // 当前分类 == 这个 chip 的分类，就算「选中」
            boolean sel = c.id.equals(category);
            // 选中：白字 + 实心按钮背景；未选中：黑字 + 浅色选项背景
            chip.setTextColor(getColor(sel ? R.color.white : R.color.ink));
            chip.setBackgroundResource(sel ? R.drawable.px_button : R.drawable.px_option);
            // 点击事件里要用到分类 id，先存成 final 局部变量（匿名内部类只能读 final）
            final String cid = c.id;
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // 换分类只改内存里的 category，等用户点「保存」才真正写盘
                    category = cid;
                    // 重画一遍 chip，把高亮换到刚点的那一个
                    buildCategories();
                }
            });
            llCats.addView(chip);
        }
    }

    /**
     * 重画节点列表：先清空容器，再按 script.nodes 的顺序给每个节点造一行卡片。
     *
     * <p>注意：这里用 values() 遍历，顺序取决于 Map 实现，
     * 所以节点是「id 插入顺序」而不是 id 大小顺序，展示上够用即可。
     */
    private void buildNodes() {
        // 清掉旧的节点卡片，避免增删之后出现重复行
        llNodes.removeAllViews();
        for (StoryNode n : script.nodes.values()) {
            llNodes.addView(buildNodeRow(n));
        }
    }

    /**
     * 造一个节点的卡片。
     *
     * <p>卡片内容分三层：
     * 第一层是标题行（节点 id + 说话人，结局节点后面追一个「[结局]」）；
     * 第二层是第一句台词（只显示一行，超出用省略号）；
     * 第三层是操作按钮（编辑节点；起始节点没有「删除」按钮）。
     *
     * @param n 要展示的节点对象
     * @return 组装好的卡片 View
     */
    private View buildNodeRow(final StoryNode n) {
        // 统一样式的圆角卡片：参数是（内边距 12dp，卡片下边距 8dp）；
        // 圆角和描边由 Ui.card 内部的 px_panel 九宫格背景决定，不需要在这里给。
        LinearLayout card = Ui.card(this, 12, 8);

        // 取第一句台词做预览；一句都没有时给个占位文字，别让卡片空着
        String first = n.messages.isEmpty() ? "(没有台词)" : n.messages.get(0);
        // 标题行：节点 id + 全角空格 + 说话人，结局再标一个「[结局]」方便一眼看出来
        TextView head = Ui.text(this, n.id + "　" + n.speaker
                + (n.isEnd ? "　[结局]" : ""), 11, R.color.ink);
        card.addView(head);

        // 台词预览：字号小一号、颜色淡一点，和标题拉开层次
        TextView msg = Ui.text(this, first, 9, R.color.ink_hint);
        // 上边留 5dp，别贴着标题
        msg.setPadding(0, Ui.dp(this, 5), 0, 0);
        // 只显示一行，多了就省略，保证每张卡片高度一致
        msg.setMaxLines(1);
        msg.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(msg);

        // 按钮行：横向排列，两个按钮平分宽度
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(this, 8), 0, 0);

        // 「编辑节点」：先把本页表单保存掉，再带着 script_id / node_id 跳到节点编辑页
        TextView edit = action("编辑节点");
        edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 先保存，否则用户刚改的标题会被节点编辑页保存时的旧对象覆盖
                if (!save()) {
                    return;
                }
                Intent i = new Intent(EditorActivity.this, NodeEditActivity.class);
                i.putExtra("script_id", scriptId);
                i.putExtra("node_id", n.id);
                startActivity(i);
            }
        });
        row.addView(edit);

        // 起始节点是剧本入口，一旦删掉整个剧本就打不开了，所以不给「删除」按钮
        if (!n.id.equals(script.startNode)) {
            TextView del = action("删除");
            // 删除是危险操作，用警示红字
            del.setTextColor(getColor(R.color.warn_red));
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // 从内存的节点表里移除，然后立刻落盘并重画列表
                    script.nodes.remove(n.id);
                    ScriptStore.saveUser(EditorActivity.this, script);
                    buildNodes();
                }
            });
            row.addView(del);
        }
        card.addView(row);
        return card;
    }

    /**
     * 造一个「胶囊按钮」用的 TextView（卡片底部那一排操作按钮）。
     *
     * @param text 按钮上显示的文字
     * @return 还没有挂点击事件的按钮；由调用方自己 setOnClickListener
     */
    private TextView action(String text) {
        // 像素字体，和整体风格统一
        TextView tv = new PTextView(this);
        tv.setText(text);
        tv.setTextSize(10);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(getColor(R.color.ink));
        // 浅色选项底，看起来像可点的胶囊
        tv.setBackgroundResource(R.drawable.px_option);
        // 左右 10dp、上下 6dp 内边距
        tv.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        // 宽度权重 1，和旁边的按钮平分一行；左右各留 3dp，按钮之间有一点缝
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
        tv.setLayoutParams(lp);
        return tv;
    }

    /**
     * 把输入框里的内容写回剧本对象，并保存到文件。
     *
     * <p>校验规则只有一条硬性的：标题不能为空（空标题在书架上没法展示）。
     * 其它字段允许留空，留空就显示成空白，不会崩。
     *
     * @return true 表示保存成功；false 表示校验没过或者写文件失败
     */
    private boolean save() {
        // trim 去掉首尾空格，避免用户手滑敲进去的空格被存下来
        String title = etTitle.getText().toString().trim();
        if (TextUtils.isEmpty(title)) {
            // 标题是书架上唯一的识别信息，不能为空
            Toast.makeText(this, "先写个标题", Toast.LENGTH_SHORT).show();
            return false;
        }
        // 把四个字段写回内存对象（注意：此时还没落盘）
        script.title = title;
        script.scamType = etScamType.getText().toString().trim();
        script.intro = etIntro.getText().toString().trim();
        script.category = category;
        // 整个剧本序列化后写到私有目录；返回 false 说明磁盘写入失败
        boolean ok = ScriptStore.saveUser(this, script);
        if (!ok) {
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
        }
        return ok;
    }

    /**
     * 新增一个节点，并给它配好「能直接跑起来」的默认内容。
     *
     * <p>新节点默认带 1 句台词 + 2 个选项（一个 high 一个 low），
     * 这样用户加完节点不用做任何事，剧本也是合法的、能玩通的。
     * 两个默认选项都指向起始节点，避免出现「跳转到不存在的节点」。
     */
    private void addNode() {
        // 先保存当前表单；保存失败就不加节点，免得两边数据不一致
        if (!save()) {
            return;
        }
        // 用「n + 节点数」当 id，例如已有 3 个节点就是 n4
        String id = "n" + (script.nodes.size() + 1);
        // 万一这个 id 已经被占用了（比如删过节点），就不断加 x 直到不重复
        while (script.nodes.containsKey(id)) {
            id = id + "x";
        }
        // 造节点骨架
        StoryNode n = new StoryNode();
        n.id = id;
        n.speaker = "对方";       // 默认说话人，用户可改成具体角色
        n.stage = 1;              // 默认归到第 1 幕
        n.stageName = "剧情";      // 幕次名，聊天页的分隔条会显示
        n.messages.add("在这里写对方说的话。"); // 占位台词，提示用户改
        // 默认选项一：高风险（绝大多数诈骗剧情的第一个选项都是「上钩」）
        Choice c1 = new Choice();
        c1.text = "选项一";
        c1.next = script.startNode;  // 先指回起始节点，保证跳转永远有效
        c1.risk = "high";
        c1.tag = "风险标签";
        c1.feedback = "为什么危险 + 正确做法";
        n.options.add(c1);
        // 默认选项二：低风险（正确做法）
        Choice c2 = new Choice();
        c2.text = "选项二";
        c2.next = script.startNode;
        c2.risk = "low";
        c2.tag = "正确做法";
        c2.feedback = "为什么这样是对的";
        n.options.add(c2);
        // 放进节点表 → 落盘 → 重画列表
        script.nodes.put(id, n);
        ScriptStore.saveUser(this, script);
        buildNodes();
    }

    /** 简化版校验：跳转是否存在、选项数量、是否有高风险、结局节点是否带标题 */
    private void check() {
        // 用一个 StringBuilder 把发现的问题一条条攒起来，最后一次性显示
        StringBuilder sb = new StringBuilder();
        // 1. 剧本的入口节点必须存在，否则整个剧本打不开
        if (!script.nodes.containsKey(script.startNode)) {
            sb.append("✗ 起始节点 ").append(script.startNode).append(" 不存在\n");
        }
        // 统计高风险选项总数和结局节点数量，最后给一句总结
        int high = 0;
        int endCount = 0;
        // 2. 逐节点检查
        for (StoryNode n : script.nodes.values()) {
            if (n.isEnd) {
                // 结局节点：不该再往下跳，所以不允许有选项
                endCount++;
                if (n.options != null && !n.options.isEmpty()) {
                    sb.append("✗ 结局节点 ").append(n.id).append(" 不该有选项\n");
                }
                // 结局建议必须有标题（复盘页要用它当大标题），缺了只给警告不算错误
                if (TextUtils.isEmpty(n.endingTitle)) {
                    sb.append("△ 结局节点 ").append(n.id).append(" 缺少结局标题\n");
                }
                continue;
            }
            // 非结局节点至少要 2 个选项，否则玩家没得选
            if (n.options.size() < 2) {
                sb.append("✗ 节点 ").append(n.id).append(" 选项少于 2 个\n");
            }
            // 每个节点至少要有一个高风险选项，保证「有险可选、有坑可踩」
            boolean hasHigh = false;
            for (Choice c : n.options) {
                if ("high".equals(c.risk)) {
                    hasHigh = true;
                    high++;
                }
                // 跳转目标写了就必须真实存在（留空表示进复盘，是允许的）
                if (!TextUtils.isEmpty(c.next) && !script.nodes.containsKey(c.next)) {
                    sb.append("✗ 节点 ").append(n.id).append(" 的跳转 ").append(c.next).append(" 不存在\n");
                }
            }
            if (!hasHigh) {
                sb.append("✗ 节点 ").append(n.id).append(" 没有高风险选项\n");
            }
        }
        // 3. 一个结局都没有的剧本永远玩不完，必须报错
        if (endCount == 0) {
            sb.append("✗ 至少要有一个结局节点（勾选「这是结局」）\n");
        }
        // 4. 一条问题都没有，就换成「通过」的总结，并提醒自己通读一遍剧情
        if (sb.length() == 0) {
            sb.append("✓ 校验通过：").append(script.nodes.size()).append(" 个节点，")
                    .append(high).append(" 个高风险选项，").append(endCount).append(" 个结局\n");
            sb.append("（建议自己先玩一遍，确认剧情通顺）");
        }
        // 5. 结果写到底部文字控件上
        tvCheck.setText(sb.toString());
    }
}
