package org.lsposed.patch.task;

import com.android.apksigner.ApkSignerTool;

import com.android.tools.build.apkzlib.zip.ZFile;

import org.apache.commons.io.IOUtils;
import org.lsposed.patch.LSPatch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * Created by Wind
 */
public class BuildAndSignApkTask implements Runnable {

    private final boolean keepUnsignedApkFile;
    private final String signedApkPath;
    private final String unzipApkFilePath;
    private final String unsignedApkPath;

    public BuildAndSignApkTask(boolean keepUnsignedApkFile, String unzipApkFilePath, String unsignedApkPath, String signedApkPath) {
        this.keepUnsignedApkFile = keepUnsignedApkFile;
        this.unsignedApkPath = unsignedApkPath;
        this.unzipApkFilePath = unzipApkFilePath;
        this.signedApkPath = signedApkPath;
    }

    @Override
    public void run() {

        try {
            File unzipApkPathFile = new File(unzipApkFilePath);
            File keyStoreFile = new File(unzipApkPathFile, "keystore");
            String keyStoreAssetPath;
            keyStoreAssetPath = "assets/keystore";

            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(keyStoreAssetPath);
                 FileOutputStream out = new FileOutputStream(keyStoreFile)) {
                IOUtils.copy(inputStream, out);
            }

            boolean signResult = signApk(unsignedApkPath, keyStoreFile.getAbsolutePath(), signedApkPath);

            File unsignedApkFile = new File(unsignedApkPath);
            File signedApkFile = new File(signedApkPath);

            // delete unsigned apk file
            if (!keepUnsignedApkFile && unsignedApkFile.exists() && signedApkFile.exists() && signResult) {
                if (!unsignedApkFile.delete()) {
                    throw new IllegalStateException("wtf");
                }
            }
        }
        catch (Exception err) {
            throw new IllegalStateException("wtf", err);
        }
    }

    private boolean signApk(String apkPath, String keyStorePath, String signedApkPath) {
        return signApkUsingAndroidApksigner(apkPath, keyStorePath, signedApkPath, "123456");
    }

    private boolean signApkUsingAndroidApksigner(String apkPath, String keyStorePath, String signedApkPath, String keyStorePassword) {
        ArrayList<String> commandList = new ArrayList<>();

        commandList.add("sign");
        commandList.add("--ks");
        commandList.add(keyStorePath);
        commandList.add("--ks-key-alias");
        commandList.add("key0");
        commandList.add("--ks-pass");
        commandList.add("pass:" + keyStorePassword);
        commandList.add("--key-pass");
        commandList.add("pass:" + keyStorePassword);
        commandList.add("--out");
        commandList.add(signedApkPath);
        commandList.add("--v1-signing-enabled");
        commandList.add("true");
        commandList.add("--v2-signing-enabled");   // v2签名不兼容android 6
        commandList.add("false");
        commandList.add("--v3-signing-enabled");   // v3签名不兼容android 6
        commandList.add("false");
        commandList.add(apkPath);

        try {
            ApkSignerTool.main(commandList.toArray(new String[0]));
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
