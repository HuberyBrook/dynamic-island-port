package de.robv.android.xposed.callbacks;

import android.content.pm.ApplicationInfo;

/**
 * Stub — provided at runtime by LSPosed framework.
 */
public final class XC_LoadPackage {

    public static final class LoadPackageParam {
        public String packageName;
        public ClassLoader classLoader;
        public ApplicationInfo appInfo;
    }
}
