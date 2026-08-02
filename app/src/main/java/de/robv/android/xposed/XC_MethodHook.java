package de.robv.android.xposed;

import java.lang.reflect.Member;

/**
 * Stub — provided at runtime by LSPosed framework.
 */
public abstract class XC_MethodHook {

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}

    public static final class MethodHookParam {
        public java.lang.reflect.Member method;
        public Object thisObject;
        public Object[] args;
        public Object result;
        public Throwable throwable;
    }

    public static class Unhook {
        public Member getHookedMethod() { return null; }
        public void unhook() {}
    }
}
