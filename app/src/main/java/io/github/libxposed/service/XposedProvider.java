package io.github.libxposed.service;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

/**
 * Stub — libxposed v2 API ContentProvider entry point.
 * Actual implementation is provided at runtime by LSPosed framework.
 * Our manifest registers this provider; LSPosed discovers our XposedMod
 * implementation via META-INF/services/io.github.libxposed.service.XposedMod.
 */
public class XposedProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
