package com.example.antifraud.ui;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.antifraud.R;
import com.example.antifraud.engine.AppLog;

/**
 * 运行日志页。藏得比较深：设置 → 「关于」标题上长按 2 秒才会进来。
 * 只读本机日志，可复制、清空。
 *
 * <p><b>为什么要藏起来：</b>普通用户不需要看到日志，但答辩/调试时想证明
 * "作品真的跑过、每一步都记下来了"，就需要一个能当场翻出来的证据页。
 * 做成隐藏入口，既不干扰正常使用，又能随时调出来。</p>
 *
 * <p><b>谁在写日志：</b>{@link AppLog}（本机文件，容量有限，满了会滚动覆盖）。
 * 各页面在 onCreate 里记"打开了哪个页面"，业务逻辑里记关键动作（注册、进复盘、
 * 生成海报等）。日志<b>只写本机私有目录</b>，不联网、不上传、不含任何个人信息。</p>
 *
 * <p><b>页面结构：</b>返回 + 标题 / 刷新·复制·清空三个按钮 / 一大块可滚动可选中文本。</p>
 *
 * <p><b>日志长什么样（示意）：</b></p>
 * <pre>
 * 11-26 19:20:03  UI    打开 MainActivity
 * 11-26 19:20:11  USER  注册 同学 头像 0
 * 11-26 19:20:30  CHAT  开始剧本 game-account
 * 11-26 19:22:14  REVIEW 进入复盘 剧本 game-account 损失 ￥4700 步数 6 高风险 2 次
 * </pre>
 * <p>格式是「时间 + 模块标签 + 内容」，模块标签便于按关键词筛选（UI / USER / CHAT / REVIEW / TIPS）。</p>
 *
 * <p><b>为什么用代码拼界面而不写 layout 文件：</b>本页只有一个页面用、结构也简单，
 * 单独建一个 XML 反而要维护两处（改一处忘一处），代码直拼更直观。</p>
 */
public class LogActivity extends BaseActivity {

    /** 显示日志正文的控件，刷新/清空按钮都要改它的内容，所以存成员变量 */
    private TextView tvLog;

    /**
     * 页面创建：整个界面都用代码拼（没有对应的 layout 文件），因为只有这一个页面用它。
     *
     * @param savedInstanceState 系统恢复用的状态包，本页不使用
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 外层 ScrollView：日志可能很长，必须能滚
        // 注意 ScrollView 里只能有一个直接子 View，所以先建 LinearLayout 当唯一子节点
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.paper));   // 羊皮纸底色，和其他页面统一
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);                              // 页面四周 16dp 留白
        root.setPadding(p, p, p, p);
        scroll.addView(root);

        // ——— 顶部：左边返回、右边标题 ———
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.text(this, "← 返回", 12, R.color.ink_secondary);
        back.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();   // 日志页不需要特殊退场动画，直接 finish
            }
        });
        top.addView(back);
        TextView title = Ui.text(this, "运行日志", 16, R.color.ink);
        // 宽度 0 + weight 1：标题占据剩余空间，配合 END 对齐就跑到最右边
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(tlp);
        title.setGravity(Gravity.END);
        top.addView(title);
        root.addView(top);

        // ——— 三个操作按钮：刷新 / 复制全部 / 清空 ———
        // 三个按钮等分一行：刷新用来看最新状态，复制用来发给队友，清空用来准备下一场演示
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 10));

        TextView refresh = chip("刷新");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 重新从内存/文件里取一份最新日志，覆盖显示
                tvLog.setText(AppLog.dump());
            }
        });
        actions.addView(refresh);

        TextView copy = chip("复制全部");
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 复制到剪贴板：最实用的场景是把日志粘到聊天里发给队友排查
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                        getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("反诈日志", AppLog.dump()));
                Toast.makeText(LogActivity.this, "已复制，可粘贴到聊天里", Toast.LENGTH_SHORT).show();
            }
        });
        actions.addView(copy);

        TextView clear = chip("清空");
        clear.setTextColor(getColor(R.color.warn_red));   // 危险操作：标红提醒
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppLog.clear();                  // 清空本机日志文件
                tvLog.setText(AppLog.dump());    // 立刻刷新显示（会显示"已清空"或空）
            }
        });
        actions.addView(clear);
        root.addView(actions);

        // ——— 日志正文 ———
        // 这里用普通 TextView + ScrollingMovementMethod，而不是再套一个 ListView：
        // 日志是一次性读出的一段纯文本，没有分页/复用需求，TextView 最省事
        tvLog = new TextView(this);
        Ui.pixel(tvLog);                        // 套像素字体，和全站一致
        tvLog.setTextSize(9);                   // 日志用 9sp 小字，一屏能看更多行
        tvLog.setTextColor(getColor(R.color.ink_secondary));   // 次要色，不刺眼
        tvLog.setLineSpacing(Ui.dp(this, 3), 1f);              // 行距 3dp，方便逐行扫
        tvLog.setTextIsSelectable(true);        // 允许长按选中局部复制（比整段复制更灵活）
        tvLog.setMovementMethod(new ScrollingMovementMethod()); // 文本自己可滚（外层 ScrollView 之外再加一层）
        tvLog.setText(AppLog.dump());           // 首次填充
        root.addView(tvLog);

        setContentView(scroll);
        AppLog.i("UI", "打开运行日志");          // 自己也要记一条，方便看到"谁在什么时候来看日志"
    }

    /**
     * 造一个三等分的操作按钮（刷新 / 复制 / 清空）。
     *
     * @param text 按钮文字
     * @return 已设好样式和等分宽度的按钮；具体的点击行为由调用方设置
     */
    private TextView chip(String text) {
        TextView tv = new PTextView(this);
        tv.setText(text);
        tv.setTextSize(11);                          // 单位 sp，随全局字号放大
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(getColor(R.color.ink));
        tv.setBackgroundResource(R.drawable.px_option);   // 白底描边样式
        tv.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        // 宽度 0 + weight 1：三个按钮平分整行，不随文字长短变形
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);   // 按钮之间 3dp 间隔
        tv.setLayoutParams(lp);
        return tv;
    }
}
