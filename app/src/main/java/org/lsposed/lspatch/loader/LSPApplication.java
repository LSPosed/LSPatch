package org.lsposed.lspatch.loader;

import static android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE;

import android.app.ActivityThread;
import android.app.Application;
import android.app.LoadedApk;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SharedMemory;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import org.lsposed.lspatch.loader.util.FileUtils;
import org.lsposed.lspatch.loader.util.XLog;
import org.lsposed.lspatch.share.Constants;
import org.lsposed.lspd.config.ApplicationServiceClient;
import org.lsposed.lspd.core.Main;
import org.lsposed.lspd.models.Module;
import org.lsposed.lspd.models.PreLoadedApk;
import org.lsposed.lspd.nativebridge.SigBypass;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipFile;

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
    private static final String USE_MANAGER_CONTROL_PATH = "use_manager.ini";
    private static final String TAG = "LSPatch";

    private static boolean useManager;
    private static String originalApplicationName = null;
    private static String originalSignature = null;
    private static Application sOriginalApplication = null;
    private static ManagerResolver managerResolver = null;
    private static ClassLoader appClassLoader;
    private static Object activityThread;

    final static public int FIRST_APP_ZYGOTE_ISOLATED_UID = 90000;
    final static public int PER_USER_RANGE = 100000;

    static private LSPApplication instance = null;

    static private final List<Module> modules = new ArrayList<>();

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

        useManager = Boolean.parseBoolean(Objects.requireNonNull(FileUtils.readTextFromAssets(context, USE_MANAGER_CONTROL_PATH)));
        originalApplicationName = FileUtils.readTextFromAssets(context, ORIGINAL_APPLICATION_NAME_ASSET_PATH);
        originalSignature = FileUtils.readTextFromAssets(context, ORIGINAL_SIGNATURE_ASSET_PATH);

        if (useManager) try {
            managerResolver = new ManagerResolver(context);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to instantiate manager resolver", e);
        }

        XLog.d(TAG, "original application class " + originalApplicationName);
        XLog.d(TAG, "original signature info " + originalSignature);

        instance = new LSPApplication();
        serviceClient = instance;
        try {
            initAppClassLoader(context);
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
        if (useManager) {
            try {
                LSPApplication.modules.addAll(managerResolver.getModules());
            } catch (NullPointerException | RemoteException e) {
                Log.e(TAG, "Failed to get modules from manager", e);
            }
        } else {
            try {
                for (var name : context.getAssets().list("modules")) {
                    String packageName = name.substring(0, name.length() - 4);
                    String modulePath = context.getCacheDir() + "/lspatch/" + packageName + "/";
                    String cacheApkPath;
                    try (ZipFile sourceFile = new ZipFile(context.getApplicationInfo().sourceDir)) {
                        cacheApkPath = modulePath + sourceFile.getEntry("assets/modules/" + name).getCrc();
                    }

                    if (!Files.exists(Paths.get(cacheApkPath))) {
                        Log.i(TAG, "extract module apk: " + packageName);
                        FileUtils.deleteFolderIfExists(Paths.get(modulePath));
                        Files.createDirectories(Paths.get(modulePath));
                        try (var is = context.getAssets().open("modules/" + name)) {
                            Files.copy(is, Paths.get(cacheApkPath));
                        }
                    }

                    var module = new Module();
                    module.apkPath = cacheApkPath;
                    module.packageName = packageName;
                    module.file = loadModule(context, cacheApkPath);
                    modules.add(module);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static void readDexes(ZipFile apkFile, List<SharedMemory> preLoadedDexes) {
        int secondary = 2;
        for (var dexFile = apkFile.getEntry("classes.dex"); dexFile != null;
             dexFile = apkFile.getEntry("classes" + secondary + ".dex"), secondary++) {
            try (var in = apkFile.getInputStream(dexFile)) {
                var memory = SharedMemory.create(null, in.available());
                var byteBuffer = memory.mapReadWrite();
                Channels.newChannel(in).read(byteBuffer);
                SharedMemory.unmap(byteBuffer);
                memory.setProtect(OsConstants.PROT_READ);
                preLoadedDexes.add(memory);
            } catch (IOException | ErrnoException e) {
                Log.w(TAG, "Can not load " + dexFile + " in " + apkFile, e);
            }
        }
    }

    private static void readName(ZipFile apkFile, String initName, List<String> names) {
        var initEntry = apkFile.getEntry(initName);
        if (initEntry == null) return;
        try (var in = apkFile.getInputStream(initEntry)) {
            var reader = new BufferedReader(new InputStreamReader(in));
            String name;
            while ((name = reader.readLine()) != null) {
                name = name.trim();
                if (name.isEmpty() || name.startsWith("#")) continue;
                names.add(name);
            }
        } catch (IOException e) {
            Log.e(TAG, "Can not open " + initEntry, e);
        }
    }

    private static PreLoadedApk loadModule(Context context, String path) {
        var file = new PreLoadedApk();
        var preLoadedDexes = new ArrayList<SharedMemory>();
        var moduleClassNames = new ArrayList<String>(1);
        var moduleLibraryNames = new ArrayList<String>(1);
        try (var apkFile = new ZipFile(path)) {
            readDexes(apkFile, preLoadedDexes);
            readName(apkFile, "assets/xposed_init", moduleClassNames);
            readName(apkFile, "assets/native_init", moduleLibraryNames);
        } catch (IOException e) {
            Log.e(TAG, "Can not open " + path, e);
            return null;
        }
        if (preLoadedDexes.isEmpty()) return null;
        if (moduleClassNames.isEmpty()) return null;
        file.hostApk = context.getApplicationInfo().sourceDir;
        file.preLoadedDexes = preLoadedDexes;
        file.moduleClassNames = moduleClassNames;
        file.moduleLibraryNames = moduleLibraryNames;
        return file;
    }

    public LSPApplication() {
        super();
    }

    private static boolean isApplicationProxied() {
        return originalApplicationName != null && !originalApplicationName.isEmpty() && !("android.app.Application").equals(originalApplicationName);
    }

    private static void initAppClassLoader(Context context) {
        try {
            Object mBoundApplication = XposedHelpers.getObjectField(getActivityThread(), "mBoundApplication");
            Object loadedApkObj = XposedHelpers.getObjectField(mBoundApplication, "info");
            appClassLoader = (ClassLoader) XposedHelpers.getObjectField(loadedApkObj, "mClassLoader");
        } catch (Throwable e) {
            Log.e(TAG, "initAppClassLoader", e);
        }
    }

    private static int getTranscationId(String clsName, String trasncationName) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        Field field = Class.forName(clsName).getDeclaredField(trasncationName);
        field.setAccessible(true);
        return field.getInt(null);
    }

    private static void byPassSignature(Context context) throws ClassNotFoundException, IllegalAccessException, NoSuchFieldException {
        final int TRANSACTION_getPackageInfo = getTranscationId("android.content.pm.IPackageManager$Stub", "TRANSACTION_getPackageInfo");
        XposedHelpers.findAndHookMethod("android.os.BinderProxy", appClassLoader, "transact", int.class, Parcel.class, Parcel.class, int.class, new XC_MethodHook() {
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
                    if (id == TRANSACTION_getPackageInfo) {
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
                    // should not happen, just crash app
                    throw new IllegalStateException("lsp hook error", err);
                }
            }
        });
    }

    private static void doHook(Context context) throws IllegalAccessException, ClassNotFoundException, IOException, NoSuchFieldException {
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
            Class<?> appStub = XposedHelpers.findClass("org.lsposed.lspatch.appstub.LSPApplicationStub", appClassLoader);
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
            XposedHelpers.findAndHookMethod("android.app.ContextImpl", appClassLoader, "setOuterContext", Context.class, new XC_MethodHook() {
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
            XposedBridge.hookAllMethods(XposedHelpers.findClass("android.app.ActivityThread", appClassLoader), "installContentProviders", new XC_MethodHook() {
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
            XposedBridge.hookAllMethods(XposedHelpers.findClass("android.app.Activity", appClassLoader), "attach", new XC_MethodHook() {
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
            XposedBridge.hookAllMethods(XposedHelpers.findClass("android.app.Service", appClassLoader), "attach", new XC_MethodHook() {
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
                sOriginalApplication = (Application) appClassLoader.loadClass(originalApplicationName).newInstance();
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
    public IBinder requestModuleBinder(String name) {
        return null;
    }

    @Override
    public boolean requestManagerBinder(String packageName, String path, List<IBinder> binder) {
        return false;
    }

    @Override
    public boolean isResourcesHookEnabled() {
        return false;
    }

    @Override
    public List getModulesList(String processName) {
        return getModulesList();
    }

    @Override
    public List<Module> getModulesList() {
        return modules;
    }

    @Override
    public String getPrefsPath(String packageName) {
        return new File(Environment.getDataDirectory(), "data/" + packageName + "/shared_prefs/").getAbsolutePath();
    }

    @Override
    public Bundle requestRemotePreference(String packageName, int userId, IBinder callback) {
        return null;
    }

    @Override
    public IBinder asBinder() {
        return null;
    }
}
