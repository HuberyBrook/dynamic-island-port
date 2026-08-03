package com.hubery.dynamicislandport

import android.os.Bundle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Injects missing island dimension signals that the phone SystemUI sends
 * but the tablet SystemUI doesn't.
 *
 * The device's island plugin (v16.5.3.14.0) reads these bundle keys:
 *   action_island_max_width  → extra_island_max_width (Float)
 *                               extra_island_clock_width (Float)
 *                               extra_island_battery_width (Float)
 *
 * These control how the plugin positions animations relative to the
 * clock and battery in the status bar.
 */
object DynamicIslandEnhancer {

    fun hook(classLoader: ClassLoader) {
        hookStart(classLoader)
    }

    /**
     * After DynamicIslandController.start(), wait briefly for the plugin
     * to load, then send the action_island_max_width bundle with clock
     * and battery widths so the plugin can position scene animations.
     */
    private fun hookStart(classLoader: ClassLoader) {
        val clz = XposedHelpers.findClass(
            "com.android.systemui.statusbar.notification.DynamicIslandController", classLoader)

        XposedHelpers.findAndHookMethod(clz, "start", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                // Post to handler so the plugin has time to load first
                try {
                    val handler = XposedHelpers.getObjectField(param.thisObject, "mMainHandler")
                        as? android.os.Handler
                    handler?.postDelayed({ sendWidthBundle(param.thisObject) }, 500)
                } catch (e: Exception) {
                    XposedBridge.log("DynamicIslandPort: width hook err — ${e.message}")
                }
            }
        })
    }

    private fun sendWidthBundle(controller: Any) {
        try {
            val pc = XposedHelpers.getObjectField(controller,
                "dynamicIslandPluginController")
            val content = XposedHelpers.callMethod(pc, "getContent") ?: return

            val ic = XposedHelpers.getObjectField(controller, "islandControllerImp")

            val clockW = try {
                XposedHelpers.callMethod(ic, "getClockWidth") as? Int
            } catch (_: Exception) { null }

            val batteryW = try {
                XposedHelpers.callMethod(ic, "getBatteryWidth") as? Int
            } catch (_: Exception) { null }

            val ctx = XposedHelpers.getObjectField(controller, "context")
                as? android.content.Context
            val res = ctx?.resources
            val maxW = res?.displayMetrics?.widthPixels?.toFloat() ?: 0f

            val bundle = Bundle().apply {
                putString("action_key", "action_island_max_width")
                putFloat("extra_island_max_width", maxW)
                putFloat("extra_island_clock_width", (clockW ?: 0).toFloat())
                putFloat("extra_island_battery_width", (batteryW ?: 0).toFloat())
            }

            XposedHelpers.callMethod(content, "handleDynamicIsland", bundle)
            XposedBridge.log("DynamicIslandPort: island width sent (max=$maxW clock=$clockW batt=$batteryW)")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: width bundle err — ${e.message}")
        }
    }
}
