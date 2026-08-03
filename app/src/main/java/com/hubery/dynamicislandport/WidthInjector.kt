package com.hubery.dynamicislandport

import android.view.View
import android.view.WindowManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Fixes v17 plugin position offset.
 * Intercepts DynamicIslandWindowView via onAttachedToWindow and
 * adjusts its WindowManager.LayoutParams x position to screen center.
 */
object WidthInjector {

    fun hook(classLoader: ClassLoader) {
        XposedHelpers.findAndHookMethod(View::class.java, "onAttachedToWindow",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as View
                    if (!view.javaClass.name.contains("DynamicIslandWindowView")) return

                    try {
                        val lp = view.layoutParams as? WindowManager.LayoutParams ?: return
                        val dw = view.context.resources.displayMetrics.widthPixels
                        val cx = (dw - lp.width) / 2
                        if (lp.x < 100 && cx > 0) {
                            lp.x = cx
                            view.layoutParams = lp
                            XposedBridge.log("DynamicIslandPort: island pos fixed x=$cx")
                        }
                    } catch (e: Exception) {
                        XposedBridge.log("DynamicIslandPort: pos fix err — ${e.message}")
                    }
                }
            })

        XposedBridge.log("DynamicIslandPort: position hook installed (onAttachedToWindow)")
    }
}
