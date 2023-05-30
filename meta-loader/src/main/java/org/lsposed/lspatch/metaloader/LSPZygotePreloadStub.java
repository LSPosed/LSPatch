package org.lsposed.lspatch.metaloader;

import static org.lsposed.lspatch.share.Constants.CONFIG_ASSET_PATH;

import android.app.ZygotePreload;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import org.json.JSONObject;

/**
 * Created by JingMatrix
 */
public class LSPZygotePreloadStub implements android.app.ZygotePreload {
    private static final String TAG = "LSPatch-MetaLoader";
    private static JSONObject config;

    @Override
    public void doPreload(ApplicationInfo appInfo) {
        String originalApkPath = Paths.get(appInfo.sourceDir).getParent().toString() + "/split_original.apk";
        appInfo.sourceDir = originalApkPath;
        var cl = LSPZygotePreloadStub.class.getClassLoader();
        try (var is = cl.getResourceAsStream(CONFIG_ASSET_PATH)) {
            BufferedReader streamReader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            config = new JSONObject(streamReader.lines().collect(Collectors.joining()));
            appInfo.appComponentFactory = config.getString("appComponentFactory");
        } catch (Throwable e) {
            Log.e(TAG, "Failed to load config file", e);
            return;
        }

        try {
            Class<?> originalPreload = cl.loadClass(config.getString("zygotePreloadName"));
            Method originalDoPreload = originalPreload.getDeclaredMethod("doPreload", ApplicationInfo.class);
            originalDoPreload.invoke(originalPreload.getDeclaredConstructor().newInstance(), appInfo);
        } catch (Throwable e) {
            Log.d(TAG, "Fail to call original ZygotePreload", e);
        }
    }
}
