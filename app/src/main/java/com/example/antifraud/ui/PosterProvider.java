package com.example.antifraud.ui;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * 海报文件提供者：只把 cacheDir/share/poster.png 以只读方式交给系统分享面板。
 * Manifest 已注册：authorities = com.example.antifraud.poster，exported=false + grantUriPermissions=true。
 * 不暴露目录，也不支持增删改查。
 *
 * <p><b>为什么需要它：</b>Android 7.0 起禁止把 {@code file://} 形式的文件路径交给别的应用
 * （会抛 FileUriExposedException），必须改用 {@code content://} 并由 ContentProvider 授权。
 * 本类就是这个"授权中间人"：分享面板（微信、QQ 等）拿着 content:// 来读，
 * 本类返回一个只读文件句柄，别的应用拿不到真实路径，也改不了文件。</p>
 *
 * <p><b>安全边界（刻意做得极窄）：</b></p>
 * <ul>
 *   <li>{@code exported=false}：别的应用不能随便调用本 Provider；</li>
 *   <li>{@code grantUriPermissions=true}：只有在 Intent 里显式带上
 *       FLAG_GRANT_READ_URI_PERMISSION 的那个接收方，才临时拿到读权限；</li>
 *   <li>路径写死成 poster.png 一个文件，不接受任意路径参数，杜绝目录穿越；</li>
 *   <li>增删改查全部抛异常，只开放 openFile 一种能力。</li>
 * </ul>
 *
 * <p><b>谁在调用：</b>{@link ReviewActivity#sharePoster}（生成分享 Intent）、
 * {@link SharePoster}（约定同一个目录与文件名）。</p>
 */
public class PosterProvider extends ContentProvider {

    /**
     * Provider 创建回调。这里不需要做任何初始化：文件路径是每次 openFile 现算的，
     * 没有要提前加载的资源，所以直接返回 true 表示"创建成功"。
     *
     * @return true 表示 Provider 已就绪；返回 false 系统会认为创建失败
     */
    @Override
    public boolean onCreate() {
        return true;
    }

    /**
     * 核心方法：把海报文件以"只读文件描述符"的形式交给请求方。
     *
     * <p>注意 mode 参数被刻意忽略——不管请求方要读还是要写，一律只给只读。
     * 这是防"分享出去被别家应用改写"的关键一步。</p>
     *
     * @param uri  请求的 content:// 地址（本项目只认 poster.png 这一份，不做分支）
     * @param mode 请求的打开模式（"r"/"w"/"rw"），本类忽略，永远只读
     * @return 只读的文件描述符，系统据此把文件内容喂给接收方
     * @throws FileNotFoundException 海报还没生成时抛出，接收方会看到"打开失败"而不是空文件
     */
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        // 路径不来自 uri，而是与 SharePoster 约定好的固定位置：避免任意路径读取
        File file = new File(SharePoster.dir(getContext()), SharePoster.FILE_NAME);
        if (!file.isFile()) {
            // 用户可能还没点过"生成分享海报"，此时明确报错，让上层提示而不是发一张空图
            throw new FileNotFoundException("poster.png 还没有生成");
        }
        // 只读打开：即使请求方传了 "w" 也写不了
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /**
     * 告诉系统这个 URI 的 MIME 类型，分享面板据此选择合适的应用列表。
     *
     * @param uri 请求的 content:// 地址
     * @return 固定返回 image/png（本项目只提供 PNG 海报）
     */
    @Override
    public String getType(Uri uri) {
        return "image/png";
    }

    /**
     * 查询：不支持。海报是"一次性生成的图片"，没有列表语义，
     * 所以直接抛异常，而不是返回空 Cursor（空 Cursor 会被误认为"有表但没数据"）。
     *
     * @throws UnsupportedOperationException 本 Provider 不提供查询能力
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        throw new UnsupportedOperationException("海报只支持 openFile 读取");
    }

    /**
     * 新增：不支持。海报由 {@link SharePoster} 在应用内直接写文件，
     * 外部应用一律不许通过 Provider 塞文件进来。
     *
     * @throws UnsupportedOperationException 本 Provider 只读
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("海报只读");
    }

    /**
     * 删除：不支持。防止分享出去的应用反过来把海报删掉。
     *
     * @throws UnsupportedOperationException 本 Provider 只读
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("海报只读");
    }

    /**
     * 修改：不支持。防止分享接收方篡改海报内容。
     *
     * @throws UnsupportedOperationException 本 Provider 只读
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("海报只读");
    }
}
