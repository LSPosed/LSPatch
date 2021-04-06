package com.wind.xposed.entry;

import static com.wind.xposed.entry.MMPLoader.initAndLoadModules;

import android.app.Application;
import android.content.Context;

import com.wind.xposed.entry.util.FileUtils;
import com.wind.xposed.entry.util.ReflectionApiCheck;
import com.wind.xposed.entry.util.XLog;
import com.wind.xposed.entry.util.XpatchUtils;

import org.lsposed.lspd.yahfa.hooker.YahfaHooker;

import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedInit;

/**
 * Created by Windysha
 */
public class MMPApplication extends Application {
    private static final String ORIGINAL_APPLICATION_NAME_ASSET_PATH = "original_application_name.ini";
    private static final String TAG = "XpatchProxyApplication";
    private static String originalApplicationName = null;
    private static Application sOriginalApplication = null;
    private static ClassLoader appClassLoader;
    private static Object activityThread;

    final static public int FIRST_ISOLATED_UID = 99000;
    final static public int LAST_ISOLATED_UID = 99999;
    final static public int FIRST_APP_ZYGOTE_ISOLATED_UID = 90000;
    final static public int LAST_APP_ZYGOTE_ISOLATED_UID = 98999;
    final static public int SHARED_RELRO_UID = 1037;
    final static public int PER_USER_RANGE = 100000;

    static public boolean isIsolated() {
        int uid = android.os.Process.myUid();
        uid = uid % PER_USER_RANGE;
        return (uid >= FIRST_ISOLATED_UID && uid <= LAST_ISOLATED_UID) || (uid >= FIRST_APP_ZYGOTE_ISOLATED_UID && uid <= LAST_APP_ZYGOTE_ISOLATED_UID);
    }

    static {
        ReflectionApiCheck.unseal();

        System.loadLibrary("lspd");
        YahfaHooker.init();
        XposedInit.startsSystemServer = false;

        Context context = XpatchUtils.createAppContext();
        originalApplicationName = FileUtils.readTextFromAssets(context, ORIGINAL_APPLICATION_NAME_ASSET_PATH);
        XLog.d(TAG, "original application name " + originalApplicationName);

        if (isIsolated()) {
            XLog.d(TAG, "skip isolated process");
        }
        else {
            if (isApplicationProxied()) {
                doHook();
                initAndLoadModules(context);
            }
            else {
                XLog.e(TAG, "something wrong");
            }
        }
    }

    public MMPApplication() {
        super();

        if (isApplicationProxied()) {
            createOriginalApplication();
        }
    }

    private static boolean isApplicationProxied() {
        if (originalApplicationName != null && !originalApplicationName.isEmpty() && !("android.app.Application").equals(originalApplicationName)) {
            return true;
        }
        else {
            return false;
        }
    }

    private static ClassLoader getAppClassLoader() {
        if (appClassLoader != null) {
            return appClassLoader;
        }
        try {
            Object mBoundApplication = XposedHelpers.getObjectField(getActivityThread(), "mBoundApplication");
            Object loadedApkObj = XposedHelpers.getObjectField(mBoundApplication, "info");
            appClassLoader = (ClassLoader) XposedHelpers.callMethod(loadedApkObj, "getClassLoader");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return appClassLoader;
    }

    private static void doHook() {
        hookContextImplSetOuterContext();
        hookInstallContentProviders();
        hookActivityAttach();
        hookServiceAttach();
    }

    private static void hookContextImplSetOuterContext() {
        XposedHelpers.findAndHookMethod("android.app.ContextImpl", getAppClassLoader(), "setOuterContext", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                replaceApplicationParam(param.args);
                // XposedHelpers.setObjectField(param.thisObject, "mOuterContext", sOriginalApplication);
            }
        });
    }

    private static void hookInstallContentProviders() {
        XposedBridge.hookAllMethods(XposedHelpers.findClass("android.app.ActivityThread", getAppClassLoader()), "installContentProviders", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                replaceApplicationParam(param.args);
            }
        });
    }

    private static void hookActivityAttach() {
        XposedBridge.hookAllMethods(XposedHelpers.findClass("android.app.Activity", getAppClassLoader()), "attach", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                replaceApplicationParam(param.args);
            }
        });
    }

    private static void hookServiceAttach() {
        XposedBridge.hookAllMethods(XposedHelpers.findClass("android.app.Service", getAppClassLoader()), "attach", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                replaceApplicationParam(param.args);
            }
        });
    }

    private static void replaceApplicationParam(Object[] args) {
        if (args == null || args.length == 0) {
            return;
        }
        for (Object para : args) {
            if (para instanceof MMPApplication) {
                para = sOriginalApplication;
            }
        }
    }

    private static Object getActivityThread() {
        if (activityThread == null) {
            try {
                Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
                activityThread = XposedHelpers.callStaticMethod(activityThreadClass, "currentActivityThread");
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        return activityThread;
    }

    @Override
    protected void attachBaseContext(Context base) {

        // 将applicationInfo中保存的applcation class name还原为真实的application class name
        if (isApplicationProxied()) {
            modifyApplicationInfoClassName();
        }

        super.attachBaseContext(base);

        if (isApplicationProxied()) {
            attachOrignalBaseContext(base);
            setLoadedApkField(base);
        }

        // setApplicationLoadedApk(base);
    }

    private void attachOrignalBaseContext(Context base) {
        try {
            XposedHelpers.callMethod(sOriginalApplication, "attachBaseContext", base);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setLoadedApkField(Context base) {
        // mLoadedApk = ContextImpl.getImpl(context).mPackageInfo;
        try {
            Class<?> contextImplClass = Class.forName("android.app.ContextImpl");
            Object contextImpl = XposedHelpers.callStaticMethod(contextImplClass, "getImpl", base);
            Object loadedApk = XposedHelpers.getObjectField(contextImpl, "mPackageInfo");
            XposedHelpers.setObjectField(sOriginalApplication, "mLoadedApk", loadedApk);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCreate() {
        // setLoadedApkField(sOriginalApplication);
        // XposedHelpers.setObjectField(sOriginalApplication, "mLoadedApk", XposedHelpers.getObjectField(this, "mLoadedApk"));
        super.onCreate();

        if (isApplicationProxied()) {
            // replaceApplication();
            replaceLoadedApkApplication();
            replaceActivityThreadApplication();

            sOriginalApplication.onCreate();
        }
    }

    private void replaceLoadedApkApplication() {
        try {
            // replace   LoadedApk.java makeApplication()      mActivityThread.mAllApplications.add(app);
            ArrayList<Application> list = (ArrayList<Application>) XposedHelpers.getObjectField(getActivityThread(), "mAllApplications");
            list.add(sOriginalApplication);

            Object mBoundApplication = XposedHelpers.getObjectField(getActivityThread(), "mBoundApplication"); // AppBindData
            Object loadedApkObj = XposedHelpers.getObjectField(mBoundApplication, "info"); // info

            // replace   LoadedApk.java makeApplication()      mApplication = app;
            XposedHelpers.setObjectField(loadedApkObj, "mApplication", sOriginalApplication);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void replaceActivityThreadApplication() {
        try {
            XposedHelpers.setObjectField(getActivityThread(), "mInitialApplication", sOriginalApplication);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Application createOriginalApplication() {
        if (sOriginalApplication == null) {
            try {
                sOriginalApplication = (Application) getAppClassLoader().loadClass(originalApplicationName).newInstance();
            }
            catch (InstantiationException | ClassNotFoundException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return sOriginalApplication;
    }

    private void modifyApplicationInfoClassName() {
        try {
            Object mBoundApplication = XposedHelpers.getObjectField(getActivityThread(), "mBoundApplication"); // AppBindData
            Object applicationInfoObj = XposedHelpers.getObjectField(mBoundApplication, "appInfo"); // info

            XposedHelpers.setObjectField(applicationInfoObj, "className", originalApplicationName);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
