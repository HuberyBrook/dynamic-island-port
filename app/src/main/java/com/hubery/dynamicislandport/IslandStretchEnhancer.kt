package com.hubery.dynamicislandport

import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.ref.WeakReference

/**
 * Enhances IslandStretchAnimation to match A16 behavior.
 *
 * Key additions over A15:
 *   - Pad (tablet) clock blur animation when island appears
 *   - Notification icon container translation on pads
 *   - Uses StatusBarDelegate to get big island width
 *
 * A15's IslandStretchAnimation already works for phone mode (translating
 * left/right containers away from the punch hole). A16 added rich pad support.
 *
 * ── Architecture ──
 * We can't add fields to existing classes in Xposed. Instead we store
 * per-instance state in a side WeakHashMap keyed by the animation object.
 * Method hooks read/write this map to simulate the A16 fields.
 */
object IslandStretchEnhancer {

    // Per-instance state simulating A16's new fields
    private val stateMap = java.util.WeakHashMap<Any, InstanceState>()

    private class InstanceState {
        var padClockView: View? = null
        var padClockViewFolme: Any? = null  // IFolme
        var padClockWidth: Int = 0
        var notificationViewTransX: Float = 0f
        var statusBarDelegate: Any? = null
        var bigIslandWidth: Int? = null
        var blurBlend: ArrayList<Any>? = null
        var notificationFolme: Any? = null
    }

    fun hook(classLoader: ClassLoader) {
        try {
            hookInitMethod(classLoader)
            hookOnIslandStatusChanged(classLoader)
            hookSetTranslationX(classLoader)
        } catch (e: Exception) {
            XposedBridge.log("IslandStretchEnhancer: ${e.message}")
        }
    }

    // ── Hook 1: initMiuiViewsOnViewCreated ─────────────────────────────
    // A15: (View, View, View, View, View, View) — 6 views
    // We hook after to also find padClockView + statusBarDelegate from the parent

    private fun hookInitMethod(classLoader: ClassLoader) {
        val animClass = findClass(classLoader,
            "com.android.systemui.statusbar.phone.IslandStretchAnimation")

        val methodName = "initMiuiViewsOnViewCreated"

        XposedHelpers.findAndHookMethod(animClass, methodName,
            View::class.java,  // clockView
            View::class.java,  // leftContainer
            View::class.java,  // rightContainer
            View::class.java,  // notificationView
            View::class.java,  // privacyArea
            View::class.java,  // extra view
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val self = param.thisObject
                    val state = stateMap.getOrPut(self) { InstanceState() }

                    // Try to find pad clock view from the clock's parent
                    val clockView = param.args[0] as? View
                    if (clockView != null) {
                        // Look for MiuiClock in A15 — it's a base View type
                        // In A16, clockView: MiuiClock with blur support
                        // Try to find padClockView in nearby hierarchy
                        try {
                            // Check if clock has a pad variant sibling
                            val parent = clockView.parent as? android.view.ViewGroup
                            if (parent != null) {
                                for (i in 0 until parent.childCount) {
                                    val child = parent.getChildAt(i)
                                    if (child.id != clockView.id &&
                                        child.javaClass.name.contains("Clock")) {
                                        state.padClockView = child
                                        state.padClockWidth = child.width
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    // Store notification view for translation
                    val notifView = param.args[3] as? View
                    if (notifView != null && state.padClockView != null) {
                        // notificationView is NotificationIconContainer in A16
                        state.padClockWidth = state.padClockView?.width ?: 0
                    }
                }
            })
    }

    // ── Hook 2: onIslandStatusChanged ──────────────────────────────────
    // A15: (boolean, boolean, boolean)V — byIslandTrigger, animate, ???
    // We add pad clock blur + notification translation after normal logic

    private fun hookOnIslandStatusChanged(classLoader: ClassLoader) {
        val animClass = findClass(classLoader,
            "com.android.systemui.statusbar.phone.IslandStretchAnimation")

        XposedHelpers.findAndHookMethod(animClass, "onIslandStatusChanged",
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val self = param.thisObject
                    val state = stateMap[self] ?: return

                    val padClock = state.padClockView ?: return
                    val islandShowing = param.args[0] as? Boolean ?: return

                    if (islandShowing) {
                        // A16 pad behavior: blur clock + scale down
                        try {
                            // Try to call MiuiClock.setBlurRadius(40f)
                            // Uses reflection since MiuiClock may be a different class
                            val blurMethod = padClock.javaClass.getDeclaredMethod(
                                "setBlurRadius", Float::class.javaPrimitiveType)
                            blurMethod.invoke(padClock, 40.0f)
                        } catch (_: Exception) {}

                        padClock.animate()
                            .scaleX(0.8f)
                            .scaleY(0.8f)
                            .alpha(0.6f)
                            .setDuration(350)
                            .start()
                    } else {
                        // Reset pad clock
                        try {
                            val blurMethod = padClock.javaClass.getDeclaredMethod(
                                "setBlurRadius", Float::class.javaPrimitiveType)
                            blurMethod.invoke(padClock, 0.0f)
                        } catch (_: Exception) {}

                        padClock.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .alpha(1.0f)
                            .setDuration(350)
                            .start()
                    }
                }
            })
    }

    // ── Hook 3: setTranslationX ────────────────────────────────────────
    // A16 adds notificationViewTransX computation for pad mode

    private fun hookSetTranslationX(classLoader: ClassLoader) {
        val animClass = findClass(classLoader,
            "com.android.systemui.statusbar.phone.IslandStretchAnimation")

        XposedHelpers.findAndHookMethod(animClass, "setTranslationX",
            Float::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val self = param.thisObject
                    val state = stateMap[self] ?: return
                    val padClock = state.padClockView ?: return
                    val x = param.args[0] as? Float ?: return

                    // If island is showing (x != 0) and pad clock exists,
                    // also translate the notification icons
                    if (x != 0f) {
                        try {
                            val notifView = XposedHelpers.getObjectField(
                                self, "notificationView") as? View
                            val padWidth = padClock.width
                            if (notifView != null && padWidth > 0) {
                                notifView.translationX = -(x * 2f)
                            }
                        } catch (_: Exception) {}
                    }
                }
            })
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private fun findClass(cl: ClassLoader, name: String): Class<*> =
        XposedHelpers.findClass(name, cl)
}
