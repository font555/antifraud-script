package com.example.antifraud.ui;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.antifraud.R;
import com.example.antifraud.engine.SettingsStore;
import com.example.antifraud.engine.AppLog;
import com.example.antifraud.engine.UserStore;

/**
 * 首次进入的"注册"页：昵称 + 头像，只存 SharedPreferences（本机），不联网、不上传。
 *
 * <p><b>从哪里进来：</b>{@link MainActivity} 启动时检查 {@code UserStore.hasUser}，
 * 没注册过就跳到本页；注册完成后 finish 回到主界面。</p>
 *
 * <p><b>为什么叫"注册"却什么都不用填真的：</b>本作品零权限、零联网、不收集任何个人信息。
 * 这里的"昵称 + 头像"只是让本机体验更亲切的显示名，随便起、随时能在"我的"里改，
 * 不会上传到任何服务器，也不与任何账号绑定。所以没有手机号、验证码、密码这些环节。</p>
 *
 * <p><b>交互设计：</b></p>
 * <ul>
 *   <li>昵称按"字"计数（中文、emoji 都算 1 个），限制 2–8 个字；</li>
 *   <li>6 个头像横向排开，选中有高亮 + 轻微弹跳；</li>
 *   <li>"跳过"按钮直接用默认昵称"同学"+ 第 0 号头像，绝不让用户卡在注册页；</li>
 *   <li>整个卡片从下方 36dp 浮起淡入，和作品其他页面的动效保持一致。</li>
 * </ul>
 */
public class RegisterActivity extends BaseActivity {

    /** 当前选中的头像序号（0–5）；初值 0，buildAvatars 会据此画高亮 */
    private int selected = 0;
    /** 头像横向容器，每次切换选中都会整体重建（数量少，重建比逐个改背景更省事） */
    private LinearLayout llAvatars;
    /** 昵称输入框，校验失败时要让它抖一下，所以要存成员变量 */
    private EditText etNick;

    /**
     * 页面创建：初始化输入框样式、头像列表、两个按钮、入场动画。
     *
     * @param savedInstanceState 系统恢复用的状态包，本页不使用（每次进入都是全新的注册流程）
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        Ui.insetContent(this);   // Android 15+ 边到边适配

        etNick = findViewById(R.id.et_nick);
        // 输入框也要像素字体，否则打字时会出现"框里是系统字体、其他文字是像素字体"的割裂感
        etNick.setTypeface(Typefaces.pixel(this));
        etNick.getPaint().setAntiAlias(false);   // 关抗锯齿，和像素字体保持一致
        llAvatars = findViewById(R.id.ll_avatars);

        buildAvatars();   // 一次性把 6 个头像铺开

        // 「进入」按钮：走校验后注册
        findViewById(R.id.btn_enter).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit();
            }
        });
        // 「跳过」按钮：不校验、不问原因，直接给个默认身份放行。
        // 这是产品上的取舍：注册页只是为了让体验亲切，不能成为使用门槛
        findViewById(R.id.btn_skip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UserStore.register(RegisterActivity.this, "同学", 0);
                close();
            }
        });

        // 卡片从下方浮起：36dp 的位移 + 320ms，比其他页面的入场稍慢一点，
        // 因为这是用户第一次见到作品，慢一点显得郑重
        View card = findViewById(R.id.card);
        card.setAlpha(0f);
        card.setTranslationY(Ui.dp(this, 36));
        card.animate().alpha(1f).translationY(0f).setDuration(320).start();
    }

    /**
     * 重建 6 个头像：外层容器负责"选中高亮"，里面是像素头像。
     *
     * <p>为什么每次切换都整体重建而不做局部刷新：总共只有 6 个，重建开销可忽略，
     * 而且能保证"只有当前选中的那一个高亮"——局部改写容易漏掉上一个的还原。</p>
     *
     * <p>尺寸全部用 {@link SettingsStore#scaleBox} 放大：头像框和头像本身都是"装内容"的
     * 固定 dp，字号放大后如果不跟着长，选中高亮的白边会显得很局促。</p>
     */
    private void buildAvatars() {
        llAvatars.removeAllViews();   // 先清空，再按当前 selected 重画
        for (int i = 0; i < MainActivity.AVATARS.length; i++) {
            final int idx = i;   // 闭包里要用的常量副本（i 会变，idx 不会）
            // 外层容器负责"选中"高亮，里面是像素头像
            LinearLayout box = new LinearLayout(this);
            box.setGravity(Gravity.CENTER);
            // 宽度 0 + weight 1：6 个平分整行，屏幕再窄也不会挤出去
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, SettingsStore.scaleBox(this, Ui.dp(this, 52)), 1f);
            lp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);   // 左右各 3dp 间隔
            box.setLayoutParams(lp);
            int bp = SettingsStore.scaleBox(this, Ui.dp(this, 3));
            box.setPadding(bp, bp, bp, bp);   // 内边距 3dp，选中时这圈就是高亮边
            // 选中用主题色实心（px_button），未选中用白纸（px_paper）
            box.setBackgroundResource(idx == selected ? R.drawable.px_button : R.drawable.px_paper);

            AvatarView av = new AvatarView(this);
            av.setAvatar(avatarColor(idx), idx);   // 底色 + 造型编号
            int avSize = SettingsStore.scaleBox(this, Ui.dp(this, 40));   // 头像 40dp，同样随字号放大
            box.addView(av, new LinearLayout.LayoutParams(avSize, avSize));

            box.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    selected = idx;
                    buildAvatars();   // 重建一次，让高亮换到新选的那个
                    // 弹跳反馈：先放大到 1.12 倍（90ms），再弹回原大小（90ms）
                    v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(90)
                            .withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    v.animate().scaleX(1f).scaleY(1f).setDuration(90).start();
                                }
                            }).start();
                }
            });
            llAvatars.addView(box);
        }
    }

    /**
     * 六个头像的底色，和聊天页的配色保持一致。
     *
     * <p>用 {@code index % colors.length} 取模：调用方传任何下标都不会越界，
     * 负下标用 {@code Math.max(0, index)} 兜住。</p>
     *
     * @param index 头像序号（0–5）
     * @return 一个不透明颜色值（ARGB）
     */
    private int avatarColor(int index) {
        int[] colors = {
                0xFFE4572E, 0xFF3F7FA6, 0xFFE9B44C, 0xFF4E7B3A, 0xFF8E5B8F, 0xFF3C7A89
        };
        return colors[Math.max(0, index) % colors.length];
    }

    /**
     * 提交注册：校验昵称长度，通过就存本机并关闭页面。
     *
     * <p>校验规则：不能为空、至少 2 个字、最多 8 个字。三条都不通过就抖一下输入框
     * 并弹 Toast，**不截断、不静默修正**——静默改用户输入是最让人困惑的交互。</p>
     *
     * <p>长度按"字"算（{@link String#codePointCount}）：中文和 emoji 都算 1 个，
     * 不用 {@code length()} 的 UTF-16 编码单元数，否则一个 emoji 会被算成 2 个字。</p>
     */
    private void submit() {
        String nick = etNick.getText() == null ? "" : etNick.getText().toString().trim();
        // 按"字"算长度：中文 / emoji 都算 1 个，不用字节数
        int len = nick.codePointCount(0, nick.length());
        if (TextUtils.isEmpty(nick)) {
            shakeNick();
            AppLog.i("USER", "注册被拦下：长度 " + len);
            Toast.makeText(this, "先起个名字吧", Toast.LENGTH_SHORT).show();
            return;
        }
        if (len < 2) {
            shakeNick();
            AppLog.i("USER", "注册被拦下：长度 " + len);
            Toast.makeText(this, "昵称至少 2 个字", Toast.LENGTH_SHORT).show();
            return;
        }
        if (len > 8) {
            // 输入框的 maxLength 在 XML 里，不在本文件范围内；超长时只提示、不截断也不注册
            shakeNick();
            AppLog.i("USER", "注册被拦下：长度 " + len);
            Toast.makeText(this, "昵称最多 8 个字", Toast.LENGTH_SHORT).show();
            return;
        }
        // 校验通过：写入本机 SharedPreferences（不联网），再记一条日志
        UserStore.register(this, nick, selected);
        AppLog.i("USER", "注册 " + nick + " 头像 " + selected);
        close();
    }

    /**
     * 输入框左右抖三下：10 → -10 → 0，每段 60ms。
     *
     * <p>用动画而不是弹窗表达"输入不合法"：抖动是移动端最轻的否定反馈，
     * 不打断操作，用户马上就能重试。</p>
     */
    private void shakeNick() {
        etNick.animate().translationX(10).setDuration(60)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        etNick.animate().translationX(-10).setDuration(60)
                                .withEndAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        etNick.animate().translationX(0).setDuration(60).start();
                                    }
                                }).start();
                    }
                }).start();
    }

    /**
     * 关闭本页并播放退场动画。
     *
     * <p>注册完成后不能留在栈里，否则用户从主界面按返回会又回到注册页。</p>
     */
    private void close() {
        finish();
        overridePendingTransition(R.anim.page_in, R.anim.page_out);
    }
}
