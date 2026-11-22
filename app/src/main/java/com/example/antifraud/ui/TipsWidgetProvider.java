package com.example.antifraud.ui;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
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
 */
public class TipsWidgetProvider extends AppWidgetProvider {

    /** 打开 App 的 PendingIntent 请求码（小组件共用同一个即可） */
    private static final int REQUEST_OPEN_APP = 0;

    /**
     * 8 条防骗贴士。MainActivity.TIPS 是私有字段无法直接引用，故在此复制一份，
     * 内容与首页「今日防骗贴士」保持一致。
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

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        if (appWidgetIds == null || appWidgetIds.length == 0) {
            return;
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
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_tips);
            // 只用 setTextViewText 绑定文本，文案全部来自本地，不涉及自定义 View
            views.setTextViewText(R.id.tv_tip, tip);
            views.setTextViewText(R.id.tv_stats, stats);
            views.setOnClickPendingIntent(R.id.widget_root, openApp);
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    /** 构造「点一下回 App」的 PendingIntent：显式 Intent + 不可变，minSdk 24 可放心用 IMMUTABLE */
    private PendingIntent buildOpenAppIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(context, REQUEST_OPEN_APP, intent, flags);
    }
}
