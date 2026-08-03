package com.hubery.dynamicislandport

import android.content.Context
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Activates media animations that phone has but tablet is missing.
 * Media island uses a separate pipeline from device notifications;
 * phone activates MusicBgView with dynamic color effects.
 */
object SignalInjector {

    fun hook(cl: ClassLoader) {
        hookMediaBinder(cl)
    }

    private fun hookMediaBinder(cl: ClassLoader) {
        try {
            val binderClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.mediaisland.MiuiIslandMediaViewBinder", cl)
            val holderClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.mediaisland.MiuiIslandMediaViewHolder", cl)

            // Hook attach — phone does MusicBgView.addAdditionalView here
            XposedHelpers.findAndHookMethod(binderClass, "attach",
                holderClass, holderClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try { activateMusicBg(param.args[0], cl) }
                        catch (_: Exception) {}
                    }
                })

            XposedBridge.log("DynamicIslandPort: media hooks installed")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: media err — ${e.message}")
        }
    }

    private fun activateMusicBg(holder: Any, cl: ClassLoader) {
        val itemView = try {
            XposedHelpers.callMethod(holder, "getItemView") as? View
        } catch (_: Exception) {
            XposedHelpers.getObjectField(holder, "itemView") as? View
        } ?: return

        // Find MusicBgView — phone calls start/resume/pause on it
        val musicBg = findMusicBgView(itemView) ?: return

        try {
            val isRunning = XposedHelpers.callMethod(musicBg, "isRunning") as? Boolean
            if (isRunning != true) {
                XposedHelpers.callMethod(musicBg, "start")
                XposedBridge.log("DynamicIslandPort: MusicBgView started")
            }
        } catch (_: Exception) {}

        // Also try to find and activate any lottie views in the layout
        activateLottieChildren(itemView)
    }

    private fun activateLottieChildren(root: View) {
        if (root.javaClass.name.contains("Lottie")) {
            try {
                XposedHelpers.callMethod(root, "playAnimation")
                XposedBridge.log("DynamicIslandPort: lottie activated: ${root.javaClass.simpleName}")
            } catch (_: Exception) {}
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                activateLottieChildren(root.getChildAt(i))
            }
        }
    }

    private fun findMusicBgView(root: View): View? {
        if (root.javaClass.name.contains("MusicBgView")) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findMusicBgView(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }
}
