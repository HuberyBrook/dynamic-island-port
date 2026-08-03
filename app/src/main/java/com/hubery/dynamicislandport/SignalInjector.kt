package com.hubery.dynamicislandport

import android.content.Context
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Hooks the plugin's animation delegate directly to trigger animations
 * when content is added, bypassing SystemUI's missing state machine.
 */
object SignalInjector {

    private var pluginCL: ClassLoader? = null
    private var windowView: Any? = null

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
                        try { onContentAdded(param.args[0], pcl) }
                        catch (_: Exception) {}
                    }
                })
            XposedBridge.log("DynamicIslandPort: hooked")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: hook err — ${e.message}")
        }
    }

    private fun onContentAdded(data: Any, pcl: ClassLoader) {
        val tickerJson = XposedHelpers.getObjectField(data, "tickerData") as? String ?: ""
        if (tickerJson.isEmpty()) return

        val obj = try { org.json.JSONObject(tickerJson) } catch (_: Exception) { return }
        val big = obj.optJSONObject("bigIslandArea") ?: return
        val swDigit = big.optJSONObject("sameWidthDigitInfo")
        val timerInfo = swDigit?.optJSONObject("timerInfo")
        val imgType = big.optJSONObject("imageTextInfoRight")?.optInt("type", -1) ?: -1

        if (timerInfo == null && imgType !in 1..4) return

        val scene = if (timerInfo != null) "timer" else "media"
        XposedBridge.log("DynamicIslandPort: scene=$scene, triggering delegate")

        // Find window view and get delegate from content view list
        val wv = findWindowView() ?: return
        try {
            val list = XposedHelpers.getObjectField(wv, "contentViewList") as? List<*> ?: return
            if (list.isEmpty()) return

            // Get the first content view and call getAnimatorDelegate
            val contentView = list[0] ?: return
            val delegate = XposedHelpers.callMethod(contentView, "getAnimatorDelegate") ?: return

            // Try triggering the expanded→small island animation
            val method = delegate.javaClass.getDeclaredMethod(
                "expandedToSmallIslandAnimation",
                pcl.loadClass("miui.systemui.dynamicisland.window.content.DynamicIslandContentView"))
            method.invoke(delegate, contentView)
            XposedBridge.log("DynamicIslandPort: animation triggered!")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: anim err — ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun findWindowView(): Any? {
        if (windowView != null) return windowView
        try {
            val wmg = Class.forName("android.view.WindowManagerGlobal")
            val inst = wmg.getDeclaredMethod("getInstance").invoke(null)
            val f = wmg.getDeclaredField("mViews"); f.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            for (root in f.get(inst) as? List<View> ?: emptyList()) {
                if (root.javaClass.name.contains("DynamicIslandWindowView")) {
                    windowView = root
                    return root
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
