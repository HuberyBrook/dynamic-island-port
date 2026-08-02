package com.hubery.dynamicislandport

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Enhances DynamicIslandController with A16 capabilities.
 *
 * A15's DynamicIslandController constructor takes 13 params.
 * A16's takes 19 (adds: coroutineScope, settingsManager, statusBarDelegate,
 * headsUpManager, keyguardUpdateMonitor, overviewProxyServiceLazy).
 *
 * The extra deps already exist in SystemUI's Dagger graph — they just
 * weren't wired to DynamicIslandController in A15. We hook the constructor
 * to obtain them via reflection from the Dagger component tree.
 *
 * Changes:
 *   1. After constructor: resolve missing deps from the Dagger graph
 *   2. Hook start(): initialize pullDownExpandIsland StateFlow
 *   3. Hook hasCustomFocusView to always return true (enable custom views)
 */
object DynamicIslandEnhancer {

    fun hook(classLoader: ClassLoader) {
        try {
            hookConstructor(classLoader)
            hookStart(classLoader)
            hookCustomFocusView(classLoader)
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandEnhancer: ${e.message}")
        }
    }

    // ── Hook 1: Constructor — inject missing dependencies ──────────────

    private fun hookConstructor(classLoader: ClassLoader) {
        val controllerClass = findClass(classLoader,
            "com.android.systemui.statusbar.notification.DynamicIslandController")

        // Hook all constructors
        for (ctor in controllerClass.declaredConstructors) {
            XposedHelpers.hookMethod(ctor, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val self = param.thisObject
                    val ctx = XposedHelpers.getObjectField(self, "context")
                        as? android.content.Context ?: return

                    // Resolve StatusBarDelegate from SystemUI's global state
                    try {
                        val statusBarDelegate = resolveStatusBarDelegate(ctx)
                        if (statusBarDelegate != null) {
                            XposedHelpers.setObjectField(
                                self, "statusBarDelegate", statusBarDelegate)
                        }
                    } catch (_: Exception) {}

                    // Try to get HeadsUpManager from SystemUI
                    try {
                        val headsUpManager = resolveHeadsUpManager(ctx)
                        if (headsUpManager != null) {
                            XposedHelpers.setObjectField(
                                self, "headsUpManager", headsUpManager)
                        }
                    } catch (_: Exception) {}
                }
            })
        }
    }

    // ── Hook 2: start() — ensure StateFlows are initialized ─────────────

    private fun hookStart(classLoader: ClassLoader) {
        val controllerClass = findClass(classLoader,
            "com.android.systemui.statusbar.notification.DynamicIslandController")

        XposedHelpers.findAndHookMethod(controllerClass, "start",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    // Reload island dimens to pick up A16-style values
                    try {
                        val method = param.thisObject.javaClass.getDeclaredMethod(
                            "updateIslandDimenData")
                        method.isAccessible = true
                        method.invoke(param.thisObject)
                    } catch (_: Exception) {
                        // updateIslandDimenData may not exist in A15
                    }
                }
            })
    }

    // ── Hook 3: hasCustomFocusView — ensure rich island content works ──

    private fun hookCustomFocusView(classLoader: ClassLoader) {
        val controllerClass = findClass(classLoader,
            "com.android.systemui.statusbar.notification.DynamicIslandController")

        try {
            XposedHelpers.findAndHookMethod(controllerClass,
                "hasCustomFocusView",
                android.service.notification.StatusBarNotification::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // Force-allow custom focus views (A16 behavior)
                        param.result = true
                    }
                })
        } catch (_: Exception) {
            // Method may have different signature; skip
        }
    }

    // ── Dependency resolution ──────────────────────────────────────────

    /**
     * Walk SystemUI's Dagger component tree to find StatusBarDelegate.
     * In A15 it's provided by ReferenceSysUIComponent but not wired to
     * DynamicIslandController's constructor.
     */
    private fun resolveStatusBarDelegate(ctx: android.content.Context): Any? {
        try {
            // StatusBarDelegate is a singleton in the Dagger graph
            // Try to get it from the SystemUIApplication
            val appClass = ctx.applicationContext.javaClass
            val sysUIComponent = XposedHelpers.callMethod(
                ctx.applicationContext, "getSystemUIComponent")
            // This returns DaggerReferenceGlobalRootComponent$ReferenceSysUIComponentImpl
            if (sysUIComponent != null) {
                return try {
                    XposedHelpers.callMethod(sysUIComponent, "getStatusBarDelegate")
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun resolveHeadsUpManager(ctx: android.content.Context): Any? {
        try {
            val sysUIComponent = XposedHelpers.callMethod(
                ctx.applicationContext, "getSystemUIComponent")
            if (sysUIComponent != null) {
                return try {
                    XposedHelpers.callMethod(sysUIComponent, "getHeadsUpManager")
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {}
        return null
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private fun findClass(cl: ClassLoader, name: String): Class<*> =
        XposedHelpers.findClass(name, cl)
}
