package org.lsposed.lspatch.loader;

import static android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE;
import static org.lsposed.lspatch.loader.LSPLoader.initAndLoadModules;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;

import org.lsposed.lspatch.loader.util.FileUtils;
import org.lsposed.lspatch.loader.util.XLog;
import org.lsposed.lspatch.loader.util.XpatchUtils;
import org.lsposed.lspatch.share.Constants;
import org.lsposed.lspd.yahfa.hooker.YahfaHooker;

import java.io.IOException;
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
public class LSPApplication extends Application {
    private static final String ORIGINAL_APPLICATION_NAME_ASSET_PATH = "original_application_name.ini";
    private static final String ORIGINAL_SIGNATURE_ASSET_PATH = "original_signature_info.ini";
    private static final String TAG = LSPApplication.class.getSimpleName();
    private static String originalApplicationName = null;
    private static String originalSignature = null;
    private static Application sOriginalApplication = null;
    private static ClassLoader appClassLoader;
    private static Object activityThread;

    private static int TRANSACTION_getPackageInfo_ID = -1;

    final static public int FIRST_ISOLATED_UID = 99000;
    final static public int LAST_ISOLATED_UID = 99999;
    final static public int FIRST_APP_ZYGOTE_ISOLATED_UID = 90000;
    final static public int LAST_APP_ZYGOTE_ISOLATED_UID = 98999;
    final static public int SHARED_RELRO_UID = 1037;
    final static public int PER_USER_RANGE = 100000;

    static Context context;

    static public boolean isIsolated() {
        int uid = android.os.Process.myUid();
        uid = uid % PER_USER_RANGE;
        return (uid >= FIRST_ISOLATED_UID && uid <= LAST_ISOLATED_UID) || (uid >= FIRST_APP_ZYGOTE_ISOLATED_UID && uid <= LAST_APP_ZYGOTE_ISOLATED_UID);
    }

    static {
        if (isIsolated()) {
            XLog.d(TAG, "skip isolated process");
        }
        else {
            System.loadLibrary("lspd");
            YahfaHooker.init();
            XposedInit.startsSystemServer = false;

            context = XpatchUtils.createAppContext();
            if (context == null) {
                XLog.e(TAG, "create context err");
            }
            else {
                originalApplicationName = FileUtils.readTextFromAssets(context, ORIGINAL_APPLICATION_NAME_ASSET_PATH);
                originalSignature = FileUtils.readTextFromAssets(context, ORIGINAL_SIGNATURE_ASSET_PATH);

                XLog.d(TAG, "original application class " + originalApplicationName);
                XLog.d(TAG, "original signature info " + originalSignature);

                if (isApplicationProxied()) {
                    try {
                        doHook();
                        initAndLoadModules(context);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public LSPApplication() {
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

    private static void byPassSignature() throws ClassNotFoundException, IllegalAccessException {
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
                }
                catch (Throwable err) {
                    err.printStackTrace();
                }
            }
        });
    }

    private static void doHook() throws IllegalAccessException, ClassNotFoundException {
        hookContextImplSetOuterContext();
        hookInstallContentProviders();
        hookActivityAttach();
        hookServiceAttach();
        if (fetchSigbypassLv() >= Constants.SIGBYPASS_LV_PM) {
            byPassSignature();
        }
    }

    private static int cacheSigbypassLv = -1;

    private static int fetchSigbypassLv() {
        if (cacheSigbypassLv != -1) {
            return cacheSigbypassLv;
        }
        for (int i = Constants.SIGBYPASS_LV_DISABLE; i < Constants.SIGBYPASS_LV_MAX; i++) {
            try {
                context.getAssets().open(Constants.CONFIG_NAME_SIGBYPASSLV + i);
                cacheSigbypassLv = i;
                return i;
            }
            catch (IOException ignore) {
            }
        }
        throw new IllegalStateException(Constants.CONFIG_NAME_SIGBYPASSLV + " err");
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
