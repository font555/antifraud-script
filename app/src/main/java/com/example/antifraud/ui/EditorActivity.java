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
 */
public class EditorActivity extends BaseActivity {

    private String scriptId;
    private String category;
    private Script script;

    private EditText etTitle;
    private EditText etScamType;
    private EditText etIntro;
    private LinearLayout llCats;
    private LinearLayout llNodes;
    private TextView tvCheck;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);
        Ui.insetContent(this);

        scriptId = getIntent().getStringExtra("script_id");
        script = ScriptStore.loadUser(this, scriptId);
        if (script == null) {
            Toast.makeText(this, "剧本不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        category = script.category;

        etTitle = findViewById(R.id.et_title);
        etScamType = findViewById(R.id.et_scamType);
        etIntro = findViewById(R.id.et_intro);
        llCats = findViewById(R.id.ll_cats);
        llNodes = findViewById(R.id.ll_nodes);
        tvCheck = findViewById(R.id.tv_check);

        etTitle.setText(script.title);
        etScamType.setText(script.scamType);
        etIntro.setText(script.intro);

        buildCategories();

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.btn_save).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (save()) {
                    Toast.makeText(EditorActivity.this, "已保存", Toast.LENGTH_SHORT).show();
                }
            }
        });
        findViewById(R.id.btn_add_node).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addNode();
            }
        });
        findViewById(R.id.btn_check).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                check();
            }
        });

        buildNodes();
    }

    @Override
    protected void onResume() {
        super.onResume();
        script = ScriptStore.loadUser(this, scriptId);
        if (script != null) {
            buildNodes();
        }
    }

    private void buildCategories() {
        llCats.removeAllViews();
        List<Category> cats = ScriptRepository.loadCategories(this);
        for (Category c : cats) {
            TextView chip = new PTextView(this);
            chip.setText(c.name);
            chip.setTextSize(10);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, Ui.dp(this, 8), 0);
            chip.setLayoutParams(lp);
            boolean sel = c.id.equals(category);
            chip.setTextColor(getColor(sel ? R.color.white : R.color.ink));
            chip.setBackgroundResource(sel ? R.drawable.px_button : R.drawable.px_option);
            final String cid = c.id;
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    category = cid;
                    buildCategories();
                }
            });
            llCats.addView(chip);
        }
    }

    private void buildNodes() {
        llNodes.removeAllViews();
        for (StoryNode n : script.nodes.values()) {
            llNodes.addView(buildNodeRow(n));
        }
    }

    private View buildNodeRow(final StoryNode n) {
        LinearLayout card = Ui.card(this, 12, 8);

        String first = n.messages.isEmpty() ? "(没有台词)" : n.messages.get(0);
        TextView head = Ui.text(this, n.id + "　" + n.speaker
                + (n.isEnd ? "　[结局]" : ""), 11, R.color.ink);
        card.addView(head);

        TextView msg = Ui.text(this, first, 9, R.color.ink_hint);
        msg.setPadding(0, Ui.dp(this, 5), 0, 0);
        msg.setMaxLines(1);
        msg.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(msg);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(this, 8), 0, 0);

        TextView edit = action("编辑节点");
        edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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

        if (!n.id.equals(script.startNode)) {
            TextView del = action("删除");
            del.setTextColor(getColor(R.color.warn_red));
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
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

    private TextView action(String text) {
        TextView tv = new PTextView(this);
        tv.setText(text);
        tv.setTextSize(10);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(getColor(R.color.ink));
        tv.setBackgroundResource(R.drawable.px_option);
        tv.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
        tv.setLayoutParams(lp);
        return tv;
    }

    private boolean save() {
        String title = etTitle.getText().toString().trim();
        if (TextUtils.isEmpty(title)) {
            Toast.makeText(this, "先写个标题", Toast.LENGTH_SHORT).show();
            return false;
        }
        script.title = title;
        script.scamType = etScamType.getText().toString().trim();
        script.intro = etIntro.getText().toString().trim();
        script.category = category;
        boolean ok = ScriptStore.saveUser(this, script);
        if (!ok) {
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
        }
        return ok;
    }

    private void addNode() {
        if (!save()) {
            return;
        }
        String id = "n" + (script.nodes.size() + 1);
        while (script.nodes.containsKey(id)) {
            id = id + "x";
        }
        StoryNode n = new StoryNode();
        n.id = id;
        n.speaker = "对方";
        n.stage = 1;
        n.stageName = "剧情";
        n.messages.add("在这里写对方说的话。");
        Choice c1 = new Choice();
        c1.text = "选项一";
        c1.next = script.startNode;
        c1.risk = "high";
        c1.tag = "风险标签";
        c1.feedback = "为什么危险 + 正确做法";
        n.options.add(c1);
        Choice c2 = new Choice();
        c2.text = "选项二";
        c2.next = script.startNode;
        c2.risk = "low";
        c2.tag = "正确做法";
        c2.feedback = "为什么这样是对的";
        n.options.add(c2);
        script.nodes.put(id, n);
        ScriptStore.saveUser(this, script);
        buildNodes();
    }

    /** 简化版校验：跳转是否存在、选项数量、是否有高风险、结局节点是否带标题 */
    private void check() {
        StringBuilder sb = new StringBuilder();
        if (!script.nodes.containsKey(script.startNode)) {
            sb.append("✗ 起始节点 ").append(script.startNode).append(" 不存在\n");
        }
        int high = 0;
        int endCount = 0;
        for (StoryNode n : script.nodes.values()) {
            if (n.isEnd) {
                endCount++;
                if (n.options != null && !n.options.isEmpty()) {
                    sb.append("✗ 结局节点 ").append(n.id).append(" 不该有选项\n");
                }
                if (TextUtils.isEmpty(n.endingTitle)) {
                    sb.append("△ 结局节点 ").append(n.id).append(" 缺少结局标题\n");
                }
                continue;
            }
            if (n.options.size() < 2) {
                sb.append("✗ 节点 ").append(n.id).append(" 选项少于 2 个\n");
            }
            boolean hasHigh = false;
            for (Choice c : n.options) {
                if ("high".equals(c.risk)) {
                    hasHigh = true;
                    high++;
                }
                if (!TextUtils.isEmpty(c.next) && !script.nodes.containsKey(c.next)) {
                    sb.append("✗ 节点 ").append(n.id).append(" 的跳转 ").append(c.next).append(" 不存在\n");
                }
            }
            if (!hasHigh) {
                sb.append("✗ 节点 ").append(n.id).append(" 没有高风险选项\n");
            }
        }
        if (endCount == 0) {
            sb.append("✗ 至少要有一个结局节点（勾选「这是结局」）\n");
        }
        if (sb.length() == 0) {
            sb.append("✓ 校验通过：").append(script.nodes.size()).append(" 个节点，")
                    .append(high).append(" 个高风险选项，").append(endCount).append(" 个结局\n");
            sb.append("（建议自己先玩一遍，确认剧情通顺）");
        }
        tvCheck.setText(sb.toString());
    }
}
