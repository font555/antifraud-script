package com.example.antifraud.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.antifraud.R;
import com.example.antifraud.engine.ScriptRepository;
import com.example.antifraud.model.CaseSource;
import com.example.antifraud.model.Script;

import java.util.ArrayList;
import java.util.List;

/**
 * 来源页：每条来源显示 标题 / 发布方 / 日期 / 支撑哪几步 / 改编说明，点一下复制链接。
 * 答辩时评委随便抽一步问"依据呢"，当场翻到这一页。
 */
public class SourcesActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sources);
        Ui.insetContent(this);

        LinearLayout box = findViewById(R.id.ll_sources);
        String scriptId = getIntent().getStringExtra("script_id");

        List<Script> scripts = new ArrayList<>();
        if (scriptId != null && !scriptId.isEmpty()) {
            Script one = ScriptRepository.load(this, scriptId);
            if (one != null) {
                scripts.add(one);
            }
        } else {
            scripts = ScriptRepository.loadAll(this);
        }

        // 顶部返回栏
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.text(this, "←", 22, R.color.ink);
        back.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                overridePendingTransition(R.anim.page_in, R.anim.page_out);
            }
        });
        topBar.addView(back);
        topBar.addView(Ui.text(this, "真实案例来源", 22, R.color.ink));
        box.addView(topBar);

        TextView sub = Ui.text(this, "剧本中的话术与金额均据此改编，人物已匿名、平台名已虚构", 11, R.color.ink_hint);
        sub.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 12));
        sub.setLineSpacing(Ui.dp(this, 3), 1f);
        box.addView(sub);

        int index = 0;
        for (Script s : scripts) {
            for (final CaseSource c : s.sources) {
                index++;
                View card = buildSource(index, c);
                box.addView(card);
            }
        }

        if (index == 0) {
            box.addView(Ui.text(this, "还没有登记来源", 14, R.color.warn_red));
        }
    }

    private View buildSource(int index, final CaseSource c) {
        LinearLayout card = Ui.card(this, 14, 10);

        TextView name = Ui.text(this, "【" + index + "】" + c.name, 14, R.color.ink);
        name.setLineSpacing(Ui.dp(this, 3), 1f);
        card.addView(name);

        TextView pub = Ui.text(this, c.publisher + "  ·  " + c.date, 10, R.color.ink_secondary);
        pub.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(pub);

        if (c.usedIn != null && !c.usedIn.isEmpty()) {
            TextView used = Ui.text(this, "支撑：" + c.usedIn, 10, R.color.brand);
            used.setPadding(0, Ui.dp(this, 6), 0, 0);
            used.setLineSpacing(Ui.dp(this, 3), 1f);
            card.addView(used);
        }

        if (c.note != null && !c.note.isEmpty()) {
            TextView note = Ui.text(this, "改编说明：" + c.note, 10, R.color.ink_hint);
            note.setPadding(0, Ui.dp(this, 6), 0, 0);
            note.setLineSpacing(Ui.dp(this, 3), 1f);
            card.addView(note);
        }

        TextView url = Ui.text(this, "点此复制链接  " + c.url, 9, R.color.ink_hint);
        url.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(url);

        card.setClickable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("case_source", c.url));
                    Toast.makeText(SourcesActivity.this, "已复制来源链接", Toast.LENGTH_SHORT).show();
                }
            }
        });
        return card;
    }
}
