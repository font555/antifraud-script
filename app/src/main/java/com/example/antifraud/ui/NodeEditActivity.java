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
 */
public class NodeEditActivity extends BaseActivity {

    private static final int MAX_OPTIONS = 3;

    private String scriptId;
    private String nodeId;
    private Script script;
    private StoryNode node;

    private EditText etSpeaker;
    private EditText etMessages;
    private CheckBox cbEnd;
    private EditText etEndingTitle;
    private EditText etEndingAdvice;
    private LinearLayout llOptions;

    private final CheckBox[] optOn = new CheckBox[MAX_OPTIONS];
    private final EditText[] optText = new EditText[MAX_OPTIONS];
    private final EditText[] optNext = new EditText[MAX_OPTIONS];
    private final EditText[] optTag = new EditText[MAX_OPTIONS];
    private final EditText[] optCost = new EditText[MAX_OPTIONS];
    private final EditText[] optFeedback = new EditText[MAX_OPTIONS];
    private final String[] optRisk = {"high", "medium", "low"};
    private final TextView[][] optChips = new TextView[MAX_OPTIONS][3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_node_edit);
        Ui.insetContent(this);

        scriptId = getIntent().getStringExtra("script_id");
        nodeId = getIntent().getStringExtra("node_id");
        script = ScriptStore.loadUser(this, scriptId);
        if (script == null || !script.nodes.containsKey(nodeId)) {
            Toast.makeText(this, "节点不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        node = script.nodes.get(nodeId);

        ((TextView) findViewById(R.id.tv_node)).setText("节点 " + nodeId);
        etSpeaker = findViewById(R.id.et_speaker);
        etMessages = findViewById(R.id.et_messages);
        cbEnd = findViewById(R.id.cb_end);
        etEndingTitle = findViewById(R.id.et_ending_title);
        etEndingAdvice = findViewById(R.id.et_ending_advice);
        llOptions = findViewById(R.id.ll_options);

        etSpeaker.setText(node.speaker);
        StringBuilder sb = new StringBuilder();
        for (String m : node.messages) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(m);
        }
        etMessages.setText(sb.toString());
        cbEnd.setChecked(node.isEnd);
        etEndingTitle.setText(node.endingTitle);
        etEndingAdvice.setText(node.endingAdvice);

        buildOptions();

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
                    Toast.makeText(NodeEditActivity.this, "节点已保存", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        });
        findViewById(R.id.btn_delete).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (nodeId.equals(script.startNode)) {
                    Toast.makeText(NodeEditActivity.this, "起始节点不能删除", Toast.LENGTH_SHORT).show();
                    return;
                }
                script.nodes.remove(nodeId);
                ScriptStore.saveUser(NodeEditActivity.this, script);
                finish();
            }
        });
    }

    private void buildOptions() {
        llOptions.removeAllViews();
        for (int i = 0; i < MAX_OPTIONS; i++) {
            Choice c = i < node.options.size() ? node.options.get(i) : null;
            llOptions.addView(buildOptionBlock(i, c));
        }
    }

    private View buildOptionBlock(final int index, Choice c) {
        LinearLayout box = Ui.card(this, 12, 8);

        CheckBox on = new CheckBox(this);
        on.setText("启用选项 " + (index + 1));
        on.setTextSize(11);
        on.setTextColor(getColor(R.color.ink));
        on.setChecked(c != null);
        box.addView(on);
        optOn[index] = on;

        EditText text = input("选项文案（如：行，加我私聊）", false);
        EditText next = input("跳转到节点 id（如 n2，留空=进入复盘）", false);
        EditText tag = input("风险标签（如：脱离官方平台）", false);
        EditText cost = input("这一步损失的金额（没有就填 0）", false);
        EditText feedback = input("复盘反馈：为什么危险 + 正确做法", true);
        box.addView(text);
        box.addView(next);
        box.addView(tag);
        box.addView(cost);
        box.addView(feedback);
        optText[index] = text;
        optNext[index] = next;
        optTag[index] = tag;
        optCost[index] = cost;
        optFeedback[index] = feedback;

        if (c != null) {
            text.setText(c.text);
            next.setText(c.next);
            tag.setText(c.tag);
            cost.setText(String.valueOf(c.cost));
            feedback.setText(c.feedback);
            optRisk[index] = c.risk == null ? "high" : c.risk;
        }

        LinearLayout riskRow = new LinearLayout(this);
        riskRow.setOrientation(LinearLayout.HORIZONTAL);
        riskRow.setPadding(0, Ui.dp(this, 8), 0, 0);
        String[] labels = {"高风险", "中风险", "低风险"};
        for (int r = 0; r < 3; r++) {
            final int ri = r;
            TextView chip = new PTextView(this);
            chip.setText(labels[r]);
            chip.setTextSize(9);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, Ui.dp(this, 6), 0);
            chip.setLayoutParams(lp);
            optChips[index][r] = chip;
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    optRisk[index] = ri == 0 ? "high" : (ri == 1 ? "medium" : "low");
                    refreshRiskChips(index);
                }
            });
            riskRow.addView(chip);
        }
        box.addView(riskRow);
        refreshRiskChips(index);
        return box;
    }

    private EditText input(String hint, boolean multiLine) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setTextSize(11);
        et.setHintTextColor(getColor(R.color.ink_hint));
        et.setTextColor(getColor(R.color.ink));
        et.setBackgroundResource(R.drawable.px_input);
        et.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 6);
        et.setLayoutParams(lp);
        if (multiLine) {
            et.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            et.setMinLines(2);
            et.setGravity(Gravity.TOP);
        }
        return et;
    }

    private void refreshRiskChips(int index) {
        String[] labels = {"高风险", "中风险", "低风险"};
        for (int r = 0; r < 3; r++) {
            boolean sel = (r == 0 && "high".equals(optRisk[index]))
                    || (r == 1 && "medium".equals(optRisk[index]))
                    || (r == 2 && "low".equals(optRisk[index]));
            TextView chip = optChips[index][r];
            if (chip == null) {
                continue;
            }
            chip.setText(labels[r]);
            chip.setTextColor(getColor(sel ? R.color.white : R.color.ink));
            chip.setBackgroundResource(sel ? R.drawable.px_button : R.drawable.px_option);
        }
    }

    private boolean save() {
        String speaker = etSpeaker.getText().toString().trim();
        node.speaker = TextUtils.isEmpty(speaker) ? "对方" : speaker;

        List<String> messages = new ArrayList<>();
        for (String line : etMessages.getText().toString().split("\n")) {
            String t = line.trim();
            if (!t.isEmpty()) {
                messages.add(t);
            }
        }
        if (messages.isEmpty()) {
            Toast.makeText(this, "至少要写一条台词", Toast.LENGTH_SHORT).show();
            return false;
        }
        node.messages = messages;

        node.isEnd = cbEnd.isChecked();
        node.endingTitle = etEndingTitle.getText().toString().trim();
        node.endingAdvice = etEndingAdvice.getText().toString().trim();

        List<Choice> options = new ArrayList<>();
        for (int i = 0; i < MAX_OPTIONS; i++) {
            if (!optOn[i].isChecked()) {
                continue;
            }
            String t = optText[i].getText().toString().trim();
            if (t.isEmpty()) {
                continue;
            }
            Choice c = new Choice();
            c.text = t;
            c.next = optNext[i].getText().toString().trim();
            c.risk = optRisk[i];
            c.tag = optTag[i].getText().toString().trim();
            c.feedback = optFeedback[i].getText().toString().trim();
            try {
                c.cost = Integer.parseInt(optCost[i].getText().toString().trim());
            } catch (Exception e) {
                c.cost = 0;
            }
            if (c.cost > 0) {
                c.risk = "high";
            }
            options.add(c);
        }
        node.options = options;

        boolean ok = ScriptStore.saveUser(this, script);
        if (!ok) {
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
        }
        return ok;
    }
}
