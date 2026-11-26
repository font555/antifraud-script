package com.example.antifraud.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.antifraud.R;
import com.example.antifraud.engine.ScriptStore;
import com.example.antifraud.engine.SettingsStore;
import com.example.antifraud.engine.UserStore;

/**
 * 「我的」Tab：个人档案 + 三格统计 + 我的创作 + 设置与帮助。
 *
 * <p>整页是一个 ScrollView，控件全部用代码搭（不新增 XML 布局）。
 * 每次 refresh() 重新读 UserStore / ScriptStore / SettingsStore，
 * 数据只存本机，不联网、不申请权限。</p>
 *
 * <p><b>和其他页面的关系：</b>由 {@link MainActivity} 创建并缓存，切 Tab 只改可见性不重建。
 * 本页自己不做任何跳转——「字体与字号」「来源库」「新手引导」「运行日志」全部转给构造时
 * 传进来的 {@link TabHost}（MainActivity 实现）；首页/剧本页要知道数据变了，也靠 host.onDataChanged() 广播。</p>
 *
 * <p><b>页面从上到下五块：</b>①个人卡（像素头像 + 昵称 + 已读/损失摘要）；
 * ②三格统计（已读 / 累计损失 / 我写的）；③我的创作（点进剧本工坊）；
 * ④设置与帮助（5 行列表）；⑤页脚声明（教学演示、不收集个人信息）。</p>
 *
 * <p><b>大字号：</b>所有「装文字的容器」尺寸都走 {@link SettingsStore#scaleBox} 放大，
 * 文字本身走 sp 由 BaseActivity 的 fontScale 自动放大，这里只放大容器。</p>
 */
public class MineTabView extends LinearLayout {

    /** 头像字符：索引来自 UserStore.avatar()，越界统一取第 0 个 */
    private static final String[] AVATARS = {"生", "学", "猫", "兔", "熊", "星"};
    /** 三格统计从左到右的标签，下标必须和 addStatCell 传的 index 一一对应 */
    private static final String[] STAT_LABELS = {"已读", "累计损失", "我写的"};

    /** 宿主 Activity，取颜色 / dp / 包名版本号都用它 */
    private final Activity a;
    /** 页面通信接口（MainActivity 实现）：打开设置页 / 工坊 / 来源库 / 日志、清档后广播刷新 */
    private final TabHost host;

    /** 个人卡左侧的像素头像控件（6 种图案之一，随昵称设置变化） */
    private AvatarView tvAvatar;
    /** 个人卡上的昵称文字 */
    private TextView tvNick;
    /** 个人卡上的摘要「已读 N 本 · 累计损失 ¥X」 */
    private TextView tvSummary;
    /** 三格统计：第 1 格的大数字（已读本数） */
    private TextView tvStatPlays;
    /** 三格统计：第 2 格的大数字（累计损失金额，单位元） */
    private TextView tvStatLoss;
    /** 三格统计：第 3 格的大数字（自己写的剧本数） */
    private TextView tvStatMine;
    /** 「我的创作」那行里的「我写的剧本　N 本」 */
    private TextView tvCreateCount;
    /** 设置区「字体与字号」行右侧的当前值，例如「像素 · 大(1.3x)」 */
    private TextView tvFontValue;
    /** 「设置与帮助」整块列表容器，新手引导第 5 步要拿它做高亮目标 */
    private View settingsSection;

    /** 「关于」行是否已触发长按，避免松手时又弹一次关于对话框 */
    private boolean aboutLongPressed;

    /**
     * 构造函数：把「我的」页五块内容一次性搭好，最后立刻 refresh() 填一次真实数据。
     *
     * @param a    宿主 Activity（MainActivity）
     * @param host 页面通信接口，所有跳转都通过它
     */
    public MineTabView(Activity a, TabHost host) {
        super(a);
        this.a = a;
        this.host = host;
        setOrientation(VERTICAL);
        setBackgroundColor(a.getColor(R.color.paper));

        // 滚动容器：内容比一屏高（5 块），必须能上下滚
        ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);   // 内容不满一屏也撑满，避免底部露白
        scroll.setBackgroundColor(a.getColor(R.color.paper));
        LinearLayout page = new LinearLayout(a);
        page.setOrientation(VERTICAL);
        int pad = Ui.dp(a, 16);         // 页边距固定 16dp，不随字号放大（横向空间不能吃）
        page.setPadding(pad, pad, pad, Ui.dp(a, 18));
        scroll.addView(page, new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 顺序即视觉顺序
        buildProfile(page);
        buildStats(page);
        buildCreation(page);
        buildSettings(page);
        buildFooter(page);
        refresh();   // 先搭骨架再填数据，避免闪一下空值
    }

    // ---------------- 1. 个人卡 ----------------

    /**
     * 搭第 1 块：个人卡（左头像 + 右侧昵称与摘要）。
     *
     * @param page 页面竖排容器，卡片会追加到它最后
     */
    private void buildProfile(LinearLayout page) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.px_panel);   // 像素风描边面板
        int p = SettingsStore.scaleBox(a, Ui.dp(a, 14));
        card.setPadding(p, p, p, p);

        // 左：像素头像。默认先用第 0 个图案 + 品牌色，refresh() 再换成真实设置
        tvAvatar = new AvatarView(a);
        tvAvatar.setAvatar(a.getColor(R.color.brand), 0);
        // 头像是纯方块绘制，跟着字号一起变大才不会在超大字号下显得过小
        int avatarSize = SettingsStore.scaleBox(a, Ui.dp(a, 60));
        card.addView(tvAvatar, new LayoutParams(avatarSize, avatarSize));

        // 右：昵称 + 摘要 + 「本地存档 · 不联网 · 零权限」
        LinearLayout info = new LinearLayout(a);
        info.setOrientation(VERTICAL);
        LayoutParams infoLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);   // 吃掉剩余宽度
        infoLp.leftMargin = SettingsStore.scaleBox(a, Ui.dp(a, 12));
        info.setLayoutParams(infoLp);

        tvNick = oneLine(Ui.text(a, "", 17, R.color.ink));
        info.addView(tvNick, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        tvSummary = oneLine(Ui.text(a, "", 10, R.color.ink_hint));
        info.addView(withTop(tvSummary, 5));
        TextView local = oneLine(Ui.text(a, "本地存档 · 不联网 · 零权限", 10, R.color.ink_hint));
        info.addView(withTop(local, 4));
        card.addView(info);

        addToPage(page, card, 0, 16);   // 卡片下方留 16dp 再进三格统计
    }

    // ---------------- 2. 三格统计 ----------------

    /**
     * 搭第 2 块：三格统计（已读 / 累计损失 / 我写的）。
     *
     * <p>三格是**同一行里三个 weight=1 的等宽格子**，不是手算宽度——
     * 写法和首页书架的三列网格一样（width=0dp + weight=1），
     * 换任何屏幕、任何字号都自动三等分，不会出现最后一格差几像素。</p>
     *
     * @param page 页面竖排容器
     */
    private void buildStats(LinearLayout page) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(HORIZONTAL);
        // 依次建三格，同时把每格的大数字 TextView 记到对应字段里，refresh() 要改它们
        tvStatPlays = addStatCell(row, 0);
        tvStatLoss = addStatCell(row, 1);
        tvStatMine = addStatCell(row, 2);
        addToPage(page, row, 0, 16);
    }

    /**
     * 给三格统计加一格。
     *
     * @param row   这一行的横排容器
     * @param index 第几格（0/1/2），同时用来取标签和决定要不要加左侧间距
     * @return 这一格里那个显示大数字的 TextView，交给 refresh() 更新
     */
    private TextView addStatCell(LinearLayout row, int index) {
        LinearLayout cell = new LinearLayout(a);
        cell.setOrientation(VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setBackgroundResource(R.drawable.px_panel_soft);   // 比个人卡更淡的底，主次分明
        // 三格统计：上下内边距跟着字号放大，数字和标签在 2.1 倍下不贴边
        cell.setPadding(SettingsStore.scaleBox(a, Ui.dp(a, 6)),
                SettingsStore.scaleBox(a, Ui.dp(a, 12)),
                SettingsStore.scaleBox(a, Ui.dp(a, 6)),
                SettingsStore.scaleBox(a, Ui.dp(a, 12)));
        LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);   // 三等分的核心写法
        if (index > 0) {
            lp.leftMargin = SettingsStore.scaleBox(a, Ui.dp(a, 8));   // 第 2、3 格左侧留 8dp，格与格不粘连
        }
        row.addView(cell, lp);

        // 一行更淡的边框，避免纯色块看起来像"没画完"
        TextView value = oneLine(Ui.text(a, "", 18, R.color.ink));
        value.setGravity(Gravity.CENTER);
        // 18sp 在超大档约 38sp，行高要跟着留出来，否则数字会被裁掉上下沿
        cell.addView(value, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        TextView label = Ui.text(a, STAT_LABELS[index], 10, R.color.ink_hint);
        label.setGravity(Gravity.CENTER);
        cell.addView(withTop(label, 4));
        return value;
    }

    // ---------------- 3. 我的创作 ----------------

    /**
     * 搭第 3 块：我的创作（一行，右侧「管理 →」），点整行进剧本工坊。
     *
     * @param page 页面竖排容器
     */
    private void buildCreation(LinearLayout page) {
        addToPage(page, sectionTitle("我的创作"), 0, 8);

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.px_option);
        // 上下 12dp、左右 14dp 都跟着字号放大，2 行文字时也不贴边
        card.setPadding(SettingsStore.scaleBox(a, Ui.dp(a, 14)),
                SettingsStore.scaleBox(a, Ui.dp(a, 12)),
                SettingsStore.scaleBox(a, Ui.dp(a, 14)),
                SettingsStore.scaleBox(a, Ui.dp(a, 12)));
        card.setClickable(true);   // 必须先可点，setOnClickListener 才会响应

        // 左：我写的剧本，N 本（weight=1，把「管理 →」顶到最右）
        tvCreateCount = oneLine(Ui.text(a, "", 12, R.color.ink));
        card.addView(tvCreateCount, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        // 右：管理 →（品牌色，暗示可点）
        TextView manage = oneLine(Ui.text(a, "管理 →", 11, R.color.brand));
        manage.setGravity(Gravity.END);
        LayoutParams manageLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        manageLp.leftMargin = SettingsStore.scaleBox(a, Ui.dp(a, 8));
        manage.setLayoutParams(manageLp);
        card.addView(manage);

        card.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                host.openStudio();   // 交给 MainActivity 去开剧本工坊
            }
        });
        addToPage(page, card, 0, 0);
    }

    // ---------------- 4. 设置与帮助 ----------------

    /**
     * 搭第 4 块：设置与帮助的 5 行列表。
     *
     * <p>5 行是同一个工厂方法 {@link #settingRow} 产出的，只有标题/当前值/点击行为不同：
     * 字体与字号 → 设置页；清除本地记录 → 确认弹窗；来源库 → 来源页；
     * 新手引导 → 重播引导；关于 → 声明弹窗（长按 1.5 秒还能进隐藏日志）。</p>
     *
     * @param page 页面竖排容器
     */
    private void buildSettings(LinearLayout page) {
        addToPage(page, sectionTitle("设置与帮助"), 18, 8);

        LinearLayout list = new LinearLayout(a);
        list.setOrientation(VERTICAL);
        settingsSection = list;   // 新手引导第 5 步要高亮这一整块

        // 第 1 行：字体与字号（右侧显示当前值，点进去是 SettingsActivity）
        LinearLayout fontRow = settingRow("字体与字号", "");
        // 右侧那个显示"当前值"的 TextView 就是这一行的第 2 个子 View
        tvFontValue = (TextView) fontRow.getChildAt(1);
        fontRow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                host.openSettings();
            }
        });
        list.addView(fontRow);

        // 第 2 行：清除本地记录（只清进度，不动用户自己写的剧本）
        LinearLayout clearRow = settingRow("清除本地记录", "只清本机进度");
        clearRow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClear();
            }
        });
        list.addView(clearRow);

        // 第 3 行：真实案例来源库（点进去能看到每条案例的原文链接）
        LinearLayout sourceRow = settingRow("真实案例来源库", "73 条可核验");
        sourceRow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                host.openAllSources();
            }
        });
        list.addView(sourceRow);

        // 第 4 行：新手引导（把"已看过"标记清掉，回首页重播一遍）
        LinearLayout guideRow = settingRow("新手引导", "再看一遍");
        guideRow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                host.openGuideAgain();
            }
        });
        list.addView(guideRow);

        // 第 5 行：关于（右侧显示版本号；长按 1.5 秒有隐藏彩蛋 → 运行日志）
        LinearLayout aboutRow = settingRow("关于", "v" + appVersion() + " · 教学演示作品");
        setupAboutRow(aboutRow);
        list.addView(aboutRow);

        addToPage(page, list, 0, 0);
    }

    /** 一行设置：左侧 12sp 标题，右侧 9sp 灰色当前值；大字号下右侧单行省略 */
    /** 读取当前安装包的版本号，避免页面里写死的版本过期 */
    private String appVersion() {
        try {
            return getContext().getPackageManager()
                    .getPackageInfo(getContext().getPackageName(), 0).versionName;
        } catch (Exception e) {
            // 极少见（包被卸载等）：读不到就用 "1.0" 兜底，不能让整页因此崩掉
            return "1.0";
        }
    }

    /**
     * 设置行工厂方法：左侧标题（weight=1 吃掉剩余宽度），右侧灰色当前值。
     *
     * <p><b>大字号下为什么要限宽：</b>右侧"当前值"最长的一行是
     * 「世界职业院校技能大赛 · 新一代信息技术赛道 · 中职组」这种长文本，
     * 放大到 2.1 倍后会很长。如果不管它，它会把左侧标题挤得只剩一两个字。
     * 所以这里给右侧设了「最多占屏宽 60% + 单行省略」，
     * 宁可把右边截断，也要保住左边的标题看得清。</p>
     *
     * @param title 左侧标题
     * @param value 右侧当前值（可为空字符串，那里就留白）
     * @return 组装好的一行（还没绑点击事件，调用方自己 setOnClickListener）
     */
    private LinearLayout settingRow(String title, String value) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.px_panel);
        // 设置行：左右/上下内边距跟着字号放大，12sp 标题和 9sp 当前值都不贴边框
        row.setPadding(SettingsStore.scaleBox(a, Ui.dp(a, 12)),
                SettingsStore.scaleBox(a, Ui.dp(a, 10)),
                SettingsStore.scaleBox(a, Ui.dp(a, 12)),
                SettingsStore.scaleBox(a, Ui.dp(a, 10)));
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = SettingsStore.scaleBox(a, Ui.dp(a, 8));   // 行与行之间 8dp 气口
        row.setLayoutParams(lp);

        // 左：标题，weight=1 把右侧顶到最右
        TextView name = oneLine(Ui.text(a, title, 12, R.color.ink));
        name.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        row.addView(name);

        // 右：当前值，右对齐、灰色小字
        TextView val = oneLine(Ui.text(a, value, 9, R.color.ink_hint));
        val.setGravity(Gravity.END);
        // 超大字号时限制右侧宽度，宁可省略号也不把左侧标题挤掉
        val.setMaxWidth(Math.round(a.getResources().getDisplayMetrics().widthPixels * 0.6f));
        LayoutParams valLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        valLp.leftMargin = SettingsStore.scaleBox(a, Ui.dp(a, 8));
        val.setLayoutParams(valLp);
        row.addView(val);

        row.setClickable(true);   // 必须先可点，调用方的 setOnClickListener 才生效
        return row;
    }

    /**
     * 关于行：点一下看声明；按住 1.5 秒打开隐藏的运行日志（界面不写任何提示）。
     *
     * <p>为什么要自己写 onTouchListener：系统默认的长按是 500ms，
     * 太容易误触（用户只是想点一下）。这里把触发时间改成 1500ms，
     * 并且**自己判断松手位置**——只有手指还在这一行范围内、
     * 且没有触发过长按，才补发一次普通点击，否则松开就什么都不做。
     * 返回 true 表示接管整条触摸链，回收 click 事件的系统默认处理。</p>
     *
     * @param row 要绑定的「关于」行
     */
    private void setupAboutRow(final LinearLayout row) {
        // 普通点击：弹「关于」声明
        row.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showAbout();
            }
        });
        // 长按：进隐藏运行日志。返回 true 表示已消费，避免再触发点击
        row.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                host.openLog();
                return true;
            }
        });
        row.setOnTouchListener(new OnTouchListener() {
            // 延时 1.5 秒后才执行的任务：如果视图已离开窗口就作废，否则标记并触发长按
            private final Runnable longPress = new Runnable() {
                @Override
                public void run() {
                    if (!row.isAttachedToWindow()) {
                        return;   // 页面已经走了，别再触发
                    }
                    aboutLongPressed = true;
                    row.performLongClick();
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                int action = e.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    aboutLongPressed = false;             // 每次按下都重置标记
                    v.postDelayed(longPress, 1500); // 系统默认 0.5 秒，这里按住 1.5 秒才触发
                } else if (action == MotionEvent.ACTION_UP) {
                    v.removeCallbacks(longPress);         // 先取消，别让长按在松手后补触发
                    // 只有「没触发过长按」+「松手时手指仍在这一行内」才当作普通点击
                    if (!aboutLongPressed && e.getX() >= 0 && e.getX() <= v.getWidth()
                            && e.getY() >= 0 && e.getY() <= v.getHeight()) {
                        v.performClick();
                    }
                } else if (action == MotionEvent.ACTION_CANCEL) {
                    v.removeCallbacks(longPress);         // 手势被系统取消（比如滑走了），清掉定时器
                }
                return true;
            }
        });
    }

    // ---------------- 5. 页脚 ----------------

    /**
     * 搭第 5 块：一行页脚声明（教学演示、不收集个人信息）。
     *
     * @param page 页面竖排容器
     */
    private void buildFooter(LinearLayout page) {
        TextView footer = Ui.text(a, "反诈剧本杀 · 教学演示作品 · 不收集任何个人信息",
                9, R.color.ink_hint);
        footer.setGravity(Gravity.CENTER);
        addToPage(page, footer, 10, 0);
    }

    // ---------------- 刷新 ----------------

    /**
     * 重新读本机数据，刷新头像、三格数字、创作数、字体当前值。
     *
     * <p>谁调它：本类构造末尾、MainActivity 每次 onResume、切到本 Tab 时、
     * 以及清档后的 {@code host.onDataChanged()}。所以它必须**幂等且轻量**——
     * 只读 4 个值、只改文字，绝不重建控件（重建会让滚动位置跳回顶部）。</p>
     */
    public void refresh() {
        int plays = UserStore.plays(a);          // 通关/读完的剧本数
        int loss = UserStore.totalLoss(a);       // 累计损失金额（元）
        int mine = ScriptStore.listUserIds(a).size();   // 用户自己写的剧本数
        int index = UserStore.avatar(a);         // 头像图案下标，可能来自旧版本而越界
        if (index < 0 || index >= AVATARS.length) {
            index = 0;   // 越界兜底：统一回第 0 个图案，绝不抛数组越界
        }
        tvAvatar.setAvatar(a.getColor(R.color.brand), index);
        tvNick.setText(UserStore.nickname(a));
        tvSummary.setText("已读 " + plays + " 本 · 累计损失 ¥" + Ui.money(loss));
        tvStatPlays.setText(plays + " 本");
        tvStatLoss.setText("¥" + Ui.money(loss));
        tvStatMine.setText(mine + " 本");
        tvCreateCount.setText("我写的剧本　" + mine + " 本");
        // 字体当前值：先看用的是不是像素字体，再拼上字号档位标签（例如「大 1.3x」）
        tvFontValue.setText((Typefaces.isPixel(a) ? "像素" : "系统")
                + " · " + SettingsStore.fontScaleLabel(a));
    }

    /** 新手引导高亮用：设置与帮助整块 */
    public View settingsRowView() {
        return settingsSection;
    }

    // ---------------- 弹窗 ----------------

    /** 清除本地记录前的二次确认：说清楚"清什么、不清什么"，避免误删用户的作品 */
    private void confirmClear() {
        PixelDialog dlg = PixelDialog.create(a);
        dlg.title("清除本地记录？");
        dlg.body("会清空昵称、头像和阅读记录；我写的剧本和案例来源不受影响。");
        // 第 1 个按钮（index 0）是主操作「清除」：清完要通知宿主刷新所有页面
        dlg.button("清除", true, new PixelDialog.OnAction() {
            @Override
            public void onAction(int index) {
                UserStore.reset(a);       // 只重置进度类数据
                host.onDataChanged();     // 广播：首页/剧本页/本页一起刷新
                host.toast("已清除");
            }
        }, 0);
        // 第 2 个按钮（index 1）是「取消」，什么都不做（回调传 null）
        dlg.button("取消", false, null, 1);
        dlg.show();
    }

    /** 关于弹窗：赛道信息 + 技术特点 + 合规声明（教学演示、非官方、人物已匿名） */
    private void showAbout() {
        PixelDialog dlg = PixelDialog.create(a);
        dlg.title("关于《反诈剧本杀》");
        dlg.field("赛道", "世界职业院校技能大赛 · 新一代信息技术赛道 · 中职组");
        dlg.field("技术特点", "零第三方依赖 · 零权限 · 零联网");
        dlg.body("案例据公开报道改编，人物已匿名、平台名已虚构。本作品为教学演示，与任何机构无隶属关系。");
        dlg.button("知道了", true, null, 0);
        dlg.show();
    }

    // ---------------- 小工具 ----------------

    /**
     * 一个小节标题（例如「我的创作」「设置与帮助」）。
     *
     * @param s 标题文字
     * @return 14sp 主色的 TextView
     */
    private TextView sectionTitle(String s) {
        return Ui.text(a, s, 14, R.color.ink);
    }

    /** 单行 + 省略号：超大字号下右对齐的文字不换行、不把布局撑破 */
    private TextView oneLine(TextView tv) {
        tv.setMaxLines(1);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        return tv;
    }

    /**
     * 给文字加上「距上一个控件多少 dp」的上边距，间距也跟着字号放大。
     *
     * @param tv    要设置间距的 TextView
     * @param topDp 标准字号下的上边距（dp）
     * @return 同一个 TextView（方便链式写法）
     */
    private TextView withTop(TextView tv, int topDp) {
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        // 两行文字之间的间距也要跟着字号长，超大档下行与行才不会挤在一起
        lp.topMargin = SettingsStore.scaleBox(a, Ui.dp(a, topDp));
        tv.setLayoutParams(lp);
        return tv;
    }

    /**
     * 把一个 View 追加到页面容器，并统一设置上下外边距（同样跟着字号放大）。
     *
     * @param page    页面竖排容器
     * @param v       要加进去的 View
     * @param topDp   标准字号下的上边距（dp）
     * @param bottomDp 标准字号下的下边距（dp）
     */
    private void addToPage(LinearLayout page, View v, float topDp, float bottomDp) {
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = SettingsStore.scaleBox(a, Ui.dp(a, topDp));
        lp.bottomMargin = SettingsStore.scaleBox(a, Ui.dp(a, bottomDp));
        page.addView(v, lp);
    }
}
