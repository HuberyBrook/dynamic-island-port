package com.hubery.dynamicislandport

import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Fixes v17 plugin position offset on A15 tablet.
 *
 * Instead of trying to inject width data through SystemUI (which crashes
 * because getContent() is inaccessible), we directly hook the plugin's
 * DynamicIslandWindowView and correct its x-position after layout.
 */
object WidthInjector {

    fun hook(classLoader: ClassLoader) {
        try {
            val windowViewClass = XposedHelpers.findClass(
                "miui.systemui.dynamicisland.window.DynamicIslandWindowView",
                classLoader)

            // After the window view is laid out, enforce correct position
            XposedHelpers.findAndHookMethod(windowViewClass, "onLayout",
                Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            fixPosition(param.thisObject as View)
                        } catch (e: Exception) {
                            XposedBridge.log("DynamicIslandPort: pos fix err — ${e.message}")
                        }
                    }
                })

            XposedBridge.log("DynamicIslandPort: position hook installed")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: WidthInjector err — ${e.message}")
        }
    }

    private fun fixPosition(view: View) {
        // Only fix if view is positioned at the far left (offset issue)
        if (view.translationX < 10f && view.x < 10f) {
            // Get status bar width and center the island
            val displayWidth = view.context.resources.displayMetrics.widthPixels.toFloat()
            val islandWidth = view.width.toFloat()
            if (islandWidth > 0) {
                // Center the island in the status bar
                val centerX = (displayWidth - islandWidth) / 2f
                view.translationX = centerX
                XposedBridge.log("DynamicIslandPort: position fixed (tx=$centerX)")
            }
        }
    }
}
