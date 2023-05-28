package org.lsposed.lspatch.metaloader;

import static org.lsposed.lspatch.share.Constants.CONFIG_ASSET_PATH;

import android.app.ZygotePreload;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Objects;

import org.lsposed.lspatch.share.PatchConfig;

/**
 * Created by JingMatrix
 */
public class LSPZygotePreloadStub implements android.app.ZygotePreload {
    private static final String TAG = "LSPatch-MetaLoader";

    @Override
    public void doPreload(ApplicationInfo appInfo) {
        String originApkPath = Paths.get(appInfo.sourceDir).getParent().toString() + "/split_original.apk";
		appInfo.sourceDir = originApkPath;
		var cl = LSPZygotePreloadStub.class.getClassLoader();
		try (var is = cl.getResourceAsStream(CONFIG_ASSET_PATH)) {
			BufferedReader streamReader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
			PatchConfig config = new Gson().fromJson(streamReader, PatchConfig.class);
            appInfo.appComponentFactory = config.appComponentFactory;
		} catch (IOException e) {
			Log.e(TAG, "Failed to load config file");
			return;
		}

		try {
			Class<?> originalPreload = cl.loadClass("org.chromium.content_public.app.ZygotePreload");
			Method originalDoPreload = originalPreload.getDeclaredMethod("doPreload", ApplicationInfo.class);
			originalDoPreload.invoke(originalPreload.getDeclaredConstructor().newInstance(), appInfo);
		} catch (Throwable e) {
			Log.d(TAG, "Fail to call original ZygotePreload", e);
		}
    }
}
