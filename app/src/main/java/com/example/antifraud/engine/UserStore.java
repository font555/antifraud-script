package com.example.antifraud.engine;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 本地用户信息：昵称 + 头像，只存在本机 SharedPreferences。
 * 不联网、不上传、不申请任何权限——答辩时可以直接讲这一点。
 */
public class UserStore {

    private static final String PREF = "antifraud_user";
    private static final String KEY_NICK = "nickname";
    private static final String KEY_AVATAR = "avatar";
    private static final String KEY_PLAYS = "plays";
    private static final String KEY_TOTAL_LOSS = "total_loss";

    private UserStore() {
    }

    public static boolean isRegistered(Context c) {
        return !prefs(c).getString(KEY_NICK, "").isEmpty();
    }

    public static void register(Context c, String nickname, int avatar) {
        prefs(c).edit()
                .putString(KEY_NICK, nickname == null ? "" : nickname.trim())
                .putInt(KEY_AVATAR, avatar)
                .apply();
    }

    public static String nickname(Context c) {
        String n = prefs(c).getString(KEY_NICK, "");
        return n.isEmpty() ? "同学" : n;
    }

    public static int avatar(Context c) {
        return prefs(c).getInt(KEY_AVATAR, 0);
    }

    /** 记录一次完整通关，用于书架上显示"已读 N 本" */
    public static void addPlay(Context c) {
        SharedPreferences p = prefs(c);
        p.edit().putInt(KEY_PLAYS, p.getInt(KEY_PLAYS, 0) + 1).apply();
    }

    public static int plays(Context c) {
        return prefs(c).getInt(KEY_PLAYS, 0);
    }

    /** 累计损失金额（用于首页展示"你在剧情里一共损失过多少钱"） */
    public static void addLoss(Context c, int loss) {
        SharedPreferences p = prefs(c);
        p.edit().putInt(KEY_TOTAL_LOSS, p.getInt(KEY_TOTAL_LOSS, 0) + loss).apply();
    }

    public static int totalLoss(Context c) {
        return prefs(c).getInt(KEY_TOTAL_LOSS, 0);
    }

    public static void reset(Context c) {
        prefs(c).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
