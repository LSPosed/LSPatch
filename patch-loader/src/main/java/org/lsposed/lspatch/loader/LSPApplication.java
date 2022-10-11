package org.lsposed.lspatch.loader;

import static org.lsposed.lspatch.share.Constants.CONFIG_ASSET_PATH;
import static org.lsposed.lspatch.share.Constants.ORIGINAL_APK_ASSET_PATH;

import android.app.ActivityThread;
import android.app.AppComponentFactory;
import android.app.LoadedApk;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.content.res.CompatibilityInfo;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.system.Os;
import android.util.ArrayMap;
import android.util.Log;

import com.google.gson.Gson;

import org.lsposed.lspatch.loader.util.FileUtils;
import org.lsposed.lspatch.loader.util.XLog;
import org.lsposed.lspatch.service.LocalApplicationService;
import org.lsposed.lspatch.service.NullApplicationService;
import org.lsposed.lspatch.service.RemoteApplicationService;
import org.lsposed.lspatch.share.Constants;
import org.lsposed.lspatch.share.PatchConfig;
import org.lsposed.lspd.core.Startup;
import org.lsposed.lspd.nativebridge.SigBypass;
import org.lsposed.lspd.service.ILSPApplicationService;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import hidden.HiddenApiBridge;

/**
 * Created by Windysha
 */
@SuppressWarnings("unused")
public class LSPApplication {

    public static final int FIRST_APP_ZYGOTE_ISOLATED_UID = 90000;
    public static final int PER_USER_RANGE = 100000;
    private static final String TAG = "LSPatch";

    private static ActivityThread activityThread;
    private static LoadedApk stubLoadedApk;
    private static LoadedApk appLoadedApk;

    private static PatchConfig config;
    private static Path cacheApkPath;

    public static boolean isIsolated() {
        return (android.os.Process.myUid() % PER_USER_RANGE) >= FIRST_APP_ZYGOTE_ISOLATED_UID;
    }

    public static void onLoad() {
        activityThread = ActivityThread.currentActivityThread();

        // we need a context for RemoteApplicationService or LocalApplicationService
        var stubContext = createStubContext();
        if (stubContext == null) {
            XLog.e(TAG, "Error when creating stub context");
            return;
        }
        loadConfig(stubContext);
        try {
            Log.d(TAG, "Initialize service client");
            ILSPApplicationService service;
            if (isIsolated()) {
                // not enable RemoteApplicationService in isolated process
                // Caused by: java.lang.SecurityException: Isolated process not allowed to call getContentProvider
                service = new NullApplicationService();
            } else {
                if (config.useManager) {
                    service = new RemoteApplicationService(stubContext);
                } else {
                    service = new LocalApplicationService(stubContext);
                }
                disableProfile(stubContext);
            }
            Startup.initXposed(false, ActivityThread.currentProcessName(), service);
            Log.i(TAG, "Bootstrap Xposed");
            Startup.bootstrapXposed();
            Log.i(TAG, "Xposed initialized");

            LoadedApk appLoadedApk = switchLoadedApk();
            if (appLoadedApk == stubLoadedApk) {
                Log.e(TAG, "appLoadedApk should diff with stubLoadedApk");
            }
            Log.i(TAG, "LoadedApk switched");

            doSigBypass(stubContext);

            // simulate instance appComponentFactory.
            // this will give shells a chance to replace appComponentFactory again.
            // and LoadedApkGetCLHooker will trigger Xposed modules.
            appLoadedApk.getClassLoader();

            switchAllClassLoader();
        } catch (Throwable e) {
            throw new RuntimeException("Do hook", e);
        }
        Log.i(TAG, "LSPatch bootstrap completed");
    }

    private static void loadConfig(Context stub) {
        try (var is = stub.getClassLoader().getResourceAsStream(CONFIG_ASSET_PATH)) {
            BufferedReader streamReader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            config = new Gson().fromJson(streamReader, PatchConfig.class);
        } catch (IOException e) {
            Log.e(TAG, "Failed to load config file");
        }
        Log.i(TAG, "Use manager: " + config.useManager);
        Log.i(TAG, "Signature bypass level: " + config.sigBypassLevel);
    }

    private static Context createStubContext() {
        try {
            var mBoundApplication = XposedHelpers.getObjectField(activityThread, "mBoundApplication");
            var stubLoadedApk = (LoadedApk) XposedHelpers.getObjectField(mBoundApplication, "info");
            var appInfo = (ApplicationInfo) XposedHelpers.getObjectField(mBoundApplication, "appInfo");

            // reset appComponentFactory to prevent loop
            appInfo.appComponentFactory = AppComponentFactory.class.getName();
            return (Context) XposedHelpers.callStaticMethod(Class.forName("android.app.ContextImpl"), "createAppContext", activityThread, stubLoadedApk);
        } catch (Throwable e) {
            Log.e(TAG, "createStubContext", e);
            return null;
        }
    }

    private static LoadedApk switchLoadedApk() {
        try {
            var mBoundApplication = XposedHelpers.getObjectField(activityThread, "mBoundApplication");

            stubLoadedApk = (LoadedApk) XposedHelpers.getObjectField(mBoundApplication, "info");
            var appInfo = (ApplicationInfo) XposedHelpers.getObjectField(mBoundApplication, "appInfo");
            var compatInfo = (CompatibilityInfo) XposedHelpers.getObjectField(mBoundApplication, "compatInfo");
            var baseClassLoader = stubLoadedApk.getClassLoader();

            if (!isIsolated() && config.sigBypassLevel != Constants.SIGBYPASS_LV_DISABLE) {
                String sourceDir = appInfo.sourceDir;
                Path originPath = Paths.get(appInfo.dataDir, "cache/lspatch/origin/");
                try (ZipFile sourceFile = new ZipFile(sourceDir)) {
                    ZipEntry originalApkAsset = sourceFile.getEntry(ORIGINAL_APK_ASSET_PATH);
                    cacheApkPath = originPath.resolve(originalApkAsset.getCrc() + ".apk");
                    if (!Files.exists(cacheApkPath)) {
                        Log.i(TAG, "Extract original apk");
                        FileUtils.deleteFolderIfExists(originPath);
                        Files.createDirectories(originPath);
                        try (InputStream is = sourceFile.getInputStream(originalApkAsset)) {
                            Files.copy(is, cacheApkPath);
                        }
                    }
                }
                if (config.sigBypassLevel == Constants.SIGBYPASS_LV_PM) {
                    appInfo.sourceDir = cacheApkPath.toString();
                    appInfo.publicSourceDir = cacheApkPath.toString();

                    try { // share class loader to save memory
                        Object applicationLoaders = XposedHelpers.callStaticMethod(Class.forName("android.app.ApplicationLoaders"), "getDefault");
                        ArrayMap<String, Object> mLoaders = (ArrayMap<String, Object>) XposedHelpers.getObjectField(applicationLoaders, "mLoaders");
                        Object cl = mLoaders.get(sourceDir);
                        if (cl != null) {
                            mLoaders.put(cacheApkPath.toString(), cl);
                        }
                    } catch (Throwable e) {
                        Log.w(TAG, "ApplicationLoaders.mLoaders failed", e);
                    }
                    // FIXME change the display name in ClassLoader?
                    // baseClassLoader.pathList.dexElements[*].zip=cacheApkPath
                }
            }
            if (config.appComponentFactory != null && config.appComponentFactory.length() > 0) {
                if (config.appComponentFactory.startsWith(".")) {
                    appInfo.appComponentFactory = appInfo.packageName + config.appComponentFactory;
                } else {
                    appInfo.appComponentFactory = config.appComponentFactory;
                }
            } else {
                appInfo.appComponentFactory = null;
            }
            var mPackages = (Map<?, ?>) XposedHelpers.getObjectField(activityThread, "mPackages");
            mPackages.remove(appInfo.packageName);
            appLoadedApk = activityThread.getPackageInfoNoCheck(appInfo, compatInfo);
            XposedHelpers.setObjectField(mBoundApplication, "info", appLoadedApk);

            var activityClientRecordClass = XposedHelpers.findClass("android.app.ActivityThread$ActivityClientRecord", ActivityThread.class.getClassLoader());
            var fixActivityClientRecord = (BiConsumer<Object, Object>) (k, v) -> {
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
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    var mLaunchingActivities = (Map<?, ?>) XposedHelpers.getObjectField(activityThread, "mLaunchingActivities");
                    mLaunchingActivities.forEach(fixActivityClientRecord);
                }
            } catch (Throwable ignored) {}
            Log.i(TAG, "hooked app initialized: " + appLoadedApk);
            return appLoadedApk;
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

    private static int getTranscationId(String clsName, String trasncationName) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        Field field = Class.forName(clsName).getDeclaredField(trasncationName);
        field.setAccessible(true);
        return field.getInt(null);
    }

    private static void bypassSignature(Context context) {
        String packageName = context.getPackageName();
        Parcelable.Creator<PackageInfo> originalCreator = PackageInfo.CREATOR;
        Parcelable.Creator<PackageInfo> proxiedCreator = new Parcelable.Creator<PackageInfo>() {
            @Override
            public PackageInfo createFromParcel(Parcel source) {
                PackageInfo packageInfo = originalCreator.createFromParcel(source);
                if (packageInfo.packageName.equals(packageName)) {
                    if (cacheApkPath != null && packageInfo.applicationInfo != null) {
                        if (config.sigBypassLevel == Constants.SIGBYPASS_LV_PM) {
                            packageInfo.applicationInfo.sourceDir = cacheApkPath.toString();
                            packageInfo.applicationInfo.publicSourceDir = cacheApkPath.toString();
                        }
                    }
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
                }
                return packageInfo;
            }

            @Override
            public PackageInfo[] newArray(int size) {
                return originalCreator.newArray(size);
            }
        };
        XposedHelpers.setStaticObjectField(PackageInfo.class, "CREATOR", proxiedCreator);
        try {
            Map<?, ?> mCreators = (Map<?, ?>) XposedHelpers.getStaticObjectField(Parcel.class, "mCreators");
            mCreators.clear();
        } catch (NoSuchFieldError ignore) {
        } catch (Throwable e) {
            Log.w(TAG, "fail to clear Parcel.mCreators", e);
        }
        try {
            Map<?, ?> sPairedCreators = (Map<?, ?>) XposedHelpers.getStaticObjectField(Parcel.class, "sPairedCreators");
            sPairedCreators.clear();
        } catch (NoSuchFieldError ignore) {
        } catch (Throwable e) {
            Log.w(TAG, "fail to clear Parcel.sPairedCreators", e);
        }
    }

    private static void doSigBypass(Context context) throws IllegalAccessException, ClassNotFoundException, IOException, NoSuchFieldException {
        if (config.sigBypassLevel >= Constants.SIGBYPASS_LV_PM) {
            XLog.d(TAG, "Original signature: " + config.originalSignature.substring(0, 16) + "...");
            bypassSignature(context);
        }
        if (config.sigBypassLevel >= Constants.SIGBYPASS_LV_PM_OPENAT && cacheApkPath != null) {
            String packageResourcePath = context.getPackageResourcePath();
            String cacheApkPath = LSPApplication.cacheApkPath.toString();
            XLog.d(TAG, String.format("enableOpenatHook (%s, %s)", packageResourcePath, cacheApkPath));
            SigBypass.enableOpenatHook(packageResourcePath, cacheApkPath);

            // ZipFile have an internal cache, for newly create ZipFile, replace to cacheApkPath
            XposedHelpers.findAndHookMethod(ZipFile.class, "open", String.class, int.class, long.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    String fn = (String) param.args[0];
                    if (fn != null && fn.equals(packageResourcePath)) {
                        param.args[0] = cacheApkPath;
                    }
                }
            });
        }
    }

    private static void switchAllClassLoader() {
        var fields = LoadedApk.class.getDeclaredFields();
        for (Field field : fields) {
            if (field.getType() == ClassLoader.class) {
                var obj = XposedHelpers.getObjectField(appLoadedApk, field.getName());
                XposedHelpers.setObjectField(stubLoadedApk, field.getName(), obj);
            }
        }
    }
}
