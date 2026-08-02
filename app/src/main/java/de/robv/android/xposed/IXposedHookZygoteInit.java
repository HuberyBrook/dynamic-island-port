package de.robv.android.xposed;

import android.os.Bundle;

public interface IXposedHookZygoteInit {
    void initZygote(StartupParam startupParam) throws Throwable;

    class StartupParam {
        public String modulePath;
        public Bundle moduleRes;
    }
}
