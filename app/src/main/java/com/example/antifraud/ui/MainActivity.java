package com.example.antifraud.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.antifraud.R;
import com.example.antifraud.engine.ScriptRepository;
import com.example.antifraud.engine.SettingsStore;
import com.example.antifraud.engine.UserStore;
import com.example.antifraud.model.Category;
import com.example.antifraud.model.Script;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 书架首页：像读书 App 一样，把每种骗局做成一本像素书。
 * 点书 → 书被"拿起来"放大到屏幕中央 → 以书脊为轴翻开 → 进入书页。
 */
public class MainActivity extends BaseActivity {

    static final String[] AVATARS = {"生", "学", "猫", "兔", "熊", "星"};

    private static final String[] TIPS = {
            "接到 96110 来电一定要接听，那是预警劝阻专线。",
            "任何要求离开官方平台、私下转账的，都是骗局的第一步。",
            "让你开「屏幕共享」的客服，100% 是假的。",
            "刷单本身就是违法的，不存在合法的刷单兼职。",
            "先交钱才能拿到的钱，永远是拿不到的。",
            "转账前，先挂断电话，用你自己找到的号码回拨核实。",
            "被骗后别删聊天记录，那是报警最有力的证据。",
            "国家反诈中心 APP 是官方防骗软件，可用于预警、举报和风险验证。"
    };

    private LinearLayout llShelf;
    private LinearLayout llExtra;
    private TextView tvHello;
    private TextView tvSub;
    private TextView tvLoss;
    private TextView tvAvatar;

    private List<Category> categories = new ArrayList<>();
    private List<Script> scripts = new ArrayList<>();
    private boolean opening = false;
    private int lastScriptCount = -1;
    private View studioCard;
    private boolean guideShowing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Ui.insetContent(this);

        llShelf = findViewById(R.id.ll_shelf);
        llExtra = findViewById(R.id.ll_extra);
        tvHello = findViewById(R.id.tv_hello);
        tvSub = findViewById(R.id.tv_sub);
        tvLoss = findViewById(R.id.tv_loss);
        tvAvatar = findViewById(R.id.tv_avatar);

        findViewById(R.id.btn_all_sources).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SourcesActivity.class));
            }
        });

        findViewById(R.id.btn_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                overridePendingTransition(R.anim.page_in, R.anim.page_out);
            }
        });

        findViewById(R.id.btn_back_home).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new android.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("退出反诈书库？")
                        .setMessage("你的读书进度会保存在本机。")
                        .setPositiveButton("退出", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int w) {
                                finish();
                            }
                        })
                        .setNegativeButton("再看看", null)
                        .show();
            }
        });

        if (!UserStore.isRegistered(this)) {
            startActivity(new Intent(this, RegisterActivity.class));
        }

        buildShelf();
        buildExtra();
    }

    @Override
    protected void onResume() {
        super.onResume();
        opening = false;
        if (SettingsStore.changed) {
            SettingsStore.changed = false;
            recreate();
            return;
        }
        refreshHeader();
        buildExtra();
        // 从剧本工坊回来后剧本数量可能变了，重新铺一次书架
        int now = ScriptRepository.loadAll(this).size();
        if (now != lastScriptCount) {
            buildShelf();
        }
        maybeShowGuide();
    }

    /** 第一次进书架时弹聚光灯引导；看过一次就不再弹 */
    private void maybeShowGuide() {
        if (guideShowing || SettingsStore.guideDone(this) || !UserStore.isRegistered(this)) {
            return;
        }
        guideShowing = true;
        final View content = findViewById(android.R.id.content);
        content.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || !guideShowing) {
                    return;
                }
                GuideOverlay guide = new GuideOverlay(MainActivity.this);
                guide.addStep(findViewById(R.id.header), "这里是你的战绩",
                        "已读几本、在剧情里一共损失了多少钱，都记在这台手机上，不联网。");
                guide.addStep(findViewById(R.id.ll_shelf), "从一本书开始",
                        "六本书对应六类骗局。点开一本，封面会翻开，进入真实案件改编的对话。");
                guide.addStep(studioCard, "自己写剧本",
                        "在剧本工坊里写剧本，导出成文件发给同学，同学导入就能玩。");
                guide.addStep(findViewById(R.id.btn_settings), "觉得字累眼？",
                        "设置里可以切换系统字体、调大字号，看久了不费眼。");
                ((android.view.ViewGroup) content).addView(guide);
                guide.start(new GuideOverlay.OnFinish() {
                    @Override
                    public void onFinish() {
                        SettingsStore.setGuideDone(MainActivity.this);
                        guideShowing = false;
                    }
                });
            }
        }, 800);
    }

    private void refreshHeader() {
        tvHello.setText("你好，" + UserStore.nickname(this));
        tvSub.setText("反诈书库 · 已读 " + UserStore.plays(this) + " 本");
        tvLoss.setText("损失 ¥" + Ui.money(UserStore.totalLoss(this)));
        int a = UserStore.avatar(this);
        tvAvatar.setText(AVATARS[a >= 0 && a < AVATARS.length ? a : 0]);
    }

    private void buildShelf() {
        categories = ScriptRepository.loadCategories(this);
        scripts = ScriptRepository.loadAll(this);
        lastScriptCount = scripts.size();
        llShelf.removeAllViews();

        final int perRow = 3;
        int rows = (categories.size() + perRow - 1) / perRow;
        int index = 0;
        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            for (int c = 0; c < perRow; c++) {
                if (index < categories.size()) {
                    row.addView(buildBook(categories.get(index), index));
                    index++;
                } else {
                    View space = new View(this);
                    space.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
                    row.addView(space);
                }
            }
            llShelf.addView(row);
            llShelf.addView(buildBoard());
        }
    }

    /** 木板书架隔板 */
    private View buildBoard() {
        View board = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 9));
        lp.setMargins(Ui.dp(this, 2), 0, Ui.dp(this, 2), Ui.dp(this, 14));
        board.setLayoutParams(lp);
        board.setBackgroundResource(R.drawable.px_shelf);
        return board;
    }

    private View buildBook(final Category cat, final int position) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(Ui.dp(this, 5), 0, Ui.dp(this, 5), Ui.dp(this, 2));
        cell.setLayoutParams(lp);

        final PixelBookView cover = new PixelBookView(this);
        cover.setBook(cat.colorInt, cat.glyph);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = Ui.dp(this, 2);
        cover.setLayoutParams(clp);
        cover.setCameraDistance(getResources().getDisplayMetrics().density * 14000);
        cell.addView(cover);

        TextView name = new PTextView(this);
        name.setText(cat.name);
        name.setTextSize(13);
        name.setTextColor(getColor(R.color.ink));
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, Ui.dp(this, 8), 0, 0);
        cell.addView(name);

        TextView desc = new PTextView(this);
        desc.setText(countOf(cat.id) + " 个剧本");
        desc.setTextSize(11);
        desc.setTextColor(getColor(R.color.ink_hint));
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, Ui.dp(this, 3), 0, 0);
        cell.addView(desc);

        cell.setAlpha(0f);
        cell.setTranslationY(Ui.dp(this, 20));
        cell.animate().alpha(1f).translationY(0f)
                .setDuration(280)
                .setStartDelay(position * 55)
                .start();

        cell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openBook(cover, cat);
            }
        });
        return cell;
    }

    private int countOf(String categoryId) {
        int n = 0;
        for (Script s : scripts) {
            if (categoryId.equals(s.category)) {
                n++;
            }
        }
        return n;
    }

    /**
     * 翻书动效：把书"拿起来"（副本浮到屏幕中央放大），再以书脊为轴把封面翻开，
     * 翻开后切到书页。原封面先隐藏，避免看到两个书。
     */
    /**
     * 翻书动效（真·翻开）：
     * 1) 全屏压暗，书从原位缓慢移到屏幕中央；
     * 2) 移动过程中放大，封面沿书脊掀开，露出内页；
     * 3) 内页淡入铺满整屏，显示这一册的大名字（字 / 分类名 / 一句话简介）；
     * 4) 淡入切到剧本页。
     */
    private void openBook(final PixelBookView cover, final Category cat) {
        if (opening) {
            return;
        }
        opening = true;

        final FrameLayout root = findViewById(android.R.id.content);
        int[] coverLoc = new int[2];
        cover.getLocationInWindow(coverLoc);
        int[] rootLoc = new int[2];
        root.getLocationInWindow(rootLoc);
        final int startX = coverLoc[0] - rootLoc[0];
        final int startY = coverLoc[1] - rootLoc[1];
        final int w = cover.getWidth();
        final int h = cover.getHeight();
        final float screenW = getResources().getDisplayMetrics().widthPixels;
        final float screenH = getResources().getDisplayMetrics().heightPixels;

        // ---- 压暗遮罩 ----
        final View scrim = new View(this);
        scrim.setBackgroundColor(0xB3000000);
        scrim.setAlpha(0f);
        scrim.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(scrim);
        scrim.animate().alpha(1f).setDuration(220).start();

        // ---- 舞台：内页 + 封面（用边距定位，避免闪到左上角）----
        final FrameLayout stage = new FrameLayout(this);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(w, h);
        slp.leftMargin = startX;
        slp.topMargin = startY;
        stage.setLayoutParams(slp);
        float cam = getResources().getDisplayMetrics().density * 18000;
        stage.setCameraDistance(cam);
        stage.setPivotX(0f);
        stage.setPivotY(0f);

        final LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundResource(R.drawable.px_paper);
        int pp = Ui.dp(this, 12);
        page.setPadding(pp, pp, pp, pp);
        page.addView(Ui.text(this, "第 1 页", 7, R.color.ink_hint));
        TextView inPageName = Ui.text(this, cat.name, 9, R.color.ink);
        inPageName.setPadding(0, Ui.dp(this, 6), 0, 0);
        page.addView(inPageName);
        View inPageLine = new View(this);
        inPageLine.setBackgroundColor(getColor(R.color.paper_dark));
        LinearLayout.LayoutParams inLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(this, 1)));
        inLp.topMargin = Ui.dp(this, 8);
        inPageLine.setLayoutParams(inLp);
        page.addView(inPageLine);
        stage.addView(page, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        final PixelBookView cov = new PixelBookView(this);
        cov.setBook(cat.colorInt, cat.glyph);
        cov.setPivotX(0f);
        cov.setPivotY(h / 2f);
        cov.setCameraDistance(cam);
        stage.addView(cov, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        root.addView(stage);
        cover.setVisibility(View.INVISIBLE);

        // ---- 翻开后铺满全屏的"大名字"页 ----
        final LinearLayout pageFull = new LinearLayout(this);
        pageFull.setOrientation(LinearLayout.VERTICAL);
        pageFull.setGravity(Gravity.CENTER_HORIZONTAL);
        pageFull.setBackgroundColor(getColor(R.color.paper));
        pageFull.setAlpha(0f);
        pageFull.setPadding(Ui.dp(this, 32), Ui.dp(this, 70), Ui.dp(this, 32), Ui.dp(this, 40));
        pageFull.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView bigGlyph = Ui.text(this, cat.glyph, 96, R.color.brand_deep);
        bigGlyph.setGravity(Gravity.CENTER);
        bigGlyph.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        pageFull.addView(bigGlyph);
        TextView bigName = Ui.text(this, cat.name, 34, R.color.ink);
        bigName.setPadding(0, Ui.dp(this, 18), 0, 0);
        bigName.setGravity(Gravity.CENTER);
        bigName.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        pageFull.addView(bigName);
        TextView bigDesc = Ui.text(this, cat.desc, 16, R.color.ink_secondary);
        bigDesc.setPadding(0, Ui.dp(this, 14), 0, 0);
        bigDesc.setGravity(Gravity.CENTER);
        bigDesc.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        pageFull.addView(bigDesc);
        root.addView(pageFull);

        final float scale = Math.min(screenW * 0.94f / w, screenH * 0.58f / h);
        final float targetX = (screenW - w * scale) / 2f;
        final float targetY = (screenH - h * scale) / 2f;

        // 等一帧，确保舞台的初始边距已经生效
        root.post(new Runnable() {
            @Override
            public void run() {
                // 第一步：缓慢移到屏幕中央，同时放大
                stage.animate()
                        .translationX(targetX - startX)
                        .translationY(targetY - startY)
                        .scaleX(scale).scaleY(scale)
                        .setDuration(420)
                        .setInterpolator(new DecelerateInterpolator())
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                // 第二步：封面沿书脊掀开
                                cov.animate()
                                        .rotationY(-170f)
                                        .setDuration(560)
                                        .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                                        .withEndAction(new Runnable() {
                                            @Override
                                            public void run() {
                                                // 第三步：内页铺满全屏，显示大名字
                                                stage.animate().alpha(0f).setDuration(240).start();
                                                pageFull.setAlpha(0f);
                                                pageFull.setTranslationY(Ui.dp(MainActivity.this, 16));
                                                pageFull.animate().alpha(1f).translationY(0f)
                                                        .setDuration(320).start();
                                                root.postDelayed(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        // 第四步：进入剧本页
                                                        Intent i = new Intent(MainActivity.this, ScriptPickerActivity.class);
                                                        i.putExtra("category_id", cat.id);
                                                        i.putExtra("category_name", cat.name);
                                                        startActivity(i);
                                                        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                                                        root.postDelayed(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                root.removeView(pageFull);
                                                                root.removeView(stage);
                                                                root.removeView(scrim);
                                                                cover.setVisibility(View.VISIBLE);
                                                                opening = false;
                                                            }
                                                        }, 300);
                                                    }
                                                }, 620);
                                            }
                                        })
                                        .start();
                            }
                        })
                        .start();
            }
        });
    }
    /** 书架下面：读书笔记 + 每日防骗贴士，避免底部太空 */
    private void buildExtra() {
        llExtra.removeAllViews();

        // 剧本工坊入口
        LinearLayout studio = Ui.card(this, 14, 12);
        studioCard = studio;
        studio.addView(Ui.text(this, "剧本工坊", 12, R.color.ink));
        TextView studioDesc = Ui.text(this, "自己写一个剧本，导出成文件，同学导入就能玩", 9, R.color.ink_hint);
        studioDesc.setPadding(0, Ui.dp(this, 6), 0, 0);
        studioDesc.setLineSpacing(Ui.dp(this, 3), 1f);
        studio.addView(studioDesc);
        TextView studioGo = Ui.text(this, "进入工坊 →", 10, R.color.brand);
        studioGo.setGravity(Gravity.END);
        studioGo.setPadding(0, Ui.dp(this, 8), 0, 0);
        studio.addView(studioGo);
        studio.setClickable(true);
        studio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, StudioActivity.class));
                overridePendingTransition(R.anim.page_in, R.anim.page_out);
            }
        });
        llExtra.addView(studio);

        LinearLayout note = Ui.card(this, 14, 12);
        view(note, "读书笔记", 13, R.color.ink);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(this, 10), 0, 0);
        row.addView(statColumn("已读", UserStore.plays(this) + " 本"));
        row.addView(statColumn("累计损失", "¥" + Ui.money(UserStore.totalLoss(this))));
        row.addView(statColumn("骗局类型", categories.size() + " 类"));
        note.addView(row);
        llExtra.addView(note);

        LinearLayout tip = Ui.card(this, 14, 12);
        view(tip, "今日防骗贴士", 12, R.color.brand);
        String text = TIPS[new Random().nextInt(TIPS.length)];
        TextView body = Ui.text(this, text, 10, R.color.ink_secondary);
        body.setPadding(0, Ui.dp(this, 8), 0, 0);
        body.setLineSpacing(Ui.dp(this, 4), 1f);
        tip.addView(body);
        llExtra.addView(tip);

        // 反诈四步口诀（填满底部，也给答辩一个记忆点）
        LinearLayout rules = Ui.card(this, 14, 0);
        view(rules, "反诈四步口诀", 12, R.color.ink);
        String[] four = {"一不轻信：陌生来电、陌生链接，一律先怀疑",
                "二不透露：验证码、密码、银行卡号，谁问都不给",
                "三不转账：任何理由要求转账，先停下",
                "四多核实：挂断电话，用自己找到的号码回拨"};
        for (int i = 0; i < four.length; i++) {
            TextView ruleRow = Ui.text(this, four[i], 10, R.color.ink_secondary);
            ruleRow.setPadding(0, Ui.dp(this, 8), 0, 0);
            ruleRow.setLineSpacing(Ui.dp(this, 3), 1f);
            rules.addView(ruleRow);
        }
        TextView ruleNote = Ui.text(this, "被骗后：保存聊天与转账记录 → 告诉家长老师 → 拨打 110，可咨询 96110。",
                9, R.color.ink_hint);
        ruleNote.setPadding(0, Ui.dp(this, 10), 0, 0);
        ruleNote.setLineSpacing(Ui.dp(this, 3), 1f);
        rules.addView(ruleNote);
        llExtra.addView(rules);
    }

    private void view(LinearLayout parent, String text, float size, int color) {
        parent.addView(Ui.text(this, text, size, color));
    }

    private View statColumn(String label, String value) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = Ui.text(this, value, 15, R.color.ink);
        TextView l = Ui.text(this, label, 9, R.color.ink_hint);
        l.setPadding(0, Ui.dp(this, 4), 0, 0);
        col.addView(v);
        col.addView(l);
        return col;
    }
}
