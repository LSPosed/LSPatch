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
    private static final String ORIGINAL_APK_ASSET_PATH = "assets/origin_apk.bin";
    private static final String ORIGINAL_APP_COMPONENT_FACTORY_ASSET_PATH = "assets/original_app_component_factory.ini";

    private AppComponentFactory originalAppComponentFactory = null;

    // Proxy appComponentFactory to load the original one
    private void initOrigin(ClassLoader cl, ApplicationInfo aInfo) {
        final String cacheApkPath = aInfo.dataDir + "/cache/origin_apk.bin";
        final String originalAppComponentFactoryClass = FileUtils.readTextFromInputStream(cl.getResourceAsStream(ORIGINAL_APP_COMPONENT_FACTORY_ASSET_PATH));

        try {
            try (InputStream inputStream = cl.getResourceAsStream(ORIGINAL_APK_ASSET_PATH)) {
                Files.copy(inputStream, Paths.get(cacheApkPath));
            } catch (FileAlreadyExistsException ignored) {
            }
            ClassLoader appClassLoader = new PathClassLoader(cacheApkPath, cl.getParent());
            originalAppComponentFactory = (AppComponentFactory) appClassLoader.loadClass(originalAppComponentFactoryClass).newInstance();
            Log.d(TAG, "appComponentFactory is now switched to " + originalAppComponentFactory);
        } catch (Throwable e) {
            Log.e(TAG, "initOrigin", e);
        }
    }

    @Override
    public ClassLoader instantiateClassLoader(ClassLoader cl, ApplicationInfo aInfo) {
        if (originalAppComponentFactory == null) initOrigin(cl, aInfo);
        return originalAppComponentFactory.instantiateClassLoader(cl, aInfo);
    }

    @Override
    public Application instantiateApplication(ClassLoader cl, String className) throws IllegalAccessException, InstantiationException, ClassNotFoundException {
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