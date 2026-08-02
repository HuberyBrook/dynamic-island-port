package io.github.libxposed.service;

/**
 * Stub — libxposed v2 API, provided at runtime by LSPosed.
 */
public interface XposedMod {
    default void onPackageLoaded(LoadPackageParam param) {}
    default void onSystemServerLoaded(SystemServerLoadedParam param) {}

    class LoadPackageParam {
        public String packageName;
        public ClassLoader classLoader;
        public boolean isFirstPackage;
    }

    class SystemServerLoadedParam {
        public ClassLoader classLoader;
    }
}
