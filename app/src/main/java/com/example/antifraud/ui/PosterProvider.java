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
 */
public class PosterProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = new File(SharePoster.dir(getContext()), SharePoster.FILE_NAME);
        if (!file.isFile()) {
            throw new FileNotFoundException("poster.png 还没有生成");
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return "image/png";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        throw new UnsupportedOperationException("海报只支持 openFile 读取");
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("海报只读");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("海报只读");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("海报只读");
    }
}
