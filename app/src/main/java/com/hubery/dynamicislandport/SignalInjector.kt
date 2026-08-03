package com.hubery.dynamicislandport

import android.content.Context
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Activates MusicBgView animation in tablet's media island.
 * Phone SystemUI binder calls MusicBgView.start/resume/pause;
 * tablet binder doesn't — the view is in the layout but never activated.
 */
object SignalInjector {

    private var pluginCL: ClassLoader? = null

    fun hook(cl: ClassLoader) {
        // Get plugin CL via onPluginLoaded
        try {
            val pcClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.DynamicIslandPluginController", cl)
            XposedHelpers.findAndHookMethod(pcClass, "onPluginLoaded",
                XposedHelpers.findClass("com.android.systemui.plugins.Plugin", cl),
                Context::class.java,
                XposedHelpers.findClass("com.android.systemui.plugins.PluginLifecycleManager", cl),
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (pluginCL != null) return
                        pluginCL = (param.args[0] as Any).javaClass.classLoader
                        XposedBridge.log("DynamicIslandPort: CL ready")
                        hookMediaBinder(cl)
                    }
                })
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: CL err — ${e.message}")
        }
    }

    /**
     * Hook tablet's MiuiIslandMediaViewBinder.attach to activate MusicBgView.
     */
    private fun hookMediaBinder(cl: ClassLoader) {
        try {
            val binderClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.mediaisland.MiuiIslandMediaViewBinder", cl)

            // Hook attach — called when media island view is attached
            XposedHelpers.findAndHookMethod(binderClass, "attach",
                Object::class.java,  // ViewHolder
                Object::class.java,  // ViewHolder (2nd)
                Object::class.java,  // MediaData
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try { activateMusicBg(param.args[0]) }
                        catch (_: Exception) {}
                    }
                })

            XposedBridge.log("DynamicIslandPort: media binder hooked")
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: binder err — ${e.message}")
        }
    }

    private fun activateMusicBg(holder: Any) {
        // Get itemView from ViewHolder
        val itemView = try {
            XposedHelpers.callMethod(holder, "getItemView") as? View
        } catch (_: Exception) {
            XposedHelpers.getObjectField(holder, "itemView") as? View
        } ?: return

        // Find MusicBgView in the view tree
        val musicBg = findMusicBgView(itemView) ?: return

        // Call start() if it hasn't been started yet
        try {
            val isRunning = XposedHelpers.callMethod(musicBg, "isRunning") as? Boolean
            if (isRunning != true) {
                XposedHelpers.callMethod(musicBg, "start")
                XposedBridge.log("DynamicIslandPort: MusicBgView activated")
            }
        } catch (_: Exception) {}
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
