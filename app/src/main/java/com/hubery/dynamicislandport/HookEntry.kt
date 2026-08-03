package com.hubery.dynamicislandport

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

class HookEntry : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            "com.android.systemui" -> {
                XposedBridge.log("DynamicIslandPort: SystemUI loaded")
                SignalInjector.hookSystemUI(lpparam.classLoader)
            }
            "miui.systemui.plugin" -> {
                XposedBridge.log("DynamicIslandPort: Plugin loaded, CL=${lpparam.classLoader}")
                SignalInjector.hookPlugin(lpparam.classLoader)
            }
        }
    }
}
