package org.lsposed.lspatch.appstub;

import static org.lsposed.lspatch.share.Constants.ORIGINAL_APK_ASSET_PATH;
import static org.lsposed.lspatch.share.Constants.ORIGINAL_APP_COMPONENT_FACTORY_ASSET_PATH;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AppComponentFactory;
import android.app.Application;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import org.lsposed.lspatch.share.FileUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.ZipFile;

import dalvik.system.PathClassLoader;

@SuppressLint("NewApi")
public class LSPAppComponentFactoryStub extends AppComponentFactory {
    private static final String TAG = "LSPatch";
    private static final String PROXY_APPLICATION = "org.lsposed.lspatch.appstub.LSPApplicationStub";

    private ClassLoader appClassLoader = null;
    private ClassLoader lspClassLoader = null;
    private ClassLoader baseClassLoader = null;
    private AppComponentFactory originalAppComponentFactory = null;

    /**
     * Instantiate original AppComponentFactory<br/>
     * This method will be called at <b>instantiateClassLoader</b> by <b>createOrUpdateClassLoaderLocked</b>
     **/
    private void initOriginalAppComponentFactory(ApplicationInfo aInfo) {
        final String originPath = aInfo.dataDir + "/cache/lspatch/origin/";
        final String originalAppComponentFactoryClass = FileUtils.readTextFromInputStream(baseClassLoader.getResourceAsStream(ORIGINAL_APP_COMPONENT_FACTORY_ASSET_PATH));

        try {
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

            appClassLoader = new PathClassLoader(cacheApkPath, aInfo.nativeLibraryDir, baseClassLoader.getParent());

            try {
                originalAppComponentFactory = (AppComponentFactory) appClassLoader.loadClass(originalAppComponentFactoryClass).newInstance();
            } catch (ClassNotFoundException | NullPointerException ignored) {
                if (originalAppComponentFactoryClass != null && !originalAppComponentFactoryClass.isEmpty())
                    Log.w(TAG, "original AppComponentFactory not found");
                originalAppComponentFactory = new AppComponentFactory();
            }

            Log.d(TAG, "instantiate original AppComponentFactory: " + originalAppComponentFactory);
        } catch (Throwable e) {
            Log.e(TAG, "initOriginalAppComponentFactory", e);
        }
    }

    @Override
    public ClassLoader instantiateClassLoader(ClassLoader cl, ApplicationInfo aInfo) {
        baseClassLoader = cl;
        var apkPath = baseClassLoader.getResource("AndroidManifest.xml").getPath();
        apkPath = apkPath.substring(5, apkPath.lastIndexOf('!'));
        lspClassLoader = new PathClassLoader(apkPath, null, null);
        initOriginalAppComponentFactory(aInfo);
        Log.d(TAG, "baseClassLoader is " + baseClassLoader);
        Log.d(TAG, "appClassLoader is " + appClassLoader);
        return originalAppComponentFactory.instantiateClassLoader(appClassLoader, aInfo);
    }

    @Override
    public Application instantiateApplication(ClassLoader cl, String className) throws IllegalAccessException, InstantiationException, ClassNotFoundException {
        lspClassLoader.loadClass(PROXY_APPLICATION).newInstance();
        Log.i(TAG, "lspd initialized, instantiate original application");
        return originalAppComponentFactory.instantiateApplication(cl, className);
    }

    @Override
    public Activity instantiateActivity(ClassLoader cl, String className, Intent intent) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        return originalAppComponentFactory.instantiateActivity(cl, className, intent);
    }

    @Override
    public BroadcastReceiver instantiateReceiver(ClassLoader cl, String className, Intent intent) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        return originalAppComponentFactory.instantiateReceiver(cl, className, intent);
    }

    @Override
    public Service instantiateService(ClassLoader cl, String className, Intent intent) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        return originalAppComponentFactory.instantiateService(cl, className, intent);
    }

    @Override
    public ContentProvider instantiateProvider(ClassLoader cl, String className) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        return originalAppComponentFactory.instantiateProvider(cl, className);
    }
}