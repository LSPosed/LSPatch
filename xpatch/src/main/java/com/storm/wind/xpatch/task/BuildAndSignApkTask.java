package com.storm.wind.xpatch.task;

import com.android.apksigner.ApkSignerTool;
import com.storm.wind.xpatch.util.FileUtils;
import com.storm.wind.xpatch.util.ShellCmdUtil;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by Wind
 */
public class BuildAndSignApkTask implements Runnable {

    private boolean keepUnsignedApkFile;

    private String signedApkPath;

    private String unzipApkFilePath;

    public BuildAndSignApkTask(boolean keepUnsignedApkFile, String unzipApkFilePath, String signedApkPath) {
        this.keepUnsignedApkFile = keepUnsignedApkFile;
        this.unzipApkFilePath = unzipApkFilePath;
        this.signedApkPath = signedApkPath;
    }

    @Override
    public void run() {

        File unzipApkFile = new File(unzipApkFilePath);

        // 将文件压缩到当前apk文件的上一级目录上
        String unsignedApkPath = unzipApkFile.getParent() + File.separator + "unsigned.apk";
        FileUtils.compressToZip(unzipApkFilePath, unsignedApkPath);

        // 将签名文件复制从assets目录下复制出来
        String keyStoreFilePath = unzipApkFile.getParent() + File.separator + "keystore";

        File keyStoreFile = new File(keyStoreFilePath);
        // assets/keystore分隔符不能使用File.separator，否则在windows上抛出IOException !!!
        FileUtils.copyFileFromJar("assets/keystore", keyStoreFilePath);

        boolean signResult = signApk(unsignedApkPath, keyStoreFilePath, signedApkPath);

        File unsignedApkFile = new File(unsignedApkPath);
        File signedApkFile = new File(signedApkPath);
        // delete unsigned apk file
        if (!keepUnsignedApkFile && unsignedApkFile.exists() && signedApkFile.exists() && signResult) {
            unsignedApkFile.delete();
        }

        // delete the keystore file
        if (keyStoreFile.exists()) {
            keyStoreFile.delete();
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
//                System.out.println("\n" + signCmd + "\n");
                String result = ShellCmdUtil.execCmd(signCmd.toString(), null);
                System.out.println(" sign apk time is :" + ((System.currentTimeMillis() - time) / 1000) +
                        "s\n\n" + "  result=" + result);
                return true;
            }
            System.out.println(" keystore not exist :" + keystoreFile.getAbsolutePath() +
                    " please sign the apk by hand. \n");
            return false;
        } catch (Throwable e) {
            System.out.println("use default jarsigner to sign apk failed, fail msg is :" +
                    e.toString());
            return false;
        }
    }

    private boolean isAndroid() {
        boolean isAndroid = true;
        try {
            Class.forName("android.content.Context");
        } catch (ClassNotFoundException e) {
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
        commandList.add(apkPath);

        int size = commandList.size();
        String[] commandArray = new String[size];
        commandArray = commandList.toArray(commandArray);

        try {
            ApkSignerTool.main(commandArray);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
