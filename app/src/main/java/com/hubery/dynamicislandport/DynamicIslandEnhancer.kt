package com.hubery.dynamicislandport

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Enhances DynamicIslandController with A16 capabilities.
 * Hooks the constructor to inject missing dependencies from the Dagger graph.
 */
object DynamicIslandEnhancer {

    fun hook(classLoader: ClassLoader) {
        try {
            hookConstructor(classLoader)
            hookStart(classLoader)
        } catch (e: Exception) {
            XposedBridge.log("DynamicIslandPort: ControllerEnhancer error — ${e.message}")
        }
    }

    private fun hookConstructor(classLoader: ClassLoader) {
        val controllerClass = XposedHelpers.findClass(
            "com.android.systemui.statusbar.notification.DynamicIslandController",
            classLoader)

        for (ctor in controllerClass.declaredConstructors) {
            try {
                XposedHelpers.findAndHookConstructor(controllerClass,
                    *ctor.parameterTypes,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val self = param.thisObject
                            val ctx = XposedHelpers.getObjectField(self, "context")
                                as? android.content.Context ?: return

                            try {
                                val app = ctx.applicationContext
                                val component = XposedHelpers.callMethod(app, "getSystemUIComponent")
                                if (component != null) {
                                    try {
                                        val delegate = XposedHelpers.callMethod(component, "getStatusBarDelegate")
                                        XposedHelpers.setObjectField(self, "statusBarDelegate", delegate)
                                    } catch (_: Exception) {}
                                    try {
                                        val hm = XposedHelpers.callMethod(component, "getHeadsUpManager")
                                        XposedHelpers.setObjectField(self, "headsUpManager", hm)
                                    } catch (_: Exception) {}
                                }
                            } catch (_: Exception) {}
                        }
                    })
            } catch (_: Exception) {}
        }
    }

    private fun hookStart(classLoader: ClassLoader) {
        val controllerClass = XposedHelpers.findClass(
            "com.android.systemui.statusbar.notification.DynamicIslandController",
            classLoader)

        XposedHelpers.findAndHookMethod(controllerClass, "start",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    try {
                        XposedHelpers.callMethod(param.thisObject, "updateIslandDimenData")
                    } catch (_: Exception) {}
                }
            })
    }
}
