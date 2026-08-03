package com.hubery.dynamicislandport

import android.content.Context
import android.os.Bundle
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object SignalInjector {

    private var pluginCL: ClassLoader? = null

    fun hook(sysUiCL: ClassLoader) {
        try {
            val pcClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.DynamicIslandPluginController", sysUiCL)
            XposedHelpers.findAndHookMethod(pcClass, "onPluginLoaded",
                XposedHelpers.findClass("com.android.systemui.plugins.Plugin", sysUiCL),
                Context::class.java,
                XposedHelpers.findClass("com.android.systemui.plugins.PluginLifecycleManager", sysUiCL),
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (pluginCL != null) return
                        pluginCL = (param.args[0] as Any).javaClass.classLoader
                        XposedBridge.log("DynamicIslandPort: CL ready")
                        hookAddView()
                    }
                })
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: err — ${e.message}")
        }
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
        val json = XposedHelpers.getObjectField(data, "tickerData") as? String ?: ""
        if (json.isEmpty()) return
        val obj = try { org.json.JSONObject(json) } catch (_: Exception) { return }
        val big = obj.optJSONObject("bigIslandArea") ?: return
        val swD = big.optJSONObject("sameWidthDigitInfo")
        val isTimer = swD?.optJSONObject("timerInfo") != null
        val imgT = big.optJSONObject("imageTextInfoRight")?.optInt("type", -1) ?: -1
        val isMedia = imgT in 1..4
        if (!isTimer && !isMedia) return

        XposedBridge.log("DynamicIslandPort: scene=${if(isTimer) "timer" else "media"}")

        try {
            val wv = findWindowView(pcl) ?: run {
                XposedBridge.log("DynamicIslandPort: windowView not found")
                return
            }
            val list = XposedHelpers.getObjectField(wv, "contentViewList") as? List<*> ?: run {
                XposedBridge.log("DynamicIslandPort: no contentViewList")
                return
            }
            if (list.isEmpty()) { XposedBridge.log("DynamicIslandPort: list empty"); return }
            val cv = list[0] ?: return
            XposedBridge.log("DynamicIslandPort: cv=${cv.javaClass.simpleName}")

            val delegate = XposedHelpers.callMethod(cv, "getAnimatorDelegate") ?: run {
                XposedBridge.log("DynamicIslandPort: delegate null")
                return
            }
            val cvClass = pcl.loadClass(
                "miui.systemui.dynamicisland.window.content.DynamicIslandContentView")
            delegate.javaClass
                .getDeclaredMethod("expandedToSmallIslandAnimation", cvClass)
                .invoke(delegate, cv)
            XposedBridge.log("DynamicIslandPort: anim done!")
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
            val wvClass = pcl.loadClass(
                "miui.systemui.dynamicisland.window.DynamicIslandWindowView")
            for (root in f.get(inst) as? List<View> ?: emptyList()) {
                if (wvClass.isInstance(root)) return root
            }
        } catch (_: Exception) {}
        return null
    }
}
