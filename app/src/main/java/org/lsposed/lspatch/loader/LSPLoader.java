package org.lsposed.lspatch.loader;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import org.lsposed.lspatch.loader.util.FileUtils;
import org.lsposed.lspatch.loader.util.XLog;
import org.lsposed.lspatch.loader.util.XpatchUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import dalvik.system.DelegateLastClassLoader;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelper;
import de.robv.android.xposed.XposedInit;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class LSPLoader {

    private static final String TAG = LSPLoader.class.getSimpleName();
    private static final String DIR_BASE = Environment.getExternalStorageDirectory().getAbsolutePath();
    private static final String XPOSED_MODULE_FILE_PATH = "xpmodules.list";
    private static AtomicBoolean hasInited = new AtomicBoolean(false);
    private static Context appContext;


    @SuppressLint("DiscouragedPrivateApi")
    public static boolean loadModule(final String moduleApkPath, String moduleLibPath, final ApplicationInfo currentApplicationInfo, ClassLoader appClassLoader) {

        XLog.i(TAG, "Loading modules from " + moduleApkPath);

        if (!new File(moduleApkPath).exists()) {
            XLog.e(TAG, moduleApkPath + " does not exist");
            return false;
        }

        // module can load it's own so
        StringBuilder nativePath = new StringBuilder();
        for (String i : Build.SUPPORTED_ABIS) {
            nativePath.append(moduleApkPath).append("!/lib/").append(i).append(File.pathSeparator);
        }
        ClassLoader initLoader = XposedInit.class.getClassLoader();
        ClassLoader mcl = new DelegateLastClassLoader(moduleApkPath, nativePath.toString(), initLoader);

        try {
            if (mcl.loadClass(XposedBridge.class.getName()).getClassLoader() != appClassLoader) {
                Log.e(TAG, "Cannot load module:");
                Log.e(TAG, "The Xposed API classes are compiled into the module's APK.");
                Log.e(TAG, "This may cause strange issues and must be fixed by the module developer.");
                Log.e(TAG, "For details, see: http://api.xposed.info/using.html");
                return false;
            }
        }
        catch (ClassNotFoundException ignored) {
        }

        try (InputStream is = mcl.getResourceAsStream("assets/xposed_init")) {
            if (is == null) {
                XLog.e(TAG, "assets/xposed_init not found in the APK");
                return false;
            }

            BufferedReader moduleClassesReader = new BufferedReader(new InputStreamReader(is));
            String moduleClassName;
            while ((moduleClassName = moduleClassesReader.readLine()) != null) {
                moduleClassName = moduleClassName.trim();
                if (moduleClassName.isEmpty() || moduleClassName.startsWith("#")) {
                    continue;
                }

                try {
                    XLog.i(TAG, "Loading class " + moduleClassName);
                    Class<?> moduleClass = mcl.loadClass(moduleClassName);

                    if (!XposedHelper.isIXposedMod(moduleClass)) {
                        Log.w(TAG, "This class doesn't implement any sub-interface of IXposedMod, skipping it");
                        continue;
                    }
                    else if (IXposedHookInitPackageResources.class.isAssignableFrom(moduleClass)) {
                        Log.w(TAG, "This class requires resource-related hooks (which are disabled), skipping it.");
                        continue;
                    }

                    final Object moduleInstance = moduleClass.newInstance();
                    if (moduleInstance instanceof IXposedHookZygoteInit) {
                        XposedHelper.callInitZygote(moduleApkPath, moduleInstance);
                    }

                    if (moduleInstance instanceof IXposedHookLoadPackage) {
                        // hookLoadPackage(new IXposedHookLoadPackage.Wrapper((IXposedHookLoadPackage) moduleInstance));
                        IXposedHookLoadPackage.Wrapper wrapper = new IXposedHookLoadPackage.Wrapper((IXposedHookLoadPackage) moduleInstance, moduleApkPath);
                        XposedBridge.CopyOnWriteSortedSet<XC_LoadPackage> xc_loadPackageCopyOnWriteSortedSet = new XposedBridge.CopyOnWriteSortedSet<>();
                        xc_loadPackageCopyOnWriteSortedSet.add(wrapper);
                        XC_LoadPackage.LoadPackageParam lpparam = new XC_LoadPackage.LoadPackageParam(xc_loadPackageCopyOnWriteSortedSet);
                        lpparam.packageName = currentApplicationInfo.packageName;
                        lpparam.processName = (String) Class.forName("android.app.ActivityThread").getDeclaredMethod("currentProcessName").invoke(null);
                        lpparam.classLoader = appClassLoader;
                        lpparam.appInfo = currentApplicationInfo;
                        lpparam.isFirstApplication = true;
                        XC_LoadPackage.callAll(lpparam);
                    }

                    if (moduleInstance instanceof IXposedHookInitPackageResources) {
                        XLog.e(TAG, "Unsupport resource hook");
                    }
                }
                catch (Throwable t) {
                    XLog.e(TAG, "", t);
                }
            }
        }
        catch (IOException e) {
            XLog.e(TAG, "", e);
        }
        return true;
    }

    public static void initAndLoadModules() {
        Context context = XpatchUtils.createAppContext();
        initAndLoadModules(context);
    }

    public static void initAndLoadModules(Context context) {
        if (!hasInited.compareAndSet(false, true)) {
            XLog.w(TAG, "Has been init");
            return;
        }

        if (context == null) {
            XLog.e(TAG, "Try to init with context null");
            return;
        }

        appContext = context;

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            if (!FileUtils.isSdcardPermissionGranted(context)) {
                XLog.e(TAG, "File permission is not granted, can not control xposed module by file " + XPOSED_MODULE_FILE_PATH);
            }
        }

        initSELinux(context);

        ClassLoader originClassLoader = context.getClassLoader();
        List<String> modulePathList = loadAllInstalledModule(context);

        for (String modulePath : modulePathList) {
            if (!TextUtils.isEmpty(modulePath)) {
                LSPLoader.loadModule(modulePath, null, context.getApplicationInfo(), originClassLoader);
            }
        }
    }

    private static void initSELinux(Context context) {
        XposedHelper.initSeLinux(context.getApplicationInfo().processName);
    }

    private static List<String> loadAllInstalledModule(Context context) {
        PackageManager pm = context.getPackageManager();
        List<String> modulePathList = new ArrayList<>();

        List<String> packageNameList = loadPackageNameListFromFile(true);
        List<Pair<String, String>> installedModuleList = new ArrayList<>();

        boolean configFileExist = configFileExist();

        // todo: Android 11
        for (PackageInfo pkg : pm.getInstalledPackages(PackageManager.GET_META_DATA)) {
            ApplicationInfo app = pkg.applicationInfo;
            if (!app.enabled) {
                continue;
            }
            if (app.metaData != null && (app.metaData.containsKey("xposedmodule"))) {
                String apkPath = pkg.applicationInfo.publicSourceDir;
                String apkName = context.getPackageManager().getApplicationLabel(pkg.applicationInfo).toString();
                if (TextUtils.isEmpty(apkPath)) {
                    apkPath = pkg.applicationInfo.sourceDir;
                }
                if (!TextUtils.isEmpty(apkPath) && (!configFileExist || packageNameList == null || packageNameList.contains(app.packageName))) {
                    XLog.d(TAG, "query installed module path " + apkPath);
                    modulePathList.add(apkPath);
                }
                installedModuleList.add(Pair.create(pkg.applicationInfo.packageName, apkName));
            }
        }

        final List<Pair<String, String>> installedModuleListFinal = installedModuleList;

        new Thread(new Runnable() {
            @Override
            public void run() {
                List<String> savedPackageNameList = loadPackageNameListFromFile(false);
                if (savedPackageNameList == null) {
                    savedPackageNameList = new ArrayList<>();
                }
                List<Pair<String, String>> addPackageList = new ArrayList<>();
                for (Pair<String, String> packgagePair : installedModuleListFinal) {
                    if (!savedPackageNameList.contains(packgagePair.first)) {
                        XLog.d(TAG, "append " + packgagePair + " to " + XPOSED_MODULE_FILE_PATH);
                        addPackageList.add(packgagePair);
                    }
                }
                try {
                    appendPackageNameToFile(addPackageList);
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        return modulePathList;
    }

    // 从 sd 卡中加载指定文件，以加载指定的 xposed module
    private static List<String> loadPackageNameListFromFile(boolean loadActivedPackages) {
        File moduleFile = new File(DIR_BASE, XPOSED_MODULE_FILE_PATH);
        if (!moduleFile.exists()) {
            return null;
        }
        List<String> modulePackageList = new ArrayList<>();

        try (FileInputStream fileInputStream = new FileInputStream(moduleFile);
             BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fileInputStream))) {
            String modulePackageName;
            while ((modulePackageName = bufferedReader.readLine()) != null) {
                modulePackageName = modulePackageName.trim();
                if (modulePackageName.isEmpty() || (modulePackageName.startsWith("#") && loadActivedPackages)) {
                    continue;
                }

                if (modulePackageName.startsWith("#")) {
                    modulePackageName = modulePackageName.substring(1);
                }
                int index = modulePackageName.indexOf("#");
                if (index > 0) {
                    modulePackageName = modulePackageName.substring(0, index);
                }
                XLog.d(TAG, "load " + XPOSED_MODULE_FILE_PATH + " file result, modulePackageName " + modulePackageName);
                modulePackageList.add(modulePackageName);
            }
        }
        catch (FileNotFoundException ignore) {
            return null;
        }
        catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return modulePackageList;
    }

    private static void appendPackageNameToFile(List<Pair<String, String>> packageNameList) throws IOException {

        if (isEmpty(packageNameList)) {
            return;
        }

        File moduleFile = new File(DIR_BASE, XPOSED_MODULE_FILE_PATH);
        if (!moduleFile.exists()) {
            if (!moduleFile.createNewFile()) {
                throw new IllegalStateException("create " + XPOSED_MODULE_FILE_PATH + " err");
            }
        }
        try (FileOutputStream outputStream = new FileOutputStream(moduleFile, true);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
            for (Pair<String, String> packageInfo : packageNameList) {
                String packageName = packageInfo.first;
                String appName = packageInfo.second;
                writer.write(packageName + "#" + appName);
                writer.write("\n");
                XLog.d(TAG, "append new pkg to " + XPOSED_MODULE_FILE_PATH);
            }
            writer.flush();
        }
        catch (FileNotFoundException ignore) {
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean configFileExist() {
        File moduleConfigFile = new File(DIR_BASE, XPOSED_MODULE_FILE_PATH);
        return moduleConfigFile.exists();
    }

    private static void closeStream(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static boolean isEmpty(Collection<?> collection) {
        if (collection == null || collection.size() == 0) {
            return true;
        }
        return false;
    }

    public static Context getAppContext() {
        return appContext;
    }
}
