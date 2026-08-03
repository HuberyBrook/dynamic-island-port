package com.hubery.dynamicislandport

import android.content.Context
import android.os.Bundle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Injects animation events into the plugin's sendWindowAnimEvent handler.
 * Tablet SystemUI doesn't send animation state events → plugin doesn't
 * play lottie. We hook the plugin's handler and call it directly
 * when content that should have animation is detected.
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
                        XposedBridge.log("DynamicIslandPort: ready")
                        hookContentView()
                    }
                })
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: init err — ${e.message}")
        }
    }

    private fun hookContentView() {
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
                        try { onContentAdded(param.args[0], param.thisObject, pcl) }
                        catch (_: Exception) {}
                    }
                })
            XposedBridge.log("DynamicIslandPort: content hook installed")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: hook err — ${e.message}")
        }
    }

    private fun onContentAdded(data: Any, vc: Any, pcl: ClassLoader) {
        val tickerJson = XposedHelpers.getObjectField(data, "tickerData") as? String ?: ""
        if (tickerJson.isEmpty()) return

        val obj = try { org.json.JSONObject(tickerJson) } catch (_: Exception) { return }
        val big = obj.optJSONObject("bigIslandArea") ?: return

        val swDigit = big.optJSONObject("sameWidthDigitInfo")
        val timerInfo = swDigit?.optJSONObject("timerInfo")
        val isTimer = timerInfo != null

        val imgRight = big.optJSONObject("imageTextInfoRight")
        val imgType = imgRight?.optInt("type", -1) ?: -1
        val isMedia = imgType in 1..4

        if (!isTimer && !isMedia) return

        val scene = if (isTimer) "timer" else "media"
        XposedBridge.log("DynamicIslandPort: scene=$scene, sending anim event")

        // Call sendWindowAnimEvent on the VC to trigger animation state change
        try {
            val bundle = Bundle()
            XposedHelpers.callMethod(vc, "sendWindowAnimEvent",
                "anim_finished", false, false, bundle)
            XposedBridge.log("DynamicIslandPort: anim event sent")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: send err — ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
