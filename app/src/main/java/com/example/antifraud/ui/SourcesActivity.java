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
import com.example.antifraud.engine.SettingsStore;
import com.example.antifraud.model.CaseSource;
import com.example.antifraud.model.Script;

import java.util.ArrayList;
import java.util.List;

/**
 * 来源页：每条来源显示 标题 / 发布方 / 日期 / 支撑哪几步 / 改编说明，点一下复制链接。
 * 答辩时评委随便抽一步问"依据呢"，当场翻到这一页。
 *
 * <p><b>从哪里进来：</b></p>
 * <ul>
 *   <li>{@link ReviewActivity} 的"查看真实案例来源"按钮 → 带 script_id，只看这一部剧本的来源；</li>
 *   <li>首页 / 我的页的"全部真实案例来源" → 不带 script_id，列出所有剧本的来源。</li>
 * </ul>
 *
 * <p><b>为什么这页很重要：</b>本作品所有剧情、金额都必须能对回公开报道。
 * 这一页就是"可溯源"的证据页：每条来源都标明标题、发布方、日期、支撑了剧本里哪几步，
 * 以及做了哪些改编（人物匿名、平台名虚构）。没有这一页，作品就只是"编的故事"。</p>
 *
 * <p><b>交互设计：</b>整张卡片可点，一点就把链接复制到剪贴板——答辩现场网络常常不通，
 * 复制链接比"点开浏览器"更实用。</p>
 *
 * <p><b>来源数据的结构（对应 model.CaseSource 六个字段）：</b></p>
 * <ul>
 *   <li>{@code name}：报道标题，可点击核对"确实有这篇"；</li>
 *   <li>{@code publisher}：发布方，用来判断权威性（央媒 / 地方公安 / 转载）；</li>
 *   <li>{@code date}：发布日期，能对上时间线；</li>
 *   <li>{@code url}：原文链接，点卡片即复制；</li>
 *   <li>{@code usedIn}：支撑剧本的哪几步，把"剧情"和"报道"一一挂上钩；</li>
 *   <li>{@code note}：改编说明，写清哪些是原文、哪些是为教学做的改动。</li>
 * </ul>
 *
 * <p><b>页面顺序：</b>先列剧本来来源卡片，最后追加一段"配图说明"，
 * 把首页 9 张自绘插图的作者与出处也交代清楚，版权上不留死角。</p>
 */
public class SourcesActivity extends BaseActivity {

    /**
     * 页面创建：按入口参数决定显示范围，然后逐条渲染来源卡片。
     *
     * @param savedInstanceState 系统恢复用的状态包，本页不使用
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sources);
        Ui.insetContent(this);   // Android 15+ 边到边适配

        LinearLayout box = findViewById(R.id.ll_sources);   // 来源卡片都往这个竖向容器里塞
        String scriptId = getIntent().getStringExtra("script_id");

        // 两种进入方式：带了 script_id 就只显示这一部剧本；没带就全部剧本都列出来
        List<Script> scripts = new ArrayList<>();
        if (scriptId != null && !scriptId.isEmpty()) {
            Script one = ScriptRepository.load(this, scriptId);
            if (one != null) {
                scripts.add(one);   // 单剧本模式
            }
        } else {
            scripts = ScriptRepository.loadAll(this);   // 全量模式
        }

        // 顶部返回栏
        // 这一行栏是代码现搭的，不走布局 XML：因为箭头 + 标题的排版只有这一处要，
        // 单独写一个 layout 文件反而更麻烦
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.text(this, "←", 22, R.color.ink);
        // 左 4dp / 右 14dp：让箭头本身靠边、同时和标题之间有间距
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

        // 一句话声明"改编边界"：先讲清规则，后面每条来源的 note 才有落脚点。
        // 这句文案本身就是合规声明的一部分，改动前要确认不违反作品的内容红线
        TextView sub = Ui.text(this, "剧本中的话术与金额均据此改编，人物已匿名、平台名已虚构", 11, R.color.ink_hint);
        sub.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 12));
        sub.setLineSpacing(Ui.dp(this, 3), 1f);
        box.addView(sub);

        // 逐条渲染：index 是"全书第几条来源"的连续编号（跨剧本累计，不断号）
        // 为什么编号要跨剧本连续：答辩时可以直接说"请看第 7 条来源"，不用先报剧本名
        // 双层循环 = 先按剧本分组，再把每个剧本自己的 sources 铺开
        int index = 0;
        for (Script s : scripts) {
            for (final CaseSource c : s.sources) {
                index++;
                View card = buildSource(index, c);   // 每一条独立成卡，互不合并
                box.addView(card);
            }
        }

        if (index == 0) {
            // 一条来源都没有 = 数据没填好，明确提示（而不是显示一片空白让人以为是加载失败）。
            // 这是作品的合规红线：宁可显示"没登记"，也不能编一条假来源充数
            box.addView(Ui.text(this, "还没有登记来源", 14, R.color.warn_red));
        }

        addImageCredits(box);   // 末尾补"配图说明"区块
    }

    /**
     * 配图说明：首页轮播的 9 张配图全部是本作品自己画的像素图，
     * 不用新闻原图、不用图库照片，所以没有第三方版权问题。
     *
     * <p>这一块是给评委看的"合规证据"：作品声明"图片自制或开源"，
     * 这里把每张图的作者与来源逐条列出来，点击可复制原页面链接。</p>
     *
     * @param box 来源页的竖向容器，说明区块直接追加在末尾
     */
    private void addImageCredits(LinearLayout box) {
        TextView title = Ui.text(this, "配图说明（本作品自绘）", 15, R.color.ink);
        title.setPadding(0, Ui.dp(this, 22), 0, Ui.dp(this, 6));   // 与上面来源卡片拉开距离
        box.addView(title);

        TextView note = Ui.text(this,
                "首页轮播的 9 张配图全部由本作品自绘（像素风格，纯几何绘制），"
                        + "不使用任何新闻原图、不依赖图库照片，因此没有第三方版权问题，也不需要署名。",
                10, R.color.ink_hint);
        note.setLineSpacing(Ui.dp(this, 3), 1f);
        note.setPadding(0, 0, 0, Ui.dp(this, 8));
        box.addView(note);

        // 读取 assets/data/covers.json：这是"9 张配图"的清单（主题、作者、来源）
        // 注意这里的 assets 是只读的、随包发布的，不涉及任何网络请求
        String json = null;
        try {
            java.io.InputStream in = getAssets().open("data/covers.json");
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];   // 4KB 缓冲，小文件读起来刚好
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            in.close();
            json = new String(out.toByteArray(), "UTF-8");   // 显式 UTF-8，避免中文变乱码
        } catch (Exception e) {
            // 清单读不到不影响来源页主体：只在说明文字后面补一句提示
            note.setText(note.getText() + "\n（配图清单读取失败）");
            return;
        }

        try {
            org.json.JSONArray arr = new org.json.JSONObject(json).optJSONArray("covers");
            if (arr == null) {
                return;   // 结构不对就静默跳过，来源卡片已经显示了，不能因此崩页面
            }
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;   // 跳过数组里的非法项
                }
                final String source = o.optString("source", "");
                // 每行格式：「主题 · 摄影：作者」换行「来源说明」
                String line = o.optString("topic", "") + " · 摄影："
                        + o.optString("author", "") + "\n" + source;
                TextView row = Ui.text(this, line, 11, R.color.ink_secondary);
                row.setLineSpacing(Ui.dp(this, 3), 1f);
                // 内边距按字号放大（封顶 1.9 倍），保证"超大"档下这块说明不被裁
                int padH = SettingsStore.scaleBox(this, Ui.dp(this, 12));
                int padV = SettingsStore.scaleBox(this, Ui.dp(this, 10));
                row.setPadding(padH, padV, padH, padV);
                row.setBackgroundResource(R.drawable.px_panel);   // 白底卡片
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = Ui.dp(this, 8);
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        copyText(source);   // 点击复制来源说明
                    }
                });
                box.addView(row, lp);
            }
        } catch (Exception ignored) {
            // JSON 解析异常同样静默：这属于"附加信息"，不值得打断整页
        }
    }

    /**
     * 把文本复制到系统剪贴板，并弹一句提示。
     *
     * @param text 要复制的文本；null 或空串直接忽略（不复制空白，也不弹提示）
     */
    private void copyText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("source", text));
            Toast.makeText(this, "已复制图片原页面链接", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 造一张来源卡片：编号 + 标题 / 发布方 · 日期 / 支撑哪几步 / 改编说明 / 链接。
     *
     * <p>字段显隐规则：{@code usedIn} 和 {@code note} 只有非空才显示，
     * 避免出现"支撑：、"这种只剩标签的空行。</p>
     *
     * @param index 第几条（从 1 开始的连续编号）
     * @param c     一条来源数据
     * @return 组装好的卡片，整张卡片可点（点击复制链接）
     */
    private View buildSource(int index, final CaseSource c) {
        LinearLayout card = Ui.card(this, 14, 10);

        // 第一行：【1】报道标题（标题可能很长，行距放宽）
        // 用中文方括号【】而不是 [ ]，是为了和像素字体的字形宽度匹配，视觉上更整齐
        TextView name = Ui.text(this, "【" + index + "】" + c.name, 14, R.color.ink);
        name.setLineSpacing(Ui.dp(this, 3), 1f);
        card.addView(name);

        // 第二行：发布方 · 日期 —— 判断来源权威性的关键信息
        TextView pub = Ui.text(this, c.publisher + "  ·  " + c.date, 10, R.color.ink_secondary);
        pub.setPadding(0, Ui.dp(this, 6), 0, 0);
        card.addView(pub);

        if (c.usedIn != null && !c.usedIn.isEmpty()) {
            // 第三行：这条来源支撑剧本的哪几步（品牌色突出，答辩时最常被问）
            TextView used = Ui.text(this, "支撑：" + c.usedIn, 10, R.color.brand);
            used.setPadding(0, Ui.dp(this, 6), 0, 0);
            used.setLineSpacing(Ui.dp(this, 3), 1f);
            card.addView(used);
        }

        if (c.note != null && !c.note.isEmpty()) {
            // 第四行：改编说明（人物匿名、平台虚构、金额对应关系等）
            TextView note = Ui.text(this, "改编说明：" + c.note, 10, R.color.ink_hint);
            note.setPadding(0, Ui.dp(this, 6), 0, 0);
            note.setLineSpacing(Ui.dp(this, 3), 1f);
            card.addView(note);
        }

        // 第五行：完整链接（字号最小，因为主要靠"点卡片复制"，不用手抄）
        // URL 故意用 9sp 排在最下面：既保证"来源可查"，又不喧宾夺主盖过标题
        TextView url = Ui.text(this, "点此复制链接  " + c.url, 9, R.color.ink_hint);
        url.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(url);

        // 整卡可点：比只点链接文字更好按（手指粗、字号放大后 URL 一行可能被截）
        card.setClickable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) {
                    // 用"case_source"作标签，和配图说明的"source"区分开
                    cm.setPrimaryClip(ClipData.newPlainText("case_source", c.url));
                    Toast.makeText(SourcesActivity.this, "已复制来源链接", Toast.LENGTH_SHORT).show();
                }
            }
        });
        return card;
    }
}
