package org.lsposed.patch.task;

import com.android.apksigner.ApkSignerTool;

import com.android.tools.build.apkzlib.zip.ZFile;

import org.apache.commons.io.IOUtils;
import org.lsposed.patch.LSPatch;
import org.lsposed.patch.util.ZipUtils;
import org.lsposed.patch.util.ShellCmdUtil;

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
            if (isAndroid()) {
                keyStoreAssetPath = "assets/android.keystore";
            }
            else {
                keyStoreAssetPath = "assets/keystore";
            }

            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(keyStoreAssetPath);
                 FileOutputStream out = new FileOutputStream(keyStoreFile)) {
                IOUtils.copy(inputStream, out);
            }

            boolean signResult = signApk(unsignedApkPath, keyStoreFile.getAbsolutePath(), signedApkPath);

            File unsignedApkFile = new File(unsignedApkPath);
            File signedApkFile = new File(signedApkPath);

            // delete unsigned apk file
            if (!keepUnsignedApkFile && unsignedApkFile.exists() && signedApkFile.exists() && signResult) {
                LSPatch.fuckIfFail(unsignedApkFile.delete());
            }
        }
        catch (Exception err) {
            throw new IllegalStateException("wtf", err);
        }
    }

    private boolean signApk(String apkPath, String keyStorePath, String signedApkPath) {
        if (signApkUsingAndroidApksigner(apkPath, keyStorePath, signedApkPath, "123456")) {
            return true;
        }
        if (isAndroid()) {
            System.out.println(" Sign apk failed, please sign it yourself.");
            return false;
        }
        try {
            long time = System.currentTimeMillis();
            File keystoreFile = new File(keyStorePath);
            if (keystoreFile.exists()) {
                StringBuilder signCmd;
                signCmd = new StringBuilder("jarsigner ");
                signCmd.append(" -keystore ")
                        .append(keyStorePath)
                        .append(" -storepass ")
                        .append("123456")
                        .append(" -signedjar ")
                        .append(" " + signedApkPath + " ")
                        .append(" " + apkPath + " ")
                        .append(" -digestalg SHA1 -sigalg SHA1withRSA ")
                        .append(" key0 ");
                System.out.println("\n" + signCmd + "\n");
                String result = ShellCmdUtil.execCmd(signCmd.toString(), null);
                System.out.println(" sign apk time is :" + ((System.currentTimeMillis() - time) / 1000) +
                        "s\n\n" + "  result=" + result);
                return true;
            }
            System.out.println(" keystore not exist :" + keystoreFile.getAbsolutePath() +
                    " please sign the apk by hand. \n");
            return false;
        }
        catch (Throwable e) {
            System.out.println("use default jarsigner to sign apk failed, fail msg is :" +
                    e.toString());
            return false;
        }
    }

    private boolean isAndroid() {
        boolean isAndroid = true;
        try {
            Class.forName("android.content.Context");
        }
        catch (ClassNotFoundException e) {
            isAndroid = false;
        }
        return isAndroid;
    }

    // 使用Android build-tools里自带的apksigner工具进行签名
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

        int size = commandList.size();
        String[] commandArray = new String[size];
        commandArray = commandList.toArray(commandArray);

        try {
            ApkSignerTool.main(commandArray);
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
