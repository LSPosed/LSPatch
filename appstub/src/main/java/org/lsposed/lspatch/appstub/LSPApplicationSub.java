package org.lsposed.lspatch.appstub;

import android.app.ActivityThread;
import android.app.Application;
import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import dalvik.system.InMemoryDexClassLoader;

public class LSPApplicationSub extends Application {
    final static String TAG = LSPApplicationSub.class.getSimpleName();

    static Object realLSPApplication = null;

    static {
        // load real lsp loader from asset
        Context context = createAppContext();
        if (context == null) {
            Log.e(TAG, "create context err");
        }
        else {
            try {
                InputStream inputStream = context.getAssets().open("lsploader.dex");
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                int nRead;
                byte[] data = new byte[16384];

                while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }

                InMemoryDexClassLoader inMemoryDexClassLoader = new InMemoryDexClassLoader(ByteBuffer.wrap(buffer.toByteArray()), LSPApplicationSub.class.getClassLoader());
                Class<?> lspa = inMemoryDexClassLoader.loadClass("org.lsposed.lspatch.appstub.LSPApplication");
                realLSPApplication = lspa.newInstance();
            }
            catch (Exception e) {
                throw new IllegalStateException("wtf", e);
            }
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        if (realLSPApplication != null) {
            try {
                realLSPApplication.getClass().getMethod("attachBaseContext", Context.class).invoke(realLSPApplication, base);
            }
            catch (Exception e) {
                throw new IllegalStateException("wtf", e);
            }
        }
    }

    // copy from app project
    public static Context createAppContext() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentActivityThreadMethod.setAccessible(true);

            Object activityThreadObj = currentActivityThreadMethod.invoke(null);

            Field boundApplicationField = activityThreadClass.getDeclaredField("mBoundApplication");
            boundApplicationField.setAccessible(true);
            Object mBoundApplication = boundApplicationField.get(activityThreadObj);   // AppBindData
            if (mBoundApplication == null) {
                Log.e(TAG, "mBoundApplication null");
                return null;
            }
            Field infoField = mBoundApplication.getClass().getDeclaredField("info");   // info
            infoField.setAccessible(true);
            Object loadedApkObj = infoField.get(mBoundApplication);  // LoadedApk
            if (loadedApkObj == null) {
                Log.e(TAG, "loadedApkObj null");
                return null;
            }
            Class<?> contextImplClass = Class.forName("android.app.ContextImpl");
            Method createAppContextMethod = contextImplClass.getDeclaredMethod("createAppContext", activityThreadClass, loadedApkObj.getClass());
            createAppContextMethod.setAccessible(true);

            Object context = createAppContextMethod.invoke(null, (ActivityThread) activityThreadObj, loadedApkObj);

            if (context instanceof Context) {
                return (Context) context;
            }
        }
        catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException | NoSuchFieldException e) {
            e.printStackTrace();
        }
        return null;
    }
}
