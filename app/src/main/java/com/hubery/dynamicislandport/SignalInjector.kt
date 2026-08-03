package com.hubery.dynamicislandport

import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Activates MusicBgView in tablet's media island binder.
 * Phone binder calls MusicBgView.start/resume/pause;
 * tablet binder has the view but never activates it.
 */
object SignalInjector {

    fun hook(cl: ClassLoader) {
        try {
            val holderClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.mediaisland.MiuiIslandMediaViewHolder", cl)
            val binderClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.mediaisland.MiuiIslandMediaViewBinder", cl)

            // bindMediaData(MediaData) — tablet only has 1 param
            XposedHelpers.findAndHookMethod(binderClass, "bindMediaData",
                XposedHelpers.findClass(
                    "com.android.systemui.media.controls.shared.model.MediaData", cl),
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        // Search view tree for MusicBgView and activate it
                        try {
                            val wmg = Class.forName("android.view.WindowManagerGlobal")
                            val inst = wmg.getDeclaredMethod("getInstance").invoke(null)
                            val f = wmg.getDeclaredField("mViews"); f.isAccessible = true
                            @Suppress("UNCHECKED_CAST")
                            for (root in f.get(inst) as? List<android.view.View> ?: emptyList()) {
                                findAndActivateMusicBg(root)
                            }
                        } catch (_: Exception) {}
                    }
                })
            XposedBridge.log("DynamicIslandPort: media bind hooked")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: err — ${e.message}")
        }
    }

    private fun findAndActivateMusicBg(view: android.view.View) {
        if (view.javaClass.name.contains("MusicBgView")) {
            try {
                val running = XposedHelpers.callMethod(view, "isRunning") as? Boolean
                if (running != true) {
                    XposedHelpers.callMethod(view, "start")
                    XposedBridge.log("DynamicIslandPort: MusicBgView started")
                }
            } catch (_: Exception) {}
        }
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                findAndActivateMusicBg(view.getChildAt(i))
            }
        }
    }
}
