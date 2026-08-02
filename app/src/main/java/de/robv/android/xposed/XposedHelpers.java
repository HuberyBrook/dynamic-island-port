package de.robv.android.xposed;

import java.lang.reflect.Field;
import java.lang.reflect.Member;

/**
 * Stub — provided at runtime by LSPosed framework.
 */
public final class XposedHelpers {

    public static Class<?> findClass(String className, ClassLoader classLoader) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new NoClassDefFoundError(e.getMessage());
        }
    }

    public static XC_MethodHook.Unhook findAndHookMethod(
            Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        return null;
    }

    public static XC_MethodHook.Unhook hookMethod(
            Member method, XC_MethodHook callback) {
        return null;
    }

    public static Object getObjectField(Object obj, String fieldName) {
        try {
            Field f = findField(obj.getClass(), fieldName);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception e) {
            throw new NoSuchFieldError(e.getMessage());
        }
    }

    public static void setObjectField(Object obj, String fieldName, Object value) {
        try {
            Field f = findField(obj.getClass(), fieldName);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) {
            throw new NoSuchFieldError(e.getMessage());
        }
    }

    public static Object callMethod(Object obj, String methodName, Object... args) {
        return null;
    }

    private static Field findField(Class<?> clazz, String fieldName)
            throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try { return current.getDeclaredField(fieldName); }
            catch (NoSuchFieldException e) { current = current.getSuperclass(); }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
