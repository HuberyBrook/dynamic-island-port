package com.hubery.dynamicislandport

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Injects lottie animations for audio/video + timer scenes in v16 plugin.
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
                        XposedBridge.log("DynamicIslandPort: plugin CL captured")
                        hookAddView(sysUiCL)
                    }
                })
            XposedBridge.log("DynamicIslandPort: hooks installed")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: init err — ${e.message}")
        }
    }

    private fun hookAddView(sysUiCL: ClassLoader) {
        val pcl = pluginCL ?: return
        val ctx = pluginCtx ?: return
        try {
            val vcClass = pcl.loadClass(
                "miui.systemui.dynamicisland.window.DynamicIslandWindowViewController")

            // Load DynamicIslandData through plugin CL (delegates to SystemUI parent)
            val dataClass = pcl.loadClass(
                "com.android.systemui.plugins.miui.dynamicisland.DynamicIslandData")

            XposedHelpers.findAndHookMethod(vcClass, "addDynamicIslandView",
                dataClass, Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try { onContentAdded(param.args[0], ctx, pcl, sysUiCL) }
                        catch (_: Exception) {}
                    }
                })
            XposedBridge.log("DynamicIslandPort: addView hooked")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: addView err — ${e.message}")
        }
    }

    // ── Content detection ──────────────────────────────────────────────

    private fun onContentAdded(data: Any, ctx: Context, pcl: ClassLoader, sysUiCL: ClassLoader) {
        val key = XposedHelpers.getObjectField(data, "key") as? String ?: ""
        val json = XposedHelpers.getObjectField(data, "tickerData") as? String ?: ""
        XposedBridge.log("DynamicIslandPort: content key=$key jsonLen=${json.length}")
        if (json.isEmpty()) {
            // Music may use view directly instead of tickerData
            val view = XposedHelpers.getObjectField(data, "view")
            if (view != null) {
                XposedBridge.log("DynamicIslandPort: direct view content: ${view.javaClass.name}")
            }
            return
        }

        val obj = try { org.json.JSONObject(json) } catch (_: Exception) { return }
        XposedBridge.log("DynamicIslandPort: business=${obj.optString("business")} bigArea=${obj.has("bigIslandArea")} smallArea=${obj.has("smallIslandArea")}")
        val big = obj.optJSONObject("bigIslandArea") ?: return

        // Timer scene: sameWidthDigitInfo.timerInfo present
        val swDigit = big.optJSONObject("sameWidthDigitInfo")
        val timerInfo = swDigit?.optJSONObject("timerInfo")

        // Media scene: imageTextInfoRight.type is 1-4
        val imgRight = big.optJSONObject("imageTextInfoRight")
        val imgType = imgRight?.optInt("type", -1) ?: -1

        // Small island: smallIslandArea.imageTextInfoRight
        val small = obj.optJSONObject("smallIslandArea")
        val smallImg = small?.optJSONObject("imageTextInfoRight")
        val smallImgType = smallImg?.optInt("type", -1) ?: -1

        val resNames = mutableListOf<String>()

        if (timerInfo != null) {
            val timerType = timerInfo.optInt("timerType", Integer.MAX_VALUE)
            if (timerType != Integer.MAX_VALUE) resNames.add("hourglass")
        }
        if (imgType in 1..4 || smallImgType in 1..4) {
            // Media scene (music/video) — use voice_wave for audio,
            // charger_light_wave for general media ripple
            val business = obj.optString("business", "")
            val isVideo = business.contains("video", ignoreCase = true)
            resNames.add(if (isVideo) "charger_light_wave" else "voice_wave_big")
        }

        if (resNames.isEmpty()) {
            XposedBridge.log("DynamicIslandPort: no scene match imgType=$imgType smallImg=$smallImgType timer=${timerInfo != null}")
            return
        }

        XposedBridge.log("DynamicIslandPort: scenes=$resNames")
        android.os.Handler(ctx.mainLooper).post {
            for (name in resNames) {
                val resId = ctx.resources.getIdentifier(name, "raw", PLUGIN_PKG)
                XposedBridge.log("DynamicIslandPort: res $name -> $resId")
                if (resId == 0) continue
                try { injectLottie(resId, ctx, pcl, key + "_" + name) }
                catch (e: Exception) {
                    XposedBridge.log("DynamicIslandPort: inject fail — ${e.message}")
                }
            }
        }
    }

    // ── View injection ─────────────────────────────────────────────────

    private fun injectLottie(resId: Int, ctx: Context, cl: ClassLoader, tag: String) {
        val islandView = findIslandView()
        XposedBridge.log("DynamicIslandPort: islandView=${islandView != null} tag=$tag")
        if (islandView == null) return
        if (islandView.findViewWithTag<View>(tag) != null) return

        val lottie = createLottieView(resId, ctx, cl)
        XposedBridge.log("DynamicIslandPort: lottie created=${lottie != null}")
        if (lottie == null) return
        lottie.tag = tag

        if (islandView is FrameLayout) {
            val size = (60 * ctx.resources.displayMetrics.density + 0.5f).toInt()
            val lp = FrameLayout.LayoutParams(size, size)
            lp.gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
            islandView.addView(lottie, lp)
            XposedHelpers.callMethod(lottie, "playAnimation")
            XposedBridge.log("DynamicIslandPort: lottie $tag added")
        }
    }

    private fun findIslandView(): View? {
        try {
            val wmg = Class.forName("android.view.WindowManagerGlobal")
            val inst = wmg.getDeclaredMethod("getInstance").invoke(null)
            val f = wmg.getDeclaredField("mViews"); f.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            for (root in f.get(inst) as? List<View> ?: emptyList()) {
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
            val lav = cl.loadClass("com.airbnb.lottie.LottieAnimationView")
            val view = lav.getConstructor(Context::class.java).newInstance(ctx) as View
            XposedHelpers.callMethod(view, "setAnimation", Integer.valueOf(resId))
            // INFINITE = -1, RESTART = 2 — hardcode since LottieDrawable missing
            XposedHelpers.callMethod(view, "setRepeatCount", -1)
            XposedHelpers.callMethod(view, "setRepeatMode", 2)
            view
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: lottie err — ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }
}
