package com.example.antifraud.engine;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * 本地用户信息：昵称 + 头像，只存在本机 SharedPreferences。
 * 不联网、不上传、不申请任何权限——答辩时可以直接讲这一点。
 *
 * <p>除了昵称头像，这里还顺手存了两个"成绩"：通关次数（plays）和累计损失（total_loss）。
 * 首页和"我的"页显示的"已读 N 本""你在剧情里一共损失过多少钱"都来自这里。</p>
 *
 * <p>为什么通关和损失要累计到本地：这是作品的"数据反馈"环节——
 * 学生玩完不只看到一句"你输了"，还能看到自己一学期玩下来一共在剧情里损失了多少，
 * 视觉冲击比单局更大。数据只在自己手机上，不统计、不上传。</p>
 *
 * <p>每次数据变化都会发一个广播（{@link #ACTION_STATS_CHANGED}），
 * 桌面小组件收到后立刻刷新，不用等系统 30 分钟的定时刷新。</p>
 */
public class UserStore {

    /** SharedPreferences 文件名；用户数据单独一个文件，方便"清除数据"时整体清掉 */
    private static final String PREF = "antifraud_user";
    /** 键：昵称 */
    private static final String KEY_NICK = "nickname";
    /** 键：头像编号（0-5，对应 AvatarView 里的 6 种像素头像） */
    private static final String KEY_AVATAR = "avatar";
    /** 键：累计通关次数 */
    private static final String KEY_PLAYS = "plays";
    /** 键：累计损失金额（元） */
    private static final String KEY_TOTAL_LOSS = "total_loss";

    /** 私有构造：纯静态工具类，不允许 new */
    private UserStore() {
    }

    /**
     * 是否已经注册过（注册页判断要不要跳过）。
     *
     * <p>判断标准就是"昵称非空"：没有单独的注册标记位，
     * 少一个字段就少一处可能不一致的状态。</p>
     *
     * @param c 任意 Context
     * @return true 表示已经填过昵称，不需要再走注册页
     */
    public static boolean isRegistered(Context c) {
        return !prefs(c).getString(KEY_NICK, "").isEmpty();
    }

    /**
     * 保存注册信息（昵称 + 头像）。
     *
     * <p>昵称做了两处清洗：null 转空串、去掉首尾空格。
     * 不做长度或敏感词限制——这是本地单机应用，昵称只给自己看。</p>
     *
     * @param c        任意 Context
     * @param nickname 昵称，null 会被当成空串
     * @param avatar   头像编号（0-5）
     */
    public static void register(Context c, String nickname, int avatar) {
        prefs(c).edit()
                .putString(KEY_NICK, nickname == null ? "" : nickname.trim()) // 一次 edit 写两个键，只落一次盘
                .putInt(KEY_AVATAR, avatar)
                .apply();
    }

    /**
     * 读昵称，没填过时返回"同学"。
     *
     * <p>这样所有显示昵称的地方都不需要自己判空，界面上永远不会出现空白名字。</p>
     *
     * @param c 任意 Context
     * @return 昵称；未注册时为"同学"
     */
    public static String nickname(Context c) {
        String n = prefs(c).getString(KEY_NICK, "");
        return n.isEmpty() ? "同学" : n;
    }

    /**
     * 读头像编号。
     *
     * @param c 任意 Context
     * @return 0-5 的编号；没设置过时是 0（第一个像素头像）
     */
    public static int avatar(Context c) {
        return prefs(c).getInt(KEY_AVATAR, 0);
    }

    /** 记录一次完整通关，用于书架上显示"已读 N 本" */
    public static void addPlay(Context c) {
        SharedPreferences p = prefs(c);
        int now = p.getInt(KEY_PLAYS, 0) + 1; // 先读旧值加一，再写回去
        p.edit().putInt(KEY_PLAYS, now).apply();
        AppLog.i("USER", "通关 +1，累计已读 " + now + " 本"); // 打点，复盘"数据怎么对不上"时能查
        notifyStatsChanged(c); // 立即通知桌面小组件刷新
    }

    /**
     * 读累计通关次数。
     *
     * @param c 任意 Context
     * @return 通关次数；一次都没通关时是 0
     */
    public static int plays(Context c) {
        return prefs(c).getInt(KEY_PLAYS, 0);
    }

    /** 累计损失金额（用于首页展示"你在剧情里一共损失过多少钱"） */
    public static void addLoss(Context c, int loss) {
        SharedPreferences p = prefs(c);
        int now = p.getInt(KEY_TOTAL_LOSS, 0) + loss; // 同样先读后写；loss 为 0 时也会触发一次刷新
        p.edit().putInt(KEY_TOTAL_LOSS, now).apply();
        AppLog.i("USER", "累计损失 +" + loss + "，合计 " + now);
        notifyStatsChanged(c);
    }

    /**
     * 读累计损失金额。
     *
     * @param c 任意 Context
     * @return 累计损失（元）；没有记录时是 0
     */
    public static int totalLoss(Context c) {
        return prefs(c).getInt(KEY_TOTAL_LOSS, 0);
    }

    /** 桌面小组件的数据刷新通知（App 内数据变化时立刻刷新，不用等 30 分钟） */
    public static final String ACTION_STATS_CHANGED = "com.example.antifraud.STATS_CHANGED";

    /**
     * 发一条"数据变了"的广播，让桌面小组件刷新。
     *
     * <p>用 setPackage 限定只发给本 App：Android 8 以后后台广播限制很严，
     * 加上包名能让系统把这条广播投递成"应用内广播"，不会被拦掉，也不会漏给别人。</p>
     *
     * <p>广播失败不影响主流程（例如系统正在重启），所以整段包在 try 里。</p>
     *
     * @param c 任意 Context
     */
    private static void notifyStatsChanged(Context c) {
        try {
            Intent i = new Intent(ACTION_STATS_CHANGED);
            i.setPackage(c.getPackageName()); // 只投递给本应用，避免被系统当成隐式广播拦截
            c.sendBroadcast(i);
        } catch (Exception e) {
            AppLog.e("USER", "刷新小组件失败", e); // 小组件刷不出来只是显示旧的，不影响 App
        }
    }

    /**
     * 清空全部用户数据（"我的 → 清除数据"用）。
     *
     * <p>直接 clear 整个设置文件：昵称、头像、通关数、累计损失一起归零，
     * 但不会动剧本文件——自己写的剧本是作品，不该被"清数据"顺手删掉。</p>
     *
     * @param c 任意 Context
     */
    public static void reset(Context c) {
        prefs(c).edit().clear().apply();
    }

    /**
     * 拿 SharedPreferences 对象，每次现取，不在类里存 Context（避免内存泄漏）。
     *
     * @param c 任意 Context
     * @return 本 App 私有的用户数据文件
     */
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
