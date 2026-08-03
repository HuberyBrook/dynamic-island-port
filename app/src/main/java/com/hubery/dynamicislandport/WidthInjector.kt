package com.hubery.dynamicislandport

import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Fixes v17 plugin position offset.
 * Finds the island window via onAttachedToWindow, reads the correct
 * translationX from SystemUI's StatusBarIslandControllerImpl, and applies it.
 */
object WidthInjector {

    fun hook(classLoader: ClassLoader) {
        try {
            val sicClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.StatusBarIslandControllerImpl", classLoader)

            XposedHelpers.findAndHookMethod(View::class.java, "onAttachedToWindow",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as View
                        if (!view.javaClass.name.contains("DynamicIslandWindowView")) return

                        try {
                            // Get StatusBarIslandControllerImpl singleton from Dagger graph
                            val ctx = view.context
                            val app = ctx.applicationContext
                            val component = XposedHelpers.callMethod(app, "getSystemUIComponent")
                            val sic = XposedHelpers.callMethod(component, "getStatusBarIslandController")

                            val tx = XposedHelpers.callMethod(sic, "getTranslationX") as? Float ?: 0f
                            if (tx > 0f) {
                                view.translationX = tx
                                XposedBridge.log("DynamicIslandPort: pos set to $tx")
                            }
                        } catch (e: Exception) {
                            XposedBridge.log("DynamicIslandPort: pos err — ${e.message}")
                        }
                    }
                })

            XposedBridge.log("DynamicIslandPort: position hook installed")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: WidthInjector err — ${e.message}")
        }
    }
}
