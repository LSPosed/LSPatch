package org.lsposed.lspatch.appstub;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;

@SuppressLint("UnsafeDynamicallyLoadedCode")
public class LSPApplicationStub extends Application {

    private static byte[] dex = null;

    static {
        try (var is = LSPApplicationStub.class.getClassLoader().getResourceAsStream("assets/lsp");
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

        try (var br = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec("getprop ro.product.cpu.abi").getInputStream()))) {
            String arch = br.readLine();
            String path = LSPApplicationStub.class.getClassLoader().getResource("assets/lib/" + arch + "/liblspd.so").getPath().substring(5);
            System.load(path);
        } catch (Throwable e) {
            Log.e("LSPatch", "load lspd error", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }
}
