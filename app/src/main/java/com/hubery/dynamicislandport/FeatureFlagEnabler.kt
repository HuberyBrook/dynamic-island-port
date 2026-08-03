package com.hubery.dynamicislandport

import android.provider.Settings
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Force-enables A16 Dynamic Island feature flags on A15 SystemUI.
 * Writes Settings.Global keys when SystemUI starts to trigger
 * ContentObservers in DynamicFeatureConfig.
 */
object FeatureFlagEnabler {

    fun hook(classLoader: ClassLoader) {
        try {
            enableSettingsFlags(classLoader)
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: FeatureFlagEnabler error — ${e.message}")
        }
    }

    private fun enableSettingsFlags(classLoader: ClassLoader) {
        val controllerClass = XposedHelpers.findClass(
            "com.android.systemui.statusbar.notification.DynamicIslandController",
            classLoader)

        XposedHelpers.findAndHookMethod(controllerClass, "start",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val ctx = XposedHelpers.getObjectField(param.thisObject, "context")
                        as? android.content.Context ?: return
                    val cr = ctx.contentResolver

                    Settings.Global.putInt(cr, "support_dynamic_island", 1)
                    Settings.Global.putInt(cr, "support_dynamic_island_blur", 1)
                    Settings.Global.putInt(cr, "support_dynamic_island_middle", 1)

                    XposedBridge.log("DynamicIslandPort: settings flags written")
                }
            })
    }
}
