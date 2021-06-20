package org.lsposed.lspatch.appstub;

import android.app.Application;
import android.content.Context;

import java.io.ByteArrayOutputStream;

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
            android.util.Log.e("LSPatch", "load dex error", e);
        }
        System.loadLibrary("lspd");
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
