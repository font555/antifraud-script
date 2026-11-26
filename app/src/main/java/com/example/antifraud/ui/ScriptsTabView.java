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
 * 「剧本」Tab：顶部搜索 + 分类筛选 chips + 全部剧本平铺列表 + 搜索行右侧的新建/导入按钮。
 *
 * <p>全部界面用代码搭，不新增 XML 布局也不新增 drawable：卡片底色复用 px_panel，
 * 分类色条和圆形按钮用 Canvas 自绘。零第三方依赖、不联网、不申请任何权限。
 * 四档字号（标准 1.0 / 大 1.3 / 特大 1.7 / 超大 2.1）下都不会溢出：
 * 搜索框、加号、chips 和卡片的内边距都走 SettingsStore.scaleBox 跟着字号放大。</p>
 *
 * <p><b>关键设计：</b>
 * <ul>
 *   <li><b>只读一次盘</b>：{@link #refresh()} 把全部剧本读进 {@link #allScripts} 缓存，
 *       之后打字搜词、切分类都只在内存数组上过滤（{@link #filterScripts()}），不会反复读盘。</li>
 *   <li><b>筛选 = 分类 + 关键词同时满足</b>：分类是"等于"匹配，关键词是"包含"匹配
 *       （同时看标题和骗局类型，忽略大小写），两个条件是"与"的关系。</li>
 *   <li><b>分类 chips 自动折行</b>：用 {@link FlowLayout} 而不是 HorizontalScrollView。
 *       七个 chip 在 2.1 倍字号下一行放不下，会让人以为只有两个分类；
 *       FlowLayout 自动换成 2-3 行，全部分类一眼可见、不用左右滑。</li>
 *   <li><b>自建卡片子类</b>：{@link ScriptCard} 在 px_panel 背景上额外画左侧分类色条，
 *       色条宽度也跟着字号放大，否则大字号下行高接近两倍，6dp 的细条会显得断开。</li>
 *   <li><b>新手引导留了三个入口</b>：{@link #listView()}、{@link #scrollListToTop()}、
 *       {@link #resetForGuide()}，清掉筛选避免高亮框框到一片空白。</li>
 * </ul></p>
 *
 * <p><b>谁在用它：</b>{@link MainActivity} 的第二个 Tab 里 new 出来；{@link HomeTabView}
 * 点书架上某本书时调 {@link #filterByCategory(String)} 切到对应分类；新建/导入后调 {@link #refresh()}。</p>
 */
public class ScriptsTabView extends FrameLayout {

    /** 列表底部留白：让最后一张卡片能滚到 FAB 上方，不被挡住 */
    private static final int LIST_BOTTOM_PAD_DP = 96;

    /** 宿主 Activity：读颜色、读 assets、起页面都要用 */
    private final Activity activity;
    /** 宿主回调：打开剧本、弹 Toast、通知数据变化，都通过它转给 MainActivity */
    private final TabHost host;

    /** 全部分类（顺序来自 categories.json，决定了 chips 的排列和列表的排序） */
    private final List<Category> categories = new ArrayList<>();
    /** 分类 id -> 分类对象，用来查分类名和分类色（避免每次线性遍历） */
    private final Map<String, Category> categoryById = new HashMap<>();
    /** 分类 id -> 那个分类的 chip 视图，刷新选中态时用 */
    private final Map<String, TextView> chipById = new HashMap<>();

    /** 顶部搜索输入框 */
    private EditText etSearch;
    /** 「全部」这个 chip，单独存一份，因为它不对应任何 Category 对象 */
    private TextView chipAll;
    /** 剧本列表的滚动容器 */
    private ScrollView scrollView;
    /** 列表容器：每次重建都先清空再逐张加卡片 */
    private LinearLayout listBox;
    /** 搜索框右侧的圆形「＋」按钮（对应任务描述里的 FAB） */
    private View plusButton;

    /** 分类筛选那一整块（可折叠） */
    private FlowLayout chipRow;
    /** 分类下方的折叠箭头 */
    private TriangleView collapseArrow;
    /** 折叠区域里的「收起分类 / 展开分类」文字 */
    private TextView collapseLabel;
    /** 分类筛选是否已折叠 */
    private boolean chipsCollapsed = false;

    /** 当前搜索词（已 trim，转小写后匹配） */
    private String query = "";
    /** 当前筛选分类 id；null = 全部 */
    private String selectedCategory = null;
    /** 最近一次 loadAll 的结果，过滤只在这个数组上做，不重复读盘 */
    private List<Script> allScripts = new ArrayList<>();

    /**
     * 构造剧本 Tab：读分类 → 建界面 → 设 chip 选中态 → 首次加载列表。
     *
     * @param a    宿主 Activity
     * @param host 宿主回调（打开剧本、Toast、数据变化通知）
     */
    public ScriptsTabView(Activity a, TabHost host) {
        super(a);
        this.activity = a;
        this.host = host;
        setBackgroundColor(a.getColor(R.color.paper));

        // 分类只在这里读一次：chips、排序、卡片色条都要用，之后不再读盘
        categories.addAll(ScriptRepository.loadCategories(a));
        for (Category c : categories) {
            categoryById.put(c.id, c);
        }

        buildRoot();
        updateChipStyles();      // 初始默认选中「全部」
        refresh();               // 读全部剧本并建列表
    }

    // ---------------- 界面搭建 ----------------

    /**
     * 搭整个页面骨架：上（搜索行 + 分类 chips + 折叠条）中（列表），由竖直 LinearLayout 排列。
     * header 和 scrollView 的左右留白用同一个值，保证大字号下搜索框和卡片边缘对齐。
     */
    private void buildRoot() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        // 顶部区块：搜索行 + 分类 chips + 折叠条，三者竖直堆叠
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        // 和下面列表的左右留白保持一致（都走 scaleBox），大字号下搜索框与卡片不会错位
        int headerPadH = SettingsStore.scaleBox(activity, Ui.dp(activity, 14));
        header.setPadding(headerPadH, 0, headerPadH, 0);

        buildSearchBox(header);
        buildChipRow(header);
        buildCollapseToggle(header);

        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        root.addView(header, headerLp);

        // 列表区：占满 header 以下的全部高度（height=0 + weight=1）
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

    /** 新手引导用：剧本列表所在的可视区域（引导高亮框按它的位置画框） */
    public View listView() {
        return scrollView;
    }

    /** 新手引导用：把列表滚回顶部 */
    public void scrollListToTop() {
        if (scrollView != null) {
            scrollView.scrollTo(0, 0);
        }
    }

    /**
     * 新手引导用：清掉筛选与搜索，让列表显示全部剧本。
     * 若用户之前筛的分类下恰好没剧本，高亮框就会框在一片空白上，看起来像出 bug。
     */
    public void resetForGuide() {
        selectedCategory = null;
        if (etSearch != null) {
            etSearch.setText("");     // 清空输入框会触发 TextWatcher，其中已包含 rebuildList
        }
        updateChipStyles();
        rebuildList();                // setText 为空时可能不触发 afterTextChanged，这里再保险一次
        scrollListToTop();
    }

    /**
     * 顶部搜索框：背景 px_input，输入时实时重建列表。
     *
     * @param header 顶部容器（本方法会把"搜索行"加进去）
     */
    private void buildSearchBox(LinearLayout header) {
        etSearch = new EditText(activity);
        etSearch.setHint("搜剧名 / 类型");
        etSearch.setSingleLine(true);           // 回车即搜索，不换行
        etSearch.setTextSize(12);
        Ui.pixel(etSearch);                     // 换成像素字体
        etSearch.setTextColor(activity.getColor(R.color.ink));
        etSearch.setHintTextColor(activity.getColor(R.color.ink_hint));
        etSearch.setBackgroundResource(R.drawable.px_input);
        // 输入框的内边距和最小高度跟着字号放大：2.1 倍时 12sp 的字不会被上下裁掉
        int searchPadH = SettingsStore.scaleBox(activity, Ui.dp(activity, 12));
        int searchPadV = SettingsStore.scaleBox(activity, Ui.dp(activity, 9));
        etSearch.setPadding(searchPadH, searchPadV, searchPadH, searchPadV);
        etSearch.setMinHeight(SettingsStore.scaleBox(activity, Ui.dp(activity, 44)));
        etSearch.setInputType(InputType.TYPE_CLASS_TEXT);
        etSearch.setImeOptions(EditorInfo.IME_ACTION_SEARCH);   // 键盘右下角显示"搜索"
        // 实时搜索：每敲一个字就跑一次过滤，列表跟着变（allScripts 已缓存，不会读盘）
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // 不需要处理
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 不需要处理
            }

            @Override
            public void afterTextChanged(Editable s) {
                // 保存搜索词（trim 掉首尾空格），然后重建列表并滚回顶部
                query = s == null ? "" : s.toString().trim();
                rebuildList();
                scrollView.scrollTo(0, 0);
            }
        });

        // 搜索框 + 右侧加号放在同一行：加号在列表上方，完全不遮挡剧本
        LinearLayout searchRow = new LinearLayout(activity);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);

        // 搜索框 width=0 + weight=1：吃掉除加号以外的全部宽度
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

    /**
     * 分类筛选 chips：全部 + 六个分类。放不下时自动折行，不要求用户左右滑动。
     *
     * <p><b>为什么用 FlowLayout 而不是横向滚动：</b>原来用 HorizontalScrollView + 一行排开，
     * 2.1 倍字号下只能看到两个分类，剩下的必须手动往右划，很多同学根本不知道右边还有。
     * 换成 {@link FlowLayout} 后会按可用宽度自动折成 2-3 行，七个分类全部一眼可见。</p>
     */
    private void buildChipRow(LinearLayout header) {
        chipRow = new FlowLayout(activity);
        // 水平、竖直间距都按字号放大（8dp），行与行之间不会粘在一起
        chipRow.setGaps(SettingsStore.scaleBox(activity, Ui.dp(activity, 8)),
                SettingsStore.scaleBox(activity, Ui.dp(activity, 8)));

        // 「全部」排在最前面，它对应 selectedCategory == null
        chipAll = makeChip("全部", null);
        chipRow.addView(chipAll);
        // 其余分类按 categories.json 里的顺序依次排开
        for (Category c : categories) {
            TextView chip = makeChip(c.name, c.id);
            chipById.put(c.id, chip);       // 存进表里，后面刷新选中态用
            chipRow.addView(chip);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = SettingsStore.scaleBox(activity, Ui.dp(activity, 8));
        lp.bottomMargin = SettingsStore.scaleBox(activity, Ui.dp(activity, 6));
        header.addView(chipRow, lp);
    }

    /** chip 的布局参数：内容自适应大小，右侧留 8dp 间距（FlowLayout 里行距另由 setGaps 控制） */
    private LinearLayout.LayoutParams chipLayoutParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = SettingsStore.scaleBox(activity, Ui.dp(activity, 8));
        return lp;
    }

    /**
     * 分类下方的折叠条：居中的小三角 + 「收起分类」。点一下收起分类块，列表就能多显示几本剧本。
     * 这是纯本地状态（{@link #chipsCollapsed}），不落盘，下次进 App 默认展开。
     */
    private void buildCollapseToggle(LinearLayout header) {
        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setClickable(true);
        // 按下变半透明、抬起恢复：给一个最小可用的按压反馈（不引入水波纹 drawable）
        bar.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent e) {
                if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                    v.setAlpha(0.55f);
                } else if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP
                        || e.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) {
                    v.setAlpha(1f);
                }
                return false;      // 返回 false，让点击事件继续传给 OnClickListener
            }
        });

        // 小三角：宽度 26dp、高度 12dp，跟着字号放大
        int arrowW = SettingsStore.scaleBox(activity, Ui.dp(activity, 26));
        int arrowH = SettingsStore.scaleBox(activity, Ui.dp(activity, 12));
        collapseArrow = new TriangleView(activity);
        collapseArrow.setArrowColor(activity.getColor(R.color.ink_hint));
        bar.addView(collapseArrow, new LinearLayout.LayoutParams(arrowW, arrowH));

        // 三角右边的说明文字
        collapseLabel = Ui.text(activity, "收起分类", 9, R.color.ink_hint);
        collapseLabel.setPadding(SettingsStore.scaleBox(activity, Ui.dp(activity, 4)), 0, 0, 0);
        bar.addView(collapseLabel);

        // 整条高度 30dp（跟着字号放大），底部再留 2dp，和下面的列表分开
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                SettingsStore.scaleBox(activity, Ui.dp(activity, 30)));
        blp.bottomMargin = SettingsStore.scaleBox(activity, Ui.dp(activity, 2));
        bar.setLayoutParams(blp);
        bar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setChipsCollapsed(!chipsCollapsed);     // 点击在"收起/展开"之间取反
            }
        });
        header.addView(bar);
    }

    /** 折叠 / 展开分类筛选区：折叠时箭头朝下提示"点我可以展开"，并用 GONE 让出真实高度 */
    private void setChipsCollapsed(boolean collapsed) {
        chipsCollapsed = collapsed;
        if (chipRow != null) {
            // 折叠用 GONE 而不是 INVISIBLE：GONE 不占高度，列表才能真正多显示几行
            chipRow.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        }
        if (collapseArrow != null) {
            // 折叠时箭头朝下（提示"点我展开"），展开时箭头朝上
            collapseArrow.setPointDown(collapsed);
        }
        if (collapseLabel != null) {
            collapseLabel.setText(collapsed ? "展开分类" : "收起分类");
        }
    }

    /** 新手引导用：把分类区恢复成展开状态，避免高亮框框到空白 */
    private void ensureChipsExpanded() {
        if (chipsCollapsed) {
            setChipsCollapsed(false);
        }
    }

    /**
     * 一个小筛选块：选中 px_button 白字，未选中 px_option ink 字。
     *
     * <p>字号刻意不跟正文一起全量放大：分类名最多 5 个字，全放大后一个块就占掉半个屏幕，
     * 7 个分类会折成五六行把列表挤没。同理横向内边距给固定值、不再按倍数放大——
     * 横向 padding 放大是"一行只放得下一个"的主因。</p>
     */
    private TextView makeChip(String label, final String categoryId) {
        TextView chip = new PTextView(activity);     // 用像素字体
        chip.setText(label);
        // 按档位给一个较小的字号，保证 7 个块能折成 2-3 行
        float fs = SettingsStore.fontScale(activity);
        chip.setTextSize(fs >= 1.9f ? 10f : (fs >= 1.45f ? 11f : 12f));
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        // 高度跟着字号长（否则 2.1 倍时字被裁），横向内边距则固定 14dp
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

    /**
     * 选中某个分类并刷新列表。
     *
     * @param categoryId 分类 id；null 表示"全部"
     */
    private void selectCategory(String categoryId) {
        selectedCategory = categoryId;
        updateChipStyles();
        rebuildList();
        scrollView.scrollTo(0, 0);      // 切分类后回到列表顶部，避免停在上一类的位置
    }

    /** 按 selectedCategory 刷新所有 chip 的选中样式（「全部」和 6 个分类都要刷） */
    private void updateChipStyles() {
        styleChip(chipAll, selectedCategory == null);
        for (String id : chipById.keySet()) {
            styleChip(chipById.get(id), id.equals(selectedCategory));
        }
    }

    /**
     * 单个 chip 的选中态样式：选中 = 品牌红实心 + 白字，未选中 = 白底描边 + 墨色字。
     *
     * @param chip     要刷新的 chip（可能为 null，例如分类还没建好时）
     * @param selected 是否选中
     */
    private void styleChip(TextView chip, boolean selected) {
        if (chip == null) {
            return;
        }
        chip.setBackgroundResource(selected ? R.drawable.px_button : R.drawable.px_option);
        chip.setTextColor(activity.getColor(selected ? R.color.white : R.color.ink));
    }

    /**
     * 搜索行右侧的圆形「＋」按钮：在列表上方，不遮挡剧本。
     * 点击弹「新建 / 套模板 / 导入」菜单，按下时轻微缩小做按压反馈。
     *
     * @return 建好的按钮视图
     */
    private View buildPlusButton() {
        TextView plus = new PTextView(activity);
        plus.setText("＋");
        plus.setTextSize(20);
        plus.setGravity(Gravity.CENTER);
        plus.setTextColor(activity.getColor(R.color.white));
        plus.setContentDescription("新建或导入剧本");       // 无障碍读屏用
        plus.setBackground(roundButton(activity.getColor(R.color.brand)));
        plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFloatingMenu();
            }
        });
        // 按下缩到 92%、抬起恢复：像素风的"点得动"的手感
        plus.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(70).start();
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                }
                return false;      // 返回 false，点击事件继续传给 OnClickListener
            }
        });
        return plus;
    }

    /** 圆形像素按钮背景：直角描边 + 实心圆 */
    private GradientDrawable roundButton(int color) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);      // 正圆
        gd.setColor(color);
        gd.setStroke(Ui.dp(activity, 2), activity.getColor(R.color.border_dark));
        return gd;
    }

    /** 新手引导高亮用：筛选行右侧的加号按钮 */
    public View fabButtonView() {
        return plusButton;
    }

    // ---------------- 对外刷新 / 筛选 ----------------

    /**
     * 重新读全部剧本并重建列表，保留当前搜索词与分类筛选。
     * 新建 / 导入 / 编辑保存之后会调用（由 MainActivity 的 onDataChanged 转发）。
     */
    public void refresh() {
        // 这里才是唯一读盘的地方：一次性读进 allScripts 缓存
        allScripts = ScriptRepository.loadAll(activity);
        sortByCategoryOrder(allScripts);
        // 输入框里可能已经有机主打的字，同步回 query，保证刷新后筛选条件不丢
        if (etSearch != null) {
            query = etSearch.getText().toString().trim();
        }
        rebuildList();
    }

    /**
     * 按书架上的分类顺序排列（游戏账号 → 刷单返利 → …），同类保持原顺序。
     *
     * <p>用 {@link java.util.Collections#sort} 是稳定排序：比较返回 0 的两条会保持
     * 输入顺序，所以同一分类里的剧本先后不会被搅乱。未知分类统一排到最后（权重 99）。</p>
     *
     * @param list 要就地排序的剧本列表
     */
    private void sortByCategoryOrder(List<Script> list) {
        // 先建"分类 id -> 名次"的映射，比较时 O(1) 查
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
                // 分类不在列表里（用户自建分类等）统一给 99，排到所有已知分类后面
                int va = ka == null ? 99 : ka;
                int vb = kb == null ? 99 : kb;
                if (va != vb) {
                    return va - vb;
                }
                // 同一分类：返回 0，保持原本的先后（稳定排序）
                return 0;
            }
        });
    }

    /**
     * 首页点书进入时调用：切到指定分类（null = 全部），并滚回列表顶部。
     *
     * @param categoryId 目标分类 id；传进不存在或为空的 id 时按"全部"处理
     */
    public void filterByCategory(String categoryId) {
        // 校验一遍：历史数据里可能有已删除的分类，此时退回"全部"而不是筛出一个空列表
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

    /**
     * 重建整个列表：过滤 → 没有结果就显示空状态 → 有结果就逐张建卡并做入场动画。
     */
    private void rebuildList() {
        if (listBox == null) {
            return;      // 界面还没搭完（buildRoot 之前）直接跳过
        }
        listBox.removeAllViews();

        List<Script> filtered = filterScripts();
        if (filtered.isEmpty()) {
            // 空状态：不显示空白页，给一句"没有符合条件的剧本"，用户才知道是筛选的问题
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
            // 逐条淡入 + 轻微上浮：列表切换时不会"啪"地整块出现
            card.setAlpha(0f);
            card.setTranslationY(Ui.dp(activity, 12));
            // 阶梯延迟：每张比上一张晚 26ms，最多叠到第 9 张（Math.min(index, 9)），
            // 否则剧本多时最后一张要等好几秒才出现
            card.animate().alpha(1f).translationY(0f)
                    .setDuration(200)
                    .setStartDelay(Math.min(index, 9) * 26L)
                    .start();
            listBox.addView(card);
            index++;
        }
    }

    /**
     * 分类筛选 + 关键词，两个条件是"与"的关系：
     * <ol>
     *   <li>分类：{@code selectedCategory} 为 null 表示"全部"，否则要求 {@code s.category} 与它完全相等；</li>
     *   <li>关键词：{@code query} 为空则跳过，否则要求标题或骗局类型里包含这个词（两边都转小写再比）。</li>
     * </ol>
     * 两个条件都不拦的剧本才会进入结果列表。
     *
     * @return 过滤后的剧本列表（新对象，不改动 allScripts）
     */
    private List<Script> filterScripts() {
        List<Script> result = new ArrayList<>();
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);   // 关键词只转一次小写
        for (Script s : allScripts) {
            if (s == null) {
                continue;      // 防御坏数据，避免空指针
            }
            // 条件一：分类过滤。"全部"（null）时不拦
            if (selectedCategory != null && !selectedCategory.equals(s.category)) {
                continue;
            }
            // 条件二：关键词过滤。标题和骗局类型任一包含即通过
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

    /** 单张剧本卡片：左侧分类色条 + 标题 + 「分类 · 节点数 · 时长」+ 可选的「我写的」标签，点击进聊天页 */
    private View buildCard(final Script s) {
        // 卡片本体是自绘的 FrameLayout 子类，构造时把分类色传进去，onDraw 里画左侧色条
        ScriptCard card = new ScriptCard(activity, categoryColor(s.category));
        card.setBackgroundResource(R.drawable.px_panel);
        card.setClickable(true);
        card.setContentDescription(nvl(s.title));      // 无障碍读屏念剧本名
        // 按下时有一层淡淡的品牌色水波，和项目里的按钮保持一致
        card.setForeground(new RippleDrawable(
                ColorStateList.valueOf(activity.getColor(R.color.brand_shape)), null, null));
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.openScript(s.id);      // 交给 MainActivity 启动聊天页
            }
        });
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = SettingsStore.scaleBox(activity, Ui.dp(activity, 10));   // 卡片间距 10dp
        card.setLayoutParams(cardLp);

        // 卡片内部：一个竖直 LinearLayout 装两行文字。
        // 左边内边距给 20dp（比右边 14dp 大），是给左侧那条分类色条让位
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

        // 标题 weight=1 吃满宽度、单行 + 尾部省略；右边的「我写的」标签才不会被挤掉
        TextView title = Ui.text(activity, nvl(s.title), 14, R.color.ink);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        titleRow.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // 只有玩家自己写的剧本才加「我写的」标签，内置剧本不加
        if (ScriptStore.isUserScript(activity, s.id)) {
            TextView tag = Ui.text(activity, "我写的", 10, R.color.brand);
            tag.setSingleLine(true);
            tag.setPadding(SettingsStore.scaleBox(activity, Ui.dp(activity, 6)),
                    SettingsStore.scaleBox(activity, Ui.dp(activity, 2)),
                    SettingsStore.scaleBox(activity, Ui.dp(activity, 6)),
                    SettingsStore.scaleBox(activity, Ui.dp(activity, 2)));
            // 浅品牌色圆角小底（3dp 圆角，比卡片边框柔和一点）
            GradientDrawable tagBg = new GradientDrawable();
            tagBg.setColor(activity.getColor(R.color.brand_soft));
            tagBg.setCornerRadius(Ui.dp(activity, 3));
            tag.setBackground(tagBg);
            LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tagLp.leftMargin = SettingsStore.scaleBox(activity, Ui.dp(activity, 6));    // 和标题隔 6dp
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

    /**
     * 卡片第二行的文案：「分类名 · N 个节点 · 时长」。
     *
     * @param s 剧本数据
     * @return 拼好的信息行
     */
    private String infoLine(Script s) {
        return categoryName(s.category) + " · " + s.nodes.size() + " 个节点 · " + durationText(s.minutes);
    }

    /**
     * 分类 id 转中文名。
     *
     * @param categoryId 分类 id（可能为 null 或已删除的分类）
     * @return 分类名；查不到时返回"未分类"或原 id
     */
    private String categoryName(String categoryId) {
        Category c = categoryById.get(categoryId);
        if (c != null) {
            return c.name;
        }
        // 查不到：空 id 显示"未分类"，否则原样显示 id（至少能看出这是哪个分类）
        return categoryId == null || categoryId.isEmpty() ? "未分类" : categoryId;
    }

    /**
     * 分类对应的颜色（卡片左侧色条用）。
     *
     * @param categoryId 分类 id
     * @return 分类色；查不到时退回品牌红
     */
    private int categoryColor(String categoryId) {
        Category c = categoryById.get(categoryId);
        return c == null ? activity.getColor(R.color.brand) : c.colorInt;
    }

    /**
     * 时长文字：分钟数字段 + "分钟"。
     *
     * @param minutes 数据里的时长（字符串，例如 "3-5"）
     * @return 展示用文案；为空时返回"时长未知"
     */
    private String durationText(String minutes) {
        if (minutes == null || minutes.trim().isEmpty()) {
            return "时长未知";
        }
        return minutes.trim() + " 分钟";
    }

    /**
     * 空值兜底：标题为空时显示"未命名剧本"，避免卡片上出现一条空白。
     *
     * @param text 原始文字
     * @return 非空文字
     */
    private String nvl(String text) {
        return text == null || text.trim().isEmpty() ? "未命名剧本" : text;
    }

    // ---------------- FAB：新建 / 模板 / 导入 ----------------

    /**
     * 加号菜单：空白新建 / 套模板 / 导入文件。
     * 用像素风弹窗，替掉系统 AlertDialog（白底圆角跟整个 App 不搭）。
     */
    private void showFloatingMenu() {
        // 用像素风弹窗，替掉系统 AlertDialog（白底圆角跟整个 App 不搭）
        final PixelDialog dlg = PixelDialog.create(activity);
        dlg.title("写一个自己的剧本");
        dlg.meta("剧本会保存在这台手机上，随时可以再编辑或导出。");

        // 三个入口各配一个主题色圆点：橙（新建）/ 蓝（模板）/ 绿（导入）
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
                        host.openImport();      // 交给 MainActivity 走系统文件选择器
                    }
                }, 2);

        dlg.button("取消", false, null, 0);
        dlg.show();
    }

    /**
     * 套模板第二步：选一个分类，按这个分类生成骨架。
     * 和第一步是串行的两个弹窗（第一步关掉才弹第二个）。
     */
    private void chooseTemplate() {
        if (categories.isEmpty()) {
            // 分类都没读到就没法套模板，直接提示，不弹一个空列表
            host.toast("分类加载失败，暂时不能套模板");
            return;
        }
        final PixelDialog dlg = PixelDialog.create(activity);
        dlg.title("选一个剧本分类");
        dlg.meta("模板会按这个分类写好开场台词，你只需要往里填内容。");
        // 六个强调色循环使用，分类多于 6 个时从头再来一遍
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

    /**
     * 套模板新建：标题和开场台词按分类来，骨架和空白剧本一致。
     *
     * @param category 选中的分类
     */
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
     *
     * @param categoryId  剧本所属分类 id
     * @param title       剧本标题
     * @param openingLine 开场那句骗子话术
     * @param intro       剧本简介（展示在剧本详情里）
     * @return 刚建好的剧本对象（含 n1 / n_bad / n_end 三个节点）
     */
    private Script buildSkeleton(String categoryId, String title, String openingLine, String intro) {
        Script s = new Script();
        // id 用时间戳生成，保证同一台手机上不重复
        s.id = "user-" + System.currentTimeMillis();
        s.title = title;
        s.scamType = categoryName(categoryId);
        s.category = categoryId;
        s.intro = intro;
        s.minutes = "3-5";       // 骨架默认标注 3-5 分钟
        s.stageCount = 1;        // 骨架只有一幕
        s.referenceLoss = 0;     // 自建剧本没有真实案件参考损失
        s.startNode = "n1";

        // 节点一：开场，两个选项分别通向坏结局和好结局
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

        // 节点二：坏结局（上当了）
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

        // 节点三：好结局（守住了）
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

    /**
     * 六个分类各写一句常见开场话术（不是案例数据，只是骗局里最常见的开场）。
     *
     * @param categoryId 分类 id
     * @return 这个分类最典型的开场白；未知分类给一句通用话术
     */
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

    /**
     * 造一个选项对象（骨架用，六字段一次填全）。
     *
     * @param text     选项文字
     * @param next     选完后跳去的节点 id
     * @param risk     风险等级（high / medium / low）
     * @param tag      选项标签（复盘页显示）
     * @param cost     这次操作的金钱损失
     * @param feedback 复盘页的讲解文案
     * @return 填好的 Choice
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

    /**
     * 保存后直接进编辑器；再通知三个页面刷新列表。
     *
     * @param s 要保存的新剧本
     */
    private void saveAndEdit(Script s) {
        if (!ScriptStore.saveUser(activity, s)) {
            // 保存失败（磁盘满 / 权限问题）：提示后留在原页，不跳编辑器
            host.toast("创建失败，请重试");
            return;
        }
        // 进编辑器，让用户马上接着写内容
        Intent intent = new Intent(activity, EditorActivity.class);
        intent.putExtra("script_id", s.id);
        activity.startActivity(intent);
        // 通知首页/我的页等刷新"我写的 N 本"统计
        host.onDataChanged();
    }

    // ---------------- 自绘小控件 ----------------

    /**
     * 卡片本体：在 px_panel 背景之上，左侧画一条 6dp 宽的分类色条（内缩，不盖住边框）。
     *
     * <p>色条位置：x 从 2dp 到 2dp+条宽，y 从 2dp 到底部 -2dp。
     * 四周留 2dp 是为了不压住 px_panel 自带的 1dp 深色边框，
     * 否则色条会看起来"糊"在边框上。</p>
     */
    private static class ScriptCard extends FrameLayout {

        /** 画分类色条用的画笔（FILL 实心，颜色在构造时定死） */
        private final Paint barPaint = new Paint();
        /** 色条与卡片四边的内缩量（2dp），避免压住卡片描边 */
        private final int insetPx;
        /** 色条宽度（6dp，跟着字号放大） */
        private final int barWidthPx;

        /**
         * @param c        上下文
         * @param barColor 这个分类对应的色条颜色
         */
        ScriptCard(Context c, int barColor) {
            super(c);
            // FrameLayout 默认不调 onDraw，必须显式打开才会走到下面的绘制
            setWillNotDraw(false);
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setColor(barColor);
            insetPx = Ui.dp(c, 2);
            // 色条跟着字号一起变粗：2.1 倍下卡片的行高接近两倍，6dp 的细条会显得断开
            barWidthPx = SettingsStore.scaleBox(c, Ui.dp(c, 6));
        }

        /**
         * 先让父类画背景（px_panel），再在左侧叠一条分类色条。
         *
         * @param canvas 画布
         */
        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            // 矩形范围：左边内缩 2dp，右边 = inset + 条宽，上下都内缩 2dp
            canvas.drawRect(insetPx, insetPx,
                    insetPx + barWidthPx, getHeight() - insetPx, barPaint);
        }
    }

    /**
     * 圆形 FAB：品牌红底 + 深色描边 + 白色加号，全部按 dp 画，字号放大也不会变形。
     *
     * <p>注意：当前页面实际用的是 {@link #buildPlusButton()} 那个 TextView 版本
     * （因为要装可放大的「＋」字符），本类是早期留下的自绘版本，保留备用。</p>
     */
    private static class FabView extends View {

        /** 圆形的填充画笔（品牌红） */
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        /** 圆形的描边画笔（深色边框） */
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        /** 中间白色加号的画笔 */
        private final Paint plusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        /** 描边宽度（3dp）；算圆半径时要扣掉一半，避免被 View 边界裁掉 */
        private final int borderPx;
        /** 加号每条"胳膊"的半长（10dp） */
        private final int armLengthPx;
        /** 加号每根手臂的粗细（2dp） */
        private final int armThicknessPx;

        /**
         * @param c 上下文（颜色全部从主题里取）
         */
        FabView(Context c) {
            super(c);
            // 圆底
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(c.getColor(R.color.brand));

            // 圆边框
            borderPx = Ui.dp(c, 3);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(borderPx);
            strokePaint.setColor(c.getColor(R.color.border_dark));

            // 加号
            plusPaint.setStyle(Paint.Style.FILL);
            plusPaint.setColor(c.getColor(R.color.white));

            armLengthPx = Ui.dp(c, 10);
            armThicknessPx = Ui.dp(c, 2);
        }

        /**
         * 画法：先画实心圆，再画描边圆，最后用两个细长矩形叠成"＋"。
         *
         * @param canvas 画布
         */
        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;         // 圆心 x
            float cy = getHeight() / 2f;        // 圆心 y
            // 半径要扣掉半个描边宽，否则描边会画到 View 边界外被裁掉
            float radius = Math.min(getWidth(), getHeight()) / 2f - borderPx / 2f;
            canvas.drawCircle(cx, cy, radius, fillPaint);     // 实心圆
            canvas.drawCircle(cx, cy, radius, strokePaint);   // 描边圆
            // 横杠：(cx-arm, cy-thick) 到 (cx+arm, cy+thick)
            canvas.drawRect(cx - armLengthPx, cy - armThicknessPx,
                    cx + armLengthPx, cy + armThicknessPx, plusPaint);
            // 竖杠：(cx-thick, cy-arm) 到 (cx+thick, cy+arm)；两杠叠起来就是"＋"
            canvas.drawRect(cx - armThicknessPx, cy - armLengthPx,
                    cx + armThicknessPx, cy + armLengthPx, plusPaint);
        }
    }
}
