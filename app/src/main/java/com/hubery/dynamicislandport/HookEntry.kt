package com.hubery.dynamicislandport

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed module entry — legacy Xposed API.
 * Compatible with all LSPosed versions including API 102 via legacy mode.
 */
class HookEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != COM_ANDROID_SYSTEMUI) return

        XposedBridge.log("DynamicIslandPort: hooking SystemUI...")

        try {
            FeatureFlagEnabler.hook(lpparam.classLoader)
            IslandStretchEnhancer.hook(lpparam.classLoader)
            DynamicIslandEnhancer.hook(lpparam.classLoader)

            XposedBridge.log("DynamicIslandPort: all hooks applied OK")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: hook error — ${e.message}")
            XposedBridge.log(e)
        }
    }

    companion object {
        private const val COM_ANDROID_SYSTEMUI = "com.android.systemui"
    }
}
