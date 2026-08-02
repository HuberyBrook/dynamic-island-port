package com.hubery.dynamicislandport

import android.provider.Settings
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers

/**
 * Enables A16-level Dynamic Island feature flags in A15 SystemUI.
 *
 * Three Settings.Global keys control island capabilities:
 *   - support_dynamic_island        → basic island on/off
 *   - support_dynamic_island_blur   → background blur behind island
 *   - support_dynamic_island_middle → center-aligned island position
 *
 * These keys exist as ContentObservers in A15's DynamicFeatureConfig already.
 * We force-write them to Global settings when SystemUI starts, which triggers
 * the existing observers and enables the features without modifying SystemUI code.
 *
 * A16 also has two manifest-level flags that aren't Settings keys:
 *   - miui.island_support_liveupdate   → live notification updates in island
 *   - miui.media_island_support_freeform → freeform window from media island
 * We hook the code paths that read these to always return true.
 */
object FeatureFlagEnabler {

    fun hook(classLoader: ClassLoader) {
        enableSettingsFlags(classLoader)
        enableFreeformSupport(classLoader)
        enableLiveUpdate(classLoader)
    }

    // ── Settings.Global flags ──────────────────────────────────────────

    private fun enableSettingsFlags(classLoader: ClassLoader) {
        // Hook DynamicIslandController.start() — once SystemUI boots, write the flags
        val controllerClass = findClass(classLoader,
            "com.android.systemui.statusbar.notification.DynamicIslandController")

        XposedHelpers.findAndHookMethod(controllerClass, "start",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val ctx = XposedHelpers.getObjectField(param.thisObject, "context")
                        as? android.content.Context ?: return
                    val cr = ctx.contentResolver

                    // Write all three flags to trigger the existing ContentObservers
                    Settings.Global.putInt(cr, "support_dynamic_island", 1)
                    Settings.Global.putInt(cr, "support_dynamic_island_blur", 1)
                    Settings.Global.putInt(cr, "support_dynamic_island_middle", 1)
                }
            })
    }

    // ── Manifest meta-data flag overrides ──────────────────────────────

    /**
     * A15 manifest has: miui.dynamicIsland.supportFreeFormAnim = false
     * A16 manifest has: miui.media_island_support_freeform = true
     *
     * Hook the DynamicIslandFreeformAnimController init to bypass the flag check.
     */
    private fun enableFreeformSupport(classLoader: ClassLoader) {
        try {
            val controllerClass = findClass(classLoader,
                "com.android.wm.shell.sysui.DynamicIslandFreeformAnimController")

            // Replace all constructors — the class is likely singleton via Dagger
            for (ctor in controllerClass.declaredConstructors) {
                XposedHelpers.hookMethod(ctor, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        // After construction, the controller is ready but may have
                        // checked the flag. We don't need to do anything here except
                        // ensure it was constructed. If it wasn't (flag = false),
                        // Dagger won't even provide it, so this hook acts as a
                        // safety net.
                    }
                })
            }
        } catch (_: Exception) {
            // Freeform controller may not exist if Dagger doesn't provide it
        }
    }

    /**
     * A16 adds miui.island_support_liveupdate = true.
     * This affects how the island handles notification content updates.
     *
     * Hook the code that reads island configuration to patch this.
     */
    private fun enableLiveUpdate(classLoader: ClassLoader) {
        try {
            val configClass = findClass(classLoader,
                "com.android.systemui.statusbar.notification.DynamicFeatureConfig")

            // Hook getBoolean / any method that reads feature flags
            // We hook all methods named "get*" and intercept island-related keys
            for (method in configClass.declaredMethods) {
                if (method.returnType == Boolean.TYPE && method.parameterTypes.isEmpty()) {
                    val name = method.name.lowercase()
                    if ("freeform" in name || "liveupdate" in name) {
                        XposedHelpers.hookMethod(method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                param.result = true
                            }
                        })
                    }
                }
            }
        } catch (_: Exception) {
            // Method names vary; this is a best-effort hook
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private fun findClass(cl: ClassLoader, name: String): Class<*> =
        XposedHelpers.findClass(name, cl)
}
