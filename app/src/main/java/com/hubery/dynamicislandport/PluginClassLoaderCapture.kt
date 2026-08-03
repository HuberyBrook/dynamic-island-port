package com.hubery.dynamicislandport

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Injects scene lottie animations into v16 plugin.
 * Handles scenes that SuperIslandLyric doesn't cover:
 * - Timer hourglass
 * - Charger ripple
 * - Alarm
 */
object PluginClassLoaderCapture {

    private const val PLUGIN_PKG = "miui.systemui.plugin"
    private var pluginCL: ClassLoader? = null
    private var pluginCtx: Context? = null

    fun hook(sysUiCL: ClassLoader) {
        try {
            val pcClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.DynamicIslandPluginController", sysUiCL)
            val pluginIf = XposedHelpers.findClass("com.android.systemui.plugins.Plugin", sysUiCL)
            val lifecycle = XposedHelpers.findClass(
                "com.android.systemui.plugins.PluginLifecycleManager", sysUiCL)

            XposedHelpers.findAndHookMethod(pcClass, "onPluginLoaded",
                pluginIf, Context::class.java, lifecycle,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (pluginCL != null) return
                        val plugin = param.args[0]
                        pluginCL = plugin.javaClass.classLoader
                        val sysCtx = XposedHelpers.getObjectField(param.thisObject, "context") as? Context
                        pluginCtx = sysCtx?.createPackageContext(PLUGIN_PKG, 0)
                        XposedBridge.log("DynamicIslandPort: plugin ready")
                        hookAddView()
                    }
                })
            XposedBridge.log("DynamicIslandPort: hooks installed")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: init err — ${e.message}")
        }
    }

    private fun hookAddView() {
        val pcl = pluginCL ?: return
        val ctx = pluginCtx ?: return
        try {
            val vcClass = pcl.loadClass(
                "miui.systemui.dynamicisland.window.DynamicIslandWindowViewController")
            val dataClass = pcl.loadClass(
                "com.android.systemui.plugins.miui.dynamicisland.DynamicIslandData")

            XposedHelpers.findAndHookMethod(vcClass, "addDynamicIslandView",
                dataClass, Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try { onContentAdded(param.args[0], ctx, pcl) }
                        catch (_: Exception) {}
                    }
                })
            XposedBridge.log("DynamicIslandPort: addView hooked")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: addView err — ${e.message}")
        }
    }

    private fun onContentAdded(data: Any, ctx: Context, pcl: ClassLoader) {
        val key = XposedHelpers.getObjectField(data, "key") as? String ?: ""
        val tickerJson = XposedHelpers.getObjectField(data, "tickerData") as? String ?: ""
        if (tickerJson.isEmpty()) return

        val obj = try { org.json.JSONObject(tickerJson) } catch (_: Exception) { return }
        val big = obj.optJSONObject("bigIslandArea") ?: return

        // Timer: sameWidthDigitInfo.timerInfo
        val swDigit = big.optJSONObject("sameWidthDigitInfo")
        val timerInfo = swDigit?.optJSONObject("timerInfo")
        val resName: String? = if (timerInfo != null) "hourglass" else null

        if (resName == null) return

        val resId = ctx.resources.getIdentifier(resName, "raw", PLUGIN_PKG)
        if (resId == 0) return

        android.os.Handler(ctx.mainLooper).post {
            try { injectLottie(resId, ctx, pcl, key + "_" + resName) }
            catch (_: Exception) {}
        }
    }

    private fun injectLottie(resId: Int, ctx: Context, cl: ClassLoader, tag: String) {
        val parent = findContentParent() ?: return
        if (parent.findViewWithTag<View>(tag) != null) return

        val lottie = createLottieView(resId, ctx, cl) ?: return
        lottie.tag = tag

        val size = (50 * ctx.resources.displayMetrics.density + 0.5f).toInt()
        val lp = FrameLayout.LayoutParams(size, size)
        lp.gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
        parent.addView(lottie, lp)
        XposedHelpers.callMethod(lottie, "playAnimation")
        XposedBridge.log("DynamicIslandPort: lottie $tag added")
    }

    private fun findContentParent(): ViewGroup? {
        try {
            val wmg = Class.forName("android.view.WindowManagerGlobal")
            val inst = wmg.getDeclaredMethod("getInstance").invoke(null)
            val f = wmg.getDeclaredField("mViews"); f.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            for (root in f.get(inst) as? List<View> ?: emptyList()) {
                findInTree(root)?.let { return it }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun findInTree(v: View): ViewGroup? {
        if (v.javaClass.name.contains("DynamicIslandBigIslandView")) return v as? ViewGroup
        if (v is ViewGroup)
            for (i in 0 until v.childCount)
                findInTree(v.getChildAt(i))?.let { return it }
        return null
    }

    private fun createLottieView(resId: Int, ctx: Context, cl: ClassLoader): View? {
        return try {
            val lav = cl.loadClass("com.airbnb.lottie.LottieAnimationView")
            val view = lav.getConstructor(Context::class.java).newInstance(ctx) as View
            XposedHelpers.callMethod(view, "setAnimation", Integer.valueOf(resId))
            XposedHelpers.callMethod(view, "setRepeatCount", -1)
            XposedHelpers.callMethod(view, "setRepeatMode", 2)
            view
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: lottie err — ${e.message}")
            null
        }
    }
}
