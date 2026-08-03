package com.hubery.dynamicislandport

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Constructor

/**
 * Injects scene lottie animations into v16 plugin content views.
 *
 * Strategy: get plugin ClassLoader via createPackageContext, then
 * use pure reflection to create and manipulate plugin-side objects
 * (LottieAnimationView, etc.) to avoid ClassLoader type conflicts.
 */
object PluginClassLoaderCapture {

    private const val PLUGIN_PKG = "miui.systemui.plugin"
    private lateinit var pluginCL: ClassLoader
    private lateinit var pluginCtx: Context

    fun hook(sysUiCL: ClassLoader) {
        val appClass = XposedHelpers.findClass(
            "com.android.systemui.SystemUIApplication", sysUiCL)

        XposedHelpers.findAndHookMethod(appClass, "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        val app = param.thisObject as Context
                        pluginCtx = app.createPackageContext(PLUGIN_PKG,
                            Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY)
                        pluginCL = pluginCtx.classLoader
                        XposedBridge.log("DynamicIslandPort: plugin CL obtained")
                        hookBinder()
                    } catch (e: Exception) {
                        XposedBridge.log("DynamicIslandPort: init err — ${e.message}")
                    }
                }
            })
    }

    // ── Hook: IslandSameWidthDigitViewHolder.bind ──────────────────────

    private fun hookBinder() {
        try {
            val holderClass = pluginCL.loadClass(
                "miui.systemui.dynamicisland.module.IslandSameWidthDigitViewHolder")

            // Find bind method with SameWidthDigitInfo param
            val infoClass = pluginCL.loadClass(
                "miui.systemui.dynamicisland.model.SameWidthDigitInfo")

            for (m in holderClass.declaredMethods) {
                if (m.name != "bind") continue
                val pts = m.parameterTypes
                if (pts.size >= 1 && pts[0] == infoClass) {
                    // Hook using Object varargs to avoid type mismatch
                    XposedHelpers.findAndHookMethod(holderClass, "bind",
                        *pts,  // original param types
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                try { injectHourglass(param.args[0], param.thisObject) }
                                catch (e: Exception) {
                                    XposedBridge.log(
                                        "DynamicIslandPort: hourglass err — ${e.message}")
                                }
                            }
                        })
                    XposedBridge.log("DynamicIslandPort: timer binder hooked")
                    return
                }
            }
            XposedBridge.log("DynamicIslandPort: bind(info) not found")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: binder err — ${e.message}")
        }
    }

    // ── Hourglass injection ────────────────────────────────────────────

    private fun injectHourglass(info: Any, holder: Any) {
        // Check timerInfo presence
        val timerInfo = XposedHelpers.getObjectField(info, "timerInfo") ?: return
        val timerType = XposedHelpers.getIntField(timerInfo, "timerType")

        // Get itemView root
        val itemView = try {
            XposedHelpers.callMethod(holder, "getItemView") as? View
        } catch (_: Exception) {
            XposedHelpers.getObjectField(holder, "itemView") as? View
        } ?: return

        // Already injected?
        if (itemView.findViewWithTag<View>("lottie_hg") != null) return

        // Load lottie resource ID from plugin
        val resName = if (timerType < 0) "hourglass" else "hourglass_big"
        val resId = pluginCtx.resources.getIdentifier(resName, "raw", PLUGIN_PKG)
        if (resId == 0) {
            XposedBridge.log("DynamicIslandPort: res $resName not found")
            return
        }

        // Create LottieAnimationView via reflection (plugin ClassLoader)
        val lottieView = createLottieView(resId) ?: return
        lottieView.tag = "lottie_hg"

        // Add to parent layout
        val parent = itemView.parent as? ViewGroup ?: return
        if (parent is FrameLayout) {
            val size = (itemView.height * 1.3f).toInt().coerceAtLeast(44)
            val lp = FrameLayout.LayoutParams(size, size)
            lp.gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
            lp.marginEnd = 4.dpToPx()
            parent.addView(lottieView, lp)

            // Play
            XposedHelpers.callMethod(lottieView, "playAnimation")
            XposedBridge.log("DynamicIslandPort: hourglass added ($resName type=$timerType)")
        }
    }

    // ── Reflection helpers for plugin-side objects ─────────────────────

    private fun createLottieView(resId: Int): View? {
        return try {
            val lavClass = pluginCL.loadClass("com.airbnb.lottie.LottieAnimationView")
            val ctor: Constructor<*> = lavClass.getConstructor(Context::class.java)
            val view = ctor.newInstance(pluginCtx) as View

            // setAnimation(resId)
            XposedHelpers.callMethod(view, "setAnimation", resId)
            // setRepeatCount(INFINITE)
            val infField = pluginCL.loadClass("com.airbnb.lottie.LottieDrawable")
                .getField("INFINITE")
            val infinite = infField.getInt(null)
            XposedHelpers.callMethod(view, "setRepeatCount", infinite)
            // setRepeatMode(RESTART)
            val restartField = pluginCL.loadClass("com.airbnb.lottie.LottieDrawable")
                .getField("RESTART")
            XposedHelpers.callMethod(view, "setRepeatMode", restartField.getInt(null))

            view
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: createLottie err — ${e.message}")
            null
        }
    }

    private fun Int.dpToPx(): Int =
        (this * pluginCtx.resources.displayMetrics.density + 0.5f).toInt()
}
