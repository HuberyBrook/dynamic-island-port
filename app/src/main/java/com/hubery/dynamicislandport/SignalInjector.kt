package com.hubery.dynamicislandport

import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object SignalInjector {

    fun hook(pluginCL: ClassLoader) {
        try {
            val vcClass = pluginCL.loadClass(
                "miui.systemui.dynamicisland.window.DynamicIslandWindowViewController")
            val dataClass = pluginCL.loadClass(
                "com.android.systemui.plugins.miui.dynamicisland.DynamicIslandData")

            XposedHelpers.findAndHookMethod(vcClass, "addDynamicIslandView",
                dataClass, Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try { onContentAdded(param.args[0], pluginCL) }
                        catch (_: Exception) {}
                    }
                })
            XposedBridge.log("DynamicIslandPort: hooked")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: err — ${e.message}")
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
        XposedBridge.log("DynamicIslandPort: scene=$scene")

        // Get animation delegate from content view list
        try {
            val wv = findWindowView(pcl) ?: return
            val list = XposedHelpers.getObjectField(wv, "contentViewList") as? List<*> ?: return
            if (list.isEmpty()) return
            val cv = list[0] ?: return
            val delegate = XposedHelpers.callMethod(cv, "getAnimatorDelegate") ?: return
            val cvClass = pcl.loadClass(
                "miui.systemui.dynamicisland.window.content.DynamicIslandContentView")
            delegate.javaClass
                .getDeclaredMethod("expandedToSmallIslandAnimation", cvClass)
                .invoke(delegate, cv)
            XposedBridge.log("DynamicIslandPort: anim triggered!")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: anim err — ${e.message}")
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
