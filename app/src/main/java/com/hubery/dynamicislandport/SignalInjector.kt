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

        // Post delayed — delegate isn't ready yet during addDynamicIslandView
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                val wv = findWindowView(pcl) ?: run {
                    XposedBridge.log("DynamicIslandPort: wv null")
                    return@postDelayed
                }
                val list = XposedHelpers.getObjectField(wv, "contentViewList") as? List<*> ?: return@postDelayed
                if (list.isEmpty()) return@postDelayed
                val cv = list[0] ?: return@postDelayed

                // Find and play lottie views directly
                playLottieViews(cv as? android.view.View)
                XposedBridge.log("DynamicIslandPort: lottie check done")
            } catch (e: Exception) {
                XposedBridge.log("DynamicIslandPort: err — ${e.message}")
            }
        }, 500)
        XposedBridge.log("DynamicIslandPort: delayed check scheduled")
    }

    private fun playLottieViews(view: android.view.View?) {
        if (view == null) return
        val name = view.javaClass.name
        if (name.contains("Lottie")) {
            try { XposedHelpers.callMethod(view, "playAnimation")
                XposedBridge.log("DynamicIslandPort: lottie play: $name")
            } catch (_: Exception) {}
        }
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                playLottieViews(view.getChildAt(i))
            }
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
