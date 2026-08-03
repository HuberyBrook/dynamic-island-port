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
 * Uses plugin ClassLoader (via createPackageContext) for all plugin-side objects.
 */
object PluginClassLoaderCapture {

    private const val PLUGIN_PKG = "miui.systemui.plugin"
    private lateinit var pluginCL: ClassLoader
    private lateinit var sysUiCL: ClassLoader
    private lateinit var pluginCtx: Context

    fun hook(sysUiLoader: ClassLoader) {
        sysUiCL = sysUiLoader
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
                        hookBind()
                    } catch (e: Exception) {
                        XposedBridge.log("DynamicIslandPort: init err — ${e.message}")
                    }
                }
            })
    }

    // ── Hook: IslandSameWidthDigitViewHolder.bind ──────────────────────

    private fun hookBind() {
        try {
            val holderClass = pluginCL.loadClass(
                "miui.systemui.dynamicisland.module.IslandSameWidthDigitViewHolder")
            val templateClass = pluginCL.loadClass(
                "miui.systemui.dynamicisland.model.IslandTemplate")
            // DynamicIslandData is in SystemUI's CL, but pluginCL can see it via parent
            val dataClass = pluginCL.loadClass(
                "com.android.systemui.plugins.miui.dynamicisland.DynamicIslandData")

            XposedHelpers.findAndHookMethod(holderClass, "bind",
                templateClass, dataClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            injectHourglass(param.args[0], param.thisObject)
                        } catch (e: Exception) {
                            XposedBridge.log("DynamicIslandPort: hourglass err — ${e.message}")
                        }
                    }
                })
            XposedBridge.log("DynamicIslandPort: timer binder hooked")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: binder err — ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    // ── Hourglass injection ────────────────────────────────────────────

    private fun injectHourglass(template: Any, holder: Any) {
        // Navigate: template.bigIslandArea.sameWidthDigitInfo.timerInfo
        val bigArea = XposedHelpers.getObjectField(template, "bigIslandArea") ?: return
        val swInfo = XposedHelpers.getObjectField(bigArea, "sameWidthDigitInfo") ?: return
        val timerInfo = XposedHelpers.getObjectField(swInfo, "timerInfo") ?: return

        val timerType = XposedHelpers.getObjectField(timerInfo, "timerType") as? Int ?: 0
        // timerType < 0 = countdown timer, use hourglass; >= 0 = stopwatch, use hourglass_big

        val itemView = try {
            XposedHelpers.callMethod(holder, "getItemView") as? View
        } catch (_: Exception) {
            XposedHelpers.getObjectField(holder, "itemView") as? View
        } ?: return

        if (itemView.findViewWithTag<View>("lottie_hg") != null) return

        val resName = "hourglass" // use the same for now
        val resId = pluginCtx.resources.getIdentifier(resName, "raw", PLUGIN_PKG)
        if (resId == 0) return

        val lottieView = createLottieView(resId) ?: return
        lottieView.tag = "lottie_hg"

        val parent = itemView.parent as? ViewGroup ?: return
        if (parent is FrameLayout) {
            val size = (itemView.height * 1.3f).toInt().coerceAtLeast(44)
            val lp = FrameLayout.LayoutParams(size, size)
            lp.gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
            lp.marginEnd = (4 * pluginCtx.resources.displayMetrics.density + 0.5f).toInt()
            parent.addView(lottieView, lp)

            XposedHelpers.callMethod(lottieView, "playAnimation")
            XposedBridge.log("DynamicIslandPort: hourglass added (type=$timerType)")
        }
    }

    // ── Reflection helpers ─────────────────────────────────────────────

    private fun createLottieView(resId: Int): View? {
        return try {
            val lavClass = pluginCL.loadClass("com.airbnb.lottie.LottieAnimationView")
            val ctor: Constructor<*> = lavClass.getConstructor(Context::class.java)
            val view = ctor.newInstance(pluginCtx) as View

            XposedHelpers.callMethod(view, "setAnimation", resId)

            val infField = pluginCL.loadClass("com.airbnb.lottie.LottieDrawable")
                .getField("INFINITE")
            XposedHelpers.callMethod(view, "setRepeatCount", infField.getInt(null))

            val restartField = pluginCL.loadClass("com.airbnb.lottie.LottieDrawable")
                .getField("RESTART")
            XposedHelpers.callMethod(view, "setRepeatMode", restartField.getInt(null))

            view
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: lottie create err — ${e.message}")
            null
        }
    }
}
