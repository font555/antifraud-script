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
 *
 * <p><b>谁在用：</b>项目里除 {@link PosterProvider}、{@link Tips}、{@link SharePoster}、
 * {@link Typefaces}、{@link Ui}、{@link TabHost} 这类非页面类之外，其余 Activity
 * （MainActivity、ChatActivity、ReviewActivity ……）全部继承本类。所以"字号设置"
 * 只需要在这一个地方做，全 App 立刻统一生效，不用每个页面各写一遍。</p>
 *
 * <p><b>它做两件事：</b></p>
 * <ol>
 *   <li>把用户在"设置 → 字体与字号"里选的字号倍数，写进本页面自己的 Configuration.fontScale；</li>
 *   <li>在 onCreate 里初始化运行日志，并记一条"打开了哪个页面"，方便出问题时回看。</li>
 * </ol>
 *
 * <p><b>⚠ 必须知道的坑：sp 会被"二次放大"</b></p>
 *
 * <p>Android 里所有以 <b>sp</b> 为单位的字号，最终像素大小 =
 * 字号数值 × density × <b>fontScale</b>。本类改了 fontScale，
 * 就等于给全 App 的 sp 文字乘了一次倍数。</p>
 *
 * <p>因此代码里写 {@code setTextSize(15)}（单位 sp）时，<b>不要再自己乘一遍
 * SettingsStore.fontScale()</b>。如果写成
 * {@code setTextSize(15 * fontScale)}，在"超大"档（2.1）下字体实际会被放大
 * 2.1 × 2.1 ≈ 4.4 倍，文字立刻撑破布局、按钮被挤出屏幕——这就是所谓
 * "setTextSize(sp) 二次放大"的坑。正确写法永远是只写基准 sp 值，
 * 让本类的 fontScale 去做唯一的一次放大。</p>
 *
 * <p>反过来说，用 <b>dp</b> 写死的"装文字的容器"（按钮高度、chip 高度、头像、
 * 气泡内边距）不会跟着 fontScale 变，字号一大就会被裁字。这类尺寸要用
 * {@link com.example.antifraud.engine.SettingsStore#scaleBox(Context, int)}
 * 主动放大（内部封顶 1.9 倍），两者分工是：</p>
 * <ul>
 *   <li><b>文字</b>：写 sp，交给 fontScale 自动放大（只放大一次）；</li>
 *   <li><b>容器</b>：写 dp，用 scaleBox 手动放大（封顶 1.9 倍）。</li>
 * </ul>
 */
public class BaseActivity extends Activity {

    /**
     * 在页面"创建之前"替换它使用的 Context，把自定义字号倍数注入 Configuration。
     *
     * <p>为什么要在这里改、而不是 onCreate 里改：Activity 解析布局、取资源、
     * 创建 View 全都在 onCreate 之前就用上了 Context 的 Configuration。
     * 只有 attachBaseContext 这个时机能保证"从第一帧起"就按新字号渲染，
     * 放在 onCreate 里改会先闪一下小字，再变大，观感很差。</p>
     *
     * @param base 系统给的原始 Context（里面的 fontScale 是系统设置里的字体大小，通常 1.0）
     */
    @Override
    protected void attachBaseContext(Context base) {
        // 1) 先复制一份系统当前配置，避免直接改系统对象（那样会污染全局，影响别的页面）
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        // 2) 把字号倍数换成用户在"设置"里挑的档位：标准 1.0 / 大 1.3 / 特大 1.7 / 超大 2.1
        cfg.fontScale = SettingsStore.fontScale(base);
        // 3) 用这份改过的配置「生出一个新的 Context」，再交给 Activity 使用。
        //    之后本页面所有 sp 文字都会自动乘上这个 fontScale（注意：只该乘这一次）
        super.attachBaseContext(base.createConfigurationContext(cfg));
    }

    /**
     * 页面创建时统一做两件事：初始化日志、记录一次页面访问。
     * 子类覆写时必须先调 super.onCreate，否则日志和对齐逻辑都会失效。
     *
     * @param savedInstanceState 系统恢复用的状态包，本类不使用，原样交给父类
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 初始化日志文件（放在应用私有目录，不联网、不上传），幂等，重复调用无副作用
        AppLog.init(getApplicationContext());
        // 记一条「打开 某页面」：出问题时按时间顺序能还原用户走到了哪一步
        AppLog.i("UI", "打开 " + getClass().getSimpleName());
    }
}
