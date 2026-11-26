package com.example.antifraud.ui;

import android.content.Context;
import android.util.AttributeSet;

/**
 * 自动套用像素字体的 TextView。布局里把 <TextView> 换成 <com.example.antifraud.ui.PTextView> 即可。
 *
 * <p><b>为什么需要这个类：</b>Android 里想改字体，正常做法是每个控件手写
 * {@code setTypeface(...)}。本项目界面大半是代码拼的（走 {@link Ui#text}），
 * 但也有部分是 XML 布局（比如注册页的昵称输入框、设置页的按钮）。为了让
 * "XML 里的文字也自动带像素字体"，就写了这个子类：只要把标签名换一下，
 * 不用在 Java 里再补一行设置代码。</p>
 *
 * <p><b>谁在用：</b>布局 XML 里的文字控件、以及 {@link ReviewActivity}、
 * {@link SettingsActivity}、{@link LogActivity} 等页面里代码创建按钮时。</p>
 *
 * <p><b>注意：</b>构造方法三个重载分别对应代码 new、XML 带属性、XML 带自定义样式三种
 * 创建方式，Android 反序列化布局时就是调用后两个，所以三个都要写，
 * 每个都要调一次 {@link #init()}，漏一个就会出现"某些控件没字体"的怪现象。</p>
 */
public class PTextView extends android.widget.TextView {

    /**
     * 代码里 new 出来的情况，例如 {@code new PTextView(this)}。
     *
     * @param c 任意 Context
     */
    public PTextView(Context c) {
        super(c);
        init();   // 统一初始化：套字体 + 调渲染参数
    }

    /**
     * 布局 XML 里声明本控件时，系统调用的就是这个构造（带 attrs，能读到 textSize 等属性）。
     *
     * @param c 任意 Context
     * @param a 布局里写的属性集合
     */
    public PTextView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    /**
     * XML 里带了 style / defStyleAttr 时走这个构造。
     *
     * @param c 任意 Context
     * @param a 布局里写的属性集合
     * @param s 默认样式资源 id
     */
    public PTextView(Context c, AttributeSet a, int s) {
        super(c, a, s);
        init();
    }

    /**
     * 三个构造共用的初始化：套像素字体，并只在"像素字体模式"下改渲染参数。
     *
     * <p>这里必须判断一下 {@link Typefaces#isPixel}：如果用户已经在设置里切到
     * "系统字体"，就不该再关抗锯齿、不该再加粗——系统字体关了抗锯齿会变得毛糙难看。</p>
     */
    private void init() {
        setTypeface(Typefaces.pixel(getContext()));   // 用户选系统字体时，这里拿到的就是默认字体
        setIncludeFontPadding(false);                 // 去掉字体自带上下留白，行距更紧凑
        // 像素字体：关抗锯齿、加粗，边缘才是硬像素块；系统字体保持正常渲染
        if (Typefaces.isPixel(getContext())) {
            getPaint().setAntiAlias(false);           // 关抗锯齿：硬边像素块的关键
            getPaint().setFakeBoldText(true);         // 伪加粗：像素字体没有粗体字重
            getPaint().setSubpixelText(false);        // 关次像素渲染，避免出现彩色描边
        }
    }
}
