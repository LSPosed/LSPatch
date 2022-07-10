package org.lsposed.lspatch.appstub;

import android.annotation.SuppressLint;
import android.app.AppComponentFactory;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Objects;

@SuppressLint("UnsafeDynamicallyLoadedCode")
public class LSPAppComponentFactoryStub extends AppComponentFactory {
    public static byte[] dex = null;

    static {
        var cl = Objects.requireNonNull(LSPAppComponentFactoryStub.class.getClassLoader());
        try (var is = cl.getResourceAsStream("assets/lspatch/lsp.dex");
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
            String path = cl.getResource("assets/lspatch/so/" + arch + "/liblspatch.so").getPath().substring(5);
            System.load(path);
        } catch (Throwable e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
