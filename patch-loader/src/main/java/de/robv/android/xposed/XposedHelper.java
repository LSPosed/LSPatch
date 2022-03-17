package de.robv.android.xposed;

import java.lang.reflect.Member;

public class XposedHelper {

    private static final String TAG = "XposedHelper";

    public static void initSeLinux(String processName) {
        // SELinuxHelper.initOnce();
        // SELinuxHelper.initForProcess(processName);
    }

    public static boolean isIXposedMod(Class<?> moduleClass) {
        return IXposedMod.class.isAssignableFrom(moduleClass);
    }


    public static XC_MethodHook.Unhook newUnHook(XC_MethodHook XC_MethodHook, Member member) {
        return XC_MethodHook.new Unhook(member);
    }

    public static void callInitZygote(String modulePath, Object moduleInstance) throws Throwable {
        IXposedHookZygoteInit.StartupParam param = new IXposedHookZygoteInit.StartupParam();
        param.modulePath = modulePath;
        param.startsSystemServer = false;
        ((IXposedHookZygoteInit) moduleInstance).initZygote(param);
    }
}
