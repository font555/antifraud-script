/*
 * ---------------------------------------------------------------------------
 * NodeEditActivity —— 节点编辑器（剧本工坊里的「单个剧情节点」编辑页）
 *
 * 这个页面管的是「剧本里的一个节点」，也就是聊天页上的一段对话：
 *   - 说话人是谁（speaker）；
 *   - 对方连续发了哪几句台词（messages，一行一条）；
 *   - 这个节点是不是结局（isEnd）；是结局的话要写结局标题和结局建议；
 *   - 最多 3 个选项（options），每个选项有：文案、跳转到哪个节点、风险等级、
 *     风险标签、这一步亏多少钱、复盘反馈。
 *
 * 从哪进来：剧本工坊 → 编辑剧本（EditorActivity）→ 节点卡片上的「编辑节点」。
 *
 * 传参：Intent extra 两个 —— "script_id"（哪个剧本）、"node_id"（哪个节点）。
 *
 * 数据存到哪：点「保存」时改内存里的 StoryNode，然后由 ScriptStore.saveUser()
 * 把「整个剧本」重新写一遍到 App 私有目录。这里之所以不改单节点而是整本写，
 * 是因为用户剧本就是一个 JSON 文件，整体覆盖写最不容易写坏。
 *
 * 页面结构：顶部显示「节点 n2」；下面是说话人输入框、台词多行输入框；
 * 结局勾选框 + 结局标题 + 结局建议；再下面是最多 3 块「选项卡片」；
 * 底部是返回 / 保存 / 删除节点三个按钮。
 *
 * 阅读提示：本文件只加注释、不改代码。
 * ---------------------------------------------------------------------------
 */
package com.example.antifraud.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.antifraud.R;
import com.example.antifraud.engine.ScriptStore;
import com.example.antifraud.model.Choice;
import com.example.antifraud.model.Script;
import com.example.antifraud.model.StoryNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 节点编辑器：说话人、台词（一行一条）、是否结局、结局文案、最多 3 个选项。
 *
 * <p>为什么选项上限是 3：手机屏幕纵向空间有限，一张卡片里塞 4 个以上选项，
 * 用户就没法一眼看完「每个选项的风险标签」，反诈教学效果会打折。
 * 同时 3 个选项也刚好覆盖「上钩 / 犹豫 / 正确」三种典型选择。
 *
 * <p>风险的三个档位固定是高 / 中 / 低，对应后面 JSON 里的 "high"/"medium"/"low"，
 * 聊天页和复盘页都会读这个字段来上色。
 *
 * <p>页面上的「3 块选项卡片」是固定画满的：剧本里只有 2 个选项时，
 * 第 3 块仍然会画出来，只是「启用选项 3」不勾、输入框全空。
 * 这样做的好处是作者想加选项时不用找「添加」按钮，直接在第 3 块里填就行；
 * 代价是多占一点纵向空间，所以页面整体套在 ScrollView 里（见布局文件）。
 *
 * <p>本页所有修改都只改内存里的 StoryNode，点「保存」才由 ScriptStore 整本写盘；
 * 直接按返回键离开等于放弃修改。
 */
public class NodeEditActivity extends BaseActivity {

    /** 最多允许的选项个数；界面固定画 3 块选项卡片，改这个值要同步改布局 */
    private static final int MAX_OPTIONS = 3;

    /** 当前编辑的剧本 id（从上一页传进来） */
    private String scriptId;

    /** 当前编辑的节点 id（从上一页传进来，例如 n2） */
    private String nodeId;

    /** 内存里的整个剧本对象；保存时整本一起写盘 */
    private Script script;

    /** 内存里正在编辑的这个节点；所有输入最终都会写回它 */
    private StoryNode node;

    /** 说话人输入框：决定聊天页那句气泡上显示的名字和头像颜色 */
    private EditText etSpeaker;

    /** 台词多行输入框：一行算一条消息，空行会被忽略 */
    private EditText etMessages;

    /** 「这是结局」勾选框：勾上之后这个节点不再往下跳，直接出复盘 */
    private CheckBox cbEnd;

    /** 结局标题输入框（例如「你上钩了」），只有结局节点用得上 */
    private EditText etEndingTitle;

    /** 结局建议输入框：一句总结性的提醒，复盘页会读它 */
    private EditText etEndingAdvice;

    /** 选项卡片的竖向容器 */
    private LinearLayout llOptions;

    /** 每个选项「是否启用」的勾选框，索引 0~2 对应选项一~三 */
    private final CheckBox[] optOn = new CheckBox[MAX_OPTIONS];

    /** 每个选项的文案输入框（玩家在聊天页看到的按钮文字） */
    private final EditText[] optText = new EditText[MAX_OPTIONS];

    /** 每个选项的跳转目标节点 id 输入框；留空表示选完直接进复盘 */
    private final EditText[] optNext = new EditText[MAX_OPTIONS];

    /** 每个选项的风险标签输入框（例如「脱离官方平台」） */
    private final EditText[] optTag = new EditText[MAX_OPTIONS];

    /** 每个选项「这一步损失多少钱」的输入框，填 0 表示不损失 */
    private final EditText[] optCost = new EditText[MAX_OPTIONS];

    /** 每个选项的复盘反馈输入框：为什么要写「为什么危险 + 正确做法」 */
    private final EditText[] optFeedback = new EditText[MAX_OPTIONS];

    /**
     * 当前三个选项各自的风险等级，取值 "high" / "medium" / "low"。
     * 默认正好是「高风险 / 中风险 / 低风险」，用户点 chip 时会被改写。
     */
    private final String[] optRisk = {"high", "medium", "low"};

    /** 每个选项的三个风险 chip 控件，用来刷新选中态（[选项下标][档位下标]） */
    private final TextView[][] optChips = new TextView[MAX_OPTIONS][3];

    /**
     * 页面创建：读剧本和节点 → 找到控件 → 回填数据 → 画选项块 → 挂按钮事件。
     *
     * @param savedInstanceState 系统重建页面时回传的状态；本页一律从磁盘重读数据，
     *                           所以不使用它
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 1. 挂上布局 activity_node_edit.xml
        setContentView(R.layout.activity_node_edit);
        // 状态栏 / 导航栏安全边距
        Ui.insetContent(this);

        // 2. 从 Intent 取出「哪个剧本、哪个节点」
        scriptId = getIntent().getStringExtra("script_id");
        nodeId = getIntent().getStringExtra("node_id");
        // 3. 读剧本；剧本不存在或节点已被删掉，都不能继续编辑
        script = ScriptStore.loadUser(this, scriptId);
        if (script == null || !script.nodes.containsKey(nodeId)) {
            Toast.makeText(this, "节点不存在", Toast.LENGTH_SHORT).show();
            finish();
            return; // 下面的代码都要用 script / node，必须提前返回
        }
        // 4. 取出要编辑的节点对象（它就在内存剧本里，改它就是改剧本）
        node = script.nodes.get(nodeId);

        // 5. 顶部显示「节点 n2」，让用户明确知道自己改的是哪一个
        ((TextView) findViewById(R.id.tv_node)).setText("节点 " + nodeId);
        // 6. 找到各个输入控件
        etSpeaker = findViewById(R.id.et_speaker);
        etMessages = findViewById(R.id.et_messages);
        cbEnd = findViewById(R.id.cb_end);
        etEndingTitle = findViewById(R.id.et_ending_title);
        etEndingAdvice = findViewById(R.id.et_ending_advice);
        llOptions = findViewById(R.id.ll_options);

        // 7. 回填：说话人
        etSpeaker.setText(node.speaker);
        // 8. 回填：台词。多条台词在输入框里用换行分隔，所以这里要手工拼回多行文本。
        //    StringBuilder 避免在循环里反复拼字符串产生大量临时对象。
        StringBuilder sb = new StringBuilder();
        for (String m : node.messages) {
            // 第一条前面不加换行，之后的每一条前面都补一个换行
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(m);
        }
        etMessages.setText(sb.toString());
        // 9. 回填：是不是结局 + 结局标题 + 结局建议
        cbEnd.setChecked(node.isEnd);
        etEndingTitle.setText(node.endingTitle);
        etEndingAdvice.setText(node.endingAdvice);

        // 10. 画三块选项卡片（没有的选项也画空块，让用户知道「还能加一个」）
        buildOptions();

        // 11. 返回：直接关页面。未保存的修改只留在输入框里，丢掉即可
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        // 12. 保存：校验通过就写盘，然后顺手关掉本页回到剧本编辑页
        findViewById(R.id.btn_save).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (save()) {
                    Toast.makeText(NodeEditActivity.this, "节点已保存", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        });
        // 13. 删除节点：起始节点不许删，其它节点删完直接写盘并退出
        findViewById(R.id.btn_delete).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 起始节点是剧本入口，删掉剧本就打不开了，直接拦下来
                if (nodeId.equals(script.startNode)) {
                    Toast.makeText(NodeEditActivity.this, "起始节点不能删除", Toast.LENGTH_SHORT).show();
                    return;
                }
                // 从剧本的节点表里移除，再整本写盘，最后关页面回到上一级
                script.nodes.remove(nodeId);
                ScriptStore.saveUser(NodeEditActivity.this, script);
                finish();
            }
        });
    }

    /**
     * 重画 3 块选项卡片。
     *
     * <p>规则：第 i 块对应剧本里的第 i 个选项；剧本里没有那么多选项时传 null，
     * 卡片照样画出来，只是「启用选项」不勾、输入框全空，等于给用户留白的添加位。
     */
    private void buildOptions() {
        // 先清掉上一次画出来的卡片
        llOptions.removeAllViews();
        for (int i = 0; i < MAX_OPTIONS; i++) {
            // 有这个选项就取出数据，没有就是 null（表示空位）
            Choice c = i < node.options.size() ? node.options.get(i) : null;
            llOptions.addView(buildOptionBlock(i, c));
        }
    }

    /**
     * 造一块选项卡片。
     *
     * <p>卡片从上到下依次是：启用勾选框 → 文案 / 跳转 / 风险标签 / 金额 / 反馈 五个输入框
     * → 三个风险 chip（高 / 中 / 低）。chip 点一下立刻改内存里的等级并刷新高亮。
     *
     * @param index 这是第几个选项（0、1、2），同时是各个数组字段的下标
     * @param c     选项数据；为 null 表示这是空位
     * @return 组装好的卡片 View
     */
    private View buildOptionBlock(final int index, Choice c) {
        // 统一样式的卡片：参数是（内边距 12dp，卡片下边距 8dp）；
        // 圆角和描边由 Ui.card 内部的 px_panel 九宫格背景决定，不需要在这里给。
        LinearLayout box = Ui.card(this, 12, 8);

        // 「启用选项 N」：不勾的选项在保存时会被直接忽略，不算进剧本
        CheckBox on = new CheckBox(this);
        on.setText("启用选项 " + (index + 1));
        on.setTextSize(11);
        on.setTextColor(getColor(R.color.ink));
        // 有数据就默认勾上，空位默认不勾
        on.setChecked(c != null);
        box.addView(on);
        optOn[index] = on;

        // 五个输入框：提示文字里直接写了填写示例，用户不用去看文档
        EditText text = input("选项文案（如：行，加我私聊）", false);
        EditText next = input("跳转到节点 id（如 n2，留空=进入复盘）", false);
        EditText tag = input("风险标签（如：脱离官方平台）", false);
        EditText cost = input("这一步损失的金额（没有就填 0）", false);
        EditText feedback = input("复盘反馈：为什么危险 + 正确做法", true);
        // 按顺序加进卡片（加进去的顺序就是屏幕上的上下顺序）
        box.addView(text);
        box.addView(next);
        box.addView(tag);
        box.addView(cost);
        box.addView(feedback);
        // 存进字段数组，保存时按 index 取值
        optText[index] = text;
        optNext[index] = next;
        optTag[index] = tag;
        optCost[index] = cost;
        optFeedback[index] = feedback;

        // 有数据的选项：把已有内容回填到输入框
        if (c != null) {
            text.setText(c.text);
            next.setText(c.next);
            tag.setText(c.tag);
            // 金额是 int，setText 只认字符串，所以要转一下
            cost.setText(String.valueOf(c.cost));
            feedback.setText(c.feedback);
            // 老数据可能没写 risk，按「高风险」兜底（宁可判重也不漏判）
            optRisk[index] = c.risk == null ? "high" : c.risk;
        }

        // 风险标签行：三个 chip 横着排
        LinearLayout riskRow = new LinearLayout(this);
        riskRow.setOrientation(LinearLayout.HORIZONTAL);
        riskRow.setPadding(0, Ui.dp(this, 8), 0, 0);
        // 屏幕上显示的中文，顺序对应 optRisk 的 high / medium / low
        String[] labels = {"高风险", "中风险", "低风险"};
        for (int r = 0; r < 3; r++) {
            // 点击回调里要用到 r，先存成 final 局部变量
            final int ri = r;
            TextView chip = new PTextView(this);
            chip.setText(labels[r]);
            chip.setTextSize(9);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
            // 宽高自适应，右边留 6dp 让 chip 之间分开
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, Ui.dp(this, 6), 0);
            chip.setLayoutParams(lp);
            // 先登记到二维数组，refreshRiskChips 要靠它找到芯片刷高亮
            optChips[index][r] = chip;
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // 0=high，1=medium，2=低风险，和 labels 的顺序一一对应
                    optRisk[index] = ri == 0 ? "high" : (ri == 1 ? "medium" : "low");
                    // 只改内存，等点「保存」才落盘
                    refreshRiskChips(index);
                }
            });
            riskRow.addView(chip);
        }
        box.addView(riskRow);
        // 卡片造好后先按当前等级刷一次高亮
        refreshRiskChips(index);
        return box;
    }

    /**
     * 造一个统一样式的输入框（这里所有输入框都是代码里 new 出来的，没有用 XML 布局）。
     *
     * @param hint      输入框为空时显示的灰色提示文字
     * @param multiLine true 表示多行输入（台词和复盘反馈用），会靠上对齐并至少 2 行高
     * @return 造好的 EditText
     */
    private EditText input(String hint, boolean multiLine) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setTextSize(11);
        // 提示文字用浅色，正文用深色，避免用户分不清「已填」和「提示」
        et.setHintTextColor(getColor(R.color.ink_hint));
        et.setTextColor(getColor(R.color.ink));
        // 统一的浅色输入框背景（像素风）
        et.setBackgroundResource(R.drawable.px_input);
        // 左右 10dp、上下 8dp 内边距，文字不要贴边
        et.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        // 宽度占满一行，高度按内容
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        // 每个输入框上面留 6dp，和上一个控件拉开距离
        lp.topMargin = Ui.dp(this, 6);
        et.setLayoutParams(lp);
        if (multiLine) {
            // 多行模式：允许换行，最少 2 行高，文字从顶部开始排
            et.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            et.setMinLines(2);
            et.setGravity(Gravity.TOP);
        }
        return et;
    }

    /**
     * 按当前 optRisk[index] 刷新这一组三个 chip 的「选中态」外观。
     *
     * <p>选中：白字 + 实心按钮底；未选中：黑字 + 浅色选项底。
     * 三个 chip 里永远只有一个处于选中态。
     *
     * @param index 第几个选项（0、1、2）
     */
    private void refreshRiskChips(int index) {
        String[] labels = {"高风险", "中风险", "低风险"};
        for (int r = 0; r < 3; r++) {
            // 判断这一档是不是当前选中的等级
            boolean sel = (r == 0 && "high".equals(optRisk[index]))
                    || (r == 1 && "medium".equals(optRisk[index]))
                    || (r == 2 && "low".equals(optRisk[index]));
            TextView chip = optChips[index][r];
            // 空位还没登记芯片时会走到这里，跳过就行
            if (chip == null) {
                continue;
            }
            chip.setText(labels[r]);
            chip.setTextColor(getColor(sel ? R.color.white : R.color.ink));
            chip.setBackgroundResource(sel ? R.drawable.px_button : R.drawable.px_option);
        }
    }

    /**
     * 把界面上的内容写回节点对象，并整本剧本落盘。
     *
     * <p>校验规则：
     * 1. 台词至少要有一条（空节点在聊天页上没法显示）；
     * 2. 没勾「启用」或者文案为空的选项会被静默跳过，不算进剧本；
     * 3. 金额填错（例如写了汉字）按 0 处理，不让页面崩；
     * 4. 只要有金额损失，风险等级强制改成 high —— 这是项目的硬规则，
     *    防止作者写出「亏了 4700 却标成低风险」这种自相矛盾的剧情。
     *
     * @return true 表示保存成功；false 表示校验没过或写文件失败
     */
    private boolean save() {
        // ---- 1. 说话人：留空就默认「对方」，不要出现没有名字的气泡 ----
        String speaker = etSpeaker.getText().toString().trim();
        node.speaker = TextUtils.isEmpty(speaker) ? "对方" : speaker;

        // ---- 2. 台词：按换行切成多条，空行丢掉 ----
        // 为什么用 split("\n") 而不是让 EditText 直接存多行：
        // 编辑框里一行 = 聊天页上的一条消息，作者在编辑器里看到的排版
        // 和玩家在聊天页看到的顺序完全一致，学起来没有负担。
        List<String> messages = new ArrayList<>();
        for (String line : etMessages.getText().toString().split("\n")) {
            String t = line.trim();
            if (!t.isEmpty()) {
                messages.add(t);
            }
        }
        if (messages.isEmpty()) {
            // 一条台词都没有的节点在聊天页是空白，必须拦住
            Toast.makeText(this, "至少要写一条台词", Toast.LENGTH_SHORT).show();
            return false;
        }
        // 用新列表整体替换旧列表（这样删掉的台词才会真的消失）
        node.messages = messages;

        // ---- 3. 结局相关 ----
        node.isEnd = cbEnd.isChecked();
        node.endingTitle = etEndingTitle.getText().toString().trim();
        node.endingAdvice = etEndingAdvice.getText().toString().trim();

        // ---- 4. 收集三个选项 ----
        List<Choice> options = new ArrayList<>();
        for (int i = 0; i < MAX_OPTIONS; i++) {
            // 没勾「启用」的卡片整块跳过
            if (!optOn[i].isChecked()) {
                continue;
            }
            String t = optText[i].getText().toString().trim();
            // 勾了启用却没写文案，也当作没填，跳过
            if (t.isEmpty()) {
                continue;
            }
            // 造一个新 Choice，把五个输入框的内容抄进去
            Choice c = new Choice();
            c.text = t;
            c.next = optNext[i].getText().toString().trim();
            c.risk = optRisk[i];
            c.tag = optTag[i].getText().toString().trim();
            c.feedback = optFeedback[i].getText().toString().trim();
            try {
                // 金额按整数解析；空字符串或乱写都会抛异常
                c.cost = Integer.parseInt(optCost[i].getText().toString().trim());
            } catch (Exception e) {
                // 解析失败就当 0（不损失），页面不该因为一个输入框写错而崩掉
                c.cost = 0;
            }
            // 硬规则：只要有损失，风险必须是 high
            if (c.cost > 0) {
                c.risk = "high";
            }
            options.add(c);
        }
        // 用新列表整体替换（删掉的选项才会真的消失）
        node.options = options;

        // ---- 5. 整本剧本写盘 ----
        boolean ok = ScriptStore.saveUser(this, script);
        if (!ok) {
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
        }
        return ok;
    }
}
