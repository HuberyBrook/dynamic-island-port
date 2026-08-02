package com.hubery.dynamicislandport

import de.robv.android.xposed.XposedBridge
import io.github.libxposed.service.XposedMod

/**
 * LSPosed module entry — libxposed v2 API (compatible with API 101/102).
 *
 * Discovered via META-INF/services/io.github.libxposed.service.XposedMod
 * Registered via XposedProvider in AndroidManifest.
 */
class ModuleInit : XposedMod {

    override fun onPackageLoaded(param: XposedMod.LoadPackageParam) {
        if (param.packageName != COM_ANDROID_SYSTEMUI) return

        XposedBridge.log("DynamicIslandPort: hooking SystemUI...")

        try {
            FeatureFlagEnabler.hook(param.classLoader)
            IslandStretchEnhancer.hook(param.classLoader)
            DynamicIslandEnhancer.hook(param.classLoader)

            XposedBridge.log("DynamicIslandPort: all hooks applied")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: hook failed — ${e.message}")
            XposedBridge.log(e)
        }
    }

    companion object {
        private const val COM_ANDROID_SYSTEMUI = "com.android.systemui"
    }
}
