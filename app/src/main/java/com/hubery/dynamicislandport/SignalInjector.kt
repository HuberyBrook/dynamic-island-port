package com.hubery.dynamicislandport

import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Fixes v17 plugin position + touch region on A15 tablet.
 * Hooks DynamicIslandSizeRepository.updateIslandMaxWidth
 * to inject correct clock/battery widths from SystemUI.
 */
object SignalInjector {

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
                        XposedBridge.log("DynamicIslandPort: plugin CL ready")
                        hookSizeRepo(pcl, param.thisObject)
                    }
                })
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: err — ${e.message}")
        }
    }

    private fun hookSizeRepo(pcl: ClassLoader, pc: Any) {
        try {
            val repoClass = pcl.loadClass(
                "miui.systemui.dynamicisland.data.repository.DynamicIslandSizeRepository")

            // updateIslandMaxWidth(float maxW, float clockW, float batteryW)
            XposedHelpers.findAndHookMethod(repoClass, "updateIslandMaxWidth",
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val clockW = param.args[1] as Float
                        val batteryW = param.args[2] as Float
                        if (clockW > 0f && batteryW > 0f) return // already correct

                        // Get real widths from SystemUI
                        val sysCtx = XposedHelpers.getObjectField(pc, "context") as? Context ?: return
                        val ctrl = findIslandController(sysCtx) ?: return
                        val realClock = try {
                            (XposedHelpers.callMethod(ctrl, "getClockWidth") as? Int)?.toFloat() ?: 0f
                        } catch (_: Exception) { 0f }
                        val realBattery = try {
                            (XposedHelpers.callMethod(ctrl, "getBatteryWidth") as? Int)?.toFloat() ?: 0f
                        } catch (_: Exception) { 0f }

                        if (realClock > 0f) param.args[1] = realClock
                        if (realBattery > 0f) param.args[2] = realBattery
                        XposedBridge.log("DynamicIslandPort: width fix cw=$realClock bw=$realBattery")
                    }
                })
            XposedBridge.log("DynamicIslandPort: sizeRepo hooked")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: repo err — ${e.message}")
        }
    }

    private fun findIslandController(ctx: Context): Any? {
        return try {
            val app = ctx.applicationContext
            val component = XposedHelpers.callMethod(app, "getSystemUIComponent")
            XposedHelpers.callMethod(component, "getStatusBarIslandController")
        } catch (_: Exception) {
            // Fallback: search via Dagger
            try {
                val app = ctx.applicationContext
                val dcl = Class.forName(
                    "com.android.systemui.dagger.DaggerReferenceGlobalRootComponent\$ReferenceSysUIComponentImpl")
                // Too complex, just return null
                null
            } catch (_: Exception) { null }
        }
    }
}
