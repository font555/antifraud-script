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
 *
 * <h2>三 Tab 切换机制（以及为什么不用 Fragment）</h2>
 *
 * <p><b>它是怎么切的：</b>整个 Activity 只有一个 {@code FrameLayout} 容器
 * （布局里的 {@code R.id.tab_container}），三个页面 {@link HomeTabView}、
 * {@link ScriptsTabView}、{@link MineTabView} 都是普通的 {@code LinearLayout} 子类，
 * 由 {@link #tabView(int)} **懒加载**创建一次，之后一直缓存在三个字段里。
 * 切 Tab 时 {@link #showTab(int)} 把当前页从容器里 removeView 掉、把新页 addView 进去，
 * 加一个 180ms 的"淡入 + 上浮"过渡；页面实例本身不销毁，所以滚动位置、
 * 搜索框里输入的字、筛选选中的分类全都保留。</p>
 *
 * <p><b>为什么不用 Fragment（四个理由，按重要性排）：</b></p>
 * <ol>
 *   <li><b>依赖负担。</b>Fragment 要引 {@code androidx.fragment} 库，而本项目的硬约束是
 *       「零第三方依赖、零权限、纯本地」，多一个 AAR 就违背作品设定。</li>
 *   <li><b>生命周期更简单。</b>Fragment 有自己的生命周期（onAttach/onCreateView/onDestroyView），
 *       切 Tab 还要管 view 销毁重建和状态保存，容易写出"切换后数据丢了"这类 bug；
 *       这里三个页面就是普通 View，只跟着 Activity 活，逻辑一眼看懂。</li>
 *   <li><b>页面之间要互相调。</b>首页翻书后要切到「剧本」并筛选分类、清档后三页都要刷新；
 *       普通 View 只要一个 {@link TabHost} 接口（本类实现）就能互相通信，
 *       不需要 FragmentManager、interface 回调、setArguments 那一整套。</li>
 *   <li><b>重建代价大。</b>三个页面都是"整页用代码搭"的重页面，Fragment 的 view 层级
 *       每次切换都可能重建，成本比 removeView/addView 高得多。</li>
 * </ol>
 *
 * <p><b>代价（也要说清楚）：</b>三个页面会一直占着内存（不会像 Fragment 那样被回收），
 * 但只是几个 View + 几百 KB 列表数据，实测占用很小，页面数量也固定 3 个——有意做的取舍。</p>
 *
 * <p><b>本类还负责：</b>实现 {@link TabHost} 给三个页面提供统一的「打开某页 / 切 Tab /
 * 弹 toast / 数据变了」入口；首次进入时若没注册则跳 {@link RegisterActivity}；
 * 5 步新手引导（会自动切 Tab 配合高亮）；用系统文件选择器导入剧本 JSON。</p>
 */
public class MainActivity extends BaseActivity implements TabHost {

    /** 注册时可选的头像字（RegisterActivity / ChatActivity 也在用，保持 public static） */
    public static final String[] AVATARS = {"生", "学", "猫", "兔", "熊", "星"};

    /** 系统文件选择器的请求码：onActivityResult 靠它认出「这是导入剧本回来的」 */
    private static final int REQ_IMPORT = 2001;

    /** 三个页面的公共容器（布局里的 FrameLayout），同一时刻只装一个页面 */
    private FrameLayout container;
    /** 底部导航条：用户点哪个 Tab 由它的回调告诉我们 */
    private BottomTabBar tabBar;
    /** 首页（懒加载，第一次切过去才创建，之后一直复用） */
    private HomeTabView homeView;
    /** 剧本页（同上） */
    private ScriptsTabView scriptsView;
    /** 我的页（同上） */
    private MineTabView mineView;
    /** 当前选中的 Tab 下标：0 首页 / 1 剧本 / 2 我的 */
    private int currentTab = 0;

    /** 新手引导浮层（5 步高亮），没有引导时为 null */
    private GuideOverlay guide;
    /** 引导是否正在显示：防止 onResume 反复进入时重复弹 */
    private boolean guideShowing = false;

    /**
     * Activity 创建：绑布局、绑底部导航、懒加载三个页面、首次进来自动开首页。
     *
     * @param savedInstanceState 系统重建时传回的状态（本类不依赖它，页面数据都在 SharedPreferences）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Ui.insetContent(this);   // 适配刘海屏 / 状态栏，把内容顶到安全区内

        container = findViewById(R.id.tab_container);
        tabBar = findViewById(R.id.tab_bar);
        // 底部导航只负责"告诉我们是第几个"，真正切页面在 switchTab 里做
        tabBar.setOnTabSelected(new BottomTabBar.OnTabSelected() {
            @Override
            public void onTabSelected(int index) {
                switchTab(index);
            }
        });

        AppLog.i("MAIN", "进入三主界面");

        // 第一次打开 App（没填过昵称）先跳注册页；注意不 return，主页照样先搭好，
        // 这样从注册页返回时下面已经是有内容的首页，不会白屏
        if (!UserStore.isRegistered(this)) {
            startActivity(new Intent(this, RegisterActivity.class));
        }
        // 三个页面都先建好：新手引导要能高亮"剧本页的加号""我的页的设置"，不能等到点开才有
        tabView(0);
        tabView(1);
        tabView(2);
        showTab(0);   // 默认停在首页
    }

    // ---------------- Tab 管理 ----------------

    /**
     * 取某个 Tab 对应的页面：没有就现在创建，有就直接返回缓存的那个。
     * 这就是"懒加载 + 复用"的核心，三个页面各只有一份实例。
     *
     * @param index Tab 下标：0 首页 / 1 剧本 / 2 我的
     * @return 对应的页面 View
     */
    private View tabView(int index) {
        if (index == 0) {
            if (homeView == null) {
                homeView = new HomeTabView(this, this);   // 第二个 this：本类就是 TabHost
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

    /**
     * 真正把某个页面显示出来：清空容器 → 换上新页面 → 播过渡动画 → 刷新数据。
     *
     * @param index 要显示的 Tab 下标
     */
    private void showTab(int index) {
        currentTab = index;
        container.removeAllViews();   // 先把上一个页面从容器摘掉（实例还在，只是不显示）
        View v = tabView(index);
        if (v.getParent() instanceof ViewGroup) {
            ((ViewGroup) v.getParent()).removeView(v);   // 保险：万一还挂在别处，先摘干净
        }
        // 切 Tab 时淡入 + 轻微上浮，避免页面“闪”一下直接换掉
        v.setAlpha(0f);
        v.setTranslationY(Ui.dp(this, 10));
        container.addView(v, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        v.animate().alpha(1f).translationY(0f).setDuration(180).start();
        tabBar.setSelected(index);   // 底部导航的高亮块跟着走
        refreshCurrent();
    }

    /** 只刷新当前选中的那一页（切 Tab 时用，避免三页都刷一遍做无用功） */
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
            // 刷新失败不能连累切 Tab：记日志，界面照常显示（数据下次进来还会再刷）
            AppLog.e("MAIN", "刷新 Tab " + currentTab + " 失败", e);
        }
    }

    /**
     * {@link TabHost} 的实现：三个页面点"切到某 Tab"时都会走这里。
     * 和当前 Tab 相同就直接忽略，避免无意义的重建和动画。
     *
     * @param index 目标 Tab 下标
     */
    @Override
    public void switchTab(int index) {
        if (index == currentTab) {
            return;
        }
        AppLog.i("MAIN", "切换 Tab -> " + index);
        showTab(index);
    }

    // ---------------- 生命周期 ----------------

    /**
     * 每次回到前台：先看字号/字体有没有改过（改过就整页重建），
     * 否则把三个页面都刷一遍（从聊天页/复盘页/工坊回来，数据可能变了），最后看要不要弹引导。
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (SettingsStore.changed) {
            // 字号/字体改了：只有重建 Activity 才能让已经创建好的 View 重新按新 fontScale 排版。
            // 重建后会重新走 onCreate → 三个页面全部按新字号重建一遍。
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

    /** 离开前台：把引导收掉，并明确标记成"没看完"，下次回来还要再显示 */
    @Override
    protected void onPause() {
        super.onPause();
        if (guide != null) {
            // 用 abort 而不是 finish：切后台/锁屏/页面重建不算"看过"，
            // 下次进来还要再显示（旧版这里用 finish，导致引导经常再也不出现）
            guide.abort();
            guide = null;
            guideShowing = false;
        }
    }

    /**
     * 返回键：优先关引导 → 其次回首页 → 都不适用才真的退出 App。
     * 这样用户在「剧本」或「我的」页按返回，是回首页而不是直接退出，符合习惯。
     */
    @Override
    public void onBackPressed() {
        if (guide != null) {
            // 返回键关掉引导也算"跳过"，记录一下，避免下次又弹
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

    /**
     * 打开某个骗局分类：切到「剧本」Tab 再按分类筛选。
     * 首页翻书动画播完后就是调的它。
     *
     * @param categoryId 分类 id，例如 "game"
     */
    @Override
    public void openCategory(String categoryId) {
        showTab(1);
        if (scriptsView != null) {
            scriptsView.filterByCategory(categoryId);
        }
    }

    /**
     * 打开某个剧本（进入聊天/剧情页）。
     *
     * @param scriptId 剧本 id，通过 Intent extra 传给 ChatActivity
     */
    @Override
    public void openScript(String scriptId) {
        Intent i = new Intent(this, ChatActivity.class);
        i.putExtra("script_id", scriptId);
        startActivity(i);
        overridePendingTransition(R.anim.page_in, R.anim.page_out);   // 统一的左进右出翻页动画
    }

    /** 打开剧本工坊（自己写剧本的地方） */
    @Override
    public void openStudio() {
        startActivity(new Intent(this, StudioActivity.class));
        overridePendingTransition(R.anim.page_in, R.anim.page_out);
    }

    /** 打开系统文件选择器导入剧本 JSON（ACTION_OPEN_DOCUMENT 走 SAF，不需要存储权限） */
    @Override
    public void openImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);   // 只让选"能打开的文件"，不会选到文件夹
        i.setType("*/*");                          // 不限定 MIME：JSON 在各机型上类型不统一，干脆放开
        startActivityForResult(i, REQ_IMPORT);
    }

    /** 打开字体与字号设置页 */
    @Override
    public void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
        overridePendingTransition(R.anim.page_in, R.anim.page_out);
    }

    /** 打开真实案例来源库（每条案例的原文链接都在里面） */
    @Override
    public void openAllSources() {
        startActivity(new Intent(this, SourcesActivity.class));
        overridePendingTransition(R.anim.page_in, R.anim.page_out);
    }

    /** 「再看一遍」新手引导：清掉"已看过"标记 → 切回首页 → 再尝试弹一次（三步缺一不可） */
    @Override
    public void openGuideAgain() {
        SettingsStore.setGuideDone(this, false);
        guideShowing = false;
        switchTab(0);
        maybeShowGuide();
    }

    /** 打开隐藏的运行日志页（「我的」页长按"关于"1.5 秒才会到这里，界面没有入口） */
    @Override
    public void openLog() {
        startActivity(new Intent(this, LogActivity.class));
    }

    /** 数据变了（清档 / 导入剧本 / 通关）：三个页面都刷一遍文字，不做页面重建 */
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

    /**
     * 弹一个短 toast（三个页面共用，不用各自拿 Context 再去 new Toast）。
     *
     * @param msg 要显示的一句话
     */
    @Override
    public void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // ---------------- 导入剧本 ----------------

    /**
     * 系统文件选择器返回：把用户选中的 JSON 文件整个读成字符串，交给 ScriptStore 解析入库。
     *
     * <p>为什么不用第三方 JSON 库：项目零依赖，解析在 ScriptStore 里手写完成。
     * 这里只负责"把字节读出来 + 转 UTF-8 + 报错"，解析失败由 ScriptStore 返回 null 表示。</p>
     *
     * @param requestCode 请求码，只认 REQ_IMPORT
     * @param resultCode  结果码，用户没选（取消）时不是 RESULT_OK，直接返回
     * @param data        里面带着选中文件的 Uri
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 三道过滤：不是导入请求 / 用户取消 / 没拿到 Uri，都不处理
        if (requestCode != REQ_IMPORT || resultCode != RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            // 用 ContentResolver 打开输入流：不需要任何存储权限，SAF 已经授权了这一个文件
            InputStream in = getContentResolver().openInputStream(uri);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];   // 4KB 一块地读，剧本文件一般几十 KB，几轮就读完
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            in.close();   // 读完立刻关流，别占着文件句柄
            // 强制按 UTF-8 解码：剧本都是中文，用系统默认编码在部分机器上会变乱码
            Script s = ScriptStore.importJson(this, new String(bos.toByteArray(), "UTF-8"));
            if (s == null) {
                toast("导入失败：不是有效的剧本文件");
            } else {
                toast("已导入：" + s.title);
                AppLog.i("MAIN", "导入剧本 " + s.id);
                onDataChanged();   // 立刻刷新三个页面，新剧本马上出现在列表里
            }
        } catch (Exception e) {
            // 读文件失败 / 编码不支持 / 存储被移除：统一提示，不让 App 崩
            AppLog.e("MAIN", "导入剧本失败", e);
            toast("导入失败：" + e.getMessage());
        }
    }

    // ---------------- 新手引导（5 步，会自动切 Tab） ----------------

    /**
     * 判断要不要弹新手引导：正在显示 / 已经看过 / 还没注册，三种情况都不弹；
     * 都满足时 post 到下一帧，等首页真正画完再挂浮层（坐标才准）。
     */
    private void maybeShowGuide() {
        if (guideShowing || SettingsStore.guideDone(this) || !UserStore.isRegistered(this)) {
            return;
        }
        guideShowing = true;
        final View content = findViewById(android.R.id.content);

        // 等首页真正画完再挂引导：用布局回调代替固定 900ms 延时，
        // 既不会"抢在页面之前"导致坐标错，也不会白等近一秒。
        content.post(new Runnable() {
            @Override
            public void run() {
                // post 期间用户可能已经退出或引导被中断，两个条件都要再确认一次
                if (isFinishing() || !guideShowing) {
                    return;
                }
                showGuide(content);
            }
        });
    }

    /**
     * 真正创建并启动引导浮层：5 步，每步前先切到对应 Tab，再高亮某个控件。
     * 第 0/1 步在首页、第 2/3 步在剧本页、第 4 步在我的页，所以 {@code setStepPreparer}
     * 里必须按 index 切 Tab，否则高亮的 View 根本不在屏幕上，浮层会指错位置。
     *
     * @param content android.R.id.content 那一层（浮层挂在这，能盖住整页）
     */
    private void showGuide(View content) {
        final GuideOverlay overlay = new GuideOverlay(this);
        guide = overlay;
        // 「跳过引导」放在底部 Tab 栏上方，不压住导航
        overlay.setBottomInsetDp(64);
        // 每一步开始前的"准备工作"：切到这一步要讲解的页面
        overlay.setStepPreparer(new GuideOverlay.OnStepPrepare() {
            @Override
            public void onPrepare(int index) {
                // 0/1 在首页；2/3 在剧本页；4 在我的页
                if (index == 2 || index == 3) {
                    switchTab(1);
                    if (index == 2 && scriptsView != null) {
                        scriptsView.resetForGuide();   // 清掉搜索/筛选，保证列表上能看见入口
                    }
                } else if (index == 4) {
                    switchTab(2);
                } else {
                    switchTab(0);
                }
            }
        });
        // 第 1 步：首页轮播
        overlay.addStep(homeView == null ? null : homeView.carouselView(), "每天一个真实案例",
                "左右滑动可以看更多；点开能复制原文链接，全部来自公开报道。");
        // 第 2 步：首页书架
        overlay.addStep(homeView == null ? null : homeView.shelfView(), "从一本书开始",
                "六本书对应六类骗局，点开封面会翻开，翻完直接带你去剧本页。");
        // 第 3 步：剧本列表（0.55f 是"高亮框最小高度"参数，给小控件留够点击区）
        overlay.addStep(scriptsView == null ? null : scriptsView.listView(), "这里是全部剧本",
                "18 本剧本都在这里，上面可以搜索和按骗局类型筛选，点一本就能开始。", 0.55f);
        // 第 4 步：剧本页的加号按钮（自己写剧本）
        overlay.addStep(scriptsView == null ? null : scriptsView.fabButtonView(), "自己也能写剧本",
                "点搜索框右边的加号：新建空白、套模板，或者导入同学发来的剧本。");
        // 第 5 步：我的页的设置区
        overlay.addStep(mineView == null ? null : mineView.settingsRowView(), "觉得字累眼？",
                "这里能换系统字体、调大字号，还有一个藏起来的运行日志。");

        ((android.view.ViewGroup) content).addView(overlay);
        overlay.start(new GuideOverlay.OnFinish() {
            @Override
            public void onFinish(boolean completed) {
                // 只有"看完"或"主动跳过"才记录；被打断下次还会显示
                if (completed) {
                    SettingsStore.setGuideDone(MainActivity.this);
                }
                guideShowing = false;
                guide = null;
                switchTab(0);   // 引导结束一律回首页，用户从这里开始用
                AppLog.i("GUIDE", completed ? "引导结束（已记录）" : "引导中断（下次再显示）");
            }
        });
    }
}