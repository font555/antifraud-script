package com.example.antifraud.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.example.antifraud.R;
import com.example.antifraud.engine.AppLog;
import com.example.antifraud.engine.ScriptStore;
import com.example.antifraud.engine.SettingsStore;
import com.example.antifraud.engine.UserStore;
import com.example.antifraud.model.Script;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 主界面：底部三个 Tab（首页 / 剧本 / 我的）。
 *
 * <p>三个页面都是普通 View（不做 Fragment，保持零第三方依赖）：
 * 创建一次后缓存起来，切换只改可见性，避免重建导致滚动位置丢失和白屏。</p>
 */
public class MainActivity extends BaseActivity implements TabHost {

    /** 注册时可选的头像字（RegisterActivity / ChatActivity 也在用，保持 public static） */
    public static final String[] AVATARS = {"生", "学", "猫", "兔", "熊", "星"};

    private static final int REQ_IMPORT = 2001;

    private FrameLayout container;
    private BottomTabBar tabBar;
    private HomeTabView homeView;
    private ScriptsTabView scriptsView;
    private MineTabView mineView;
    private int currentTab = 0;

    private GuideOverlay guide;
    private boolean guideShowing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Ui.insetContent(this);

        container = findViewById(R.id.tab_container);
        tabBar = findViewById(R.id.tab_bar);
        tabBar.setOnTabSelected(new BottomTabBar.OnTabSelected() {
            @Override
            public void onTabSelected(int index) {
                switchTab(index);
            }
        });

        AppLog.i("MAIN", "进入三主界面");

        if (!UserStore.isRegistered(this)) {
            startActivity(new Intent(this, RegisterActivity.class));
        }
        // 三个页面都先建好：新手引导要能高亮"剧本页的加号""我的页的设置"，不能等到点开才有
        tabView(0);
        tabView(1);
        tabView(2);
        showTab(0);
    }

    // ---------------- Tab 管理 ----------------

    private View tabView(int index) {
        if (index == 0) {
            if (homeView == null) {
                homeView = new HomeTabView(this, this);
            }
            return homeView;
        }
        if (index == 1) {
            if (scriptsView == null) {
                scriptsView = new ScriptsTabView(this, this);
            }
            return scriptsView;
        }
        if (mineView == null) {
            mineView = new MineTabView(this, this);
        }
        return mineView;
    }

    private void showTab(int index) {
        currentTab = index;
        container.removeAllViews();
        View v = tabView(index);
        if (v.getParent() instanceof ViewGroup) {
            ((ViewGroup) v.getParent()).removeView(v);
        }
        // 切 Tab 时淡入 + 轻微上浮，避免页面“闪”一下直接换掉
        v.setAlpha(0f);
        v.setTranslationY(Ui.dp(this, 10));
        container.addView(v, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        v.animate().alpha(1f).translationY(0f).setDuration(180).start();
        tabBar.setSelected(index);
        refreshCurrent();
    }

    private void refreshCurrent() {
        try {
            if (currentTab == 0 && homeView != null) {
                homeView.refresh();
            } else if (currentTab == 1 && scriptsView != null) {
                scriptsView.refresh();
            } else if (currentTab == 2 && mineView != null) {
                mineView.refresh();
            }
        } catch (Exception e) {
            AppLog.e("MAIN", "刷新 Tab " + currentTab + " 失败", e);
        }
    }

    @Override
    public void switchTab(int index) {
        if (index == currentTab) {
            return;
        }
        AppLog.i("MAIN", "切换 Tab -> " + index);
        showTab(index);
    }

    // ---------------- 生命周期 ----------------

    @Override
    protected void onResume() {
        super.onResume();
        if (SettingsStore.changed) {
            SettingsStore.changed = false;
            recreate();
            return;
        }
        // 从聊天/复盘/工坊回来，数据可能变了，全部刷新一遍
        if (homeView != null) {
            homeView.refresh();
        }
        if (scriptsView != null) {
            scriptsView.refresh();
        }
        if (mineView != null) {
            mineView.refresh();
        }
        maybeShowGuide();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (guide != null) {
            guide.finish();
            guide = null;
            guideShowing = false;
        }
    }

    @Override
    public void onBackPressed() {
        if (guide != null) {
            guide.finish();
            return;
        }
        if (currentTab != 0) {
            switchTab(0);
            return;
        }
        super.onBackPressed();
    }

    // ---------------- TabHost 实现 ----------------

    @Override
    public void openCategory(String categoryId) {
        showTab(1);
        if (scriptsView != null) {
            scriptsView.filterByCategory(categoryId);
        }
    }

    @Override
    public void openScript(String scriptId) {
        Intent i = new Intent(this, ChatActivity.class);
        i.putExtra("script_id", scriptId);
        startActivity(i);
        overridePendingTransition(R.anim.page_in, R.anim.page_out);
    }

    @Override
    public void openStudio() {
        startActivity(new Intent(this, StudioActivity.class));
        overridePendingTransition(R.anim.page_in, R.anim.page_out);
    }

    @Override
    public void openImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_IMPORT);
    }

    @Override
    public void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
        overridePendingTransition(R.anim.page_in, R.anim.page_out);
    }

    @Override
    public void openAllSources() {
        startActivity(new Intent(this, SourcesActivity.class));
        overridePendingTransition(R.anim.page_in, R.anim.page_out);
    }

    @Override
    public void openGuideAgain() {
        SettingsStore.setGuideDone(this, false);
        guideShowing = false;
        switchTab(0);
        maybeShowGuide();
    }

    @Override
    public void openLog() {
        startActivity(new Intent(this, LogActivity.class));
    }

    @Override
    public void onDataChanged() {
        if (homeView != null) {
            homeView.refresh();
        }
        if (scriptsView != null) {
            scriptsView.refresh();
        }
        if (mineView != null) {
            mineView.refresh();
        }
    }

    @Override
    public void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // ---------------- 导入剧本 ----------------

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT || resultCode != RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            in.close();
            Script s = ScriptStore.importJson(this, new String(bos.toByteArray(), "UTF-8"));
            if (s == null) {
                toast("导入失败：不是有效的剧本文件");
            } else {
                toast("已导入：" + s.title);
                AppLog.i("MAIN", "导入剧本 " + s.id);
                onDataChanged();
            }
        } catch (Exception e) {
            AppLog.e("MAIN", "导入剧本失败", e);
            toast("导入失败：" + e.getMessage());
        }
    }

    // ---------------- 新手引导（5 步，会自动切 Tab） ----------------

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
                final GuideOverlay overlay = new GuideOverlay(MainActivity.this);
                guide = overlay;
                // 「跳过引导」放在底部 Tab 栏上方，不压住导航
                overlay.setBottomInsetDp(64);
                overlay.setStepPreparer(new GuideOverlay.OnStepPrepare() {
                    @Override
                    public void onPrepare(int index) {
                        // 0/1 在首页；2/3 在剧本页；4 在我的页
                        if (index == 2 || index == 3) {
                            switchTab(1);
                            if (index == 2 && scriptsView != null) {
                                scriptsView.resetForGuide();
                            }
                        } else if (index == 4) {
                            switchTab(2);
                        } else {
                            switchTab(0);
                        }
                    }
                });
                overlay.addStep(homeView == null ? null : homeView.carouselView(), "每天一个真实案例",
                        "左右滑动可以看更多；点开能复制原文链接，全部来自公开报道。");
                overlay.addStep(homeView == null ? null : homeView.shelfView(), "从一本书开始",
                        "六本书对应六类骗局，点开封面会翻开，翻完直接带你去剧本页。");
                overlay.addStep(scriptsView == null ? null : scriptsView.listView(), "这里是全部剧本",
                        "17 本剧本都在这里，上面可以搜索和按骗局类型筛选，点一本就能开始。", 0.55f);
                overlay.addStep(scriptsView == null ? null : scriptsView.fabButtonView(), "自己也能写剧本",
                        "点搜索框右边的加号：新建空白、套模板，或者导入同学发来的剧本。");
                overlay.addStep(mineView == null ? null : mineView.settingsRowView(), "觉得字累眼？",
                        "这里能换系统字体、调大字号，还有一个藏起来的运行日志。");
                ((android.view.ViewGroup) content).addView(overlay);
                overlay.start(new GuideOverlay.OnFinish() {
                    @Override
                    public void onFinish() {
                        SettingsStore.setGuideDone(MainActivity.this);
                        guideShowing = false;
                        guide = null;
                        // 引导结束回到首页，用户第一眼看到的是书架
                        switchTab(0);
                        AppLog.i("GUIDE", "引导结束");
                    }
                });
            }
        }, 900);
    }
}
