package org.lsposed.lspatch.loader;

import static android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE;

import android.app.ActivityThread;
import android.app.Application;
import android.app.LoadedApk;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.lsposed.lspatch.loader.util.FileUtils;
import org.lsposed.lspatch.loader.util.XLog;
import org.lsposed.lspatch.share.Constants;
import org.lsposed.lspd.config.ApplicationServiceClient;
import org.lsposed.lspd.core.Main;
import org.lsposed.lspd.nativebridge.SigBypass;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import hidden.HiddenApiBridge;

/**
 * Created by Windysha
 */
@SuppressWarnings("unused")
public class LSPApplication extends ApplicationServiceClient {
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

    static private LSPApplication instance = null;

    static private final Map<String, String> modules = new HashMap<>();

    static public boolean isIsolated() {
        return (android.os.Process.myUid() % PER_USER_RANGE) >= FIRST_APP_ZYGOTE_ISOLATED_UID;
    }

    public static void onLoad() {
        cacheSigbypassLv = -1;

        if (isIsolated()) {
            XLog.d(TAG, "skip isolated process");
            return;
        }
        Context context = createAppContext();
        if (context == null) {
            XLog.e(TAG, "create context err");
            return;
        }

        originalApplicationName = FileUtils.readTextFromAssets(context, ORIGINAL_APPLICATION_NAME_ASSET_PATH);
        originalSignature = FileUtils.readTextFromAssets(context, ORIGINAL_SIGNATURE_ASSET_PATH);

        XLog.d(TAG, "original application class " + originalApplicationName);
        XLog.d(TAG, "original signature info " + originalSignature);

        instance = new LSPApplication();
        serviceClient = instance;
        try {
            disableProfile(context);
            loadModules(context);
            Main.forkPostCommon(false, context.getDataDir().toString(), ActivityThread.currentProcessName());
            doHook(context);
            // WARN: Since it uses `XResource`, the following class should not be initialized
            // before forkPostCommon is invoke. Otherwise, you will get failure of XResources
            LSPLoader.initModules(context);
        } catch (Throwable e) {
            Log.e(TAG, "Do hook", e);
        }
        if (isApplicationProxied()) {
            instance.createOriginalApplication();
        }
    }

    public static void disableProfile(Context context) {
        final ArrayList<String> codePaths = new ArrayList<>();
        var appInfo = context.getApplicationInfo();
        var pkgName = context.getPackageName();
        if (appInfo == null) return;
        if ((appInfo.flags & ApplicationInfo.FLAG_HAS_CODE) != 0) {
            codePaths.add(appInfo.sourceDir);
        }
        if (appInfo.splitSourceDirs != null) {
            Collections.addAll(codePaths, appInfo.splitSourceDirs);
        }

        if (codePaths.isEmpty()) {
            // If there are no code paths there's no need to setup a profile file and register with
            // the runtime,
            return;
        }

        var profileDir = HiddenApiBridge.Environment_getDataProfilesDePackageDirectory(appInfo.uid / PER_USER_RANGE, pkgName);

        var attrs = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("r--------"));

        for (int i = codePaths.size() - 1; i >= 0; i--) {
            String splitName = i == 0 ? null : appInfo.splitNames[i - 1];
            File curProfileFile = new File(profileDir, splitName == null ? "primary.prof" : splitName + ".split.prof").getAbsoluteFile();
            Log.d(TAG, "processing " + curProfileFile.getAbsolutePath());
            try {
                if (!curProfileFile.canWrite() && Files.size(curProfileFile.toPath()) == 0) {
                    Log.d(TAG, "skip profile " + curProfileFile.getAbsolutePath());
                    continue;
                }
                if (curProfileFile.exists() && !curProfileFile.delete()) {
                    try (var writer = new FileOutputStream(curProfileFile)) {
                        Log.d(TAG, "failed to delete, try to clear content " + curProfileFile.getAbsolutePath());
                    } catch (Throwable e) {
                        Log.e(TAG, "failed to delete and clear profile file " + curProfileFile.getAbsolutePath(), e);
                    }
                    Os.chmod(curProfileFile.getAbsolutePath(), 00400);
                } else {
                    Files.createFile(curProfileFile.toPath(), attrs);
                }
            } catch (Throwable e) {
                Log.e(TAG, "failed to disable profile file " + curProfileFile.getAbsolutePath(), e);
            }
        }

    }

    public static void loadModules(Context context) {
        var configFile = new File(context.getExternalFilesDir(null), "lspatch.json");
        var cacheDir = new File(context.getExternalCacheDir(), "modules");
        cacheDir.mkdirs();
        JSONObject moduleConfigs = new JSONObject();
        try (var is = new FileInputStream(configFile)) {
            moduleConfigs = new JSONObject(FileUtils.readTextFromInputStream(is));
        } catch (Throwable ignored) {
        }
        var modules = moduleConfigs.optJSONArray("modules");
        if (modules == null) {
            modules = new JSONArray();
            try {
                moduleConfigs.put("modules", modules);
            } catch (Throwable ignored) {

            }
        }
        HashSet<String> embedded_modules = new HashSet<>();
        HashSet<String> disabled_modules = new HashSet<>();
        try {
            var lastInstalledTime = new File(context.getApplicationInfo().sourceDir).lastModified();
            for (var name : context.getAssets().list("modules")) {
                var target = new File(cacheDir, name + ".apk");
                if (target.lastModified() > lastInstalledTime) {
                    embedded_modules.add(name);
                    LSPApplication.modules.put(name, target.getAbsolutePath());
                    continue;
                }
                try (var is = context.getAssets().open("modules/" + name)) {
                    Files.copy(is, target.toPath());
                    embedded_modules.add(name);
                    LSPApplication.modules.put(name, target.getAbsolutePath());
                } catch (IOException ignored) {

                }
            }
        } catch (Throwable ignored) {

        }
        for (int i = 0; i < modules.length(); ++i) {
            var module = modules.optJSONObject(i);
            var name = module.optString("name");
            var enabled = module.optBoolean("enabled", true);
            var useEmbed = module.optBoolean("use_embed", false);
            if (name.isEmpty()) continue;
            if (!enabled) disabled_modules.add(name);
            if (embedded_modules.contains(name) && !useEmbed) embedded_modules.remove(name);
        }

        for (PackageInfo pkg : context.getPackageManager().getInstalledPackages(PackageManager.GET_META_DATA)) {
            ApplicationInfo app = pkg.applicationInfo;
            if (!app.enabled) {
                continue;
            }
            if (app.metaData != null && app.metaData.containsKey("xposedminversion") && !embedded_modules.contains(app.packageName)) {
                LSPApplication.modules.put(app.packageName, app.publicSourceDir);
            }
        }
        final var new_modules = new JSONArray();
        LSPApplication.modules.forEach((k, v) -> {
            try {
                var module = new JSONObject();
                module.put("name", k);
                module.put("enabled", !disabled_modules.contains(k));
                module.put("use_embed", embedded_modules.contains(k));
                module.put("path", v);
                new_modules.put(module);
            } catch (Throwable ignored) {
            }
        });
        try {
            moduleConfigs.put("modules", new_modules);
        } catch (Throwable ignored) {
        }
        try (var is = new ByteArrayInputStream(moduleConfigs.toString(4).getBytes(StandardCharsets.UTF_8))) {
            Files.copy(is, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable ignored) {
        }
        for (var module : disabled_modules) {
            LSPApplication.modules.remove(module);
        }
    }

    public LSPApplication() {
        super();
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
                activityThread = ActivityThread.currentActivityThread();
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

    public static Context createAppContext() {
        try {

            ActivityThread activityThreadObj = ActivityThread.currentActivityThread();

            Field boundApplicationField = ActivityThread.class.getDeclaredField("mBoundApplication");
            boundApplicationField.setAccessible(true);
            Object mBoundApplication = boundApplicationField.get(activityThreadObj);   // AppBindData
            if (mBoundApplication == null) {
                Log.e(TAG, "mBoundApplication null");
                return null;
            }
            Field infoField = mBoundApplication.getClass().getDeclaredField("info");   // info
            infoField.setAccessible(true);
            LoadedApk loadedApkObj = (LoadedApk) infoField.get(mBoundApplication);  // LoadedApk
            if (loadedApkObj == null) {
                Log.e(TAG, "loadedApkObj null");
                return null;
            }
            Class<?> contextImplClass = Class.forName("android.app.ContextImpl");
            Method createAppContextMethod = contextImplClass.getDeclaredMethod("createAppContext", ActivityThread.class, LoadedApk.class);
            createAppContextMethod.setAccessible(true);

            Object context = createAppContextMethod.invoke(null, activityThreadObj, loadedApkObj);

            if (context instanceof Context) {
                return (Context) context;
            }
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException | NoSuchFieldException e) {
            Log.e(TAG, "Fail to create app context", e);
        }
        return null;
    }

    @Override
    public IBinder requestModuleBinder() {
        return null;
    }

    @Override
    public IBinder requestManagerBinder(String packageName) {
        return null;
    }

    @Override
    public boolean isResourcesHookEnabled() {
        return false;
    }

    @Override
    public Map getModulesList(String processName) {
        return getModulesList();
    }

    @Override
    public Map<String, String> getModulesList() {
        return modules;
    }

    @Override
    public String getPrefsPath(String packageName) {
        return new File(Environment.getDataDirectory(), "data/" + packageName + "/shared_prefs/").getAbsolutePath();
    }

    @Override
    public ParcelFileDescriptor getModuleLogger() {
        return null;
    }

    @Override
    public IBinder asBinder() {
        return null;
    }
}
