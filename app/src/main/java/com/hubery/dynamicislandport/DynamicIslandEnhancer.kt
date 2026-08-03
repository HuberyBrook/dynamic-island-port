package com.hubery.dynamicislandport

import android.os.Bundle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Adds phone-version Dynamic Island signals to trigger plugin animations.
 *
 * The island plugin (v16.5.3.14.0 on device) supports rich animations
 * (water ripple, hourglass, recorder, etc.) but needs specific bundle
 * signals that only the phone SystemUI sends. We inject those signals.
 */
object DynamicIslandEnhancer {

    fun hook(classLoader: ClassLoader) {
        hookStart(classLoader)
        hookSetMaxIslandWidth(classLoader)
        hookPluginCallback(classLoader)
    }

    // ── Signal 1: action_update_island_dimen_data ──────────────────────

    private fun hookStart(classLoader: ClassLoader) {
        val clz = XposedHelpers.findClass(
            "com.android.systemui.statusbar.notification.DynamicIslandController", classLoader)

        XposedHelpers.findAndHookMethod(clz, "start", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try { sendDimenData(param.thisObject) }
                catch (e: Exception) {
                    XposedBridge.log("DynamicIslandPort: dimen error — ${e.message}")
                }
            }
        })
    }

    // ── Signal 2: extra_island_clock/battery_width ─────────────────────

    private fun hookSetMaxIslandWidth(classLoader: ClassLoader) {
        val clz = XposedHelpers.findClass(
            "com.android.systemui.statusbar.notification.DynamicIslandController", classLoader)

        XposedHelpers.findAndHookMethod(clz, "setMaxIslandWidth", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try { sendWidthExtras(param.thisObject) }
                catch (_: Exception) {}
            }
        })
    }

    // ── Signal 3: dropDownExpandedIsland callback ──────────────────────

    private fun hookPluginCallback(classLoader: ClassLoader) {
        val clz = XposedHelpers.findClass(
            "com.android.systemui.statusbar.notification.DynamicIslandController", classLoader)

        XposedHelpers.findAndHookMethod(clz, "onDynamicPluginCallback",
            String::class.java, Bundle::class.java, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (param.args[0] as? String != "dropDownExpandedIsland") return
                    val bundle = param.args[1] as? Bundle ?: return
                    val pkg = bundle.getString("miui.pkg.name") ?: return

                    try {
                        val pc = XposedHelpers.getObjectField(
                            param.thisObject, "dynamicIslandPluginController")
                        val content = XposedHelpers.callMethod(pc, "getContent")
                        val event = Bundle().apply {
                            putString("action_key", "action_heads_up_height_changed")
                            putBoolean("is_pull_down_expand", true)
                            putString("miui.pkg.name", pkg)
                        }
                        XposedHelpers.callMethod(content, "handleDynamicIsland", event)
                    } catch (_: Exception) {}

                    param.result = Bundle().apply { putBoolean("handled", true) }
                }
            })
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private fun sendDimenData(controller: Any) {
        val ctx = XposedHelpers.getObjectField(controller, "context")
            as? android.content.Context ?: return
        val pc = XposedHelpers.getObjectField(controller,
            "dynamicIslandPluginController") ?: return
        val content = XposedHelpers.callMethod(pc, "getContent") ?: return

        val res = ctx.resources
        val pw = res.displayMetrics.widthPixels
        val sp = res.getDimensionPixelSize(
            res.getIdentifier("notification_side_paddings", "dimen", ctx.packageName))
        val ew = pw - (2 * sp)

        val bundle = Bundle().apply {
            putString("action_key", "action_update_island_dimen_data")
            putInt("expanded_island_width", ew)
            putInt("heads_up_status_bar_padding", 0)
        }
        XposedHelpers.callMethod(content, "handleDynamicIsland", bundle)
        XposedBridge.log("DynamicIslandPort: dimen data sent (w=$ew)")
    }

    private fun sendWidthExtras(controller: Any) {
        val pc = XposedHelpers.getObjectField(controller,
            "dynamicIslandPluginController") ?: return
        val content = XposedHelpers.callMethod(pc, "getContent") ?: return

        val ic = try {
            XposedHelpers.getObjectField(controller, "islandControllerImp")
        } catch (_: Exception) { null }

        val bundle = Bundle().apply {
            putString("action_key", "action_big_island_width_changed")
        }
        if (ic != null) {
            try { bundle.putInt("extra_island_clock_width",
                XposedHelpers.callMethod(ic, "getClockWidth") as? Int ?: 0) } catch (_: Exception) {}
            try { bundle.putInt("extra_island_battery_width",
                XposedHelpers.callMethod(ic, "getBatteryWidth") as? Int ?: 0) } catch (_: Exception) {}
        }
        try { XposedHelpers.callMethod(content, "handleDynamicIsland", bundle) }
        catch (_: Exception) {}
    }
}
