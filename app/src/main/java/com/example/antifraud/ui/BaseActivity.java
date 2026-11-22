package com.example.antifraud.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

import com.example.antifraud.engine.SettingsStore;

/**
 * 所有页面的基类：在这里统一应用"字号倍数"设置。
 * 布局里的 sp 会跟着系统 fontScale 缩放，所以改这里就能全局生效。
 */
public class BaseActivity extends Activity {

    @Override
    protected void attachBaseContext(Context base) {
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.fontScale = SettingsStore.fontScale(base);
        super.attachBaseContext(base.createConfigurationContext(cfg));
    }
}
