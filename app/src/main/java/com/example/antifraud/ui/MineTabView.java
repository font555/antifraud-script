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
 */
public class MineTabView extends LinearLayout {

    /** 头像字符：索引来自 UserStore.avatar()，越界统一取第 0 个 */
    private static final String[] AVATARS = {"生", "学", "猫", "兔", "熊", "星"};
    private static final String[] STAT_LABELS = {"已读", "累计损失", "我写的"};

    private final Activity a;
    private final TabHost host;

    private AvatarView tvAvatar;
    private TextView tvNick;
    private TextView tvSummary;
    private TextView tvStatPlays;
    private TextView tvStatLoss;
    private TextView tvStatMine;
    private TextView tvCreateCount;
    private TextView tvFontValue;
    private View settingsSection;

    /** 「关于」行是否已触发长按，避免松手时又弹一次关于对话框 */
    private boolean aboutLongPressed;

    public MineTabView(Activity a, TabHost host) {
        super(a);
        this.a = a;
        this.host = host;
        setOrientation(VERTICAL);
        setBackgroundColor(a.getColor(R.color.paper));

        ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(a.getColor(R.color.paper));
        LinearLayout page = new LinearLayout(a);
        page.setOrientation(VERTICAL);
        int pad = Ui.dp(a, 16);
        page.setPadding(pad, pad, pad, Ui.dp(a, 18));
        scroll.addView(page, new ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        buildProfile(page);
        buildStats(page);
        buildCreation(page);
        buildSettings(page);
        buildFooter(page);
        refresh();
    }

    // ---------------- 1. 个人卡 ----------------

    private void buildProfile(LinearLayout page) {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.px_panel);
        int p = Ui.dp(a, 14);
        card.setPadding(p, p, p, p);

        tvAvatar = new AvatarView(a);
        tvAvatar.setAvatar(a.getColor(R.color.brand), 0);
        card.addView(tvAvatar, new LayoutParams(Ui.dp(a, 60), Ui.dp(a, 60)));

        LinearLayout info = new LinearLayout(a);
        info.setOrientation(VERTICAL);
        LayoutParams infoLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        infoLp.leftMargin = Ui.dp(a, 12);
        info.setLayoutParams(infoLp);

        tvNick = oneLine(Ui.text(a, "", 17, R.color.ink));
        info.addView(tvNick, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        tvSummary = oneLine(Ui.text(a, "", 10, R.color.ink_hint));
        info.addView(withTop(tvSummary, 5));
        TextView local = oneLine(Ui.text(a, "本地存档 · 不联网 · 零权限", 10, R.color.ink_hint));
        info.addView(withTop(local, 4));
        card.addView(info);

        addToPage(page, card, 0, 16);
    }

    // ---------------- 2. 三格统计 ----------------

    private void buildStats(LinearLayout page) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(HORIZONTAL);
        tvStatPlays = addStatCell(row, 0);
        tvStatLoss = addStatCell(row, 1);
        tvStatMine = addStatCell(row, 2);
        addToPage(page, row, 0, 16);
    }

    private TextView addStatCell(LinearLayout row, int index) {
        LinearLayout cell = new LinearLayout(a);
        cell.setOrientation(VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setBackgroundResource(R.drawable.px_panel_soft);
        cell.setPadding(Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 6), Ui.dp(a, 12));
        LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        if (index > 0) {
            lp.leftMargin = Ui.dp(a, 8);
        }
        row.addView(cell, lp);

        TextView value = oneLine(Ui.text(a, "", 18, R.color.ink));
        value.setGravity(Gravity.CENTER);
        cell.addView(value, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        TextView label = Ui.text(a, STAT_LABELS[index], 10, R.color.ink_hint);
        label.setGravity(Gravity.CENTER);
        cell.addView(withTop(label, 4));
        return value;
    }

    // ---------------- 3. 我的创作 ----------------

    private void buildCreation(LinearLayout page) {
        addToPage(page, sectionTitle("我的创作"), 0, 8);

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.px_option);
        card.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12));
        card.setClickable(true);

        tvCreateCount = oneLine(Ui.text(a, "", 12, R.color.ink));
        card.addView(tvCreateCount, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        TextView manage = oneLine(Ui.text(a, "管理 →", 11, R.color.brand));
        manage.setGravity(Gravity.END);
        LayoutParams manageLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        manageLp.leftMargin = Ui.dp(a, 8);
        manage.setLayoutParams(manageLp);
        card.addView(manage);

        card.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                host.openStudio();
            }
        });
        addToPage(page, card, 0, 0);
    }

    // ---------------- 4. 设置与帮助 ----------------

    private void buildSettings(LinearLayout page) {
        addToPage(page, sectionTitle("设置与帮助"), 18, 8);

        LinearLayout list = new LinearLayout(a);
        list.setOrientation(VERTICAL);
        settingsSection = list;

        LinearLayout fontRow = settingRow("字体与字号", "");
        tvFontValue = (TextView) fontRow.getChildAt(1);
        fontRow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                host.openSettings();
            }
        });
        list.addView(fontRow);

        LinearLayout clearRow = settingRow("清除本地记录", "只清本机进度");
        clearRow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClear();
            }
        });
        list.addView(clearRow);

        LinearLayout sourceRow = settingRow("真实案例来源库", "71 条可核验");
        sourceRow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                host.openAllSources();
            }
        });
        list.addView(sourceRow);

        LinearLayout guideRow = settingRow("新手引导", "再看一遍");
        guideRow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                host.openGuideAgain();
            }
        });
        list.addView(guideRow);

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
            return "1.0";
        }
    }

    private LinearLayout settingRow(String title, String value) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.px_panel);
        row.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 10));
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(a, 8);
        row.setLayoutParams(lp);

        TextView name = oneLine(Ui.text(a, title, 12, R.color.ink));
        name.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        row.addView(name);

        TextView val = oneLine(Ui.text(a, value, 9, R.color.ink_hint));
        val.setGravity(Gravity.END);
        // 超大字号时限制右侧宽度，宁可省略号也不把左侧标题挤掉
        val.setMaxWidth(Math.round(a.getResources().getDisplayMetrics().widthPixels * 0.6f));
        LayoutParams valLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        valLp.leftMargin = Ui.dp(a, 8);
        val.setLayoutParams(valLp);
        row.addView(val);

        row.setClickable(true);
        return row;
    }

    /** 关于行：点一下看声明；按住 1.5 秒打开隐藏的运行日志（界面不写任何提示） */
    private void setupAboutRow(final LinearLayout row) {
        row.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showAbout();
            }
        });
        row.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                host.openLog();
                return true;
            }
        });
        row.setOnTouchListener(new OnTouchListener() {
            private final Runnable longPress = new Runnable() {
                @Override
                public void run() {
                    if (!row.isAttachedToWindow()) {
                        return;
                    }
                    aboutLongPressed = true;
                    row.performLongClick();
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                int action = e.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    aboutLongPressed = false;
                    v.postDelayed(longPress, 1500); // 系统默认 0.5 秒，这里按住 1.5 秒才触发
                } else if (action == MotionEvent.ACTION_UP) {
                    v.removeCallbacks(longPress);
                    if (!aboutLongPressed && e.getX() >= 0 && e.getX() <= v.getWidth()
                            && e.getY() >= 0 && e.getY() <= v.getHeight()) {
                        v.performClick();
                    }
                } else if (action == MotionEvent.ACTION_CANCEL) {
                    v.removeCallbacks(longPress);
                }
                return true;
            }
        });
    }

    // ---------------- 5. 页脚 ----------------

    private void buildFooter(LinearLayout page) {
        TextView footer = Ui.text(a, "反诈剧本杀 · 教学演示作品 · 不收集任何个人信息",
                9, R.color.ink_hint);
        footer.setGravity(Gravity.CENTER);
        addToPage(page, footer, 10, 0);
    }

    // ---------------- 刷新 ----------------

    /** 重新读本机数据，刷新头像、三格数字、创作数、字体当前值 */
    public void refresh() {
        int plays = UserStore.plays(a);
        int loss = UserStore.totalLoss(a);
        int mine = ScriptStore.listUserIds(a).size();
        int index = UserStore.avatar(a);
        if (index < 0 || index >= AVATARS.length) {
            index = 0;
        }
        tvAvatar.setAvatar(a.getColor(R.color.brand), index);
        tvNick.setText(UserStore.nickname(a));
        tvSummary.setText("已读 " + plays + " 本 · 累计损失 ¥" + Ui.money(loss));
        tvStatPlays.setText(plays + " 本");
        tvStatLoss.setText("¥" + Ui.money(loss));
        tvStatMine.setText(mine + " 本");
        tvCreateCount.setText("我写的剧本　" + mine + " 本");
        tvFontValue.setText((Typefaces.isPixel(a) ? "像素" : "系统")
                + " · " + SettingsStore.fontScaleLabel(a));
    }

    /** 新手引导高亮用：设置与帮助整块 */
    public View settingsRowView() {
        return settingsSection;
    }

    // ---------------- 弹窗 ----------------

    private void confirmClear() {
        new AlertDialog.Builder(a)
                .setTitle("清除本地记录？")
                .setMessage("会清空昵称、头像和阅读记录；我写的剧本和案例来源不受影响。")
                .setPositiveButton("清除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        UserStore.reset(a);
                        host.onDataChanged();
                        host.toast("已清除");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAbout() {
        new AlertDialog.Builder(a)
                .setTitle("关于《反诈剧本杀》")
                .setMessage("作品：反诈剧本杀\n"
                        + "赛道：世界职业院校技能大赛 · 新一代信息技术赛道 · 中职组\n\n"
                        + "零第三方依赖 · 零权限 · 零联网\n"
                        + "案例据公开报道改编，人物已匿名、平台名已虚构。\n"
                        + "本作品为教学演示，与任何机构无隶属关系。")
                .setPositiveButton("知道了", null)
                .show();
    }

    // ---------------- 小工具 ----------------

    private TextView sectionTitle(String s) {
        return Ui.text(a, s, 14, R.color.ink);
    }

    /** 单行 + 省略号：超大字号下右对齐的文字不换行、不把布局撑破 */
    private TextView oneLine(TextView tv) {
        tv.setMaxLines(1);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        return tv;
    }

    private TextView withTop(TextView tv, int topDp) {
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, topDp);
        tv.setLayoutParams(lp);
        return tv;
    }

    private void addToPage(LinearLayout page, View v, float topDp, float bottomDp) {
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, topDp);
        lp.bottomMargin = Ui.dp(a, bottomDp);
        page.addView(v, lp);
    }
}
