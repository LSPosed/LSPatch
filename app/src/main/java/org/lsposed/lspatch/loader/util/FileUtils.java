package org.lsposed.lspatch.loader.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class FileUtils {

    //读写权限
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    public static boolean isSdcardPermissionGranted(Context context) {
        int pid = android.os.Process.myPid();
        int uid = Process.myUid();
        return context.checkPermission(PERMISSIONS_STORAGE[0], pid, uid) == PackageManager.PERMISSION_GRANTED && context.checkPermission(PERMISSIONS_STORAGE[1], pid,
                uid) == PackageManager.PERMISSION_GRANTED;
    }

    public static String readTextFromAssets(Context context, String assetsFileName) {
        if (context == null) {
            throw new IllegalStateException("context null");
        }
        try (InputStream is = context.getAssets().open(assetsFileName)) {
            return readTextFromInputStream(is);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String readTextFromInputStream(InputStream is) {
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8); BufferedReader bufferedReader = new BufferedReader(reader)) {
            StringBuilder builder = new StringBuilder();
            String str;
            while ((str = bufferedReader.readLine()) != null) {
                builder.append(str);
            }
            return builder.toString();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
