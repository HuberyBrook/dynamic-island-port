package com.hubery.dynamicislandport

import android.content.Context
import android.os.Bundle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

object SignalInjector {

    private var clockWidth = 0f
    private var batteryWidth = 0f
    private var pluginCL: ClassLoader? = null

    fun hook(cl: ClassLoader) {
        // Hook DynamicIslandController.setMaxIslandWidth to capture widths
        try {
            val ctrlClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.DynamicIslandController", cl)
            XposedHelpers.findAndHookMethod(ctrlClass, "setMaxIslandWidth",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val ic = XposedHelpers.getObjectField(param.thisObject, "islandControllerImp")
                            clockWidth = (XposedHelpers.callMethod(ic, "getClockWidth") as? Int)?.toFloat() ?: 0f
                            batteryWidth = (XposedHelpers.callMethod(ic, "getBatteryWidth") as? Int)?.toFloat() ?: 0f
                        } catch (_: Exception) {}
                    }
                })
        } catch (_: Exception) {}

        // Hook onPluginLoaded to get plugin CL and intercept width bundles
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
                        hookPluginReceiver()
                    }
                })
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: CL err — ${e.message}")
        }
    }

    private fun hookPluginReceiver() {
        val pcl = pluginCL ?: return
        try {
            val vcClass = pcl.loadClass(
                "miui.systemui.dynamicisland.window.DynamicIslandWindowViewController")
            XposedHelpers.findAndHookMethod(vcClass, "handleDynamicIsland",
                Bundle::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val bundle = param.args[0] as? Bundle ?: return
                        if (bundle.getString("action_key") != "action_island_max_width") return
                        if (bundle.containsKey("extra_island_clock_width")) return
                        if (clockWidth <= 0f) return
                        bundle.putFloat("extra_island_clock_width", clockWidth)
                        bundle.putFloat("extra_island_battery_width", batteryWidth)
                        XposedBridge.log("DynamicIslandPort: width injected cw=$clockWidth bw=$batteryWidth")
                    }
                })
            XposedBridge.log("DynamicIslandPort: receiver hooked")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: hook err — ${e.message}")
        }
    }
}
