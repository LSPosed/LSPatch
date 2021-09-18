package org.lsposed.lspatch.loader;

import static android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE;
import static org.lsposed.lspatch.share.Constants.ORIGINAL_APK_ASSET_PATH;
import static org.lsposed.lspatch.share.Constants.ORIGINAL_APP_COMPONENT_FACTORY_ASSET_PATH;
import static org.lsposed.lspatch.share.Constants.PROXY_APP_COMPONENT_FACTORY;
import static org.lsposed.lspd.service.ConfigFileManager.loadModule;

import android.annotation.SuppressLint;
import android.app.ActivityThread;
import android.app.AppComponentFactory;
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
import android.system.Os;
import android.util.Log;

import org.lsposed.lspatch.loader.util.FileUtils;
import org.lsposed.lspatch.loader.util.XLog;
import org.lsposed.lspatch.share.Constants;
import org.lsposed.lspd.config.ApplicationServiceClient;
import org.lsposed.lspd.core.Main;
import org.lsposed.lspd.models.Module;
import org.lsposed.lspd.nativebridge.SigBypass;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipFile;

import dalvik.system.DelegateLastClassLoader;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedInit;
import hidden.HiddenApiBridge;

/**
 * Created by Windysha
 */
@SuppressWarnings("unused")
public class LSPApplication extends ApplicationServiceClient {
    private static final String ORIGINAL_SIGNATURE_ASSET_PATH = "original_signature_info.ini";
    private static final String USE_MANAGER_CONTROL_PATH = "use_manager.ini";
    private static final String TAG = "LSPatch";

    private static boolean useManager;
    private static String originalSignature = null;
    private static ManagerResolver managerResolver = null;
    private static Object activityThread;
    private static LoadedApk loadedApkObj;

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

        initAppComponentFactory(context);

        useManager = Boolean.parseBoolean(Objects.requireNonNull(FileUtils.readTextFromAssets(context, USE_MANAGER_CONTROL_PATH)));
        originalSignature = FileUtils.readTextFromAssets(context, ORIGINAL_SIGNATURE_ASSET_PATH);

        if (useManager) try {
            managerResolver = new ManagerResolver(context);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to instantiate manager resolver", e);
        }

        XLog.d(TAG, "original signature info " + originalSignature);

        instance = new LSPApplication();
        serviceClient = instance;
        try {
            disableProfile(context);
            loadModules(context);
            Main.forkPostCommon(false, context.getDataDir().toString(), ActivityThread.currentProcessName());
            doHook(context);
            Log.i(TAG, "Start loading modules");
            XposedInit.loadModules();
            // WARN: Since it uses `XResource`, the following class should not be initialized
            // before forkPostCommon is invoke. Otherwise, you will get failure of XResources
            LSPLoader.initModules(context);
        } catch (Throwable e) {
            Log.e(TAG, "Do hook", e);
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private static void initAppComponentFactory(Context context) {
        try {
            ApplicationInfo aInfo = context.getApplicationInfo();
            ClassLoader baseClassLoader = context.getClassLoader();
            Class<?> stubClass = Class.forName(PROXY_APP_COMPONENT_FACTORY, false, baseClassLoader);

            String originPath = aInfo.dataDir + "/cache/lspatch/origin/";
            String originalAppComponentFactoryClass = FileUtils.readTextFromInputStream(baseClassLoader.getResourceAsStream(ORIGINAL_APP_COMPONENT_FACTORY_ASSET_PATH));
            String cacheApkPath;
            try (ZipFile sourceFile = new ZipFile(aInfo.sourceDir)) {
                cacheApkPath = originPath + sourceFile.getEntry(ORIGINAL_APK_ASSET_PATH).getCrc();
            }
            if (!Files.exists(Paths.get(cacheApkPath))) {
                Log.i(TAG, "extract original apk");
                FileUtils.deleteFolderIfExists(Paths.get(originPath));
                Files.createDirectories(Paths.get(originPath));
                try (InputStream is = baseClassLoader.getResourceAsStream(ORIGINAL_APK_ASSET_PATH)) {
                    Files.copy(is, Paths.get(cacheApkPath));
                }
            }
            var appClassLoader = new DelegateLastClassLoader(cacheApkPath, aInfo.nativeLibraryDir, baseClassLoader.getParent());
            AppComponentFactory originalAppComponentFactory;
            try {
                originalAppComponentFactory = (AppComponentFactory) appClassLoader.loadClass(originalAppComponentFactoryClass).newInstance();
            } catch (ClassNotFoundException | NullPointerException ignored) {
                if (originalAppComponentFactoryClass != null && !originalAppComponentFactoryClass.isEmpty())
                    Log.w(TAG, "original AppComponentFactory not found");
                originalAppComponentFactory = new AppComponentFactory();
            }
            Field mClassLoaderField = LoadedApk.class.getDeclaredField("mClassLoader");
            mClassLoaderField.setAccessible(true);
            mClassLoaderField.set(loadedApkObj, appClassLoader);

            stubClass.getDeclaredField("appClassLoader").set(null, appClassLoader);
            stubClass.getDeclaredField("originalAppComponentFactory").set(null, originalAppComponentFactory);

            Log.d(TAG, "set up original AppComponentFactory");
        } catch (Throwable e) {
            Log.e(TAG, "initAppComponentFactory", e);
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
                modules.addAll(managerResolver.getModules());
                modules.forEach(m -> Log.i(TAG, "load module from manager: " + m.packageName));
            } catch (NullPointerException | RemoteException e) {
                Log.e(TAG, "Failed to get modules from manager", e);
            }
        } else {
            try {
                for (var name : context.getAssets().list("modules")) {
                    String packageName = name.substring(0, name.length() - 4);
                    String modulePath = context.getCacheDir() + "/lspatch/" + packageName + "/";
                    String cacheApkPath;
                    try (ZipFile sourceFile = new ZipFile(context.getPackageResourcePath())) {
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
                    module.file = loadModule(cacheApkPath);
                    if (module.file != null) module.file.hostApk = context.getPackageResourcePath();
                    modules.add(module);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public LSPApplication() {
        super();
    }

    private static int getTranscationId(String clsName, String trasncationName) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        Field field = Class.forName(clsName).getDeclaredField(trasncationName);
        field.setAccessible(true);
        return field.getInt(null);
    }

    private static void byPassSignature(Context context) throws ClassNotFoundException, IllegalAccessException, NoSuchFieldException {
        final int TRANSACTION_getPackageInfo = getTranscationId("android.content.pm.IPackageManager$Stub", "TRANSACTION_getPackageInfo");
        XposedHelpers.findAndHookMethod("android.os.BinderProxy", null, "transact", int.class, Parcel.class, Parcel.class, int.class, new XC_MethodHook() {
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
                                    XLog.d(TAG, "replace signature info [0]");
                                    packageInfo.signatures[0] = new Signature(originalSignature);
                                }

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    if (packageInfo.signingInfo != null) {
                                        XLog.d(TAG, "replace signature info [1]");
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
        int bypassLv = fetchSigbypassLv(context);
        if (bypassLv >= Constants.SIGBYPASS_LV_PM) {
            byPassSignature(context);
        }
        if (bypassLv >= Constants.SIGBYPASS_LV_PM_OPENAT) {
            String cacheApkPath;
            try (ZipFile sourceFile = new ZipFile(context.getPackageResourcePath())) {
                cacheApkPath = context.getCacheDir() + "/lspatch/origin/" + sourceFile.getEntry(ORIGINAL_APK_ASSET_PATH).getCrc();
            }
            SigBypass.enableOpenatHook(context.getPackageResourcePath(), cacheApkPath);
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
            loadedApkObj = (LoadedApk) infoField.get(mBoundApplication);  // LoadedApk
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
