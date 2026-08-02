package com.hubery.dynamicislandport

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed module entry point.
 * Hooks into com.android.systemui (MiuiSystemUI) to backport A16's
 * Dynamic Island enhancements to A15.
 */
class HookEntry : IXposedHookLoadPackage, IXposedHookZygoteInit {

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        // No zygote-level hooks needed — SystemUI process only
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != COM_ANDROID_SYSTEMUI) return

        // Order matters: enable features before anything reads them
        FeatureFlagEnabler.hook(lpparam.classLoader)
        IslandStretchEnhancer.hook(lpparam.classLoader)
        DynamicIslandEnhancer.hook(lpparam.classLoader)
    }

    companion object {
        const val COM_ANDROID_SYSTEMUI = "com.android.systemui"
    }
}
