package com.example.antifraud.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;

import com.example.antifraud.engine.AppLog;
import com.example.antifraud.engine.SettingsStore;

/**
 * 所有页面的基类：统一应用"字号倍数"设置，并记录页面访问日志（方便复盘）。
 * 布局里的 sp 会跟着系统 fontScale 缩放，所以改这里就能全局生效。
 */
public class BaseActivity extends Activity {

    @Override
    protected void attachBaseContext(Context base) {
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.fontScale = SettingsStore.fontScale(base);
        super.attachBaseContext(base.createConfigurationContext(cfg));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLog.init(getApplicationContext());
        AppLog.i("UI", "打开 " + getClass().getSimpleName());
    }
}
