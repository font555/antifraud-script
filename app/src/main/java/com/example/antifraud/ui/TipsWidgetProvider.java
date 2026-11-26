package com.example.antifraud.ui;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.example.antifraud.R;
import com.example.antifraud.engine.UserStore;

import java.util.Random;

/**
 * 桌面小组件「反诈书库 · 今日防骗贴士」。
 *
 * <p>显示三行内容：标题「反诈书库」、一条随机防骗贴士、本机读书战绩
 * 「已读 N 本 · 累计损失 ¥X」；点击整个小组件打开 App 首页。</p>
 *
 * <p>零第三方依赖、不联网、不申请权限：贴士文案是本地常量，
 * 已读本数与累计损失只读本机 SharedPreferences（UserStore）。</p>
 *
 * <p><b>谁在用它：</b>系统桌面（AppWidget 宿主）+ 本 App 里的广播
 * {@link UserStore#ACTION_STATS_CHANGED}。用户把小组件拖到桌面后，
 * 由系统按周期调 {@link #onUpdate}；App 内数据一变就发广播，走 {@link #onReceive}
 * 立刻刷新，不用干等系统的 30 分钟刷新周期。</p>
 *
 * <p><b>为什么只用 RemoteViews 的 setTextViewText：</b>小组件跑在"桌面进程"里，
 * 不能放自定义 View、不能跑自己的绘制代码，能用的 API 只有 RemoteViews 那几种。
 * 所以这里刻意不引用 AvatarView / PTextView 之类的自定义控件，只用系统 TextView。</p>
 */
public class TipsWidgetProvider extends AppWidgetProvider {

    /** 打开 App 的 PendingIntent 请求码（小组件共用同一个即可） */
    private static final int REQUEST_OPEN_APP = 0;

    /**
     * 8 条防骗贴士。MainActivity.TIPS 是私有字段无法直接引用，故在此复制一份，
     * 内容与首页「今日防骗贴士」保持一致。
     *
     * <p>改文案时记得两边同步改，否则桌面和首页会"各说各话"。</p>
     */
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

    /** App 里数据变化时会发这个广播，收到就立刻刷新，不必等系统 30 分钟周期 */
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);   // 先交给父类处理（ACTION_APPWIDGET_UPDATE 等系统事件）
        if (intent != null && UserStore.ACTION_STATS_CHANGED.equals(intent.getAction())) {
            // 只处理"战绩变了"这一种广播，其它广播交回父类逻辑
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            // 取出桌面上所有本小组件实例（用户可能放了多个）
            int[] ids = manager.getAppWidgetIds(
                    new ComponentName(context, TipsWidgetProvider.class));
            onUpdate(context, manager, ids);   // 复用统一刷新逻辑
        }
    }

    /**
     * 刷新小组件内容：随机贴士 + 本机战绩 + 点击跳转。
     *
     * <p>系统会在三种时机调用：拖到桌面时、系统周期刷新时、以及本类 onReceive 收到
     * 数据变化广播时。</p>
     *
     * @param context          任意 Context（小组件不能持有 Activity）
     * @param appWidgetManager 小组件管理器，负责把 RemoteViews 推给桌面
     * @param appWidgetIds     要刷新的实例 id 数组，可能为空
     */
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        if (appWidgetIds == null || appWidgetIds.length == 0) {
            return;   // 桌面上没有本小组件，什么都不用做
        }

        // 每次刷新随机换一条贴士，避免桌面上永远是同一句
        Random random = new Random();
        String tip = TIPS[random.nextInt(TIPS.length)];

        // 本机读书战绩：已读 N 本 · 累计损失 ¥X
        String stats = "已读 " + UserStore.plays(context) + " 本 · 累计损失 ¥"
                + Ui.money(UserStore.totalLoss(context));

        // 点击整个小组件：打开 App 首页
        PendingIntent openApp = buildOpenAppIntent(context);

        for (int appWidgetId : appWidgetIds) {
            // 每个实例都要单独创建 RemoteViews：桌面上的多个小组件互不影响
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_tips);
            // 只用 setTextViewText 绑定文本，文案全部来自本地，不涉及自定义 View
            views.setTextViewText(R.id.tv_tip, tip);
            views.setTextViewText(R.id.tv_stats, stats);
            views.setOnClickPendingIntent(R.id.widget_root, openApp);   // 整块可点
            appWidgetManager.updateAppWidget(appWidgetId, views);       // 推给桌面生效
        }
    }

    /**
     * 构造「点一下回 App」的 PendingIntent：显式 Intent + 不可变，minSdk 24 可放心用 IMMUTABLE。
     *
     * <p>为什么要显式 Intent：隐式 Intent 在小组件里不可靠，且在 Android 12+
     * 会因 PendingIntent 可变性检查报错。显式写清目标 Activity 最稳妥。</p>
     *
     * <p>为什么要 FLAG_IMMUTABLE：Android 12（API 31）起必须显式声明可变性，
     * 本 Intent 不需要被别的应用改写，声明成不可变既合规又更安全。</p>
     *
     * @param context 任意 Context
     * @return 可直接挂到 RemoteViews 上的点击意图
     */
    private PendingIntent buildOpenAppIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        // 从桌面启动要给 NEW_TASK：小组件不在 Activity 任务栈里，没有它会启动失败
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // UPDATE_CURRENT：多个小组件共用同一个请求码时，复用并更新已存在的 PendingIntent
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(context, REQUEST_OPEN_APP, intent, flags);
    }
}
