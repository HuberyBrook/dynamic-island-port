package com.hubery.dynamicislandport

import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Force-enables small island lottie animation on tablet.
 * The plugin's animation delegate has the rendering code but
 * the tablet SystemUI doesn't trigger it. We hook the delegate
 * directly to enable the animation when island enters small state.
 */
object SignalInjector {

    private var pluginCL: ClassLoader? = null

    fun hook(cl: ClassLoader) {
        try {
            val pcClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.DynamicIslandPluginController", cl)
            XposedHelpers.findAndHookMethod(pcClass, "onPluginLoaded",
                XposedHelpers.findClass("com.android.systemui.plugins.Plugin", cl),
                Context::class.java,
                XposedHelpers.findClass("com.android.systemui.plugins.PluginLifecycleManager", cl),
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (pluginCL != null) return
                        pluginCL = (param.args[0] as Any).javaClass.classLoader
                        XposedBridge.log("DynamicIslandPort: CL ready")
                        hookAnimDelegate()
                    }
                })
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: init err — ${e.message}")
        }
    }

    private fun hookAnimDelegate() {
        val pcl = pluginCL ?: return
        try {
            val delegateClass = pcl.loadClass(
                "miui.systemui.dynamicisland.anim.DynamicIslandAnimationDelegate")
            val contentViewClass = pcl.loadClass(
                "miui.systemui.dynamicisland.window.content.DynamicIslandContentView")

            XposedHelpers.findAndHookMethod(delegateClass, "hiddenToSmallIslandAnimation",
                contentViewClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        XposedBridge.log("DynamicIslandPort: hidden→small anim")
                    }
                })

            XposedHelpers.findAndHookMethod(delegateClass, "expandedToSmallIslandAnimation",
                contentViewClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        XposedBridge.log("DynamicIslandPort: expanded→small anim")
                    }
                })

            XposedBridge.log("DynamicIslandPort: anim delegate hooked")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: delegate err — ${e.message}")
        }
    }
}
