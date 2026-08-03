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
 * Injects scene lottie animations into v16 plugin.
 * Gets plugin ClassLoader from onPluginLoaded callback.
 */
object PluginClassLoaderCapture {

    private const val PLUGIN_PKG = "miui.systemui.plugin"
    private var pluginCL: ClassLoader? = null
    private var pluginCtx: Context? = null

    fun hook(sysUiCL: ClassLoader) {
        try {
            val pcClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.DynamicIslandPluginController",
                sysUiCL)
            val pluginIfClass = XposedHelpers.findClass(
                "com.android.systemui.plugins.Plugin", sysUiCL)
            val lifecycleClass = XposedHelpers.findClass(
                "com.android.systemui.plugins.PluginLifecycleManager", sysUiCL)

            XposedHelpers.findAndHookMethod(pcClass, "onPluginLoaded",
                pluginIfClass, Context::class.java, lifecycleClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (pluginCL != null) return
                        val plugin = param.args[0]
                        pluginCL = plugin.javaClass.classLoader
                        pluginCtx = XposedHelpers.getObjectField(
                            param.thisObject, "context") as? Context
                        XposedBridge.log("DynamicIslandPort: plugin CL captured")
                        hookAddView()
                    }
                })

            XposedBridge.log("DynamicIslandPort: hook installed")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: err — ${e.message}")
        }
    }

    private fun hookAddView() {
        val cl = pluginCL ?: return
        val ctx = pluginCtx ?: return
        try {
            val vcClass = cl.loadClass(
                "miui.systemui.dynamicisland.window.DynamicIslandWindowViewController")
            XposedHelpers.findAndHookMethod(vcClass, "addDynamicIslandView",
                Object::class.java, Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try { onContentAdded(param.args[0], ctx, cl) }
                        catch (_: Exception) {}
                    }
                })
            XposedBridge.log("DynamicIslandPort: addView hooked")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: addView err — ${e.message}")
        }
    }

    private fun onContentAdded(data: Any, ctx: Context, cl: ClassLoader) {
        val tickerJson = XposedHelpers.getObjectField(data, "tickerData") as? String ?: return
        val json = try { org.json.JSONObject(tickerJson) } catch (_: Exception) { return }
        val big = json.optJSONObject("bigIslandArea") ?: return
        val swDigit = big.optJSONObject("sameWidthDigitInfo") ?: return
        val timer = swDigit.optJSONObject("timerInfo") ?: return
        val timerType = timer.optInt("timerType", Integer.MAX_VALUE)
        if (timerType == Integer.MAX_VALUE) return

        val resId = ctx.resources.getIdentifier("hourglass", "raw", PLUGIN_PKG)
        if (resId == 0) return

        android.os.Handler(ctx.mainLooper).post {
            try { injectLottie(resId, ctx, cl) }
            catch (_: Exception) {}
        }
    }

    private fun injectLottie(resId: Int, ctx: Context, cl: ClassLoader) {
        val islandView = findIslandView() ?: return
        if (islandView.findViewWithTag<View>("lottie_hg") != null) return

        val lottie = createLottieView(resId, ctx, cl) ?: return
        lottie.tag = "lottie_hg"

        if (islandView is FrameLayout) {
            val size = (64 * ctx.resources.displayMetrics.density + 0.5f).toInt()
            val lp = FrameLayout.LayoutParams(size, size)
            lp.gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
            islandView.addView(lottie, lp)
            XposedHelpers.callMethod(lottie, "playAnimation")
            XposedBridge.log("DynamicIslandPort: hourglass added")
        }
    }

    private fun findIslandView(): View? {
        try {
            val wmg = Class.forName("android.view.WindowManagerGlobal")
            val inst = wmg.getDeclaredMethod("getInstance").invoke(null)
            val f = wmg.getDeclaredField("mViews"); f.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val views = f.get(inst) as? List<View> ?: return null
            for (root in views) {
                findRecursive(root)?.let { return it }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun findRecursive(v: View): View? {
        if (v.javaClass.name.contains("DynamicIslandWindowView")) return v
        if (v is ViewGroup)
            for (i in 0 until v.childCount)
                findRecursive(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun createLottieView(resId: Int, ctx: Context, cl: ClassLoader): View? {
        return try {
            val lavClass = cl.loadClass("com.airbnb.lottie.LottieAnimationView")
            val ctor: Constructor<*> = lavClass.getConstructor(Context::class.java)
            val view = ctor.newInstance(ctx) as View
            XposedHelpers.callMethod(view, "setAnimation", resId)
            val infField = cl.loadClass("com.airbnb.lottie.LottieDrawable").getField("INFINITE")
            XposedHelpers.callMethod(view, "setRepeatCount", infField.getInt(null))
            val restart = cl.loadClass("com.airbnb.lottie.LottieDrawable").getField("RESTART")
            XposedHelpers.callMethod(view, "setRepeatMode", restart.getInt(null))
            view
        } catch (_: Exception) { null }
    }
}
