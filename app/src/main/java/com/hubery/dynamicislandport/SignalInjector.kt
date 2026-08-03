package com.hubery.dynamicislandport

import android.content.Context
import android.os.Bundle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Injects missing width signals that phone SystemUI sends but tablet doesn't.
 *
 * Instead of sending bundles (requires getContent() which crashes),
 * we hook the PLUGIN's handleDynamicIsland to intercept the tablet's
 * incomplete action_island_max_width bundle and inject the missing
 * clock/battery width extras that the phone version would include.
 */
object SignalInjector {

    private var pluginCL: ClassLoader? = null
    private var controllerRef: java.lang.ref.WeakReference<Any>? = null

    fun hook(cl: ClassLoader) {
        // Capture controller reference from setMaxIslandWidth
        try {
            val ctrlClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.DynamicIslandController", cl)
            XposedHelpers.findAndHookMethod(ctrlClass, "setMaxIslandWidth",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        controllerRef = java.lang.ref.WeakReference(param.thisObject)
                    }
                })
        } catch (_: Exception) {}

        // Hook onPluginLoaded to get plugin CL
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
                        XposedBridge.log("DynamicIslandPort: plugin CL ready")
                        hookPluginReceiver()
                    }
                })
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: init err — ${e.message}")
        }
    }

    /**
     * Hook plugin's handleDynamicIsland to inject missing width extras.
     * Tablet sends: {action_key, extra_island_max_width}
     * Phone sends:  {action_key, extra_island_max_width, extra_island_clock_width, extra_island_battery_width}
     * We intercept and add the missing two.
     */
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
                        val action = bundle.getString("action_key") ?: return
                        if (action != "action_island_max_width") return
                        // Already has clock width → phone version sent this, skip
                        if (bundle.containsKey("extra_island_clock_width")) return

                        val ctrl = controllerRef?.get() ?: return
                        val ic = XposedHelpers.getObjectField(ctrl, "islandControllerImp") ?: return
                        val clockW = try {
                            (XposedHelpers.callMethod(ic, "getClockWidth") as? Int)?.toFloat() ?: 0f
                        } catch (_: Exception) { 0f }
                        val batteryW = try {
                            (XposedHelpers.callMethod(ic, "getBatteryWidth") as? Int)?.toFloat() ?: 0f
                        } catch (_: Exception) { 0f }

                        bundle.putFloat("extra_island_clock_width", clockW)
                        bundle.putFloat("extra_island_battery_width", batteryW)
                        XposedBridge.log("DynamicIslandPort: widths injected cw=$clockW bw=$batteryW")
                    }
                })
            XposedBridge.log("DynamicIslandPort: plugin receiver hooked")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: receiver err — ${e.message}")
        }
    }
}
