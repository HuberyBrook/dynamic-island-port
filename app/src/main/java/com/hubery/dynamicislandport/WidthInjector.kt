package com.hubery.dynamicislandport

import android.view.View
import dalvik.system.PathClassLoader
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Fixes v17 plugin position offset by hooking the plugin's own classes.
 * Plugin classes are in a separate ClassLoader — we use PathClassLoader
 * to load them from the APK path.
 */
object WidthInjector {

    private const val PLUGIN_PATH = "/product/app/MIUISystemUIPlugin/MIUISystemUIPlugin.apk"
    private const val WINDOW_VIEW = "miui.systemui.dynamicisland.window.DynamicIslandWindowView"

    fun hook(sysuiClassLoader: ClassLoader) {
        try {
            val pluginCL = PathClassLoader(PLUGIN_PATH, sysuiClassLoader)
            val windowViewClass = Class.forName(WINDOW_VIEW, false, pluginCL)

            XposedHelpers.findAndHookMethod(windowViewClass, "setTranslationX",
                Float::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as View
                        val tx = param.args[0] as Float
                        if (tx < 100f && view.width > 0) {
                            val dw = view.context.resources.displayMetrics.widthPixels.toFloat()
                            val cx = (dw - view.width) / 2f
                            param.args[0] = cx
                        }
                    }
                })

            XposedBridge.log("DynamicIslandPort: position hook installed via PathClassLoader")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: WidthInjector err — ${e.message}")
        }
    }
}
