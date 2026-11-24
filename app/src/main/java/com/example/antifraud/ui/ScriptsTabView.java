package com.example.antifraud.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.antifraud.R;
import com.example.antifraud.engine.ScriptRepository;
import com.example.antifraud.engine.ScriptStore;
import com.example.antifraud.model.Category;
import com.example.antifraud.model.Choice;
import com.example.antifraud.model.Script;
import com.example.antifraud.model.StoryNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 「剧本」Tab：顶部搜索 + 分类筛选 chips + 全部剧本平铺列表 + 右下角新建/导入按钮。
 *
 * <p>全部界面用代码搭，不新增 XML 布局也不新增 drawable：卡片底色复用 px_panel，
 * 分类色条和圆形 FAB 用 Canvas 自绘。零第三方依赖、不联网、不申请任何权限。
 * 三种字号（含「超大」1.55 倍）下，搜索框 / chips / 卡片标题都只占一行，不会溢出。</p>
 */
public class ScriptsTabView extends FrameLayout {

    /** 列表底部留白：让最后一张卡片能滚到 FAB 上方，不被挡住 */
    private static final int LIST_BOTTOM_PAD_DP = 96;

    private final Activity activity;
    private final TabHost host;

    private final List<Category> categories = new ArrayList<>();
    private final Map<String, Category> categoryById = new HashMap<>();
    private final Map<String, TextView> chipById = new HashMap<>();

    private EditText etSearch;
    private TextView chipAll;
    private ScrollView scrollView;
    private LinearLayout listBox;
    private View fabView;

    /** 当前搜索词（已 trim，转小写后匹配） */
    private String query = "";
    /** 当前筛选分类 id；null = 全部 */
    private String selectedCategory = null;
    /** 最近一次 loadAll 的结果，过滤只在这个数组上做，不重复读盘 */
    private List<Script> allScripts = new ArrayList<>();

    public ScriptsTabView(Activity a, TabHost host) {
        super(a);
        this.activity = a;
        this.host = host;
        setBackgroundColor(a.getColor(R.color.paper));

        categories.addAll(ScriptRepository.loadCategories(a));
        for (Category c : categories) {
            categoryById.put(c.id, c);
        }

        buildRoot();
        buildFab();
        updateChipStyles();
        refresh();
    }

    // ---------------- 界面搭建 ----------------

    /** 上（搜索 + chips）中（列表）下由 LinearLayout 排列，FAB 浮在 FrameLayout 右下角 */
    private void buildRoot() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(Ui.dp(activity, 14), 0, Ui.dp(activity, 14), 0);

        buildSearchBox(header);
        buildChipRow(header);

        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        root.addView(header, headerLp);

        scrollView = new ScrollView(activity);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        // 底部留白给 FAB，卡片滚到底时不会压在加号下面
        scrollView.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 8),
                Ui.dp(activity, 14), Ui.dp(activity, LIST_BOTTOM_PAD_DP));

        listBox = new LinearLayout(activity);
        listBox.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listBox, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(scrollView, scrollLp);

        addView(root, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    /** 顶部搜索框：背景 px_input，输入时实时重建列表 */
    private void buildSearchBox(LinearLayout header) {
        etSearch = new EditText(activity);
        etSearch.setHint("搜剧名 / 类型");
        etSearch.setSingleLine(true);
        etSearch.setTextSize(12);
        Ui.pixel(etSearch);
        etSearch.setTextColor(activity.getColor(R.color.ink));
        etSearch.setHintTextColor(activity.getColor(R.color.ink_hint));
        etSearch.setBackgroundResource(R.drawable.px_input);
        etSearch.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 9),
                Ui.dp(activity, 12), Ui.dp(activity, 9));
        etSearch.setInputType(InputType.TYPE_CLASS_TEXT);
        etSearch.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s == null ? "" : s.toString().trim();
                rebuildList();
                scrollView.scrollTo(0, 0);
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(activity, 12);
        header.addView(etSearch, lp);
    }

    /** 分类筛选 chips：全部 + 六个分类，超出屏幕宽度可横向滑动 */
    private void buildChipRow(LinearLayout header) {
        HorizontalScrollView hsv = new HorizontalScrollView(activity);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout chipRow = new LinearLayout(activity);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);

        chipAll = makeChip("全部", null);
        chipRow.addView(chipAll, chipLayoutParams());
        for (Category c : categories) {
            TextView chip = makeChip(c.name, c.id);
            chipById.put(c.id, chip);
            chipRow.addView(chip, chipLayoutParams());
        }

        hsv.addView(chipRow, new HorizontalScrollView.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(activity, 8);
        header.addView(hsv, lp);
    }

    private LinearLayout.LayoutParams chipLayoutParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(activity, 8);
        return lp;
    }

    /** 一个小筛选块：选中 px_button 白字，未选中 px_option ink 字 */
    private TextView makeChip(String label, final String categoryId) {
        TextView chip = new PTextView(activity);
        chip.setText(label);
        chip.setTextSize(12);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setMinHeight(Ui.dp(activity, 36));
        chip.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 7),
                Ui.dp(activity, 12), Ui.dp(activity, 7));
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectCategory(categoryId);
            }
        });
        return chip;
    }

    private void selectCategory(String categoryId) {
        selectedCategory = categoryId;
        updateChipStyles();
        rebuildList();
        scrollView.scrollTo(0, 0);
    }

    private void updateChipStyles() {
        styleChip(chipAll, selectedCategory == null);
        for (String id : chipById.keySet()) {
            styleChip(chipById.get(id), id.equals(selectedCategory));
        }
    }

    private void styleChip(TextView chip, boolean selected) {
        if (chip == null) {
            return;
        }
        chip.setBackgroundResource(selected ? R.drawable.px_button : R.drawable.px_option);
        chip.setTextColor(activity.getColor(selected ? R.color.white : R.color.ink));
    }

    /** 右下角圆形加号按钮（Canvas 自绘，不新增 drawable） */
    private void buildFab() {
        fabView = new FabView(activity);
        fabView.setContentDescription("新建或导入剧本");
        fabView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFloatingMenu();
            }
        });
        // 按下时略微变淡，给一点触感反馈
        fabView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    v.setAlpha(0.72f);
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    v.setAlpha(1f);
                }
                return false;
            }
        });

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Ui.dp(activity, 56), Ui.dp(activity, 56));
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        lp.rightMargin = Ui.dp(activity, 20);
        lp.bottomMargin = Ui.dp(activity, 20);
        addView(fabView, lp);
    }

    /** 新手引导高亮用：右下角加号按钮 */
    public View fabButtonView() {
        return fabView;
    }

    // ---------------- 对外刷新 / 筛选 ----------------

    /** 重新读全部剧本并重建列表，保留当前搜索词与分类筛选 */
    public void refresh() {
        allScripts = ScriptRepository.loadAll(activity);
        sortByCategoryOrder(allScripts);
        if (etSearch != null) {
            query = etSearch.getText().toString().trim();
        }
        rebuildList();
    }

    /** 按书架上的分类顺序排列（游戏账号 → 刷单返利 → …），同类保持原顺序 */
    private void sortByCategoryOrder(List<Script> list) {
        final java.util.Map<String, Integer> order = new java.util.HashMap<>();
        int i = 0;
        for (Category c : categories) {
            order.put(c.id, i++);
        }
        java.util.Collections.sort(list, new java.util.Comparator<Script>() {
            @Override
            public int compare(Script a, Script b) {
                Integer ka = order.get(a.category);
                Integer kb = order.get(b.category);
                int va = ka == null ? 99 : ka;
                int vb = kb == null ? 99 : kb;
                if (va != vb) {
                    return va - vb;
                }
                return 0;
            }
        });
    }

    /** 首页点书进入时调用：切到指定分类（null = 全部），并滚回列表顶部 */
    public void filterByCategory(String categoryId) {
        if (categoryId != null && !categoryById.containsKey(categoryId)) {
            categoryId = null;
        }
        selectedCategory = categoryId;
        updateChipStyles();
        rebuildList();
        if (scrollView != null) {
            scrollView.scrollTo(0, 0);
        }
    }

    // ---------------- 列表 ----------------

    private void rebuildList() {
        if (listBox == null) {
            return;
        }
        listBox.removeAllViews();

        List<Script> filtered = filterScripts();
        if (filtered.isEmpty()) {
            TextView empty = Ui.text(activity, "没有符合条件的剧本", 12, R.color.ink_hint);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 20),
                    Ui.dp(activity, 20), Ui.dp(activity, 20));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(activity, 48);
            listBox.addView(empty, lp);
            return;
        }

        for (Script s : filtered) {
            listBox.addView(buildCard(s));
        }
    }

    /** 分类筛选 + 关键词（title / scamType 包含匹配，忽略大小写） */
    private List<Script> filterScripts() {
        List<Script> result = new ArrayList<>();
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        for (Script s : allScripts) {
            if (s == null) {
                continue;
            }
            if (selectedCategory != null && !selectedCategory.equals(s.category)) {
                continue;
            }
            if (q.length() > 0) {
                String title = s.title == null ? "" : s.title.toLowerCase(Locale.ROOT);
                String type = s.scamType == null ? "" : s.scamType.toLowerCase(Locale.ROOT);
                if (!title.contains(q) && !type.contains(q)) {
                    continue;
                }
            }
            result.add(s);
        }
        return result;
    }

    /** 单张剧本卡片：左侧 6dp 分类色条 + 标题 + 「分类 · 节点数 · 时长」+ 可选的「我写的」标签 */
    private View buildCard(final Script s) {
        ScriptCard card = new ScriptCard(activity, categoryColor(s.category));
        card.setBackgroundResource(R.drawable.px_panel);
        card.setClickable(true);
        card.setContentDescription(nvl(s.title));
        // 按下时有一层淡淡的品牌色水波，和项目里的按钮保持一致
        card.setForeground(new RippleDrawable(
                ColorStateList.valueOf(activity.getColor(R.color.brand_shape)), null, null));
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.openScript(s.id);
            }
        });
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = Ui.dp(activity, 10);
        card.setLayoutParams(cardLp);

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 12),
                Ui.dp(activity, 14), Ui.dp(activity, 12));
        card.addView(body, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        // 第一行：标题（占满剩余宽度，单行省略）+ 右上角「我写的」
        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(titleRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView title = Ui.text(activity, nvl(s.title), 14, R.color.ink);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        titleRow.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (ScriptStore.isUserScript(activity, s.id)) {
            TextView tag = Ui.text(activity, "我写的", 10, R.color.brand);
            tag.setSingleLine(true);
            tag.setPadding(Ui.dp(activity, 6), Ui.dp(activity, 2),
                    Ui.dp(activity, 6), Ui.dp(activity, 2));
            GradientDrawable tagBg = new GradientDrawable();
            tagBg.setColor(activity.getColor(R.color.brand_soft));
            tagBg.setCornerRadius(Ui.dp(activity, 3));
            tag.setBackground(tagBg);
            LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tagLp.leftMargin = Ui.dp(activity, 6);
            titleRow.addView(tag, tagLp);
        }

        // 第二行：分类名 · N 个节点 · 时长分钟
        TextView info = Ui.text(activity, infoLine(s), 10, R.color.ink_hint);
        info.setSingleLine(true);
        info.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        infoLp.topMargin = Ui.dp(activity, 6);
        body.addView(info, infoLp);

        return card;
    }

    private String infoLine(Script s) {
        return categoryName(s.category) + " · " + s.nodes.size() + " 个节点 · " + durationText(s.minutes);
    }

    private String categoryName(String categoryId) {
        Category c = categoryById.get(categoryId);
        if (c != null) {
            return c.name;
        }
        return categoryId == null || categoryId.isEmpty() ? "未分类" : categoryId;
    }

    private int categoryColor(String categoryId) {
        Category c = categoryById.get(categoryId);
        return c == null ? activity.getColor(R.color.brand) : c.colorInt;
    }

    private String durationText(String minutes) {
        if (minutes == null || minutes.trim().isEmpty()) {
            return "时长未知";
        }
        return minutes.trim() + " 分钟";
    }

    private String nvl(String text) {
        return text == null || text.trim().isEmpty() ? "未命名剧本" : text;
    }

    // ---------------- FAB：新建 / 模板 / 导入 ----------------

    /** 加号菜单：空白新建 / 套模板 / 导入文件 */
    private void showFloatingMenu() {
        final String[] items = {"新建空白剧本", "套模板新建", "导入剧本文件"};
        new AlertDialog.Builder(activity)
                .setTitle("写一个自己的剧本")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            createBlank();
                        } else if (which == 1) {
                            chooseTemplate();
                        } else {
                            host.openImport();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 套模板第二步：选一个分类，按这个分类生成骨架 */
    private void chooseTemplate() {
        if (categories.isEmpty()) {
            host.toast("分类加载失败，暂时不能套模板");
            return;
        }
        final String[] names = new String[categories.size()];
        for (int i = 0; i < categories.size(); i++) {
            names[i] = categories.get(i).name;
        }
        new AlertDialog.Builder(activity)
                .setTitle("选一个剧本分类")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        createFromTemplate(categories.get(which));
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 新建空白剧本：最小可玩骨架（1 个开场节点 + 2 个选项 + 2 个结局），默认放游戏账号分类 */
    private void createBlank() {
        Script s = buildSkeleton("game", "我的新剧本",
                "在吗？有个好机会跟你说一下。", "点「编辑」开始写你的剧本。");
        saveAndEdit(s);
    }

    /** 套模板新建：标题和开场台词按分类来，骨架和空白剧本一致 */
    private void createFromTemplate(Category category) {
        String title = "我的" + category.name + "剧本";
        String intro = "点「编辑」把你遇到的真实话术写进去。开场用了一句常见的"
                + category.name + "话术。";
        Script s = buildSkeleton(category.id, title, openingLine(category.id), intro);
        saveAndEdit(s);
    }

    /**
     * 最小可玩剧本骨架：开场 n1 两个选项分别通向「被骗」和「守住」两个结局。
     * 和 StudioActivity.createNew() 的结构保持一致，这里只是按分类换了开场台词。
     */
    private Script buildSkeleton(String categoryId, String title, String openingLine, String intro) {
        Script s = new Script();
        s.id = "user-" + System.currentTimeMillis();
        s.title = title;
        s.scamType = categoryName(categoryId);
        s.category = categoryId;
        s.intro = intro;
        s.minutes = "3-5";
        s.stageCount = 1;
        s.referenceLoss = 0;
        s.startNode = "n1";

        StoryNode n1 = new StoryNode();
        n1.id = "n1";
        n1.speaker = "陌生人";
        n1.stage = 1;
        n1.stageName = "开场";
        n1.messages.add(openingLine);
        n1.options.add(choice("先按对方说的做", "n_bad", "high", "轻信陌生人", 0,
                "先停一下。凡是让你私下转账、垫钱、共享屏幕的，都要先找官方渠道核实。"));
        n1.options.add(choice("先挂断，找官方核实", "n_end", "low", "及时核实", 0,
                "正确。多问一句、多核实一次，骗子就少了机会。"));
        s.nodes.put(n1.id, n1);

        StoryNode bad = new StoryNode();
        bad.id = "n_bad";
        bad.speaker = "陌生人";
        bad.stage = 1;
        bad.stageName = "结局";
        bad.messages.add("对方立刻发来收款码，一直催你马上转账。");
        bad.isEnd = true;
        bad.endingTitle = "你上当了";
        bad.endingAdvice = "任何「先转钱才能办事」的说法都要警惕，拿不准就先问家人、老师或官方客服。";
        s.nodes.put(bad.id, bad);

        StoryNode end = new StoryNode();
        end.id = "n_end";
        end.speaker = "系统";
        end.stage = 1;
        end.stageName = "结局";
        end.isEnd = true;
        end.messages.add("你挂断了对话，打完官方电话才知道根本没有这回事。");
        end.endingTitle = "你守住了";
        end.endingAdvice = "对陌生人的「好事」保持警惕，是最简单的反诈。";
        s.nodes.put(end.id, end);

        return s;
    }

    /** 六个分类各写一句常见开场话术（不是案例数据，只是骗局里最常见的开场） */
    private String openingLine(String categoryId) {
        if ("game".equals(categoryId)) {
            return "你这号我收了，1000，诚心要。加个微信私下交易，平台抽成太狠了。";
        }
        if ("rebate".equals(categoryId)) {
            return "点赞返现的兼职了解一下？一单 5 块，先做两单试试，做完马上返你。";
        }
        if ("service".equals(categoryId)) {
            return "您好，我是商城客服，您买的东西有质量问题，这边给您双倍退款，麻烦开一下屏幕共享。";
        }
        if ("police".equals(categoryId)) {
            return "这里是市局，你名下银行卡涉嫌洗钱，请配合调查，把钱转到安全账户自证清白。";
        }
        if ("loan".equals(categoryId)) {
            return "零门槛校园贷，额度马上到账；再给你介绍个高薪兼职，交 199 押金就能上岗。";
        }
        if ("aiface".equals(categoryId)) {
            return "（视频里是熟人的脸）我手机限额了，急用钱，先借我 2000，明天就还你。";
        }
        return "在吗？有个好机会跟你说一下。";
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

    /** 保存后直接进编辑器；再通知三个页面刷新列表 */
    private void saveAndEdit(Script s) {
        if (!ScriptStore.saveUser(activity, s)) {
            host.toast("创建失败，请重试");
            return;
        }
        Intent intent = new Intent(activity, EditorActivity.class);
        intent.putExtra("script_id", s.id);
        activity.startActivity(intent);
        host.onDataChanged();
    }

    // ---------------- 自绘小控件 ----------------

    /** 卡片本体：在 px_panel 背景之上，左侧画一条 6dp 宽的分类色条（内缩，不盖住边框） */
    private static class ScriptCard extends FrameLayout {

        private final Paint barPaint = new Paint();
        private final int insetPx;
        private final int barWidthPx;

        ScriptCard(Context c, int barColor) {
            super(c);
            setWillNotDraw(false);
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setColor(barColor);
            insetPx = Ui.dp(c, 2);
            barWidthPx = Ui.dp(c, 6);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawRect(insetPx, insetPx,
                    insetPx + barWidthPx, getHeight() - insetPx, barPaint);
        }
    }

    /** 圆形 FAB：品牌红底 + 深色描边 + 白色加号，全部按 dp 画，字号放大也不会变形 */
    private static class FabView extends View {

        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint plusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int borderPx;
        private final int armLengthPx;
        private final int armThicknessPx;

        FabView(Context c) {
            super(c);
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(c.getColor(R.color.brand));

            borderPx = Ui.dp(c, 3);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(borderPx);
            strokePaint.setColor(c.getColor(R.color.border_dark));

            plusPaint.setStyle(Paint.Style.FILL);
            plusPaint.setColor(c.getColor(R.color.white));

            armLengthPx = Ui.dp(c, 10);
            armThicknessPx = Ui.dp(c, 2);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) / 2f - borderPx / 2f;
            canvas.drawCircle(cx, cy, radius, fillPaint);
            canvas.drawCircle(cx, cy, radius, strokePaint);
            canvas.drawRect(cx - armLengthPx, cy - armThicknessPx,
                    cx + armLengthPx, cy + armThicknessPx, plusPaint);
            canvas.drawRect(cx - armThicknessPx, cy - armLengthPx,
                    cx + armThicknessPx, cy + armLengthPx, plusPaint);
        }
    }
}
