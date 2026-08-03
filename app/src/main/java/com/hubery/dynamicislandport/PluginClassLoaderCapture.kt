package com.hubery.dynamicislandport

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Injects lottie animations for audio/video + timer scenes.
 * Lottie JSON files bundled in module's res/raw/ for full control.
 */
object PluginClassLoaderCapture {

    private const val PLUGIN_PKG = "miui.systemui.plugin"
    private const val MODULE_PKG = "com.hubery.dynamicislandport"
    private var pluginCL: ClassLoader? = null
    private var pluginCtx: Context? = null
    private var moduleCtx: Context? = null

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
                        pluginCL = (param.args[0] as Any).javaClass.classLoader
                        val sysCtx = XposedHelpers.getObjectField(param.thisObject, "context") as? Context
                        pluginCtx = sysCtx?.createPackageContext(PLUGIN_PKG, 0)
                        moduleCtx = sysCtx?.createPackageContext(MODULE_PKG, 0)
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
        try {
            val vcClass = pcl.loadClass(
                "miui.systemui.dynamicisland.window.DynamicIslandWindowViewController")
            val dataClass = pcl.loadClass(
                "com.android.systemui.plugins.miui.dynamicisland.DynamicIslandData")

            XposedHelpers.findAndHookMethod(vcClass, "addDynamicIslandView",
                dataClass, Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try { onContentAdded(param.args[0]) }
                        catch (_: Exception) {}
                    }
                })
            XposedBridge.log("DynamicIslandPort: addView hooked")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: addView err — ${e.message}")
        }
    }

    private fun onContentAdded(data: Any) {
        val key = XposedHelpers.getObjectField(data, "key") as? String ?: ""
        val tickerJson = XposedHelpers.getObjectField(data, "tickerData") as? String ?: ""
        if (tickerJson.isEmpty()) return

        val obj = try { org.json.JSONObject(tickerJson) } catch (_: Exception) { return }
        val big = obj.optJSONObject("bigIslandArea") ?: return

        val swDigit = big.optJSONObject("sameWidthDigitInfo")
        val timerInfo = swDigit?.optJSONObject("timerInfo")

        val imgRight = big.optJSONObject("imageTextInfoRight")
        val imgType = imgRight?.optInt("type", -1) ?: -1

        val business = obj.optString("business", "")
        val isVideo = business.contains("video", ignoreCase = true)

        // Scene → our bundled lottie resource name
        val resName = when {
            timerInfo != null -> "hourglass"  // still from plugin, or we can bundle
            imgType in 1..4 && isVideo -> "video_ripple"
            imgType in 1..4 -> "music_pulse"
            else -> return
        }

        val jsonStr = readModuleRaw(resName) ?: return
        if (jsonStr.isEmpty()) return

        android.os.Handler(pluginCtx!!.mainLooper).post {
            try { injectLottie(jsonStr, key + "_" + resName) }
            catch (_: Exception) {}
        }
    }

    private fun injectLottie(jsonStr: String, tag: String) {
        val ctx = pluginCtx ?: return
        val cl = pluginCL ?: return

        val parent = findContentParent() ?: return
        if (parent.findViewWithTag<View>(tag) != null) return

        val lottie = createLottieFromJson(jsonStr, ctx, cl) ?: return
        lottie.tag = tag

        val size = (50 * ctx.resources.displayMetrics.density + 0.5f).toInt()
        val lp = FrameLayout.LayoutParams(size, size)
        lp.gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
        lp.marginEnd = (4 * ctx.resources.displayMetrics.density + 0.5f).toInt()
        parent.addView(lottie, lp)
        XposedHelpers.callMethod(lottie, "playAnimation")
        XposedBridge.log("DynamicIslandPort: lottie $tag added")
    }

    // ── View finding ───────────────────────────────────────────────────

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

    // ── Lottie creation ────────────────────────────────────────────────

    private fun createLottieFromJson(json: String, ctx: Context, cl: ClassLoader): View? {
        return try {
            val lav = cl.loadClass("com.airbnb.lottie.LottieAnimationView")
            val view = lav.getConstructor(Context::class.java).newInstance(ctx) as View
            XposedHelpers.callMethod(view, "setAnimationFromJson", json)
            XposedHelpers.callMethod(view, "setRepeatCount", -1)
            XposedHelpers.callMethod(view, "setRepeatMode", 2)
            view
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: lottie create err — ${e.message}")
            null
        }
    }

    private fun readModuleRaw(name: String): String? {
        val ctx = moduleCtx ?: return null
        val resId = ctx.resources.getIdentifier(name, "raw", MODULE_PKG)
        if (resId == 0) return null
        return try {
            val stream = ctx.resources.openRawResource(resId)
            BufferedReader(InputStreamReader(stream)).readText()
        } catch (_: Exception) { null }
    }
}
