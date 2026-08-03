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
 * Uses createPackageContext to get plugin CL, then hooks into
 * plugin's view controller to add lottie for timer/chronometer scenes.
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
                        hookAddView()
                    } catch (e: Exception) {
                        XposedBridge.log("DynamicIslandPort: init err — ${e.message}")
                    }
                }
            })
    }

    // ── Hook: DynamicIslandWindowViewController.addDynamicIslandView ───

    private fun hookAddView() {
        try {
            val vcClass = pluginCL.loadClass(
                "miui.systemui.dynamicisland.window.DynamicIslandWindowViewController")

            // addDynamicIslandView(DynamicIslandData, boolean)
            // Use Object for DynamicIslandData to avoid ClassLoader issues
            XposedHelpers.findAndHookMethod(vcClass, "addDynamicIslandView",
                Object::class.java,
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val data = param.args[0]
                            onContentAdded(data)
                        } catch (e: Exception) {
                            XposedBridge.log("DynamicIslandPort: addView err — ${e.message}")
                        }
                    }
                })

            XposedBridge.log("DynamicIslandPort: addView hooked")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: addView hook err — ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    // ── Content handler ────────────────────────────────────────────────

    private fun onContentAdded(data: Any) {
        // Get tickerData (JSON string) and key from DynamicIslandData
        val tickerJson = XposedHelpers.getObjectField(data, "tickerData") as? String ?: return
        val key = XposedHelpers.getObjectField(data, "key") as? String ?: ""

        // Parse JSON to detect scene type
        val json = try { org.json.JSONObject(tickerJson) } catch (_: Exception) { return }
        val big = json.optJSONObject("bigIslandArea") ?: return
        val swDigit = big.optJSONObject("sameWidthDigitInfo") ?: return
        val timer = swDigit.optJSONObject("timerInfo") ?: return
        val timerType = timer.optInt("timerType", Integer.MAX_VALUE)
        if (timerType == Integer.MAX_VALUE) return // not a timer

        // Find the island window view to add lottie
        val resName = "hourglass"
        val resId = pluginCtx.resources.getIdentifier(resName, "raw", PLUGIN_PKG)
        if (resId == 0) {
            XposedBridge.log("DynamicIslandPort: hourglass res not found")
            return
        }

        // Post to main thread to ensure view is laid out
        android.os.Handler(pluginCtx.mainLooper).post {
            try {
                injectLottieToWindow(resId, key)
            } catch (e: Exception) {
                XposedBridge.log("DynamicIslandPort: inject err — ${e.message}")
            }
        }
    }

    private fun injectLottieToWindow(resId: Int, key: String) {
        // Find DynamicIslandWindowView by searching decor view
        val wm = pluginCtx.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        // Actually, the island view is in SystemUI's view hierarchy, not a separate window
        // Let's search from the root view
        val roots = arrayListOf<View>()
        try {
            val wmGlobalClass = Class.forName("android.view.WindowManagerGlobal")
            val getInstance = wmGlobalClass.getDeclaredMethod("getInstance")
            val instance = getInstance.invoke(null)
            val viewsField = wmGlobalClass.getDeclaredField("mViews")
            viewsField.isAccessible = true
            val views = viewsField.get(instance) as? List<*>
            views?.forEach { v -> (v as? View)?.let { roots.add(it) } }
        } catch (_: Exception) {}

        for (root in roots) {
            val islandView = findIslandView(root) ?: continue
            if (islandView.findViewWithTag<View>("lottie_hg") != null) continue

            val lottie = createLottieView(resId) ?: continue
            lottie.tag = "lottie_hg"

            if (islandView is FrameLayout) {
                val lp = FrameLayout.LayoutParams(64.dpToPx(), 64.dpToPx())
                lp.gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
                islandView.addView(lottie, lp)
                XposedHelpers.callMethod(lottie, "playAnimation")
                XposedBridge.log("DynamicIslandPort: hourglass lottie added ($key)")
                return
            }
        }
    }

    private fun findIslandView(root: View): View? {
        if (root.javaClass.name.contains("DynamicIslandWindowView")) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findIslandView(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    // ── Lottie creation ────────────────────────────────────────────────

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
            XposedBridge.log("DynamicIslandPort: lottie err — ${e.message}")
            null
        }
    }

    private fun Int.dpToPx(): Int =
        (this * pluginCtx.resources.displayMetrics.density + 0.5f).toInt()
}
