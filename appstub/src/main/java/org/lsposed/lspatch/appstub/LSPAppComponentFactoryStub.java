package org.lsposed.lspatch.appstub;

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

import org.lsposed.lspatch.util.FileUtils;

import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Paths;

import dalvik.system.PathClassLoader;

@SuppressLint("NewApi")
public class LSPAppComponentFactoryStub extends AppComponentFactory {
    private static final String TAG = "LSPatch";
    private static final String PROXY_APPLICATION = "org.lsposed.lspatch.appstub.LSPApplicationStub";
    private static final String ORIGINAL_APK_ASSET_PATH = "assets/origin_apk.bin";
    private static final String ORIGINAL_APP_COMPONENT_FACTORY_ASSET_PATH = "assets/original_app_component_factory.ini";

    private ClassLoader appClassLoader = null;
    private ClassLoader lspClassLoader = null;
    private ClassLoader baseClassLoader = null;
    private AppComponentFactory originalAppComponentFactory = null;

    /**
     * Instantiate original AppComponentFactory<br/>
     * This method will be called at <b>instantiateClassLoader</b> by <b>createOrUpdateClassLoaderLocked</b>
     **/
    private void initOriginalAppComponentFactory(ApplicationInfo aInfo) {
        final String cacheApkPath = aInfo.dataDir + "/cache/origin_apk.bin";
        final String originalAppComponentFactoryClass = FileUtils.readTextFromInputStream(baseClassLoader.getResourceAsStream(ORIGINAL_APP_COMPONENT_FACTORY_ASSET_PATH));

        try {
            try (InputStream inputStream = baseClassLoader.getResourceAsStream(ORIGINAL_APK_ASSET_PATH)) {
                Files.copy(inputStream, Paths.get(cacheApkPath));
            } catch (FileAlreadyExistsException ignored) {
            }
            appClassLoader = new PathClassLoader(cacheApkPath, aInfo.nativeLibraryDir, baseClassLoader.getParent());

            try {
                originalAppComponentFactory = (AppComponentFactory) appClassLoader.loadClass(originalAppComponentFactoryClass).newInstance();
            } catch (ClassNotFoundException | NullPointerException ignored) {
                if (originalAppComponentFactoryClass != null && !originalAppComponentFactoryClass.isEmpty())
                    Log.w(TAG, "Original AppComponentFactory not found");
                originalAppComponentFactory = new AppComponentFactory();
            }

            Log.d(TAG, "Instantiate original AppComponentFactory: " + originalAppComponentFactory);
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