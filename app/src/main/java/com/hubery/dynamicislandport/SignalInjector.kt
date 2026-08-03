package com.hubery.dynamicislandport

import android.content.Context
import android.os.Bundle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * When content is added to the island, sends all the signals that
 * phone SystemUI would send but tablet SystemUI doesn't.
 */
object SignalInjector {

    private var pluginCL: ClassLoader? = null
    private var vcInstance: Any? = null

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
                        if (pluginCL != null) return
                        pluginCL = (param.args[0] as Any).javaClass.classLoader
                        captureVCInstance()
                        XposedBridge.log("DynamicIslandPort: ready")
                        hookContentView()
                    }
                })
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: init err — ${e.message}")
        }
    }

    private fun captureVCInstance() {
        try {
            val pcl = pluginCL ?: return
            val wmg = Class.forName("android.view.WindowManagerGlobal")
            val inst = wmg.getDeclaredMethod("getInstance").invoke(null)
            val f = wmg.getDeclaredField("mViews"); f.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            for (root in f.get(inst) as? List<*> ?: emptyList<Any>()) {
                if (root == null) continue
                if (root.javaClass.name.contains("DynamicIslandWindowView")) {
                    // Try viewController field
                    for (field in root.javaClass.declaredFields) {
                        if (field.name.contains("iewController")) {
                            field.isAccessible = true
                            vcInstance = field.get(root)
                            return
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun hookContentView() {
        val pcl = pluginCL ?: return
        try {
            val vcClass = pcl.loadClass(
                "miui.systemui.dynamicisland.window.DynamicIslandWindowViewController")
            val dataClass = pcl.loadClass(
                "com.android.systemui.plugins.miui.dynamicisland.DynamicIslandData")

            XposedHelpers.findAndHookMethod(vcClass, "addDynamicIslandView",
                dataClass, Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try { onContentAdded(param.args[0], param.thisObject) }
                        catch (_: Exception) {}
                    }
                })
            XposedBridge.log("DynamicIslandPort: hooked")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: hook err — ${e.message}")
        }
    }

    private fun onContentAdded(data: Any, vc: Any) {
        val tickerJson = XposedHelpers.getObjectField(data, "tickerData") as? String ?: ""
        if (tickerJson.isEmpty()) return

        val obj = try { org.json.JSONObject(tickerJson) } catch (_: Exception) { return }
        val big = obj.optJSONObject("bigIslandArea") ?: return
        val swDigit = big.optJSONObject("sameWidthDigitInfo")
        val isTimer = swDigit?.optJSONObject("timerInfo") != null
        val imgType = big.optJSONObject("imageTextInfoRight")?.optInt("type", -1) ?: -1
        val isMedia = imgType in 1..4
        if (!isTimer && !isMedia) return

        val key = XposedHelpers.getObjectField(data, "key") as? String ?: ""
        XposedBridge.log("DynamicIslandPort: content key=$key timer=$isTimer media=$isMedia")

        // Send all phone-version signals to plugin
        val signals = listOf(
            Bundle().apply {
                putString("action_key", "action_island_device_notification_changed")
                putBoolean("extra_device_notification_add", true)
            },
            Bundle().apply {
                putString("action_key", "action_island_data_changed")
                putInt("extra_data_size", 1)
            },
            Bundle().apply {
                putString("action_key", "action_back_add_island")
                putString("miui.key", key)
            },
            Bundle().apply {
                putString("action_key", "action_big_island_ticker_data_changed")
                putBoolean("extra_has_big_island_ticker_data", true)
            }
        )

        for (bundle in signals) {
            try {
                XposedHelpers.callMethod(vc, "handleDynamicIsland", bundle)
            } catch (_: Exception) {}
        }
        XposedBridge.log("DynamicIslandPort: signals sent")
    }
}
