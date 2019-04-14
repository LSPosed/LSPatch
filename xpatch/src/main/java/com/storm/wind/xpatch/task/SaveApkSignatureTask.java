package com.storm.wind.xpatch.task;

import com.storm.wind.xpatch.util.ApkSignatureHelper;
import com.storm.wind.xpatch.util.FileUtils;

import java.io.File;

/**
 * Created by Wind
 */
public class SaveApkSignatureTask implements Runnable {

    private String apkPath;
    private String dstFilePath;

    private final static String SIGNATURE_INFO_ASSET_PATH = "assets/xpatch_asset/original_signature_info.ini";

    public SaveApkSignatureTask(String apkPath, String unzipApkFilePath) {
        this.apkPath = apkPath;
        this.dstFilePath = (unzipApkFilePath + SIGNATURE_INFO_ASSET_PATH).replace("/", File.separator);
    }

    @Override
    public void run() {
        // First,  get the original signature
        String originalSignature = ApkSignatureHelper.getApkSignInfo(apkPath);
        if (originalSignature == null || originalSignature.isEmpty()) {
            System.out.println(" Get original signature failed !!!!");
            return;
        }

        // Then, save the signature chars to the asset file
        File file = new File(dstFilePath);
        File fileParent = file.getParentFile();
        if (!fileParent.exists()) {
            fileParent.mkdirs();
        }

        FileUtils.writeFile(dstFilePath, originalSignature);
    }
}
