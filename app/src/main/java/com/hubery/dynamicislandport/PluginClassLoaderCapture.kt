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
 * Gets plugin ClassLoader via onPluginLoaded callback.
 */
object PluginClassLoaderCapture {

    private const val PLUGIN_PKG = "miui.systemui.plugin"
    private var pluginCL: ClassLoader? = null
    private var pluginCtx: Context? = null

    fun hook(sysUiCL: ClassLoader) {
        // Hook onPluginLoaded: first param is the plugin instance.
        // plugin.getClass().getClassLoader() = plugin's ClassLoader.
        try {
            val pcClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.DynamicIslandPluginController",
                sysUiCL)

            XposedHelpers.findAndHookMethod(pcClass, "onPluginLoaded",
                Object::class.java, Object::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (pluginCL != null) return
                        val plugin = param.args[0]
                        pluginCL = plugin.javaClass.classLoader
                        try {
                            pluginCtx = (param.thisObject as? Context)
                                ?.createPackageContext(PLUGIN_PKG, 0)
                        } catch (_: Exception) {}
                        XposedBridge.log("DynamicIslandPort: plugin CL captured")
                        hookAddView()
                    }
                })
            XposedBridge.log("DynamicIslandPort: onPluginLoaded hook installed")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: CL capture err — ${e.message}")
        }
    }

    // ── Hook: DynamicIslandWindowViewController.addDynamicIslandView ───

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
                        try { onContentAdded(param.args[0], ctx) }
                        catch (_: Exception) {}
                    }
                })
            XposedBridge.log("DynamicIslandPort: addView hooked")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: addView err — ${e.javaClass.simpleName}")
        }
    }

    // ── Content handler ────────────────────────────────────────────────

    private fun onContentAdded(data: Any, ctx: Context) {
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
            try { injectLottie(resId, ctx) }
            catch (_: Exception) {}
        }
    }

    private fun injectLottie(resId: Int, ctx: Context) {
        val islandView = findIslandView(ctx) ?: return
        if (islandView.findViewWithTag<View>("lottie_hg") != null) return

        val lottie = createLottieView(resId, ctx) ?: return
        lottie.tag = "lottie_hg"

        if (islandView is FrameLayout) {
            val dp = ctx.resources.displayMetrics.density
            val size = (64 * dp + 0.5f).toInt()
            val lp = FrameLayout.LayoutParams(size, size)
            lp.gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
            islandView.addView(lottie, lp)
            XposedHelpers.callMethod(lottie, "playAnimation")
            XposedBridge.log("DynamicIslandPort: hourglass added")
        }
    }

    private fun findIslandView(ctx: Context): View? {
        try {
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            // Island is in SystemUI's decor view — search from WManager roots
            val wmgClass = Class.forName("android.view.WindowManagerGlobal")
            val getInst = wmgClass.getDeclaredMethod("getInstance")
            val inst = getInst.invoke(null)
            val f = wmgClass.getDeclaredField("mViews"); f.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val views = f.get(inst) as? List<View> ?: return null
            for (root in views) {
                findIslandRecursive(root)?.let { return it }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun findIslandRecursive(v: View): View? {
        if (v.javaClass.name.contains("DynamicIslandWindowView")) return v
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                findIslandRecursive(v.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    // ── Lottie creation (plugin CL reflection) ─────────────────────────

    private fun createLottieView(resId: Int, ctx: Context): View? {
        val cl = pluginCL ?: return null
        return try {
            val lavClass = cl.loadClass("com.airbnb.lottie.LottieAnimationView")
            val ctor: Constructor<*> = lavClass.getConstructor(Context::class.java)
            val view = ctor.newInstance(ctx) as View
            XposedHelpers.callMethod(view, "setAnimation", resId)
            val infField = cl.loadClass("com.airbnb.lottie.LottieDrawable").getField("INFINITE")
            XposedHelpers.callMethod(view, "setRepeatCount", infField.getInt(null))
            val restartField = cl.loadClass("com.airbnb.lottie.LottieDrawable").getField("RESTART")
            XposedHelpers.callMethod(view, "setRepeatMode", restartField.getInt(null))
            view
        } catch (_: Exception) { null }
    }
}
