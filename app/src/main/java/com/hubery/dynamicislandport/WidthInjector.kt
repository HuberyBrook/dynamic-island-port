package com.hubery.dynamicislandport

import android.os.Bundle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Fixes v17 plugin position offset by injecting missing width data.
 *
 * v17 plugin's PadDynamicIslandAnimationDelegateHelper needs
 * extra_island_clock_width and extra_island_battery_width from
 * action_island_max_width bundle. A15 tablet SystemUI doesn't
 * send these — we inject them after setMaxIslandWidth runs.
 */
object WidthInjector {

    fun hook(classLoader: ClassLoader) {
        val clz = XposedHelpers.findClass(
            "com.android.systemui.statusbar.notification.DynamicIslandController",
            classLoader)

        XposedHelpers.findAndHookMethod(clz, "setMaxIslandWidth",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        injectWidths(param.thisObject)
                    } catch (e: Exception) {
                        XposedBridge.log("DynamicIslandPort: width err — ${e.message}")
                    }
                }
            })
    }

    private fun injectWidths(controller: Any) {
        val pc = XposedHelpers.getObjectField(controller,
            "dynamicIslandPluginController") ?: return
        val content = try {
            XposedHelpers.callMethod(pc, "getContent")
        } catch (_: Exception) { return }

        val ic = try {
            XposedHelpers.getObjectField(controller, "islandControllerImp")
        } catch (_: Exception) { null }

        val clockW = try {
            (XposedHelpers.callMethod(ic, "getClockWidth") as? Int)?.toFloat() ?: 0f
        } catch (_: Exception) { 0f }

        val batteryW = try {
            (XposedHelpers.callMethod(ic, "getBatteryWidth") as? Int)?.toFloat() ?: 0f
        } catch (_: Exception) { 0f }

        val ctx = XposedHelpers.getObjectField(controller, "context")
            as? android.content.Context
        val maxW = (ctx?.resources?.displayMetrics?.widthPixels ?: 0).toFloat()

        val bundle = Bundle().apply {
            putString("action_key", "action_island_max_width")
            putFloat("extra_island_max_width", maxW)
            putFloat("extra_island_clock_width", clockW)
            putFloat("extra_island_battery_width", batteryW)
        }

        XposedHelpers.callMethod(content, "handleDynamicIsland", bundle)
        XposedBridge.log("DynamicIslandPort: width injected (max=$maxW clock=$clockW batt=$batteryW)")
    }
}
