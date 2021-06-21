package org.lsposed.lspatch.loader;

import android.app.ActivityThread;
import android.content.Context;
import android.content.res.XResources;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedInit;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class LSPLoader {
    public static void initModules(Context context) {
        XposedInit.loadedPackagesInProcess.add(context.getPackageName());
        XResources.setPackageNameForResDir(context.getPackageName(), context.getPackageResourcePath());
        XC_LoadPackage.LoadPackageParam lpparam = new XC_LoadPackage.LoadPackageParam(
                XposedBridge.sLoadedPackageCallbacks);
        lpparam.packageName = context.getPackageName();
        lpparam.processName = ActivityThread.currentProcessName();
        lpparam.classLoader = context.getClassLoader();
        lpparam.appInfo = context.getApplicationInfo();
        lpparam.isFirstApplication = true;
        XC_LoadPackage.callAll(lpparam);
    }
}
