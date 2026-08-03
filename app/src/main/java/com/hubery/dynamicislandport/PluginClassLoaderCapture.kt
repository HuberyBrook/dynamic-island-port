package com.hubery.dynamicislandport

import android.os.Bundle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Gets the plugin's ClassLoader via createPackageContext, then hooks
 * the size repository to inject correct clock/battery widths from SystemUI.
 */
object PluginClassLoaderCapture {

    private const val PLUGIN_PKG = "miui.systemui.plugin"

    fun hook(sysUiCL: ClassLoader) {
        // Wait for SystemUI's Application to be ready, then get plugin Context
        val appClass = XposedHelpers.findClass(
            "com.android.systemui.SystemUIApplication", sysUiCL)

        XposedHelpers.findAndHookMethod(appClass, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val app = param.thisObject as android.content.Context
                        val pluginCtx = app.createPackageContext(
                            PLUGIN_PKG,
                            android.content.Context.CONTEXT_INCLUDE_CODE or
                            android.content.Context.CONTEXT_IGNORE_SECURITY)
                        val pluginCL = pluginCtx.classLoader

                        XposedBridge.log("DynamicIslandPort: plugin CL obtained")
                        hookSizeRepo(pluginCL, app)
                    } catch (e: Exception) {
                        XposedBridge.log("DynamicIslandPort: plugin ctx err — ${e.message}")
                    }
                }
            })
    }

    private fun hookSizeRepo(pluginCL: ClassLoader, sysUiCtx: android.content.Context) {
        try {
            val repoClass = Class.forName(
                "miui.systemui.dynamicisland.data.repository.DynamicIslandSizeRepository",
                false, pluginCL)

            // Hook updateIslandMaxWidth(float maxW, float clockW, float batteryW)
            XposedHelpers.findAndHookMethod(repoClass, "updateIslandMaxWidth",
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val maxW = param.args[0] as Float
                        val clockW = param.args[1] as Float
                        val batteryW = param.args[2] as Float

                        // If widths are defaults (-1 or 0), inject correct values
                        if (clockW <= 0f || batteryW <= 0f) {
                            // Get systemUI's island controller for real widths
                            val sic = try {
                                XposedHelpers.callMethod(
                                    sysUiCtx.getSystemService("statusbar"),
                                    "getStatusBarIslandController")
                            } catch (_: Exception) { null }

                            val realClock = try {
                                XposedHelpers.callMethod(sic, "getClockWidth") as? Int
                            } catch (_: Exception) { null } ?: 0

                            val realBattery = try {
                                XposedHelpers.callMethod(sic, "getBatteryWidth") as? Int
                            } catch (_: Exception) { null } ?: 0

                            if (realClock > 0) param.args[1] = realClock.toFloat()
                            if (realBattery > 0) param.args[2] = realBattery.toFloat()

                            XposedBridge.log(
                                "DynamicIslandPort: size injected cw=$realClock bw=$realBattery")
                        }
                    }
                })

            XposedBridge.log("DynamicIslandPort: size repo hooked")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: size repo err — ${e.message}")
        }
    }
}
