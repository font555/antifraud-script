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
import com.example.antifraud.engine.SettingsStore;
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
 * 四档字号（标准 1.0 / 大 1.3 / 特大 1.7 / 超大 2.1）下，搜索框 / chips / 卡片标题都只占一行，
 * 不会溢出：搜索框、加号、chips 和卡片的内边距都走 SettingsStore.scaleBox 跟着字号放大。</p>
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
    private View plusButton;

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
        // 和下面列表的左右留白保持一致（都走 scaleBox），大字号下搜索框与卡片不会错位
        int headerPadH = SettingsStore.scaleBox(activity, Ui.dp(activity, 14));
        header.setPadding(headerPadH, 0, headerPadH, 0);

        buildSearchBox(header);
        buildChipRow(header);

        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        root.addView(header, headerLp);

        scrollView = new ScrollView(activity);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        // 加号在列表上方的搜索行里，不再悬浮在列表上：列表底部不需要任何留白
        scrollView.setPadding(SettingsStore.scaleBox(activity, Ui.dp(activity, 14)),
                SettingsStore.scaleBox(activity, Ui.dp(activity, 8)),
                SettingsStore.scaleBox(activity, Ui.dp(activity, 14)),
                SettingsStore.scaleBox(activity, Ui.dp(activity, 10)));

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

    /** 新手引导用：剧本列表所在的可视区域 */
    public View listView() {
        return scrollView;
    }

    /** 新手引导用：把列表滚回顶部 */
    public void scrollListToTop() {
        if (scrollView != null) {
            scrollView.scrollTo(0, 0);
        }
    }

    /** 新手引导用：清掉筛选与搜索，让列表显示全部剧本，高亮区不会一片空白 */
    public void resetForGuide() {
        selectedCategory = null;
        if (etSearch != null) {
            etSearch.setText("");
        }
        updateChipStyles();
        rebuildList();
        scrollListToTop();
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
        // 输入框的内边距和最小高度跟着字号放大：2.1 倍时 12sp 的字不会被上下裁掉
        int searchPadH = SettingsStore.scaleBox(activity, Ui.dp(activity, 12));
        int searchPadV = SettingsStore.scaleBox(activity, Ui.dp(activity, 9));
        etSearch.setPadding(searchPadH, searchPadV, searchPadH, searchPadV);
        etSearch.setMinHeight(SettingsStore.scaleBox(activity, Ui.dp(activity, 44)));
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

        // 搜索框 + 右侧加号放在同一行：加号在列表上方，完全不遮挡剧本
        LinearLayout searchRow = new LinearLayout(activity);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        searchRow.addView(etSearch, searchLp);

        plusButton = buildPlusButton();
        // 圆形加号按钮要装下放大后的「＋」，整体跟着字号长
        int plusSize = SettingsStore.scaleBox(activity, Ui.dp(activity, 44));
        LinearLayout.LayoutParams plusLp = new LinearLayout.LayoutParams(plusSize, plusSize);
        plusLp.leftMargin = SettingsStore.scaleBox(activity, Ui.dp(activity, 8));
        searchRow.addView(plusButton, plusLp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = SettingsStore.scaleBox(activity, Ui.dp(activity, 12));
        header.addView(searchRow, lp);
    }

    /** 分类筛选 chips：全部 + 六个分类。放不下时自动折行，不要求用户左右滑动 */
    private void buildChipRow(LinearLayout header) {
        // 原来是 HorizontalScrollView + 一行排开，2.1 倍字号下只能看到两个分类，
        // 剩下的必须手动往右划。改成 FlowLayout 后自动换成 2-3 行，全部可见。
        FlowLayout chipRow = new FlowLayout(activity);
        chipRow.setGaps(SettingsStore.scaleBox(activity, Ui.dp(activity, 8)),
                SettingsStore.scaleBox(activity, Ui.dp(activity, 8)));

        chipAll = makeChip("全部", null);
        chipRow.addView(chipAll);
        for (Category c : categories) {
            TextView chip = makeChip(c.name, c.id);
            chipById.put(c.id, chip);
            chipRow.addView(chip);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = SettingsStore.scaleBox(activity, Ui.dp(activity, 8));
        lp.bottomMargin = SettingsStore.scaleBox(activity, Ui.dp(activity, 6));
        header.addView(chipRow, lp);
    }

    private LinearLayout.LayoutParams chipLayoutParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = SettingsStore.scaleBox(activity, Ui.dp(activity, 8));
        return lp;
    }

    /** 一个小筛选块：选中 px_button 白字，未选中 px_option ink 字 */
    private TextView makeChip(String label, final String categoryId) {
        TextView chip = new PTextView(activity);
        chip.setText(label);
        // chip 的字号不像正文那样全量放大：分类名最多 5 个字，字号全放大后
        // 一个块就占掉半个屏幕，7 个分类会折成五六行把列表挤没。
        // 这里按档位给一个较小的字号，保证 7 个块能折成 2-3 行。
        float fs = SettingsStore.fontScale(activity);
        chip.setTextSize(fs >= 1.9f ? 10f : (fs >= 1.45f ? 11f : 12f));
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        // 高度跟着字号长（否则 2.1 倍时字被裁），但内边距横向只给固定值，
        // 不再按倍数放大——横向 padding 放大是"一行只放得下一个"的主因。
        chip.setMinHeight(SettingsStore.scaleBox(activity, Ui.dp(activity, 40)));
        int padH = Ui.dp(activity, 14);
        int padV = SettingsStore.scaleBox(activity, Ui.dp(activity, 6));
        chip.setPadding(padH, padV, padH, padV);
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

    /** 搜索行右侧的圆形「＋」按钮：在列表上方，不遮挡剧本 */
    private View buildPlusButton() {
        TextView plus = new PTextView(activity);
        plus.setText("＋");
        plus.setTextSize(20);
        plus.setGravity(Gravity.CENTER);
        plus.setTextColor(activity.getColor(R.color.white));
        plus.setContentDescription("新建或导入剧本");
        plus.setBackground(roundButton(activity.getColor(R.color.brand)));
        plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFloatingMenu();
            }
        });
        plus.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(70).start();
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                }
                return false;
            }
        });
        return plus;
    }

    /** 圆形像素按钮背景：直角描边 + 实心圆 */
    private GradientDrawable roundButton(int color) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(color);
        gd.setStroke(Ui.dp(activity, 2), activity.getColor(R.color.border_dark));
        return gd;
    }

    /** 新手引导高亮用：筛选行右侧的加号按钮 */
    public View fabButtonView() {
        return plusButton;
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
            int emptyPad = SettingsStore.scaleBox(activity, Ui.dp(activity, 20));
            empty.setPadding(emptyPad, emptyPad, emptyPad, emptyPad);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = SettingsStore.scaleBox(activity, Ui.dp(activity, 48));
            listBox.addView(empty, lp);
            return;
        }

        int index = 0;
        for (Script s : filtered) {
            View card = buildCard(s);
            // 逐条淡入 + 轻微上浮：列表切换时不会“啪”地整块出现
            card.setAlpha(0f);
            card.setTranslationY(Ui.dp(activity, 12));
            card.animate().alpha(1f).translationY(0f)
                    .setDuration(200)
                    .setStartDelay(Math.min(index, 9) * 26L)
                    .start();
            listBox.addView(card);
            index++;
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
        cardLp.bottomMargin = SettingsStore.scaleBox(activity, Ui.dp(activity, 10));
        card.setLayoutParams(cardLp);

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        // 卡片内边距跟着字号放大：左边多留一点，给放大的分类色条让位
        body.setPadding(SettingsStore.scaleBox(activity, Ui.dp(activity, 20)),
                SettingsStore.scaleBox(activity, Ui.dp(activity, 12)),
                SettingsStore.scaleBox(activity, Ui.dp(activity, 14)),
                SettingsStore.scaleBox(activity, Ui.dp(activity, 12)));
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
            tag.setPadding(SettingsStore.scaleBox(activity, Ui.dp(activity, 6)),
                    SettingsStore.scaleBox(activity, Ui.dp(activity, 2)),
                    SettingsStore.scaleBox(activity, Ui.dp(activity, 6)),
                    SettingsStore.scaleBox(activity, Ui.dp(activity, 2)));
            GradientDrawable tagBg = new GradientDrawable();
            tagBg.setColor(activity.getColor(R.color.brand_soft));
            tagBg.setCornerRadius(Ui.dp(activity, 3));
            tag.setBackground(tagBg);
            LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tagLp.leftMargin = SettingsStore.scaleBox(activity, Ui.dp(activity, 6));
            titleRow.addView(tag, tagLp);
        }

        // 第二行：分类名 · N 个节点 · 时长分钟
        TextView info = Ui.text(activity, infoLine(s), 10, R.color.ink_hint);
        info.setSingleLine(true);
        info.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        infoLp.topMargin = SettingsStore.scaleBox(activity, Ui.dp(activity, 6));
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
        // 用像素风弹窗，替掉系统 AlertDialog（白底圆角跟整个 App 不搭）
        final PixelDialog dlg = PixelDialog.create(activity);
        dlg.title("写一个自己的剧本");
        dlg.meta("剧本会保存在这台手机上，随时可以再编辑或导出。");

        dlg.item(0xFFE4572E, "新建空白剧本",
                "从一句开场话术开始，自己写剧情和选项",
                new PixelDialog.OnAction() {
                    @Override
                    public void onAction(int index) {
                        createBlank();
                    }
                }, 0);
        dlg.item(0xFF3F7FA6, "套模板新建",
                "选一个骗局类型，自动生成能玩的骨架",
                new PixelDialog.OnAction() {
                    @Override
                    public void onAction(int index) {
                        chooseTemplate();
                    }
                }, 1);
        dlg.item(0xFF4E7B3A, "导入剧本文件",
                "打开同学发来的 .json 剧本文件",
                new PixelDialog.OnAction() {
                    @Override
                    public void onAction(int index) {
                        host.openImport();
                    }
                }, 2);

        dlg.button("取消", false, null, 0);
        dlg.show();
    }

    /** 套模板第二步：选一个分类，按这个分类生成骨架 */
    private void chooseTemplate() {
        if (categories.isEmpty()) {
            host.toast("分类加载失败，暂时不能套模板");
            return;
        }
        final PixelDialog dlg = PixelDialog.create(activity);
        dlg.title("选一个剧本分类");
        dlg.meta("模板会按这个分类写好开场台词，你只需要往里填内容。");
        int[] accents = {0xFFC0392B, 0xFFD98C1F, 0xFF3F7FA6, 0xFF6B4C8A, 0xFF4E7B3A, 0xFF3C7A89};
        for (int i = 0; i < categories.size(); i++) {
            final Category cat = categories.get(i);
            dlg.item(accents[i % accents.length], cat.name, cat.desc,
                    new PixelDialog.OnAction() {
                        @Override
                        public void onAction(int index) {
                            createFromTemplate(cat);
                        }
                    }, i);
        }
        dlg.button("取消", false, null, 0);
        dlg.show();
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
            // 色条跟着字号一起变粗：2.1 倍下卡片的行高接近两倍，6dp 的细条会显得断开
            barWidthPx = SettingsStore.scaleBox(c, Ui.dp(c, 6));
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
