package com.hubery.dynamicislandport

import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Fixes v17 plugin position offset on A15 tablet.
 * Hooks the plugin's DynamicIslandWindowView.setTranslationX
 * to prevent the island from sticking to the far left.
 */
object WidthInjector {

    private var fixed = false

    fun hook(classLoader: ClassLoader) {
        try {
            val windowViewClass = XposedHelpers.findClass(
                "miui.systemui.dynamicisland.window.DynamicIslandWindowView", classLoader)

            XposedHelpers.findAndHookMethod(windowViewClass, "setTranslationX",
                Float::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (fixed) return
                        val view = param.thisObject as View
                        val dw = view.context.resources.displayMetrics.widthPixels.toFloat()
                        val iw = view.width.toFloat()
                        if (iw > 0 && dw > 0) {
                            val cx = (dw - iw) / 2f
                            param.args[0] = cx
                            fixed = true
                            XposedBridge.log("DynamicIslandPort: pos override $cx")
                        }
                    }
                })

            XposedBridge.log("DynamicIslandPort: position hook installed")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: WidthInjector err — ${e.message}")
        }
    }
}
