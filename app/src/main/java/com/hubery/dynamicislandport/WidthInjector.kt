package com.hubery.dynamicislandport

import android.view.View
import android.view.ViewTreeObserver
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Fixes v17 plugin position offset by continuously watching the island
 * view and correcting its position whenever it shifts to the left edge.
 */
object WidthInjector {

    fun hook(classLoader: ClassLoader) {
        XposedHelpers.findAndHookMethod(View::class.java, "onAttachedToWindow",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as View
                    if (!view.javaClass.name.contains("DynamicIslandWindowView")) return

                    XposedBridge.log("DynamicIslandPort: island found, installing watcher")
                    installPositionWatcher(view)
                }
            })

        XposedBridge.log("DynamicIslandPort: position hook installed")
    }

    private fun installPositionWatcher(view: View) {
        val watcher = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (view.translationX < 100f && view.width > 0) {
                    val dw = view.context.resources.displayMetrics.widthPixels.toFloat()
                    // Position island to the right of the clock area
                    // A16 tablet style: island sits at notification icon area
                    val targetX = dw * 0.55f - view.width / 2f
                    view.translationX = targetX
                    XposedBridge.log("DynamicIslandPort: pos -> $targetX")
                }
                return true
            }
        }
        view.viewTreeObserver.addOnPreDrawListener(watcher)
    }
}
