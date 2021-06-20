package org.lsposed.lspatch.loader;

import static android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE;
import static org.lsposed.lspatch.loader.LSPLoader.initAndLoadModules;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

import org.lsposed.lspatch.loader.util.FileUtils;
import org.lsposed.lspatch.loader.util.XLog;
import org.lsposed.lspatch.loader.util.XpatchUtils;
import org.lsposed.lspatch.share.Constants;
import org.lsposed.lspd.deopt.PrebuiltMethodsDeopter;
import org.lsposed.lspd.nativebridge.SigBypass;
import org.lsposed.lspd.yahfa.hooker.YahfaHooker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedInit;

/**
 * Created by Windysha
 */
@SuppressLint("UnsafeDynamicallyLoadedCode")
public class LSPApplication {
    private static final String ORIGINAL_APPLICATION_NAME_ASSET_PATH = "original_application_name.ini";
    private static final String ORIGINAL_SIGNATURE_ASSET_PATH = "original_signature_info.ini";
    private static final String TAG = "LSPatch";

    private static String originalApplicationName = null;
    private static String originalSignature = null;
    private static Application sOriginalApplication = null;
    private static ClassLoader appClassLoader;
    private static Object activityThread;

    private static int TRANSACTION_getPackageInfo_ID = -1;

    final static public int FIRST_APP_ZYGOTE_ISOLATED_UID = 90000;
    final static public int PER_USER_RANGE = 100000;

    final static private LSPApplication instance = new LSPApplication();

    static public boolean isIsolated() {
        return (android.os.Process.myUid() % PER_USER_RANGE) >= FIRST_APP_ZYGOTE_ISOLATED_UID;
    }

    static public void onLoad() {
        cacheSigbypassLv = -1;

        if (isIsolated()) {
            XLog.d(TAG, "skip isolated process");
            return;
        }
        Context context = XpatchUtils.createAppContext();
        if (context == null) {
            XLog.e(TAG, "create context err");
            return;
        }
        YahfaHooker.init();
        XposedBridge.initXResources();
        PrebuiltMethodsDeopter.deoptBootMethods(); // do it once for secondary zygote
        XposedInit.startsSystemServer = false;

        originalApplicationName = FileUtils.readTextFromAssets(context, ORIGINAL_APPLICATION_NAME_ASSET_PATH);
        originalSignature = FileUtils.readTextFromAssets(context, ORIGINAL_SIGNATURE_ASSET_PATH);

        XLog.d(TAG, "original application class " + originalApplicationName);
        XLog.d(TAG, "original signature info " + originalSignature);

        try {
            doHook(context);
            initAndLoadModules(context);
        } catch (Throwable e) {
            Log.e(TAG, "Do hook", e);
        }
    }

    public LSPApplication() {
        super();

        if (isApplicationProxied()) {
            createOriginalApplication();
        }
    }

    private static boolean isApplicationProxied() {
        return originalApplicationName != null && !originalApplicationName.isEmpty() && !("android.app.Application").equals(originalApplicationName);
    }

    private static ClassLoader getAppClassLoader() {
        if (appClassLoader != null) {
            return appClassLoader;
        }
        try {
            Object mBoundApplication = XposedHelpers.getObjectField(getActivityThread(), "mBoundApplication");
            Object loadedApkObj = XposedHelpers.getObjectField(mBoundApplication, "info");
            appClassLoader = (ClassLoader) XposedHelpers.callMethod(loadedApkObj, "getClassLoader");
        } catch (Throwable e) {
            Log.e(TAG, "getAppClassLoader", e);
        }
        return appClassLoader;
    }

    private static void byPassSignature(Context context) throws ClassNotFoundException, IllegalAccessException {
        Field[] pmStubFields = Class.forName("android.content.pm.IPackageManager$Stub").getDeclaredFields();
        for (Field field : pmStubFields) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != int.class) {
                continue;
            }
            field.setAccessible(true);
            int fieldValue = field.getInt(null);
            String fieldName = field.getName();
            field.setAccessible(false);

            if (fieldName.equals("TRANSACTION_getPackageInfo")) {
                TRANSACTION_getPackageInfo_ID = fieldValue;
                break;
            }
        }

        if (TRANSACTION_getPackageInfo_ID == -1) {
            throw new IllegalStateException("getPackageInfo transaction id null");
        }

        XposedHelpers.findAndHookMethod("android.os.BinderProxy", getAppClassLoader(), "transact", int.class, Parcel.class, Parcel.class, int.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    Object object = param.thisObject;

                    int id = (int) param.args[0];
                    Parcel write = (Parcel) param.args[1];
                    Parcel out = (Parcel) param.args[2];

                    // forward check
                    if (write == null || out == null) {
                        return;
                    }

                    // prevent recurise call
                    if (id == IBinder.INTERFACE_TRANSACTION) {
                        return;
                    }

                    String desc = (String) XposedHelpers.callMethod(object, "getInterfaceDescriptor");
                    if (desc == null || desc.isEmpty() || !desc.equals("android.content.pm.IPackageManager")) {
                        return;
                    }
                    if (id == TRANSACTION_getPackageInfo_ID) {
                        out.readException();
                        if (0 != out.readInt()) {
                            PackageInfo packageInfo = PackageInfo.CREATOR.createFromParcel(out);
                            if (packageInfo.packageName.equals(context.getApplicationInfo().packageName)) {
                                if (packageInfo.signatures != null && packageInfo.signatures.length > 0) {
                                    packageInfo.signatures[0] = new Signature(originalSignature);
                                }

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    if (packageInfo.signingInfo != null) {
                                        Signature[] signaturesArray = packageInfo.signingInfo.getApkContentsSigners();
                                        if (signaturesArray != null && signaturesArray.length > 0) {
                                            signaturesArray[0] = new Signature(originalSignature);
                                        }
                                    }
                                }

                                out.setDataPosition(0);
                                out.setDataSize(0);
                                out.writeNoException();
                                out.writeInt(1);
                                packageInfo.writeToParcel(out, PARCELABLE_WRITE_RETURN_VALUE);
                            }
                        }

                        // reset pos
                        out.setDataPosition(0);
                    }
                } catch (Throwable err) {
                    err.printStackTrace();
                }
            }
        });
    }

    private static void doHook(Context context) throws IllegalAccessException, ClassNotFoundException, IOException {
        if (isApplicationProxied()) {
            hookContextImplSetOuterContext();
            hookInstallContentProviders();
            hookActivityAttach();
            hookServiceAttach();
        }
        hookApplicationStub();
        int bypassLv = fetchSigbypassLv(context);
        if (bypassLv >= Constants.SIGBYPASS_LV_PM) {
            byPassSignature(context);
        }
        if (bypassLv >= Constants.SIGBYPASS_LV_PM_OPENAT) {
            File apk = new File(context.getCacheDir(), "lspatchapk.so");
            if (!apk.exists()) {
                try (InputStream inputStream = context.getAssets().open("origin_apk.bin");
                     FileOutputStream buffer = new FileOutputStream(apk)) {

                    int nRead;
                    byte[] data = new byte[16384];

                    while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, nRead);
                    }
                }
            }
            SigBypass.enableOpenatHook(context.getApplicationInfo().packageName);
        }
    }

    private static int cacheSigbypassLv;

    private static int fetchSigbypassLv(Context context) {
        if (cacheSigbypassLv != -1) {
            return cacheSigbypassLv;
        }
        for (int i = Constants.SIGBYPASS_LV_DISABLE; i < Constants.SIGBYPASS_LV_MAX; i++) {
            try (InputStream inputStream = context.getAssets().open(Constants.CONFIG_NAME_SIGBYPASSLV + i)) {
                cacheSigbypassLv = i;
                return i;
            } catch (IOException ignore) {
            }
        }
        return 0;
    }

    private static void hookApplicationStub() {
        try {
            Class<?> appStub = XposedHelpers.findClass("org.lsposed.lspatch.appstub.LSPApplicationStub", getAppClassLoader());
            XposedHelpers.findAndHookMethod(appStub, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    instance.onCreate();
                }
            });
            XposedHelpers.findAndHookMethod(appStub, "attachBaseContext", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    instance.attachBaseContext((Context) param.args[0]);
                }
            });
        } catch (Throwable e) {
            Log.e(TAG, "hookApplicationStub");
        }
    }

    private static void hookContextImplSetOuterContext() {
        try {
            XposedHelpers.findAndHookMethod("android.app.ContextImpl", getAppClassLoader(), "setOuterContext", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    replaceApplicationParam(param.args);
                }
            });
        } catch (Throwable e) {
            Log.e(TAG, "hookContextImplSetOuterContext", e);
        }
    }

    private static void hookInstallContentProviders() {
        try {
            XposedBridge.hookAllMethods(XposedHelpers.findClass("android.app.ActivityThread", getAppClassLoader()), "installContentProviders", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    replaceApplicationParam(param.args);
                }
            });
        } catch (Throwable e) {
            Log.e(TAG, "hookInstallContextProviders", e);
        }
    }

    private static void hookActivityAttach() {
        try {
            XposedBridge.hookAllMethods(XposedHelpers.findClass("android.app.Activity", getAppClassLoader()), "attach", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    replaceApplicationParam(param.args);
                }
            });
        } catch (Throwable e) {
            Log.e(TAG, "hookActivityAttach", e);
        }
    }

    private static void hookServiceAttach() {
        try {
            XposedBridge.hookAllMethods(XposedHelpers.findClass("android.app.Service", getAppClassLoader()), "attach", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    replaceApplicationParam(param.args);
                }
            });
        } catch (Throwable e) {
            Log.e(TAG, "hookServiceAttach", e);
        }
    }

    private static void replaceApplicationParam(Object[] args) {
        if (args == null || args.length == 0) {
            return;
        }
        for (Object para : args) {
            if (para instanceof LSPApplication) {
                para = sOriginalApplication;
            }
        }
    }

    private static Object getActivityThread() {
        if (activityThread == null) {
            try {
                Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
                activityThread = XposedHelpers.callStaticMethod(activityThreadClass, "currentActivityThread");
            } catch (Throwable e) {
                Log.e(TAG, "getActivityThread", e);
            }
        }
        return activityThread;
    }

    protected void attachBaseContext(Context base) {
        if (isApplicationProxied()) {
            modifyApplicationInfoClassName();
            attachOrignalBaseContext(base);
            setLoadedApkField(base);
        }
    }

    private void attachOrignalBaseContext(Context base) {
        try {
            XposedHelpers.callMethod(sOriginalApplication, "attachBaseContext", base);
        } catch (Throwable e) {
            Log.e(TAG, "attachOriginalBaseContext", e);
        }
    }

    private void setLoadedApkField(Context base) {
        try {
            Class<?> contextImplClass = Class.forName("android.app.ContextImpl");
            Object contextImpl = XposedHelpers.callStaticMethod(contextImplClass, "getImpl", base);
            Object loadedApk = XposedHelpers.getObjectField(contextImpl, "mPackageInfo");
            XposedHelpers.setObjectField(sOriginalApplication, "mLoadedApk", loadedApk);
        } catch (Throwable e) {
            Log.e(TAG, "setLoadedApkField", e);
        }
    }

    public void onCreate() {
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
        } catch (Throwable e) {
            Log.e(TAG, "replaceLoadedApkApplication", e);
        }
    }

    private void replaceActivityThreadApplication() {
        try {
            XposedHelpers.setObjectField(getActivityThread(), "mInitialApplication", sOriginalApplication);
        } catch (Throwable e) {
            Log.e(TAG, "replaceActivityThreadApplication", e);
        }
    }

    private Application createOriginalApplication() {
        if (sOriginalApplication == null) {
            try {
                sOriginalApplication = (Application) getAppClassLoader().loadClass(originalApplicationName).newInstance();
            } catch (Throwable e) {
                Log.e(TAG, "createOriginalApplication", e);
            }
        }
        return sOriginalApplication;
    }

    private void modifyApplicationInfoClassName() {
        try {
            Object mBoundApplication = XposedHelpers.getObjectField(getActivityThread(), "mBoundApplication"); // AppBindData
            Object applicationInfoObj = XposedHelpers.getObjectField(mBoundApplication, "appInfo"); // info

            XposedHelpers.setObjectField(applicationInfoObj, "className", originalApplicationName);
        } catch (Throwable e) {
            Log.e(TAG, "modifyApplicationInfoClassName", e);
        }
    }
}
