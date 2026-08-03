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

            // bindMediaData(MediaData, ViewHolder, ViewHolder)
            XposedHelpers.findAndHookMethod(binderClass, "bindMediaData",
                XposedHelpers.findClass(
                    "com.android.systemui.media.controls.shared.model.MediaData", cl),
                holderClass, holderClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try { activateMusicBg(param.args[1]) }
                        catch (_: Exception) {}
                        try { activateMusicBg(param.args[2]) }
                        catch (_: Exception) {}
                    }
                })
            XposedBridge.log("DynamicIslandPort: media bind hooked")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: err — ${e.message}")
        }
    }

    private fun activateMusicBg(holder: Any) {
        val bg = XposedHelpers.getObjectField(holder, "mediaBgView") ?: return
        try {
            val running = XposedHelpers.callMethod(bg, "isRunning") as? Boolean
            if (running != true) {
                XposedHelpers.callMethod(bg, "start")
                XposedBridge.log("DynamicIslandPort: MusicBgView started")
            }
        } catch (_: Exception) {}
    }
}
