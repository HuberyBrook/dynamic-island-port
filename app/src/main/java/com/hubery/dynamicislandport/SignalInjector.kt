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

            // Hook hiddenToSmallIslandAnimation — called when island transitions to small pill
            XposedHelpers.findAndHookMethod(delegateClass, "hiddenToSmallIslandAnimation",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        XposedBridge.log("DynamicIslandPort: small island anim triggered")
                    }
                })

            // Hook expandedToSmallIslandAnimation
            XposedHelpers.findAndHookMethod(delegateClass, "expandedToSmallIslandAnimation",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        XposedBridge.log("DynamicIslandPort: expand→small anim triggered")
                    }
                })

            XposedBridge.log("DynamicIslandPort: anim delegate hooked")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: delegate err — ${e.message}")
        }
    }
}
