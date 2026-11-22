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
 */
public class StudioActivity extends BaseActivity {

    private static final int REQ_IMPORT = 1001;
    private static final int REQ_EXPORT = 1002;

    private LinearLayout llList;
    private TextView tvEmpty;
    private String exportScriptId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_studio);
        Ui.insetContent(this);

        llList = findViewById(R.id.ll_list);
        tvEmpty = findViewById(R.id.tv_empty);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                overridePendingTransition(R.anim.page_in, R.anim.page_out);
            }
        });
        findViewById(R.id.btn_new).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                createNew();
            }
        });
        findViewById(R.id.btn_import).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(i, REQ_IMPORT);
            }
        });

        buildList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        buildList();
    }

    private void buildList() {
        llList.removeAllViews();
        List<String> ids = ScriptStore.listUserIds(this);
        tvEmpty.setVisibility(ids.isEmpty() ? View.VISIBLE : View.GONE);
        for (String id : ids) {
            Script s = ScriptStore.loadUser(this, id);
            if (s != null) {
                llList.addView(buildRow(s));
            }
        }
    }

    private View buildRow(final Script s) {
        LinearLayout card = Ui.card(this, 14, 10);

        TextView head = Ui.text(this, s.title, 14, R.color.ink);
        card.addView(head);

        TextView info = Ui.text(this, categoryName(s.category) + " · " + s.nodes.size() + " 个节点 · 起始 "
                + s.startNode, 10, R.color.ink_hint);
        info.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(info);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(this, 10), 0, 0);
        row.addView(action("编辑", R.color.white, R.drawable.px_button, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(StudioActivity.this, EditorActivity.class);
                i.putExtra("script_id", s.id);
                startActivity(i);
            }
        }));
        row.addView(action("导出", R.color.ink, R.drawable.px_option, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportScript(s.id);
            }
        }));
        row.addView(action("删除", R.color.warn_red, R.drawable.px_option, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(StudioActivity.this);
                b.setTitle("删除这个剧本？").setMessage(s.title)
                        .setPositiveButton("删除", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int w) {
                                ScriptStore.deleteUser(StudioActivity.this, s.id);
                                buildList();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }
        }));
        card.addView(row);
        return card;
    }

    private View action(String text, int color, int bg, View.OnClickListener l) {
        TextView tv = new PTextView(this);
        tv.setText(text);
        tv.setTextSize(11);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setTextColor(getColor(color));
        tv.setBackgroundResource(bg);
        tv.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(l);
        return tv;
    }

    private String categoryName(String id) {
        for (Category c : ScriptRepository.loadCategories(this)) {
            if (c.id.equals(id)) {
                return c.name;
            }
        }
        return id;
    }

    /** 新建一个最小可玩的剧本骨架：一个开场节点 + 两个选项 + 一个结局 */
    private void createNew() {
        Script s = new Script();
        s.id = "user-" + System.currentTimeMillis();
        s.title = "我的新剧本";
        s.scamType = "自定义骗局";
        s.category = "game";
        s.intro = "点「编辑」开始写你的剧本。";
        s.minutes = "3-5";
        s.stageCount = 1;
        s.referenceLoss = 0;
        s.startNode = "n1";

        StoryNode n1 = new StoryNode();
        n1.id = "n1";
        n1.speaker = "陌生人";
        n1.stage = 1;
        n1.stageName = "开场";
        n1.messages.add("在吗？有个好机会跟你说一下。");
        n1.options.add(choice("听听是什么机会", "n2", "high", "轻信陌生人", 0,
                "陌生人的「机会」先别急着听，先想想他为什么找你。"));
        n1.options.add(choice("不感兴趣，拉黑", "n_end", "low", "及时止损", 0,
                "正确。对陌生人的莫名好意保持警惕。"));
        s.nodes.put(n1.id, n1);

        StoryNode n2 = new StoryNode();
        n2.id = "n2";
        n2.speaker = "陌生人";
        n2.stage = 1;
        n2.stageName = "开场";
        n2.messages.add("你先转一笔钱，我马上带你赚更多。");
        n2.isEnd = true;
        n2.endingTitle = "你上钩了";
        n2.endingAdvice = "任何「先转钱才能赚钱」的说法都是骗局。";
        s.nodes.put(n2.id, n2);

        StoryNode end = new StoryNode();
        end.id = "n_end";
        end.speaker = "系统";
        end.stage = 1;
        end.stageName = "结局";
        end.isEnd = true;
        end.messages.add("你没有继续理会，对方再没回你。");
        end.endingTitle = "你守住了";
        end.endingAdvice = "对陌生人的「好事」保持警惕，是最简单的反诈。";
        s.nodes.put(end.id, end);

        if (ScriptStore.saveUser(this, s)) {
            Intent i = new Intent(this, EditorActivity.class);
            i.putExtra("script_id", s.id);
            startActivity(i);
        } else {
            Toast.makeText(this, "创建失败", Toast.LENGTH_SHORT).show();
        }
    }

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

    private void exportScript(String id) {
        exportScriptId = id;
        Script s = ScriptStore.loadUser(this, id);
        String name = s == null ? "script.json" : (s.id + ".json");
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(i, REQ_EXPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQ_EXPORT) {
            doExport(uri);
        } else if (requestCode == REQ_IMPORT) {
            doImport(uri);
        }
    }

    private void doExport(Uri uri) {
        try {
            Script s = ScriptStore.loadUser(this, exportScriptId);
            if (s == null) {
                Toast.makeText(this, "剧本不存在", Toast.LENGTH_SHORT).show();
                return;
            }
            OutputStream out = getContentResolver().openOutputStream(uri);
            out.write(ScriptStore.toJson(s).getBytes("UTF-8"));
            out.close();
            Toast.makeText(this, "已导出，可以发给同学了", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void doImport(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            in.close();
            String json = new String(bos.toByteArray(), "UTF-8");
            Script s = ScriptStore.importJson(this, json);
            if (s == null) {
                Toast.makeText(this, "导入失败：不是有效的剧本文件", Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, "已导入：" + s.title, Toast.LENGTH_LONG).show();
            buildList();
        } catch (Exception e) {
            Toast.makeText(this, "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
