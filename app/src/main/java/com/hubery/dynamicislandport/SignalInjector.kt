package com.hubery.dynamicislandport

import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Forces small island lottie when media content is detected.
 * The tablet SystemUI doesn't send animation state events,
 * so we directly trigger the plugin's animation delegate.
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
                        hookMediaContent()
                    }
                })
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: init err — ${e.message}")
        }
    }

    private fun hookMediaContent() {
        val pcl = pluginCL ?: return
        try {
            val vcClass = pcl.loadClass(
                "miui.systemui.dynamicisland.window.DynamicIslandWindowViewController")
            val dataClass = pcl.loadClass(
                "com.android.systemui.plugins.miui.dynamicisland.DynamicIslandData")

            XposedHelpers.findAndHookMethod(vcClass, "addDynamicIslandView",
                dataClass, Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            checkAndAnimate(param.args[0], param.thisObject, pcl)
                        } catch (_: Exception) {}
                    }
                })
            XposedBridge.log("DynamicIslandPort: media hook installed")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: media err — ${e.message}")
        }
    }

    private fun checkAndAnimate(data: Any, vc: Any, pcl: ClassLoader) {
        val tickerJson = XposedHelpers.getObjectField(data, "tickerData") as? String ?: ""
        if (tickerJson.isEmpty()) return

        val obj = try { org.json.JSONObject(tickerJson) } catch (_: Exception) { return }
        val big = obj.optJSONObject("bigIslandArea") ?: return
        val imgRight = big.optJSONObject("imageTextInfoRight")
        val imgType = imgRight?.optInt("type", -1) ?: -1
        if (imgType !in 1..4) return

        XposedBridge.log("DynamicIslandPort: media detected, triggering anim")

        try {
            // Get window view from VC
            val windowView = XposedHelpers.getObjectField(vc, "windowView")
                ?: XposedHelpers.callMethod(vc, "getWindowView")
                ?: return

            // Get content view via the animation controller
            val animCtrl = XposedHelpers.getObjectField(windowView, "animationController")
                ?: return
            val currentView = XposedHelpers.getObjectField(animCtrl, "currentExpandedView")
                ?: XposedHelpers.getObjectField(animCtrl, "currentBigIslandView")
                ?: return

            // Get delegate from content view
            val delegate = XposedHelpers.callMethod(currentView, "getAnimatorDelegate") ?: return

            // Call the small island animation
            val contentViewClass = pcl.loadClass(
                "miui.systemui.dynamicisland.window.content.DynamicIslandContentView")
            val method = delegate.javaClass.getDeclaredMethod(
                "expandedToSmallIslandAnimation", contentViewClass)
            method.invoke(delegate, currentView)
            XposedBridge.log("DynamicIslandPort: small island anim triggered!")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: anim err — ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
