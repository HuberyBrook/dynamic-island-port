package com.hubery.dynamicislandport

import android.content.Context
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Directly hooks the MIUISystemUIPlugin using its own ClassLoader
 * (obtained via xposedscope including miui.systemui.plugin).
 * No need to go through SystemUI's onPluginLoaded anymore.
 */
object SignalInjector {

    private var pluginCL: ClassLoader? = null

    // Called when miui.systemui.plugin package loads
    fun hookPlugin(cl: ClassLoader) {
        pluginCL = cl
        XposedBridge.log("DynamicIslandPort: plugin CL direct")
        hookAddView()
    }

    // Called when com.android.systemui loads (fallback)
    fun hookSystemUI(cl: ClassLoader) {
        // Try to capture plugin CL via onPluginLoaded as fallback
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
                        XposedBridge.log("DynamicIslandPort: plugin CL via fallback")
                        hookAddView()
                    }
                })
        } catch (_: Exception) {}
    }

    private fun hookAddView() {
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
            XposedBridge.log("DynamicIslandPort: addView hooked")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: addView err — ${e.message}")
        }
    }

    private fun onContentAdded(data: Any, vc: Any, pcl: ClassLoader) {
        val tickerJson = XposedHelpers.getObjectField(data, "tickerData") as? String ?: ""
        if (tickerJson.isEmpty()) return

        val obj = try { org.json.JSONObject(tickerJson) } catch (_: Exception) { return }
        val big = obj.optJSONObject("bigIslandArea") ?: return
        val swDigit = big.optJSONObject("sameWidthDigitInfo")
        val timerInfo = swDigit?.optJSONObject("timerInfo")
        val imgType = big.optJSONObject("imageTextInfoRight")?.optInt("type", -1) ?: -1
        if (timerInfo == null && imgType !in 1..4) return

        XposedBridge.log("DynamicIslandPort: scene detected, triggering anim")

        // Find window view, get delegate from contentViewList, trigger animation
        try {
            val wv = findWindowView(pcl) ?: return
            val list = XposedHelpers.getObjectField(wv, "contentViewList") as? List<*> ?: return
            if (list.isEmpty()) return
            val contentView = list[0] ?: return
            val delegate = XposedHelpers.callMethod(contentView, "getAnimatorDelegate") ?: return

            val cvClass = pcl.loadClass(
                "miui.systemui.dynamicisland.window.content.DynamicIslandContentView")
            val method = delegate.javaClass.getDeclaredMethod(
                "expandedToSmallIslandAnimation", cvClass)
            method.invoke(delegate, contentView)
            XposedBridge.log("DynamicIslandPort: animation triggered!")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: anim err — ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun findWindowView(pcl: ClassLoader): Any? {
        try {
            val wmg = Class.forName("android.view.WindowManagerGlobal")
            val inst = wmg.getDeclaredMethod("getInstance").invoke(null)
            val f = wmg.getDeclaredField("mViews"); f.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            for (root in f.get(inst) as? List<View> ?: emptyList()) {
                if (pcl.loadClass("miui.systemui.dynamicisland.window.DynamicIslandWindowView")
                        .isInstance(root)) return root
            }
        } catch (_: Exception) {}
        return null
    }
}
