package org.lsposed.lspatch.service;

import android.content.Context;
import android.os.Environment;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import org.lsposed.lspatch.loader.util.FileUtils;
import org.lsposed.lspatch.share.Constants;
import org.lsposed.lspatch.util.ModuleLoader;
import org.lsposed.lspd.models.Module;
import org.lsposed.lspd.service.ILSPApplicationService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

public class LocalApplicationService extends ILSPApplicationService.Stub {

    private static final String TAG = "LSPatch";

    private final List<Module> modules = new ArrayList<>();

    public LocalApplicationService(Context context) {
        try {
            for (var name : context.getAssets().list("lspatch/modules")) {
                String packageName = name.substring(0, name.length() - 4);
                String modulePath = context.getCacheDir() + "/lspatch/" + packageName + "/";
                String cacheApkPath;
                try (ZipFile sourceFile = new ZipFile(context.getPackageResourcePath())) {
                    cacheApkPath = modulePath + sourceFile.getEntry(Constants.EMBEDDED_MODULES_ASSET_PATH + name).getCrc() + ".apk";
                }

                if (!Files.exists(Paths.get(cacheApkPath))) {
                    Log.i(TAG, "Extract module apk: " + packageName);
                    FileUtils.deleteFolderIfExists(Paths.get(modulePath));
                    Files.createDirectories(Paths.get(modulePath));
                    try (var is = context.getAssets().open("lspatch/modules/" + name)) {
                        Files.copy(is, Paths.get(cacheApkPath));
                    }
                }

                var module = new Module();
                module.apkPath = cacheApkPath;
                module.packageName = packageName;
                module.file = ModuleLoader.loadModule(cacheApkPath);
                modules.add(module);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error when initializing LocalApplicationServiceClient", e);
        }
    }

    @Override
    public List<Module> getLegacyModulesList() {
        return modules;
    }

    @Override
    public List<Module> getModulesList() {
        return new ArrayList<>();
    }

    @Override
    public String getPrefsPath(String packageName) {
        return new File(Environment.getDataDirectory(), "data/" + packageName + "/shared_prefs/").getAbsolutePath();
    }

    @Override
    public ParcelFileDescriptor requestInjectedManagerBinder(List<IBinder> binder) {
        return null;
    }

    @Override
    public IBinder asBinder() {
        return this;
    }
}
