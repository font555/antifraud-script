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
import com.example.antifraud.engine.AppLog;
import com.example.antifraud.engine.UserStore;

/**
 * 首次进入的"注册"页：昵称 + 头像，只存 SharedPreferences（本机），不联网、不上传。
 */
public class RegisterActivity extends BaseActivity {

    private int selected = 0;
    private LinearLayout llAvatars;
    private EditText etNick;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        Ui.insetContent(this);

        etNick = findViewById(R.id.et_nick);
        etNick.setTypeface(Typefaces.pixel(this));
        etNick.getPaint().setAntiAlias(false);
        llAvatars = findViewById(R.id.ll_avatars);

        buildAvatars();

        findViewById(R.id.btn_enter).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submit();
            }
        });
        findViewById(R.id.btn_skip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UserStore.register(RegisterActivity.this, "同学", 0);
                close();
            }
        });

        // 卡片从下方浮起
        View card = findViewById(R.id.card);
        card.setAlpha(0f);
        card.setTranslationY(Ui.dp(this, 36));
        card.animate().alpha(1f).translationY(0f).setDuration(320).start();
    }

    private void buildAvatars() {
        llAvatars.removeAllViews();
        for (int i = 0; i < MainActivity.AVATARS.length; i++) {
            final int idx = i;
            // 外层容器负责"选中"高亮，里面是像素头像
            LinearLayout box = new LinearLayout(this);
            box.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1f);
            lp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
            box.setLayoutParams(lp);
            box.setPadding(Ui.dp(this, 3), Ui.dp(this, 3), Ui.dp(this, 3), Ui.dp(this, 3));
            box.setBackgroundResource(idx == selected ? R.drawable.px_button : R.drawable.px_paper);

            AvatarView av = new AvatarView(this);
            av.setAvatar(avatarColor(idx), idx);
            box.addView(av, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));

            box.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    selected = idx;
                    buildAvatars();
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

    /** 六个头像的底色，和聊天页的配色保持一致 */
    private int avatarColor(int index) {
        int[] colors = {
                0xFFE4572E, 0xFF3F7FA6, 0xFFE9B44C, 0xFF4E7B3A, 0xFF8E5B8F, 0xFF3C7A89
        };
        return colors[Math.max(0, index) % colors.length];
    }

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
        UserStore.register(this, nick, selected);
        AppLog.i("USER", "注册 " + nick + " 头像 " + selected);
        close();
    }

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

    private void close() {
        finish();
        overridePendingTransition(R.anim.page_in, R.anim.page_out);
    }
}
