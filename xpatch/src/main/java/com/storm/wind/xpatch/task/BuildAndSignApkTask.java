package com.storm.wind.xpatch.task;

import com.storm.wind.xpatch.util.FileUtils;
import com.storm.wind.xpatch.util.ShellCmdUtil;

import java.io.File;

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

        signApk(unsignedApkPath, keyStoreFilePath, signedApkPath, false);

        File unsignedApkFile = new File(unsignedApkPath);
        File signedApkFile = new File(signedApkPath);
        // delete unsigned apk file
        if (!keepUnsignedApkFile && unsignedApkFile.exists() && signedApkFile.exists()) {
            unsignedApkFile.delete();
        }

        // delete the keystore file
        if (keyStoreFile.exists()) {
            keyStoreFile.delete();
        }

    }

    private boolean signApk(String apkPath, String keyStorePath, String signedApkPath, boolean useLocalJarsigner) {
        if (isAndroid()) {
            boolean success = true;
            try {
                ShellCmdUtil.chmodNoException((new File(apkPath)).getParent(), ShellCmdUtil.FileMode.MODE_755);
                net.fornwall.apksigner.Main.main
                        ("--password", "123456", keyStorePath, apkPath, signedApkPath);
            } catch (Exception e1) {
                success = false;
                e1.printStackTrace();
                System.out.println("use fornwall apksigner to sign apk failed, fail msg is :" + e1.toString());
            }
            if (success && new File(signedApkPath).exists()) {
                return true;
            }
        }

        File localJarsignerFile = null;
        try {
            long time = System.currentTimeMillis();
            File keystoreFile = new File(keyStorePath);
            if (keystoreFile.exists()) {
                StringBuilder signCmd;
                if (!useLocalJarsigner) {
                    signCmd = new StringBuilder("jarsigner ");
                } else {
                    String localJarsignerPath = (new File(apkPath)).getParent() + File.separator + "jarsigner-081688";
                    localJarsignerFile = new File(localJarsignerPath);
                    FileUtils.copyFileFromJar("assets/jarsigner", localJarsignerPath);
                    ShellCmdUtil.chmodNoException(localJarsignerPath, ShellCmdUtil.FileMode.MODE_755);
                    // ShellCmdUtil.execCmd("chmod -R 777 " + localJarsignerPath, null);
                    signCmd = new StringBuilder(localJarsignerPath + " ");
                }
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
            if (!useLocalJarsigner) {
                System.out.println("use default jarsigner to sign apk failed，and try again, fail msg is :" +
                        e.toString());
                signApk(apkPath, keyStorePath, signedApkPath, true);
            } else {
                System.out.println("use inner jarsigner to sign apk failed, sign it yourself fail msg is :" +
                        e.toString());

                try {
                    net.fornwall.apksigner.Main.main
                            ("--password", "123456", keyStorePath, apkPath, signedApkPath);
                } catch (Exception e1) {
                    e1.printStackTrace();
                    System.out.println("use fornwall apksigner to sign apk failed, fail msg is :" +
                            e1.toString());
                }
            }
            return false;
        } finally {
            if (localJarsignerFile != null && localJarsignerFile.exists()) {
                localJarsignerFile.delete();
            }
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
}
