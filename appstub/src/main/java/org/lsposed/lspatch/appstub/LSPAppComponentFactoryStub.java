package org.lsposed.lspatch.appstub;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AppComponentFactory;
import android.app.Application;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.Intent;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;

@SuppressLint({"UnsafeDynamicallyLoadedCode", "DiscouragedPrivateApi"})
public class LSPAppComponentFactoryStub extends AppComponentFactory {
    private static final String TAG = "LSPatch";

    public static byte[] dex = null;
    public static ClassLoader appClassLoader = null;
    public static ClassLoader baseClassLoader = null;
    public static AppComponentFactory originalAppComponentFactory = null;

    public LSPAppComponentFactoryStub() {
        baseClassLoader = getClass().getClassLoader();
        try (var is = baseClassLoader.getResourceAsStream("assets/lsp");
             var os = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while (-1 != (n = is.read(buffer))) {
                os.write(buffer, 0, n);
            }
            dex = os.toByteArray();
        } catch (Throwable e) {
            Log.e("LSPatch", "load dex error", e);
        }

        try {
            Class<?> VMRuntime = Class.forName("dalvik.system.VMRuntime");
            Method getRuntime = VMRuntime.getDeclaredMethod("getRuntime");
            getRuntime.setAccessible(true);
            Method vmInstructionSet = VMRuntime.getDeclaredMethod("vmInstructionSet");
            vmInstructionSet.setAccessible(true);

            String arch = (String) vmInstructionSet.invoke(getRuntime.invoke(null));
            String path = baseClassLoader.getResource("assets/lib/lspd/" + arch + "/liblspd.so").getPath().substring(5);
            System.load(path);
        } catch (Throwable e) {
            Log.e("LSPatch", "load lspd error", e);
        }
    }

    @Override
    public Application instantiateApplication(ClassLoader cl, String className) throws IllegalAccessException, InstantiationException, ClassNotFoundException {
        Log.d(TAG, "baseClassLoader is " + baseClassLoader);
        Log.d(TAG, "appClassLoader is " + appClassLoader);
        Log.d(TAG, "originalAppComponentFactory is " + originalAppComponentFactory);
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