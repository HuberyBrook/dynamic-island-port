package com.hubery.dynamicislandport

import android.content.Context
import android.os.Bundle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Diagnostic: logs ALL bundles sent from SystemUI to plugin.
 * This reveals exactly what signals the tablet sends vs what's missing.
 */
object SignalInjector {

    private var pluginCL: ClassLoader? = null

    fun hook(cl: ClassLoader) {
        // Hook SystemUI animation controller to see what events it sends
        try {
            val animCtrlClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.DynamicIslandWindowAnimController", cl)
            // Use the Kotlin $default wrapper: (controller, String, boolean, boolean, String, String, Boolean, int)
            XposedHelpers.findAndHookMethod(animCtrlClass, "sendWindowAnimStatusToPlugin\$default",
                animCtrlClass, String::class.java,
                Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
                String::class.java, String::class.java,
                Object::class.java, Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        XposedBridge.log("DynamicIslandPort: animEvent=${param.args[1]}")
                    }
                })
            XposedBridge.log("DynamicIslandPort: anim event hook installed")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: anim hook err — ${e.message}")
        }

        // Also hook plugin CL
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
                        val b = param.args[0] as? Bundle ?: return
                        val action = b.getString("action_key") ?: return
                        // Log all actions to see what tablet sends
                        XposedBridge.log("DynamicIslandPort: signal=$action")
                    }
                })

            // Also hook addDynamicIslandView to see content
            val dataClass = pcl.loadClass(
                "com.android.systemui.plugins.miui.dynamicisland.DynamicIslandData")
            XposedHelpers.findAndHookMethod(vcClass, "addDynamicIslandView",
                dataClass, Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val key = XposedHelpers.getObjectField(param.args[0], "key") as? String
                        XposedBridge.log("DynamicIslandPort: addView key=$key")
                    }
                })

            XposedBridge.log("DynamicIslandPort: monitoring hooks installed")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: hook err — ${e.message}")
        }
    }
}
