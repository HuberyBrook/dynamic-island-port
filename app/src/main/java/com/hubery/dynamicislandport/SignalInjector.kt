package com.hubery.dynamicislandport

import android.content.Context
import android.os.Bundle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Syncs phone-version animation flow logic to tablet SystemUI.
 *
 * Phone's onIslandViewChanged handles many callback types and
 * sends dimension data after processing. Tablet version is
 * stripped down. We intercept and add the missing signals.
 */
object SignalInjector {

    private var pluginCL: ClassLoader? = null
    private var controllerRef: java.lang.ref.WeakReference<Any>? = null

    fun hook(cl: ClassLoader) {
        hookOnIslandViewChanged(cl)
        hookPluginReady(cl)
    }

    // ── 1. Hook onIslandViewChanged to add missing callbacks ───────────

    private fun hookOnIslandViewChanged(cl: ClassLoader) {
        try {
            val ctrlClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.DynamicIslandController", cl)

            // Capture controller reference for later use
            XposedHelpers.findAndHookMethod(ctrlClass, "onIslandViewChanged",
                Bundle::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        controllerRef = java.lang.ref.WeakReference(param.thisObject)
                        // After original handling, send extra dimension data
                        try { sendExtraSignals(param.thisObject) }
                        catch (_: Exception) {}
                    }
                })

            // Also hook start() to send initial dimension data
            XposedHelpers.findAndHookMethod(ctrlClass, "start",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        controllerRef = java.lang.ref.WeakReference(param.thisObject)
                    }
                })

            // Hook setMaxIslandWidth to add clock/battery width (phone extras)
            XposedHelpers.findAndHookMethod(ctrlClass, "setMaxIslandWidth",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        controllerRef = java.lang.ref.WeakReference(param.thisObject)
                    }
                })

            XposedBridge.log("DynamicIslandPort: SystemUI hooks installed")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: sysUI hook err — ${e.message}")
        }
    }

    private fun sendExtraSignals(controller: Any) {
        val pcl = pluginCL ?: return
        val cl = controller.javaClass.classLoader

        try {
            // Get status bar island controller for clock/battery widths
            val ic = XposedHelpers.getObjectField(controller, "islandControllerImp")
            val clockW = try {
                (XposedHelpers.callMethod(ic, "getClockWidth") as? Int)?.toFloat() ?: 0f
            } catch (_: Exception) { 0f }
            val batteryW = try {
                (XposedHelpers.callMethod(ic, "getBatteryWidth") as? Int)?.toFloat() ?: 0f
            } catch (_: Exception) { 0f }

            if (clockW <= 0f && batteryW <= 0f) return

            val ctx = XposedHelpers.getObjectField(controller, "context") as? Context ?: return
            val maxW = ctx.resources.displayMetrics.widthPixels.toFloat()

            // Send action_island_max_width with phone extras
            val bundle = Bundle().apply {
                putString("action_key", "action_island_max_width")
                putFloat("extra_island_max_width", maxW)
                putFloat("extra_island_clock_width", clockW)
                putFloat("extra_island_battery_width", batteryW)
            }
            sendToPlugin(pcl, bundle)

            // Also compute and send expanded island dimensions
            val res = ctx.resources
            val panelW = res.getDimensionPixelSize(
                res.getIdentifier("notification_panel_width", "dimen", ctx.packageName))
            val panelP = res.getDimensionPixelSize(
                res.getIdentifier("notification_side_paddings", "dimen", ctx.packageName))
            val expandedW = panelW - (panelP * 2)

            val dimenBundle = Bundle().apply {
                putString("action_key", "action_update_island_dimen_data")
                putInt("expanded_island_width", expandedW)
                putInt("heads_up_status_bar_padding", 0)
            }
            sendToPlugin(pcl, dimenBundle)

            XposedBridge.log("DynamicIslandPort: signals sent (maxW=$maxW cw=$clockW bw=$batteryW expW=$expandedW)")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: signal err — ${e.message}")
        }
    }

    // ── 2. Plugin communication ────────────────────────────────────────

    private fun hookPluginReady(cl: ClassLoader) {
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
                        XposedBridge.log("DynamicIslandPort: plugin ready")
                    }
                })
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: plugin err — ${e.message}")
        }
    }

    private fun sendToPlugin(pcl: ClassLoader, bundle: Bundle) {
        try {
            val vcClass = pcl.loadClass(
                "miui.systemui.dynamicisland.window.DynamicIslandWindowViewController")
            // Get singleton via static or instance method - use reflection on the class
            // The VC is a singleton; try getting instance from known field
            val vcInstance = getVCInstance(pcl, vcClass) ?: return
            XposedHelpers.callMethod(vcInstance, "handleDynamicIsland", bundle)
        } catch (_: Exception) {}
    }

    private fun getVCInstance(pcl: ClassLoader, vcClass: Class<*>): Any? {
        // Try to get the singleton from the Dagger component or static holder
        // The VC is created by Dagger and stored in DynamicIslandWindowView
        try {
            val wmClass = pcl.loadClass("android.view.WindowManagerGlobal")
            val inst = wmClass.getDeclaredMethod("getInstance").invoke(null)
            val f = wmClass.getDeclaredField("mViews"); f.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            for (root in f.get(inst) as? List<*> ?: emptyList<Any>()) {
                if (root == null) continue
                // Find DynamicIslandWindowView, get its viewController field
                val wvClass = pcl.loadClass(
                    "miui.systemui.dynamicisland.window.DynamicIslandWindowView")
                if (wvClass.isInstance(root)) {
                    try {
                        return XposedHelpers.getObjectField(root, "viewController")
                    } catch (_: Exception) {
                        try {
                            return XposedHelpers.getObjectField(root, "windowViewController")
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
