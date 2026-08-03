package com.hubery.dynamicislandport

import android.os.Bundle
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Fixes v17 plugin position by finding the plugin ClassLoader through
 * a loaded SystemUI class, then hooking the plugin's animation delegate.
 */
object WidthInjector {

    private var islandViewRef: java.lang.ref.WeakReference<View>? = null

    fun hook(classLoader: ClassLoader) {
        // Track island view via onAttachedToWindow
        XposedHelpers.findAndHookMethod(View::class.java, "onAttachedToWindow",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as View
                    if (view.javaClass.name.contains("DynamicIslandWindowView")) {
                        islandViewRef = java.lang.ref.WeakReference(view)
                        XposedBridge.log("DynamicIslandPort: island view detected")
                        tryFix(view)
                    }
                }
            })

        // Also hook DynamicIslandWindowViewController via its own classloader
        try {
            hookPluginViaClassFinder(classLoader)
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: plugin hook err — ${e.message}")
        }

        XposedBridge.log("DynamicIslandPort: position hooks installed")
    }

    /**
     * Search for the plugin's ClassLoader by finding a loaded class.
     */
    private fun hookPluginViaClassFinder(sysUiCL: ClassLoader) {
        // Try the parent ClassLoaders
        var cl: ClassLoader? = sysUiCL
        while (cl != null) {
            try {
                Class.forName(
                    "miui.systemui.dynamicisland.window.DynamicIslandWindowViewController",
                    false, cl)
                // Found it! Hook the method that processes width data
                hookWidthProcessor(cl)
                return
            } catch (_: ClassNotFoundException) {}
            cl = cl.parent
        }

        // Try boot classloader
        try {
            val bootCL = ClassLoader.getSystemClassLoader()
            try {
                Class.forName(
                    "miui.systemui.dynamicisland.window.DynamicIslandWindowViewController",
                    false, bootCL)
                hookWidthProcessor(bootCL)
                return
            } catch (_: ClassNotFoundException) {}
        } catch (_: Exception) {}

        XposedBridge.log("DynamicIslandPort: plugin CL not found")
    }

    private fun hookWidthProcessor(pluginCL: ClassLoader) {
        val vcClass = Class.forName(
            "miui.systemui.dynamicisland.window.DynamicIslandWindowViewController",
            false, pluginCL)

        // Hook handleDynamicIsland to intercept action_island_max_width
        XposedHelpers.findAndHookMethod(vcClass, "handleDynamicIsland",
            Bundle::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val bundle = param.args[0] as? Bundle ?: return
                    val action = bundle.getString("action_key") ?: return
                    if (action != "action_island_max_width") return
                    val v = bundle.getFloat("extra_island_clock_width", -1f)
                    if (v > 0f) return // already has data

                    // Inject missing width data
                    bundle.putFloat("extra_island_max_width",
                        bundle.getFloat("extra_island_max_width", 2560f))
                    bundle.putFloat("extra_island_clock_width", 300f)
                    bundle.putFloat("extra_island_battery_width", 80f)
                    XposedBridge.log("DynamicIslandPort: width injected into plugin")
                }
            })

        XposedBridge.log("DynamicIslandPort: plugin width hook installed")
    }

    private fun tryFix(view: View) {
        val tx = view.translationX
        if (tx < 100f) {
            view.post { view.translationX = 300f }
        }
    }
}
