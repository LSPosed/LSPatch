package org.lsposed.lspatch.loader;

import static android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE;
import static org.lsposed.lspatch.share.Constants.CONFIG_ASSET_PATH;
import static org.lsposed.lspatch.share.Constants.ORIGINAL_APK_ASSET_PATH;
import static org.lsposed.lspd.service.ConfigFileManager.loadModule;

import android.app.ActivityThread;
import android.app.LoadedApk;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.content.res.CompatibilityInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.system.Os;
import android.util.Log;

import com.google.gson.Gson;

import org.lsposed.lspatch.loader.util.FileUtils;
import org.lsposed.lspatch.loader.util.XLog;
import org.lsposed.lspatch.share.Constants;
import org.lsposed.lspatch.share.PatchConfig;
import org.lsposed.lspd.config.ApplicationServiceClient;
import org.lsposed.lspd.core.Main;
import org.lsposed.lspd.models.Module;
import org.lsposed.lspd.nativebridge.SigBypass;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.zip.ZipFile;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedInit;
import hidden.HiddenApiBridge;

/**
 * Created by Windysha
 */
@SuppressWarnings("unused")
public class LSPApplication extends ApplicationServiceClient {
    private static final String TAG = "LSPatch";

    private static ActivityThread activityThread;
    private static LoadedApk stubLoadedApk;
    private static LoadedApk appLoadedApk;

    private static PatchConfig config;
    private static ManagerResolver managerResolver = null;

    final static public int FIRST_APP_ZYGOTE_ISOLATED_UID = 90000;
    final static public int PER_USER_RANGE = 100000;

    static private LSPApplication instance = null;

    static private final List<Module> modules = new ArrayList<>();

    static public boolean isIsolated() {
        return (android.os.Process.myUid() % PER_USER_RANGE) >= FIRST_APP_ZYGOTE_ISOLATED_UID;
    }

    public static void onLoad() {
        if (isIsolated()) {
            XLog.d(TAG, "Skip isolated process");
            return;
        }
        activityThread = ActivityThread.currentActivityThread();
        var context = createLoadedApkWithContext();
        if (context == null) {
            XLog.e(TAG, "Error when creating context");
            return;
        }

        if (config.useManager) try {
            managerResolver = new ManagerResolver(context);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to instantiate manager resolver", e);
        }

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
            LSPLoader.initModules(appLoadedApk);
            Log.i(TAG, "Modules initialized");

            switchClassLoader("mBaseClassLoader");
            switchClassLoader("mDefaultClassLoader");
            switchClassLoader("mClassLoader");
        } catch (Throwable e) {
            Log.e(TAG, "Do hook", e);
        }
    }

    private static Context createLoadedApkWithContext() {
        try {
            var mBoundApplication = XposedHelpers.getObjectField(activityThread, "mBoundApplication");

            stubLoadedApk = (LoadedApk) XposedHelpers.getObjectField(mBoundApplication, "info");
            var appInfo = (ApplicationInfo) XposedHelpers.getObjectField(mBoundApplication, "appInfo");
            var compatInfo = (CompatibilityInfo) XposedHelpers.getObjectField(mBoundApplication, "compatInfo");
            var baseClassLoader = stubLoadedApk.getClassLoader();

            try (var is = baseClassLoader.getResourceAsStream(CONFIG_ASSET_PATH)) {
                BufferedReader streamReader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                config = new Gson().fromJson(streamReader, PatchConfig.class);
            } catch (IOException e) {
                Log.e(TAG, "Failed to load config file");
                return null;
            }
            Log.i(TAG, "Use manager: " + config.useManager);
            Log.i(TAG, "Signature bypass level: " + config.sigBypassLevel);

            String originPath = appInfo.dataDir + "/cache/lspatch/origin/";
            String cacheApkPath;
            try (ZipFile sourceFile = new ZipFile(appInfo.sourceDir)) {
                cacheApkPath = originPath + sourceFile.getEntry(ORIGINAL_APK_ASSET_PATH).getCrc();
            }

            appInfo.sourceDir = cacheApkPath;
            appInfo.publicSourceDir = cacheApkPath;
            appInfo.appComponentFactory = config.appComponentFactory;

            if (!Files.exists(Paths.get(cacheApkPath))) {
                Log.i(TAG, "Extract original apk");
                FileUtils.deleteFolderIfExists(Paths.get(originPath));
                Files.createDirectories(Paths.get(originPath));
                try (InputStream is = baseClassLoader.getResourceAsStream(ORIGINAL_APK_ASSET_PATH)) {
                    Files.copy(is, Paths.get(cacheApkPath));
                }
            }

            var mPackages = (Map<?, ?>) XposedHelpers.getObjectField(activityThread, "mPackages");
            mPackages.remove(appInfo.packageName);
            appLoadedApk = activityThread.getPackageInfoNoCheck(appInfo, compatInfo);
            XposedHelpers.setObjectField(mBoundApplication, "info", appLoadedApk);

            var activityClientRecordClass = XposedHelpers.findClass("android.app.ActivityThread$ActivityClientRecord", ActivityThread.class.getClassLoader());
            var fixActivityClientRecord = (BiConsumer<Object, Object>)(k, v) -> {
                if (activityClientRecordClass.isInstance(v)) {
                    var pkgInfo = XposedHelpers.getObjectField(v, "packageInfo");
                    if (pkgInfo == stubLoadedApk) {
                        Log.d(TAG, "fix loadedapk from ActivityClientRecord");
                        XposedHelpers.setObjectField(v, "packageInfo", appLoadedApk);
                    }
                }
            };
            var mActivities = (Map<?, ?>) XposedHelpers.getObjectField(activityThread, "mActivities");
            mActivities.forEach(fixActivityClientRecord);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                var mLaunchingActivities = (Map<?, ?>) XposedHelpers.getObjectField(activityThread, "mLaunchingActivities");
                mLaunchingActivities.forEach(fixActivityClientRecord);
            }
            Log.i(TAG, "hooked app initialized: " + appLoadedApk);

            return (Context) XposedHelpers.callStaticMethod(Class.forName("android.app.ContextImpl"), "createAppContext", activityThread, stubLoadedApk);
        } catch (Throwable e) {
            Log.e(TAG, "createLoadedApk", e);
            return null;
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
            Log.d(TAG, "Processing " + curProfileFile.getAbsolutePath());
            try {
                if (!curProfileFile.canWrite() && Files.size(curProfileFile.toPath()) == 0) {
                    Log.d(TAG, "Skip profile " + curProfileFile.getAbsolutePath());
                    continue;
                }
                if (curProfileFile.exists() && !curProfileFile.delete()) {
                    try (var writer = new FileOutputStream(curProfileFile)) {
                        Log.d(TAG, "Failed to delete, try to clear content " + curProfileFile.getAbsolutePath());
                    } catch (Throwable e) {
                        Log.e(TAG, "Failed to delete and clear profile file " + curProfileFile.getAbsolutePath(), e);
                    }
                    Os.chmod(curProfileFile.getAbsolutePath(), 00400);
                } else {
                    Files.createFile(curProfileFile.toPath(), attrs);
                }
            } catch (Throwable e) {
                Log.e(TAG, "Failed to disable profile file " + curProfileFile.getAbsolutePath(), e);
            }
        }

    }

    public static void loadModules(Context context) {
        if (config.useManager) {
            try {
                modules.addAll(managerResolver.getModules());
                modules.forEach(m -> Log.i(TAG, "load module from manager: " + m.packageName));
            } catch (NullPointerException | RemoteException e) {
                Log.e(TAG, "Failed to get modules from manager", e);
            }
        } else {
            try {
                for (var name : context.getAssets().list("lspatch/modules")) {
                    String packageName = name.substring(0, name.length() - 4);
                    String modulePath = context.getCacheDir() + "/lspatch/" + packageName + "/";
                    String cacheApkPath;
                    try (ZipFile sourceFile = new ZipFile(context.getPackageResourcePath())) {
                        cacheApkPath = modulePath + sourceFile.getEntry("assets/lspatch/modules/" + name).getCrc();
                    }

                    if (!Files.exists(Paths.get(cacheApkPath))) {
                        Log.i(TAG, "Extract module apk: " + packageName);
                        FileUtils.deleteFolderIfExists(Paths.get(modulePath));
                        Files.createDirectories(Paths.get(modulePath));
                        try (var is = context.getAssets().open("lspatch/modules/" + name)) {
                            Files.copy(is, Paths.get(cacheApkPath));
                        }
                    }

                    var module = new Module();
                    module.apkPath = cacheApkPath;
                    module.packageName = packageName;
                    module.file = loadModule(cacheApkPath);
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
                                    XLog.d(TAG, "Replace signature info (method 1)");
                                    packageInfo.signatures[0] = new Signature(config.originalSignature);
                                }

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    if (packageInfo.signingInfo != null) {
                                        XLog.d(TAG, "Replace signature info (method 2)");
                                        Signature[] signaturesArray = packageInfo.signingInfo.getApkContentsSigners();
                                        if (signaturesArray != null && signaturesArray.length > 0) {
                                            signaturesArray[0] = new Signature(config.originalSignature);
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
        if (config.sigBypassLevel >= Constants.SIGBYPASS_LV_PM) {
            XLog.d(TAG, "Original signature: " + config.originalSignature.substring(0, 16) + "...");
            byPassSignature(context);
        }
        if (config.sigBypassLevel >= Constants.SIGBYPASS_LV_PM_OPENAT) {
            String cacheApkPath;
            try (ZipFile sourceFile = new ZipFile(context.getPackageResourcePath())) {
                cacheApkPath = context.getCacheDir() + "/lspatch/origin/" + sourceFile.getEntry(ORIGINAL_APK_ASSET_PATH).getCrc();
            }
            SigBypass.enableOpenatHook(context.getPackageResourcePath(), cacheApkPath);
        }
    }

    private static void switchClassLoader(String fieldName) {
        var obj = XposedHelpers.getObjectField(appLoadedApk, fieldName);
        XposedHelpers.setObjectField(stubLoadedApk, fieldName, obj);
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
