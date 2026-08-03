package com.hubery.dynamicislandport

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object SignalInjector {

    private var clockWidth = 0f
    private var batteryWidth = 0f

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
                        val pcl = (param.args[0] as Any).javaClass.classLoader
                        XposedBridge.log("DynamicIslandPort: CL ready")
                        measureWidthsFromStatusBar()
                        hookAndSend(pcl)
                    }
                })
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: err — ${e.message}")
        }
    }

    private fun hookAndSend(pcl: ClassLoader) {
        try {
            val vcClass = pcl.loadClass(
                "miui.systemui.dynamicisland.window.DynamicIslandWindowViewController")
            // Hook receiver for future bundles
            XposedHelpers.findAndHookMethod(vcClass, "handleDynamicIsland",
                Bundle::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val bundle = param.args[0] as? Bundle ?: return
                        if (bundle.getString("action_key") != "action_island_max_width") return
                        if (bundle.containsKey("extra_island_clock_width")) return
                        if (clockWidth <= 0f) measureWidthsFromStatusBar()
                        if (clockWidth <= 0f) return
                        bundle.putFloat("extra_island_clock_width", clockWidth)
                        bundle.putFloat("extra_island_battery_width", batteryWidth)
                        XposedBridge.log("DynamicIslandPort: width injected")
                    }
                })

            // Proactively send width now
            if (clockWidth > 0f) {
                sendNow(pcl, vcClass)
            } else {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    measureWidthsFromStatusBar()
                    if (clockWidth > 0f) sendNow(pcl, vcClass)
                }, 3000)
            }
            XposedBridge.log("DynamicIslandPort: ready cw=$clockWidth bw=$batteryWidth")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: hook err — ${e.message}")
        }
    }

    private fun sendNow(pcl: ClassLoader, vcClass: Class<*>) {
        try {
            val bundle = Bundle().apply {
                putString("action_key", "action_island_max_width")
                putFloat("extra_island_max_width", 2560f)
                putFloat("extra_island_clock_width", clockWidth)
                putFloat("extra_island_battery_width", batteryWidth)
            }
            val wmg = Class.forName("android.view.WindowManagerGlobal")
            val inst = wmg.getDeclaredMethod("getInstance").invoke(null)
            val f = wmg.getDeclaredField("mViews"); f.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            for (root in f.get(inst) as? List<View> ?: emptyList()) {
                val wvClass = pcl.loadClass(
                    "miui.systemui.dynamicisland.window.DynamicIslandWindowView")
                if (wvClass.isInstance(root)) {
                    val vc = XposedHelpers.getObjectField(root, "viewController")
                    XposedHelpers.callMethod(vc, "handleDynamicIsland", bundle)
                    XposedBridge.log("DynamicIslandPort: width sent")
                    break
                }
            }
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: send err — ${e.message}")
        }
    }

    private fun measureWidthsFromStatusBar() {
        if (clockWidth > 0f) return
        try {
            val wmg = Class.forName("android.view.WindowManagerGlobal")
            val inst = wmg.getDeclaredMethod("getInstance").invoke(null)
            val f = wmg.getDeclaredField("mViews"); f.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            for (root in f.get(inst) as? List<View> ?: emptyList()) {
                val name = root.javaClass.name
                if (name.contains("StatusBar") || name.contains("status_bar")) {
                    scanViews(root)
                    if (clockWidth > 0f) break
                }
            }
        } catch (_: Exception) {}
    }

    private fun scanViews(v: View) {
        val name = v.javaClass.name
        if (name.contains("MiuiClock") || name.contains("Clock")) {
            clockWidth = v.width.toFloat()
            XposedBridge.log("DynamicIslandPort: clock=${clockWidth}")
        }
        if (name.contains("Battery") && batteryWidth <= 0f) {
            batteryWidth = v.width.toFloat()
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) scanViews(v.getChildAt(i))
        }
    }
}
